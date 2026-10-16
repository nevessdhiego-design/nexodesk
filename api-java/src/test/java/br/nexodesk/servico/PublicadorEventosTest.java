package br.nexodesk.servico;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class PublicadorEventosTest {

    /** Emissor que anota o que foi enviado em vez de escrever numa conexão HTTP. */
    static class Captura extends SseEmitter {
        final List<String> enviados = new CopyOnWriteArrayList<>();
        volatile boolean completo;
        volatile boolean morto;

        Captura() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder construtor) throws IOException {
            if (morto) {
                throw new IOException("a conexão caiu");
            }
            StringBuilder texto = new StringBuilder();
            construtor.build().forEach(parte -> texto.append(parte.getData()));
            enviados.add(texto.toString());
        }

        @Override
        public synchronized void complete() {
            completo = true;
        }
    }

    final List<Captura> criados = new ArrayList<>();
    final PublicadorEventos publicador = new PublicadorEventos(() -> {
        Captura c = new Captura();
        criados.add(c);
        return c;
    });

    @Test
    void aoConectarEnviaOEventoPronto() {
        publicador.registrar("ana@x.test");
        assertThat(criados.get(0).enviados).hasSize(1);
        assertThat(criados.get(0).enviados.get(0)).contains("event:pronto").contains("ok").contains("retry:5000");
        assertThat(publicador.conexoes("ana@x.test")).isEqualTo(1);
    }

    @Test
    void avisoVaiSoParaOUsuarioCertoESemConteudoSensivel() {
        publicador.registrar("ana@x.test");
        publicador.registrar("bruno@x.test");

        publicador.notificar("ana@x.test", "status", 7L);

        Captura ana = criados.get(0);
        Captura bruno = criados.get(1);
        assertThat(ana.enviados).hasSize(2);
        assertThat(ana.enviados.get(1)).contains("event:atualizacao").contains("\"tipo\":\"status\"").contains("\"chamadoId\":7");
        assertThat(bruno.enviados).as("o outro usuário só recebeu o 'pronto'").hasSize(1);
    }

    @Test
    void notificarUmConjuntoAvisaCadaUmUmaVez() {
        publicador.registrar("a@x.test");
        publicador.registrar("b@x.test");
        publicador.registrar("c@x.test");
        publicador.notificar(Set.of("a@x.test", "b@x.test"), "chamado-novo", 3L);
        assertThat(criados.get(0).enviados).hasSize(2);
        assertThat(criados.get(1).enviados).hasSize(2);
        assertThat(criados.get(2).enviados).hasSize(1);
    }

    @Test
    void usuarioSemConexaoNaoDaErro() {
        publicador.notificar("ninguem@x.test", "status", 1L);
        assertThat(publicador.conexoes("ninguem@x.test")).isZero();
    }

    @Test
    void asVariasAbasDoMesmoUsuarioTodasRecebem() {
        publicador.registrar("ana@x.test");
        publicador.registrar("ana@x.test");
        publicador.notificar("ana@x.test", "mensagem", 2L);
        assertThat(criados.get(0).enviados).hasSize(2);
        assertThat(criados.get(1).enviados).hasSize(2);
    }

    @Test
    void passandoDoLimiteDeConexoesAMaisAntigaEEncerrada() {
        for (int i = 0; i < PublicadorEventos.MAXIMO_POR_USUARIO + 2; i++) {
            publicador.registrar("ana@x.test");
        }
        assertThat(publicador.conexoes("ana@x.test")).isEqualTo(PublicadorEventos.MAXIMO_POR_USUARIO);
        assertThat(criados.get(0).completo).as("a mais antiga foi fechada").isTrue();
        assertThat(criados.get(1).completo).isTrue();
        assertThat(criados.get(2).completo).as("as recentes continuam abertas").isFalse();
    }

    @Test
    void conexaoQueFalhaNoEnvioERemovidaSemDerrubarAsOutras() {
        publicador.registrar("ana@x.test");
        publicador.registrar("ana@x.test");
        criados.get(0).morto = true;

        publicador.notificar("ana@x.test", "status", 1L);

        assertThat(publicador.conexoes("ana@x.test")).isEqualTo(1);
        assertThat(criados.get(1).enviados).hasSize(2);
    }

    @Test
    void encerrarFechaTudoDoUsuarioEDeixaOsOutrosEmPaz() {
        publicador.registrar("ana@x.test");
        publicador.registrar("ana@x.test");
        publicador.registrar("bruno@x.test");

        publicador.encerrar("ana@x.test");

        assertThat(criados.get(0).completo).isTrue();
        assertThat(criados.get(1).completo).isTrue();
        assertThat(criados.get(2).completo).isFalse();
        assertThat(publicador.conexoes("ana@x.test")).isZero();
        assertThat(publicador.conexoes("bruno@x.test")).isEqualTo(1);
    }

    @Test
    void batimentoEnviaComentarioEDescartaConexaoMorta() {
        publicador.registrar("ana@x.test");
        publicador.registrar("bruno@x.test");
        criados.get(1).morto = true;

        publicador.batimento();

        assertThat(criados.get(0).enviados.get(1)).contains("batimento");
        assertThat(publicador.conexoes("bruno@x.test")).isZero();
        assertThat(publicador.conexoes("ana@x.test")).isEqualTo(1);
    }

    @Test
    void aoDesligarAApiTodosOsFluxosSaoFechadosParaNaoTravarODesligamento() {
        publicador.registrar("ana@x.test");
        publicador.registrar("ana@x.test");
        publicador.registrar("bruno@x.test");

        publicador.encerrarTodos();

        assertThat(criados).hasSize(3).allMatch(c -> c.completo);
        assertThat(publicador.conexoes("ana@x.test")).isZero();
        assertThat(publicador.conexoes("bruno@x.test")).isZero();
    }
}
