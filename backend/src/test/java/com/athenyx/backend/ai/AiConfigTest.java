package com.athenyx.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de configuración del bean AI (US 3.1).
 *
 * <p>Usa el perfil {@code test} que tiene {@code spring.ai.ollama.chat.enabled=false}
 * y {@code spring.ai.ollama.chat.options.timeout=8s}. Esto verifica que el contexto
 * Spring arranca <strong>sin Ollama corriendo</strong> (lazy connect / criterio 4
 * de validación de US 3.1) y que los beans se wirean correctamente con los valores
 * de properties.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiConfigTest {

    @Autowired
    private AiProperties aiProperties;

    @Autowired(required = false)
    private org.springframework.ai.chat.client.ChatClient chatClient;

    @Autowired(required = false)
    private org.springframework.ai.chat.model.ChatModel chatModel;

    @Test
    void contextLoads_withOllamaDisabled() {
        assertThat(chatClient).as("ChatClient bean must be present even with Ollama disabled")
                .isNotNull();
        assertThat(chatModel).as("ChatModel (OllamaChatModel) bean must be present")
                .isNotNull();
    }

    @Test
    void aiProperties_exposesValuesFromProperties() {
        assertThat(aiProperties.enabled()).isFalse();
        assertThat(aiProperties.modelName()).isEqualTo("llama3");
        assertThat(aiProperties.temperature()).isEqualTo(0.2);
        assertThat(aiProperties.numPredict()).isEqualTo(300);
        assertThat(aiProperties.timeout()).isEqualTo(Duration.ofSeconds(8));
    }
}
