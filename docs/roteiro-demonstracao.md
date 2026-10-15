# Roteiro da demonstração do NexoDesk (5 minutos)

Formato do projeto A3: apresentação por squad, até 5 minutos. Este roteiro cobre o sistema funcionando;
os slides (problema, público, arquitetura, equipe) ficam em volta dele.

## Antes de começar (feito 10 minutos antes, não na frente da banca)

1. Rodar o checklist de ensaio (seção abaixo) e deixar o sistema aberto em **duas janelas do navegador**:
   uma logada como **síndico** e outra como **morador** (uma delas em janela anônima, para não misturar a sessão).
2. Subir com dados de exemplo: `.\iniciar-nexodesk.ps1 -Perfil demo` (as senhas vêm das variáveis de ambiente,
   nunca digitadas ou projetadas).
3. Plano B pronto: **vídeo gravado** da demonstração e capturas de tela, caso a internet, o notebook ou a porta 8080 falhem.

## Fala e ações (tempo aproximado)

| Tempo | Quem | O que mostrar | O que dizer (ideia) |
|---|---|---|---|
| 0:00 | PO | Slide do problema | Hoje o morador avisa o síndico por WhatsApp e recado; chamados se perdem e o síndico não sabe por onde começar. |
| 0:30 | Arquiteto | Slide da arquitetura (diagrama da documentação técnica) | Java concentra login, regras e dados; um serviço Python decide a dificuldade; banco SQL (MySQL). Se o Python cair, o sistema continua. |
| 1:00 | Morador | Janela do morador: **abrir chamado** "Elevador parado no bloco B", categoria Elevador, com foto, texto "tem gente presa" | O morador descreve o problema em segundos; a foto é opcional; ele pode escolher ficar anônimo. |
| 1:45 | Morador | A resposta: "Dificuldade estimada: Difícil" | O sistema classifica sozinho (RF02). |
| 2:00 | Síndico | Janela do síndico: **fila priorizada** | A fila vai do mais fácil ao mais difícil (RF03): o síndico resolve rápido o que é simples e planeja o que é grave. |
| 2:30 | Síndico | Abrir **"Por que essa dificuldade?"** no chamado do elevador | A decisão é explicável: categoria com pontuação base, mais termos do texto ("parado", "presa"). |
| 3:00 | Síndico | Mostrar o chamado **anônimo**: nome e unidade ocultos | O morador mantém o controle da identidade. |
| 3:15 | Síndico | Mudar o status de um chamado para **Em andamento**, com observação "Técnico a caminho" | RF04: todo status fica no histórico. |
| 3:45 | Morador | Janela do morador: **aviso** no sino ("Avisos") | RF05: o morador é avisado sem precisar perguntar. |
| 4:15 | Morador | Chamado resolvido: **avaliar o atendimento** (nota 1 a 5) | Fecha o ciclo; o síndico vê a média no painel. |
| 4:30 | PO | Painel do síndico: botão **Exportar CSV** e botão **Aa** (acessibilidade: texto maior, alto contraste, modo escuro) | Relatório para a administradora e acessibilidade para todos. |
| 4:45 | PO | Slide de encerramento: testes (números medidos), próximos passos | 63 testes automáticos Java e 12 Python passando; MySQL real testado; limitações e próximos passos na documentação. |

## Perguntas que a banca pode fazer (e a resposta curta e verdadeira)

- **"Como a dificuldade é calculada?"** Pontuação base por categoria (1 a 10) mais termos no texto que somam ou
  subtraem pontos. É uma regra simples e explicável, não inteligência artificial. Os 27 casos de validação estão em
  `docs/casos-de-prioridade.md`, com os pontos em que a regra ainda erra.
- **"O que acontece se o serviço Python cair?"** O Java classifica só pela categoria e marca o chamado como "aproximado".
  Abrir chamado nunca fica bloqueado.
- **"E a segurança?"** Senha com bcrypt, bloqueio de tentativas de login, proteção contra CSRF, política de conteúdo restritiva,
  foto validada pelo conteúdo do arquivo e isolamento entre moradores.
- **"O e-mail realmente envia?"** O envio por SMTP está implementado e foi testado contra um servidor falso; vem **desligado**
  por padrão. Para ligar é preciso configurar o servidor (perfil `email`). Não foi testado com um provedor real.
- **"Quem fez o quê?"** Responder com os papéis do squad e, sobre o código, que cada integrante precisa saber explicar o fluxo
  (veja a seção "Atenção: uso de ferramentas de IA" abaixo).

## Atenção: uso de ferramentas de IA

Parte do código foi escrita com apoio de uma IA. Antes da banca, **todos os integrantes devem conseguir explicar o fluxo**
(abrir chamado → classificar → fila → status → aviso) lendo o `README.md` e a documentação técnica, e o grupo deve verificar a
política da faculdade sobre uso de IA e declarar o uso como ela exigir.

## Checklist de ensaio (máquina limpa)

Marque cada item ao ensaiar. Se algo falhar, anote o erro exato.

- [ ] `java -version` mostra 21 ou superior; `python --version` mostra 3.12 ou superior.
- [ ] Clonar o repositório numa pasta nova (`git clone ...`) e entrar em `nexodesk-app`.
- [ ] `.\iniciar-nexodesk.ps1 -Perfil demo` (com `NEXODESK_DEMO_SENHA` e `NEXODESK_SINDICO_SENHA` definidas) sobe sem erro.
- [ ] http://localhost:8080 abre; o login do síndico funciona; a fila mostra 8 chamados de exemplo.
- [ ] Abrir um chamado novo como morador e ver a dificuldade estimada **sem** o aviso "classificação aproximada" (Python no ar).
- [ ] Mudar o status e ver o aviso no sino do morador.
- [ ] Botão **Aa** abre; modo escuro e texto maior funcionam; Esc fecha o painel.
- [ ] Exportar CSV baixa um arquivo que abre no Excel com acentos corretos.
- [ ] `Ctrl+C` encerra a API e o serviço Python (portas 8080 e 8001 livres depois).
- [ ] Portas 8080 e 8001 livres **antes** de começar (nada do ensaio anterior rodando).
- [ ] Vídeo de reserva gravado e testado no notebook da apresentação.
