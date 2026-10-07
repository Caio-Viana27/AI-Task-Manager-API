package br.com.planned.api.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.planned.api.dto.CreateTaskRequest;
import br.com.planned.api.dto.PatchTaskRequest;
import br.com.planned.api.dto.TaskResponse;
import br.com.planned.api.dto.UpdateTaskRequest;
import br.com.planned.api.service.TaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Single-task endpoints (PLAN §4). Listing lives in {@code TaskQueryController}. */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Create, read, update, and delete the caller's own tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@PostMapping
	@Operation(summary = "Create a top-level task",
			description = "Starts as TODO, or OVERDUE if the due date is already past. Priority defaults to MEDIUM.")
	@ApiResponse(responseCode = "201", description = "Created; Location points to the new task")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, INVALID_PRIORITY, or INVALID_COMPLEXITY",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
		TaskResponse task = taskService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(task.id()).toUri();
		return ResponseEntity.created(location).body(task);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a task with its ancestors (root first) and its direct subtasks")
	@ApiResponse(responseCode = "200", description = "The task")
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND: no such task, or it belongs to another user",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	TaskResponse get(@PathVariable UUID id) {
		return taskService.get(id);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Replace every editable field",
			description = "A null dueDate or complexity clears it. OVERDUE can't be sent; the overdue rule runs after the update. "
					+ "Moving a task to DONE also marks its whole subtree DONE.")
	@ApiResponse(responseCode = "200", description = "The updated task")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, INVALID_STATUS (including OVERDUE), INVALID_PRIORITY, or INVALID_COMPLEXITY",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND: no such task, or it belongs to another user",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	TaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
		return taskService.update(id, request);
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Update only the fields that are sent",
			description = "An absent field is unchanged. null clears dueDate or complexity; null for any other field is a 400. "
					+ "OVERDUE can't be sent; the overdue rule runs after the update. "
					+ "Moving a task to DONE also marks its whole subtree DONE.")
	@ApiResponse(responseCode = "200", description = "The updated task")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, INVALID_STATUS (including OVERDUE), INVALID_PRIORITY, or INVALID_COMPLEXITY",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND: no such task, or it belongs to another user",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	TaskResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchTaskRequest request) {
		return taskService.patch(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a task and its whole subtree")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND: no such task, or it belongs to another user",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	void delete(@PathVariable UUID id) {
		taskService.delete(id);
	}
}
