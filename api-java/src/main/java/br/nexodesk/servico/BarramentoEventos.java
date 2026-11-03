package br.nexodesk.servico;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Faz o tempo real funcionar com <b>mais de uma instância</b> da API sem Redis: o banco (que já é compartilhado) serve de
 * barramento. Quando uma instância avisa um usuário, ela grava uma linha em {@code eventos_tempo_real} e entrega às
 * conexões <i>dela</i>; a cada segundo, cada instância lê as linhas novas gravadas pelas <i>outras</i> e entrega às suas
 * conexões. Atraso entre instâncias: cerca de 1 s. Só tipo e ids passam por aqui (o navegador recarrega pela API normal,
 * que confere quem pode ver o quê).
 *
 * <p>Limites: depende de as instâncias usarem o mesmo banco (MySQL; um H2 em arquivo serve a uma instância só) e adiciona uma
 * leitura por segundo por instância. Desligue com {@code nexodesk.tempo-real.barramento=false} se houver uma instância só.
 */
@Component
@ConditionalOnProperty(name = "nexodesk.tempo-real.barramento", havingValue = "true", matchIfMissing = true)
public class BarramentoEventos {

    static final String ENCERRAR = "encerrar";
    private static final Logger log = LoggerFactory.getLogger(BarramentoEventos.class);

    private final JdbcTemplate jdbc;
    private final PublicadorEventos publicador;
    private final String instancia;
    private volatile long ultimoId;
    private int ciclos;

    @org.springframework.beans.factory.annotation.Autowired
    public BarramentoEventos(JdbcTemplate jdbc, PublicadorEventos publicador) {
        this(jdbc, publicador, UUID.randomUUID().toString().substring(0, 8));
    }

    /** Permite ao teste simular duas instâncias com o mesmo banco. */
    public BarramentoEventos(JdbcTemplate jdbc, PublicadorEventos publicador, String instancia) {
        this.jdbc = jdbc;
        this.publicador = publicador;
        this.instancia = instancia;
    }

    @PostConstruct
    void iniciar() {
        Long maximo = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM eventos_tempo_real", Long.class);
        ultimoId = maximo == null ? 0 : maximo;   // só interessa o que acontecer daqui para frente
    }

    /** Grava o aviso para as outras instâncias (a instância atual já entregou às conexões dela). */
    public void publicar(String destinatario, String tipo, Long chamadoId) {
        try {
            jdbc.update("INSERT INTO eventos_tempo_real (instancia, destinatario, tipo, chamado_id) VALUES (?, ?, ?, ?)",
                    instancia, destinatario, tipo, chamadoId == null ? 0L : chamadoId);
        } catch (RuntimeException e) {
            log.warn("Não foi possível publicar o aviso de tempo real para as outras instâncias: {}", e.getClass().getSimpleName());
        }
    }

    /** Lê o que as outras instâncias gravaram e entrega às conexões desta. Roda a cada segundo. */
    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void ler() {
        try {
            List<Object[]> novos = jdbc.query(
                    "SELECT id, instancia, destinatario, tipo, chamado_id FROM eventos_tempo_real WHERE id > ? ORDER BY id",
                    (rs, n) -> new Object[] {rs.getLong("id"), rs.getString("instancia"), rs.getString("destinatario"),
                            rs.getString("tipo"), rs.getLong("chamado_id")}, ultimoId);
            for (Object[] e : novos) {
                ultimoId = (Long) e[0];
                if (instancia.equals(e[1])) {
                    continue;   // foi esta instância que gerou: já entregou
                }
                if (ENCERRAR.equals(e[3])) {
                    publicador.encerrarLocal((String) e[2]);
                } else {
                    publicador.notificarLocal((String) e[2], (String) e[3], (Long) e[4]);
                }
            }
            if (++ciclos % 60 == 0) {   // a cada ~1 min apaga o que tem mais de 5 min
                jdbc.update("DELETE FROM eventos_tempo_real WHERE criado_em < ?",
                        java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(5)));
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao ler avisos de tempo real das outras instâncias: {}", e.getClass().getSimpleName());
        }
    }
}
