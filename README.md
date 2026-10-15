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
| RF05 notificar o morador | tabela `notificacoes` (aviso dentro do sistema) e e-mail por SMTP (desligado por padrão; testado só contra servidor falso) |
| RNF01 resposta < 3 s | chamada ao Python com limite de 2 s e reserva |
| RNF02 senha criptografada | bcrypt (`SecurityConfig`) |
| RNF03 interface responsiva | `app.css` (testado em largura de celular) |

## Como rodar (Windows)

Precisa de **JDK 21** e **Python 3.12**. O Maven e as bibliotecas baixam sozinhos.

### Um comando só

```powershell
.\iniciar-nexodesk.ps1                   # H2 embutido, nada para instalar
.\iniciar-nexodesk.ps1 -Perfil demo      # com 2 moradores e 8 chamados de exemplo
.\iniciar-nexodesk.ps1 -Perfil mysql     # MySQL (variáveis NEXODESK_DB_*, veja abaixo)
```

O script acha o Java e o Python, cria o ambiente virtual e gera o `.jar` na primeira vez, sobe o serviço
Python (porta 8001) e a API (porta 8080) e encerra os dois com Ctrl+C. Se sobrar alguma dúvida de ambiente,
use o passo a passo manual a seguir.

**Perfil `demo`:** exige a variável `NEXODESK_DEMO_SENHA` (mínimo 8 caracteres), usada como senha dos moradores
`ana.demo@nexodesk.local` e `bruno.demo@nexodesk.local`. Só cria dados se o banco ainda não tiver chamados.

### Passo a passo manual

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

### Ligar o aviso por e-mail (opcional)

Por padrão **nenhum e-mail sai da máquina**: o sistema só registra no log (com o endereço mascarado). Para enviar de
verdade quando o síndico muda o status, use o perfil `email` e defina as variáveis (nunca em arquivo):

```powershell
$env:NEXODESK_SMTP_HOST = 'smtp.exemplo.com'; $env:NEXODESK_SMTP_PORTA = '587'
$env:NEXODESK_SMTP_USUARIO = '...'; $env:NEXODESK_SMTP_SENHA = '...'      # no Gmail, use "senha de app"
$env:NEXODESK_EMAIL_REMETENTE = 'nexodesk@seudominio.com'
.\iniciar-nexodesk.ps1 -Perfil email          # ou mysql,email
```

O envio por SMTP foi testado contra um servidor falso (testes automáticos), **não** contra um provedor real.

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
O `schema.sql` roda sozinho na inicialização e o Hibernate confere (`validate`) se as entidades batem com as tabelas.
**Testado no MySQL 8.4.9** (14/10/2026): a suíte completa passou e o sistema inteiro subiu pelo `iniciar-nexodesk.ps1 -Perfil mysql,demo`.

Para rodar os testes Java contra o seu MySQL (use um banco **só para teste**, as tabelas são criadas nele):

```powershell
$env:NEXODESK_TEST_DB_URL = 'jdbc:mysql://localhost:3306/nexodesk_test?serverTimezone=America/Sao_Paulo'
$env:NEXODESK_TEST_DB_USUARIO = '...'
$env:NEXODESK_TEST_DB_SENHA = '...'
.\mvnw.cmd clean test
```

Use `clean` ao mudar arquivos de configuração de teste: o Maven não apaga cópias antigas em `target/test-classes`.

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
| `GET /api/auth/sessao` | público | diz se há sessão (200 sempre; usado pela tela de login) |
| `GET /api/categorias` | público | categorias do formulário |
| `POST /api/chamados` (multipart) | morador | abre chamado (foto opcional) |
| `GET /api/chamados/meus`, `GET /api/chamados/{id}`, `GET /api/chamados/{id}/imagem` | dono ou síndico | consulta |
| `GET /api/sindico/chamados?status=ativos\|todos\|ABERTO\|EM_ANDAMENTO\|RESOLVIDO` | síndico | fila priorizada |
| `PATCH /api/auth/me`, `POST /api/auth/senha` | logado | minha conta: dados e troca de senha |
| `POST /api/chamados/{id}/avaliacao` | dono | nota de 1 a 5 (só chamado resolvido, uma vez) |
| `GET /api/sindico/chamados/exportar.csv` | síndico | a fila em CSV (abre no Excel) |
| `PATCH /api/sindico/chamados/{id}/status` | síndico | muda status, avisa o morador (sistema e e-mail) |
| `GET /api/sindico/estatisticas` | síndico | contagens |
| `GET /api/notificacoes`, `POST /api/notificacoes/{id}/lida` | logado | avisos |

## Segurança: o que está feito

- Senhas com bcrypt; mensagem de erro de login igual para e-mail inexistente e senha errada.
- Bloqueio de força bruta: 5 senhas erradas para o mesmo e-mail e IP, ou 30 falhas de um IP, bloqueiam o login
  por 15 minutos (429); o dono da conta, de outro IP, não é afetado. O contador fica em memória.
- Cadastro público só cria morador; síndico nasce no primeiro start, sem senha fixa no código.
- CSRF ativo (cookie `XSRF-TOKEN` + cabeçalho `X-XSRF-TOKEN`), cookie de sessão `HttpOnly` e `SameSite=Lax`,
  CSP restritiva (`default-src 'self'`), texto do usuário sempre via `textContent` (sem XSS).
- Upload: o tipo é decidido pelo conteúdo do arquivo (JPG/PNG/GIF), nome gerado pelo sistema, 5 MB,
  pasta fora da área pública, só o dono e o síndico veem a foto.
- Morador não vê chamado de outro (404). Chamado **anônimo** esconde nome e unidade do síndico.
- Consultas sempre parametrizadas (JPA), sem SQL montado por concatenação.

## Ainda não feito (decidir com o grupo)

- E-mail testado com um provedor real (hoje só contra servidor SMTP falso).
- Encerrar as outras sessões do usuário ao trocar a senha.
- Gestão de vários condomínios, paginação da fila, recuperação de senha, painel desktop (PyQt5 do `aa`).
- HTTPS e cookie `Secure` para publicar fora do localhost.
