# S31 — correction-01: retomada com BuildKit pinado

> **Estado:** `AUTHORIZED` pelo orquestrador em 01/08/2026
> **Contrato-base:** `S31-remocao-npm-runtime-whatsapp.task.md`
> **Relatório a continuar:** `S31-remocao-npm-runtime-whatsapp.report.md`
> **Checkpoint de retomada:** mensagem exata `docs: authorize pinned BuildKit for S31 resume`
> **Estado da S31:** `IN_PROGRESS`; implementação não rejeitada; sem autorização de push

## 1. Veredito sobre a execução bloqueada

A parada registrada no relatório foi correta. O builder nominal com driver
`docker-container` tentou obter automaticamente
`moby/buildkit:buildx-stable-1` antes de processar o Dockerfile. Esse destino não
estava na lista fechada da task. O executor interrompeu a operação, removeu o
builder e não tentou alternativa, inspeção, Trivy, smoke, commit ou push.

O bloqueio é uma omissão do contrato, não defeito demonstrado nos três arquivos
de implementação. Permanecem válidas como evidência intermediária da mesma
árvore de trabalho: enumeração da base; 28 casos S31; treze validadores; sete
suítes com 517 testes; `git diff --check`; e cleanup nominal sem prune.

Esta correção autoriza somente a retomada da paridade Docker. Não aceita a S31,
não cria nova slice e não autoriza alteração técnica adicional por antecipação.

## 2. Resolução imutável do BuildKit

O orquestrador consultou o registry público em modo somente leitura:

```bash
docker buildx imagetools inspect moby/buildkit:buildx-stable-1
```

Resultado observado em 01/08/2026:

```text
index:       sha256:2f5adac4ecd194d9f8c10b7b5d7bceb5186853db1b26e5abd3a657af0b7e26ec
linux/amd64: sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
```

O manifesto `linux/amd64` foi reconfirmado diretamente com media type
`application/vnd.oci.image.manifest.v1+json`. Fica autorizado exclusivamente o
pull de leitura desta referência imutável:

```text
docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
```

A tag `buildx-stable-1`, qualquer outro digest de BuildKit e qualquer outro
registry continuam proibidos. A consulta do orquestrador não criou imagem,
container, builder, volume ou cache local.

## 3. Preflight de retomada sobre o worktree herdado

O preflight limpo da task foi satisfeito na primeira execução e não deve ser
repetido: agora ele falharia corretamente porque existe trabalho autorizado a
preservar.

Antes de continuar, executar e registrar:

```bash
test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"
test "$(git branch --show-current)" = "main"
test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"
test "$(git log -1 --format=%s)" = "docs: authorize pinned BuildKit for S31 resume"
test "$(git rev-list --count origin/main..HEAD)" = "7"
git rev-parse HEAD
git status --short
git diff --cached --name-only
git diff --check
```

Os cinco `test` e `git diff --check` devem retornar exit 0. O stage deve estar
vazio. O worktree deve conter exatamente:

```text
 M tools/docker/tests/test_validate_node_images.py
 M tools/docker/validate_node_images.py
 M whatsapp_service/Dockerfile
?? docs/infrastructure/deployment/implementation/slices/S31-remocao-npm-runtime-whatsapp.report.md
```

Também devem ser verdadeiros:

- `S31-trivy-findings.whatsapp.after.json` continua ausente;
- builder, tag, container e volume nominais `s31-*` continuam ausentes;
- a referência BuildKit autorizada continua ausente no daemon;
- Build Cache continua em `0B`;
- a imagem base Node preexistente continua preservada.

Parar diante de qualquer divergência. Não usar `reset`, `checkout`, `restore`,
`clean`, rebase ou qualquer operação que descarte ou reescreva o trabalho
herdado.

## 4. Retomada fechada da paridade Docker

Preservar integralmente os três arquivos técnicos e o relatório existentes. Não
refazer a implementação nem alterar testes que já passaram.

Criar o mesmo builder nominal com a imagem fechada:

```bash
docker buildx create \
  --name s31-whatsapp-builder-10db6b3 \
  --driver docker-container \
  --driver-opt image=docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684 \
  --platform linux/amd64 \
  --use
```

Inicializar e inspecionar o builder antes do build. Registrar o container criado
e provar por `docker inspect` que `Config.Image` referencia o digest autorizado.
Se o Buildx tentar resolver `buildx-stable-1`, outro digest ou outro destino,
parar e executar somente o cleanup nominal.

Retomar literalmente a task S31 a partir do build da §4.5, preservando:

- contexto `whatsapp_service` e Dockerfile canônico;
- `linux/amd64`, `--load` e ausência de push;
- `VCS_REF=10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a`;
- `IMAGE_VERSION=ci-10db6b3`;
- tag `s31-whatsapp-runtime:local-10db6b3`.

Concluir na ordem: build; inspeção; Trivy; smoke determinístico; prova de
ausência dos gerenciadores; JSON e linhagem; cleanup; relatório; stage dos cinco
caminhos; dois secret scans; `git diff --cached --check`; commit único local.

Para a linhagem do JSON, manter:

```text
sourceSha=10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a
sourceState=working-tree-after-s31-remediation-before-evidence-files
```

`sourceTreeSha` e `sourceDiffSha256` continuam limitados aos três arquivos
técnicos. Esta correção e o tracker ficam fora da árvore medida e do commit do
executor.

## 5. Cleanup adicional do BuildKit

Registrar antes da retomada que a referência BuildKit pinada não existe
localmente. Ao final, depois de remover o builder nominal:

- remover somente container e volume pertencentes ao builder nominal, se
  `docker buildx rm` não os remover;
- remover somente a imagem BuildKit pinada criada pela retomada;
- provar ausência do builder, container, volume e imagem BuildKit;
- preservar a imagem base e qualquer imagem Trivy preexistente; se a imagem
  Trivy estiver ausente no preflight e for baixada pela retomada, removê-la
  nominalmente conforme a task;
- não executar qualquer forma ampla de prune.

Se a imagem pinada passar a ser usada por recurso não criado nesta execução,
não forçar sua remoção: registrar a divergência e parar antes do commit.

## 6. Fronteira e condições de parada

A fronteira de escrita continua sendo somente:

```text
whatsapp_service/Dockerfile
tools/docker/validate_node_images.py
tools/docker/tests/test_validate_node_images.py
docs/infrastructure/deployment/implementation/slices/S31-trivy-findings.whatsapp.after.json
docs/infrastructure/deployment/implementation/slices/S31-remocao-npm-runtime-whatsapp.report.md
```

Esta correção e o tracker são autoridade versionada e não podem ser alterados
pelo executor. Além das paradas da task, parar antes do commit se o digest
efetivo divergir, o builder não operar em `linux/amd64`, o cleanup exigir prune
amplo, surgir necessidade de contorno técnico de infraestrutura ou qualquer
acesso externo sair da lista acrescida somente do BuildKit pinado.

Não fazer push em nenhuma hipótese.

## 7. Relatório e commit

Não apagar a evidência da execução bloqueada. Remover apenas a linha terminal
anterior e acrescentar ao mesmo relatório:

```text
## 9. Execução da correction-01 — retomada com BuildKit pinado
```

Registrar preflight, digest, builder, build, inspeção, Trivy, smoke, JSON,
cleanup, stage, dois secret scans, `git diff --cached --check`, acessos externos,
resíduos e divergências.

Se todos os gates passarem, criar exatamente:

```bash
git commit -m "fix: remove package managers from whatsapp runtime image"
```

O commit contém somente os cinco caminhos da task. Sem push, tag, force,
`--no-verify`, outra branch, outro remote ou commit adicional. O relatório
termina exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

O SHA final aparece no handoff pós-commit, nunca dentro do próprio commit.

## 8. Prompt formal de retomada

```text
Retome exclusivamente a S31 pela correction-01 em
/home/gregorio/git/baronesa/emporio.

Leia integralmente a task S31, esta correction-01 e o relatório S31 existente.
Não repita o preflight de worktree limpo: execute o preflight herdado da §3 e
preserve exatamente os três arquivos técnicos modificados e o relatório não
rastreado. Não use reset, checkout, restore ou clean.

Use somente o builder nominal e a imagem BuildKit linux/amd64 fixada por digest
na §2. Continue da paridade Docker: build, inspeção, Trivy, smoke, JSON, cleanup,
relatório, stage, dois secret scans e commit único local.

Não altere a implementação já validada por antecipação, não use builder default,
tag flutuante do BuildKit ou prune amplo e não faça push.

Se qualquer gate falhar, registre no relatório e pare sem commit. Se tudo passar,
crie exatamente `fix: remove package managers from whatsapp runtime image`,
informe o SHA final no handoff e deixe a S31 `IN_PROGRESS` para revisão do
orquestrador.
```
