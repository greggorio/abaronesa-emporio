# S30a — Paridade local e fechamento da CI e do candidato

> **Estado:** `PLANNED`
> **Tipo:** fechamento da cadeia CI -> candidato, com prova local antes da remota
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Dependências:** S01–S29 `ACCEPTED`; S30 `REJECTED`, dividida por esta task
> **Commit-base:** `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`
> **Relatório de saída:** `S30a-paridade-local-fechamento-ci-candidato.report.md`

## 1. Por que esta slice existe

A S30 acumulou quatro ciclos remotos e oito defeitos (A–H, mais G-linha-67 e o
irmão de E). Todos eram preexistentes e latentes. Nenhum foi introduzido pelas
correções. O problema não são os defeitos: é o mecanismo de descoberta.

Duas causas estruturais:

1. **Os contratos afirmam texto, não comportamento.** `validate_ci.py` verifica
   que a string `python3 tools/security/bootstrap_contract.py` existe no YAML e
   nunca verifica que o comando é invocável. Foi assim que G nasceu e é por isso
   que as linhas 67 e 69 continuam quebradas.
2. **`set -e` num passo de onze comandos, mais dependência entre jobs, revela um
   defeito por execução remota.** O custo unitário é contrato, execução, commit
   permanente, push e uma rodada de CI.

A superfície ainda não exercitada remotamente é grande: o job `images` da CI
(seis builds e seis scans) e quarenta e cinco passos em cinco jobs do
`publish-candidate` (`predecessor`, `build`, `assemble`, `integrated`,
`publish`). São vinte e sete comandos Python afirmados por texto nos dois
workflows. Mantido o ritmo atual, isso são dezenas de ciclos.

Esta slice troca o mecanismo: prova local primeiro, remoto depois.

## 2. Divisão da S30

A S30 original acumulava CI verde, candidato publicado, release global pela UI,
idempotência e restart num único aceite. Ela passa a ser o contrato-pai
histórico e é dividida:

- **S30a (esta):** CI verde para o commit exato e candidato publicado por
  digest, com manifesto, provenance e attestation coerentes;
- **S30b (futura, não criar):** release global pela UI/runtime publisher,
  reconciliação, idempotência e restart.

Não criar S30b, S31 ou qualquer outra slice. O relatório da S30 permanece como
está; esta slice escreve apenas o próprio relatório.

## 3. Objetivo observável

Ao final, para um único commit novo:

1. o run de `ci.yml` conclui **verde** em `plan`, `contracts`, `backend`,
   `website_back`, `frontend`, `website_front`, `whatsapp` e `images`;
2. o run de `publish-candidate.yml` conclui **verde** em `trust`,
   `predecessor`, `build`, `assemble`, `integrated` e `publish`;
3. existem os artifacts `candidate-manifest` e `candidate-outcome`;
4. o manifesto contém os seis componentes comerciais por digest imutável, sem
   `latest`, com provenance e attestation verificáveis;
5. nada de release, tag, deploy, rollback ou produção foi tocado.

## 4. Decisões fechadas do orquestrador

Estas decisões estão fechadas. O executor implementa; não escolhe alternativa.

### 4.1 Fechar a família G

Corrigir em `.github/workflows/ci.yml`:

```text
linha 67   python3 tools/security/bootstrap_contract.py   -> ... validate
linha 69   python3 tools/docker/java_images_contract.py   -> ... validate
```

A auditoria já feita fechou a família: as outras cinco chamadas do job
`contracts` estão corretas. Não alterar nenhuma delas.

### 4.2 Gate de invocabilidade

Implementar um validador que, para **cada** invocação `python3 tools/**.py …`
presente em `ci.yml` e em `publish-candidate.yml`, prove que a linha de comando
exata é aceita pela CLI do utilitário.

Requisitos do gate:

- extrair a invocação como escrita no YAML, substituindo expressões
  `${{ … }}` por valores sintaticamente válidos e fixos (SHA de 40 hexadecimais,
  inteiro positivo, caminho);
- provar aceitação **interrompendo na fronteira de `parse_args`**, de modo que
  nenhum efeito colateral do utilitário seja executado: sem rede, sem Docker,
  sem escrita fora de diretório temporário, sem GitHub;
- falhar quando a invocação for recusada pela CLI, inclusive por subcomando
  obrigatório ausente ou flag desconhecida;
- reportar todos os comandos recusados de uma vez, nunca apenas o primeiro.

O mecanismo de interceptação é escolha do executor, desde que satisfaça os
quatro requisitos. Se algum utilitário executar trabalho relevante antes de
`parse_args`, registrar o caso no relatório em vez de contorná-lo.

Mutantes obrigatórios, todos devendo ser rejeitados:

1. remover `validate` de `release_control_contract.py`;
2. remover `validate` de `bootstrap_contract.py`;
3. remover `validate` de `java_images_contract.py`;
4. acrescentar uma flag inexistente a qualquer invocação;
5. trocar um subcomando por outro inexistente;
6. remover uma flag obrigatória de qualquer invocação.

O gate deve ser executado pela própria CI, no job `contracts`.

### 4.3 Imagem PostgreSQL do job `backend`

Convergir `ci.yml` para o mesmo digest imutável que o `publish-candidate.yml`
já usa, substituindo a tag flutuante:

```text
postgres:16.6-alpine
->
postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
```

`POSTGRES_DB=testdb`, `POSTGRES_USER=test`, `POSTGRES_PASSWORD=test`, a porta e
o healthcheck permanecem exatamente como estão. Atualizar `validate_ci.py` e
`test_ci.py` para exigir o digest e rejeitar tag flutuante, inclusive `latest` e
qualquer `postgres:<tag>` sem `@sha256:`.

### 4.4 Paridade local antes do push

Docker local está **autorizado** para esta slice, revogando a proibição herdada
da S30, exclusivamente para build e scan locais. Antes de qualquer commit,
executar localmente e registrar:

- os seis builds da matriz `images` de `ci.yml`, com o mesmo contexto,
  dockerfile, plataforma `linux/amd64` e `load: true`/`push: false`;
- o scan Trivy de cada imagem, com `severity: HIGH,CRITICAL` e
  `ignore-unfixed: false`, exatamente como o workflow faz. Trivy não está
  instalado: usar a imagem oficial fixada por digest, sem instalar binário no
  host;
- remoção local das imagens ao final, provando ausência de resíduo.

**Não alterar a política do Trivy nesta slice.** Se houver achados
HIGH/CRITICAL, inventariá-los por componente, severidade, identificador e
condição de correção disponível, e **parar antes do commit**. A decisão de
política é do orquestrador e será tomada com esse inventário na mão.

`push: false` no job `images` significa que nada vai para registry na CI. O
`build` do `publish-candidate` publica em GHCR; ele não é reproduzível
localmente sem credencial e está fora da paridade local.

### 4.5 Um único commit

Depois de todos os gates locais, exatamente um commit e um push:

```bash
git commit -m "fix: close CI command contract and local parity"
git push origin main
```

Sem force, tags, outra branch, outro remote, `--no-verify`, `git init`,
alteração de identidade ou commit adicional.

## 5. Fronteira autorizada

Alterar ou criar somente:

- `.github/workflows/ci.yml`;
- `tools/ci/validate_ci.py`;
- `tools/ci/tests/test_ci.py`;
- `tools/candidates/validate_candidate_workflow.py`;
- `tools/candidates/tests/test_definitive_contract.py`;
- `tools/ci/invocability.py` (novo, se o executor optar por módulo separado);
- `tools/ci/tests/test_invocability.py` (novo, se aplicável);
- `docs/infrastructure/deployment/implementation/slices/S30a-paridade-local-fechamento-ci-candidato.report.md`.

Não alterar: task S30, suas três corrections, relatório S30, relatórios
históricos, `publish-candidate.yml`, `publish-release.yml`,
`deploy-production.yml`, `rollback-production.yml`, `trust.py`,
`publish_guard.py`, `candidate_plan.py`, `previous_candidate.py`, `lineage.py`,
`finalize_candidate.py`, `validate_pending.py`, backend, frontend,
`website_back`, `website_front`, `whatsapp_service`, `release_control`,
Dockerfiles, Compose, `.gitignore`, OpenAPI, schemas, HANDOFF, tracker,
produção ou qualquer nova slice.

`publish-candidate.yml` é **lido** pelo gate de invocabilidade, mas não é
alterado por esta slice.

## 6. Comportamentos negativos

Não publicar release, não criar tag, não executar `publish-release.yml`,
`deploy-production.yml` ou `rollback-production.yml`, não fazer deploy,
rollback, SSH, VPS, DNS, alteração de Nginx, mutação de produção, cleanup
destrutivo remoto, criação ou rotação de credencial. Não usar `docker push`,
`docker login` ou qualquer registry. Não instalar binário no host. Não relaxar
Trivy, scanner de segredos, pinagem por SHA ou permissões de workflow.

## 7. Matriz de validação local obrigatória

```bash
cd /home/gregorio/git/baronesa/emporio
python3 tools/ci/validate_ci.py
python3 tools/candidates/validate_candidate_workflow.py
python3 tools/releases/validate_release_workflow.py
python3 tools/releases/validate_publisher_ui.py
python3 tools/releases/validate_publisher_identity_bridge.py
python3 tools/ci/validate_workflow_inventory.py
python3 tools/deploy/validate_deploy_workflow.py
python3 tools/deploy/validate_rollback_contract.py
python3 tools/deploy/validate_rollback_runtime.py
python3 tools/releases/release_control_contract.py validate
python3 tools/security/bootstrap_contract.py validate
python3 tools/docker/java_images_contract.py validate
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v
GITHUB_RUN_ID=999999999 GITHUB_RUN_ATTEMPT=99 PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/security/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/gateway/tests -v
python3 tools/ci/secret_scan.py --tracked
git diff --check
```

Acrescentar a paridade local da Seção 4.4 e, antes do commit, revisar a lista
staged, confirmar que ela contém exclusivamente os caminhos da Seção 5 e que
`git diff --cached --check` retorna 0.

## 8. Critérios de aceite

A slice só é aceita quando, simultaneamente:

1. os doze validadores e as sete suítes retornam exit 0;
2. o gate de invocabilidade cobre os vinte e sete comandos dos dois workflows e
   mata os seis mutantes prescritos;
3. a paridade local dos seis builds está registrada com resultado literal;
4. o scanner permanece `clean` com `unsupported=0`;
5. o run de CI do commit novo é verde nos oito jobs;
6. o run de `publish-candidate` é verde nos seis jobs;
7. existem `candidate-manifest` e `candidate-outcome`, com digests, provenance
   e attestation cruzados e sem `latest`;
8. nenhuma release, tag, deploy, rollback ou efeito de produção ocorreu;
9. o relatório registra comandos, exits, saídas sanitizadas, IDs, divergências
   e resíduos, sem qualquer token, header ou segredo.

## 9. Condições de parada

Parar antes do commit e registrar o bloqueio, sem improvisar correção fora da
fronteira, diante de:

- achado HIGH/CRITICAL do Trivy na paridade local;
- comando recusado pelo gate de invocabilidade cuja correção esteja fora da
  Seção 5;
- qualquer defeito novo em arquivo não autorizado;
- ausência de `read:packages` no momento de verificar GHCR;
- divergência entre digest, provenance, attestation e manifesto;
- qualquer efeito observado em produção.

Depois do push, se a CI ou o candidato falharem, registrar run, job, passo,
saída sanitizada, causa e fronteira, e parar. Não emitir uma correção
improvisada.

## 10. Relatório

Criar somente:

`docs/infrastructure/deployment/implementation/slices/S30a-paridade-local-fechamento-ci-candidato.report.md`

Deve conter: CWD; autoridade lida; arquivos criados e alterados; implementação
por decisão fechada; matriz local com comandos, exits e durações; paridade
local com resultado por componente; SHA do commit; runs, jobs, artifacts,
digests, provenance e attestation; resíduos; acessos externos; divergências.
Terminar exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

## 11. Próxima fronteira, apenas informativa

S30b tratará da release global pela UI/runtime publisher, com reconciliação,
idempotência e restart. Ela não está autorizada e não deve ser criada.
