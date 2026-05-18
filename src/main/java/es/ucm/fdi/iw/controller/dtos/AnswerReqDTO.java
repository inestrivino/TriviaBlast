package es.ucm.fdi.iw.controller.dtos;

import lombok.Data;

@Data
public class AnswerReqDTO {
    private int questionId;
    private String answer;
}
