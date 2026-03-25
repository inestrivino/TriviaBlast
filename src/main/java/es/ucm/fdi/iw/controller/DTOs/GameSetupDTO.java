package es.ucm.fdi.iw.controller.DTOs;

// THIS OBJECT IS USED AS A WAY FOR THE CONTROLLER TO HANDLE THE SETTINGS FOR A NEW GAME

public class GameSetupDTO {

    private String category;      
    private String difficulty;
    private int questionCount; 

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }
}
