package br.nexodesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Habilita @Async (usado no envio de e-mail, para não atrasar a resposta da API). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
