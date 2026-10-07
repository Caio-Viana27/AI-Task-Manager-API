package br.com.planned.api.repository;

import java.util.UUID;

/** One row of {@link TaskRepository#countChildren}: how many direct children a task has. */
public record SubtaskCount(UUID parentId, long count) {
}
