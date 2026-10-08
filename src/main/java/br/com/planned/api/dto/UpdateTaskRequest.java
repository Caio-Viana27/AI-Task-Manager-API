package br.com.planned.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code PUT /api/v1/tasks/{id}}: replaces every editable field. A {@code null} {@code dueDate},
 * {@code complexity}, or {@code estimatedHours} clears it; the other fields are required.
 */
public record UpdateTaskRequest(
		@Schema(example = "Write the quarterly report", maxLength = 100)
		@NotBlank @Size(max = 100) String title,

		@Schema(example = "Collect the numbers and draft the summary", maxLength = 500)
		@NotBlank @Size(max = 500) String description,

		@Schema(example = "2026-10-31", description = "null clears it")
		LocalDate dueDate,

		@Schema(example = "HIGH", description = "LOW, MEDIUM, or HIGH")
		@NotNull String priority,

		@Schema(example = "IN_PROGRESS", description = "TODO, IN_PROGRESS, or DONE. OVERDUE is set only by the system.")
		@NotNull String status,

		@Schema(example = "MEDIUM", description = "EASY, MEDIUM, HARD, or null to clear it")
		String complexity,

		@Schema(example = "8", minimum = "1", maximum = "999", description = "Estimated effort in whole hours, 1 to 999, or null to clear it")
		@Min(1) @Max(999) Integer estimatedHours) {
}
