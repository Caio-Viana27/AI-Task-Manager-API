package br.com.planned.api.dto;

import java.util.List;
import java.util.UUID;

import br.com.planned.api.entity.User;

/** {@code roles} is an array even though a user has one role (wave 1, D2). */
public record UserResponse(UUID id, String name, String email, List<String> roles) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), List.of(user.getRole().getName()));
	}
}
