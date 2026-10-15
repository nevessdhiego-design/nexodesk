package br.nexodesk.servico;

import br.nexodesk.dominio.Dificuldade;
import java.util.List;

public record ResultadoClassificacao(Dificuldade dificuldade, int pontuacao, List<String> justificativa,
                                     boolean fallback) {
}
