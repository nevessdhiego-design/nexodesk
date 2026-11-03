package br.nexodesk.web;

import br.nexodesk.servico.ChamadoService;
import br.nexodesk.servico.ExportadorCsv;
import br.nexodesk.servico.RelatorioService;
import br.nexodesk.servico.UsuarioAdminService;
import br.nexodesk.web.Dtos.RelatorioResponse;
import br.nexodesk.web.Dtos.AlterarUsuarioRequest;
import br.nexodesk.web.Dtos.AtribuirResponsavelRequest;
import br.nexodesk.web.Dtos.AusenciaRequest;
import br.nexodesk.web.Dtos.AusenciaResponse;
import br.nexodesk.web.Dtos.EscalaRequest;
import br.nexodesk.web.Dtos.CategoriaConfigResponse;
import br.nexodesk.web.Dtos.ChecklistRequest;
import br.nexodesk.web.Dtos.ConfiguracaoResponse;
import br.nexodesk.web.Dtos.EquipeResponse;
import br.nexodesk.web.Dtos.MatrizRequest;
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
    private final RelatorioService relatorios;
    private final br.nexodesk.servico.ConfiguracaoService configuracao;

    public SindicoController(ChamadoService chamados, UsuarioAdminService usuarios, RelatorioService relatorios,
                             br.nexodesk.servico.ConfiguracaoService configuracao) {
        this.chamados = chamados;
        this.usuarios = usuarios;
        this.relatorios = relatorios;
        this.configuracao = configuracao;
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
        var matriz = configuracao.matriz();
        return java.util.Arrays.stream(br.nexodesk.dominio.Funcao.values())
                .map(f -> new FuncaoResponse(f.name(), f.getRotulo(), matriz.get(f).stream().map(Enum::name).toList(), f.recebeEmergencias()))
                .toList();
    }

    /** Tela de configurações: a matriz função x categoria e o checklist de cada categoria. */
    @GetMapping("/configuracao")
    public ConfiguracaoResponse configuracao() {
        List<CategoriaConfigResponse> categorias = java.util.Arrays.stream(br.nexodesk.dominio.Categoria.values())
                .map(c -> new CategoriaConfigResponse(c.name(), c.getRotulo(), c.getPrazoHoras(), configuracao.checklist(c))).toList();
        return new ConfiguracaoResponse(funcoes(), categorias);
    }

    @org.springframework.web.bind.annotation.PutMapping("/configuracao/funcoes")
    public ConfiguracaoResponse salvarMatriz(@Valid @RequestBody MatrizRequest req) {
        configuracao.salvarMatriz(req.matriz());
        return configuracao();
    }

    @org.springframework.web.bind.annotation.PutMapping("/configuracao/checklists/{categoria}")
    public ConfiguracaoResponse salvarChecklist(@PathVariable br.nexodesk.dominio.Categoria categoria, @Valid @RequestBody ChecklistRequest req) {
        configuracao.salvarChecklist(categoria, req.itens());
        return configuracao();
    }

    @PostMapping("/configuracao/restaurar")
    public ConfiguracaoResponse restaurarPadrao() {
        configuracao.restaurarPadrao();
        return configuracao();
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

    /** Relatório do período (padrão: últimos 30 dias). Datas no formato AAAA-MM-DD. */
    @GetMapping("/relatorio")
    public RelatorioResponse relatorio(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate ate) {
        return relatorios.gerar(de, ate);
    }

    @GetMapping("/relatorio.csv")
    public ResponseEntity<byte[]> relatorioCsv(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate ate) {
        RelatorioResponse r = relatorios.gerar(de, ate);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=\"nexodesk-relatorio-" + r.de() + "-a-" + r.ate() + ".csv\"")
                .header("Cache-Control", "no-store")
                .body(relatorios.csv(r).getBytes(StandardCharsets.UTF_8));
    }

    /** Ausências em curso ou futuras da equipe. */
    @GetMapping("/ausencias")
    public List<AusenciaResponse> ausencias() {
        return usuarios.listarAusencias();
    }

    @PostMapping("/usuarios/{id}/ausencias")
    public AusenciaResponse registrarAusencia(@PathVariable Long id, @Valid @RequestBody AusenciaRequest req) {
        return usuarios.registrarAusencia(id, req);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/ausencias/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void removerAusencia(@PathVariable Long id) {
        usuarios.removerAusencia(id);
    }

    /** Dias da semana em que o funcionário trabalha (1 = segunda ... 7 = domingo). */
    @org.springframework.web.bind.annotation.PutMapping("/usuarios/{id}/escala")
    public UsuarioAdminResponse definirEscala(@PathVariable Long id, @Valid @RequestBody EscalaRequest req) {
        return usuarios.definirEscala(id, req.dias());
    }

    @GetMapping("/estatisticas")
    public EstatisticasResponse estatisticas() {
        return chamados.estatisticas();
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
