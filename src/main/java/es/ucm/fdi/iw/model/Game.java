package es.ucm.fdi.iw.model;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "game_gen")
    @SequenceGenerator(name = "game_gen", sequenceName = "game_seq", allocationSize = 1)
    private long id;

    @Column(nullable = false)
    private int numQuestions;

    @ElementCollection
    @CollectionTable(name = "game_categories", joinColumns = @JoinColumn(name = "game_id"))
    private Set<String> categories;

    @Column(nullable = false)
    private String difficulty;

    // HOW TO GENERATE?
    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private int numPlayers;

    @Column(columnDefinition = "TEXT")
    private String internalState; // this is later turned into JSON

    private String gameState;

    @ManyToOne
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @OneToMany(mappedBy = "game")
    private List<Player> players = new ArrayList<>();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true) // los mensajes pertenecen completamente al juego, si se borra el juego se borran los mensajes
    private List<Message> messages = new ArrayList<>();

}