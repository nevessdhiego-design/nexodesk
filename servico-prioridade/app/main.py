"""Serviço de prioridade do NexoDesk (Python + FastAPI).

Rodar:  uvicorn app.main:app --port 8001
Docs:   http://localhost:8001/docs
"""

from fastapi import FastAPI
from pydantic import BaseModel, Field

from .regras import PONTUACAO_BASE, classificar

app = FastAPI(title="NexoDesk - Serviço de Prioridade", version="1.0.0")


class PedidoClassificacao(BaseModel):
    categoria: str = Field(..., max_length=40)
    titulo: str = Field("", max_length=200)
    descricao: str = Field("", max_length=5000)


class ResultadoClassificacao(BaseModel):
    dificuldade: str
    pontuacao: int
    justificativa: list[str]


@app.get("/saude")
def saude() -> dict:
    return {"status": "ok"}


@app.get("/categorias")
def categorias() -> list[str]:
    return sorted(PONTUACAO_BASE)


@app.post("/classificar", response_model=ResultadoClassificacao)
def classificar_chamado(pedido: PedidoClassificacao) -> dict:
    return classificar(pedido.categoria, pedido.titulo, pedido.descricao)
