package br.com.planned.api.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.planned.api.dto.PageResponse;
import br.com.planned.api.dto.TaskResponse;
import br.com.planned.api.service.TaskQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The task list (PLAN §4, {@code GET /api/v1/tasks}). Single-task endpoints live in {@link TaskController}. */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks")
public class TaskQueryController {

	private final TaskQueryService taskQueryService;

	public TaskQueryController(TaskQueryService taskQueryService) {
		this.taskQueryService = taskQueryService;
	}

	@GetMapping
	@Operation(summary = "List, filter, sort, and paginate the caller's tasks",
			description = "Top-level tasks only unless includeSubtasks=true. Ties are broken by createdAt desc, "
					+ "then id asc; tasks without a due date come last in both directions.")
	@ApiResponse(responseCode = "200", description = "One page of matching tasks")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (bad page, size, sort, date, or q), "
			+ "INVALID_STATUS, INVALID_PRIORITY, or INVALID_COMPLEXITY",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	PageResponse<TaskResponse> list(
			@Parameter(description = "Repeatable: TODO, IN_PROGRESS, OVERDUE, DONE")
			@RequestParam(required = false) List<String> status,
			@Parameter(description = "Repeatable: LOW, MEDIUM, HIGH")
			@RequestParam(required = false) List<String> priority,
			@Parameter(description = "Repeatable: EASY, MEDIUM, HARD")
			@RequestParam(required = false) List<String> complexity,
			@Parameter(description = "Inclusive", example = "2026-10-01")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
			@Parameter(description = "Inclusive", example = "2026-10-31")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
			@Parameter(description = "Case-insensitive match on title and description; trimmed, at most 100 characters")
			@RequestParam(required = false) String q,
			@Parameter(description = "false lists only top-level tasks; true lists tasks at every depth")
			@RequestParam(defaultValue = "false") boolean includeSubtasks,
			@Parameter(description = "Zero-based")
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Capped at 100")
			@RequestParam(defaultValue = "" + TaskQueryService.DEFAULT_PAGE_SIZE) @Min(1) int size,
			@Parameter(description = "field or field,asc|desc; fields: dueDate, priority, createdAt, title")
			@RequestParam(defaultValue = TaskQueryService.DEFAULT_SORT) String sort) {
		var filters = new TaskQueryService.Filters(status, priority, complexity, dueFrom, dueTo, q, includeSubtasks);
		return taskQueryService.list(filters, page, size, sort);
	}
}
