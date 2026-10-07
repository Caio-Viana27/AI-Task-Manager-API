package br.com.planned.api.dto.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class MaxUtf8BytesValidatorTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	record Probe(@MaxUtf8Bytes(4) String value) {
	}

	@Test
	void countsBytesNotCharacters() {
		assertThat(validator.validate(new Probe("abcd"))).isEmpty();
		assertThat(validator.validate(new Probe("éé"))).isEmpty();
		assertThat(validator.validate(new Probe("abcde"))).hasSize(1);
		// Three characters, six bytes.
		assertThat(validator.validate(new Probe("ééé")))
				.singleElement()
				.extracting(violation -> violation.getMessage())
				.isEqualTo("must be at most 4 bytes");
	}

	@Test
	void nullIsValid() {
		assertThat(validator.validate(new Probe(null))).isEmpty();
	}
}
