package es.ucm.fdi.iw.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.karatelabs.js.Context;
import io.karatelabs.js.Interpreter;
import io.karatelabs.js.Node;
import io.karatelabs.js.Parser;
import io.karatelabs.js.Source;
import jakarta.persistence.EntityManager;

/**
* CONTROLLER REST / API 

* Expone endpoints JSON sin necesidad de autenticación por defecto
* Usa @RestController (= @Controller + @ResponseBody en todos los métodos)
* Ruta base: /api/**
*
*/

// RestController = all methods annotated with @ResponseBody by default, JSON
// in, JSON out
@RestController
@RequestMapping("api")
public class ApiController {

  @Autowired
  private EntityManager entityManager;

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
}