package br.com.planned.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import br.com.planned.api.support.IntegrationTest;
import br.com.planned.api.support.TestRows;

/**
 * The task list (PLAN §4, {@code GET /tasks}): filters, sort (wave 2, D3 and D7), text search
 * (D8), pagination, and ownership. Rows are inserted directly, so each test controls every
 * field, and deleted afterwards.
 */
@AutoConfigureMockMvc
class TaskQueryControllerTest extends IntegrationTest {

	private static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");
	private static final LocalDate OCT_10 = LocalDate.of(2026, 10, 10);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID ana;
	private UUID bob;
	/** Makes each inserted task one minute newer than the last, unless a test sets createdAt. */
	private int inserted;

	@BeforeEach
	void setUp() {
		ana = TestRows.insertUser(jdbc, "ana-list@example.com");
		bob = TestRows.insertUser(jdbc, "bob-list@example.com");
		inserted = 0;
	}

	@AfterEach
	void cleanUp() {
		TestRows.deleteUsersAndTasks(jdbc, ana, bob);
	}

	// --- Shape, defaults, ownership ---

	@Test
	void list_returnsAPageOfTheCallersTopLevelTasks_sortedByDueDateAscByDefault() throws Exception {
		UUID late = task(ana).title("Late").due(OCT_10.plusDays(5)).insert();
		UUID soon = task(ana).title("Soon").due(OCT_10).priority("HIGH").complexity("HARD").status("IN_PROGRESS")
				.insert();
		task(ana).title("Child").parent(soon).insert();
		task(bob).title("Bob's").due(OCT_10).insert();

		list(get("/api/v1/tasks"), ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id").value(contains(soon.toString(), late.toString())))
				.andExpect(jsonPath("$.content[0].title").value("Soon"))
				.andExpect(jsonPath("$.content[0].dueDate").value("2026-10-10"))
				.andExpect(jsonPath("$.content[0].priority").value("HIGH"))
				.andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.content[0].complexity").value("HARD"))
				.andExpect(jsonPath("$.content[0].parentTaskId").isEmpty())
				// The tree fields belong to GET /tasks/{id} only.
				.andExpect(jsonPath("$.content[0].ancestors").doesNotExist())
				.andExpect(jsonPath("$.content[0].canAddSubtasks").doesNotExist())
				.andExpect(jsonPath("$.content[0].subtasks").doesNotExist())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void list_neverShowsAnotherUsersTasks() throws Exception {
		UUID bobsRoot = task(bob).title("Bob's").insert();
		task(bob).title("Bob's child").parent(bobsRoot).insert();

		list(get("/api/v1/tasks").param("includeSubtasks", "true").param("q", "Bob"), ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value(empty()))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void list_withoutToken_returns401() throws Exception {
		mockMvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
	}

	// --- Filters ---

	@Test
	void status_filtersAlone_andRepeatsAsOr() throws Exception {
		UUID todo = task(ana).status("TODO").insert();
		UUID overdue = task(ana).status("OVERDUE").insert();
		task(ana).status("DONE").insert();

		expectIds(get("/api/v1/tasks").param("status", "OVERDUE"), overdue);
		expectIdsInAnyOrder(get("/api/v1/tasks").param("status", "TODO", "OVERDUE"), todo, overdue);
	}

	@Test
	void priority_andComplexity_filterAlone() throws Exception {
		UUID high = task(ana).priority("HIGH").complexity("EASY").insert();
		UUID low = task(ana).priority("LOW").insert();

		expectIds(get("/api/v1/tasks").param("priority", "HIGH"), high);
		expectIdsInAnyOrder(get("/api/v1/tasks").param("priority", "HIGH", "LOW"), high, low);
		expectIds(get("/api/v1/tasks").param("complexity", "EASY"), high);
	}

	@Test
	void dueRange_isInclusive_andSkipsTasksWithoutADueDate() throws Exception {
		UUID dayBefore = task(ana).due(OCT_10.minusDays(1)).insert();
		UUID first = task(ana).due(OCT_10).insert();
		UUID last = task(ana).due(OCT_10.plusDays(2)).insert();
		UUID dayAfter = task(ana).due(OCT_10.plusDays(3)).insert();
		task(ana).insert();

		expectIds(get("/api/v1/tasks").param("dueFrom", "2026-10-10").param("dueTo", "2026-10-12"), first, last);
		expectIds(get("/api/v1/tasks").param("dueFrom", "2026-10-12"), last, dayAfter);
		expectIds(get("/api/v1/tasks").param("dueTo", "2026-10-10"), dayBefore, first);
	}

	@Test
	void filters_combine() throws Exception {
		UUID match = task(ana).title("Report").status("TODO").priority("HIGH").complexity("HARD").due(OCT_10)
				.insert();
		task(ana).title("Report").status("DONE").priority("HIGH").complexity("HARD").due(OCT_10).insert();
		task(ana).title("Report").status("TODO").priority("LOW").complexity("HARD").due(OCT_10).insert();
		task(ana).title("Report").status("TODO").priority("HIGH").complexity("EASY").due(OCT_10).insert();
		task(ana).title("Report").status("TODO").priority("HIGH").complexity("HARD").due(OCT_10.plusDays(1))
				.insert();
		task(ana).title("Other").status("TODO").priority("HIGH").complexity("HARD").due(OCT_10).insert();

		expectIds(get("/api/v1/tasks")
				.param("status", "TODO", "IN_PROGRESS")
				.param("priority", "HIGH")
				.param("complexity", "HARD")
				.param("dueFrom", "2026-10-10")
				.param("dueTo", "2026-10-10")
				.param("q", "report"), match);
	}

	@Test
	void q_matchesTitleOrDescription_inAnyLetterCase() throws Exception {
		UUID inTitle = task(ana).title("Quarterly REPORT").insert();
		UUID inDescription = task(ana).description("Send the report to finance").insert();
		task(ana).title("Unrelated").insert();

		expectIdsInAnyOrder(get("/api/v1/tasks").param("q", "Report"), inTitle, inDescription);
	}

	@Test
	void q_isTrimmed_andBlankMeansNoFilter() throws Exception {
		UUID report = task(ana).title("Report").insert();
		UUID other = task(ana).title("Other").insert();

		expectIds(get("/api/v1/tasks").param("q", "  report  "), report);
		expectIdsInAnyOrder(get("/api/v1/tasks").param("q", "   "), report, other);
	}

	@Test
	void q_withLikeWildcards_matchesThemLiterally() throws Exception {
		UUID percent = task(ana).title("Raise 10% budget").insert();
		task(ana).title("Raise 100 budget").insert();
		UUID underscore = task(ana).title("file_name").insert();
		task(ana).title("filename").insert();
		UUID backslash = task(ana).title("C:\\temp").insert();
		task(ana).title("C:temp").insert();

		expectIds(get("/api/v1/tasks").param("q", "10%"), percent);
		expectIds(get("/api/v1/tasks").param("q", "e_n"), underscore);
		expectIds(get("/api/v1/tasks").param("q", ":\\t"), backslash);
	}

	@Test
	void q_longerThan100Characters_returns400() throws Exception {
		list(get("/api/v1/tasks").param("q", "x".repeat(101)), ana)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		list(get("/api/v1/tasks").param("q", " " + "x".repeat(100) + " "), ana)
				.andExpect(status().isOk());
	}

	@Test
	void includeSubtasks_listsTasksAtEveryDepth() throws Exception {
		UUID root = task(ana).insert();
		UUID child = task(ana).parent(root).insert();
		UUID grandchild = task(ana).parent(child).insert();

		expectIds(get("/api/v1/tasks"), root);
		expectIds(get("/api/v1/tasks").param("includeSubtasks", "false"), root);
		list(get("/api/v1/tasks").param("includeSubtasks", "true"), ana)
				.andExpect(jsonPath("$.content[*].id")
						.value(containsInAnyOrder(root.toString(), child.toString(), grandchild.toString())))
				.andExpect(jsonPath("$.content[?(@.id == '%s')].parentTaskId".formatted(grandchild))
						.value(contains(child.toString())));
	}

	// --- Sort ---

	@Test
	void dueDate_sortsNullsLast_inBothDirections() throws Exception {
		UUID none = task(ana).insert();
		UUID early = task(ana).due(OCT_10).insert();
		UUID later = task(ana).due(OCT_10.plusDays(1)).insert();

		expectIds(get("/api/v1/tasks").param("sort", "dueDate,asc"), early, later, none);
		expectIds(get("/api/v1/tasks").param("sort", "dueDate,desc"), later, early, none);
	}

	@Test
	void priority_sortsBySeedOrder_notByName() throws Exception {
		UUID medium = task(ana).priority("MEDIUM").insert();
		UUID high = task(ana).priority("HIGH").insert();
		UUID low = task(ana).priority("LOW").insert();

		expectIds(get("/api/v1/tasks").param("sort", "priority,desc"), high, medium, low);
		expectIds(get("/api/v1/tasks").param("sort", "priority,asc"), low, medium, high);
	}

	@Test
	void createdAt_andTitle_sortBothWays_andTheDirectionDefaultsToAsc() throws Exception {
		UUID older = task(ana).title("B").insert();
		UUID newer = task(ana).title("A").insert();

		expectIds(get("/api/v1/tasks").param("sort", "createdAt,asc"), older, newer);
		expectIds(get("/api/v1/tasks").param("sort", "createdAt,desc"), newer, older);
		expectIds(get("/api/v1/tasks").param("sort", "title,asc"), newer, older);
		expectIds(get("/api/v1/tasks").param("sort", "title,desc"), older, newer);
		expectIds(get("/api/v1/tasks").param("sort", "title"), newer, older);
	}

	@Test
	void equalSortKeys_breakTiesByCreatedAtDescThenId_andPagesNeverRepeatOrSkip() throws Exception {
		// Five tasks with the same due date: two share a createdAt, so only the id orders them.
		Instant shared = T0.plusSeconds(3_600);
		UUID tieA = task(ana).due(OCT_10).createdAt(shared).insert();
		UUID tieB = task(ana).due(OCT_10).createdAt(shared).insert();
		UUID oldest = task(ana).due(OCT_10).createdAt(T0).insert();
		UUID newest = task(ana).due(OCT_10).createdAt(T0.plusSeconds(7_200)).insert();
		UUID middle = task(ana).due(OCT_10).createdAt(T0.plusSeconds(1_800)).insert();
		// The id tie-breaker follows the database's UUID order.
		List<UUID> ties = jdbc.queryForList("SELECT ID FROM TASK WHERE ID IN (?, ?) ORDER BY ID", UUID.class, tieA, tieB);
		List<UUID> expected = List.of(newest, ties.get(0), ties.get(1), middle, oldest);

		List<String> paged = new ArrayList<>();
		for (int page = 0; page < 3; page++) {
			String body = list(get("/api/v1/tasks").param("size", "2").param("page", String.valueOf(page)), ana)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(5))
					.andExpect(jsonPath("$.totalPages").value(3))
					.andReturn().getResponse().getContentAsString();
			paged.addAll(JsonPath.read(body, "$.content[*].id"));
		}
		assertThat(paged)
				.containsExactlyElementsOf(expected.stream().map(UUID::toString).toList());
	}

	// --- Paging ---

	@Test
	void size_isCappedAt100() throws Exception {
		task(ana).insert();

		list(get("/api/v1/tasks").param("size", "500"), ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
	}

	@Test
	void pageBeyondTheLast_isEmpty() throws Exception {
		task(ana).insert();

		list(get("/api/v1/tasks").param("page", "3"), ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value(empty()))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	// --- Invalid parameters ---

	@Test
	void invalidLookupNames_return400WithTheirCodes() throws Exception {
		expectError(get("/api/v1/tasks").param("status", "OPEN"), "INVALID_STATUS");
		expectError(get("/api/v1/tasks").param("status", "TODO", "OPEN"), "INVALID_STATUS");
		expectError(get("/api/v1/tasks").param("priority", "URGENT"), "INVALID_PRIORITY");
		expectError(get("/api/v1/tasks").param("complexity", "TRIVIAL"), "INVALID_COMPLEXITY");
	}

	@Test
	void invalidSort_returns400() throws Exception {
		for (String sort : List.of("status,asc", "dueDate,up", "dueDate,asc,extra", "id", ",asc")) {
			expectError(get("/api/v1/tasks").param("sort", sort), "VALIDATION_ERROR");
		}
	}

	@Test
	void invalidPageSizeOrDate_returns400() throws Exception {
		expectError(get("/api/v1/tasks").param("page", "-1"), "VALIDATION_ERROR");
		expectError(get("/api/v1/tasks").param("size", "0"), "VALIDATION_ERROR");
		expectError(get("/api/v1/tasks").param("size", "abc"), "VALIDATION_ERROR");
		expectError(get("/api/v1/tasks").param("dueFrom", "10/10/2026"), "VALIDATION_ERROR");
	}

	// --- Helpers ---

	private ResultActions list(MockHttpServletRequestBuilder request, UUID user) throws Exception {
		return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(user.toString()))));
	}

	/** Ana's list for {@code request} holds exactly {@code ids}, in this order. */
	private void expectIds(MockHttpServletRequestBuilder request, UUID... ids) throws Exception {
		list(request, ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id").value(contains(strings(ids))));
	}

	private void expectIdsInAnyOrder(MockHttpServletRequestBuilder request, UUID... ids) throws Exception {
		list(request, ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(strings(ids))));
	}

	private void expectError(MockHttpServletRequestBuilder request, String code) throws Exception {
		list(request, ana)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(code));
	}

	private static Object[] strings(UUID... ids) {
		return Arrays.stream(ids).map(UUID::toString).toArray();
	}

	private TaskRow task(UUID user) {
		inserted++;
		return new TaskRow(user, "TASK " + inserted, T0.plusSeconds(60L * inserted));
	}

	/** A task row to insert; defaults: {@code MEDIUM}, {@code TODO}, no complexity, no due date. */
	private final class TaskRow {

		private final UUID user;
		private String title;
		private String description = "description";
		private String priority = "MEDIUM";
		private String status = "TODO";
		private String complexity;
		private LocalDate dueDate;
		private UUID parent;
		private Instant createdAt;

		private TaskRow(UUID user, String title, Instant createdAt) {
			this.user = user;
			this.title = title;
			this.createdAt = createdAt;
		}

		TaskRow title(String value) {
			title = value;
			return this;
		}

		TaskRow description(String value) {
			description = value;
			return this;
		}

		TaskRow priority(String value) {
			priority = value;
			return this;
		}

		TaskRow status(String value) {
			status = value;
			return this;
		}

		TaskRow complexity(String value) {
			complexity = value;
			return this;
		}

		TaskRow due(LocalDate value) {
			dueDate = value;
			return this;
		}

		TaskRow parent(UUID value) {
			parent = value;
			return this;
		}

		TaskRow createdAt(Instant value) {
			createdAt = value;
			return this;
		}

		UUID insert() {
			UUID id = UUID.randomUUID();
			jdbc.update("""
					INSERT INTO TASK (ID, USER_ID, PARENT_TASK_ID, TITLE, DESCRIPTION, DUE_DATE, PRIORITY_ID, STATUS_ID,
					                  COMPLEXITY_ID, CREATED_AT, UPDATED_AT)
					VALUES (?, ?, ?, ?, ?, ?,
					        (SELECT ID FROM PRIORITIES WHERE NAME = ?),
					        (SELECT ID FROM TASK_STATUS WHERE NAME = ?),
					        (SELECT ID FROM COMPLEXITIES WHERE NAME = ?),
					        ?, ?)
					""", id, user, parent, title, description, dueDate, priority, status, complexity,
					Timestamp.from(createdAt), Timestamp.from(createdAt));
			return id;
		}
	}
}
