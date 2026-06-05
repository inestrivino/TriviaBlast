package es.ucm.fdi.iw.controller;

import java.util.Arrays;
import java.util.List;

//ENUMERADO PARA GESTIONAR LAS CATEGORÍAS DE TRIVIA
//SE USA SOBRETODO PARA POBLAR DINÁMICAMENTE LOS "GAME SETUP"
//Y COMO REFERENCIA PARA EL MULTI_GAME, ETC

public enum TriviaCategory {
    //el id es el de llamada para la API, el label es el nombre legible
    GENERAL_KNOWLEDGE(9, "General Knowledge"),
    ENTERTAINMENT_BOOKS(10, "Entertainment: Books"),
    ENTERTAINMENT_FILM(11, "Entertainment: Film"),
    ENTERTAINMENT_MUSIC(12, "Entertainment: Music"),
    ENTERTAINMENT_MUSICALS(13, "Entertainment: Musicals & Theatres"),
    ENTERTAINMENT_TV(14, "Entertainment: Television"),
    VIDEO_GAMES(15, "Entertainment: Video Games"),
    BOARD_GAMES(16, "Entertainment: Board Games"),
    SCIENCE_NATURE(17, "Science & Nature"),
    SCIENCE_COMPUTERS(18, "Science: Computers"),
    SCIENCE_MATH(19, "Science: Mathematics"),
    MYTHOLOGY(20, "Mythology"),
    SPORTS(21, "Sports"),
    GEOGRAPHY(22, "Geography"),
    HISTORY(23, "History"),
    POLITICS(24, "Politics"),
    ART(25, "Art"),
    CELEBRITIES(26, "Celebrities"),
    ANIMALS(27, "Animals"),
    VEHICLES(28, "Vehicles"),
    COMICS(29, "Comics"),
    GADGETS(30, "Gadgets"),
    ANIME_MANGA(31, "Anime & Manga"),
    CARTOONS(32, "Cartoons");

    private final int id;
    private final String label;

    TriviaCategory(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public static List<TriviaCategory> getAll() {
        return Arrays.asList(values());
    }

    public static TriviaCategory fromId(int id) {
        return Arrays.stream(values())
                .filter(c -> c.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid category id: " + id));
    }
}