package es.ucm.fdi.iw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import es.ucm.fdi.iw.model.User;

@SpringBootApplication
public class IwApplication {

    public static void main(String[] args) {
        SpringApplication.run(IwApplication.class, args);
    }

    @Bean
    @Transactional
    public org.springframework.boot.CommandLineRunner initUsers(EntityManager entityManager) {
        return args -> {

            Long count = (Long) entityManager
                .createQuery("SELECT COUNT(u) FROM User u")
                .getSingleResult();

            if (count == 0) {

                User u1 = new User();
                u1.setUsername("user1");
                u1.setPassword("pass");

                User u2 = new User();
                u2.setUsername("user2");
                u2.setPassword("pass");

                entityManager.persist(u1);
                entityManager.persist(u2);
            }
        };
    }
}