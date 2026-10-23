package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guarda de segurança e acessibilidade das telas. A política CSP (default-src 'self') bloqueia script e
 * estilo inline: se alguém colocar um no HTML, a tela quebra em silêncio no navegador. E texto vindo do
 * servidor nunca pode entrar na página como HTML (XSS). Este teste falha antes disso chegar à banca.
 */
class PaginasEstaticasTest {

    static final Path RAIZ = Path.of("src", "main", "resources", "static");

    static List<Path> arquivos(String extensao) throws IOException {
        try (Stream<Path> s = Files.walk(RAIZ)) {
            return s.filter(p -> p.toString().endsWith(extensao)).sorted().toList();
        }
    }

    static String ler(Path p) throws IOException {
        return Files.readString(p);
    }

    @Test
    void existemAsOitoTelas() throws IOException {
        assertThat(arquivos(".html")).extracting(p -> p.getFileName().toString())
                .containsExactlyInAnyOrder("index.html", "morador.html", "sindico.html", "funcionario.html", "usuarios.html", "relatorios.html", "conta.html", "privacidade.html");
    }

    @Test
    void htmlSemScriptNemEstiloInlineNemRecursoExterno() throws IOException {
        for (Path p : arquivos(".html")) {
            String html = ler(p);
            assertThat(Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>").matcher(html).find())
                    .as("%s tem <script> inline", p.getFileName()).isFalse();
            assertThat(Pattern.compile("<style\\b").matcher(html).find())
                    .as("%s tem <style> inline", p.getFileName()).isFalse();
            assertThat(Pattern.compile("\\sstyle\\s*=").matcher(html).find())
                    .as("%s tem atributo style=", p.getFileName()).isFalse();
            assertThat(Pattern.compile("\\son[a-z]+\\s*=").matcher(html).find())
                    .as("%s tem manipulador inline (onclick=...)", p.getFileName()).isFalse();
            assertThat(Pattern.compile("(?:src|href)\\s*=\\s*\"https?://").matcher(html).find())
                    .as("%s carrega recurso externo", p.getFileName()).isFalse();
        }
    }

    @Test
    void cssSemImportExternoENemUrlExterna() throws IOException {
        for (Path p : arquivos(".css")) {
            String css = ler(p);
            assertThat(css).as("%s", p.getFileName()).doesNotContain("@import").doesNotContain("http://").doesNotContain("https://");
        }
    }

    @Test
    void jsNuncaInsereHtmlNemExecutaTexto() throws IOException {
        List<String> proibidos = List.of("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function(");
        for (Path p : arquivos(".js")) {
            // só o código conta: comentários que explicam "não usamos innerHTML" não podem reprovar
            String js = ler(p)
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("(?m)^\\s*//.*$", "")
                    .replaceAll("(?m)\\s//\\s.*$", "");
            for (String proibido : proibidos) {
                assertThat(js).as("%s usa %s", p.getFileName(), proibido).doesNotContain(proibido);
            }
        }
    }

    @Test
    void todaTelaTemIdiomaTituloLinkParaPularEPainelDeAcessibilidade() throws IOException {
        for (Path p : arquivos(".html")) {
            String html = ler(p);
            String nome = p.getFileName().toString();
            assertThat(html).as(nome).contains("<html lang=\"pt-BR\">").containsPattern("<title>NexoDesk - [^<]+</title>");
            assertThat(html).as(nome).contains("class=\"pular-conteudo\"").contains("id=\"conteudo\"");
            assertThat(html).as(nome).contains("/js/acessibilidade.js").contains("/css/acessibilidade.css");
        }
    }

    @Test
    void todoCampoDeFormularioTemRotulo() throws IOException {
        Pattern campo = Pattern.compile("<(?:input|select|textarea)\\b[^>]*\\bid=\"([^\"]+)\"[^>]*>");
        for (Path p : arquivos(".html")) {
            String html = ler(p);
            var m = campo.matcher(html);
            while (m.find()) {
                String id = m.group(1);
                String tag = m.group();
                if (tag.contains("type=\"hidden\"")) {
                    continue;
                }
                boolean temLabel = html.contains("for=\"" + id + "\"")
                        || html.matches("(?s).*<label[^>]*>[^<]*<input[^>]*\\bid=\"" + Pattern.quote(id) + "\".*")
                        || tag.contains("aria-label=");
                assertThat(temLabel).as("%s: campo '%s' sem rótulo", p.getFileName(), id).isTrue();
            }
        }
    }

    /** Leitor de tela navega por títulos: cada página precisa de um (e só um) h1, e os níveis não podem pular. */
    @Test
    void todaTelaTemUmUnicoH1ETitulosSemPularNivel() throws IOException {
        Pattern titulo = Pattern.compile("<h([1-6])\\b");
        for (Path p : arquivos(".html")) {
            String html = ler(p);
            assertThat(Pattern.compile("<h1\\b").matcher(html).results().count()).as("%s: quantidade de h1", p.getFileName()).isEqualTo(1);
            int anterior = 0;
            var m = titulo.matcher(html);
            while (m.find()) {
                int nivel = Integer.parseInt(m.group(1));
                assertThat(anterior == 0 || nivel <= anterior + 1).as("%s: h%d logo depois de h%d", p.getFileName(), nivel, anterior).isTrue();
                anterior = nivel;
            }
        }
    }
}
