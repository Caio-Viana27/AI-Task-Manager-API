package br.com.planned.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import br.com.planned.api.dto.validation.MaxUtf8Bytes;

import io.swagger.v3.oas.annotations.media.Schema;

/** Field limits follow the {@code USERS} columns and BCrypt (wave 1, D5). */
public record SignUpRequest(
		@Schema(example = "ana@example.com", description = "Stored lowercase")
		@NotBlank @Email @Size(max = 100) String email,

		@Schema(example = "Ana", description = "Display name; leading and trailing spaces are removed")
		@NotBlank @Size(max = 100) String name,

		@Schema(example = "correct horse battery", description = "At least 8 characters and at most 72 UTF-8 bytes")
		@NotNull @Size(min = 8) @MaxUtf8Bytes(72) String password) {
}
