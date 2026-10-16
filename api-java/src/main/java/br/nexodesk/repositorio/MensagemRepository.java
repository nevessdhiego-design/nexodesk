package br.nexodesk.repositorio;

import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.Mensagem;
import br.nexodesk.dominio.Usuario;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    @EntityGraph(attributePaths = "autor")
    List<Mensagem> findByChamadoOrderByCriadoEmAscIdAsc(Chamado chamado);

    /** Para o freio de frequência: quantas mensagens esta pessoa mandou desde um instante. */
    long countByAutorAndCriadoEmAfter(Usuario autor, LocalDateTime desde);
}
