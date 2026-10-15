package br.nexodesk.servico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio real por SMTP (nexodesk.email.enabled=true). Exige spring.mail.host e, normalmente,
 * spring.mail.username e spring.mail.password, SEMPRE por variável de ambiente.
 */
@Component
@ConditionalOnProperty(name = "nexodesk.email.enabled", havingValue = "true")
public class EnviadorEmailSmtp implements EnviadorEmail {

    private final JavaMailSender remetenteJava;
    private final String remetente;

    public EnviadorEmailSmtp(JavaMailSender remetenteJava,
                             @Value("${nexodesk.email.remetente:nexodesk@localhost}") String remetente) {
        this.remetenteJava = remetenteJava;
        this.remetente = remetente;
    }

    @Override
    public void enviar(String para, String assunto, String texto) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(para);
        mensagem.setSubject(assunto);
        mensagem.setText(texto);
        remetenteJava.send(mensagem);
    }
}
