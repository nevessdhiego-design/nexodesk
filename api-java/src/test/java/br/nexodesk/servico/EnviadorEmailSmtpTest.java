package br.nexodesk.servico;

import static org.assertj.core.api.Assertions.assertThat;

import br.nexodesk.dominio.StatusChamado;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Prova o caminho de SMTP de verdade contra um servidor SMTP falso que roda só neste teste
 * (127.0.0.1, porta livre). Nenhum e-mail sai da máquina.
 */
class EnviadorEmailSmtpTest {

    GreenMail smtp;
    EnviadorEmailSmtp enviador;

    @BeforeEach
    void subir() throws IOException {
        int porta;
        try (ServerSocket s = new ServerSocket(0)) {
            porta = s.getLocalPort();
        }
        smtp = new GreenMail(new ServerSetup(porta, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        smtp.start();
        JavaMailSenderImpl remetenteJava = new JavaMailSenderImpl();
        remetenteJava.setHost("127.0.0.1");
        remetenteJava.setPort(porta);
        enviador = new EnviadorEmailSmtp(remetenteJava, "nexodesk@teste.local");
    }

    @AfterEach
    void parar() {
        smtp.stop();
    }

    @Test
    void enviaDeVerdadePorSmtpComRemetenteAssuntoECorpo() throws Exception {
        enviador.enviar("morador@exemplo.test", "NexoDesk: atualização do chamado #7", "Olá!\nSeu chamado mudou.");

        assertThat(smtp.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage[] recebidas = smtp.getReceivedMessages();
        assertThat(recebidas).hasSize(1);
        assertThat(recebidas[0].getFrom()[0].toString()).isEqualTo("nexodesk@teste.local");
        assertThat(recebidas[0].getAllRecipients()[0].toString()).isEqualTo("morador@exemplo.test");
        assertThat(recebidas[0].getSubject()).isEqualTo("NexoDesk: atualização do chamado #7");
        assertThat(String.valueOf(recebidas[0].getContent())).contains("Seu chamado mudou.");
    }

    @Test
    void falhaDoServidorNaoDerrubaONotificador() throws Exception {
        int porta = smtp.getSmtp().getPort();
        smtp.stop(); // servidor fora do ar
        JavaMailSenderImpl quebrado = new JavaMailSenderImpl();
        quebrado.setHost("127.0.0.1");
        quebrado.setPort(porta);
        quebrado.getJavaMailProperties().put("mail.smtp.connectiontimeout", "500");
        quebrado.getJavaMailProperties().put("mail.smtp.timeout", "500");
        NotificadorEmailStatus notificador =
                new NotificadorEmailStatus(new EnviadorEmailSmtp(quebrado, "nexodesk@teste.local"), "http://localhost:8080");

        // não pode lançar exceção: o aviso dentro do sistema já foi gravado e o síndico não deve receber erro
        notificador.aoMudarStatus(new StatusAlteradoEvent(1L, "Título", "Ana", "ana@exemplo.test",
                StatusChamado.ABERTO, StatusChamado.RESOLVIDO, null));
        smtp.start(); // para o @AfterEach conseguir parar normalmente
    }

    @Test
    void textoDoEmailNaoTrazADescricaoEMencionaOStatus() {
        NotificadorEmailStatus n = new NotificadorEmailStatus(null, "http://localhost:8080");
        StatusAlteradoEvent e = new StatusAlteradoEvent(9L, "Elevador parado", "Bruno", "b@x.test",
                StatusChamado.ABERTO, StatusChamado.EM_ANDAMENTO, "Técnico a caminho.");
        String texto = n.texto(e);
        assertThat(texto).contains("Olá, Bruno.").contains("#9").contains("Aberto para Em andamento")
                .contains("Técnico a caminho.").contains("http://localhost:8080");
        assertThat(NotificadorEmailStatus.assunto(e)).isEqualTo("NexoDesk: atualização do chamado #9");
    }

    @Test
    void mascaraOEnderecoNoLog() {
        assertThat(EnviadorEmailSimulado.mascarar("ana.silva@exemplo.com")).isEqualTo("a***@exemplo.com");
        assertThat(EnviadorEmailSimulado.mascarar("sem-arroba")).isEqualTo("***");
    }
}
