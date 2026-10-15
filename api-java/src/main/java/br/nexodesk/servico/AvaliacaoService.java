package br.nexodesk.servico;

import br.nexodesk.dominio.Avaliacao;
import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.StatusChamado;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.AvaliacaoRepository;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.web.Dtos.AvaliacaoRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacoes;
    private final ChamadoRepository chamados;
    private final UsuarioService usuarios;

    public AvaliacaoService(AvaliacaoRepository avaliacoes, ChamadoRepository chamados, UsuarioService usuarios) {
        this.avaliacoes = avaliacoes;
        this.chamados = chamados;
        this.usuarios = usuarios;
    }

    /** Só o dono avalia, só chamado RESOLVIDO, uma vez por chamado. */
    @Transactional
    public void avaliar(String emailMorador, Long chamadoId, AvaliacaoRequest dados) {
        Usuario morador = usuarios.buscarPorEmail(emailMorador);
        Chamado c = chamados.findById(chamadoId)
                .filter(x -> x.getMorador().getId().equals(morador.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chamado não encontrado."));
        if (c.getStatus() != StatusChamado.RESOLVIDO) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Só é possível avaliar chamados resolvidos.");
        }
        if (avaliacoes.existsByChamado(c)) {
            throw new ApiException(HttpStatus.CONFLICT, "Este chamado já foi avaliado.");
        }
        Avaliacao a = new Avaliacao();
        a.setChamado(c);
        a.setMorador(morador);
        a.setNota(dados.nota());
        a.setComentario(dados.comentario() == null || dados.comentario().isBlank() ? null : dados.comentario().trim());
        try {
            avaliacoes.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            // duas avaliações simultâneas do mesmo chamado: a restrição UNIQUE do banco barra a segunda
            throw new ApiException(HttpStatus.CONFLICT, "Este chamado já foi avaliado.");
        }
    }
}
