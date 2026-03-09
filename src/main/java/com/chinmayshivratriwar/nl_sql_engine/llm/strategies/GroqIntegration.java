package com.chinmayshivratriwar.nl_sql_engine.llm.strategies;

import com.chinmayshivratriwar.nl_sql_engine.llm.LLMStrategy;
import com.chinmayshivratriwar.nl_sql_engine.llm.ratelimiter.LLMRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("groq")
public class GroqIntegration implements LLMStrategy {

    private final ChatClient chatClient;
    private final LLMRateLimiter llmRateLimiter;

    public GroqIntegration(OpenAiChatModel openAiChatModel,
                           @Qualifier("groqRateLimiter") LLMRateLimiter llmRateLimiter) {
        this.chatClient = ChatClient.builder(openAiChatModel).build();
        this.llmRateLimiter = llmRateLimiter;
    }

    @Override
    public String generateSQL(String prompt) {
        llmRateLimiter.validateAndConsume();

        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                throw new RuntimeException(
                        "Groq rate limit hit. Please try again in sometime."
                );
            }
            throw e;
        }
    }
}