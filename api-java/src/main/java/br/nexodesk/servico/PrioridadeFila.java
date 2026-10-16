package br.nexodesk.servico;

import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.StatusChamado;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import org.springframework.stereotype.Component;

/**
 * Regra da ordem da fila do síndico (RF03).
 *
 * <ol>
 *   <li><b>Emergência primeiro</b> (risco à vida, saúde ou segurança), qualquer que seja a dificuldade;</li>
 *   <li>depois, da <b>pontuação efetiva</b> menor para a maior, isto é, do mais fácil ao mais difícil;</li>
 *   <li>em empate, o chamado mais antigo.</li>
 * </ol>
 *
 * <p>Sem correção, "sempre o mais fácil primeiro" faria os chamados difíceis esperarem para sempre (os fáceis
 * furam a fila o tempo todo). Por isso a <b>pontuação efetiva</b> cai 1 ponto a cada 2 dias de espera, até 3 pontos:
 * um chamado difícil antigo vai subindo na fila. A dificuldade mostrada <b>não muda</b>, só a ordem.
 *
 * <p>Idade e prioridade continuam separadas: mostramos "há N dias" e "atrasado" (7 dias ou mais) como informação,
 * em vez de tratar todo chamado antigo como urgente (isso inflaria a prioridade de tudo).
 */
@Component
public class PrioridadeFila {

    public static final int BONUS_MAXIMO = 3;
    public static final int DIAS_POR_PONTO = 2;
    public static final int LIMITE_ATRASO_DIAS = 7;

    private final Clock relogio;

    public PrioridadeFila(Clock relogio) {
        this.relogio = relogio;
    }

    /** Dias que o chamado espera por uma solução (0 se já resolvido). */
    public int diasEmEspera(Chamado c) {
        if (c.getStatus() == StatusChamado.RESOLVIDO || c.getCriadoEm() == null) {
            return 0;
        }
        return (int) Math.max(0, ChronoUnit.DAYS.between(c.getCriadoEm(), LocalDateTime.now(relogio)));
    }

    public int pontuacaoEfetiva(Chamado c) {
        int bonus = Math.min(BONUS_MAXIMO, diasEmEspera(c) / DIAS_POR_PONTO);
        return Math.max(0, c.getPontuacao() - bonus);
    }

    public boolean atrasado(Chamado c) {
        return diasEmEspera(c) >= LIMITE_ATRASO_DIAS;
    }

    public Comparator<Chamado> ordem() {
        return Comparator
                .comparing((Chamado c) -> !c.isEmergencia())          // false vem primeiro: emergência no topo
                .thenComparingInt(this::pontuacaoEfetiva)
                .thenComparing(Chamado::getCriadoEm, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Chamado::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
