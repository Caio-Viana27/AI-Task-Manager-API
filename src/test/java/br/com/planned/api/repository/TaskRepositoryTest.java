package br.com.planned.api.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.planned.api.entity.Complexity;
import br.com.planned.api.entity.Priority;
import br.com.planned.api.entity.Task;
import br.com.planned.api.entity.TaskStatus;
import br.com.planned.api.entity.User;
import br.com.planned.api.service.LookupService;
import br.com.planned.api.support.IntegrationTest;
import br.com.planned.api.support.TestRows;

/** Each test runs in a transaction that is rolled back, so no rows leak into other tests. */
@Transactional
class TaskRepositoryTest extends IntegrationTest {

	private static final Instant CREATED_AT = Instant.parse("2026-10-07T12:34:56.123456Z");

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LookupService lookupService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID ana;
	private UUID bob;

	@BeforeEach
	void insertUsers() {
		ana = TestRows.insertUser(jdbc, "ana@example.com");
		bob = TestRows.insertUser(jdbc, "bob@example.com");
	}

	@Test
	void savesAndLoadsEveryField() {
		User user = userRepository.getReferenceById(ana);
		Task parent = taskRepository.saveAndFlush(new Task(user, null, "Parent", "Parent description",
				lookupService.priority(Priority.MEDIUM), lookupService.status(TaskStatus.TODO), CREATED_AT));
		Task task = new Task(user, parent, "Child", "Child description",
				lookupService.priority(Priority.HIGH), lookupService.status(TaskStatus.IN_PROGRESS), CREATED_AT);
		task.setDueDate(LocalDate.of(2026, 10, 31));
		task.setComplexity(lookupService.complexity(Complexity.HARD));
		task.setPosition(3);
		UUID id = taskRepository.saveAndFlush(task).getId();
		entityManager.clear();

		Task found = taskRepository.findById(id).orElseThrow();

		assertThat(found.getTitle()).isEqualTo("Child");
		assertThat(found.getDescription()).isEqualTo("Child description");
		assertThat(found.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(found.getUser().getId()).isEqualTo(ana);
		assertThat(found.getPriority().getName()).isEqualTo("HIGH");
		assertThat(found.getStatus().getName()).isEqualTo("IN_PROGRESS");
		assertThat(found.getComplexity().getName()).isEqualTo("HARD");
		assertThat(found.getParent().getId()).isEqualTo(parent.getId());
		assertThat(found.getPosition()).isEqualTo(3);
		assertThat(found.getCreatedAt()).isEqualTo(CREATED_AT);
		assertThat(found.getUpdatedAt()).isEqualTo(CREATED_AT);
	}

	@Test
	void findByIdAndUserIdOnlyFindsTheOwnersTask() {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Ana's task");

		assertThat(taskRepository.findByIdAndUserId(task, ana)).isPresent();
		assertThat(taskRepository.findByIdAndUserId(task, bob)).isEmpty();
		assertThat(taskRepository.findByIdAndUserId(UUID.randomUUID(), ana)).isEmpty();
	}

	@Test
	void findAncestorsReturnsThePathRootFirst() {
		UUID root = TestRows.insertTask(jdbc, ana, null, "Root");
		UUID level2 = TestRows.insertTask(jdbc, ana, root, "Level 2");
		UUID level3 = TestRows.insertTask(jdbc, ana, level2, "Level 3");
		UUID level4 = TestRows.insertTask(jdbc, ana, level3, "Level 4");
		TestRows.insertTask(jdbc, ana, level4, "Level 5");
		TestRows.insertTask(jdbc, ana, root, "Sibling of level 2");

		assertThat(taskRepository.findAncestors(level4, ana))
				.extracting(TaskAncestor::getId, TaskAncestor::getTitle)
				.containsExactly(
						tuple(root, "Root"),
						tuple(level2, "Level 2"),
						tuple(level3, "Level 3"));
	}

	@Test
	void findAncestorsIsEmptyForTopLevelTask() {
		UUID root = TestRows.insertTask(jdbc, ana, null, "Root");
		TestRows.insertTask(jdbc, ana, root, "Child");

		assertThat(taskRepository.findAncestors(root, ana)).isEmpty();
	}

	@Test
	void findAncestorsIsEmptyForAnotherUsersTask() {
		UUID root = TestRows.insertTask(jdbc, ana, null, "Root");
		UUID child = TestRows.insertTask(jdbc, ana, root, "Child");

		assertThat(taskRepository.findAncestors(child, bob)).isEmpty();
	}
}
