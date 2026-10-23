package br.nexodesk.servico;

import br.nexodesk.dominio.Avaliacao;
import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.StatusChamado;
import br.nexodesk.repositorio.AvaliacaoRepository;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.repositorio.HistoricoStatusRepository;
import br.nexodesk.web.Dtos.CargaFuncionarioResponse;
import br.nexodesk.web.Dtos.ContagemResponse;
import br.nexodesk.web.Dtos.RelatorioResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatório do síndico: o que entrou, o que foi resolvido, quanto demorou, quanto ficou dentro do prazo e quem atendeu.
 * Considera os chamados <b>abertos</b> dentro do período (de/até, inclusive). Só leitura.
 */
@Service
public class RelatorioService {

    static final int MAXIMO_DIAS = 366;

    private final ChamadoRepository chamados;
    private final HistoricoStatusRepository historicos;
    private final AvaliacaoRepository avaliacoes;
    private final Clock relogio;

    public RelatorioService(ChamadoRepository chamados, HistoricoStatusRepository historicos,
                            AvaliacaoRepository avaliacoes, Clock relogio) {
        this.chamados = chamados;
        this.historicos = historicos;
        this.avaliacoes = avaliacoes;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public RelatorioResponse gerar(LocalDate de, LocalDate ate) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate fim = ate == null ? hoje : ate;
        LocalDate inicio = de == null ? fim.minusDays(29) : de;
        if (inicio.isAfter(fim)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A data inicial deve ser anterior ou igual à final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) >= MAXIMO_DIAS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O período pode ter no máximo " + MAXIMO_DIAS + " dias.");
        }
        List<Chamado> lista = chamados.findByCriadoEmGreaterThanEqualAndCriadoEmLessThan(
                inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay());

        long abertos = 0, andamento = 0, resolvidos = 0, emergencias = 0, vencidos = 0;
        LocalDateTime agora = LocalDateTime.now(relogio);
        Map<String, Long> porCategoria = new HashMap<>();
        for (Chamado c : lista) {
            switch (c.getStatus()) {
                case ABERTO -> abertos++;
                case EM_ANDAMENTO -> andamento++;
                case RESOLVIDO -> resolvidos++;
            }
            if (c.isEmergencia()) {
                emergencias++;
            }
            if (c.getStatus() != StatusChamado.RESOLVIDO && c.getPrazoLimite() != null && c.getPrazoLimite().isBefore(agora)) {
                vencidos++;
            }
            porCategoria.merge(c.getCategoria().getRotulo(), 1L, Long::sum);
        }

        // quando cada chamado foi resolvido (a partir do histórico) -> tempo médio e % dentro do prazo
        Map<Long, LocalDateTime> resolvidoEm = new HashMap<>();
        if (!lista.isEmpty()) {
            for (Object[] linha : historicos.resolucoes(StatusChamado.RESOLVIDO, lista)) {
                resolvidoEm.put((Long) linha[0], (LocalDateTime) linha[1]);
            }
        }
        double somaHoras = 0;
        int comTempo = 0, comPrazo = 0, noPrazo = 0;
        for (Chamado c : lista) {
            LocalDateTime fimDoAtendimento = c.getStatus() == StatusChamado.RESOLVIDO ? resolvidoEm.get(c.getId()) : null;
            if (fimDoAtendimento == null) {
                continue;
            }
            somaHoras += Duration.between(c.getCriadoEm(), fimDoAtendimento).toMinutes() / 60.0;
            comTempo++;
            if (c.getPrazoLimite() != null) {
                comPrazo++;
                if (!fimDoAtendimento.isAfter(c.getPrazoLimite())) {
                    noPrazo++;
                }
            }
        }

        // carga e avaliação por quem atendeu
        Map<Long, Avaliacao> notas = new HashMap<>();
        if (!lista.isEmpty()) {
            for (Avaliacao a : avaliacoes.buscarDosChamados(lista)) {
                notas.put(a.getChamado().getId(), a);
            }
        }
        Map<Long, List<Chamado>> porResponsavel = new LinkedHashMap<>();
        for (Chamado c : lista) {
            if (c.getResponsavel() != null) {
                porResponsavel.computeIfAbsent(c.getResponsavel().getId(), k -> new ArrayList<>()).add(c);
            }
        }
        List<CargaFuncionarioResponse> equipe = new ArrayList<>();
        for (List<Chamado> deles : porResponsavel.values()) {
            var pessoa = deles.get(0).getResponsavel();
            double[] soma = {0};
            long[] quantas = {0};
            deles.forEach(c -> {
                Avaliacao a = notas.get(c.getId());
                if (a != null) {
                    soma[0] += a.getNota();
                    quantas[0]++;
                }
            });
            equipe.add(new CargaFuncionarioResponse(pessoa.getNome(),
                    pessoa.getFuncao() == null ? "Síndico" : pessoa.getFuncao().getRotulo(),
                    deles.size(), deles.stream().filter(c -> c.getStatus() == StatusChamado.RESOLVIDO).count(),
                    quantas[0] == 0 ? null : arredondar(soma[0] / quantas[0])));
        }
        equipe.sort(Comparator.comparingLong(CargaFuncionarioResponse::atribuidos).reversed().thenComparing(CargaFuncionarioResponse::nome));

        List<ContagemResponse> categorias = porCategoria.entrySet().stream()
                .map(e -> new ContagemResponse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(ContagemResponse::total).reversed().thenComparing(ContagemResponse::rotulo))
                .toList();

        return new RelatorioResponse(inicio, fim, lista.size(), abertos, andamento, resolvidos, emergencias,
                comTempo == 0 ? null : arredondar(somaHoras / comTempo),
                comPrazo == 0 ? null : arredondar(100.0 * noPrazo / comPrazo),
                vencidos, categorias, equipe);
    }

    /** O mesmo relatório em CSV (abre no Excel). Campos que o Excel leria como fórmula são neutralizados. */
    public String csv(RelatorioResponse r) {
        StringBuilder sb = new StringBuilder(ExportadorCsv.BOM);
        sb.append("Relatório do NexoDesk;").append(r.de()).append(";").append(r.ate()).append("\r\n");
        sb.append("Total de chamados;").append(r.total()).append("\r\n");
        sb.append("Abertos;").append(r.abertos()).append("\r\n");
        sb.append("Em andamento;").append(r.emAndamento()).append("\r\n");
        sb.append("Resolvidos;").append(r.resolvidos()).append("\r\n");
        sb.append("Emergências;").append(r.emergencias()).append("\r\n");
        sb.append("Tempo médio de resolução (h);").append(numero(r.tempoMedioResolucaoHoras())).append("\r\n");
        sb.append("Resolvidos dentro do prazo (%);").append(numero(r.percentualNoPrazo())).append("\r\n");
        sb.append("Fora do prazo agora;").append(r.vencidosAgora()).append("\r\n\r\n");
        sb.append("Categoria;Total\r\n");
        r.porCategoria().forEach(c -> sb.append(ExportadorCsv.campo(c.rotulo())).append(";").append(c.total()).append("\r\n"));
        sb.append("\r\nResponsável;Função;Atribuídos;Resolvidos;Média de avaliação\r\n");
        r.porFuncionario().forEach(f -> sb.append(ExportadorCsv.campo(f.nome())).append(";").append(ExportadorCsv.campo(f.funcao()))
                .append(";").append(f.atribuidos()).append(";").append(f.resolvidos()).append(";").append(numero(f.mediaAvaliacao())).append("\r\n"));
        return sb.toString();
    }

    private static String numero(Double v) {
        return v == null ? "" : String.valueOf(v).replace('.', ',');
    }

    private static double arredondar(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
