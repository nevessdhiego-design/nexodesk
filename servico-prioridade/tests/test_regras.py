from fastapi.testclient import TestClient

from app.main import app
from app.regras import PONTUACAO_BASE, classificar, nivel


def test_lampada_queimada_e_facil():
    r = classificar("ILUMINACAO", "Lâmpada queimada", "Trocar a lâmpada do corredor do 3º andar")
    assert r["dificuldade"] == "FACIL"
    assert r["pontuacao"] == 1  # 2 - 2 (lampada) - 2 (queimada) - 2 (trocar), mínimo 1


def test_elevador_preso_e_dificil():
    r = classificar("ELEVADOR", "Elevador parado", "Tem gente presa no elevador")
    assert r["dificuldade"] == "DIFICIL"
    assert r["pontuacao"] == 10  # 8 + parado(2) + presa(2) = 12, limitado ao teto 10


def test_infiltracao_na_estrutura_e_dificil_e_teto_10():
    r = classificar("ESTRUTURA", "Infiltração e rachadura", "Rachadura no bloco inteiro")
    assert r["dificuldade"] == "DIFICIL"
    assert r["pontuacao"] == 10


def test_barulho_sem_termos_e_facil_pela_base():
    r = classificar("BARULHO", "Festa", "Música alta depois das 22h")
    assert r["dificuldade"] == "FACIL"
    assert r["pontuacao"] == 3


def test_hidraulica_vazamento_e_media():
    r = classificar("HIDRAULICA", "Vazamento", "Vazamento no teto da garagem")
    assert r["pontuacao"] == 6
    assert r["dificuldade"] == "MEDIA"


def test_vazamento_de_gas_sobe_para_dificil():
    r = classificar("HIDRAULICA", "Vazamento de gás", "")
    assert r["pontuacao"] == 8  # 6 + gas(2)
    assert r["dificuldade"] == "DIFICIL"


def test_categoria_desconhecida_vira_outros_e_explica():
    r = classificar("INEXISTENTE", "x", "y")
    assert r["pontuacao"] == PONTUACAO_BASE["OUTROS"]
    assert "desconhecida" in r["justificativa"][0]


def test_acentos_e_caixa_nao_atrapalham():
    a = classificar("ELETRICA", "CURTO NA FIAÇÃO", "")
    b = classificar("ELETRICA", "curto na fiacao", "")
    assert a == b
    assert a["pontuacao"] == 10  # 6 + curto(2) + fiacao(2)


def test_termo_nao_casa_no_meio_da_palavra():
    # "gas" não pode casar dentro de "gasolina"
    r = classificar("OUTROS", "Cheiro de gasolina", "")
    assert r["pontuacao"] == PONTUACAO_BASE["OUTROS"]


def test_limites_dos_niveis():
    assert nivel(3) == "FACIL"
    assert nivel(4) == "MEDIA"
    assert nivel(6) == "MEDIA"
    assert nivel(7) == "DIFICIL"


def test_endpoint_classificar():
    client = TestClient(app)
    resp = client.post("/classificar", json={
        "categoria": "ILUMINACAO", "titulo": "Lâmpada queimada", "descricao": ""})
    assert resp.status_code == 200
    assert resp.json()["dificuldade"] == "FACIL"


def test_endpoint_rejeita_categoria_ausente():
    client = TestClient(app)
    assert client.post("/classificar", json={"titulo": "x"}).status_code == 422
