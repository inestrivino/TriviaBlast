package es.ucm.fdi.iw.controller.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

//OBJETO PARA MANEJAR EL RESULTADO DE UNA RESPUESTA A PREGUNTA
@Data
@AllArgsConstructor
public class AnswerResDTO {
    private boolean correct;
    private String correctAnswer;
}
