package br.nexodesk.servico;

import br.nexodesk.dominio.Notificacao;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.NotificacaoRepository;
import br.nexodesk.web.Dtos.NotificacaoResponse;
import br.nexodesk.web.Dtos.NotificacoesResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacaoService {

    private final NotificacaoRepository notificacoes;
    private final UsuarioService usuarios;

    public NotificacaoService(NotificacaoRepository notificacoes, UsuarioService usuarios) {
        this.notificacoes = notificacoes;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public NotificacoesResponse listar(String email) {
        Usuario u = usuarios.buscarPorEmail(email);
        return new NotificacoesResponse(
                notificacoes.countByUsuarioAndLidaFalse(u),
                notificacoes.findTop50ByUsuarioOrderByCriadoEmDescIdDesc(u).stream()
                        .map(n -> new NotificacaoResponse(n.getId(), n.getChamado().getId(),
                                n.getMensagem(), n.isLida(), n.getCriadoEm()))
                        .toList());
    }

    @Transactional
    public void marcarLida(String email, Long id) {
        Usuario u = usuarios.buscarPorEmail(email);
        Notificacao n = notificacoes.findByIdAndUsuario(id, u)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notificação não encontrada."));
        n.setLida(true);
        notificacoes.save(n);
    }
}
