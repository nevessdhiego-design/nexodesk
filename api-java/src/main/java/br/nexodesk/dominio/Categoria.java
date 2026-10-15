package br.nexodesk.dominio;

/**
 * Categorias de chamado de condomínio. A pontuação base (1 a 10) é a mesma do
 * serviço Python (servico-prioridade/app/regras.py) e só é usada pelo Java
 * quando esse serviço está fora do ar.
 */
public enum Categoria {
    ILUMINACAO("Iluminação", 2),
    LIMPEZA("Limpeza", 2),
    PORTARIA("Portaria", 3),
    ESTACIONAMENTO("Estacionamento", 3),
    BARULHO("Barulho", 3),
    CONVIVENCIA("Convivência entre vizinhos", 4),
    AREAS_COMUNS("Áreas comuns (salão, piscina, churrasqueira)", 4),
    OUTROS("Outros", 4),
    SEGURANCA("Segurança", 5),
    ELETRICA("Elétrica", 6),
    HIDRAULICA("Hidráulica (vazamento, entupimento)", 6),
    ELEVADOR("Elevador", 8),
    ESTRUTURA("Estrutura (infiltração, rachadura)", 9);

    private final String rotulo;
    private final int pontuacaoBase;

    Categoria(String rotulo, int pontuacaoBase) {
        this.rotulo = rotulo;
        this.pontuacaoBase = pontuacaoBase;
    }

    public String getRotulo() {
        return rotulo;
    }

    public int getPontuacaoBase() {
        return pontuacaoBase;
    }
}
