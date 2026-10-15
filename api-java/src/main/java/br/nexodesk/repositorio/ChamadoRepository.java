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

    /**
     * Fila do síndico (RF03): do mais fácil ao mais difícil. A pontuação cresce junto
     * com a dificuldade, então ordenar por ela equivale a FACIL, MEDIA, DIFICIL.
     * Dentro da mesma pontuação, o chamado mais antigo vem primeiro.
     */
    @EntityGraph(attributePaths = "morador")
    List<Chamado> findByStatusInOrderByPontuacaoAscCriadoEmAsc(Collection<StatusChamado> status);

    long countByStatus(StatusChamado status);

    long countByStatusAndDificuldade(StatusChamado status, Dificuldade dificuldade);
}
