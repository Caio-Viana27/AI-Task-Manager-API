package br.com.planned.api.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.planned.api.config.AppProperties;
import br.com.planned.api.dto.AncestorSummary;
import br.com.planned.api.dto.CreateTaskRequest;
import br.com.planned.api.dto.PatchTaskRequest;
import br.com.planned.api.dto.SubtaskSummary;
import br.com.planned.api.dto.TaskResponse;
import br.com.planned.api.dto.UpdateTaskRequest;
import br.com.planned.api.entity.Complexity;
import br.com.planned.api.entity.Priority;
import br.com.planned.api.entity.Task;
import br.com.planned.api.entity.TaskStatus;
import br.com.planned.api.entity.User;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.repository.SubtaskCount;
import br.com.planned.api.repository.TaskRepository;
import br.com.planned.api.repository.UserRepository;

/**
 * Task create, read, update, and delete (PLAN §2 and §4), always scoped to the caller: another
 * user's task is a {@code 404 TASK_NOT_FOUND}, exactly like a task that doesn't exist.
 *
 * <p>The building blocks other services need are public: {@link #loadOwned}, {@link #newTask},
 * {@link #depth}, {@link #canAddSubtasks}, {@link #requireCanAddSubtasks}, and
 * {@link #applyOverdueRule}.
 */
@Service
public class TaskService {

	private final TaskRepository taskRepository;
	private final UserRepository userRepository;
	private final LookupService lookupService;
	private final CurrentUserService currentUserService;
	private final AppProperties properties;
	private final Clock clock;

	public TaskService(TaskRepository taskRepository, UserRepository userRepository, LookupService lookupService,
			CurrentUserService currentUserService, AppProperties properties, Clock clock) {
		this.taskRepository = taskRepository;
		this.userRepository = userRepository;
		this.lookupService = lookupService;
		this.currentUserService = currentUserService;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public TaskResponse create(CreateTaskRequest request) {
		User user = userRepository.getReferenceById(currentUserService.currentUserId());
		Task task = taskRepository.save(newTask(user, null, request));
		return TaskResponse.from(task);
	}

	/** The single-task view: the task, its ancestors (root first), and its direct subtasks. */
	@Transactional(readOnly = true)
	public TaskResponse get(UUID id) {
		Task task = loadOwned(id);
		UUID userId = task.getUser().getId();
		List<AncestorSummary> ancestors = taskRepository.findAncestors(id, userId).stream()
				.map(ancestor -> new AncestorSummary(ancestor.getId(), ancestor.getTitle()))
				.toList();
		return TaskResponse.withTree(task, ancestors, canAddSubtasks(ancestors.size() + 1), subtasks(id, userId));
	}

	/** {@code PUT}: replaces every editable field. */
	@Transactional
	public TaskResponse update(UUID id, UpdateTaskRequest request) {
		Task task = loadOwned(id);
		task.setTitle(request.title());
		task.setDescription(request.description());
		task.setDueDate(request.dueDate());
		task.setPriority(lookupService.priority(request.priority()));
		task.setStatus(userStatus(request.status()));
		task.setComplexity(complexityOrNull(request.complexity()));
		return save(task);
	}

	/** {@code PATCH}: changes only the fields that were sent (wave 2, D2). */
	@Transactional
	public TaskResponse patch(UUID id, PatchTaskRequest request) {
		Task task = loadOwned(id);
		if (request.isPresent(PatchTaskRequest.TITLE)) {
			task.setTitle(request.getTitle());
		}
		if (request.isPresent(PatchTaskRequest.DESCRIPTION)) {
			task.setDescription(request.getDescription());
		}
		if (request.isPresent(PatchTaskRequest.DUE_DATE)) {
			task.setDueDate(request.getDueDate());
		}
		if (request.isPresent(PatchTaskRequest.PRIORITY)) {
			task.setPriority(lookupService.priority(request.getPriority()));
		}
		if (request.isPresent(PatchTaskRequest.STATUS)) {
			task.setStatus(userStatus(request.getStatus()));
		}
		if (request.isPresent(PatchTaskRequest.COMPLEXITY)) {
			task.setComplexity(complexityOrNull(request.getComplexity()));
		}
		return save(task);
	}

	/** Deletes the task and, through the database cascade, its whole subtree (wave 2, D1). */
	@Transactional
	public void delete(UUID id) {
		if (taskRepository.deleteByIdAndUserId(id, currentUserService.currentUserId()) == 0) {
			throw notFound();
		}
	}

	/**
	 * Loads a task the caller owns.
	 *
	 * @throws ApiException {@code TASK_NOT_FOUND} if it doesn't exist or belongs to another user
	 */
	public Task loadOwned(UUID id) {
		return taskRepository.findByIdAndUserId(id, currentUserService.currentUserId()).orElseThrow(TaskService::notFound);
	}

	/**
	 * Builds (doesn't save) a new task with the PLAN §2 defaults: priority {@code MEDIUM}, status
	 * {@code TODO}, no complexity. The overdue rule is already applied (wave 2, D4).
	 *
	 * @param user   the owner; a subtask must get its parent's user
	 * @param parent {@code null} for a top-level task. The caller checks the depth limit
	 *               ({@link #requireCanAddSubtasks}) and sets the position (wave 2, D5).
	 * @throws ApiException {@code INVALID_PRIORITY} or {@code INVALID_COMPLEXITY} for an unknown name
	 */
	public Task newTask(User user, Task parent, CreateTaskRequest request) {
		Priority priority = request.priority() == null
				? lookupService.priority(Priority.MEDIUM)
				: lookupService.priority(request.priority());
		Task task = new Task(user, parent, request.title(), request.description(), priority,
				lookupService.status(TaskStatus.TODO), Instant.now(clock));
		task.setDueDate(request.dueDate());
		task.setComplexity(complexityOrNull(request.complexity()));
		applyOverdueRule(task);
		return task;
	}

	/**
	 * The task's depth in the tree: 1 for a top-level task (wave 2, D1). One query.
	 */
	public int depth(Task task) {
		return taskRepository.findAncestors(task.getId(), task.getUser().getId()).size() + 1;
	}

	/** Whether a task at {@code depth} may get subtasks: only below {@code app.tasks.max-depth}. */
	public boolean canAddSubtasks(int depth) {
		return depth < properties.tasks().maxDepth();
	}

	/** @throws ApiException {@code SUBTASK_DEPTH_EXCEEDED} if {@code parent} is at the maximum depth */
	public void requireCanAddSubtasks(Task parent) {
		if (!canAddSubtasks(depth(parent))) {
			throw new ApiException(ErrorCode.SUBTASK_DEPTH_EXCEEDED,
					"The task is at the maximum depth of " + properties.tasks().maxDepth() + " and can't get subtasks");
		}
	}

	/**
	 * The overdue rule, run after every write (PLAN §2 Status rules, wave 2 D4), with "today" from
	 * the {@code Clock} in {@code app.timezone}:
	 * <ul>
	 * <li>{@code TODO} or {@code IN_PROGRESS} with a due date before today becomes {@code OVERDUE};</li>
	 * <li>{@code OVERDUE} with a due date of today or later, or none, goes back to {@code TODO};</li>
	 * <li>{@code DONE} never changes.</li>
	 * </ul>
	 */
	public void applyOverdueRule(Task task) {
		String status = task.getStatus().getName();
		boolean late = task.getDueDate() != null && task.getDueDate().isBefore(today());
		if (late && (TaskStatus.TODO.equals(status) || TaskStatus.IN_PROGRESS.equals(status))) {
			task.setStatus(lookupService.status(TaskStatus.OVERDUE));
		} else if (!late && TaskStatus.OVERDUE.equals(status)) {
			task.setStatus(lookupService.status(TaskStatus.TODO));
		}
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(properties.timezone()));
	}

	private TaskResponse save(Task task) {
		applyOverdueRule(task);
		task.setUpdatedAt(Instant.now(clock));
		return TaskResponse.from(taskRepository.save(task));
	}

	/** Direct children in sibling order, each with its own child count, in two queries total. */
	private List<SubtaskSummary> subtasks(UUID parentId, UUID userId) {
		List<Task> children = taskRepository.findChildren(parentId, userId);
		if (children.isEmpty()) {
			return List.of();
		}
		Map<UUID, Long> counts = taskRepository.countChildren(children.stream().map(Task::getId).toList()).stream()
				.collect(Collectors.toMap(SubtaskCount::parentId, SubtaskCount::count));
		return children.stream()
				.map(child -> SubtaskSummary.from(child, counts.getOrDefault(child.getId(), 0L)))
				.toList();
	}

	/** A status a user may set: anything but {@code OVERDUE} (PLAN §2). */
	private TaskStatus userStatus(String name) {
		if (TaskStatus.OVERDUE.equals(name)) {
			throw new ApiException(ErrorCode.INVALID_STATUS, "OVERDUE is set only by the system");
		}
		return lookupService.status(name);
	}

	private Complexity complexityOrNull(String name) {
		return name == null ? null : lookupService.complexity(name);
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.TASK_NOT_FOUND, "Task not found");
	}
}
