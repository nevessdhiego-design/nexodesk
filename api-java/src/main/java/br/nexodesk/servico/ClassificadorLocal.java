package br.nexodesk.servico;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.dominio.Dificuldade;
import java.util.List;

/**
 * Modo degradado: usa só a pontuação base da categoria. É o que o sistema faz quando o
 * serviço Python está fora do ar, para nunca bloquear a abertura de um chamado.
 */
public class ClassificadorLocal implements ClassificadorDificuldade {

    @Override
    public ResultadoClassificacao classificar(Categoria categoria, String titulo, String descricao) {
        int pontuacao = categoria.getPontuacaoBase();
        return new ResultadoClassificacao(
                Dificuldade.dePontuacao(pontuacao),
                pontuacao,
                false, // sem o serviço Python não há detecção automática de emergência; o morador ainda pode marcar à mão
                List.of("serviço de prioridade indisponível; classificado só pela categoria "
                        + categoria.name() + ": base " + pontuacao),
                true);
    }
}
