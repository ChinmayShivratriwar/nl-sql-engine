package com.chinmayshivratriwar.nl_sql_engine.llm.strategies;

import com.chinmayshivratriwar.nl_sql_engine.llm.LLMStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;

@Component("ollama")
public class OllamaIntegration implements LLMStrategy {

    private final ChatClient chatClient;

    public OllamaIntegration(OllamaChatModel ollamaChatModel) {
        this.chatClient = ChatClient.builder(ollamaChatModel).build();
    }

    @Override
    public String generateSQL(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .options(OllamaOptions.builder()
                        .temperature(0.0)
                        .numPredict(300)
                        .build())
                .call()
                .content();
    }
}