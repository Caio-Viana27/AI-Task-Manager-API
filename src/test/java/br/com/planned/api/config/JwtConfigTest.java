package br.com.planned.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(AppConfig.class, JwtConfig.class)
			.withPropertyValues(
					"app.timezone=America/Sao_Paulo",
					"app.jwt.ttl=60m",
					"app.ai.timeout=20s",
					"app.ai.quota-per-hour=30",
					"app.cors.allowed-origins=http://localhost:5173");

	@Test
	void startupFailsWithA31ByteSecret() {
		contextRunner.withPropertyValues("app.jwt.secret=" + "a".repeat(31))
				.run(context -> assertThat(context).getFailure()
						.rootCause()
						.isInstanceOf(IllegalStateException.class)
						.hasMessage("app.jwt.secret (JWT_SECRET) must be at least 32 bytes long, but it is 31 bytes"));
	}

	@Test
	void secretLengthIsCountedInUtf8Bytes() {
		// 16 characters, but 32 bytes.
		contextRunner.withPropertyValues("app.jwt.secret=" + "é".repeat(16))
				.run(context -> assertThat(context).hasNotFailed()
						.getBean(SecretKey.class)
						.extracting(key -> key.getEncoded().length)
						.isEqualTo(32));
	}

	@Test
	void startsWithA32ByteSecret() {
		contextRunner.withPropertyValues("app.jwt.secret=" + "a".repeat(32))
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(JwtEncoder.class);
					assertThat(context).hasSingleBean(JwtDecoder.class);
					assertThat(context.getBean(SecretKey.class).getAlgorithm()).isEqualTo("HmacSHA256");
					assertThat(context.getBean(PasswordEncoder.class).encode("password")).startsWith("$2");
				});
	}
}
