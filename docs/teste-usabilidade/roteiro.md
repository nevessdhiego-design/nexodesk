# Teste de usabilidade do NexoDesk

Objetivo: descobrir **onde uma pessoa de verdade trava** ao usar o sistema, antes da banca. Hoje os slides do projeto afirmam
"os testes confirmam alta aceitação", mas **nenhum teste com usuário foi feito**. Este roteiro serve para que, depois de aplicado,
o grupo possa dizer números reais (ou dizer "não realizamos" sem inventar nada).

## Regras de ouro

1. **Testa-se o sistema, não a pessoa.** Diga isso ao participante. Se ele errar, o erro é do sistema.
2. **Não ajude.** Fique em silêncio e anote. Só intervenha se ele ficar travado por mais de 2 minutos (anote "com ajuda").
3. **Peça para pensar em voz alta** ("o que você está procurando agora?").
4. **Não colete dados pessoais.** Use P1, P2, P3. Não grave rosto nem voz sem pedir permissão. Use os dados de demonstração
   (perfil `demo`), nunca dados reais de moradores.
5. **Quem pode participar:** gente que não participou do projeto. O ideal são **5 pessoas** (cerca de 3 a 4 moradores e 1 síndico
   ou pessoa que já cuidou de um condomínio); com 5 pessoas costumam aparecer a maior parte dos problemas graves (regra prática
   de Nielsen). Menos que isso vale como indício, não como resultado.

## Preparação (10 minutos, antes da primeira pessoa)

- Subir o sistema com dados de exemplo: `.\iniciar-nexodesk.ps1 -Perfil demo` (senhas por variável de ambiente).
- Deixar uma conta de morador e uma de síndico **já criadas** (para as tarefas que não são de cadastro).
- Ter uma foto qualquer no computador (ex.: foto de uma lâmpada) para a tarefa 3.
- Para a tarefa 6, antes de cada participante: entrar como síndico e **resolver** um chamado dele.
- Ficha de anotação impressa (abaixo) e cronômetro.

## Tarefas do MORADOR (leia uma de cada vez, em voz alta)

| # | Tarefa (como você fala) | Sucesso quando |
|---|---|---|
| M1 | "Você acabou de se mudar. Crie uma conta e entre no sistema." | chegou na tela "Meus chamados" |
| M2 | "A luz do corredor do seu andar queimou. Avise o síndico." | chamado criado com categoria Iluminação |
| M3 | "Agora faça o mesmo, mas anexe uma foto do problema." | chamado criado com foto |
| M4 | "Você não quer que o síndico saiba que foi você. Abra um chamado de barulho de vizinho sem se identificar." | chamado criado marcando anônimo |
| M5 | "Tem uma pessoa presa no elevador agora. Avise o síndico da forma mais rápida." | chamado criado como emergência (marcou a caixa **ou** o sistema detectou) e leu o aviso de ligar 192/193/190 |
| M6 | "O síndico disse que resolveu aquele chamado. Veja o que aconteceu e dê sua opinião sobre o atendimento." | achou o aviso/histórico e enviou uma nota |
| M7 | "Mude seu telefone cadastrado." | salvou em "Minha conta" |

## Tarefas do SÍNDICO

| # | Tarefa | Sucesso quando |
|---|---|---|
| S1 | "Quais chamados você atenderia primeiro e por quê?" | apontou a emergência e entendeu a ordem da fila |
| S2 | "Descubra por que o sistema acha que o chamado do elevador é difícil." | abriu "Por que essa dificuldade?" |
| S3 | "Avise o morador que o técnico vem amanhã." | mudou o status com observação |
| S4 | "Você quer levar a lista para uma reunião. Gere um arquivo." | baixou o CSV |
| S5 | "Um chamado é anônimo. Quem abriu?" | percebeu que **não** consegue saber (e entendeu o motivo) |
| S6 | "O texto está pequeno demais para você. Aumente." | usou o botão "Aa" |

## O que anotar em cada tarefa

| Participante | Tarefa | Conseguiu? (sim / com ajuda / não) | Tempo (s) | Erros ou hesitações | O que disse (frases literais) |
|---|---|---|---|---|---|
| P1 | M1 | | | | |

Marque também **onde clicou errado** e **o que não entendeu** (palavras, ícones, botões). Esses são os achados mais valiosos.

## Questionário SUS (ao final, para cada participante)

Para cada frase, o participante dá uma nota de **1 (discordo totalmente) a 5 (concordo totalmente)**.
SUS: *System Usability Scale* (John Brooke, 1996), de uso livre. Tradução livre para este projeto.

1. Acho que gostaria de usar este sistema com frequência.
2. Achei o sistema desnecessariamente complexo.
3. Achei o sistema fácil de usar.
4. Acho que precisaria de ajuda de uma pessoa técnica para conseguir usar este sistema.
5. Achei que as várias funções do sistema estavam bem integradas.
6. Achei que havia inconsistência demais neste sistema.
7. Imagino que a maioria das pessoas aprenderia a usar este sistema muito rapidamente.
8. Achei o sistema muito trabalhoso de usar.
9. Senti-me muito confiante usando o sistema.
10. Precisei aprender muitas coisas antes de conseguir usar o sistema.

Depois, 3 perguntas abertas: **O que foi mais difícil? O que você mais gostou? O que mudaria primeiro?**

## Como calcular

Copie as notas para um CSV (modelo em `respostas-exemplo.csv`) e rode:

```bash
python calcular_sus.py respostas.csv
```

Leitura: abaixo de 50 é ruim; cerca de 68 é a média de mercado; 80 ou mais é muito bom (Bangor et al., 2008).

## Como usar o resultado (sem exagerar)

1. Liste os problemas por **frequência** (quantos participantes travaram) e **gravidade** (impediu a tarefa? atrasou?).
2. Corrija os 3 mais graves antes da apresentação e **refaça o teste com 2 a 3 pessoas novas** para comparar o SUS antes e depois.
3. Nos slides, só afirme o que foi medido: "SUS médio de X com N participantes", "M5 concluída por N de N". Se não houve teste,
   escreva "teste de usabilidade planejado" e **não** escreva "alta aceitação".
4. Guarde as planilhas em `docs/teste-usabilidade/resultados/` (anonimizadas).
