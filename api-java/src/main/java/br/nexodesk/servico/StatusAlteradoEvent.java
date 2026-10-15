package br.nexodesk.servico;

import br.nexodesk.dominio.StatusChamado;

/** Publicado quando o síndico muda o status de um chamado; ouvido depois do commit. */
public record StatusAlteradoEvent(Long chamadoId, String titulo, String nomeMorador, String emailMorador,
                                  StatusChamado anterior, StatusChamado novo, String observacao) {
}
