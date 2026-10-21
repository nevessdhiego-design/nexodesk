package br.nexodesk.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.web.Dtos.AlterarUsuarioRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** As regras que impedem o condomínio de ficar sem síndico, sem mexer no banco. */
class UsuarioAdminServiceTest {

    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    ChamadoRepository chamados = mock(ChamadoRepository.class);
    PublicadorEventos publicador = mock(PublicadorEventos.class);
    UsuarioAdminService servico = new UsuarioAdminService(usuarios, chamados, publicador,
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4));

    Usuario autor = pessoa(1L, "a@nexodesk.test", Perfil.SINDICO);

    @BeforeEach
    void prepara() {
        when(usuarios.findByEmail("a@nexodesk.test")).thenReturn(Optional.of(autor));
    }

    static Usuario pessoa(long id, String email, Perfil perfil) {
        Usuario u = new Usuario();
        ReflectionTestUtils.setField(u, "id", id);
        u.setNome("Pessoa " + id);
        u.setEmail(email);
        u.setPerfil(perfil);
        u.setAtivo(true);
        return u;
    }

    @Test
    void naoRebaixaNemDesativaOUltimoSindicoAtivo() {
        Usuario outro = pessoa(2L, "b@nexodesk.test", Perfil.SINDICO);
        when(usuarios.findById(2L)).thenReturn(Optional.of(outro));
        when(usuarios.countByPerfilAndAtivoTrue(Perfil.SINDICO)).thenReturn(1L);

        for (AlterarUsuarioRequest req : new AlterarUsuarioRequest[] {
                new AlterarUsuarioRequest(Perfil.MORADOR, null),
                new AlterarUsuarioRequest(Perfil.ZELADOR, null),
                new AlterarUsuarioRequest(null, false)}) {
            assertThatThrownBy(() -> servico.alterar("a@nexodesk.test", 2L, req))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
        assertThat(outro.getPerfil()).isEqualTo(Perfil.SINDICO);
        assertThat(outro.isAtivo()).isTrue();
        verify(usuarios, never()).save(any());
    }

    @Test
    void comDoisSindicosUmPodeSerRebaixadoESaiDaListaDeResponsaveis() {
        Usuario outro = pessoa(2L, "b@nexodesk.test", Perfil.SINDICO);
        when(usuarios.findById(2L)).thenReturn(Optional.of(outro));
        when(usuarios.countByPerfilAndAtivoTrue(Perfil.SINDICO)).thenReturn(2L);
        int versaoAntes = outro.getVersaoSessao();

        servico.alterar("a@nexodesk.test", 2L, new AlterarUsuarioRequest(Perfil.MORADOR, null));

        assertThat(outro.getPerfil()).isEqualTo(Perfil.MORADOR);
        assertThat(outro.getVersaoSessao()).isEqualTo(versaoAntes + 1); // sessões antigas caem
        verify(chamados).liberarResponsavel(outro);
        verify(publicador).encerrar("b@nexodesk.test");
    }

    @Test
    void promoverMoradorNaoLiberaChamadoNenhum() {
        Usuario morador = pessoa(3L, "c@nexodesk.test", Perfil.MORADOR);
        when(usuarios.findById(3L)).thenReturn(Optional.of(morador));

        servico.alterar("a@nexodesk.test", 3L, new AlterarUsuarioRequest(Perfil.ZELADOR, null));

        assertThat(morador.getPerfil()).isEqualTo(Perfil.ZELADOR);
        verify(chamados, never()).liberarResponsavel(any());
    }

    @Test
    void pedidoSemMudancaNaoDerrubaSessaoNemGrava() {
        Usuario zelador = pessoa(4L, "d@nexodesk.test", Perfil.ZELADOR);
        when(usuarios.findById(4L)).thenReturn(Optional.of(zelador));

        servico.alterar("a@nexodesk.test", 4L, new AlterarUsuarioRequest(Perfil.ZELADOR, true));

        assertThat(zelador.getVersaoSessao()).isZero();
        verify(usuarios, never()).save(any());
        verify(publicador, never()).encerrar(any());
    }
}
