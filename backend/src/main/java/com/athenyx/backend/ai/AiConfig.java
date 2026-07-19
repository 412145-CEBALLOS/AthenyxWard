package com.athenyx.backend.ai;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuración de Spring AI + Ollama para Athenyx Ward.
 *
 * <h3>Parámetros aplicados</h3>
 * <ul>
 *   <li><b>timeout = 30 s</b>: Latencia típica de Llama 3 8B en CPU local;
 *       margen suficiente para evitar errores 5xx en el frontend.
 *       Configurado via {@code spring.ai.ollama.chat.options.timeout} en
 *       {@code application.properties}, aplicado por el {@link RestClient} de
 *       {@link OllamaApi} (no requiere configuración adicional aquí).
 *   <li><b>temperature = 0.2</b>: Baja creatividad — respuestas más
 *       determinísticas y enfocadas, adecuadas para explicar amenazas.
 *   <li><b>num-predict = 1000</b>: ~3 párrafos cortos (~1000 tokens), suficiente
 *       para una explicación en lenguaje natural sin consumir VRAM excesiva.
 * </ul>
 *
 * <h3>Lazy connect</h3>
 * El {@link OllamaChatModel} NO contacta a Ollama en su constructor;
 * la primera llamada a {@code .call()} es la que abre la conexión HTTP.
 * Esto permite que el backend arranque con Ollama apagado sin excepciones
 * (requisito US 3.1 / criterio de validación).
 *
 * <h3>Resiliencia (US 3.8)</h3>
 * El bean {@link ChatClient} existe siempre ({@code enabled} es un flag de
 * runtime, no de wiring). {@code AiExplanationService} consulta {@code
 * AiProperties.enabled()} para decidir si invoca al modelo o devuelve
 * un fallback heurístico directamente — sin propagate excepciones al startup.
 */
@Configuration
public class AiConfig {

    private final Boolean envAiEnabled;
    private final String modelName;
    private final double temperature;
    private final int numPredict;
    private final Duration timeout;
    private final ConfigService configService;

    public AiConfig(
            @Value("${spring.ai.ollama.chat.enabled:#{null}}") Boolean envAiEnabled,
            @Value("${spring.ai.ollama.chat.model:qwen2.5:7b-instruct}") String modelName,
            @Value("${spring.ai.ollama.chat.options.temperature:0.2}") double temperature,
            @Value("${spring.ai.ollama.chat.options.num-predict:1000}") int numPredict,
            @Value("${spring.ai.ollama.chat.options.timeout:25s}") Duration timeout,
            ConfigService configService) {
        this.envAiEnabled = envAiEnabled;
        this.modelName = modelName;
        this.temperature = temperature;
        this.numPredict = numPredict;
        this.timeout = timeout;
        this.configService = configService;
    }

    /**
     * Modelo Ollama creado via builder con las opciones por defecto
     * (temperature, num-predict). Creado manualmente (no delega al
     * auto-config del starter) para que el bean esté presente incluso
     * cuando {@code enabled=false}, habilitando el patrón de fallback
     * en US 3.8.
     *
     * <p>El timeout de lectura HTTP se aplica en el {@link OllamaApi}
     * via {@code spring.ai.ollama.chat.options.timeout} (auto-configurado
     * por {@code OllamaApiAutoConfiguration} sobre el {@code RestClient}
     * subyacente).
     */
    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        OllamaChatOptions defaultOptions = OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .numPredict(numPredict)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(defaultOptions)
                .build();
    }

    /**
     * Cliente ChatClient con los defaults de temperature y num-predict
     * aplicados a cada llamada.
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        OllamaChatOptions defaultOptions = OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .numPredict(numPredict)
                .build();

        return ChatClient.builder(chatModel)
                .defaultOptions(defaultOptions)
                .build();
    }

    /**
     * Bean de configuración AI para inyección limpia en servicios.
     * Contiene todos los parámetros necesarios para que
     * AiExplanationService (US 3.2) tome decisiones de fallback
     * sin necesidad de múltiples {@code @Value}.
     */
    @Bean
    public AiProperties aiProperties() {
        return new AiProperties(true, modelName, temperature, numPredict, timeout);
    }
}
