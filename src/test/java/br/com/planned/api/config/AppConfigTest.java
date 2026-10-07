package br.com.planned.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AppConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(AppConfig.class)
			.withPropertyValues(
					"app.timezone=Asia/Tokyo",
					"app.jwt.secret=test-secret",
					"app.jwt.ttl=60m",
					"app.ai.timeout=20s",
					"app.ai.quota-per-hour=30",
					"app.cors.allowed-origins=http://localhost:5173,https://planned.example",
					"app.tasks.max-depth=5");

	@Test
	void appPropertiesBindEveryValue() {
		contextRunner.run(context -> {
			AppProperties properties = context.getBean(AppProperties.class);

			assertThat(properties.timezone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
			assertThat(properties.jwt().secret()).isEqualTo("test-secret");
			assertThat(properties.jwt().ttl()).isEqualTo(Duration.ofMinutes(60));
			assertThat(properties.ai().timeout()).isEqualTo(Duration.ofSeconds(20));
			assertThat(properties.ai().quotaPerHour()).isEqualTo(30);
			assertThat(properties.cors().allowedOrigins())
					.containsExactly("http://localhost:5173", "https://planned.example");
			assertThat(properties.tasks().maxDepth()).isEqualTo(5);
		});
	}

	@Test
	void clockUsesTheConfiguredTimezone() {
		contextRunner.run(context -> assertThat(context.getBean(Clock.class).getZone())
				.isEqualTo(ZoneId.of("Asia/Tokyo")));
	}

	@Test
	void startupFailsWithoutJwtSecret() {
		contextRunner.withPropertyValues("app.jwt.secret=")
				.run(context -> assertThat(context).getFailure()
						.rootCause()
						.isInstanceOf(BindValidationException.class)
						.hasMessageContaining("jwt.secret"));
	}

	@Test
	void startupFailsWithInvalidTimezone() {
		contextRunner.withPropertyValues("app.timezone=Not/AZone")
				.run(context -> assertThat(context).getFailure()
						.rootCause()
						.isInstanceOf(ZoneRulesException.class));
	}

	@Test
	void startupFailsWithoutAllowedOrigins() {
		contextRunner.withPropertyValues("app.cors.allowed-origins=")
				.run(context -> assertThat(context).getFailure()
						.rootCause()
						.isInstanceOf(BindValidationException.class)
						.hasMessageContaining("cors.allowedOrigins"));
	}

	@Test
	void startupFailsWithMaxDepthBelowOne() {
		contextRunner.withPropertyValues("app.tasks.max-depth=0")
				.run(context -> assertThat(context).getFailure()
						.rootCause()
						.isInstanceOf(BindValidationException.class)
						.hasMessageContaining("tasks.maxDepth"));
	}
}
