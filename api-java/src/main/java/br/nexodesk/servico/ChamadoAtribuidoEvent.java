package br.nexodesk.servico;

import java.util.Set;

/** Publicado quando o síndico atribui (ou retira) o responsável ou muda a previsão; avisa em tempo real quem tem a ver com o chamado. */
public record ChamadoAtribuidoEvent(Long chamadoId, Set<String> destinatarios) {
}
