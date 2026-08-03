# S40 — Publicação inaugural da imagem do release control

> **Data:** 03/08/2026
> **Predecessora:** S39 aceita
> **Tipo:** uma mutação controlada em GitHub Actions/GHCR
> **Produção/VPS:** proibidas

## 1. Objetivo

Publicar pela primeira vez a imagem operacional do `release_control` pelo único
caminho contratado:

```text
allowlist exclusiva -> workflow_dispatch em main
-> trust -> verify -> publish -> outcome
-> GHCR por digest + manifesto/sidecar + outcome terminal
```

A S40 deve:

1. configurar a allowlist exclusiva com o actor ID humano autenticado;
2. produzir uma única intenção e um único dispatch;
3. executar o workflow publicado pela S39 sem qualquer input;
4. publicar uma única versão inicial da imagem no GHCR;
5. validar run, jobs, logs, pacote, digest e artifacts de forma cruzada;
6. preservar a separação do BOM comercial e todos os negativos de produção.

A imagem será publicada, mas **não será instalada nem executada na VPS** nesta
slice.

## 2. Autorização humana obrigatória

As mutações da S40 somente estão autorizadas se a mensagem de delegação enviada
diretamente pelo usuário contiver literalmente:

```text
Autorizo integralmente a S40: configurar RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS com meu actor ID autenticado, executar uma única vez Publish Release Control Image em main, publicar a primeira imagem do release_control no GHCR e preservar os artefatos e logs para auditoria.
```

Sem essa frase na delegação, executar apenas o snapshot read-only da §4 e parar
antes de criar variável ou dispatch. O executor não pode inferir autorização do
texto desta task.

A autorização cobre somente:

- criar/atualizar a variável de repositório
  `RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS` com um actor ID decimal;
- um único `workflow_dispatch` de `publish-release-control.yml` em `main`;
- o login/push/logout internos do job `publish` com `GITHUB_TOKEN`;
- criação do package/version/tag de transporte e dos artifacts desse run.

Ela não autoriza VPS, environment `production`, App deployer, SSH, Nginx, TLS,
deploy, rollback, release global, Git tag, exclusão ou mudança de código.

## 3. Identidade e source SHA fechados

No snapshot de aceite da S39:

```text
repository   greggorio/abaronesa-emporio
main remoto  daaa7061ab9f7a722b17e37c0f060f45141225e7
actor        greggorio
actor ID     35626201
workflow     .github/workflows/publish-release-control.yml
package      ghcr.io/greggorio/abaronesa-emporio-release-control
```

Revalidar tudo antes de mutar. O actor do `gh auth status`, `gh api user` e do
dispatch deve ser a mesma identidade acima. Se conta, actor ID ou `main` tiverem
mudado, parar; não adaptar a allowlist nem publicar outro SHA.

A allowlist deve conter exatamente `35626201`, sem espaços, duplicatas ou outro
ID. Não reutilizar `RELEASE_PUBLISHER_ACTOR_IDS` ou `DEPLOYER_ACTOR_IDS`.

## 4. Snapshot inicial read-only

Executar e registrar comandos e exits individualmente:

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD
git ls-remote origin refs/heads/main
git log --oneline --decorate -10
sha256sum docs/infrastructure/deployment/implementation/slices/S40-publicacao-inaugural-imagem-release-control.task.md
gh auth status
gh api user --jq '{login,id}'
gh api repos/greggorio/abaronesa-emporio/actions/workflows/publish-release-control.yml
gh run list --workflow publish-release-control.yml --limit 100 --json databaseId,event,status,conclusion,headSha,attempt
gh variable list --json name,updatedAt
gh api /user/packages/container/abaronesa-emporio-release-control
gh api repos/greggorio/abaronesa-emporio/releases
gh api repos/greggorio/abaronesa-emporio/git/matching-refs/tags/
gh run list --workflow deploy-production.yml --limit 100 --json databaseId
gh run list --workflow rollback-production.yml --limit 100 --json databaseId
git diff --check
git diff --cached --name-only
```

`404` da variável ou do package deve ser registrado como `MISSING`, nunca
mascarado. O baseline obrigatório é:

- `origin/main` e remoto em `daaa7061...`;
- workflow `active`, zero runs;
- allowlist ausente;
- package ausente;
- uma tag e uma release, ambas `v0.1.0`;
- zero runs de deploy e rollback;
- stage vazio;
- relatório S39 não rastreado, mais nenhum resíduo alheio.

O `HEAD` local conterá o commit documental do orquestrador e estará à frente do
remoto. Isso é esperado: o workflow deve continuar usando o `main` remoto em
`daaa7061...`; não fazer push dos documentos.

## 5. Gates locais antes da mutação

Sem alterar arquivos, executar:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  tools.deploy.tests.test_release_control_image \
  tools.deploy.tests.test_validate_release_control_workflow -v
python3 tools/deploy/validate_release_control_package.py
python3 tools/deploy/validate_release_control_workflow.py
python3 tools/ci/validate_workflow_inventory.py
python3 tools/releases/validate_release_workflow.py
git diff --check
```

Todos devem retornar `0`. Confirmar estaticamente que o workflow não aceita
inputs, que `packages: write` existe apenas em `publish`, que o scan precede o
login e que existem exatamente os jobs `trust`, `verify`, `publish`, `outcome`.

## 6. Intenção única

### 6.1 Configurar a allowlist

Com a autorização literal presente e todos os gates verdes:

1. obter novamente `login` e `id` pela API;
2. validar localmente que o ID é o decimal exato `35626201`;
3. criar a variável com valor exato `35626201`;
4. ler de volta a variável e provar nome/valor sem expor token;
5. confirmar que as duas outras allowlists não foram alteradas.

Não usar secret para a allowlist e não adicionar segundo actor.

### 6.2 Dispatch único

Imediatamente antes do dispatch:

- reconfirmar `main` remoto em `daaa7061...`;
- capturar a lista completa de runs do workflow como baseline;
- confirmar novamente package ausente;
- confirmar que a variável contém somente o actor ID autorizado.

Executar uma única vez:

```bash
gh workflow run publish-release-control.yml --ref main
```

Não fornecer `-f`, `--raw-field`, SHA, tag, imagem ou qualquer input.

Se a resposta do comando for ambígua, **não repetir**. Consultar a lista de runs:

- se houver exatamente um novo run do actor, anexar-se a ele;
- se não houver novo run, parar sem retry;
- se houver mais de um, parar e preservar todos para auditoria.

## 7. Observação do run

Identificar o run por diferença contra o baseline e exigir:

```text
event       workflow_dispatch
headBranch  main
headSha     daaa7061ab9f7a722b17e37c0f060f45141225e7
attempt     1
actor       greggorio / 35626201
jobs        trust, verify, publish, outcome
terminal    success nos quatro jobs
```

Observar até terminal sem rerun, retry ou cancelamento. Auditar logs e provar a
ordem real:

```text
trusted -> testes/validadores -> build único -> probe
-> Trivy estrito verde -> login -> push único -> digest remoto
-> manifesto -> logout/cleanup -> outcome published
```

Confirmar que nenhum token, header, Docker config, PEM ou credencial apareceu em
log. Credenciais mascaradas pelo GitHub não devem ser transcritas.

## 8. Artifacts e identidade da imagem

Baixar os artifacts em diretório temporário criado por `mktemp -d`, sem
sobrescrever arquivos do repositório:

```text
release-control-image-manifest
release-control-image-outcome
```

Um artifact auxiliar `.dockerbuild` gerado pela action pode existir, mas não é
identidade nem substitui os dois artifacts contratados.

Validar:

1. artifacts presentes, não expirados e vinculados ao run único;
2. manifesto JSON canônico e sidecar exato com
   `release_control_image.py validate`;
3. doze chaves exatas, sem tag de transporte;
4. `sourceSha == daaa7061...`;
5. `workflowRunId` e `workflowAttempt` iguais ao run observado;
6. actor e actor ID iguais à identidade autorizada;
7. `imageRepository` canônico;
8. `imageDigest` válido e
   `immutableRef == imageRepository + "@" + imageDigest`;
9. outcome canônico, sidecar válido, `status=published`, mesmo run/attempt,
   mesmo digest e `manifestSha256` igual ao hash do manifesto;
10. package GHCR criado, vinculado ao repositório e não público;
11. exatamente uma versão inicial e nenhuma tag `latest` ou SemVer;
12. tag de transporte determinística correspondente ao SHA, run e attempt;
13. digest registrado no package compatível com o manifesto, quando exposto
    pela API; ausência desse campo na API não autoriza inferência divergente.

Não fazer `docker login`, pull ou execução local da imagem. O build, scan e push
válidos são os do workflow observado.

## 9. Negativos posteriores

Depois do run, provar:

- um único run total do novo workflow;
- variável presente com somente `35626201`;
- nenhuma mudança em `v0.1.0`, Git tags ou GitHub Releases;
- nenhum novo CI/Publish Candidate causado pelo dispatch;
- zero deploy e rollback;
- `release_control` continua fora do BOM comercial;
- nenhum environment `production`, App deployer, SSH ou VPS acessado;
- nenhum commit, push, stage ou alteração de código/documentação;
- package, versão, logs e artifacts preservados; nada excluído.

## 10. Fail-closed e proibições

Na primeira divergência:

- não repetir dispatch;
- não executar rerun de job ou workflow;
- não editar workflow, Dockerfile, allowlist ou artifact para obter verde;
- não criar segunda tag/version;
- não apagar run, package, versão, log ou artifact;
- não fazer commit, push, rebase, reset, amend ou force;
- não acessar VPS, produção, deploy ou rollback;
- registrar a causa e parar.

Se a falha ocorrer depois do push da imagem, preservar o estado remoto e
classificar com precisão; não fabricar rollback por exclusão.

## 11. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S40-publicacao-inaugural-imagem-release-control.report.md
```

O relatório permanece não rastreado, não staged e não commitado. Deve conter:

- autorização literal recebida;
- snapshot Git/GitHub/GHCR inicial e final;
- SHA-256 da task;
- identidade autenticada e valor não sensível da allowlist;
- gates locais e exits;
- prova de intenção/dispatch únicos;
- run, attempt, actor, SHA e quatro jobs;
- ordem auditada do caminho de publicação;
- artifact IDs, nomes, vínculos, hashes e conteúdo sem segredos;
- immutableRef final por digest;
- package/version/tag de transporte;
- todos os negativos e resíduos;
- lista literal de mutações realizadas.

O executor não aceita S40, não cria a próxima slice e não inicia o Gate B.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — imagem operacional do release control publicada e validada; aguardando aceite e preparação do Gate B
```

Na primeira causa não resolvida:

```text
BLOCKED — S40 interrompida fail-closed na primeira causa técnica
```

## 12. Critérios de aceite

S40 somente será aceita quando:

- houver autorização humana literal;
- allowlist exclusiva contiver somente o actor ID validado;
- existir exatamente um dispatch/run, attempt 1, no SHA fechado;
- trust, verify, publish e outcome estiverem verdes;
- imagem estiver publicada por digest, sem `latest` ou SemVer;
- manifesto, sidecar e outcome estiverem íntegros e cruzados ao run/package;
- package não for público e possuir somente a versão inicial esperada;
- nenhum candidato, release global, deploy, rollback ou VPS tiver mudado;
- relatório contínuo estiver completo, local e fora do Git.
