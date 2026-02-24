package es.ucm.fdi.iw.model;

import java.util.Set;

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

    @ElementCollection(targetClass = Category.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "game_categories", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "category")
    private Set<Category> categories;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    // HOW TO GENERATE?
    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private int numPlayers;

    @Column(columnDefinition = "TEXT")
    private String internalState; // this is later turned into JSON

    @Enumerated(EnumType.STRING)
    private State gameState;

    @ManyToOne
    @JoinColumn(name = "host_id", nullable = false)
    private User host;
}