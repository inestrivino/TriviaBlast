package es.ucm.fdi.iw.controller.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
* DATA TRANSFER OBJECTS 

* Objetos simples (sin lógica ni JPA) que sirven para transferir datos
* entre el frontend y el backend de forma estructurada.
*
* Devuelve el resultado de comprobar la respuesta
* Campos: correct (boolean), correctAnswer (String)
* Usado en: GameController.checkAnswer() como valor de retorno JSON
*/

@Data
@AllArgsConstructor
public class AnswerResDTO {
    private boolean correct;
    private String correctAnswer;
}
