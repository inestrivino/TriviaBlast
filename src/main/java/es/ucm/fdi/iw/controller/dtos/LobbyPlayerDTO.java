package es.ucm.fdi.iw.controller.dtos;

public class LobbyPlayerDTO {
    private Long id;
    private String username;

    public LobbyPlayerDTO(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
}
