package br.nexodesk.web;

import br.nexodesk.servico.ApiException;
import br.nexodesk.servico.UsuarioService;
import br.nexodesk.web.Dtos.CadastroRequest;
import br.nexodesk.web.Dtos.LoginRequest;
import br.nexodesk.web.Dtos.UsuarioResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioService usuarios;
    private final AuthenticationManager gerenteAutenticacao;
    private final SecurityContextRepository repositorioContexto;

    public AuthController(UsuarioService usuarios, AuthenticationManager gerenteAutenticacao,
                          SecurityContextRepository repositorioContexto) {
        this.usuarios = usuarios;
        this.gerenteAutenticacao = gerenteAutenticacao;
        this.repositorioContexto = repositorioContexto;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse cadastrar(@Valid @RequestBody CadastroRequest dados) {
        return usuarios.cadastrar(dados);
    }

    @PostMapping("/login")
    public UsuarioResponse entrar(@Valid @RequestBody LoginRequest dados,
                                  HttpServletRequest request, HttpServletResponse response) {
        Authentication autenticacao;
        try {
            autenticacao = gerenteAutenticacao.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(dados.email().trim().toLowerCase(), dados.senha()));
        } catch (AuthenticationException e) {
            // mesma mensagem para e-mail inexistente e senha errada (não revela qual dos dois)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        // evita fixação de sessão: troca o id de uma sessão já existente antes de autenticar
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        repositorioContexto.saveContext(contexto, request, response);
        return usuarios.eu(autenticacao.getName());
    }

    @GetMapping("/me")
    public UsuarioResponse eu(Authentication autenticacao) {
        return usuarios.eu(autenticacao.getName());
    }
}
