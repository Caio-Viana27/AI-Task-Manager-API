package br.com.planned.api.support;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts rows straight into the database, bypassing the services, for schema and query tests. */
public final class TestRows {

	private TestRows() {
	}

	/** Inserts a {@code USER} account with the given email and returns its id. */
	public static UUID insertUser(JdbcTemplate jdbc, String email) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO USERS (ID, NAME, EMAIL, PASSWORD, ROLE_ID, CREATED_AT, UPDATED_AT)
				VALUES (?, 'Test', ?, 'hash', (SELECT ID FROM ROLE WHERE NAME = 'USER'), now(), now())
				""", id, email);
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
				        now(), now())
				""", id, userId, parentId, title);
		return id;
	}
}
