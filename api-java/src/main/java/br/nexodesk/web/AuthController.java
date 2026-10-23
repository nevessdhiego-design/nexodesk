package br.nexodesk.web;

import br.nexodesk.config.FiltroSessaoValida;
import br.nexodesk.config.LimitadorTentativasLogin;
import br.nexodesk.servico.ApiException;
import br.nexodesk.servico.ExclusaoDeContaService;
import br.nexodesk.servico.RecuperacaoSenhaService;
import br.nexodesk.web.Dtos.EsqueciSenhaRequest;
import br.nexodesk.web.Dtos.RedefinirSenhaRequest;
import br.nexodesk.servico.PublicadorEventos;
import br.nexodesk.servico.UsuarioService;
import br.nexodesk.web.Dtos.AlterarSenhaRequest;
import br.nexodesk.web.Dtos.AtualizarPerfilRequest;
import br.nexodesk.web.Dtos.CadastroRequest;
import br.nexodesk.web.Dtos.ExcluirContaRequest;
import br.nexodesk.web.Dtos.LoginRequest;
import br.nexodesk.web.Dtos.SessaoResponse;
import br.nexodesk.web.Dtos.UsuarioResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final LimitadorTentativasLogin limitador;
    private final PublicadorEventos publicador;
    private final ExclusaoDeContaService exclusao;
    private final RecuperacaoSenhaService recuperacao;

    public AuthController(UsuarioService usuarios, AuthenticationManager gerenteAutenticacao,
                          SecurityContextRepository repositorioContexto, LimitadorTentativasLogin limitador,
                          PublicadorEventos publicador, ExclusaoDeContaService exclusao,
                          RecuperacaoSenhaService recuperacao) {
        this.usuarios = usuarios;
        this.gerenteAutenticacao = gerenteAutenticacao;
        this.repositorioContexto = repositorioContexto;
        this.limitador = limitador;
        this.publicador = publicador;
        this.exclusao = exclusao;
        this.recuperacao = recuperacao;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse cadastrar(@Valid @RequestBody CadastroRequest dados) {
        return usuarios.cadastrar(dados);
    }

    @PostMapping("/login")
    public UsuarioResponse entrar(@Valid @RequestBody LoginRequest dados,
                                  HttpServletRequest request, HttpServletResponse response) {
        String email = dados.email().trim().toLowerCase();
        String ip = request.getRemoteAddr();
        limitador.verificar(ip, email);
        Authentication autenticacao;
        try {
            autenticacao = gerenteAutenticacao.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, dados.senha()));
        } catch (AuthenticationException e) {
            limitador.registrarFalha(ip, email);
            // mesma mensagem para e-mail inexistente e senha errada (não revela qual dos dois)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        limitador.registrarSucesso(ip, email);
        // evita fixação de sessão: troca o id de uma sessão já existente antes de autenticar
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        repositorioContexto.saveContext(contexto, request, response);
        // a sessão guarda a versão do login; trocar a senha ou desativar a conta a invalida (FiltroSessaoValida)
        request.getSession().setAttribute(FiltroSessaoValida.ATRIBUTO_VERSAO, usuarios.versaoSessao(autenticacao.getName()));
        return usuarios.eu(autenticacao.getName());
    }

    /** Público: diz se há sessão sem gerar erro 401 (usado pela tela de login). */
    @GetMapping("/sessao")
    public SessaoResponse sessao(Authentication autenticacao) {
        if (autenticacao == null || autenticacao instanceof AnonymousAuthenticationToken) {
            return new SessaoResponse(false, null);
        }
        return new SessaoResponse(true, usuarios.eu(autenticacao.getName()));
    }

    @GetMapping("/me")
    public UsuarioResponse eu(Authentication autenticacao) {
        return usuarios.eu(autenticacao.getName());
    }

    /** "Minha conta": nome, telefone e unidade. E-mail e perfil não mudam por aqui. */
    @PatchMapping("/me")
    public UsuarioResponse atualizarPerfil(@Valid @RequestBody AtualizarPerfilRequest dados,
                                           Authentication autenticacao) {
        return usuarios.atualizarPerfil(autenticacao.getName(), dados);
    }

    /**
     * Troca de senha. Errar a senha atual conta como falha de login (mesmo limitador), senão uma
     * sessão sequestrada poderia tentar adivinhar a senha sem limite.
     */
    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@Valid @RequestBody AlterarSenhaRequest dados, Authentication autenticacao,
                             HttpServletRequest request) {
        String email = autenticacao.getName();
        String ip = request.getRemoteAddr();
        limitador.verificar(ip, email);
        Integer novaVersao = usuarios.alterarSenha(email, dados);
        if (novaVersao == null) {
            limitador.registrarFalha(ip, email);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Senha atual incorreta.");
        }
        limitador.registrarSucesso(ip, email);
        // quem trocou a senha continua logado; as OUTRAS sessões (com a versão antiga) deixam de valer
        request.getSession().setAttribute(FiltroSessaoValida.ATRIBUTO_VERSAO, novaVersao);
        // as outras sessões deixaram de valer: fecha os fluxos de tempo real delas (o navegador atual reconecta sozinho)
        publicador.encerrar(email);
    }

    /**
     * "Excluir minha conta" (LGPD): apaga os dados pessoais da conta e mantém os chamados sem identificação. Exige a senha
     * atual (errar conta como falha de login) e encerra a sessão. O único síndico ativo não pode sair sem passar o cargo.
     */
    @PostMapping("/excluir-conta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirConta(@Valid @RequestBody ExcluirContaRequest dados, Authentication autenticacao,
                             HttpServletRequest request) {
        String email = autenticacao.getName();
        String ip = request.getRemoteAddr();
        limitador.verificar(ip, email);
        if (!exclusao.excluir(email, dados.senha())) {
            limitador.registrarFalha(ip, email);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Senha incorreta.");
        }
        limitador.registrarSucesso(ip, email);
        publicador.encerrar(email);
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
    }

    /**
     * "Esqueci minha senha": a resposta é sempre a mesma, exista ou não a conta. O freio conta cada pedido (5 por e-mail e IP
     * a cada 15 min) para ninguém usar o sistema para encher a caixa de e-mail de outra pessoa.
     */
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public java.util.Map<String, String> esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest dados, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String chave = "recuperacao:" + dados.email().trim().toLowerCase();
        limitador.verificar(ip, chave);
        limitador.registrarFalha(ip, chave);
        recuperacao.solicitar(dados.email());
        return java.util.Map.of("mensagem", "Se existir uma conta com esse e-mail, enviamos um link para redefinir a senha. Ele vale por 30 minutos.");
    }

    /** Usa o token do link: quem erra o token também conta no freio (não dá para adivinhar tokens sem limite). */
    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest dados, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        limitador.verificar(ip, "token");
        try {
            recuperacao.redefinir(dados.token(), dados.novaSenha());
        } catch (ApiException e) {
            limitador.registrarFalha(ip, "token");
            throw e;
        }
    }
}
