package br.nexodesk.dominio;

public enum Dificuldade {
    FACIL("Fácil"),
    MEDIA("Média"),
    DIFICIL("Difícil");

    private final String rotulo;

    Dificuldade(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Mesmos limites do serviço Python (regras.py): até 3 fácil, até 6 média, acima difícil. */
    public static Dificuldade dePontuacao(int pontuacao) {
        if (pontuacao <= 3) {
            return FACIL;
        }
        return pontuacao <= 6 ? MEDIA : DIFICIL;
    }
}
