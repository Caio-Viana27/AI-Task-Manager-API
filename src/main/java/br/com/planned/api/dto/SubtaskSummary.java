package br.com.planned.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import br.com.planned.api.entity.Task;

/**
 * A direct subtask in {@code GET /tasks/{id}} (PLAN §2, SubtaskSummary object).
 *
 * @param subtaskCount the number of the subtask's own direct children
 */
public record SubtaskSummary(UUID id, String title, String status, String priority, LocalDate dueDate,
		long subtaskCount) {

	/** The task's priority and status must be loaded (or cheap to load). */
	public static SubtaskSummary from(Task task, long subtaskCount) {
		return new SubtaskSummary(task.getId(), task.getTitle(), task.getStatus().getName(),
				task.getPriority().getName(), task.getDueDate(), subtaskCount);
	}
}
