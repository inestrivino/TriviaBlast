package es.ucm.fdi.iw.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An authorized user of the system.
 */
@Entity
@Data
@NoArgsConstructor
@NamedQueries({
    @NamedQuery(name = "User.byUsername", query = "SELECT u FROM User u "
        + "WHERE u.username = :username AND u.enabled = TRUE"),
    @NamedQuery(name = "User.hasUsername", query = "SELECT COUNT(u) "
        + "FROM User u "
        + "WHERE u.username = :username"),
})
@Table(name = "IWUser")
public class User implements Transferable<User.Transfer> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable=false, unique = true)
    private String email;

    private String visibilityState = "PUBLIC";

    private Integer totalPoints = 0;

    private String avatar = "default-pic.png";

    private boolean enabled = true;

    private String roles = "USER";


    @OneToMany(mappedBy = "sender")
    private List<Message> sent = new ArrayList<>();


    // Games created by this user
    @OneToMany(mappedBy = "host")
    private List<Game> partidasCreadas = new ArrayList<>();

    // Games played by this user
    @OneToMany(mappedBy = "user")
    private List<Player> players = new ArrayList<>();
    

    public boolean hasRole(String role) {
        if (roles == null) return false;
        return Arrays.asList(roles.split(",")).contains(role);
    }

    @Getter
    @AllArgsConstructor
    public static class Transfer {
        private long id;
        private String username;
        private int totalReceived;
        private int totalSent;
        private String groups;
    }

    @Override
    public Transfer toTransfer() {
        return new Transfer(
            id,
            username,
            0,
            sent.size(),
            ""
        );
    }

    @Override
    public String toString() {
        return toTransfer().toString();
    }
}