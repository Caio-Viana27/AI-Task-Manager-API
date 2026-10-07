package br.com.planned.api.service.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.planned.api.dto.BreakdownResult;
import br.com.planned.api.dto.LookupsResponse;
import br.com.planned.api.dto.SubtaskDraft;
import br.com.planned.api.entity.Task;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.repository.TaskAncestor;
import br.com.planned.api.repository.TaskRepository;
import br.com.planned.api.service.LookupService;
import br.com.planned.api.service.TaskService;

/**
 * Drafts 2–8 subtasks for a saved task (PLAN §5, Breakdown). Never writes to the database.
 *
 * <p>Not transactional on purpose: the reads are done before the AI call, so no database
 * connection is held while the model works.
 */
@Service
public class TaskBreakdownService {

	private static final Logger log = LoggerFactory.getLogger(TaskBreakdownService.class);

	private final AiClient aiClient;
	private final AiQuotaService quotaService;
	private final TaskService taskService;
	private final TaskRepository taskRepository;
	private final LookupService lookupService;

	public TaskBreakdownService(AiClient aiClient, AiQuotaService quotaService, TaskService taskService,
			TaskRepository taskRepository, LookupService lookupService) {
		this.aiClient = aiClient;
		this.quotaService = quotaService;
		this.taskService = taskService;
		this.taskRepository = taskRepository;
		this.lookupService = lookupService;
	}

	/**
	 * @return the drafts in the model's order
	 * @throws ApiException {@code TASK_NOT_FOUND}, {@code SUBTASK_DEPTH_EXCEEDED}, {@code AI_RATE_LIMITED},
	 *         {@code AI_INVALID_RESPONSE} (also for an unknown priority or complexity name), or
	 *         {@code AI_UNAVAILABLE}
	 */
	public List<SubtaskDraft> breakdown(UUID taskId, Locale locale) {
		// Checks first, so a request that can't succeed never uses quota (wave 3, D3).
		Task task = taskService.loadOwned(taskId);
		taskService.requireCanAddSubtasks(task);
		UUID userId = task.getUser().getId();
		quotaService.consume(userId);

		Map<String, Object> taskData = new LinkedHashMap<>();
		taskData.put("title", task.getTitle());
		taskData.put("description", task.getDescription());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("task", taskData);
		data.put("ancestors", taskRepository.findAncestors(taskId, userId).stream().map(TaskAncestor::getTitle).toList());
		data.put("existingSubtasks", taskRepository.findChildren(taskId, userId).stream().map(Task::getTitle).toList());

		List<SubtaskDraft> drafts = aiClient.call("breakdown", data, BreakdownResult.class, locale).subtasks();
		LookupsResponse lookups = lookupService.lookups();
		boolean namesValid = drafts.stream().allMatch(draft -> lookups.priorities().contains(draft.priority())
				&& lookups.complexities().contains(draft.complexity()));
		if (!namesValid) {
			log.warn("AI breakdown has an unknown priority or complexity name");
			throw new ApiException(ErrorCode.AI_INVALID_RESPONSE, "The AI returned an invalid response. Try again.");
		}
		return drafts;
	}
}
