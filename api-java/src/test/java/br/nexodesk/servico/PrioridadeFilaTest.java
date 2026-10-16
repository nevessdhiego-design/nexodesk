package br.nexodesk.servico;

import static org.assertj.core.api.Assertions.assertThat;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.Dificuldade;
import br.nexodesk.dominio.StatusChamado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** A regra que decide a ordem da fila do síndico, com o relógio sob controle do teste. */
class PrioridadeFilaTest {

    static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");
    final PrioridadeFila fila = new PrioridadeFila(Clock.fixed(AGORA, ZoneOffset.UTC));

    Chamado chamado(long id, int pontuacao, boolean emergencia, int diasDeIdade, StatusChamado status) {
        Chamado c = new Chamado();
        ReflectionTestUtils.setField(c, "id", id);
        c.setPontuacao(pontuacao);
        c.setDificuldade(Dificuldade.dePontuacao(pontuacao));
        c.setCategoria(Categoria.OUTROS);
        c.setEmergencia(emergencia);
        c.setStatus(status);
        ReflectionTestUtils.setField(c, "criadoEm", LocalDateTime.ofInstant(AGORA, ZoneOffset.UTC).minusDays(diasDeIdade));
        return c;
    }

    Chamado novo(long id, int pontuacao, boolean emergencia, int dias) {
        return chamado(id, pontuacao, emergencia, dias, StatusChamado.ABERTO);
    }

    List<Long> ordenar(Chamado... chamados) {
        List<Chamado> lista = new ArrayList<>(List.of(chamados));
        lista.sort(fila.ordem());
        return lista.stream().map(Chamado::getId).toList();
    }

    @Test
    void semEmergenciaNemIdadeVaiDoMaisFacilAoMaisDificil() {
        assertThat(ordenar(novo(1, 8, false, 0), novo(2, 2, false, 0), novo(3, 5, false, 0))).containsExactly(2L, 3L, 1L);
    }

    @Test
    void emergenciaFuraAFilaMesmoSendoDificil() {
        assertThat(ordenar(novo(1, 1, false, 0), novo(2, 10, true, 0), novo(3, 3, false, 0))).containsExactly(2L, 1L, 3L);
    }

    @Test
    void entreEmergenciasValeAOrdemNormal() {
        assertThat(ordenar(novo(1, 9, true, 0), novo(2, 4, true, 0))).containsExactly(2L, 1L);
    }

    @Test
    void aCadaDoisDiasDeEsperaOChamadoGanhaUmPontoAteTres() {
        assertThat(fila.pontuacaoEfetiva(novo(1, 8, false, 0))).isEqualTo(8);
        assertThat(fila.pontuacaoEfetiva(novo(1, 8, false, 1))).isEqualTo(8);   // 1 dia: ainda sem bônus
        assertThat(fila.pontuacaoEfetiva(novo(1, 8, false, 2))).isEqualTo(7);
        assertThat(fila.pontuacaoEfetiva(novo(1, 8, false, 4))).isEqualTo(6);
        assertThat(fila.pontuacaoEfetiva(novo(1, 8, false, 6))).isEqualTo(5);
        assertThat(fila.pontuacaoEfetiva(novo(1, 8, false, 30))).isEqualTo(5);  // teto de 3 pontos
    }

    @Test
    void chamadoDificilAntigoUltrapassaUmMedioNovo() {
        // sem a correção o 2 ficaria na frente do 1 para sempre
        assertThat(ordenar(novo(1, 8, false, 10), novo(2, 6, false, 0))).containsExactly(1L, 2L);
    }

    @Test
    void oBonusNaoPassaDeTresEntaoUmDificilMuitoAntigoNaoVirasFacil() {
        // 10 pontos com 3 de bônus = 7: continua atrás de um chamado de 2 pontos recém-aberto
        assertThat(ordenar(novo(1, 10, false, 90), novo(2, 2, false, 0))).containsExactly(2L, 1L);
    }

    @Test
    void empateNaPontuacaoEfetivaVaiParaOMaisAntigoEDepoisPeloId() {
        assertThat(ordenar(novo(5, 4, false, 0), novo(6, 4, false, 1))).containsExactly(6L, 5L);   // o de 1 dia: 4 e mais antigo
        assertThat(ordenar(novo(8, 4, false, 0), novo(7, 4, false, 0))).containsExactly(7L, 8L);
    }

    @Test
    void chamadoResolvidoNaoEnvelheceNemFicaAtrasado() {
        Chamado resolvido = chamado(1, 8, false, 40, StatusChamado.RESOLVIDO);
        assertThat(fila.diasEmEspera(resolvido)).isZero();
        assertThat(fila.atrasado(resolvido)).isFalse();
        assertThat(fila.pontuacaoEfetiva(resolvido)).isEqualTo(8);
    }

    @Test
    void atrasadoComecaEmSeteDias() {
        assertThat(fila.atrasado(novo(1, 5, false, 6))).isFalse();
        assertThat(fila.atrasado(novo(1, 5, false, 7))).isTrue();
        assertThat(fila.diasEmEspera(novo(1, 5, false, 7))).isEqualTo(7);
    }

    @Test
    void pontuacaoEfetivaNuncaFicaNegativa() {
        assertThat(fila.pontuacaoEfetiva(novo(1, 1, false, 60))).isGreaterThanOrEqualTo(0);
    }
}
