package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.nexodesk.repositorio.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.LocalDate;
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

/** Reabrir chamado, problema que se repete, ausência e escala da equipe, e os números novos do relatório. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReaberturaAusenciaRecorrenciaTest {

    static final String SENHA = "Morador-Teste-123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;

    MockHttpSession entrar(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    String cadastrar(String extra) throws Exception {
        String email = "rr-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa RR\",\"email\":\"%s\",\"senha\":\"%s\"%s}".formatted(email, SENHA, extra)))
                .andExpect(status().isCreated());
        return email;
    }

    MockHttpSession sindico() throws Exception {
        return entrar("sindico.teste@nexodesk.test", "Senha-Somente-Para-Testes-1");
    }

    long id(String email) {
        return usuarios.findByEmail(email).orElseThrow().getId();
    }

    String funcionario(MockHttpSession sindico, String funcao) throws Exception {
        String email = cadastrar("");
        mvc.perform(patch("/api/sindico/usuarios/%d".formatted(id(email))).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"FUNCIONARIO\",\"funcao\":\"%s\"}".formatted(funcao)))
                .andExpect(status().isOk());
        return email;
    }

    int abrir(MockHttpSession m, String categoria, String local, boolean anonimo) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/chamados").with(csrf()).param("titulo", "Chamado de teste").param("descricao", "Descrição do teste.")
                        .param("categoria", categoria).param("localOcorrencia", local).param("anonimo", String.valueOf(anonimo)).session(m))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    void resolver(MockHttpSession sindico, int id) throws Exception {
        mvc.perform(patch("/api/sindico/chamados/%d/status".formatted(id)).with(csrf()).session(sindico)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDO\"}")).andExpect(status().isOk());
    }

    // ---------- reabrir ----------

    @Test
    void moradorReabreChamadoResolvidoERecebeNovoPrazoEOSindicoEAvisado() throws Exception {
        MockHttpSession sindico = sindico();
        MockHttpSession morador = entrar(cadastrar(""), SENHA);
        int id = abrir(morador, "LIMPEZA", "Hall " + UUID.randomUUID(), false);
        resolver(sindico, id);

        mvc.perform(post("/api/chamados/%d/reabrir".formatted(id)).with(csrf()).session(morador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"A sujeira voltou no mesmo dia.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.reaberturas").value(1))
                .andExpect(jsonPath("$.minutosParaPrazo").value(Matchers.greaterThan(60)));
        mvc.perform(get("/api/chamados/%d".formatted(id)).session(morador))
                .andExpect(jsonPath("$.historico[?(@.statusNovo=='Aberto')].observacao")
                        .value(Matchers.hasItem(Matchers.containsString("Reaberto pelo morador: A sujeira voltou"))));
        mvc.perform(get("/api/notificacoes").session(sindico))
                .andExpect(jsonPath("$.itens[?(@.chamadoId==%d)].mensagem".formatted(id))
                        .value(Matchers.hasItem(Matchers.containsString("REABERTO pelo morador"))));
        mvc.perform(get("/api/sindico/chamados").session(sindico))
                .andExpect(jsonPath("$[?(@.id==%d)].reaberturas".formatted(id)).value(Matchers.hasItem(1)));
    }

    @Test
    void reabrirTemRegras() throws Exception {
        MockHttpSession sindico = sindico();
        MockHttpSession morador = entrar(cadastrar(""), SENHA);
        int aberto = abrir(morador, "LIMPEZA", "Corredor", false);
        String corpo = "{\"motivo\":\"Continua com problema.\"}";
        // ainda aberto: não dá para reabrir
        mvc.perform(post("/api/chamados/%d/reabrir".formatted(aberto)).with(csrf()).session(morador)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest());

        int resolvido = abrir(morador, "LIMPEZA", "Corredor", false);
        resolver(sindico, resolvido);
        // outro morador não enxerga o chamado
        mvc.perform(post("/api/chamados/%d/reabrir".formatted(resolvido)).with(csrf()).session(entrar(cadastrar(""), SENHA))
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isNotFound());
        // motivo curto ou vazio
        mvc.perform(post("/api/chamados/%d/reabrir".formatted(resolvido)).with(csrf()).session(morador)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"ruim\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chamados/%d/reabrir".formatted(resolvido)).with(csrf()).session(morador)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        // passou de 30 dias desde a resolução: abra um novo
        jdbc.update("UPDATE chamados SET atualizado_em = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusDays(40)), resolvido);
        mvc.perform(post("/api/chamados/%d/reabrir".formatted(resolvido)).with(csrf()).session(morador)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("30 dias")));
    }

    // ---------- recorrência ----------

    @Test
    void mesmoProblemaNoMesmoLocalTresVezesApareceComoRecorrente() throws Exception {
        MockHttpSession morador = entrar(cadastrar(""), SENHA);
        String local = "Pátio " + UUID.randomUUID().toString().substring(0, 6);
        abrir(morador, "LIMPEZA", local, false);
        abrir(morador, "LIMPEZA", local.toUpperCase(), false);   // maiúsculas também contam
        int terceiro = abrir(morador, "LIMPEZA", local, false);
        int outraCategoria = abrir(morador, "ELETRICA", local, false);
        int outroLocal = abrir(morador, "LIMPEZA", "Garagem " + local, false);

        String fila = mvc.perform(get("/api/sindico/chamados?status=todos").session(sindico())).andReturn().getResponse().getContentAsString();
        assertThat(((List<Number>) JsonPath.read(fila, "$[?(@.id==%d)].recorrencias".formatted(terceiro))).get(0).intValue()).isEqualTo(3);
        assertThat(((List<Number>) JsonPath.read(fila, "$[?(@.id==%d)].recorrencias".formatted(outraCategoria))).get(0).intValue()).isEqualTo(1);
        assertThat(((List<Number>) JsonPath.read(fila, "$[?(@.id==%d)].recorrencias".formatted(outroLocal))).get(0).intValue()).isEqualTo(1);
    }

    // ---------- ausências e escala ----------

    @Test
    void feriasTiramAPessoaDoIndicadoEAusenciaTemRegras() throws Exception {
        MockHttpSession sindico = sindico();
        String eletricista = funcionario(sindico, "ELETRICISTA");
        long idEle = id(eletricista);
        String filtro = "$[?(@.id==%d)]".formatted(idEle);

        mvc.perform(get("/api/sindico/equipe").session(sindico)).andExpect(jsonPath(filtro + ".disponivel").value(Matchers.hasItem(true)));

        String hoje = LocalDate.now().toString();
        String ate = LocalDate.now().plusDays(3).toString();
        String criada = mvc.perform(post("/api/sindico/usuarios/%d/ausencias".formatted(idEle)).with(csrf()).session(sindico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"FERIAS\",\"de\":\"%s\",\"ate\":\"%s\",\"observacao\":\"Viagem\"}".formatted(hoje, ate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipoRotulo").value("Férias"))
                .andReturn().getResponse().getContentAsString();
        int ausenciaId = JsonPath.read(criada, "$.id");

        mvc.perform(get("/api/sindico/equipe").session(sindico))
                .andExpect(jsonPath(filtro + ".disponivel").value(Matchers.hasItem(false)))
                .andExpect(jsonPath(filtro + ".indisponibilidade").value(Matchers.hasItem(Matchers.startsWith("Férias até"))));
        mvc.perform(get("/api/sindico/ausencias").session(sindico))
                .andExpect(jsonPath("$[?(@.id==%d)].nome".formatted(ausenciaId)).isNotEmpty());

        // regras
        mvc.perform(post("/api/sindico/usuarios/%d/ausencias".formatted(idEle)).with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"FOLGA\",\"de\":\"%s\",\"ate\":\"%s\"}".formatted(ate, hoje))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/sindico/usuarios/%d/ausencias".formatted(id(cadastrar("")))).with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"FOLGA\",\"de\":\"%s\",\"ate\":\"%s\"}".formatted(hoje, ate))).andExpect(status().isBadRequest());
        MockHttpSession morador = entrar(cadastrar(""), SENHA);
        mvc.perform(post("/api/sindico/usuarios/%d/ausencias".formatted(idEle)).with(csrf()).session(morador).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"FOLGA\",\"de\":\"%s\",\"ate\":\"%s\"}".formatted(hoje, ate))).andExpect(status().isForbidden());

        // remover devolve a disponibilidade
        mvc.perform(delete("/api/sindico/ausencias/%d".formatted(ausenciaId)).with(csrf()).session(sindico)).andExpect(status().isNoContent());
        mvc.perform(get("/api/sindico/equipe").session(sindico)).andExpect(jsonPath(filtro + ".disponivel").value(Matchers.hasItem(true)));
        mvc.perform(delete("/api/sindico/ausencias/%d".formatted(ausenciaId)).with(csrf()).session(sindico)).andExpect(status().isNotFound());
    }

    @Test
    void escalaSemanalMarcaFolgaNosDiasQueNaoTrabalha() throws Exception {
        MockHttpSession sindico = sindico();
        String porteiro = funcionario(sindico, "PORTEIRO");
        long idP = id(porteiro);
        String filtro = "$[?(@.id==%d)]".formatted(idP);
        int hoje = LocalDate.now().getDayOfWeek().getValue();
        int outroDia = hoje % 7 + 1;

        // trabalha só em outro dia da semana: hoje é folga
        mvc.perform(put("/api/sindico/usuarios/%d/escala".formatted(idP)).with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dias\":[%d]}".formatted(outroDia)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diasTrabalho").value(String.valueOf(outroDia)));
        mvc.perform(get("/api/sindico/equipe").session(sindico))
                .andExpect(jsonPath(filtro + ".disponivel").value(Matchers.hasItem(false)))
                .andExpect(jsonPath(filtro + ".indisponibilidade").value(Matchers.hasItem("Folga hoje (escala)")));

        // os sete dias tiram a restrição; lista vazia ou dia inválido são recusados
        mvc.perform(put("/api/sindico/usuarios/%d/escala".formatted(idP)).with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dias\":[1,2,3,4,5,6,7]}")).andExpect(status().isOk()).andExpect(jsonPath("$.diasTrabalho").doesNotExist());
        mvc.perform(get("/api/sindico/equipe").session(sindico)).andExpect(jsonPath(filtro + ".disponivel").value(Matchers.hasItem(true)));
        mvc.perform(put("/api/sindico/usuarios/%d/escala".formatted(idP)).with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dias\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/sindico/usuarios/%d/escala".formatted(idP)).with(csrf()).session(sindico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dias\":[8]}")).andExpect(status().isBadRequest());
    }

    // ---------- relatório: por bloco e problemas que se repetem ----------

    @Test
    void relatorioAgrupaPorBlocoEMostraProblemasQueSeRepetem() throws Exception {
        MockHttpSession sindico = sindico();
        MockHttpSession morador = entrar(cadastrar(",\"bloco\":\"Q\",\"apartamento\":\"1\""), SENHA);
        String local = "Subsolo " + UUID.randomUUID().toString().substring(0, 6);
        int a = abrir(morador, "HIDRAULICA", local, false);
        int b = abrir(morador, "HIDRAULICA", local, false);
        int c = abrir(morador, "BARULHO", "Salão", true);   // anônimo: o bloco não aparece
        for (int id : new int[] {a, b, c}) {
            jdbc.update("UPDATE chamados SET criado_em = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.of(2020, 4, 1, 9, 0)), id);
        }

        mvc.perform(get("/api/sindico/relatorio?de=2020-04-01&ate=2020-04-01").session(sindico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.porBloco[?(@.rotulo=='Bloco Q')].total").value(Matchers.hasItem(2)))
                .andExpect(jsonPath("$.porBloco[?(@.rotulo=='Anônimo (bloco oculto)')].total").value(Matchers.hasItem(1)))
                .andExpect(jsonPath("$.locaisRecorrentes.length()").value(1))
                .andExpect(jsonPath("$.locaisRecorrentes[0].total").value(2))
                .andExpect(jsonPath("$.locaisRecorrentes[0].rotulo").value(Matchers.containsString(local)));
    }
}
