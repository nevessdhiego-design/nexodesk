package br.nexodesk.config;

import br.nexodesk.repositorio.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Derruba a sessão que não vale mais. Ao entrar, a sessão guarda a "versão de sessão" do usuário; a cada
 * requisição autenticada comparamos com a do banco. Se o usuário trocou a senha ou foi desativado, a versão
 * mudou (ou o usuário sumiu) e a sessão antiga recebe 401. Sem isso, uma sessão roubada continuaria
 * valendo mesmo depois de a pessoa trocar a senha.
 *
 * <p>Custo: uma consulta por chave primária por requisição. Não é um {@code @Component} de propósito: o
 * Spring Boot registraria o filtro também fora da cadeia de segurança e ele rodaria antes do login.
 */
public class FiltroSessaoValida extends OncePerRequestFilter {

    public static final String ATRIBUTO_VERSAO = "nexodesk.versaoSessao";

    private final UsuarioRepository usuarios;

    public FiltroSessaoValida(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        HttpSession sessao = request.getSession(false);
        if (sessao != null && autenticacao != null && autenticacao.isAuthenticated()
                && !(autenticacao instanceof AnonymousAuthenticationToken)) {
            Object guardada = sessao.getAttribute(ATRIBUTO_VERSAO);
            boolean valida = guardada instanceof Integer versao
                    && usuarios.situacaoDaSessao(autenticacao.getName())
                            .map(s -> s.getAtivo() && s.getVersaoSessao() == versao)
                            .orElse(false);
            if (!valida) {
                SecurityContextHolder.clearContext();
                sessao.invalidate();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getOutputStream().write(
                        "{\"erro\":\"Sessão encerrada. Entre novamente.\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
