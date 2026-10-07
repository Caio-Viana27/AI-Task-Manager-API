package br.com.planned.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;

class CurrentUserServiceTest {

	private final CurrentUserService service = new CurrentUserService();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void returnsTheSubjectOfTheJwt() {
		UUID id = UUID.randomUUID();
		authenticate(id.toString());

		assertThat(service.currentUserId()).isEqualTo(id);
	}

	@Test
	void throwsUnauthorizedWithoutAuthentication() {
		assertUnauthorized();
	}

	@Test
	void throwsUnauthorizedForANonJwtAuthentication() {
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("ana", "pw"));

		assertUnauthorized();
	}

	@Test
	void throwsUnauthorizedWhenTheSubjectIsNotAUuid() {
		authenticate("not-a-uuid");

		assertUnauthorized();
	}

	private static void authenticate(String subject) {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject(subject)
				.issuedAt(Instant.now())
				.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	private void assertUnauthorized() {
		assertThatThrownBy(service::currentUserId)
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
	}
}
