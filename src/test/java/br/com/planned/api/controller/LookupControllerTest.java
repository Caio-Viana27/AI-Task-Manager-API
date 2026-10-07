package br.com.planned.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import br.com.planned.api.support.IntegrationTest;

/** {@code GET /api/v1/lookups} (PLAN §4). */
@AutoConfigureMockMvc
class LookupControllerTest extends IntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsSeededNamesInSeedOrder() throws Exception {
		mockMvc.perform(get("/api/v1/lookups").with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().json("""
						{
						  "priorities": ["LOW", "MEDIUM", "HIGH"],
						  "statuses": ["TODO", "IN_PROGRESS", "OVERDUE", "DONE"],
						  "complexities": ["EASY", "MEDIUM", "HARD"]
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void withoutTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/lookups"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}
