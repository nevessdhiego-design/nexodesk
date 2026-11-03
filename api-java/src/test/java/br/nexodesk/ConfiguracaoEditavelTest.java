package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.dominio.Funcao;
import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.servico.ChecklistModelo;
import br.nexodesk.servico.ConfiguracaoService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Matriz função x categoria e checklists que o síndico edita. Restaura o padrão no fim, porque o banco é compartilhado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguracaoEditavelTest {

    static final String SENHA = "Morador-Teste-123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ConfiguracaoService configuracao;

    @AfterEach
    void voltaAoPadrao() {
        configuracao.restaurarPadrao();
    }

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    String cadastrar() throws Exception {
        String email = "cfg-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa Cfg\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isCreated());
        return email;
    }

    MockHttpSession sindico() throws Exception {
        return entrar("sindico.teste@nexodesk.test", "Senha-Somente-Para-Testes-1");
    }

    @Test
    void oPadraoDeFabricaDoBancoEIgualAoDoCodigo() {
        var matriz = configuracao.matriz();
        for (Funcao f : Funcao.values()) {
            assertThat(matriz.get(f)).as("função %s", f).containsExactlyInAnyOrderElementsOf(f.getCategorias());
        }
        for (Categoria c : Categoria.values()) {
            assertThat(configuracao.checklist(c)).as("checklist de %s", c).containsExactlyElementsOf(ChecklistModelo.itens(c));
        }
    }

    @Test
    void sindicoEditaAMatrizEOPainelPassaAIndicarPeloNovoCriterio() throws Exception {
        MockHttpSession sindico = sindico();
        mvc.perform(get("/api/sindico/configuracao").session(sindico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funcoes.length()").value(8))
                .andExpect(jsonPath("$.categorias.length()").value(13));

        // o encanador passa a cobrir Limpeza também (e continua cobrindo Hidráulica)
        String corpo = "{\"matriz\":{\"ZELADOR\":[\"LIMPEZA\",\"AREAS_COMUNS\",\"ESTACIONAMENTO\",\"OUTROS\"],\"FAXINEIRO\":[\"LIMPEZA\"],"
                + "\"ELETRICISTA\":[\"ELETRICA\",\"ILUMINACAO\"],\"ENCANADOR\":[\"HIDRAULICA\",\"LIMPEZA\"],\"MANUTENCAO\":[\"ESTRUTURA\",\"OUTROS\"],"
                + "\"PORTEIRO\":[\"PORTARIA\",\"ESTACIONAMENTO\",\"BARULHO\",\"CONVIVENCIA\"],\"SEGURANCA\":[\"SEGURANCA\",\"BARULHO\"],\"TECNICO_EXTERNO\":[\"ELEVADOR\"]}}";
        mvc.perform(put("/api/sindico/configuracao/funcoes").with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funcoes[?(@.codigo=='ENCANADOR')].categorias").value(Matchers.hasItem(Matchers.hasItem("HIDRAULICA"))));

        String email = cadastrar();
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(usuarios.findByEmail(email).orElseThrow().getId())).with(csrf()).session(sindico)
                .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"FUNCIONARIO\",\"funcao\":\"ENCANADOR\"}")).andExpect(status().isOk());
        String equipe = mvc.perform(get("/api/sindico/equipe").session(sindico)).andReturn().getResponse().getContentAsString();
        assertThat((List<List<String>>) JsonPath.read(equipe, "$[?(@.funcao=='ENCANADOR')].categorias")).anyMatch(l -> l.contains("LIMPEZA"));
    }

    @Test
    void categoriaNaoPodeFicarSemNenhumaFuncao() throws Exception {
        MockHttpSession sindico = sindico();
        // ninguém cobre Elevador
        String semElevador = "{\"matriz\":{\"ZELADOR\":[\"LIMPEZA\"],\"FAXINEIRO\":[\"AREAS_COMUNS\"],\"ELETRICISTA\":[\"ELETRICA\",\"ILUMINACAO\"],"
                + "\"ENCANADOR\":[\"HIDRAULICA\"],\"MANUTENCAO\":[\"ESTRUTURA\",\"OUTROS\"],\"PORTEIRO\":[\"PORTARIA\",\"ESTACIONAMENTO\",\"BARULHO\",\"CONVIVENCIA\"],"
                + "\"SEGURANCA\":[\"SEGURANCA\"]}}";
        mvc.perform(put("/api/sindico/configuracao/funcoes").with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON).content(semElevador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("Elevador")));
        // nada mudou
        assertThat(configuracao.matriz().get(Funcao.TECNICO_EXTERNO)).contains(Categoria.ELEVADOR);
    }

    @Test
    void checklistEditadoValeParaOsProximosChamadosETemLimites() throws Exception {
        MockHttpSession sindico = sindico();
        mvc.perform(put("/api/sindico/configuracao/checklists/LIMPEZA").with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[\"  Colocar placa de piso molhado  \",\"\",\"Passar pano\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[?(@.codigo=='LIMPEZA')].checklist").value(Matchers.hasItem(Matchers.contains("Colocar placa de piso molhado", "Passar pano"))));

        // um chamado de limpeza atribuído agora nasce com as etapas novas
        MockHttpSession morador = entrar(cadastrar(), SENHA);
        MvcResult r = mvc.perform(multipart("/api/chamados").with(csrf()).param("titulo", "Sujeira").param("descricao", "Tem sujeira no chão.")
                        .param("categoria", "LIMPEZA").param("anonimo", "false").session(morador)).andExpect(status().isCreated()).andReturn();
        int id = JsonPath.read(r.getResponse().getContentAsString(), "$.id");
        String ze = cadastrar();
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(usuarios.findByEmail(ze).orElseThrow().getId())).with(csrf()).session(sindico)
                .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"FUNCIONARIO\",\"funcao\":\"ZELADOR\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(id)).with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"responsavelId\":%d}".formatted(usuarios.findByEmail(ze).orElseThrow().getId()))).andExpect(status().isOk());
        mvc.perform(get("/api/chamados/%d".formatted(id)).session(morador))
                .andExpect(jsonPath("$.tarefas[*].texto").value(Matchers.contains("Colocar placa de piso molhado", "Passar pano")));

        // limites: vazio, etapa grande demais, etapas demais
        mvc.perform(put("/api/sindico/configuracao/checklists/LIMPEZA").with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[\"\",\" \"]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/sindico/configuracao/checklists/LIMPEZA").with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[\"" + "x".repeat(121) + "\"]}")).andExpect(status().isBadRequest());
        String treze = "[" + "\"a\",".repeat(12) + "\"b\"]";
        mvc.perform(put("/api/sindico/configuracao/checklists/LIMPEZA").with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":" + treze + "}")).andExpect(status().isBadRequest());
    }

    @Test
    void restaurarVoltaAoPadraoESoOSindicoMexe() throws Exception {
        MockHttpSession sindico = sindico();
        mvc.perform(put("/api/sindico/configuracao/checklists/ELEVADOR").with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[\"Só esta etapa\"]}")).andExpect(status().isOk());
        assertThat(configuracao.checklist(Categoria.ELEVADOR)).containsExactly("Só esta etapa");
        mvc.perform(post("/api/sindico/configuracao/restaurar").with(csrf()).session(sindico)).andExpect(status().isOk());
        assertThat(configuracao.checklist(Categoria.ELEVADOR)).containsExactlyElementsOf(ChecklistModelo.itens(Categoria.ELEVADOR));

        MockHttpSession morador = entrar(cadastrar(), SENHA);
        mvc.perform(get("/api/sindico/configuracao").session(morador)).andExpect(status().isForbidden());
        mvc.perform(post("/api/sindico/configuracao/restaurar").with(csrf()).session(morador)).andExpect(status().isForbidden());
        mvc.perform(put("/api/sindico/configuracao/checklists/ELEVADOR").with(csrf()).session(morador).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[\"x\"]}")).andExpect(status().isForbidden());
    }
}
