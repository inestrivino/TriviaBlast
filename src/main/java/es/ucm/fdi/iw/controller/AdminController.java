package es.ucm.fdi.iw.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.Transferable;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
* CONTROLLER DE ADMINISTRACIÓN 

* Solo accesible para usuarios con rol ADMIN (configurado en SecurityConfig)
* Ruta base: /admin/**
*/

/**
 * Site administration.
 *
 * Access to this end-point is authenticated - see SecurityConfig
 */
@Controller
@RequestMapping("admin")
public class AdminController {

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private EntityManager entityManager;

  @ModelAttribute
  public void populateModel(HttpSession session, Model model) {
    for (String name : new String[] { "u", "url", "ws", "topics" }) {
      model.addAttribute(name, session.getAttribute(name));
    }
  }

  private static final Logger log = LogManager.getLogger(AdminController.class);

  // Lista todos los usuarios de la BD y renderiza admin.html
  @GetMapping("/")
  public String index(Model model) {
    // 1. Traer los usuarios
    List<User> users = entityManager.createQuery(
        "SELECT u FROM User u ORDER BY u.totalPoints DESC", User.class).getResultList();
    model.addAttribute("users", users);

    // 2. RECUPERAR LAS ALERTAS DE MODERACIÓN EXCLUSIVAS
    List<Message> alertasModeracion = entityManager.createQuery(
        "SELECT m FROM Message m WHERE m.adminOnly = true ORDER BY m.dateSent DESC", Message.class)
        .setMaxResults(10) // Limitamos a las 10 más recientes
        .getResultList();

    model.addAttribute("alertas", alertasModeracion);

    return "admin";
  }

  /*
   * Cambia el campo enabled del usuario (true/false)
   * Un usuario oculto no aparece en el scoreboard para otros usuarios
   * Devuelve JSON con el nuevo estado. Llamado desde triviablast.js
   * mediante fetch() (sin recargar la página)
   */
  @PostMapping("/toggleView/{userId}")
  @Transactional
  @ResponseBody
  public Map<String, Object> toggleVisibility(@PathVariable long userId) {
    try {
      User user = entityManager.find(User.class, userId);

      if (user == null) {
        log.warn("Attempted to toggle visibility for non-existent user ID: {}", userId);
        return Map.of("error", "UserNotFound");
      }

      user.setEnabled(!user.isEnabled());
      entityManager.merge(user);

      log.info("Toggled visibility for user {}: now {}", user.getUsername(), user.isEnabled());

      return Map.of(
          "userId", user.getId(),
          "enabled", user.isEnabled());

    } catch (Exception e) {
      log.error("Error toggling visibility for user ID: " + userId, e);
      return Map.of("error", "ToggleFailed");
    }
  }

  /**
   * Returns JSON with all received messages
   */
  // Devuelve los últimos 5 mensajes del sistema como JSON
  @GetMapping(path = "all-messages", produces = "application/json")
  @Transactional // para no recibir resultados inconsistentes
  @ResponseBody // para indicar que no devuelve vista, sino un objeto (jsonizado)
  public List<Message.Transfer> retrieveMessages(HttpSession session) {
    TypedQuery<Message> query = entityManager.createQuery("select m from Message m", Message.class);
    query.setMaxResults(5);
    query.setFirstResult(0);
    // devuelve resultado
    return query.getResultList().stream().map(Transferable::toTransfer)
        .collect(Collectors.toList());
  }
}