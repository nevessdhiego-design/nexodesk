package br.nexodesk.servico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RF05 por e-mail. Roda DEPOIS do commit (se a gravação falhar, ninguém recebe aviso falso) e em
 * outra thread (um SMTP lento não atrasa a resposta ao síndico). Falha de envio só vira log:
 * o aviso dentro do sistema já foi gravado.
 */
@Component
public class NotificadorEmailStatus {

    private static final Logger log = LoggerFactory.getLogger(NotificadorEmailStatus.class);

    private final EnviadorEmail enviador;
    private final String urlPublica;

    public NotificadorEmailStatus(EnviadorEmail enviador,
                                  @Value("${nexodesk.url-publica:http://localhost:8080}") String urlPublica) {
        this.enviador = enviador;
        this.urlPublica = urlPublica;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoMudarStatus(StatusAlteradoEvent e) {
        try {
            enviador.enviar(e.emailMorador(), assunto(e), texto(e));
        } catch (RuntimeException falha) {
            log.warn("Não foi possível enviar o e-mail do chamado #{}: {}", e.chamadoId(), falha.getClass().getSimpleName());
        }
    }

    /** O assunto não usa o título digitado pelo usuário (evita quebra de linha/injeção de cabeçalho). */
    static String assunto(StatusAlteradoEvent e) {
        return "NexoDesk: atualização do chamado #" + e.chamadoId();
    }

    /** Não inclui a descrição do chamado, só o necessário para o morador saber o que mudou. */
    String texto(StatusAlteradoEvent e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Olá, ").append(e.nomeMorador()).append(".\n\n");
        sb.append("O chamado #").append(e.chamadoId()).append(" \"").append(e.titulo()).append("\" mudou de ")
                .append(e.anterior().getRotulo()).append(" para ").append(e.novo().getRotulo()).append(".\n");
        if (e.observacao() != null) {
            sb.append("\nObservação do síndico: ").append(e.observacao()).append("\n");
        }
        sb.append("\nAcompanhe em ").append(urlPublica).append("\n");
        return sb.toString();
    }
}
