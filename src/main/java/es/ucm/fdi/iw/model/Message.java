package es.ucm.fdi.iw.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
	 * - TOPIC (ROOM)
	 * - TEXT
	 * - DATASENT
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
	@SequenceGenerator(name = "gen", sequenceName = "gen")
	private long id;
	@ManyToOne
	private User sender;
	@ManyToOne
	private Topic topic; // We consider this to be a game room

	@Column(columnDefinition = "TEXT", nullable = false)
	private String text;

	@Column(nullable = false)
	private LocalDateTime dateSent;

	/**
	 * Objeto para persistir a/de JSON
	 * 
	 * @author mfreire
	 */
	@Getter
	@AllArgsConstructor
	public static class Transfer {
		private String from;
		private String to;
		private String sent;
		private String received;
		private String topic;
		private String text;
		long id;

		public Transfer(Message m) {
			this.from = m.getSender().getUsername();
			this.topic = m.getTopic() == null ? "null" : m.getTopic().getName();
			this.sent = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(m.getDateSent());
			this.text = m.getText();
			this.id = m.getId();
		}
	}

	@Override
	public Transfer toTransfer() {
		return new Transfer(this);
	}
}
