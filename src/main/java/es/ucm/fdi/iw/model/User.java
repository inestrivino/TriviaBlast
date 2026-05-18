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
* ENTIDAD USUARIO 

* Representa un usuario registrado en el sistema. Se mapea a la tabla
* "IWUser" en la BD (nombre cambiado para evitar conflicto con la
* palabra reservada "user" en H2)
*/


/**
 * An authorized user of the system.
 */
@Entity
@Data
@NoArgsConstructor
@NamedQueries({
    // busca usuario activo por username (usado en login)
    @NamedQuery(name = "User.byUsername", query = "SELECT u FROM User u "
        + "WHERE u.username = :username AND u.enabled = TRUE"),
    // cuenta usuarios con ese username (para validar unicidad)
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

    private Integer totalPoints = 0;

    private String avatar = "default-pic.png";

    // cuenta desactivada (no puede hacer login)
    private boolean enabled = true;

    private String roles = "USER";

    // mensajes enviados por este usuario (relación 1:N)
    @OneToMany(mappedBy = "sender")
    private List<Message> sent = new ArrayList<>();


    // Games created by this user
    // partidas de las que es host (relación 1:N con Game)
    @OneToMany(mappedBy = "host")
    private List<Game> partidasCreadas = new ArrayList<>();

    // Games played by this user
    // registros en partidas jugadas (relación 1:N con Player)
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

    // convierte a User.Transfer para serializar a JSON sin exponer la contraseña
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