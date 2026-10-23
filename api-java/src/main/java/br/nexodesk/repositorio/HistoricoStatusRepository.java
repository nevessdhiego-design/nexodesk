package br.nexodesk.repositorio;

import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.HistoricoStatus;
import br.nexodesk.dominio.StatusChamado;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoStatusRepository extends JpaRepository<HistoricoStatus, Long> {

    /** Quando cada chamado foi resolvido pela última vez: {id do chamado, data}. */
    @Query("select h.chamado.id, max(h.criadoEm) from HistoricoStatus h where h.statusNovo = :resolvido and h.chamado in :chamados group by h.chamado.id")
    List<Object[]> resolucoes(@Param("resolvido") StatusChamado resolvido, @Param("chamados") Collection<Chamado> chamados);

    @EntityGraph(attributePaths = "alteradoPor")
    List<HistoricoStatus> findByChamadoOrderByCriadoEmAscIdAsc(Chamado chamado);
}
