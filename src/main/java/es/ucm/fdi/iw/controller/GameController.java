package es.ucm.fdi.iw.controller;

import java.net.Authenticator;
import java.net.http.WebSocket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

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
import com.fasterxml.jackson.databind.ObjectMapper;

import es.ucm.fdi.iw.controller.DTOs.AnswerReqDTO;
import es.ucm.fdi.iw.controller.DTOs.AnswerResDTO;
import es.ucm.fdi.iw.controller.DTOs.GameSetupDTO;
import es.ucm.fdi.iw.controller.DTOs.QuestionDataPrivateDTO;
import es.ucm.fdi.iw.controller.DTOs.QuestionDataPublicDTO;
import es.ucm.fdi.iw.model.Game;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;


/**
* CONTROLLER DE PARTIDAS 

* Gestiona tanto las partidas individuales como
* las multijugador y el lobby en tiempo real vía WebSocket
* Ruta base: /game/**

* Limitar el número de jugadores en el lobby: en joinLobby(), comprobar
* players.size() antes de añadir y devolver error si se superó el límite.
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




    // SINGLE PLAYER

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




    // MULTI PLAYER

    // Crea una nueva partida Game en la BD con código único de 4 chars,
    // asigna al usuario actual como host, y redirige al lobby
    @PostMapping("/multi_game")
    @Transactional
    public String createMultiGame(HttpSession session) {

        User u = (User)session.getAttribute("u");
        u = entityManager.find(User.class, u.getId());

        Game game=new Game();
        game.setCategories(null);
        game.setCode(UserController.generateRandomBase64Token(4));
        game.setDifficulty("Easy");
        game.setGameState("{\"x\" = 42;}");
        game.setHost(u);
        game.setInternalState("{\"x\" = 42;}");
        game.setNumPlayers(1);
        game.setNumQuestions(5);

        u.getPartidasCreadas().add(game);

        entityManager.persist(game);
        entityManager.flush();

        return "redirect:/game/lobby/" + game.getCode();
    }

    // Carga la partida por código y renderiza multi_game.html
    @GetMapping("/multi_game/{code}")
    public String multiGame(@PathVariable String code, Model model, HttpSession session) {
        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
            .setParameter("code", code)
            .getSingleResult();

        model.addAttribute("game", game);
        session.setAttribute("topics", code);

        return "multi_game";
    }




    // CHAT (AJAX)

    // Devuelve todos los mensajes de la partida como JSON
    @GetMapping(path = "/{code}/msg", produces = "application/json")
    @Transactional // para no recibir resultados inconsistentes
    @ResponseBody // para indicar que no devuelve vista, sino un objeto (jsonizado)
    public List<Message.Transfer> retrieveMessages(HttpSession session, @PathVariable String code) {
        //long userId = ((User) session.getAttribute("u")).getId();
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
   * @param o  JSON-ized message, similar to {"message": "text goes here"}
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
        User u = (User)session.getAttribute("u");
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




    // LOBBY

    private static final ConcurrentHashMap<String, List<String>> lobbyPlayers = new ConcurrentHashMap<>();

    // Muestra lobby.html. Determina si el usuario es el host
    @GetMapping("/lobby/{code}")
    public String showLobby(@PathVariable String code, Model model, HttpSession session) {
        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
            .setParameter("code", code)
            .getSingleResult();
        model.addAttribute("game", game);

        User u = (User) session.getAttribute("u");
        boolean isHost = (u != null && game.getHost().getId() == u.getId());
        model.addAttribute("isHost", isHost);

        session.setAttribute("topics", code);

        return "lobby";
    }

    // Valida el código de partida y redirige al lobby
    @PostMapping("/join")
    public String joinGame(@RequestParam("gameCode") String gameCode,
            Model model, HttpSession session) {
        try {
            entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", gameCode)
                .getSingleResult();
            return "redirect:/game/lobby/" + gameCode;
        } catch (Exception e) {
            model.addAttribute("error", "Código de partida inválido: " + gameCode);
            return "join_game";
        }
    }

    // Añade al usuario a la lista en memoria (lobbyPlayers) y emite
    // un mensaje WebSocket de tipo "lobby_update" a /topic/{code}
    @PostMapping("/{code}/lobby/join")
    @ResponseBody
    @Transactional
    public Map<String, Object> joinLobby(@PathVariable String code, HttpSession session) {
        User u = (User) session.getAttribute("u");
        String username = (u != null) ? u.getUsername() : "Guest";

        lobbyPlayers.computeIfAbsent(code, k -> new ArrayList<>());
        List<String> players = lobbyPlayers.get(code);
        if (!players.contains(username)) {
            players.add(username);
        }

        Map<String, Object> wsMsg = new HashMap<>();
        wsMsg.put("type", "lobby_update");
        wsMsg.put("players", players);

        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(wsMsg);
            messagingTemplate.convertAndSend("/topic/" + code, json);
        } catch (Exception e) {
            log.error("Error broadcasting lobby leave", e);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "joined");
        resp.put("players", players);
        resp.put("username", username);
        return resp;
    }

    // Elimina al usuario del lobby y emite "lobby_update"
    @PostMapping("/{code}/lobby/leave")
    @ResponseBody
    public Map<String, Object> leaveLobby(@PathVariable String code, HttpSession session) {
        User u = (User) session.getAttribute("u");
        String username = (u != null) ? u.getUsername() : "Guest";

        List<String> players = lobbyPlayers.getOrDefault(code, new ArrayList<>());
        players.remove(username);

        Map<String, Object> wsMsg = new HashMap<>();
        wsMsg.put("type", "lobby_update");
        wsMsg.put("players", players);

        try {
            String json = new ObjectMapper().writeValueAsString(wsMsg);
            messagingTemplate.convertAndSend("/topic/" + code, json);
        } catch (Exception e) {
            log.error("Error broadcasting lobby leave", e);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "left");
        resp.put("players", players);
        return resp;
    }

    // Devuelve la lista actual de jugadores en el lobby
    @GetMapping(path = "/{code}/lobby/players", produces = "application/json")
    @ResponseBody
    public Map<String, Object> getLobbyPlayers(@PathVariable String code) {
        List<String> players = lobbyPlayers.getOrDefault(code, new ArrayList<>());
        Map<String, Object> resp = new HashMap<>();
        resp.put("players", players);
        return resp;
    }

    // Solo el host puede llamar esto. Emite "game_start" por WebSocket
    // con la URL de redirección a la pantalla de juego
    @PostMapping("/{code}/lobby/start")
    @ResponseBody
    @Transactional
    public Map<String, Object> startLobbyGame(@PathVariable String code, HttpSession session) {
        User u = (User) session.getAttribute("u");

        Game game = entityManager.createNamedQuery("Game.byCode", Game.class)
                .setParameter("code", code)
                .getSingleResult();

        if (u == null || game.getHost().getId() != u.getId()) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("status", "error");
            errorResp.put("message", "Only the host can start the game");
            return errorResp;
        }

        Map<String, Object> wsMsg = new HashMap<>();
        wsMsg.put("type", "game_start");
        wsMsg.put("redirect", "/game/multi_game/" + code + "/play");

        try {
            String json = new ObjectMapper().writeValueAsString(wsMsg);
            messagingTemplate.convertAndSend("/topic/" + code, json);
        } catch (Exception e) {
            log.error("Error broadcasting game start", e);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "started");
        return resp;
    }
  
}