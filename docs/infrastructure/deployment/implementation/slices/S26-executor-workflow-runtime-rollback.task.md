# S26 — executor, workflow e runtime de rollback comercial

> Estado: PLANNED
> Tipo: implementação local do contrato S25
> Executor previsto: CLI
> Diretório obrigatório: /home/gregorio/git/baronesa/emporio
> Dependências: S01 a S25 ACCEPTED
> Relatório de saída: S26-executor-workflow-runtime-rollback.report.md

## Instrução para delegação

Execute integralmente esta task. Leia, nesta ordem:

1. esta task inteira;
2. a seção terminal do relatório S25;
3. ROLLBACK_COMERCIAL.md, rollback.openapi.yml, rollback-state-machine.yml e rollback-security.yml;
4. CONTRATO_API_ESTADOS_SEGURANCA.md, RUNTIME_DEPLOYER.md, deployer.openapi.yml, state-machines.yml e security-matrix.yml;
5. o código e os testes de `release_control`, incluindo API, schemas, serviço, persistência, reconciliação, segurança e runtime;
6. o executor, adapters, transporte remoto, scripts e validadores locais de `tools/deploy` e `ops/deploy`;
7. WORKFLOW_IMPLANTACAO.md, OPERACAO_LOCAL_IMPLANTACAO.md, TRANSACAO_IMPLANTACAO.md e os workflows existentes.

Implemente o contrato fechado de S25 no runtime e no fluxo local. O relatório é criado pelo executor e termina em `IN_PROGRESS — aguardando revisão do orquestrador`. Não altere esta task, o tracker nem crie S27.

## 1. Objetivo

Ativar, somente após provas locais verdes, o rollback comercial solicitado pelo operador, com elegibilidade server-side, operação persistida, lock global compartilhado com forward, idempotência, recuperação e execução segura. A implementação deve preservar o contrato forward existente e distinguir rollback comercial de compensação interna de uma tentativa forward.

É permitido implementar e testar o caminho local completo. É proibido executar rollback real, acessar VPS, SSH, GitHub, GHCR, produção, rede, containers ou volumes reais.

## 2. Comportamento obrigatório

### API, identidade e operação

- ativar `POST /api/deployment-control/v1/rollbacks` e `GET /api/deployment-control/v1/rollbacks/{operationId}` somente com o contrato S25;
- aceitar exatamente `release` e `reason`; rejeitar campos extras e `reason` fora de 10–1000 caracteres;
- exigir o prefixo `deployer-rollback-<UUID v4>` no `Idempotency-Key`, escopo `deployment:rollback` e papel autorizado;
- expor capability exatamente `deployment:read`, `deployment:execute`, `deployment:rollback` após a ativação; não conceder rollback a perfis sem o escopo;
- persistir `operationType=rollback`, release alvo, predecessor/current reconciliados, reason, idempotência e journal sem segredo, path privado ou token;
- replay semanticamente idêntico retorna a mesma operação; mesma chave com request divergente retorna `IDEMPOTENCY_CONFLICT`;
- usar o mesmo lock global e a mesma regra de operação ativa máxima do forward; conflito retorna `PRODUCTION_OPERATION_ACTIVE`;
- respostas e `ProblemDetails` não revelam credencial, token, caminho, dump, URL privada ou detalhes internos;
- suportar todos os estados S25: `QUEUED`, `PRECHECKING`, `RESTORING`, `SWITCHING`, `VERIFYING`, `SUCCEEDED`, `ROLLING_BACK`, `ROLLED_BACK`, `FAILED`, `UNCERTAIN`.

### Elegibilidade e reconciliação

- o servidor calcula o alvo; o cliente não escolhe componente, digest, tag ou migration;
- exigir instalação atual reconciliada, release global publicada, imutável, anterior e imediatamente predecessor na mesma cadeia;
- rejeitar salto, candidato, release não implantável, predecessor divergente, current incerto e qualquer operação ativa;
- calcular a diferença de migrations e exigir restore quando houver migration não comprovadamente reversível;
- exigir backup anterior à release atual, compatível e verificado, com os metadados contratuais de S25; ausência, expiração, parcialidade ou hash divergente bloqueia;
- manter `databaseRestoreRequired=true` até evidência terminal;
- não apagar nem restaurar uploads; não restaurar automaticamente sessão WhatsApp; incompatibilidade deve produzir estado seguro e orientação manual;
- bloquear nova operação enquanto houver `UNCERTAIN`, até reconciliação humana explícita.

### Execução e recuperação

- `PRECHECKING` ocorre antes de qualquer side effect;
- `RESTORING` ocorre somente quando exigido e somente após validar backup e política;
- `SWITCHING` ocorre somente após prechecks e restore obrigatório;
- `VERIFYING` exige evidência do alvo, banco, links e seis componentes;
- `SUCCEEDED` somente com estado reconciliado no alvo;
- falha antes de side effect termina `FAILED`;
- falha depois de side effect só termina `ROLLED_BACK` com evidência completa de compensação; incerteza termina `UNCERTAIN`;
- estados terminais não repetem side effects após restart, retry ou replay;
- estender workflow, handoff/outcome e protocolo remoto de forma versionada e compatível, vinculando cada comando à operação, ao estado esperado e à evidência;
- preservar current/previous, slot global e segurança de restart; não permitir downgrade implícito por troca das seis imagens;
- manter separado o `ROLLBACK`/`ROLLED_BACK` interno de compensação forward do rollback comercial solicitado.

## 3. Fronteira autorizada

Pode alterar somente o código, testes, migrations, validadores e documentação diretamente necessários ao runtime de rollback:

~~~text
release_control/src/emporio_release_control/
release_control/tests/
release_control/migrations/versions/ (somente migration necessária)
tools/deploy/
ops/deploy/
.github/workflows/ (somente extensão versionada do fluxo, sem execução)
docs/infrastructure/deployment/release-control/
docs/infrastructure/deployment/implementation/slices/S26-executor-workflow-runtime-rollback.report.md
~~~

Preserve S25 e seus quatro artefatos; atualize seus marcadores apenas para refletir a ativação implementada e comprovada. Não altere S01–S25, a task, o tracker, a UI S24, o publisher S17 ou contratos sem relação direta.

## 4. Proibições

Não executar rollback ou deploy real. Não acessar GitHub, GHCR, SSH, VPS, DNS, TLS, gateway, Nginx, produção, rede, containers, volumes, secrets, `.env`, chaves ou tokens reais. Não criar S27. Não remover evidência. Não mascarar falha de restore, migration, lock, protocolo ou reconciliação.

## 5. Testes causais obrigatórios

Adicionar ou atualizar testes que matem mutantes para:

- capability exata antes/depois da ativação, papel e scope;
- body fechado, reason e Idempotency-Key;
- alvo/predecessor/current reconciliado, cadeia, migration e backup;
- restore obrigatório, backup ausente/expirado/parcial/hash divergente e `databaseRestoreRequired`;
- não restauração implícita de uploads e sessão WhatsApp;
- lock global, corrida, replay e conflito de idempotência;
- todas as transições S25, incluindo `UNCERTAIN` e bloqueio pós-incerteza;
- falha antes/depois de side effect, restart/recovery e ausência de repetição terminal;
- separação entre rollback comercial e compensação forward;
- binding de operação/estado/evidência no workflow e transporte;
- não regressão de todos os testes forward existentes e da validação dos contratos ativos.

## 6. Matriz terminal obrigatória

Executar com CWD `/home/gregorio/git/baronesa/emporio` e registrar comandos, exit codes, contagens, duração, saída literal relevante e interpretação:

~~~bash
python3 tools/releases/release_control_contract.py validate
python3 tools/deploy/validate_rollback_contract.py
PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q release_control/tests
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
python3 -m compileall -q release_control/src tools/deploy ops/deploy
git diff --check
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f -printf '%f\n' | sort
find . -path './.git' -prune -o \( -name '.venv' -o -name '.coverage' -o -name '.pytest_cache' -o -name '.ruff_cache' -o -name '.mypy_cache' -o -name '__pycache__' -o -name '*.pyc' \) -print
~~~

`git rev-parse --verify HEAD` pode retornar 128 somente se o workspace continuar pré-Git; todos os demais comandos devem retornar 0. A busca de resíduos deve ser vazia. Se o projeto exigir comandos adicionais para a suíte canônica, registrá-los e executá-los.

## 7. Aceite

Somente o orquestrador pode aceitar ou rejeitar. O aceite exige:

- contrato S25 implementado sem divergência;
- API, capability, lock, idempotência, estados, restore, reconciliação e recovery comprovados;
- workflow/transporte versionados e testados localmente;
- testes causais e regressões verdes;
- nenhuma operação externa ou real executada;
- relatório completo com arquivos, matriz, divergências e estado literal.

Se houver falha, divergência ou decisão arquitetural aberta, parar sem criar S27 e registrar o bloqueio objetivo.

## 8. Prompt formal

~~~text
Implemente a task S26 em /home/gregorio/git/baronesa/emporio.
Leia a task inteira e os artefatos S25 antes de editar.
Ative localmente o rollback comercial conforme o contrato S25: API fechada, capability deployment:rollback, elegibilidade server-side, operação persistida, lock global, idempotência, estados, restore condicionado por migration/backup, reconciliação, recovery e protocolo workflow/remote versionado.
Preserve o forward e diferencie rollback comercial de compensação interna.
Não execute acesso externo nem rollback/deploy real; não use GitHub, GHCR, SSH, VPS, produção, rede, containers, volumes, secrets ou tokens reais.
Execute a matriz terminal completa, registre provas literais e crie somente o relatório S26.
Não altere esta task, o tracker, S01–S25 e não crie S27.
Termine o relatório com: IN_PROGRESS — aguardando revisão do orquestrador
~~~
