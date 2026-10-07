package br.com.planned.api.service.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.planned.api.config.AppProperties;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.support.MutableClock;

/** The sliding-hour AI quota (wave 3, D3). */
class AiQuotaServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

	private MutableClock clock;
	private AiQuotaService quota;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(NOW, ZoneId.of("America/Sao_Paulo"));
		AppProperties properties = new AppProperties(ZoneId.of("America/Sao_Paulo"),
				new AppProperties.Jwt("secret", Duration.ofMinutes(60)), new AppProperties.Ai(Duration.ofSeconds(20), 30),
				new AppProperties.Cors(List.of("http://localhost")), new AppProperties.Tasks(5));
		quota = new AiQuotaService(properties, clock);
	}

	private void consumeTimes(UUID user, int times) {
		for (int i = 0; i < times; i++) {
			quota.consume(user);
		}
	}

	@Test
	void the31stCallWithinAnHour_isRateLimited() {
		UUID user = UUID.randomUUID();
		consumeTimes(user, 30);

		assertThatThrownBy(() -> quota.consume(user)).isInstanceOfSatisfying(ApiException.class,
				ex -> org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_RATE_LIMITED));
	}

	@Test
	void afterTheOldestCallLeavesTheWindow_consumeSucceedsAgain() {
		UUID user = UUID.randomUUID();
		quota.consume(user);
		clock.advance(Duration.ofMinutes(30));
		consumeTimes(user, 29);
		assertThatThrownBy(() -> quota.consume(user)).isInstanceOf(ApiException.class);

		// The first call is now exactly an hour old and leaves the window; the other 29 stay.
		clock.advance(Duration.ofMinutes(30));
		assertThatCode(() -> quota.consume(user)).doesNotThrowAnyException();
		assertThatThrownBy(() -> quota.consume(user)).isInstanceOf(ApiException.class);
	}

	@Test
	void rejectedCalls_dontUseQuota() {
		UUID user = UUID.randomUUID();
		consumeTimes(user, 30);
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> quota.consume(user)).isInstanceOf(ApiException.class);
		}

		clock.advance(Duration.ofHours(1));
		consumeTimes(user, 30);
	}

	@Test
	void usersHaveSeparateQuotas() {
		UUID ana = UUID.randomUUID();
		consumeTimes(ana, 30);

		assertThatCode(() -> quota.consume(UUID.randomUUID())).doesNotThrowAnyException();
	}
}
