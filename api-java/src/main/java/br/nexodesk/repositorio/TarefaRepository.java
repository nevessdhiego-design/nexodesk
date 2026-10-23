package br.nexodesk.repositorio;

import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.Tarefa;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TarefaRepository extends JpaRepository<Tarefa, Long> {

    List<Tarefa> findByChamadoOrderByOrdemAscIdAsc(Chamado chamado);

    boolean existsByChamado(Chamado chamado);

    @Override
    @EntityGraph(attributePaths = {"chamado", "chamado.responsavel", "chamado.morador"})
    java.util.Optional<Tarefa> findById(Long id);
}
