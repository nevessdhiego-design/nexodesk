package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prova de comportamento (não só de configuração): sobe a API de verdade numa porta aleatória com o perfil "prod"
 * e confere que o cookie de sessão sai com o atributo Secure e HttpOnly depois de um login real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "NEXODESK_URL_PUBLICA=https://nexodesk.exemplo.test",
        "spring.datasource.url=jdbc:h2:mem:prodtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles({"test", "prod"})
class CookieSeguroProdTest {

    @LocalServerPort
    int porta;

    @Value("${nexodesk.url-publica}")
    String urlPublica;

    @Test
    void urlPublicaVemDaVariavelDeAmbiente() {
        assertThat(urlPublica).isEqualTo("https://nexodesk.exemplo.test");
    }

    @Test
    void cookieDeSessaoSaiSecureEHttpOnlyDepoisDoLogin() throws Exception {
        CookieManager jar = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient http = HttpClient.newBuilder().cookieHandler(jar).build();
        String base = "http://localhost:" + porta;

        // pega o cookie de CSRF
        http.send(HttpRequest.newBuilder(URI.create(base + "/index.html")).build(), HttpResponse.BodyHandlers.discarding());
        String csrf = jar.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();

        String email = "prod-" + UUID.randomUUID().toString().substring(0, 8) + "@nexodesk.test";
        String corpoCadastro = "{\"nome\":\"Prod\",\"email\":\"%s\",\"senha\":\"Senha-Prod-12345\"}".formatted(email);
        HttpResponse<String> cadastro = http.send(post(base + "/api/auth/register", csrf, corpoCadastro),
                HttpResponse.BodyHandlers.ofString());
        assertThat(cadastro.statusCode()).isEqualTo(201);

        HttpResponse<String> login = http.send(
                post(base + "/api/auth/login", csrf,
                        "{\"email\":\"%s\",\"senha\":\"Senha-Prod-12345\"}".formatted(email)),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);

        List<String> cookies = login.headers().allValues("Set-Cookie");
        String sessao = cookies.stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        assertThat(sessao).containsIgnoringCase("; Secure").containsIgnoringCase("; HttpOnly")
                .containsIgnoringCase("SameSite=Lax");
    }

    private static HttpRequest post(String url, String csrf, String json) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
