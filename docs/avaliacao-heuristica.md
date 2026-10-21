# Avaliação heurística das telas e revisão dos casos (feita por IA, 20/10/2026)

> **O que isto é e o que não é.** É uma análise feita pela IA que escreveu o código, usando as 10 heurísticas de Nielsen e a
> leitura das telas e do código. **Não é teste de usabilidade** (nenhuma pessoa usou o sistema), **não gera SUS** e **não é
> validação por síndico**. Serve para achar problemas prováveis e para o grupo priorizar o que testar com gente de verdade.
> Quem fez a análise conhece o código, então tende a ser benevolente: trate como lista de hipóteses.

## 1. Revisão independente da coluna "esperado" dos 15 casos de validação

Reli os 15 casos como se fosse um síndico e dei a minha dificuldade esperada. **Limite:** eu mesmo escrevi a coluna original e já tinha
visto o que a regra respondia, então não sou independente. O que importa é onde eu **duvido** da expectativa:

| Caso | Esperado no documento | Minha opinião | Observação |
|---|---|---|---|
| Luz de emergência da escada não acende | Média | Média | concordo (equipamento de segurança; a regra erra) |
| Caçamba de entulho cheia | Fácil | Fácil | concordo |
| Encomenda sem aviso | Fácil | Fácil | concordo (a regra erra) |
| Moto na calçada | Fácil | Fácil | concordo |
| Obra começou às 7h | Fácil | Fácil | concordo |
| Vizinho joga lixo pela janela | Média | Média | concordo |
| Quadra com rede rasgada | Média | **Fácil ou Média** | duvido: trocar uma rede é simples, mas exige compra |
| Cerca elétrica desligada | Média | Média | concordo |
| Disjuntor desarmando | Média | Média | concordo |
| Fio solto na piscina | Média + emergência | Média + emergência | concordo (a regra erra a dificuldade, acerta a emergência) |
| Caixa d'água transbordando | Média | **Fácil ou Média** | duvido: costuma ser a boia, mas pode ser sinal de problema maior |
| Falta de água | Difícil | Difícil | concordo |
| Barulho estranho no elevador | Média | Média | concordo (a regra erra) |
| Reboco caiu da fachada | Difícil | Difícil | concordo |
| Dúvida sobre a taxa | Fácil | Fácil | concordo |

**Resultado:** concordo com 13 dos 15; duvido de 2 (quadra e caixa d'água), e nos dois a regra deu "Média", que está dentro do que eu aceitaria.
Se aceitasse as duas respostas nesses casos, a regra acertaria os mesmos 11 de 15 (73%). **Isso não muda o número a citar nem substitui a
validação por um síndico real.** Se um síndico real discordar de muitas linhas, o 73% muda.

## 2. Avaliação heurística (hipóteses de problemas, por prioridade)

| # | Heurística | Problema provável | Onde | Gravidade (1 a 4) | Sugestão |
|---|---|---|---|---|---|
| 1 | Prevenção de erros | ~~Mudar para "Resolvido" era um clique sem confirmação~~ **corrigido em 20/10**: pergunta antes de resolver (conferido no navegador) | `sindico.js`, `zelador.js` | 3 | feito |
| 2 | Ajuda e recuperação | ~~Não existia "esqueci minha senha"~~ **resolvido em parte em 20/10**: o síndico gera uma senha temporária na tela de usuários. Continua sem recuperação automática por e-mail | login, `usuarios.html` | 3 | e-mail real (próximo passo) |
| 3 | Visibilidade do estado | Lista do síndico sem busca: com dezenas de chamados rola muito. **Busca por texto e filtro de categoria feitos em 20/10** (conferido no navegador, sem diferenciar maiúsculas e acentos, mantendo a posição real na fila). **Paginação feita em 20/10** (20 por vez, "Mostrar mais") | `sindico.html` | 3 | feito |
| 4 | Reconhecimento | A caixa "é uma emergência" fica no fim do formulário, depois da foto e do anonimato | `morador.html` | 2 | levar para perto do título ou destacar |
| 5 | Controle e liberdade | Chamado aberto não pode ser editado nem cancelado pelo morador | `morador.js` | 2 | permitir cancelar enquanto "Aberto" |
| 6 | Visibilidade do estado | Depois de enviar um chamado, o morador vê só uma mensagem no alto; a lista fica abaixo do formulário, fora da tela em celular | `morador.html` | 2 | rolar até o novo chamado |
| 7 | Estética e minimalismo | A explicação da fila no topo do painel do síndico é longa e fica sempre visível | `sindico.html` | 1 | mostrar uma vez ou recolher |
| 8 | Flexibilidade | A gestão de usuários não tem filtro por perfil nem por situação (só busca por texto) | `usuarios.html` | 1 | filtro rápido "só equipe" |

**O que já está bom (conferido no código ou no navegador):** foco visível, links "pular para o conteúdo", um `h1` por tela, contraste AA nas três
aparências (síndico, morador e usuários), rótulo em todo campo, mensagens de erro em português claro, tempo real sem recarregar, confirmação ao
desativar usuário e ao excluir a conta, anonimato explicado na própria tela.

**Não avaliado:** leitor de tela real, teclado em todos os fluxos (só estrutura e rótulos), celulares reais, pessoas com baixa familiaridade com tecnologia.

## 3. O que dizer na apresentação (verdadeiro)

- "Fizemos uma **avaliação heurística** das telas (10 heurísticas de Nielsen) e uma revisão dos casos da regra. Isso encontrou 8 pontos de
  melhoria, dois de gravidade 3 que são os próximos."
- **Não dizer** "testamos com usuários", "SUS de X pontos" nem "validado por síndico" se isso não aconteceu com pessoas reais e com respostas em mãos.
