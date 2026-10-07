package br.com.planned.api.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/** {@code Accept-Language} to the AI's output language (wave 3, D5). */
class AiLocalesTest {

	@ParameterizedTest
	@CsvSource({
			"pt-BR, pt-BR",
			"pt, pt-BR",
			"pt-PT, pt-BR",
			"'pt-BR,pt;q=0.9,en;q=0.8', pt-BR",
			"en-US, en",
			"fr, en",
			"'en;q=0.5,pt-BR;q=0.9', pt-BR",
			"'fr,pt;q=0.5', en",
			"'not a ;; header', en" })
	void mapsHeader(String header, String expected) {
		assertThat(AiLocales.fromAcceptLanguage(header)).isEqualTo(Locale.forLanguageTag(expected));
	}

	@ParameterizedTest
	@NullAndEmptySource
	void missingHeader_isEnglish(String header) {
		assertThat(AiLocales.fromAcceptLanguage(header)).isEqualTo(AiLocales.ENGLISH);
	}
}
