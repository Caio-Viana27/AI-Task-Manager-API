package br.com.planned.api.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 JWT signing and verification (PLAN §0, JWT) and the password hasher.
 *
 * <p>{@code app.jwt.secret} is used as raw UTF-8 bytes, not base64-decoded (wave 1, D6).
 */
@Configuration
public class JwtConfig {

	/** HS256 needs a key at least as long as its 256-bit hash. */
	static final int MIN_SECRET_BYTES = 32;

	static final String HMAC_SHA256 = "HmacSHA256";

	@Bean
	SecretKey jwtSecretKey(AppProperties properties) {
		byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < MIN_SECRET_BYTES) {
			// Never include the secret itself in the message.
			throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES
					+ " bytes long, but it is " + secret.length + " bytes");
		}
		return new SecretKeySpec(secret, HMAC_SHA256);
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
	}

	/**
	 * Accepts only HS256 tokens signed with our key. Expiry is checked against the {@code Clock}
	 * bean, so tests can expire a token. There's no clock skew allowance: this server both issues
	 * and verifies the tokens.
	 */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
		timestampValidator.setClock(clock);
		timestampValidator.setAllowEmptyExpiryClaim(false);
		decoder.setJwtValidator(timestampValidator);
		return decoder;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
