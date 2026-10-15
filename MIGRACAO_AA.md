# Do `aa` (PHP + PyQt5) para o NexoDesk (Java + Python)

Cada peça do protótipo antigo e o que aconteceu com ela. O `aa` original continua intacto em
`Downloads/aa - Copia/` e no zip de `PROJETOS/nexodesk/codigo/`.

## O que virou o quê

| No `aa` | No NexoDesk | Mudança |
|---|---|---|
| `denuncia.php` (formulário + INSERT) | `POST /api/chamados`, `morador.html`, `ChamadoService.abrir` | "denúncia" virou **chamado** com título, descrição e categoria. Mantidos: opção anônima, foto, local |
| `buscar_denuncias.php` (lista do usuário) | `GET /api/chamados/meus` | Filtro por **dono** (`morador_id`), não mais por e-mail |
| `main.py` > `DenunciasDialog` (tela do síndico em PyQt5) | `GET /api/sindico/chamados`, `sindico.html` | Agora na web, **ordenada do mais fácil ao mais difícil** (RF03) |
| `main.py` > `StatusChangeDialog` | `PATCH /api/sindico/chamados/{id}/status` | Estados: pendente/em andamento/resolvida/rejeitada → **aberto/em andamento/resolvido** (RF04). Ganhou histórico e aviso ao morador |
| aviso em JavaScript quando resolvida | tabela `notificacoes` + `/api/notificacoes` | Aviso persistente por morador (RF05) |
| `login.php`, `cadastro.php`, `conta.php` | `AuthController`, `index.html` | Senha em **bcrypt** nos dois lados (antes: bcrypt no PHP, SHA-256 sem sal no Python, e a tabela `adms` em texto puro) |
| `criar_banco_completo.sql`, `tabelas/*.sql` | `api-java/src/main/resources/schema.sql` | Esquema novo e enxuto: `usuarios`, `chamados`, `historico_status`, `notificacoes` |
| lista fixa de ~50 cidades + categorias municipais | `Categoria` (enum, 13 categorias de condomínio) | Cidades e categorias municipais (Saúde Pública, Turismo...) eram do projeto antigo |
| (não existia) | `servico-prioridade` (Python) | **RF02**: classificação de dificuldade |
| `filtrarConteudo()` (lista de palavrões e anti-spam em PHP) | **não portado** | Bloqueava texto legítimo (qualquer número de 4+ dígitos, palavras em maiúsculas, "teste teste") e dá falsa sensação de segurança. Se o grupo quiser moderação, fazer como revisão do síndico |
| `avaliacao.php`, `avaliar_*.php`, `avaliacoes_admin.py` | **não portado** | Fora dos requisitos do relatório. Candidato a uma próxima sprint (nota de 1 a 5 ao resolver o chamado) |
| `noticias.php`, `eventos`, `cookies.php`, `termos-uso.php`, `privacidade.php`, `objetivos/principios.*` | **não portado** | Páginas institucionais do projeto original. Se o professor pedir, viram HTML estático |
| `instalar_*`, `inserir_dados_banco.php`, `debug_imagens.php`, `*_backup.php`, `*_old.py` | **descartados** | Scripts de instalação e arquivos duplicados |
| `PyQt5` (`.ui`, `.qss`) | **não portado** | O painel do síndico passou para a web. Um app desktop poderia voltar depois consumindo a API |

## Defeitos do `aa` que não foram trazidos

- Credenciais de teste dentro dos arquivos `.sql` (hash de administrador e senha em texto puro na tabela `adms`).
- Login do desktop e do site incompatíveis entre si (algoritmos de senha diferentes).
- `SHOW COLUMNS` a cada requisição para "adivinhar" o esquema do banco.
- Upload validado só pela extensão, em pasta pública criada com permissão 0777.
- Parâmetro `redirect` do login usado direto no `header("Location: ...")`.
- Painel do síndico ligado direto ao MySQL (qualquer cliente precisava da senha do banco).
