package br.com.planned.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.planned.api.repository.UserRepository;
import br.com.planned.api.support.IntegrationTest;

/** One test per PLAN §3 scenario (sign up, sign in, current user). */
@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTest {

	private static final String PASSWORD = "correct horse battery";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	private UserRepository userRepository;

	@MockitoSpyBean
	private PasswordEncoder passwordEncoder;

	@AfterEach
	void deleteUsers() {
		jdbcTemplate.update("DELETE FROM USERS");
	}

	@Test
	void signUp_withValidData_returns201AndUsableToken() throws Exception {
		String body = signUp("ana@example.com", "Ana", PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.expiresAt").isString())
				.andExpect(jsonPath("$.user.id").isString())
				.andExpect(jsonPath("$.user.name").value("Ana"))
				.andExpect(jsonPath("$.user.email").value("ana@example.com"))
				.andExpect(jsonPath("$.user.roles").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.token");
		String id = JsonPath.read(body, "$.user.id");
		Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
		assertThat(expiresAt).isBetween(Instant.now().plusSeconds(59 * 60), Instant.now().plusSeconds(61 * 60));

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.name").value("Ana"))
				.andExpect(jsonPath("$.email").value("ana@example.com"))
				.andExpect(jsonPath("$.roles", hasSize(1)))
				.andExpect(jsonPath("$.roles[0]").value("USER"));
	}

	@Test
	void signUp_withExistingEmail_returns409() throws Exception {
		signUp("ana@example.com", "Ana", PASSWORD).andExpect(status().isCreated());

		expectProblem(signUp("ana@example.com", "Other Ana", PASSWORD), 409, "EMAIL_ALREADY_USED");
		expectProblem(signUp("ANA@Example.com", "Other Ana", PASSWORD), 409, "EMAIL_ALREADY_USED");
		assertThat(userCount()).isEqualTo(1);
	}

	@Test
	void signUp_whenEmailTakenConcurrently_returns409() throws Exception {
		signUp("ana@example.com", "Ana", PASSWORD).andExpect(status().isCreated());
		// Another sign-up inserted the row after this one's existence check.
		doReturn(false).when(userRepository).existsByEmail("ana@example.com");

		expectProblem(signUp("ana@example.com", "Ana", PASSWORD), 409, "EMAIL_ALREADY_USED");
		assertThat(userCount()).isEqualTo(1);
	}

	@Test
	void signUp_whenMixedCaseRowExists_returns409() throws Exception {
		// Bypasses the service, so the stored email isn't normalized.
		jdbcTemplate.update("""
				INSERT INTO USERS (ID, NAME, EMAIL, PASSWORD, ROLE_ID, CREATED_AT, UPDATED_AT)
				VALUES (?, 'Ana', 'Ana@x.com', 'hash', (SELECT ID FROM ROLE WHERE NAME = 'USER'), now(), now())
				""", UUID.randomUUID());

		expectProblem(signUp("ana@x.com", "Ana", PASSWORD), 409, "EMAIL_ALREADY_USED");
		assertThat(userCount()).isEqualTo(1);
	}

	@Test
	void signUp_storesLowercaseEmailAndStrippedName() throws Exception {
		signUp("Ana.Maria@Example.COM", "  Ana Maria  ", PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user.email").value("ana.maria@example.com"))
				.andExpect(jsonPath("$.user.name").value("Ana Maria"));

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT EMAIL, NAME FROM USERS");
		assertThat(row).containsEntry("email", "ana.maria@example.com").containsEntry("name", "Ana Maria");
	}

	@Test
	void signUp_withPasswordOver72Bytes_returns400() throws Exception {
		// 37 characters, but 74 bytes in UTF-8.
		String password = "é".repeat(37);

		expectProblem(signUp("ana@example.com", "Ana", password), 400, "VALIDATION_ERROR")
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("password"))
				.andExpect(jsonPath("$.errors[0].message").value("must be at most 72 bytes"));
		assertThat(userCount()).isZero();

		// Exactly 72 bytes is fine, and works for sign-in.
		signUp("ana@example.com", "Ana", "é".repeat(36)).andExpect(status().isCreated());
		signIn("ana@example.com", "é".repeat(36)).andExpect(status().isOk());
	}

	@Test
	void signUp_withInvalidFields_returns400() throws Exception {
		expectProblem(signUp("not-an-email", " ", "short"), 400, "VALIDATION_ERROR")
				.andExpect(jsonPath("$.errors", hasSize(3)))
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "name", "password")));

		// A well-formed email of 105 characters, a 101-character name, and no password.
		String longEmail = "a".repeat(60) + "@" + "b".repeat(40) + ".com";
		expectProblem(signUp(longEmail, "n".repeat(101), null), 400, "VALIDATION_ERROR")
				.andExpect(jsonPath("$.errors", hasSize(3)))
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "name", "password")));
		assertThat(userCount()).isZero();
	}

	@Test
	void signUp_storesBcryptHash() throws Exception {
		signUp("ana@example.com", "Ana", PASSWORD).andExpect(status().isCreated());

		String stored = jdbcTemplate.queryForObject("SELECT PASSWORD FROM USERS", String.class);
		assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
		assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
	}

	@Test
	void signIn_withValidCredentials_returns200() throws Exception {
		signUp("ana@example.com", "Ana", PASSWORD).andExpect(status().isCreated());

		for (String email : new String[] { "ana@example.com", "ANA@EXAMPLE.COM" }) {
			String body = signIn(email, PASSWORD)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.expiresAt").isString())
					.andExpect(jsonPath("$.user.name").value("Ana"))
					.andExpect(jsonPath("$.user.email").value("ana@example.com"))
					.andReturn().getResponse().getContentAsString();
			String token = JsonPath.read(body, "$.token");
			mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isOk());
		}
	}

	@Test
	void signIn_withWrongPassword_returns401() throws Exception {
		signUp("ana@example.com", "Ana", PASSWORD).andExpect(status().isCreated());

		String wrongPassword = badCredentialsBody(signIn("ana@example.com", "wrong password"));
		String unknownEmail = badCredentialsBody(signIn("nobody@example.com", PASSWORD));
		String passwordOver72Bytes = badCredentialsBody(signIn("ana@example.com", "é".repeat(37)));

		assertThat(unknownEmail).isEqualTo(wrongPassword);
		assertThat(passwordOver72Bytes).isEqualTo(wrongPassword);
	}

	@Test
	void signIn_withUnknownEmail_returns401() throws Exception {
		badCredentialsBody(signIn("nobody@example.com", PASSWORD));

		// A BCrypt check still runs, so the response time doesn't reveal that the email is unknown.
		verify(passwordEncoder, times(1)).matches(anyString(), anyString());
	}

	@Test
	void signIn_withPasswordOver72Bytes_returns401() throws Exception {
		signUp("ana@example.com", "Ana", PASSWORD).andExpect(status().isCreated());
		String tooLong = "é".repeat(37);

		badCredentialsBody(signIn("ana@example.com", tooLong));

		// Same dummy check as an unknown email; the long input never reaches the encoder.
		verify(passwordEncoder, times(1)).matches(anyString(), anyString());
		verify(passwordEncoder, never()).matches(eq(tooLong), anyString());
	}

	@Test
	void signIn_withBlankFields_returns400() throws Exception {
		expectProblem(signIn(" ", ""), 400, "VALIDATION_ERROR")
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "password")));
	}

	@Test
	void me_withoutToken_returns401() throws Exception {
		expectProblem(mockMvc.perform(get("/api/v1/users/me")), 401, "UNAUTHORIZED");
	}

	@Test
	void me_whenUserNoLongerExists_returns401() throws Exception {
		String body = signUp("ana@example.com", "Ana", PASSWORD).andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.token");
		jdbcTemplate.update("DELETE FROM USERS");

		expectProblem(mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)),
				401, "UNAUTHORIZED");
	}

	private ResultActions signUp(String email, String name, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("email", email, "name", name), password)));
	}

	private ResultActions signIn(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signin")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("email", email), password)));
	}

	private String badCredentialsBody(ResultActions result) throws Exception {
		return expectProblem(result, 401, "BAD_CREDENTIALS").andReturn().getResponse().getContentAsString();
	}

	private static ResultActions expectProblem(ResultActions result, int status, String code) throws Exception {
		return result
				.andExpect(status().is(status))
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.code").value(code));
	}

	private int userCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USERS", Integer.class);
	}

	/** Builds the JSON by hand so a {@code null} password can be sent. */
	private static String json(Map<String, String> fields, String password) {
		StringBuilder json = new StringBuilder("{");
		fields.forEach((key, value) -> json.append('"').append(key).append("\":").append(quote(value)).append(','));
		return json.append("\"password\":").append(quote(password)).append('}').toString();
	}

	private static String quote(String value) {
		return value == null ? "null" : '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
	}
}
