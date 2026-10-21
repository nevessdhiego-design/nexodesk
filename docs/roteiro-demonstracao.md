# Roteiro da demonstração do NexoDesk (5 minutos)

Formato do projeto A3: apresentação por squad, até 5 minutos. Este roteiro cobre o sistema funcionando;
os slides (problema, concorrentes, arquitetura, resultados, equipe) ficam em volta dele. Versão de 20/10/2026 (fase 2).

## Antes de começar (feito 10 minutos antes, não na frente da banca)

1. Rodar o checklist de ensaio (seção abaixo) e deixar o sistema aberto em **três janelas do navegador**, lado a lado:
   **síndico**, **morador** (Ana) e **zelador** (Zé). Cada uma em janela anônima ou perfil diferente, senão as sessões se misturam.
2. Subir com dados de exemplo: `.\iniciar-nexodesk.ps1 -Perfil demo` (as senhas vêm das variáveis de ambiente,
   nunca digitadas ou projetadas). Os logins de exemplo estão em `credenciais-demo.local.txt`, arquivo **fora do Git**.
3. Plano B pronto: **vídeo gravado** da demonstração e capturas de tela, caso a internet, o notebook ou a porta 8080 falhem.
4. Deixar o chamado "Lixeira do térreo transbordando" (do zelador de exemplo) visível na janela do zelador.

## Fala e ações (tempo aproximado)

| Tempo | Quem | O que mostrar | O que dizer (ideia) |
|---|---|---|---|
| 0:00 | PO | Slide do problema | Hoje o morador avisa o síndico por WhatsApp e recado; chamado se perde e o síndico não sabe por onde começar. **Não citar pesquisa de 300 pessoas: ela era de outro tema (segurança de bairro).** |
| 0:30 | Arquiteto | Slide da arquitetura (diagrama da documentação técnica) | Java concentra login, regras e dados; um serviço Python decide a dificuldade e detecta emergência; banco SQL (MySQL). Se o Python cair, o sistema continua. |
| 1:00 | Morador | Janela da Ana: abrir chamado **"Pessoa presa no elevador do bloco B"** (sem marcar a caixa de emergência), com foto | O morador descreve o problema em segundos; a foto é opcional (e o sistema tira a localização GPS dela). |
| 1:30 | Síndico | Janela do síndico, **sem recarregar a página**: o chamado aparece no **topo da fila**, com selo vermelho EMERGÊNCIA, e o sino recebe o aviso | Tempo real: o síndico é avisado na hora. O Python reconheceu a emergência pelo texto. |
| 2:00 | Síndico | Abrir **"Por que essa dificuldade?"** | A decisão é explicável: categoria com pontuação base, mais termos do texto. Emergência fura a fila; os demais vão do mais fácil ao mais difícil, e o difícil que espera sobe (1 ponto a cada 2 dias, até 3) para ninguém ser esquecido. |
| 2:30 | Síndico e Morador | Abrir **Conversa** no chamado: o síndico pergunta "O elevador está parado entre andares?" e a Ana responde | Comunicação de mão dupla dentro do chamado, também em tempo real. No chamado anônimo, o síndico vê "Anônimo". |
| 3:00 | Síndico | **Atribuir responsável**: o Zé, previsão para amanhã; ir à janela do Zé | O síndico delega; o morador e o zelador são avisados. |
| 3:20 | Zelador | Janela do Zé: **"Meus atendimentos"** mostra só o chamado dele; mudar para **Em andamento** com "Técnico a caminho" | O zelador não vê chamado de outras pessoas. RF04: todo status fica no histórico. |
| 3:45 | Morador | Janela da Ana: o status mudou **sozinho**; aparece "Responsável: Zé" e a previsão; depois, chamado resolvido: **avaliar** (nota de 1 a 5) | RF05: avisada sem precisar perguntar; fecha o ciclo e o síndico vê a média no painel. |
| 4:15 | Síndico | Tela **Usuários** (promover/desativar) e a página **Privacidade** | O síndico gerencia quem entra; o morador pode excluir a própria conta (os chamados ficam sem identificação). |
| 4:30 | PO | Slide de encerramento: números medidos e **o que não fizemos** | 149 testes Java e 20 Python passando, MySQL real testado; regra de dificuldade com 73% de acerto em casos que ela nunca viu. **Não fizemos:** teste de usabilidade com pessoas, e-mail com provedor real, teste de carga. |

## Perguntas que a banca pode fazer (e a resposta curta e verdadeira)

- **"Como a dificuldade é calculada?"** Pontuação base por categoria (1 a 10) mais termos no texto que somam ou
  subtraem pontos. É uma regra simples e explicável, não inteligência artificial. Foi ajustada com 27 casos e conferida com 15 casos que
  ela nunca tinha visto: **acertou 11 de 15 (73%)**. Os casos, inclusive os que erram, estão em `docs/casos-de-prioridade.md`.
- **"Por que emergência passa na frente do mais fácil?"** Porque "pessoa presa no elevador" por último é inaceitável. E o chamado
  difícil que espera muito sobe na fila (bônus de idade), senão poderia nunca ser atendido.
- **"O que acontece se o serviço Python cair?"** O Java classifica só pela categoria e marca o chamado como "aproximado". Abrir
  chamado nunca fica bloqueado. Nesse modo **não** há detecção automática de emergência (o morador ainda pode marcar a caixa).
- **"Como funciona o tempo real? Escala?"** Server-Sent Events: o servidor avisa "algo mudou" e a tela recarrega pela API normal. Se a
  conexão cair, a tela consulta a cada 15 s. Funciona com **uma instância** da API; para várias seria preciso um barramento (Redis).
- **"E a segurança?"** Senha com bcrypt, bloqueio de tentativas de login, proteção contra CSRF, política de conteúdo restritiva,
  foto validada pelo conteúdo e sem GPS, isolamento entre moradores, zelador só no que é dele, sessão derrubada ao trocar senha ou perfil.
- **"E a LGPD?"** Coletamos o mínimo (nome, e-mail, unidade, telefone opcional), há política de privacidade e "excluir minha conta". É um
  mínimo de projeto acadêmico, **não revisado por advogado**.
- **"O e-mail realmente envia?"** O envio por SMTP está implementado e foi testado contra um servidor falso; vem **desligado**
  por padrão. Não foi testado com um provedor real.
- **"Vocês testaram com usuários?"** **Não.** O roteiro de teste e o cálculo do SUS estão prontos em `docs/teste-usabilidade/`, mas
  nenhuma pessoa foi testada. É um próximo passo, e por isso não afirmamos "alta aceitação".
- **"Quem fez o quê?"** Responder com os papéis do squad e, sobre o código, que cada integrante precisa saber explicar o fluxo
  (veja a seção "Atenção: uso de ferramentas de IA" abaixo).

## Atenção: uso de ferramentas de IA

Parte do código foi escrita com apoio de uma IA. Antes da banca, **todos os integrantes devem conseguir explicar o fluxo**
(abrir chamado → classificar → fila → atribuir → status → aviso) lendo o `README.md` e a documentação técnica, e o grupo deve verificar a
política da faculdade sobre uso de IA e declarar o uso como ela exigir.

## Checklist de ensaio (máquina limpa)

Marque cada item ao ensaiar. Se algo falhar, anote o erro exato.

- [ ] `java -version` mostra 21 ou superior; `python --version` mostra 3.12 ou superior.
- [ ] Clonar o repositório numa pasta nova (`git clone ...`) e entrar em `nexodesk-app`.
- [ ] `.\iniciar-nexodesk.ps1 -Perfil demo` (com `NEXODESK_DEMO_SENHA` e `NEXODESK_SINDICO_SENHA` definidas) sobe sem erro.
- [ ] http://localhost:8080 abre; o login do síndico funciona; a fila mostra os 8 chamados de exemplo (o do elevador, em emergência, no topo).
- [ ] Abrir um chamado novo como morador e ver a dificuldade estimada **sem** o aviso "classificação aproximada" (Python no ar).
- [ ] "Pessoa presa no elevador" aparece no topo da fila do síndico **sem recarregar** a página.
- [ ] A conversa funciona nos dois sentidos, em duas janelas.
- [ ] O síndico atribui ao zelador; o zelador (`ze.demo@nexodesk.local`) vê só o chamado dele e muda o status.
- [ ] Mudar o status e ver o aviso no sino do morador.
- [ ] Botão **Aa** abre; modo escuro e texto maior funcionam; Esc fecha o painel.
- [ ] Exportar CSV baixa um arquivo que abre no Excel com acentos corretos.
- [ ] `Ctrl+C` encerra a API e o serviço Python (portas 8080 e 8001 livres depois).
- [ ] Portas 8080 e 8001 livres **antes** de começar (nada do ensaio anterior rodando).
- [ ] Vídeo de reserva gravado e testado no notebook da apresentação.
