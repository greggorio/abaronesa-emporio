# S31 — correction-02: retomada consolidada da paridade Docker

> **Estado:** `AUTHORIZED` pelo orquestrador em 01/08/2026
> **Contrato-base:** `S31-remocao-npm-runtime-whatsapp.task.md`
> **Relatório a continuar:** `S31-remocao-npm-runtime-whatsapp.report.md`
> **Checkpoint:** mensagem exata `docs: consolidate S31 Docker parity plan`
> **Estado da S31:** `IN_PROGRESS`; sem push

## 1. Decisão

A correction-01 resolveu o BuildKit, mas não levantou previamente o frontend
declarado por `# syntax`. A segunda parada foi correta e não rejeita os três
arquivos técnicos herdados.

Antes desta autorização, o orquestrador executou o caminho Docker restante de
forma integral, sem alterar o worktree: substituiu somente em memória a primeira
linha do Dockerfile, construiu em `linux/amd64`, inspecionou a imagem, executou o
smoke e o Trivy. O resultado foi:

- build concluído com BuildKit e frontend fixados por digest;
- `apk add` e `npm ci --omit=dev` concluídos pelos destinos previstos;
- oito caminhos ausentes, `node` presente e cinco gerenciadores não invocáveis;
- `/health/live` em 200 e `/status` em 200 com
  `connected=false` e `hasQr=false`;
- Trivy 0.70.0 com zero achados HIGH/CRITICAL e exit 0;
- cleanup nominal, `Build Cache 0B` e base Node preservada.

Essa prova fecha antecipadamente a cadeia externa e a mecânica de execução. O
executor ainda deve reproduzi-la sobre o Dockerfile versionado e produzir os
artefatos formais da S31.

## 2. Delta técnico único

No `whatsapp_service/Dockerfile`, substituir somente a diretiva inicial por:

```dockerfile
# syntax=docker.io/docker/dockerfile@sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720
```

O digest foi resolvido e reconfirmado como manifesto OCI `linux/amd64` de
`docker/dockerfile:1.7`. A referência flutuante deixa de integrar a imagem
medida.

Não criar regra genérica de pin nesta slice nem alterar outros Dockerfiles. Esse
endurecimento transversal fica fora da remoção dos gerenciadores do runtime.
Preservar integralmente as demais alterações herdadas no Dockerfile, no
validador e nos testes.

## 3. Execução já reconciliada

O preflight limpo da task não se repete. Confirmar:

```bash
test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"
test "$(git branch --show-current)" = "main"
test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"
test "$(git log -1 --format=%s)" = "docs: consolidate S31 Docker parity plan"
test "$(git rev-list --count origin/main..HEAD)" = "8"
git rev-parse HEAD
git status --short
git diff --cached --name-only
git diff --check
```

Stage vazio; JSON S31 ausente; worktree exatamente com os três arquivos técnicos
modificados e o relatório não rastreado. Builder, imagens, container e volume
`s31-*` ausentes; base Node preservada; `Build Cache 0B`. Parar se divergir, sem
descartar trabalho herdado.

Como houve uma nova alteração no Dockerfile, executar uma vez os treze
validadores, as sete suítes e os 28 casos S31. Não acrescentar testes fora da
matriz existente.

## 4. Builder e rede fechados

Criar o builder nominal com a referência já autorizada pela correction-01 e
com a rede do container explicitamente alinhada ao host:

```bash
docker buildx create \
  --name s31-whatsapp-builder-10db6b3 \
  --driver docker-container \
  --driver-opt image=docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684 \
  --driver-opt network=host \
  --platform linux/amd64 \
  --use
```

Provar por inspeção o digest, `NetworkMode=host`, BuildKit em execução e
plataforma `linux/amd64`. A rede host corrige o timeout observado no DNS da
bridge local; não amplia a autoridade de acesso.

A lista completa de leitura para build e scan é:

- `auth.docker.io` e `registry-1.docker.io`, somente para BuildKit, frontend,
  base Node e Trivy nas referências fechadas pelo contrato;
- `dl-cdn.alpinelinux.org`, somente os repositórios configurados na base;
- `registry.npmjs.org`, somente os recursos presentes no lockfile;
- `mirror.gcr.io/aquasec/trivy-db:2`, somente o banco de vulnerabilidades.

Para OCI, a autorização é vinculada à referência e ao digest do artefato, não
ao nome transitório de cada endpoint HTTP: serviços de token e URLs assinadas de
blob/CDN emitidos pelo próprio registry integram a mesma leitura. Eles não
autorizam outro repositório, tag ou artefato. Aplicar a mesma semântica fechada
ao banco `mirror.gcr.io/aquasec/trivy-db:2`.

Executar Trivy com `--skip-version-check` para eliminar consulta não necessária.
GHCR, outro mirror, login, push e publicação permanecem proibidos.

## 5. Retomada e tolerância a falhas transitórias

Executar o build canônico da correction-01, agora lendo diretamente o
Dockerfile corrigido. São permitidas no máximo três tentativas totais do mesmo
build, no mesmo builder e com os mesmos argumentos. Repetir somente diante de
timeout, falha DNS, reset de conexão, erro TLS/I/O de transporte ou
indisponibilidade temporária de um destino autorizado. Manter o cache nominal
entre as tentativas e registrar todas elas.

Qualquer erro de Dockerfile, pacote, teste, política, digest, destino ou runtime
interrompe imediatamente, sem fallback. O scan Trivy pode seguir a mesma regra
de até três tentativas idênticas apenas para falha de transporte.

Depois do build, concluir sem nova delegação intermediária: inspeção; Trivy;
smoke determinístico; JSON e linhagem; cleanup nominal; relatório; stage dos
cinco caminhos; dois secret scans; `git diff --cached --check`; commit local
único com:

```bash
git commit -m "fix: remove package managers from whatsapp runtime image"
```

Manter `sourceSha=10db6b3ec4c07025fbe1eed1dbc20c34c2b77f0a`; a diretiva pinada integra o
`sourceTreeSha` e o `sourceDiffSha256` dos mesmos três arquivos técnicos.

No relatório, preservar as execuções anteriores, remover somente a linha
terminal atual e acrescentar:

```text
## 10. Execução da correction-02 — paridade Docker consolidada
```

Ao final, remover apenas builder, container, volume, cache e imagens criados
nominalmente pela execução. Sem prune amplo. O relatório termina com
`IN_PROGRESS — aguardando revisão do orquestrador`. Não fazer push.

## 6. Prompt formal

```text
Retome exclusivamente a S31 pela correction-02 em
/home/gregorio/git/baronesa/emporio.

Leia integralmente a task S31, as corrections 01 e 02 e o relatório existente.
A correction-02 substitui as instruções conflitantes de paridade Docker. Faça o
preflight do checkpoint documental, preserve o trabalho herdado e aplique apenas
o pin do frontend já prescrito ao Dockerfile.

Execute a matriz local uma vez e depois todo o caminho restante: builder BuildKit
pinado com network=host, build linux/amd64, inspeção, Trivy com
--skip-version-check, smoke desabilitado para inicialização real, JSON, cleanup,
relatório, dois secret scans e o commit único da task. Use no máximo três
tentativas idênticas por build ou scan somente para falhas transitórias de
transporte. Não improvise mirror, digest, DNS, fallback, prune ou alteração fora
da fronteira.

Não faça push. Entregue o SHA local, comandos, exits, achados, resíduos e a linha
terminal IN_PROGRESS para revisão do orquestrador.
```
