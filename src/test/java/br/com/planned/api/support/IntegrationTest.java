package br.com.planned.api.support;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need the full application context and a real database.
 *
 * <p>The PostgreSQL container is started once and shared by every subclass, so the Spring
 * context cache can reuse the same context across test classes. Testcontainers removes it
 * when the JVM exits. The chat model is mocked so no test ever calls Gemini.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-bookworm");

	static {
		POSTGRES.start();
	}

	@MockitoBean
	protected ChatModel chatModel;
}
