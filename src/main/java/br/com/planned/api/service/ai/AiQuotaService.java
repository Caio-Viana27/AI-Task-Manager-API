package br.com.planned.api.service.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import br.com.planned.api.config.AppProperties;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;

/**
 * The per-user AI quota (PLAN §5, wave 3 D3): at most {@code app.ai.quota-per-hour} calls in any
 * sliding hour. In memory, for the single API instance. Each user's window is pruned whenever that
 * user calls, so it never holds more than the quota.
 */
@Service
public class AiQuotaService {

	static final Duration WINDOW = Duration.ofHours(1);

	private final Map<UUID, Deque<Instant>> calls = new ConcurrentHashMap<>();
	private final AppProperties properties;
	private final Clock clock;

	public AiQuotaService(AppProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Uses one unit of the user's quota. Call it after the request is validated and authorized,
	 * right before the model is called (D3).
	 *
	 * @throws ApiException {@code AI_RATE_LIMITED} if the user already used the whole quota in the last hour
	 */
	public void consume(UUID userId) {
		Instant now = clock.instant();
		Instant windowStart = now.minus(WINDOW);
		int limit = properties.ai().quotaPerHour();
		boolean[] allowed = { false };
		// compute() runs atomically per key, so concurrent calls of one user can't exceed the limit.
		calls.compute(userId, (id, window) -> {
			Deque<Instant> times = window == null ? new ArrayDeque<>() : window;
			while (!times.isEmpty() && !times.peekFirst().isAfter(windowStart)) {
				times.pollFirst();
			}
			if (times.size() < limit) {
				times.addLast(now);
				allowed[0] = true;
			}
			return times.isEmpty() ? null : times;
		});
		if (!allowed[0]) {
			throw new ApiException(ErrorCode.AI_RATE_LIMITED,
					"AI quota of " + limit + " requests per hour reached. Try again later.");
		}
	}
}
