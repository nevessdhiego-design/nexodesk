package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.repositorio.NotificacaoRepository;
import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.servico.ApiException;
import br.nexodesk.servico.ExclusaoDeContaService;
import com.jayway.jsonpath.JsonPath;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** "Excluir minha conta": dados pessoais somem, chamados ficam sem identificação, sessão e login caem. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExclusaoDeContaTest {

    static final String SENHA = "Morador-Teste-123";
    static final String SINDICO_EMAIL = "sindico.teste@nexodesk.test";
    static final String SINDICO_SENHA = "Senha-Somente-Para-Testes-1";

    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioRepository usuarios;

    String cadastrar(String nome) throws Exception {
        String email = "saindo-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","email":"%s","unidade":"Bloco C, 303","telefone":"11 98888-7777","senha":"%s"}
                                """.formatted(nome, email, SENHA)))
                .andExpect(status().isCreated());
        return email;
    }

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    int abrirChamado(MockHttpSession morador, String titulo) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/chamados").with(csrf())
                        .param("titulo", titulo).param("descricao", "Descrição de teste do chamado.")
                        .param("categoria", "LIMPEZA").param("anonimo", "false").session(morador))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void excluirApagaDadosPessoaisMantemOChamadoSemIdentificacaoEDerrubaAcesso() throws Exception {
        String email = cadastrar("Carla Saindo");
        MockHttpSession sessao = entrar(email, SENHA);
        MockHttpSession outraSessao = entrar(email, SENHA);
        int id = abrirChamado(sessao, "Lixo acumulado no hall");
        MockHttpSession sindico = entrar(SINDICO_EMAIL, SINDICO_SENHA);

        mvc.perform(post("/api/auth/excluir-conta").with(csrf()).session(sessao)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"%s\"}".formatted(SENHA)))
                .andExpect(status().isNoContent());

        Usuario u = usuarios.findAll().stream().filter(x -> x.getEmail().startsWith("excluido-")
                && x.getNome().equals("Conta excluída") && x.getId() != null).max(java.util.Comparator.comparing(Usuario::getId)).orElseThrow();
        assertThat(u.getEmail()).endsWith("@nexodesk.invalid");
        assertThat(u.getTelefone()).isNull();
        assertThat(u.getUnidade()).isNull();
        assertThat(u.isAtivo()).isFalse();
        assertThat(usuarios.existsByEmail(email)).isFalse();

        // sessões antigas e login novo não funcionam mais
        mvc.perform(get("/api/chamados/meus").session(outraSessao)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isUnauthorized());

        // o chamado continua no histórico, sem nome nem unidade
        String json = mvc.perform(get("/api/sindico/chamados?status=todos").session(sindico))
                .andExpect(jsonPath("$[?(@.id==%d)].titulo".formatted(id)).isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("Carla Saindo").doesNotContain("Bloco C, 303").doesNotContain(email);
        mvc.perform(get("/api/sindico/chamados?status=todos").session(sindico))
                .andExpect(jsonPath("$[?(@.id==%d)].moradorNome".formatted(id)).value(org.hamcrest.Matchers.hasItem("Conta excluída")));

        // o e-mail antigo fica livre: dá para criar uma conta nova com ele
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Carla Nova\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isCreated());
    }

    @Test
    void senhaErradaNaoExcluiNada() throws Exception {
        String email = cadastrar("Pessoa Fica");
        MockHttpSession sessao = entrar(email, SENHA);

        mvc.perform(post("/api/auth/excluir-conta").with(csrf()).session(sessao)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"errada-errada-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Senha incorreta."));
        mvc.perform(post("/api/auth/excluir-conta").with(csrf()).session(sessao)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"\"}"))
                .andExpect(status().isBadRequest());

        assertThat(usuarios.findByEmail(email).orElseThrow().getNome()).isEqualTo("Pessoa Fica");
        mvc.perform(get("/api/chamados/meus").session(sessao)).andExpect(status().isOk());
    }

    @Test
    void semLoginOuSemTokenCsrfNaoExclui() throws Exception {
        mvc.perform(post("/api/auth/excluir-conta").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        String email = cadastrar("Sem Csrf");
        MockHttpSession sessao = entrar(email, SENHA);
        mvc.perform(post("/api/auth/excluir-conta").session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"%s\"}".formatted(SENHA)))
                .andExpect(status().isForbidden());
        assertThat(usuarios.findByEmail(email).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void paginaDePrivacidadeEhPublica() throws Exception {
        mvc.perform(get("/privacidade.html")).andExpect(status().isOk());
    }

    // ---------- regra do último síndico, sem mexer no banco ----------

    @Test
    void oUnicoSindicoAtivoNaoPodeExcluirAContaMasComOutroPode() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        ChamadoRepository chamados = mock(ChamadoRepository.class);
        NotificacaoRepository avisos = mock(NotificacaoRepository.class);
        PasswordEncoder codificador = new BCryptPasswordEncoder(4);
        ExclusaoDeContaService servico = new ExclusaoDeContaService(repo, chamados, avisos, codificador);

        Usuario sindico = new Usuario();
        ReflectionTestUtils.setField(sindico, "id", 7L);
        sindico.setNome("Síndico");
        sindico.setEmail("s@nexodesk.test");
        sindico.setPerfil(Perfil.SINDICO);
        sindico.setAtivo(true);
        sindico.setSenhaHash(codificador.encode("Senha-Do-Sindico-1"));
        when(repo.findByEmail("s@nexodesk.test")).thenReturn(Optional.of(sindico));

        when(repo.countByPerfilAndAtivoTrue(Perfil.SINDICO)).thenReturn(1L);
        assertThatThrownBy(() -> servico.excluir("s@nexodesk.test", "Senha-Do-Sindico-1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repo, never()).save(any());
        assertThat(sindico.getNome()).isEqualTo("Síndico");

        when(repo.countByPerfilAndAtivoTrue(Perfil.SINDICO)).thenReturn(2L);
        assertThat(servico.excluir("s@nexodesk.test", "Senha-Do-Sindico-1")).isTrue();
        assertThat(sindico.getNome()).isEqualTo("Conta excluída");
        assertThat(sindico.isAtivo()).isFalse();
        verify(chamados).liberarResponsavel(sindico);
        verify(avisos).apagarDoUsuario(sindico);
    }
}
