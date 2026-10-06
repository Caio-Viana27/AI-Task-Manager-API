package br.com.planned.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import br.com.planned.api.support.IntegrationTest;

/** Uses the real application.properties, so it also checks {@code app.cors.allowed-origins}. */
@AutoConfigureMockMvc
class CorsConfigTest extends IntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void preflightFromTheUiOriginGetsCorsHeaders() throws Exception {
		mockMvc.perform(options("/api/tasks")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
						"GET,POST,PUT,PATCH,DELETE,OPTIONS"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						"authorization, content-type"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	void preflightFromAnotherOriginIsRejected() throws Exception {
		mockMvc.perform(options("/api/tasks")
						.header(HttpHeaders.ORIGIN, "http://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}
}
