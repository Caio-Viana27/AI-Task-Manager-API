package br.com.planned.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import br.com.planned.api.config.AppProperties;
import br.com.planned.api.config.JwtTestSupport;
import br.com.planned.api.entity.Role;
import br.com.planned.api.entity.User;

class JwtServiceTest {

	private static final String SECRET = "test-jwt-secret-not-for-production-0123456789abcdef";
	private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
	private static final Duration TTL = Duration.ofMinutes(60);

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final SecretKey key = key(SECRET);
	private final JwtService jwtService = new JwtService(JwtTestSupport.encoder(key), clock, properties());
	private final User user = user();

	@Test
	void tokenRoundTripsWithTheExpectedClaims() {
		JwtService.IssuedToken issued = jwtService.issue(user);

		Jwt jwt = JwtTestSupport.decoder(key, clock).decode(issued.token());

		assertThat(issued.expiresAt()).isEqualTo(NOW.plus(TTL));
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
		assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
		assertThat(jwt.getClaimAsString("email")).isEqualTo("ana@example.com");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
		assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
		assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(TTL));
	}

	@Test
	void tokenIsValidUntilItsExpiry() {
		String token = jwtService.issue(user).token();
		Clock atExpiry = Clock.fixed(NOW.plus(TTL), ZoneOffset.UTC);

		assertThat(JwtTestSupport.decoder(key, atExpiry).decode(token).getSubject())
				.isEqualTo(user.getId().toString());
	}

	@Test
	void decoderRejectsATokenPastItsExpiry() {
		String token = jwtService.issue(user).token();
		Clock afterExpiry = Clock.fixed(NOW.plus(TTL).plusSeconds(1), ZoneOffset.UTC);
		JwtDecoder decoder = JwtTestSupport.decoder(key, afterExpiry);

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
	}

	@Test
	void decoderRejectsATokenWithAChangedPayload() {
		String token = jwtService.issue(user).token();
		String tampered = JwtTestSupport.replacePayload(token, payload -> payload.replace("ana@example.com", "eve@example.com"));
		JwtDecoder decoder = JwtTestSupport.decoder(key, clock);

		assertThat(tampered).isNotEqualTo(token);
		assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(BadJwtException.class);
	}

	@Test
	void decoderRejectsATokenSignedWithAnotherSecret() {
		SecretKey otherKey = key("another-secret-that-is-also-long-enough-0123456789");
		String token = new JwtService(JwtTestSupport.encoder(otherKey), clock, properties()).issue(user).token();
		JwtDecoder decoder = JwtTestSupport.decoder(key, clock);

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
	}

	private static SecretKey key(String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	private static AppProperties properties() {
		return new AppProperties(
				ZoneId.of("America/Sao_Paulo"),
				new AppProperties.Jwt(SECRET, TTL),
				new AppProperties.Ai(Duration.ofSeconds(20), 30),
				new AppProperties.Cors(List.of("http://localhost:5173")));
	}

	private static User user() {
		Role role = mock(Role.class);
		when(role.getName()).thenReturn("USER");
		User user = new User("Ana", "ana@example.com", "hash", role, NOW);
		user.setId(UUID.fromString("2f1f7a8e-3c1b-4b8e-9a7c-5d2e6f1a0b3c"));
		return user;
	}

}
