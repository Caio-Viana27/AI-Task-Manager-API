package br.com.planned.api.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** No format or length checks, so a failed sign-in never reveals the sign-up rules (wave 1, D5). */
public record SignInRequest(
		@Schema(example = "ana@example.com") @NotBlank String email,
		@Schema(example = "correct horse battery") @NotBlank String password) {
}
