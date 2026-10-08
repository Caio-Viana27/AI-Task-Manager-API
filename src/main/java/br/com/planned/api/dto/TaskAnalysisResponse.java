package br.com.planned.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The AI's analysis of a saved task (wave 4, D13). It is also the record the model's reply is
 * converted to, so its constraints are the D13 limits. Text is trimmed before it is checked.
 * Nothing is saved: the user applies the values through the normal task endpoints.
 */
public record TaskAnalysisResponse(
		@Schema(example = "HIGH", description = "One of the PRIORITIES names")
		@NotBlank String priority,

		@Schema(example = "MEDIUM", description = "One of the COMPLEXITIES names; never null")
		@NotBlank String complexity,

		@Schema(example = "6", minimum = "1", maximum = "999", description = "Estimated effort in whole hours")
		@NotNull @Min(1) @Max(999) Integer estimatedHours,

		@Schema(example = "The report is due this week and needs data from two teams.", maxLength = 1000)
		@NotBlank @Size(max = 1000) String reason) {

	public TaskAnalysisResponse {
		priority = trim(priority);
		complexity = trim(complexity);
		reason = trim(reason);
	}

	private static String trim(String value) {
		return value == null ? null : value.strip();
	}
}
