package br.nexodesk.repositorio;

import br.nexodesk.dominio.Notificacao;
import br.nexodesk.dominio.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    @EntityGraph(attributePaths = "chamado")
    List<Notificacao> findTop50ByUsuarioOrderByCriadoEmDescIdDesc(Usuario usuario);

    long countByUsuarioAndLidaFalse(Usuario usuario);

    Optional<Notificacao> findByIdAndUsuario(Long id, Usuario usuario);

    /** Usado ao excluir a conta: os avisos são da caixa de entrada pessoal. */
    @Modifying
    @Query("delete from Notificacao n where n.usuario = :usuario")
    int apagarDoUsuario(@Param("usuario") Usuario usuario);
}
