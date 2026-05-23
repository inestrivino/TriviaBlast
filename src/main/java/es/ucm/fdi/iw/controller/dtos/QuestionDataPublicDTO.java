package es.ucm.fdi.iw.controller.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

// OBJETO PARA MANEJAR UNA PREGUNTA DE CARA AL CLIENTE (NO MENCIONA LA RESPUESTA CORRECTA)
@Data
@AllArgsConstructor
public class QuestionDataPublicDTO {
    private int id;               
    private String question; 
    private List<String> answers; 
}