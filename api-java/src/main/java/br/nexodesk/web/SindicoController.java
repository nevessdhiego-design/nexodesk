package br.nexodesk.web;

import br.nexodesk.servico.ChamadoService;
import br.nexodesk.servico.ExportadorCsv;
import br.nexodesk.web.Dtos.AlterarStatusRequest;
import br.nexodesk.web.Dtos.ChamadoResponse;
import br.nexodesk.web.Dtos.EstatisticasResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tudo em /api/sindico/** exige o perfil SINDICO (regra em SecurityConfig). */
@RestController
@RequestMapping("/api/sindico")
public class SindicoController {

    private final ChamadoService chamados;

    public SindicoController(ChamadoService chamados) {
        this.chamados = chamados;
    }

    /** Fila do mais fácil ao mais difícil. ?status=ativos (padrão) | todos | ABERTO | EM_ANDAMENTO | RESOLVIDO */
    @GetMapping("/chamados")
    public List<ChamadoResponse> fila(@RequestParam(required = false) String status) {
        return chamados.fila(status);
    }

    /** Mesma fila e mesmos filtros da tela, em CSV (abre no Excel). Respeita o anonimato. */
    @GetMapping("/chamados/exportar.csv")
    public ResponseEntity<byte[]> exportarCsv(@RequestParam(required = false) String status) {
        byte[] corpo = ExportadorCsv.gerar(chamados.fila(status)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=\"nexodesk-chamados-" + LocalDate.now() + ".csv\"")
                .header("Cache-Control", "no-store")
                .body(corpo);
    }

    @PatchMapping("/chamados/{id}/status")
    public ChamadoResponse alterarStatus(@PathVariable Long id, @Valid @RequestBody AlterarStatusRequest req,
                                         Authentication autenticacao) {
        return chamados.alterarStatus(autenticacao.getName(), id, req);
    }

    @GetMapping("/estatisticas")
    public EstatisticasResponse estatisticas() {
        return chamados.estatisticas();
    }
}
