package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.nexodesk.repositorio.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Fluxo completo pela API: cadastro, login, chamado, fila do síndico, status e notificação. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FluxoChamadoTest {

    static final String SENHA = "Morador-Teste-123";
    static final String SINDICO_EMAIL = "sindico.teste@nexodesk.test";
    static final String SINDICO_SENHA = "Senha-Somente-Para-Testes-1";

    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioRepository usuarios;

    // ---------- ajudantes ----------

    String novoEmail() {
        return "morador-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
    }

    void cadastrar(String email) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Morador Teste","email":"%s","unidade":"Bloco A, 101","senha":"%s"}
                                """.formatted(email, SENHA)))
                .andExpect(status().isCreated());
    }

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    MockHttpSession moradorLogado() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        return entrar(email, SENHA);
    }

    MockHttpSession sindicoLogado() throws Exception {
        return entrar(SINDICO_EMAIL, SINDICO_SENHA);
    }

    MockMultipartHttpServletRequestBuilder formChamado(String titulo, String categoria, boolean anonimo) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/chamados").with(csrf())
                .param("titulo", titulo)
                .param("descricao", "Descrição de teste do chamado.")
                .param("categoria", categoria)
                .param("anonimo", String.valueOf(anonimo));
    }

    int abrirChamado(MockHttpSession morador, String titulo, String categoria, boolean anonimo) throws Exception {
        MvcResult r = mvc.perform(formChamado(titulo, categoria, anonimo).session(morador))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    // ---------- cadastro e login ----------

    @Test
    void cadastroDuplicadoDa409() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email.toUpperCase(), SENHA)))
                .andExpect(status().isConflict());
    }

    @Test
    void cadastroInvalidoDa400ComCampos() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"email\":\"nao-e-email\",\"senha\":\"curta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.email").exists())
                .andExpect(jsonPath("$.campos.senha").exists());
    }

    @Test
    void senhaFicaGuardadaComBcryptENuncaEmTextoPuro() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        String hash = usuarios.findByEmail(email).orElseThrow().getSenhaHash();
        assertThat(hash).startsWith("$2").isNotEqualTo(SENHA).doesNotContain(SENHA);
    }

    @Test
    void cadastroPublicoSempreCriaMoradorMesmoSeTentarEnviarPerfil() throws Exception {
        String email = novoEmail();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Esperto\",\"email\":\"%s\",\"senha\":\"%s\",\"perfil\":\"SINDICO\"}"
                                .formatted(email, SENHA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("MORADOR"));
    }

    @Test
    void loginComSenhaErradaDa401ComMensagemGenerica() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"errada-errada\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha incorretos."));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"naoexiste@nexodesk.test\",\"senha\":\"qualquer-coisa\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha incorretos."));
    }

    @Test
    void sessaoPublicaDizSeHaLoginSemGerar401() throws Exception {
        mvc.perform(get("/api/auth/sessao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(false));
        mvc.perform(get("/api/auth/sessao").session(moradorLogado()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.usuario.perfil").value("MORADOR"));
    }

    @Test
    void cincoSenhasErradasBloqueiamOLoginMesmoComASenhaCerta() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(csrf()).with(ip("10.0.0.7"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"senha\":\"errada-errada-%d\"}".formatted(email, i)))
                    .andExpect(status().isUnauthorized());
        }
        // 6ª tentativa, agora com a senha CERTA: continua bloqueada
        mvc.perform(post("/api/auth/login").with(csrf()).with(ip("10.0.0.7"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("Tente novamente")));
        // o dono, de outro IP, entra normalmente: o atacante não consegue trancar a conta da vítima
        mvc.perform(post("/api/auth/login").with(csrf()).with(ip("10.0.0.8"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isOk());
    }

    static org.springframework.test.web.servlet.request.RequestPostProcessor ip(String endereco) {
        return requisicao -> {
            requisicao.setRemoteAddr(endereco);
            return requisicao;
        };
    }

    @Test
    void semLoginAApiDa401() throws Exception {
        mvc.perform(get("/api/chamados/meus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sindico/chamados")).andExpect(status().isUnauthorized());
    }

    @Test
    void postSemTokenCsrfDa403() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"email\":\"a@b.com\",\"senha\":\"12345678\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- RF01 + RF02 ----------

    @Test
    void chamadoNasceAbertoEClassificadoPorDificuldade() throws Exception {
        MockHttpSession morador = moradorLogado();
        mvc.perform(formChamado("Lâmpada queimada no corredor", "ILUMINACAO", false).session(morador))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.dificuldade").value("FACIL"))
                // serviço Python fora do ar nos testes: o Java usa a pontuação base e avisa
                .andExpect(jsonPath("$.classificacaoFallback").value(true));
        mvc.perform(formChamado("Elevador parado", "ELEVADOR", false).session(morador))
                .andExpect(jsonPath("$.dificuldade").value("DIFICIL"));
        mvc.perform(formChamado("Cano pingando", "HIDRAULICA", false).session(morador))
                .andExpect(jsonPath("$.dificuldade").value("MEDIA"));
    }

    @Test
    void chamadoInvalidoDa400() throws Exception {
        MockHttpSession morador = moradorLogado();
        mvc.perform(formChamado("   ", "ILUMINACAO", false).session(morador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.titulo").exists());
        mvc.perform(formChamado("Titulo", "CATEGORIA_QUE_NAO_EXISTE", false).session(morador))
                .andExpect(status().isBadRequest());
    }

    @Test
    void campoAnonimoAusenteVaiParaFalso() throws Exception {
        MockHttpSession morador = moradorLogado();
        mvc.perform(multipart("/api/chamados").with(csrf()).session(morador)
                        .param("titulo", "Sem campo anonimo").param("descricao", "d").param("categoria", "OUTROS"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anonimo").value(false));
    }

    @Test
    void erroDeConversaoNaoVazaTextoInternoDoSpring() throws Exception {
        MockHttpSession morador = moradorLogado();
        mvc.perform(formChamado("Titulo", "CATEGORIA_QUE_NAO_EXISTE", false).session(morador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Valor inválido para o campo 'categoria'."));
    }

    // ---------- RF03 ----------

    @Test
    void filaDoSindicoVemDoMaisFacilAoMaisDificil() throws Exception {
        MockHttpSession morador = moradorLogado();
        int elevador = abrirChamado(morador, "Elevador parado", "ELEVADOR", false);
        int luz = abrirChamado(morador, "Lâmpada queimada", "ILUMINACAO", false);
        int cano = abrirChamado(morador, "Cano pingando", "HIDRAULICA", false);

        MvcResult r = mvc.perform(get("/api/sindico/chamados").session(sindicoLogado()))
                .andExpect(status().isOk()).andReturn();
        List<Integer> ids = new ArrayList<>(JsonPath.read(r.getResponse().getContentAsString(), "$[*].id"));

        assertThat(ids).contains(elevador, luz, cano);
        assertThat(ids.indexOf(luz)).isLessThan(ids.indexOf(cano));
        assertThat(ids.indexOf(cano)).isLessThan(ids.indexOf(elevador));
    }

    @Test
    void moradorNaoAcessaPainelDoSindico() throws Exception {
        MockHttpSession morador = moradorLogado();
        mvc.perform(get("/api/sindico/chamados").session(morador)).andExpect(status().isForbidden());
        mvc.perform(get("/api/sindico/estatisticas").session(morador)).andExpect(status().isForbidden());
    }

    @Test
    void chamadoAnonimoEscondeIdentidadeDoSindicoMasNaoDoDono() throws Exception {
        MockHttpSession morador = moradorLogado();
        int id = abrirChamado(morador, "Vizinho fazendo barulho", "BARULHO", true);

        mvc.perform(get("/api/sindico/chamados?status=todos").session(sindicoLogado()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==%d)].moradorNome".formatted(id)).value("Anônimo"))
                .andExpect(jsonPath("$[?(@.id==%d)].moradorUnidade".formatted(id))
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
        mvc.perform(get("/api/chamados/meus").session(morador))
                .andExpect(jsonPath("$[0].moradorNome").value("Morador Teste"));
    }

    // ---------- RF04 + RF05 ----------

    @Test
    void sindicoMudaStatusEMoradorRecebeNotificacao() throws Exception {
        MockHttpSession morador = moradorLogado();
        MockHttpSession sindico = sindicoLogado();
        int id = abrirChamado(morador, "Portão da garagem emperrado", "PORTARIA", false);

        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_ANDAMENTO\",\"observacao\":\"Técnico agendado para amanhã.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));

        MvcResult n = mvc.perform(get("/api/notificacoes").session(morador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas").value(1))
                .andExpect(jsonPath("$.itens[0].chamadoId").value(id))
                .andExpect(jsonPath("$.itens[0].mensagem").value(org.hamcrest.Matchers.containsString("Em andamento")))
                .andExpect(jsonPath("$.itens[0].mensagem").value(org.hamcrest.Matchers.containsString("Técnico agendado")))
                .andReturn();
        int notificacaoId = JsonPath.read(n.getResponse().getContentAsString(), "$.itens[0].id");

        mvc.perform(post("/api/notificacoes/%d/lida".formatted(notificacaoId)).with(csrf()).session(morador))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notificacoes").session(morador))
                .andExpect(jsonPath("$.naoLidas").value(0));

        // histórico: abertura + mudança
        mvc.perform(get("/api/chamados/%d".formatted(id)).session(morador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historico.length()").value(2))
                .andExpect(jsonPath("$.historico[1].statusNovo").value("Em andamento"));
    }

    @Test
    void repetirOMesmoStatusDa400() throws Exception {
        MockHttpSession morador = moradorLogado();
        int id = abrirChamado(morador, "Teste", "OUTROS", false);
        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(sindicoLogado())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ABERTO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moradorNaoMudaStatus() throws Exception {
        MockHttpSession morador = moradorLogado();
        int id = abrirChamado(morador, "Teste", "OUTROS", false);
        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(morador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void estatisticasContamPorStatusEDificuldade() throws Exception {
        MockHttpSession morador = moradorLogado();
        abrirChamado(morador, "Teste", "ILUMINACAO", false);
        mvc.perform(get("/api/sindico/estatisticas").session(sindicoLogado()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abertos").isNumber())
                .andExpect(jsonPath("$.pendentesFaceis").isNumber());
    }

    // ---------- avaliação do atendimento ----------

    void mudarStatus(MockHttpSession sindico, int id, String status) throws Exception {
        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)))
                .andExpect(status().isOk());
    }

    @Test
    void avaliacaoSoDepoisDeResolvidoSoDoDonoEUmaVez() throws Exception {
        MockHttpSession morador = moradorLogado();
        MockHttpSession sindico = sindicoLogado();
        int id = abrirChamado(morador, "Portão quebrado", "PORTARIA", false);
        String url = "/api/chamados/%d/avaliacao".formatted(id);
        String corpo = "{\"nota\":4,\"comentario\":\"Atendimento rápido.\"}";

        // ainda aberto: não pode avaliar
        mvc.perform(post(url).with(csrf()).session(morador).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Só é possível avaliar chamados resolvidos."));

        mudarStatus(sindico, id, "RESOLVIDO");

        // nota fora da faixa
        mvc.perform(post(url).with(csrf()).session(morador).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":6}"))
                .andExpect(status().isBadRequest());
        // outro morador não avalia chamado alheio (404, não revela que existe)
        mvc.perform(post(url).with(csrf()).session(moradorLogado()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
        // o dono avalia
        mvc.perform(post(url).with(csrf()).session(morador).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        // só uma vez
        mvc.perform(post(url).with(csrf()).session(morador).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());

        // a nota aparece para o dono e para o síndico
        mvc.perform(get("/api/chamados/meus").session(morador))
                .andExpect(jsonPath("$[0].notaAvaliacao").value(4))
                .andExpect(jsonPath("$[0].comentarioAvaliacao").value("Atendimento rápido."));
        mvc.perform(get("/api/sindico/chamados?status=todos").session(sindico))
                .andExpect(jsonPath("$[?(@.id==%d)].notaAvaliacao".formatted(id)).value(4));
        mvc.perform(get("/api/sindico/estatisticas").session(sindico))
                .andExpect(jsonPath("$.totalAvaliacoes").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.mediaAvaliacoes").isNumber());
    }

    // ---------- minha conta ----------

    @Test
    void minhaContaAtualizaPerfilMasNuncaEmailNemPerfil() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        MockHttpSession sessao = entrar(email, SENHA);
        mvc.perform(patch("/api/auth/me").with(csrf()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Novo\",\"telefone\":\"11 99999-0000\",\"unidade\":\"Bloco C, 30\","
                                + "\"email\":\"outro@x.com\",\"perfil\":\"SINDICO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Novo"))
                .andExpect(jsonPath("$.telefone").value("11 99999-0000"))
                .andExpect(jsonPath("$.unidade").value("Bloco C, 30"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.perfil").value("MORADOR"));
        mvc.perform(patch("/api/auth/me").with(csrf()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trocaDeSenhaExigeAAtualEFazAAntigaDeixarDeValer() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        MockHttpSession sessao = entrar(email, SENHA);
        String nova = "Outra-Senha-Forte-9";

        mvc.perform(post("/api/auth/senha").with(csrf()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"errada-errada\",\"novaSenha\":\"%s\"}".formatted(nova)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Senha atual incorreta."));
        mvc.perform(post("/api/auth/senha").with(csrf()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"%s\",\"novaSenha\":\"%s\"}".formatted(SENHA, SENHA)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/senha").with(csrf()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"%s\",\"novaSenha\":\"curta\"}".formatted(SENHA)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/senha").with(csrf()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"%s\",\"novaSenha\":\"%s\"}".formatted(SENHA, nova)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isUnauthorized());
        entrar(email, nova);
        assertThat(usuarios.findByEmail(email).orElseThrow().getSenhaHash()).startsWith("$2").doesNotContain(nova);
    }

    @Test
    void senhaAtualErradaVariasVezesNaTrocaTambemBloqueia() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        MockHttpSession sessao = entrar(email, SENHA);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/senha").with(csrf()).with(ip("10.9.9.9")).session(sessao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senhaAtual\":\"errada-%d-errada\",\"novaSenha\":\"Qualquer-Nova-1\"}".formatted(i)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/senha").with(csrf()).with(ip("10.9.9.9")).session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"%s\",\"novaSenha\":\"Qualquer-Nova-1\"}".formatted(SENHA)))
                .andExpect(status().isTooManyRequests());
    }

    // ---------- emergência e envelhecimento na fila ----------

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void emergenciaMarcadaFuraAFilaMesmoSendoDificilENotificaOSindico() throws Exception {
        MockHttpSession morador = moradorLogado();
        MockHttpSession sindico = sindicoLogado();
        int facil = abrirChamado(morador, "Lâmpada queimada", "ILUMINACAO", false);
        MvcResult r = mvc.perform(formChamado("Pessoa presa no elevador", "ELEVADOR", true)
                        .param("emergencia", "true").session(morador))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emergencia").value(true))
                .andExpect(jsonPath("$.dificuldade").value("DIFICIL"))
                .andReturn();
        int emergencia = JsonPath.read(r.getResponse().getContentAsString(), "$.id");

        MvcResult fila = mvc.perform(get("/api/sindico/chamados").session(sindico)).andExpect(status().isOk()).andReturn();
        List<Integer> ids = new ArrayList<>(JsonPath.read(fila.getResponse().getContentAsString(), "$[*].id"));
        assertThat(ids.indexOf(emergencia)).as("emergência antes do fácil").isLessThan(ids.indexOf(facil));
        // todas as emergências pendentes vêm antes de qualquer chamado comum
        List<Boolean> flags = JsonPath.read(fila.getResponse().getContentAsString(), "$[*].emergencia");
        assertThat(flags.indexOf(false)).isGreaterThanOrEqualTo(flags.lastIndexOf(true));

        // o síndico recebe um aviso; ele NÃO traz o nome do morador (o chamado é anônimo)
        mvc.perform(get("/api/notificacoes").session(sindico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(emergencia))
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("EMERGÊNCIA no chamado #" + emergencia),
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Morador Teste"))))));
    }

    @Test
    void chamadoComumNaoGeraAvisoDeEmergencia() throws Exception {
        MockHttpSession morador = moradorLogado();
        MockHttpSession sindico = sindicoLogado();
        int id = abrirChamado(morador, "Torneira pingando", "HIDRAULICA", false);
        mvc.perform(get("/api/notificacoes").session(sindico))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)]".formatted(id)).isEmpty());
    }

    @Test
    void chamadoDificilAntigoSobeNaFilaSemMudarADificuldade() throws Exception {
        MockHttpSession morador = moradorLogado();
        MockHttpSession sindico = sindicoLogado();
        int antigoDificil = abrirChamado(morador, "Elevador parado", "ELEVADOR", false);   // 8 pontos
        int novoMedio = abrirChamado(morador, "Cano pingando", "HIDRAULICA", false);       // 6 pontos
        // hoje o médio vem antes do difícil
        assertThat(posicao(sindico, novoMedio)).isLessThan(posicao(sindico, antigoDificil));

        // envelhece o difícil em 10 dias: bônus máximo de 3 pontos (8 vira 5) e passa o médio (6)
        jdbc.update("UPDATE chamados SET criado_em = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(10)), antigoDificil);

        assertThat(posicao(sindico, antigoDificil)).isLessThan(posicao(sindico, novoMedio));
        mvc.perform(get("/api/sindico/chamados").session(sindico))
                .andExpect(jsonPath("$[?(@.id==%d)].dificuldade".formatted(antigoDificil)).value(org.hamcrest.Matchers.contains("DIFICIL")))
                .andExpect(jsonPath("$[?(@.id==%d)].pontuacao".formatted(antigoDificil)).value(org.hamcrest.Matchers.contains(8)))
                .andExpect(jsonPath("$[?(@.id==%d)].pontuacaoEfetiva".formatted(antigoDificil)).value(org.hamcrest.Matchers.contains(5)))
                .andExpect(jsonPath("$[?(@.id==%d)].diasEmEspera".formatted(antigoDificil)).value(org.hamcrest.Matchers.contains(10)))
                .andExpect(jsonPath("$[?(@.id==%d)].atrasado".formatted(antigoDificil)).value(org.hamcrest.Matchers.contains(true)));
    }

    int posicao(MockHttpSession sindico, int id) throws Exception {
        MvcResult r = mvc.perform(get("/api/sindico/chamados").session(sindico)).andReturn();
        List<Integer> ids = JsonPath.read(r.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).contains(id);
        return ids.indexOf(id);
    }

    @Test
    void estatisticasContamEmergenciasPendentes() throws Exception {
        MockHttpSession morador = moradorLogado();
        MockHttpSession sindico = sindicoLogado();
        mvc.perform(formChamado("Fio solto", "ELETRICA", false).param("emergencia", "true").session(morador))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/sindico/estatisticas").session(sindico))
                .andExpect(jsonPath("$.emergenciasAbertas").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    // ---------- sessão com versão ----------

    @Test
    void trocarASenhaEncerraAsOutrasSessoesMasNaoAAtual() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        MockHttpSession atual = entrar(email, SENHA);
        MockHttpSession outra = entrar(email, SENHA); // ex.: sessão aberta no celular, possivelmente roubada
        mvc.perform(get("/api/auth/me").session(outra)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/senha").with(csrf()).session(atual).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"%s\",\"novaSenha\":\"Nova-Senha-Forte-5\"}".formatted(SENHA)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(atual)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(outra))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Sessão encerrada. Entre novamente."));
    }

    @Test
    void usuarioDesativadoPerdeASessaoENaoConsegueEntrar() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        MockHttpSession sessao = entrar(email, SENHA);
        mvc.perform(get("/api/chamados/meus").session(sessao)).andExpect(status().isOk());

        var usuario = usuarios.findByEmail(email).orElseThrow();
        usuario.setAtivo(false);
        usuarios.save(usuario);

        mvc.perform(get("/api/chamados/meus").session(sessao)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- conflito de versão ----------

    @Test
    void telaDesatualizadaDoSindicoRecebe409EmVezDeApagarAMudancaDoOutro() throws Exception {
        MockHttpSession morador = moradorLogado();
        MockHttpSession sindico = sindicoLogado();
        int id = abrirChamado(morador, "Portão da garagem", "PORTARIA", false);

        MvcResult lista = mvc.perform(get("/api/sindico/chamados?status=todos").session(sindico)).andReturn();
        List<Integer> versoes = JsonPath.read(lista.getResponse().getContentAsString(), "$[?(@.id==%d)].versao".formatted(id));
        int versaoVista = versoes.get(0);

        // o síndico A atualiza com a versão que viu: funciona
        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_ANDAMENTO\",\"versao\":%d}".formatted(versaoVista)))
                .andExpect(status().isOk());
        // o síndico B ainda tem a tela antiga (mesma versão): recebe 409 e NADA é alterado
        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVIDO\",\"versao\":%d}".formatted(versaoVista)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("alterado por outra pessoa")));
        mvc.perform(get("/api/chamados/%d".formatted(id)).session(morador))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
    }

    // ---------- exportação CSV ----------

    @Test
    void csvDoSindicoRespeitaAnonimatoEBloqueiaFormula() throws Exception {
        MockHttpSession morador = moradorLogado();
        int anonimo = abrirChamado(morador, "=HYPERLINK(\"http://exemplo.invalido\";\"clique\")", "BARULHO", true);
        int normal = abrirChamado(morador, "Lâmpada; queimada", "ILUMINACAO", false);

        MvcResult r = mvc.perform(get("/api/sindico/chamados/exportar.csv?status=todos").session(sindicoLogado()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn();
        assertThat(r.getResponse().getHeader("Content-Disposition")).contains("attachment").contains(".csv");
        String csv = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Fila;ID;");
        String linhaAnonima = linhaDoId(csv, anonimo);
        assertThat(linhaAnonima).contains("'=HYPERLINK").contains("Anônimo").doesNotContain("Morador Teste");
        // ponto e vírgula dentro do texto não quebra a coluna: o campo vem entre aspas
        assertThat(linhaDoId(csv, normal)).contains("\"Lâmpada; queimada\"").contains("Morador Teste");
    }

    static String linhaDoId(String csv, int id) {
        // as linhas são "posição;id;..."; as colunas Título e Descrição podem ter aspas, mas não quebras aqui
        return java.util.Arrays.stream(csv.split("\r\n"))
                .filter(l -> l.matches("^\\d+;" + id + ";.*"))
                .findFirst().orElseThrow(() -> new AssertionError("chamado " + id + " não está no CSV"));
    }

    @Test
    void moradorNaoExportaCsvDoSindico() throws Exception {
        mvc.perform(get("/api/sindico/chamados/exportar.csv").session(moradorLogado()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sindico/chamados/exportar.csv")).andExpect(status().isUnauthorized());
    }

    // ---------- e-mail de mudança de status ----------

    @Autowired
    br.nexodesk.servico.EnviadorEmailSimulado emailSimulado;

    @Test
    void mudarStatusGeraEmailSemADescricaoENoAssuntoSemOTitulo() throws Exception {
        String email = novoEmail();
        cadastrar(email);
        MockHttpSession morador = entrar(email, SENHA);
        int id = abrirChamado(morador, "Vazamento no teto", "HIDRAULICA", false);

        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(sindicoLogado())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_ANDAMENTO\",\"observacao\":\"Encanador agendado.\"}"))
                .andExpect(status().isOk());

        // o envio é assíncrono e só acontece depois do commit: espera aparecer
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(emailSimulado.enviados()).anySatisfy(m -> {
                    assertThat(m.para()).isEqualTo(email);
                    assertThat(m.assunto()).isEqualTo("NexoDesk: atualização do chamado #" + id);
                    assertThat(m.texto()).contains("Em andamento").contains("Encanador agendado.")
                            .contains("Vazamento no teto").doesNotContain("Descrição de teste do chamado.");
                }));
    }

    // ---------- isolamento entre moradores ----------

    @Test
    void moradorNaoVeChamadoDeOutroMorador() throws Exception {
        int id = abrirChamado(moradorLogado(), "Meu chamado", "OUTROS", false);
        MockHttpSession intruso = moradorLogado();
        mvc.perform(get("/api/chamados/%d".formatted(id)).session(intruso)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/%d/imagem".formatted(id)).session(intruso)).andExpect(status().isNotFound());
    }

    // ---------- upload de imagem ----------

    /** PNG de verdade (8x8): o servidor agora decodifica e recodifica a foto para tirar o EXIF. */
    static final byte[] PNG_MINIMO = pngReal();

    static byte[] pngReal() {
        try {
            java.io.ByteArrayOutputStream s = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", s);
            return s.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void arquivoFalsoComExtensaoPngEhRecusado() throws Exception {
        MockHttpSession morador = moradorLogado();
        mvc.perform(formChamado("Foto falsa", "OUTROS", false)
                        .file(new MockMultipartFile("imagem", "foto.png", "image/png", "isto nao e imagem".getBytes()))
                        .session(morador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Envie uma imagem JPG, PNG ou GIF válida."));
    }

    @Test
    void imagemValidaEhSalvaEServidaSoParaODonoESindico() throws Exception {
        MockHttpSession morador = moradorLogado();
        MvcResult r = mvc.perform(formChamado("Com foto", "OUTROS", false)
                        .file(new MockMultipartFile("imagem", "qualquer-nome.exe.png", "image/png", PNG_MINIMO))
                        .session(morador))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temImagem").value(true))
                .andReturn();
        int id = JsonPath.read(r.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/chamados/%d/imagem".formatted(id)).session(morador))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/chamados/%d/imagem".formatted(id)).session(sindicoLogado()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chamados/%d/imagem".formatted(id)).session(moradorLogado()))
                .andExpect(status().isNotFound());
    }
}
