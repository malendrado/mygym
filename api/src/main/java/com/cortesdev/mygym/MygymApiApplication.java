package com.cortesdev.mygym;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Las tareas programadas (MembershipReminderJob y demás) se activan en config/SchedulingConfig, que se puede apagar con SCHEDULING_ENABLED=false.
@SpringBootApplication
public class MygymApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MygymApiApplication.class, args);
    }
}
