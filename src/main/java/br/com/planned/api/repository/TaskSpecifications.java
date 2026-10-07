package br.com.planned.api.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;

import org.springframework.data.jpa.domain.Specification;

import br.com.planned.api.entity.Task;

/**
 * The building blocks of the task list query ({@code GET /api/v1/tasks}, PLAN §4). Lookups are
 * matched by id, which the caller resolves from their names through {@code LookupService}.
 */
public final class TaskSpecifications {

	/** The escape character of {@link #matches}'s {@code LIKE} pattern. */
	private static final char ESCAPE = '\\';

	/** The fields the list can be sorted on (PLAN §4), with their query-parameter names. */
	public enum SortField {
		DUE_DATE("dueDate"),
		PRIORITY("priority"),
		CREATED_AT("createdAt"),
		TITLE("title");

		private final String parameter;

		SortField(String parameter) {
			this.parameter = parameter;
		}

		public String parameter() {
			return parameter;
		}
	}

	private TaskSpecifications() {
	}

	public static Specification<Task> ownedBy(UUID userId) {
		return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
	}

	/** Top-level tasks only: no parent. */
	public static Specification<Task> topLevel() {
		return (root, query, cb) -> cb.isNull(root.get("parent"));
	}

	public static Specification<Task> statusIn(Collection<Integer> statusIds) {
		return (root, query, cb) -> root.get("status").get("id").in(statusIds);
	}

	public static Specification<Task> priorityIn(Collection<Integer> priorityIds) {
		return (root, query, cb) -> root.get("priority").get("id").in(priorityIds);
	}

	public static Specification<Task> complexityIn(Collection<Integer> complexityIds) {
		return (root, query, cb) -> root.get("complexity").get("id").in(complexityIds);
	}

	/** Due on {@code date} or later. A task with no due date never matches. */
	public static Specification<Task> dueOnOrAfter(LocalDate date) {
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), date);
	}

	/** Due on {@code date} or earlier. A task with no due date never matches. */
	public static Specification<Task> dueOnOrBefore(LocalDate date) {
		return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), date);
	}

	/**
	 * The title or the description contains {@code text}, in any letter case (wave 2, D8).
	 * {@code %}, {@code _}, and {@code \} in {@code text} match literally.
	 */
	public static Specification<Task> matches(String text) {
		String pattern = "%" + escapeLike(text) + "%";
		return (root, query, cb) -> {
			Expression<String> lowerPattern = cb.lower(cb.literal(pattern));
			return cb.or(
					cb.like(cb.lower(root.get("title")), lowerPattern, ESCAPE),
					cb.like(cb.lower(root.get("description")), lowerPattern, ESCAPE));
		};
	}

	/**
	 * Orders the query; matches every row. {@code priority} sorts by id, which is seed order
	 * {@code LOW < MEDIUM < HIGH} (D3). Tasks without a due date come last in both directions,
	 * and ties are broken by {@code createdAt desc}, then {@code id asc}, so pages are stable (D7).
	 *
	 * <p>Use it on the data query only, never on the count query.
	 */
	public static Specification<Task> sortedBy(SortField field, boolean ascending) {
		return (root, query, cb) -> {
			Expression<?> key = switch (field) {
				case DUE_DATE -> root.get("dueDate");
				case PRIORITY -> root.get("priority").get("id");
				case CREATED_AT -> root.get("createdAt");
				case TITLE -> root.get("title");
			};
			query.orderBy(List.of(
					ascending ? cb.asc(key, Nulls.LAST) : cb.desc(key, Nulls.LAST),
					cb.desc(root.get("createdAt")),
					cb.asc(root.get("id"))));
			return null;
		};
	}

	static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
