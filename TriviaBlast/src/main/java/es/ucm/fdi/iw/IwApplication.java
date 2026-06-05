package es.ucm.fdi.iw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
* PUNTO DE ENTRADA DE LA APLICACIÓN

* Es la clase main de Spring Boot
* Cuando ejecutas "mvn spring-boot:run", Spring arranca aquí
*/

@SpringBootApplication
public class IwApplication {

    // arranca todo el contexto de Spring Boot
    public static void main(String[] args) {
        SpringApplication.run(IwApplication.class, args);
    }
}