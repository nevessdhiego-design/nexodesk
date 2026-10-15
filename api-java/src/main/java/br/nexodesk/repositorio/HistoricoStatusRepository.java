package br.nexodesk.repositorio;

import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.HistoricoStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoStatusRepository extends JpaRepository<HistoricoStatus, Long> {

    @EntityGraph(attributePaths = "alteradoPor")
    List<HistoricoStatus> findByChamadoOrderByCriadoEmAscIdAsc(Chamado chamado);
}
