package br.nexodesk.servico;

import br.nexodesk.dominio.Usuario;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduz o que aconteceu no sistema em avisos de tempo real. Roda só DEPOIS do commit (se a gravação falhar, ninguém
 * recebe aviso falso) e cada aviso vai só para quem pode ver aquele chamado.
 */
@Component
public class EventosTempoReal {

    /** Tipos de aviso (texto fixo; o navegador usa para decidir o que recarregar). */
    public static final String CHAMADO_NOVO = "chamado-novo";
    public static final String EMERGENCIA = "emergencia";
    public static final String STATUS = "status";
    public static final String MENSAGEM = "mensagem";
    public static final String ATRIBUICAO = "atribuicao";

    private final PublicadorEventos publicador;
    private final UsuarioService usuarios;

    public EventosTempoReal(PublicadorEventos publicador, UsuarioService usuarios) {
        this.publicador = publicador;
        this.usuarios = usuarios;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAbrirChamado(ChamadoAbertoEvent e) {
        publicador.notificar(emailsDosSindicos(), e.emergencia() ? EMERGENCIA : CHAMADO_NOVO, e.chamadoId());
    }

    /** O morador vê o status mudar; os outros síndicos veem a lista se atualizar. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoMudarStatus(StatusAlteradoEvent e) {
        publicador.notificar(e.emailMorador(), STATUS, e.chamadoId());
        publicador.notificar(emailsDosSindicos(), STATUS, e.chamadoId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEnviarMensagem(MensagemEnviadaEvent e) {
        publicador.notificar(e.destinatarios(), MENSAGEM, e.chamadoId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAtribuir(ChamadoAtribuidoEvent e) {
        publicador.notificar(e.destinatarios(), ATRIBUICAO, e.chamadoId());
    }

    private Set<String> emailsDosSindicos() {
        return usuarios.sindicosAtivos().stream().map(Usuario::getEmail).collect(Collectors.toSet());
    }
}
