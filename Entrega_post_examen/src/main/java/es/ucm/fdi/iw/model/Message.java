package es.ucm.fdi.iw.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;

import lombok.Data;
import lombok.Getter;
import lombok.AllArgsConstructor;

/**
* ENTIDAD MENSAJE DE CHAT

* Representa un mensaje enviado en el chat de una partida
* Implementa Transferable<Message.Transfer> para poder serializarse
* a JSON de forma segura (sin exponer relaciones JPA circulares)
*
* Los mensajes se eliminan en cascada si se elimina la partida
* (cascade = CascadeType.ALL, orphanRemoval = true en Game.messages).
*/

@Entity
@Data
public class Message implements Transferable<Message.Transfer> {

	/*
	 * SCHEMA:
	 * - ID
	 * - SENDER
	 * - GAME (chat room)
	 * - TEXT
	 * - DATE_SENT
	 * - ADMIN_ONLY (true -> only admins receive it)
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
	@SequenceGenerator(name = "gen", sequenceName = "gen")
	private long id;
	// usuario que envió el mensaje (ManyToOne → User)
	@ManyToOne
	private User sender;

	// partida a la que pertenece el mensaje (ManyToOne → Game)
	@ManyToOne
	@JoinColumn(name = "game_id") // obligatorio
	private Game game; // this serves as the "topic" of the message, i.e. the room where it was sent

	@Column(columnDefinition = "TEXT", nullable = false)
	private String text;

	@Column(nullable = false)
	private LocalDateTime dateSent = LocalDateTime.now();

	// si true, solo visible para admins (mensajes del sistema)
	@Column(nullable = false)
	private boolean adminOnly = false; // if true, the message is addressed to the admins of the game

	// Clase interna Transfer: Objeto plano (sin relaciones JPA) que se serializa a JSON para
	// enviarse por WebSocket o como respuesta de la API
	@Getter
	@AllArgsConstructor
	public static class Transfer {
		private String from;
		private String sent;
		private String game;
		private String text;
		private boolean adminOnly;
		long id;

		public Transfer(Message m) {
			this.from = m.getSender().getUsername();
			this.game = m.getGame() == null ? "null" : m.getGame().getCode();
			this.sent = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(m.getDateSent());
			this.text = m.getText();
			this.adminOnly = m.isAdminOnly();
			this.id = m.getId();
		}
	}

	@Override
	public Transfer toTransfer() {
		return new Transfer(this);
	}
}
