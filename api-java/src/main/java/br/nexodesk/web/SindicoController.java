package br.nexodesk.web;

import br.nexodesk.servico.ChamadoService;
import br.nexodesk.servico.ExportadorCsv;
import br.nexodesk.servico.UsuarioAdminService;
import br.nexodesk.web.Dtos.AlterarUsuarioRequest;
import br.nexodesk.web.Dtos.AtribuirResponsavelRequest;
import br.nexodesk.web.Dtos.EquipeResponse;
import br.nexodesk.web.Dtos.FuncaoResponse;
import br.nexodesk.web.Dtos.SenhaTemporariaResponse;
import br.nexodesk.web.Dtos.UsuarioAdminResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
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
    private final UsuarioAdminService usuarios;

    public SindicoController(ChamadoService chamados, UsuarioAdminService usuarios) {
        this.chamados = chamados;
        this.usuarios = usuarios;
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

    /** Atribui o chamado a um síndico ou zelador e informa a previsão (ou tira os dois). */
    @PatchMapping("/chamados/{id}/responsavel")
    public ChamadoResponse atribuir(@PathVariable Long id, @RequestBody AtribuirResponsavelRequest req,
                                    Authentication autenticacao) {
        return chamados.atribuir(autenticacao.getName(), id, req);
    }

    /** Síndicos e zeladores ativos: quem pode ser responsável por um chamado. */
    @GetMapping("/equipe")
    public List<EquipeResponse> equipe() {
        return usuarios.equipe();
    }

    /** Funções que um funcionário pode ter e as categorias de chamado que cada uma cobre. */
    @GetMapping("/funcoes")
    public List<FuncaoResponse> funcoes() {
        return java.util.Arrays.stream(br.nexodesk.dominio.Funcao.values())
                .map(f -> new FuncaoResponse(f.name(), f.getRotulo(), f.getCategorias().stream().map(Enum::name).toList(), f.recebeEmergencias()))
                .toList();
    }

    @GetMapping("/usuarios")
    public List<UsuarioAdminResponse> listarUsuarios() {
        return usuarios.listar();
    }

    /** Promove, rebaixa, desativa ou reativa um usuário (regras em UsuarioAdminService). */
    @PatchMapping("/usuarios/{id}")
    public UsuarioAdminResponse alterarUsuario(@PathVariable Long id, @RequestBody AlterarUsuarioRequest req,
                                               Authentication autenticacao) {
        return usuarios.alterar(autenticacao.getName(), id, req);
    }

    /** "Esqueci minha senha": gera uma senha temporária (aparece uma vez) para morador ou zelador. */
    @PostMapping("/usuarios/{id}/redefinir-senha")
    public SenhaTemporariaResponse redefinirSenha(@PathVariable Long id, Authentication autenticacao) {
        return usuarios.redefinirSenha(autenticacao.getName(), id);
    }

    @GetMapping("/estatisticas")
    public EstatisticasResponse estatisticas() {
        return chamados.estatisticas();
    }
}
