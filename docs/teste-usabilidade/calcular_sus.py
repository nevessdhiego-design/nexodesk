#!/usr/bin/env python3
"""Calcula a pontuação SUS (System Usability Scale) de cada participante e a média.

Uso:
    python calcular_sus.py respostas.csv

O CSV tem uma linha por participante: `participante,perfil,q1,q2,...,q10` com notas de 1 a 5
(1 = discordo totalmente, 5 = concordo totalmente). Veja respostas-exemplo.csv.

Fórmula (Brooke, 1996): nas perguntas ÍMPARES (positivas) a contribuição é nota − 1; nas PARES (negativas) é 5 − nota.
A soma das 10 contribuições (0 a 40) vezes 2,5 dá a pontuação de 0 a 100.
Referência de leitura (Bangor et al., 2008): abaixo de 50 ruim; cerca de 68 é a média de mercado; 80 ou mais é muito bom.
Só biblioteca padrão do Python.
"""

import csv
import statistics
import sys


def pontuacao_sus(notas):
    """notas: lista com as 10 respostas (1 a 5) na ordem das perguntas. Devolve 0 a 100."""
    if len(notas) != 10:
        raise ValueError(f"o SUS tem 10 perguntas, vieram {len(notas)}")
    for n in notas:
        if not isinstance(n, int) or not 1 <= n <= 5:
            raise ValueError(f"nota inválida: {n!r} (use inteiros de 1 a 5)")
    soma = 0
    for posicao, nota in enumerate(notas, start=1):
        soma += (nota - 1) if posicao % 2 == 1 else (5 - nota)
    return soma * 2.5


def interpretar(pontuacao):
    if pontuacao >= 80:
        return "muito bom"
    if pontuacao >= 68:
        return "acima da média"
    if pontuacao >= 50:
        return "abaixo da média"
    return "ruim"


def ler_csv(caminho):
    with open(caminho, encoding="utf-8", newline="") as f:
        for linha in csv.DictReader(f):
            notas = [int(linha[f"q{i}"]) for i in range(1, 11)]
            yield linha["participante"], linha.get("perfil", ""), notas


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    resultados = []
    for participante, perfil, notas in ler_csv(argv[1]):
        pontos = pontuacao_sus(notas)
        resultados.append(pontos)
        print(f"{participante:<6} {perfil:<10} SUS = {pontos:5.1f}  ({interpretar(pontos)})")
    if not resultados:
        print("nenhuma resposta no arquivo")
        return 1
    media = statistics.mean(resultados)
    print(f"\nParticipantes: {len(resultados)} | média SUS = {media:.1f} ({interpretar(media)})"
          + (f" | mínimo {min(resultados):.1f} | máximo {max(resultados):.1f}" if len(resultados) > 1 else ""))
    if len(resultados) < 5:
        print("Atenção: com menos de 5 participantes a média é só um indício, não um resultado.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
