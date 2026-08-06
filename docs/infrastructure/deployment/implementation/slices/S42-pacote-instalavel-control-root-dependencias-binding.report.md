# S42 — Pacote instalável do control root, dependências isoladas e binding do SHA

> **Data:** 04/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Executor:** CLI
> **Contrato:** `S42-pacote-instalavel-control-root-dependencias-binding.task.md`
> **SHA-256 da task:** `5a3081edcea01b821fe6e4c7df590590a8e3213bee42ebaa48546423cbd652e6`
> **Resultado:** `IN_PROGRESS — pacote do control root versionado e validado; aguardando aceite e instalação na VPS`

## 1. Autorização humana

A delegação continha literalmente a frase exigida pela §3:

> "Autorizo integralmente a S42, incluindo implementação do pacote instalável do
> control root, um único commit causal, um único push fast-forward e observação
> de CI e Publish Candidate. Não autorizo acesso ou mutação da VPS, deploy ou
> rollback."

Não autoriza acesso/mutação da VPS, instalação do pacote nela, deploy ou
rollback. Nenhuma dessas ações foi executada.

## 2. Snapshot inicial

| Item | Exigido | Observado |
|---|---|---|
| `HEAD` inicial | `1a8fca00d3b866a4eefa2e7d67c1fc0beccd73c9` | idêntico |
| `origin/main` inicial | `daaa7061ab9f7a722b17e37c0f060f45141225e7` | idêntico |
| divergência inicial | ahead 3 / behind 0 | idêntico |
| SHA-256 da task | `5a3081ed...4d652e6` | idêntico |
| `git diff --check` | 0 | 0 |

## 3. Causa técnica reproduzida por teste

A VPS roda Python 3.10.12 com `jsonschema` 3.2.0 (pacote global do Ubuntu
22.04). Reproduzido localmente em venv descartável (rede permitida apenas
neste venv de teste, nunca instalado globalmente na workstation):

```text
jsonschema version: 3.2.0
schema: {"$schema": ".../2020-12/schema", "properties": {"pair": {"type": "array",
         "prefixItems": [{"type": "string"}, {"type": "integer"}]}}, ...}
mutante: {"pair": [1, "a"], ...}   # ordem invertida, viola prefixItems
resultado: jsonschema.validate() não levanta exceção
CONFIRMED BUG: jsonschema 3.2.0 silently ACCEPTED a prefixItems-type-violating array
```

Causa raiz: 3.2.0 não conhece a palavra-chave `prefixItems` (adicionada em
Draft 2020-12); por ser desconhecida, é ignorada — o array passa sem qualquer
verificação posicional. O mesmo schema, validado pelo `jsonschema` 4.23.0
vendorizado por este pacote, dentro do container isolado Python 3.10
linux/amd64 (§7), rejeita corretamente o mesmo mutante e mais dois
(`items:false` com item extra, `$defs` violado) — ver §7, passo 6.

## 4. Fechamento de arquivos, imports e lock

`tools/deploy/control_root_package.py` resolve o conteúdo do pacote
exclusivamente via `git cat-file`/`git ls-tree` a partir de um `sourceSha`,
nunca da working tree. Conjunto fechado (`SOURCE_FILES` + glob de schemas em
`ops/deploy/schemas` e `ops/releases`, excluindo `/examples/` e `/tests/`):
13 arquivos de código fixos + 17 schemas/catálogos JSON/YAML + o próprio lock.
O pacote final do commit `9731954d474fb68ec1384a525e1075f9a5542e24` contém
188 entradas de arquivo no manifesto.

Lock (`ops/deploy/control-root/requirements.lock`), 7 wheels pinados e
hashados, `cp310`/`py3-none-any`/`manylinux2014_x86_64` apenas, sem sdist:

```text
attrs==26.1.0                    sha256=c647aa4a...  py3-none-any
jsonschema==4.23.0                sha256=fbadb6f8...  py3-none-any
jsonschema_specifications==2025.9.1 sha256=98802fee...  py3-none-any
PyYAML==6.0.2                     sha256=ec031d5d...  cp310-manylinux2014_x86_64
referencing==0.37.0               sha256=381329a9...  py3-none-any
rpds_py==0.30.0                   sha256=0c0e95f6...  cp310-manylinux2014_x86_64
typing_extensions==4.15.0         sha256=f0fa19c6...  py3-none-any
```

`typing_extensions` foi incluído deliberadamente: `referencing>=0.31.0` o exige
em tempo de execução sob Python < 3.13 (`python_version < '3.13'`), e um
`pip download` ingênuo o omite quando resolvido a partir de um interpretador
≥3.13 na workstation.

**Bug real encontrado e corrigido nesta slice**: vendorizar o runtime não tinha
efeito algum. Nem `ops/deploy/deployment-remote.py` nem
`tools/deploy/deployment_cli.py` (processo próprio, exec'd por
`deploy-release.sh`) inseriam `CONTROL_ROOT/vendor` no `sys.path` antes de
importar módulos que fazem `import jsonschema`; todo `import jsonschema`
continuaria resolvendo para o 3.2.0 global. Corrigido inserindo
`sys.path.insert(0, str(ROOT / "vendor"))` como primeira entrada em ambos os
pontos de entrada, antes de qualquer import que toque `jsonschema`/`yaml`.
`validate_control_root_package.py` agora verifica estaticamente essa ordem
(`helper-does-not-prioritise-vendor`, `cli-vendor-inserted-after-import`).

**Segundo bug real encontrado e corrigido**: o builder usava
`from datetime import UTC, datetime` — `datetime.UTC` só existe a partir do
Python 3.11. Como o instalador roda sob o Python 3.10 exato do alvo, importar
`control_root_package.py` no container Python 3.10 falhava com `ImportError`
antes de qualquer outra coisa. Corrigido para `from datetime import datetime,
timezone` / `timezone.utc`.

## 5. Manifesto, sidecars e determinismo

Manifesto (`control-root.manifest.json`) fechado por chaves exatas
(`schemaVersion, kind, repository, sourceSha, platform, pythonAbi,
requirementsSha256, files, createdAt`), `createdAt` derivado do commit
(`git show -s --format=%cI`, nunca do relógio de build), arquivos ordenados
por path posix, nenhum modo com bit de grupo/outro. Sidecar
`control-root.manifest.json.sha256` protege o próprio manifesto.

Dois builds independentes do mesmo `sourceSha`, em diretórios de saída
distintos, produziram tar **byte-idêntico**:

```text
sourceSha 9731954d474fb68ec1384a525e1075f9a5542e24 (HEAD real, pós-commit)
build A:  control-root-package:built:9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0
build B:  control-root-package:built:9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0
cmp:      BYTE-IDENTICAL / SIDECAR-IDENTICAL
manifest.sourceSha == 9731954d474fb68ec1384a525e1075f9a5542e24  (confirmado)
```

## 6. Testes causais

`tools/deploy/tests/test_control_root_package.py`, 16 categorias (T01–T16) +
contrato estático:

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools.deploy.tests.test_control_root_package -v
Ran 33 tests in 0.127s
OK
```

Cobertura: SHA ausente/inválido/não-commit/divergente; conteúdo vindo do
commit, não da working tree; allowlist fechada (arquivo ausente, extra,
`/tests/`/`/examples/` excluídos); archive com traversal, symlink, hardlink,
device, FIFO, modo inseguro; wheel sem hash/ABI errada/sdist/duplicada; lock
adulterado (digest de wheel e wheel ausente); manifesto não canônico ou
mutado em cada campo, arquivo alterado detectado por `verify_tree`;
serialização canônica estável; host errado (não-root, Python≠3.10,
arquitetura≠x86_64, usuário `deploy-emporio` ausente); target não-vazio,
modo errado, dono errado; **falha de extração deixando target vazio e sem
staging** (categoria 11, sidecar interno do arquivo corrompido); binding do
`controlSha` (manifesto ausente, sidecar inválido, bytes adulterados,
SHA divergente); transporte recusando **antes** de qualquer mutação (SHA
divergente e SHA malformado nunca alcançam o host — `runner.calls == []`);
Draft 2020-12 com `prefixItems`/`$defs` rejeitando 3 mutantes concretos;
capabilities só verde com `controlSha` exato; ausência de qualquer menção a
Docker/SSH/GHCR/IP da VPS no módulo (checagem por regex com fronteira de
palavra, para não confundir com `requirementsSha256`).

Suíte de transporte após a mudança de assinatura `capabilities(self,
control_sha)`:

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools.deploy.tests.test_control_root_package tools.deploy.tests.test_deployment_transport
Ran 92 tests ... OK
```

## 7. Prova em runtime isolado (Python 3.10, linux/amd64, rede bloqueada)

Base pinada por digest, sem build de imagem própria e sem push:

```text
image: python@sha256:9643927a6fc74bd81b0f1bbb5cce3cb4a491f46b4c5dbee770f28e575f180015
       (python:3.10-slim-bookworm), arch amd64, python 3.10.20, x86_64
docker run --rm --network none --platform linux/amd64 ...
```

Executado duas vezes: uma vez sobre um commit de fixture pré-commit (prova de
viabilidade), e a prova final, repetida integralmente sobre o `sourceSha`
real `9731954d474fb68ec1384a525e1075f9a5542e24` depois do commit único.
Passos e resultado da prova final:

```text
0. base sem jsonschema/PyYAML globais                         PASS/PASS
1. usuário/grupo deploy-emporio + cadeia de dono 0700           PASS
2. install (root, arquivo local, sem rede)                      control-root-package:installed:9731954d474fb68ec1384a525e1075f9a5542e24
3. dono/modo de toda a árvore instalada                         PASS
4. verify (usuário não-root)                                    control-root-package:verified:9731954d474fb68ec1384a525e1075f9a5542e24
5. imports vindos do vendor (jsonschema 4.23.0, yaml)            PASS — /opt/.../control/vendor/jsonschema/__init__.py
6. prefixItems + $defs: 3 mutantes rejeitados (Draft 2020-12)    PASS
7. capabilities real, end-to-end, não-root                      {"controlSha":"9731954...","deployRoot":"/opt/sistemas/emporio",
                                                                   "protocol":"emporio-deployment-transport","schemaVersion":1,
                                                                   "user":"deploy-emporio"}
8. adulteração isolada: helper, CLI, schema, Compose, vendor     5/5 PASS (verify rejeita cada um)
9. controlSha binding rejeita cópia adulterada antes de retornar PASS — REMOTE_CONTROL_TAMPERED
10. manifesto ausente é recusado                                 PASS
11. segunda instalação em target não-vazio é recusada             PASS
RESULT: ALL ISOLATED PROOFS PASSED
```

Cleanup dirigido: `docker run --rm`, sem imagem própria construída, sem
volume ou rede criados. Confirmado por diferença de conjunto antes/depois:
zero containers residuais (`baronesa-postgres` preexistente, não tocado),
zero volumes novos, zero redes novas, zero imagens novas além do pull da base
pública já esperado.

## 8. Commit, prova do SHA e push

Gates pré-commit verdes (§10 completo — ver §9). Staged exclusivamente os 8
arquivos permitidos pela fronteira §7 do contrato; os três relatórios
não-rastreados (S39/S40/S41) permaneceram fora do stage.

```text
secret scan (staged, 8 arquivos):  secret-scan:clean:scanned=8:allowed=0:unsupported=0
commit:                            9731954d474fb68ec1384a525e1075f9a5542e24 (main)
                                    "feat: vendor an isolated control-root runtime and bind it to the deployed commit"
                                    8 files changed, 1381 insertions(+), 8 deletions(-), sem amend
build duplo do novo HEAD:          byte-idêntico, sourceSha == commit (ver §5)
prova isolada final:               repetida integralmente sobre o novo HEAD (ver §7) — PASS
origin/main antes do push:         daaa7061ab9f7a722b17e37c0f060f45141225e7 (reconfirmado, sem divergência)
push:                              daaa706..9731954  main -> main  (fast-forward único)
```

## 9. Matriz local completa

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests     -> Ran 117 tests, OK
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests         -> Ran 31 tests, OK
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -> Ran 75 tests, OK
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests   -> OK
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests     -> Ran 423 tests, OK
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/security/tests   -> Ran 26 tests, OK
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests    -> Ran 4 tests, OK
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/gateway/tests    -> Ran 4 tests, OK

python3 tools/deploy/validate_control_root_package.py     -> control-root-package-contract:valid
python3 tools/deploy/validate_deploy_workflow.py           -> deploy-workflow-contract: ok
python3 tools/deploy/validate_production_adapter.py        -> production-adapter-contract:valid
python3 tools/deploy/validate_deployer_runtime.py           -> deployer-runtime:valid
python3 tools/deploy/validate_deployment_executor.py        -> deployment-executor-contract:valid
python3 tools/deploy/validate_deployment_plan.py            -> deployment-plan-contract:valid
python3 tools/deploy/validate_deployer_identity_bridge.py    -> deployer-identity:valid
python3 tools/deploy/validate_release_control_package.py    -> release-control-package:valid
python3 tools/deploy/validate_release_control_workflow.py   -> release-control-workflow:valid
python3 tools/deploy/validate_rollback_contract.py           -> rollback-contract:valid
python3 tools/deploy/validate_rollback_runtime.py             -> rollback-runtime:valid
python3 tools/ci/validate_ci.py                              -> ci:valid
python3 tools/ci/validate_workflow_inventory.py               -> workflow-inventory:valid
python3 tools/candidates/validate_candidate_workflow.py      -> candidate-workflow:valid
python3 tools/compose/validate_compose.py                    -> Compose contract valid
python3 tools/docker/validate_node_images.py validate         -> node-images-contract:valid
python3 tools/gateway/validate_gateway.py                     -> Gateway contract valid
python3 tools/releases/validate_candidate_manifest.py         -> candidate:valid
python3 tools/releases/validate_publisher_identity_bridge.py  -> publisher-identity-bridge:valid
python3 tools/releases/validate_publisher_runtime.py          -> publisher-runtime:valid
python3 tools/releases/validate_publisher_ui.py                -> publisher-ui:valid
python3 tools/releases/validate_release_workflow.py            -> release-workflow:valid
python3 tools/releases/catalog.py validate                    -> catalog:valid

python3 tools/ci/secret_scan.py --tracked
  -> secret-scan:clean:scanned=2493:allowed=864:unsupported=0:history_scanned=130326

git diff --check          -> 0
git diff --cached --check -> 0
```

Todos os exits `0`, nenhum skip, nenhuma falha nova, secret scan `clean` com
`unsupported=0` tanto sobre a árvore completa quanto sobre o diff staged.

## 10. Runs remotos e artifacts

```text
CI               https://github.com/greggorio/abaronesa-emporio/actions/runs/30902014368
                 sha 9731954d474fb68ec1384a525e1075f9a5542e24 — success (13/13 jobs)
Publish Candidate https://github.com/greggorio/abaronesa-emporio/actions/runs/30902729166
                 sha 9731954d474fb68ec1384a525e1075f9a5542e24 — success (11/11 jobs:
                 trust, predecessor, 6× build, assemble, integrated, publish)
```

`gh run list --workflow deploy-production.yml` e `--workflow
rollback-production.yml`: `[]` em ambos — nenhuma execução, antes ou depois
desta slice.

## 11. Pacote temporário: hashes e cleanup

Dois pacotes locais foram construídos durante esta slice, ambos prova
temporária, nunca artifact GitHub nem release:

```text
fixture pré-commit (131eb9e882872978d6d4a9b8f2fd4882dc888155):
  control-root-package:built:971635c636f16fcab4403b416e4fbf9c347206c4c4843b6a8fbf15a06fcd395f

commit real (9731954d474fb68ec1384a525e1075f9a5542e24), prova final:
  control-root-package:built:9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0
```

Removidos de forma dirigida após o registro acima: os diretórios de build
(`s42-out-a`, `s42-out-b`, `s42-final-a`, `s42-final-b`, `s42-workspace`,
extrações de vendor/manifesto) e o venv descartável de reprodução do
jsonschema 3.2.0, todos sob o scratchpad de sessão — nenhum sob o
repositório, nenhum commitado, nenhum publicado.

## 12. Zero VPS, deploy e rollback

Nenhum comando SSH, `docker` remoto ou leitura/escrita na VPS foi executado
nesta slice. Nenhum workflow de deploy ou rollback foi disparado (§10). O
pacote não foi instalado em lugar algum fora dos containers descartáveis do
§7. Nenhum environment, secret, var, App ou allowlist foi criado ou alterado.

## 13. Resíduos finais

```text
git status --short  -> apenas os três relatórios não-rastreados (S39, S40, S41)
                        e este relatório (S42), todos fora do stage
docker ps -a         -> nenhum container órfão (apenas baronesa-postgres, preexistente)
docker volume ls      -> conjunto idêntico ao inicial
docker network ls     -> conjunto idêntico ao inicial
docker images         -> nenhuma imagem nova; apenas o pull esperado da base pública
```

O executor não aceitou a S42, não instalou o pacote em lugar algum e não
criou a próxima slice.

IN_PROGRESS — pacote do control root versionado e validado; aguardando aceite e instalação na VPS
