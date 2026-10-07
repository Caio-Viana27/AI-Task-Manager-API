package br.com.planned.api.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.planned.api.dto.AuthResponse;
import br.com.planned.api.dto.SignInRequest;
import br.com.planned.api.dto.SignUpRequest;
import br.com.planned.api.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Sign up and sign in. Both return a JWT for the Authorization header.")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirements
	@Operation(summary = "Create an account with role USER and sign in")
	@ApiResponse(responseCode = "201", description = "Account created; the token is ready to use")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: a field is missing or invalid",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_USED: the email is registered (in any letter case)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	AuthResponse signUp(@Valid @RequestBody SignUpRequest request) {
		return authService.signUp(request);
	}

	@PostMapping("/signin")
	@SecurityRequirements
	@Operation(summary = "Sign in with email and password")
	@ApiResponse(responseCode = "200", description = "Signed in")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: email or password is blank",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "BAD_CREDENTIALS: unknown email or wrong password",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	AuthResponse signIn(@Valid @RequestBody SignInRequest request) {
		return authService.signIn(request);
	}
}
