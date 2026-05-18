package es.ucm.fdi.iw.controller;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

* Gestiona las páginas que no requieren autenticación
* Todas las rutas aquí son accesibles sin login (configurado
* en SecurityConfig)
*
* Añadir una nueva página pública: crear el método con @GetMapping y
* devuelve el nombre del template. Añadir la URL a
* SecurityConfig como .permitAll()
*/


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
        return "multi_game_setup";
    }

    // muestra join_game.html
    @GetMapping("/join_game")
    public String join_game(Model model) {
        return "join_game";
    }

    // muestra multi_game.html
    @GetMapping("/multi_game")
    public String multi_game(Model model) {
        return "multi_game";
    }

    // muestra multi_victoryscr.html
    @GetMapping("/multi_victoryscr")
    public String multi_victoryscr(Model model) {
        return "multi_victoryscr";
    }

    // muestra profile.html
    @GetMapping("/profile")
    public String profile(Model model) {
        return "profile";
    }

    // muestra proposal.html
    @GetMapping("/proposal")
    public String proposal(Model model) {
        return "proposal";
    }

    // muestra single_game_setup.html
    @GetMapping("/single_game_setup")
    public String single_game_setup(Model model) {
        return "single_game_setup";
    }

    // llama a la API de OpenTDB con categoría/dificultad/número
    // de preguntas del formulario, y renderiza single_game.html con los datos de las preguntas
    @PostMapping("/start_single_game")
    public String startSingleGame(@ModelAttribute GameSetupDTO setup, Model model) {

        String url = "https://opentdb.com/api.php?amount=" + setup.getQuestionCount();

        if (setup.getCategory() != null && !setup.getCategory().isEmpty()) {
            url += "&category=" + setup.getCategory();
        }

        if (setup.getDifficulty() != null && !setup.getDifficulty().isEmpty()) {
            url += "&difficulty=" + setup.getDifficulty().toLowerCase();
        }

        RestTemplate rest = new RestTemplate();
        Map<String, Object> response;

        try {
            response = rest.getForObject(url, Map.class);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            response = Map.of("response_code", 5, "results", List.of());
        } catch (RestClientException ex) {
            response = Map.of("response_code", 1, "results", List.of());
        }

        model.addAttribute("questionData", response);
        return "single_game";
    }
}
