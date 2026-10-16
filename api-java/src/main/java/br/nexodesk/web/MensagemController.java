package br.nexodesk.web;

import br.nexodesk.servico.MensagemService;
import br.nexodesk.web.Dtos.MensagemRequest;
import br.nexodesk.web.Dtos.MensagemResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Conversa dentro do chamado. Quem pode ver o chamado pode ler e escrever (regra no serviço). */
@RestController
@RequestMapping("/api/chamados/{id}/mensagens")
public class MensagemController {

    private final MensagemService mensagens;

    public MensagemController(MensagemService mensagens) {
        this.mensagens = mensagens;
    }

    @GetMapping
    public List<MensagemResponse> listar(@PathVariable Long id, Authentication autenticacao) {
        return mensagens.listar(autenticacao.getName(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemResponse enviar(@PathVariable Long id, @Valid @RequestBody MensagemRequest dados,
                                   Authentication autenticacao) {
        return mensagens.enviar(autenticacao.getName(), id, dados.texto());
    }
}
