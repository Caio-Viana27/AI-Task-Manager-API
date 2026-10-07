package br.com.planned.api.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.service.JwtService;

/**
 * Stateless JWT security (PLAN §0, JWT). Sign-up, sign-in, the healthcheck, and the API docs are
 * public; everything else needs a valid bearer token signed by {@link JwtConfig}.
 *
 * <p>Every rejection is a {@code 401 UNAUTHORIZED} {@code ProblemDetail}, written by
 * {@code GlobalExceptionHandler} so it has the same shape as any other API error. It never says
 * why the token was rejected. CORS comes from the {@link CorsConfig} bean.
 */
@Configuration
public class SecurityConfig {

	static final String UNAUTHORIZED_DETAIL = "Authentication is required";

	/** Paths that need no token. A token sent to them is ignored, so a stale one can't break sign-in. */
	static final RequestMatcher PUBLIC_PATHS = publicPaths();

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
		AuthenticationEntryPoint entryPoint = (request, response, ex) -> {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			exceptionResolver.resolveException(request, response, null, unauthorized());
		};
		// No endpoint is role-restricted in v1. If one ever denies access, it gets the same 401.
		AccessDeniedHandler accessDeniedHandler = (request, response, ex) ->
				exceptionResolver.resolveException(request, response, null, unauthorized());

		return http
				.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenResolver(bearerTokenResolver())
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.build();
	}

	/** Turns the {@code roles} claim ({@code ["USER"]}) into {@code ROLE_USER} authorities. */
	static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(JwtService.ROLES_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	private static BearerTokenResolver bearerTokenResolver() {
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		return request -> PUBLIC_PATHS.matches(request) ? null : delegate.resolve(request);
	}

	private static RequestMatcher publicPaths() {
		PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
		return new OrRequestMatcher(
				path.matcher(HttpMethod.POST, "/api/v1/auth/signup"),
				path.matcher(HttpMethod.POST, "/api/v1/auth/signin"),
				path.matcher("/actuator/health"),
				path.matcher("/swagger-ui.html"),
				path.matcher("/swagger-ui/**"),
				path.matcher("/v3/api-docs/**"));
	}

	private static ApiException unauthorized() {
		return new ApiException(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_DETAIL);
	}
}
