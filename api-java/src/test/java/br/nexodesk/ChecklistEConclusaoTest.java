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
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/** Checklist por categoria e foto do serviço pronto (antes e depois). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChecklistEConclusaoTest {

    static final String SENHA = "Morador-Teste-123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    String cadastrar() throws Exception {
        String email = "etapa-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa Etapa\",\"email\":\"%s\",\"unidade\":\"C3\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isCreated());
        return email;
    }

    MockHttpSession sindico() throws Exception {
        return entrar("sindico.teste@nexodesk.test", "Senha-Somente-Para-Testes-1");
    }

    String funcionario(MockHttpSession sindico, String funcao) throws Exception {
        String email = cadastrar();
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(usuarios.findByEmail(email).orElseThrow().getId())).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"FUNCIONARIO\",\"funcao\":\"%s\"}".formatted(funcao)))
                .andExpect(status().isOk());
        return email;
    }

    long id(String email) {
        return usuarios.findByEmail(email).orElseThrow().getId();
    }

    int abrir(MockHttpSession morador, String categoria) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/chamados").with(csrf()).param("titulo", "Sujeira no pátio").param("descricao", "Tem sujeira no chão do pátio.")
                        .param("categoria", categoria).param("localOcorrencia", "Pátio").param("anonimo", "false").session(morador))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    void atribuir(MockHttpSession sindico, int chamado, long responsavel) throws Exception {
        mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(chamado)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"responsavelId\":%d}".formatted(responsavel)))
                .andExpect(status().isOk());
    }

    @Test
    void atribuirCriaOChecklistDaCategoriaUmaVezEOMoradorVeOProgresso() throws Exception {
        MockHttpSession sindico = sindico();
        String ze = funcionario(sindico, "ZELADOR");
        MockHttpSession morador = entrar(cadastrar(), SENHA);
        int chamado = abrir(morador, "LIMPEZA");
        mvc.perform(get("/api/chamados/%d".formatted(chamado)).session(morador)).andExpect(jsonPath("$.tarefas.length()").value(0));

        atribuir(sindico, chamado, id(ze));
        atribuir(sindico, chamado, id(ze)); // de novo: não duplica

        String json = mvc.perform(get("/api/chamados/%d".formatted(chamado)).session(morador)).andReturn().getResponse().getContentAsString();
        List<String> textos = JsonPath.read(json, "$.tarefas[*].texto");
        assertThat(textos).hasSize(4).contains("Recolher o lixo e os resíduos", "Conferir se ficou limpo");
        assertThat((List<Boolean>) JsonPath.read(json, "$.tarefas[*].feita")).containsOnly(false);
    }

    @Test
    void soOResponsavelEOSindicoMarcamEtapasEOMoradorNao() throws Exception {
        MockHttpSession sindico = sindico();
        String ze = funcionario(sindico, "ZELADOR");
        String outro = funcionario(sindico, "FAXINEIRO");
        MockHttpSession morador = entrar(cadastrar(), SENHA);
        int chamado = abrir(morador, "LIMPEZA");
        atribuir(sindico, chamado, id(ze));
        int tarefa = ((List<Integer>) JsonPath.read(mvc.perform(get("/api/chamados/%d".formatted(chamado)).session(morador)).andReturn()
                .getResponse().getContentAsString(), "$.tarefas[*].id")).get(0);

        mvc.perform(patch("/api/funcionario/tarefas/%d".formatted(tarefa)).with(csrf()).session(entrar(ze, SENHA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"feita\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.feita").value(true));
        // outro funcionário (que não é o responsável): a etapa "não existe"
        mvc.perform(patch("/api/funcionario/tarefas/%d".formatted(tarefa)).with(csrf()).session(entrar(outro, SENHA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"feita\":false}"))
                .andExpect(status().isNotFound());
        // morador não usa a rota da equipe
        mvc.perform(patch("/api/funcionario/tarefas/%d".formatted(tarefa)).with(csrf()).session(morador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"feita\":false}"))
                .andExpect(status().isForbidden());
        // o síndico pode desmarcar
        mvc.perform(patch("/api/sindico/tarefas/%d".formatted(tarefa)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"feita\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.feita").value(false));
        mvc.perform(patch("/api/sindico/tarefas/%d".formatted(tarefa)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fotoDoServicoProntoAparecePraOMoradorEAvisaMasNaoParaForasteiro() throws Exception {
        MockHttpSession sindico = sindico();
        String ze = funcionario(sindico, "ZELADOR");
        String outro = funcionario(sindico, "ELETRICISTA");
        MockHttpSession morador = entrar(cadastrar(), SENHA);
        int chamado = abrir(morador, "LIMPEZA");
        atribuir(sindico, chamado, id(ze));
        MockMultipartFile foto = new MockMultipartFile("imagem", "depois.png", "image/png", FluxoChamadoTest.PNG_MINIMO);

        mvc.perform(multipart("/api/funcionario/chamados/%d/conclusao".formatted(chamado)).file(foto).with(csrf()).session(entrar(ze, SENHA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.temImagemConclusao").value(true));

        mvc.perform(get("/api/chamados/%d".formatted(chamado)).session(morador)).andExpect(jsonPath("$.temImagemConclusao").value(true));
        mvc.perform(get("/api/chamados/%d/imagem-conclusao".formatted(chamado)).session(morador))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/notificacoes").session(morador))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(chamado))
                        .value(Matchers.hasItem(Matchers.containsString("tem foto do resultado"))));
        // quem não é do chamado nem consegue ver nem enviar
        mvc.perform(get("/api/chamados/%d/imagem-conclusao".formatted(chamado)).session(entrar(cadastrar(), SENHA))).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/funcionario/chamados/%d/conclusao".formatted(chamado)).file(foto).with(csrf()).session(entrar(outro, SENHA)))
                .andExpect(status().isNotFound());
        // arquivo que não é imagem de verdade é recusado
        MockMultipartFile falso = new MockMultipartFile("imagem", "depois.png", "image/png", "isto nao e uma imagem".getBytes());
        mvc.perform(multipart("/api/funcionario/chamados/%d/conclusao".formatted(chamado)).file(falso).with(csrf()).session(entrar(ze, SENHA)))
                .andExpect(status().is4xxClientError());
    }
}
