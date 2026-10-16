package br.nexodesk.servico;

/** Publicado quando um chamado é aberto; ouvido depois do commit para avisar os síndicos em tempo real. */
public record ChamadoAbertoEvent(Long chamadoId, boolean emergencia) {
}
