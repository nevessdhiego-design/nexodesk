package br.nexodesk.web;

import br.nexodesk.servico.PublicadorEventos;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Fluxo de tempo real (Server-Sent Events). Só para quem está logado; cada pessoa recebe só os avisos que são dela. */
@RestController
public class EventosController {

    private final PublicadorEventos publicador;

    public EventosController(PublicadorEventos publicador) {
        this.publicador = publicador;
    }

    @GetMapping(path = "/api/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> assinar(Authentication autenticacao) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no") // nginx não pode guardar a resposta em buffer
                .body(publicador.registrar(autenticacao.getName()));
    }
}
