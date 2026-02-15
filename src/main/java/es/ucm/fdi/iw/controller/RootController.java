package es.ucm.fdi.iw.controller;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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

    @GetMapping("/proposal")
    public String proposal(Model model) {
        return "proposal";
    }

    @GetMapping("/authors")
    public String authors(Model model) {
        return "authors";
    }

    @GetMapping("/multi_game_setup")
    public String multi_game_setup(Model model) {
        return "multi_game_setup";
    }

    @GetMapping("/join_game")
    public String join_game(Model model) {
        return "join_game";
    }

    @GetMapping("/multi_game")
    public String multi_game(Model model) {
        return "multi_game";
    }

    @GetMapping("/multi_victoryscr")
    public String multi_victoryscr(Model model) {
        return "multi_victoryscr";
    }

    @GetMapping("/scoreboard")
    public String scoreboard(Model model) {
        return "scoreboard";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        return "profile";
    }

    @GetMapping("/single_game")
    public String single_game(Model model) {
        //TO DO: IN THE FUTURE WE WILL HAVE A USER CHOICES OBJECT THAT WILL CHANGE THE API CALL BASED ON WHAT THE USER WANTS FOR THE GAME
        RestTemplate rest = new RestTemplate();
        String url = "https://opentdb.com/api.php?amount=1";

        Map<String, Object> response;

        try {
            response = rest.getForObject(url, Map.class);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            // API rate limit hit, returning a dummy object
            response = Map.of(
                    "response_code", 5,
                    "results", List.of());
        } catch (RestClientException ex) {
            // Other errors:
            response = Map.of(
                    "response_code", 1,
                    "results", List.of());
        }

        model.addAttribute("questionData", response);
        return "single_game";
    }

}
