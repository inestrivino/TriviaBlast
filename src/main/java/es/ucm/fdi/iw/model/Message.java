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
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.SequenceGenerator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lombok.Data;
import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * A message that users can send each other.
 *
 */
@Entity
@Data
public class Message implements Transferable<Message.Transfer> {

	private static Logger log = LogManager.getLogger(Message.class);

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
	@ManyToOne
	private User sender;
	@ManyToOne(optional = false)
	@JoinColumn(name = "game_id") // obligatorio
	private Game game; // this serves as the "topic" of the message, i.e. the room where it was sent

	@Column(columnDefinition = "TEXT", nullable = false)
	private String text;

	@Column(nullable = false)
	private LocalDateTime dateSent = LocalDateTime.now();

	@Column(nullable = false)
	private boolean adminOnly = false; // if true, the message is addressed to the admins of the game

	/**
	 * Objeto para persistir a/de JSON
	 * 
	 * @author mfreire
	 */
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
