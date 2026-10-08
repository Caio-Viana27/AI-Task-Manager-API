package br.com.planned.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/ai/chat} (PLAN §5, wave 4 D2): a question about the caller's tasks, with up
 * to 10 earlier turns, oldest first. Named {@code AiChat*} so it doesn't clash with Spring AI's
 * types (D1).
 */
public record AiChatRequest(
		@Schema(example = "Do I have overdue tasks?", maxLength = 1000)
		@NotBlank @Size(max = 1000) String message,

		@Schema(description = "Earlier turns, oldest first; optional", maxLength = 10)
		@Size(max = 10) List<@NotNull @Valid AiChatMessage> history) {

	public AiChatRequest {
		history = history == null ? List.of() : history;
	}
}
