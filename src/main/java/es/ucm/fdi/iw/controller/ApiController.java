package es.ucm.fdi.iw.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.ui.Model;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.ucm.fdi.iw.model.Game;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.User;
import io.karatelabs.js.Context;
import io.karatelabs.js.Interpreter;
import io.karatelabs.js.Node;
import io.karatelabs.js.Parser;
import io.karatelabs.js.Source;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
* CONTROLLER REST / API 

* Expone endpoints JSON sin necesidad de autenticación por defecto
* Usa @RestController (= @Controller + @ResponseBody en todos los métodos)
* Ruta base: /api/**
*
*/

/**
 * API, intended for logged-in users.
 *
 * Access to this end-point is NOT authenticated
 * - see SecurityConfig and add per-endpoint authentication as needed
 */

// RestController = all methods annotated with @ResponseBody by default, JSON
// in, JSON out
@RestController
@RequestMapping("api")
public class ApiController {

  @Autowired
  private EntityManager entityManager;

  private static final Logger log = LogManager.getLogger(ApiController.class);

  /**
   * Simple status test - returns whatever the message is
   * 
   * @param message
   * @return {"code" = "<message>"}
   */

  // Endpoint de prueba. Devuelve {code: "<message>"}
  @GetMapping("/status/{message}")
  public Map<String, String> check(@PathVariable String message) {
    return Map.of("code", message);
  }

  /**
   * Counts current users
   * 
   * @param message
   * @return {"code" = "<message>"}
   */

  // Devuelve {count: N} con el número total de usuarios en la BD
  @GetMapping("/users/count")
  public Map<String, Long> usersCount() {
    return Map.of("count",
        (Long) entityManager.createQuery("SELECT COUNT(u) FROM User u").getSingleResult());
  }

  /**
   * Loads a file from the classpath.
   * This works even if the file is in a JAR.
   * 
   * @param path - path to the file - **relative to target/classes**
   * @return the file
   */

  // carga un fichero del classpath (dentro del JAR)
  private File loadFromClasspath(String path) {
    try {
      return ResourceUtils.getFile("classpath:" + path);
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Could not load file from classpath: " + path, e);
    }
  }

  /**
   * Executes JS code using karate-js
   * 
   * @param text
   * @param vars
   * @return
   */

  // ejecuta código JavaScript embebido con karate-js
  private Object eval(String source, Map<String, Object> vars) {
    Parser parser = new Parser(new Source(source));
    Node node = parser.parse();
    Context context = Context.root();
    if (vars != null) {
      vars.forEach((k, v) -> context.declare(k, v));
    }
    return Interpreter.eval(node, context);
  }

  /**
   * Executes JS code loaded from a file in the server
   */

  /*
   * Carga el fichero static/js/js-eval.js del classpath, lo ejecuta
   * con el intérprete karate-js (motor JS embebido en Java) y devuelve
   * el resultado
   */
  @GetMapping(value = "/js", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> testJs() throws Exception {
    String start = Files.readString(
        loadFromClasspath("static/js/js-eval.js").toPath());
    String source = start + "\n" + "f(v);";

    Object result = eval(source, Map.of(
        "v", 10,
        "exampleExternalVar", "patata"));
    return Map.of("result", result.toString());
  }

  @Autowired
  private SimpMessagingTemplate messagingTemplate;

  /**
   * Posts a message to a game.
   * 
   * @param code of target game (source user is from ID)
   * @param o    JSON-ized message, similar to {"message": "text goes here"}
   * @throws JsonProcessingException
   */

  // Envía un mensaje a una partida (solo si el usuario es miembro o ADMIN)
  // Guarda el mensaje en BD y lo emite por WebSocket a /game/{code}
  @PostMapping("/game/{code}")
  @ResponseBody
  @Transactional
  public Map<String, String> postMsg(@PathVariable String code,
      @RequestBody JsonNode o, Model model, HttpSession session,
      HttpServletResponse response)
      throws JsonProcessingException {

    String text = o.get("message").asText();
    User sender = entityManager.find(
        User.class, ((User) session.getAttribute("u")).getId());

    Game target = entityManager.createQuery(
        "SELECT g FROM Game g WHERE g.code = :code", Game.class)
        .setParameter("code", code)
        .getSingleResult();

    // Parse internal state
    ObjectMapper objectMapper = new ObjectMapper();

    Map<String, Object> state;
    try {
      String internalState = target.getInternalState();

      if (internalState == null || internalState.isBlank()) {
        state = Map.of("players", List.of());
      } else {
        state = objectMapper.readValue(
            internalState,
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
      }

    } catch (Exception e) {
      throw new RuntimeException("Invalid internal state", e);
    }

    // Extract players list
    List<Map<String, Object>> players = (List<Map<String, Object>>) state.getOrDefault("players", List.of());

    // verify permissions
    boolean isInGame = players.stream()
        .anyMatch(p -> Objects.equals(sender.getId(), ((Number) p.get("id")).longValue()));

    if (!sender.hasRole("ADMIN") && !isInGame) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return Map.of("error", "user not in group");
    }

    // build message, save to BD
    Message m = new Message();
    m.setSender(sender);
    m.setGame(target);
    m.setDateSent(LocalDateTime.now());
    m.setText(text);
    entityManager.persist(m);
    entityManager.flush(); // to get Id before commit

    // send to game & return
    String json = new ObjectMapper().writeValueAsString(m.toTransfer());
    log.info("Sending a message to  game {} with contents '{}'", target.getCode(), json);
    messagingTemplate.convertAndSend("/game/" + code, json);
    return Map.of("result", "message sent");
  }

  /**
   * Posts a message to a game.
   * 
   * @param code of target game (source user is from ID)
   * @param o    JSON-ized message, similar to {"message": "text goes here"}
   * @throws JsonProcessingException
   */

  @GetMapping("/game/{code}")
  @ResponseBody
  @Transactional
  public Map<String, String> getMessages(@PathVariable String code, HttpSession session,
      HttpServletResponse response)
      throws JsonProcessingException {

    User requester = entityManager.find(
        User.class, ((User) session.getAttribute("u")).getId());

    Game target = entityManager.createQuery(
        "SELECT g FROM Game g WHERE g.code = :code", Game.class)
        .setParameter("code", code)
        .getSingleResult();

    ObjectMapper mapper = new ObjectMapper();

    Map<String, Object> state;
    try {
      String internalState = target.getInternalState();

      if (internalState == null || internalState.isBlank()) {
        state = Map.of("players", List.of());
      } else {
        state = mapper.readValue(internalState, Map.class);
      }
    } catch (Exception e) {
      throw new RuntimeException("Invalid internalState", e);
    }

    List<Map<String, Object>> players = (List<Map<String, Object>>) state.getOrDefault("players", List.of());

    boolean isInGame = players.stream().anyMatch(p -> Objects.equals(
        requester.getId(),
        ((Number) p.get("id")).longValue()));

    // verify permissions
    if (!requester.hasRole("ADMIN") && !isInGame) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return Map.of("error", "user not in group");
    }

    // return result
    ObjectMapper outMapper = new ObjectMapper();

    return Map.of("messages",
        outMapper.writeValueAsString(
            target.getMessages().stream()
                .map(Message::toTransfer)
                .toArray()));
  }
}