package br.com.planned.api.exception;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 {@link ProblemDetail} responses with a {@code code} property
 * (PLAN §2, Errors). Validation failures also carry an {@code errors} list of
 * {@link ValidationError}.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own exceptions (405, 415, ...)
 * are rendered as {@code ProblemDetail} too.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String CODE = "code";
	static final String ERRORS = "errors";

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApiException(ApiException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(ex.getCode().status());
		problem.setDetail(ex.getMessage());
		problem.setProperty(CODE, ex.getCode().name());
		return problem;
	}

	/** {@code @Valid @RequestBody} failed. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ValidationError> errors = ex.getBindingResult().getAllErrors().stream()
				.map(error -> new ValidationError(
						error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
						error.getDefaultMessage()))
				.toList();
		return validationProblem(ex, errors, headers, request);
	}

	/** Constraints on {@code @RequestParam}, {@code @PathVariable}, and similar parameters failed. */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ValidationError> errors = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new ValidationError(
								error instanceof FieldError fieldError
										? fieldError.getField()
										: result.getMethodParameter().getParameterName(),
								error.getDefaultMessage())))
				.toList();
		return validationProblem(ex, errors, headers, request);
	}

	/** A parameter couldn't be converted, e.g. {@code ?dueBefore=not-a-date}. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String field = ex.getPropertyName();
		String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "the expected type";
		List<ValidationError> errors = List.of(new ValidationError(field, "must be a valid " + expected));
		return validationProblem(ex, errors, headers, request);
	}

	/** The body is missing or isn't valid JSON for the target type. */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// Don't echo the parser's message: it names internal classes.
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				ErrorCode.VALIDATION_ERROR.status(), "Request body is missing or malformed");
		problem.setProperty(CODE, ErrorCode.VALIDATION_ERROR.name());
		return handleExceptionInternal(ex, problem, headers, ErrorCode.VALIDATION_ERROR.status(), request);
	}

	private @Nullable ResponseEntity<Object> validationProblem(
			Exception ex, List<ValidationError> errors, HttpHeaders headers, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				ErrorCode.VALIDATION_ERROR.status(), "Request validation failed");
		problem.setProperty(CODE, ErrorCode.VALIDATION_ERROR.name());
		problem.setProperty(ERRORS, errors);
		return handleExceptionInternal(ex, problem, headers, ErrorCode.VALIDATION_ERROR.status(), request);
	}
}
