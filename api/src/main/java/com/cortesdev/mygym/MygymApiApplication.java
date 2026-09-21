package com.cortesdev.mygym;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling: necesario para MembershipReminderJob (aviso de "tu
// membresía vence pronto" — antes lo disparaba el cliente al detectar el
// vencimiento en su propia pantalla, ahora corre solo, todos los días).
@SpringBootApplication
@EnableScheduling
public class MygymApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MygymApiApplication.class, args);
    }
}
