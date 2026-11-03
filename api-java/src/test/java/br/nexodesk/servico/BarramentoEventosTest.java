package br.nexodesk.servico;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Duas "instâncias" (dois publicadores com seus barramentos) compartilhando um mesmo banco: o aviso de uma chega à outra. */
class BarramentoEventosTest {

    static class Captura extends SseEmitter {
        final List<String> enviados = new CopyOnWriteArrayList<>();
        volatile boolean completo;

        Captura() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder construtor) throws IOException {
            StringBuilder texto = new StringBuilder();
            construtor.build().forEach(parte -> texto.append(parte.getData()));
            enviados.add(texto.toString());
        }

        @Override
        public synchronized void complete() {
            completo = true;
        }
    }

    static class Instancia {
        final List<Captura> conexoes = new ArrayList<>();
        final PublicadorEventos publicador = new PublicadorEventos(() -> {
            Captura c = new Captura();
            conexoes.add(c);
            return c;
        });
        BarramentoEventos barramento;

        Instancia(JdbcTemplate jdbc, String nome) {
            barramento = new BarramentoEventos(jdbc, publicador, nome);
            publicador.setBarramento(barramento);
            barramento.iniciar();
        }
    }

    JdbcTemplate banco() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:barramento-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE eventos_tempo_real (id BIGINT AUTO_INCREMENT PRIMARY KEY, instancia VARCHAR(40) NOT NULL, "
                + "destinatario VARCHAR(160) NOT NULL, tipo VARCHAR(30) NOT NULL, chamado_id BIGINT NOT NULL, "
                + "criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        return jdbc;
    }

    @Test
    void avisoGeradoNaInstanciaAChegaAoNavegadorConectadoNaInstanciaB() {
        JdbcTemplate jdbc = banco();
        Instancia a = new Instancia(jdbc, "A");
        Instancia b = new Instancia(jdbc, "B");
        b.publicador.registrar("ana@x.test");                 // a Ana está conectada na instância B
        Captura daAna = b.conexoes.get(0);
        daAna.enviados.clear();

        a.publicador.notificar("ana@x.test", "status", 42L);  // o síndico mudou o status na instância A

        assertThat(daAna.enviados).as("ainda não chegou: só depois da leitura da B").isEmpty();
        b.barramento.ler();
        assertThat(daAna.enviados).hasSize(1);
        assertThat(daAna.enviados.get(0)).contains("\"tipo\":\"status\"").contains("42");
        // só tipo e ids passam pelo barramento
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eventos_tempo_real WHERE destinatario = 'ana@x.test'", Integer.class)).isEqualTo(1);
    }

    @Test
    void ainstanciaQueGerouNaoEntregaDeNovoELeituraRepetidaNaoDuplica() {
        JdbcTemplate jdbc = banco();
        Instancia a = new Instancia(jdbc, "A");
        Instancia b = new Instancia(jdbc, "B");
        a.publicador.registrar("bia@x.test");
        Captura daBia = a.conexoes.get(0);
        daBia.enviados.clear();

        a.publicador.notificar("bia@x.test", "mensagem", 7L);   // entrega local imediata
        a.barramento.ler();                                     // a própria linha é ignorada
        a.barramento.ler();
        b.barramento.ler();                                     // a outra instância não tem a Bia conectada: nada acontece

        assertThat(daBia.enviados).hasSize(1);
    }

    @Test
    void trocarASenhaEmUmaInstanciaFechaOsFluxosDoUsuarioNaOutra() {
        JdbcTemplate jdbc = banco();
        Instancia a = new Instancia(jdbc, "A");
        Instancia b = new Instancia(jdbc, "B");
        b.publicador.registrar("caio@x.test");
        Captura doCaio = b.conexoes.get(0);

        a.publicador.encerrar("caio@x.test");
        b.barramento.ler();

        assertThat(doCaio.completo).isTrue();
        assertThat(b.publicador.conexoes("caio@x.test")).isZero();
    }

    @Test
    void instanciaNovaSoVeOQueAconteceDepoisDeSubir() {
        JdbcTemplate jdbc = banco();
        Instancia a = new Instancia(jdbc, "A");
        a.publicador.notificar("dani@x.test", "status", 1L);   // evento antigo, antes de a B existir

        Instancia b = new Instancia(jdbc, "B");
        b.publicador.registrar("dani@x.test");
        Captura daDani = b.conexoes.get(0);
        daDani.enviados.clear();
        b.barramento.ler();

        assertThat(daDani.enviados).isEmpty();
    }

    @Test
    void falhaNoBancoNaoDerrubaOAvisoLocal() {
        JdbcTemplate jdbc = banco();
        Instancia a = new Instancia(jdbc, "A");
        a.publicador.registrar("edu@x.test");
        Captura doEdu = a.conexoes.get(0);
        doEdu.enviados.clear();
        jdbc.execute("DROP TABLE eventos_tempo_real");

        a.publicador.notificar("edu@x.test", "status", 3L);   // a gravação no barramento falha, mas a entrega local acontece

        assertThat(doEdu.enviados).hasSize(1);
    }
}
