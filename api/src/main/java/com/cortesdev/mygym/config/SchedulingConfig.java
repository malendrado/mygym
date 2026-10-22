package com.cortesdev.mygym.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas (recordatorios, cobros de suscripción, cambios de plan, lista de espera, purgas).
 * Con {@code SCHEDULING_ENABLED=false} la API atiende peticiones pero NO corre tareas: así una copia de respaldo (por ejemplo en Fly.io)
 * puede conectarse a la base real mientras la API principal sigue encendida, sin duplicar correos, avisos ni cobros.
 * Por defecto (variable ausente) están activas, igual que antes.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
