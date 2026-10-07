package br.com.planned.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import br.com.planned.api.support.IntegrationTest;

class SecurityContextTest extends IntegrationTest {

	@Autowired
	private ApplicationContext context;

	/** The JwtDecoder bean stops Boot from creating a default user and logging its password. */
	@Test
	void bootDoesNotCreateADefaultUser() {
		assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
	}
}
