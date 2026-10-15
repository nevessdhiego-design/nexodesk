# NexoDesk (código)

Helpdesk para condomínios: o morador abre chamados, o sistema estima a **dificuldade** de cada um e o
síndico atende da fila **do mais fácil ao mais difícil**, avisando o morador a cada mudança de status.

Reescrita do protótipo antigo "Nexo" (PHP + PyQt5, pasta `aa`) para **Java + Python + SQL**, como no
relatório da 1ª parcial.

## Arquitetura

```
 navegador (HTML/CSS/JS puros)
        │  JSON / multipart
        ▼
 api-java  (Java 21, Spring Boot 4)  ── regras, login, chamados, status, avisos
        │                │
        │ SQL            │ HTTP  POST /classificar
        ▼                ▼
 H2 (dev) ou MySQL   servico-prioridade (Python 3.12, FastAPI)  ── RF02
```

- **api-java/**: API REST e também serve as páginas (`src/main/resources/static`).
- **servico-prioridade/**: regras de dificuldade (função pura em `app/regras.py`, testada).
  Se estiver fora do ar, o Java classifica só pela categoria e marca `classificacaoFallback`;
  abrir chamado nunca fica bloqueado.
- Banco: `api-java/src/main/resources/schema.sql` (H2 em modo MySQL e MySQL 8).

## Requisitos x onde estão

| Requisito | Onde |
|---|---|
| RF01 cadastrar chamado (título, descrição, categoria) | `ChamadoController.abrir`, `ChamadoService.abrir`, `morador.html` |
| RF02 classificar por dificuldade | `servico-prioridade/app/regras.py`, `ClassificadorPython` |
| RF03 síndico vê do mais fácil ao mais difícil | `ChamadoRepository.findByStatusInOrderByPontuacaoAscCriadoEmAsc`, `sindico.html` |
| RF04 atualizar status (aberto, em andamento, resolvido) | `SindicoController.alterarStatus` + tabela `historico_status` |
| RF05 notificar o morador | tabela `notificacoes` (aviso dentro do sistema); e-mail **não** implementado |
| RNF01 resposta < 3 s | chamada ao Python com limite de 2 s e reserva |
| RNF02 senha criptografada | bcrypt (`SecurityConfig`) |
| RNF03 interface responsiva | `app.css` (testado em largura de celular) |

## Como rodar (Windows)

Precisa de **JDK 21** e **Python 3.12**. O Maven e as bibliotecas baixam sozinhos.

1. Serviço Python (terminal 1):
   ```powershell
   cd servico-prioridade
   python -m venv .venv
   .\.venv\Scripts\python.exe -m pip install -r requirements.txt
   .\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001
   ```
2. API Java (terminal 2):
   ```powershell
   cd api-java
   .\mvnw.cmd spring-boot:run
   ```
   Abra http://localhost:8080. Na primeira execução o console mostra o e-mail e uma **senha aleatória do
   síndico** (aparece uma única vez; anote). Para escolher a senha, defina antes
   `$env:NEXODESK_SINDICO_SENHA = '...'`. O e-mail padrão é `sindico@nexodesk.local`
   (`NEXODESK_SINDICO_EMAIL`).
3. Moradores se cadastram pela própria tela ("Criar conta").

Os dados do modo desenvolvimento ficam em `api-java/data/` e as fotos em `api-java/uploads/` (ambos fora do Git).

### Usar MySQL em vez do H2

```sql
CREATE DATABASE nexodesk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
```powershell
$env:NEXODESK_DB_URL = 'jdbc:mysql://localhost:3306/nexodesk?serverTimezone=America/Sao_Paulo'
$env:NEXODESK_DB_USUARIO = '...'
$env:NEXODESK_DB_SENHA = '...'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql"
```
O `schema.sql` roda sozinho na inicialização. **Não foi testado contra um MySQL real**, só contra o H2 em modo MySQL.

## Testes

```powershell
cd servico-prioridade; .\.venv\Scripts\python.exe -m pytest -q     # regras e endpoint
cd api-java;           .\mvnw.cmd test                              # API completa, esquema SQL e cliente do Python
```
`RegrasTest` compara as pontuações base do Java com as do `regras.py` e falha se divergirem.

## API (resumo)

| Método e rota | Quem | O que faz |
|---|---|---|
| `POST /api/auth/register` | público | cria conta de **morador** |
| `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` | | sessão por cookie |
| `GET /api/categorias` | público | categorias do formulário |
| `POST /api/chamados` (multipart) | morador | abre chamado (foto opcional) |
| `GET /api/chamados/meus`, `GET /api/chamados/{id}`, `GET /api/chamados/{id}/imagem` | dono ou síndico | consulta |
| `GET /api/sindico/chamados?status=ativos\|todos\|ABERTO\|EM_ANDAMENTO\|RESOLVIDO` | síndico | fila priorizada |
| `PATCH /api/sindico/chamados/{id}/status` | síndico | muda status e avisa o morador |
| `GET /api/sindico/estatisticas` | síndico | contagens |
| `GET /api/notificacoes`, `POST /api/notificacoes/{id}/lida` | logado | avisos |

## Segurança: o que está feito

- Senhas com bcrypt; mensagem de erro de login igual para e-mail inexistente e senha errada.
- Cadastro público só cria morador; síndico nasce no primeiro start, sem senha fixa no código.
- CSRF ativo (cookie `XSRF-TOKEN` + cabeçalho `X-XSRF-TOKEN`), cookie de sessão `HttpOnly` e `SameSite=Lax`,
  CSP restritiva (`default-src 'self'`), texto do usuário sempre via `textContent` (sem XSS).
- Upload: o tipo é decidido pelo conteúdo do arquivo (JPG/PNG/GIF), nome gerado pelo sistema, 5 MB,
  pasta fora da área pública, só o dono e o síndico veem a foto.
- Morador não vê chamado de outro (404). Chamado **anônimo** esconde nome e unidade do síndico.
- Consultas sempre parametrizadas (JPA), sem SQL montado por concatenação.

## Ainda não feito (decidir com o grupo)

- Limite de tentativas de login (hoje não há bloqueio contra força bruta).
- Notificação por e-mail (RF05 hoje é só dentro do sistema).
- Gestão de vários condomínios, paginação da fila, recuperação de senha, painel desktop (PyQt5 do `aa`).
- HTTPS e cookie `Secure` para publicar fora do localhost.
