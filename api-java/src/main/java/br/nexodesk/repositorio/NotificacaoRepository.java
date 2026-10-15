package br.nexodesk.repositorio;

import br.nexodesk.dominio.Notificacao;
import br.nexodesk.dominio.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    @EntityGraph(attributePaths = "chamado")
    List<Notificacao> findTop50ByUsuarioOrderByCriadoEmDescIdDesc(Usuario usuario);

    long countByUsuarioAndLidaFalse(Usuario usuario);

    Optional<Notificacao> findByIdAndUsuario(Long id, Usuario usuario);
}
