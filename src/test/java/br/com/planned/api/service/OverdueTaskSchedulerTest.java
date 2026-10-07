package br.com.planned.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;

import br.com.planned.api.support.IntegrationTest;
import br.com.planned.api.support.MutableClock;
import br.com.planned.api.support.TestRows;

/**
 * The nightly overdue job (PLAN §2, Status rules). The clock is fixed at {@link #NOW}: 22:00 of
 * {@link #TODAY} in {@code America/Sao_Paulo}, when the UTC date is already the next day, so the
 * tests also check that "today" comes from {@code app.timezone}.
 */
class OverdueTaskSchedulerTest extends IntegrationTest {

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	private static final Instant NOW = Instant.parse("2026-10-08T01:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
	private static final Timestamp CREATED = Timestamp.from(Instant.parse("2026-09-01T12:00:00Z"));
	private static final MutableClock CLOCK = new MutableClock(NOW, ZONE);

	@TestBean
	private Clock clock;

	static Clock clock() {
		return CLOCK;
	}

	@Autowired
	private OverdueTaskScheduler scheduler;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID ana;
	private UUID bob;

	@BeforeEach
	void setUp() {
		CLOCK.set(NOW);
		ana = TestRows.insertUser(jdbc, "ana-overdue@example.com");
		bob = TestRows.insertUser(jdbc, "bob-overdue@example.com");
	}

	@AfterEach
	void cleanUp() {
		TestRows.deleteUsersAndTasks(jdbc, ana, bob);
	}

	@Test
	void lateTodoAndInProgressTasks_becomeOverdue_acrossUsersAndAtEveryDepth() {
		UUID todo = insert(ana, null, "TODO", TODAY.minusDays(1));
		UUID inProgress = insert(bob, null, "IN_PROGRESS", TODAY.minusDays(30));
		UUID root = insert(ana, null, "DONE", null);
		UUID child = insert(ana, root, "TODO", null);
		UUID grandchild = insert(ana, child, "IN_PROGRESS", TODAY.minusDays(2));

		int changed = scheduler.markOverdueTasks();

		assertThat(changed).isEqualTo(3);
		for (UUID task : new UUID[] { todo, inProgress, grandchild }) {
			assertThat(status(task)).as("status of %s", task).isEqualTo("OVERDUE");
			assertThat(updatedAt(task)).as("updatedAt of %s", task).isNotEqualTo(CREATED);
		}
	}

	@Test
	void otherTasks_areUnchanged_includingUpdatedAt() {
		UUID dueToday = insert(ana, null, "TODO", TODAY);
		UUID future = insert(ana, null, "IN_PROGRESS", TODAY.plusDays(1));
		UUID done = insert(ana, null, "DONE", TODAY.minusDays(1));
		UUID noDueDate = insert(ana, null, "TODO", null);
		UUID alreadyOverdue = insert(bob, null, "OVERDUE", TODAY.minusDays(3));

		scheduler.markOverdueTasks();

		assertUnchanged(dueToday, "TODO");
		assertUnchanged(future, "IN_PROGRESS");
		assertUnchanged(done, "DONE");
		assertUnchanged(noDueDate, "TODO");
		assertUnchanged(alreadyOverdue, "OVERDUE");
	}

	@Test
	void secondRun_changesNothing() {
		insert(ana, null, "TODO", TODAY.minusDays(1));
		scheduler.markOverdueTasks();

		assertThat(scheduler.markOverdueTasks()).isZero();
	}

	private void assertUnchanged(UUID task, String expectedStatus) {
		assertThat(status(task)).as("status of %s", task).isEqualTo(expectedStatus);
		assertThat(updatedAt(task)).as("updatedAt of %s", task).isEqualTo(CREATED);
	}

	private UUID insert(UUID user, UUID parent, String status, LocalDate dueDate) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO TASK (ID, USER_ID, PARENT_TASK_ID, TITLE, DESCRIPTION, DUE_DATE, PRIORITY_ID, STATUS_ID,
				                  CREATED_AT, UPDATED_AT)
				VALUES (?, ?, ?, 'Task', 'description', ?,
				        (SELECT ID FROM PRIORITIES WHERE NAME = 'MEDIUM'),
				        (SELECT ID FROM TASK_STATUS WHERE NAME = ?),
				        ?, ?)
				""", id, user, parent, dueDate, status, CREATED, CREATED);
		return id;
	}

	private String status(UUID task) {
		return jdbc.queryForObject(
				"SELECT S.NAME FROM TASK T JOIN TASK_STATUS S ON S.ID = T.STATUS_ID WHERE T.ID = ?", String.class, task);
	}

	private Timestamp updatedAt(UUID task) {
		return jdbc.queryForObject("SELECT UPDATED_AT FROM TASK WHERE ID = ?", Timestamp.class, task);
	}
}
