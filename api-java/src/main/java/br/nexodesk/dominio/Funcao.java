package br.nexodesk.dominio;

import static br.nexodesk.dominio.Categoria.*;

import java.util.EnumSet;
import java.util.Set;

/**
 * Função de quem trabalha no condomínio (perfil FUNCIONARIO). Cada função cobre algumas categorias de chamado: é com isso que
 * o painel do síndico mostra "Indicado" ao escolher o responsável. É só uma sugestão: quem atribui é sempre o síndico.
 */
public enum Funcao {
    ZELADOR("Zelador", "zelador", EnumSet.of(LIMPEZA, AREAS_COMUNS, ESTACIONAMENTO, OUTROS), false),
    FAXINEIRO("Faxineiro(a)", "faxineiro(a)", EnumSet.of(LIMPEZA, AREAS_COMUNS), false),
    ELETRICISTA("Eletricista", "eletricista", EnumSet.of(ELETRICA, ILUMINACAO), false),
    ENCANADOR("Encanador", "encanador", EnumSet.of(HIDRAULICA), false),
    MANUTENCAO("Manutenção geral (pedreiro, pintor)", "manutenção", EnumSet.of(ESTRUTURA, AREAS_COMUNS, OUTROS), false),
    PORTEIRO("Porteiro", "porteiro", EnumSet.of(PORTARIA, ESTACIONAMENTO, BARULHO, CONVIVENCIA), false),
    SEGURANCA("Segurança / vigilante", "segurança", EnumSet.of(Categoria.SEGURANCA, BARULHO), true),
    TECNICO_EXTERNO("Técnico externo (elevador)", "técnico externo", EnumSet.of(ELEVADOR), false);

    private final String rotulo;
    private final String rotuloCurto;
    private final Set<Categoria> categorias;
    private final boolean recebeEmergencias;

    Funcao(String rotulo, String rotuloCurto, Set<Categoria> categorias, boolean recebeEmergencias) {
        this.rotulo = rotulo;
        this.rotuloCurto = rotuloCurto;
        this.categorias = categorias;
        this.recebeEmergencias = recebeEmergencias;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Em minúsculas, para frases como "O zelador respondeu". */
    public String getRotuloCurto() {
        return rotuloCurto;
    }

    public Set<Categoria> getCategorias() {
        return EnumSet.copyOf(categorias);
    }

    public boolean cobre(Categoria categoria) {
        return categorias.contains(categoria);
    }

    /** Segurança é avisada de toda emergência. */
    public boolean recebeEmergencias() {
        return recebeEmergencias;
    }
}
