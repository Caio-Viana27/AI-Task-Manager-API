package br.com.planned.api.config;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS for the UI origins in {@code app.cors.allowed-origins}. Spring Security picks up this
 * bean through {@code http.cors()}, so preflight requests are answered before authentication.
 */
@Configuration
public class CorsConfig {

	@Bean
	CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.cors().allowedOrigins());
		config.setAllowedMethods(List.of(
				HttpMethod.GET.name(),
				HttpMethod.POST.name(),
				HttpMethod.PUT.name(),
				HttpMethod.PATCH.name(),
				HttpMethod.DELETE.name(),
				HttpMethod.OPTIONS.name()));
		config.setAllowedHeaders(List.of(
				HttpHeaders.AUTHORIZATION,
				HttpHeaders.CONTENT_TYPE,
				HttpHeaders.ACCEPT,
				HttpHeaders.ACCEPT_LANGUAGE));
		config.setExposedHeaders(List.of(HttpHeaders.LOCATION));
		// Bearer tokens travel in a header, not in cookies.
		config.setAllowCredentials(false);
		config.setMaxAge(Duration.ofHours(1));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
