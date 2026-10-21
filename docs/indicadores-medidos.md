# Indicadores medidos do NexoDesk (para o relatório e os slides finais)

Medidos em **20/10/2026**, no commit mais recente deste repositório, na máquina de desenvolvimento (Windows 11,
JDK 21, Python 3.12, MySQL 8.4.9). Cada número tem o comando ou a fonte para qualquer integrante reproduzir.
Números que o relatório já traz e **dependem do grupo** (sprints, cards no Kanban, % de conclusão) estão na seção 3:
eu não tenho como medi-los a partir do código, então não os inventei. **Nada aqui vem de teste com usuários**: não houve.

## 1. O que o código comprova

| Indicador | Valor medido | Como conferir |
|---|---|---|
| Requisitos funcionais implementados e testados | **5 de 5** (RF01 a RF05; RF05 por aviso no sistema, e-mail só testado contra servidor falso) | tabela da seção 2 de `documentacao-tecnica.md` |
| Requisitos não funcionais atendidos | **3 de 3**, mais o "tempo real" que os slides de 20/08 prometiam (RNF01 medido na seção 2 abaixo) | idem |
| Testes automáticos Java | **147**, 0 falhas no H2; **146**, 0 falhas no MySQL 8.4.9 real, a partir de banco vazio (o 147º, de `h1`, só lê arquivos) | `cd api-java; .\mvnw.cmd clean test` |
| Testes automáticos Python | **20**, 0 falhas | `cd servico-prioridade; .\.venv\Scripts\python.exe -m pytest -q` |
| Tempo da suíte Java (H2) | **~65 s** (era ~3,5 min antes de corrigir o desligamento com SSE aberto) | idem |
| Casos de uso implementados | **10** (o relatório da 2ª parcial diz 5; ver nota abaixo) | seção 10.1 da documentação técnica |
| Perfis de usuário | **3** (morador, zelador, síndico) | `dominio/Perfil.java` |
| Diagramas (em Mermaid, no repositório) | **6**: 4 UML (casos de uso, classes, 2 de sequência), o modelo entidade-relacionamento e a arquitetura | seções 3, 4 e 10 da documentação técnica |
| Tabelas no banco | **6** (`usuarios`, `chamados`, `historico_status`, `notificacoes`, `avaliacoes`, `mensagens`) | `cat api-java/src/main/resources/db/migration/V*.sql \| grep -c -i "^CREATE TABLE"` |
| Migrações de banco (Flyway) | **5** (V1 a V5), testadas em H2 e MySQL, inclusive em banco criado antes do Flyway | `api-java/src/main/resources/db/migration/` |
| Endpoints REST (mapeamentos) | **28** (+ logout do Spring Security) | seção 6 da documentação técnica |
| Categorias de chamado | **13** | `dominio/Categoria.java` |
| Linhas de código Java (produção / testes) | **3.834 / 3.143** | `git ls-files` + contagem de linhas |
| Linhas Python (produção / testes) | **176 / 235** | idem |
| Telas (HTML, CSS, JS) | **20 arquivos, 1.860 linhas**; 7 páginas | `api-java/src/main/resources/static` |
| Commits no repositório | mais de uma dezena; os de 15 a 20/10 ainda **sem push**, o GitHub tem só até `604591e` (conferir com `git status -sb`) | `git rev-list --count HEAD`; `git status -sb` |
| Casos da regra de dificuldade | ajuste: **27 de 27** (100%, foi ajustada neles: não prova nada); **validação: 11 de 15 (73%)** em casos que ela nunca viu. **Cite os 73%** | `docs/casos-de-prioridade.md` |
| Contraste de cores (WCAG AA, 4,5:1) | 0 falhas no síndico (37 chamados), morador e usuários, em tema claro, escuro e alto contraste (script de medição, não leitor de tela) | seção 9 da documentação técnica |

> **Nota sobre os casos de uso:** o relatório lista 5. A fase 1 entregou 6 (cadastrar-se e entrar, abrir chamado, acompanhar,
> ver fila priorizada, atualizar status, receber aviso) e a fase 2 acrescentou 4 (conversar no chamado, atribuir responsável e previsão,
> gerenciar usuários, ver meus atendimentos). Decidam o que o relatório final passa a dizer.

## 2. RNF01: tempo de resposta menor que 3 s (medido de novo em 20/10)

Medição com um cliente HTTP em Python (`urllib`) contra o sistema iniciado a partir do `.jar` com o perfil `demo`
(H2 em memória, de 9 a 39 chamados durante a medição, um usuário por vez, 30 repetições por requisição, serviço Python no ar;
os chamados abertos na medição foram classificados pelo Python):

| Requisição | Média | p95 | Máximo |
|---|---|---|---|
| Login (bcrypt, 5 medições) | 0,218 s | 0,240 s | 0,380 s |
| Página `sindico.html` | 0,019 s | 0,032 s | 0,039 s |
| Fila do síndico (todos) | 0,044 s | 0,061 s | 0,186 s |
| Estatísticas | 0,040 s | 0,057 s | 0,092 s |
| Exportar CSV | 0,030 s | 0,043 s | 0,061 s |
| Equipe (lista de responsáveis) | 0,026 s | 0,034 s | 0,067 s |
| Usuários (gestão) | 0,016 s | 0,029 s | 0,032 s |
| **Abrir chamado (inclui chamada ao Python)** | **0,060 s** | 0,072 s | 0,180 s |
| Meus chamados | 0,021 s | 0,034 s | 0,038 s |
| Avisos | 0,022 s | 0,035 s | 0,037 s |

**Pior caso medido: 0,380 s** (o login, por causa do bcrypt, que é lento de propósito). Limite do requisito: 3 s.
Os números subiram em relação a 15/10 (pior caso 0,258 s) porque a fila agora ordena em memória com a regra de emergência e
envelhecimento, e porque esta medição foi feita logo após subir o sistema (sem aquecimento). Continuam ordens de grandeza abaixo do limite.

**Tempo real, medido com dois clientes:** o síndico (um processo separado) mudou o status de um chamado da Ana e a tela dela,
já aberta no navegador, passou de "Aberto" a "Em andamento" e o contador de avisos de 2 para 3 **sem recarregar a página**.
Não foi medida a latência exata do aviso: o teste de HTTP real (`TempoRealHttpTest`) só prova que ele chega.

**Limites desta medição (digam isso se perguntarem):** é no `localhost`, com banco pequeno e uma pessoa por vez; não
representa internet, muitos usuários simultâneos nem MySQL em outra máquina. Prova que o sistema não tem lentidão própria
do código, não que aguenta uma carga real.

## 3. Números que dependem do grupo (não medidos por mim)

O relatório da 2ª parcial traz estes valores; confirmem com o Kanban antes de repeti-los na entrega final:

| Indicador do relatório | Situação |
|---|---|
| Sprints concluídas / total ("2 / 3") | só o grupo sabe; o Kanban (GitHub Projects) deve mostrar |
| Tarefas concluídas no Kanban ("16") | **conferir:** no GitHub havia 20 cards (issues), 14 fechados e 6 abertos em 15/10 (não reconferido em 20/10); 14 e 16 não são o mesmo número |
| Bugs em aberto ("2 de interface") | os cards #15 e #16 existem **sem descrição**; um defeito real de interface foi achado e corrigido em 14/10 (rolagem horizontal no celular, commit `8bc8040`). Descrevam os bugs reais ou fechem os cards |
| % de conclusão estimado | estimativa do grupo |

## 4. Como reproduzir tudo em 5 minutos (para mostrar à banca)

```powershell
git clone <url do repositório>; cd nexodesk-app
cd api-java;           .\mvnw.cmd clean test           # 147 testes, ~1 min
cd ..\servico-prioridade; python -m venv .venv; .\.venv\Scripts\python.exe -m pip install -r requirements.txt
                       .\.venv\Scripts\python.exe -m pytest -q   # 20 testes
cd ..;                 .\iniciar-nexodesk.ps1 -Perfil demo        # sistema inteiro com dados de exemplo
```
