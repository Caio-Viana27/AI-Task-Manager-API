package br.com.planned.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.planned.api.entity.Task;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Task object (PLAN §2). {@code ancestors}, {@code canAddSubtasks}, and {@code subtasks} are
 * filled only for the single-task response ({@code GET /tasks/{id}}); everywhere else they are
 * {@code null} and left out of the JSON.
 */
public record TaskResponse(
		UUID id,
		String title,
		String description,
		LocalDate dueDate,
		String priority,
		String status,
		String complexity,
		@Schema(example = "8", description = "Estimated effort in whole hours (1 to 999), or null")
		Integer estimatedHours,
		UUID parentTaskId,
		@Schema(description = "Root first; only in GET /tasks/{id}")
		@JsonInclude(JsonInclude.Include.NON_NULL) List<AncestorSummary> ancestors,
		@Schema(description = "false at the maximum depth; only in GET /tasks/{id}")
		@JsonInclude(JsonInclude.Include.NON_NULL) Boolean canAddSubtasks,
		@Schema(description = "Direct subtasks only; only in GET /tasks/{id}")
		@JsonInclude(JsonInclude.Include.NON_NULL) List<SubtaskSummary> subtasks,
		Instant createdAt,
		Instant updatedAt) {

	/**
	 * The list form, without the tree fields. Reads the task's lookups and its parent's id, so
	 * call it where they are loaded (or inside a transaction).
	 */
	public static TaskResponse from(Task task) {
		return withTree(task, null, null, null);
	}

	/** The single-task form, with the tree fields. */
	public static TaskResponse withTree(Task task, List<AncestorSummary> ancestors, Boolean canAddSubtasks,
			List<SubtaskSummary> subtasks) {
		return new TaskResponse(
				task.getId(),
				task.getTitle(),
				task.getDescription(),
				task.getDueDate(),
				task.getPriority().getName(),
				task.getStatus().getName(),
				task.getComplexity() == null ? null : task.getComplexity().getName(),
				task.getEstimatedHours(),
				task.getParent() == null ? null : task.getParent().getId(),
				ancestors,
				canAddSubtasks,
				subtasks,
				task.getCreatedAt(),
				task.getUpdatedAt());
	}
}
