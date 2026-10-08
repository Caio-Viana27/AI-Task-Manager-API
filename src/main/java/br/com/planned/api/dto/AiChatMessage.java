package br.com.planned.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One earlier turn of a chat, sent back by the client as context (PLAN §5, wave 4 D2). The
 * content limit leaves room for a full assistant reply (D3).
 */
public record AiChatMessage(
		@Schema(example = "user", allowableValues = { "user", "assistant" })
		@NotNull @Pattern(regexp = "user|assistant") String role,

		@Schema(example = "Do I have overdue tasks?", maxLength = 2000)
		@NotBlank @Size(max = 2000) String content) {
}
