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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversa dentro do chamado. Quem pode ler e escrever: o morador dono, o síndico e o zelador responsável (a mesma regra
 * de acesso do chamado: para os outros ele "não existe", 404).
 *
 * <p>Privacidade: se o chamado é anônimo, a equipe (síndico e zelador) vê as mensagens do morador como "Anônimo" e os
 * avisos que a equipe recebe não trazem o nome de ninguém.
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

        avisarOsOutros(c, autor, texto);
        return paraResposta(m, c, autor);
    }

    // ---------- internos ----------

    /**
     * Avisa todo mundo que participa do chamado, menos quem escreveu: o dono, os síndicos e o responsável.
     * O morador lê "O síndico respondeu..." ou "O zelador respondeu..."; a equipe lê "Nova mensagem...".
     */
    private void avisarOsOutros(Chamado c, Usuario autor, String texto) {
        Map<Long, Usuario> destinatarios = new LinkedHashMap<>();
        destinatarios.put(c.getMorador().getId(), c.getMorador());
        usuarios.sindicosAtivos().forEach(s -> destinatarios.put(s.getId(), s));
        if (c.getResponsavel() != null && c.getResponsavel().isAtivo()) {
            destinatarios.put(c.getResponsavel().getId(), c.getResponsavel());
        }
        destinatarios.remove(autor.getId());

        String trecho = texto.length() <= TAMANHO_TRECHO_AVISO ? texto : texto.substring(0, TAMANHO_TRECHO_AVISO - 1) + "…";
        String resposta = autor.getPerfil() == Perfil.SINDICO ? "O síndico respondeu"
                : "O " + (autor.getFuncao() == null ? "funcionário" : autor.getFuncao().getRotuloCurto()) + " respondeu";
        Set<String> emails = new HashSet<>();
        for (Usuario d : destinatarios.values()) {
            boolean ehOMoradorDono = d.getId().equals(c.getMorador().getId());
            boolean autorEhEquipe = autor.getPerfil() != Perfil.MORADOR;
            String mensagem = (ehOMoradorDono && autorEhEquipe ? resposta : "Nova mensagem")
                    + " no chamado #" + c.getId() + ": \"" + trecho + "\"";
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
        boolean autorEhEquipe = autor.getPerfil() != Perfil.MORADOR;
        boolean ocultar = !autorEhEquipe && c.isAnonimo() && quem.getPerfil() != Perfil.MORADOR;
        String nome = autorEhEquipe
                ? autor.getNome() + (doSindico ? " (síndico)"
                        : " (" + (autor.getFuncao() == null ? "funcionário" : autor.getFuncao().getRotuloCurto()) + ")")
                : ocultar ? "Anônimo" : autor.getNome();
        return new MensagemResponse(m.getId(), nome, autorEhEquipe, autor.getId().equals(quem.getId()),
                m.getTexto(), m.getCriadoEm());
    }
}
