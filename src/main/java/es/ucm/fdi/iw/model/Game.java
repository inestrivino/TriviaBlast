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

    // Note: code attribute being unique is not ideal as it means that there is a reachable limit in number of possible codes. However, JPA does not support (to our knowledge) conditional keys, and this system allows for 2.1 billion different codes which is enough for our application's current uses
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

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true) //if game is deleted then messages are deleted too
    private List<Message> messages = new ArrayList<>();

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    @PrePersist
    public void generateCode() {
        if (this.code == null) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
            }
            this.code = sb.toString();
        }
    }
}