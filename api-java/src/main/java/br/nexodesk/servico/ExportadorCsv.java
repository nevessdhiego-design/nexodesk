package br.nexodesk.servico;

import br.nexodesk.web.Dtos.ChamadoResponse;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

/**
 * Gera o CSV da fila do síndico para abrir no Excel em português: separador ";", UTF-8 com BOM e
 * quebra de linha CRLF. Duas proteções importantes:
 * <ul>
 *   <li>aspas e quebras de linha dentro do texto não quebram as colunas (campo entre aspas);</li>
 *   <li>injeção de fórmula: texto digitado por um morador que começa com = + - @ vira texto puro
 *       (recebe um apóstrofo na frente), senão o Excel executaria como fórmula.</li>
 * </ul>
 * O CSV respeita o anonimato: chamado anônimo sai sem nome nem unidade, como na tela.
 */
public final class ExportadorCsv {

    static final String BOM = "﻿";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String CABECALHO = "Fila;ID;Emergência;Título;Categoria;Local;Status;Dificuldade;Pontuação;Dias em espera;"
            + "Morador;Unidade;Aberto em;Descrição;Nota da avaliação;Comentário da avaliação";

    private ExportadorCsv() {
    }

    public static String gerar(List<ChamadoResponse> fila) {
        StringBuilder sb = new StringBuilder(BOM).append(CABECALHO).append("\r\n");
        int posicao = 1;
        for (ChamadoResponse c : fila) {
            StringJoiner linha = new StringJoiner(";");
            linha.add(String.valueOf(posicao++));
            linha.add(String.valueOf(c.id()));
            linha.add(c.emergencia() ? "SIM" : "");
            linha.add(campo(c.titulo()));
            linha.add(campo(c.categoriaRotulo()));
            linha.add(campo(c.localOcorrencia()));
            linha.add(campo(c.statusRotulo()));
            linha.add(campo(c.dificuldadeRotulo()));
            linha.add(String.valueOf(c.pontuacao()));
            linha.add(String.valueOf(c.diasEmEspera()));
            linha.add(campo(c.moradorNome()));
            linha.add(campo(c.moradorUnidade()));
            linha.add(campo(c.criadoEm() == null ? null : DATA.format(c.criadoEm())));
            linha.add(campo(c.descricao()));
            linha.add(c.notaAvaliacao() == null ? "" : String.valueOf(c.notaAvaliacao()));
            linha.add(campo(c.comentarioAvaliacao()));
            sb.append(linha).append("\r\n");
        }
        return sb.toString();
    }

    /** Neutraliza fórmula e protege separador, aspas e quebra de linha. */
    static String campo(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String v = valor;
        char primeiro = v.charAt(0);
        if (primeiro == '=' || primeiro == '+' || primeiro == '-' || primeiro == '@'
                || primeiro == '\t' || primeiro == '\r') {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
