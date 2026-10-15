"""Regras de classificação de dificuldade dos chamados (RF02).

Função pura, sem banco nem rede: recebe categoria, título e descrição e devolve
a dificuldade estimada de RESOLVER o chamado (FACIL, MEDIA, DIFICIL).
O síndico enxerga a fila do mais fácil para o mais difícil (RF03).

A categoria dá a pontuação base (1 a 10). Termos no texto sobem ou descem a
pontuação. O resultado traz a justificativa, para a decisão ser explicável.
"""

import re
import unicodedata

# Mesmos códigos do enum Categoria do backend Java.
PONTUACAO_BASE = {
    "ILUMINACAO": 2,
    "LIMPEZA": 2,
    "PORTARIA": 3,
    "ESTACIONAMENTO": 3,
    "BARULHO": 3,
    "CONVIVENCIA": 4,
    "AREAS_COMUNS": 4,
    "OUTROS": 4,
    "SEGURANCA": 5,
    "ELETRICA": 6,
    "HIDRAULICA": 6,
    "ELEVADOR": 8,
    "ESTRUTURA": 9,
}

# (peso, termos). Termos já sem acento e em minúsculas.
TERMOS = [
    (+2, ["infiltracao", "rachadura", "curto", "choque", "fiacao", "incendio",
          "fumaca", "gas", "inundacao", "desabamento", "estrutural", "bomba",
          "parado", "preso", "presa", "presos"]),
    (+1, ["todos", "todo o", "toda a", "varios", "varias", "bloco inteiro",
          "andar inteiro", "predio inteiro"]),
    (-2, ["lampada", "queimada", "queimado", "lixeira", "varrer", "varrida",
          "limpar", "simples", "pequeno", "pequena", "trocar"]),
]

LIMITE_FACIL = 3    # pontuação <= 3  -> FACIL
LIMITE_MEDIA = 6    # pontuação <= 6  -> MEDIA; acima disso DIFICIL


def _normalizar(texto: str) -> str:
    sem_acento = unicodedata.normalize("NFKD", texto or "")
    sem_acento = "".join(c for c in sem_acento if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", sem_acento.lower()).strip()


def _contem(texto: str, termo: str) -> bool:
    return re.search(rf"\b{re.escape(termo)}\b", texto) is not None


def nivel(pontuacao: int) -> str:
    if pontuacao <= LIMITE_FACIL:
        return "FACIL"
    if pontuacao <= LIMITE_MEDIA:
        return "MEDIA"
    return "DIFICIL"


def classificar(categoria: str, titulo: str = "", descricao: str = "") -> dict:
    """Devolve {'dificuldade', 'pontuacao', 'justificativa'}."""
    categoria = (categoria or "OUTROS").strip().upper()
    base = PONTUACAO_BASE.get(categoria)
    justificativa = []
    if base is None:
        base = PONTUACAO_BASE["OUTROS"]
        justificativa.append(f"categoria desconhecida '{categoria}', tratada como OUTROS: base {base}")
    else:
        justificativa.append(f"categoria {categoria}: base {base}")

    texto = _normalizar(f"{titulo} {descricao}")
    pontuacao = base
    for peso, termos in TERMOS:
        for termo in termos:
            if _contem(texto, termo):
                pontuacao += peso
                sinal = f"+{peso}" if peso > 0 else str(peso)
                justificativa.append(f"termo '{termo}': {sinal}")

    pontuacao = max(1, min(10, pontuacao))
    return {
        "dificuldade": nivel(pontuacao),
        "pontuacao": pontuacao,
        "justificativa": justificativa,
    }
