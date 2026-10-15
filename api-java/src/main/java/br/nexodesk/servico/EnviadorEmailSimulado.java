package br.nexodesk.servico;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Padrão (nexodesk.email.enabled=false): nenhum e-mail sai da máquina. Só registra no log, sem o
 * texto da mensagem, e guarda as últimas 100 em memória para os testes conferirem.
 */
@Component
@ConditionalOnProperty(name = "nexodesk.email.enabled", havingValue = "false", matchIfMissing = true)
public class EnviadorEmailSimulado implements EnviadorEmail {

    public record Simulado(String para, String assunto, String texto) {
    }

    private static final Logger log = LoggerFactory.getLogger(EnviadorEmailSimulado.class);
    private static final int LIMITE = 100;

    private final List<Simulado> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String para, String assunto, String texto) {
        enviados.add(new Simulado(para, assunto, texto));
        while (enviados.size() > LIMITE) {
            enviados.remove(0);
        }
        log.info("[e-mail simulado] para={} assunto=\"{}\" (defina nexodesk.email.enabled=true para enviar de verdade)",
                mascarar(para), assunto);
    }

    public List<Simulado> enviados() {
        return List.copyOf(enviados);
    }

    /** ana.silva@exemplo.com vira a***@exemplo.com: o log não precisa do endereço inteiro. */
    static String mascarar(String email) {
        int arroba = email.indexOf('@');
        return arroba <= 0 ? "***" : email.charAt(0) + "***" + email.substring(arroba);
    }
}
