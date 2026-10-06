package br.com.planned.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Every error code the API returns (PLAN §2, Errors). The UI maps each code to an i18n key,
 * so add the matching key to both locales whenever a code is added here.
 */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	INVALID_STATUS(HttpStatus.BAD_REQUEST),
	INVALID_PRIORITY(HttpStatus.BAD_REQUEST),
	INVALID_COMPLEXITY(HttpStatus.BAD_REQUEST),
	SUBTASK_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
	TASK_NOT_FOUND(HttpStatus.NOT_FOUND),
	EMAIL_ALREADY_USED(HttpStatus.CONFLICT),
	AI_INVALID_RESPONSE(HttpStatus.UNPROCESSABLE_CONTENT),
	AI_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
	AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
