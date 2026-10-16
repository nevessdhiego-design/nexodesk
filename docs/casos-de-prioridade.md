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

---

# Atualização da fase 2 (15/10/2026): refinamento, emergência e validação honesta

## O que mudou na regra

1. **Emergência separada da dificuldade.** A regra agora devolve também `emergencia`. Pessoa presa, choque, fio exposto,
   gás, fogo, alagamento, risco de desabamento, assalto e emergência de saúde **furam a fila** do síndico, qualquer que seja
   a dificuldade (uma lâmpada fácil com faíscas é urgente; uma infiltração difícil sem risco não é). Os padrões evitam
   falso alarme: "portão preso", "carro preso na vaga", "taxa de gás" e "fogos de artifício" **não** disparam.
2. **Termos novos** nascidos das divergências da tabela acima: pedidos de informação e sugestões (−2), peças pequenas
   (botão, torneira: −2), gravidade do esgoto, necessidade de técnico (interfone, motor, sensor, poste), piscina.
3. **Dois conjuntos de casos, de propósito:**
   - **Ajuste** (os 27 casos acima): serviram para escolher os termos. Acertar 100% ali **não prova nada**.
   - **Validação** (15 casos escritos **depois** de fixar os termos, que a regra nunca viu): é o número que vale.

## Resultado

| Conjunto | Antes (14/10) | Depois (15/10) |
|---|---|---|
| Ajuste (27 casos, usados para ajustar) | 17 de 27 (63%) | **27 de 27** (100%), esperado: foi ajustado nesses |
| **Validação (15 casos nunca vistos)** | não existia | **11 de 15 (73%)** |

A diferença entre 100% e 73% é o tamanho do "ajuste que decorou os casos". **Use o número da validação** em qualquer slide.
Emergência: 12 de 12 frases de risco detectadas e 0 falsos alarmes nas 7 frases de controle (`tests/casos.py`).

## Conjunto de validação (casos nunca vistos)

| | Categoria | Título | Esperado | Obtido | Como a regra chegou lá |
|---|---|---|---|---|---|
| ❌ | ILUMINACAO | Luz de emergência da escada não acende | Média | Fácil (2) | categoria ILUMINACAO: base 2 |
| ✅ | LIMPEZA | Caçamba de entulho cheia | Fácil | Fácil (2) | categoria LIMPEZA: base 2 |
| ❌ | PORTARIA | Encomenda sem aviso | Fácil | Média (4) | categoria PORTARIA: base 3; termo 'porteiro': +1 |
| ✅ | ESTACIONAMENTO | Moto na calçada | Fácil | Fácil (3) | categoria ESTACIONAMENTO: base 3 |
| ✅ | BARULHO | Obra começou às 7h | Fácil | Fácil (3) | categoria BARULHO: base 3 |
| ✅ | CONVIVENCIA | Vizinho joga lixo pela janela | Média | Média (4) | categoria CONVIVENCIA: base 4 |
| ✅ | AREAS_COMUNS | Quadra com rede rasgada | Média | Média (4) | categoria AREAS_COMUNS: base 4 |
| ✅ | SEGURANCA | Cerca elétrica desligada | Média | Média (5) | categoria SEGURANCA: base 5 |
| ✅ | ELETRICA | Disjuntor desarmando | Média | Média (6) | categoria ELETRICA: base 6 |
| ❌ | ELETRICA | Fio solto na piscina | Média + emergência | Difícil (8) + emergência | categoria ELETRICA: base 6; termo 'piscina': +2; emergência detectada: fio exposto |
| ✅ | HIDRAULICA | Caixa d'água transbordando | Média | Média (6) | categoria HIDRAULICA: base 6 |
| ✅ | HIDRAULICA | Falta de água | Difícil | Difícil (7) | categoria HIDRAULICA: base 6; termo 'bloco inteiro': +1 |
| ❌ | ELEVADOR | Barulho estranho no elevador | Média | Difícil (8) | categoria ELEVADOR: base 8 |
| ✅ | ESTRUTURA | Reboco caiu da fachada | Difícil | Difícil (9) | categoria ESTRUTURA: base 9 |
| ✅ | OUTROS | Dúvida sobre a taxa | Fácil | Fácil (2) | categoria OUTROS: base 4; termo 'duvida': -2 |

### O que os erros ensinam (limites reais de uma regra por palavras)

- **"Luz de emergência da escada não acende"**: a categoria Iluminação tem base baixa e nada no texto indica equipamento especial.
- **"Encomenda sem aviso"**: o termo `porteiro` (+1) foi criado para "portaria sem porteiro à noite" e aqui prejudica. É sinal de
  ajuste excessivo a um caso.
- **"Fio solto na piscina"**: o termo `piscina` (+2) foi pensado para a água verde da piscina; aqui soma pontos a um problema elétrico.
  (A emergência foi detectada corretamente.)
- **"Barulho estranho no elevador"**: qualquer chamado de Elevador começa em 8 (Difícil), mesmo quando o problema é pequeno.

Esses erros **não foram corrigidos de propósito**: corrigi-los agora seria ajustar a regra ao conjunto de validação e o número deixaria
de significar alguma coisa. Se o grupo quiser, a próxima rodada cria um **terceiro** conjunto, com casos reais de síndicos.

## Ainda pendente

- A coluna "Esperado" é proposta de quem escreveu. **Nenhum síndico real validou.** Isso precisa do grupo.
- Se o serviço Python estiver fora do ar, o Java classifica só pela categoria e **não detecta emergência** (o morador ainda pode marcar à mão).

## Conjunto de ajuste (27 casos) com o resultado atual

| | Categoria | Título | Esperado | Obtido | Como a regra chegou lá |
|---|---|---|---|---|---|
| ✅ | ILUMINACAO | Lâmpada queimada no corredor | Fácil | Fácil (1) | categoria ILUMINACAO: base 2; termo 'lampada': -2; termo 'queimada': -2; termo 'trocar': -2 |
| ✅ | ILUMINACAO | Poste do estacionamento apagado | Média | Média (4) | categoria ILUMINACAO: base 2; termo 'poste': +2 |
| ✅ | ILUMINACAO | Luz do hall piscando | Fácil | Fácil (2) | categoria ILUMINACAO: base 2 |
| ✅ | LIMPEZA | Lixeira transbordando | Fácil | Fácil (1) | categoria LIMPEZA: base 2; termo 'lixeira': -2 |
| ✅ | LIMPEZA | Piscina com água verde | Média | Média (4) | categoria LIMPEZA: base 2; termo 'piscina': +2 |
| ✅ | PORTARIA | Interfone sem funcionar | Média | Média (4) | categoria PORTARIA: base 3; termo 'interfone': +1 |
| ✅ | PORTARIA | Portaria sem porteiro à noite | Média | Média (4) | categoria PORTARIA: base 3; termo 'porteiro': +1 |
| ✅ | ESTACIONAMENTO | Carro na minha vaga | Fácil | Fácil (3) | categoria ESTACIONAMENTO: base 3 |
| ✅ | ESTACIONAMENTO | Vaga demarcada apagada | Média | Média (4) | categoria ESTACIONAMENTO: base 3; termo 'pintura': +1 |
| ✅ | BARULHO | Música alta depois das 22h | Fácil | Fácil (3) | categoria BARULHO: base 3 |
| ✅ | BARULHO | Obra fora do horário | Fácil | Fácil (3) | categoria BARULHO: base 3 |
| ✅ | CONVIVENCIA | Cachorro latindo o dia todo | Média | Média (5) | categoria CONVIVENCIA: base 4; termo 'todo o': +1 |
| ✅ | AREAS_COMUNS | Churrasqueira quebrada | Média | Média (4) | categoria AREAS_COMUNS: base 4 |
| ✅ | AREAS_COMUNS | Reserva do salão de festas | Fácil | Fácil (2) | categoria AREAS_COMUNS: base 4; termo 'como reservar': -2 |
| ✅ | SEGURANCA | Portão da garagem não fecha | Média | Média (6) | categoria SEGURANCA: base 5; termo 'varios': +1 |
| ✅ | SEGURANCA | Câmera do térreo sem imagem | Média | Média (5) | categoria SEGURANCA: base 5 |
| ✅ | ELETRICA | Tomada do salão sem energia | Média | Média (6) | categoria ELETRICA: base 6 |
| ✅ | ELETRICA | Choque na porta de metal | Difícil + emergência | Difícil (8) + emergência | categoria ELETRICA: base 6; termo 'choque': +2; emergência detectada: choque elétrico |
| ✅ | HIDRAULICA | Torneira pingando | Fácil | Fácil (3) | categoria HIDRAULICA: base 6; termo 'torneira': -2; termo 'pinga': -1 |
| ✅ | HIDRAULICA | Vazamento no teto da garagem | Média | Média (6) | categoria HIDRAULICA: base 6 |
| ✅ | HIDRAULICA | Entupimento na coluna de esgoto | Difícil | Difícil (9) | categoria HIDRAULICA: base 6; termo 'esgoto': +1; termo 'coluna': +1; termo 'voltando': +1 |
| ✅ | ELEVADOR | Elevador parado | Difícil | Difícil (10) | categoria ELEVADOR: base 8; termo 'parado': +2 |
| ✅ | ELEVADOR | Botão do elevador não acende | Média | Média (6) | categoria ELEVADOR: base 8; termo 'botao': -2 |
| ✅ | ELEVADOR | Pessoa presa no elevador | Difícil + emergência | Difícil (10) + emergência | categoria ELEVADOR: base 8; termo 'presa': +2; emergência detectada: pessoa presa |
| ✅ | ESTRUTURA | Rachadura na parede do hall | Difícil | Difícil (10) | categoria ESTRUTURA: base 9; termo 'rachadura': +2 |
| ✅ | ESTRUTURA | Infiltração na laje | Difícil | Difícil (10) | categoria ESTRUTURA: base 9; termo 'infiltracao': +2 |
| ✅ | OUTROS | Sugestão de horta comunitária | Fácil | Fácil (1) | categoria OUTROS: base 4; termo 'gostaria de propor': -2; termo 'sugestao': -2; termo 'propor': -2 |
