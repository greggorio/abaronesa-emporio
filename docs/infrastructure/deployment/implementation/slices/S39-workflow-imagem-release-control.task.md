# S39 — Workflow independente da imagem do release control

> **Data:** 03/08/2026
> **Predecessora:** S38 aceita
> **Tipo:** implementação local, commit/push e validação remota sem execução do novo workflow
> **Produção/VPS:** proibidas

## 1. Objetivo

Implementar o ciclo operacional independente que falta para a imagem do
`release_control`, conforme a arquitetura:

1. workflow manual, seguro e fail-closed para construir, testar, escanear e
   publicar exclusivamente a imagem operacional do `release_control`;
2. referência final por digest, fora do BOM comercial de seis componentes;
3. manifesto sanitizado que vincule imagem, source SHA e run do workflow;
4. bases do Dockerfile fixadas por digest antes de qualquer publicação real;
5. validadores e testes causais do novo contrato;
6. documentação do procedimento independente de atualização;
7. um push fast-forward da implementação e validação de CI/Publish Candidate.

O workflow será publicado no repositório, mas **não será executado na S39**.
Nenhuma imagem `release_control` será enviada ao GHCR nesta slice.

## 2. Decisões arquiteturais fechadas

### 2.1 Separação do produto comercial

O `release_control` permanece em `excluded_operational_components` e nunca entra
em:

- `canonical_order` comercial;
- candidato comercial;
- BOM da release global;
- `publish-release.yml`;
- transação de deploy que ele próprio acompanha.

Seu repositório de imagem será exclusivamente:

```text
ghcr.io/greggorio/abaronesa-emporio-release-control
```

### 2.2 Disparo e identidade

O novo workflow:

- chama-se `Publish Release Control Image`;
- fica em `.github/workflows/publish-release-control.yml`;
- aceita somente `workflow_dispatch` sobre `main`;
- não recebe SHA, tag, image name, Dockerfile, command ou repository como input;
- usa o SHA do próprio dispatch em `main` como source SHA;
- exige allowlist decimal em
  `vars.RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS`;
- falha se repository, ref, event, actor ID ou SHA não forem exatamente válidos;
- não reutiliza `RELEASE_PUBLISHER_ACTOR_IDS` nem `DEPLOYER_ACTOR_IDS`.

A variável será criada somente numa autorização futura. Nesta slice ela deve
continuar `MISSING`, e o workflow não deve ser disparado.

### 2.3 Imutabilidade e publicação

- `latest`, SemVer comercial e tags escolhidas pelo operador são proibidos;
- a tag de transporte deve ser derivada somente de source SHA, run ID e attempt,
  sendo única para a execução;
- construir uma vez, carregar a imagem local exata, escanear antes do login e
  publicar essa mesma imagem uma vez;
- autenticar no GHCR somente depois de testes e scan verdes;
- a saída canônica é `repository@sha256:<64 hex>` obtida do registry depois do
  push;
- logout e remoção apenas da tag local exata devem ocorrer em `always()`;
- nenhum token, header, Docker config ou saída sensível entra no artifact.

Não criar Git tag, GitHub Release, pacote com nome diferente ou mutable alias.

### 2.4 Dockerfile

As duas instruções `FROM python:3.13-slim` devem passar a usar a mesma referência
por digest verificável para `linux/amd64`. O tag legível pode permanecer antes do
`@sha256`, mas o digest é obrigatório.

Não atualizar Python, `uv`, dependências ou código do runtime. A mudança é
somente o pin da base necessário para publicação reproduzível.

## 3. Snapshot inicial obrigatório

Antes de editar:

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD
git ls-remote origin refs/heads/main
git log --oneline --decorate -12
sha256sum docs/infrastructure/deployment/implementation/slices/S39-workflow-imagem-release-control.task.md
gh run list --workflow publish-release-control.yml --limit 10
gh variable list --json name --jq '.[].name'
git diff --check
```

Para workflow ainda inexistente e variável ainda ausente, exits/404 esperados
devem ser classificados como `MISSING`, não mascarados como sucesso.

O único arquivo não rastreado esperado será o relatório S39 criado durante a
execução. Qualquer outra divergência interrompe a slice antes de editar.

## 4. Contrato mínimo do workflow

Implementar no mínimo os jobs lógicos abaixo. É permitido separá-los em mais
jobs quando isso melhorar o isolamento de permissões, sem fundi-los de modo que
o login ocorra antes dos gates.

### 4.1 `trust`

- permissões somente `contents: read`;
- checkout do SHA recebido, histórico suficiente e
  `persist-credentials: false`;
- persistir o evento sem interpolar JSON em shell;
- validar repository, workflow path, event, `refs/heads/main`, SHA, run attempt,
  actor e actor ID decimal na allowlist;
- confirmar que o SHA é exatamente o head de `main` no momento da execução;
- produzir apenas outputs sanitizados necessários aos jobs seguintes.

### 4.2 `verify`

- instalar dependências pelo lock sem alterar `uv.lock`;
- executar `release_control/tests/` integralmente;
- executar os validadores de publisher, deployer, rollback e pacote S28;
- validar que `release_control` continua fora do BOM comercial;
- validar estaticamente base pinada, usuário final `10001:10001`, healthcheck e
  labels OCI esperadas;
- não construir, autenticar nem publicar neste job.

### 4.3 `publish`

- depende integralmente de `trust` e `verify` verdes;
- única fronteira com `packages: write`;
- construir a imagem `linux/amd64` exatamente uma vez nesse job, carregando uma
  tag local derivada somente do contexto confiável;
- provar usuário final `10001:10001`, healthcheck e labels OCI esperadas;
- escanear a imagem exata com Trivy, falhando em `HIGH` ou `CRITICAL`, inclusive
  vulnerabilidades ainda sem correção;
- login no `ghcr.io` com `github.actor` e `github.token` somente depois do scan;
- reconstrução entre scan e push é proibida;
- executar um único `docker push` da tag de transporte determinística;
- resolver e validar o digest remoto;
- gerar manifesto canônico e sidecar SHA-256;
- fazer upload do artifact `release-control-image-manifest` com retenção
  explícita e `overwrite: false`.

### 4.4 `outcome`

- executar com `if: always()` e permissões somente leitura;
- produzir artifact terminal sanitizado mesmo quando trust, verify ou publish
  falhar;
- nunca transformar falha, skip inesperado ou estado indeterminado em sucesso;
- sucesso exige manifesto, sidecar, digest e vínculo exatos;
- artifact terminal: `release-control-image-outcome`.

## 5. Manifesto operacional

Criar um schema/validador em Python standard library, sem dependência nova. O
manifesto de sucesso deve ser JSON canônico e conter somente:

```text
schemaVersion
kind = release-control-image
repository = greggorio/abaronesa-emporio
sourceSha
imageRepository
imageDigest
immutableRef
workflowRunId
workflowAttempt
actor
actorId
publishedAt
```

Invariantes:

- chaves exatas, sem extensão arbitrária;
- SHA de 40 hex minúsculos;
- digest `sha256:` com 64 hex minúsculos;
- immutableRef composto exatamente do repository e digest anteriores;
- run ID/attempt/actor ID positivos;
- actor no formato GitHub contratado;
- timestamp UTC normalizado;
- nenhum tag de transporte como identidade final;
- nenhum segredo ou caminho local.

O sidecar deve conter somente o hex SHA-256 do manifesto seguido de LF.

## 6. Testes causais obrigatórios

Cobrir no mínimo a rejeição de:

1. evento diferente de `workflow_dispatch`;
2. branch/ref diferente de `main`;
3. repository, workflow path ou SHA divergentes;
4. actor ID ausente, não decimal, zero ou fora da allowlist;
5. input capaz de escolher SHA, tag, repository, Dockerfile ou command;
6. action sem pin de SHA completo;
7. `packages: write` fora do job de publicação;
8. login anterior a testes/scan;
9. `latest`, tag SemVer ou referência sem digest;
10. segundo build entre scan e push;
11. base do Dockerfile sem digest ou digests diferentes nos dois estágios;
12. Trivy ignorando `HIGH`, `CRITICAL` ou `ignore-unfixed=true`;
13. artifact sem sidecar, com overwrite ou nome divergente;
14. manifesto com campo extra, digest/SHA/run inválido ou immutableRef divergente;
15. `release_control` entrando no candidato/BOM comercial;
16. workflow criando tag, release, deploy, rollback ou acessando SSH/VPS;
17. outcome verde quando qualquer predecessor falha ou é pulado indevidamente.

## 7. Fronteira de arquivos

Permitidos somente os arquivos necessários dentro de:

```text
.github/workflows/publish-release-control.yml
.github/workflows/ci.yml
release_control/Dockerfile
release_control/README.md
ops/env/release-control.env.example
docs/infrastructure/deployment/release-control/OPERACAO_RELEASE_CONTROL.md
tools/deploy/release_control_image.py
tools/deploy/validate_release_control_workflow.py
tools/deploy/tests/test_release_control_image.py
tools/deploy/tests/test_validate_release_control_workflow.py
tools/deploy/validate_release_control_package.py
tools/deploy/tests/test_validate_release_control_package.py
tools/ci/validate_workflow_inventory.py
tools/ci/tests/test_validate_workflow_inventory.py
```

Não é obrigatório tocar todos. Preferir o menor diff que feche o contrato.

Não alterar `release_control/src`, migrations, schemas comerciais, workflows de
candidato/release/deploy/rollback, Compose comercial, state machines ou UI.

## 8. Validação local

Executar testes direcionados do novo validador e manifesto, depois:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q release_control/tests
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v
python3 tools/deploy/validate_release_control_package.py
python3 tools/deploy/validate_release_control_workflow.py
python3 tools/releases/release_control_contract.py validate
python3 tools/releases/validate_publisher_runtime.py
python3 tools/deploy/validate_deployer_runtime.py
python3 tools/deploy/validate_rollback_runtime.py
python3 tools/ci/validate_ci.py
python3 tools/ci/validate_workflow_inventory.py
python3 tools/releases/catalog.py validate
python3 tools/ci/secret_scan.py --tracked
git diff --check
```

Executar também os 17 validadores e as oito suítes canônicas enumerados na S38.
Todos os exits devem ser `0`; scanner `clean`, `unsupported=0`; nenhuma falha ou
skip novo.

Uma prova Docker local é obrigatória antes do commit:

- build `linux/amd64` com tag temporária única;
- inspeção do usuário, healthcheck e digest/config da imagem;
- Trivy `HIGH,CRITICAL` verde com `ignore-unfixed=false`;
- remoção dirigida apenas da imagem/tag criada;
- zero container, volume ou rede residual.

Não fazer login nem push nessa prova.

## 9. Commit, push e validação remota autorizados

Após todos os gates locais verdes:

1. reconfirmar que o remoto ainda é o SHA inicial;
2. stagear somente implementação/testes/documentação permitidos;
3. manter o relatório S39 não staged;
4. criar um único commit causal;
5. fazer um único push normal e fast-forward de `main`;
6. observar CI e Publish Candidate desse SHA até terminal;
7. validar jobs e artifacts desses dois runs;
8. confirmar que o novo workflow existe no default branch e possui **zero
   runs**;
9. confirmar que o pacote GHCR do release control continua ausente;
10. confirmar que `v0.1.0`, tag, release, deploy e rollback não mudaram.

Se o remoto mover, um gate falhar ou o push não for fast-forward, parar sem
rebase, retry ou expansão de escopo.

## 10. Proibições

Nesta slice é proibido:

- executar `publish-release-control.yml`;
- criar `RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS`;
- fazer login ou push no GHCR fora da CI comercial já automática;
- publicar a imagem do `release_control`;
- criar Git tag ou GitHub Release;
- executar publish-release, deploy ou rollback;
- acessar ou mutar a VPS, DNS, TLS, Nginx ou firewall;
- abrir/criar segredos, `.env` real, chave ou token;
- usar cache de imagem como prova de identidade;
- apagar runs, packages, artifacts, logs ou tags;
- usar force push, rebase, amend ou segundo push.

## 11. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S39-workflow-imagem-release-control.report.md
```

O relatório permanece não staged e não commitado e deve conter:

- snapshot Git/GitHub inicial e final;
- SHA-256 da task;
- decisões implementadas e arquivos alterados;
- testes causais e mutantes;
- comandos, exits, contagens e interpretação;
- prova Docker local e cleanup dirigido;
- commit/push e runs CI/candidato, se alcançados;
- prova de zero runs do novo workflow e zero imagem publicada;
- negativos de release, deploy, rollback e VPS;
- resíduos finais.

O executor não aceita S39, não executa o novo workflow e não cria a próxima
slice.

Terminar exatamente com:

```text
IN_PROGRESS — workflow da imagem do release control pronto; aguardando aceite e autorização de publicação
```

ou, na primeira causa não resolvida:

```text
BLOCKED — S39 interrompida fail-closed na primeira causa técnica
```

## 12. Critérios de aceite

S39 somente será aceita quando:

- o workflow independente estiver versionado no default branch, sem execução;
- trust, permissões, build único, scan anterior ao login, push único futuro,
  manifesto/sidecar e outcome estiverem causalmente validados;
- Dockerfile estiver pinado por digest nos dois estágios;
- `release_control` continuar fora do BOM comercial;
- prova Docker local e matriz completa estiverem verdes e sem resíduos;
- CI e Publish Candidate do commit estiverem verdes;
- o novo workflow tiver zero runs e o pacote GHCR ainda não existir;
- nenhuma release, produção, VPS, deploy ou rollback tiver sido alterado;
- relatório estiver completo, local e fora do commit.
