package br.nexodesk.repositorio;

import br.nexodesk.dominio.TokenSenha;
import br.nexodesk.dominio.Usuario;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenSenhaRepository extends JpaRepository<TokenSenha, Long> {

    @EntityGraph(attributePaths = "usuario")
    Optional<TokenSenha> findByTokenHash(String tokenHash);

    /** Ao pedir um link novo, os anteriores ainda não usados deixam de valer. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TokenSenha t set t.usadoEm = :agora where t.usuario = :usuario and t.usadoEm is null")
    int invalidarAnteriores(@Param("usuario") Usuario usuario, @Param("agora") LocalDateTime agora);
}
