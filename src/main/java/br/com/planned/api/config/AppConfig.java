package br.com.planned.api.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

	/** Inject this instead of calling {@code now()} directly, so tests can use a fixed clock. */
	@Bean
	Clock clock(AppProperties properties) {
		return Clock.system(properties.timezone());
	}
}
