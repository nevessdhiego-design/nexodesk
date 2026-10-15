package br.nexodesk.servico;

import br.nexodesk.dominio.Categoria;

/** RF02: estima a dificuldade de resolver um chamado. */
public interface ClassificadorDificuldade {

    ResultadoClassificacao classificar(Categoria categoria, String titulo, String descricao);
}
