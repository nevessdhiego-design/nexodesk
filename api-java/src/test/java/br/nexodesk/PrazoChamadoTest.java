package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.servico.ChamadoService;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/** Prazo (SLA) por categoria e o aviso de prazo vencido. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrazoChamadoTest {

    static final String SENHA = "Morador-Teste-123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ChamadoService servico;
    @Autowired JdbcTemplate jdbc;

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    MockHttpSession morador() throws Exception {
        String email = "prazo-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Morador Prazo\",\"email\":\"%s\",\"unidade\":\"A1\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isCreated());
        return entrar(email, SENHA);
    }

    MvcResult abrir(MockHttpSession m, String titulo, String categoria, boolean emergencia) throws Exception {
        return mvc.perform(multipart("/api/chamados").with(csrf()).param("titulo", titulo).param("descricao", "Descrição do chamado de teste.")
                        .param("categoria", categoria).param("anonimo", "false").param("emergencia", String.valueOf(emergencia)).session(m))
                .andExpect(status().isCreated()).andReturn();
    }

    @Test
    void prazoVemDaCategoriaEEmergenciaTemUmaHora() throws Exception {
        MockHttpSession m = morador();
        for (Categoria c : new Categoria[] {Categoria.LIMPEZA, Categoria.ESTRUTURA, Categoria.ELETRICA}) {
            long minutos = ((Number) JsonPath.read(abrir(m, "Chamado de " + c, c.name(), false).getResponse().getContentAsString(),
                    "$.minutosParaPrazo")).longValue();
            assertThat(minutos).as(c.name()).isBetween(c.getPrazoHoras() * 60L - 2, c.getPrazoHoras() * 60L);
        }
        long emergencia = ((Number) JsonPath.read(abrir(m, "Fio solto na escada", "ELETRICA", true).getResponse().getContentAsString(),
                "$.minutosParaPrazo")).longValue();
        assertThat(emergencia).isBetween(58L, 60L);
    }

    @Test
    void prazoVencidoAvisaSindicoEResponsavelUmaVezSoEResolvidoNaoAvisa() throws Exception {
        MockHttpSession m = morador();
        MockHttpSession sindico = entrar("sindico.teste@nexodesk.test", "Senha-Somente-Para-Testes-1");
        int vencido = JsonPath.read(abrir(m, "Lixo no hall (vencido)", "LIMPEZA", false).getResponse().getContentAsString(), "$.id");
        int resolvido = JsonPath.read(abrir(m, "Lâmpada (resolvida)", "ILUMINACAO", false).getResponse().getContentAsString(), "$.id");
        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(resolvido)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDO\"}"))
                .andExpect(status().isOk());
        Timestamp passado = Timestamp.valueOf(LocalDateTime.now().minusHours(3));
        jdbc.update("UPDATE chamados SET prazo_limite = ? WHERE id IN (?, ?)", passado, vencido, resolvido);

        int avisados = servico.alertarPrazosVencidos();

        assertThat(avisados).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/notificacoes").session(sindico))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(vencido))
                        .value(Matchers.hasItem(Matchers.containsString("PRAZO VENCIDO no chamado #" + vencido))))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(resolvido))
                        .value(Matchers.not(Matchers.hasItem(Matchers.containsString("PRAZO VENCIDO")))));
        // o selo do painel mostra "vencido" (minutos negativos) para o não resolvido e nada para o resolvido
        String fila = mvc.perform(get("/api/sindico/chamados?status=todos").session(sindico)).andReturn().getResponse().getContentAsString();
        assertThat(((List<Number>) JsonPath.read(fila, "$[?(@.id==%d)].minutosParaPrazo".formatted(vencido))).get(0).longValue()).isNegative();
        assertThat((List<Object>) JsonPath.read(fila, "$[?(@.id==%d)].minutosParaPrazo".formatted(resolvido))).containsOnlyNulls();

        // segunda rodada: nada novo para esse chamado (aviso único) e o MORADOR nunca recebe o aviso de prazo
        long antes = ((Number) JsonPath.read(mvc.perform(get("/api/notificacoes").session(sindico)).andReturn().getResponse().getContentAsString(), "$.itens.length()")).longValue();
        servico.alertarPrazosVencidos();
        long depois = ((Number) JsonPath.read(mvc.perform(get("/api/notificacoes").session(sindico)).andReturn().getResponse().getContentAsString(), "$.itens.length()")).longValue();
        assertThat(depois).isEqualTo(antes);
        mvc.perform(get("/api/notificacoes").session(m))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(vencido))
                        .value(Matchers.not(Matchers.hasItem(Matchers.containsString("PRAZO VENCIDO")))));
    }
}
