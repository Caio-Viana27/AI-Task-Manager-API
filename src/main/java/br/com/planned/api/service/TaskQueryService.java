package br.com.planned.api.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.planned.api.dto.PageResponse;
import br.com.planned.api.dto.TaskResponse;
import br.com.planned.api.entity.Complexity;
import br.com.planned.api.entity.Priority;
import br.com.planned.api.entity.Task;
import br.com.planned.api.entity.TaskStatus;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.repository.TaskRepository;
import br.com.planned.api.repository.TaskSpecifications;
import br.com.planned.api.repository.TaskSpecifications.SortField;

/**
 * The task list: filter, sort, and paginate the caller's tasks (PLAN §4, {@code GET /tasks}).
 * Always scoped to the caller.
 */
@Service
public class TaskQueryService {

	public static final String DEFAULT_SORT = "dueDate,asc";
	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;
	public static final int MAX_QUERY_LENGTH = 100;

	private static final String ALLOWED_SORT_FIELDS = Arrays.stream(SortField.values())
			.map(SortField::parameter)
			.collect(Collectors.joining(", "));

	/**
	 * The list filters. Empty or null lists mean no filter on that field.
	 *
	 * @param q               trimmed; blank means no text filter (wave 2, D8)
	 * @param includeSubtasks {@code false} lists only top-level tasks
	 */
	public record Filters(
			List<String> statuses,
			List<String> priorities,
			List<String> complexities,
			LocalDate dueFrom,
			LocalDate dueTo,
			String q,
			boolean includeSubtasks) {
	}

	private final TaskRepository taskRepository;
	private final LookupService lookupService;
	private final CurrentUserService currentUserService;

	public TaskQueryService(TaskRepository taskRepository, LookupService lookupService,
			CurrentUserService currentUserService) {
		this.taskRepository = taskRepository;
		this.lookupService = lookupService;
		this.currentUserService = currentUserService;
	}

	/**
	 * @param page zero-based, at least 0
	 * @param size at least 1; anything above {@link #MAX_PAGE_SIZE} is capped to it
	 * @param sort {@code field} or {@code field,asc|desc}, with a field from {@link SortField}
	 * @throws ApiException {@code INVALID_STATUS}, {@code INVALID_PRIORITY}, or
	 *         {@code INVALID_COMPLEXITY} for an unknown name; {@code VALIDATION_ERROR} for a bad
	 *         sort or a {@code q} longer than {@link #MAX_QUERY_LENGTH}
	 */
	@Transactional(readOnly = true)
	public PageResponse<TaskResponse> list(Filters filters, int page, int size, String sort) {
		Specification<Task> where = Specification.allOf(conditions(filters));
		Specification<Task> sorted = where.and(sortSpecification(sort));
		PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
		return PageResponse.from(taskRepository.findAll(sorted, where, pageRequest), TaskResponse::from);
	}

	private List<Specification<Task>> conditions(Filters filters) {
		List<Specification<Task>> conditions = new ArrayList<>();
		conditions.add(TaskSpecifications.ownedBy(currentUserService.currentUserId()));
		if (!filters.includeSubtasks()) {
			conditions.add(TaskSpecifications.topLevel());
		}
		if (isPresent(filters.statuses())) {
			conditions.add(TaskSpecifications.statusIn(
					ids(filters.statuses(), lookupService::status, TaskStatus::getId)));
		}
		if (isPresent(filters.priorities())) {
			conditions.add(TaskSpecifications.priorityIn(
					ids(filters.priorities(), lookupService::priority, Priority::getId)));
		}
		if (isPresent(filters.complexities())) {
			conditions.add(TaskSpecifications.complexityIn(
					ids(filters.complexities(), lookupService::complexity, Complexity::getId)));
		}
		if (filters.dueFrom() != null) {
			conditions.add(TaskSpecifications.dueOnOrAfter(filters.dueFrom()));
		}
		if (filters.dueTo() != null) {
			conditions.add(TaskSpecifications.dueOnOrBefore(filters.dueTo()));
		}
		String q = filters.q() == null ? "" : filters.q().strip();
		if (q.length() > MAX_QUERY_LENGTH) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR,
					"q must be at most " + MAX_QUERY_LENGTH + " characters");
		}
		if (!q.isEmpty()) {
			conditions.add(TaskSpecifications.matches(q));
		}
		return conditions;
	}

	private static Specification<Task> sortSpecification(String sort) {
		String[] parts = (sort == null || sort.isBlank() ? DEFAULT_SORT : sort).split(",", -1);
		SortField field = Arrays.stream(SortField.values())
				.filter(candidate -> candidate.parameter().equals(parts[0].strip()))
				.findFirst()
				.orElseThrow(() -> invalidSort());
		if (parts.length > 2) {
			throw invalidSort();
		}
		String direction = parts.length == 2 ? parts[1].strip().toLowerCase(Locale.ROOT) : "asc";
		if (!direction.equals("asc") && !direction.equals("desc")) {
			throw invalidSort();
		}
		return TaskSpecifications.sortedBy(field, direction.equals("asc"));
	}

	private static ApiException invalidSort() {
		return new ApiException(ErrorCode.VALIDATION_ERROR,
				"sort must be field or field,asc|desc, with a field among: " + ALLOWED_SORT_FIELDS);
	}

	private static boolean isPresent(List<String> values) {
		return values != null && !values.isEmpty();
	}

	private static <T> List<Integer> ids(List<String> names, Function<String, T> resolve, Function<T, Integer> id) {
		return names.stream().map(resolve).map(id).distinct().toList();
	}
}
