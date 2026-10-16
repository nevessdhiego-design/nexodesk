package br.nexodesk.servico;

import java.util.Set;

/** Publicado quando alguém escreve na conversa de um chamado; {@code destinatarios} são os e-mails de quem deve ser avisado. */
public record MensagemEnviadaEvent(Long chamadoId, Set<String> destinatarios) {
}
