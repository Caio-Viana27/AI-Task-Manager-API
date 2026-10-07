package br.com.planned.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The AI's suggestion for a task (PLAN §5). It is also the record the model's reply is converted
 * to, so its constraints are the wave 3 D6 limits. Text is trimmed before it is checked.
 */
public record SuggestResponse(
		@Schema(example = "Write the Q3 sales report", maxLength = 100)
		@NotBlank @Size(max = 100) String suggestedTitle,

		@Schema(example = "Collect the Q3 sales numbers and write the report.", maxLength = 500)
		@NotBlank @Size(max = 500) String suggestedDescription,

		@Schema(example = "HIGH", description = "One of the PRIORITIES names")
		@NotBlank String suggestedPriority,

		@Schema(example = "MEDIUM", description = "One of the COMPLEXITIES names; never null")
		@NotBlank String suggestedComplexity,

		@Schema(example = "Reports have a fixed deadline; gathering numbers takes some effort.", maxLength = 1000)
		@NotBlank @Size(max = 1000) String reasoning) {

	public SuggestResponse {
		suggestedTitle = trim(suggestedTitle);
		suggestedDescription = trim(suggestedDescription);
		suggestedPriority = trim(suggestedPriority);
		suggestedComplexity = trim(suggestedComplexity);
		reasoning = trim(reasoning);
	}

	private static String trim(String value) {
		return value == null ? null : value.strip();
	}
}
