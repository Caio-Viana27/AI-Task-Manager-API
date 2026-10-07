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

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.planned.api.service.ai.AiQuotaService;
import br.com.planned.api.support.IntegrationTest;
import br.com.planned.api.support.TestRows;

/** {@code POST /api/v1/ai/suggest} (PLAN §5, wave 3 T3.2) with a mocked chat model. */
@AutoConfigureMockMvc
class AiControllerTest extends IntegrationTest {

	private static final String SUGGEST = "/api/v1/ai/suggest";
	private static final String REQUEST = """
			{ "title": "report", "description": "numbers for q3" }
			""";
	private static final String VALID_REPLY = """
			{ "suggestedTitle": "Write the Q3 sales report",
			  "suggestedDescription": "Collect the Q3 sales numbers and write the report.",
			  "suggestedPriority": "HIGH", "suggestedComplexity": "MEDIUM",
			  "reasoning": "It has a deadline and takes some effort." }
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AiQuotaService quotaService;

	private UUID ana;

	@BeforeEach
	void setUp() {
		ana = TestRows.insertUser(jdbc, "ana-suggest@example.com");
	}

	@AfterEach
	void cleanUp() {
		TestRows.deleteUsersAndTasks(jdbc, ana);
	}

	@Test
	void suggest_returnsTheSuggestion_inEnglishByDefault() throws Exception {
		stubReply(chatModel, VALID_REPLY);

		suggest(REQUEST, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestedTitle").value("Write the Q3 sales report"))
				.andExpect(jsonPath("$.suggestedDescription").value("Collect the Q3 sales numbers and write the report."))
				.andExpect(jsonPath("$.suggestedPriority").value("HIGH"))
				.andExpect(jsonPath("$.suggestedComplexity").value("MEDIUM"))
				.andExpect(jsonPath("$.reasoning").value("It has a deadline and takes some effort."));

		List<Message> messages = lastPrompt(chatModel).getInstructions();
		assertThat(messages.get(0).getText()).contains("Write every text field in English")
				.contains("LOW, MEDIUM, HIGH").contains("EASY, MEDIUM, HARD");
		assertThat(messages.get(1).getText()).contains("\"title\":\"report\"").contains("\"description\":\"numbers for q3\"");
	}

	@Test
	void suggest_inPortuguese_asksForBrazilianPortuguese() throws Exception {
		stubReply(chatModel, VALID_REPLY);

		suggest(REQUEST, "pt-BR,pt;q=0.9,en;q=0.8").andExpect(status().isOk());

		assertThat(lastPrompt(chatModel).getInstructions().get(0).getText())
				.contains("Write every text field in Brazilian Portuguese");
	}

	@Test
	void invalidInput_is400_withoutCallingTheModelOrUsingQuota() throws Exception {
		suggest("{ \"title\": \" \", \"description\": \"d\" }", null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		suggest("{ \"title\": \"%s\", \"description\": \"d\" }".formatted("t".repeat(101)), null)
				.andExpect(status().isBadRequest());
		suggest("{ \"title\": \"t\", \"description\": \"%s\" }".formatted("d".repeat(501)), null)
				.andExpect(status().isBadRequest());
		suggest("{ \"title\": \"t\" }", null).andExpect(status().isBadRequest());

		verify(chatModel, never()).call(any(Prompt.class));
		assertQuotaUnused(ana);
	}

	@Test
	void tooLongSuggestedTitle_is422() throws Exception {
		stubReply(chatModel, VALID_REPLY.replace("Write the Q3 sales report", "t".repeat(300)));

		suggest(REQUEST, null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));
	}

	@Test
	void unknownSuggestedPriority_is422() throws Exception {
		stubReply(chatModel, VALID_REPLY.replace("\"HIGH\"", "\"URGENT\""));

		suggest(REQUEST, null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));
	}

	@Test
	void nullSuggestedComplexity_is422() throws Exception {
		stubReply(chatModel, VALID_REPLY.replace("\"MEDIUM\"", "null"));

		suggest(REQUEST, null).andExpect(status().isUnprocessableContent());
	}

	@Test
	void modelFailure_is503() throws Exception {
		stubFailure(chatModel, googleError(500));

		suggest(REQUEST, null).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
	}

	@Test
	void suggest_writesNothing() throws Exception {
		stubReply(chatModel, VALID_REPLY);
		TestRows.insertTask(jdbc, ana, null, "Existing");
		String before = tasksSnapshot();

		suggest(REQUEST, null).andExpect(status().isOk());

		assertThat(tasksSnapshot()).isEqualTo(before);
	}

	@Test
	void the31stCallInAnHour_is429() throws Exception {
		stubReply(chatModel, VALID_REPLY);
		for (int i = 0; i < 30; i++) {
			suggest(REQUEST, null).andExpect(status().isOk());
		}

		suggest(REQUEST, null).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("AI_RATE_LIMITED"));
	}

	@Test
	void withoutToken_returns401() throws Exception {
		mockMvc.perform(post(SUGGEST).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
				.andExpect(status().isUnauthorized());

		verify(chatModel, never()).call(any(Prompt.class));
	}

	// --- Helpers ---

	private ResultActions suggest(String json, String acceptLanguage) throws Exception {
		var request = post(SUGGEST).contentType(MediaType.APPLICATION_JSON).content(json)
				.with(jwt().jwt(token -> token.subject(ana.toString())));
		if (acceptLanguage != null) {
			request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
		}
		return mockMvc.perform(request);
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
