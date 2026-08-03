# S38 — Fechamento do Gate A e invariantes do deployer

> **Data:** 03/08/2026
> **Predecessora:** S37 aceita
> **Natureza:** implementação e validação local, seguida de um push fast-forward
> **Produção:** proibida nesta slice

## 1. Objetivo

Fechar o Gate A descoberto pela S37 antes de qualquer preparação da VPS:

1. preservar `/opt/sistemas/emporio` como única raiz canônica;
2. eliminar as duas falhas preexistentes de `release_control/tests/` sem
   enfraquecer contratos de produção;
3. tornar explícita no serviço a precondição de vínculo completo antes de
   aceitar um outcome `CONFIRMED` de deploy ou rollback;
4. comprovar local e remotamente que o caminho de CI/candidato permanece verde;
5. produzir evidência suficiente para o orquestrador abrir posteriormente a
   preparação da VPS como um ato separado e explicitamente autorizado.

Esta slice não dimensiona a VPS, não cria swap, não agenda reinício e não
prepara produção.

## 2. Decisões já fechadas

O executor não deve reabrir estas escolhas:

### 2.1 Raiz canônica

A raiz de produção é:

```text
/opt/sistemas/emporio
```

Ela já está congelada na arquitetura, S20, S21, transporte, helper remoto, CLI,
validadores e documentação operacional. O fato de o diretório ainda não existir
na VPS é normal para um sistema greenfield; sua criação pertence ao futuro Gate
B. O diretório vazio `/opt/sistemas/baronesa/emporio` não será utilizado.

Não alterar `DEPLOY_ROOT`, `DEFAULT_DEPLOY_ROOT`, `REMOTE_HELPER`,
`INCOMING_ROOT`, `SNAPSHOT_ROOT`, o helper remoto ou os validadores para adotar
o path alternativo.

### 2.2 Rate limit de rollback

O default de produção permanece:

```text
rollback_rate_per_minute = 2
```

O teste que prova `first -> replay -> conflict 409` deve executar em um contexto
de teste que permita observar o contrato de idempotência sem ser interceptado
pelo rate limiter. Não aumentar o default de produção e não mover o rate limit
para depois do handler.

Adicionar ou preservar teste causal independente provando que a terceira
mutação de rollback do mesmo ator/bucket dentro da janela recebe `429`.

### 2.3 Vínculo do workflow

O call graph vigente é:

```text
DeployerReconciler._operation/_rollback_operation
  -> _bind_run
  -> download e validação do artifact
  -> DeployerService.apply_outcome/apply_rollback_outcome
```

Não existe hoje um caller de produção que aplique o outcome antes do bind. A
`CheckViolation` observada decorre de uma fixture que cria
`dispatch_state=CONFIRMED` sem as quatro colunas exigidas.

Ainda assim, o serviço deve preservar o invariante por si mesmo: antes de
processar qualquer outcome com `transportStatus=CONFIRMED`, exigir que
`workflow_run_id`, `workflow_attempt`, `workflow_run_url` e `control_sha`
estejam todos presentes e válidos. Ausência ou vínculo parcial deve produzir
falha de domínio estável, antes de qualquer alteração da operação, current
installation, journal ou auditoria de sucesso.

A constraint `ck_rc_deployment_workflow_binding` permanece inalterada. Nenhuma
migration é necessária.

Outcomes `INDETERMINATE` continuam seguindo a máquina de estados existente; não
os transforme em `CONFIRMED` e não exija vínculo onde o contrato admite
`UNCERTAIN` sem run descoberto.

## 3. Snapshot e integridade inicial

Antes de editar:

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD
git ls-remote origin refs/heads/main
git log --oneline --decorate -12
sha256sum docs/infrastructure/deployment/implementation/slices/S38-fechamento-gate-a-invariantes-deployer.task.md
git diff --check
```

O prompt de delegação fornecerá os hashes esperados. Divergência de remoto,
stage prévio, alteração fora do relatório da S38 ou base diferente interrompe a
execução antes de qualquer edição.

Não rebasear, não fazer cherry-pick, não reescrever os sete commits documentais
locais e não usar force push.

## 4. Implementação obrigatória

### 4.1 Reproduzir antes de corrigir

Executar isoladamente os dois testes atualmente falhos e registrar comandos,
exits e causas:

```text
test_rollback_persists_dispatches_replays_and_supports_get
test_rollback_state_machine_restore_recovery_and_terminal_replay
```

Confirmar também por busca completa que somente o reconciliador chama os dois
métodos de aplicação de outcome em código de produção.

### 4.2 Isolar idempotência do rate limiter

Corrigir o teste de idempotência com configuração/fixture local explícita. A
correção deve permitir as três chamadas necessárias apenas naquele cenário e
manter a política default em `2/min`.

Cobrir separadamente:

- primeira mutação aceita;
- replay idempotente aceito;
- payload divergente com mesma chave retorna `409` quando o bucket do cenário
  permite alcançar o handler;
- terceira mutação do mesmo ator no default conservador retorna `429`;
- um `429` não cria operação, journal ou dispatch adicional.

### 4.3 Corrigir a fixture de workflow vinculada

A fixture que inicia em `CONFIRMED` deve fornecer os quatro campos coerentes:

```text
workflow_run_id
workflow_attempt
workflow_run_url
control_sha
```

Eles devem respeitar schemas, constraint e repositório reais. Não relaxar a
constraint e não trocar `CONFIRMED` por um estado incoerente apenas para fazer o
teste passar.

### 4.4 Defesa em profundidade no serviço

Implementar uma validação comum e pequena usada por deploy e rollback para
outcomes `CONFIRMED`.

Provar no mínimo:

1. vínculo completo permite o comportamento vigente;
2. cada campo ausente, isoladamente, falha antes do flush/commit;
3. vínculo parcial falha com o mesmo código de domínio estável;
4. a operação persistida permanece semanticamente inalterada após a recusa;
5. `CurrentInstallation`, journal, `active_slot`, `outcome_sha256` e auditoria
   não ganham evidência falsa;
6. replay terminal idêntico continua idempotente;
7. outcome `INDETERMINATE` sem vínculo continua no caminho fail-closed já
   contratado.

Preferir código já existente como `WORKFLOW_RUN_BINDING_INVALID` se sua
semântica e exposição forem adequadas. Não ampliar a API pública sem necessidade.

### 4.5 Raiz canônica

Executar uma busca final nos arquivos operacionais e comprovar que todos os
produtores e consumidores do path permanecem alinhados em
`/opt/sistemas/emporio`.

Ocorrências históricas do path alternativo em relatórios S37 são evidência e
não devem ser reescritas. Não tocar a VPS para criar ou remover qualquer uma das
árvores.

## 5. Fronteira de arquivos

Alterações de código são permitidas somente no menor conjunto necessário sob:

```text
release_control/src/emporio_release_control/deployer_service.py
release_control/tests/test_deployer_api.py
release_control/tests/test_deployer_reconciliation.py
```

Um novo helper de teste dentro de `release_control/tests/` é permitido somente
se reduzir duplicação real. Mudança fora dessa lista exige parar e registrar a
causa; não expandir o escopo por conveniência.

Não alterar migrations, workflows, Compose, arquivos de ambiente, contratos de
release, transporte SSH ou código comercial.

## 6. Validação local obrigatória

Executar primeiro os testes direcionados de deployer e reconciliação. Depois,
executar integralmente:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q release_control/tests
```

Resultado exigido: zero falhas e zero skips introduzidos. Não aceitar as duas
falhas como preexistentes depois da correção.

Executar os 17 validadores canônicos:

```bash
python3 tools/docker/validate_node_images.py validate
python3 tools/docker/java_images_contract.py validate
python3 tools/ci/validate_ci.py
python3 tools/ci/invocability.py
python3 tools/ci/migrations_contract.py
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
python3 tools/compose/validate_compose.py
python3 tools/gateway/validate_gateway.py
```

Executar as oito suítes canônicas:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/security/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/gateway/tests -v
```

E também:

```bash
python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json
python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json
python3 tools/ci/secret_scan.py --tracked
git diff --check
```

Todos devem retornar exit `0`; secret scan deve terminar `clean` e
`unsupported=0`. Capturar o exit final de cada wrapper/suíte. Silêncio ou perda
do exit não é sucesso.

## 7. Commit, push e validação remota autorizados

Somente depois de todos os gates locais verdes:

1. confirmar que `origin/main` remoto ainda é o SHA inicial esperado;
2. conferir o diff e stagear apenas os arquivos de implementação/teste;
3. manter o relatório S38 fora do stage;
4. criar um único commit com mensagem causal;
5. executar um único push normal, estritamente fast-forward, de `main`;
6. observar a CI desse SHA até terminal;
7. observar o Publish Candidate disparado por essa CI até terminal;
8. validar os artifacts e vínculos do candidato sem publicar release.

Se o remoto mover antes do push, se qualquer gate falhar ou se o push não for
fast-forward, parar fail-closed. Não rebasear nem repetir o push por iniciativa
própria.

## 8. Proibições

Nesta slice é proibido:

- acessar, preparar ou mutar a VPS;
- criar usuário, diretório, swap, serviço, container, volume, Nginx, TLS,
  firewall, backup ou agendar reinício;
- abrir `ops/env/.env.production` ou qualquer segredo;
- criar ou alterar GitHub App, environment, variable ou secret;
- executar `gh workflow run`;
- criar tag ou release;
- executar deploy ou rollback;
- apagar run, log, artifact ou evidência;
- alterar a política real de rate limit para acomodar teste;
- relaxar constraint, schema ou estado fail-closed;
- usar force push ou reescrever histórico.

## 9. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S38-fechamento-gate-a-invariantes-deployer.report.md
```

O relatório deve permanecer não staged e não commitado e conter:

- CWD, hashes e snapshots Git inicial/final;
- reprodução causal das duas falhas;
- call graph confrontado;
- arquivos e linhas alterados;
- testes novos/alterados e interpretação;
- comandos exatos, exits e contagens de todas as validações;
- commit e push, se alcançados;
- URLs/IDs e jobs da CI e do Publish Candidate;
- prova de que release `v0.1.0`, deploy, rollback e VPS permaneceram intocados;
- resíduos e estado final.

O executor não aceita S38 e não cria a próxima slice.

Terminar exatamente com um dos estados:

```text
IN_PROGRESS — Gate A fechado; aguardando aceite e autorização da preparação da VPS
```

ou, na primeira causa não resolvida:

```text
BLOCKED — S38 interrompida fail-closed na primeira causa técnica
```

## 10. Critérios de aceite

O orquestrador aceitará S38 somente se:

- a raiz canônica permanecer coerente em `/opt/sistemas/emporio`;
- as duas falhas preexistentes estiverem resolvidas causalmente;
- o default `rollback_rate_per_minute=2` permanecer inalterado;
- outcome `CONFIRMED` sem vínculo completo falhar antes de qualquer mutação;
- toda a suíte `release_control/tests/` estiver verde;
- validadores, suítes canônicas, secret scan e diff estiverem verdes;
- commit/push forem únicos e fast-forward;
- CI e Publish Candidate do SHA final estiverem verdes e vinculados;
- nenhum ato de produção, release, deploy ou rollback tiver ocorrido;
- o relatório estiver completo, local e fora do commit.
