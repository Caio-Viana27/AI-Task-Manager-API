package br.com.planned.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import br.com.planned.api.support.IntegrationTest;
import br.com.planned.api.support.MutableClock;
import br.com.planned.api.support.TestRows;

/**
 * Task CRUD (PLAN §4: create, view, edit, status change, delete) with ownership, status rules,
 * {@code PATCH} semantics (wave 2, D2), and the overdue-on-write rule (D4).
 *
 * <p>The app clock is fixed at {@link #NOW}: noon of {@link #TODAY} in {@code America/Sao_Paulo}.
 * Requests commit, so every test deletes its users and tasks afterwards.
 */
@AutoConfigureMockMvc
class TaskControllerTest extends IntegrationTest {

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
		ana = TestRows.insertUser(jdbc, "ana-tasks@example.com");
		bob = TestRows.insertUser(jdbc, "bob-tasks@example.com");
	}

	@AfterEach
	void cleanUp() {
		TestRows.deleteUsersAndTasks(jdbc, ana, bob);
	}

	// --- Create ---

	@Test
	void create_withRequiredFields_returns201WithDefaultsAndLocation() throws Exception {
		String body = perform(post("/api/v1/tasks"), ana, """
				{ "title": "Write report", "description": "Quarterly numbers" }
				""")
				.andExpect(status().isCreated())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.title").value("Write report"))
				.andExpect(jsonPath("$.description").value("Quarterly numbers"))
				.andExpect(jsonPath("$.dueDate").isEmpty())
				.andExpect(jsonPath("$.priority").value("MEDIUM"))
				.andExpect(jsonPath("$.status").value("TODO"))
				.andExpect(jsonPath("$.complexity").isEmpty())
				.andExpect(jsonPath("$.parentTaskId").isEmpty())
				.andExpect(jsonPath("$.createdAt").value("2026-10-07T15:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-10-07T15:00:00Z"))
				// The tree fields belong to GET /tasks/{id} only.
				.andExpect(jsonPath("$.ancestors").doesNotExist())
				.andExpect(jsonPath("$.canAddSubtasks").doesNotExist())
				.andExpect(jsonPath("$.subtasks").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		assertThat(jdbc.queryForObject("SELECT USER_ID FROM TASK WHERE ID = ?", UUID.class, UUID.fromString(id)))
				.isEqualTo(ana);
	}

	@Test
	void create_setsLocationToTheNewTask() throws Exception {
		var result = perform(post("/api/v1/tasks"), ana, """
				{ "title": "Write report", "description": "Quarterly numbers" }
				""").andExpect(status().isCreated()).andReturn();
		String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		assertThat(result.getResponse().getHeader("Location")).isEqualTo("http://localhost/api/v1/tasks/" + id);
	}

	@Test
	void create_withEveryField_savesThem() throws Exception {
		perform(post("/api/v1/tasks"), ana, """
				{ "title": "Write report", "description": "Quarterly numbers",
				  "dueDate": "2026-10-31", "priority": "HIGH", "complexity": "HARD" }
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.dueDate").value("2026-10-31"))
				.andExpect(jsonPath("$.priority").value("HIGH"))
				.andExpect(jsonPath("$.complexity").value("HARD"))
				.andExpect(jsonPath("$.status").value("TODO"));
	}

	@Test
	void create_withInvalidFields_returns400ValidationError() throws Exception {
		perform(post("/api/v1/tasks"), ana, """
				{ "title": "   ", "description": "%s" }
				""".formatted("x".repeat(501)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder(
						"title", "description")));
		perform(post("/api/v1/tasks"), ana, """
				{ "title": "%s" }
				""".formatted("x".repeat(101)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder(
						"title", "description")));
		assertThat(taskCount(ana)).isZero();
	}

	@Test
	void create_withUnknownLookupNames_returnsTheirCodes() throws Exception {
		perform(post("/api/v1/tasks"), ana, """
				{ "title": "t", "description": "d", "priority": "URGENT" }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PRIORITY"));
		perform(post("/api/v1/tasks"), ana, """
				{ "title": "t", "description": "d", "complexity": "TRIVIAL" }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COMPLEXITY"));
		assertThat(taskCount(ana)).isZero();
	}

	@Test
	void withoutToken_returns401() throws Exception {
		mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"title\": \"t\", \"description\": \"d\" }"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		UUID task = TestRows.insertTask(jdbc, ana, null, "Mine");
		mockMvc.perform(get("/api/v1/tasks/" + task)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/tasks/" + task)).andExpect(status().isUnauthorized());
		assertThat(taskCount(ana)).isOne();
	}

	// --- View ---

	@Test
	void get_topLevelTask_returnsFullDetailsWithEmptyTree() throws Exception {
		UUID id = createTask(ana, "Write report");

		perform(get("/api/v1/tasks/" + id), ana)
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "id": "%s",
						  "title": "Write report",
						  "description": "description",
						  "dueDate": null,
						  "priority": "MEDIUM",
						  "status": "TODO",
						  "complexity": null,
						  "parentTaskId": null,
						  "ancestors": [],
						  "canAddSubtasks": true,
						  "subtasks": [],
						  "createdAt": "2026-10-07T15:00:00Z",
						  "updatedAt": "2026-10-07T15:00:00Z"
						}
						""".formatted(id), JsonCompareMode.STRICT));
	}

	@Test
	void get_depthThreeTask_returnsAncestorsRootFirstAndDirectSubtasksInPositionOrder() throws Exception {
		UUID root = TestRows.insertTask(jdbc, ana, null, "Root");
		UUID middle = TestRows.insertTask(jdbc, ana, root, "Middle");
		UUID task = TestRows.insertTask(jdbc, ana, middle, "Task");
		// Inserted in one order, positioned in another: POSITION wins (D5).
		UUID first = TestRows.insertTask(jdbc, ana, task, "First");
		UUID second = TestRows.insertTask(jdbc, ana, task, "Second");
		UUID third = TestRows.insertTask(jdbc, ana, task, "Third");
		setPosition(first, 3);
		setPosition(second, 1);
		setPosition(third, 2);
		jdbc.update("UPDATE TASK SET DUE_DATE = ?, PRIORITY_ID = (SELECT ID FROM PRIORITIES WHERE NAME = 'HIGH') "
				+ "WHERE ID = ?", TODAY.plusDays(3), second);
		// Grandchildren: counted on their parent, never listed.
		TestRows.insertTask(jdbc, ana, second, "Second.1");
		TestRows.insertTask(jdbc, ana, second, "Second.2");
		UUID thirdChild = TestRows.insertTask(jdbc, ana, third, "Third.1");
		TestRows.insertTask(jdbc, ana, thirdChild, "Third.1.1");

		perform(get("/api/v1/tasks/" + task), ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parentTaskId").value(middle.toString()))
				.andExpect(jsonPath("$.canAddSubtasks").value(true))
				.andExpect(content().json("""
						{
						  "ancestors": [
						    { "id": "%s", "title": "Root" },
						    { "id": "%s", "title": "Middle" }
						  ],
						  "subtasks": [
						    { "id": "%s", "title": "Second", "status": "TODO", "priority": "HIGH",
						      "dueDate": "2026-10-10", "subtaskCount": 2 },
						    { "id": "%s", "title": "Third", "status": "TODO", "priority": "MEDIUM",
						      "dueDate": null, "subtaskCount": 1 },
						    { "id": "%s", "title": "First", "status": "TODO", "priority": "MEDIUM",
						      "dueDate": null, "subtaskCount": 0 }
						  ]
						}
						""".formatted(root, middle, second, third, first), JsonCompareMode.LENIENT))
				.andExpect(jsonPath("$.ancestors", hasSize(2)))
				.andExpect(jsonPath("$.subtasks", hasSize(3)))
				.andExpect(jsonPath("$.subtasks[0]", aMapWithSize(6)));
	}

	@Test
	void get_subtasksWithEqualPositions_fallBackToCreatedAtThenId() throws Exception {
		UUID parent = TestRows.insertTask(jdbc, ana, null, "Parent");
		UUID later = TestRows.insertTask(jdbc, ana, parent, "Later");
		UUID earlier = TestRows.insertTask(jdbc, ana, parent, "Earlier");
		jdbc.update("UPDATE TASK SET CREATED_AT = ? WHERE ID = ?", Timestamp.valueOf("2026-10-01 10:00:00"),
				earlier);
		jdbc.update("UPDATE TASK SET CREATED_AT = ? WHERE ID = ?", Timestamp.valueOf("2026-10-02 10:00:00"),
				later);

		perform(get("/api/v1/tasks/" + parent), ana)
				.andExpect(jsonPath("$.subtasks[0].title").value("Earlier"))
				.andExpect(jsonPath("$.subtasks[1].title").value("Later"));
	}

	@Test
	void get_taskAtMaximumDepth_cannotAddSubtasks() throws Exception {
		UUID parent = null;
		List<UUID> chain = new ArrayList<>();
		for (int depth = 1; depth <= 5; depth++) {
			parent = TestRows.insertTask(jdbc, ana, parent, "Level " + depth);
			chain.add(parent);
		}

		perform(get("/api/v1/tasks/" + chain.get(4)), ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ancestors", hasSize(4)))
				.andExpect(jsonPath("$.ancestors[0].title").value("Level 1"))
				.andExpect(jsonPath("$.ancestors[3].title").value("Level 4"))
				.andExpect(jsonPath("$.canAddSubtasks").value(false));
		perform(get("/api/v1/tasks/" + chain.get(3)), ana)
				.andExpect(jsonPath("$.canAddSubtasks").value(true))
				.andExpect(jsonPath("$.subtasks[0].subtaskCount").value(0));
	}

	@Test
	void get_unknownTask_returns404() throws Exception {
		perform(get("/api/v1/tasks/" + UUID.randomUUID()), ana)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
	}

	// --- Ownership ---

	@Test
	void anotherUsersTask_isNotFoundForEveryMethodAndStaysUnchanged() throws Exception {
		UUID task = createTask(ana, "Ana's task");
		String before = perform(get("/api/v1/tasks/" + task), ana).andReturn().getResponse().getContentAsString();
		String full = """
				{ "title": "Hijacked", "description": "d", "dueDate": null, "priority": "LOW",
				  "status": "DONE", "complexity": null }
				""";

		expectNotFound(perform(get("/api/v1/tasks/" + task), bob));
		expectNotFound(perform(put("/api/v1/tasks/" + task), bob, full));
		expectNotFound(perform(patch("/api/v1/tasks/" + task), bob, "{ \"title\": \"Hijacked\" }"));
		expectNotFound(perform(delete("/api/v1/tasks/" + task), bob));

		perform(get("/api/v1/tasks/" + task), ana)
				.andExpect(status().isOk())
				.andExpect(content().json(before, JsonCompareMode.STRICT));
	}

	@Test
	void anotherUsersSubtasks_areNotListed() throws Exception {
		UUID parent = TestRows.insertTask(jdbc, ana, null, "Ana's parent");
		TestRows.insertTask(jdbc, ana, parent, "Ana's child");

		perform(get("/api/v1/tasks/" + parent), ana).andExpect(jsonPath("$.subtasks", hasSize(1)));
		expectNotFound(perform(get("/api/v1/tasks/" + parent), bob));
	}

	// --- Edit (PUT) ---

	@Test
	void put_replacesEveryEditableFieldAndRefreshesUpdatedAt() throws Exception {
		UUID task = createTask(ana, "Old", """
				, "dueDate": "2026-10-20", "priority": "LOW", "complexity": "EASY" """);
		CLOCK.advance(Duration.ofHours(2));

		perform(put("/api/v1/tasks/" + task), ana, """
				{ "title": "New", "description": "New description", "dueDate": "2026-11-01",
				  "priority": "HIGH", "status": "IN_PROGRESS", "complexity": "HARD" }
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("New"))
				.andExpect(jsonPath("$.description").value("New description"))
				.andExpect(jsonPath("$.dueDate").value("2026-11-01"))
				.andExpect(jsonPath("$.priority").value("HIGH"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.complexity").value("HARD"))
				.andExpect(jsonPath("$.createdAt").value("2026-10-07T15:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-10-07T17:00:00Z"))
				.andExpect(jsonPath("$.subtasks").doesNotExist());

		perform(get("/api/v1/tasks/" + task), ana)
				.andExpect(jsonPath("$.title").value("New"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.updatedAt").value("2026-10-07T17:00:00Z"));
	}

	@Test
	void put_withNullDueDateAndComplexity_clearsThem() throws Exception {
		UUID task = createTask(ana, "Task", """
				, "dueDate": "2026-10-20", "complexity": "EASY" """);

		perform(put("/api/v1/tasks/" + task), ana, """
				{ "title": "Task", "description": "description", "dueDate": null,
				  "priority": "MEDIUM", "status": "TODO", "complexity": null }
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dueDate").isEmpty())
				.andExpect(jsonPath("$.complexity").isEmpty());
	}

	@Test
	void put_withMissingRequiredFields_returns400() throws Exception {
		UUID task = createTask(ana, "Task");

		perform(put("/api/v1/tasks/" + task), ana, """
				{ "title": "Task", "description": "description" }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder(
						"priority", "status")));
	}

	@Test
	void put_withOverdueOrUnknownNames_returnsTheirCodes() throws Exception {
		UUID task = createTask(ana, "Task");

		perform(put("/api/v1/tasks/" + task), ana, fullBody("OVERDUE", "MEDIUM", null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS"));
		perform(put("/api/v1/tasks/" + task), ana, fullBody("OPEN", "MEDIUM", null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS"));
		perform(put("/api/v1/tasks/" + task), ana, fullBody("TODO", "URGENT", null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PRIORITY"));
		perform(put("/api/v1/tasks/" + task), ana, fullBody("TODO", "MEDIUM", "\"TRIVIAL\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COMPLEXITY"));
		perform(get("/api/v1/tasks/" + task), ana).andExpect(jsonPath("$.status").value("TODO"));
	}

	// --- Edit (PATCH), D2 ---

	@Test
	void patch_statusOnly_changesOnlyTheStatus() throws Exception {
		UUID task = createTask(ana, "Task", """
				, "dueDate": "2026-10-20", "priority": "HIGH", "complexity": "EASY" """);
		String before = perform(get("/api/v1/tasks/" + task), ana).andReturn().getResponse().getContentAsString();
		CLOCK.advance(Duration.ofMinutes(5));

		String after = perform(patch("/api/v1/tasks/" + task), ana, "{ \"status\": \"DONE\" }")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"))
				.andReturn().getResponse().getContentAsString();

		assertSameExcept(before, after, "status", "updatedAt");
		assertThat(JsonPath.<String>read(after, "$.updatedAt")).isEqualTo("2026-10-07T15:05:00Z");
	}

	@Test
	void patch_emptyBody_changesNothingButUpdatedAt() throws Exception {
		UUID task = createTask(ana, "Task", """
				, "dueDate": "2026-10-20", "priority": "HIGH", "complexity": "EASY" """);
		String before = perform(get("/api/v1/tasks/" + task), ana).andReturn().getResponse().getContentAsString();
		CLOCK.advance(Duration.ofHours(1));

		String after = perform(patch("/api/v1/tasks/" + task), ana, "{}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updatedAt").value("2026-10-07T16:00:00Z"))
				.andReturn().getResponse().getContentAsString();

		assertSameExcept(before, after, "updatedAt");
	}

	@Test
	void patch_nullDueDateAndComplexity_clearsThemAndLeavesAbsentFieldsUntouched() throws Exception {
		UUID task = createTask(ana, "Task", """
				, "dueDate": "2026-10-20", "priority": "HIGH", "complexity": "EASY" """);
		String before = perform(get("/api/v1/tasks/" + task), ana).andReturn().getResponse().getContentAsString();
		CLOCK.advance(Duration.ofMinutes(1));

		String afterDueDate = perform(patch("/api/v1/tasks/" + task), ana, "{ \"dueDate\": null }")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dueDate").isEmpty())
				.andExpect(jsonPath("$.complexity").value("EASY"))
				.andReturn().getResponse().getContentAsString();
		assertSameExcept(before, afterDueDate, "dueDate", "updatedAt");

		perform(patch("/api/v1/tasks/" + task), ana, "{ \"complexity\": null }")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.complexity").isEmpty())
				.andExpect(jsonPath("$.priority").value("HIGH"));
	}

	@Test
	void patch_someFields_changesOnlyThem() throws Exception {
		UUID task = createTask(ana, "Task", """
				, "dueDate": "2026-10-20", "priority": "HIGH", "complexity": "EASY" """);
		String before = perform(get("/api/v1/tasks/" + task), ana).andReturn().getResponse().getContentAsString();
		CLOCK.advance(Duration.ofMinutes(1));

		String after = perform(patch("/api/v1/tasks/" + task), ana, """
				{ "title": "Renamed", "priority": "LOW", "dueDate": "2026-12-01" }
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Renamed"))
				.andExpect(jsonPath("$.priority").value("LOW"))
				.andExpect(jsonPath("$.dueDate").value("2026-12-01"))
				.andReturn().getResponse().getContentAsString();
		assertSameExcept(before, after, "title", "priority", "dueDate", "updatedAt");
	}

	@Test
	void patch_nullForANonClearableField_returns400NamingIt() throws Exception {
		UUID task = createTask(ana, "Task");

		for (String field : List.of("title", "description", "priority", "status")) {
			perform(patch("/api/v1/tasks/" + task), ana, "{ \"%s\": null }".formatted(field))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.errors", hasSize(1)))
					.andExpect(jsonPath("$.errors[0].field").value(field));
		}
		perform(get("/api/v1/tasks/" + task), ana).andExpect(jsonPath("$.title").value("Task"));
	}

	@Test
	void patch_presentFields_getTheSameValidationAsPut() throws Exception {
		UUID task = createTask(ana, "Task");

		perform(patch("/api/v1/tasks/" + task), ana, """
				{ "title": "  ", "description": "%s" }
				""".formatted("x".repeat(501)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder(
						"title", "description")));
		perform(patch("/api/v1/tasks/" + task), ana, "{ \"title\": \"%s\" }".formatted("x".repeat(101)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("title"));
		perform(patch("/api/v1/tasks/" + task), ana, "{ \"priority\": \"URGENT\" }")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PRIORITY"));
		perform(patch("/api/v1/tasks/" + task), ana, "{ \"complexity\": \"TRIVIAL\" }")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COMPLEXITY"));
		perform(patch("/api/v1/tasks/" + task), ana, "{ \"status\": \"OVERDUE\" }")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS"));
		perform(get("/api/v1/tasks/" + task), ana)
				.andExpect(jsonPath("$.title").value("Task"))
				.andExpect(jsonPath("$.status").value("TODO"));
	}

	// --- Overdue on write, D4 ---

	@Test
	void create_dueYesterday_isOverdue_dueTodayIsTodo() throws Exception {
		perform(post("/api/v1/tasks"), ana, taskBody("Late", TODAY.minusDays(1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("OVERDUE"));
		perform(post("/api/v1/tasks"), ana, taskBody("Today", TODAY))
				.andExpect(jsonPath("$.status").value("TODO"));
	}

	@Test
	void today_comesFromTheAppTimeZone() throws Exception {
		// 01:00 UTC on Oct 8 is still Oct 7 in São Paulo (UTC-3): due Oct 7 is not late yet.
		CLOCK.set(Instant.parse("2026-10-08T01:00:00Z"));
		perform(post("/api/v1/tasks"), ana, taskBody("Due today", TODAY))
				.andExpect(jsonPath("$.status").value("TODO"));

		// 03:00 UTC on Oct 8 is Oct 8 in São Paulo: now it's late.
		CLOCK.set(Instant.parse("2026-10-08T03:00:00Z"));
		perform(post("/api/v1/tasks"), ana, taskBody("Due yesterday", TODAY))
				.andExpect(jsonPath("$.status").value("OVERDUE"));
	}

	@Test
	void overdueTask_movedToTodayOrLater_goesBackToTodo() throws Exception {
		UUID today = overdueTask("Today");
		UUID later = overdueTask("Later");

		perform(patch("/api/v1/tasks/" + today), ana, "{ \"dueDate\": \"%s\" }".formatted(TODAY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TODO"));
		perform(put("/api/v1/tasks/" + later), ana, """
				{ "title": "Later", "description": "d", "dueDate": "%s", "priority": "MEDIUM",
				  "status": "TODO", "complexity": null }
				""".formatted(TODAY.plusDays(5)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TODO"));
	}

	@Test
	void overdueTask_withDueDateCleared_goesBackToTodo() throws Exception {
		UUID task = overdueTask("Task");

		perform(patch("/api/v1/tasks/" + task), ana, "{ \"dueDate\": null }")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TODO"))
				.andExpect(jsonPath("$.dueDate").isEmpty());
	}

	@Test
	void overdueTask_editedButStillLate_staysOverdue() throws Exception {
		UUID task = overdueTask("Task");

		perform(patch("/api/v1/tasks/" + task), ana, "{ \"title\": \"Renamed\" }")
				.andExpect(jsonPath("$.status").value("OVERDUE"));
		// Sending TODO or IN_PROGRESS on a task that is still late ends up OVERDUE again.
		perform(patch("/api/v1/tasks/" + task), ana, "{ \"status\": \"IN_PROGRESS\" }")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OVERDUE"));
	}

	@Test
	void lateTask_markedDone_staysDone_andCanBeReopened() throws Exception {
		UUID task = overdueTask("Task");

		perform(patch("/api/v1/tasks/" + task), ana, "{ \"status\": \"DONE\" }")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"));
		perform(patch("/api/v1/tasks/" + task), ana, "{ \"title\": \"Still done\" }")
				.andExpect(jsonPath("$.status").value("DONE"));

		// DONE -> TODO is allowed; with a future due date it stays TODO.
		perform(patch("/api/v1/tasks/" + task), ana, """
				{ "status": "TODO", "dueDate": "%s" }
				""".formatted(TODAY.plusDays(1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TODO"));
	}

	// --- Delete ---

	@Test
	void delete_removesTheTaskAndItsWholeSubtree() throws Exception {
		UUID root = TestRows.insertTask(jdbc, ana, null, "Root");
		UUID child = TestRows.insertTask(jdbc, ana, root, "Child");
		UUID grandchild = TestRows.insertTask(jdbc, ana, child, "Grandchild");
		TestRows.insertTask(jdbc, ana, grandchild, "Great-grandchild");
		UUID sibling = TestRows.insertTask(jdbc, ana, null, "Sibling");
		UUID bobs = TestRows.insertTask(jdbc, bob, null, "Bob's");

		perform(delete("/api/v1/tasks/" + root), ana).andExpect(status().isNoContent());

		assertThat(jdbc.queryForList("SELECT ID FROM TASK WHERE USER_ID IN (?, ?)", UUID.class, ana, bob))
				.containsExactlyInAnyOrder(sibling, bobs);
		expectNotFound(perform(get("/api/v1/tasks/" + root), ana));
		expectNotFound(perform(delete("/api/v1/tasks/" + root), ana));
	}

	@Test
	void delete_subtask_keepsItsParentAndSiblings() throws Exception {
		UUID root = TestRows.insertTask(jdbc, ana, null, "Root");
		UUID child = TestRows.insertTask(jdbc, ana, root, "Child");
		UUID sibling = TestRows.insertTask(jdbc, ana, root, "Sibling");
		TestRows.insertTask(jdbc, ana, child, "Grandchild");

		perform(delete("/api/v1/tasks/" + child), ana).andExpect(status().isNoContent());

		assertThat(jdbc.queryForList("SELECT ID FROM TASK WHERE USER_ID = ?", UUID.class, ana))
				.containsExactlyInAnyOrder(root, sibling);
	}

	// --- Helpers ---

	private ResultActions perform(MockHttpServletRequestBuilder request, UUID user) throws Exception {
		return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(user.toString()))));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, UUID user, String json) throws Exception {
		return perform(request.contentType(MediaType.APPLICATION_JSON).content(json), user);
	}

	private UUID createTask(UUID user, String title) throws Exception {
		return createTask(user, title, "");
	}

	/** @param extraFields more JSON members, starting with a comma */
	private UUID createTask(UUID user, String title, String extraFields) throws Exception {
		String body = perform(post("/api/v1/tasks"), user, """
				{ "title": "%s", "description": "description" %s }
				""".formatted(title, extraFields.strip()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	/** A task due yesterday, which the overdue rule makes {@code OVERDUE} on create. */
	private UUID overdueTask(String title) throws Exception {
		UUID task = createTask(ana, title, ", \"dueDate\": \"%s\"".formatted(TODAY.minusDays(1)));
		assertThat(jdbc.queryForObject(
				"SELECT S.NAME FROM TASK T JOIN TASK_STATUS S ON S.ID = T.STATUS_ID WHERE T.ID = ?",
				String.class, task)).isEqualTo("OVERDUE");
		return task;
	}

	private static String taskBody(String title, LocalDate dueDate) {
		return """
				{ "title": "%s", "description": "d", "dueDate": "%s" }
				""".formatted(title, dueDate);
	}

	private static String fullBody(String status, String priority, String complexityJson) {
		return """
				{ "title": "Task", "description": "description", "dueDate": null, "priority": "%s",
				  "status": "%s", "complexity": %s }
				""".formatted(priority, status, complexityJson);
	}

	private static void expectNotFound(ResultActions result) throws Exception {
		result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
	}

	private void setPosition(UUID task, int position) {
		jdbc.update("UPDATE TASK SET POSITION = ? WHERE ID = ?", position, task);
	}

	private long taskCount(UUID user) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM TASK WHERE USER_ID = ?", Long.class, user);
	}

	/** Both JSON task objects are equal, except for the named top-level fields. */
	private static void assertSameExcept(String before, String after, String... changed) {
		Map<String, Object> expected = new HashMap<>(JsonPath.read(before, "$"));
		Map<String, Object> actual = new HashMap<>(JsonPath.read(after, "$"));
		// GET carries the tree fields; write responses don't.
		for (String treeField : List.of("ancestors", "canAddSubtasks", "subtasks")) {
			expected.remove(treeField);
			actual.remove(treeField);
		}
		for (String field : changed) {
			assertThat(actual.get(field)).as(field + " changed").isNotEqualTo(expected.get(field));
			expected.remove(field);
			actual.remove(field);
		}
		assertThat(actual).isEqualTo(expected);
	}
}
