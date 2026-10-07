package br.com.planned.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.planned.api.dto.CreateSubtaskRequest;
import br.com.planned.api.dto.TaskResponse;
import br.com.planned.api.service.SubtaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Subtask creation (PLAN §4, {@code POST /api/v1/tasks/{id}/subtasks}). */
@RestController
@RequestMapping("/api/v1/tasks/{id}/subtasks")
@Tag(name = "Tasks")
public class SubtaskController {

	public static final int MAX_SUBTASKS_PER_REQUEST = 10;

	private final SubtaskService subtaskService;

	public SubtaskController(SubtaskService subtaskService) {
		this.subtaskService = subtaskService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create 1–10 subtasks under a task",
			description = "All or nothing. Each item has the same defaults and validation as a task create; "
					+ "the subtasks are added after the existing ones, in request order.")
	@ApiResponse(responseCode = "201", description = "The created subtasks, in request order")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (including 0 or more than 10 items), "
			+ "INVALID_PRIORITY, INVALID_COMPLEXITY, or SUBTASK_DEPTH_EXCEEDED (the task is at the maximum depth)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND: no such task, or it belongs to another user",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	List<TaskResponse> create(@PathVariable UUID id,
			@RequestBody @Size(min = 1, max = MAX_SUBTASKS_PER_REQUEST) List<@Valid CreateSubtaskRequest> subtasks) {
		return subtaskService.create(id, subtasks);
	}
}
