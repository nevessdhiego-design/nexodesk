# Contas de demonstração (para apresentar)

Criadas pelo perfil `demo` (`.\iniciar-nexodesk.ps1 -Perfil demo`). **Todas são contas falsas e locais** (`@nexodesk.local`).

**Senhas:** por regra do projeto (`AGENTS.md`) o repositório **não guarda senha**.
- Todas as contas de demonstração usam **a mesma senha, a que o grupo define na variável `NEXODESK_DEMO_SENHA`** antes de subir o sistema
  (mínimo 8 caracteres). Combinem uma senha simples entre vocês e anotem **fora do repositório** (no celular, por exemplo).
- O **síndico** usa a senha de `NEXODESK_SINDICO_SENHA` (na primeira vez que o banco é criado) ou a que já estiver no banco.
- Na tela de login do perfil `demo` há botões **"Entrar como..."** que preenchem o e-mail; quem apresenta só digita a senha.

| Conta (e-mail) | Perfil e função | O que mostrar com ela |
|---|---|---|
| `sindico@nexodesk.local` | Síndico | fila, atribuição com "Indicado", usuários, estatísticas |
| `ana.demo@nexodesk.local` | Morador | abrir chamado (inclusive emergência), conversa, avaliar |
| `bruno.demo@nexodesk.local` | Morador | segundo morador (chamados de exemplo) |
| `ze.demo@nexodesk.local` | Funcionário · Zelador | "Meus atendimentos" (lixeira do térreo) |
| `marta.demo@nexodesk.local` | Funcionário · Faxineira | indicada para "Sujeira no chão do pátio" |
| `eletricista.demo@nexodesk.local` | Funcionário · Eletricista | chamados de elétrica e iluminação |
| `encanador.demo@nexodesk.local` | Funcionário · Encanador | "Vazamento no teto da garagem" |
| `porteiro.demo@nexodesk.local` | Funcionário · Porteiro | portaria, estacionamento, barulho |
| `seguranca.demo@nexodesk.local` | Funcionário · Segurança | **recebe aviso de toda emergência** |
| `elevador.demo@nexodesk.local` | Funcionário · Técnico externo | chamados de elevador |
| `manutencao.demo@nexodesk.local` | Funcionário · Manutenção geral | estrutura e áreas comuns |

## Roteiro curto: "algo sujo no chão do pátio"

1. Entre como **Ana** e abra o chamado "Sujeira no chão do pátio" (categoria Limpeza, local Pátio). Ele já existe nos dados de exemplo.
2. Entre como **síndico**: no chamado, o seletor de responsável mostra **"Indicado"** para Marta (faxineira) e Zé (zelador), com a carga de
   cada um. O síndico escolhe e salva.
3. Entre como **Marta** ou **Zé**: o chamado aparece em "Meus atendimentos". Mude o status; Ana vê a mudança sozinha.
4. Para emergência: Ana marca "é uma emergência" ao abrir; aparecem os **telefones 190, 192, 193 e 199**; o síndico e o **Sérgio
   (segurança)** recebem o aviso na hora. **O sistema não liga para ninguém.**
