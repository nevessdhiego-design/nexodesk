package br.nexodesk.repositorio;

import br.nexodesk.dominio.Ausencia;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AusenciaRepository extends JpaRepository<Ausencia, Long> {

    /** Ausências que ainda não terminaram (em curso ou futuras), da mais próxima para a mais distante. */
    @EntityGraph(attributePaths = "usuario")
    List<Ausencia> findByAteGreaterThanEqualOrderByDeAscIdAsc(LocalDate hoje);

    /** Ausências que cobrem o dia informado. */
    @EntityGraph(attributePaths = "usuario")
    List<Ausencia> findByDeLessThanEqualAndAteGreaterThanEqual(LocalDate dia, LocalDate mesmoDia);
}
