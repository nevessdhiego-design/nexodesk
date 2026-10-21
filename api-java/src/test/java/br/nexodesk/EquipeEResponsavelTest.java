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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Gestão de usuários pelo síndico, atribuição de responsável e a área restrita do zelador. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EquipeEResponsavelTest {

    static final String SENHA = "Morador-Teste-123";
    static final String SINDICO_EMAIL = "sindico.teste@nexodesk.test";
    static final String SINDICO_SENHA = "Senha-Somente-Para-Testes-1";

    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioRepository usuarios;

    // ---------- ajudantes ----------

    String cadastrarMorador() throws Exception {
        return cadastrarMorador("Pessoa Teste");
    }

    String cadastrarMorador(String nome) throws Exception {
        String email = "pessoa-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","email":"%s","unidade":"Bloco B, 202","senha":"%s"}
                                """.formatted(nome, email, SENHA)))
                .andExpect(status().isCreated());
        return email;
    }

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    MockHttpSession sindico() throws Exception {
        return entrar(SINDICO_EMAIL, SINDICO_SENHA);
    }

    long idDe(String email) {
        return usuarios.findByEmail(email).orElseThrow().getId();
    }

    void mudarPerfil(MockHttpSession sindico, String email, String perfil) throws Exception {
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(idDe(email))).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"%s\"}".formatted(perfil)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value(perfil));
    }

    /** Cria uma pessoa, o síndico a promove a zelador e devolve o e-mail dela (a sessão antiga já não vale). */
    String novoZelador(MockHttpSession sindico) throws Exception {
        String email = cadastrarMorador();
        mudarPerfil(sindico, email, "ZELADOR");
        return email;
    }

    int abrirChamado(MockHttpSession morador, String titulo, boolean anonimo) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/chamados").with(csrf())
                        .param("titulo", titulo)
                        .param("descricao", "Descrição de teste do chamado.")
                        .param("categoria", "ELETRICA")
                        .param("anonimo", String.valueOf(anonimo))
                        .session(morador))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    void atribuir(MockHttpSession sindico, int chamado, Long responsavel, LocalDate previsao) throws Exception {
        mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(chamado)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsavelId\":%s,\"previsao\":%s}".formatted(
                                responsavel, previsao == null ? "null" : "\"" + previsao + "\"")))
                .andExpect(status().isOk());
    }

    // ---------- gestão de usuários ----------

    @Test
    void sindicoPromoveMoradorAZeladorESessaoAntigaCai() throws Exception {
        MockHttpSession sindico = sindico();
        String email = cadastrarMorador();
        MockHttpSession antiga = entrar(email, SENHA);
        mvc.perform(get("/api/chamados/meus").session(antiga)).andExpect(status().isOk());

        mudarPerfil(sindico, email, "ZELADOR");

        mvc.perform(get("/api/chamados/meus").session(antiga)).andExpect(status().isUnauthorized());
        MockHttpSession nova = entrar(email, SENHA);
        mvc.perform(get("/api/zelador/chamados").session(nova)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/sessao").session(nova)).andExpect(jsonPath("$.usuario.perfil").value("ZELADOR"));
    }

    @Test
    void sindicoNaoAlteraAPropriaConta() throws Exception {
        MockHttpSession sindico = sindico();
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(idDe(SINDICO_EMAIL))).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("própria conta")));
        assertThat(usuarios.findByEmail(SINDICO_EMAIL).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void moradorEZeladorNaoGerenciamUsuarios() throws Exception {
        MockHttpSession morador = entrar(cadastrarMorador(), SENHA);
        mvc.perform(get("/api/sindico/usuarios").session(morador)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/sindico/usuarios/1").with(csrf()).session(morador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isForbidden());

        MockHttpSession admin = sindico();
        String zeladorEmail = novoZelador(admin);
        MockHttpSession zelador = entrar(zeladorEmail, SENHA);
        mvc.perform(get("/api/sindico/usuarios").session(zelador)).andExpect(status().isForbidden());
        mvc.perform(get("/api/sindico/equipe").session(zelador)).andExpect(status().isForbidden());
    }

    @Test
    void listaDeUsuariosNaoTrazSenhaNemTelefone() throws Exception {
        String email = cadastrarMorador();
        String json = mvc.perform(get("/api/sindico/usuarios").session(sindico()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> emails = JsonPath.read(json, "$[*].email");
        assertThat(emails).contains(email, SINDICO_EMAIL);
        assertThat(json).doesNotContain("senha").doesNotContain("telefone");
    }

    @Test
    void usuarioDesativadoPeloSindicoPerdeAcessoEPodeSerReativado() throws Exception {
        MockHttpSession sindico = sindico();
        String email = cadastrarMorador();
        MockHttpSession sessao = entrar(email, SENHA);

        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(idDe(email))).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(get("/api/chamados/meus").session(sessao)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(idDe(email))).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"))
                .andExpect(status().isOk());
        entrar(email, SENHA);
    }

    @Test
    void alterarSemInformarNadaOuUsuarioInexistenteDaErro() throws Exception {
        MockHttpSession sindico = sindico();
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(idDe(cadastrarMorador()))).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/sindico/usuarios/999999").with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isNotFound());
    }

    // ---------- responsável e previsão ----------

    @Test
    void atribuirAvisaMoradorEResponsavelEMostraQuemCuidaEAPrevisao() throws Exception {
        MockHttpSession sindico = sindico();
        String zeladorEmail = novoZelador(sindico);
        MockHttpSession zelador = entrar(zeladorEmail, SENHA);
        MockHttpSession morador = entrar(cadastrarMorador(), SENHA);
        int id = abrirChamado(morador, "Luz do corredor piscando", false);
        LocalDate previsao = LocalDate.now().plusDays(3);

        atribuir(sindico, id, idDe(zeladorEmail), previsao);

        mvc.perform(get("/api/chamados/%d".formatted(id)).session(morador))
                .andExpect(jsonPath("$.responsavelNome").value("Pessoa Teste"))
                .andExpect(jsonPath("$.responsavelId").value((int) idDe(zeladorEmail)))
                .andExpect(jsonPath("$.previsao").value(previsao.toString()));
        mvc.perform(get("/api/notificacoes").session(morador))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(id))
                        .value(Matchers.hasItem(Matchers.containsString("agora tem responsável"))));
        mvc.perform(get("/api/notificacoes").session(zelador))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(id))
                        .value(Matchers.hasItem(Matchers.containsString("foi atribuído a você"))));
    }

    @Test
    void removerOResponsavelAvisaQuemDeixouDeSer() throws Exception {
        MockHttpSession sindico = sindico();
        String zeladorEmail = novoZelador(sindico);
        MockHttpSession zelador = entrar(zeladorEmail, SENHA);
        MockHttpSession morador = entrar(cadastrarMorador(), SENHA);
        int id = abrirChamado(morador, "Lâmpada da escada queimada", false);
        atribuir(sindico, id, idDe(zeladorEmail), null);

        atribuir(sindico, id, null, null);

        mvc.perform(get("/api/chamados/%d".formatted(id)).session(morador))
                .andExpect(jsonPath("$.responsavelId").doesNotExist())
                .andExpect(jsonPath("$.previsao").doesNotExist());
        mvc.perform(get("/api/notificacoes").session(zelador))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(id))
                        .value(Matchers.hasItem(Matchers.containsString("deixou de ser seu"))));
        mvc.perform(get("/api/chamados/%d".formatted(id)).session(zelador)).andExpect(status().isNotFound());
    }

    @Test
    void soEquipeAtivaPodeSerResponsavelEPrevisaoTemLimites() throws Exception {
        MockHttpSession sindico = sindico();
        MockHttpSession morador = entrar(cadastrarMorador(), SENHA);
        int id = abrirChamado(morador, "Tomada solta na garagem", false);
        String outroMorador = cadastrarMorador();

        mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(id)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsavelId\":%d}".formatted(idDe(outroMorador))))
                .andExpect(status().is4xxClientError());

        String zeladorEmail = novoZelador(sindico);
        String ontem = LocalDate.now().minusDays(1).toString();
        String longe = LocalDate.now().plusYears(2).toString();
        for (String data : List.of(ontem, longe)) {
            mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(id)).with(csrf()).session(sindico)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"responsavelId\":%d,\"previsao\":\"%s\"}".formatted(idDe(zeladorEmail), data)))
                    .andExpect(status().isBadRequest());
        }

        // zelador desativado também não pode receber chamado
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(idDe(zeladorEmail))).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(id)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsavelId\":%d}".formatted(idDe(zeladorEmail))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void moradorEZeladorNaoAtribuemChamado() throws Exception {
        MockHttpSession admin = sindico();
        MockHttpSession morador = entrar(cadastrarMorador(), SENHA);
        int id = abrirChamado(morador, "Interfone mudo", false);
        String zeladorEmail = novoZelador(admin);
        String corpo = "{\"responsavelId\":%d}".formatted(idDe(zeladorEmail));

        mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(id)).with(csrf()).session(morador)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(id)).with(csrf())
                        .session(entrar(zeladorEmail, SENHA))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void atribuirComTelaDesatualizadaDa409() throws Exception {
        MockHttpSession sindico = sindico();
        MockHttpSession morador = entrar(cadastrarMorador(), SENHA);
        int id = abrirChamado(morador, "Cerca elétrica desligada", false);
        String zeladorEmail = novoZelador(sindico);
        MvcResult lista = mvc.perform(get("/api/sindico/chamados?status=todos").session(sindico)).andReturn();
        List<Integer> versoes = JsonPath.read(lista.getResponse().getContentAsString(), "$[?(@.id==%d)].versao".formatted(id));

        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_ANDAMENTO\",\"versao\":%d}".formatted(versoes.get(0))))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(id)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsavelId\":%d,\"versao\":%d}".formatted(idDe(zeladorEmail), versoes.get(0))))
                .andExpect(status().isConflict());
    }

    // ---------- área do zelador ----------

    @Test
    void zeladorSoEnxergaEMudaOsChamadosDele() throws Exception {
        MockHttpSession sindico = sindico();
        String zeladorEmail = novoZelador(sindico);
        MockHttpSession zelador = entrar(zeladorEmail, SENHA);
        MockHttpSession morador = entrar(cadastrarMorador(), SENHA);
        int dele = abrirChamado(morador, "Vazamento no hall", false);
        int dosOutros = abrirChamado(morador, "Pintura descascando", false);
        atribuir(sindico, dele, idDe(zeladorEmail), null);

        String json = mvc.perform(get("/api/zelador/chamados").session(zelador))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(json, "$[*].id");
        assertThat(ids).containsExactly(dele);

        mvc.perform(get("/api/chamados/%d".formatted(dele)).session(zelador)).andExpect(status().isOk());
        mvc.perform(get("/api/chamados/%d".formatted(dosOutros)).session(zelador)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/%d/mensagens".formatted(dosOutros)).session(zelador)).andExpect(status().isNotFound());

        mvc.perform(patch("/api/zelador/chamados/%d/status".formatted(dosOutros)).with(csrf()).session(zelador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_ANDAMENTO\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/zelador/chamados/%d/status".formatted(dele)).with(csrf()).session(zelador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_ANDAMENTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
        mvc.perform(get("/api/chamados/%d".formatted(dosOutros)).session(morador))
                .andExpect(jsonPath("$.status").value("ABERTO"));
        mvc.perform(get("/api/notificacoes").session(morador))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(dele))
                        .value(Matchers.hasItem(Matchers.containsString("andamento"))));
    }

    @Test
    void zeladorNaoUsaARotaDeStatusDoSindicoNemMoradorUsaADoZelador() throws Exception {
        MockHttpSession sindico = sindico();
        String zeladorEmail = novoZelador(sindico);
        MockHttpSession zelador = entrar(zeladorEmail, SENHA);
        MockHttpSession morador = entrar(cadastrarMorador(), SENHA);
        int id = abrirChamado(morador, "Ralo entupido", false);
        atribuir(sindico, id, idDe(zeladorEmail), null);

        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(zelador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/zelador/chamados").session(morador)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/zelador/chamados/%d/status".formatted(id)).with(csrf()).session(morador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/zelador/chamados").session(sindico)).andExpect(status().isForbidden());
    }

    @Test
    void zeladorNaoDescobreQuemAbriuChamadoAnonimoMasConversaComOMorador() throws Exception {
        MockHttpSession sindico = sindico();
        String zeladorEmail = novoZelador(sindico);
        MockHttpSession zelador = entrar(zeladorEmail, SENHA);
        MockHttpSession morador = entrar(cadastrarMorador("Marta Anonima"), SENHA);
        int id = abrirChamado(morador, "Barulho estranho no gerador", true);
        atribuir(sindico, id, idDe(zeladorEmail), null);

        String json = mvc.perform(get("/api/zelador/chamados").session(zelador)).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("Marta Anonima", "Bloco B");
        mvc.perform(get("/api/chamados/%d".formatted(id)).session(zelador))
                .andExpect(jsonPath("$.moradorNome").value("Anônimo"));

        mvc.perform(post("/api/chamados/%d/mensagens".formatted(id)).with(csrf()).session(morador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"O barulho piorou.\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/chamados/%d/mensagens".formatted(id)).session(zelador))
                .andExpect(jsonPath("$[0].autor").value("Anônimo"));
        mvc.perform(post("/api/chamados/%d/mensagens".formatted(id)).with(csrf()).session(zelador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Passo aí hoje.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autor").value(Matchers.containsString("(zelador)")));
        mvc.perform(get("/api/notificacoes").session(morador))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(id))
                        .value(Matchers.hasItem(Matchers.containsString("O zelador respondeu"))));
    }

    @Test
    void zeladorRebaixadoDeixaDeSerResponsavelEPerdeOAcesso() throws Exception {
        MockHttpSession sindico = sindico();
        String zeladorEmail = novoZelador(sindico);
        MockHttpSession zelador = entrar(zeladorEmail, SENHA);
        MockHttpSession morador = entrar(cadastrarMorador(), SENHA);
        int id = abrirChamado(morador, "Corrimão solto", false);
        atribuir(sindico, id, idDe(zeladorEmail), LocalDate.now().plusDays(1));

        mudarPerfil(sindico, zeladorEmail, "MORADOR");

        mvc.perform(get("/api/zelador/chamados").session(zelador)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/%d".formatted(id)).session(morador))
                .andExpect(jsonPath("$.responsavelId").doesNotExist());
        mvc.perform(get("/api/sindico/equipe").session(sindico))
                .andExpect(jsonPath("$[?(@.id==%d)]".formatted(idDe(zeladorEmail))).isEmpty());
    }

    @Test
    void equipeListaSoSindicosEZeladoresAtivos() throws Exception {
        MockHttpSession sindico = sindico();
        String zeladorEmail = novoZelador(sindico);
        String moradorEmail = cadastrarMorador();
        String json = mvc.perform(get("/api/sindico/equipe").session(sindico))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(json, "$[*].id");
        assertThat(ids).contains((int) idDe(zeladorEmail), (int) idDe(SINDICO_EMAIL))
                .doesNotContain((int) idDe(moradorEmail));
    }

    // ---------- "esqueci minha senha" (redefinição pelo síndico) ----------

    @Test
    void sindicoRedefineASenhaDeUmMoradorEASessaoAntigaCai() throws Exception {
        MockHttpSession sindico = sindico();
        String email = cadastrarMorador();
        MockHttpSession antiga = entrar(email, SENHA);

        String json = mvc.perform(post("/api/sindico/usuarios/%d/redefinir-senha".formatted(idDe(email)))
                        .with(csrf()).session(sindico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaTemporaria").isString())
                .andReturn().getResponse().getContentAsString();
        String temporaria = JsonPath.read(json, "$.senhaTemporaria");
        assertThat(temporaria).hasSize(12).isNotEqualTo(SENHA);

        mvc.perform(get("/api/chamados/meus").session(antiga)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isUnauthorized());
        entrar(email, temporaria);
        assertThat(usuarios.findByEmail(email).orElseThrow().getSenhaHash()).doesNotContain(temporaria);
    }

    @Test
    void redefinirSenhaTemRegrasDeProtecao() throws Exception {
        MockHttpSession sindico = sindico();
        String moradorEmail = cadastrarMorador();
        // a propria conta: usar "Minha conta"
        mvc.perform(post("/api/sindico/usuarios/%d/redefinir-senha".formatted(idDe(SINDICO_EMAIL))).with(csrf()).session(sindico))
                .andExpect(status().isBadRequest());
        // outro sindico: proibido (ninguem toma a conta de um sindico)
        String outroSindico = cadastrarMorador();
        mudarPerfil(sindico, outroSindico, "SINDICO");
        mvc.perform(post("/api/sindico/usuarios/%d/redefinir-senha".formatted(idDe(outroSindico))).with(csrf()).session(sindico))
                .andExpect(status().isForbidden());
        // conta desativada: reativar antes
        String inativo = cadastrarMorador();
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(idDe(inativo))).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/sindico/usuarios/%d/redefinir-senha".formatted(idDe(inativo))).with(csrf()).session(sindico))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/sindico/usuarios/999999/redefinir-senha").with(csrf()).session(sindico))
                .andExpect(status().isNotFound());
        // morador nao redefine a senha de ninguem; sem token CSRF tambem nao
        MockHttpSession morador = entrar(moradorEmail, SENHA);
        mvc.perform(post("/api/sindico/usuarios/%d/redefinir-senha".formatted(idDe(inativo))).with(csrf()).session(morador))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sindico/usuarios/%d/redefinir-senha".formatted(idDe(moradorEmail))).session(sindico))
                .andExpect(status().isForbidden());
    }
}
