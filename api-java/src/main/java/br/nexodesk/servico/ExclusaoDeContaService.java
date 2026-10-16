package br.nexodesk.servico;

import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.repositorio.NotificacaoRepository;
import br.nexodesk.repositorio.UsuarioRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Excluir minha conta" (direito de eliminação da LGPD, art. 18).
 *
 * <p>A linha do usuário não é apagada: os chamados e mensagens dele ficam no histórico do condomínio e precisam de um
 * dono no banco. Em vez disso os dados pessoais são <strong>anonimizados</strong>: nome, e-mail, telefone e unidade somem,
 * a senha vira um valor aleatório que ninguém conhece, a conta fica desativada e as sessões abertas caem. Os avisos
 * pessoais são apagados. O texto que a pessoa escreveu nos chamados e nas mensagens permanece (ela pode ter citado dados
 * pessoais ali: a política de privacidade avisa isso).
 */
@Service
public class ExclusaoDeContaService {

    private static final Logger log = LoggerFactory.getLogger(ExclusaoDeContaService.class);

    private final UsuarioRepository usuarios;
    private final ChamadoRepository chamados;
    private final NotificacaoRepository notificacoes;
    private final PasswordEncoder codificador;

    public ExclusaoDeContaService(UsuarioRepository usuarios, ChamadoRepository chamados,
                                  NotificacaoRepository notificacoes, PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.chamados = chamados;
        this.notificacoes = notificacoes;
        this.codificador = codificador;
    }

    /** Devolve false se a senha estiver errada (nada é alterado). */
    @Transactional
    public boolean excluir(String email, String senha) {
        Usuario u = usuarios.findByEmail(UsuarioService.normalizarEmail(email))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
        if (!codificador.matches(senha, u.getSenhaHash())) {
            return false;
        }
        if (u.getPerfil() == Perfil.SINDICO && u.isAtivo() && usuarios.countByPerfilAndAtivoTrue(Perfil.SINDICO) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Você é o único síndico ativo. Promova outra pessoa a síndico antes de excluir a sua conta.");
        }
        chamados.liberarResponsavel(u);
        notificacoes.apagarDoUsuario(u);

        u.setNome("Conta excluída");
        u.setEmail("excluido-" + u.getId() + "@nexodesk.invalid"); // único e nunca entregável; libera o e-mail antigo
        u.setTelefone(null);
        u.setUnidade(null);
        u.setSenhaHash(codificador.encode(UUID.randomUUID().toString()));
        u.setAtivo(false);
        u.setVersaoSessao(u.getVersaoSessao() + 1);
        usuarios.save(u);
        log.info("Conta #{} excluída (dados pessoais anonimizados)", u.getId());
        return true;
    }
}
