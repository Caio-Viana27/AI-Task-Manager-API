package br.com.planned.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import br.com.planned.api.support.IntegrationTest;
import br.com.planned.api.support.MutableClock;
import br.com.planned.api.support.TestRows;

/**
 * Subtask creation (PLAN §4, {@code POST /tasks/{id}/subtasks}): request order and positions
 * (wave 2, D5), the depth limit (D1), all-or-nothing writes, and ownership.
 *
 * <p>The app clock is fixed at {@link #NOW}: noon of {@link #TODAY} in {@code America/Sao_Paulo}.
 */
@AutoConfigureMockMvc
class SubtaskControllerTest extends IntegrationTest {

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
	private static final MutableClock CLOCK = new MutableClock(NOW, ZONE);

	@TestBean
	private Clock clock;

	static Clock clock() {
		return CLOCK;
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID ana;
	private UUID bob;

	@BeforeEach
	void setUp() {
		CLOCK.set(NOW);
		ana = TestRows.insertUser(jdbc, "ana-subtasks@example.com");
		bob = TestRows.insertUser(jdbc, "bob-subtasks@example.com");
	}

	@AfterEach
	void cleanUp() {
		TestRows.deleteUsersAndTasks(jdbc, ana, bob);
	}

	@Test
	void create_returns201WithTheSubtasksInRequestOrder_andDefaults() throws Exception {
		UUID parent = TestRows.insertTask(jdbc, ana, null, "Parent");

		perform(post(subtasksOf(parent)), ana, """
				[ { "title": "First", "description": "d" },
				  { "title": "Second", "description": "d", "dueDate": "2026-10-31", "priority": "HIGH",
				    "complexity": "EASY" },
				  { "title": "Late", "description": "d", "dueDate": "%s" } ]
				""".formatted(TODAY.minusDays(1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$[*].title").value(contains("First", "Second", "Late")))
				.andExpect(jsonPath("$[*].parentTaskId").value(contains(parent.toString(), parent.toString(),
						parent.toString())))
				.andExpect(jsonPath("$[0].priority").value("MEDIUM"))
				.andExpect(jsonPath("$[0].status").value("TODO"))
				.andExpect(jsonPath("$[0].complexity").isEmpty())
				.andExpect(jsonPath("$[0].createdAt").value("2026-10-07T15:00:00Z"))
				.andExpect(jsonPath("$[1].dueDate").value("2026-10-31"))
				.andExpect(jsonPath("$[1].priority").value("HIGH"))
				.andExpect(jsonPath("$[1].complexity").value("EASY"))
				// The overdue rule applies on create (wave 2, D4).
				.andExpect(jsonPath("$[2].status").value("OVERDUE"))
				.andExpect(jsonPath("$[0].ancestors").doesNotExist());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TASK WHERE PARENT_TASK_ID = ? AND USER_ID = ?",
				Long.class, parent, ana)).isEqualTo(3);
	}

	@Test
	void secondBatch_goesAfterTheFirst_inGetTaskInRequestOrder() throws Exception {
		UUID parent = TestRows.insertTask(jdbc, ana, null, "Parent");

		create(parent, "B1", "B2");
		create(parent, "C1", "C2", "C3");

		perform(get("/api/v1/tasks/" + parent), ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subtasks[*].title").value(contains("B1", "B2", "C1", "C2", "C3")));
		assertThat(jdbc.queryForList("SELECT POSITION FROM TASK WHERE PARENT_TASK_ID = ? ORDER BY POSITION",
				Integer.class, parent)).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void aChainDownToTheMaximumDepth_canBeBuilt_butNotBelowIt() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Level 1");
		for (int level = 2; level <= 5; level++) {
			task = create(task, "Level " + level);
		}

		perform(get("/api/v1/tasks/" + task), ana)
				.andExpect(jsonPath("$.ancestors", hasSize(4)))
				.andExpect(jsonPath("$.canAddSubtasks").value(false));

		long before = taskCount(ana);
		perform(post(subtasksOf(task)), ana, items(1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SUBTASK_DEPTH_EXCEEDED"));
		assertThat(taskCount(ana)).isEqualTo(before);
	}

	@Test
	void zeroOrMoreThanTenItems_return400() throws Exception {
		UUID parent = TestRows.insertTask(jdbc, ana, null, "Parent");

		for (String body : new String[] { "[]", items(11) }) {
			perform(post(subtasksOf(parent)), ana, body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		perform(post(subtasksOf(parent)), ana, items(10)).andExpect(status().isCreated());
		assertThat(taskCount(ana)).isEqualTo(11);
	}

	@Test
	void oneInvalidItem_returns400_andWritesNothing() throws Exception {
		UUID parent = TestRows.insertTask(jdbc, ana, null, "Parent");

		perform(post(subtasksOf(parent)), ana, """
				[ { "title": "Fine", "description": "d" }, { "title": " ", "description": "d" } ]
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value(containsString("title")));
		perform(post(subtasksOf(parent)), ana, """
				[ { "title": "Fine", "description": "d" }, { "title": "Bad", "description": "d", "priority": "URGENT" } ]
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PRIORITY"));
		perform(post(subtasksOf(parent)), ana, "not json")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		assertThat(taskCount(ana)).isEqualTo(1);
	}

	@Test
	void anotherUsersParent_returns404_andWritesNothing() throws Exception {
		UUID bobsTask = TestRows.insertTask(jdbc, bob, null, "Bob's");

		perform(post(subtasksOf(bobsTask)), ana, items(1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
		perform(post(subtasksOf(UUID.randomUUID())), ana, items(1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
		assertThat(taskCount(bob)).isEqualTo(1);
		assertThat(taskCount(ana)).isZero();
	}

	@Test
	void withoutToken_returns401() throws Exception {
		UUID parent = TestRows.insertTask(jdbc, ana, null, "Parent");

		mockMvc.perform(post(subtasksOf(parent)).contentType(MediaType.APPLICATION_JSON).content(items(1)))
				.andExpect(status().isUnauthorized());
	}

	// --- Helpers ---

	private ResultActions perform(MockHttpServletRequestBuilder request, UUID user) throws Exception {
		return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(user.toString()))));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, UUID user, String json) throws Exception {
		return perform(request.contentType(MediaType.APPLICATION_JSON).content(json), user);
	}

	/** Creates Ana's subtasks with these titles and returns the id of the last one. */
	private UUID create(UUID parent, String... titles) throws Exception {
		String body = Arrays.stream(titles)
				.map(title -> "{ \"title\": \"%s\", \"description\": \"d\" }".formatted(title))
				.collect(Collectors.joining(", ", "[", "]"));
		String response = perform(post(subtasksOf(parent)), ana, body)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(response, "$[%d].id".formatted(titles.length - 1)));
	}

	private static String subtasksOf(UUID parent) {
		return "/api/v1/tasks/" + parent + "/subtasks";
	}

	private static String items(int count) {
		return IntStream.rangeClosed(1, count)
				.mapToObj(i -> "{ \"title\": \"Item %d\", \"description\": \"d\" }".formatted(i))
				.collect(Collectors.joining(", ", "[", "]"));
	}

	private long taskCount(UUID user) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM TASK WHERE USER_ID = ?", Long.class, user);
	}
}
