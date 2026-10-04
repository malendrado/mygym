package com.cortesdev.mygym.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// Pool chico y dedicado para que el fan-out de emails de un cierre de emergencia (ver
// ClosureNotificationService) nunca compita por hilos con las requests HTTP normales ni abra
// más conexiones simultáneas al pool de 5 de Hikari de las estrictamente necesarias.
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "closureNotificationExecutor")
    public Executor closureNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("closure-email-");
        executor.initialize();
        return executor;
    }
}
