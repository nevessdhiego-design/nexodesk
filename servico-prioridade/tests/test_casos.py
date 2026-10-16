"""Mede a regra contra os conjuntos de casos (veja casos.py) e guarda contra regressão.

Os pisos abaixo são o resultado REAL de hoje, não metas otimistas: se uma mudança futura piorar a regra, o teste avisa.
O número que importa é o do conjunto de VALIDAÇÃO (casos que a regra nunca viu); o de ajuste é sempre alto porque os
termos foram escolhidos olhando para ele.
"""

from app.regras import classificar, detectar_emergencia
from tests.casos import CASOS_DE_AJUSTE, CASOS_DE_VALIDACAO, NAO_SAO_EMERGENCIA, SAO_EMERGENCIA

PISO_AJUSTE = 27       # de 27
PISO_VALIDACAO = 11    # de 15 (73%)


def _acertos(casos):
    return sum(
        1 for cat, tit, desc, dificuldade, emergencia in casos
        if (r := classificar(cat, tit, desc))["dificuldade"] == dificuldade and r["emergencia"] == emergencia
    )


def test_conjunto_de_ajuste_nao_regride():
    assert _acertos(CASOS_DE_AJUSTE) >= PISO_AJUSTE, "a regra voltou a errar casos que já acertava"


def test_conjunto_de_validacao_nao_regride():
    acertos = _acertos(CASOS_DE_VALIDACAO)
    assert acertos >= PISO_VALIDACAO, f"validação caiu para {acertos}/{len(CASOS_DE_VALIDACAO)}"


def test_os_dois_conjuntos_nao_se_misturam():
    ajuste = {(c, t) for c, t, *_ in CASOS_DE_AJUSTE}
    validacao = {(c, t) for c, t, *_ in CASOS_DE_VALIDACAO}
    assert not ajuste & validacao, "um caso de validação também está no conjunto de ajuste"


def test_toda_frase_de_emergencia_e_detectada_com_o_rotulo_certo():
    for _categoria, titulo, descricao, rotulo in SAO_EMERGENCIA:
        assert rotulo in detectar_emergencia(titulo, descricao), f"não detectou: {titulo!r} ({rotulo})"


def test_nenhum_falso_alarme_de_emergencia():
    for _categoria, titulo, descricao in NAO_SAO_EMERGENCIA:
        assert detectar_emergencia(titulo, descricao) == [], f"falso alarme: {titulo!r}"


def test_emergencia_e_independente_da_dificuldade():
    facil_e_urgente = classificar("ILUMINACAO", "Lâmpada queimada", "Lâmpada queimada e saindo faíscas do soquete")
    assert facil_e_urgente["dificuldade"] == "FACIL" and facil_e_urgente["emergencia"] is True

    dificil_sem_urgencia = classificar("ESTRUTURA", "Infiltração na laje", "Infiltração no teto quando chove")
    assert dificil_sem_urgencia["dificuldade"] == "DIFICIL" and dificil_sem_urgencia["emergencia"] is False


def test_emergencia_aparece_na_justificativa():
    r = classificar("ELEVADOR", "Elevador parado", "Tem gente presa no elevador")
    assert r["emergencia"] is True
    assert any("emergência detectada: pessoa presa" in linha for linha in r["justificativa"])


def test_endpoint_devolve_o_campo_emergencia():
    from fastapi.testclient import TestClient

    from app.main import app

    resposta = TestClient(app).post("/classificar", json={
        "categoria": "ELEVADOR", "titulo": "Elevador parado", "descricao": "Tem gente presa"})
    assert resposta.status_code == 200
    assert resposta.json()["emergencia"] is True
