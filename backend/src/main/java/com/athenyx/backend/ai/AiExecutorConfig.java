package com.athenyx.backend.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated thread pool for AI (Ollama) calls.
 *
 * <p>Llama 3 CPU-bound calls with an 8 s timeout should run on a separate
 * pool from the heuristic {@code heuristicsExecutor} to prevent heuristic
 * analysis from being starved when many AI requests queue up.
 *
 * <p>Pool: 1 core thread, 2 max threads, queue of 10. Overflow is handled
 * by {@link ThreadPoolExecutor.CallerRunsPolicy} to apply back-pressure.
 */
@Configuration
public class AiExecutorConfig {

    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(10);
        ex.setThreadNamePrefix("ai-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}
