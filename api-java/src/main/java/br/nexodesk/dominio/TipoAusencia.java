package br.nexodesk.dominio;

/** Por que um funcionário não está disponível num período. */
public enum TipoAusencia {
    FERIAS("Férias"), FOLGA("Folga"), ATESTADO("Atestado"), OUTRO("Ausência");

    private final String rotulo;

    TipoAusencia(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
