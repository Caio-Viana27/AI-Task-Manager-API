package br.com.planned.api.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import br.com.planned.api.dto.LookupsResponse;
import br.com.planned.api.entity.Complexity;
import br.com.planned.api.entity.Priority;
import br.com.planned.api.entity.TaskStatus;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.repository.ComplexityRepository;
import br.com.planned.api.repository.PriorityRepository;
import br.com.planned.api.repository.TaskStatusRepository;

/**
 * The seeded lookups ({@code PRIORITIES}, {@code TASK_STATUS}, {@code COMPLEXITIES}), loaded once
 * at startup and cached, in {@code ID} (seed) order (wave 2, D3). They only change through a
 * migration, so the cache never goes stale while the app runs.
 *
 * <p>The cached entities are detached. They can be assigned to a {@code Task}'s many-to-one
 * fields directly; only their id is used.
 */
@Service
public class LookupService {

	private static final Sort BY_ID = Sort.by("id");

	private final Map<String, Priority> priorities;
	private final Map<String, TaskStatus> statuses;
	private final Map<String, Complexity> complexities;
	private final LookupsResponse lookups;

	public LookupService(PriorityRepository priorityRepository, TaskStatusRepository statusRepository,
			ComplexityRepository complexityRepository) {
		this.priorities = byName(priorityRepository.findAll(BY_ID), Priority::getName);
		this.statuses = byName(statusRepository.findAll(BY_ID), TaskStatus::getName);
		this.complexities = byName(complexityRepository.findAll(BY_ID), Complexity::getName);
		this.lookups = new LookupsResponse(
				List.copyOf(priorities.keySet()),
				List.copyOf(statuses.keySet()),
				List.copyOf(complexities.keySet()));
	}

	/** Every lookup name, each list in seed order. */
	public LookupsResponse lookups() {
		return lookups;
	}

	/** Priorities in seed order, lowest first. */
	public List<Priority> priorities() {
		return List.copyOf(priorities.values());
	}

	public List<TaskStatus> statuses() {
		return List.copyOf(statuses.values());
	}

	public List<Complexity> complexities() {
		return List.copyOf(complexities.values());
	}

	/** @throws ApiException {@code INVALID_PRIORITY} if no priority has this exact name (or it's null) */
	public Priority priority(String name) {
		return resolve(priorities, name, ErrorCode.INVALID_PRIORITY, "priority");
	}

	/**
	 * Resolves any status, including {@code OVERDUE}. Rejecting {@code OVERDUE} sent by a user is
	 * the caller's job (PLAN §2, Status rules).
	 *
	 * @throws ApiException {@code INVALID_STATUS} if no status has this exact name (or it's null)
	 */
	public TaskStatus status(String name) {
		return resolve(statuses, name, ErrorCode.INVALID_STATUS, "status");
	}

	/** @throws ApiException {@code INVALID_COMPLEXITY} if no complexity has this exact name (or it's null) */
	public Complexity complexity(String name) {
		return resolve(complexities, name, ErrorCode.INVALID_COMPLEXITY, "complexity");
	}

	private static <T> Map<String, T> byName(List<T> rows, Function<T, String> name) {
		Map<String, T> map = new LinkedHashMap<>();
		rows.forEach(row -> map.put(name.apply(row), row));
		return map;
	}

	private static <T> T resolve(Map<String, T> values, String name, ErrorCode code, String label) {
		T value = name == null ? null : values.get(name);
		if (value == null) {
			throw new ApiException(code, "Unknown " + label + ". Allowed values: " + String.join(", ", values.keySet()));
		}
		return value;
	}
}
