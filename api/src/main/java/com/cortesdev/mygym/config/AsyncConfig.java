package com.cortesdev.mygym.config;

import java.util.concurrent.Executor;
import org.slf4j.LoggerFactory;
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
    // Avisos push (ver PushDispatcher): llamadas HTTP salientes a los servicios de push de los navegadores. Pool chico y
    // dedicado, igual que el de emails, para que un push lento nunca ocupe hilos de requests ni conexiones de Hikari.
    @Bean(name = "pushExecutor")
    public Executor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        // Mejor esfuerzo de verdad: si la cola se llena (ej. un cierre que afecta a cientos de socios) se DESCARTA el aviso y se
        // registra; el comportamiento por defecto lanzaria una excepcion que subiria hasta la reserva o el cierre que lo pidio.
        executor.setRejectedExecutionHandler((task, pool) -> LoggerFactory.getLogger(AsyncConfig.class)
                .warn("Cola de avisos push llena: se descarta un aviso"));
        executor.setThreadNamePrefix("push-");
        executor.initialize();
        return executor;
    }
}
