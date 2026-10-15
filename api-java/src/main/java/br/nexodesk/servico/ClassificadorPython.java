package br.nexodesk.servico;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.dominio.Dificuldade;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Chama o serviço Python (POST /classificar). Qualquer falha cai no ClassificadorLocal. */
@Service
public class ClassificadorPython implements ClassificadorDificuldade {

    private static final Logger log = LoggerFactory.getLogger(ClassificadorPython.class);

    record RespostaPython(String dificuldade, int pontuacao, List<String> justificativa) {
    }

    private final RestClient cliente;
    private final ClassificadorLocal reserva = new ClassificadorLocal();

    public ClassificadorPython(@Value("${nexodesk.prioridade.url}") String url,
                               @Value("${nexodesk.prioridade.timeout-ms}") long timeoutMs) {
        // HTTP/1.1 de propósito: por padrão o cliente do JDK tenta subir para HTTP/2 (cabeçalho
        // Upgrade: h2c) e o uvicorn recusa, respondendo 422 mesmo com o corpo correto.
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.cliente = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
    }

    @Override
    public ResultadoClassificacao classificar(Categoria categoria, String titulo, String descricao) {
        try {
            RespostaPython r = cliente.post()
                    .uri("/classificar")
                    .body(Map.of("categoria", categoria.name(), "titulo", titulo, "descricao", descricao))
                    .retrieve()
                    .body(RespostaPython.class);
            if (r == null || r.pontuacao() < 1 || r.pontuacao() > 10 || r.justificativa() == null) {
                throw new IllegalStateException("resposta inválida do serviço de prioridade");
            }
            return new ResultadoClassificacao(Dificuldade.valueOf(r.dificuldade()), r.pontuacao(),
                    r.justificativa(), false);
        } catch (Exception e) {
            log.warn("Serviço de prioridade indisponível ({}); usando classificação só pela categoria.",
                    e.getClass().getSimpleName());
            return reserva.classificar(categoria, titulo, descricao);
        }
    }
}
