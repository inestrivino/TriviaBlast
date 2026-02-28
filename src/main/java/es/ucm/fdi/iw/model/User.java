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
    @NamedQuery(name = "User.topics", query = "SELECT t.key "
        + "FROM Topic t JOIN t.members u "
        + "WHERE u.id = :id")
})
@Table(name = "IWUser")
public class User implements Transferable<User.Transfer> {
    public enum Role {
        USER,
        ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    public enum Visibility {
        PUBLIC,
        HIDDEN
    }

    @Enumerated(EnumType.STRING)
    private Visibility estadoVisibilidad = Visibility.PUBLIC;

    private Integer totalPoints = 0;

    private String avatar = "default-pic.png";

    private boolean enabled = true;


    private String roles = "USER";


    @OneToMany
    @JoinColumn(name = "sender_id")
    private List<Message> sent = new ArrayList<>();

    @OneToMany
    @JoinColumn(name = "recipient_id")
    private List<Message> received = new ArrayList<>();


    @ManyToMany(mappedBy = "members")
    private List<Topic> groups = new ArrayList<>();



    // Games created by this user
    @OneToMany(mappedBy = "host")
    private List<Game> partidasCreadas = new ArrayList<>();

    // Games played by this user
    @ManyToMany
    @JoinTable(
        name = "user_games",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "game_id")
    )
    private List<Game> partidasJugadas = new ArrayList<>();


    public boolean hasRole(Role role) {
        if (roles == null) return false;
        String roleName = role.name();
        return Arrays.asList(roles.split(",")).contains(roleName);
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
        StringBuilder gs = new StringBuilder();
        for (Topic g : groups) {
            gs.append(g.getName()).append(", ");
        }
        return new Transfer(
            id,
            username,
            received.size(),
            sent.size(),
            gs.toString()
        );
    }

    @Override
    public String toString() {
        return toTransfer().toString();
    }
}