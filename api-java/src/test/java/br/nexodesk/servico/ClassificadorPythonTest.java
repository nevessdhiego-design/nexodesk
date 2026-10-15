package br.nexodesk.servico;

import static org.assertj.core.api.Assertions.assertThat;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.dominio.Dificuldade;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Testa o cliente do serviço Python com um servidor falso local (sem precisar do Python rodando). */
class ClassificadorPythonTest {

    HttpServer servidor;

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    String subir(int codigo, String corpo) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/classificar", troca -> {
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(codigo, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @Test
    void usaARespostaDoServicoQuandoValida() throws IOException {
        String url = subir(200, "{\"dificuldade\":\"DIFICIL\",\"pontuacao\":9,\"justificativa\":[\"categoria ELEVADOR: base 8\",\"termo 'preso': +2\"]}");
        ResultadoClassificacao r = new ClassificadorPython(url, 1000)
                .classificar(Categoria.ELEVADOR, "Elevador parado", "gente presa");
        assertThat(r.fallback()).isFalse();
        assertThat(r.dificuldade()).isEqualTo(Dificuldade.DIFICIL);
        assertThat(r.pontuacao()).isEqualTo(9);
        assertThat(r.justificativa()).hasSize(2);
    }

    /** Regressão: o uvicorn real recusa o Upgrade h2c do cliente padrão do JDK (erro 422). */
    @Test
    void enviaJsonEmHttp11SemPedirUpgradeParaHttp2() throws IOException {
        String[] visto = new String[2]; // [0] cabeçalho Upgrade, [1] corpo
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/classificar", troca -> {
            visto[0] = troca.getRequestHeaders().getFirst("Upgrade");
            visto[1] = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = "{\"dificuldade\":\"FACIL\",\"pontuacao\":2,\"justificativa\":[]}".getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort();

        new ClassificadorPython(url, 1000).classificar(Categoria.ELEVADOR, "Elevador parado", "gente presa");

        assertThat(visto[0]).isNull();
        assertThat(visto[1]).contains("\"categoria\":\"ELEVADOR\"").contains("\"titulo\":\"Elevador parado\"");
    }

    @Test
    void caiNaReservaQuandoOServicoDa500() throws IOException {
        String url = subir(500, "{}");
        ResultadoClassificacao r = new ClassificadorPython(url, 1000)
                .classificar(Categoria.ILUMINACAO, "t", "d");
        assertThat(r.fallback()).isTrue();
        assertThat(r.pontuacao()).isEqualTo(Categoria.ILUMINACAO.getPontuacaoBase());
    }

    @Test
    void caiNaReservaQuandoARespostaTemPontuacaoForaDaFaixa() throws IOException {
        String url = subir(200, "{\"dificuldade\":\"FACIL\",\"pontuacao\":99,\"justificativa\":[]}");
        assertThat(new ClassificadorPython(url, 1000).classificar(Categoria.OUTROS, "t", "d").fallback()).isTrue();
    }

    @Test
    void caiNaReservaQuandoADificuldadeEhDesconhecida() throws IOException {
        String url = subir(200, "{\"dificuldade\":\"IMPOSSIVEL\",\"pontuacao\":5,\"justificativa\":[]}");
        assertThat(new ClassificadorPython(url, 1000).classificar(Categoria.OUTROS, "t", "d").fallback()).isTrue();
    }

    @Test
    void caiNaReservaQuandoOServicoEstaForaDoAr() {
        ResultadoClassificacao r = new ClassificadorPython("http://127.0.0.1:9", 300)
                .classificar(Categoria.ELEVADOR, "t", "d");
        assertThat(r.fallback()).isTrue();
        assertThat(r.dificuldade()).isEqualTo(Dificuldade.DIFICIL);
    }
}
