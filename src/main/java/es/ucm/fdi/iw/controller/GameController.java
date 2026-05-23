package es.ucm.fdi.iw.controller;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.ucm.fdi.iw.controller.dtos.AnswerReqDTO;
import es.ucm.fdi.iw.controller.dtos.AnswerResDTO;
import es.ucm.fdi.iw.controller.dtos.GameSetupDTO;
import es.ucm.fdi.iw.controller.dtos.QuestionDataPrivateDTO;
import es.ucm.fdi.iw.controller.dtos.QuestionDataPublicDTO;
import es.ucm.fdi.iw.model.Game;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 * CONTROLLER DE PARTIDAS
 * 
 * Gestiona tanto las partidas individuales como
 * las multijugador y el lobby
 * Ruta base: /game/**
 */

@Controller
@RequestMapping("/game")
public class GameController {
    private final AuthenticationManager authenticationManager;
    private static final Logger log = LogManager.getLogger(GameController.class);
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    GameController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    /*
     * ==============
     * SINGLE PLAYER
     * ================
     */
    // Comienza la partida de un solo jugador
    @PostMapping("/start_single_game")
    public String startGame(@ModelAttribute GameSetupDTO setup,
            Model model,
            HttpSession session) {

        // crea la url para la llamada a la api externa con el numero de preguntas que
        // se hayan pedido
        // la categoría y la dificultad seleccionadas
        String url = "https://opentdb.com/api.php?amount=" + setup.getQuestionCount();
        if (setup.getCategory() != null && !setup.getCategory().isEmpty()) {
            url += "&category=" + setup.getCategory();
        }
        if (setup.getDifficulty() != null && !setup.getDifficulty().isEmpty()) {
            url += "&difficulty=" + setup.getDifficulty().toLowerCase();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        try {
            // creamos una llamada a la api con el url y recibimos la respuesta
            RestTemplate rest = new RestTemplate();
            Map<String, Object> response = rest.getForObject(url, Map.class);
            results = (List<Map<String, Object>>) response.get("results");

            // Validamos también que la API no haya devuelto una lista vacía o nula
            if (results == null || results.isEmpty()) {
                return "redirect:/";
            }
        } catch (org.springframework.web.client.RestClientException e) {
            // Si no hay conexión, el host no responde o da un error HTTP (4xx o 5xx),
            // redirigimos al usuario a la raíz "/"
            return "redirect:/";
        }

        // arrays para las preguntas (versión completa para validación y versión
        // limitada para el cliente)
        List<QuestionDataPrivateDTO> fullQuestions = new ArrayList<>();
        List<QuestionDataPublicDTO> publicQuestions = new ArrayList<>();

        // por cada pregunta, mezclamos las respuestas y las metemos en los arrays
        int i = 0;
        for (Map<String, Object> q : results) {
            List<String> answers = new ArrayList<>();
            String correct = (String) q.get("correct_answer");

            answers.add(correct);
            answers.addAll((List<String>) q.get("incorrect_answers"));
            Collections.shuffle(answers);

            fullQuestions.add(new QuestionDataPrivateDTO(i, (String) q.get("question"), answers, correct));
            publicQuestions.add(new QuestionDataPublicDTO(i, (String) q.get("question"), answers));

            i++;
        }

        // insertamos las preguntas tanto en la sesión (enteras) como en el modelo
        // (cliente)
        session.setAttribute("questions", fullQuestions);
        model.addAttribute("questions", publicQuestions);

        // redirigimos a la pantalla de juego
        return "single_game";
    }

    // Comprueba si la respuesta dada es correcta o no
    @PostMapping("/answer")
    @ResponseBody
    @Transactional
    public AnswerResDTO checkAnswer(@RequestBody AnswerReqDTO req, HttpSession session) {
        // recupera la lista de preguntas enteras
        List<QuestionDataPrivateDTO> questions = (List<QuestionDataPrivateDTO>) session.getAttribute("questions");

        if (questions == null || req.getQuestionId() >= questions.size()) {
            return new AnswerResDTO(false, null);
        }

        // compara la respuesta dada con la correcta para determinar su validez
        QuestionDataPrivateDTO q = questions.get(req.getQuestionId());
        boolean isCorrect = q.getCorrectAnswer().equals(req.getAnswer());

        // si la respuesta es correcta inserta 10 puntos en la BD para el usuario que la
        // envío
        if (isCorrect) {
            User user = (User) session.getAttribute("u");
            if (user != null) {
                User managedUser = entityManager.find(User.class, user.getId());

                if (managedUser != null) {
                    managedUser.setTotalPoints(
                            (managedUser.getTotalPoints() == null ? 0 : managedUser.getTotalPoints()) + 10);

                    session.setAttribute("u", managedUser);
                }
            }
        }

        // devuelve el resultado y la respuesta correcta
        return new AnswerResDTO(isCorrect, q.getCorrectAnswer());
    }

    /*
     * ==============
     * MULTI PLAYER
     * ================
     */

    // Crea una nueva partida Game en la BD con código único de 4 chars,
    // asigna al usuario actual como host, y redirige al lobby
    @PostMapping("/multi_game")
    @Transactional
    public String createMultiGame(
            @RequestParam(required = false) List<Integer> categories,
            @RequestParam String difficulty,
            HttpSession session) throws JsonProcessingException {

        // tomamos al usuario
        User u = (User) session.getAttribute("u");
        u = entityManager.find(User.class, u.getId());

        // creamos la partida con sus categorías, un código generado, su dificultad, su
        // estado (iniciado a WAITING) y su host
        Game game = new Game();
        Set<TriviaCategory> cats = (categories == null || categories.isEmpty())
                ? EnumSet.allOf(TriviaCategory.class)
                : categories.stream()
                        .map(TriviaCategory::fromId)
                        .collect(Collectors.toSet());
        game.setCategories(cats);
        game.setCode(generateGameCode(6));
        game.setDifficulty(difficulty);
        game.setGameState("WAITING");
        game.setHost(u);
        // Inicializar el estado interno de la partida
        Map<String, Object> initialState = new HashMap<>();

        // Players: inicializamos con el host como primer jugador
        List<Map<String, Object>> players = new ArrayList<>();
        Map<String, Object> hostPlayer = new HashMap<>();
        hostPlayer.put("id", u.getId());
        hostPlayer.put("username", u.getUsername());
        hostPlayer.put("boardPosition", 0);
        hostPlayer.put("gamePosition", 1);
        players.add(hostPlayer);
        initialState.put("players", players);

        // Orden de turnos: inicialmente solo el host
        Map<Integer, Long> turnOrder = new HashMap<>();
        turnOrder.put(1, u.getId());
        initialState.put("turnOrder", turnOrder);
        // la "ultima tirada del dado" se inicializa a 0 (valor imposible)
        initialState.put("lastDiceRoll", 0);
        // aunque se inicializan los valores, no hay streak, no hay pregunta esperando,
        // ni turno.
        Map<String, Object> turnInfo = new HashMap<>();
        turnInfo.put("currentStreak", 0);
        turnInfo.put("questionPending", false);
        turnInfo.put("currentTurn", 0);
        initialState.put("turnInfo", turnInfo);

        // Convertimos a JSON el estado interno y lo insertamos en el game
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonState = objectMapper.writeValueAsString(initialState);
        game.setInternalState(jsonState);

        // Persistimos el nuevo game
        entityManager.persist(game);
        entityManager.flush();

        // Redirigimos al lobby para la nueva partida
        return "redirect:/game/lobby/" + game.getCode();
    }

    // Carga la partida por código y renderiza multi_game.html
    @GetMapping("/multi_game/{code}")
    public String multiGame(@PathVariable String code, Model model, HttpSession session) {
        // encuentra la partida
        Game game;
        game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        // si la partida no existe envía al usuario al inicio
        if (game == null)
            return "redirect:/";

        // si la partida existe pero no está empezada enviamos al usuario al lobby
        if (("WAITING").equalsIgnoreCase(game.getGameState().trim())) {
            return "redirect:/game/lobby/" + code;
        }
        // si la partida existe pero ya acabó enviamos al usuario al inicio
        if (("FINISHED").equalsIgnoreCase(game.getGameState().trim())) {
            return "redirect:/";
        }

        // obtenemos el usuario logueado
        User currentUser = (User) session.getAttribute("u");
        if (currentUser == null) {
            return "redirect:/login";
        }

        // obtenemos las categorías de la partida
        List<Map<String, String>> categories = game.getCategories()
                .stream()
                .map(c -> Map.of(
                        "key", c.name(),
                        "label", c.getLabel()))
                .toList();

        // insertamos los valores en el modelo y devolvemos el html de multi_game
        model.addAttribute("categories", categories);
        model.addAttribute("user", currentUser);
        model.addAttribute("game", game);
        session.setAttribute("topics", game.getCode());

        return "multi_game";
    }

    // Devuelve el estado interno de la partida
    @GetMapping("/{code}/state")
    @ResponseBody
    public Map<String, Object> getState(@PathVariable String code) throws Exception {
        // toma el juego
        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> state = mapper.readValue(game.getInternalState(), Map.class);

        // devuelve su estado
        return state;
    }

    // Expulsa a un jugador de la partida y emite "player_kicked" por WebSocket
    @PostMapping("/{code}/kick/{userId}")
    @ResponseBody
    @Transactional
    public Map<String, Object> kickPlayer(
            @PathVariable String code,
            @PathVariable Long userId,
            HttpSession session) throws JsonProcessingException {

        // obtiene al usuario que hace la petición
        User u = (User) session.getAttribute("u");
        if (u == null) {
            return Map.of("status", "error", "message", "Usuario no autenticado");
        }

        // recupera el juego
        Game game;
        try {
            game = entityManager.createNamedQuery("Game.byCode", Game.class)
                    .setParameter("code", code)
                    .getSingleResult();
        } catch (Exception e) {
            return Map.of("status", "error", "message", "Partida no encontrada");
        }

        // Solo el host puede expulsar jugadores
        if (game.getHost().getId() != (u.getId())) {
            return Map.of("status", "error", "message", "Solo el host puede expulsar jugadores");
        }

        // No puede expulsarse a sí mismo
        if (userId.equals(u.getId())) {
            return Map.of("status", "error", "message", "No puedes expulsarte a ti mismo");
        }

        // Leer internalState y parsearlo
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> state;
        String internalState = game.getInternalState();

        if (internalState == null || internalState.isBlank()) {
            state = new HashMap<>();
            state.put("players", new ArrayList<>());
        } else {
            state = objectMapper.readValue(internalState, new TypeReference<Map<String, Object>>() {
            });
            state.putIfAbsent("players", new ArrayList<>());
        }

        @SuppressWarnings("unchecked")
        Map<Integer, Number> turnOrderRaw = (Map<Integer, Number>) state.get("turnOrder");

        if (turnOrderRaw != null) {
            // elimina jugador del turnOrder
            turnOrderRaw.entrySet().removeIf(e -> e.getValue().longValue() == userId);

            // reindexar y convertir todo el turnOrder
            Map<Integer, Long> newTurnOrder = new LinkedHashMap<>();
            int index = 1;
            for (Number n : turnOrderRaw.values()) {
                newTurnOrder.put(index++, n.longValue());
            }

            state.put("turnOrder", newTurnOrder);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");

        // Elimina al jugador
        boolean removed = players.removeIf(p -> {
            Object idObj = p.get("id");
            if (idObj instanceof Number) {
                return ((Number) idObj).longValue() == userId;
            }
            return false;
        });
        if (!removed) {
            return Map.of("status", "error", "message", "Jugador no encontrado en la partida");
        }

        // SI SE EXPULSA AL PENULTIMO JUGADOR SE ACABA LA PARTIDA
        state.put("players", players);

        boolean isLastPlayer = (players.size()) <= 1;
        boolean isStarted = "STARTED".equalsIgnoreCase(game.getGameState());
        boolean isFinished = "FINISHED".equalsIgnoreCase(game.getGameState().trim());

        if (isStarted && isLastPlayer && !isFinished) {
            if (!isFinished) {
                // Expulsar a todos y eliminar el juego
                Map<String, Object> wsMsg = new HashMap<>();
                wsMsg.put("type", "player_kicked");
                wsMsg.put("players", new ArrayList<>());
                wsMsg.put("kickedPlayer", "all");
                wsMsg.put("message", "Too few players..., redirecting...");

                messagingTemplate.convertAndSend("/topic/" + code, wsMsg);

                entityManager.remove(entityManager.contains(game) ? game : entityManager.merge(game));
                return Map.of("status", "left");
            }
        }

        // Actualizar internalState en la base de datos
        game.setInternalState(objectMapper.writeValueAsString(state));
        entityManager.merge(game);

        // Enviar mensaje WebSocket
        Map<String, Object> wsMsg = new HashMap<>();
        wsMsg.put("type", "player_kicked");
        wsMsg.put("kickedPlayer", userId);
        wsMsg.put("players", players);

        messagingTemplate.convertAndSend("/topic/" + code, wsMsg);

        // Respuesta HTTP
        return Map.of("status", "kicked", "kickedPlayer", userId, "players", players);
    }

    // El jugador se marcha de la partida y emite un "update" por WebSocket
    @PostMapping("/{code}/leave")
    @ResponseBody
    @Transactional
    public Map<String, Object> leave(@PathVariable String code, HttpSession session) throws JsonProcessingException {
        // tomamos al usuario
        User uSession = (User) session.getAttribute("u");
        if (uSession == null) {
            throw new RuntimeException("No user in session");
        }
        final User u = entityManager.find(User.class, uSession.getId());

        // Recuperar el juego
        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> state = new HashMap<>();

        // Leer el estado interno
        String internalState = game.getInternalState();
        if (internalState != null && !internalState.isBlank()) {
            state = mapper.readValue(internalState, new TypeReference<Map<String, Object>>() {
            });
        }

        // Lista de jugadores en el estado interno
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.getOrDefault("players",
                new ArrayList<>());

        boolean isHost = u.getId() == game.getHost().getId();
        boolean willBeLastPlayer = (players.size() - 1) <= 1;
        boolean isStarted = "STARTED".equalsIgnoreCase(game.getGameState());
        boolean isFinished = "FINISHED".equalsIgnoreCase(game.getGameState().trim());

        if (isHost || (isStarted && willBeLastPlayer)) {
            // Host se va o solo quedará una persona
            if (!isFinished) {
                // Expulsar a todos y eliminar el juego
                Map<String, Object> wsMsg = new HashMap<>();
                wsMsg.put("type", "player_kicked");
                wsMsg.put("players", new ArrayList<>());
                wsMsg.put("kickedPlayer", "all");
                wsMsg.put("message", "Host has left the game, redirecting...");

                messagingTemplate.convertAndSend("/topic/" + code, wsMsg);

                entityManager.remove(entityManager.contains(game) ? game : entityManager.merge(game));
                return Map.of("status", "left");
            }
        } else {
            // Jugador se retira sin ser host ni el penúltimo
            boolean removed = players.removeIf(p -> u.getId() == (((Number) p.get("id")).longValue()));
            Map<Integer, Long> turnOrder = (Map<Integer, Long>) state.get("turnOrder");

            if (turnOrder != null) {
                // 1. eliminar el usuario del turnOrder
                turnOrder.entrySet().removeIf(e -> {
                    Object val = e.getValue();
                    long id = (val instanceof Number) ? ((Number) val).longValue() : 0L;
                    return id == u.getId();
                });

                // 2. reindexar
                Map<Integer, Long> newTurnOrder = new LinkedHashMap<>();
                int index = 1;
                for (Object val : turnOrder.values()) {
                    long id = (val instanceof Number) ? ((Number) val).longValue() : 0L;
                    newTurnOrder.put(index++, id);
                }
                state.put("turnOrder", newTurnOrder);
            }

            if (removed) {
                state.put("players", players);
                // Guardar de nuevo en internalState
                game.setInternalState(mapper.writeValueAsString(state));
                entityManager.merge(game);

                Map<String, Object> wsMsg = new HashMap<>();
                wsMsg.put("type", "update");
                wsMsg.put("players", players);

                messagingTemplate.convertAndSend("/topic/" + code, wsMsg);
            }
        }

        return Map.of("status", "left");
    }

    // Devuelve la lista actual de jugadores en la partida usando internalState
    @GetMapping("/{code}/players")
    @ResponseBody
    public Map<String, Object> getPlayersAndState(@PathVariable String code) throws JsonProcessingException {
        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        ObjectMapper mapper = new ObjectMapper();
        // Parseamos el internalState completo de la base de datos
        Map<String, Object> state = mapper.readValue(game.getInternalState(), new TypeReference<Map<String, Object>>() {
        });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");

        // Devolvemos tanto la lista suelta (por compatibilidad) como el gameState
        // completo
        return Map.of(
                "players", players != null ? players : List.of(),
                "gameState", state);
    }

    // Maneja el lanzamiento del dado
    @PostMapping("/{code}/roll-dice")
    @ResponseBody
    @Transactional
    public Map<String, Object> rollDice(@PathVariable String code, HttpSession session)
            throws JsonProcessingException {

        // obtenemos al usuario actual y la partida
        User u = (User) session.getAttribute("u");
        if (u == null)
            return Map.of("status", "error", "message", "No autenticado");
        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        // si la partida no ha comenzado no hacer nada
        if (!"STARTED".equalsIgnoreCase(game.getGameState())) {
            return Map.of("status", "error", "message", "La partida no está activa");
        }

        // obtenemos el internalState de la partida
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> state = mapper.readValue(game.getInternalState(), new TypeReference<Map<String, Object>>() {
        });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");
        @SuppressWarnings("unchecked")
        Map<String, Object> turnInfo = (Map<String, Object>) state.get("turnInfo");
        // si ya hay otra pregunta pendiente no hacemos anda
        if ("QUESTION_PENDING".equalsIgnoreCase((String) turnInfo.get("subState"))) {
            return Map.of("status", "error", "message", "Hay una pregunta pendiente");
        }

        // solo puede tirar el dado el jugador que tiene el turno actualmente
        long currentTurnPlayerId = ((Number) state.get("currentTurnPlayerId")).longValue();
        if (u.getId() != currentTurnPlayerId) {
            return Map.of("status", "error", "message", "No es tu turno");
        }

        // Tirar dado, mover jugador
        int diceRoll = random.nextInt(6) + 1;
        state.put("lastDiceRoll", diceRoll);

        int targetPos = 0;
        for (Map<String, Object> p : players) {
            if (((Number) p.get("id")).longValue() == currentTurnPlayerId) {
                int currentPos = ((Number) p.getOrDefault("boardPosition", 0)).intValue();
                targetPos = currentPos + diceRoll;
                if (targetPos >= 65) {
                    // si un jugador llega a la meta se acaba la partida
                    targetPos = 65;
                    manejarFinDePartida(game, state, players);
                }
                p.put("boardPosition", targetPos);
                break;
            }
        }

        // actualizamos las posiciones respecto a los otros jugadores en la partida
        if (players != null && !players.isEmpty()) {
            // Creamos una copia de la lista para ordenarla sin romper el orden original de
            // almacenamiento
            List<Map<String, Object>> sortedPlayers = new ArrayList<>(players);

            // Ordenamos de mayor a menor según su 'boardPosition'
            sortedPlayers.sort((p1, p2) -> {
                int pos1 = ((Number) p1.getOrDefault("boardPosition", 0)).intValue();
                int pos2 = ((Number) p2.getOrDefault("boardPosition", 0)).intValue();
                return Integer.compare(pos2, pos1);
            });

            // Asignamos las posiciones de la carrera
            int currentRank = 1;
            int lastPos = -1;

            for (int i = 0; i < sortedPlayers.size(); i++) {
                Map<String, Object> p = sortedPlayers.get(i);
                int pos = ((Number) p.getOrDefault("boardPosition", 0)).intValue();

                // Si la posición de este jugador es menor que la del anterior, el ranking
                // avanza
                if (i > 0 && pos < lastPos) {
                    currentRank = i + 1;
                }

                p.put("gamePosition", currentRank);
                lastPos = pos;
            }
        }

        // Control del Streak y Obtención de la Pregunta según Casilla
        int currentStreak = ((Number) turnInfo.getOrDefault("currentStreak", 1)).intValue();
        QuestionDataPublicDTO publicQuestion = null;

        // si la partida no ha terminado y todavía no se han alcanzado el límte
        // de turnos, pedimos la pregunta a la API
        if (!"FINISHED".equalsIgnoreCase(game.getGameState()) && currentStreak < 3 && targetPos >= 1
                && targetPos <= 64) {

            // recuperamos las categorías y construimos la url en base a la categoría de la
            // casilla y la dificultad de la partida
            Set<TriviaCategory> categories = game.getCategories();
            String categoryUrlParam = "";
            if (categories != null && !categories.isEmpty()) {
                List<TriviaCategory> categoriesList = new ArrayList<>(categories);
                int categoryIndex = (targetPos - 1) % categoriesList.size();
                TriviaCategory chosenCategory = categoriesList.get(categoryIndex);
                categoryUrlParam = "&category=" + chosenCategory.getId();
            }
            String url = "https://opentdb.com/api.php?amount=1" + categoryUrlParam + "&difficulty="
                    + game.getDifficulty().toLowerCase();

            try {
                // hacemos la petición a la API
                RestTemplate rest = new RestTemplate();
                Map<String, Object> apiRes = rest.getForObject(url, Map.class);
                List<Map<String, Object>> results = (List<Map<String, Object>>) apiRes.get("results");

                if (results != null && !results.isEmpty()) {
                    // como en la partida singleplayer creamos dos arrays, uno con la información
                    // privada de la pregunta
                    // y otro con la información para el cliente. El primero lo guardamos en session
                    // y el segundo lo devolvemos.
                    Map<String, Object> q = results.get(0);
                    String correct = (String) q.get("correct_answer");

                    List<String> answers = new ArrayList<>();
                    answers.add(correct);
                    answers.addAll((List<String>) q.get("incorrect_answers"));
                    Collections.shuffle(answers);

                    QuestionDataPrivateDTO privateQuestion = new QuestionDataPrivateDTO(0, (String) q.get("question"),
                            answers, correct);
                    session.setAttribute("activeMultiplayerQuestion", privateQuestion);
                    publicQuestion = new QuestionDataPublicDTO(0, (String) q.get("question"), answers);
                    turnInfo.put("subState", "QUESTION_PENDING");
                }
            } catch (Exception ex) {
                System.out.println("Error al invocar API externa: " + ex.getMessage());
                // si la api da error subimos el currentstreak para que sea el turno del
                // siguiente jugador
                // esto se podría manejar mejor haciendo que se tenga que volver a tirar el
                // dado, pero hemos decidido
                // mantenerlo para poder hacer partidas enteras sin conexión a internet (como
                // durante el examen)
                currentStreak = 3;
            }
        } else if (currentStreak >= 3) {
            turnInfo.put("subState", "NORMAL");
        }

        // si no quedan preguntas pendientes y la partida no ha acabado, pasamos al
        // siguiente turno
        if (!"QUESTION_PENDING".equalsIgnoreCase((String) turnInfo.get("subState"))
                && !"FINISHED".equalsIgnoreCase(game.getGameState())) {
            avanzarSiguienteTurnoEstructural(state);
        }

        if (publicQuestion != null) {
            // Guardamos un mapa con los datos públicos indispensables para reconstruir el
            // modal
            Map<String, Object> questionMap = new HashMap<>();
            questionMap.put("question", publicQuestion.getQuestion());
            questionMap.put("answers", publicQuestion.getAnswers());
            // Inicializamos banderas por si recargan a mitad del proceso
            questionMap.put("answered", false);
            questionMap.put("selectedAnswer", "");
            state.put("activeQuestion", questionMap);
        }

        // Ahora que 'state' ya incluye 'activeQuestion', lo convertimos en JSON y lo
        // guardamos
        game.setInternalState(mapper.writeValueAsString(state));
        entityManager.merge(game);

        messagingTemplate.convertAndSend("/topic/" + code,
                Map.of("type", "question", "players", players, "gameState", state));

        return Map.of("status", "success");
    }

    // Helper interno para mover el puntero de turnos
    private void avanzarSiguienteTurnoEstructural(Map<String, Object> state) {
        @SuppressWarnings("unchecked")
        Map<String, Object> turnOrder = (Map<String, Object>) state.get("turnOrder");
        @SuppressWarnings("unchecked")
        Map<String, Object> turnInfo = (Map<String, Object>) state.get("turnInfo");

        Number currentTurnIdxNum = (Number) turnInfo.get("currentTurn");
        int nextTurnIdx = currentTurnIdxNum.intValue() + 1;

        if (!turnOrder.containsKey(String.valueOf(nextTurnIdx))) {
            nextTurnIdx = 1;
        }
        turnInfo.put("currentTurn", nextTurnIdx);
        turnInfo.put("currentStreak", 1);

        Object nextPlayerIdObj = turnOrder.get(String.valueOf(nextTurnIdx));
        state.put("currentTurnPlayerId", ((Number) nextPlayerIdObj).longValue());

        turnInfo.put("subState", "NORMAL");
        state.remove("activeQuestion");
    }

    // Helper interno para manejar el fin de la partida
    private void manejarFinDePartida(Game game, Map<String, Object> state, List<Map<String, Object>> players)
            throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();

        // 1. Cambiamos el estado global de la partida
        game.setGameState("FINISHED");

        // 2. Procesamos y persistimos los puntos de cada usuario en la Base de Datos
        if (players != null) {
            for (Map<String, Object> p : players) {
                long userId = ((Number) p.get("id")).longValue();
                int pointsInGame = ((Number) p.getOrDefault("points", 0)).intValue();
                int positionInGame = ((Number) p.getOrDefault("gamePosition", 4)).intValue();

                // Calculamos el bonus según la posición de la carrera
                int bonus = 0;
                switch (positionInGame) {
                    case 1:
                        bonus = 50;
                        break;
                    case 2:
                        bonus = 25;
                        break;
                    case 3:
                        bonus = 10;
                        break;
                    default:
                        bonus = 0;
                        break;
                }

                int totalPuntosAñadidos = pointsInGame + bonus;

                // Buscamos al usuario real en la base de datos para actualizar su perfil de
                // forma persistente
                User userEntity = entityManager.find(User.class, userId);
                if (userEntity != null) {
                    // Asumiendo que tu entidad User tiene un campo 'score' o 'points' total e
                    // histórico
                    int currentGlobalScore = userEntity.getTotalPoints();
                    userEntity.setTotalPoints(currentGlobalScore + totalPuntosAñadidos);
                    entityManager.merge(userEntity); // Persistimos en la BD
                }

                // Guardamos también el desglose en el mapa temporal del jugador para que el
                // front pueda mostrarlo
                p.put("bonusPoints", bonus);
                p.put("totalEarned", totalPuntosAñadidos);
            }

            // 3. Ordenamos la lista de jugadores por su posición final antes de mandarla al
            // frontend
            // actualizamos las posiciones respecto a los otros jugadores en la partida
            if (players != null && !players.isEmpty()) {
                // Creamos una copia de la lista para ordenarla sin romper el orden original de
                // almacenamiento
                List<Map<String, Object>> sortedPlayers = new ArrayList<>(players);

                // Ordenamos de mayor a menor según su 'boardPosition'
                sortedPlayers.sort((p1, p2) -> {
                    int pos1 = ((Number) p1.getOrDefault("boardPosition", 0)).intValue();
                    int pos2 = ((Number) p2.getOrDefault("boardPosition", 0)).intValue();
                    return Integer.compare(pos2, pos1);
                });

                // Asignamos las posiciones de la carrera
                int currentRank = 1;
                int lastPos = -1;

                for (int i = 0; i < sortedPlayers.size(); i++) {
                    Map<String, Object> p = sortedPlayers.get(i);
                    int pos = ((Number) p.getOrDefault("boardPosition", 0)).intValue();

                    // Si la posición de este jugador es menor que la del anterior, el ranking
                    // avanza
                    if (i > 0 && pos < lastPos) {
                        currentRank = i + 1;
                    }

                    p.put("gamePosition", currentRank);
                    lastPos = pos;
                }
            }
            players.sort((p1, p2) -> {
                int r1 = ((Number) p1.getOrDefault("gamePosition", 4)).intValue();
                int r2 = ((Number) p2.getOrDefault("gamePosition", 4)).intValue();
                return Integer.compare(r1, r2);
            });
        }

        // 4. Guardamos el estado definitivo en el internalState de la partida
        game.setInternalState(mapper.writeValueAsString(state));
        entityManager.merge(game);

        // 5. Emitimos el mensaje WebSocket exclusivo de fin de partida con el podio
        // ordenado
        messagingTemplate.convertAndSend("/topic/" + game.getCode(),
                Map.of("type", "game_finished", "podium", players));
    }

    // Maneja la selección de una respuesta para determinar si es correcta o no
    @PostMapping("/{code}/submit-answer")
    @ResponseBody
    @Transactional
    public Map<String, Object> submitAnswer(@PathVariable String code,
            @RequestBody Map<String, String> payload,
            HttpSession session) throws JsonProcessingException {

        // Obtenemos el usuario
        User u = (User) session.getAttribute("u");
        if (u == null)
            return Map.of("status", "error", "message", "No autenticado");

        // Tomamos la partida de la base de datos
        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> state = mapper.readValue(game.getInternalState(), new TypeReference<Map<String, Object>>() {
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> turnInfo = (Map<String, Object>) state.get("turnInfo");

        // Comprobamos que el juego esté esperando una respuesta
        if (!"QUESTION_PENDING".equalsIgnoreCase((String) turnInfo.get("subState"))) {
            return Map.of("status", "error", "message", "No hay ninguna pregunta activa");
        }

        // Comprobar que la respuesta ha sido dada por el dueño del turno
        long currentTurnPlayerId = ((Number) state.get("currentTurnPlayerId")).longValue();
        if (u.getId() != currentTurnPlayerId) {
            return Map.of("status", "error", "message", "No es tu turno de responder");
        }

        // Comparamos la respuesta recibida con la información privada de la pregunta
        QuestionDataPrivateDTO privateQuestion = (QuestionDataPrivateDTO) session
                .getAttribute("activeMultiplayerQuestion");
        if (privateQuestion == null) {
            return Map.of("status", "error", "message", "No se encontró la pregunta en tu sesión");
        }
        String clientAnswer = payload.get("answer");
        String correctAnswer = privateQuestion.getCorrectAnswer();
        boolean isCorrect = correctAnswer.equals(clientAnswer);

        // Limpiamos la pregunta privada de la sesión
        session.removeAttribute("activeMultiplayerQuestion");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");

        // Recuperamos el mapa de la pregunta que guardamos en rollDice e incluimos la
        // nueva información relevante
        @SuppressWarnings("unchecked")
        Map<String, Object> activeQuestion = (Map<String, Object>) state.get("activeQuestion");

        if (activeQuestion != null) {
            activeQuestion.put("answered", true);
            activeQuestion.put("selectedAnswer", clientAnswer);
            activeQuestion.put("correctAnswer", correctAnswer);
        }

        // Control de racha, puntos y cambio de estado
        int currentStreak = ((Number) turnInfo.getOrDefault("currentStreak", 1)).intValue();
        if (isCorrect) {
            // Acierto: Sumamos puntos
            for (Map<String, Object> p : players) {
                if (((Number) p.get("id")).longValue() == currentTurnPlayerId) {
                    int currentPoints = ((Number) p.getOrDefault("points", 0)).intValue();
                    p.put("points", currentPoints + 10);
                    break;
                }
            }
            // Aumentamos racha
            currentStreak++;
            turnInfo.put("currentStreak", currentStreak);
            if (currentStreak >= 3) {
                turnInfo.put("subState", "NORMAL");
            }
        } else {
            // Fallo: Rompemos racha y el subState vuelve a NORMAL para obligar a cambiar el
            // turno
            turnInfo.put("currentStreak", 1);
            turnInfo.put("subState", "NORMAL");
        }

        // Guardamos el estado interno en la base de datos
        game.setInternalState(mapper.writeValueAsString(state));
        entityManager.merge(game);

        // Informamos por websocket de un "update" (re-renderización del juego)
        messagingTemplate.convertAndSend("/topic/" + code,
                Map.of("type", "update", "players", players, "gameState", state));

        return Map.of("status", "success", "correct", isCorrect);
    }

    // Maneja el cerrado del modal de una pregunta
    @PostMapping("/{code}/close-question-modal")
    @ResponseBody
    @Transactional
    public Map<String, Object> closeQuestionModal(@PathVariable String code, HttpSession session)
            throws JsonProcessingException {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return Map.of("status", "error", "message", "No autenticado");

        Game game = entityManager.createNamedQuery("Game.byCode", Game.class).setParameter("code", code)
                .getSingleResult();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> state = mapper.readValue(game.getInternalState(), new TypeReference<Map<String, Object>>() {
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> turnInfo = (Map<String, Object>) state.get("turnInfo");

        // Si el subState es NORMAL (porque falló o completó racha de 3), avanzamos
        // el turno
        if ("NORMAL".equalsIgnoreCase((String) turnInfo.get("subState"))
                && !"FINISHED".equalsIgnoreCase(game.getGameState())) {
            avanzarSiguienteTurnoEstructural(state);
        } else {
            // Si el subState seguía siendo QUESTION_PENDING, significa que acertó y tiene
            // racha < 3.
            // Cambiamos el subState a NORMAL para que pueda volver a tirar el dado en su
            // nuevo turno.
            turnInfo.put("subState", "NORMAL");
        }

        // Quitamos la pregunta del JSON de la BD y de la sesión
        // HTTP
        state.remove("activeQuestion");
        session.removeAttribute("activeMultiplayerQuestion");

        game.setInternalState(mapper.writeValueAsString(state));
        entityManager.merge(game);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");

        // Enviamos mensaje websocket a todos los jugadores para que el modal se cierre
        messagingTemplate.convertAndSend("/topic/" + code,
                Map.of("type", "update", "players", players, "gameState", state));

        return Map.of("status", "success");
    }

    /*
     * ==============
     * LOBBY
     * ================
     */

    // Muestra lobby.html. Determina si el usuario es el host
    @GetMapping("/lobby/{code}")
    public String showLobby(@PathVariable String code, Model model, HttpSession session) {
        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();
        model.addAttribute("game", game);

        if (game == null || !game.getGameState().equals("WAITING"))
            return "redirect:/";

        User u = (User) session.getAttribute("u");
        boolean isHost = (u != null && game.getHost().getId() == u.getId());
        model.addAttribute("isHost", isHost);
        model.addAttribute("u", u);

        session.setAttribute("topics", code);
        model.addAttribute("topics", code);

        return "lobby";
    }

    // Valida el código de partida y redirige al lobby
    @PostMapping("/join")
    public String joinGame(@RequestParam("gameCode") String gameCode,
            Model model, HttpSession session) {
        try {
            Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                    .setParameter("code", gameCode)
                    .getSingleResult();

            // Comprobamos que el estado no sea "WAITING"
            if (!"WAITING".equalsIgnoreCase(game.getGameState().trim())) {
                model.addAttribute("error", "Game has already started: " + gameCode);
                return "join_game";
            }

            return "redirect:/game/lobby/" + gameCode;

        } catch (Exception e) {
            model.addAttribute("error", "Invalid game code: " + gameCode);
            return "join_game";
        }
    }

    @PostMapping("/{code}/join")
    @ResponseBody
    @Transactional
    public Map<String, Object> join(@PathVariable String code, HttpSession session) {
        User uSession = (User) session.getAttribute("u");
        if (uSession == null) {
            throw new RuntimeException("No user in session");
        }
        User u = entityManager.find(User.class, uSession.getId());

        // Recuperamos el juego de la base de datos
        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> state = new HashMap<>();

        String internalState = game.getInternalState();

        if (internalState == null || internalState.trim().isEmpty()) {
            // Inicialización por defecto
            state.put("players", new ArrayList<Map<String, Object>>());
            state.put("turnOrder", new LinkedHashMap<Integer, Long>());
            state.put("lastDiceRoll", 0);
            state.put("turnInfo", Map.of("currentStreak", 0, "questionPending", false, "currentTurn", 0));
        } else {
            try {
                // Limpiamos comillas extra si vienen de una cadena serializada
                if (internalState.startsWith("\"") && internalState.endsWith("\"")) {
                    internalState = internalState.substring(1, internalState.length() - 1)
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                }
                state = objectMapper.readValue(internalState, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                // Fallback seguro
                state.put("players", new ArrayList<Map<String, Object>>());
                state.put("turnOrder", new LinkedHashMap<Integer, Long>());
                state.put("lastDiceRoll", 0);
                state.put("turnInfo", Map.of("currentStreak", 0, "questionPending", false, "currentTurn", 0));
            }
        }

        // Obtenemos la lista de jugadores en el estado interno
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");

        // Buscamos si el jugador ya está
        Map<String, Object> existingPlayer = players.stream()
                .filter(p -> {
                    Object idObj = p.get("id");
                    if (idObj instanceof Number) {
                        return ((Number) idObj).longValue() == u.getId();
                    }
                    return false;
                }).findFirst().orElse(null);

        if (existingPlayer != null)

        {
            // Ya estaba: actualizamos datos que puedan cambiar
            existingPlayer.put("username", u.getUsername());
        } else {
            // Nuevo jugador: comprobamos límite antes de añadir
            if (players.size() >= 4) {
                return Map.of("status", "full", "message", "El juego está lleno (máximo 4 jugadores)");
            }

            Map<String, Object> newPlayer = new HashMap<>();
            newPlayer.put("id", u.getId());
            newPlayer.put("username", u.getUsername());
            newPlayer.put("boardPosition", 0);
            newPlayer.put("gamePosition", players.size() + 1);

            // Actualizamos el orden de turnos si aún no hay ninguno
            // Guardamos cambios en el estado interno
            Map<Integer, Long> turnOrder = (Map<Integer, Long>) state.getOrDefault("turnOrder", new LinkedHashMap<>());

            boolean alreadyInTurnOrder = turnOrder.values().contains(u.getId());

            if (!alreadyInTurnOrder) {
                turnOrder.put(turnOrder.size() + 1, u.getId());
            }

            state.put("players", players);
            state.put("turnOrder", turnOrder);

            players.add(newPlayer);
            try {
                String jsonState = objectMapper.writeValueAsString(state);
                game.setInternalState(jsonState);
            } catch (Exception e) {
            }
        }

        try {
            entityManager.merge(game);
            entityManager.flush();
        } catch (Exception e) {
            throw new RuntimeException("Error writing internalState JSON", e);
        }

        // Enviamos mensaje WebSocket para actualizar lobby
        Map<String, Object> wsMsg = new HashMap<>();
        wsMsg.put("type", "update");
        wsMsg.put("players", players);

        messagingTemplate.convertAndSend("/topic/" + code, wsMsg);

        return Map.of("status", "joined", "players", players, "username", u.getUsername());
    }

    // Emite "game_start" por WebSocket
    // con la URL de redirección a la pantalla de juego
    private final Random random = new Random();

    @PostMapping("/{code}/start")
    @ResponseBody
    @Transactional
    public Map<String, Object> startGame(@PathVariable String code, HttpSession session)
            throws JsonProcessingException {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return Map.of("status", "error", "message", "No autenticado");

        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        if (game.getHost().getId() != u.getId()) {
            return Map.of("status", "error", "message", "Solo el host puede iniciar la partida");
        }

        if (!"WAITING".equalsIgnoreCase(game.getGameState())) {
            return Map.of("status", "error", "message", "La partida ya ha comenzado");
        }

        game.setGameState("STARTED");

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> state = mapper.readValue(game.getInternalState(), new TypeReference<Map<String, Object>>() {
        });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");

        if (players == null || players.size() < 2) {
            return Map.of("status", "error", "message", "Se necesitan al menos 2 jugadores");
        }

        // 1. Crear el orden de turnos inicial
        Map<String, Long> turnOrder = new LinkedHashMap<>();
        int idx = 1;
        for (Map<String, Object> p : players) {
            turnOrder.put(String.valueOf(idx++), ((Number) p.get("id")).longValue());
            p.put("boardPosition", 0);
        }
        state.put("turnOrder", turnOrder);

        // 2. Definir el primer jugador (Turno 1)
        Long firstPlayerId = turnOrder.get("1");
        state.put("currentTurnPlayerId", firstPlayerId);

        // Inicializar turnInfo. Apunta a la clave de orden "1"
        Map<String, Object> turnInfo = new HashMap<>();
        turnInfo.put("currentTurn", 1);
        turnInfo.put("currentStreak", 1);
        state.put("turnInfo", turnInfo);

        game.setInternalState(mapper.writeValueAsString(state));
        entityManager.merge(game);

        // Notificar inicio de juego
        messagingTemplate.convertAndSend("/topic/" + code, Map.of("type", "game_start"));

        // Emitir un primer mensaje de "update" inmediato con el estado para que se
        // inicialice el botón al redirigir
        messagingTemplate.convertAndSend("/topic/" + code, Map.of(
                "type", "update",
                "players", players,
                "gameState", state));

        return Map.of("status", "success");
    }

    /*
     * ==============
     * CHAT
     * ================
     */

    // Devuelve todos los mensajes de la partida como JSON
    @GetMapping(path = "/{code}/msg", produces = "application/json")
    @Transactional // para no recibir resultados inconsistentes
    @ResponseBody // para indicar que no devuelve vista, sino un objeto (jsonizado)
    public List<Message.Transfer> retrieveMessages(HttpSession session, @PathVariable String code) {
        // long userId = ((User) session.getAttribute("u")).getId();
        Game g = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();
        return g.getMessages().stream()
                .map(Message::toTransfer)
                .collect(Collectors.toList());
    }

    // Guarda un mensaje en BD y lo emite por WebSocket a /topic/{code}
    @PostMapping("/{code}/msg")
    @ResponseBody
    @Transactional
    public String postMsg(@PathVariable String code,
            @RequestBody JsonNode o, Model model, HttpSession session)
            throws JsonProcessingException {

        String text = o.get("message").asText();
        User u = (User) session.getAttribute("u");
        u = entityManager.find(User.class, u.getId());
        Game g = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        // 1. Crear la entidad del mensaje
        Message m = new Message();
        m.setSender(u);
        m.setGame(g);
        m.setDateSent(LocalDateTime.now());

        ObjectMapper mapper = new ObjectMapper();

        // 2. Interceptamos si es una alerta para administradores
        if (text.trim().startsWith("@admin")) {
            // Quitamos la etiqueta "@admin" del inicio para limpiar el texto de la alerta
            String cleanText = text.replaceFirst("(?i)^@admin\\s*", "").trim();

            m.setText("[ALERTA JUEGO " + code + "] " + cleanText);
            m.setAdminOnly(true);
            entityManager.persist(m);
            entityManager.flush();

            String jsonAlert = mapper.writeValueAsString(m.toTransfer());
            log.info("Alerta de moderación enviada por {} en la partida {}: {}", u.getUsername(), code, cleanText);

            // Emitimos al canal privado global de administradores
            messagingTemplate.convertAndSend("/topic/admin", jsonAlert);

            return "{\"result\": \"admin alert sent.\"}";
        }

        // 3. Flujo normal si NO es un mensaje para el admin
        m.setText(text);
        m.setAdminOnly(false);
        entityManager.persist(m);
        entityManager.flush();

        String json = mapper.writeValueAsString(m.toTransfer());
        log.info("Sending a message to {} with contents '{}'", code, json);
        messagingTemplate.convertAndSend("/topic/" + code, json);

        return "{\"result\": \"message sent.\"}";
    }

    // helper que genera códigos de partida solo con caracteres alfanuméricos
    private static String generateGameCode(int length) {
        final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}