package br.nexodesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita @Async (e-mail sem atrasar a API) e @Scheduled (batimento do tempo real). */
@Configuration
@EnableAsync
@EnableScheduling // batimento das conexões de tempo real (PublicadorEventos)
public class AsyncConfig {
}
