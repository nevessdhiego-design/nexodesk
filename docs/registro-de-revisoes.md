# NexoDesk: registro de revisões

Registro de quem revisou o quê (Sommerville, cap. 24, revisões e inspeções). **Só entra o que realmente aconteceu**; o que
ainda não foi revisado por outra pessoa do grupo aparece como pendente, de propósito.

| Data | Documento ou artefato | Quem revisou | O que a revisão corrigiu ou evitou |
|---|---|---|---|
| 20/10/2026 | `indicadores-medidos.md` e `documentacao-tecnica.md` | Dhiego, com o assistente de IA (Claude Code), conferindo cada número contra o comando que o reproduz | O "100% de acerto" da regra (27 casos de ajuste) deixou de ser citado como prova: vale o **73%** (11 de 15) em casos novos; números de testes e medições passaram a ter o comando para repetir |
| 29/10/2026 | Diagramas UML de casos de uso e de classes (entrega "Caso de Uso com UML") | O grupo, em reunião (4 prints em `PROJETOS/nexodesk/entregas/2026-10-29_print-reuniao-*.png`) | Diagramas tirados do código (17 casos de uso, 6 atores; classes com interfaces e enumerações). **O que a reunião corrigiu: a registrar pelo grupo** |
| 03/11/2026 | `indicadores-medidos.md`, `documentacao-tecnica.md`, README | Dhiego, com o assistente de IA | Contagem de testes (172 para 191), migrações (V1 a V12), limites de carga com "mesma máquina" e correção de "commits sem push" |
| 04/11/2026 | `docs/riscos.md` e `CONTRIBUTING.md` | Pendente: revisão por outro integrante (Giovanna, Kamili ou Julia) | A confirmar na reunião: probabilidade e efeito de cada risco |

## Pendências de revisão

- [ ] Revisão cruzada da documentação técnica por **outro integrante** (hoje só foi conferida por quem a escreveu).
- [ ] Conferir os diagramas do relatório (Canva) contra os desta documentação.
- [ ] Registrar o que a reunião de 29/10 corrigiu nos diagramas UML.
- [ ] Primeiro pull request revisado por outro integrante (ver [`../CONTRIBUTING.md`](../CONTRIBUTING.md)).
