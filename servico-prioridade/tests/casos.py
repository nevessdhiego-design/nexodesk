"""Casos de validação da regra de dificuldade e da detecção de emergência.

Dois conjuntos SEPARADOS, de propósito:

* CASOS_DE_AJUSTE: os 27 casos de docs/casos-de-prioridade.md, usados para ajustar os termos da regra.
* CASOS_DE_VALIDACAO: casos escritos DEPOIS de fixar os termos, que a regra nunca viu. Medem se o ajuste generaliza ou se
  só decorou os 27 (ajustar uma regra aos mesmos casos com que se mede é enganar a si mesmo).

A coluna "esperado" é a proposta de quem escreveu, ainda NÃO validada por um síndico de verdade (veja o documento).
Formato: (categoria, título, descrição, dificuldade esperada, emergência esperada)
"""

CASOS_DE_AJUSTE = [
    ("ILUMINACAO", "Lâmpada queimada no corredor", "Trocar a lâmpada do 2º andar", "FACIL", False),
    ("ILUMINACAO", "Poste do estacionamento apagado", "O poste da entrada não acende há uma semana", "MEDIA", False),
    ("ILUMINACAO", "Luz do hall piscando", "A luz do hall do bloco A pisca o tempo todo", "FACIL", False),
    ("LIMPEZA", "Lixeira transbordando", "A lixeira do térreo não é esvaziada há dias", "FACIL", False),
    ("LIMPEZA", "Piscina com água verde", "A piscina está com a água esverdeada", "MEDIA", False),
    ("PORTARIA", "Interfone sem funcionar", "O interfone do apto 302 não toca", "MEDIA", False),
    ("PORTARIA", "Portaria sem porteiro à noite", "Ontem a portaria ficou vazia depois das 23h", "MEDIA", False),
    ("ESTACIONAMENTO", "Carro na minha vaga", "Um carro estranho ocupa a vaga 12", "FACIL", False),
    ("ESTACIONAMENTO", "Vaga demarcada apagada", "A pintura das vagas do subsolo sumiu", "MEDIA", False),
    ("BARULHO", "Música alta depois das 22h", "Vizinho do 401 faz festa toda sexta", "FACIL", False),
    ("BARULHO", "Obra fora do horário", "Reforma em apartamento aos domingos", "FACIL", False),
    ("CONVIVENCIA", "Cachorro latindo o dia todo", "O cão do 205 late sem parar", "MEDIA", False),
    ("AREAS_COMUNS", "Churrasqueira quebrada", "A grelha da churrasqueira soltou", "MEDIA", False),
    ("AREAS_COMUNS", "Reserva do salão de festas", "Como reservar o salão para o mês que vem", "FACIL", False),
    ("SEGURANCA", "Portão da garagem não fecha", "O portão fica aberto por vários minutos", "MEDIA", False),
    ("SEGURANCA", "Câmera do térreo sem imagem", "A câmera do hall está apagada", "MEDIA", False),
    ("ELETRICA", "Tomada do salão sem energia", "As tomadas do salão não funcionam", "MEDIA", False),
    ("ELETRICA", "Choque na porta de metal", "Levei um choque ao encostar na porta do hall", "DIFICIL", True),
    ("HIDRAULICA", "Torneira pingando", "A torneira da área de lazer pinga", "FACIL", False),
    ("HIDRAULICA", "Vazamento no teto da garagem", "Está pingando água perto das vagas 10 a 14", "MEDIA", False),
    ("HIDRAULICA", "Entupimento na coluna de esgoto", "O esgoto está voltando no térreo", "DIFICIL", False),
    ("ELEVADOR", "Elevador parado", "O elevador social parou no 5º andar, ninguém dentro", "DIFICIL", False),
    ("ELEVADOR", "Botão do elevador não acende", "O botão do 3º andar não acende", "MEDIA", False),
    ("ELEVADOR", "Pessoa presa no elevador", "Tem gente presa no elevador agora", "DIFICIL", True),
    ("ESTRUTURA", "Rachadura na parede do hall", "Apareceu uma rachadura grande no hall", "DIFICIL", False),
    ("ESTRUTURA", "Infiltração na laje", "Infiltração no teto do último andar quando chove", "DIFICIL", False),
    ("OUTROS", "Sugestão de horta comunitária", "Gostaria de propor uma horta no jardim", "FACIL", False),
]

# Escritos depois de fixar os termos. Alguns erros aqui são ESPERADOS e honestos: mostram o limite de uma regra por palavras.
CASOS_DE_VALIDACAO = [
    ("ILUMINACAO", "Luz de emergência da escada não acende", "A luz de emergência da escada não funciona", "MEDIA", False),
    ("LIMPEZA", "Caçamba de entulho cheia", "A caçamba na frente do prédio está transbordando", "FACIL", False),
    ("PORTARIA", "Encomenda sem aviso", "O porteiro não avisou que chegou minha encomenda", "FACIL", False),
    ("ESTACIONAMENTO", "Moto na calçada", "Uma moto fica estacionada na calçada do prédio", "FACIL", False),
    ("BARULHO", "Obra começou às 7h", "A reforma do vizinho começou às 7h da manhã", "FACIL", False),
    ("CONVIVENCIA", "Vizinho joga lixo pela janela", "Estão jogando lixo da janela do 6º andar", "MEDIA", False),
    ("AREAS_COMUNS", "Quadra com rede rasgada", "A rede da quadra poliesportiva está rasgada", "MEDIA", False),
    ("SEGURANCA", "Cerca elétrica desligada", "A cerca elétrica do muro dos fundos está desligada", "MEDIA", False),
    ("ELETRICA", "Disjuntor desarmando", "O disjuntor do salão desarma toda hora", "MEDIA", False),
    ("ELETRICA", "Fio solto na piscina", "Tem um fio solto perto da área da piscina", "MEDIA", True),
    ("HIDRAULICA", "Caixa d'água transbordando", "A caixa d'água da cobertura está transbordando", "MEDIA", False),
    ("HIDRAULICA", "Falta de água", "Falta de água no bloco inteiro desde cedo", "DIFICIL", False),
    ("ELEVADOR", "Barulho estranho no elevador", "O elevador faz um barulho estranho ao subir", "MEDIA", False),
    ("ESTRUTURA", "Reboco caiu da fachada", "Um pedaço de reboco caiu da fachada", "DIFICIL", False),
    ("OUTROS", "Dúvida sobre a taxa", "Tenho uma dúvida sobre a taxa do condomínio", "FACIL", False),
]

# Frases que NÃO podem acionar o alerta de emergência (falso alarme gera fadiga de alerta).
NAO_SAO_EMERGENCIA = [
    ("PORTARIA", "Portão preso", "O portão da garagem está preso e não abre"),
    ("ESTACIONAMENTO", "Carro preso na vaga", "O carro ficou preso na vaga por causa de outro carro"),
    ("OUTROS", "Taxa de gás", "Quero saber o valor da taxa de gás do mês"),
    ("ILUMINACAO", "Lâmpada queimada", "A lâmpada queimada do corredor precisa ser trocada"),
    ("AREAS_COMUNS", "Churrasqueira", "Posso fazer fogão a lenha na churrasqueira?"),
    ("OUTROS", "Fogo de artifício", "Soltam fogos de artifício toda noite"),
    ("BARULHO", "Reclamação de barulho", "A obra do vizinho é um choque de barulho para quem trabalha em casa"),
]

# Frases que PRECISAM acionar o alerta, com o rótulo esperado.
SAO_EMERGENCIA = [
    ("ELEVADOR", "Pessoa presa", "Tem uma criança presa no elevador", "pessoa presa"),
    ("ELEVADOR", "Presa no elevador", "Minha mãe ficou presa no elevador do bloco B", "pessoa presa"),
    ("ELETRICA", "Choque", "Levei um choque no portão", "choque elétrico"),
    ("ELETRICA", "Fio exposto", "Fio desencapado na entrada do prédio", "fio exposto"),
    ("ELETRICA", "Faíscas", "Saindo faíscas do quadro de luz", "faísca"),
    ("HIDRAULICA", "Gás", "Cheiro forte de gás no corredor do 4º andar", "vazamento de gás"),
    ("OUTROS", "Fumaça", "Muita fumaça saindo do apartamento 502", "fogo ou fumaça"),
    ("OUTROS", "Incêndio", "Começou um incêndio na lixeira", "fogo ou fumaça"),
    ("HIDRAULICA", "Alagamento", "Garagem alagando, a água já chegou nos carros", "alagamento"),
    ("ESTRUTURA", "Desabamento", "Risco de desabamento da marquise", "risco de desabamento"),
    ("SEGURANCA", "Assalto", "Houve um assalto na portaria agora há pouco", "invasão ou assalto"),
    ("OUTROS", "Saúde", "Uma moradora desmaiou na escada", "emergência de saúde"),
]
