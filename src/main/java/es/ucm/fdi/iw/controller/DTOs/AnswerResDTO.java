package es.ucm.fdi.iw.controller.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnswerResDTO {
    private boolean correct;
    private String correctAnswer;
}
