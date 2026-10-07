package br.com.planned.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The model's breakdown reply (wave 3, D6). Internal: {@code AiClient} converts to a class, so the
 * list is wrapped; the endpoint returns the bare list.
 */
public record BreakdownResult(@NotNull @Size(min = 2, max = 8) List<@NotNull @Valid SubtaskDraft> subtasks) {
}
