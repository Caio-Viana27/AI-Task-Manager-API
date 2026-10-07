package br.com.planned.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/** The vendor-neutral AI env vars map onto the Spring AI properties (wave 3 plan, D10). */
class AiEnvironmentPropertiesTest {

	@Test
	void aiEnvVarsMapOntoGeminiProperties() throws IOException {
		Properties properties = applicationProperties();
		StandardEnvironment environment = environmentWith(Map.of("AI_MODEL", "gemini-test", "AI_API_KEY", "key-123"));

		assertThat(resolve(environment, properties, "spring.ai.google.genai.chat.model")).isEqualTo("gemini-test");
		assertThat(resolve(environment, properties, "spring.ai.google.genai.api-key")).isEqualTo("key-123");
	}

	@Test
	void providerDefaultsToGoogleGenAi() throws IOException {
		Properties properties = applicationProperties();

		assertThat(resolve(environmentWith(Map.of()), properties, "spring.ai.model.chat")).isEqualTo("google-genai");
		assertThat(resolve(environmentWith(Map.of("AI_PROVIDER", "ollama")), properties, "spring.ai.model.chat"))
				.isEqualTo("ollama");
	}

	private static Properties applicationProperties() throws IOException {
		return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
	}

	private static StandardEnvironment environmentWith(Map<String, Object> variables) {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("env", variables));
		return environment;
	}

	private static String resolve(StandardEnvironment environment, Properties properties, String key) {
		return environment.resolveRequiredPlaceholders(properties.getProperty(key));
	}
}
