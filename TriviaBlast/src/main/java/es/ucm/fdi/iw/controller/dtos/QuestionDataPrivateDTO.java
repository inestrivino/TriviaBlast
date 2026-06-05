package es.ucm.fdi.iw.controller.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

// OBJETO PARA MANEJAR UNA PREGUNTA DE CARA AL SERVIDOR
@Data
@AllArgsConstructor
public class QuestionDataPrivateDTO {
    private int id;               
    private String question; 
    private List<String> answers; 
    private String correctAnswer;
}