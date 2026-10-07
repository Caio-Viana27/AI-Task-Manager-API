package br.com.planned.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.planned.api.dto.CreateSubtaskRequest;
import br.com.planned.api.dto.TaskResponse;
import br.com.planned.api.entity.Task;
import br.com.planned.api.repository.TaskRepository;

/** Creates subtasks under a task the caller owns (PLAN §0 Subtasks, §4 add subtask). */
@Service
public class SubtaskService {

	private final TaskService taskService;
	private final TaskRepository taskRepository;

	public SubtaskService(TaskService taskService, TaskRepository taskRepository) {
		this.taskService = taskService;
		this.taskRepository = taskRepository;
	}

	/**
	 * Creates the subtasks in one transaction, with the same defaults and overdue rule as a task
	 * create. They go after the parent's existing children, in request order (wave 2, D5). If any
	 * item is invalid, nothing is written.
	 *
	 * @return the created subtasks, in request order
	 * @throws br.com.planned.api.exception.ApiException {@code TASK_NOT_FOUND} if the caller doesn't
	 *         own the parent; {@code SUBTASK_DEPTH_EXCEEDED} if it is at the maximum depth;
	 *         {@code INVALID_PRIORITY} or {@code INVALID_COMPLEXITY} for an unknown name
	 */
	@Transactional
	public List<TaskResponse> create(UUID parentId, List<CreateSubtaskRequest> requests) {
		Task parent = taskService.loadOwned(parentId);
		taskService.requireCanAddSubtasks(parent);
		int position = taskRepository.maxChildPosition(parentId);
		List<Task> subtasks = new ArrayList<>(requests.size());
		for (CreateSubtaskRequest request : requests) {
			Task subtask = taskService.newTask(parent.getUser(), parent, request.toCreateTaskRequest());
			subtask.setPosition(++position);
			subtasks.add(subtask);
		}
		return taskRepository.saveAll(subtasks).stream().map(TaskResponse::from).toList();
	}
}
