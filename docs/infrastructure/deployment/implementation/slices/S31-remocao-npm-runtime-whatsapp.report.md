# S31 — relatório de execução bloqueada

> **Estado da execução:** bloqueada antes da paridade Docker completa
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Autoridade:** `S31-remocao-npm-runtime-whatsapp.task.md`, derivada de `S30a-paridade-local-fechamento-ci-candidato.authorization-01.md`
> **SHA-base:** `10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a`
> **Mensagem prevista:** `fix: remove package managers from whatsapp runtime image`
> **Commit:** não criado por condição de parada da §4.5

## 1. Autoridade e preflight

Foram lidos integralmente antes da alteração: task S31; authorization-01 da
S30a; Seção 15 do relatório S30a; JSONs Trivy `before` e `after` da S30a;
`whatsapp_service/Dockerfile`; validador e testes Node; job `images` de
`.github/workflows/ci.yml`.

Preflight canônico:

| Comando | Exit | Saída |
|---|---:|---|
| `test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"` | 0 | vazia |
| `test "$(git branch --show-current)" = "main"` | 0 | vazia |
| `test -z "$(git status --porcelain)"` | 0 | vazia |
| `test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"` | 0 | vazia |
| `test "$(git log -1 --format=%s)" = "docs: refine S31 delegation contract"` | 0 | vazia |
| `test "$(git rev-list --count origin/main..HEAD)" = "6"` | 0 | vazia |
| `git rev-parse HEAD` | 0 | `10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a` |

Confirmações adicionais: branch `main`; assunto de HEAD
`docs: refine S31 delegation contract`; `origin/main` em
`0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`; seis commits locais; stage e
worktree inicialmente vazios.

Houve uma invocação anterior com erro de transcrição manual do SHA esperado
(`0bd563b7bb44ffcf2d1d705a5bbafe7a356f06`, com um caractere ausente), exit 1.
O comando canônico acima foi então executado literalmente e retornou exit 0; a
consulta direta a `origin/main` confirmou o valor autorizado. Não houve
alteração antes dessa confirmação.

## 2. Arquivos alterados e implementação

Alterados no worktree:

- `whatsapp_service/Dockerfile`;
- `tools/docker/validate_node_images.py`;
- `tools/docker/tests/test_validate_node_images.py`;
- este relatório.

Não criado devido à parada anterior ao scan:

- `S31-trivy-findings.whatsapp.after.json`.

Implementação realizada, ainda sem commit:

- removida a instalação global `npm@12.0.2` do runtime;
- adicionada, na camada existente de `apk add`, purga `rm -rf` dos oito
  caminhos fechados, antes de `USER 10001:10001`;
- mantidos o estágio `dependencies`, `npm ci`, o `COPY --from=dependencies` e
  o runtime Node;
- estágio `runtime` isolado por alias até o próximo `FROM` ou EOF;
- `GLOBAL_CLI_FORBIDDEN` passou a reconhecer o produto cartesiano fechado de
  três flags, três subcomandos e duas posições;
- `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` passou a examinar comandos efetivos,
  inclusive formas shell e JSON, e somente aceita a purga quando os oito
  caminhos são argumentos de `rm` recursivo;
- `NPM_CI_REQUIRED` permaneceu independente;
- adicionados 28 casos S31 observáveis ao unittest.

## 3. Enumeração da base

Imagem pinada:
`node:24.18.1-alpine3.24@sha256:f70403e87646dc51b45295f4b8b70cdad0b63d2297c4c9899119b03f7af7a6b3`.
A imagem já existia localmente como
`sha256:56b2e0aba61d59409f91edd9a629b8315e0264a4d82fedfd64e7e632b3fd2aa6`.
O pull autorizado retornou exit 0 e `Image is up to date`.

Enumeração literal, exit 0, duração 0,6 s:

```text
/usr/local/lib/node_modules/npm|directory|present
/usr/local/lib/node_modules/corepack|directory|present
/opt/yarn-v1.22.22|directory|present
/usr/local/bin/npm|symlink|../lib/node_modules/npm/bin/npm-cli.js
/usr/local/bin/npx|symlink|../lib/node_modules/npm/bin/npx-cli.js
/usr/local/bin/corepack|symlink|../lib/node_modules/corepack/dist/corepack.js
/usr/local/bin/yarn|symlink|/opt/yarn-v1.22.22/bin/yarn
/usr/local/bin/yarnpkg|symlink|/opt/yarn-v1.22.22/bin/yarnpkg
/usr/local/bin/pnpm|absent|-
/usr/local/bin/pnpx|absent|-
```

A enumeração coincide com os oito caminhos da §4.2; `pnpm` e `pnpx` estão
ausentes.

## 4. Resultado literal dos 28 casos S31

Todos foram executados por
`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools.docker.tests.test_validate_node_images -v`:
50 testes totais, exit 0, `Ran 50 tests in 2.055s`, `OK`.

| Caso | Mutação/caso | Resultado observado e exigido |
|---:|---|---|
| 1 | `npm -g install some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 2 | `npm install -g some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 3 | `npm -g i some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 4 | `npm i -g some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 5 | `npm -g add some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 6 | `npm add -g some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 7 | `npm --global install some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 8 | `npm install --global some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 9 | `npm --global i some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 10 | `npm i --global some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 11 | `npm --global add some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 12 | `npm add --global some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 13 | `npm --location=global install some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 14 | `npm install --location=global some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 15 | `npm --location=global i some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 16 | `npm i --location=global some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 17 | `npm --location=global add some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 18 | `npm add --location=global some-cli` | `['GLOBAL_CLI_FORBIDDEN:whatsapp_service']` — ok |
| 19 | `RUN npx some-cli` no runtime | `['PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME']` — ok |
| 20 | `RUN corepack enable` no runtime | `['PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME']` — ok |
| 21 | `RUN yarn global add some-cli` no runtime | `['PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME']` — ok |
| 22 | purga removida | `['PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME']` — ok |
| 23 | `rm` trocado por `echo` | `['PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME']` — ok |
| 24 | purga reduzida a comentário | `['PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME']` — ok |
| 25 | purga sem diretório e links Yarn | `['PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME']` — ok |
| 26 | `npm ci` removido de `dependencies` | `['NPM_CI_REQUIRED:whatsapp_service']` — ok |
| 27 | Dockerfile corrigido íntegro | `[]` — ok |
| 28 | `npm ci` em `dependencies`, regra focal de runtime | `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` ausente; erros `[]` — ok |

## 5. Matriz local executada antes do bloqueio

Treze validadores, todos exit 0:

| Comando | Duração | Saída |
|---|---:|---|
| `python3 tools/docker/validate_node_images.py validate` | 0,000014 s | `node-images-contract:valid` |
| `python3 tools/docker/java_images_contract.py validate` | 0,001068 s | `java-images-contract:valid` |
| `python3 tools/ci/validate_ci.py` | 0,000009 s | `ci:valid` |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0,166300 s | `candidate-workflow:valid` |
| `python3 tools/releases/validate_release_workflow.py` | 0,198380 s | `release-workflow:valid` |
| `python3 tools/releases/validate_publisher_ui.py` | 0,000017 s | `publisher-ui:valid` |
| `python3 tools/releases/validate_publisher_identity_bridge.py` | 0,000004 s | `publisher-identity-bridge:valid` |
| `python3 tools/ci/validate_workflow_inventory.py` | 0,062891 s | `workflow-inventory:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0,175094 s | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0,010122 s | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0,000005 s | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | 0,108748 s | `release-control-contract:valid` |
| `python3 tools/security/bootstrap_contract.py validate` | 0,000004 s | `bootstrap-contract:valid` |

Sete suítes, todas exit 0:

| Suíte | Duração do comando | Resultado unittest |
|---|---:|---|
| `tools/docker/tests` | 2,640 s | 87 testes em 2,617 s; `OK` |
| `tools/ci/tests` | 9,247 s | 30 testes em 8,986 s; `OK` |
| `tools/candidates/tests` | 7,242 s | 68 testes em 6,983 s; `OK` |
| `tools/releases/tests` | 13,026 s | 298 testes em 12,777 s; `OK` |
| `tools/security/tests` | 0,092 s | 26 testes em 0,115 s; `OK` |
| `tools/compose/tests` | 0,174 s | 4 testes em 0,193 s; `OK` |
| `tools/gateway/tests` | 0,000008 s | 4 testes em 0,004 s; `OK` |

Total: 517 testes, todos verdes. `git diff --check` havia retornado exit 0 após
a implementação e antes dessa matriz. A última repetição prevista ao final da
§7 não foi executada porque a condição de parada ocorreu na etapa seguinte.

## 6. Paridade Docker e condição de parada

Recursos planejados e confirmados ausentes antes da execução:

- tag `s31-whatsapp-runtime:local-10db6b3`;
- container `s31-whatsapp-smoke-10db6b3`;
- builder `s31-whatsapp-builder-10db6b3`;
- volume `s31-trivy-cache-10db6b3`.

O builder foi criado por:

```text
docker buildx create --name s31-whatsapp-builder-10db6b3 --driver docker-container --platform linux/amd64 --use
```

Resultado: exit 0; duração 0,087 s.

O build foi iniciado com contexto `whatsapp_service`, Dockerfile canônico,
`linux/amd64`, `--load`, `--push=false`, `VCS_REF` igual ao SHA-base e
`IMAGE_VERSION=ci-<SHA-base>`. Antes de processar o Dockerfile, o driver
`docker-container` tentou baixar:

```text
#1 pulling image moby/buildkit:buildx-stable-1
```

Esse destino não está entre os acessos externos fechados da S31 §4.5. A
tentativa foi identificada com o builder ainda `inactive`; a execução foi
interrompida. Saída terminal literal:

```text
#1 pulling image moby/buildkit:buildx-stable-1 158.2s done
#1 CANCELED
ERROR: failed to build: context canceled
```

Exit do build: 130. Esta é a condição de parada. Não foi tentada alternativa
com o builder default, pois isso seria continuar depois de um destino externo
não autorizado.

Consequências:

- inspeção da imagem: não executada;
- scan Trivy: não executado;
- JSON de evidência e linhagem: não emitido;
- smoke `/health/live` e `/status`: não executado;
- prova de invocabilidade no container: não executada;
- delta Trivy: não medido; referência anterior permanece 1 HIGH no componente;
- secret scans finais: não executados, porque JSON e relatório final não
  puderam ser staged na ordem contratual;
- `git diff --cached --check`: não executado;
- commit: não criado;
- push: não executado.

## 7. Cleanup e resíduos

Cleanup executado exclusivamente no recurso criado:

```text
docker buildx rm s31-whatsapp-builder-10db6b3
```

Exit 0; saída `s31-whatsapp-builder-10db6b3 removed`. Depois do cleanup:

- builder nominal: ausente;
- tag nominal: ausente;
- container nominal: ausente;
- volume Trivy nominal: ausente;
- `moby/buildkit:buildx-stable-1`: não existe como imagem no daemon;
- imagem base preexistente: preservada;
- nenhum prune foi executado.

Resíduos ignorados preexistentes e preservados:

```text
tools/docker/__pycache__/java_images_contract.cpython-313.pyc
tools/docker/tests/__pycache__/test_java_images_contract.cpython-313.pyc
```

Dois `.pyc` de Node criados por uma compilação focal durante esta execução
foram removidos nominalmente; os resíduos Java acima têm timestamp anterior à
execução e não foram tocados.

Estado Git ao encerrar: stage vazio; três arquivos de implementação e este
relatório no worktree; nenhum arquivo fora da fronteira alterado. O JSON
obrigatório está ausente por falta de medição.

## 8. Acessos externos, divergências e efeitos negativos

Acessos observados:

- `docker.io/library/node`: pull autorizado da base pinada, já atualizada;
- `docker.io/moby/buildkit`: tentativa não autorizada provocada pelo driver
  `docker-container`; causa terminal deste relatório;
- nenhum download `apk` ou npm ocorreu, pois o Dockerfile não começou a ser
  processado;
- nenhum acesso a Trivy/database ocorreu;
- nenhum GHCR, login, push, workflow dispatch, release, tag, deploy, rollback,
  SSH, produção, sessão ou tráfego de WhatsApp ocorreu.

Divergência terminal: destino externo fora da lista fechada da §4.5. Não houve
ampliação de autoridade, workaround, alteração fora da fronteira ou commit.
Backend e website_back não foram tocados; os grupos A e C permanecem fora do
escopo e continuam bloqueando a CI global.

Mensagem contratual preservada: `fix: remove package managers from whatsapp runtime image`.

commit final = HEAD entregue

## 9. Execução da correction-01 — retomada com BuildKit pinado

> **Autoridade de retomada:** `S31-remocao-npm-runtime-whatsapp.correction-01.md`
> **Checkpoint:** `db1de9e1e3c433101fba49f826f9fa5bfbd94406`
> **Resultado:** bloqueada no build antes do primeiro estágio

### 9.1 Preflight herdado

O preflight limpo da task não foi repetido. Os comandos fechados da §3 da
correction-01 retornaram:

| Comando | Exit | Saída |
|---|---:|---|
| `test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"` | 0 | vazia |
| `test "$(git branch --show-current)" = "main"` | 0 | vazia |
| `test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"` | 0 | vazia |
| `test "$(git log -1 --format=%s)" = "docs: authorize pinned BuildKit for S31 resume"` | 0 | vazia |
| `test "$(git rev-list --count origin/main..HEAD)" = "7"` | 0 | vazia |
| `git rev-parse HEAD` | 0 | `db1de9e1e3c433101fba49f826f9fa5bfbd94406` |
| `git status --short` | 0 | três técnicos modificados e este relatório não rastreado |
| `git diff --cached --name-only` | 0 | vazia |
| `git diff --check` | 0 | vazia |

O JSON S31 estava ausente. Builder, tag, container, volume e imagem BuildKit
pinada estavam ausentes. A imagem Trivy também estava ausente. A base Node
preexistente permanecia em
`sha256:56b2e0aba61d59409f91edd9a629b8315e0264a4d82fedfd64e7e632b3fd2aa6`.
`docker system df` registrou literalmente:

```text
TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE
Images          28        1         7.69GB    7.414GB (96%)
Containers      1         1         63B       0B (0%)
Local Volumes   19        1         1.081GB   1.032GB (95%)
Build Cache     0         0         0B        0B
```

### 9.2 Builder pinado e digest efetivo

Criação, exit 0:

```text
docker buildx create --name s31-whatsapp-builder-10db6b3 --driver docker-container --driver-opt image=docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684 --platform linux/amd64 --use
s31-whatsapp-builder-10db6b3
```

O bootstrap acessou somente a referência BuildKit autorizada e criou o
container nominal:

```text
#1 pulling image docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684 117.0s done
#1 creating container buildx_buildkit_s31-whatsapp-builder-10db6b30 0.6s done
#1 DONE 117.6s
```

A primeira saída de `docker buildx inspect --bootstrap` terminou com exit 0,
mas registrou um `context deadline exceeded` ao consultar o container recém-
criado. A inspeção imediata seguinte retornou exit 0, estado `running`,
BuildKit `v0.31.2` e `linux/amd64*` como plataforma selecionada.

Prova literal por `docker inspect`:

```text
/buildx_buildkit_s31-whatsapp-builder-10db6b30|docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684|sha256:e0e67ea9c4bb07bdef683e29f70f3b623d1eb9c82274a9ec04e6bb0511449fb0|running|true
```

Prova da imagem efetiva:

```text
sha256:e0e67ea9c4bb07bdef683e29f70f3b623d1eb9c82274a9ec04e6bb0511449fb0|["moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684"]|amd64|linux
```

Não houve resolução de `buildx-stable-1`, outro digest ou outro registry nessa
etapa.

### 9.3 Build e nova condição de parada

Comando executado:

```text
docker buildx build --builder s31-whatsapp-builder-10db6b3 --platform linux/amd64 --load --push=false --file whatsapp_service/Dockerfile --tag s31-whatsapp-runtime:local-10db6b3 --build-arg VCS_REF=10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a --build-arg IMAGE_VERSION=ci-10db6b3 whatsapp_service
```

Resultado: exit 1; duração 20,157 s. Saída terminal literal:

```text
#0 building with "s31-whatsapp-builder-10db6b3" instance using docker-container driver

#1 [internal] load build definition from Dockerfile
#1 transferring dockerfile: 1.91kB done
#1 DONE 0.0s

#2 resolve image config for docker-image://docker.io/docker/dockerfile:1.7
#2 ERROR: failed to do request: Head "https://registry-1.docker.io/v2/docker/dockerfile/manifests/1.7": dial tcp: lookup registry-1.docker.io on 177.86.16.10:53: read udp 172.17.0.2:42151->177.86.16.10:53: i/o timeout
------
 > resolve image config for docker-image://docker.io/docker/dockerfile:1.7:
------
Dockerfile:1
--------------------
   1 | >>> # syntax=docker/dockerfile:1.7
   2 |     FROM node:24.18.1-alpine3.24@sha256:f70403e87646dc51b45295f4b8b70cdad0b63d2297c4c9899119b03f7af7a6b3 AS dependencies
   3 |     WORKDIR /workspace
--------------------
ERROR: failed to build: failed to solve: failed to resolve source metadata for docker.io/docker/dockerfile:1.7: failed to do request: Head "https://registry-1.docker.io/v2/docker/dockerfile/manifests/1.7": dial tcp: lookup registry-1.docker.io on 177.86.16.10:53: read udp 172.17.0.2:42151->177.86.16.10:53: i/o timeout
```

O Dockerfile frontend `docker.io/docker/dockerfile:1.7` não integra a lista
fechada de acessos da task, e a correction-01 acrescentou exclusivamente o
BuildKit pinado. A tentativa de resolução dessa referência e o exit 1 são
condição de parada. Não foi usado builder default, outra tag, outro digest,
workaround ou alteração técnica.

Como o build não ultrapassou a resolução do frontend:

- a tag runtime não foi criada;
- inspeção da imagem não foi executada;
- Trivy e seus bancos não foram baixados;
- scan e JSON de linhagem não foram produzidos;
- smoke e prova dos gerenciadores não foram executados;
- stage, secret scans, `git diff --cached --check` e commit não foram
  executados;
- não houve tráfego ou sessão de WhatsApp.

### 9.4 Cleanup nominal

Antes do cleanup, o cache exclusivo do builder era:

```text
ID                           RECLAIMABLE   SIZE      LAST ACCESSED
jap5zku90wmghln75qya4njxu*   true          8.192kB   14 seconds ago
Reclaimable: 8.192kB
Total:       8.192kB
```

`docker buildx rm s31-whatsapp-builder-10db6b3` retornou exit 0 e
`s31-whatsapp-builder-10db6b3 removed`. O container
`buildx_buildkit_s31-whatsapp-builder-10db6b30` e o volume
`buildx_buildkit_s31-whatsapp-builder-10db6b30_state` ficaram ausentes.

Depois de provar que nenhum outro container usava a imagem, foi executado
nominalmente:

```text
docker image rm docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
```

Exit 0; a referência e a imagem `sha256:e0e67ea9c4bb07bdef683e29f70f3b623d1eb9c82274a9ec04e6bb0511449fb0`
foram removidas. Não houve prune.

Prova final:

- builder, container e volume BuildKit nominais: ausentes;
- imagem BuildKit pinada: ausente;
- imagem runtime e container smoke: ausentes;
- volume Trivy nominal: ausente;
- imagem Trivy, ausente desde o preflight: continua ausente;
- base Node preexistente: preservada com o mesmo ID;
- `Build Cache`: `0B`;
- stage: vazio;
- worktree: os três arquivos técnicos herdados e este relatório;
- JSON S31: ausente.

### 9.5 Acessos externos, resíduos e divergência

Acessos externos desta retomada:

- `docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684`:
  autorizado e concluído;
- `registry-1.docker.io/v2/docker/dockerfile/manifests/1.7`: tentativa de
  resolução do frontend não autorizada pela lista fechada e encerrada por
  timeout DNS; causa terminal;
- nenhum outro download, GHCR, login, push, workflow dispatch, release, tag,
  deploy, rollback, SSH ou produção.

Divergência terminal: a paridade Docker exige resolver o frontend declarado na
primeira linha do Dockerfile, mas a correction-01 não o autorizou nem o fixou
por digest. A implementação técnica validada foi preservada sem nova alteração.
Não existe commit S31 e `HEAD` permanece no checkpoint da correction-01.

## 10. Execução da correction-02 — paridade Docker consolidada

> **Autoridade de retomada:** `S31-remocao-npm-runtime-whatsapp.correction-02.md`
> **Checkpoint:** `5ca4d1164dbfb206114b02c0daf5da3487b90423`
> **Resultado:** bloqueada após três falhas transitórias idênticas do scan Trivy

### 10.1 Preflight e delta técnico único

O preflight limpo da task não foi repetido. Os comandos da §3 da correction-02
retornaram:

| Comando | Exit | Saída |
|---|---:|---|
| `test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"` | 0 | vazia |
| `test "$(git branch --show-current)" = "main"` | 0 | vazia |
| `test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"` | 0 | vazia |
| `test "$(git log -1 --format=%s)" = "docs: consolidate S31 Docker parity plan"` | 0 | vazia |
| `test "$(git rev-list --count origin/main..HEAD)" = "8"` | 0 | vazia |
| `git rev-parse HEAD` | 0 | `5ca4d1164dbfb206114b02c0daf5da3487b90423` |
| `git status --short` | 0 | três técnicos modificados e relatório não rastreado |
| `git diff --cached --name-only` | 0 | vazia |
| `git diff --check` | 0 | vazia |

JSON, builder, imagens BuildKit/frontend/Trivy/runtime, container e volumes
nominais estavam ausentes. A base Node preexistente permanecia em
`sha256:56b2e0aba61d59409f91edd9a629b8315e0264a4d82fedfd64e7e632b3fd2aa6`.
`docker system df` registrou `Build Cache 0B`.

Foi aplicado somente o delta prescrito, na primeira linha de
`whatsapp_service/Dockerfile`:

```dockerfile
# syntax=docker.io/docker/dockerfile@sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720
```

Nenhum outro Dockerfile, validador, teste ou arquivo técnico foi alterado nesta
retomada.

### 10.2 Matriz local executada uma vez

Treze validadores, todos exit 0:

| Comando | Duração | Saída |
|---|---:|---|
| `python3 tools/docker/validate_node_images.py validate` | 0,000008 s | `node-images-contract:valid` |
| `python3 tools/docker/java_images_contract.py validate` | 0,000217 s | `java-images-contract:valid` |
| `python3 tools/ci/validate_ci.py` | 0,000006 s | `ci:valid` |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0,011644 s | `candidate-workflow:valid` |
| `python3 tools/releases/validate_release_workflow.py` | 0,041831 s | `release-workflow:valid` |
| `python3 tools/releases/validate_publisher_ui.py` | 0,000003 s | `publisher-ui:valid` |
| `python3 tools/releases/validate_publisher_identity_bridge.py` | 0,000004 s | `publisher-identity-bridge:valid` |
| `python3 tools/ci/validate_workflow_inventory.py` | 0,000003 s | `workflow-inventory:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0,040670 s | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0,000003 s | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0,000004 s | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | 0,000003 s | `release-control-contract:valid` |
| `python3 tools/security/bootstrap_contract.py validate` | 0,000003 s | `bootstrap-contract:valid` |

Sete suítes, todas exit 0:

| Suíte | Duração do comando | Resultado unittest |
|---|---:|---|
| `tools/docker/tests` | 1,152 s | 87 testes em 1,218 s; `OK` |
| `tools/ci/tests` | 4,744 s | 30 testes em 4,720 s; `OK` |
| `tools/candidates/tests` | 3,647 s | 68 testes em 3,608 s; `OK` |
| `tools/releases/tests` | 6,597 s | 298 testes em 6,553 s; `OK` |
| `tools/security/tests` | 0,000006 s | 26 testes em 0,042 s; `OK` |
| `tools/compose/tests` | 0,138 s | 4 testes em 0,227 s; `OK` |
| `tools/gateway/tests` | 0,000004 s | 4 testes em 0,003 s; `OK` |

Total: 517 testes. Os 28 casos `test_s31_case_01` a
`test_s31_case_28` apareceram individualmente com resultado literal `... ok`;
as expectativas exatas permanecem enumeradas na Seção 4 deste relatório.
`git diff --check` foi executado uma vez ao final da matriz, exit 0 e saída
vazia.

### 10.3 Builder pinado com rede host

Criação, exit 0:

```text
docker buildx create --name s31-whatsapp-builder-10db6b3 --driver docker-container --driver-opt image=docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684 --driver-opt network=host --platform linux/amd64 --use
s31-whatsapp-builder-10db6b3
```

O bootstrap baixou somente o digest autorizado em 46,5 s e criou
`buildx_buildkit_s31-whatsapp-builder-10db6b30`. Assim como na correction-01,
a primeira inspeção agregada registrou `context deadline exceeded` imediatamente
após criar o container, mas terminou exit 0. As inspeções seguintes passaram:

```text
/buildx_buildkit_s31-whatsapp-builder-10db6b30|docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684|sha256:e0e67ea9c4bb07bdef683e29f70f3b623d1eb9c82274a9ec04e6bb0511449fb0|host|running|true
```

`docker buildx inspect` retornou exit 0, BuildKit `v0.31.2`, `Status: running`,
`network="host"` e `Platforms: linux/amd64*`. A imagem efetiva era `amd64/linux`
e tinha exclusivamente o RepoDigest autorizado.

### 10.4 Build e inspeção

O build canônico foi executado uma vez, sem retry:

```text
docker buildx build --builder s31-whatsapp-builder-10db6b3 --platform linux/amd64 --load --push=false --file whatsapp_service/Dockerfile --tag s31-whatsapp-runtime:local-10db6b3 --build-arg VCS_REF=10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a --build-arg IMAGE_VERSION=ci-10db6b3 whatsapp_service
```

Resultado: exit 0, aproximadamente 492 s. O frontend e a base foram resolvidos
pelos digests fechados; `npm ci --omit=dev` acrescentou 300 pacotes, informou
`found 0 vulnerabilities` e terminou; `apk add` instalou 179 pacotes dos
repositórios configurados; a imagem foi carregada localmente.

Metadados inspecionados, exit 0:

```text
sha256:3d123d8b615b247f04b6fb721c8f3ec7cfaf44c79f0b89eae736417a5e99024b|amd64|linux|10001:10001|["node","index.js"]|10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a|ci-10db6b3
```

Inspeção literal do filesystem, exit 0:

```text
/usr/local/lib/node_modules/npm|absent
/usr/local/lib/node_modules/corepack|absent
/opt/yarn-v1.22.22|absent
/usr/local/bin/npm|absent
/usr/local/bin/npx|absent
/usr/local/bin/corepack|absent
/usr/local/bin/yarn|absent
/usr/local/bin/yarnpkg|absent
node|/usr/local/bin/node|v24.18.1
npm|not-invocable
npx|not-invocable
corepack|not-invocable
yarn|not-invocable
yarnpkg|not-invocable
```

### 10.5 Trivy e condição terminal

A imagem Trivy pinada foi baixada com exit 0; uma camada exigiu retries internos
do próprio `docker pull`, que ainda terminou no digest autorizado. Foi criado o
volume `s31-trivy-cache-10db6b3` e usado sempre o mesmo comando:

```text
docker run --rm --platform linux/amd64 -v /var/run/docker.sock:/var/run/docker.sock -v s31-trivy-cache-10db6b3:/root/.cache/trivy -v /tmp/s31-trivy-10db6b3.X6RG4l:/evidence aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e image --skip-version-check --cache-dir /root/.cache/trivy --db-repository mirror.gcr.io/aquasec/trivy-db:2 --format json --output /evidence/scan.json --severity HIGH,CRITICAL --ignore-unfixed=false --exit-code 1 s31-whatsapp-runtime:local-10db6b3
```

As três tentativas totais foram idênticas e falharam somente durante a leitura
do banco autorizado:

| Tentativa | Exit | Duração | Resultado |
|---:|---:|---:|---|
| 1 | 1 | 58,640 s | timeout DNS em `mirror.gcr.io` |
| 2 | 1 | 59,000 s | timeout DNS em `mirror.gcr.io` |
| 3 | 1 | 60,002 s | timeout DNS em `mirror.gcr.io` |

Saída terminal comum, com apenas porta UDP efêmera diferente:

```text
FATAL Fatal error run error: init error: DB error: failed to download vulnerability DB: OCI artifact error: failed to download vulnerability DB: failed to download artifact from mirror.gcr.io/aquasec/trivy-db:2: OCI repository error: 1 error occurred:
  * Get "https://mirror.gcr.io/v2/": dial tcp: lookup mirror.gcr.io on 177.86.16.10:53: read udp 172.17.0.2:<porta>->177.86.16.10:53: i/o timeout
```

O scanner não chegou a inicializar o banco nem a analisar a imagem. Portanto:

- achados HIGH/CRITICAL: não medidos nesta execução;
- delta Trivy: não produzido;
- `S31-trivy-findings.whatsapp.after.json`: não criado;
- smoke: não executado, pois vem depois do gate Trivy na ordem consolidada;
- stage, dois secret scans, `git diff --cached --check` e commit: não
  executados.

O limite de três tentativas idênticas foi esgotado. Não foi usado outro mirror,
digest, DNS, fallback ou alteração técnica.

### 10.6 Cleanup nominal e estado final

Antes do cleanup, `docker buildx du` registrou 2,386 GB, integralmente no
builder nominal. O cleanup executou:

```text
docker buildx rm s31-whatsapp-builder-10db6b3
docker image rm s31-whatsapp-runtime:local-10db6b3
docker volume rm s31-trivy-cache-10db6b3
docker image rm aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e
docker image rm docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
rmdir /tmp/s31-trivy-10db6b3.X6RG4l
```

Todos retornaram exit 0. Uma tentativa anterior de `rm -rf` sobre o diretório
temporário vazio foi rejeitada pela camada de execução antes de iniciar
processo; `rmdir` concluiu o cleanup sem efeito externo adicional.

Prova final:

- builder, container e volume BuildKit nominais: ausentes;
- imagens BuildKit, frontend, runtime e Trivy: ausentes;
- container smoke e volume Trivy: ausentes;
- diretório temporário: ausente;
- base Node preexistente: preservada com o mesmo ID;
- `Build Cache`: `0B`;
- stage: vazio;
- JSON S31: ausente;
- worktree: os três técnicos herdados, com somente o pin adicional no
  Dockerfile, e este relatório;
- `git diff --check`: exit 0.

Acessos externos observados e autorizados: `auth.docker.io`,
`registry-1.docker.io` para BuildKit, frontend, base e Trivy;
`dl-cdn.alpinelinux.org`; `registry.npmjs.org`; e tentativa de
`mirror.gcr.io/aquasec/trivy-db:2`. Não houve GHCR, login, push, publicação,
workflow dispatch, release, tag, deploy, rollback, SSH, produção ou tráfego de
WhatsApp.

Resíduos ignorados preexistentes preservados:

```text
tools/docker/__pycache__/java_images_contract.cpython-313.pyc
tools/docker/tests/__pycache__/test_java_images_contract.cpython-313.pyc
```

Divergência terminal: três timeouts DNS consecutivos impediram obter o banco
Trivy prescrito. Não existe commit S31; `HEAD` permanece no checkpoint da
correction-02. A implementação e a prova de build/inspeção continuam no
worktree para nova revisão do orquestrador.

## 11. Retomada operacional — Trivy em rede host

> **Autoridade de retomada:** clarificação operacional da correction-02 emitida
> pelo orquestrador em 02/08/2026
> **Checkpoint:** `5ca4d1164dbfb206114b02c0daf5da3487b90423`
> **Resultado:** paridade Docker concluída; commit único criado; sem push

### 11.1 Causa das três falhas e delta operacional

A revisão do orquestrador estabeleceu que as três falhas registradas na §10.5
ocorreram porque o container Trivy foi executado na bridge padrão. O comando
literal registrado naquela seção não contém `--network host`. O orquestrador
reproduziu em 02/08/2026 a obtenção do mesmo banco, com a mesma imagem e o mesmo
repository, acrescentando somente `--network host`, com exit 0 e
`mirror.gcr.io/aquasec/trivy-db:2` baixado integralmente.

Trata-se de clarificação operacional da correction-02, não de nova correction.
Não houve ampliação de imagem, digest, mirror, destino ou fronteira. As três
tentativas anteriores não contam para o comando corrigido.

Nenhum arquivo técnico foi alterado nesta retomada. O `whatsapp_service/Dockerfile`,
o validador e os testes permanecem exatamente como aprovados na §10, incluindo o
pin do frontend. Por decisão do orquestrador, a matriz, o build e a inspeção já
aprovados foram preservados: os treze validadores, as sete suítes e os 28 casos
não foram repetidos. Como o cleanup correto da §10.6 removeu a imagem runtime,
o builder pinado foi recriado e o build canônico refeito.

### 11.2 Preflight desta retomada

| Comando | Exit | Saída |
|---|---:|---|
| `test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"` | 0 | vazia |
| `test "$(git branch --show-current)" = "main"` | 0 | vazia |
| `test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"` | 0 | vazia |
| `test "$(git log -1 --format=%s)" = "docs: consolidate S31 Docker parity plan"` | 0 | vazia |
| `test "$(git rev-list --count origin/main..HEAD)" = "8"` | 0 | vazia |
| `git rev-parse HEAD` | 0 | `5ca4d1164dbfb206114b02c0daf5da3487b90423` |
| `git status --short` | 0 | três técnicos modificados e este relatório não rastreado |
| `git diff --cached --name-only` | 0 | vazia |
| `git diff --check` | 0 | vazia |

Builder, imagens `s31`/BuildKit/frontend/Trivy, container e volumes nominais
estavam ausentes. O JSON S31 estava ausente. A base Node preexistente permanecia
em `sha256:56b2e0aba61d59409f91edd9a629b8315e0264a4d82fedfd64e7e632b3fd2aa6`.
`docker system df` registrou literalmente:

```text
TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE
Images          28        1         7.69GB    7.414GB (96%)
Containers      1         1         63B       0B (0%)
Local Volumes   19        1         1.081GB   1.032GB (95%)
Build Cache     0         0         0B        0B
```

### 11.3 Builder pinado recriado

Criação, exit 0, duração 0,065 s:

```text
docker buildx create --name s31-whatsapp-builder-10db6b3 --driver docker-container --driver-opt image=docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684 --driver-opt network=host --platform linux/amd64 --use
s31-whatsapp-builder-10db6b3
```

`docker buildx inspect --bootstrap` retornou exit 0 em 5,520 s, sem o
`context deadline exceeded` observado nas retomadas anteriores. Registrou
BuildKit `v0.31.2`, `Platforms: linux/amd64*` e o label
`org.mobyproject.buildkit.worker.network: host`.

Prova literal por `docker inspect`:

```text
/buildx_buildkit_s31-whatsapp-builder-10db6b30|docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684|sha256:e0e67ea9c4bb07bdef683e29f70f3b623d1eb9c82274a9ec04e6bb0511449fb0|host|running|true
```

Prova da imagem efetiva, com RepoDigest exclusivamente autorizado:

```text
sha256:e0e67ea9c4bb07bdef683e29f70f3b623d1eb9c82274a9ec04e6bb0511449fb0|["moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684"]|amd64|linux
```

`docker buildx inspect` confirmou
`Driver Options: image="docker.io/moby/buildkit@sha256:63db51c9…" network="host"`
e `Status: running`. O ID da imagem BuildKit coincide com o das duas retomadas
anteriores.

### 11.4 Build canônico refeito

Executado uma vez, sem retry:

```text
docker buildx build --builder s31-whatsapp-builder-10db6b3 --platform linux/amd64 --load --push=false --file whatsapp_service/Dockerfile --tag s31-whatsapp-runtime:local-10db6b3 --build-arg VCS_REF=10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a --build-arg IMAGE_VERSION=ci-10db6b3 whatsapp_service
```

Resultado: exit 0; duração 64,685 s. O frontend
`docker.io/docker/dockerfile@sha256:b5f3b260…` e a base
`docker.io/library/node:24.18.1-alpine3.24@sha256:f70403e8…` foram resolvidos
pelos digests fechados. `npm ci --omit=dev` registrou literalmente
`added 300 packages, and audited 301 packages in 12s` e `found 0 vulnerabilities`;
`apk add` terminou em `OK: 747.8 MiB in 197 packages`; a imagem foi exportada e
carregada localmente.

Metadados inspecionados, exit 0:

```text
sha256:48fad4ca931b493617efcb27be3d533ef54473c1b6e25788806f2df39835596e|amd64|linux|10001:10001|["node","index.js"]|10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a|ci-10db6b3
```

`/etc/alpine-release` na imagem: `3.24.1`.

O `imageId` difere do registrado na §10.4
(`sha256:3d123d8b615b247f04b6fb721c8f3ec7cfaf44c79f0b89eae736417a5e99024b`)
porque o build foi refeito: o exportador grava novo timestamp de criação e as
camadas `apk` e `npm ci` foram resolvidas em nova data. Dockerfile, contexto,
plataforma, digests e argumentos são idênticos. O `imageId` gravado no JSON é o
efetivamente medido pelo Trivy nesta execução.

Inspeção literal do filesystem da imagem construída, exit 0:

```text
/usr/local/lib/node_modules/npm|absent
/usr/local/lib/node_modules/corepack|absent
/opt/yarn-v1.22.22|absent
/usr/local/bin/npm|absent
/usr/local/bin/npx|absent
/usr/local/bin/corepack|absent
/usr/local/bin/yarn|absent
/usr/local/bin/yarnpkg|absent
node|/usr/local/bin/node|v24.18.1
npm|not-invocable
npx|not-invocable
corepack|not-invocable
yarn|not-invocable
yarnpkg|not-invocable
```

### 11.5 Trivy em rede host — zero achados

A imagem Trivy pinada foi baixada com exit 0 em 4,419 s, terminando em
`Digest: sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e`.
O volume `s31-trivy-cache-10db6b3` foi criado com exit 0.

Comando executado, uma única vez, idêntico ao da §10.5 acrescido de
`--network host`:

```text
docker run --rm --platform linux/amd64 --network host -v /var/run/docker.sock:/var/run/docker.sock -v s31-trivy-cache-10db6b3:/root/.cache/trivy -v <diretorio-evidencia-nominal>:/evidence aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e image --skip-version-check --cache-dir /root/.cache/trivy --db-repository mirror.gcr.io/aquasec/trivy-db:2 --format json --output /evidence/scan.json --severity HIGH,CRITICAL --ignore-unfixed=false --exit-code 1 s31-whatsapp-runtime:local-10db6b3
```

Resultado: **exit 0**; duração 12,763 s. Nenhuma tentativa adicional foi
necessária. Saída literal, sem as linhas de barra de progresso:

```text
INFO	[vulndb] Need to update DB
INFO	[vulndb] Downloading vulnerability DB...
INFO	[vulndb] Downloading artifact...	repo="mirror.gcr.io/aquasec/trivy-db:2"
103.27 MiB / 103.27 MiB [---------------------------------------] 100.00% 24.67 MiB p/s 4.4s
INFO	[vulndb] Artifact successfully downloaded	repo="mirror.gcr.io/aquasec/trivy-db:2"
INFO	[vuln] Vulnerability scanning is enabled
INFO	[secret] Secret scanning is enabled
INFO	Detected OS	family="alpine" version="3.24.1"
WARN	This OS version is not on the EOL list	family="alpine" version="3.24"
INFO	[alpine] Detecting vulnerabilities...	os_version="3.24" repository="3.24" pkg_num=197
INFO	Number of language-specific files	num=1
INFO	[node-pkg] Detecting vulnerabilities...
```

Resultados do `scan.json`, `SchemaVersion 2`, `CreatedAt`
`2026-08-02T07:05:18.318539895Z`, `Metadata.ImageID`
`sha256:48fad4ca931b493617efcb27be3d533ef54473c1b6e25788806f2df39835596e`:

| Target | Class | Type | Vulnerabilidades | Segredos | Misconfigurations |
|---|---|---|---:|---:|---:|
| `s31-whatsapp-runtime:local-10db6b3 (alpine 3.24.1)` | `os-pkgs` | `alpine` | 0 | 0 | 0 |
| `Node.js` | `lang-pkgs` | `node-pkg` | 0 | 0 | 0 |

Total de achados HIGH/CRITICAL: **zero**. Com `--exit-code 1` e
`--ignore-unfixed=false`, o exit 0 é a própria prova do gate.

Metadados do banco, lidos literalmente de `/cache/db/metadata.json` no volume
nominal antes do cleanup:

```text
{"Version":2,"NextUpdate":"2026-08-03T00:55:42.260375303Z","UpdatedAt":"2026-08-02T00:55:42.260375924Z","DownloadedAt":"2026-08-02T07:05:13.962606527Z"}
```

O banco Java não foi baixado: a imagem não contém artefatos Java e o Trivy não
inicializa `java-db` nesse caso. O volume nominal continha somente
`db/metadata.json`. Por isso `trivyDatabase.javaDb` é `null` no JSON, com a
chave presente para preservar o esquema da S30a.

**Delta do componente `whatsapp_service`:** a medição `after` da S30a registrava
um achado HIGH — `CVE-2026-14257`, `brace-expansion` 5.0.7, corrigido em 5.0.8,
com `packageOrigin=npm-runtime`. Após a purga, o componente vai de **1 HIGH para
0**, e a origem `npm-runtime` deixa de existir na imagem. O grupo B do inventário
da §2 da task está fechado.

### 11.6 Smoke funcional determinístico

Container nominal executado com `WHATSAPP_INITIALIZATION_DISABLED=true` e porta
livre do host `43683` mapeada em `127.0.0.1:43683 -> 3001`. `docker run -d`
retornou exit 0; o serviço respondeu 200 em `/health/live` após 2 s.

| Rota | HTTP | Corpo literal |
|---|---:|---|
| `/health/live` | 200 | `{"status":"UP"}` |
| `/status` | 200 | `{"connected":false,"hasQr":false}` |

`/status` satisfaz `connected=false` e `hasQr=false`. Logs literais do container:

```text
WhatsApp initialization disabled for local health validation
WhatsApp HTTP service listening on :3001
```

A primeira linha prova que `index.js:39` entrou no ramo desabilitado e que
`service.initialize()` não foi chamado: não houve Puppeteer, sessão real nem
tráfego externo de WhatsApp.

Prova de não invocabilidade dentro do container **em execução**:

```text
npm|exit=127|OCI runtime exec failed: exec failed: unable to start container process: exec: "npm": executable file not found in $PATH: unknown
npx|exit=127|OCI runtime exec failed: exec failed: unable to start container process: exec: "npx": executable file not found in $PATH: unknown
corepack|exit=127|OCI runtime exec failed: exec failed: unable to start container process: exec: "corepack": executable file not found in $PATH: unknown
yarn|exit=127|OCI runtime exec failed: exec failed: unable to start container process: exec: "yarn": executable file not found in $PATH: unknown
yarnpkg|exit=127|OCI runtime exec failed: exec failed: unable to start container process: exec: "yarnpkg": executable file not found in $PATH: unknown
node|v24.18.1|exit=0
```

Estado do container e identidade efetiva:

```text
healthy|running|10001:10001
process.getuid():process.getgid() = 10001:10001
```

O `HEALTHCHECK` declarado no Dockerfile passou por conta própria, com o container
em `healthy`, confirmando que `node -e fetch(...)` funciona sem gerenciador.

### 11.7 JSON de evidência e linhagem

Emitido
`docs/infrastructure/deployment/implementation/slices/S31-trivy-findings.whatsapp.after.json`,
com todas as chaves exigidas pela §4.5 da task presentes e `findings: []`.

Linhagem computada literalmente:

| Campo | Valor | Origem |
|---|---|---|
| `measurement` | `after` | §4.5 |
| `sourceSha` | `10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a` | SHA-base do preflight da §4.0, mantido pela correction-02 |
| `sourceState` | `working-tree-after-s31-remediation-before-evidence-files` | §4.5 |
| `sourceDiffSha256` | `adedb70bdd7650887801ba910eaff8309d5425a3d57126c1c3c8ecd68c0a59d7` | `git diff --binary 10db6b3… -- tools/docker/tests/test_validate_node_images.py tools/docker/validate_node_images.py whatsapp_service/Dockerfile \| sha256sum` |
| `sourceTreeSha` | `18502627415bd282debd45663f94e338e1e9bf73` | `git write-tree` em índice temporário, após `read-tree 10db6b3…` e `update-index --add` dos mesmos três caminhos |
| `measuredAtUtc` | `2026-08-02T07:05:18.318539895Z` | `CreatedAt` do `scan.json` |

Prova de que a árvore medida difere do commit-base exatamente nos três arquivos
de implementação, com JSON e relatório fora da árvore:

```text
git diff-tree -r --name-status a0e0dca7311c2d7e908bf0aaf7a6071ba1501917 18502627415bd282debd45663f94e338e1e9bf73
M	tools/docker/tests/test_validate_node_images.py
M	tools/docker/validate_node_images.py
M	whatsapp_service/Dockerfile
```

Os blobs da árvore medida coincidem com os do worktree:

```text
tools/docker/tests/test_validate_node_images.py|b5b694aef05d85c3e734d63a69a790d066a4f8d4
tools/docker/validate_node_images.py|e53b4ecfe6cdd570da3bd9020d4114412bec8fe6
whatsapp_service/Dockerfile|22b215cdc7a4f009f51902dca5478e9aef420779
```

Nota sobre `counts`: com `findings: []`, o agrupamento reprodutível sobre a
lista vazia não produz chaves. Para expressar os totais zerados exigidos pela
§4.5 sem inventar categorias, `total` é `0`, `bySeverity` e
`byComponent.whatsapp_service` trazem `CRITICAL: 0` e `HIGH: 0`, e
`byComponentPackage.whatsapp_service`, `byComponentOrigin.whatsapp_service` e
`byOrigin` ficam vazios. Todas as chaves do esquema da S30a estão presentes.

### 11.8 Cleanup nominal e estado final

Antes do cleanup, `docker buildx du --builder s31-whatsapp-builder-10db6b3`
registrou `Reclaimable: 2.386GB` e `Total: 2.386GB`, integralmente no builder
nominal. Foram executados, todos com exit 0:

```text
docker stop s31-whatsapp-smoke-10db6b3
docker rm s31-whatsapp-smoke-10db6b3
docker buildx rm s31-whatsapp-builder-10db6b3
docker image rm s31-whatsapp-runtime:local-10db6b3
docker volume rm s31-trivy-cache-10db6b3
docker image rm aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e
docker image rm docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
```

Nenhum prune foi executado. O `scan.json` bruto permaneceu em diretório temporário
fora do repositório e não integra o commit.

Prova final:

- builder, container BuildKit e container smoke nominais: ausentes;
- imagens BuildKit, frontend, runtime e Trivy: ausentes;
- volumes nominais: ausentes;
- base Node preexistente: preservada em
  `sha256:56b2e0aba61d59409f91edd9a629b8315e0264a4d82fedfd64e7e632b3fd2aa6`;
- `docker system df` idêntico ao preflight desta seção: 28 imagens, 1 container,
  19 volumes locais, `Build Cache 0B`.

Resíduos ignorados preexistentes preservados:

```text
tools/docker/__pycache__/java_images_contract.cpython-313.pyc
tools/docker/tests/__pycache__/test_java_images_contract.cpython-313.pyc
```

### 11.9 Acessos externos desta retomada

- `auth.docker.io` e `registry-1.docker.io`, exclusivamente para o BuildKit
  pinado, o frontend pinado, a base Node pinada e a imagem Trivy pinada;
- `dl-cdn.alpinelinux.org`, somente os repositórios configurados na base;
- `registry.npmjs.org`, somente recursos do lockfile versionado;
- `mirror.gcr.io/aquasec/trivy-db:2`, somente o banco de vulnerabilidades,
  desta vez concluído.

Não houve GHCR, `docker login`, `docker push`, publicação, workflow dispatch,
release, tag, deploy, rollback, SSH, produção nem tráfego ou sessão de WhatsApp.
Nenhum outro mirror, digest, DNS ou fallback foi usado.

### 11.10 Critérios de aceite da task

| # | Critério da §8 | Estado |
|---:|---|---|
| 1 | treze validadores e sete suítes exit 0 | atendido na §10.2; preservado por decisão do orquestrador, sem repetição |
| 2 | 26 mutantes rejeitados com erro exato e dois casos de aceitação sem erro | atendido; resultado literal dos 28 casos na Seção 4 |
| 3 | enumeração da base coincidente e imagem construída sem os oito caminhos, com `node` | atendido nas §3 e §11.4 |
| 4 | Trivy do componente com zero achados HIGH/CRITICAL e JSON no esquema da §4.5 | atendido na §11.5 e §11.7 |
| 5 | smoke com `/health/live` 200 e `/status` 200 `connected=false`/`hasQr=false`, sem gerenciador invocável | atendido na §11.6 |
| 6 | cleanup só dos artefatos criados, sem prune amplo | atendido na §11.8 |
| 7 | dois secret scans `clean`, `unsupported=0`, saídas idênticas | registrado na §11.11 |
| 8 | exatamente um commit local, sem push, tocando só os cinco caminhos da §5 | registrado na §11.11 |
| 9 | nenhum efeito em release, tag, deploy, rollback, WhatsApp ou produção | atendido na §11.9 |
| 10 | relatório conforme §10 da task | este documento |

Reafirmação da §2 da task: fechar o grupo B **não** deixa a CI verde. As quinze
ocorrências dos grupos A e C permanecem, `backend` e `website_back` continuam
retornando exit 1 no job `images`, e o push segue proibido. CI verde não é
critério de aceite desta slice.

Arquivos da fronteira da §5 tocados por esta execução:

- `whatsapp_service/Dockerfile` — herdado das §2 e §10.1, sem nova alteração;
- `tools/docker/validate_node_images.py` — herdado da §2, sem nova alteração;
- `tools/docker/tests/test_validate_node_images.py` — herdado da §2, sem nova alteração;
- `docs/infrastructure/deployment/implementation/slices/S31-trivy-findings.whatsapp.after.json` — criado nesta retomada;
- `docs/infrastructure/deployment/implementation/slices/S31-remocao-npm-runtime-whatsapp.report.md` — este relatório.

Nenhum arquivo fora da fronteira foi criado, alterado ou removido.

Mensagem exata do commit:
`fix: remove package managers from whatsapp runtime image`

### 11.11 Stage, secret scans e commit

Stage exclusivamente dos cinco caminhos da §5, exit 0:

```text
docs/infrastructure/deployment/implementation/slices/S31-remocao-npm-runtime-whatsapp.report.md
docs/infrastructure/deployment/implementation/slices/S31-trivy-findings.whatsapp.after.json
tools/docker/tests/test_validate_node_images.py
tools/docker/validate_node_images.py
whatsapp_service/Dockerfile
```

`git status --short` registrou exatamente `A` para os dois arquivos novos e `M`
para os três técnicos, sem nenhuma outra entrada.

Primeira execução de `python3 tools/ci/secret_scan.py --tracked`, exit 0, saída
literal:

```text
secret-scan:clean:scanned=2446:allowed=208:unsupported=0:history_scanned=29244
```

O relatório foi então atualizado com esta seção, feito stage novamente, e o mesmo
comando repetido. Segunda execução, exit 0, saída literal:

```text
secret-scan:clean:scanned=2446:allowed=208:unsupported=0:history_scanned=29244
```

As duas saídas são idênticas, `clean` e com `unsupported=0`. A ordem exigida pela
§7 foi respeitada: os dois arquivos novos só entraram na varredura depois do
stage, porque `secret_scan.py --tracked` usa `git ls-files`.

A primeira passagem por `git diff --cached --check` não retornou 0. Saída
literal:

```text
docs/infrastructure/deployment/implementation/slices/S31-remocao-npm-runtime-whatsapp.report.md:1087: new blank line at EOF.
```

A linha em branco terminal foi herdada da versão não rastreada deste relatório e
nunca havia sido exposta ao gate, porque `git diff --check` não inspeciona
arquivos não rastreados. A §10 da task exige que o relatório termine exatamente
na linha `IN_PROGRESS`. O byte excedente foi removido, sem qualquer outra
alteração de conteúdo. Como isso mudou o relatório depois da segunda varredura,
o arquivo foi staged de novo e o mesmo secret scan foi executado uma terceira
vez, exit 0, saída literal:

```text
secret-scan:clean:scanned=2446:allowed=208:unsupported=0:history_scanned=29244
```

As três execuções são idênticas entre si, e o conteúdo efetivamente commitado é
exatamente o varrido pela última delas. `git diff --cached --check` foi então
repetido e retornou exit 0 com saída vazia. A lista staged foi reconfirmada nos
mesmos cinco caminhos imediatamente antes do commit.

O commit local único foi criado com a mensagem contratual, sem `--no-verify`,
sem force, sem tag, sem outra branch, sem outro remote e sem commit adicional.
`git diff --check origin/main..HEAD` retornou exit 0. **Não houve push.**

commit final = HEAD entregue

IN_PROGRESS — aguardando revisão do orquestrador
