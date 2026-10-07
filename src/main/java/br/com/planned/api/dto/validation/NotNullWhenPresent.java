package br.com.planned.api.dto.validation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * On a {@link PresenceTracking} body: each listed property may be absent, but if it was sent it
 * must not be {@code null}. A violation is reported on the property itself, so the
 * {@code errors} entry names that field (wave 2, D2).
 */
@Documented
@Constraint(validatedBy = NotNullWhenPresentValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface NotNullWhenPresent {

	/** Property names. */
	String[] value();

	String message() default "must not be null";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
