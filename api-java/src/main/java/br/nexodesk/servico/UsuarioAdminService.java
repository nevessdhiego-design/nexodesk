package br.nexodesk.servico;

import br.nexodesk.dominio.Funcao;
import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.web.Dtos.AlterarUsuarioRequest;
import br.nexodesk.web.Dtos.EquipeResponse;
import br.nexodesk.web.Dtos.SenhaTemporariaResponse;
import br.nexodesk.web.Dtos.UsuarioAdminResponse;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de usuários pelo síndico: promover (morador, zelador, síndico), desativar e reativar.
 *
 * <p>Regras que protegem o condomínio de ficar sem acesso ou de privilégio esquecido:
 * <ul>
 *   <li>ninguém altera a própria conta aqui (evita se trancar para fora);</li>
 *   <li>sempre deve existir pelo menos um síndico ativo;</li>
 *   <li>mudar o perfil ou desativar derruba as sessões abertas da pessoa (o perfil antigo ficaria valendo na sessão);</li>
 *   <li>quem sai da equipe deixa de ser responsável pelos chamados.</li>
 * </ul>
 */
@Service
public class UsuarioAdminService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioAdminService.class);

    private final UsuarioRepository usuarios;
    private final ChamadoRepository chamados;
    private final PublicadorEventos publicador;
    private final PasswordEncoder codificador;
    private final SecureRandom aleatorio = new SecureRandom();

    public UsuarioAdminService(UsuarioRepository usuarios, ChamadoRepository chamados, PublicadorEventos publicador,
                               PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.chamados = chamados;
        this.publicador = publicador;
        this.codificador = codificador;
    }

    /** Lista para a tela de gestão. Não traz telefone nem senha: o síndico só precisa saber quem é quem. */
    @Transactional(readOnly = true)
    public List<UsuarioAdminResponse> listar() {
        return usuarios.findAllByOrderByNomeAsc().stream().map(UsuarioAdminService::paraResposta).toList();
    }

    /** Síndicos e zeladores ativos: quem pode ser escolhido como responsável por um chamado. */
    @Transactional(readOnly = true)
    public List<EquipeResponse> equipe() {
        Map<Long, Long> carga = new HashMap<>();
        for (Object[] linha : chamados.cargaPorResponsavel(br.nexodesk.dominio.StatusChamado.RESOLVIDO)) {
            carga.put((Long) linha[0], (Long) linha[1]);
        }
        return usuarios.findByPerfilInAndAtivoTrueOrderByNomeAsc(List.of(Perfil.SINDICO, Perfil.FUNCIONARIO)).stream()
                .map(u -> new EquipeResponse(u.getId(), u.getNome(), u.getPerfil().name(),
                        u.getFuncao() == null ? null : u.getFuncao().name(),
                        u.getFuncao() == null ? null : u.getFuncao().getRotulo(),
                        u.getFuncao() == null ? List.<String>of()
                                : u.getFuncao().getCategorias().stream().map(Enum::name).toList(),
                        carga.getOrDefault(u.getId(), 0L)))
                .toList();
    }

    @Transactional
    public UsuarioAdminResponse alterar(String emailDoAutor, Long id, AlterarUsuarioRequest req) {
        Usuario autor = usuarios.findByEmail(UsuarioService.normalizarEmail(emailDoAutor))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
        Usuario alvo = usuarios.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        if (alvo.getId().equals(autor.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Você não pode alterar a sua própria conta aqui. Peça a outro síndico.");
        }
        if (req.perfil() == null && req.ativo() == null && req.funcao() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe o novo perfil, a função ou se a conta fica ativa.");
        }

        Perfil novoPerfil = req.perfil() != null ? req.perfil() : alvo.getPerfil();
        boolean novoAtivo = req.ativo() != null ? req.ativo() : alvo.isAtivo();
        if (req.funcao() != null && novoPerfil != Perfil.FUNCIONARIO) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A função só vale para funcionários.");
        }
        Funcao novaFuncao = novoPerfil != Perfil.FUNCIONARIO ? null
                : req.funcao() != null ? req.funcao()
                : alvo.getFuncao() != null ? alvo.getFuncao() : Funcao.ZELADOR;
        if (novoPerfil == alvo.getPerfil() && novoAtivo == alvo.isAtivo()) {
            if (!Objects.equals(novaFuncao, alvo.getFuncao())) {
                alvo.setFuncao(novaFuncao); // mudar só a função não muda o que a pessoa pode fazer: não derruba a sessão
                usuarios.save(alvo);
            }
            return paraResposta(alvo);
        }

        boolean eraSindicoAtivo = alvo.getPerfil() == Perfil.SINDICO && alvo.isAtivo();
        boolean seguiraSindicoAtivo = novoPerfil == Perfil.SINDICO && novoAtivo;
        if (eraSindicoAtivo && !seguiraSindicoAtivo && usuarios.countByPerfilAndAtivoTrue(Perfil.SINDICO) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Precisa existir pelo menos um síndico ativo.");
        }

        boolean eraEquipe = alvo.getPerfil() != Perfil.MORADOR;
        boolean saiuDaEquipe = eraEquipe && (novoPerfil == Perfil.MORADOR || !novoAtivo);

        Perfil perfilAnterior = alvo.getPerfil();
        boolean estavaAtivo = alvo.isAtivo();
        alvo.setPerfil(novoPerfil);
        alvo.setFuncao(novaFuncao);
        alvo.setAtivo(novoAtivo);
        alvo.setVersaoSessao(alvo.getVersaoSessao() + 1); // derruba as sessões abertas com o perfil/situação antigos
        usuarios.save(alvo);
        if (saiuDaEquipe) {
            chamados.liberarResponsavel(alvo);
        }
        publicador.encerrar(alvo.getEmail()); // fecha os fluxos de tempo real da sessão derrubada

        // trilha mínima (só ids, sem e-mail): quem mudou o quê
        log.info("Usuário #{} alterado por #{}: perfil {} -> {}, ativo {} -> {}",
                alvo.getId(), autor.getId(), perfilAnterior, novoPerfil, estavaAtivo, novoAtivo);
        return paraResposta(alvo);
    }

    /**
     * "Esqueci minha senha" sem e-mail: o síndico gera uma senha temporária para um morador ou zelador e a entrega pessoalmente.
     * A senha aparece UMA vez na resposta (nunca é registrada em log), as sessões abertas da pessoa caem e ela deve trocá-la em
     * "Minha conta". Não vale para síndico (um síndico não pode tomar a conta de outro) nem para a própria conta.
     */
    @Transactional
    public SenhaTemporariaResponse redefinirSenha(String emailDoAutor, Long id) {
        Usuario autor = usuarios.findByEmail(UsuarioService.normalizarEmail(emailDoAutor))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
        Usuario alvo = usuarios.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        if (alvo.getId().equals(autor.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Para trocar a sua própria senha use \"Minha conta\".");
        }
        if (alvo.getPerfil() == Perfil.SINDICO) {
            throw new ApiException(HttpStatus.FORBIDDEN, "A senha de outro síndico não pode ser redefinida por aqui.");
        }
        if (!alvo.isAtivo()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reative a conta antes de redefinir a senha.");
        }
        String temporaria = gerarSenhaTemporaria();
        alvo.setSenhaHash(codificador.encode(temporaria));
        alvo.setVersaoSessao(alvo.getVersaoSessao() + 1);
        usuarios.save(alvo);
        publicador.encerrar(alvo.getEmail());
        log.info("Senha do usuário #{} redefinida pelo síndico #{}", alvo.getId(), autor.getId());
        return new SenhaTemporariaResponse(alvo.getNome(), temporaria);
    }

    /** 12 caracteres sem símbolos que se confundem (0/O, 1/l/I), fáceis de ditar. */
    private String gerarSenhaTemporaria() {
        String alfabeto = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(alfabeto.charAt(aleatorio.nextInt(alfabeto.length())));
        }
        return sb.toString();
    }

    private static UsuarioAdminResponse paraResposta(Usuario u) {
        return new UsuarioAdminResponse(u.getId(), u.getNome(), u.getEmail(), u.getUnidade(),
                u.getPerfil().name(), u.getFuncao() == null ? null : u.getFuncao().name(),
                u.getFuncao() == null ? null : u.getFuncao().getRotulo(), u.isAtivo(), u.getCriadoEm());
    }
}
