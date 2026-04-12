package es.ucm.fdi.iw.controller.DTOs;

import lombok.Data;

@Data
public class AnswerReqDTO {
    private int questionId;
    private String answer;
    private int userId;
}
