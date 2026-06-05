package es.ucm.fdi.iw.controller.dtos;

import lombok.Data;

//OBJETO PARA MANEJAR LA RESPUESTA A UNA PREGUNTA
@Data
public class AnswerReqDTO {
    private int questionId;
    private String answer;
    private boolean doubleOrNothing;
}
