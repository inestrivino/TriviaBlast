package es.ucm.fdi.iw;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@SpringBootApplication
public class IwApplication {

    public static void main(String[] args) {
        SpringApplication.run(IwApplication.class, args);
    }

    @Bean
    @Transactional
    public org.springframework.boot.CommandLineRunner initUsers(EntityManager entityManager) {
        return new CommandLineRunner() {
            @Override
            @Transactional
            public void run(String... args) throws Exception {
                Long count = (Long) entityManager
                        .createQuery("SELECT COUNT(u) FROM User u")
                        .getSingleResult();

                if (count == 0) {

                    User u1 = new User();
                    u1.setUsername("user1");
                    u1.setPassword("pass");
                    u1.setEmail("asd2@gmail.com");

                    User u2 = new User();
                    u2.setUsername("user2");
                    u2.setPassword("pass");
                    u2.setEmail("asd@gmail.com");

                    entityManager.persist(u1);
                    entityManager.persist(u2);
                }
            }
        };
    }
}
