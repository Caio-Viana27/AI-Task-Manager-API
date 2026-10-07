package br.com.planned.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.planned.api.dto.SubtaskDraft;
import br.com.planned.api.service.ai.AiLocales;
import br.com.planned.api.service.ai.TaskBreakdownService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** AI endpoints for one saved task (PLAN §5, {@code POST /api/v1/tasks/{id}/ai/breakdown}). */
@RestController
@RequestMapping("/api/v1/tasks/{id}/ai")
@Tag(name = "AI")
public class TaskAiController {

	private final TaskBreakdownService breakdownService;

	public TaskAiController(TaskBreakdownService breakdownService) {
		this.breakdownService = breakdownService;
	}

	@PostMapping("/breakdown")
	@Operation(summary = "Draft 2–8 subtasks for a task",
			description = "The drafts fit the task's ancestors and don't repeat its existing subtasks. Text comes back in the "
					+ "Accept-Language language. Uses one unit of the hourly AI quota. Nothing is saved: create the drafts "
					+ "you keep with POST /api/v1/tasks/{id}/subtasks.")
	@ApiResponse(responseCode = "200", description = "The drafts, in suggested order")
	@ApiResponse(responseCode = "400", description = "SUBTASK_DEPTH_EXCEEDED: the task is at the maximum depth",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND: no such task, or it belongs to another user",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "AI_INVALID_RESPONSE: the AI's answer couldn't be used; try again",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "429", description = "AI_RATE_LIMITED: the hourly AI quota is used up",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "AI_UNAVAILABLE: the AI failed or timed out",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	List<SubtaskDraft> breakdown(@PathVariable UUID id,
			@Parameter(description = "en or pt-BR") @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return breakdownService.breakdown(id, AiLocales.fromAcceptLanguage(acceptLanguage));
	}
}
