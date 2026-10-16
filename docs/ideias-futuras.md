# Ideias futuras (e por que ficaram de fora)

Decidido em 15/10/2026 para a apresentação de 20/10. Nada aqui é "descartado para sempre": é o que **não** entra nesta
versão e o motivo, para que a banca (e o próprio grupo) saiba que foi uma escolha e não um esquecimento.
Origem das ideias: os PDFs de ideação em `PROJETOS/nexodesk/docs/ideacao/` e a pesquisa de aplicativos de condomínio feita em 15/10.

| Ideia | De onde veio | Por que ficou de fora agora | O que seria preciso |
|---|---|---|---|
| **Mural de comunicados** (avisos do síndico, por bloco) | ideação; jornada do síndico | Não faz parte dos requisitos do A3; a conversa dentro do chamado já resolve a comunicação sobre um problema | tabela `comunicados`, segmentação por bloco (hoje a unidade é texto livre), notificação |
| **Reserva de áreas comuns** | ideação (persona do morador) | Regra própria (calendário, conflitos, regimento); é outro produto dentro do produto | tabelas de áreas e reservas, regras de uso, aprovação |
| **Vários condomínios** (administradora) | ideação (persona "Ana") | Muda o modelo de dados inteiro (tudo passa a ter `condominio_id`) e as permissões | migração de esquema, perfil de administradora, painel consolidado |
| **Financeiro** (boletos, prestação de contas) | ideação | Exige integração bancária e cuidado legal | fora do escopo de um projeto A3 |
| **Assembleias e votações online** | ideação | Exige validade jurídica e identificação forte | idem |
| **Portaria e visitantes** | ideação (Nok Nox faz) | Outro domínio (e outro projeto do A3 trata de visitantes em hospital) | cadastro de visitantes, QR code |
| **Aplicativo desktop (PyQt5)** | protótipo `aa` | O painel do síndico passou para a web, que também funciona no celular | consumir a API existente |
| **Voz e gestos para abrir chamado** | ideação (tecnologias emergentes) | Cresce o escopo sem resolver a dor principal | reconhecimento de fala no navegador |
| **"IA" para classificar chamados** | ideação | Hoje a regra é simples e **explicável**; não há dados reais suficientes para treinar um modelo e a banca pode perguntar de onde vêm os números | base de chamados reais rotulados pelo síndico |
| **Integração com WhatsApp** | pesquisa de mercado | Exige conta comercial e custos; o e-mail já cobre o aviso | API oficial do WhatsApp Business |
| **Criptografia "de ponta a ponta"** | slide de ideação | Não existe e não é necessária aqui; o sistema usa HTTPS (em produção), bcrypt e isolamento de dados. **Não prometer isso nos slides.** | n/a |
| **Aplicativo móvel nativo / PWA instalável** | ideação ("acesso mobile") | O site já é responsivo (testado em 375 px); PWA é um bom próximo passo barato | `manifest.json` e service worker compatíveis com a CSP |
| **Unificar a tabela de categorias** (Java e Python) | revisão técnica | Hoje há duas cópias protegidas por teste (`RegrasTest`); funciona, só não é elegante | uma única fonte (arquivo JSON lido pelos dois) |

> Regra para novas ideias: precisa responder a uma dor medida (teste de usabilidade, entrevista) ou a um requisito do relatório.
