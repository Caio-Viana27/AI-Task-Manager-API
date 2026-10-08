package br.com.planned.api.service.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import br.com.planned.api.dto.AiChatRequest;
import br.com.planned.api.dto.AiChatResponse;
import br.com.planned.api.entity.Task;
import br.com.planned.api.entity.TaskStatus;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.repository.TaskRepository;
import br.com.planned.api.service.CurrentUserService;
import br.com.planned.api.service.LookupService;

/**
 * Answers questions about the caller's tasks (PLAN §5, Chat). Read-only and stateless: the
 * client sends the earlier turns, and nothing is stored (PLAN §0).
 *
 * <p>Not transactional on purpose: the reads are done before the AI call, so no database
 * connection is held while the model works.
 */
@Service
public class ChatAssistantService {

	/** How many open tasks go in the prompt. Fixed by PLAN §5, so not a property (wave 4, D4). */
	static final int CONTEXT_TASKS = 20;

	private final AiClient aiClient;
	private final AiQuotaService quotaService;
	private final CurrentUserService currentUserService;
	private final TaskRepository taskRepository;
	private final LookupService lookupService;

	public ChatAssistantService(AiClient aiClient, AiQuotaService quotaService, CurrentUserService currentUserService,
			TaskRepository taskRepository, LookupService lookupService) {
		this.aiClient = aiClient;
		this.quotaService = quotaService;
		this.currentUserService = currentUserService;
		this.taskRepository = taskRepository;
		this.lookupService = lookupService;
	}

	/**
	 * @param request already validated
	 * @throws ApiException {@code AI_RATE_LIMITED}, {@code AI_INVALID_RESPONSE}, or {@code AI_UNAVAILABLE}
	 */
	public AiChatResponse chat(AiChatRequest request, Locale locale) {
		UUID userId = currentUserService.currentUserId();
		quotaService.consume(userId);

		TaskStatus done = lookupService.status("DONE");
		TaskStatus overdue = lookupService.status("OVERDUE");
		List<Map<String, Object>> tasks = taskRepository
				.findChatContext(userId, done, overdue, Pageable.ofSize(CONTEXT_TASKS))
				.stream().map(ChatAssistantService::taskData).toList();

		// Everything is data, including past assistant replies, so a forged turn can't give instructions (D5).
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("message", request.message().strip());
		data.put("history", request.history().stream().map(item -> {
			Map<String, Object> turn = new LinkedHashMap<>();
			turn.put("role", item.role());
			turn.put("content", item.content().strip());
			return turn;
		}).toList());
		data.put("openTaskCount", taskRepository.countOpen(userId, done));
		data.put("tasks", tasks);
		return aiClient.call("chat", data, AiChatResponse.class, locale);
	}

	/** A task as the prompt shows it: no ids, the reply can't link to tasks (D4). */
	private static Map<String, Object> taskData(Task task) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("title", task.getTitle());
		data.put("description", task.getDescription());
		data.put("status", task.getStatus().getName());
		data.put("priority", task.getPriority().getName());
		data.put("complexity", task.getComplexity() == null ? null : task.getComplexity().getName());
		data.put("dueDate", task.getDueDate() == null ? null : task.getDueDate().toString());
		data.put("parentTitle", task.getParent() == null ? null : task.getParent().getTitle());
		return data;
	}
}
