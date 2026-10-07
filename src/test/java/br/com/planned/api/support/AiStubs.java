package br.com.planned.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** Stubs the mocked {@link ChatModel} so no test reaches a real provider. */
public final class AiStubs {

	private AiStubs() {
	}

	/** A model response whose text is {@code text}, e.g. a canned JSON reply. */
	public static ChatResponse reply(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	/**
	 * Makes the mock usable through {@code ChatClient}, which reads the model's default options.
	 * {@link #stubReply} and {@link #stubFailure} call it; call it yourself before stubbing
	 * {@code call(Prompt)} directly.
	 */
	public static void stubOptions(ChatModel chatModel) {
		when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
	}

	/** Every call returns {@code text}. */
	public static void stubReply(ChatModel chatModel, String text) {
		stubOptions(chatModel);
		when(chatModel.call(any(Prompt.class))).thenReturn(reply(text));
	}

	/** Every call throws {@code error}. */
	public static void stubFailure(ChatModel chatModel, RuntimeException error) {
		stubOptions(chatModel);
		when(chatModel.call(any(Prompt.class))).thenThrow(error);
	}

	/** The last prompt the model got. */
	public static Prompt lastPrompt(ChatModel chatModel) {
		ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel, atLeastOnce()).call(captor.capture());
		return captor.getValue();
	}

	/** A Google GenAI SDK error with an HTTP status, wrapped as {@code GoogleGenAiChatModel} wraps it. */
	public static RuntimeException googleError(int status) {
		return new RuntimeException("Failed to generate content",
				new com.google.genai.errors.ApiException(status, "STATUS", "message"));
	}
}
