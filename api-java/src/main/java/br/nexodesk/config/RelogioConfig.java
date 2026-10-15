package br.nexodesk.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio injetável: permite testar regras de tempo (janela de login) sem esperar. */
@Configuration
public class RelogioConfig {

    @Bean
    Clock relogio() {
        return Clock.systemDefaultZone();
    }
}
