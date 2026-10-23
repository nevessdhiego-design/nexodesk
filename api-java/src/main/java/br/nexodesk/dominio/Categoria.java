package br.nexodesk.dominio;

/**
 * Categorias de chamado de condomínio. A pontuação base (1 a 10) é a mesma do
 * serviço Python (servico-prioridade/app/regras.py) e só é usada pelo Java
 * quando esse serviço está fora do ar.
 */
public enum Categoria {
    ILUMINACAO("Iluminação", 2, 48),
    LIMPEZA("Limpeza", 2, 24),
    PORTARIA("Portaria", 3, 24),
    ESTACIONAMENTO("Estacionamento", 3, 48),
    BARULHO("Barulho", 3, 24),
    CONVIVENCIA("Convivência entre vizinhos", 4, 72),
    AREAS_COMUNS("Áreas comuns (salão, piscina, churrasqueira)", 4, 72),
    OUTROS("Outros", 4, 72),
    SEGURANCA("Segurança", 5, 24),
    ELETRICA("Elétrica", 6, 48),
    HIDRAULICA("Hidráulica (vazamento, entupimento)", 6, 48),
    ELEVADOR("Elevador", 8, 24),
    ESTRUTURA("Estrutura (infiltração, rachadura)", 9, 168);

    private final String rotulo;
    private final int pontuacaoBase;
    private final int prazoHoras;

    Categoria(String rotulo, int pontuacaoBase, int prazoHoras) {
        this.rotulo = rotulo;
        this.pontuacaoBase = pontuacaoBase;
        this.prazoHoras = prazoHoras;
    }

    /** Prazo padrão (SLA) para resolver um chamado desta categoria. Emergência tem prazo fixo de {@value #PRAZO_EMERGENCIA_HORAS} h. */
    public int getPrazoHoras() {
        return prazoHoras;
    }

    public static final int PRAZO_EMERGENCIA_HORAS = 1;

    public String getRotulo() {
        return rotulo;
    }

    public int getPontuacaoBase() {
        return pontuacaoBase;
    }
}
