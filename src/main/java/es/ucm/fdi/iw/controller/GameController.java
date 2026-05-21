package es.ucm.fdi.iw.controller;

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

    /*
     * Llama a OpenTDB con los parámetros de GameSetupDTO, construye la
     * lista de preguntas mezclando respuestas correctas e incorrectas,
     * guarda las preguntas COMPLETAS (con respuesta correcta) en sesión
     * como "questions" (List<QuestionDataPrivateDTO>), y manda al
     * frontend solo QuestionDataPublicDTO (sin la respuesta correcta)
     * Renderiza single_game.html
     */
    @PostMapping("/start_single_game")
    public String startGame(@ModelAttribute GameSetupDTO setup,
            Model model,
            HttpSession session) {

        String url = "https://opentdb.com/api.php?amount=" + setup.getQuestionCount();
        if (setup.getCategory() != null && !setup.getCategory().isEmpty()) {
            url += "&category=" + setup.getCategory();
        }
        if (setup.getDifficulty() != null && !setup.getDifficulty().isEmpty()) {
            url += "&difficulty=" + setup.getDifficulty().toLowerCase();
        }

        RestTemplate rest = new RestTemplate();
        Map<String, Object> response = rest.getForObject(url, Map.class);

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

        List<QuestionDataPrivateDTO> fullQuestions = new ArrayList<>(); // full data stored in session
        List<QuestionDataPublicDTO> publicQuestions = new ArrayList<>(); // safe version for frontend

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

        session.setAttribute("questions", fullQuestions);
        model.addAttribute("questions", publicQuestions);

        return "single_game";
    }

    /*
     * Recibe{questionId, answer} del frontend
     * Comprueba contra las preguntas guardadas en sesión
     * Si es correcta, suma 10 puntos al User en la BD
     * Devuelve {correct, correctAnswer} como JSON
     */
    @PostMapping("/answer")
    @ResponseBody
    @Transactional
    public AnswerResDTO checkAnswer(@RequestBody AnswerReqDTO req, HttpSession session) {
        List<QuestionDataPrivateDTO> questions = (List<QuestionDataPrivateDTO>) session.getAttribute("questions");

        if (questions == null || req.getQuestionId() >= questions.size()) {
            return new AnswerResDTO(false, null);
        }

        QuestionDataPrivateDTO q = questions.get(req.getQuestionId());
        boolean isCorrect = q.getCorrectAnswer().equals(req.getAnswer());

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

        User u = (User) session.getAttribute("u");
        u = entityManager.find(User.class, u.getId());

        Game game = new Game();
        Set<TriviaCategory> cats = (categories == null || categories.isEmpty())
                ? EnumSet.allOf(TriviaCategory.class)
                : categories.stream()
                        .map(TriviaCategory::fromId)
                        .collect(Collectors.toSet());
        game.setCategories(cats);
        game.setCode(UserController.generateGameCode(6));
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

        initialState.put("lastDiceRoll", 0);

        Map<String, Object> turnInfo = new HashMap<>();
        turnInfo.put("currentStreak", 0);
        turnInfo.put("questionPending", false);
        turnInfo.put("currentTurn", 0);
        initialState.put("turnInfo", turnInfo);

        // Convertimos a JSON
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonState = objectMapper.writeValueAsString(initialState);

        game.setInternalState(jsonState);

        entityManager.persist(game);
        entityManager.flush();

        return "redirect:/game/lobby/" + game.getCode();
    }

    // Carga la partida por código y renderiza multi_game.html
    @GetMapping("/multi_game/{code}")
    public String multiGame(@PathVariable String code, Model model, HttpSession session) {
        Game game;
        game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();
        if (game == null)
            return "redirect:/";

        User currentUser = (User) session.getAttribute("u");
        if (currentUser == null) {
            return "redirect:/login";
        }

        // no se carga la vista de partida si esta no está empezada por el host
        if (!("STARTED").equalsIgnoreCase(game.getGameState().trim())) {
            return "redirect:/game/lobby/" + code;
        }

        List<Map<String, String>> categories = game.getCategories()
                .stream()
                .map(c -> Map.of(
                        "key", c.name(),
                        "label", c.getLabel()))
                .toList();

        model.addAttribute("categories", categories);

        model.addAttribute("user", currentUser);
        model.addAttribute("game", game);
        session.setAttribute("topics", game.getCode());

        return "multi_game";
    }

    @GetMapping("/{code}/state")
    @ResponseBody
    public Map<String, Object> getState(@PathVariable String code) throws Exception {

        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> state = mapper.readValue(game.getInternalState(), Map.class);

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

        User u = (User) session.getAttribute("u");
        if (u == null) {
            return Map.of("status", "error", "message", "Usuario no autenticado");
        }

        // Recuperar el juego
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
            // 1. eliminar jugador del turnOrder
            turnOrderRaw.entrySet().removeIf(e -> e.getValue().longValue() == userId);

            // 2. reindexar y convertir todo a Long
            Map<Integer, Long> newTurnOrder = new LinkedHashMap<>();
            int index = 1;
            for (Number n : turnOrderRaw.values()) {
                newTurnOrder.put(index++, n.longValue());
            }

            state.put("turnOrder", newTurnOrder);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");

        // Eliminar al jugador
        boolean removed = players.removeIf(p -> {
            Object idObj = p.get("id");
            if (idObj instanceof Number) {
                return ((Number) idObj).longValue() == userId;
            }
            return false;
        });

        if (!removed)

        {
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
        return Map.of(
                "status", "kicked",
                "kickedPlayer", userId,
                "players", players);
    }

    @PostMapping("/{code}/leave")
    @ResponseBody
    @Transactional
    public Map<String, Object> leave(@PathVariable String code, HttpSession session) throws JsonProcessingException {

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
            // Host se va
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
            // Jugador normal se retira
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

    @PostMapping("/{code}/roll-dice")
    @ResponseBody
    @Transactional
    public Map<String, Object> rollDice(@PathVariable String code, HttpSession session)
            throws JsonProcessingException {

        User u = (User) session.getAttribute("u");
        if (u == null)
            return Map.of("status", "error", "message", "No autenticado");

        // 1. Recuperar juego
        Game game;
        try {
            game = entityManager.createNamedQuery("Game.byCode", Game.class)
                    .setParameter("code", code)
                    .getSingleResult();
        } catch (Exception e) {
            return Map.of("status", "error", "message", "Partida no encontrada");
        }

        if (!"STARTED".equalsIgnoreCase(game.getGameState())) {
            return Map.of("status", "error", "message", "La partida no está activa");
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> state = mapper.readValue(game.getInternalState(), new TypeReference<Map<String, Object>>() {
        });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");
        @SuppressWarnings("unchecked")
        Map<String, Object> turnOrder = (Map<String, Object>) state.get("turnOrder");
        @SuppressWarnings("unchecked")
        Map<String, Object> turnInfo = (Map<String, Object>) state.get("turnInfo");

        // 2. Validación crítica de seguridad: ¿Es realmente el turno del usuario que
        // invoca la ruta?
        long currentTurnPlayerId = ((Number) state.get("currentTurnPlayerId")).longValue();
        if (u.getId() != currentTurnPlayerId) {
            return Map.of("status", "error", "message", "No es tu turno de lanzar el dado");
        }

        // 3. Tirar el dado (1 al 6)
        int diceRoll = random.nextInt(6) + 1;
        state.put("lastDiceRoll", diceRoll);

        // 4. Mover al jugador en la lista
        for (Map<String, Object> p : players) {
            if (((Number) p.get("id")).longValue() == currentTurnPlayerId) {
                int currentPos = ((Number) p.getOrDefault("boardPosition", 0)).intValue();
                int newPos = currentPos + diceRoll;

                if (newPos >= 65) {
                    newPos = 65; // Casilla GOAL
                    game.setGameState("FINISHED");
                }
                p.put("boardPosition", newPos);
                break;
            }
        }

        // 5. Calcular el próximo turno (solo si la partida no ha terminado)
        if (!"FINISHED".equalsIgnoreCase(game.getGameState())) {
            Number currentTurnIdxNum = (Number) turnInfo.get("currentTurn");
            int nextTurnIdx = currentTurnIdxNum.intValue() + 1;

            if (!turnOrder.containsKey(String.valueOf(nextTurnIdx))) {
                nextTurnIdx = 1; // Volver al primer jugador del ciclo
            }
            turnInfo.put("currentTurn", nextTurnIdx);

            // Actualizar el ID del jugador activo para la siguiente ronda
            Object nextPlayerIdObj = turnOrder.get(String.valueOf(nextTurnIdx));
            state.put("currentTurnPlayerId", ((Number) nextPlayerIdObj).longValue());
        }

        // 6. Guardar los cambios
        game.setInternalState(mapper.writeValueAsString(state));
        entityManager.merge(game);

        // 7. Emitir actualización en tiempo real a todos los clientes enganchados
        messagingTemplate.convertAndSend("/topic/" + code, Map.of(
                "type", "update",
                "players", players,
                "gameState", state));

        return Map.of("status", "success", "diceRoll", diceRoll);
    }

    /*
     * ==============
     * CHAT TO DO: MOVER A OTRO CONTROLLER?
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

    /**
     * Posts a message to a game.
     * 
     * @param code of target game (source user is from ID)
     * @param o    JSON-ized message, similar to {"message": "text goes here"}
     * @throws JsonProcessingException
     */

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

        // falta : ¿es u miembro de g?

        // construye mensaje, lo guarda en BD
        Message m = new Message();
        m.setSender(u);
        m.setGame(g);
        m.setDateSent(LocalDateTime.now());
        m.setText(text);
        m.setAdminOnly(false);
        entityManager.persist(m);
        entityManager.flush(); // to get Id before commit

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(m.toTransfer());
        log.info("Sending a message to {} with contents '{}'", code, json);
        messagingTemplate.convertAndSend("/topic/" + code, json);
        return "{\"result\": \"message sent.\"}";
    }
}