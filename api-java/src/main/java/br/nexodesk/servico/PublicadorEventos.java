package br.nexodesk.servico;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Tempo real por Server-Sent Events (SSE): o navegador abre UMA conexão de leitura em {@code GET /api/eventos} e o
 * servidor empurra um aviso curto sempre que algo muda ("atualizacao", com o tipo e o id do chamado). O aviso não leva
 * conteúdo (nem texto de mensagem, nem nomes): o navegador recarrega os dados pela API normal, que já confere quem pode ver o quê.
 *
 * <p>Por que SSE e não WebSocket: só o servidor fala, a conexão usa o mesmo cookie de sessão e a política de segurança
 * (CSP) não muda; o navegador reconecta sozinho.
 *
 * <p>Limites assumidos (documentados): as conexões ficam na memória desta instância, então vale para <b>uma instância</b>
 * da API (para várias, seria preciso um barramento como Redis). Cada usuário pode ter até {@value #MAXIMO_POR_USUARIO}
 * conexões (várias abas); a mais antiga é encerrada quando passa disso.
 */
@Component
public class PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

    static final long TEMPO_LIMITE_MS = 30L * 60 * 1000;
    static final int MAXIMO_POR_USUARIO = 5;

    private final Map<String, List<SseEmitter>> porUsuario = new ConcurrentHashMap<>();
    private final Supplier<SseEmitter> fabrica;

    @Autowired
    public PublicadorEventos() {
        this(() -> new SseEmitter(TEMPO_LIMITE_MS));
    }

    /** Permite ao teste trocar o emissor por um que anota o que foi enviado. */
    PublicadorEventos(Supplier<SseEmitter> fabrica) {
        this.fabrica = fabrica;
    }

    /** Abre a conexão do usuário (chamado por {@code EventosController}). */
    public SseEmitter registrar(String email) {
        SseEmitter emissor = fabrica.get();
        List<SseEmitter> lista = porUsuario.computeIfAbsent(email, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        while (lista.size() >= MAXIMO_POR_USUARIO) {
            SseEmitter maisAntigo = lista.remove(0);
            fechar(maisAntigo);
        }
        lista.add(emissor);
        Runnable limpar = () -> remover(email, emissor);
        emissor.onCompletion(limpar);
        emissor.onTimeout(limpar);
        emissor.onError(erro -> limpar.run());
        try {
            // "pronto": o navegador sabe que conectou. reconnectTime: se a conexão cair, tenta de novo em 5 s (e não a cada ~3 s)
            emissor.send(SseEmitter.event().name("pronto").data("ok").reconnectTime(5000));
        } catch (IOException | IllegalStateException e) {
            remover(email, emissor);
        }
        return emissor;
    }

    /** Avisa UM usuário. {@code tipo} é um texto fixo do sistema (nunca digitado por usuário). */
    public void notificar(String email, String tipo, Long chamadoId) {
        List<SseEmitter> lista = porUsuario.get(email);
        if (lista == null) {
            return;
        }
        String dados = "{\"tipo\":\"" + tipo + "\",\"chamadoId\":" + chamadoId + "}";
        for (SseEmitter emissor : new ArrayList<>(lista)) {
            try {
                emissor.send(SseEmitter.event().name("atualizacao").data(dados));
            } catch (IOException | IllegalStateException e) {
                remover(email, emissor); // conexão morreu: o navegador reconecta sozinho
            }
        }
    }

    public void notificar(Set<String> emails, String tipo, Long chamadoId) {
        emails.forEach(email -> notificar(email, tipo, chamadoId));
    }

    /**
     * Fecha todas as conexões do usuário. Usado quando a senha muda ou a conta é desativada: uma sessão derrubada
     * não pode continuar recebendo avisos. O navegador legítimo reconecta e passa pela checagem de sessão de novo.
     */
    public void encerrar(String email) {
        List<SseEmitter> lista = porUsuario.remove(email);
        if (lista != null) {
            lista.forEach(PublicadorEventos::fechar);
        }
    }

    /**
     * Ao desligar a API, fecha todos os fluxos abertos. Sem isto o desligamento "graceful" do Tomcat conta cada fluxo
     * como requisição em andamento e espera o prazo inteiro (30 s) antes de encerrar. O navegador reconecta sozinho
     * quando a API volta.
     */
    @EventListener(ContextClosedEvent.class)
    public void encerrarTodos() {
        List<String> emails = new ArrayList<>(porUsuario.keySet());
        emails.forEach(this::encerrar);
    }

    /** Batimento: comentário SSE a cada 25 s, para proxies não derrubarem a conexão parada e para descobrir conexões mortas. */
    @Scheduled(fixedRate = 25_000)
    public void batimento() {
        porUsuario.forEach((email, lista) -> {
            for (SseEmitter emissor : new ArrayList<>(lista)) {
                try {
                    emissor.send(SseEmitter.event().comment("batimento"));
                } catch (IOException | IllegalStateException e) {
                    remover(email, emissor);
                }
            }
        });
    }

    public int conexoes(String email) {
        List<SseEmitter> lista = porUsuario.get(email);
        return lista == null ? 0 : lista.size();
    }

    private void remover(String email, SseEmitter emissor) {
        List<SseEmitter> lista = porUsuario.get(email);
        if (lista != null) {
            lista.remove(emissor);
            if (lista.isEmpty()) {
                porUsuario.remove(email, lista);
            }
        }
    }

    private static void fechar(SseEmitter emissor) {
        try {
            emissor.complete();
        } catch (RuntimeException e) {
            log.debug("Conexão já estava fechada: {}", e.getClass().getSimpleName());
        }
    }
}
