package es.ucm.fdi.iw.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Non-authenticated requests only.
 */
@Controller
public class RootController {

    private static final Logger log = LogManager.getLogger(RootController.class);

    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        boolean error = request.getQueryString() != null && request.getQueryString().indexOf("error") != -1;
        model.addAttribute("loginError", error);
        return "login";
    }

    @GetMapping("/propuesta")
    public String propuesta(Model model) {
        return "propuesta";
    }

    @GetMapping("/autores")
    public String autores(Model model) {
        return "autores";
    }

    @GetMapping("/vistas")
    public String vistas(Model model) {
        return "vistas";
    }

    @GetMapping("/crear_par_multijug")
    public String crearParMultijug(Model model) {
        return "crear_par_multijug";
    }

    @GetMapping("/inicio_sin_sesion")
    public String inicioSinSesion(Model model) {
        return "inicio_sin_sesion";
    }

    @GetMapping("/insertar_codigo")
    public String insertarCodigo(Model model) {
        return "insertar_codigo";
    }

    @GetMapping("/par_multijugador")
    public String parMultijugador(Model model) {
        return "par_multijugador";
    }
    
    @GetMapping("/multip_victoryscr")
    public String multipVictoryScr(Model model) {
        return "multip_victoryscr";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        return "profile";
    }
}
