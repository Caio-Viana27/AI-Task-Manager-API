package br.com.planned.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API docs metadata and the bearer JWT scheme. Every operation requires the token by default;
 * public endpoints (sign up, sign in) opt out with an empty {@code @SecurityRequirements}.
 */
@Configuration
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Planned AI Task Manager API")
						.version("v1"))
				.components(new Components()
						.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}
}
