package es.ucm.fdi.iw.controller.DTOs;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
* DATA TRANSFER OBJECTS 

* Objetos simples (sin lógica ni JPA) que sirven para transferir datos
* entre el frontend y el backend de forma estructurada.
*
* Versión COMPLETA de una pregunta (con la respuesta correcta)
* Se guarda en sesión del servidor, NUNCA se manda al navegador
* Campos: id, question, answers (lista mezclada), correctAnswer
*/


@Data
@AllArgsConstructor
public class QuestionDataPrivateDTO {
    private int id;               
    private String question; 
    private List<String> answers; 
    private String correctAnswer;
}