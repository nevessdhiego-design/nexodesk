package br.nexodesk.servico;

import java.util.Set;

/** Um chamado passou do prazo. Só ids e e-mails: o conteúdo vem pela API. */
public record PrazoVencidoEvent(Long chamadoId, Set<String> destinatarios) {
}
