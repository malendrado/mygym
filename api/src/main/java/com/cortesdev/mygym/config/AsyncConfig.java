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
    // Correos transaccionales (bienvenida, avisos de plan, invitaciones, clave): la llamada HTTP a Resend sale de este pool y no
    // del hilo de la request, que puede estar dentro de una transacción con una de las 5 conexiones de Hikari. Si la cola se
    // llena, el correo se envía en el hilo que lo pidió (CallerRuns): se frena la request en vez de perder el correo.
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Un deploy (reinicio) no debe perder los correos que están en cola en ese instante.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setThreadNamePrefix("email-");
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
