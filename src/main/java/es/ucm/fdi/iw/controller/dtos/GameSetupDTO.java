package es.ucm.fdi.iw.controller.dtos;

/**
* DATA TRANSFER OBJECTS 

* Objetos simples (sin lógica ni JPA) que sirven para transferir datos
* entre el frontend y el backend de forma estructurada.
*
* Recoge los parámetros del formulario de configuración de partida
* Campos: category (String), difficulty (String), questionCount (int)
* Usado en: RootController.startSingleGame() y GameController.startGame()
* como @ModelAttribute (formulario HTML)
*/

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
