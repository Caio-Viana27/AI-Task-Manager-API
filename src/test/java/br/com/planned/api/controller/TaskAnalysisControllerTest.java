package br.com.planned.api.controller;

import static br.com.planned.api.support.AiStubs.googleError;
import static br.com.planned.api.support.AiStubs.lastPrompt;
import static br.com.planned.api.support.AiStubs.stubFailure;
import static br.com.planned.api.support.AiStubs.stubReply;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.planned.api.service.ai.AiQuotaService;
import br.com.planned.api.support.IntegrationTest;
import br.com.planned.api.support.TestRows;

/** {@code POST /api/v1/tasks/{id}/ai/analysis} (wave 4, D11–D13) with a mocked chat model. */
@AutoConfigureMockMvc
class TaskAnalysisControllerTest extends IntegrationTest {

	private static final String VALID_REPLY = """
			{ "priority": "HIGH", "complexity": "HARD", "estimatedHours": 12, "reason": "  Due soon and has many parts.  " }""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AiQuotaService quotaService;

	private UUID ana;
	private UUID bob;

	@BeforeEach
	void setUp() {
		ana = TestRows.insertUser(jdbc, "ana-analysis@example.com");
		bob = TestRows.insertUser(jdbc, "bob-analysis@example.com");
	}

	@AfterEach
	void cleanUp() {
		TestRows.deleteUsersAndTasks(jdbc, ana, bob);
	}

	@Test
	void analysis_returnsTheFourFields() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Write the report");
		stubReply(chatModel, VALID_REPLY);

		analysis(task, ana, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.priority").value("HIGH"))
				.andExpect(jsonPath("$.complexity").value("HARD"))
				.andExpect(jsonPath("$.estimatedHours").value(12))
				.andExpect(jsonPath("$.reason").value("Due soon and has many parts."));

		assertThat(lastPrompt(chatModel).getInstructions().get(0).getText()).contains("Write every text field in English");
	}

	@Test
	void analysisInPtBr_namesBrazilianPortuguese() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Write the report");
		stubReply(chatModel, VALID_REPLY);

		analysis(task, ana, "pt-BR").andExpect(status().isOk());

		assertThat(lastPrompt(chatModel).getInstructions().get(0).getText())
				.contains("Write every text field in Brazilian Portuguese");
	}

	@Test
	void prompt_holdsCurrentValues_ancestorsRootFirst_andDirectSubtasks() throws Exception {
		UUID root = TestRows.insertTask(jdbc, ana, null, "Root task");
		UUID middle = TestRows.insertTask(jdbc, ana, root, "Middle task");
		UUID task = TestRows.insertTask(jdbc, ana, middle, "Deep task");
		jdbc.update("""
				UPDATE TASK SET DUE_DATE = ?, PRIORITY_ID = (SELECT ID FROM PRIORITIES WHERE NAME = 'LOW'),
				    STATUS_ID = (SELECT ID FROM TASK_STATUS WHERE NAME = 'IN_PROGRESS'),
				    COMPLEXITY_ID = (SELECT ID FROM COMPLEXITIES WHERE NAME = 'EASY'), ESTIMATED_HOURS = 7
				WHERE ID = ?
				""", LocalDate.parse("2099-03-15"), task);
		UUID child = TestRows.insertTask(jdbc, ana, task, "Child one");
		jdbc.update("UPDATE TASK SET STATUS_ID = (SELECT ID FROM TASK_STATUS WHERE NAME = 'DONE') WHERE ID = ?", child);
		TestRows.insertTask(jdbc, ana, child, "Grandchild");
		stubReply(chatModel, VALID_REPLY);

		analysis(task, ana, null).andExpect(status().isOk());

		assertThat(lastPrompt(chatModel).getInstructions().get(1).getText())
				.contains("\"task\":{\"title\":\"Deep task\",\"description\":\"description\",\"status\":\"IN_PROGRESS\","
						+ "\"dueDate\":\"2099-03-15\",\"priority\":\"LOW\",\"complexity\":\"EASY\",\"estimatedHours\":7}")
				.contains("\"ancestors\":[\"Root task\",\"Middle task\"]")
				.contains("\"subtasks\":[{\"title\":\"Child one\",\"status\":\"DONE\"}]")
				.doesNotContain("Grandchild");
	}

	@Test
	void prompt_sendsNullsForAnUnsetDueDateComplexityAndHours() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Plain task");
		stubReply(chatModel, VALID_REPLY);

		analysis(task, ana, null).andExpect(status().isOk());

		assertThat(lastPrompt(chatModel).getInstructions().get(1).getText())
				.contains("\"dueDate\":null,\"priority\":\"MEDIUM\",\"complexity\":null,\"estimatedHours\":null")
				.contains("\"ancestors\":[]")
				.contains("\"subtasks\":[]");
	}

	@Test
	void anotherUsersTask_andUnknownId_neverCallTheModelOrUseQuota() throws Exception {
		UUID bobsTask = TestRows.insertTask(jdbc, bob, null, "Bob's");

		analysis(bobsTask, ana, null).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
		analysis(UUID.randomUUID(), ana, null).andExpect(status().isNotFound());

		verify(chatModel, never()).call(any(Prompt.class));
		assertQuotaUnused(ana);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{ \"priority\": \"URGENT\", \"complexity\": \"HARD\", \"estimatedHours\": 12, \"reason\": \"Why\" }",
			"{ \"priority\": \"HIGH\", \"complexity\": \"TRIVIAL\", \"estimatedHours\": 12, \"reason\": \"Why\" }",
			"{ \"priority\": \"HIGH\", \"complexity\": null, \"estimatedHours\": 12, \"reason\": \"Why\" }",
			"{ \"priority\": \"HIGH\", \"complexity\": \"HARD\", \"estimatedHours\": 0, \"reason\": \"Why\" }",
			"{ \"priority\": \"HIGH\", \"complexity\": \"HARD\", \"estimatedHours\": 1000, \"reason\": \"Why\" }",
			"{ \"priority\": \"HIGH\", \"complexity\": \"HARD\", \"estimatedHours\": null, \"reason\": \"Why\" }",
			"{ \"priority\": \"HIGH\", \"complexity\": \"HARD\", \"estimatedHours\": 12, \"reason\": \"   \" }",
			"not json" })
	void invalidReply_is422(String reply) throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		stubReply(chatModel, reply);

		analysis(task, ana, null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));
	}

	@Test
	void reasonOf1001Characters_is422_and1000IsAccepted() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		String template = "{ \"priority\": \"HIGH\", \"complexity\": \"HARD\", \"estimatedHours\": %d, \"reason\": \"%s\" }";

		stubReply(chatModel, template.formatted(1, "a".repeat(1001)));
		analysis(task, ana, null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));

		stubReply(chatModel, template.formatted(999, "a".repeat(1000)));
		analysis(task, ana, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.estimatedHours").value(999));
	}

	@Test
	void modelFailure_is503() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		stubFailure(chatModel, googleError(503));

		analysis(task, ana, null).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
	}

	@Test
	void quotaUsedUp_is429_withoutCallingTheModel() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		for (int i = 0; i < 30; i++) {
			quotaService.consume(ana);
		}

		analysis(task, ana, null).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("AI_RATE_LIMITED"));

		verify(chatModel, never()).call(any(Prompt.class));
	}

	@Test
	void analysis_writesNothing() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		TestRows.insertTask(jdbc, ana, task, "Child");
		stubReply(chatModel, VALID_REPLY);
		String before = tasksSnapshot();

		analysis(task, ana, null).andExpect(status().isOk());

		assertThat(tasksSnapshot()).isEqualTo(before);
	}

	@Test
	void withoutToken_returns401() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");

		mockMvc.perform(post(analysisOf(task))).andExpect(status().isUnauthorized());

		verify(chatModel, never()).call(any(Prompt.class));
	}

	// --- Helpers ---

	private ResultActions analysis(UUID task, UUID user, String acceptLanguage) throws Exception {
		var request = post(analysisOf(task)).with(jwt().jwt(token -> token.subject(user.toString())));
		if (acceptLanguage != null) {
			request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
		}
		return mockMvc.perform(request);
	}

	private static String analysisOf(UUID task) {
		return "/api/v1/tasks/" + task + "/ai/analysis";
	}

	/** The whole quota is still there: 30 calls succeed. */
	private void assertQuotaUnused(UUID user) {
		for (int i = 0; i < 30; i++) {
			assertThatCode(() -> quotaService.consume(user)).doesNotThrowAnyException();
		}
	}

	private String tasksSnapshot() {
		return jdbc.queryForList("SELECT * FROM TASK ORDER BY ID").toString();
	}
}
