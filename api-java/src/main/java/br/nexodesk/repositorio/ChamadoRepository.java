package br.nexodesk.repositorio;

import br.nexodesk.dominio.Chamado;
import br.nexodesk.dominio.Dificuldade;
import br.nexodesk.dominio.StatusChamado;
import br.nexodesk.dominio.Usuario;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChamadoRepository extends JpaRepository<Chamado, Long> {

    @EntityGraph(attributePaths = {"morador", "responsavel"})
    List<Chamado> findByMoradorOrderByCriadoEmDesc(Usuario morador);

    /** A ordem da fila é decidida em {@code PrioridadeFila} (emergência, pontuação efetiva e idade). */
    @EntityGraph(attributePaths = {"morador", "responsavel"})
    List<Chamado> findByStatusIn(Collection<StatusChamado> status);

    /** Chamados que estão com um responsável (a tela "meus atendimentos" do zelador). */
    @EntityGraph(attributePaths = {"morador", "responsavel"})
    List<Chamado> findByResponsavelAndStatusIn(Usuario responsavel, Collection<StatusChamado> status);

    /** Quando alguém sai da equipe (desativado ou rebaixado), os chamados dele ficam sem responsável. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Chamado c set c.responsavel = null where c.responsavel = :usuario")
    int liberarResponsavel(@Param("usuario") Usuario usuario);

    long countByEmergenciaTrueAndStatusIn(Collection<StatusChamado> status);

    long countByStatus(StatusChamado status);

    long countByStatusAndDificuldade(StatusChamado status, Dificuldade dificuldade);
}
