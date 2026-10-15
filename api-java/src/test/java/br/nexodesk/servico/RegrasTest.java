package br.nexodesk.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.dominio.Dificuldade;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegrasTest {

    @Test
    void limitesDeDificuldadeSaoOsMesmosDoPython() {
        assertThat(Dificuldade.dePontuacao(1)).isEqualTo(Dificuldade.FACIL);
        assertThat(Dificuldade.dePontuacao(3)).isEqualTo(Dificuldade.FACIL);
        assertThat(Dificuldade.dePontuacao(4)).isEqualTo(Dificuldade.MEDIA);
        assertThat(Dificuldade.dePontuacao(6)).isEqualTo(Dificuldade.MEDIA);
        assertThat(Dificuldade.dePontuacao(7)).isEqualTo(Dificuldade.DIFICIL);
        assertThat(Dificuldade.dePontuacao(10)).isEqualTo(Dificuldade.DIFICIL);
    }

    /** Evita que o Java e o Python divirjam: lê PONTUACAO_BASE de regras.py e compara com o enum. */
    @Test
    void pontuacaoBaseDoJavaEIgualAoPython() throws IOException {
        Path regras = Path.of("..", "servico-prioridade", "app", "regras.py");
        assumeTrue(Files.exists(regras), "regras.py não encontrado ao lado do projeto Java");

        String fonte = Files.readString(regras);
        String bloco = fonte.substring(fonte.indexOf("PONTUACAO_BASE = {"));
        bloco = bloco.substring(0, bloco.indexOf("}"));
        Map<String, Integer> python = new HashMap<>();
        Matcher m = Pattern.compile("\"([A-Z_]+)\":\\s*(\\d+)").matcher(bloco);
        while (m.find()) {
            python.put(m.group(1), Integer.parseInt(m.group(2)));
        }

        Map<String, Integer> java = new HashMap<>();
        for (Categoria c : Categoria.values()) {
            java.put(c.name(), c.getPontuacaoBase());
        }
        assertThat(java).isEqualTo(python);
    }

    @Test
    void detectaTipoDeImagemPelaAssinaturaDoArquivo() {
        assertThat(ArmazenamentoImagem.detectarExtensao(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("jpg");
        assertThat(ArmazenamentoImagem.detectarExtensao(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isEqualTo("png");
        assertThat(ArmazenamentoImagem.detectarExtensao("GIF89a".getBytes())).isEqualTo("gif");
        assertThat(ArmazenamentoImagem.detectarExtensao("<?php echo 1;".getBytes())).isNull();
        assertThat(ArmazenamentoImagem.detectarExtensao("<svg onload=alert(1)>".getBytes())).isNull();
        assertThat(ArmazenamentoImagem.detectarExtensao(new byte[0])).isNull();
    }
}
