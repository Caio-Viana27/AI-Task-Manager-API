package br.com.planned.api.exception;

/**
 * Thrown by services for any expected failure. {@link GlobalExceptionHandler} turns it into a
 * {@code ProblemDetail} with the code's HTTP status. The message becomes the {@code detail},
 * so it must never contain secrets or reveal data the user can't see.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	public ApiException(ErrorCode code) {
		this(code, null);
	}

	public ApiException(ErrorCode code, String detail) {
		super(detail);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
