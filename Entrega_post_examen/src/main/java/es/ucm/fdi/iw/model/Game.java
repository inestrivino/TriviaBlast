package es.ucm.fdi.iw.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import es.ucm.fdi.iw.controller.TriviaCategory;

import java.util.List;

import jakarta.persistence.*;
import lombok.Data;

/**
 * ENTIDAD PARTIDA
 * 
 * Representa una partida multijugador
 * (las partidas singleplayer no necesitan guardarse en la bd)
 * Se mapea a la tabla "Game"
 */

// busca una partida por su código único
@NamedQueries({
        @NamedQuery(name = "Game.byCode", query = "SELECT g FROM Game g "
                + "WHERE g.code = :code"),
})
@Entity
@Data
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "game_gen")
    @SequenceGenerator(name = "game_gen", sequenceName = "game_seq", allocationSize = 1)
    private long id;

    @ElementCollection(targetClass = TriviaCategory.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "game_categories", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "category")
    private Set<TriviaCategory> categories = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "game_casillas_comodin", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "casilla")
    private Set<Integer> casillasComodin = new HashSet<>();

    
    @Column(nullable = false)
    private String difficulty;

    // Note: code attribute being unique is not ideal as it means that there is a
    // reachable limit in number of possible codes. However, JPA does not support
    // (to our knowledge) conditional keys, and this system allows for 2.1 billion
    // different codes which is enough for our application's current uses
    @Column(unique = true, nullable = false)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String internalState; // this is later turned into JSON

    private String gameState;

    private LocalDateTime date;

    @ManyToOne
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true) // if game is deleted then messages
                                                                                   // are deleted too
    private List<Message> messages = new ArrayList<>();

    @OneToMany(mappedBy = "game")
    private List<Player> players = new ArrayList<>();
}