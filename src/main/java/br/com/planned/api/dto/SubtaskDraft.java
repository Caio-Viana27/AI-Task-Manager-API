package br.com.planned.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One AI-drafted subtask (PLAN §5, Breakdown). Nothing is saved: the user edits the drafts and
 * creates them through {@code POST /tasks/{id}/subtasks}. Its constraints are the task limits
 * (wave 3, D6); text is trimmed before it is checked.
 */
public record SubtaskDraft(
		@Schema(example = "Collect the Q3 numbers", maxLength = 100)
		@NotBlank @Size(max = 100) String title,

		@Schema(example = "Export the Q3 sales from the CRM.", maxLength = 500)
		@NotBlank @Size(max = 500) String description,

		@Schema(example = "HIGH", description = "One of the PRIORITIES names")
		@NotBlank String priority,

		@Schema(example = "EASY", description = "One of the COMPLEXITIES names; never null")
		@NotBlank String complexity) {

	public SubtaskDraft {
		title = trim(title);
		description = trim(description);
		priority = trim(priority);
		complexity = trim(complexity);
	}

	private static String trim(String value) {
		return value == null ? null : value.strip();
	}
}
