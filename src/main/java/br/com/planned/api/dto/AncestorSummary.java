package br.com.planned.api.dto;

import java.util.UUID;

/** One step of a task's breadcrumb, root first (PLAN §2, {@code ancestors}). */
public record AncestorSummary(UUID id, String title) {
}
