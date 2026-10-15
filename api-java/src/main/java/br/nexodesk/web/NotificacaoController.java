package br.nexodesk.web;

import br.nexodesk.servico.NotificacaoService;
import br.nexodesk.web.Dtos.NotificacoesResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacoes;

    public NotificacaoController(NotificacaoService notificacoes) {
        this.notificacoes = notificacoes;
    }

    @GetMapping
    public NotificacoesResponse listar(Authentication autenticacao) {
        return notificacoes.listar(autenticacao.getName());
    }

    @PostMapping("/{id}/lida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLida(@PathVariable Long id, Authentication autenticacao) {
        notificacoes.marcarLida(autenticacao.getName(), id);
    }
}
