package br.com.planned.api.exception;

/** One entry of the {@code errors} list in a validation {@code ProblemDetail}. */
public record ValidationError(String field, String message) {
}
