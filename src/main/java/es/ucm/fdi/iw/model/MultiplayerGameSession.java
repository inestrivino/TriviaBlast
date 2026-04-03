package es.ucm.fdi.iw.model;

import java.util.*;

import es.ucm.fdi.iw.controller.DTOs.GameSetupDTO;
import es.ucm.fdi.iw.controller.DTOs.QuestionDataPrivateDTO;

// this is for everyone to have the same session of questions

public class MultiplayerGameSession {

    private String code;

    private GameSetupDTO setup;

    private List<QuestionDataPrivateDTO> questions;

    private List<User> players = new ArrayList<>();

    private Map<Long, Integer> scores = new HashMap<>();

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public GameSetupDTO getSetup() {
        return setup;
    }

    public void setSetup(GameSetupDTO setup) {
        this.setup = setup;
    }

    public List<QuestionDataPrivateDTO> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionDataPrivateDTO> questions) {
        this.questions = questions;
    }

    public List<User> getPlayers() {
        return players;
    }

    public Map<Long, Integer> getScores() {
        return scores;
    }
}