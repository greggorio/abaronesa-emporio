# S29 — Gate do primeiro commit e checklist do primeiro push

## 1. Escopo e autoridade

- CWD: `/home/gregorio/git/baronesa/emporio`.
- Task: `docs/infrastructure/deployment/implementation/slices/S29-gate-primeiro-commit-ativacao-remota.task.md`.
- S01–S28 foram lidas conforme a entrada da task e permanecem fora da
  fronteira de alteração.
- Nenhum commit, tag, push, remote write, workflow remoto ou acesso externo
  foi executado.
- Este relatório registra o gate; não declara aceite da S29.

## 2. Arquivos da fronteira

### Alterados

- `.github/workflows/README.md`: inventário corrigido de quatro para cinco e
  documentação do rollback manual/read-only.
- `.github/workflows/rollback-production.yml`: somente
  `actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd` foi alterado.
- `tools/releases/validate_release_workflow.py`: `EXPECTED` agora contém os
  cinco workflows ativos.

### Criados

- `tools/ci/validate_workflow_inventory.py`;
- `tools/ci/tests/test_validate_workflow_inventory.py`;
- este relatório.

### Não alterados

- `ci.yml`, `publish-candidate.yml`, `publish-release.yml` e
  `deploy-production.yml`;
- `.gitignore`, runtime, backend, frontend, Docker, Compose, Nginx, env,
  secrets e contratos;
- `tools/ci/secret_scan.py`, allowlist de segredos, validadores de candidato,
  deploy e rollback;
- task S29, tracker, handoff, arquitetura e S01–S28;
- nenhum workflow novo e nenhum arquivo fora da fronteira autorizada.

## 3. Gate de inventário e pinagem

O inventário ativo da raiz é exatamente:

```text
ci.yml
deploy-production.yml
publish-candidate.yml
publish-release.yml
rollback-production.yml
```

`README.md` de workflows agora declara cinco workflows e registra que a CI e
os workflows remotos ainda não foram executados. O conjunto contém 73 usos de
actions, todos com SHA hexadecimal de 40 caracteres. Os dez pins distintos
observados são:

```text
actions/attest-build-provenance@977bb373ede98d70efdf65b84cb5f73e068dcc2a
actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd
actions/download-artifact@634f93cb2916e3fdff6788551b99b062d0335ce0
actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95
actions/setup-node@395ad3262231945c25e8478fd5baf05154b1d79f
actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02
aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25
docker/build-push-action@d08e5c354a6adb9ed34480a06d141179aa583294
docker/login-action@5e57cd118135c172c3672efd75eb46360885c0ef
docker/setup-buildx-action@4d04d5d9486b7bd6fa91e7baf45bbb4f8b9deedd
```

`rollback-production.yml` permanece com um único job `protocol`, somente
`workflow_dispatch`, inputs obrigatórios `operation_id` e `release`,
`contents: read`, concorrência `emporio-production` sem cancelamento e sem
Docker, SSH, publicação ou segredo.

## 4. Candidato e resíduos

A lista candidata foi materializada somente em fluxo de memória a partir de:

```text
git ls-files --cached --others --exclude-standard -z
```

Para tornar o hash estável dentro do próprio relatório, o snapshot exclui
somente o caminho deste relatório, que ainda não existia quando a lista foi
capturada. Resultado da lista NUL-terminated:

```text
candidate-count=2424
candidate-list-sha256=21f2e5fd4b9c74ffa1930b93d1275dcba12efa830fcbd2582e7a29bac6c99bf4
```

O `.gitignore` já exclui `.env`, variantes, `ops/env/.env.production`,
`node_modules`, `.venv`, caches, coverage, bytecode, uploads e diagnósticos.
A S29 não ampliou nem alterou o `.gitignore`; o scanner e o filtro Git
continuam impedindo que os resíduos locais entrem no candidato.

A busca de resíduos encontrou, sem remoção:

```text
./frontend/.env
./release_control/.pytest_cache
./.pytest_cache
./ops/env/.env.production
./website_front/.env
```

Os ambientes e caches estão fora do candidato por exclusão padrão e foram
preservados conforme a task.

## 5. Validador e mutantes causais

`tools/ci/validate_workflow_inventory.py` verifica, sem rede:

- conjunto exato dos cinco YAML;
- parsing estrutural e todos os `uses:` fixados por SHA de 40 hexadecimais;
- rollback manual, inputs, permissões read-only, concorrência e ausência de
  efeitos externos;
- README com cinco workflows, rollback manual e ausência de afirmação de
  execução remota;
- `EXPECTED` do validador de release igual ao inventário.

Os testes exercitam o baseline e oito mutantes prescritos:

1. workflow extra — rejeitado;
2. action por tag — rejeitado;
3. rollback com `push` — rejeitado;
4. rollback com permissão de escrita — rejeitado;
5. README declarando quatro workflows — rejeitado;
6. `EXPECTED` do release sem rollback — rejeitado;
7. Docker introduzido no rollback — rejeitado;
8. README alegando execução remota — rejeitado.

Resultado literal:

```text
workflow-inventory:valid
.........                                                                [100%]
9 passed in 0.36s
```

## 6. Efeito previsto do primeiro push

Sem acessar serviços remotos, o efeito contratual documentado é:

1. primeiro push em `main` pode disparar `ci.yml`;
2. CI verde em `main` pode liberar `publish-candidate.yml` por
   `workflow_run`;
3. `publish-release.yml`, `deploy-production.yml` e
   `rollback-production.yml` permanecem manuais por `workflow_dispatch`;
4. permissões, variables, secrets, environments, branch protection e
   configurações efetivas do GitHub ainda exigem autorização/verificação
   humana e não foram inventadas nem consultadas.

O primeiro push e qualquer workflow remoto ainda não foram executados.

## 7. Matriz terminal

Todos os comandos foram executados a partir de
`/home/gregorio/git/baronesa/emporio`.

| Comando | Exit | Duração | Saída literal relevante | Interpretação |
|---|---:|---:|---|---|
| `python3 tools/ci/validate_workflow_inventory.py` | 0 | 0,120 s | `workflow-inventory:valid` | inventário, pinagem, rollback e README válidos |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q tools/ci/tests/test_validate_workflow_inventory.py` | 0 | 0,972 s | `9 passed in 0.34s` | baseline + oito mutantes verdes |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | 0,107 s | `candidate-workflow:valid` | contrato candidato válido |
| `python3 tools/releases/validate_release_workflow.py` | 0 | 0,170 s | `release-workflow:valid` | contrato release e EXPECTED de cinco válidos |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | 0,169 s | `deploy-workflow-contract: ok` | workflow deploy preservado |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | 0,105 s | `rollback-contract:valid` | contrato rollback preservado |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | 0,085 s | `rollback-runtime:valid` | runtime rollback preservado |
| `python3 tools/releases/release_control_contract.py validate` | 0 | 0,155 s | `release-control-contract:valid` | contrato release-control preservado |
| `git ls-files --cached --others --exclude-standard -z \| xargs -0 python3 tools/ci/secret_scan.py` | 123 | 2,129 s | `secret-scan:failed:scanned=1931:allowed=13:unsupported=0:history_scanned=0`; segunda parcela: `secret-scan:clean:scanned=493:allowed=6:unsupported=0:history_scanned=0` | divergência: fingerprints não allowlisted em artefatos fora da fronteira; nenhum valor impresso |
| `git ls-files --stage` | 0 | 0,052 s | saída vazia | índice real vazio |
| `git status --short` | 0 | 0,061 s | diretórios do workspace aparecem `??` | workspace pré-commit, sem arquivos staged |
| `git rev-parse --show-toplevel` | 0 | 0,052 s | `/home/gregorio/git/baronesa/emporio` | raiz Git local reconhecida |
| `git rev-parse --verify HEAD` | 128 | 0,060 s | `fatal: Needed a single revision` | sem HEAD/commit |
| `git tag --list` | 0 | 0,052 s | saída vazia | sem tags |
| `git reflog show --all` | 0 | 0,052 s | saída vazia | sem reflog |
| `git diff --check` | 0 | 0,051 s | saída vazia | whitespace limpo |
| `find .github/workflows -maxdepth 1 -type f -printf '%f\\n' \| sort` | 0 | 0,052 s | README.md + cinco YAML | inventário físico confirma cinco workflows |
| matriz de resíduos prescrita | 0 | 0,255 s | cinco caminhos listados na Seção 4 | resíduos preservados, não candidatos |
| `git ls-files --cached --others --exclude-standard -z \| python3 -c ...` (excluindo somente este relatório) | 0 | 0,073 s | `candidate-count=2424`; `candidate-list-sha256=21f2e5fd4b9c74ffa1930b93d1275dcba12efa830fcbd2582e7a29bac6c99bf4` | snapshot reproduzível do candidato versionável |

O scanner foi executado offline. O exit 123 é a agregação do `xargs` porque a
primeira parcela encontrou fingerprints; a saída permaneceu sanitizada. Os
paths/fingerprints reportados foram:

```text
PRIVATE_KEY:backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityConfiguration.java:645a3fd1fbded7d1
PRIVATE_KEY:backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/DeployerReleaseControlIdentityConfiguration.java:45b857fbbf8f5cff
PRIVATE_KEY:backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityContractTest.java:adf9d660c6ecea59
PRIVATE_KEY:backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityContractTest.java:ece92808270e9fba
PRIVATE_KEY:docs/infrastructure/deployment/implementation/slices/S16-ponte-identidade-rs256-jwks-perfil-local-publisher.task.md:697fe882c1911841
PRIVATE_KEY:tools/releases/validate_publisher_identity_bridge.py:d5c0e5f19884c2e6
SENSITIVE_ASSIGNMENT:ops/compose/release-control.yml:79e04ba7492befd4
SENSITIVE_ASSIGNMENT:release_control/.env.example:e80c875335067302
```

`actionlint` não está instalado (`actionlint: unavailable`); não foi
instalado nem substituído por acesso à rede.

## 8. Git, workflows e acessos

Não houve `git add`, commit, tag, push, `git init`, alteração de remote,
`git ls-remote`, `gh`, `curl`, workflow remoto, GitHub, GHCR, SSH, VPS,
DNS, Docker, containers, Postgres, produção, npm, instalação ou leitura de
segredo real. O único scanner usou a listagem local do Git e exibiu apenas
fingerprints.

O índice, HEAD, tags e reflog permanecem vazios. Nenhum workflow além do
checkout do rollback foi alterado; não foi criado workflow novo. O `.gitignore`
e os resíduos locais permanecem exatamente como encontrados.

## 9. Divergências restantes

1. O scanner canônico falhou com exit 123 por oito fingerprints sanitizados,
   não allowlisted, em configurações/testes/documentação de identidade já
   existentes e no Compose/exemplo do `release_control`. A correção exigiria
   alterar arquivos S16/S23/S28 ou `tools/ci/secret-allowlist.json`, todos
   fora da fronteira S29; por isso nenhum foi alterado.
2. `actionlint` está indisponível no ambiente; o validador offline estrutural
   passou e nenhuma instalação foi tentada.

Não há outra divergência na implementação autorizada da S29.

IN_PROGRESS — aguardando revisão do orquestrador

## 12. Revisão terminal do orquestrador — aceite

**Veredito: `ACCEPTED` — 31/07/2026.**

A correction-01 foi revisada e reproduzida pelo orquestrador. O scanner
canônico terminou com exit 0, `secret-scan:clean` e `unsupported=0`; os testes
causais passaram em unittest e pytest; e os validadores de workflow, release,
candidate, deploy, rollback e release-control permaneceram verdes.

Os invariantes do workspace também foram preservados: índice vazio, `HEAD`
ausente com exit 128, tags e reflog vazios, `git diff --check` limpo e nenhum
acesso externo, segredo real ou alteração remota. A indisponibilidade de
`actionlint` permanece registrada como limitação ambiental, sem ser a causa
de rejeição da correction.

S29 está aceita. A próxima slice é S30, criada e delegada no mesmo ciclo pelo
orquestrador. A fase remota de S30 exige autorização externa explícita.

## 10. Revisão terminal do orquestrador — rejeição e correction-01

**Veredito: `REJECTED` — 31/07/2026.**

Os contratos e workflows autorizados passaram, mas o gate principal do primeiro
commit não passou: o scanner canônico terminou em exit 123 com oito fingerprints
não allowlisted. A revisão confirmou que seis são marcadores PEM sintéticos de
contrato/teste/documentação, um é um placeholder do `.env.example` e um é um
falso positivo do scanner ao atravessar a linha YAML `secrets:`.

`actionlint` indisponível é limitação ambiental registrada, mas não é o motivo
da rejeição. O scanner falho impede declarar o primeiro commit seguro. S29
permanece `IN_PROGRESS`; não criar S30.

A correção autorizada está em
[S29-gate-primeiro-commit-ativacao-remota.correction-01.md](./S29-gate-primeiro-commit-ativacao-remota.correction-01.md).

## 11. Execução da correction-01 — 31/07/2026

### 11.1 Fronteira e arquivos

Execução realizada em `/home/gregorio/git/baronesa/emporio`, exclusivamente
nos quatro caminhos autorizados pela correction-01.

- Criados: nenhum.
- Alterados: `tools/ci/secret_scan.py`;
  `tools/ci/secret-allowlist.json`; `tools/ci/tests/test_ci.py`; este
  relatório.
- Não alterados: task S29, correction-01, tracker, S01–S28, workflows,
  contratos, `.gitignore`, runtime, backend, frontend, publisher, Docker,
  produção e qualquer outro arquivo fora da fronteira.

### 11.2 Implementação e provas causais

- `SENSITIVE_ASSIGNMENT` passou a usar `[ \t]*` em todos os espaços
  estruturais, impedindo que uma chave de mapa YAML e a linha seguinte sejam
  combinadas. O caso `secrets:` seguido de `external_key:` não gera finding.
- `PLACEHOLDERS` contém somente `replace-with`, `__set_` e
  `from-secret-manager`, comparados em forma minúscula. Cada marcador foi
  exercitado separadamente; remover qualquer um faz o teste causal falhar.
- A sublista `PRIVATE_KEY` da allowlist contém exatamente os seis pares
  `path/fingerprint` registrados na rejeição, cada um com justificativa
  individual de marcador sintético/contratual sem material de chave. A antiga
  entrada do header incompleto de teste foi removida; o teste agora monta o
  header em fragmentos, sem vazar um marker PEM literal para o scanner
  canônico.
- A detecção de assignment real continua exigindo valor não-placeholder. Os
  testes verificam também que valores capturados nunca aparecem nas linhas de
  findings.
- A igualdade da allowlist foi provada para a tupla completa de regra, path e
  fingerprint. Mutantes com entrada removida, path alterado ou fingerprint
  alterado não são aceitos.

Testes causais executados: 13 testes unittest, 13 testes pytest e 21
subtestes. A suíte matou os mutantes de `\s*` atravessando newline, remoção
individual dos três markers, assignment não-placeholder, remoção de entrada
da allowlist, alteração de path/fingerprint e vazamento de valor.

### 11.3 Matriz terminal da correction-01

Todos os comandos abaixo foram executados com CWD
`/home/gregorio/git/baronesa/emporio`.

| Comando | Exit | Duração | Contagem/saída literal relevante | Interpretação |
|---|---:|---:|---|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/ci/tests/test_ci.py -v` | 0 | 0,145731 s | `Ran 13 tests in 0.154s`; `OK` | testes causais e suíte offline aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q tools/ci/tests/test_ci.py` | 0 | 0,551654 s | `13 passed, 21 subtests passed in 0.19s` | execução pytest aprovada |
| `git ls-files --cached --others --exclude-standard -z \| xargs -0 python3 tools/ci/secret_scan.py` | 0 | 1,842919 s | `secret-scan:clean:scanned=1931:allowed=10:unsupported=0:history_scanned=0` e `secret-scan:clean:scanned=494:allowed=6:unsupported=0:history_scanned=0` | scanner canônico limpo, sem unsupported e sem valores expostos |
| `python3 tools/ci/validate_workflow_inventory.py` | 0 | 0,000004 s | `workflow-inventory:valid` | inventário dos cinco workflows válido |
| `python3 tools/releases/validate_release_workflow.py` | 0 | 0,000002 s | `release-workflow:valid` | release válido |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | 0,000003 s | `candidate-workflow:valid` | candidato válido |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | 0,000003 s | `deploy-workflow-contract: ok` | deploy preservado |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | 0,000003 s | `rollback-contract:valid` | contrato de rollback preservado |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | 0,000003 s | `rollback-runtime:valid` | runtime de rollback preservado |
| `python3 tools/releases/release_control_contract.py validate` | 0 | 0,000003 s | `release-control-contract:valid` | contrato release-control preservado |
| `git diff --check` | 0 | 0,000003 s | saída vazia | whitespace limpo |
| `git ls-files --stage` | 0 | 0,000005 s | saída vazia | nenhum arquivo staged |
| `git rev-parse --verify HEAD` | 128 | 0,000004 s | `fatal: Needed a single revision` | HEAD/commit continuam inexistentes |
| `git tag --list` | 0 | 0,000003 s | saída vazia | nenhuma tag |
| `git reflog show --all` | 0 | 0,000003 s | saída vazia | nenhum reflog |

### 11.4 Git, workflows, resíduos e acessos

O workspace continua pré-Git: o índice permanece vazio, `HEAD` retorna 128,
tags e reflog permanecem vazios, e não houve `git add`, commit, tag, push,
`git init` ou alteração de remote. Os workflows não foram alterados nesta
correction e nenhum workflow novo foi criado.

Os diretórios `.pytest_cache` e `release_control/.pytest_cache`, já presentes
no workspace e fora da fronteira da correction, foram preservados. Não há
`__pycache__` gerado pela matriz, pois os testes Python foram executados com
`PYTHONDONTWRITEBYTECODE=1`.

Não houve rede, GitHub, GHCR, SSH, VPS, Docker, containers, Postgres,
produção, instalação ou acesso a segredo real. O scanner operou somente sobre
a listagem local de candidatos e reportou apenas estados limpos; nenhuma saída
de teste ou relatório contém valores de tokens, chaves ou assignments.

### 11.5 Divergências restantes

Nenhuma divergência funcional da correction-01: o scanner canônico terminou
com exit 0, `secret-scan:clean` e `unsupported=0`, todos os contratos
retornaram exit 0 e os invariantes Git exigidos foram preservados. A
indisponibilidade ambiental de `actionlint`, já registrada na revisão anterior,
permanece apenas como limitação não executada; a correction-01 proíbe sua
instalação e não depende desse binário.

IN_PROGRESS — aguardando revisão do orquestrador
