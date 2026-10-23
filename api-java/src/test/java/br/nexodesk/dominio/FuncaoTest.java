package br.nexodesk.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A tabela "função cobre categoria" é o que alimenta o "Indicado" do painel do síndico. */
class FuncaoTest {

    @Test
    void todaCategoriaTemPeloMenosUmaFuncaoQueACubra() {
        for (Categoria categoria : Categoria.values()) {
            assertThat(Arrays.stream(Funcao.values()).anyMatch(f -> f.cobre(categoria)))
                    .as("categoria %s sem nenhuma função", categoria).isTrue();
        }
    }

    @Test
    void sujeiraNoPatioVaiParaZeladorEFaxineiroEPiaVazandoParaEncanador() {
        assertThat(Funcao.ZELADOR.cobre(Categoria.LIMPEZA)).isTrue();
        assertThat(Funcao.FAXINEIRO.cobre(Categoria.LIMPEZA)).isTrue();
        assertThat(Funcao.ELETRICISTA.cobre(Categoria.LIMPEZA)).isFalse();
        assertThat(Funcao.ENCANADOR.cobre(Categoria.HIDRAULICA)).isTrue();
        assertThat(Funcao.ELETRICISTA.cobre(Categoria.ELETRICA)).isTrue();
        assertThat(Funcao.TECNICO_EXTERNO.cobre(Categoria.ELEVADOR)).isTrue();
        assertThat(Funcao.PORTEIRO.cobre(Categoria.PORTARIA)).isTrue();
    }

    @Test
    void soSegurancaRecebeTodaEmergencia() {
        Set<Funcao> recebem = EnumSet.noneOf(Funcao.class);
        for (Funcao f : Funcao.values()) {
            if (f.recebeEmergencias()) {
                recebem.add(f);
            }
        }
        assertThat(recebem).containsExactly(Funcao.SEGURANCA);
    }
}
