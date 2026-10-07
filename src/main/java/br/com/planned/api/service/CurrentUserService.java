package br.com.planned.api.service;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;

/** The authenticated user of the current request. Scope every task query with {@link #currentUserId()}. */
@Service
public class CurrentUserService {

	private static final String UNAUTHORIZED_DETAIL = "Authentication is required";

	/**
	 * @return the user id from the token's {@code sub}
	 * @throws ApiException {@code UNAUTHORIZED} when the request isn't authenticated with a JWT
	 *         whose {@code sub} is a UUID
	 */
	public UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token && token.getToken().getSubject() != null) {
			try {
				return UUID.fromString(token.getToken().getSubject());
			} catch (IllegalArgumentException ex) {
				// Fall through: a token we didn't issue.
			}
		}
		throw new ApiException(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_DETAIL);
	}
}
