package br.nexodesk.servico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envia o e-mail do link de recuperação em outra thread e só depois do commit (se a gravação do token falhar, ninguém
 * recebe um link que não funciona). Falha de envio vira só log: quem pediu não é avisado (a resposta é sempre a mesma).
 */
@Component
public class EmailRecuperacao {

    private static final Logger log = LoggerFactory.getLogger(EmailRecuperacao.class);

    private final EnviadorEmail enviador;
    private final EmailRecuperacao proxy;

    public EmailRecuperacao(EnviadorEmail enviador, @org.springframework.context.annotation.Lazy EmailRecuperacao proxy) {
        this.enviador = enviador;
        this.proxy = proxy;
    }

    public void enviarLink(String para, String nome, String link) {
        Runnable envio = () -> proxy.enviarAgora(para, nome, link);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
        } else {
            envio.run();
        }
    }

    @Async
    public void enviarAgora(String para, String nome, String link) {
        try {
            enviador.enviar(para, "NexoDesk: redefinir a sua senha",
                    "Olá, " + nome + ".\n\nAlguém pediu para redefinir a senha da sua conta no NexoDesk. "
                            + "Para escolher uma nova senha, abra o link abaixo (vale por " + RecuperacaoSenhaService.VALIDADE_MINUTOS
                            + " minutos e só pode ser usado uma vez):\n\n" + link
                            + "\n\nSe não foi você, ignore este e-mail: a sua senha continua a mesma.\n");
        } catch (RuntimeException falha) {
            log.warn("Não foi possível enviar o e-mail de recuperação: {}", falha.getClass().getSimpleName());
        }
    }
}
