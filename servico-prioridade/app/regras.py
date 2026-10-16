"""Regras de classificação dos chamados (RF02).

Função pura, sem banco nem rede: recebe categoria, título e descrição e devolve DUAS coisas independentes:

* **dificuldade** (FACIL, MEDIA, DIFICIL): o quanto é trabalhoso resolver. Define a ordem normal da fila do síndico,
  do mais fácil ao mais difícil (RF03).
* **emergencia** (True/False): risco à vida, à saúde ou à segurança. Emergência fura a fila, **não importa a dificuldade**
  (pessoa presa no elevador é difícil E urgente; lâmpada queimada é fácil E não urgente).

Dificuldade e urgência são conceitos diferentes e por isso são calculados separadamente.

A categoria dá a pontuação base da dificuldade (1 a 10). Termos no texto sobem ou descem a pontuação. O resultado traz a
justificativa, para a decisão ser explicável. A detecção de emergência é um AVISO ao síndico (o morador também pode
marcar à mão); este sistema não substitui os serviços de emergência (SAMU 192, Bombeiros 193, Polícia 190).
"""

import re
import unicodedata

# Mesmos códigos do enum Categoria do backend Java (o RegrasTest do Java confere que os números são iguais).
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

# (peso, termos). Termos já sem acento e em minúsculas; casam como palavra inteira.
TERMOS = [
    # gravidade ou risco: mais difícil de resolver
    (+2, ["infiltracao", "rachadura", "curto", "choque", "fiacao", "incendio",
          "fumaca", "gas", "inundacao", "desabamento", "estrutural", "bomba",
          "parado", "preso", "presa", "presos", "poste"]),
    # abrangência ou necessidade de técnico/serviço externo
    (+1, ["todos", "todo o", "toda a", "varios", "varias", "bloco inteiro",
          "andar inteiro", "predio inteiro", "varios apartamentos",
          "esgoto", "coluna", "voltando",
          "motor", "sensor", "interfone", "porteiro", "pintura"]),
    (+2, ["piscina"]),
    # simplicidade: troca ou reparo pequeno
    (-2, ["lampada", "queimada", "queimado", "lixeira", "varrer", "varrida",
          "limpar", "simples", "pequeno", "pequena", "trocar",
          "botao", "torneira"]),
    (-1, ["pinga"]),
    # pedido de informação ou sugestão: não é problema a consertar
    (-2, ["como reservar", "gostaria de saber", "gostaria de propor", "sugestao", "sugiro", "propor", "duvida"]),
]

# Emergência: padrões (regex sobre o texto sem acento) que indicam risco imediato. Cada padrão tem um rótulo
# curto que aparece na justificativa. Escritos para evitar falso positivo: "portão preso" NÃO é emergência,
# "pessoa presa no portão" é; "taxa de gás" NÃO é, "cheiro de gás" é.
EMERGENCIAS = [
    ("pessoa presa", r"\bpres[ao]s?\s+(no|na|dentro\s+d[oa])\s+(elevador|banheiro|apartamento|sauna|cabine|quarto)\b"),
    ("pessoa presa", r"\b(gente|pessoas?|crianca|idoso|idosa|alguem|morador|moradora)\b.{0,20}\bpres[ao]s?\b"),
    ("choque elétrico", r"\b(levei|levou|tomei|tomou|deu|dando|da|dar)\s+(um\s+)?choque\b"),
    ("fio exposto", r"\bfios?\s+(solto|soltos|exposto|expostos|desencapado|desencapados)\b"),
    ("faísca", r"\bfaisc(a|as|ando)\b"),
    ("vazamento de gás", r"\b(cheiro\s+(forte\s+)?de\s+gas|vazamento\s+de\s+gas|gas\s+vazando|vazando\s+gas)\b"),
    ("fogo ou fumaça", r"\b(incendio|fumaca|pegando\s+fogo|fogo(?!\s+de\s+artificio))\b"),
    ("alagamento", r"\b(inundacao|inundando|alagando|alagamento)\b"),
    ("risco de desabamento", r"\b(desabamento|desabou|desabando|risco\s+de\s+queda)\b"),
    ("invasão ou assalto", r"\b(assalto|invasao|invadiram|arrombamento|arrombaram)\b"),
    ("emergência de saúde", r"\b(desmaiou|desmaio|infarto|ambulancia|socorro|sangrando)\b"),
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


def detectar_emergencia(titulo: str = "", descricao: str = "") -> list:
    """Devolve os rótulos das emergências encontradas (lista vazia = nenhuma), sem repetir rótulo."""
    texto = _normalizar(f"{titulo} {descricao}")
    achados = []
    for rotulo, padrao in EMERGENCIAS:
        if rotulo not in achados and re.search(padrao, texto):
            achados.append(rotulo)
    return achados


def classificar(categoria: str, titulo: str = "", descricao: str = "") -> dict:
    """Devolve {'dificuldade', 'pontuacao', 'emergencia', 'justificativa'}."""
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
    emergencias = detectar_emergencia(titulo, descricao)
    for rotulo in emergencias:
        justificativa.append(f"emergência detectada: {rotulo}")
    return {
        "dificuldade": nivel(pontuacao),
        "pontuacao": pontuacao,
        "emergencia": bool(emergencias),
        "justificativa": justificativa,
    }
