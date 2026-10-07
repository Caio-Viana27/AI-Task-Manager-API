package br.com.planned.api.dto;

import java.util.UUID;

import br.com.planned.api.entity.User;

public record AuthUserResponse(UUID id, String name, String email) {

	public static AuthUserResponse from(User user) {
		return new AuthUserResponse(user.getId(), user.getName(), user.getEmail());
	}
}
