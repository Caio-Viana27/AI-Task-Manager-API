package br.com.planned.api.service.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.planned.api.dto.LookupsResponse;
import br.com.planned.api.dto.TaskAnalysisResponse;
import br.com.planned.api.entity.Task;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.repository.TaskAncestor;
import br.com.planned.api.repository.TaskRepository;
import br.com.planned.api.service.LookupService;
import br.com.planned.api.service.TaskService;

/**
 * Suggests a priority, complexity, and estimated hours for a saved task, with a reason (wave 4,
 * D11–D13). Never writes to the database.
 *
 * <p>Not transactional on purpose: the reads are done before the AI call, so no database
 * connection is held while the model works. The task comes with its lookups loaded, and
 * {@code findChildren} fetches each child's status.
 */
@Service
public class TaskAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(TaskAnalysisService.class);

	private final AiClient aiClient;
	private final AiQuotaService quotaService;
	private final TaskService taskService;
	private final TaskRepository taskRepository;
	private final LookupService lookupService;

	public TaskAnalysisService(AiClient aiClient, AiQuotaService quotaService, TaskService taskService,
			TaskRepository taskRepository, LookupService lookupService) {
		this.aiClient = aiClient;
		this.quotaService = quotaService;
		this.taskService = taskService;
		this.taskRepository = taskRepository;
		this.lookupService = lookupService;
	}

	/**
	 * @throws ApiException {@code TASK_NOT_FOUND}, {@code AI_RATE_LIMITED}, {@code AI_INVALID_RESPONSE}
	 *         (also for an unknown priority or complexity name), or {@code AI_UNAVAILABLE}
	 */
	public TaskAnalysisResponse analyze(UUID taskId, Locale locale) {
		// Ownership first, so a request that can't succeed never uses quota (wave 3, D3).
		Task task = taskService.loadOwned(taskId);
		UUID userId = task.getUser().getId();
		quotaService.consume(userId);

		Map<String, Object> taskData = new LinkedHashMap<>();
		taskData.put("title", task.getTitle());
		taskData.put("description", task.getDescription());
		taskData.put("status", task.getStatus().getName());
		taskData.put("dueDate", task.getDueDate() == null ? null : task.getDueDate().toString());
		taskData.put("priority", task.getPriority().getName());
		taskData.put("complexity", task.getComplexity() == null ? null : task.getComplexity().getName());
		taskData.put("estimatedHours", task.getEstimatedHours());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("task", taskData);
		data.put("ancestors", taskRepository.findAncestors(taskId, userId).stream().map(TaskAncestor::getTitle).toList());
		data.put("subtasks", taskRepository.findChildren(taskId, userId).stream()
				.map(child -> {
					Map<String, Object> subtask = new LinkedHashMap<>();
					subtask.put("title", child.getTitle());
					subtask.put("status", child.getStatus().getName());
					return subtask;
				})
				.toList());

		TaskAnalysisResponse analysis = aiClient.call("analysis", data, TaskAnalysisResponse.class, locale);
		LookupsResponse lookups = lookupService.lookups();
		if (!lookups.priorities().contains(analysis.priority()) || !lookups.complexities().contains(analysis.complexity())) {
			log.warn("AI analysis has an unknown priority or complexity name");
			throw new ApiException(ErrorCode.AI_INVALID_RESPONSE, "The AI returned an invalid response. Try again.");
		}
		return analysis;
	}
}
