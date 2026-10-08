package br.com.planned.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The assistant's answer (PLAN §5, wave 4 D3). It is also the record the model's reply is
 * converted to, so its constraints are the reply limits. Text is trimmed before it is checked.
 */
public record AiChatResponse(
		@Schema(example = "Yes: \"Pay the rent\" was due on 2026-10-01 and is overdue.", maxLength = 2000)
		@NotBlank @Size(max = 2000) String reply) {

	public AiChatResponse {
		reply = reply == null ? null : reply.strip();
	}
}
