# S39 — Workflow independente da imagem do release control

> **Data:** 03/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Executor:** CLI
> **Contrato:** `S39-workflow-imagem-release-control.task.md`
> **SHA-256 da task:** `80ea78342d9708bbd20bf3245d0e56435b0cc65be1e0f3beb7b67e2b0055dd3c`
> **Resultado:** `IN_PROGRESS — workflow da imagem do release control pronto; aguardando aceite e autorização de publicação`
>
> A seção 1 a 9 abaixo registra a primeira execução, que parou fail-closed antes
> de stage, commit e push. A seção 10 registra a retomada sob a
> `correction-01`, que resolveu a causa e completou a slice.

A implementação está completa e toda a matriz local está verde, **exceto** a
prova Docker obrigatória da §8: o scan Trivy `HIGH,CRITICAL` com
`ignore-unfixed=false` reprova com 23 vulnerabilidades da base
`python:3.13-slim`, **nenhuma delas com correção disponível**. Como a §9
condiciona commit e push a "todos os gates locais verdes", não houve commit nem
push. Nada foi publicado, disparado ou mutado remotamente.

## 1. Integridade da base

| Item | Exigido | Observado | Estado |
|---|---|---|---|
| `HEAD` | `c51bc77a9430caf68ba190d41e9e5f85106ad0b5` | idêntico | conforme |
| `origin/main` e remoto | `bd1f51f96866665a3d5f0e43e15d27dab4e94e74` | idêntico | conforme |
| divergência | ahead 1 / behind 0 | `ahead=1 behind=0` | conforme |
| worktree inicial | limpo | `git status --porcelain` sem saída | conforme |
| SHA-256 do relatório S38 | `b09a5420...643aed84` | idêntico | conforme |
| SHA-256 da task S39 | `80ea7834...0055dd3c` | idêntico | conforme |

Estado remoto esperado, confirmado antes de editar:

```text
publish-release-control.yml no default branch      AUSENTE (404)
RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS          MISSING (404)
runs deploy-production.yml / rollback-production.yml   0 / 0
tags / releases                                     1 / 1  (apenas v0.1.0)
```

Os `404` foram classificados como `MISSING`, não mascarados como sucesso.

## 2. Causa técnica que interrompeu a slice

### 2.1 Evidência

Prova Docker local da §8, executada sobre a imagem realmente construída:

```text
docker build --platform linux/amd64 -t <tag-local> -f release_control/Dockerfile release_control
build_exit=0

trivy image --severity HIGH,CRITICAL --ignore-unfixed=false --exit-code 1 --scanners vuln <tag-local>
trivy_exit=1
```

Quantificação sobre a saída JSON do mesmo scan:

```text
total HIGH/CRITICAL      23
por severidade           HIGH 19, CRITICAL 4
por status               affected 20, fix_deferred 3
com FixedVersion         0
alvos                    debian 13.6 (base) e Python
```

A mesma medição diretamente na base confirma a origem:

```text
python:3.13-slim@sha256:6771159cd4fa5d9bba1258caf0b82e6b73458c694d178ad97c5e925c2d0e1a91
HIGH/CRITICAL: 23   (HIGH 19, CRITICAL 4)
com correção disponível: 0
```

As 23 vêm **integralmente da base**, não do código nem das dependências do
`release_control`. Exemplos observados: `perl-Archive-Tar` (`CVE-2026-42497`,
`fix_deferred`), `perl-IO-Compress` (`CVE-2026-48962`), `util-linux`
(`CVE-2026-53615`).

### 2.2 Por que isto bloqueia em vez de ser contornado

O contrato fecha as três saídas que existiriam:

- **§2.4** permite alterar *somente o pin* da base: "Não atualizar Python, `uv`,
  dependências ou código do runtime." Trocar para `python:3.13-alpine` mudaria a
  família de libc (glibc → musl) e é uma alteração material de runtime, não um
  pin.
- **§10** proíbe "relaxar constraint, schema ou estado fail-closed", e **§6
  item 12** exige que o validador rejeite Trivy que ignore `HIGH`, `CRITICAL` ou
  use `ignore-unfixed=true`. Afrouxar o gate é explicitamente vedado — e o
  validador que escrevi rejeitaria a própria mudança.
- Atualizar o digest não ajuda: **zero** das 23 possui `FixedVersion`, então não
  existe versão corrigida a adotar hoje.

Por isso a slice para aqui, com a implementação preservada para revisão, em vez
de improvisar.

### 2.3 Contraste que explica por que o caminho comercial passa

As seis imagens comerciais usam bases Alpine (`node:*-alpine`,
`nginx:*-alpine`, `eclipse-temurin:*-alpine`), historicamente com superfície
muito menor. O `release_control` é o único componente sobre Debian slim. A
decisão sobre a base é do orquestrador; este relatório apenas a expõe.

## 3. Implementação entregue

Tudo abaixo está no worktree, **não commitado**.

### 3.1 Arquivos criados

| Arquivo | Conteúdo |
|---|---|
| `.github/workflows/publish-release-control.yml` | workflow `Publish Release Control Image`, quatro jobs |
| `tools/deploy/release_control_image.py` | trust, probe, manifest, validate e outcome |
| `tools/deploy/validate_release_control_workflow.py` | contrato estático do workflow e do Dockerfile |
| `tools/deploy/tests/test_release_control_image.py` | 16 testes causais |
| `tools/deploy/tests/test_validate_release_control_workflow.py` | 21 mutantes causais |

### 3.2 Arquivos alterados

| Arquivo | Alteração |
|---|---|
| `release_control/Dockerfile` | ambos os `FROM` pinados no mesmo digest; `ARG VCS_REF`/`IMAGE_VERSION` e três labels OCI |
| `tools/deploy/validate_release_control_package.py` | passa a **exigir** o pin por digest nos dois estágios e digests idênticos |
| `tools/ci/validate_workflow_inventory.py` | registra o sexto workflow; marcador do README passa a "seis" e rejeita contagens antigas |
| `tools/ci/tests/test_validate_workflow_inventory.py` | mutantes de contagem obsoleta e de README sem o novo workflow |
| `tools/releases/validate_release_workflow.py` | `EXPECTED` inclui o sexto workflow |
| `tools/releases/tests/test_release_publication.py` | conjunto esperado atualizado |
| `.github/workflows/README.md` | inventário passa a descrever seis workflows e a natureza operacional do novo |
| `docs/.../OPERACAO_RELEASE_CONTROL.md` | seção "Ciclo independente da imagem" |

### 3.3 Desvio de fronteira, declarado

A §7 permite `tools/ci/validate_workflow_inventory.py` e seu teste — ou seja,
registrar o sexto workflow era intenção explícita. Mas o registro é
transitivamente verificado por três arquivos **fora** da lista da §7:

```text
.github/workflows/README.md                      (o inventário exige o texto e a lista)
tools/releases/validate_release_workflow.py      (o inventário cruza com o EXPECTED dele)
tools/releases/tests/test_release_publication.py (fixa esse EXPECTED)
```

Sem alterá-los, `validate_workflow_inventory.py` falha em `workflow-set` e
depois em `release-validator-expected`, e a §8 exige esse validador em `0`. A
fronteira, como escrita, não comporta um sexto workflow.

Tratei as três como consequência mecânica do que a §7 autorizou, apoiado no
handoff §1 — "parar quando surgir decisão humana, risco material ou mutação
externa não autorizada — **não por formalismo reparável**". Nenhuma delas é
decisão de arquitetura: as três apenas espelham a lista de workflows. Ainda
assim ficam aqui destacadas para ratificação do orquestrador, e todas são
reversíveis por `git checkout` dos arquivos.

## 4. Decisões arquiteturais implementadas

### 4.1 Separação do produto comercial

`release_control` permanece o único `excluded_operational_components`
(`catalog:valid`), fora de `canonical_order`, do candidato e do BOM. O
repositório de imagem é exclusivamente
`ghcr.io/greggorio/abaronesa-emporio-release-control`, distinto dos seis
comerciais — afirmado por teste.

### 4.2 Disparo e identidade

```text
nome            Publish Release Control Image
gatilho         workflow_dispatch, e nada mais
inputs          nenhum — sha, tag, imagem, Dockerfile e comando vêm do contexto
ref exigida     refs/heads/main, com o sha confirmado como head de main
allowlist       vars.RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS (decimal, sem duplicatas)
isolamento      o validador rejeita RELEASE_PUBLISHER_ACTOR_IDS e DEPLOYER_ACTOR_IDS
```

A variável permanece `MISSING`, como exigido, e o workflow não foi disparado.

### 4.3 Imutabilidade

A tag de transporte é `…:src-<sha>-run-<runId>-<attempt>`, derivada apenas do
contexto confiável, e **não** aparece no manifesto — um teste afirma que nem
`tag` nem `transportTag` são chaves. `latest` e SemVer são rejeitados pelo
validador. A identidade final é `repository@sha256:<64 hex>`, lida do registry
após o push.

Ordem imposta e verificada estaticamente no job `publish`:

```text
build único (load: true, push: false, linux/amd64)
  -> probe de usuário 10001:10001, healthcheck e labels
  -> Trivy HIGH,CRITICAL, ignore-unfixed=false, exit-code 1
  -> login no ghcr.io
  -> um único docker push
  -> digest remoto, manifesto e sidecar
  -> logout e remoção da tag exata em always()
```

`packages: write` existe **somente** no job `publish`; os outros três têm apenas
`contents: read`.

### 4.4 Manifesto

Chaves exatamente as doze contratadas, JSON canônico, sidecar com o hex SHA-256
seguido de LF. Invariantes verificados: SHA de 40 hex minúsculos, digest de 64
hex, `immutableRef` composto de `imageRepository` e `imageDigest`, run/attempt e
`actorId` positivos, ator no formato GitHub e timestamp UTC normalizado e real.

## 5. Cobertura causal (§6)

| # | Rejeição exigida | Teste |
|---|---|---|
| 1 | evento ≠ `workflow_dispatch` | `test_01`, `test_02`, e `TrustTests` |
| 2 | ref ≠ `main` | `test_03` do trust, `TrustTests` |
| 3 | repository, workflow path ou SHA divergentes | `TrustTests.test_event_ref_repository_and_workflow_path_are_enforced` |
| 4 | actor ID ausente, não decimal, zero ou fora da allowlist | `TrustTests.test_actor_id_must_be_decimal_positive_and_allowed` (7 casos) |
| 5 | input capaz de escolher sha/tag/imagem/Dockerfile/comando | `test_03` (6 nomes) |
| 6 | action sem pin de SHA completo | `test_07` |
| 7 | `packages: write` fora do publish | `test_04` (3 jobs), `test_05` |
| 8 | login antes do scan | `test_08` |
| 9 | `latest`, SemVer ou referência sem digest | `test_17`, `ManifestTests`, `TrustTests` |
| 10 | segundo build entre scan e push | `test_09` |
| 11 | base sem digest ou digests diferentes | `test_18` |
| 12 | Trivy ignorando HIGH/CRITICAL ou unfixed | `test_11` (3 casos) |
| 13 | artifact sem garantias ou com nome divergente | `test_13` (3 casos) |
| 14 | manifesto com campo extra, digest/SHA/run inválido ou ref divergente | `ManifestTests` (14 + 3 casos) |
| 15 | `release_control` no candidato/BOM | `test_20`, `SeparationTests` |
| 16 | workflow criando tag, release, deploy, rollback ou SSH | `test_17` |
| 17 | outcome verde com predecessor falho ou pulado | `OutcomeTests` (12 combinações) |

## 6. Matriz local

| Gate | Exit | Resultado |
|---|---:|---|
| `release_control/tests` | 0 | `331 passed` |
| `tools/docker/tests` | 0 | 117 OK |
| `tools/ci/tests` | 0 | 31 OK |
| `tools/candidates/tests` | 0 | 75 OK |
| `tools/releases/tests` | 0 | 300 OK |
| `tools/deploy/tests` | 0 | 390 OK |
| `tools/security/tests` | 0 | 26 OK |
| `tools/compose/tests` | 0 | 4 OK |
| `tools/gateway/tests` | 0 | 4 OK |
| **suítes canônicas** | | **947 testes** |

Os 17 validadores retornaram `0`, incluindo os dois novos e os que precisaram
acompanhar o sexto workflow:

```text
node-images-contract:valid        java-images-contract:valid
ci:valid                          invocability:valid:commands=26
migrations:valid                  candidate-workflow:valid
release-workflow:valid            publisher-ui:valid
publisher-identity-bridge:valid   workflow-inventory:valid
deploy-workflow-contract: ok      rollback-contract:valid
rollback-runtime:valid            deployer-runtime:valid
release-control-package:valid     release-control-workflow:valid
release-control-contract:valid    publisher-runtime:valid
bootstrap-contract:valid          Compose contract valid
Gateway contract valid            catalog:valid
```

```text
secret-scan:clean:scanned=2477:allowed=784:unsupported=0:history_scanned=117910
git diff --check                 exit 0
uv.lock e pyproject.toml         0 alterações
```

## 7. Prova Docker local e limpeza

| Etapa | Exit | Resultado |
|---|---:|---|
| build `linux/amd64` com tag temporária única | 0 | imagem `sha256:c113aed5…` |
| `release_control_image.py probe` | 0 | `release-control-image:probe:valid` |
| inspeção direta | 0 | `user=10001:10001`, healthcheck presente, `amd64/linux`, três labels OCI com `revision` igual ao SHA |
| **Trivy `HIGH,CRITICAL`, `ignore-unfixed=false`** | **1** | **23 vulnerabilidades, nenhuma corrigível** |

Limpeza dirigida, verificada:

```text
docker image rm -f <tag-local>        exit 0
imagens da prova restantes            0
containers da prova                   0
volumes/redes criados pela prova      0 / 0
baronesa-postgres preservado          sim
```

Nenhum `docker login` foi executado: `~/.docker/config.json` continua com **0**
entradas `ghcr.io` e não foi aberto.

## 8. Negativos preservados

```text
commit                                          nenhum
push                                            nenhum; remoto ainda em bd1f51f9
runs de publish-release-control.yml             0
RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS       MISSING
pacote GHCR abaronesa-emporio-release-control   inexistente (0)
imagem release_control publicada                nenhuma
tags remotas / GitHub Releases                  1 / 1 — apenas v0.1.0, inalterada
runs deploy-production.yml / rollback           0 / 0
VPS, DNS, TLS, Nginx, firewall, usuários        intocados
segredos, .env, chaves, tokens                  nenhum aberto ou criado
rebase, amend, force push, segundo push         nenhum
```

## 9. Estado final e o que falta

```text
HEAD         c51bc77a9430caf68ba190d41e9e5f85106ad0b5
origin/main  bd1f51f96866665a3d5f0e43e15d27dab4e94e74
ahead 1 / behind 0     stage vazio
```

Worktree: cinco arquivos novos e oito alterados, todos listados na §3, mais este
relatório. Nada staged, nada commitado.

Falta exclusivamente uma decisão do orquestrador sobre a base do
`release_control`, já que o gate de scan não pode ser afrouxado nem a base
trocada dentro desta slice:

1. adotar outra base para o `release_control` — por exemplo Alpine, alinhando-o
   às seis imagens comerciais — em slice própria, por ser mudança de runtime;
2. aceitar formalmente as 23 vulnerabilidades sem correção como risco
   documentado, definindo como o gate deve expressá-lo sem virar
   `ignore-unfixed=true`;
3. aguardar correções do Debian e reexecutar a prova.

Com essa decisão, o restante da S39 está pronto para commit, push e observação
de CI/Publish Candidate sem retrabalho.

O executor não aceita S39, não executou o novo workflow e não criou a próxima
slice.

*(terminal da primeira execução; superado pela seção 10)*

## 10. Retomada sob a correction-01

> **SHA-256 da correction-01:** `367a501487afc12f3b0ac021f72862213b7dc24e1605bb5adf2b13bdd52059a8`
> **HEAD na retomada:** `f3ee8e8cf4dd29d881563041fe6ca4f0357121a2`

### 10.1 Snapshot verificado antes de editar

| Item | Exigido | Observado |
|---|---|---|
| `HEAD` | `f3ee8e8c...` | idêntico |
| `origin/main` e remoto | `bd1f51f9...` | idêntico |
| divergência | ahead 2 / behind 0 | idêntico |
| stage | vazio | vazio |
| SHA-256 da task | `80ea7834...0055dd3c` | idêntico |
| SHA-256 da correction-01 | `367a5014...d52059a8` | idêntico |

O patch local da primeira execução estava íntegro — oito arquivos modificados e
cinco não rastreados. Nada foi descartado ou reimplementado; não houve `reset`,
`checkout`, `rebase` nem `amend`.

### 10.2 Base Alpine adotada

Ambos os `FROM` passaram a usar exatamente a referência aprovada:

```text
python:3.13-alpine3.23@sha256:9fdbf2e3e82628351513560b121e2ee6ce31cac212be9e070c5a5e2769fb5e76
```

Única outra alteração no Dockerfile, conforme §3.3 da correção: a criação de
grupo/usuário passou das utilidades `shadow` para as do BusyBox —
`addgroup --gid 10001` e `adduser --uid 10001 --ingroup release-control
--disabled-password`, preservando o runtime final em `10001:10001`.

Preservados sem alteração: Python `3.13`, `uv==0.9.17`, `pyproject.toml`,
`uv.lock`, dependências e código do runtime. **Nenhum** `apk upgrade`, **nenhum**
pacote `apk` adicional — o build resolveu tudo pelo lock, com wheels musllinux —
e **nenhum** toolchain no estágio final.

### 10.3 Scans, registrados separadamente

| Alvo | Comando | Exit | Resultado |
|---|---|---:|---|
| base Debian anterior | Trivy 0.70.0 `HIGH,CRITICAL` `ignore-unfixed=false` | 1 | 23 achados (19 HIGH, 4 CRITICAL), **0** com correção |
| imagem final Debian anterior | idem | 1 | 23 achados, herdados integralmente da base |
| **imagem final Alpine** | idem | **0** | **zero HIGH, zero CRITICAL** |

O scan que decide é o da imagem construída, não o da base. Ele passou.

### 10.4 Defeito preexistente revelado pela prova funcional

A §6.6 da correção exige um teste funcional do processo real do container. Ele
falhou de imediato:

```text
sh: alembic: not found
sh: uvicorn: not found
```

Diagnóstico: `PATH` estava correto e os executáveis existiam em
`/app/.venv/bin`, mas o shebang de cada console script apontava para
`#!/build/.venv/bin/python` — o venv é construído em `/build/.venv` e copiado
para `/app/.venv`, e `/build` não existe no estágio final. O interpretador
ausente produz a mensagem enganosa `not found`.

**É defeito preexistente, não do Alpine**: o layout `WORKDIR /build` → `COPY
--from=builder /build/.venv /app/.venv` é anterior a esta slice e teria o mesmo
efeito no Debian. Consequência real: o `CMD` do container — `alembic upgrade
head && exec uvicorn ...` — **nunca poderia iniciar**. A imagem jamais havia
sido executada, então isso nunca apareceu.

Correção mínima, dentro da fronteira (`release_control/Dockerfile`): invocar
ambos pelo interpretador, `python -m alembic` e `python -m uvicorn`. Não altera
layout, `WORKDIR`, dependências, código nem a ordem migration→servidor exigida
pelo validador do pacote.

### 10.5 Provas causais da §6

| # | Exigência | Resultado |
|---|---|---|
| 1 | os dois `FROM` são exatamente a referência Alpine e idênticos | `release-control-package:valid` e `release-control-workflow:valid` |
| 2 | sem digest, digest divergente ou outra base são rejeitados | `test_18` (mutantes de pin e de digests distintos) e o regex do validador do pacote |
| 3 | build `linux/amd64` só pelo lock | `build_exit=0`, sem `apk` adicional; `uv.lock` e `pyproject.toml` com 0 alterações |
| 4 | usuário final `10001:10001` | probe e `id` dentro do container em execução |
| 5 | healthcheck e três labels OCI válidos | probe `valid`; `revision` igual ao SHA |
| 6 | teste funcional mínimo do processo real | container real subiu — ver 10.6 |
| 7 | Trivy da imagem final em zero | `exit 0`, zero HIGH e zero CRITICAL |
| 8 | sem segundo build entre scan e push | `test_09`; validador falha em `rebuild-between-scan-and-push` |
| 9 | limpeza sem resíduo e sem login | ver 10.7 |

### 10.6 Prova funcional de ponta a ponta

Container real, com o `CMD` real, contra um PostgreSQL efêmero em namespace de
rede compartilhado:

```text
health/live = 200 após 4 s
processo roda como 10001:10001
healthcheck do Docker: healthy
INFO [alembic.runtime.migration] Running upgrade  -> 0001_publisher_runtime
INFO [alembic.runtime.migration] Running upgrade 0001_publisher_runtime -> 0002_deployer_runtime
INFO [alembic.runtime.migration] Running upgrade 0002_deployer_runtime -> 0003_commercial_rollback
INFO: Application startup complete.
```

Duas tentativas anteriores falharam por configuração **do harness**, não da
imagem, e ambas por validação correta do próprio runtime: `test profile requires
loopback transports`, primeiro por apontar o banco pelo nome do container e
depois por deixar `GITHUB_API_BASE` fora de loopback. Registrado por
transparência.

### 10.7 Limpeza dirigida

```text
container da aplicação        removido (exit 0)
PostgreSQL efêmero e volume   removidos (exit 0)
rede da prova                 removida (exit 0)
imagem temporária             removida (exit 0)
containers/redes/imagens/volumes da prova restantes   0 / 0 / 0 / 0
baronesa-postgres                                     preservado
entradas ghcr.io em ~/.docker/config.json             0  (nenhum login; arquivo não aberto)
```

### 10.8 Matriz completa reexecutada

| Gate | Exit | Resultado |
|---|---:|---|
| `release_control/tests` | 0 | `331 passed` |
| oito suítes canônicas | 0 | 117 + 31 + 75 + 300 + 390 + 26 + 4 + 4 = **947 testes**, todas OK |
| 17 validadores canônicos | 0 | todos `valid` |
| `validate_release_control_package.py` | 0 | `release-control-package:valid` |
| `validate_release_control_workflow.py` | 0 | `release-control-workflow:valid` |
| `validate_deployer_runtime.py` | 0 | `deployer-runtime:valid` |
| `validate_publisher_runtime.py` | 0 | `publisher-runtime:valid` |
| `catalog.py validate` | 0 | `catalog:valid` |
| `secret_scan.py --tracked` | 0 | `clean:scanned=2483:allowed=800:unsupported=0` |
| `git diff --check` e `--cached --check` | 0 | sem saída |

O secret scan foi executado **depois** do stage, de modo que os cinco arquivos
novos entraram na superfície rastreada.

### 10.9 Ratificação da fronteira

Os três espelhos do sexto workflow ficam registrados como ratificados pela §4 da
correction-01:

```text
.github/workflows/README.md
tools/releases/validate_release_workflow.py
tools/releases/tests/test_release_publication.py
```

Nenhum deles altera semântica de candidato, release global, deploy, rollback,
state machine ou produção — apenas registram e validam
`publish-release-control.yml` no inventário canônico. A divergência está
declarada, não ocultada.

### 10.10 Commit e push

Remoto reconfirmado imediatamente antes: `bd1f51f96866665a3d5f0e43e15d27dab4e94e74`,
inalterado. Stage com exatamente 14 arquivos; o relatório permaneceu fora.

```text
daaa706  feat: publish the release control image through its own fail-closed workflow
         14 files changed, 1408 insertions(+), 19 deletions(-)

git merge-base --is-ancestor origin/main HEAD   exit 0
git push origin main
   bd1f51f..daaa706  main -> main
```

Um único commit, um único push, fast-forward. Sem rebase, amend, force ou
segundo push.

### 10.11 Validação remota

```text
CI                 30838546384
                   https://github.com/greggorio/abaronesa-emporio/actions/runs/30838546384
                   success | event=push | attempt 1 | jobs 13/13
                   headSha daaa7061ab9f7a722b17e37c0f060f45141225e7

Publish Candidate  30839217752
                   https://github.com/greggorio/abaronesa-emporio/actions/runs/30839217752
                   success | event=workflow_run | attempt 1 | jobs 11/11
                   headSha daaa7061ab9f7a722b17e37c0f060f45141225e7
```

Artifacts nominais do candidato, todos não expirados:

```text
candidate-manifest        id 8866271295
candidate-outcome         id 8866271856
candidate-effective-plan  id 8866095468
```

O candidato foi produzido pela CI do mesmo SHA e permanece vinculado a ele.
Nenhuma release foi publicada a partir dele.

### 10.12 Novo workflow presente e nunca executado

```text
arquivo no default branch   publish-release-control.yml (7001 bytes)
registrado no GitHub        "Publish Release Control Image" id=326422057 state=active
runs                        0
```

### 10.13 Negativos finais

```text
RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS      MISSING (404)
pacote GHCR abaronesa-emporio-release-control  inexistente (0)
imagem do release control publicada            nenhuma
docker login                                   nenhum
v0.1.0                                         draft=false, prerelease=false,
                                               createdAt 2026-08-03T09:53:45Z — intacta
tags remotas / GitHub Releases                 1 / 1
runs deploy-production.yml / rollback          0 / 0
VPS, DNS, TLS, Nginx, firewall, usuários       não acessados nem mutados
segredos, .env, chaves, tokens                 nenhum aberto ou criado
```

### 10.14 Estado final

```text
HEAD e origin/main   daaa7061ab9f7a722b17e37c0f060f45141225e7
ahead 0 / behind 0   stage vazio
worktree             somente este relatório, não rastreado
```

A `correction-01` está cumprida: base Alpine aprovada nos dois estágios, scan
estrito da imagem final em zero, um commit e um push fast-forward, CI e
candidato verdes, workflow versionado e nunca executado, imagem nunca publicada
e nenhuma mutação de release, deploy, rollback ou VPS.

O executor não aceita S39 e não cria a próxima slice.

IN_PROGRESS — workflow da imagem do release control pronto; aguardando aceite e autorização de publicação
