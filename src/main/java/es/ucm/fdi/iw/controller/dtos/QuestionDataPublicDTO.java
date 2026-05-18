package es.ucm.fdi.iw.controller.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
* DATA TRANSFER OBJECTS

* Objetos simples (sin lógica ni JPA) que sirven para transferir datos
* entre el frontend y el backend de forma estructurada.
*
* Versión SEGURA de una pregunta (sin la respuesta correcta)
* Esta SÍ se manda al navegador en el modelo de Thymeleaf
* Campos: id, question, answers (lista mezclada)
*/

@Data
@AllArgsConstructor
public class QuestionDataPublicDTO {
    private int id;               
    private String question; 
    private List<String> answers; 
}