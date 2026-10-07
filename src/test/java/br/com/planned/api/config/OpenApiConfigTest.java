package br.com.planned.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import br.com.planned.api.support.IntegrationTest;

@AutoConfigureMockMvc
class OpenApiConfigTest extends IntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsDeclareTheBearerJwtScheme() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Planned AI Task Manager API"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
				.andExpect(jsonPath("$.security[0].bearerAuth").isArray());
	}

	@Test
	void signUpAndSignInArePublicInTheDocs() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.security").isEmpty())
				.andExpect(jsonPath("$.paths['/api/v1/auth/signin'].post.security").isEmpty())
				.andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/signin'].post.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/users/me'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses['401']").exists());
	}
}
