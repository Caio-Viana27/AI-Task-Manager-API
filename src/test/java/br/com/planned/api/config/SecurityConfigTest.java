package br.com.planned.api.config;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.planned.api.entity.Role;
import br.com.planned.api.entity.User;
import br.com.planned.api.exception.GlobalExceptionHandler;
import br.com.planned.api.service.CurrentUserService;
import br.com.planned.api.service.JwtService;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@ActiveProfiles("test")
@Import({
		AppConfig.class,
		CorsConfig.class,
		JwtConfig.class,
		SecurityConfig.class,
		GlobalExceptionHandler.class,
		JwtService.class,
		CurrentUserService.class,
		SecurityConfigTest.ProbeController.class })
class SecurityConfigTest {

	private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
	private static final UUID USER_ID = UUID.fromString("2f1f7a8e-3c1b-4b8e-9a7c-5d2e6f1a0b3c");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private AppProperties properties;

	@MockitoBean
	private Clock clock;

	@BeforeEach
	void fixTheClock() {
		setNow(NOW);
		when(clock.getZone()).thenReturn(ZoneOffset.UTC);
	}

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

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/auth/signup", "/api/v1/auth/signin" })
	void signUpAndSignInDoNotRequireAuthentication(String path) throws Exception {
		mockMvc.perform(post(path)).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/auth/signup", "/api/v1/auth/signin" })
	void aStaleTokenDoesNotBreakSignUpOrSignIn(String path) throws Exception {
		mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
				.andExpect(status().isOk());
	}

	@Test
	void requestWithoutTokenReturnsUnauthorizedProblem() throws Exception {
		expectUnauthorizedProblem(mockMvc.perform(get("/api/probe")))
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
	}

	@Test
	void otherActuatorEndpointsRequireAuthentication() throws Exception {
		expectUnauthorizedProblem(mockMvc.perform(get("/actuator/env")));
	}

	@Test
	void expiredTokenReturnsUnauthorized() throws Exception {
		String token = validToken();
		setNow(NOW.plus(properties.jwt().ttl()).plusSeconds(1));

		expectUnauthorizedProblem(mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, bearer(token))))
				// No reason, such as "Jwt expired at ...", leaks out.
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(content().string(not(containsString("xpired"))));
	}

	@Test
	void tamperedTokenReturnsUnauthorized() throws Exception {
		String token = JwtTestSupport.replacePayload(validToken(), payload -> payload.replace("USER", "ADMIN"));

		expectUnauthorizedProblem(mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, bearer(token))));
	}

	@Test
	void tokenSignedWithAnotherSecretReturnsUnauthorized() throws Exception {
		SecretKey otherKey = new SecretKeySpec(
				"another-secret-that-is-also-long-enough-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		String token = new JwtService(JwtTestSupport.encoder(otherKey), clock, properties).issue(user()).token();

		expectUnauthorizedProblem(mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, bearer(token))));
	}

	@Test
	void unsignedTokenReturnsUnauthorized() throws Exception {
		long exp = NOW.plusSeconds(3600).getEpochSecond();
		String token = JwtTestSupport.unsignedToken(
				"{\"sub\":\"" + USER_ID + "\",\"roles\":[\"ADMIN\"],\"iat\":" + NOW.getEpochSecond() + ",\"exp\":" + exp + "}");

		expectUnauthorizedProblem(mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, bearer(token))));
	}

	@Test
	void malformedTokenReturnsUnauthorized() throws Exception {
		expectUnauthorizedProblem(mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, "Bearer garbage")));
	}

	@Test
	void validTokenPassesAndExposesTheUserIdAndRoles() throws Exception {
		String token = validToken();

		mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(content().string(USER_ID.toString()));
		mockMvc.perform(get("/api/probe/authorities").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				// Spring Security also adds a FACTOR_BEARER authority; only the ROLE_ ones come from the claim.
				.andExpect(jsonPath("$[?(@ =~ /ROLE_.*/)]", contains("ROLE_USER")));
	}

	@Test
	void corsPreflightFromTheUiGetsItsHeaders() throws Exception {
		mockMvc.perform(options("/api/v1/users/me")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "authorization"));
	}

	private static ResultActions expectUnauthorizedProblem(ResultActions result) throws Exception {
		return result
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.detail").value("Authentication is required"));
	}

	private void setNow(Instant now) {
		when(clock.instant()).thenReturn(now);
	}

	private String validToken() {
		return jwtService.issue(user()).token();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static User user() {
		Role role = mock(Role.class);
		when(role.getName()).thenReturn("USER");
		User user = new User("Ana", "ana@example.com", "hash", role, NOW);
		user.setId(USER_ID);
		return user;
	}

	/** Stands in for the real endpoints so the test only exercises the security rules. */
	@RestController
	static class ProbeController {

		private final CurrentUserService currentUserService;

		ProbeController(CurrentUserService currentUserService) {
			this.currentUserService = currentUserService;
		}

		@GetMapping({
				"/actuator/health",
				"/actuator/env",
				"/swagger-ui.html",
				"/swagger-ui/index.html",
				"/v3/api-docs",
				"/v3/api-docs/swagger-config" })
		String probe() {
			return "ok";
		}

		@PostMapping({ "/api/v1/auth/signup", "/api/v1/auth/signin" })
		String auth() {
			return "ok";
		}

		@GetMapping("/api/probe")
		String currentUser() {
			return currentUserService.currentUserId().toString();
		}

		@GetMapping("/api/probe/authorities")
		List<String> authorities(Authentication authentication) {
			return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
		}
	}
}
