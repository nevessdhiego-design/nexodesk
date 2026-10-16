# Do protótipo `aa` (PHP + PyQt5) para o NexoDesk (Java + Python)

Registro histórico: o que cada peça do protótipo antigo virou. O `aa` original continua intacto em
`Downloads/aa - Copia/` e no zip de `PROJETOS/nexodesk/codigo/`. Para **usar** o sistema, leia o [README](../README.md).

## O que virou o quê

| No `aa` | No NexoDesk | Mudança |
|---|---|---|
| `denuncia.php` | `POST /api/chamados`, `morador.html` | "denúncia" virou **chamado** (título, descrição, categoria); mantidos anonimato, foto e local |
| `buscar_denuncias.php` | `GET /api/chamados/meus` | Filtro por **dono** (`morador_id`), não por e-mail |
| `main.py` (tela do síndico em PyQt5) | `sindico.html`, `GET /api/sindico/chamados` | Agora na web, **do mais fácil ao mais difícil** (RF03) |
| `StatusChangeDialog` | `PATCH /api/sindico/chamados/{id}/status` | Estados: aberto, em andamento, resolvido; ganhou histórico e aviso |
| `login.php`, `cadastro.php` | `AuthController`, `index.html` | **bcrypt** nos dois lados (antes: bcrypt no PHP, SHA-256 sem sal no Python, senha em texto puro na tabela `adms`) |
| `conta.php` | `conta.html`, `PATCH /api/auth/me`, `POST /api/auth/senha` | Mantido; a troca de senha encerra as outras sessões |
| `avaliacao*.php`, `avaliacoes_admin.py` | `POST /api/chamados/{id}/avaliacao`, média no painel | Mantido (nota de 1 a 5 só em chamado resolvido, uma vez) |
| exportação CSV do painel PyQt5 | `GET /api/sindico/chamados/exportar.csv` | Mantido, com proteção contra injeção de fórmula |
| `css/acessibilidade.css`, `js/acessibilidade.js` | `acessibilidade.css/js` | Reescritos para a CSP; o filtro de "daltonismo" saiu (era só simulação) |
| `criar_banco_completo.sql`, `tabelas/*.sql` | `db/migration/V*.sql` (Flyway) | Esquema enxuto e versionado |
| lista de ~50 cidades e categorias municipais | `Categoria` (13 categorias de condomínio) | Eram do projeto municipal antigo |
| (não existia) | `servico-prioridade` (Python) | **RF02**: classificação de dificuldade |

## O que foi descartado de propósito

| Item do `aa` | Motivo |
|---|---|
| `filtrarConteudo()` (palavrões e anti-spam em PHP) | Bloqueava texto legítimo (qualquer número de 4+ dígitos, palavras em maiúsculas) e dava falsa sensação de segurança |
| `noticias.php`, `eventos`, `cookies.php`, `termos-uso.php`, `objetivos`/`principios` | Páginas do projeto municipal; fora do escopo do A3 (ideias futuras em `ideias-futuras.md`) |
| `instalar_*`, `inserir_dados_banco.php`, `debug_imagens.php`, `*_backup.php`, `*_old.py` | Scripts de instalação e arquivos duplicados |
| Painel PyQt5 (`.ui`, `.qss`) | O painel do síndico passou para a web |

## Defeitos do `aa` que não foram trazidos

- Credenciais de teste dentro de arquivos `.sql` (hash de administrador e senha em texto puro na tabela `adms`).
- Login do desktop e do site incompatíveis (algoritmos de senha diferentes).
- `SHOW COLUMNS` a cada requisição para "adivinhar" o esquema do banco.
- Upload validado só pela extensão, em pasta pública criada com permissão 0777.
- Parâmetro `redirect` do login usado direto em `header("Location: ...")`.
- Painel do síndico ligado direto ao MySQL (qualquer cliente precisava da senha do banco).
