package br.nexodesk.web;

import br.nexodesk.servico.ChamadoService;
import br.nexodesk.web.Dtos.AlterarStatusRequest;
import br.nexodesk.web.Dtos.ChamadoResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Área do funcionário (perfil FUNCIONARIO, regra em SecurityConfig): ele só enxerga e atualiza os chamados que o síndico
 * atribuiu a ele. Chamado de outra pessoa responde 404, como se não existisse.
 */
@RestController
@RequestMapping("/api/funcionario")
public class FuncionarioController {

    private final ChamadoService chamados;

    public FuncionarioController(ChamadoService chamados) {
        this.chamados = chamados;
    }

    /** Meus atendimentos, na mesma ordem da fila (emergência primeiro). ?status=ativos (padrão) | todos | ... */
    @GetMapping("/chamados")
    public List<ChamadoResponse> meusAtendimentos(@RequestParam(required = false) String status,
                                                  Authentication autenticacao) {
        return chamados.atendimentos(autenticacao.getName(), status);
    }

    @PatchMapping("/chamados/{id}/status")
    public ChamadoResponse alterarStatus(@PathVariable Long id, @Valid @RequestBody AlterarStatusRequest req,
                                         Authentication autenticacao) {
        return chamados.alterarStatus(autenticacao.getName(), id, req);
    }

    /** Foto do serviço pronto (multipart, campo "imagem"). */
    @org.springframework.web.bind.annotation.PostMapping(path = "/chamados/{id}/conclusao", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChamadoResponse anexarConclusao(@PathVariable Long id,
                                           @RequestParam("imagem") org.springframework.web.multipart.MultipartFile imagem,
                                           Authentication autenticacao) {
        return chamados.anexarConclusao(autenticacao.getName(), id, imagem);
    }

    /** Marca ou desmarca uma etapa do checklist. */
    @PatchMapping("/tarefas/{id}")
    public br.nexodesk.web.Dtos.TarefaResponse marcarTarefa(@PathVariable Long id,
                                                           @Valid @RequestBody br.nexodesk.web.Dtos.MarcarTarefaRequest req,
                                                           Authentication autenticacao) {
        return chamados.marcarTarefa(autenticacao.getName(), id, req.feita());
    }
}
