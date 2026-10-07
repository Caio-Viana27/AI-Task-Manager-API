package br.com.planned.api.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@link ChatClient} for the auto-configured chat model, whichever provider
 * {@code spring.ai.model.chat} selects (wave 3, D10). Only {@code service.ai.AiClient} uses it (D1).
 */
@Configuration
public class AiConfig {

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}
}
