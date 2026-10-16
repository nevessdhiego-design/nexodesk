package br.nexodesk.servico;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExportadorCsvTest {

    @Test
    void textoComumPassaSemMudar() {
        assertThat(ExportadorCsv.campo("Lâmpada queimada")).isEqualTo("Lâmpada queimada");
        assertThat(ExportadorCsv.campo(null)).isEmpty();
        assertThat(ExportadorCsv.campo("")).isEmpty();
    }

    @Test
    void separadorAspasEQuebraDeLinhaFicamEntreAspas() {
        assertThat(ExportadorCsv.campo("a;b")).isEqualTo("\"a;b\"");
        assertThat(ExportadorCsv.campo("disse \"oi\"")).isEqualTo("\"disse \"\"oi\"\"\"");
        assertThat(ExportadorCsv.campo("linha 1\nlinha 2")).isEqualTo("\"linha 1\nlinha 2\"");
    }

    @Test
    void formulasViramTextoPuro() {
        assertThat(ExportadorCsv.campo("=1+1")).isEqualTo("'=1+1");
        assertThat(ExportadorCsv.campo("+55 11 99999")).isEqualTo("'+55 11 99999");
        assertThat(ExportadorCsv.campo("-2+3")).isEqualTo("'-2+3");
        assertThat(ExportadorCsv.campo("@SOMA(A1)")).isEqualTo("'@SOMA(A1)");
        assertThat(ExportadorCsv.campo("\t=cmd")).startsWith("'");
    }

    @Test
    void formulaComSeparadorTemOApostrofoDentroDasAspas() {
        assertThat(ExportadorCsv.campo("=HYPERLINK(\"u\";\"t\")")).isEqualTo("\"'=HYPERLINK(\"\"u\"\";\"\"t\"\")\"");
    }

    @Test
    void cabecalhoTemBomEUmaLinhaPorChamado() {
        String csv = ExportadorCsv.gerar(java.util.List.of());
        assertThat(csv).startsWith("﻿Fila;ID;Emergência;Título;").contains(";Dias em espera;").endsWith("\r\n");
        assertThat(csv.split("\r\n")).hasSize(1);
    }
}
