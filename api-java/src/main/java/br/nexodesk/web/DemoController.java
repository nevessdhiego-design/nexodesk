package br.nexodesk.web;

import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.UsuarioRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Só existe no perfil {@code demo}. Lista as contas de exemplo (nome, função e e-mail) para a tela de login oferecer
 * "Entrar como..." na apresentação. <b>Nunca devolve senha</b>: a senha das contas de demonstração é a que o grupo
 * definiu em NEXODESK_DEMO_SENHA, e quem apresenta a digita.
 */
@RestController
@Profile("demo")
@RequestMapping("/api/demo")
public class DemoController {

    public record ContaDemo(String rotulo, String email) {
    }

    private final UsuarioRepository usuarios;

    public DemoController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/contas")
    public List<ContaDemo> contas() {
        return usuarios.findAllByOrderByNomeAsc().stream()
                .filter(Usuario::isAtivo)
                .filter(u -> u.getEmail().endsWith("@nexodesk.local"))
                .map(u -> new ContaDemo(u.getNome().equalsIgnoreCase(rotulo(u)) ? u.getNome() : u.getNome() + " · " + rotulo(u), u.getEmail()))
                .toList();
    }

    private static String rotulo(Usuario u) {
        return switch (u.getPerfil()) {
            case SINDICO -> "Síndico";
            case MORADOR -> "Morador";
            case FUNCIONARIO -> u.getFuncao() == null ? "Funcionário" : u.getFuncao().getRotulo();
        };
    }
}
