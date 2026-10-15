package br.nexodesk.dominio;

public enum StatusChamado {
    ABERTO("Aberto"),
    EM_ANDAMENTO("Em andamento"),
    RESOLVIDO("Resolvido");

    private final String rotulo;

    StatusChamado(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
