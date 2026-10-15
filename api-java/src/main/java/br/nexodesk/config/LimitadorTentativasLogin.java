package br.nexodesk.config;

import br.nexodesk.servico.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Freio contra tentativa repetida de senha (força bruta). Conta só as FALHAS, em janela fixa
 * de 15 minutos a partir da primeira falha:
 * <ul>
 *   <li>5 falhas para o mesmo e-mail vindas do mesmo IP bloqueiam esse par (um atacante não
 *       consegue travar a conta da vítima de outro IP);</li>
 *   <li>30 falhas de um mesmo IP bloqueiam o IP, mesmo trocando de e-mail.</li>
 * </ul>
 * Um login correto zera o contador do par e-mail+IP. O estado fica em memória: reiniciar a API zera tudo
 * e, com várias instâncias, cada uma conta separado (aceitável para este projeto; produção usaria Redis).
 * Atrás de proxy o IP visto é o do proxy.
 */
@Component
public class LimitadorTentativasLogin {

    static final int MAX_POR_CONTA = 5;
    static final int MAX_POR_IP = 30;
    static final Duration JANELA = Duration.ofMinutes(15);
    private static final int LIMITE_DE_ENTRADAS = 10_000;

    private static final class Contador {
        int falhas;
        Instant inicio;

        Contador(Instant inicio) {
            this.inicio = inicio;
        }
    }

    private final Clock relogio;
    private final Map<String, Contador> porConta = new HashMap<>();
    private final Map<String, Contador> porIp = new HashMap<>();

    public LimitadorTentativasLogin(Clock relogio) {
        this.relogio = relogio;
    }

    /** Lança 429 se o par e-mail+IP ou o IP já estourou o limite na janela atual. */
    public synchronized void verificar(String ip, String email) {
        Instant agora = relogio.instant();
        long minutos = Math.max(
                minutosRestantes(porConta.get(chave(ip, email)), MAX_POR_CONTA, agora),
                minutosRestantes(porIp.get(ip), MAX_POR_IP, agora));
        if (minutos > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Tente novamente em " + minutos + " minuto(s).");
        }
    }

    public synchronized void registrarFalha(String ip, String email) {
        Instant agora = relogio.instant();
        limparExpirados(agora);
        somar(porConta, chave(ip, email), agora);
        somar(porIp, ip, agora);
    }

    public synchronized void registrarSucesso(String ip, String email) {
        porConta.remove(chave(ip, email));
    }

    // ---------- internos ----------

    private static String chave(String ip, String email) {
        return ip + "|" + email;
    }

    private static void somar(Map<String, Contador> mapa, String chave, Instant agora) {
        Contador c = mapa.get(chave);
        if (c == null || expirou(c, agora)) {
            c = new Contador(agora);
            mapa.put(chave, c);
        }
        c.falhas++;
    }

    private static boolean expirou(Contador c, Instant agora) {
        return !agora.isBefore(c.inicio.plus(JANELA));
    }

    /** 0 se não está bloqueado; senão, minutos (arredondados para cima) até a janela acabar. */
    private static long minutosRestantes(Contador c, int maximo, Instant agora) {
        if (c == null || c.falhas < maximo || expirou(c, agora)) {
            return 0;
        }
        long segundos = Duration.between(agora, c.inicio.plus(JANELA)).toSeconds();
        return Math.max(1, (segundos + 59) / 60);
    }

    /** Evita crescer sem limite sob ataque com e-mails aleatórios. */
    private void limparExpirados(Instant agora) {
        if (porConta.size() + porIp.size() < LIMITE_DE_ENTRADAS) {
            return;
        }
        porConta.values().removeIf(c -> expirou(c, agora));
        porIp.values().removeIf(c -> expirou(c, agora));
    }
}
