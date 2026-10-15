# Casos de prioridade: validação da regra de dificuldade (RF02)

Data: 14/10/2026. Item 7 do plano de continuidade e issue #17 ("Refinar a lógica de priorização automática").

## Como usar este documento

1. A coluna **Esperado** é uma **proposta minha**, escrita sem conversar com ninguém. **Não é a verdade.**
2. O Product Owner e o grupo (idealmente com a opinião de um síndico de verdade) corrigem a coluna "Esperado" onde discordarem.
3. Só depois disso a regra em `servico-prioridade/app/regras.py` é ajustada, e cada caso vira um teste em `tests/test_regras.py`.

Ajustar a regra para bater com as minhas expectativas seria circular: é o grupo que decide o que é fácil ou difícil.

## Resultado atual: 17 de 27 casos batem (63%)

Colunas: **Obtido** é o que a regra devolve hoje (pontuação de 1 a 10 entre parênteses; até 3 Fácil, até 6 Média, acima Difícil).

| | Categoria | Título | Esperado | Obtido | Por que esperar isso | Como a regra chegou lá |
|---|---|---|---|---|---|---|
| ✅ | ILUMINACAO | Lâmpada queimada no corredor | Fácil | Fácil (1) | troca simples, sem obra | categoria ILUMINACAO: base 2; termo 'lampada': -2; termo 'queimada': -2; termo 'trocar': -2 |
| ❌ | ILUMINACAO | Poste do estacionamento apagado | Média | Fácil (2) | pode ser fiação ou reator, exige técnico | categoria ILUMINACAO: base 2 |
| ✅ | ILUMINACAO | Luz do hall piscando | Fácil | Fácil (2) | troca de lâmpada ou reator | categoria ILUMINACAO: base 2 |
| ✅ | LIMPEZA | Lixeira transbordando | Fácil | Fácil (1) | só reorganizar a coleta | categoria LIMPEZA: base 2; termo 'lixeira': -2 |
| ❌ | LIMPEZA | Piscina com água verde | Média | Fácil (2) | tratamento químico e filtro | categoria LIMPEZA: base 2 |
| ❌ | PORTARIA | Interfone sem funcionar | Média | Fácil (3) | pode exigir técnico | categoria PORTARIA: base 3 |
| ❌ | PORTARIA | Portaria sem porteiro à noite | Média | Fácil (3) | envolve empresa terceirizada | categoria PORTARIA: base 3 |
| ✅ | ESTACIONAMENTO | Carro na minha vaga | Fácil | Fácil (3) | aviso ao proprietário ou reboque | categoria ESTACIONAMENTO: base 3 |
| ❌ | ESTACIONAMENTO | Vaga demarcada apagada | Média | Fácil (3) | serviço de pintura | categoria ESTACIONAMENTO: base 3 |
| ✅ | BARULHO | Música alta depois das 22h | Fácil | Fácil (3) | advertência formal | categoria BARULHO: base 3 |
| ✅ | BARULHO | Obra fora do horário | Fácil | Fácil (3) | regimento interno | categoria BARULHO: base 3 |
| ✅ | CONVIVENCIA | Cachorro latindo o dia todo | Média | Média (5) | mediação entre vizinhos | categoria CONVIVENCIA: base 4; termo 'todo o': +1 |
| ✅ | AREAS_COMUNS | Churrasqueira quebrada | Média | Média (4) | conserto de serralheria | categoria AREAS_COMUNS: base 4 |
| ❌ | AREAS_COMUNS | Reserva do salão de festas | Fácil | Média (4) | informação, sem execução | categoria AREAS_COMUNS: base 4 |
| ✅ | SEGURANCA | Portão da garagem não fecha | Média | Média (6) | sensor ou motor, exige técnico | categoria SEGURANCA: base 5; termo 'varios': +1 |
| ✅ | SEGURANCA | Câmera do térreo sem imagem | Média | Média (5) | técnico de CFTV | categoria SEGURANCA: base 5 |
| ✅ | ELETRICA | Tomada do salão sem energia | Média | Média (6) | disjuntor ou fiação | categoria ELETRICA: base 6 |
| ✅ | ELETRICA | Choque na porta de metal | Difícil | Difícil (8) | risco à vida, eletricista com urgência | categoria ELETRICA: base 6; termo 'choque': +2 |
| ❌ | HIDRAULICA | Torneira pingando | Fácil | Média (6) | troca de vedação | categoria HIDRAULICA: base 6 |
| ✅ | HIDRAULICA | Vazamento no teto da garagem | Média | Média (6) | localizar o cano | categoria HIDRAULICA: base 6 |
| ❌ | HIDRAULICA | Entupimento na coluna de esgoto | Difícil | Média (6) | desentupimento pesado, vários apartamentos | categoria HIDRAULICA: base 6 |
| ✅ | ELEVADOR | Elevador parado | Difícil | Difícil (10) | empresa de manutenção, peça específica | categoria ELEVADOR: base 8; termo 'parado': +2 |
| ❌ | ELEVADOR | Botão do elevador não acende | Média | Difícil (8) | técnico, peça simples | categoria ELEVADOR: base 8 |
| ✅ | ELEVADOR | Pessoa presa no elevador | Difícil | Difícil (10) | emergência | categoria ELEVADOR: base 8; termo 'presa': +2 |
| ✅ | ESTRUTURA | Rachadura na parede do hall | Difícil | Difícil (10) | laudo de engenheiro | categoria ESTRUTURA: base 9; termo 'rachadura': +2 |
| ✅ | ESTRUTURA | Infiltração na laje | Difícil | Difícil (10) | impermeabilização | categoria ESTRUTURA: base 9; termo 'infiltracao': +2 |
| ❌ | OUTROS | Sugestão de horta comunitária | Fácil | Média (4) | decisão em assembleia, sem execução imediata | categoria OUTROS: base 4 |

## Onde a regra diverge e o que isso sugere

Padrões nas 10 divergências (hipóteses para discutir, nada foi alterado):

1. **A categoria pesa demais.** "Botão do elevador não acende" sai **Difícil** só porque Elevador tem base 8;
   "Torneira pingando" sai **Média** porque Hidráulica tem base 6. Dentro de uma categoria há problemas simples e graves.
   *Ideia:* termos de simplicidade específicos (botão, torneira, vedação, pingando) com peso −2 ou −3.
2. **Faltam termos de gravidade.** "Entupimento na coluna de esgoto" e "esgoto voltando" não somam nada.
   *Ideia:* termos como "esgoto", "coluna" e "voltando" com +2; "vários apartamentos" com +1.
3. **Iluminação, Portaria e Estacionamento ficam sempre Fáceis** (base 2 e 3), mas "poste apagado",
   "interfone sem funcionar" e "pintura das vagas" exigem técnico ou obra.
   *Ideia:* termos de necessidade de técnico ou serviço externo (poste, interfone, pintura, motor, sensor) com +1 ou +2.
4. **Pedidos de informação** ("Reserva do salão de festas", "Sugestão de horta") não são problemas a consertar.
   *Ideia:* subir a regra "pergunta ou sugestão" (como, gostaria de, sugestão) com −2, ou criar uma categoria própria.
5. **Piscina com água verde** (Limpeza) pede tratamento químico: termo "piscina" com +1 ou +2.

## Limitações do método

- Termos são procurados no texto sem entender o contexto (por exemplo, "não" não inverte o sentido).
- Só 27 casos, todos escritos por uma pessoa. Para a banca, o ideal é o grupo trazer 5 a 10 casos reais de condomínios que conhecem.
- Se a regra mudar, o `RegrasTest` do Java exige que as pontuações base de `dominio/Categoria.java` continuem iguais às do Python.
