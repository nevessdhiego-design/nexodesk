package br.nexodesk.config;

import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.servico.UsuarioService;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro síndico na primeira execução. A senha vem de nexodesk.sindico.senha
 * (variável NEXODESK_SINDICO_SENHA); se estiver vazia, gera uma aleatória e mostra uma única
 * vez no console. Nenhuma senha fica gravada no código ou no repositório.
 */
@Component
public class InicializadorSindico implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorSindico.class);
    private static final String ALFABETO = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final String nome;
    private final String email;
    private final String senhaConfigurada;

    public InicializadorSindico(UsuarioRepository usuarios, PasswordEncoder codificador,
                                @Value("${nexodesk.sindico.nome}") String nome,
                                @Value("${nexodesk.sindico.email}") String email,
                                @Value("${nexodesk.sindico.senha:}") String senhaConfigurada) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.nome = nome;
        this.email = email;
        this.senhaConfigurada = senhaConfigurada;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarios.existsByPerfil(Perfil.SINDICO)) {
            return;
        }
        boolean gerada = senhaConfigurada == null || senhaConfigurada.isBlank();
        String senha = gerada ? gerarSenha() : senhaConfigurada;

        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(UsuarioService.normalizarEmail(email));
        u.setSenhaHash(codificador.encode(senha));
        u.setPerfil(Perfil.SINDICO);
        usuarios.save(u);

        if (gerada) {
            log.warn("Síndico criado: {} | senha inicial (aparece só agora, anote): {}", u.getEmail(), senha);
        } else {
            log.info("Síndico criado: {} (senha definida na configuração)", u.getEmail());
        }
    }

    private static String gerarSenha() {
        SecureRandom aleatorio = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
