package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.User;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/**
* CONTROLLER DE USUARIOS 

* Gestiona todo lo relacionado con cuentas de usuario: registro,
* perfil, foto, mensajes y puntuación. Ruta base: /user/**
*
*
* Añadir campo nuevo al perfil: añadir el campo en User.java, AdminController.java
* en profile.html un nuevo input, y en postUser() guarda el valor
*/

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
  // codifica contraseña con BCrypt
  public String encodePassword(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  /**
   * Generates random tokens. From https://stackoverflow.com/a/44227131/15472
   */
  // genera tokens aleatorios seguros para la contraseña
  // no se usó para los códigos de partida ya que crea tokens con chars no
  // alfanuméricos
  public static String generateRandomBase64Token(int byteLength) {
    SecureRandom secureRandom = new SecureRandom();
    byte[] token = new byte[byteLength];
    secureRandom.nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token); // base64 encoding
  }

  // Obtiene el usuario de la sesión y renderiza profile.html
  @GetMapping("/profile")
  public String profile(Model model, HttpSession session) {
    User sessionUser = (User) session.getAttribute("u");
    if (sessionUser == null) {
      return "redirect:/login";
    }

    User freshUser = entityManager.find(User.class, sessionUser.getId());

    // Si por algún motivo el usuario ya no existe en la BD (ej. borrado),
    // protegemos la app
    if (freshUser == null) {
      session.invalidate(); // Limpiamos sesión inválida
      return "redirect:/login";
    }

    // Le pasamos a Thymeleaf el usuario recién sacado de la BD
    model.addAttribute("user", freshUser);
    return "profile";
  }

  // helper que hace login programático tras el registro
  private void authenticateUser(String username, String rawPassword, HttpSession session) {
    Authentication authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(username, rawPassword);

    Authentication authenticationResponse = authenticationManager.authenticate(authenticationRequest);

    SecurityContextHolder.getContext().setAuthentication(authenticationResponse);

    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        SecurityContextHolder.getContext());
  }

  /*
   * Registra un nuevo usuario. Valida que las contraseñas coincidan,
   * codifica la contraseña, persiste el User, y hace auto-login
   */
  @PostMapping("/register")
  @Transactional
  public String register(
      HttpServletResponse response,
      @ModelAttribute User edited,
      @RequestParam(required = false) String pass2,
      RedirectAttributes redirectAttributes,
      Model model,
      HttpSession session) throws IOException {

    try {

      // Passwords distintas
      if (!edited.getPassword().equals(pass2)) {

        log.warn("Passwords do not match");

        redirectAttributes.addFlashAttribute(
            "error",
            "Passwords do not match");

        return "redirect:/login";
      }

      // Username ya existe
      Long usernameCount = entityManager.createQuery("""
          SELECT COUNT(u)
          FROM User u
          WHERE LOWER(u.username) = LOWER(:username)
          """, Long.class)
          .setParameter("username", edited.getUsername())
          .getSingleResult();

      if (usernameCount > 0) {

        redirectAttributes.addFlashAttribute(
            "error",
            "Username already in use");

        return "redirect:/login";
      }

      // Email ya existe
      Long emailCount = entityManager.createQuery("""
          SELECT COUNT(u)
          FROM User u
          WHERE LOWER(u.email) = LOWER(:email)
          """, Long.class)
          .setParameter("email", edited.getEmail())
          .getSingleResult();

      if (emailCount > 0) {

        redirectAttributes.addFlashAttribute(
            "error",
            "Email already in use");

        return "redirect:/login";
      }

      // Crear usuario
      User target = new User();

      target.setPassword(encodePassword(edited.getPassword()));
      target.setUsername(edited.getUsername());
      target.setEmail(edited.getEmail());
      target.setRoles("USER");

      entityManager.persist(target);
      entityManager.flush();

      authenticateUser(
          target.getUsername(),
          edited.getPassword(),
          session);

      session.setAttribute("u", target);

      return "redirect:/user/" + target.getId();

    } catch (Exception e) {

      log.error("Registration error", e);

      redirectAttributes.addFlashAttribute(
          "error",
          "Unexpected registration error");

      return "redirect:/login";
    }
  }

  // devuelve el scoreboard de los usuarios
  @GetMapping("/scoreboard")
  public String scoreboard(Model model, Authentication authentication) {
    List<User> users;

    // 1. Verificar si el usuario actual está autenticado
    boolean isAdmin = false;
    if (authentication != null && authentication.isAuthenticated()) {
      String currentUsername = authentication.getName();

      try {
        User currentUser = entityManager.createQuery(
            "SELECT u FROM User u WHERE u.username = :username", User.class)
            .setParameter("username", currentUsername)
            .getSingleResult();

        if (currentUser.getRoles() != null && currentUser.getRoles().contains("ADMIN")) {
          isAdmin = true;
        }
      } catch (Exception e) {
        isAdmin = false;
      }
    }

    if (isAdmin) {
      users = entityManager.createQuery(
          "SELECT u FROM User u ORDER BY u.totalPoints DESC", User.class)
          .getResultList();
    } else {
      // Los usuarios normales y anónimos SOLO ven los que tienen enabled = true
      users = entityManager.createQuery(
          "SELECT u FROM User u WHERE u.enabled = true ORDER BY u.totalPoints DESC", User.class)
          .getResultList();
    }

    model.addAttribute("users", users);
    return "scoreboard";
  }

  // permite a un usuario reportar a otro ante los administradores
  @PostMapping("/report/{id}")
  @ResponseBody
  @Transactional
  public Map<String, Object> reportUser(@PathVariable long id, Authentication authentication)
      throws JsonProcessingException {

    if (authentication == null || !authentication.isAuthenticated()) {
      return Map.of("status", "error", "message", "No autenticado");
    }

    String myUsername = authentication.getName();
    User me = entityManager.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
        .setParameter("username", myUsername)
        .getSingleResult();

    User reportedUser = entityManager.find(User.class, id);
    if (reportedUser == null) {
      return Map.of("status", "error", "message", "Usuario no encontrado");
    }

    // Texto descriptivo
    String textoMensaje = String.format("El usuario '%s' (ID: %d) ha reportado a '%s' (ID: %d).",
        me.getUsername(), me.getId(), reportedUser.getUsername(), reportedUser.getId());

    // 1. CREAR Y PERSISTIR EL MENSAJE
    Message dbMessage = new Message();
    dbMessage.setSender(me);
    dbMessage.setText(textoMensaje);
    dbMessage.setDateSent(java.time.LocalDateTime.now());
    dbMessage.setAdminOnly(true);
    dbMessage.setGame(null);

    entityManager.persist(dbMessage);
    entityManager.flush();

    // 2. PREPARAR PAYLOAD WEBSOCKET
    ObjectMapper mapper = new ObjectMapper();
    String horaActual = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

    Map<String, Object> alertPayload = Map.of(
        "from", "SISTEMA (Denuncia)",
        "text", textoMensaje,
        "sent", horaActual);

    String jsonAlert = mapper.writeValueAsString(alertPayload);

    // 3. ENVIAR AL CANAL
    log.info("Despachando alerta de moderación a /topic/admin: {}", jsonAlert);
    messagingTemplate.convertAndSend("/topic/admin", jsonAlert);

    return Map.of("status", "success", "message", "Denuncia procesada y registrada en el sistema");
  }

  /*
   * * Actualiza el perfil. Distingue tres sub-formularios vía el param
   * "formType":
   * · "password" → valida contraseña actual, actualiza si coincide
   * · "userNameEmail"→ actualiza username y/o email
   * · "avatar" → redirige al endpoint /pic
   * Solo el propio usuario o un ADMIN pueden modificar un perfil
   */
  // Busca el usuario por ID en la BD y renderiza profile.html
  @GetMapping("{id}")
  public String index(@PathVariable long id, Model model, HttpSession session) {
    User target = entityManager.find(User.class, id);
    model.addAttribute("user", target);
    return "profile";
  }

  @PostMapping("/{id}")
  @Transactional
  public String postUser(
      HttpServletResponse response,
      @PathVariable long id,
      @ModelAttribute User edited,
      @RequestParam(required = false) String pass2,
      @RequestParam(required = false) String formType,
      @RequestParam(required = false) String currentPassword,
      Model model,
      HttpSession session) throws IOException {

    try {
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

      if (requester.getId() != target.getId()
          && !requester.hasRole("ADMIN")) {
        throw new NoEsTuPerfilException();
      }

      if ("password".equals(formType)) {
        if (edited.getPassword() != null
            && !edited.getPassword().isEmpty()) {
          if (!passwordEncoder.matches(currentPassword, target.getPassword())) {
            model.addAttribute("error", "Current password is incorrect");
            model.addAttribute("openTab", "password");
            return "profile";
          }

          if (!edited.getPassword().equals(pass2)) {
            model.addAttribute("error", "Passwords do not match");
            model.addAttribute("openTab", "password");
            return "profile";
          }

          target.setPassword(encodePassword(edited.getPassword()));
        }

      } else if ("userNameEmail".equals(formType)) {
        if (edited.getUsername() != null
            && !edited.getUsername().isEmpty()) {
          Long existingUsername = entityManager
              .createQuery("""
                      SELECT COUNT(u)
                      FROM User u
                      WHERE u.username = :username
                      AND u.id != :id
                  """, Long.class)
              .setParameter("username", edited.getUsername())
              .setParameter("id", target.getId())
              .getSingleResult();

          if (existingUsername > 0) {
            model.addAttribute("error", "Username already in use");
            model.addAttribute("user", target);
            model.addAttribute("openTab", "name");
            return "profile";
          }
          target.setUsername(edited.getUsername());
        }

        if (edited.getEmail() != null
            && !edited.getEmail().isEmpty()) {
          Long existingEmail = entityManager
              .createQuery("""
                      SELECT COUNT(u)
                      FROM User u
                      WHERE u.email = :email
                      AND u.id != :id
                  """, Long.class)
              .setParameter("email", edited.getEmail())
              .setParameter("id", target.getId())
              .getSingleResult();

          if (existingEmail > 0) {
            model.addAttribute("error", "Email already in use");
            model.addAttribute("user", target);
            model.addAttribute("openTab", "name");
            return "profile";
          }

          target.setEmail(edited.getEmail());
        }
      } else if ("avatar".equals(formType)) {
        log.info("Avatar update should be handled in /pic endpoint");

      } else {
        model.addAttribute("error", "Unknown form type");
        return "profile";
      }
      entityManager.merge(target);
      entityManager.flush();

      // update user session
      if (requester.getId() == target.getId()) {
        session.setAttribute("u", target);
      }
      return "redirect:/user/" + id;
    } catch (NoEsTuPerfilException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error updating user", e);
      String msg = e.getMessage();
      if (msg != null) {
        String lower = msg.toLowerCase();

        if (lower.contains("username")) {
          msg = "Username already in use";
        } else if (lower.contains("email")) {
          msg = "Email already in use";
        }
      }

      model.addAttribute("error",
          msg != null ? msg : "Error updating profile");
      model.addAttribute("openTab", formType);

      User target = entityManager.find(User.class, id);
      model.addAttribute("user", target);

      return "profile";
    }
  }

  // helper que devuelve la foto de perfil básica
  private static InputStream defaultPic() {
    return new BufferedInputStream(Objects.requireNonNull(
        UserController.class.getClassLoader().getResourceAsStream(
            "static/img/default-pic.jpg")));
  }

  // Descarga la foto de perfil. Si no existe, devuelve la foto por defecto
  @GetMapping("{id}/pic")
  public StreamingResponseBody getPic(@PathVariable long id) throws IOException {
    File f = localData.getFile("user", "" + id + ".jpg");
    InputStream in = new BufferedInputStream(f.exists() ? new FileInputStream(f) : UserController.defaultPic());
    return os -> FileCopyUtils.copy(in, os);
  }

  // Guarda la foto subida en ./iwdata/user /{id}.jpg
  @PostMapping("{id}/pic")
  @Transactional
  public String setPic(@RequestParam("photo") MultipartFile photo,
      @PathVariable long id,
      HttpServletResponse response,
      HttpSession session,
      Model model) throws IOException {

    User target = entityManager.find(User.class, id);
    model.addAttribute("user", target);

    // check permissions
    User requester = (User) session.getAttribute("u");
    if (requester == null ||
        (requester.getId() != target.getId() && !requester.hasRole("ADMIN"))) {
      throw new NoEsTuPerfilException();
    }

    if (photo.isEmpty()) {
      log.info("Empty file uploaded for user {}", id);
      return "redirect:/user/" + id;
    }

    try {
      // 1. Validación real de imagen
      BufferedImage img = ImageIO.read(photo.getInputStream());
      if (img == null) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        throw new RuntimeException("Invalid image file");
      }

      // 2. Detectar tipo MIME
      String contentType = photo.getContentType();
      String extension;

      if (contentType == null) {
        throw new RuntimeException("Unknown file type");
      }

      switch (contentType) {
        case "image/png" -> extension = ".png";
        case "image/jpeg" -> extension = ".jpg";
        case "image/jpg" -> extension = ".jpg";
        case "image/gif" -> extension = ".gif";
        default -> throw new RuntimeException("Unsupported image type: " + contentType);
      }

      // 3. Eliminar avatar anterior si no es default
      String oldAvatar = target.getAvatar();
      if (oldAvatar != null &&
          !oldAvatar.equals("default-pic.jpg")) {

        File oldFile = localData.getFile("user", oldAvatar);
        if (oldFile.exists()) {
          boolean deleted = oldFile.delete();
          if (deleted) {
            log.info("Deleted old avatar {}", oldAvatar);
          }
        }
      }

      // 4. Guardar nueva imagen
      String filename = id + extension;
      File f = localData.getFile("user", filename);

      try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(f))) {
        stream.write(photo.getBytes());
      }

      // 5. Actualizar BD
      target.setAvatar(filename);
      entityManager.merge(target);

      log.info("Uploaded new avatar for user {} -> {}", id, filename);

    } catch (Exception e) {
      log.warn("Error uploading image for user " + id, e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      throw new RuntimeException("Error uploading image", e);
    }

    return "redirect:/user/" + id;
  }

  @GetMapping("error")
  public String error(Model model, HttpSession session, HttpServletRequest request) {
    model.addAttribute("sess", session);
    model.addAttribute("req", request);
    return "error";
  }

  // Devuelve mensajes enviados por el usuario como JSON
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
  }

  // Borra el usuario de la BD. Si se borra a sí mismo, cierra sesión
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
}
