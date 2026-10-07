package br.com.planned.api.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.planned.api.dto.SuggestRequest;
import br.com.planned.api.dto.SuggestResponse;
import br.com.planned.api.service.ai.AiLocales;
import br.com.planned.api.service.ai.TaskSuggestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** AI endpoints that aren't about one saved task (PLAN §5). Breakdown lives in {@code TaskAiController}. */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "AI suggestions. Nothing is saved until the user saves it through the task endpoints.")
public class AiController {

	private final TaskSuggestionService suggestionService;

	public AiController(TaskSuggestionService suggestionService) {
		this.suggestionService = suggestionService;
	}

	@PostMapping("/suggest")
	@Operation(summary = "Suggest a clearer title and description, a priority, and a complexity for a task",
			description = "Text comes back in the Accept-Language language (pt* → Brazilian Portuguese, anything else → English). "
					+ "Uses one unit of the hourly AI quota. Nothing is saved.")
	@ApiResponse(responseCode = "200", description = "The suggestion")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "AI_INVALID_RESPONSE: the AI's answer couldn't be used; try again",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "429", description = "AI_RATE_LIMITED: the hourly AI quota is used up",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "AI_UNAVAILABLE: the AI failed or timed out",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	SuggestResponse suggest(@Valid @RequestBody SuggestRequest request,
			@Parameter(description = "en or pt-BR") @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return suggestionService.suggest(request, AiLocales.fromAcceptLanguage(acceptLanguage));
	}
}
