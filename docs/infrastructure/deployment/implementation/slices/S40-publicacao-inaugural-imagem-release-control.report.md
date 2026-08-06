# S40 — Publicação inaugural da imagem do release control

> **Data:** 03/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Executor:** CLI
> **Contrato:** `S40-publicacao-inaugural-imagem-release-control.task.md`
> **SHA-256 da task:** `d91606d337db7b3d15ad7391502cbf04b6b3285eec3e5cb3a985eddc4d068698`
> **Resultado:** `IN_PROGRESS — imagem operacional do release control publicada e validada; aguardando aceite e preparação do Gate B`

## 1. Autorização humana

A delegação continha literalmente a frase exigida pela §2:

> Autorizo integralmente a S40: configurar RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS com meu actor ID autenticado, executar uma única vez Publish Release Control Image em main, publicar a primeira imagem do release_control no GHCR e preservar os artefatos e logs para auditoria.

Ela cobre exatamente as quatro mutações da §2 e nada além. Nenhuma confirmação
adicional foi solicitada, conforme a delegação determinou.

## 2. Snapshot inicial read-only (§4)

| Item | Exigido | Observado |
|---|---|---|
| `HEAD` | `0f219a2be498a64c94d5dc675f8e130d33d9dedd` | idêntico |
| `origin/main` e remoto | `daaa7061ab9f7a722b17e37c0f060f45141225e7` | idêntico |
| divergência | ahead 1 / behind 0 | idêntico |
| SHA-256 da task | `d91606d3...4d068698` | idêntico |
| stage | vazio | vazio |
| não rastreado | apenas o relatório S39 | apenas ele |
| `git diff --check` | 0 | 0 |

```text
gh auth status            greggorio (keyring), Active account: true
gh api user               login=greggorio  id=35626201
workflow                  "Publish Release Control Image" id=326422057 state=active
runs do workflow          0
variables                 apenas RELEASE_PUBLISHER_ACTOR_IDS (updatedAt 2026-08-02T21:54:40Z)
RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS   MISSING (404)
package GHCR              MISSING (404, "Package not found.")
releases                  1  -> v0.1.0
tags                      1  -> refs/tags/v0.1.0
deploy / rollback runs    0 / 0
```

Os dois `404` foram classificados como `MISSING`, não mascarados. O `HEAD` local
à frente do remoto é o commit documental do orquestrador; nenhum push de
documentos foi feito e o workflow usou o `main` remoto em `daaa7061...`.

## 3. Gates locais antes da mutação (§5)

| Gate | Exit | Resultado |
|---|---:|---|
| `test_release_control_image` + `test_validate_release_control_workflow` | 0 | `Ran 37 tests` OK |
| `validate_release_control_package.py` | 0 | `release-control-package:valid` |
| `validate_release_control_workflow.py` | 0 | `release-control-workflow:valid` |
| `validate_workflow_inventory.py` | 0 | `workflow-inventory:valid` |
| `validate_release_workflow.py` | 0 | `release-workflow:valid` |
| `git diff --check` | 0 | sem saída |

Confirmação estática exigida, lida do próprio YAML:

```text
gatilhos               ['workflow_dispatch']
inputs                 None            aceita inputs? False
jobs                   ('trust', 'verify', 'publish', 'outcome')
permissions trust      {'contents': 'read'}
permissions verify     {'contents': 'read'}
permissions publish    {'contents': 'read', 'packages': 'write'}
permissions outcome    {'contents': 'read'}
ordem em publish       ['scan', 'login', 'push']   scan antes de login? True
```

Nenhum arquivo foi alterado nesta fase.

## 4. Intenção única

### 4.1 Allowlist (§6.1)

```text
1. gh api user            -> login=greggorio  id=35626201  type=User
2. validação local        login confere: True   id decimal exato 35626201: True
3. POST actions/variables -> exit 0   (valor por --input, nunca em argv)
4. leitura de volta       name=RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS  value=35626201
5. outras allowlists      RELEASE_PUBLISHER_ACTOR_IDS updatedAt 2026-08-02T21:54:40Z (inalterada)
                          DEPLOYER_ACTOR_IDS  MISSING
```

Valor exato `35626201`, sem espaços, duplicatas ou segundo ator. Não é secret e,
por ser um actor ID público, é registrado aqui conforme a §11. Nenhuma outra
allowlist foi reutilizada ou tocada.

### 4.2 Dispatch (§6.2)

Reconfirmação imediatamente antes:

```text
main remoto                daaa7061ab9f7a722b17e37c0f060f45141225e7   REMOTO_CONFORME
baseline de runs           0
package                    MISSING
allowlist                  35626201
```

```text
gh workflow run publish-release-control.yml --ref main
dispatch_exit=0
```

Sem `-f`, `--raw-field`, SHA, tag, imagem ou qualquer input. A resposta **não**
foi ambígua, então nenhuma reconciliação por retry foi necessária; ainda assim a
identificação do run foi feita por diferença contra o baseline:

```text
runs totais agora: 1   novos: 1
id=30855327740 event=workflow_dispatch branch=main attempt=1
sha=daaa7061ab9f7a722b17e37c0f060f45141225e7
ANEXADO_AO_RUN_UNICO
```

Uma intenção, um dispatch, um run.

## 5. Run observado (§7)

```text
run          30855327740
url          https://github.com/greggorio/abaronesa-emporio/actions/runs/30855327740
display      publish-release-control-daaa7061ab9f7a722b17e37c0f060f45141225e7-30855327740-1
event        workflow_dispatch      headBranch main      attempt 1
headSha      daaa7061ab9f7a722b17e37c0f060f45141225e7
actor        greggorio / 35626201   triggering_actor  greggorio / 35626201
conclusão    success
jobs         trust success | verify success | publish success | outcome success
```

Observado até o terminal sem rerun, retry ou cancelamento.

### 5.1 Ordem real auditada nos logs

Marcos emitidos pelo próprio contrato, na ordem em que aparecem:

```text
release-control-image:trusted
build único (containerimage.digest exportado; base python@3.13-alpine3.23 por digest, platform=linux/amd64)
release-control-image:probe:valid
Trivy — "Scan the exact local image before any authentication"  (alpine 3.23, 29 pacotes)
Login Succeeded!                     ← somente depois do scan
docker push  (uma única vez, tag de transporte)
release-control-image:published:sha256:64b6f2be…
release-control-image:valid
Logout and remove only the exact local tag
release-control-image:outcome:published
```

A sequência de passos do job `publish` confirma que o passo de build ocorre uma
única vez e que não há segundo build entre o scan e o push. O passo de logout e
remoção da tag exata executou ao final.

Nenhum token, header, Docker config, PEM ou credencial apareceu em log. Valores
mascarados pelo GitHub não foram transcritos; digests longos foram redigidos nas
citações de log deste relatório apenas por legibilidade.

## 6. Artifacts e identidade da imagem (§8)

Baixados em diretório criado por `mktemp -d`, fora do repositório:

```text
release-control-image-manifest   id 8872278618  expired=false  680 B   run 30855327740
release-control-image-outcome    id 8872283996  expired=false  662 B   run 30855327740
greggorio~abaronesa-emporio~Q7ZQ5M.dockerbuild  id 8872279589  (auxiliar da action; não é identidade)
```

`release_control_image.py validate` → `release-control-image:valid` (exit 0).

Manifesto, integralmente:

```json
{
  "actor": "greggorio",
  "actorId": "35626201",
  "imageDigest": "sha256:64b6f2be31b8532b870656d401656df2184599921c73ad667c65c36d65022380",
  "imageRepository": "ghcr.io/greggorio/abaronesa-emporio-release-control",
  "immutableRef": "ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:64b6f2be31b8532b870656d401656df2184599921c73ad667c65c36d65022380",
  "kind": "release-control-image",
  "publishedAt": "2026-08-03T21:38:49Z",
  "repository": "greggorio/abaronesa-emporio",
  "schemaVersion": 1,
  "sourceSha": "daaa7061ab9f7a722b17e37c0f060f45141225e7",
  "workflowAttempt": 1,
  "workflowRunId": 30855327740
}
```

Verificações cruzadas, todas `True`:

| # | Verificação | Resultado |
|---|---|---|
| 1 | artifacts presentes, não expirados, vinculados ao run único | sim |
| 2 | manifesto canônico e sidecar exato | `release-control-image:valid` |
| 3 | doze chaves exatas, sem tag de transporte | `len=12`, sem `tag`/`transportTag` |
| 4 | `sourceSha == daaa7061…` | sim |
| 5 | `workflowRunId`/`workflowAttempt` iguais ao run | `30855327740` / `1` |
| 6 | actor e actorId iguais à identidade autorizada | `greggorio` / `35626201` |
| 7 | `imageRepository` canônico | sim |
| 8 | `immutableRef == imageRepository + "@" + imageDigest` | sim |
| 9 | outcome canônico, sidecar válido, `status=published`, mesmo run/attempt e digest, `manifestSha256` igual ao hash do manifesto | sim |

Outcome:

```json
{"imageDigest":"sha256:64b6f2be31b8532b870656d401656df2184599921c73ad667c65c36d65022380","kind":"release-control-image-outcome","manifestSha256":"20a98214b912013ffa899a533c1ed52cff1b1450213363509cd1406bfe59c49a","repository":"greggorio/abaronesa-emporio","schemaVersion":1,"status":"published","workflowAttempt":1,"workflowRunId":30855327740}
```

### 6.1 Package GHCR

```text
name           abaronesa-emporio-release-control
package_type   container
visibility     private
version_count  1
created_at     2026-08-03T21:38:48Z
owner          greggorio
repository     greggorio/abaronesa-emporio
```

Versão única:

```text
id      1095307691
digest  sha256:64b6f2be31b8532b870656d401656df2184599921c73ad667c65c36d65022380
tags    ['src-daaa7061ab9f7a722b17e37c0f060f45141225e7-run-30855327740-1']
latest  ausente        SemVer  ausente
```

O package foi criado, está vinculado ao repositório canônico, **não é público** e
possui exatamente a versão inicial. A tag de transporte é determinística e
corresponde ao SHA, ao run e ao attempt. O digest da versão no package é
idêntico ao `imageDigest` do manifesto e do outcome.

Nenhum `docker login`, pull ou execução local da imagem foi feito: o
`~/.docker/config.json` local continua com **0** entradas `ghcr.io`. O build,
scan e push válidos são os do run observado.

**Referência imutável final:**

```text
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:64b6f2be31b8532b870656d401656df2184599921c73ad667c65c36d65022380
```

## 7. Negativos posteriores (§9)

```text
runs do novo workflow                      1  (único, o observado)
RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS  35626201, somente esse ator
v0.1.0                                     draft=false prerelease=false
                                           createdAt 2026-08-03T09:53:45Z — inalterada
tags remotas / GitHub Releases             1 / 1
CI no SHA daaa7061                         1  (o da S39; o dispatch não criou novo)
Publish Candidate no SHA daaa7061          1  (idem)
runs deploy-production / rollback          0 / 0
environments                               0  (nenhum `production`)
release_control fora do BOM comercial      catalog:valid
commit, push, stage                        nenhum; origin/main segue daaa7061
código e documentação                      inalterados
package, versão, logs e artifacts          preservados; nada excluído
App deployer, SSH, VPS, DNS, TLS, Nginx    não acessados nem mutados
```

## 8. Mutações realizadas, lista literal

1. criação da variável de repositório
   `RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS` com valor `35626201`;
2. um único `workflow_dispatch` de `publish-release-control.yml` em `main`;
3. como consequência do run: login/push/logout internos do job `publish` com
   `GITHUB_TOKEN`, criação do package
   `abaronesa-emporio-release-control`, da sua versão inicial e da tag de
   transporte, e dos artifacts do run.

Nada além disso.

## 9. Estado final e resíduos

```text
HEAD         0f219a2be498a64c94d5dc675f8e130d33d9dedd
origin/main  daaa7061ab9f7a722b17e37c0f060f45141225e7   (inalterado)
ahead 1 / behind 0        stage vazio
```

Worktree contém apenas os relatórios da S39 e da S40, ambos não rastreados, não
staged e não commitados. Os artifacts foram baixados para um diretório
`mktemp -d` fora do repositório, sem sobrescrever nenhum arquivo versionado.

O executor não aceita a S40, não cria a próxima slice e não inicia o Gate B.

IN_PROGRESS — imagem operacional do release control publicada e validada; aguardando aceite e preparação do Gate B
