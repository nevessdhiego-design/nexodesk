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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
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

/** Relatório do síndico: números conferidos contra dados que o teste mesmo cria, num dia isolado do passado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RelatorioTest {

    static final String SENHA = "Morador-Teste-123";
    static final LocalDateTime DIA = LocalDateTime.of(2020, 3, 1, 8, 0);

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    String cadastrar() throws Exception {
        String email = "rel-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa Relatorio\",\"email\":\"%s\",\"unidade\":\"R1\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isCreated());
        return email;
    }

    int abrir(MockHttpSession m, String categoria, boolean emergencia) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/chamados").with(csrf()).param("titulo", "Chamado de relatório").param("descricao", "Descrição do chamado de teste.")
                        .param("categoria", categoria).param("anonimo", "false").param("emergencia", String.valueOf(emergencia)).session(m))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    void noDia(int id, int horasDePrazo) {
        jdbc.update("UPDATE chamados SET criado_em = ?, prazo_limite = ? WHERE id = ?", Timestamp.valueOf(DIA),
                Timestamp.valueOf(DIA.plusHours(horasDePrazo)), id);
    }

    void resolver(MockHttpSession sindico, int id, int horasDepois) throws Exception {
        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDO\"}")).andExpect(status().isOk());
        jdbc.update("UPDATE historico_status SET criado_em = ? WHERE chamado_id = ? AND status_novo = 'RESOLVIDO'",
                Timestamp.valueOf(DIA.plusHours(horasDepois)), id);
    }

    @Test
    void relatorioConfereComOsChamadosDoPeriodo() throws Exception {
        MockHttpSession sindico = entrar("sindico.teste@nexodesk.test", "Senha-Somente-Para-Testes-1");
        MockHttpSession morador = entrar(cadastrar(), SENHA);
        String funcionario = cadastrar();
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(usuarios.findByEmail(funcionario).orElseThrow().getId())).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"FUNCIONARIO\",\"funcao\":\"ZELADOR\"}")).andExpect(status().isOk());
        long ze = usuarios.findByEmail(funcionario).orElseThrow().getId();

        int a = abrir(morador, "LIMPEZA", false);     // resolvido em 5 h, prazo 24 h  -> dentro do prazo
        int b = abrir(morador, "ELETRICA", false);    // resolvido em 60 h, prazo 48 h -> fora do prazo
        int c = abrir(morador, "LIMPEZA", false);     // aberto e vencido
        int d = abrir(morador, "ELETRICA", true);     // emergência aberta e vencida
        for (int id : new int[] {a, c}) {
            mvc.perform(patch("/api/sindico/chamados/%d/responsavel".formatted(id)).with(csrf()).session(sindico)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"responsavelId\":%d}".formatted(ze))).andExpect(status().isOk());
        }
        resolver(sindico, a, 5);
        resolver(sindico, b, 60);
        noDia(a, 24);
        noDia(b, 48);
        noDia(c, 24);
        noDia(d, 1);

        mvc.perform(get("/api/sindico/relatorio?de=2020-03-01&ate=2020-03-01").session(sindico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.resolvidos").value(2))
                .andExpect(jsonPath("$.abertos").value(2))
                .andExpect(jsonPath("$.emergencias").value(1))
                .andExpect(jsonPath("$.tempoMedioResolucaoHoras").value(32.5))
                .andExpect(jsonPath("$.percentualNoPrazo").value(50.0))
                .andExpect(jsonPath("$.vencidosAgora").value(2))
                .andExpect(jsonPath("$.porCategoria[?(@.rotulo=='Limpeza')].total").value(org.hamcrest.Matchers.hasItem(2)))
                .andExpect(jsonPath("$.porCategoria[?(@.rotulo=='Elétrica')].total").value(org.hamcrest.Matchers.hasItem(2)))
                .andExpect(jsonPath("$.porFuncionario.length()").value(1))
                .andExpect(jsonPath("$.porFuncionario[0].funcao").value("Zelador"))
                .andExpect(jsonPath("$.porFuncionario[0].atribuidos").value(2))
                .andExpect(jsonPath("$.porFuncionario[0].resolvidos").value(1));

        String csv = mvc.perform(get("/api/sindico/relatorio.csv?de=2020-03-01&ate=2020-03-01").session(sindico))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Total de chamados;4").contains("Tempo médio de resolução (h);32,5").contains("Limpeza;2");
    }

    @Test
    void periodoSemChamadosDevolveZerosENulosEPeriodosInvalidosDao400() throws Exception {
        MockHttpSession sindico = entrar("sindico.teste@nexodesk.test", "Senha-Somente-Para-Testes-1");
        mvc.perform(get("/api/sindico/relatorio?de=2001-01-01&ate=2001-01-10").session(sindico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.tempoMedioResolucaoHoras").doesNotExist())
                .andExpect(jsonPath("$.percentualNoPrazo").doesNotExist());
        mvc.perform(get("/api/sindico/relatorio?de=2020-03-10&ate=2020-03-01").session(sindico)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/sindico/relatorio?de=2018-01-01&ate=2020-03-01").session(sindico)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/sindico/relatorio?de=ontem").session(sindico)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/sindico/relatorio").session(sindico)).andExpect(status().isOk()); // padrão: últimos 30 dias
    }

    @Test
    void soOSindicoVeORelatorio() throws Exception {
        MockHttpSession morador = entrar(cadastrar(), SENHA);
        mvc.perform(get("/api/sindico/relatorio").session(morador)).andExpect(status().isForbidden());
        mvc.perform(get("/api/sindico/relatorio.csv").session(morador)).andExpect(status().isForbidden());
        mvc.perform(get("/api/sindico/relatorio")).andExpect(status().isUnauthorized());
    }
}
