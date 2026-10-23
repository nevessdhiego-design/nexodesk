package br.nexodesk.config;

import br.nexodesk.servico.ChamadoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A cada 15 minutos avisa (uma vez) o síndico e o responsável dos chamados que passaram do prazo. */
@Component
public class AlertaDePrazoAgendado {

    private static final Logger log = LoggerFactory.getLogger(AlertaDePrazoAgendado.class);

    private final ChamadoService chamados;

    public AlertaDePrazoAgendado(ChamadoService chamados) {
        this.chamados = chamados;
    }

    @Scheduled(fixedRate = 15 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void verificar() {
        try {
            int avisados = chamados.alertarPrazosVencidos();
            if (avisados > 0) {
                log.info("{} chamado(s) passaram do prazo e geraram aviso", avisados);
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao verificar prazos: {}", e.getClass().getSimpleName()); // tenta de novo no próximo ciclo
        }
    }
}
