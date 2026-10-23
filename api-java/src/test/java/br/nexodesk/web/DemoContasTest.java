package br.nexodesk.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Contas de demonstração para a tela de login: só no perfil demo, sempre sem senha. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:democontas;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "nexodesk.demo.senha=Demo-Somente-Para-Testes-1"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
class DemoContasTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listaUmaContaPorFuncaoSemNuncaDevolverSenha() throws Exception {
        String json = mvc.perform(get("/api/demo/contas"))   // sem login: é a tela de login que pede
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='seguranca.demo@nexodesk.local')].rotulo").value(org.hamcrest.Matchers.hasItem("Sérgio · Segurança / vigilante")))
                .andExpect(jsonPath("$[?(@.email=='ana.demo@nexodesk.local')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("Demo-Somente-Para-Testes-1").doesNotContain("senha").doesNotContain("hash");
    }
}
