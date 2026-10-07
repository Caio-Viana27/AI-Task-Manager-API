package br.com.planned.api.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.function.UnaryOperator;

import javax.crypto.SecretKey;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** Builds the production encoder and decoder outside a Spring context, and forges bad tokens. */
public final class JwtTestSupport {

	private static final JwtConfig CONFIG = new JwtConfig();

	private JwtTestSupport() {
	}

	public static JwtEncoder encoder(SecretKey key) {
		return CONFIG.jwtEncoder(key);
	}

	public static JwtDecoder decoder(SecretKey key, Clock clock) {
		return CONFIG.jwtDecoder(key, clock);
	}

	/** Rewrites the payload of a signed token and keeps the original signature. */
	public static String replacePayload(String token, UnaryOperator<String> change) {
		String[] parts = token.split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
		return parts[0] + "." + base64Url(change.apply(payload)) + "." + parts[2];
	}

	/** An unsigned token ({@code alg: none}) with the given JSON payload. */
	public static String unsignedToken(String payloadJson) {
		return base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + base64Url(payloadJson) + ".";
	}

	public static String base64Url(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}
}
