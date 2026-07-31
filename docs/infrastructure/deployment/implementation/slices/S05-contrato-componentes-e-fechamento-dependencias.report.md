# S05 — Relatorio do contrato de componentes e dependencias

> **Estado informado pelo executor:** `IN_PROGRESS`
> **Revisao do orquestrador:** pendente
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Data:** `2026-07-28`

## 1. Metadados

| Campo | Resultado |
|---|---|
| Contrato | `S05-contrato-componentes-e-fechamento-dependencias.task.md` |
| Dependencias | S01 a S04 `ACCEPTED` |
| Python | Python 3 |
| PyYAML | `6.0.1`, ja instalado |
| jsonschema | `4.19.1`, ja instalado |
| Estado | `IN_PROGRESS` |

Nenhuma dependencia foi instalada.

## 2. Arquivos criados e alterados

Criados:

- `ops/releases/components.yml`;
- `ops/releases/components.schema.json`;
- `tools/releases/requirements.txt`;
- `tools/releases/catalog.py`;
- `tools/releases/tests/test_catalog.py`;
- `docs/infrastructure/deployment/release-control/README.md`;
- este relatorio.

Alterado:

- `docs/infrastructure/README.md`, somente para atualizar o estado do tracker
  vinculado e adicionar o link do contrato.

`.gitignore` nao precisou ser alterado: nenhum cache Python foi criado no
workspace.

## 3. Decisoes do catalogo

- o BOM comercial possui exatamente seis componentes;
- a ordem canonica e usada em toda saida;
- dependencias apontam de consumidor para provedor;
- build direto e revalidacao transitiva sao conjuntos distintos;
- componente nao reconstruido aparece em `inheritedComponents` e herdara
  futuramente um digest exato do ultimo conjunto valido;
- repositorios de imagem nao possuem tag nem digest;
- `release_control` e componente operacional explicitamente excluido;
- servicos externos e PostgreSQL sao dependencias de runtime;
- primeiro release constroi e valida os seis;
- path desconhecido aplica `fail_closed_all`;
- gates pendentes bloqueiam readiness sem invalidar a estrutura.

O catalogo nao cria candidato, manifesto, imagem ou release.

## 4. Matriz dos componentes

| Componente | Imagem base do repositorio | Path | Dependencias comerciais | Estado |
|---|---|---|---|---|
| `backend` | `ghcr.io/greggorio/abaronesa-emporio-backend` | `backend/**` | `whatsapp_service` | `blocked` |
| `website_back` | `ghcr.io/greggorio/abaronesa-emporio-website-backend` | `website_back/**` | `backend` | `blocked` |
| `frontend` | `ghcr.io/greggorio/abaronesa-emporio-frontend` | `frontend/**` | `backend` | `blocked` |
| `website_front` | `ghcr.io/greggorio/abaronesa-emporio-website-frontend` | `website_front/**` | `website_back` | `blocked` |
| `whatsapp_service` | `ghcr.io/greggorio/abaronesa-emporio-whatsapp-service` | `whatsapp_service/**` | nenhuma | `blocked` |
| `gateway` | `ghcr.io/greggorio/abaronesa-emporio-gateway` | `ops/gateway/**` | os cinco componentes | `blocked` |

Cada contrato tambem registra contexto, Dockerfile, comandos atuais,
porta interna, health check, dependencias de runtime, migrations,
persistencia e gates.

`release_control` nao aparece na ordem nem no mapa de componentes comerciais.

## 5. Grafo e fechamentos

Grafo direto, consumidor para provedor:

```text
frontend         -> backend
website_back     -> backend
website_front    -> website_back
backend          -> whatsapp_service
gateway          -> backend
gateway          -> website_back
gateway          -> frontend
gateway          -> website_front
gateway          -> whatsapp_service
```

O validador comprova que o grafo e aciclico e corresponde exatamente ao
contrato aprovado.

| Mudanca direta | Build | Fechamento de validacao |
|---|---|---|
| `backend` | `backend` | `backend`, `website_back`, `frontend`, `website_front`, `gateway` |
| `website_back` | `website_back` | `website_back`, `website_front`, `gateway` |
| `frontend` | `frontend` | `frontend`, `gateway` |
| `website_front` | `website_front` | `website_front`, `gateway` |
| `whatsapp_service` | `whatsapp_service` | os seis |
| `gateway` | `gateway` | `gateway` |

As listas sao sempre emitidas na ordem canonica, independentemente da ordem
dos argumentos.

## 6. Politica de paths

| Classe | Paths | Build | Validacao |
|---|---|---|---|
| componente | source paths declarados | componentes diretos | fechamento transitivo |
| global | `.github/workflows/**`, `ops/releases/**`, `ops/compose/**`, `ops/deploy/**`, `deploy/**` | somente diretos, se houver | os seis |
| documental | `docs/**`, `README.md` | nenhum | nenhum |
| desconhecido | qualquer outro | os seis | os seis |
| primeiro release | opcao explicita | os seis | os seis |

Paths absolutos, vazios ou com `..` sao rejeitados. Separadores sao
normalizados sem permitir escape da raiz. O resolvedor nao le Git nem consulta
servico externo.

## 7. Gates iniciais

O modo readiness lista somente IDs e codigos:

### `backend`

- `BACKEND_DOCKERFILE_HARDENING`;
- `BACKEND_JVM_OPTIONS`;
- `BACKEND_FISCAL_SCHEMA_PATH`.

### `website_back`

- `WEBSITE_BACK_HEALTH_CHECK`;
- `WEBSITE_BACK_UPLOAD_PERSISTENCE`;
- `WEBSITE_BACK_DOCKERFILE_HARDENING`.

### `frontend`

- `FRONTEND_DOCKERFILE_HARDENING`;
- `FRONTEND_NODE24_COMPATIBILITY`;
- `FRONTEND_HEALTH_CHECK_CONFIRMATION`.

### `website_front`

- `WEBSITE_FRONT_ENV_NAME`;
- `WEBSITE_FRONT_INTERNAL_TARGET`;
- `WEBSITE_FRONT_DOCKERFILE_HARDENING`;
- `WEBSITE_FRONT_NODE24_COMPATIBILITY`;
- `WEBSITE_FRONT_TEST_COMMAND`;
- `WEBSITE_FRONT_HEALTH_CHECK_CONFIRMATION`.

### `whatsapp_service`

- `WHATSAPP_NODE18_UNSUPPORTED`;
- `WHATSAPP_LIVENESS_CONTRACT`;
- `WHATSAPP_DOCKERFILE_HARDENING`;
- `WHATSAPP_TEST_COMMAND`.

### `gateway`

- `GATEWAY_CANONICAL_ARTIFACTS`;
- `GATEWAY_HEALTH_CHECK`;
- `GATEWAY_LOOPBACK_PORT`;
- `GATEWAY_TEST_COMMAND`.

Total atual: 23 gates pendentes. Os 18 gates originais foram preservados e os
cinco contratos operacionais antes nao representados receberam gates
especificos.

## 8. Schema e invariantes

O JSON Schema local usa Draft 2020-12, nao possui `$ref` remoto e:

- exige exatamente as seis chaves de componente;
- rejeita componente ausente ou extra;
- rejeita propriedades desconhecidas;
- restringe IDs, enums, tipos, namespace de imagem e campos obrigatorios;
- representa comandos, health check e gates pendentes explicitamente.

O validador semantico verifica:

- conjunto e ordem canonicos;
- `release_control` excluido;
- politica `fail_closed_all`;
- IDs coerentes;
- dependencias existentes e grafo direto exato;
- ausencia de ciclo;
- imagens unicas, namespace aprovado e ausencia de tag/digest;
- coerencia entre readiness e gates;
- componente `ready` somente com build, teste, health check e persistencias
  confirmados;
- coerencia de comandos, health checks e migrations pendentes/confirmados;
- source paths sem colisao;
- listas globais, documentais e source paths congeladas conforme a politica
  aprovada;
- seis fechamentos minimos aprovados.

## 9. Interface CLI

| Comando | Codigo esperado |
|---|---:|
| `python3 tools/releases/catalog.py validate` | 0 quando estrutura e semantica sao validas |
| `python3 tools/releases/catalog.py validate --require-release-ready` | 3 enquanto houver gate |
| `python3 tools/releases/catalog.py resolve --changed <path>` | 0 ou 2 para path invalido |
| `python3 tools/releases/catalog.py resolve --first-release` | 0 |

A resolucao imprime JSON deterministico com `classification`, `changedPaths`,
`directComponents`, `buildComponents`, `validationComponents`,
`inheritedComponents` e `warnings`.

## 10. Testes

Comando exato:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py' \
  -v
```

Primeira execucao: codigo `1`; 30 testes, 29 aprovados e 1 erro. O mutante de
dependencia desconhecida era corretamente detectado, mas a verificacao
posterior de ciclo ainda tentava percorrer o ID ausente e produzia `KeyError`.

Correcao: a busca de ciclo passou a ignorar arestas cujo erro de referencia ja
foi classificado, permitindo retornar a falha contratual sem excecao interna.
Nenhum gate ou regra foi relaxado.

Execucao final: codigo `0`; **30 testes aprovados**, zero falhas e zero erros.

No ciclo corretivo 1, a suite foi ampliada para **42 testes**. Os 12 casos
adicionais cobrem normalizacao de `.github` e `./`, hidden path desconhecido,
readiness negativo para build/test/health/persistencia e congelamento das
politicas de path. Resultado: codigo `0`; 42 aprovados, zero falhas e zero
erros.

Cobertura:

- schema/invariantes do catalogo real;
- readiness bloqueado;
- componente ausente/extra;
- dependencia desconhecida e ciclo;
- imagem duplicada, namespace, tag e digest;
- coerencia readiness/gates e comandos;
- seis fechamentos;
- uniao deterministica;
- global, docs-only, unknown fail-closed e primeiro release;
- path absoluto e traversal;
- colisao de source path;
- complemento herdado;
- exclusao de `release_control`.

Catalogos mutantes existiram somente em memoria durante os testes.

## 11. Validacoes manuais prescritas

| Comando | Codigo | Resultado |
|---|---:|---|
| `python3 tools/releases/catalog.py validate` | 0 | `catalog:valid` |
| `... validate --require-release-ready` | 3 | 23 gates por ID/codigo |
| `... resolve --changed backend/src/main/App.java` | 0 | build backend; fechamento de cinco |
| `... resolve --changed website_back/src/main/App.java` | 0 | build website_back; fechamento de tres |
| `... resolve --changed whatsapp_service/index.js` | 0 | build whatsapp; valida os seis |
| `... resolve --changed docs/README.md` | 0 | build e validacao vazios |
| `... resolve --changed caminho/desconhecido.txt` | 0 | unknown; build/validacao dos seis; aviso |
| `... resolve --first-release` | 0 | build/validacao dos seis; sem heranca |

A falha de readiness e intencional e necessaria. O modo estrutural permanece
verde.

## 12. Documentacao

Criado `docs/infrastructure/deployment/release-control/README.md` com:

- diferenca entre catalogo, candidato e manifesto;
- BOM completo e exclusao de `release_control`;
- grafo, fechamento e heranca futura de digest;
- primeiro release;
- politicas global, documental e desconhecida;
- gates e comandos;
- procedimento de manutencao conjunta de catalogo, schema, testes e docs.

O indice `docs/infrastructure/README.md` vincula o novo contrato e deixa
explicito que releases ainda nao foram implementadas.

## 13. Estado Git e CI

| Verificacao | Resultado |
|---|---|
| `git ls-files --stage` | zero entradas |
| `git rev-parse --verify HEAD` | codigo 128; HEAD inexistente |
| `git tag --list` | zero tags |
| `git reflog show --all` | zero entradas |
| workflow `.yml`/`.yaml` em `.github/workflows/` | zero |
| cache Python em `tools/releases/` | zero |

Nao houve `git add` real ou temporario, commit, tag ou push.

## 14. Comandos relevantes

| Comando | CWD | Codigo | Interpretacao |
|---|---|---:|---|
| consulta de versoes PyYAML/jsonschema | raiz | 0 | dependencias disponiveis |
| validacao estrutural inicial | raiz | 0 | catalogo real valido |
| primeira suite unittest | raiz | 1 | robustez negativa revelou `KeyError` |
| suite unittest final da implementacao inicial | raiz | 0 | 30/30 |
| suite unittest do ciclo corretivo 1 | raiz | 0 | 42/42 |
| oito validacoes manuais prescritas | raiz | 0/3 esperado | todos os contratos confirmados |
| busca de caches Python | raiz | 0 | nenhum cache |
| verificacao Git/CI final | raiz | 0/128 esperado | estado protegido |

## 15. Desvios, itens nao determinados e bloqueios

Desvio:

- a primeira suite revelou e permitiu corrigir uma excecao interna no caso
  negativo de dependencia desconhecida. A repeticao integral ficou verde.

Itens nao determinados:

- digests herdados so existirao no manifesto/candidato futuro;
- gates operacionais e de Docker permanecem para slices posteriores;
- porta loopback do gateway ainda exige validacao no ambiente apropriado.

Bloqueios para a S05: nenhum.

O estado `blocked` dos componentes e a falha de readiness sao gates de
publicacao, nao falhas estruturais da slice.

## 16. Declaracao do que nao foi executado

Nao houve:

- instalacao ou download de dependencia;
- alteracao de aplicacao, Dockerfile, Compose ou workflow;
- `git add`, indice temporario, commit, tag ou push;
- build de imagem, candidato, manifesto ou release;
- implementacao de CI, UI ou `release_control`;
- alteracao de ambiente ou producao;
- acesso a GitHub, GHCR, DNS ou VPS;
- alteracao da task S05 ou do tracker de slices;
- remocao/ocultacao de gate para obter resultado verde.

## 17. Resposta final do executor

- catalogo canonico com os seis componentes entregue;
- schema local e invariantes semanticas verdes;
- resolvedor deterministico com fechamento transitivo;
- unknown path e primeiro release selecionam os seis;
- `release_control` permanece fora do BOM;
- readiness falha com codigo `3` e 18 gates pendentes;
- 30 testes aprovados;
- documentacao criada e indexada;
- indice, HEAD, tags, reflog e workflows YAML permanecem vazios.

> **Estado final do executor:** `IN_PROGRESS` — aguardando revisao do
> orquestrador. O executor nao declara `ACCEPTED`.

---

## 18. Revisao do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS` — implementação ainda não aceita  
> **Data:** `2026-07-28`

O catálogo, o schema, o grafo, os fechamentos principais, a documentação e a
disciplina Git atendem à maior parte do contrato. A suíte registrada também
preserva corretamente a primeira falha causal e a repetição verde.

Restam duas falhas que impedem o aceite.

### 18.1 Normalizacao corrompe paths iniciados por ponto

`normalize_changed_path` termina atualmente com:

```python
return normalized.lstrip("./")
```

`str.lstrip` não remove o prefixo literal `"./"`. Ele remove repetidamente
qualquer caractere pertencente ao conjunto `{".", "/"}`. Assim:

```text
.github/workflows/ci.yml
```

vira:

```text
github/workflows/ci.yml
```

Como consequência, a regra global declarada
`.github/workflows/**` não é reconhecida. O path cai em `unknown`,
selecionando rebuild dos seis e produzindo classificação/aviso incorretos. O
comportamento continua conservador, mas viola o contrato de paths e demonstra
uma lacuna nos testes.

### 18.2 Readiness nao exige contratos operacionais confirmados

As invariantes atuais verificam apenas:

- componente `ready` não pode manter gates;
- componente `blocked` precisa ter ao menos um gate.

Isso permite que uma alteração futura remova gates, marque o componente como
`ready` e ainda deixe:

- build ou teste `pending`;
- health check `pending` ou `inferred`;
- persistência `pending`.

Nesse estado, a validação estrutural e `--require-release-ready` poderiam ficar
verdes apesar de contratos operacionais ainda não confirmados. Isso contradiz
o propósito fail-closed do catálogo.

O catálogo atual também possui contratos não confirmados sem gate específico:

```text
frontend.health_check = inferred
website_front.test = pending
website_front.health_check = inferred
whatsapp_service.test = pending
gateway.test = pending
```

### Correcao requerida

Alterar somente:

```text
ops/releases/components.yml
tools/releases/catalog.py
tools/releases/tests/test_catalog.py
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/implementation/slices/S05-contrato-componentes-e-fechamento-dependencias.report.md
```

Não alterar schema, requirements, `.gitignore`, índice de infraestrutura,
workflows, aplicação, Docker ou metadados Git.

#### 1. Corrigir normalizacao

- remover o uso de `lstrip("./")`;
- preservar corretamente `.github/...` e qualquer basename iniciado por
  ponto;
- aceitar `./backend/...` por normalização segura, sem retirar pontos válidos;
- manter rejeição de path absoluto, vazio e traversal.

Adicionar testes que comprovem:

- `.github/workflows/ci.yml` é `global`, revalida os seis e não força rebuild;
- `./.github/workflows/ci.yml` produz o mesmo resultado normalizado;
- um path oculto desconhecido preserva o ponto na saída e no warning;
- `./backend/src/A.java` continua sendo mudança direta de `backend`.

#### 2. Fechar readiness

Adicionar invariantes semânticas para que `readiness: ready` somente seja
válido quando:

- `readiness_gates` estiver vazio;
- build estiver `confirmed` e possuir comando;
- teste estiver `confirmed` e possuir comando;
- health check estiver `confirmed` e possuir path;
- todas as persistências estiverem `confirmed`.

`inferred` não é suficiente para readiness de release.

Preservar a regra inversa: componente `blocked` deve possuir gates.

Adicionar ao catálogo gates explícitos, no mínimo:

```text
FRONTEND_HEALTH_CHECK_CONFIRMATION
WEBSITE_FRONT_TEST_COMMAND
WEBSITE_FRONT_HEALTH_CHECK_CONFIRMATION
WHATSAPP_TEST_COMMAND
GATEWAY_TEST_COMMAND
```

Os gates já existentes que cobrem build, health ou persistência devem ser
preservados. O total esperado passa de 18 para pelo menos 23.

Adicionar testes negativos que comprovem que um componente não pode ficar
`ready` com:

- teste pendente;
- health check `pending`;
- health check `inferred`;
- persistência pendente.

#### 3. Congelar politicas de path

Como as listas de paths são parte fixa desta slice, adicionar invariantes e
testes que rejeitem deriva em:

```text
global_paths
documentation_paths
source_paths de cada componente
```

Isso impede que a cobertura de `.github/workflows/**` ou de um componente seja
removida silenciosamente mantendo o schema verde.

#### 4. Revalidar

Repetir:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py' \
  -v

python3 tools/releases/catalog.py validate
python3 tools/releases/catalog.py validate --require-release-ready
python3 tools/releases/catalog.py resolve \
  --changed .github/workflows/ci.yml
python3 tools/releases/catalog.py resolve \
  --changed .hidden/unknown.txt
python3 tools/releases/catalog.py resolve \
  --changed ./backend/src/A.java
```

Resultados esperados:

- todos os testes verdes;
- validação estrutural com código `0`;
- readiness com código `3` e pelo menos 23 gates;
- `.github/workflows/ci.yml` classificado como global, build vazio e
  revalidação dos seis;
- hidden path desconhecido preservado no output/warning;
- path relativo com `./` resolvido como `backend`.

#### 5. Preservar estado

- atualizar as seções afetadas do relatório e da documentação;
- preservar esta revisão;
- adicionar `Resposta às correções do ciclo 1`;
- manter índice, `HEAD`, tags e reflog vazios;
- manter zero workflow YAML;
- não instalar dependências;
- não repetir testes de aplicação;
- manter `IN_PROGRESS` para nova revisão.

## Resposta às correções do ciclo 1

1. **Normalizacao corrigida:** `normalize_changed_path` nao usa mais
   `lstrip("./")`. O resultado de `posixpath.normpath` e retornado diretamente,
   preservando basenames iniciados por ponto e removendo apenas prefixos
   relativos reais como `./`.

2. **Paths iniciados por ponto comprovados:**

   - `.github/workflows/ci.yml` retorna `classification: global`, build vazio
     e revalidacao dos seis;
   - `./.github/workflows/ci.yml` possui exatamente o mesmo resultado
     normalizado;
   - `.hidden/unknown.txt` permanece com ponto em `changedPaths` e em
     `FAIL_CLOSED_UNKNOWN_PATH:.hidden/unknown.txt`;
   - `./backend/src/A.java` normaliza para `backend/src/A.java`, constroi
     `backend` e preserva seu fechamento transitivo.

3. **Readiness fail-closed:** um componente `ready` agora exige,
   simultaneamente:

   - `readiness_gates` vazio;
   - build `confirmed` com comando;
   - teste `confirmed` com comando;
   - health check `confirmed` com path; `inferred` nao basta;
   - todas as persistencias declaradas `confirmed`.

   A regra inversa permanece: componente `blocked` precisa de gate.

4. **Gates adicionados sem remover existentes:**

   - `FRONTEND_HEALTH_CHECK_CONFIRMATION`;
   - `WEBSITE_FRONT_TEST_COMMAND`;
   - `WEBSITE_FRONT_HEALTH_CHECK_CONFIRMATION`;
   - `WHATSAPP_TEST_COMMAND`;
   - `GATEWAY_TEST_COMMAND`.

   Os 18 gates anteriores foram preservados. O total atual e **23** e o modo
   readiness retorna codigo `3`.

5. **Politicas congeladas:** o validador compara semanticamente
   `global_paths`, `documentation_paths` e cada `source_paths` com as listas
   aprovadas nesta slice. Remocao, adicao ou substituicao silenciosa e
   rejeitada mesmo quando o schema permanece valido.

6. **Testes negativos e regressao:** a suite passou de 30 para 42 casos. Foram
   adicionados testes para:

   - `.github`, `./.github`, hidden desconhecido e `./backend`;
   - componente `ready` com build pending;
   - componente `ready` com teste pending;
   - health check pending;
   - health check inferred;
   - persistencia pending;
   - deriva de global paths, documentation paths e source paths.

   Comando prescrito: codigo `0`; **42 testes aprovados**, zero falhas e zero
   erros.

7. **Validacoes prescritas:**

   | Validacao | Codigo | Resultado |
   |---|---:|---|
   | estrutural | 0 | `catalog:valid` |
   | readiness | 3 | 23 gates por ID/codigo |
   | `.github/workflows/ci.yml` | 0 | global, build vazio, seis revalidados |
   | `.hidden/unknown.txt` | 0 | unknown fail-closed, ponto preservado |
   | `./backend/src/A.java` | 0 | backend direto e fechamento correto |

8. **Documentacao:** o contrato de release-control agora explica a
   normalizacao de paths ocultos, os requisitos completos para `ready` e a
   manutencao das listas de paths junto da decisao contratual.

9. **Escopo preservado:** foram alterados somente os cinco arquivos
   autorizados. Schema, requirements, `.gitignore`, indice de infraestrutura,
   workflows, aplicacao, Docker, task, tracker e metadados Git permaneceram
   inalterados. Nao houve instalacao, teste de aplicacao, `git add`, commit,
   tag, push ou acesso externo.

10. **Estado:** a S05 permanece `IN_PROGRESS`, aguardando nova revisao do
    orquestrador; o executor nao declara `ACCEPTED`.

---

## 19. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-28`

A S05 atende ao contrato após o ciclo corretivo.

Evidências aceitas:

- o BOM comercial contém exatamente os seis componentes aprovados;
- `release_control` permanece explicitamente fora do BOM;
- o schema é local e o catálogo passa validação estrutural;
- o grafo direto e os seis fechamentos transitivos correspondem ao contrato;
- primeiro release e path desconhecido selecionam os seis componentes;
- docs-only não seleciona build ou revalidação;
- componentes não reconstruídos são identificados para futura herança de
  digest;
- paths absolutos, vazios e com traversal são rejeitados;
- `.github/workflows/**` é corretamente classificado como global, inclusive
  quando recebido com prefixo relativo `./`;
- paths ocultos desconhecidos preservam o ponto no output e no warning;
- políticas globais, documentais e source paths estão congeladas por
  invariantes semânticas;
- `ready` exige build, teste, health check e persistências confirmados, além de
  ausência de gates;
- health check `inferred` não satisfaz readiness;
- os 18 gates originais foram preservados e cinco gates complementares foram
  adicionados, totalizando 23;
- `--require-release-ready` falha fechado com código `3`;
- a suíte final registrada passou em 42 de 42 testes;
- documentação, catálogo, schema, resolvedor e testes permanecem alinhados;
- índice, `HEAD`, tags e reflog continuam vazios;
- nenhum workflow YAML, cache Python, commit, push ou acesso externo foi
  criado.

Os estados `IN_PROGRESS` anteriores permanecem como histórico. A autoridade
final desta seção altera o estado da S05 para `ACCEPTED`.

A S06 pode definir os contratos de API, estados, idempotência, concorrência e
segurança do `release_control`. A escolha de framework, persistência e
implementação executável permanece para slice posterior.
