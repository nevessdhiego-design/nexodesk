package br.nexodesk.servico;

import br.nexodesk.dominio.TokenSenha;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.TokenSenhaRepository;
import br.nexodesk.repositorio.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Esqueci minha senha" por e-mail.
 *
 * <ul>
 *   <li>O pedido <b>sempre responde igual</b>, exista ou não o e-mail (não revela quem tem conta);</li>
 *   <li>o link leva um token aleatório de 256 bits que vale {@value #VALIDADE_MINUTOS} minutos e <b>uma única vez</b>; o
 *       banco guarda só o hash SHA-256 dele; o token vai no fragmento da URL ({@code #token=...}), que o navegador não
 *       envia ao servidor nem a outros sites;</li>
 *   <li>pedir um link novo invalida os anteriores; trocar a senha derruba as sessões abertas da pessoa;</li>
 *   <li>o envio sai depois do commit e em outra thread ({@link EmailRecuperacao}).</li>
 * </ul>
 */
@Service
public class RecuperacaoSenhaService {

    static final int VALIDADE_MINUTOS = 30;
    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);

    private final UsuarioRepository usuarios;
    private final TokenSenhaRepository tokens;
    private final PasswordEncoder codificador;
    private final PublicadorEventos publicador;
    private final EmailRecuperacao email;
    private final Environment ambiente;
    private final Clock relogio;
    private final String urlPublica;
    private final SecureRandom aleatorio = new SecureRandom();

    public RecuperacaoSenhaService(UsuarioRepository usuarios, TokenSenhaRepository tokens, PasswordEncoder codificador,
                                   PublicadorEventos publicador, EmailRecuperacao email, Environment ambiente, Clock relogio,
                                   @Value("${nexodesk.url-publica:http://localhost:8080}") String urlPublica) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.codificador = codificador;
        this.publicador = publicador;
        this.email = email;
        this.ambiente = ambiente;
        this.relogio = relogio;
        this.urlPublica = urlPublica;
    }

    /** Se o e-mail pertence a uma conta ativa, gera o link e dispara o e-mail. Nunca diz se existe. */
    @Transactional
    public void solicitar(String emailInformado) {
        String endereco = UsuarioService.normalizarEmail(emailInformado);
        Usuario u = usuarios.findByEmail(endereco).filter(Usuario::isAtivo).orElse(null);
        if (u == null) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now(relogio);
        tokens.invalidarAnteriores(u, agora);
        byte[] bruto = new byte[32];
        aleatorio.nextBytes(bruto);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bruto);
        TokenSenha t = new TokenSenha();
        t.setUsuario(u);
        t.setTokenHash(hash(token));
        t.setExpiraEm(agora.plusMinutes(VALIDADE_MINUTOS));
        tokens.save(t);

        String link = urlPublica + "/redefinir-senha.html#token=" + token;
        if (ambiente.acceptsProfiles(Profiles.of("demo"))) {
            // só na demonstração, para quem apresenta ver o link sem servidor de e-mail; nunca em produção
            log.info("[demo] link de recuperação para {}: {}", EnviadorEmailSimulado.mascarar(u.getEmail()), link);
        }
        email.enviarLink(u.getEmail(), u.getNome(), link);
    }

    /** Troca a senha se o token for válido, não usado e não expirado. Qualquer outro caso: o mesmo erro genérico. */
    @Transactional
    public void redefinir(String token, String novaSenha) {
        TokenSenha t = token == null ? null : tokens.findByTokenHash(hash(token.trim())).orElse(null);
        LocalDateTime agora = LocalDateTime.now(relogio);
        if (t == null || t.getUsadoEm() != null || t.getExpiraEm().isBefore(agora)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Este link é inválido ou expirou. Peça um novo em \"Esqueci minha senha\".");
        }
        Usuario u = t.getUsuario();
        if (!u.isAtivo()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Este link é inválido ou expirou. Peça um novo em \"Esqueci minha senha\".");
        }
        u.setSenhaHash(codificador.encode(novaSenha));
        u.setVersaoSessao(u.getVersaoSessao() + 1); // derruba as sessões abertas (quem roubou a senha antiga sai)
        usuarios.save(u);
        t.setUsadoEm(agora);
        tokens.save(t);
        tokens.invalidarAnteriores(u, agora);
        publicador.encerrar(u.getEmail());
        log.info("Senha do usuário #{} redefinida pelo link de recuperação", u.getId());
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
