package br.com.planned.api.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of the {@code app.*} properties. Startup fails if any of them is missing or invalid.
 *
 * @param timezone the zone that defines "today" (PLAN §0)
 * @param tasks    task tree settings (PLAN §0, Subtasks)
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
		@NotNull ZoneId timezone,
		@NotNull @Valid Jwt jwt,
		@NotNull @Valid Ai ai,
		@NotNull @Valid Cors cors,
		@NotNull @Valid Tasks tasks) {

	public record Jwt(@NotBlank String secret, @NotNull Duration ttl) {
	}

	public record Ai(@NotNull Duration timeout, @Positive int quotaPerHour) {
	}

	public record Cors(@NotEmpty List<String> allowedOrigins) {
	}

	/** @param maxDepth the deepest level of the task tree; a top-level task is depth 1 */
	public record Tasks(@Min(1) int maxDepth) {
	}
}
