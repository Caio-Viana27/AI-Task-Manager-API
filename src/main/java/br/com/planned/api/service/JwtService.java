package br.com.planned.api.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.planned.api.config.AppProperties;
import br.com.planned.api.entity.User;

/** Issues the access tokens (PLAN §0, JWT). {@code JwtConfig}'s decoder verifies them. */
@Service
public class JwtService {

	public static final String EMAIL_CLAIM = "email";
	public static final String ROLES_CLAIM = "roles";

	private final JwtEncoder encoder;
	private final Clock clock;
	private final AppProperties properties;

	public JwtService(JwtEncoder encoder, Clock clock, AppProperties properties) {
		this.encoder = encoder;
		this.clock = clock;
		this.properties = properties;
	}

	/**
	 * Signs a token for the user. Must run where the user's role can be loaded (it's lazy).
	 *
	 * @return the token and its expiry, which matches the {@code exp} claim (whole seconds)
	 */
	public IssuedToken issue(User user) {
		Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = issuedAt.plus(properties.jwt().ttl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(user.getId().toString())
				.claim(EMAIL_CLAIM, user.getEmail())
				// An array even though a user has one role (wave 1, D2).
				.claim(ROLES_CLAIM, List.of(user.getRole().getName()))
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	public record IssuedToken(String token, Instant expiresAt) {
	}
}
