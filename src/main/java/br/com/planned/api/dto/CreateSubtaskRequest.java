package br.com.planned.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One item of {@code POST /api/v1/tasks/{id}/subtasks} (PLAN §4). Same fields, validation, and
 * defaults as a task create ({@link CreateTaskRequest}).
 */
public record CreateSubtaskRequest(
		@Schema(example = "Collect the numbers", maxLength = 100)
		@NotBlank @Size(max = 100) String title,

		@Schema(example = "Export last quarter's sales", maxLength = 500)
		@NotBlank @Size(max = 500) String description,

		@Schema(example = "2026-10-20", description = "Optional")
		LocalDate dueDate,

		@Schema(example = "HIGH", description = "LOW, MEDIUM, or HIGH; defaults to MEDIUM")
		String priority,

		@Schema(example = "EASY", description = "EASY, MEDIUM, HARD, or null (the default)")
		String complexity) {

	public CreateTaskRequest toCreateTaskRequest() {
		return new CreateTaskRequest(title, description, dueDate, priority, complexity);
	}
}
