package br.nexodesk.repositorio;

import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.Dificuldade;
import br.nexodesk.dominio.StatusChamado;
import br.nexodesk.dominio.Usuario;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChamadoRepository extends JpaRepository<Chamado, Long> {

    @EntityGraph(attributePaths = "morador")
    List<Chamado> findByMoradorOrderByCriadoEmDesc(Usuario morador);

    /** A ordem da fila é decidida em {@code PrioridadeFila} (emergência, pontuação efetiva e idade). */
    @EntityGraph(attributePaths = "morador")
    List<Chamado> findByStatusIn(Collection<StatusChamado> status);

    long countByEmergenciaTrueAndStatusIn(Collection<StatusChamado> status);

    long countByStatus(StatusChamado status);

    long countByStatusAndDificuldade(StatusChamado status, Dificuldade dificuldade);
}
