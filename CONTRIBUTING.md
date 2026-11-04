# Como contribuir com o NexoDesk (política de versionamento)

Política de gerência de configuração do projeto (Sommerville, cap. 25). **Vale a partir de 04/11/2026.** Até 03/11 o trabalho
foi feito direto na `main` (32 commits, uma só branch); isso é um fato do histórico, não um modelo a seguir.

## Regras

1. **A `main` só recebe código por pull request.** Nada de commit direto na `main` para mudança de código.
2. **Um ramo por tarefa**, com o nome `tipo/assunto-curto`, saindo da `main` atualizada:
   `feature/reabrir-chamado`, `fix/rolagem-celular`, `docs/politica-versionamento`.
3. **Uma tarefa, um card do Kanban, um pull request.** O PR cita o card (`Fecha #NN`) e diz o que muda e como foi testado.
4. **A CI precisa estar verde** (Java no H2, Java no MySQL 8.4 e Python) antes de mesclar.
5. **Outra pessoa do grupo revisa** antes de mesclar: quem escreveu não aprova o próprio PR. A revisão confere o código, os testes
   e a documentação que a mudança toca.
6. **Pull requests pequenos** (de preferência menos de 400 linhas): reduzem conflito e tornam a revisão real.
7. **Conflito é resolvido por quem abriu o PR**, atualizando o ramo com a `main` (`git pull --rebase origin main`) antes de pedir revisão.

## Commits

- Primeira linha: o que mudou, no imperativo, até cerca de 72 caracteres ("Corrige rolagem lateral no celular").
- Corpo (quando precisar): o porquê e o que foi medido ou testado.
- Um assunto por commit. Sem `final`, `copia` ou `(1)` em nomes de arquivo.

## Banco de dados

- **Nunca edite uma migração já publicada** (`V1` a `V12`). Mudou o esquema? Crie a próxima (`V13__descricao.sql`).
- Toda migração nova passa nos testes do H2 e do MySQL.

## O que nunca entra no repositório

Senhas, tokens, chaves de API, dados pessoais reais, o arquivo `uploads/` e `credenciais-demo.local.txt`. As contas de
demonstração estão em [`docs/contas-demonstracao.md`](docs/contas-demonstracao.md), sem senha.

## Como a sobrescrita é evitada

- No código: ramos curtos, `main` protegida pela CI e pela revisão, conflito resolvido antes do merge.
- No sistema: duas pessoas editando o mesmo chamado não se atropelam; a segunda recebe `409` (campo `versao`, bloqueio otimista).

> **Para o dono do repositório:** a regra 1 só é imposta de verdade se a proteção de branch da `main` for ligada no GitHub
> (Settings, Branches: exigir pull request, CI verde e uma aprovação). Isso é uma configuração do GitHub, não do código.
