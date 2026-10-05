package br.com.planned.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({ SecurityConfig.class, SecurityConfigTest.ProbeController.class })
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest
	@ValueSource(strings = {
			"/actuator/health",
			"/swagger-ui.html",
			"/swagger-ui/index.html",
			"/v3/api-docs",
			"/v3/api-docs/swagger-config" })
	void publicPathsDoNotRequireAuthentication(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isOk());
	}

	@Test
	void otherPathsReturnUnauthorized() throws Exception {
		mockMvc.perform(get("/api/probe")).andExpect(status().isUnauthorized());
	}

	@Test
	void otherActuatorEndpointsReturnUnauthorized() throws Exception {
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}

	/** Stands in for the real endpoints so the test only exercises the security rules. */
	@RestController
	static class ProbeController {

		@GetMapping({
				"/actuator/health",
				"/actuator/env",
				"/swagger-ui.html",
				"/swagger-ui/index.html",
				"/v3/api-docs",
				"/v3/api-docs/swagger-config",
				"/api/probe" })
		String probe() {
			return "ok";
		}
	}
}
