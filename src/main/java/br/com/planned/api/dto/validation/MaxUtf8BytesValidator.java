package br.com.planned.api.dto.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

	private int max;

	@Override
	public void initialize(MaxUtf8Bytes annotation) {
		this.max = annotation.value();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || utf8Length(value) <= max;
	}

	public static int utf8Length(String value) {
		return value.getBytes(StandardCharsets.UTF_8).length;
	}
}
