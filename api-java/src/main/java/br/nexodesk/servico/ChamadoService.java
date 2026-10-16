package br.nexodesk.servico;

import br.nexodesk.dominio.Avaliacao;
import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.Dificuldade;
import br.nexodesk.dominio.HistoricoStatus;
import br.nexodesk.dominio.Notificacao;
import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.StatusChamado;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.AvaliacaoRepository;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.repositorio.HistoricoStatusRepository;
import br.nexodesk.repositorio.NotificacaoRepository;
import br.nexodesk.web.Dtos.AlterarStatusRequest;
import br.nexodesk.web.Dtos.AtribuirResponsavelRequest;
import br.nexodesk.web.Dtos.ChamadoResponse;
import br.nexodesk.web.Dtos.EstatisticasResponse;
import br.nexodesk.web.Dtos.HistoricoResponse;
import br.nexodesk.web.Dtos.NovoChamadoForm;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ChamadoService {

    private static final String SEPARADOR = " | ";
    private static final Set<StatusChamado> PENDENTES = EnumSet.of(StatusChamado.ABERTO, StatusChamado.EM_ANDAMENTO);

    private final ChamadoRepository chamados;
    private final HistoricoStatusRepository historicos;
    private final NotificacaoRepository notificacoes;
    private final UsuarioService usuarios;
    private final ClassificadorDificuldade classificador;
    private final ArmazenamentoImagem imagens;
    private final AvaliacaoRepository avaliacoes;
    private final ApplicationEventPublisher eventos;
    private final PrioridadeFila prioridade;
    private final Clock relogio;

    public ChamadoService(ChamadoRepository chamados, HistoricoStatusRepository historicos,
                          NotificacaoRepository notificacoes, UsuarioService usuarios,
                          ClassificadorDificuldade classificador, ArmazenamentoImagem imagens,
                          AvaliacaoRepository avaliacoes, ApplicationEventPublisher eventos,
                          PrioridadeFila prioridade, Clock relogio) {
        this.chamados = chamados;
        this.historicos = historicos;
        this.notificacoes = notificacoes;
        this.usuarios = usuarios;
        this.classificador = classificador;
        this.imagens = imagens;
        this.avaliacoes = avaliacoes;
        this.eventos = eventos;
        this.prioridade = prioridade;
        this.relogio = relogio;
    }

    /** RF01 + RF02: cadastra o chamado já classificado por dificuldade. */
    @Transactional
    public ChamadoResponse abrir(String emailMorador, NovoChamadoForm form) {
        Usuario morador = usuarios.buscarPorEmail(emailMorador);
        String titulo = form.titulo().trim();
        String descricao = form.descricao().trim();
        // valida a imagem antes de gastar uma chamada ao serviço de prioridade
        String nomeImagem = imagens.salvar(form.imagem());
        if (nomeImagem != null) {
            // se a transação não confirmar (erro ao classificar ou gravar), a foto não fica órfã no disco
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        imagens.remover(nomeImagem);
                    }
                }
            });
        }

        ResultadoClassificacao r = classificador.classificar(form.categoria(), titulo, descricao);

        Chamado c = new Chamado();
        c.setTitulo(titulo);
        c.setDescricao(descricao);
        c.setCategoria(form.categoria());
        c.setLocalOcorrencia(form.localOcorrencia() == null || form.localOcorrencia().isBlank()
                ? null : form.localOcorrencia().trim());
        c.setAnonimo(Boolean.TRUE.equals(form.anonimo()));
        c.setImagem(nomeImagem);
        c.setStatus(StatusChamado.ABERTO);
        c.setDificuldade(r.dificuldade());
        c.setPontuacao(r.pontuacao());
        boolean marcadaPeloMorador = Boolean.TRUE.equals(form.emergencia());
        c.setEmergencia(marcadaPeloMorador || r.emergencia());
        List<String> justificativa = new java.util.ArrayList<>(r.justificativa());
        if (marcadaPeloMorador) {
            justificativa.add("emergência marcada pelo morador");
        }
        c.setJustificativa(truncar(String.join(SEPARADOR, justificativa), 1000));
        c.setClassificacaoFallback(r.fallback());
        c.setMorador(morador);
        c = chamados.save(c);

        registrarHistorico(c, null, StatusChamado.ABERTO, morador, "Chamado aberto.");
        if (c.isEmergencia()) {
            avisarSindicosDaEmergencia(c);
        }
        // tempo real: os síndicos veem o chamado novo (ou a emergência) sem recarregar a página
        eventos.publishEvent(new ChamadoAbertoEvent(c.getId(), c.isEmergencia()));
        return paraResposta(c, false, List.of(), null);
    }

    @Transactional(readOnly = true)
    public List<ChamadoResponse> meus(String emailMorador) {
        Usuario morador = usuarios.buscarPorEmail(emailMorador);
        List<Chamado> lista = chamados.findByMoradorOrderByCriadoEmDesc(morador);
        Map<Long, Avaliacao> notas = avaliacoesDe(lista);
        return lista.stream().map(c -> paraResposta(c, false, List.of(), notas.get(c.getId()))).toList();
    }

    /**
     * RF03: fila do síndico. Emergências primeiro; depois do mais fácil ao mais difícil, com bônus de idade
     * para o chamado antigo não ser esquecido (veja {@link PrioridadeFila}).
     * filtro: "ativos" (padrão: aberto + em andamento), "todos" ou um status.
     */
    @Transactional(readOnly = true)
    public List<ChamadoResponse> fila(String filtro) {
        List<Chamado> lista = new java.util.ArrayList<>(chamados.findByStatusIn(statusDoFiltro(filtro)));
        lista.sort(prioridade.ordem());
        Map<Long, Avaliacao> notas = avaliacoesDe(lista);
        return lista.stream().map(c -> paraResposta(c, true, List.of(), notas.get(c.getId()))).toList();
    }

    /** "Meus atendimentos" do zelador: só os chamados que o síndico atribuiu a ele, na mesma ordem da fila. */
    @Transactional(readOnly = true)
    public List<ChamadoResponse> atendimentos(String emailDoZelador, String filtro) {
        Usuario zelador = usuarios.buscarPorEmail(emailDoZelador);
        List<Chamado> lista = new java.util.ArrayList<>(
                chamados.findByResponsavelAndStatusIn(zelador, statusDoFiltro(filtro)));
        lista.sort(prioridade.ordem());
        Map<Long, Avaliacao> notas = avaliacoesDe(lista);
        return lista.stream().map(c -> paraResposta(c, true, List.of(), notas.get(c.getId()))).toList();
    }

    /** "ativos" (padrão: aberto + em andamento), "todos" ou um status. */
    private static Set<StatusChamado> statusDoFiltro(String filtro) {
        if (filtro == null || filtro.isBlank() || filtro.equalsIgnoreCase("ativos")) {
            return PENDENTES;
        }
        if (filtro.equalsIgnoreCase("todos")) {
            return EnumSet.allOf(StatusChamado.class);
        }
        try {
            return EnumSet.of(StatusChamado.valueOf(filtro.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Filtro de status inválido.");
        }
    }

    @Transactional(readOnly = true)
    public ChamadoResponse detalhe(String email, Long id) {
        Usuario quem = usuarios.buscarPorEmail(email);
        Chamado c = buscarComAcesso(quem, id);
        boolean visaoSindico = quem.getPerfil() != Perfil.MORADOR; // síndico e zelador: sem identidade em chamado anônimo
        List<HistoricoResponse> historico = historicos.findByChamadoOrderByCriadoEmAscIdAsc(c).stream()
                .map(h -> new HistoricoResponse(
                        h.getStatusAnterior() == null ? null : h.getStatusAnterior().getRotulo(),
                        h.getStatusNovo().getRotulo(),
                        h.getAlteradoPor().getNome(),
                        h.getObservacao(),
                        h.getCriadoEm()))
                .toList();
        Avaliacao nota = avaliacoes.buscarDosChamados(List.of(c)).stream().findFirst().orElse(null);
        return paraResposta(c, visaoSindico, historico, nota);
    }

    /** RF04 + RF05: muda o status, registra o histórico e avisa o morador. */
    @Transactional
    public ChamadoResponse alterarStatus(String emailSindico, Long id, AlterarStatusRequest req) {
        Usuario sindico = usuarios.buscarPorEmail(emailSindico);
        Chamado c = chamados.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chamado não encontrado."));
        if (sindico.getPerfil() == Perfil.MORADOR) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Só a equipe do condomínio muda o status.");
        }
        if (sindico.getPerfil() == Perfil.ZELADOR && !ehResponsavel(c, sindico)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Chamado não encontrado."); // o zelador só enxerga os dele
        }
        verificarVersao(c, req.versao());
        StatusChamado anterior = c.getStatus();
        if (anterior == req.status()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O chamado já está com esse status.");
        }
        String observacao = req.observacao() == null || req.observacao().isBlank() ? null : req.observacao().trim();

        c.setStatus(req.status());
        chamados.saveAndFlush(c); // flush: a versão devolvida na resposta já é a nova
        registrarHistorico(c, anterior, req.status(), sindico, observacao);

        Notificacao n = new Notificacao();
        n.setUsuario(c.getMorador());
        n.setChamado(c);
        n.setMensagem(montarMensagem(c, anterior, observacao));
        notificacoes.save(n);

        // o e-mail só sai depois que a transação confirmar (veja NotificadorEmailStatus)
        eventos.publishEvent(new StatusAlteradoEvent(c.getId(), c.getTitulo(), c.getMorador().getNome(),
                c.getMorador().getEmail(), anterior, c.getStatus(), observacao));

        Avaliacao nota = avaliacoes.buscarDosChamados(List.of(c)).stream().findFirst().orElse(null);
        return paraResposta(c, true, List.of(), nota);
    }

    /**
     * O síndico escolhe quem cuida do chamado (síndico ou zelador ativo) e a previsão. Avisa o morador, o novo responsável
     * e quem deixou de ser. Passar responsavelId nulo tira o responsável; previsao nula tira a previsão.
     */
    @Transactional
    public ChamadoResponse atribuir(String emailSindico, Long id, AtribuirResponsavelRequest req) {
        Usuario autor = usuarios.buscarPorEmail(emailSindico);
        Chamado c = chamados.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chamado não encontrado."));
        verificarVersao(c, req.versao());
        if (c.getStatus() == StatusChamado.RESOLVIDO) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Este chamado já foi resolvido.");
        }
        Usuario novo = req.responsavelId() == null ? null : usuarios.buscarEquipeAtivaPorId(req.responsavelId());
        LocalDate hoje = LocalDate.now(relogio);
        if (req.previsao() != null && (req.previsao().isBefore(hoje) || req.previsao().isAfter(hoje.plusYears(1)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A previsão deve ser de hoje até um ano à frente.");
        }

        Usuario anterior = c.getResponsavel();
        boolean mudouResponsavel = (novo == null ? anterior != null : anterior == null || !anterior.getId().equals(novo.getId()));
        boolean mudouPrevisao = !java.util.Objects.equals(c.getPrevisao(), req.previsao());
        c.setResponsavel(novo);
        c.setPrevisao(req.previsao());
        chamados.saveAndFlush(c);

        Set<String> emails = new java.util.HashSet<>();
        if (mudouResponsavel || mudouPrevisao) {
            String data = req.previsao() == null ? "" : " Previsão: "
                    + req.previsao().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".";
            if (novo != null || req.previsao() != null) {
                avisar(c.getMorador(), c, truncar("Seu chamado #" + c.getId() + (novo == null ? ""
                        : " agora tem responsável: " + novo.getNome() + ".") + data, 300));
            }
            emails.add(c.getMorador().getEmail());
            if (novo != null && !novo.getId().equals(autor.getId()) && (mudouResponsavel || mudouPrevisao)) {
                avisar(novo, c, truncar("O chamado #" + c.getId() + " \"" + truncar(c.getTitulo(), 60)
                        + "\" foi atribuído a você." + data, 300));
            }
            if (novo != null) {
                emails.add(novo.getEmail());
            }
            if (anterior != null && (novo == null || !anterior.getId().equals(novo.getId()))) {
                if (!anterior.getId().equals(autor.getId())) {
                    avisar(anterior, c, "O chamado #" + c.getId() + " deixou de ser seu.");
                }
                emails.add(anterior.getEmail());
            }
            usuarios.sindicosAtivos().forEach(sd -> emails.add(sd.getEmail()));
            eventos.publishEvent(new ChamadoAtribuidoEvent(c.getId(), emails));
        }
        Avaliacao nota = avaliacoes.buscarDosChamados(List.of(c)).stream().findFirst().orElse(null);
        return paraResposta(c, true, List.of(), nota);
    }

    private void avisar(Usuario destinatario, Chamado c, String mensagem) {
        Notificacao n = new Notificacao();
        n.setUsuario(destinatario);
        n.setChamado(c);
        n.setMensagem(mensagem);
        notificacoes.save(n);
    }

    @Transactional(readOnly = true)
    public EstatisticasResponse estatisticas() {
        return new EstatisticasResponse(
                chamados.countByStatus(StatusChamado.ABERTO),
                chamados.countByStatus(StatusChamado.EM_ANDAMENTO),
                chamados.countByStatus(StatusChamado.RESOLVIDO),
                pendentes(Dificuldade.FACIL),
                pendentes(Dificuldade.MEDIA),
                pendentes(Dificuldade.DIFICIL),
                media(avaliacoes.mediaDasNotas()),
                avaliacoes.count(),
                chamados.countByEmergenciaTrueAndStatusIn(PENDENTES));
    }

    @Transactional(readOnly = true)
    public Resource imagem(String email, Long id) {
        Usuario quem = usuarios.buscarPorEmail(email);
        Chamado c = buscarComAcesso(quem, id);
        if (c.getImagem() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Este chamado não tem imagem.");
        }
        return imagens.carregar(c.getImagem());
    }

    public String tipoImagem(Resource recurso) {
        String nome = recurso.getFilename();
        return nome == null ? "image/jpeg" : imagens.tipoDe(nome).toString();
    }

    // ---------- internos ----------

    private Map<Long, Avaliacao> avaliacoesDe(List<Chamado> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        return avaliacoes.buscarDosChamados(lista).stream()
                .collect(Collectors.toMap(a -> a.getChamado().getId(), Function.identity()));
    }

    /** Média com uma casa decimal (4,3333 vira 4,3), ou null se ninguém avaliou ainda. */
    private static Double media(Double valor) {
        return valor == null ? null : Math.round(valor * 10.0) / 10.0;
    }

    /** Aviso dentro do sistema para cada síndico ativo. Não leva o nome do morador (o chamado pode ser anônimo). */
    private void avisarSindicosDaEmergencia(Chamado c) {
        String mensagem = truncar("EMERGÊNCIA no chamado #" + c.getId() + " \"" + truncar(c.getTitulo(), 60) + "\" ("
                + c.getCategoria().getRotulo() + "). Atenda primeiro.", 300);
        for (Usuario sindico : usuarios.sindicosAtivos()) {
            Notificacao n = new Notificacao();
            n.setUsuario(sindico);
            n.setChamado(c);
            n.setMensagem(mensagem);
            notificacoes.save(n);
        }
    }

    private long pendentes(Dificuldade d) {
        return PENDENTES.stream().mapToLong(s -> chamados.countByStatusAndDificuldade(s, d)).sum();
    }

    /** Morador só vê o que é dele; para os outros o chamado "não existe" (404, não 403). */
    public Chamado buscarComAcesso(Usuario quem, Long id) {
        Chamado c = chamados.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chamado não encontrado."));
        boolean dono = c.getMorador().getId().equals(quem.getId());
        boolean sindico = quem.getPerfil() == Perfil.SINDICO;
        boolean zeladorResponsavel = quem.getPerfil() == Perfil.ZELADOR && ehResponsavel(c, quem);
        if (!dono && !sindico && !zeladorResponsavel) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Chamado não encontrado.");
        }
        return c;
    }

    private static boolean ehResponsavel(Chamado c, Usuario quem) {
        return c.getResponsavel() != null && c.getResponsavel().getId().equals(quem.getId());
    }

    /** A tela estava desatualizada: outra pessoa já mexeu neste chamado. */
    private static void verificarVersao(Chamado c, Long versaoVista) {
        if (versaoVista != null && !versaoVista.equals(c.getVersao())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Este chamado foi alterado por outra pessoa. Atualize a lista e tente de novo.");
        }
    }

    private void registrarHistorico(Chamado c, StatusChamado anterior, StatusChamado novo,
                                    Usuario por, String observacao) {
        HistoricoStatus h = new HistoricoStatus();
        h.setChamado(c);
        h.setStatusAnterior(anterior);
        h.setStatusNovo(novo);
        h.setAlteradoPor(por);
        h.setObservacao(observacao);
        historicos.save(h);
    }

    private static String montarMensagem(Chamado c, StatusChamado anterior, String observacao) {
        String base = "Seu chamado #" + c.getId() + " \"" + truncar(c.getTitulo(), 60) + "\" mudou de "
                + anterior.getRotulo() + " para " + c.getStatus().getRotulo() + ".";
        return observacao == null ? base : truncar(base + " Observação: " + observacao, 300);
    }

    private static String truncar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** No chamado anônimo o síndico não recebe nome nem unidade do morador. */
    private ChamadoResponse paraResposta(Chamado c, boolean visaoSindico, List<HistoricoResponse> historico,
                                         Avaliacao avaliacao) {
        boolean ocultar = visaoSindico && c.isAnonimo();
        List<String> justificativa = c.getJustificativa() == null
                ? List.of()
                : Arrays.asList(c.getJustificativa().split(" \\| "));
        return new ChamadoResponse(
                c.getId(), c.getTitulo(), c.getDescricao(),
                c.getCategoria().name(), c.getCategoria().getRotulo(),
                c.getLocalOcorrencia(), c.isAnonimo(), c.getImagem() != null,
                c.getStatus().name(), c.getStatus().getRotulo(),
                c.getDificuldade().name(), c.getDificuldade().getRotulo(),
                c.getPontuacao(), justificativa, c.isClassificacaoFallback(),
                c.isEmergencia(), prioridade.pontuacaoEfetiva(c), prioridade.diasEmEspera(c), prioridade.atrasado(c),
                ocultar ? "Anônimo" : c.getMorador().getNome(),
                ocultar ? null : c.getMorador().getUnidade(),
                c.getCriadoEm(), c.getAtualizadoEm(), c.getVersao(),
                c.getResponsavel() == null ? null : c.getResponsavel().getId(),
                c.getResponsavel() == null ? null : c.getResponsavel().getNome(),
                c.getPrevisao(),
                avaliacao == null ? null : avaliacao.getNota(),
                avaliacao == null ? null : avaliacao.getComentario(),
                historico);
    }
}
