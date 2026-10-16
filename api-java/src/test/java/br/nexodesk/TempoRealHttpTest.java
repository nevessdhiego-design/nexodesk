package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tempo real de verdade: sobe a API numa porta aleatória e usa conexões HTTP reais (cookie de sessão, CSRF e o fluxo
 * {@code text/event-stream}). Prova o que os testes simulados não alcançam: o aviso chega sem recarregar, só para quem
 * deve receber, e a conexão cai quando a sessão deixa de valer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:temporeal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class TempoRealHttpTest {

    static final String SINDICO_EMAIL = "sindico.teste@nexodesk.test";
    static final String SINDICO_SENHA = "Senha-Somente-Para-Testes-1";
    static final String SENHA = "Morador-RT-12345";

    @LocalServerPort
    int porta;

    /** Um "navegador": cookies próprios, login, POST com CSRF e leitura do fluxo de eventos. */
    class Navegador {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        final String base = "http://localhost:" + porta;

        String csrf() throws Exception {
            if (cookies.getCookieStore().getCookies().stream().noneMatch(c -> c.getName().equals("XSRF-TOKEN"))) {
                http.send(HttpRequest.newBuilder(URI.create(base + "/index.html")).build(), HttpResponse.BodyHandlers.discarding());
            }
            return cookies.getCookieStore().getCookies().stream()
                    .filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
        }

        HttpResponse<String> postJson(String caminho, String json) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create(base + caminho))
                    .header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrf())
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> patchJson(String caminho, String json) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create(base + caminho))
                    .header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrf())
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
        }

        void entrar(String email, String senha) throws Exception {
            assertThat(postJson("/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)).statusCode())
                    .isEqualTo(200);
        }

        String cadastrarEEntrar() throws Exception {
            String email = "rt-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
            assertThat(postJson("/api/auth/register",
                    "{\"nome\":\"Morador RT\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)).statusCode()).isEqualTo(201);
            entrar(email, SENHA);
            return email;
        }

        /** Abre um chamado (multipart) e devolve o id. */
        long abrirChamado(String titulo, String categoria, boolean emergencia) throws Exception {
            String limite = "----nexodesk" + UUID.randomUUID();
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("titulo", titulo);
            campos.put("descricao", "Descrição do chamado de tempo real.");
            campos.put("categoria", categoria);
            campos.put("emergencia", String.valueOf(emergencia));
            StringBuilder corpo = new StringBuilder();
            campos.forEach((k, v) -> corpo.append("--").append(limite).append("\r\nContent-Disposition: form-data; name=\"")
                    .append(k).append("\"\r\n\r\n").append(v).append("\r\n"));
            corpo.append("--").append(limite).append("--\r\n");
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/api/chamados"))
                    .header("Content-Type", "multipart/form-data; boundary=" + limite).header("X-XSRF-TOKEN", csrf())
                    .POST(HttpRequest.BodyPublishers.ofString(corpo.toString())).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(r.body());
            assertThat(m.find()).isTrue();
            return Long.parseLong(m.group(1));
        }

        Fluxo abrirFluxo() throws Exception {
            HttpResponse<Stream<String>> resposta = http.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/eventos")).header("Accept", "text/event-stream").build(),
                    HttpResponse.BodyHandlers.ofLines());
            return new Fluxo(resposta);
        }
    }

    /** Lê o fluxo numa thread própria e guarda as linhas numa fila para o teste conferir. */
    static class Fluxo {
        final HttpResponse<Stream<String>> resposta;
        final BlockingQueue<String> linhas = new LinkedBlockingQueue<>();
        final AtomicBoolean terminou = new AtomicBoolean(false);

        Fluxo(HttpResponse<Stream<String>> resposta) {
            this.resposta = resposta;
            Thread t = new Thread(() -> {
                try (Stream<String> s = resposta.body()) {
                    s.forEach(linhas::add);
                } catch (RuntimeException e) {
                    // conexão fechada pelo servidor
                } finally {
                    terminou.set(true);
                }
            }, "leitor-sse");
            t.setDaemon(true);
            t.start();
        }

        /** Espera uma linha que contenha o texto (até 5 s). */
        String esperar(String trecho) throws InterruptedException {
            long fim = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < fim) {
                String linha = linhas.poll(200, TimeUnit.MILLISECONDS);
                if (linha != null && linha.contains(trecho)) {
                    return linha;
                }
            }
            throw new AssertionError("o aviso com '" + trecho + "' não chegou em 5 s");
        }

        /** Recebeu algum aviso de atualização nos próximos 1,5 s? */
        boolean recebeuAtualizacao() throws InterruptedException {
            long fim = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
            while (System.nanoTime() < fim) {
                String linha = linhas.poll(100, TimeUnit.MILLISECONDS);
                if (linha != null && linha.contains("atualizacao")) {
                    return true;
                }
            }
            return false;
        }

        boolean esperarFim() throws InterruptedException {
            long fim = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < fim && !terminou.get()) {
                Thread.sleep(100);
            }
            return terminou.get();
        }
    }

    @Test
    void semLoginOFluxoEhRecusado() throws Exception {
        Navegador anonimo = new Navegador();
        HttpResponse<String> r = anonimo.http.send(
                HttpRequest.newBuilder(URI.create(anonimo.base + "/api/eventos")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(401);
    }

    @Test
    void sindicoVeChamadoNovoEEmergenciaSemRecarregar() throws Exception {
        Navegador sindico = new Navegador();
        sindico.entrar(SINDICO_EMAIL, SINDICO_SENHA);
        Fluxo fluxo = sindico.abrirFluxo();
        assertThat(fluxo.resposta.statusCode()).isEqualTo(200);
        assertThat(fluxo.resposta.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        fluxo.esperar("pronto");

        Navegador morador = new Navegador();
        morador.cadastrarEEntrar();
        long comum = morador.abrirChamado("Lâmpada queimada", "ILUMINACAO", false);
        assertThat(fluxo.esperar("atualizacao")).isNotNull();
        String dados = fluxo.esperar("\"chamadoId\":" + comum);
        assertThat(dados).contains("\"tipo\":\"chamado-novo\"");

        long emergencia = morador.abrirChamado("Pessoa presa no elevador", "ELEVADOR", true);
        String aviso = fluxo.esperar("\"chamadoId\":" + emergencia);
        assertThat(aviso).contains("\"tipo\":\"emergencia\"");
        // o aviso é só um sinal: não carrega título, texto nem nome de ninguém
        assertThat(aviso).doesNotContain("elevador").doesNotContain("Morador RT");
    }

    @Test
    void moradorVeOStatusMudarENinguemMaisRecebe() throws Exception {
        Navegador dono = new Navegador();
        dono.cadastrarEEntrar();
        Navegador intruso = new Navegador();
        intruso.cadastrarEEntrar();
        Fluxo fluxoDono = dono.abrirFluxo();
        Fluxo fluxoIntruso = intruso.abrirFluxo();
        fluxoDono.esperar("pronto");
        fluxoIntruso.esperar("pronto");

        long id = dono.abrirChamado("Portão da garagem", "PORTARIA", false);

        Navegador sindico = new Navegador();
        sindico.entrar(SINDICO_EMAIL, SINDICO_SENHA);
        assertThat(sindico.patchJson("/api/sindico/chamados/" + id + "/status", "{\"status\":\"EM_ANDAMENTO\"}").statusCode())
                .isEqualTo(200);

        String aviso = fluxoDono.esperar("\"chamadoId\":" + id);
        assertThat(aviso).contains("\"tipo\":\"status\"");
        assertThat(fluxoIntruso.recebeuAtualizacao()).as("outro morador não pode ser avisado do chamado alheio").isFalse();
    }

    @Test
    void mensagemAvisaSoAOutraParte() throws Exception {
        Navegador dono = new Navegador();
        dono.cadastrarEEntrar();
        long id = dono.abrirChamado("Barulho no teto", "BARULHO", false);
        Navegador sindico = new Navegador();
        sindico.entrar(SINDICO_EMAIL, SINDICO_SENHA);
        Fluxo fluxoDono = dono.abrirFluxo();
        Fluxo fluxoSindico = sindico.abrirFluxo();
        fluxoDono.esperar("pronto");
        fluxoSindico.esperar("pronto");
        fluxoSindico.linhas.clear();   // descarta o aviso de "chamado novo" que já estava a caminho
        fluxoDono.linhas.clear();

        assertThat(dono.postJson("/api/chamados/" + id + "/mensagens", "{\"texto\":\"Pode vir hoje?\"}").statusCode()).isEqualTo(201);

        assertThat(fluxoSindico.esperar("\"tipo\":\"mensagem\"")).contains("\"chamadoId\":" + id);
        assertThat(fluxoDono.recebeuAtualizacao()).as("quem escreveu não é avisado da própria mensagem").isFalse();
    }

    @Test
    void trocarASenhaFechaOFluxoDasOutrasSessoes() throws Exception {
        Navegador celular = new Navegador();
        String email = celular.cadastrarEEntrar();
        Fluxo fluxoDoCelular = celular.abrirFluxo();
        fluxoDoCelular.esperar("pronto");

        Navegador computador = new Navegador();   // outra sessão do mesmo usuário
        computador.entrar(email, SENHA);
        assertThat(computador.postJson("/api/auth/senha",
                "{\"senhaAtual\":\"%s\",\"novaSenha\":\"Senha-Nova-RT-98765\"}".formatted(SENHA)).statusCode()).isEqualTo(204);

        assertThat(fluxoDoCelular.esperarFim()).as("o fluxo da sessão antiga precisa ser encerrado").isTrue();
        // e a sessão antiga não consegue abrir outro
        HttpResponse<String> r = celular.http.send(
                HttpRequest.newBuilder(URI.create(celular.base + "/api/eventos")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(401);
    }
}
