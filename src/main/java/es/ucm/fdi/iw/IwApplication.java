package es.ucm.fdi.iw;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
public class IwApplication {

    public static void main(String[] args) {
        SpringApplication.run(IwApplication.class, args);
    }

    @Bean
    public CommandLineRunner initUsers(EntityManager entityManager, PasswordEncoder passwordEncoder) {
        return new CommandLineRunner() {
            @Override
            @Transactional
            public void run(String... args) throws Exception {
                Long adminCount = (Long) entityManager
                        .createQuery("SELECT COUNT(u) FROM User u WHERE u.username = :username")
                        .setParameter("username", "a")
                        .getSingleResult();

                if (adminCount == 0) {
                    User admin = new User();
                    admin.setUsername("a");
                    admin.setPassword(passwordEncoder.encode("aa"));
                    admin.setEmail("admin@example.com");
                    admin.setRoles("ADMIN,USER");
                    admin.setEnabled(true);
                    entityManager.persist(admin);

                    User user = new User();
                    user.setUsername("b");
                    user.setPassword(passwordEncoder.encode("aa"));
                    user.setEmail("user@example.com");
                    user.setRoles("USER");
                    user.setEnabled(true);
                    entityManager.persist(user);

                    System.out.println("Inserted admin and default user");
                } else {
                    System.out.println("Admin already exists, skipping user initialization");
                }
            }
        };
    }
}