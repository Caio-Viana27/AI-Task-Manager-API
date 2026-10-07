package br.com.planned.api.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.planned.api.support.IntegrationTest;
import br.com.planned.api.support.TestRows;

/**
 * The task schema migration (PLAN §1, wave 2 D1 and D5), checked straight against the database.
 * Each test runs in a transaction that is rolled back.
 */
@Transactional
class TaskSchemaTest extends IntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	private UUID userId;

	@BeforeEach
	void insertUser() {
		userId = TestRows.insertUser(jdbc, "schema@example.com");
	}

	@Test
	void blankTitleIsRejected() {
		assertThatThrownBy(() -> TestRows.insertTask(jdbc, userId, null, " \t "))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_task_title_not_blank");
	}

	@Test
	void nullUserIsRejected() {
		assertThatThrownBy(() -> TestRows.insertTask(jdbc, null, null, "Task"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("user_id");
	}

	@Test
	void nullStatusIsRejected() {
		UUID id = TestRows.insertTask(jdbc, userId, null, "Task");

		assertThatThrownBy(() -> jdbc.update("UPDATE TASK SET STATUS_ID = NULL WHERE ID = ?", id))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("status_id");
	}

	@Test
	void nullPriorityIsRejected() {
		UUID id = TestRows.insertTask(jdbc, userId, null, "Task");

		assertThatThrownBy(() -> jdbc.update("UPDATE TASK SET PRIORITY_ID = NULL WHERE ID = ?", id))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("priority_id");
	}

	@Test
	void taskCannotBeItsOwnParent() {
		UUID id = TestRows.insertTask(jdbc, userId, null, "Task");

		assertThatThrownBy(() -> jdbc.update("UPDATE TASK SET PARENT_TASK_ID = ID WHERE ID = ?", id))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_task_not_own_parent");
	}

	@Test
	void taskSubtaskTableNoLongerExists() {
		Integer tables = jdbc.queryForObject(
				"SELECT count(*) FROM information_schema.tables WHERE table_name = 'task_subtask'", Integer.class);

		assertThat(tables).isZero();
	}

	@Test
	void newColumnsHaveTheirDefaults() {
		UUID id = TestRows.insertTask(jdbc, userId, null, "Task");

		assertThat(jdbc.queryForObject("SELECT POSITION FROM TASK WHERE ID = ?", Integer.class, id)).isZero();
		assertThat(jdbc.queryForObject("SELECT DUE_DATE FROM TASK WHERE ID = ?", Object.class, id)).isNull();
	}

	@Test
	void deletingTopLevelTaskRemovesItsWholeSubtree() {
		UUID root = TestRows.insertTask(jdbc, userId, null, "Root");
		UUID child = TestRows.insertTask(jdbc, userId, root, "Child");
		UUID grandchild = TestRows.insertTask(jdbc, userId, child, "Grandchild");
		TestRows.insertTask(jdbc, userId, grandchild, "Great-grandchild");
		TestRows.insertTask(jdbc, userId, root, "Second child");
		UUID other = TestRows.insertTask(jdbc, userId, null, "Other root");

		int deleted = jdbc.update("DELETE FROM TASK WHERE ID = ?", root);

		assertThat(deleted).isOne();
		assertThat(jdbc.queryForList("SELECT ID FROM TASK WHERE USER_ID = ?", UUID.class, userId))
				.containsExactly(other);
	}
}
