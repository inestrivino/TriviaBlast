package es.ucm.fdi.iw.controller.DTOs;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuestionDataPublicDTO {
    private int id;               
    private String question; 
    private List<String> answers; 
}