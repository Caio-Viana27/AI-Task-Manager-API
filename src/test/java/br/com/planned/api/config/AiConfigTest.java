package br.com.planned.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.planned.api.support.IntegrationTest;

/** The AI wiring (wave 3, T3.1): a {@link ChatClient} bean, and Spring AI's own retry turned off (D2). */
class AiConfigTest extends IntegrationTest {

	@Autowired
	private ChatClient chatClient;

	@Autowired
	private SpringAiRetryProperties retryProperties;

	@Test
	void chatClientExists() {
		assertThat(chatClient).isNotNull();
	}

	@Test
	void springAiRetry_makesASingleAttempt() {
		// max-attempts is passed to RetryPolicy.maxRetries: 0 retries means one attempt.
		assertThat(retryProperties.getMaxAttempts()).isZero();
	}
}
