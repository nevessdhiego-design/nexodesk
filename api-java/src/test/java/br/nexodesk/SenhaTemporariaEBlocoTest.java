package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.nexodesk.repositorio.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Troca obrigatória depois de senha temporária e cadastro com bloco e apartamento. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SenhaTemporariaEBlocoTest {

    static final String SENHA = "Morador-Teste-123";
    static final String NOVA = "Senha-Nova-Teste-456";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    String cadastrar(String complemento) throws Exception {
        String email = "tmp-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa Troca\",\"email\":\"%s\",\"senha\":\"%s\"%s}".formatted(email, SENHA, complemento)))
                .andExpect(status().isCreated());
        return email;
    }

    MockHttpSession sindico() throws Exception {
        return entrar("sindico.teste@nexodesk.test", "Senha-Somente-Para-Testes-1");
    }

    @Test
    void comSenhaTemporariaSoDaParaTrocarASenhaEDepoisTudoVoltaAoNormal() throws Exception {
        String email = cadastrar("");
        String temporaria = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/sindico/usuarios/%d/redefinir-senha".formatted(usuarios.findByEmail(email).orElseThrow().getId()))
                                .with(csrf()).session(sindico())).andReturn().getResponse().getContentAsString(), "$.senhaTemporaria");
        MockHttpSession sessao = entrar(email, temporaria);

        // quem sou eu continua funcionando e avisa que precisa trocar
        mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.trocarSenha").value(true));
        // o resto da API está travado, com um aviso que o navegador entende
        mvc.perform(get("/api/chamados/meus").session(sessao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.trocarSenha").value(true));
        mvc.perform(post("/api/chamados/1/mensagens").with(csrf()).session(sessao).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/notificacoes").session(sessao)).andExpect(status().isForbidden());
        // as páginas (HTML) continuam abrindo: é a tela de conta que mostra o aviso
        mvc.perform(get("/conta.html")).andExpect(status().isOk());

        // trocar a senha libera
        mvc.perform(post("/api/auth/senha").with(csrf()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"%s\",\"novaSenha\":\"%s\"}".formatted(temporaria, NOVA)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/chamados/meus").session(sessao)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(sessao)).andExpect(jsonPath("$.trocarSenha").value(false));
        assertThat(usuarios.findByEmail(email).orElseThrow().isSenhaTemporaria()).isFalse();
    }

    @Test
    void senhaTemporariaTambemPodeSair() throws Exception {
        String email = cadastrar("");
        String temporaria = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/sindico/usuarios/%d/redefinir-senha".formatted(usuarios.findByEmail(email).orElseThrow().getId()))
                                .with(csrf()).session(sindico())).andReturn().getResponse().getContentAsString(), "$.senhaTemporaria");
        MockHttpSession sessao = entrar(email, temporaria);
        mvc.perform(post("/api/auth/logout").with(csrf()).session(sessao)).andExpect(status().isNoContent());
    }

    @Test
    void cadastroComBlocoEApartamentoMontaAUnidadeEOSindicoVeOBlocoMasNaoNoChamadoAnonimo() throws Exception {
        String email = cadastrar(",\"bloco\":\"C\",\"apartamento\":\"305\"");
        MockHttpSession morador = entrar(email, SENHA);
        mvc.perform(get("/api/auth/me").session(morador))
                .andExpect(jsonPath("$.bloco").value("C"))
                .andExpect(jsonPath("$.apartamento").value("305"))
                .andExpect(jsonPath("$.unidade").value("Bloco C, 305"));

        // a conta antiga, só com texto livre, continua valendo
        String antigo = cadastrar(",\"unidade\":\"Bloco Z, 9\"");
        mvc.perform(get("/api/auth/me").session(entrar(antigo, SENHA)))
                .andExpect(jsonPath("$.unidade").value("Bloco Z, 9")).andExpect(jsonPath("$.bloco").doesNotExist());

        // atualizar pela tela de conta
        mvc.perform(patch("/api/auth/me").with(csrf()).session(morador).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa Troca\",\"bloco\":\"D\",\"apartamento\":\"12\"}"))
                .andExpect(jsonPath("$.unidade").value("Bloco D, 12"));

        MockHttpSession sindico = sindico();
        int aberto = abrir(morador, false);
        int anonimo = abrir(morador, true);
        String fila = mvc.perform(get("/api/sindico/chamados?status=todos").session(sindico)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(fila, "$[?(@.id==%d)].moradorBloco".formatted(aberto))).containsExactly("D");
        assertThat((List<Object>) JsonPath.read(fila, "$[?(@.id==%d)].moradorBloco".formatted(anonimo))).containsOnlyNulls();
    }

    int abrir(MockHttpSession morador, boolean anonimo) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/chamados").with(csrf()).param("titulo", "Chamado de bloco").param("descricao", "Descrição do teste.")
                        .param("categoria", "OUTROS").param("anonimo", String.valueOf(anonimo)).session(morador))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }
}
