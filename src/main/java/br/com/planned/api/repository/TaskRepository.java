package br.com.planned.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.planned.api.entity.Task;

/** Every lookup is scoped to the owner: another user's task is never found (PLAN §2, 404). */
public interface TaskRepository extends JpaRepository<Task, UUID> {

	Optional<Task> findByIdAndUserId(UUID id, UUID userId);

	/**
	 * The path from the top-level task down to the task's direct parent, root first (wave 2, D1).
	 * Empty for a top-level task, or when the task doesn't belong to {@code userId}. Every row is
	 * also filtered by {@code userId}, as a second guard. The task's depth is {@code size() + 1}.
	 */
	@Query(nativeQuery = true, value = """
			WITH RECURSIVE ancestors (id, title, parent_task_id, level) AS (
			    SELECT p.id, p.title, p.parent_task_id, 1
			    FROM task t
			    JOIN task p ON p.id = t.parent_task_id
			    WHERE t.id = :taskId AND t.user_id = :userId AND p.user_id = :userId
			  UNION ALL
			    SELECT p.id, p.title, p.parent_task_id, a.level + 1
			    FROM ancestors a
			    JOIN task p ON p.id = a.parent_task_id
			    WHERE p.user_id = :userId
			)
			SELECT id, title FROM ancestors ORDER BY level DESC
			""")
	List<TaskAncestor> findAncestors(@Param("taskId") UUID taskId, @Param("userId") UUID userId);
}
