package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.Transferable;
import es.ucm.fdi.iw.model.User;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * User management.
 *
 * Access to this end-point is authenticated.
 */
@Controller()
@RequestMapping("user")
public class UserController {

  private static final Logger log = LogManager.getLogger(UserController.class);

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private LocalData localData;

  @Autowired
  private SimpMessagingTemplate messagingTemplate;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private AuthenticationManager authenticationManager;

  @ModelAttribute
  public void populateModel(HttpSession session, Model model) {
    for (String name : new String[] { "u", "url", "ws", "topics" }) {
      model.addAttribute(name, session.getAttribute(name));
    }
  }

  /**
   * Exception to use when denying access to unauthorized users.
   * 
   * In general, admins are always authorized, but users cannot modify
   * each other's profiles.
   */
  @ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "No eres administrador, y éste no es tu perfil") // 403
  public static class NoEsTuPerfilException extends RuntimeException {
  }

  /**
   * Encodes a password, so that it can be saved for future checking. Notice
   * that encoding the same password multiple times will yield different
   * encodings, since encodings contain a randomly-generated salt.
   * 
   * @param rawPassword to encode
   * @return the encoded password (typically a 60-character string)
   *         for example, a possible encoding of "test" is
   *         {bcrypt}$2y$12$XCKz0zjXAP6hsFyVc8MucOzx6ER6IsC1qo5zQbclxhddR1t6SfrHm
   */
  public String encodePassword(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  /**
   * Generates random tokens. From https://stackoverflow.com/a/44227131/15472
   * 
   * @param byteLength
   * @return
   */
  public static String generateRandomBase64Token(int byteLength) {
    SecureRandom secureRandom = new SecureRandom();
    byte[] token = new byte[byteLength];
    secureRandom.nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token); // base64 encoding
  }

  @GetMapping("/profile")
  public String profile(Model model, HttpSession session) {
    User user = (User) session.getAttribute("u");
    if (user == null) {
      return "redirect:/login";
    }
    model.addAttribute("user", user);
    return "profile";
  }

  /**
   * Landing page for a user profile
   */
  @GetMapping("{id}")
  public String index(@PathVariable long id, Model model, HttpSession session) {
    User target = entityManager.find(User.class, id);
    model.addAttribute("user", target);
    return "profile";
  }

  private void authenticateUser(String username, String rawPassword, HttpSession session) {
    Authentication authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(username, rawPassword);

    Authentication authenticationResponse = authenticationManager.authenticate(authenticationRequest);

    SecurityContextHolder.getContext().setAuthentication(authenticationResponse);

    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        SecurityContextHolder.getContext());
  }

  /**
   * Alter or create a user
   */
  @PostMapping("/register")
  @Transactional
  public String register(
      HttpServletResponse response,
      @ModelAttribute User edited,
      @RequestParam(required = false) String pass2,
      RedirectAttributes redirectAttributes,
      Model model, HttpSession session) throws IOException {

    User target = new User();
    if (!edited.getPassword().equals(pass2)) {
      log.warn("Passwords do not match - returning to register form");
      model.addAttribute("user", edited);
      redirectAttributes.addFlashAttribute("error", "password_mismatch");
      return "redirect:/login";
    }
    // save encoded version of password
    target.setPassword(encodePassword(edited.getPassword()));
    target.setUsername(edited.getUsername());
    target.setEmail(edited.getEmail());
    target.setRoles("USER");

    entityManager.persist(target);
    authenticateUser(target.getUsername(), edited.getPassword(), session);
    session.setAttribute("u", target);
    return "redirect:/user/" + target.getId();
  }

  /**
   * Alter or create a user
   */
  @PostMapping("/{id}")
  @Transactional
  public String postUser(
      HttpServletResponse response,
      @PathVariable long id,
      @ModelAttribute User edited,
      @RequestParam(required = false) String pass2,
      @RequestParam(required = false) String formType,
      @RequestParam(required = false) String currentPassword,
      Model model, HttpSession session) throws IOException {

    User requester = (User) session.getAttribute("u");
    User target = null;
    if (id == -1 && requester.hasRole("ADMIN")) {
      target = new User();
      target.setPassword(encodePassword(generateRandomBase64Token(12)));
      target.setEnabled(true);
      entityManager.persist(target);
      entityManager.flush();
      id = target.getId();
    }

    // retrieve requested user
    target = entityManager.find(User.class, id);
    model.addAttribute("user", target);

    if (requester.getId() != target.getId() &&
        !requester.hasRole("ADMIN")) {
      throw new NoEsTuPerfilException();
    }

    if ("password".equals(formType)) {
      if (edited.getPassword() != null && !edited.getPassword().isEmpty()) {

        if (!passwordEncoder.matches(currentPassword, target.getPassword())) {
          model.addAttribute("error", "current_password_incorrect");
          model.addAttribute("openTab", "password");
          return "profile";
        }

        if (!edited.getPassword().equals(pass2)) {
          log.warn("Passwords do not match - returning to profile");

          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          model.addAttribute("error", "password_mismatch");
          model.addAttribute("openTab", "password");

          return "profile";
        }

        target.setPassword(encodePassword(edited.getPassword()));
      }

    } else if ("userNameEmail".equals(formType)) {

      if (edited.getUsername() != null && !edited.getUsername().isEmpty()) {
        target.setUsername(edited.getUsername());
      }

      if (edited.getEmail() != null && !edited.getEmail().isEmpty()) {
        target.setEmail(edited.getEmail());
      }

    } else if ("avatar".equals(formType)) {

      log.info("Avatar update should be handled in /pic endpoint");

    } else {
      log.warn("Unknown formType or null - no changes applied");
    }

    // update user session so that changes are persisted in the session, too
    if (requester.getId() == target.getId()) {
      session.setAttribute("u", target);
    }

    return "redirect:/user/" + id;
  }

  /**
   * Returns the default profile pic
   * 
   * @return
   */
  private static InputStream defaultPic() {
    return new BufferedInputStream(Objects.requireNonNull(
        UserController.class.getClassLoader().getResourceAsStream(
            "static/img/default-pic.jpg")));
  }

  /**
   * Downloads a profile pic for a user id
   * 
   * @param id
   * @return
   * @throws IOException
   */
  @GetMapping("{id}/pic")
  public StreamingResponseBody getPic(@PathVariable long id) throws IOException {
    File f = localData.getFile("user", "" + id + ".jpg");
    InputStream in = new BufferedInputStream(f.exists() ? new FileInputStream(f) : UserController.defaultPic());
    return os -> FileCopyUtils.copy(in, os);
  }

  /**
   * Uploads a profile pic for a user id
   * 
   * @param id
   * @return
   * @throws IOException
   */
  @PostMapping("{id}/pic")
  public String setPic(@RequestParam("photo") MultipartFile photo, @PathVariable long id,
      HttpServletResponse response, HttpSession session, Model model) throws IOException {

    User target = entityManager.find(User.class, id);
    model.addAttribute("user", target);

    // check permissions
    User requester = (User) session.getAttribute("u");
    if (requester.getId() != target.getId() &&
        !requester.hasRole("ADMIN")) {
      throw new NoEsTuPerfilException();
    }

    log.info("Updating photo for user {}", id);
    File f = localData.getFile("user", "" + id + ".jpg");

    if (photo.isEmpty()) {
      log.info("failed to upload photo: emtpy file?");
    } else {
      try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(f))) {
        byte[] bytes = photo.getBytes();
        stream.write(bytes);
        log.info("Uploaded photo for {} into {}!", id, f.getAbsolutePath());
      } catch (Exception e) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        log.warn("Error uploading " + id + " ", e);
      }
    }
    return "redirect:/user/" + id;
  }

  @GetMapping("error")
  public String error(Model model, HttpSession session, HttpServletRequest request) {
    model.addAttribute("sess", session);
    model.addAttribute("req", request);
    return "error";
  }

  /**
   * Returns JSON with all received messages
   */
  @GetMapping(path = "received", produces = "application/json")
  @Transactional // para no recibir resultados inconsistentes
  @ResponseBody // para indicar que no devuelve vista, sino un objeto (jsonizado)
  public List<Message.Transfer> retrieveMessages(HttpSession session) {
    long userId = ((User) session.getAttribute("u")).getId();

    List<Message> messages = entityManager
        .createQuery("select m from Message m where m.sender.id = :id", Message.class)
        .setParameter("id", userId)
        .getResultList();

    return messages.stream()
        .map(Message::toTransfer)
        .collect(Collectors.toList());
    /**
     * User u = entityManager.find(User.class, userId);
     * log.info("Generating message list for user {} ({} messages)",
     * u.getUsername(), u.getReceived().size());
     * return
     * u.getReceived().stream().map(Transferable::toTransfer).collect(Collectors.toList());
     **/
  }

  /**
   * Returns JSON with count of unread messages
   */
  @GetMapping(path = "unread", produces = "application/json")
  @ResponseBody
  public String checkUnread(HttpSession session) {
    long userId = ((User) session.getAttribute("u")).getId();
    long unread = entityManager.createNamedQuery("Message.countUnread", Long.class)
        .setParameter("userId", userId)
        .getSingleResult();
    session.setAttribute("unread", unread);
    return "{\"unread\": " + unread + "}";
  }

  /**
   * Posts a message to a user.
   * 
   * @param id of target user (source user is from ID)
   * @param o  JSON-ized message, similar to {"message": "text goes here"}
   * @throws JsonProcessingException
   */
  @PostMapping("/{id}/msg")
  @ResponseBody
  @Transactional
  public String postMsg(@PathVariable long id,
      @RequestBody JsonNode o, Model model, HttpSession session)
      throws JsonProcessingException {

    String text = o.get("message").asText();
    User u = entityManager.find(User.class, id);
    User sender = entityManager.find(
        User.class, ((User) session.getAttribute("u")).getId());
    model.addAttribute("user", u);

    // construye mensaje, lo guarda en BD
    Message m = new Message();
    // m.setRecipient();
    m.setSender(sender);
    m.setDateSent(LocalDateTime.now());
    m.setText(text);
    entityManager.persist(m);
    entityManager.flush(); // to get Id before commit

    ObjectMapper mapper = new ObjectMapper();
    /*
     * // construye json: método manual
     * ObjectNode rootNode = mapper.createObjectNode();
     * rootNode.put("from", sender.getUsername());
     * rootNode.put("to", u.getUsername());
     * rootNode.put("text", text);
     * rootNode.put("id", m.getId());
     * String json = mapper.writeValueAsString(rootNode);
     */
    // persiste objeto a json usando Jackson
    String json = mapper.writeValueAsString(m.toTransfer());

    log.info("Sending a message to {} with contents '{}'", id, json);

    messagingTemplate.convertAndSend("/user/" + u.getUsername() + "/queue/updates", json);
    return "{\"result\": \"message sent.\"}";
  }

  @PostMapping("/{id}/delete")
  @Transactional
  public String deleteUser(@PathVariable long id, HttpSession session) {

    User requester = (User) session.getAttribute("u");
    User target = entityManager.find(User.class, id);

    if (requester.getId() != target.getId() &&
        !requester.hasRole("ADMIN")) {
      throw new NoEsTuPerfilException();
    }

    entityManager.remove(target);

    // cerrar sesión si se borra a sí mismo
    if (requester.getId() == id) {
      session.invalidate();
      return "redirect:/login";
    }

    return "redirect:/";
  }

  @GetMapping("/scoreboard")
  public String scoreboard(Model model) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean isAdmin = auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

    List<User> users;

    if (isAdmin) {
      users = entityManager.createQuery(
          "SELECT u FROM User u ORDER BY u.totalPoints DESC", User.class)
          .getResultList();
    } else {
      users = entityManager.createQuery(
          "SELECT u FROM User u WHERE u.visibilityState = TRUE ORDER BY u.totalPoints DESC", User.class)
          .getResultList();
    }

    model.addAttribute("users", users);
    return "scoreboard";
  }

}
