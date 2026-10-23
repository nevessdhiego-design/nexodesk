package br.nexodesk.web;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.dominio.StatusChamado;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Objetos de entrada e saída da API (nada de entidade JPA direto no JSON). */
public final class Dtos {

    private Dtos() {
    }

    // ---------- entrada ----------

    public record CadastroRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String nome,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @Size(max = 20) String telefone,
            @Size(max = 40) String unidade,
            // bcrypt só considera os primeiros 72 bytes
            @NotBlank(message = "Informe a senha.")
            @Size(min = 8, max = 72, message = "A senha deve ter de 8 a 72 caracteres.") String senha) {
    }

    public record LoginRequest(
            @NotBlank(message = "Informe o e-mail.") String email,
            @NotBlank(message = "Informe a senha.") String senha) {
    }

    public record NovoChamadoForm(
            @NotBlank(message = "Informe o título.") @Size(max = 150) String titulo,
            @NotBlank(message = "Descreva o problema.") @Size(max = 2000) String descricao,
            @NotNull(message = "Escolha a categoria.") Categoria categoria,
            @Size(max = 120) String localOcorrencia,
            Boolean anonimo, // objeto (não boolean): campo ausente vira null em vez de erro de conversão
            Boolean emergencia, // o morador marca "é uma emergência"; o Python também pode detectar sozinho
            MultipartFile imagem) {
    }

    public record AtualizarPerfilRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String nome,
            @Size(max = 20) String telefone,
            @Size(max = 40) String unidade) {
    }

    public record AlterarSenhaRequest(
            @NotBlank(message = "Informe a senha atual.") String senhaAtual,
            @NotBlank(message = "Informe a nova senha.")
            @Size(min = 8, max = 72, message = "A nova senha deve ter de 8 a 72 caracteres.") String novaSenha) {
    }

    public record AvaliacaoRequest(
            @NotNull(message = "Informe a nota de 1 a 5.")
            @Min(value = 1, message = "A nota deve ser de 1 a 5.")
            @Max(value = 5, message = "A nota deve ser de 1 a 5.") Integer nota,
            @Size(max = 500, message = "O comentário deve ter no máximo 500 caracteres.") String comentario) {
    }

    /** Confirmação da exclusão de conta: a senha atual, para ninguém apagar a conta de outra pessoa numa sessão esquecida. */
    public record ExcluirContaRequest(@NotBlank(message = "Informe a sua senha para confirmar.") String senha) {
    }

    public record MensagemRequest(
            @NotBlank(message = "Escreva a mensagem.")
            @Size(max = 1000, message = "A mensagem deve ter no máximo 1000 caracteres.") String texto) {
    }

    /** responsavelId nulo remove o responsável. previsao nula remove a previsão. */
    public record AtribuirResponsavelRequest(Long responsavelId, java.time.LocalDate previsao, Long versao) {
    }

    /** funcao só vale para o perfil FUNCIONARIO (se faltar, fica a atual ou ZELADOR). */
    public record AlterarUsuarioRequest(br.nexodesk.dominio.Perfil perfil, Boolean ativo, br.nexodesk.dominio.Funcao funcao) {
        public AlterarUsuarioRequest(br.nexodesk.dominio.Perfil perfil, Boolean ativo) {
            this(perfil, ativo, null);
        }
    }

    public record MarcarTarefaRequest(@NotNull(message = "Informe se a etapa foi feita.") Boolean feita) {
    }

    public record TarefaResponse(Long id, String texto, boolean feita) {
    }

    public record AlterarStatusRequest(
            @NotNull(message = "Informe o novo status.") StatusChamado status,
            @Size(max = 500) String observacao,
            /** Versão do chamado que a tela mostrava. Se outra pessoa já alterou, a API responde 409. Opcional. */
            Long versao) {
    }

    // ---------- saída ----------

    public record UsuarioResponse(Long id, String nome, String email, String telefone, String unidade, String perfil,
                                  String funcao) {
    }

    public record SessaoResponse(boolean autenticado, UsuarioResponse usuario) {
    }

    public record CategoriaResponse(String codigo, String rotulo) {
    }

    public record HistoricoResponse(String statusAnterior, String statusNovo, String alteradoPor,
                                    String observacao, LocalDateTime criadoEm) {
    }

    public record ChamadoResponse(
            Long id,
            String titulo,
            String descricao,
            String categoria,
            String categoriaRotulo,
            String localOcorrencia,
            boolean anonimo,
            boolean temImagem,
            boolean temImagemConclusao,
            String status,
            String statusRotulo,
            String dificuldade,
            String dificuldadeRotulo,
            int pontuacao,
            List<String> justificativa,
            boolean classificacaoFallback,
            boolean emergencia,
            int pontuacaoEfetiva,
            int diasEmEspera,
            boolean atrasado,
            String moradorNome,
            String moradorUnidade,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm,
            Long versao,
            Long responsavelId,
            String responsavelNome,
            String responsavelFuncao,
            java.time.LocalDate previsao,
            LocalDateTime prazoLimite,
            /** Minutos até o prazo (negativo = vencido); nulo se resolvido ou sem prazo. */
            Long minutosParaPrazo,
            Integer notaAvaliacao,
            String comentarioAvaliacao,
            List<TarefaResponse> tarefas,
            List<HistoricoResponse> historico) {
    }

    /** {@code autor} já vem no formato certo para quem pergunta (ex.: "Anônimo" para o síndico em chamado anônimo). */
    public record MensagemResponse(Long id, String autor, boolean daEquipe, boolean minha, String texto,
                                   LocalDateTime criadoEm) {
    }

    public record UsuarioAdminResponse(Long id, String nome, String email, String unidade, String perfil,
                                       String funcao, String funcaoRotulo, boolean ativo, LocalDateTime criadoEm) {
    }

    /** A senha temporária só aparece nesta resposta. */
    public record SenhaTemporariaResponse(String nome, String senhaTemporaria) {
    }

    /** categorias: o que a função cobre (o painel marca "Indicado" quando o chamado é de uma delas); cargaAberta: chamados não resolvidos dela. */
    public record FuncaoResponse(String codigo, String rotulo, List<String> categorias, boolean recebeEmergencias) {
    }

    public record EquipeResponse(Long id, String nome, String perfil, String funcao, String funcaoRotulo,
                                 java.util.List<String> categorias, long cargaAberta) {
    }

    public record NotificacaoResponse(Long id, Long chamadoId, String mensagem, boolean lida,
                                      LocalDateTime criadoEm) {
    }

    public record NotificacoesResponse(long naoLidas, List<NotificacaoResponse> itens) {
    }

    public record EstatisticasResponse(long abertos, long emAndamento, long resolvidos,
                                       long pendentesFaceis, long pendentesMedios, long pendentesDificeis,
                                       Double mediaAvaliacoes, long totalAvaliacoes, long emergenciasAbertas) {
    }
}
