package br.com.planned.api.service.ai;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import br.com.planned.api.config.AppProperties;
import br.com.planned.api.dto.LookupsResponse;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.service.LookupService;

import tools.jackson.databind.json.JsonMapper;

/**
 * The only class that talks to the chat model (PLAN §5, wave 3 D1). Feature services pass a
 * prompt template name, the data to work on, and the record to get back; they never build prompt
 * strings or see provider types.
 *
 * <ul>
 * <li><b>Prompt (D4):</b> the system message is {@code prompts/<template>.st}, filled with today's
 * date in {@code app.timezone}, the zone id, the output language, and the allowed lookup names,
 * followed by the JSON format instructions for the record. The user message is only the data, as
 * JSON inside {@code <data>…</data>}, with {@code <} and {@code >} escaped so the data can't close
 * the section.</li>
 * <li><b>Deadline and retry (D2):</b> one {@code app.ai.timeout} budget covers every attempt. A
 * transient error is retried once if time is left; a timeout or any other error is not. Spring AI's
 * own retry is off ({@code spring.ai.retry.max-attempts=0}).</li>
 * <li><b>Output (D6):</b> the reply is converted with Spring AI's {@link BeanOutputConverter} and
 * checked with Bean Validation. Lookup names are the feature service's job.</li>
 * </ul>
 *
 * Prompts, replies, and task text are never logged.
 */
@Service
public class AiClient {

	private static final Logger log = LoggerFactory.getLogger(AiClient.class);

	static final int MAX_ATTEMPTS = 2;
	private static final Pattern TEMPLATE_NAME = Pattern.compile("[a-z][a-z0-9-]*");

	private final ChatClient chatClient;
	private final LookupService lookupService;
	private final Validator validator;
	private final JsonMapper jsonMapper;
	private final ResourceLoader resourceLoader;
	private final AppProperties properties;
	private final Clock clock;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public AiClient(ChatClient chatClient, LookupService lookupService, Validator validator, JsonMapper jsonMapper,
			ResourceLoader resourceLoader, AppProperties properties, Clock clock) {
		this.chatClient = chatClient;
		this.lookupService = lookupService;
		this.validator = validator;
		this.jsonMapper = jsonMapper;
		this.resourceLoader = resourceLoader;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Asks the model and returns its validated answer.
	 *
	 * @param template a file name in {@code prompts/}, without {@code .st}
	 * @param userData the data to work on, serialized as JSON; use a {@code LinkedHashMap} to keep key order
	 * @param type     the record to convert the reply to
	 * @param locale   the output language, from {@link AiLocales#fromAcceptLanguage}
	 * @throws ApiException {@code AI_INVALID_RESPONSE} if the reply isn't valid JSON for {@code type} or
	 *         breaks its constraints; {@code AI_UNAVAILABLE} if the model fails or the deadline passes
	 */
	public <T> T call(String template, Map<String, Object> userData, Class<T> type, Locale locale) {
		BeanOutputConverter<T> converter = new BeanOutputConverter<>(type, jsonMapper);
		String system = systemPrompt(template, locale) + "\n\n" + converter.getFormat();
		String user = dataSection(userData);
		long start = System.nanoTime();
		int[] attempts = { 0 };
		String outcome = "OK";
		try {
			String reply = callModel(system, user, start + properties.ai().timeout().toNanos(), attempts);
			return parse(reply, converter, type);
		} catch (ApiException ex) {
			outcome = ex.getCode().name();
			throw ex;
		} finally {
			log.info("AI call template={} attempts={} latencyMs={} outcome={}", template, attempts[0],
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), outcome);
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	/** Calls the model, retrying once on a transient error, all within {@code deadline} ({@code nanoTime}). */
	private String callModel(String system, String user, long deadline, int[] attempts) {
		while (true) {
			attempts[0]++;
			Future<String> future = executor.submit(() -> chatClient.prompt()
					.messages(new SystemMessage(system), new UserMessage(user))
					.call()
					.content());
			try {
				return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
			} catch (TimeoutException ex) {
				future.cancel(true);
				log.warn("AI call timed out after {}", properties.ai().timeout());
				throw unavailable();
			} catch (InterruptedException ex) {
				future.cancel(true);
				Thread.currentThread().interrupt();
				throw unavailable();
			} catch (ExecutionException ex) {
				Throwable error = ex.getCause();
				boolean isTransient = isTransient(error);
				if (isTransient && attempts[0] < MAX_ATTEMPTS && deadline - System.nanoTime() > 0) {
					log.warn("AI call failed with a transient error, retrying: {}", describe(error));
					continue;
				}
				if (isTransient) {
					log.warn("AI call failed with a transient error: {}", describe(error));
				} else {
					log.error("AI call failed: {}", describe(error));
				}
				throw unavailable();
			}
		}
	}

	private <T> T parse(String reply, BeanOutputConverter<T> converter, Class<T> type) {
		if (reply == null || reply.isBlank()) {
			log.warn("AI reply for {} is empty", type.getSimpleName());
			throw invalidResponse();
		}
		T value;
		try {
			value = converter.convert(reply);
		} catch (RuntimeException ex) {
			// The exception message may quote the reply, so log only its type.
			log.warn("AI reply isn't valid JSON for {}: {}", type.getSimpleName(), ex.getClass().getName());
			throw invalidResponse();
		}
		if (value == null) {
			log.warn("AI reply for {} is null", type.getSimpleName());
			throw invalidResponse();
		}
		Set<ConstraintViolation<T>> violations = validator.validate(value);
		if (!violations.isEmpty()) {
			log.warn("AI reply for {} breaks constraints on: {}", type.getSimpleName(), violations.stream()
					.map(violation -> violation.getPropertyPath().toString())
					.sorted()
					.collect(Collectors.joining(", ")));
			throw invalidResponse();
		}
		return value;
	}

	private String systemPrompt(String template, Locale locale) {
		if (!TEMPLATE_NAME.matcher(template).matches()) {
			throw new IllegalArgumentException("Invalid prompt template name: " + template);
		}
		Resource resource = resourceLoader.getResource("classpath:prompts/" + template + ".st");
		if (!resource.exists()) {
			throw new IllegalArgumentException("Prompt template not found: " + template);
		}
		LookupsResponse lookups = lookupService.lookups();
		return new PromptTemplate(resource).render(Map.of(
				"today", LocalDate.now(clock.withZone(properties.timezone())).toString(),
				"zone", properties.timezone().getId(),
				"language", AiLocales.languageName(locale),
				"priorities", String.join(", ", lookups.priorities()),
				"complexities", String.join(", ", lookups.complexities())));
	}

	/**
	 * The data as JSON inside {@code <data>} tags. {@code <} and {@code >} become JSON unicode
	 * escapes, so the JSON means the same but can't contain {@code </data>} (D4).
	 */
	private String dataSection(Map<String, Object> userData) {
		String json = jsonMapper.writeValueAsString(userData)
				.replace("<", "\\u003c")
				.replace(">", "\\u003e");
		return "<data>\n" + json + "\n</data>";
	}

	/**
	 * Whether the model error is worth one retry: Spring AI's {@link TransientAiException}, an I/O
	 * error, or an HTTP 429 or 5xx anywhere in the cause chain. This is the only code that knows
	 * provider exception types (D1); add a new provider's errors here.
	 */
	static boolean isTransient(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
			if (cause instanceof NonTransientAiException) {
				return false;
			}
			if (cause instanceof TransientAiException || cause instanceof IOException
					|| cause instanceof com.google.genai.errors.GenAiIOException) {
				return true;
			}
			Integer status = httpStatus(cause);
			if (status != null) {
				return status == 429 || status >= 500;
			}
		}
		return false;
	}

	private static Integer httpStatus(Throwable error) {
		if (error instanceof com.google.genai.errors.ApiException api) {
			return api.code();
		}
		if (error instanceof RestClientResponseException rest) {
			return rest.getStatusCode().value();
		}
		return null;
	}

	/** The error's class and HTTP status, if any: never its message, which may hold request data. */
	private static String describe(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
			Integer status = httpStatus(cause);
			if (status != null) {
				return cause.getClass().getName() + " (HTTP " + status + ")";
			}
		}
		Throwable root = error;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		return root == error ? error.getClass().getName() : error.getClass().getName() + " caused by " + root.getClass().getName();
	}

	private static ApiException unavailable() {
		return new ApiException(ErrorCode.AI_UNAVAILABLE, "The AI is temporarily unavailable. Try again later.");
	}

	private static ApiException invalidResponse() {
		return new ApiException(ErrorCode.AI_INVALID_RESPONSE, "The AI returned an invalid response. Try again.");
	}
}
