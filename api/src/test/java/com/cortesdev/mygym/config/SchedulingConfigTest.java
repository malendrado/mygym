package com.cortesdev.mygym.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** El interruptor SCHEDULING_ENABLED: una copia de respaldo no debe correr tareas programadas contra la base real. */
class SchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);

    @Test
    void schedulingIsOnByDefault() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void schedulingCanBeTurnedOff() {
        runner.withPropertyValues("app.scheduling.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void schedulingStaysOnWhenExplicitlyEnabled() {
        runner.withPropertyValues("app.scheduling.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
