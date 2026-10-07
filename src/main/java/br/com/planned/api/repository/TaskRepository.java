package br.com.planned.api.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.planned.api.entity.Task;
import br.com.planned.api.entity.TaskStatus;

/** Every lookup is scoped to the owner: another user's task is never found (PLAN §2, 404). */
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

	Optional<Task> findByIdAndUserId(UUID id, UUID userId);

	/**
	 * A page of tasks with their lookups loaded, so the page costs two queries (rows and count),
	 * however many tasks it holds. {@code spec} must include the owner ({@link TaskSpecifications}).
	 */
	@Override
	@EntityGraph(attributePaths = { "priority", "status", "complexity" })
	Page<Task> findAll(Specification<Task> spec, Specification<Task> countSpec, Pageable pageable);

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

	/**
	 * Marks every descendant of the task, at every depth, {@code DONE} unless it already is, with
	 * {@code updatedAt = now} (wave 2, D9). The task itself and its ancestors are untouched. Every
	 * row is also filtered by {@code userId}, as a second guard.
	 *
	 * @return the number of descendants changed
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			WITH RECURSIVE subtree (id) AS (
			    SELECT id FROM task WHERE parent_task_id = :taskId AND user_id = :userId
			  UNION ALL
			    SELECT t.id
			    FROM task t
			    JOIN subtree s ON t.parent_task_id = s.id
			    WHERE t.user_id = :userId
			)
			UPDATE task SET status_id = :doneStatusId, updated_at = :now
			WHERE id IN (SELECT id FROM subtree) AND status_id <> :doneStatusId
			""")
	int completeSubtree(@Param("taskId") UUID taskId, @Param("userId") UUID userId,
			@Param("doneStatusId") Integer doneStatusId, @Param("now") Instant now);

	/**
	 * The direct children of a task, with their priority and status loaded, in sibling order:
	 * {@code POSITION}, then {@code createdAt}, then {@code id} (wave 2, D5).
	 */
	@Query("""
			SELECT t FROM Task t
			JOIN FETCH t.priority
			JOIN FETCH t.status
			WHERE t.parent.id = :parentId AND t.user.id = :userId
			ORDER BY t.position, t.createdAt, t.id
			""")
	List<Task> findChildren(@Param("parentId") UUID parentId, @Param("userId") UUID userId);

	/** The highest {@code POSITION} among the task's direct children, or 0 if it has none (wave 2, D5). */
	@Query("SELECT COALESCE(MAX(t.position), 0) FROM Task t WHERE t.parent.id = :parentId")
	int maxChildPosition(@Param("parentId") UUID parentId);

	/** The number of direct children of each given task, in one query. Tasks with none are left out. */
	@Query("""
			SELECT new br.com.planned.api.repository.SubtaskCount(t.parent.id, COUNT(t))
			FROM Task t
			WHERE t.parent.id IN :parentIds
			GROUP BY t.parent.id
			""")
	List<SubtaskCount> countChildren(@Param("parentIds") Collection<UUID> parentIds);

	/**
	 * Deletes the task if {@code userId} owns it. One statement: the {@code ON DELETE CASCADE} on
	 * {@code PARENT_TASK_ID} removes the whole subtree (wave 2, D1).
	 *
	 * @return the number of tasks deleted directly (0 or 1), not counting the subtree
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM Task t WHERE t.id = :id AND t.user.id = :userId")
	int deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

	/**
	 * Sets {@code overdue} on every task with one of the {@code active} statuses that was due
	 * before {@code today}, for every user (the nightly job, PLAN §2).
	 *
	 * @return the number of tasks changed
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Task t SET t.status = :overdue, t.updatedAt = :now
			WHERE t.status IN :active AND t.dueDate < :today
			""")
	int markOverdue(@Param("overdue") TaskStatus overdue, @Param("active") Collection<TaskStatus> active,
			@Param("today") LocalDate today, @Param("now") Instant now);
}
