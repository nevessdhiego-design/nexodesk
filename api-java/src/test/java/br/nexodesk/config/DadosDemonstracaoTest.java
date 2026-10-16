package br.nexodesk.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.nexodesk.dominio.StatusChamado;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.repositorio.NotificacaoRepository;
import br.nexodesk.repositorio.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Banco próprio (demotest), para não misturar com os dados dos outros testes. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demotest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "nexodesk.demo.senha=Demo-Somente-Para-Testes-1"})
@ActiveProfiles({"test", "demo"})
class DadosDemonstracaoTest {

    @Autowired ChamadoRepository chamados;
    @Autowired UsuarioRepository usuarios;
    @Autowired NotificacaoRepository notificacoes;
    @Autowired DadosDemonstracao demonstracao;

    @Test
    void criaMoradoresEChamadosVariadosComHistoricoEAvisos() {
        assertThat(usuarios.existsByEmail("ana.demo@nexodesk.local")).isTrue();
        assertThat(usuarios.existsByEmail("bruno.demo@nexodesk.local")).isTrue();
        assertThat(chamados.count()).isEqualTo(8);
        assertThat(chamados.countByStatus(StatusChamado.RESOLVIDO)).isEqualTo(1);
        assertThat(chamados.countByStatus(StatusChamado.EM_ANDAMENTO)).isEqualTo(1);
        assertThat(chamados.countByStatus(StatusChamado.ABERTO)).isEqualTo(6);
        // 2 avisos de andamento/resolução + 2 da atribuição do chamado da lixeira (morador e zelador)
        assertThat(notificacoes.count()).isEqualTo(4);
        assertThat(usuarios.existsByEmail("ze.demo@nexodesk.local")).isTrue();
    }

    @Test
    void rodarDeNovoNaoDuplicaNada() {
        demonstracao.run(null);
        assertThat(chamados.count()).isEqualTo(8);
    }
}
