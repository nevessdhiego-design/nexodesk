package br.nexodesk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.nexodesk.servico.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LimitadorTentativasLoginTest {

    /** Relógio que anda quando o teste manda. */
    static class RelogioManual extends Clock {
        Instant agora = Instant.parse("2026-10-14T12:00:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    RelogioManual relogio;
    LimitadorTentativasLogin limitador;

    @BeforeEach
    void preparar() {
        relogio = new RelogioManual();
        limitador = new LimitadorTentativasLogin(relogio);
    }

    void falhar(int vezes, String ip, String email) {
        for (int i = 0; i < vezes; i++) {
            limitador.registrarFalha(ip, email);
        }
    }

    @Test
    void ateQuatroFalhasAindaPermite() {
        falhar(4, "1.1.1.1", "a@x.com");
        assertThatCode(() -> limitador.verificar("1.1.1.1", "a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void naQuintaFalhaBloqueiaComStatus429() {
        falhar(5, "1.1.1.1", "a@x.com");
        assertThatThrownBy(() -> limitador.verificar("1.1.1.1", "a@x.com"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getMessage()).contains("15 minuto");
                });
    }

    @Test
    void bloqueioNaoAtingeOutroEmailNemOutroIp() {
        falhar(5, "1.1.1.1", "a@x.com");
        assertThatCode(() -> limitador.verificar("1.1.1.1", "b@x.com")).doesNotThrowAnyException();
        assertThatCode(() -> limitador.verificar("2.2.2.2", "a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void bloqueioExpiraDepoisDaJanela() {
        falhar(5, "1.1.1.1", "a@x.com");
        relogio.avancar(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limitador.verificar("1.1.1.1", "a@x.com"))
                .hasMessageContaining("1 minuto");
        relogio.avancar(Duration.ofMinutes(1));
        assertThatCode(() -> limitador.verificar("1.1.1.1", "a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void loginCorretoZeraOContadorDoPar() {
        falhar(4, "1.1.1.1", "a@x.com");
        limitador.registrarSucesso("1.1.1.1", "a@x.com");
        falhar(4, "1.1.1.1", "a@x.com");
        assertThatCode(() -> limitador.verificar("1.1.1.1", "a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void ipQueErraEmMuitosEmailsEhBloqueado() {
        for (int i = 0; i < LimitadorTentativasLogin.MAX_POR_IP; i++) {
            limitador.registrarFalha("9.9.9.9", "usuario" + i + "@x.com");
        }
        assertThatThrownBy(() -> limitador.verificar("9.9.9.9", "novo@x.com"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void janelaReiniciaDepoisDeExpirar() {
        falhar(4, "1.1.1.1", "a@x.com");
        relogio.avancar(Duration.ofMinutes(16));
        falhar(1, "1.1.1.1", "a@x.com"); // conta como a 1ª da nova janela
        assertThatCode(() -> limitador.verificar("1.1.1.1", "a@x.com")).doesNotThrowAnyException();
    }
}
