package br.com.planned.api.service.ai;

import java.util.List;
import java.util.Locale;

/**
 * Maps an {@code Accept-Language} header to the two languages the AI writes in (wave 3, D5): any
 * {@code pt*} first choice is {@code pt-BR}; anything else, a missing header, or a malformed one is
 * {@code en}.
 */
public final class AiLocales {

	public static final Locale ENGLISH = Locale.forLanguageTag("en");
	public static final Locale BRAZILIAN_PORTUGUESE = Locale.forLanguageTag("pt-BR");

	private AiLocales() {
	}

	public static Locale fromAcceptLanguage(String header) {
		if (header == null || header.isBlank()) {
			return ENGLISH;
		}
		List<Locale.LanguageRange> ranges;
		try {
			ranges = Locale.LanguageRange.parse(header);
		} catch (IllegalArgumentException ex) {
			return ENGLISH;
		}
		// parse() sorts by weight, highest first.
		return !ranges.isEmpty() && ranges.getFirst().getRange().startsWith("pt") ? BRAZILIAN_PORTUGUESE : ENGLISH;
	}

	/** The language's English name, as the prompts state it. */
	public static String languageName(Locale locale) {
		return BRAZILIAN_PORTUGUESE.equals(locale) ? "Brazilian Portuguese" : "English";
	}
}
