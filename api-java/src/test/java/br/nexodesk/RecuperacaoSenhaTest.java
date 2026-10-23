package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.servico.EnviadorEmailSimulado;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** "Esqueci minha senha" por e-mail: resposta igual para qualquer e-mail, token de uso único, expira, derruba sessões. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecuperacaoSenhaTest {

    static final String SENHA = "Morador-Teste-123";
    static final String NOVA = "Senha-Nova-Teste-456";
    static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    @Autowired EnviadorEmailSimulado emails;

    String cadastrar() throws Exception {
        String email = "rec-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa Recupera\",\"email\":\"%s\",\"unidade\":\"B2\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isCreated());
        return email;
    }

    static RequestPostProcessor ip(String endereco) {
        return r -> {
            r.setRemoteAddr(endereco);
            return r;
        };
    }

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    void pedir(String email, String ip) throws Exception {
        mvc.perform(post("/api/auth/esqueci-senha").with(csrf()).with(ip(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
    }

    /** O e-mail sai em outra thread: espera chegar e devolve o token que está no link. */
    String tokenDoEmail(String email) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> emails.enviados().stream().anyMatch(e -> e.para().equals(email)));
        List<EnviadorEmailSimulado.Simulado> dele = emails.enviados().stream().filter(e -> e.para().equals(email)).toList();
        Matcher m = TOKEN.matcher(dele.get(dele.size() - 1).texto());
        assertThat(m.find()).as("link com token no e-mail").isTrue();
        return m.group(1);
    }

    int redefinir(String token, String senha) throws Exception {
        return mvc.perform(post("/api/auth/redefinir-senha").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"novaSenha\":\"%s\"}".formatted(token, senha)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void fluxoCompletoTrocaASenhaDerrubaSessaoETokenSoServeUmaVez() throws Exception {
        String email = cadastrar();
        MockHttpSession antiga = entrar(email, SENHA);
        pedir(email, "10.0.1.1");
        String token = tokenDoEmail(email);

        // o banco guarda só o hash, nunca o token
        Integer comTokenPuro = jdbc.queryForObject("SELECT COUNT(*) FROM tokens_senha WHERE token_hash = ?", Integer.class, token);
        assertThat(comTokenPuro).isZero();

        assertThat(redefinir(token, NOVA)).isEqualTo(204);
        mvc.perform(get("/api/chamados/meus").session(antiga)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA))).andExpect(status().isUnauthorized());
        entrar(email, NOVA);

        assertThat(redefinir(token, "Outra-Senha-789")).as("o link só vale uma vez").isEqualTo(400);
        entrar(email, NOVA); // a senha não mudou de novo
    }

    @Test
    void respostaEIgualParaEmailInexistenteEContaDesativadaENaoEnviaNada() throws Exception {
        long antes = emails.enviados().size();
        pedir("ninguem-" + UUID.randomUUID() + "@nexodesk.test", "10.0.2.1");
        String desativado = cadastrar();
        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE email = ?", desativado);
        pedir(desativado, "10.0.2.2");
        Thread.sleep(400);
        assertThat(emails.enviados().size()).isEqualTo(antes);
        // a mensagem é a mesma de quando a conta existe
        String existente = cadastrar();
        String corpoExistente = mvc.perform(post("/api/auth/esqueci-senha").with(csrf()).with(ip("10.0.2.3")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(existente))).andReturn().getResponse().getContentAsString();
        String corpoInexistente = mvc.perform(post("/api/auth/esqueci-senha").with(csrf()).with(ip("10.0.2.4")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted("x-" + UUID.randomUUID() + "@nexodesk.test"))).andReturn().getResponse().getContentAsString();
        assertThat(corpoExistente).isEqualTo(corpoInexistente);
    }

    @Test
    void tokenExpiradoInventadoOuSenhaFracaSaoRecusados() throws Exception {
        String email = cadastrar();
        pedir(email, "10.0.3.1");
        String token = tokenDoEmail(email);

        assertThat(redefinir("inventado-1234567890", NOVA)).isEqualTo(400);
        assertThat(redefinir(token, "curta")).as("senha fraca").isEqualTo(400);
        jdbc.update("UPDATE tokens_senha SET expira_em = ?", Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)));
        assertThat(redefinir(token, NOVA)).as("link expirado").isEqualTo(400);
        entrar(email, SENHA); // nada mudou
    }

    @Test
    void pedirUmLinkNovoInvalidaOAnterior() throws Exception {
        String email = cadastrar();
        pedir(email, "10.0.4.1");
        String primeiro = tokenDoEmail(email);
        long enviados = emails.enviados().stream().filter(e -> e.para().equals(email)).count();
        pedir(email, "10.0.4.1");
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> emails.enviados().stream().filter(e -> e.para().equals(email)).count() > enviados);
        String segundo = tokenDoEmail(email);

        assertThat(segundo).isNotEqualTo(primeiro);
        assertThat(redefinir(primeiro, NOVA)).as("link antigo").isEqualTo(400);
        assertThat(redefinir(segundo, NOVA)).isEqualTo(204);
    }

    @Test
    void pedidosDemaisDoMesmoEmailEIpSaoFreados() throws Exception {
        String email = cadastrar();
        for (int i = 0; i < 5; i++) {
            pedir(email, "10.0.5.1");
        }
        mvc.perform(post("/api/auth/esqueci-senha").with(csrf()).with(ip("10.0.5.1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/esqueci-senha").with(csrf()).with(ip("10.0.5.1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.email").exists());
    }
}
