package br.nexodesk.repositorio;

import br.nexodesk.dominio.Avaliacao;
import br.nexodesk.dominio.Chamado;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    boolean existsByChamado(Chamado chamado);

    /** Uma consulta para uma lista inteira de chamados (evita 1 consulta por chamado). */
    @Query("select a from Avaliacao a where a.chamado in :chamados")
    List<Avaliacao> buscarDosChamados(Collection<Chamado> chamados);

    @Query("select avg(a.nota) from Avaliacao a")
    Double mediaDasNotas();
}
