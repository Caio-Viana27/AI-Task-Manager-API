package br.com.planned.api.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.springframework.beans.BeanWrapperImpl;

public class NotNullWhenPresentValidator implements ConstraintValidator<NotNullWhenPresent, PresenceTracking> {

	private String[] fields;

	@Override
	public void initialize(NotNullWhenPresent annotation) {
		this.fields = annotation.value();
	}

	@Override
	public boolean isValid(PresenceTracking value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		BeanWrapperImpl bean = new BeanWrapperImpl(value);
		boolean valid = true;
		context.disableDefaultConstraintViolation();
		for (String field : fields) {
			if (value.isPresent(field) && bean.getPropertyValue(field) == null) {
				valid = false;
				context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
						.addPropertyNode(field)
						.addConstraintViolation();
			}
		}
		return valid;
	}
}
