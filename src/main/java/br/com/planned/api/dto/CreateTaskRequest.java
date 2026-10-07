package br.com.planned.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/tasks} (PLAN §2, Validation and defaults). A new task always starts as
 * {@code TODO}, then the overdue rule applies (wave 2, D4).
 */
public record CreateTaskRequest(
		@Schema(example = "Write the quarterly report", maxLength = 100)
		@NotBlank @Size(max = 100) String title,

		@Schema(example = "Collect the numbers and draft the summary", maxLength = 500)
		@NotBlank @Size(max = 500) String description,

		@Schema(example = "2026-10-31", description = "Optional")
		LocalDate dueDate,

		@Schema(example = "HIGH", description = "LOW, MEDIUM, or HIGH; defaults to MEDIUM")
		String priority,

		@Schema(example = "MEDIUM", description = "EASY, MEDIUM, HARD, or null (the default)")
		String complexity) {
}
