package com.chinmayshivratriwar.nl_sql_engine.llm.factory;

import com.chinmayshivratriwar.nl_sql_engine.llm.LLMStrategy;
import com.chinmayshivratriwar.nl_sql_engine.llm.strategies.GroqIntegration;
import com.chinmayshivratriwar.nl_sql_engine.llm.strategies.OllamaIntegration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LLMFactory {

    private final OllamaIntegration ollamaLLMStrategy;
    private final GroqIntegration groqLLMStrategy;

    @Value("${app.llm.provider}")
    private String provider;

    public LLMStrategy getStrategy() {
        if ("groq".equalsIgnoreCase(provider)) {
            return groqLLMStrategy;
        } else if ("ollama".equalsIgnoreCase(provider)) {
            return ollamaLLMStrategy;
        }
        throw new IllegalArgumentException(
                "Unknown LLM provider: " + provider
        );
    }
}