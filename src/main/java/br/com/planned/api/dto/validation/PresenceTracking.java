package br.com.planned.api.dto.validation;

/** A request body that knows which properties the client sent (wave 2, D2). */
public interface PresenceTracking {

	boolean isPresent(String field);
}
