# Indicadores medidos do NexoDesk (para o relatório e os slides finais)

Medidos em **15/10/2026**, no commit mais recente deste repositório, na máquina de desenvolvimento (Windows 11,
JDK 21, Python 3.12, MySQL 8.4.9). Cada número tem o comando ou a fonte para qualquer integrante reproduzir.
Números que o relatório já traz e **dependem do grupo** (sprints, cards no Kanban, % de conclusão) estão na seção 3:
eu não tenho como medi-los a partir do código, então não os inventei.

## 1. O que o código comprova

| Indicador | Valor medido | Como conferir |
|---|---|---|
| Requisitos funcionais implementados e testados | **5 de 5** (RF01 a RF05; RF05 por aviso no sistema, e-mail só testado contra servidor falso) | tabela da seção 2 de `documentacao-tecnica.md` |
| Requisitos não funcionais atendidos | **3 de 3** (RNF01 medido na seção 2 abaixo) | idem |
| Testes automáticos Java | **63**, 0 falhas, no H2 **e** no MySQL 8.4.9 real | `cd api-java; .\mvnw.cmd clean test` |
| Testes automáticos Python | **12**, 0 falhas | `cd servico-prioridade; .\.venv\Scripts\python.exe -m pytest -q` |
| Casos de uso implementados | **6** (o relatório da 2ª parcial diz 5; ver nota abaixo) | seção 10.1 da documentação técnica |
| Diagramas (em Mermaid, no repositório) | **6**: 4 UML (casos de uso, classes, 2 de sequência), o modelo entidade-relacionamento e a arquitetura | seções 3, 4 e 10 da documentação técnica |
| Tabelas no banco | **5** (`usuarios`, `chamados`, `historico_status`, `notificacoes`, `avaliacoes`) | `grep -c "^CREATE TABLE" api-java/src/main/resources/db/migration/V*.sql` |
| Endpoints REST | **18** mapeados (+ logout) | seção 6 da documentação técnica |
| Categorias de chamado | **13** | `dominio/Categoria.java` |
| Linhas de código Java (produção / testes) | **2.353 / 1.114** | `git ls-files` + contagem de linhas |
| Linhas Python (produção / testes) | **128 / 79** | idem |
| Telas (HTML, CSS, JS) | **12 arquivos, 1.131 linhas** | `api-java/src/main/resources/static` |
| Commits no repositório | **4** até o commit `e4a37da` | `git rev-list --count HEAD` |
| Casos de validação da regra de dificuldade | **27**, com **17 batendo (63%)** com a expectativa inicial | `docs/casos-de-prioridade.md` |

> **Nota sobre os casos de uso:** o relatório lista 5; o sistema tem 6 (cadastrar-se e entrar, abrir chamado, acompanhar,
> ver fila priorizada, atualizar status, receber aviso). Decidam se o relatório passa a dizer 6 ou agrupa dois deles.
>

## 2. RNF01: tempo de resposta menor que 3 s (medido)

Medição com `curl` contra o sistema recém-clonado e iniciado pelo `iniciar-nexodesk.ps1 -Perfil demo`
(H2 em memória, de 8 a 38 chamados durante a medição, um usuário por vez, 30 repetições por requisição, serviço Python no ar; os 38 chamados
foram classificados pelo Python, nenhum caiu no modo de reserva):

| Requisição | Média | p95 | Máximo |
|---|---|---|---|
| Login (bcrypt, 5 medições) | 0,133 s | 0,258 s | 0,258 s |
| Página `sindico.html` | 0,010 s | 0,030 s | 0,030 s |
| Fila do síndico (todos) | 0,020 s | 0,045 s | 0,056 s |
| Estatísticas | 0,016 s | 0,038 s | 0,040 s |
| Exportar CSV | 0,014 s | 0,034 s | 0,036 s |
| **Abrir chamado (inclui chamada ao Python)** | **0,027 s** | 0,055 s | 0,059 s |
| Meus chamados | 0,014 s | 0,037 s | 0,038 s |

**Pior caso medido: 0,258 s** (o login, por causa do bcrypt, que é lento de propósito). Limite do requisito: 3 s.

**Limites desta medição (digam isso se perguntarem):** é no `localhost`, com banco pequeno e uma pessoa por vez; não
representa internet, muitos usuários simultâneos nem MySQL em outra máquina. Prova que o sistema não tem lentidão própria
do código, não que aguenta uma carga real.

## 3. Números que dependem do grupo (não medidos por mim)

O relatório da 2ª parcial traz estes valores; confirmem com o Kanban antes de repeti-los na entrega final:

| Indicador do relatório | Situação |
|---|---|
| Sprints concluídas / total ("2 / 3") | só o grupo sabe; o Kanban (GitHub Projects) deve mostrar |
| Tarefas concluídas no Kanban ("16") | **conferir:** no GitHub há 20 cards (issues), 14 fechados e 6 abertos em 15/10; 14 e 16 não são o mesmo número |
| Bugs em aberto ("2 de interface") | os cards #15 e #16 existem **sem descrição**; um defeito real de interface foi achado e corrigido em 14/10 (rolagem horizontal no celular, commit `8bc8040`). Descrevam os bugs reais ou fechem os cards |
| % de conclusão estimado | estimativa do grupo |

## 4. Como reproduzir tudo em 5 minutos (para mostrar à banca)

```powershell
git clone <url do repositório>; cd nexodesk-app
cd api-java;           .\mvnw.cmd clean test           # 63 testes, ~1 min
cd ..\servico-prioridade; python -m venv .venv; .\.venv\Scripts\python.exe -m pip install -r requirements.txt
                       .\.venv\Scripts\python.exe -m pytest -q   # 12 testes
cd ..;                 .\iniciar-nexodesk.ps1 -Perfil demo        # sistema inteiro com dados de exemplo
```
