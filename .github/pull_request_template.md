## O que muda
<!-- Em uma ou duas frases. Cite o card do Kanban: "Fecha #NN". -->

## Por que
<!-- O problema ou o requisito que isto resolve. -->

## Como foi testado
<!-- Comandos rodados e o que você viu na tela. Se não testou algo, diga qual. -->
- [ ] `.\mvnw.cmd test` (api-java)
- [ ] `python -m pytest -q` (servico-prioridade), se mexeu nas regras
- [ ] Conferido no navegador, se mexeu em tela

## Antes de pedir revisão
- [ ] A CI está verde (Java no H2, Java no MySQL 8.4 e Python)
- [ ] Sem senha, token, chave ou dado pessoal no código, nos testes ou nos prints
- [ ] Migração publicada não foi editada (mudou o banco? arquivo novo `V<n+1>__...sql`)
- [ ] Documentação atualizada, se mudou comportamento (`README.md` ou `docs/`)

## Revisão
Pela política do [`CONTRIBUTING.md`](../CONTRIBUTING.md), outro integrante revisa antes de mesclar.
