package br.nexodesk.servico;

import org.springframework.http.HttpStatus;

/** Erro previsto de regra de negócio; vira resposta JSON {"erro": "..."} com o status indicado. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
