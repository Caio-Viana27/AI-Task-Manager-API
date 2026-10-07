package br.com.planned.api.dto;

import java.time.Instant;

/** A signed JWT for the {@code Authorization: Bearer} header, and when it expires. */
public record AuthResponse(String token, Instant expiresAt, AuthUserResponse user) {
}
