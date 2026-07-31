# S30 — Ensaio remoto de candidato, publisher e release

> **Estado:** `IN_PROGRESS` — emenda-01 autoriza primeiro commit e push  
> **Tipo:** preflight local e ensaio remoto controlado de GitHub/GHCR  
> **Executor previsto:** CLI  
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Dependências:** S01–S29 `ACCEPTED`  
> **Relatório de saída:** `S30-ensaio-remoto-candidato-publisher-release.report.md`

## 0. Autoridade e entrada

Leia antes de agir:

1. `docs/infrastructure/deployment/implementation/HANDOFF_ORQUESTRADOR.md`;
2. `docs/infrastructure/deployment/implementation/README.md`;
3. a arquitetura e os relatórios S11–S29;
4. `docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md`;
5. `docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md`;
6. `docs/infrastructure/deployment/release-control/UI_PUBLISHER.md`;
7. `docs/infrastructure/deployment/release-control/RELEASES.md`;
8. os cinco workflows em `.github/workflows/` e seus validadores.
9. `S30-ensaio-remoto-candidato-publisher-release.amendment-01.md`.

Esta task é o contrato fechado. Não a altere, não aceite a slice e não crie
S31. O relatório registra evidências; somente o orquestrador muda o estado.

Há duas fases independentes, conforme a emenda-01 vigente:

- **Fase 0 — preflight local:** autorizada nesta delegação, somente leitura e
  sem rede. Prepara o ensaio, verifica os contratos e registra bloqueios.
- **Fase 1 — ensaio remoto:** autorizada pela emenda-01 para criar o primeiro
  commit e fazer push somente após os gates fechados nela. A observação de
  CI/candidato é permitida após o push. Publicação da release, replay,
  configuração de credenciais e cleanup destrutivo continuam exigindo
  autorização/dados específicos.

O repositório canônico é `greggorio/abaronesa-emporio`, na branch `main`. O
primeiro push do executor está autorizado somente pela emenda-01 e somente
para o remote exato `git@github.com:greggorio/abaronesa-emporio.git`.

## 1. Objetivo

Validar, primeiro localmente e depois em ensaio remoto autorizado, a cadeia:

```text
push autorizado do executor -> CI em main -> candidato por digest/proveniência
-> UI publisher -> publish-release.yml -> release global reconciliada
```

O resultado deve provar:

- permissões mínimas e identidade separada do GitHub App publisher;
- CI real verde para o commit exato e candidato publicado por digest;
- manifesto, artefatos, provenance e attestation coerentes;
- publicação de uma release global exclusivamente pela UI/runtime publisher;
- correlação de `operationId`, workflow, release, tag e artefatos;
- recuperação após reinício e idempotência sem redispatch indevido;
- ausência de qualquer alteração em deploy, rollback ou produção.

Configuração de App, variáveis, secrets e environments não pode ser inventada.
Secrets reais nunca devem ser lidos, copiados ou transcritos no relatório.

## 2. Fronteira autorizada

### 2.1 Fase 0 local

Pode criar ou alterar somente o relatório S30. Não altere código, workflows,
contratos, configuração, task, tracker ou qualquer relatório anterior.

Pode executar validadores e inspeções locais sem rede, sem instalar
dependências e sem criar commit, índice ou tag.

### 2.2 Fase 1 remota, conforme a emenda-01

Pode executar apenas as ações nomeadas na autorização do orquestrador:

- consultar o repositório canônico, runs, artifacts, releases, checks,
  attestation e manifestos;
- verificar a instalação e as permissões mínimas do GitHub App publisher;
- configurar somente uma permissão/variável explicitamente nomeada, se a
  autorização permitir; provisionamento de secret continua sendo humano;
- usar a UI/runtime publisher para solicitar uma única release global;
- observar a reconciliação, um replay idempotente ou restart controlado apenas
  se isso estiver incluído na autorização;
- registrar IDs, URLs, SHA e digests, nunca tokens, headers ou valores secretos.
- criar o primeiro commit e fazer `git push --set-upstream origin main` após os
  gates da emenda-01, sem force, tags, outra branch ou outro remote;

Não disparar `publish-release.yml` diretamente fora da UI/runtime. Não executar
`deploy-production.yml`, `rollback-production.yml` ou qualquer mutação de
produção.

## 3. Requisitos fechados

### 3.1 Preflight local obrigatório

Antes de qualquer eventual autorização remota, confirme:

- os cinco workflows esperados e todas as actions fixadas por SHA de 40
  hexadecimais;
- validadores de CI/candidate/release/deploy/rollback/release-control verdes;
- scanner canônico limpo, sem imprimir valores sensíveis;
- contratos `RUNTIME_PUBLISHER`, `UI_PUBLISHER` e `RELEASES` coerentes;
- target fixo `greggorio/abaronesa-emporio`, branch `main` e workflow
  `publish-release.yml`, sem consultar a rede;
- ausência de autorização para Fase 1, se ela não tiver sido fornecida;
- plano de parada e checklist remoto sem inventar App ID, actor ID, candidate
  ID, commit SHA, run ID, release, token ou credencial.

### 3.2 Gates remotos, se autorizados

- O CI observado deve corresponder ao commit exato aprovado e terminar verde.
- `publish-candidate.yml` deve ser o resultado da cadeia prevista, com
  manifesto validado, imagens referenciadas por digest imutável, provenance e
  attestation verificáveis, sem uso de `latest`.
- O candidato deve ser revalidado server-side antes de ser usado. A release
  deve receber `candidate_id`, `version_bump`, descrição e changelog aprovados
  explicitamente; se qualquer um faltar, parar antes do POST.
- A publicação deve partir da UI de desenvolvimento e do endpoint/runtime
  canônico, com uma única tentativa idempotente. Registrar o `operationId` e
  o estado reconciliado sem expor o bearer.
- O workflow observado deve ser `publish-release.yml` no repositório e ref
  canônicos. O outcome deve cruzar run, attempt, tag, release, manifesto,
  source commit, assets e digests.
- Após um restart ou replay autorizado, a operação deve ser reconciliada sem
  redispatch indevido; conflito, timeout ou incerteza devem permanecer nos
  estados e códigos contratuais.
- Não pode existir execução ou efeito em `deploy-production.yml`,
  `rollback-production.yml`, SSH, VPS, DNS, Nginx, Docker ou produção.

### 3.3 Cleanup e parada

- Não apagar release, tag, artifact, pacote ou run sem autorização específica.
- Registrar no relatório os recursos criados, sua retenção e o cleanup
  necessário; uma release deliberadamente publicada não deve ser removida por
  iniciativa do executor.
- Parar imediatamente diante de identidade ambígua, candidate divergente,
  digest/provenance inválido, workflow inesperado, segredo exposto, efeito de
  produção, operação concorrente não autorizada ou falta de dado obrigatório.

## 4. Matriz terminal obrigatória

### 4.1 Fase 0 — sempre executar localmente

Execute a partir do CWD indicado e registre comando, exit, duração/contagem,
saída literal sanitizada e interpretação:

```bash
cd /home/gregorio/git/baronesa/emporio
python3 tools/ci/validate_workflow_inventory.py
python3 tools/candidates/validate_candidate_workflow.py
python3 tools/releases/validate_release_workflow.py
python3 tools/deploy/validate_deploy_workflow.py
python3 tools/deploy/validate_rollback_contract.py
python3 tools/deploy/validate_rollback_runtime.py
python3 tools/releases/release_control_contract.py validate
git ls-files --cached --others --exclude-standard -z | xargs -0 python3 tools/ci/secret_scan.py
git status --short
git remote -v
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog show --all
git diff --check
```

`git rev-parse --verify HEAD` deve retornar 128 se o workspace ainda estiver
pré-Git. Não use `git ls-remote` no preflight. Se `actionlint` estiver
instalado, pode executá-lo localmente; não instalar nem buscar a ferramenta.

### 4.2 Fase 1 — somente com autorização explícita

Registrar, sem tokens:

- commit/ref, workflow, run ID, attempt, resultado e URL do CI;
- run do candidato, candidate ID, manifest digest, image digests, provenance,
  attestation e artefatos;
- request público sanitizado, `operationId`, workflow publisher, release/tag,
  assets, digests e outcome;
- restart/replay autorizado, estado antes/depois e prova de não redispatch;
- consultas de produção comprovadamente não realizadas e cleanup pendente.

## 5. Proibições

Sem autorização adicional, não usar `gh`, GitHub API, GHCR, registry, rede,
workflow remoto, `git ls-remote`, SSH, VPS, DNS, Docker, containers, Postgres,
produção, instalação ou secrets reais. O push exato autorizado pela emenda-01
é a exceção única.

Mesmo com autorização da Fase 1, não fazer push, deploy, rollback, exclusão
remota, alteração de produção, criação de credenciais ou leitura de secrets.
Não alterar arquivos fora do relatório S30 e não criar S31.

## 6. Relatório obrigatório

Crie somente:

`docs/infrastructure/deployment/implementation/slices/S30-ensaio-remoto-candidato-publisher-release.report.md`

O relatório deve conter: fase executada; CWD; autorização recebida ou
ausente; arquivos alterados; pré-condições; comandos e exits; workflow,
candidate, release, provenance, attestation, idempotência, restart, cleanup,
produção, Git, resíduos, acessos e divergências. IDs podem aparecer; tokens,
secrets, headers e valores sensíveis nunca.

Se a Fase 1 não estiver explicitamente autorizada, registrar o preflight e
terminar exatamente com:

```text
IN_PROGRESS — aguardando autorização externa e revisão do orquestrador
```

Se a Fase 1 for autorizada e todos os gates passarem, ainda termine com
`IN_PROGRESS — aguardando revisão do orquestrador`. Não declarar `ACCEPTED`.

## 7. Prompt formal para delegação

```text
Execute exclusivamente a S30 em /home/gregorio/git/baronesa/emporio.
Leia o HANDOFF_ORQUESTRADOR, tracker, arquitetura, relatórios S11–S29,
RUNTIME_PUBLISHER, RUNTIME_DEPLOYER, UI_PUBLISHER, RELEASES e os cinco
workflows antes de agir.

Execute a Fase 0 e, se todos os gates da emenda-01 passarem, a ativação Git
autorizada. Valide os cinco workflows, actions por SHA, contratos de
CI/candidate/release/deploy/rollback/release-control e o scanner canônico.
Confirme CWD, remote exato, branch main, lista candidata e ausência de
segredos/resíduos proibidos.

Prepare no relatório S30 o checklist do ensaio para greggorio/abaronesa-emporio,
main e publish-release.yml, os gates de digest/provenance/attestation, os
dados obrigatórios da release, o protocolo de idempotência/restart e o plano
de parada/cleanup. Não invente candidate ID, SHA, run ID, release, App ID,
actor ID, token ou credencial.

Se os gates passarem, execute exatamente `git add -A`,
`git commit -m "chore: establish initial emporio baseline"` e
`git push --set-upstream origin main`. Não use force, tags, outra branch,
outro remote, no-verify, git init ou alteração de identidade global. Depois,
observe CI e publish-candidate.yml no GitHub sem publicar release. Não use
publish-release.yml direto, não crie credenciais e não crie S31.

Altere somente o relatório S30. Registre comandos, exits, saídas sanitizadas,
interpretação e divergências. Termine com:
IN_PROGRESS — aguardando autorização externa e revisão do orquestrador
```
