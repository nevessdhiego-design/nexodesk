package br.nexodesk.servico;

import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.Mensagem;
import br.nexodesk.dominio.Notificacao;
import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.MensagemRepository;
import br.nexodesk.repositorio.NotificacaoRepository;
import br.nexodesk.web.Dtos.MensagemResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversa dentro do chamado. Quem pode ler e escrever: o morador dono do chamado e o síndico (a mesma regra de acesso
 * do chamado: para os outros ele "não existe", 404).
 *
 * <p>Privacidade: se o chamado é anônimo, o síndico vê as mensagens do morador como "Anônimo" e os avisos que o
 * síndico recebe não trazem o nome de ninguém.
 */
@Service
public class MensagemService {

    static final int LIMITE_POR_MINUTO = 10;
    static final int TAMANHO_TRECHO_AVISO = 80;

    private final MensagemRepository mensagens;
    private final NotificacaoRepository notificacoes;
    private final UsuarioService usuarios;
    private final ChamadoService chamados;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public MensagemService(MensagemRepository mensagens, NotificacaoRepository notificacoes, UsuarioService usuarios,
                           ChamadoService chamados, ApplicationEventPublisher eventos, Clock relogio) {
        this.mensagens = mensagens;
        this.notificacoes = notificacoes;
        this.usuarios = usuarios;
        this.chamados = chamados;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<MensagemResponse> listar(String email, Long chamadoId) {
        Usuario quem = usuarios.buscarPorEmail(email);
        Chamado c = chamados.buscarComAcesso(quem, chamadoId);
        return mensagens.findByChamadoOrderByCriadoEmAscIdAsc(c).stream()
                .map(m -> paraResposta(m, c, quem))
                .toList();
    }

    @Transactional
    public MensagemResponse enviar(String email, Long chamadoId, String textoBruto) {
        Usuario autor = usuarios.buscarPorEmail(email);
        Chamado c = chamados.buscarComAcesso(autor, chamadoId);
        String texto = textoBruto == null ? "" : textoBruto.trim();
        if (texto.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Escreva a mensagem.");
        }
        if (mensagens.countByAutorAndCriadoEmAfter(autor, LocalDateTime.now(relogio).minusMinutes(1)) >= LIMITE_POR_MINUTO) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas mensagens em pouco tempo. Aguarde um minuto.");
        }

        Mensagem m = new Mensagem();
        m.setChamado(c);
        m.setAutor(autor);
        m.setTexto(texto);
        m = mensagens.save(m);

        avisarOutraParte(c, autor, texto);
        return paraResposta(m, c, autor);
    }

    // ---------- internos ----------

    /** Se o morador escreveu, avisa os síndicos; se o síndico escreveu, avisa o morador dono. */
    private void avisarOutraParte(Chamado c, Usuario autor, String texto) {
        boolean autorEhSindico = autor.getPerfil() == Perfil.SINDICO;
        List<Usuario> destinatarios = autorEhSindico
                ? List.of(c.getMorador())
                : usuarios.sindicosAtivos();
        String trecho = texto.length() <= TAMANHO_TRECHO_AVISO ? texto : texto.substring(0, TAMANHO_TRECHO_AVISO - 1) + "…";
        String mensagem = autorEhSindico
                ? "O síndico respondeu no chamado #" + c.getId() + ": \"" + trecho + "\""
                : "Nova mensagem no chamado #" + c.getId() + ": \"" + trecho + "\"";

        Set<String> emails = new HashSet<>();
        for (Usuario d : destinatarios) {
            if (d.getId().equals(autor.getId())) {
                continue; // não avisa a própria pessoa
            }
            Notificacao n = new Notificacao();
            n.setUsuario(d);
            n.setChamado(c);
            n.setMensagem(mensagem);
            notificacoes.save(n);
            emails.add(d.getEmail());
        }
        if (!emails.isEmpty()) {
            eventos.publishEvent(new MensagemEnviadaEvent(c.getId(), emails));
        }
    }

    private static MensagemResponse paraResposta(Mensagem m, Chamado c, Usuario quem) {
        Usuario autor = m.getAutor();
        boolean doSindico = autor.getPerfil() == Perfil.SINDICO;
        boolean ocultar = !doSindico && c.isAnonimo() && quem.getPerfil() == Perfil.SINDICO;
        String nome = doSindico ? autor.getNome() + " (síndico)" : ocultar ? "Anônimo" : autor.getNome();
        return new MensagemResponse(m.getId(), nome, doSindico, autor.getId().equals(quem.getId()),
                m.getTexto(), m.getCriadoEm());
    }
}
