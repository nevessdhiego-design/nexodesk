package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.servico.ClassificadorDificuldade;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Se o chamado não chega a ser gravado, a foto que já foi para o disco não pode ficar para trás. */
@SpringBootTest(properties = "nexodesk.uploads.dir=target/test-uploads-orfa")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImagemOrfaTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ClassificadorDificuldade classificador;

    @Test
    void falhaDepoisDeSalvarAFotoNaoDeixaArquivoOrfao() throws Exception {
        Mockito.when(classificador.classificar(Mockito.any(Categoria.class), Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new IllegalStateException("falha simulada depois de salvar a foto"));

        String email = "orfa-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Orfa\",\"email\":\"%s\",\"senha\":\"Senha-Orfa-123\"}".formatted(email)))
                .andExpect(status().isCreated());
        MockHttpSession sessao = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"Senha-Orfa-123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        Path pasta = Path.of("target", "test-uploads-orfa");
        long antes = contar(pasta);

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        mvc.perform(multipart("/api/chamados").with(csrf()).session(sessao)
                        .file(new MockMultipartFile("imagem", "f.png", "image/png", png.toByteArray()))
                        .param("titulo", "Vai falhar").param("descricao", "d").param("categoria", "OUTROS"))
                .andExpect(status().isInternalServerError());

        assertThat(contar(pasta)).as("a foto salva antes da falha deve ter sido apagada").isEqualTo(antes);
    }

    static long contar(Path pasta) throws Exception {
        if (!Files.exists(pasta)) {
            return 0;
        }
        try (var s = Files.list(pasta)) {
            return s.count();
        }
    }
}
