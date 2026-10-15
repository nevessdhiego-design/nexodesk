package br.nexodesk.servico;

import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.web.Dtos.CadastroRequest;
import br.nexodesk.web.Dtos.UsuarioResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.codificador = codificador;
    }

    public static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    /** Todo cadastro público nasce MORADOR. Síndico só é criado pelo InicializadorSindico. */
    @Transactional
    public UsuarioResponse cadastrar(CadastroRequest dados) {
        String email = normalizarEmail(dados.email());
        if (usuarios.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Este e-mail já está cadastrado.");
        }
        Usuario u = new Usuario();
        u.setNome(dados.nome().trim());
        u.setEmail(email);
        u.setTelefone(vazioParaNulo(dados.telefone()));
        u.setUnidade(vazioParaNulo(dados.unidade()));
        u.setSenhaHash(codificador.encode(dados.senha()));
        u.setPerfil(Perfil.MORADOR);
        return paraResposta(usuarios.save(u));
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorEmail(String email) {
        return usuarios.findByEmail(normalizarEmail(email))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse eu(String email) {
        return paraResposta(buscarPorEmail(email));
    }

    public static UsuarioResponse paraResposta(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getUnidade(), u.getPerfil().name());
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
