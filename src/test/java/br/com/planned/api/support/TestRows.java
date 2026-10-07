package br.com.planned.api.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts rows straight into the database, bypassing the services, for schema and query tests. */
public final class TestRows {

	/**
	 * {@code CREATED_AT} and {@code UPDATED_AT} of every inserted row. It's fixed and earlier than
	 * any test clock, so a later write through the API (which stamps {@code UPDATED_AT} from the
	 * test clock) always satisfies {@code UPDATED_AT >= CREATED_AT}, whatever the real time is.
	 */
	public static final Instant INSERTED_AT = Instant.parse("2000-01-01T00:00:00Z");

	private TestRows() {
	}

	/** Inserts a {@code USER} account with the given email and returns its id. */
	public static UUID insertUser(JdbcTemplate jdbc, String email) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO USERS (ID, NAME, EMAIL, PASSWORD, ROLE_ID, CREATED_AT, UPDATED_AT)
				VALUES (?, 'Test', ?, 'hash', (SELECT ID FROM ROLE WHERE NAME = 'USER'), ?, ?)
				""", id, email, insertedAt(), insertedAt());
		return id;
	}

	/** Inserts a {@code MEDIUM} / {@code TODO} task and returns its id. {@code parentId} may be null. */
	public static UUID insertTask(JdbcTemplate jdbc, UUID userId, UUID parentId, String title) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO TASK (ID, USER_ID, PARENT_TASK_ID, TITLE, DESCRIPTION, PRIORITY_ID, STATUS_ID,
				                  CREATED_AT, UPDATED_AT)
				VALUES (?, ?, ?, ?, 'description',
				        (SELECT ID FROM PRIORITIES WHERE NAME = 'MEDIUM'),
				        (SELECT ID FROM TASK_STATUS WHERE NAME = 'TODO'),
				        ?, ?)
				""", id, userId, parentId, title, insertedAt(), insertedAt());
		return id;
	}

	/** {@link #INSERTED_AT} as the UTC wall-clock time the {@code TIMESTAMP} columns hold. */
	private static LocalDateTime insertedAt() {
		return LocalDateTime.ofInstant(INSERTED_AT, ZoneOffset.UTC);
	}

	/**
	 * Deletes the given users and all their tasks. Use it after tests that commit rows: other
	 * tests delete every user, which fails while a task still references one.
	 */
	public static void deleteUsersAndTasks(JdbcTemplate jdbc, UUID... userIds) {
		for (UUID userId : userIds) {
			jdbc.update("DELETE FROM TASK WHERE USER_ID = ?", userId);
			jdbc.update("DELETE FROM USERS WHERE ID = ?", userId);
		}
	}
}
