package br.com.planned.api.controller;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.planned.api.dto.UserResponse;
import br.com.planned.api.service.CurrentUserService;
import br.com.planned.api.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

	private final UserService userService;
	private final CurrentUserService currentUserService;

	public UserController(UserService userService, CurrentUserService currentUserService) {
		this.userService = userService;
		this.currentUserService = currentUserService;
	}

	@GetMapping("/me")
	@Operation(summary = "The signed-in user's profile")
	@ApiResponse(responseCode = "200", description = "The current user")
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	UserResponse me() {
		return userService.getUser(currentUserService.currentUserId());
	}
}
