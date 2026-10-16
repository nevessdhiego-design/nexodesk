package br.nexodesk.servico;

import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.web.Dtos.AlterarUsuarioRequest;
import br.nexodesk.web.Dtos.EquipeResponse;
import br.nexodesk.web.Dtos.UsuarioAdminResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

    public UsuarioAdminService(UsuarioRepository usuarios, ChamadoRepository chamados, PublicadorEventos publicador) {
        this.usuarios = usuarios;
        this.chamados = chamados;
        this.publicador = publicador;
    }

    /** Lista para a tela de gestão. Não traz telefone nem senha: o síndico só precisa saber quem é quem. */
    @Transactional(readOnly = true)
    public List<UsuarioAdminResponse> listar() {
        return usuarios.findAllByOrderByNomeAsc().stream().map(UsuarioAdminService::paraResposta).toList();
    }

    /** Síndicos e zeladores ativos: quem pode ser escolhido como responsável por um chamado. */
    @Transactional(readOnly = true)
    public List<EquipeResponse> equipe() {
        return usuarios.findByPerfilInAndAtivoTrueOrderByNomeAsc(List.of(Perfil.SINDICO, Perfil.ZELADOR)).stream()
                .map(u -> new EquipeResponse(u.getId(), u.getNome(), u.getPerfil().name()))
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
        if (req.perfil() == null && req.ativo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe o novo perfil ou se a conta fica ativa.");
        }

        Perfil novoPerfil = req.perfil() != null ? req.perfil() : alvo.getPerfil();
        boolean novoAtivo = req.ativo() != null ? req.ativo() : alvo.isAtivo();
        if (novoPerfil == alvo.getPerfil() && novoAtivo == alvo.isAtivo()) {
            return paraResposta(alvo); // nada mudou
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

    private static UsuarioAdminResponse paraResposta(Usuario u) {
        return new UsuarioAdminResponse(u.getId(), u.getNome(), u.getEmail(), u.getUnidade(),
                u.getPerfil().name(), u.isAtivo(), u.getCriadoEm());
    }
}
