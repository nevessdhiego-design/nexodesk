# NexoDesk: tabela de riscos

Gestão de riscos do projeto (Sommerville, cap. 22, seção sobre gestão de riscos: identificar, analisar, planejar e monitorar).
Versão de 04/11/2026.

> **Como ler.** Os riscos e as estratégias vêm do que o projeto de fato fez (relatório da 2ª parcial, código, testes e
> documentação). **Probabilidade e efeito são estimativas registradas em 04/11/2026 para o grupo confirmar ou ajustar na reunião**;
> não foram medidas. Escala de probabilidade: baixa, moderada, alta. Escala de efeito: tolerável, sério, catastrófico.

## Tabela

| # | Risco | Tipo | Prob. | Efeito | Estratégia | O que foi feito | Como monitorar |
|---|---|---|---|---|---|---|---|
| R1 | **Prazo apertado até a entrega final** (o mais crítico) | projeto | alta | sério | **Minimização** | Reunião curta semanal; responsabilidades no Kanban; cortes de escopo declarados em [`ideias-futuras.md`](ideias-futuras.md) | Cards abertos no Kanban; datas do relatório da 2ª parcial |
| R2 | Pouco tempo da equipe (trabalho e faculdade) | projeto | alta | tolerável | **Minimização** | Reunião curta semanal; tarefas pequenas por card; papéis do Scrum definidos | Presença nas reuniões; cards parados |
| R3 | Serviço Python de prioridade fora do ar | produto | baixa | sério | **Contingência** | Reserva em Java (`ClassificadorLocal`) classifica só pela categoria; abrir chamado nunca fica bloqueado; **tem teste**. Limite: a reserva não detecta emergência | `classificacaoFallback` no chamado; teste do cenário |
| R4 | Dois usuários editando o mesmo chamado | produto | moderada | tolerável | **Minimização** | Bloqueio otimista (`chamados.versao`): o segundo recebe 409 | Teste de concorrência |
| R5 | Regra de dificuldade errar em casos novos | produto | moderada | tolerável | **Minimização** | Acerto medido em casos novos (73%, 11 de 15); o síndico decide e a justificativa aparece na tela | Revalidar com casos de um síndico real |
| R6 | RF05 depender de e-mail nunca testado com provedor real | produto | moderada | tolerável | **Evitação** | O aviso dentro do sistema é o canal principal; o e-mail é opcional e vem desligado | Teste com provedor real (pendente) |
| R7 | Dados pessoais dos moradores (LGPD) | negócio | baixa | sério | **Minimização** | Página de privacidade; exclusão de conta; foto sem localização; anonimato no chamado. **Sem revisão jurídica** | Revisão jurídica (pendente) |
| R8 | Revisão entre pares sem registro (acontece fora do GitHub) | projeto | moderada | sério | **Minimização** | Revisão entre pares já praticada; política em [`../CONTRIBUTING.md`](../CONTRIBUTING.md) passa a registrá-la: ramo por tarefa, pull request, CI verde, revisão de outro integrante | Pull requests revisados no GitHub |

## Por que cada estratégia (as três de Sommerville)

- **Evitação** muda o plano para o risco deixar de existir. Serve quando dá para abrir mão da coisa arriscada: foi o caso do
  e-mail (R6), que não é obrigatório para o RF05.
- **Minimização** reduz a probabilidade ou o efeito. É a escolha para o prazo (R1): prazo e escopo são fixos pela disciplina, então
  não dá para "evitar" o risco, só diminuí-lo.
- **Contingência** é o plano para quando o risco acontece. Serve ao Python fora do ar (R3): não dá para impedir uma queda, então
  a reserva garante que o sistema continue funcionando.

## Limites

Esta tabela não foi revisada por um orientador nem validada com stakeholders. Os níveis de probabilidade e efeito são
julgamento do grupo e podem mudar. Riscos novos entram aqui com data.
