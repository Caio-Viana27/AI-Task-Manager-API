package br.com.planned.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code POST /api/v1/ai/suggest} (PLAN §5): a task draft to improve, with the task limits. */
public record SuggestRequest(
		@Schema(example = "report", maxLength = 100)
		@NotBlank @Size(max = 100) String title,

		@Schema(example = "numbers for q3", maxLength = 500)
		@NotBlank @Size(max = 500) String description) {
}
