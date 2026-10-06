package br.com.planned.api.exception;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Security is out of scope here: SecurityConfigTest covers it.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ GlobalExceptionHandler.class, GlobalExceptionHandlerTest.ProbeController.class })
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	/** The status of every code in PLAN §2. */
	@ParameterizedTest
	@CsvSource({
			"VALIDATION_ERROR, 400",
			"INVALID_STATUS, 400",
			"INVALID_PRIORITY, 400",
			"INVALID_COMPLEXITY, 400",
			"SUBTASK_DEPTH_EXCEEDED, 400",
			"UNAUTHORIZED, 401",
			"BAD_CREDENTIALS, 401",
			"TASK_NOT_FOUND, 404",
			"EMAIL_ALREADY_USED, 409",
			"AI_INVALID_RESPONSE, 422",
			"AI_RATE_LIMITED, 429",
			"AI_UNAVAILABLE, 503" })
	void apiExceptionReturnsItsCodeAndStatus(ErrorCode code, int expectedStatus) throws Exception {
		mockMvc.perform(get("/probe/api-exception/{code}", code))
				.andExpect(status().is(expectedStatus))
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(expectedStatus))
				.andExpect(jsonPath("$.code").value(code.name()))
				.andExpect(jsonPath("$.detail").value("probe detail"))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void invalidBodyReturnsFieldErrors() throws Exception {
		mockMvc.perform(post("/probe/body")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title": " ", "description": "too long"}
								"""))
				.andExpect(validationError())
				.andExpect(jsonPath("$.errors", hasSize(2)))
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "description")))
				.andExpect(jsonPath("$.errors[?(@.field == 'title')].message").value("must not be blank"));
	}

	@Test
	void invalidRequestParamReturnsFieldErrors() throws Exception {
		mockMvc.perform(get("/probe/param").param("size", "1000"))
				.andExpect(validationError())
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("size"))
				.andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));
	}

	@Test
	void malformedPathVariableReturnsFieldErrors() throws Exception {
		mockMvc.perform(get("/probe/tasks/{id}", "not-a-uuid"))
				.andExpect(validationError())
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("id"))
				.andExpect(jsonPath("$.errors[0].message").value("must be a valid UUID"));
	}

	@Test
	void malformedJsonReturnsValidationError() throws Exception {
		mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(validationError())
				.andExpect(jsonPath("$.detail").value("Request body is missing or malformed"))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void missingBodyReturnsValidationError() throws Exception {
		mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON))
				.andExpect(validationError())
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	private static ResultMatcher validationError() {
		List<ResultMatcher> matchers = List.of(
				status().isBadRequest(),
				content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
				jsonPath("$.status").value(400),
				jsonPath("$.code").value("VALIDATION_ERROR"));
		return result -> {
			for (ResultMatcher matcher : matchers) {
				matcher.match(result);
			}
		};
	}

	record ProbeBody(@NotBlank String title, @NotBlank @Size(max = 5) String description) {
	}

	/** Triggers each exception type the handler covers. */
	@RestController
	static class ProbeController {

		@GetMapping("/probe/api-exception/{code}")
		void apiException(@PathVariable ErrorCode code) {
			throw new ApiException(code, "probe detail");
		}

		@PostMapping("/probe/body")
		void body(@Valid @RequestBody ProbeBody body) {
		}

		@GetMapping("/probe/param")
		void param(@RequestParam @Max(100) int size) {
		}

		@GetMapping("/probe/tasks/{id}")
		void task(@PathVariable UUID id) {
		}
	}
}
