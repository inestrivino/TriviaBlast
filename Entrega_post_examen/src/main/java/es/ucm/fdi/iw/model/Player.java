package es.ucm.fdi.iw.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

/**
* ENTIDAD JUGADOR EN partida

* Tabla de unión entre User y Game. Representa la participación de
* un usuario concreto en una partida concreta y sus puntos en ella
*/

@NamedQueries({
    @NamedQuery(name = "Player.lastFiveByUser",
        query = "SELECT p FROM Player p WHERE p.user.id = :userId ORDER BY p.date DESC")
})

@Entity
@Data
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_gen")
    @SequenceGenerator(name = "player_gen", sequenceName = "player_seq", allocationSize = 1)
    private long id;

    @Column(nullable = false)
    private int points;

    // el usuario jugador (ManyToOne → User)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // la partida (ManyToOne → Game)
    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false)
    private int finalBoardPosition;

    @Column(nullable = false)
    private int finalRank;

    @Column(nullable = false)
    private LocalDateTime date;
}
