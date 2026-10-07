package br.com.planned.api.dto.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The string's UTF-8 encoding is at most {@link #value()} bytes. {@code @Size} counts characters,
 * which isn't enough for BCrypt: it only reads the first 72 bytes (wave 1, D5). {@code null} is valid.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT })
@Retention(RUNTIME)
public @interface MaxUtf8Bytes {

	int value();

	String message() default "must be at most {value} bytes";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
