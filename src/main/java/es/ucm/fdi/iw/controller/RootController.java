package es.ucm.fdi.iw.controller;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import es.ucm.fdi.iw.controller.dtos.GameSetupDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
* CONTROLLER PÚBLICO / RAÍZ 
*
* Añadir una nueva página pública: crear el método con @GetMapping y
* devuelve el nombre del template. Añadir la URL a
* SecurityConfig como .permitAll() si no requiere login
*/

/**
 * Non-authenticated requests only.
 */
@Controller
public class RootController {
    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    // muestra login.html con flag de error si falló
    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        boolean error = request.getQueryString() != null && request.getQueryString().indexOf("error") != -1;
        model.addAttribute("loginError", error);
        return "login";
    }

    // muestra authors.html
    @GetMapping("/authors")
    public String authors(Model model) {
        return "authors";
    }

    // muestra multi_game_setup.html
    @GetMapping("/multi_game_setup")
    public String multi_game_setup(Model model) {
        model.addAttribute("categories", TriviaCategory.getAll());
        return "multi_game_setup";
    }

    // muestra join_game.html
    @GetMapping("/join_game")
    public String join_game(Model model) {
        return "join_game";
    }

    // muestra proposal.html
    @GetMapping("/proposal")
    public String proposal(Model model) {
        return "proposal";
    }

    // muestra single_game_setup.html
    @GetMapping("/single_game_setup")
    public String single_setup(Model model) {
        model.addAttribute("categories", TriviaCategory.getAll());
        return "single_game_setup";
    }
}
