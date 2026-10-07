package br.com.planned.api.controller;

import static br.com.planned.api.support.AiStubs.googleError;
import static br.com.planned.api.support.AiStubs.lastPrompt;
import static br.com.planned.api.support.AiStubs.stubFailure;
import static br.com.planned.api.support.AiStubs.stubReply;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** {@code POST /api/v1/tasks/{id}/ai/breakdown} (PLAN §5, wave 3 T3.3) with a mocked chat model. */
@AutoConfigureMockMvc
class TaskAiControllerTest extends IntegrationTest {

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
		ana = TestRows.insertUser(jdbc, "ana-breakdown@example.com");
		bob = TestRows.insertUser(jdbc, "bob-breakdown@example.com");
	}

	@AfterEach
	void cleanUp() {
		TestRows.deleteUsersAndTasks(jdbc, ana, bob);
	}

	@Test
	void breakdown_returnsTheDraftsInTheModelsOrder() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Write the report");
		stubReply(chatModel, drafts(3));

		breakdown(task, ana, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].title").value(contains("Step 1", "Step 2", "Step 3")))
				.andExpect(jsonPath("$[0].description").value("Do step 1"))
				.andExpect(jsonPath("$[0].priority").value("HIGH"))
				.andExpect(jsonPath("$[0].complexity").value("EASY"));

		assertThat(lastPrompt(chatModel).getInstructions().get(0).getText()).contains("Write every text field in English");
	}

	@Test
	void breakdownOfADepth3Task_sendsAncestorsRootFirst_andExistingSubtasks() throws Exception {
		UUID root = TestRows.insertTask(jdbc, ana, null, "Root task");
		UUID middle = TestRows.insertTask(jdbc, ana, root, "Middle task");
		UUID task = TestRows.insertTask(jdbc, ana, middle, "Deep task");
		TestRows.insertTask(jdbc, ana, task, "Already there");
		stubReply(chatModel, drafts(2));

		breakdown(task, ana, "pt-BR").andExpect(status().isOk());

		var messages = lastPrompt(chatModel).getInstructions();
		assertThat(messages.get(0).getText()).contains("Write every text field in Brazilian Portuguese");
		assertThat(messages.get(1).getText())
				.contains("\"task\":{\"title\":\"Deep task\",\"description\":\"description\"}")
				.contains("\"ancestors\":[\"Root task\",\"Middle task\"]")
				.contains("\"existingSubtasks\":[\"Already there\"]");
	}

	@Test
	void anotherUsersTask_unknownId_andDepth5_neverCallTheModelOrUseQuota() throws Exception {
		UUID bobsTask = TestRows.insertTask(jdbc, bob, null, "Bob's");
		UUID deepest = TestRows.insertTask(jdbc, ana, null, "Level 1");
		for (int level = 2; level <= 5; level++) {
			deepest = TestRows.insertTask(jdbc, ana, deepest, "Level " + level);
		}

		breakdown(bobsTask, ana, null).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
		breakdown(UUID.randomUUID(), ana, null).andExpect(status().isNotFound());
		breakdown(deepest, ana, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SUBTASK_DEPTH_EXCEEDED"));

		verify(chatModel, never()).call(any(Prompt.class));
		assertQuotaUnused(ana);
	}

	@Test
	void oneDraft_is422() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		stubReply(chatModel, drafts(1));

		breakdown(task, ana, null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));
	}

	@Test
	void nineDrafts_is422() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		stubReply(chatModel, drafts(9));

		breakdown(task, ana, null).andExpect(status().isUnprocessableContent());
	}

	@Test
	void unknownComplexity_is422() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		stubReply(chatModel, drafts(3).replaceFirst("\"EASY\"", "\"TRIVIAL\""));

		breakdown(task, ana, null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));
	}

	@Test
	void draftWithBlankTitle_is422() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		stubReply(chatModel, drafts(3).replaceFirst("Step 1", "  "));

		breakdown(task, ana, null).andExpect(status().isUnprocessableContent());
	}

	@Test
	void modelFailure_is503() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		stubFailure(chatModel, googleError(503));

		breakdown(task, ana, null).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
	}

	@Test
	void breakdown_writesNothing() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");
		stubReply(chatModel, drafts(3));
		String before = tasksSnapshot();

		breakdown(task, ana, null).andExpect(status().isOk());

		assertThat(tasksSnapshot()).isEqualTo(before);
	}

	@Test
	void withoutToken_returns401() throws Exception {
		UUID task = TestRows.insertTask(jdbc, ana, null, "Task");

		mockMvc.perform(post(breakdownOf(task))).andExpect(status().isUnauthorized());

		verify(chatModel, never()).call(any(Prompt.class));
	}

	// --- Helpers ---

	private ResultActions breakdown(UUID task, UUID user, String acceptLanguage) throws Exception {
		var request = post(breakdownOf(task)).with(jwt().jwt(token -> token.subject(user.toString())));
		if (acceptLanguage != null) {
			request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
		}
		return mockMvc.perform(request);
	}

	private static String breakdownOf(UUID task) {
		return "/api/v1/tasks/" + task + "/ai/breakdown";
	}

	/** A model reply with {@code count} valid drafts named "Step 1", "Step 2", .... */
	private static String drafts(int count) {
		return IntStream.rangeClosed(1, count)
				.mapToObj(i -> """
						{ "title": "Step %d", "description": "Do step %d", "priority": "HIGH", "complexity": "EASY" }"""
						.formatted(i, i))
				.collect(Collectors.joining(", ", "{ \"subtasks\": [", "] }"));
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
