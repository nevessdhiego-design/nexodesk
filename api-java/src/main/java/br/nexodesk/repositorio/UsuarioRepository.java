package br.nexodesk.repositorio;

import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPerfil(Perfil perfil);

    java.util.List<Usuario> findByPerfilAndAtivoTrue(Perfil perfil);

    /** Só o que o filtro de sessão precisa (não carrega o usuário inteiro a cada requisição). */
    interface SituacaoDaSessao {
        boolean getAtivo();

        int getVersaoSessao();
    }

    @Query("select u.ativo as ativo, u.versaoSessao as versaoSessao from Usuario u where u.email = :email")
    Optional<SituacaoDaSessao> situacaoDaSessao(@Param("email") String email);
}
