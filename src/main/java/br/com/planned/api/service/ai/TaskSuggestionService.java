package br.com.planned.api.service.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.planned.api.dto.LookupsResponse;
import br.com.planned.api.dto.SuggestRequest;
import br.com.planned.api.dto.SuggestResponse;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.service.CurrentUserService;
import br.com.planned.api.service.LookupService;

/**
 * Suggests a clearer title and description, a priority, and a complexity for a task draft
 * (PLAN §5, Suggest). Never writes to the database: the user saves what they accept (wave 3, D7).
 */
@Service
public class TaskSuggestionService {

	private static final Logger log = LoggerFactory.getLogger(TaskSuggestionService.class);

	private final AiClient aiClient;
	private final AiQuotaService quotaService;
	private final CurrentUserService currentUserService;
	private final LookupService lookupService;

	public TaskSuggestionService(AiClient aiClient, AiQuotaService quotaService, CurrentUserService currentUserService,
			LookupService lookupService) {
		this.aiClient = aiClient;
		this.quotaService = quotaService;
		this.currentUserService = currentUserService;
		this.lookupService = lookupService;
	}

	/**
	 * @param request already validated
	 * @throws ApiException {@code AI_RATE_LIMITED}, {@code AI_INVALID_RESPONSE} (also for an unknown
	 *         priority or complexity name), or {@code AI_UNAVAILABLE}
	 */
	public SuggestResponse suggest(SuggestRequest request, Locale locale) {
		quotaService.consume(currentUserService.currentUserId());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("title", request.title().strip());
		data.put("description", request.description().strip());
		SuggestResponse suggestion = aiClient.call("suggest", data, SuggestResponse.class, locale);
		LookupsResponse lookups = lookupService.lookups();
		if (!lookups.priorities().contains(suggestion.suggestedPriority())
				|| !lookups.complexities().contains(suggestion.suggestedComplexity())) {
			log.warn("AI suggestion has an unknown priority or complexity name");
			throw new ApiException(ErrorCode.AI_INVALID_RESPONSE, "The AI returned an invalid response. Try again.");
		}
		return suggestion;
	}
}
