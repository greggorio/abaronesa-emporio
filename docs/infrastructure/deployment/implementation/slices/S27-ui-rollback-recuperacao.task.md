# S27 — UI de rollback e recuperação

> Estado: PLANNED
> Tipo: frontend administrativo para rollback comercial
> Executor previsto: CLI
> Diretório obrigatório: /home/gregorio/git/baronesa/emporio
> Dependências: S01 a S26 ACCEPTED
> Relatório de saída: S27-ui-rollback-recuperacao.report.md

## Instrução para delegação

Execute integralmente esta task. Leia, nesta ordem:

1. esta task inteira;
2. a revisão terminal do relatório S26;
3. `ROLLBACK_COMERCIAL.md`, `rollback.openapi.yml`, `rollback-state-machine.yml`, `rollback-security.yml` e `ROLLBACK_RUNTIME.md`;
4. a task e o relatório S24;
5. `frontend/src/services/releaseDeployerClient.js`, `releaseDeployerAttempt.js`, `ProductionDeploymentPage.vue` e seus testes;
6. `frontend/src/config/releaseDeployer.js`, `frontend/entrypoint.sh`, `runtime-entrypoint.spec.js`, `routes.js` e `PainelControle.vue`;
7. os contratos ativos de API, segurança e runtime.

Implemente somente a UI de rollback e recuperação sobre o runtime S26 já aceito. Não altere esta task, o tracker ou crie S28.

## 1. Objetivo

Estender a rota existente `/configuracoes/atualizacao-sistema` para permitir solicitação explícita de rollback comercial, acompanhamento e recuperação segura, preservando integralmente a atualização forward S24.

A UI não executa rollback, não calcula elegibilidade, não escolhe componente, imagem, digest, tag, migration, banco, ordem ou comando. A autoridade de elegibilidade, predecessor, restore, lock e estado continua no deployer S26.

## 2. Contrato fechado

### Identidade e capability

- usar a mesma origem HTTPS ERP/proxy same-origin e o mesmo token deployer em memória do cliente S24;
- validar exatamente `mode=deployer`, `apiVersion=v1` e capabilities ordenadas `deployment:read`, `deployment:execute`, `deployment:rollback`;
- validar no JWT exatamente `aud=emporio-release-control-deployer`, RS256, scope `deployment:read deployment:execute deployment:rollback` e TTL de 300 segundos;
- qualquer capability, claim, campo ou formato adicional/inválido desabilita a UI inteira;
- o token nunca vai para storage, URL, log, payload ou mensagem pública.

### Endpoints e request

Usar somente:

~~~text
POST /api/deployment-control/v1/rollbacks
GET  /api/deployment-control/v1/rollbacks/{operationId}
~~~

- o POST exige capability `deployment:rollback` e o GET exige `deployment:read`;
- o body contém exatamente `release` e `reason`;
- `reason` é obrigatório, entre 10 e 1000 caracteres;
- `Idempotency-Key` usa exatamente `deployer-rollback-<UUID v4>`;
- a release enviada é uma release global apresentada pelo servidor; a UI não deriva predecessor nem elegibilidade localmente;
- a resposta é validada contra o contrato S25/S26, com `operationType=rollback`, `sourceRelease`, `targetRelease`, `databaseRestoreRequired`, timestamps, estado e erro público permitido;
- não criar endpoint de plano, endpoint de cancelamento, retry automático ou seleção por componente.

### Alvo e confirmação

- apresentar somente releases globais retornadas pelo deployer; não exibir digest, tag, imagem, migration ou comando como opção;
- não marcar uma release como rollback-elegible por cálculo local; a resposta do servidor é a única autoridade;
- antes do POST, confirmar target release, motivo, restore de banco quando indicado pelo resultado do servidor e os limites de uploads/WhatsApp;
- se a resposta do servidor rejeitar a release como inelegível, manter a tentativa segura e não reenviar automaticamente;
- exibir que uploads não são restaurados implicitamente e que sessão WhatsApp pode exigir reemparelhamento manual.

### Tentativa, reload e idempotência

Criar armazenamento separado da tentativa forward:

~~~text
sessionStorage[emporio.releaseDeployer.rollback.pending.v1]
~~~

Registro permitido, máximo 16 KiB:

~~~json
{"schemaVersion":1,"idempotencyKey":"deployer-rollback-<UUID v4>","release":"vX.Y.Z","reason":"...","operationId":null,"createdAt":"..."}
~~~

- persistir somente release, reason, chave, operationId, schemaVersion e createdAt;
- rejeitar/remover registro inválido, storage indisponível ou tamanho excedido;
- após `202`, salvar operationId e acompanhar a mesma operação;
- após reload com operationId, consultar exclusivamente o GET da operação;
- sem operationId, oferecer `Retomar envio` com a mesma chave, release e reason;
- `Descartar tentativa` exige confirmação e remove apenas o registro local; não cancela operação remota;
- replay idêntico acompanha a operação existente; `IDEMPOTENCY_CONFLICT` encerra a tentativa sem novo POST;
- erro de rede, resposta inválida, timeout ou conflito nunca dispara retry automático.

### Estados e mensagens

Aceitar exatamente os estados S26:

~~~text
QUEUED, PRECHECKING, RESTORING, SWITCHING, VERIFYING, SUCCEEDED,
ROLLING_BACK, ROLLED_BACK, FAILED, UNCERTAIN
~~~

Mensagens mínimas:

- `QUEUED`: Aguardando reconciliação;
- `PRECHECKING`: Validando condições de segurança;
- `RESTORING`: Restaurando o banco conforme o backup verificado;
- `SWITCHING`: Aplicando a release anterior;
- `VERIFYING`: Verificando instalação e persistências;
- `SUCCEEDED`: Rollback comercial concluído;
- `ROLLED_BACK`: A tentativa foi compensada; rollback comercial não concluído;
- `FAILED`: Rollback impedido antes de side effect;
- `UNCERTAIN`: Instalação incerta; não iniciar nova operação e consultar suporte.

Estado desconhecido, payload divergente, release divergente ou target diferente da tentativa interrompe o polling e mostra mensagem genérica.

Polling mantém o intervalo de 3 segundos, uma requisição por vez e timeout contínuo de 10 minutos. Estados terminais interrompem polling; `UNCERTAIN` mantém a informação local necessária para suporte e bloqueia nova solicitação.

Nunca exibir detail, traceback, body desconhecido, token, chave, URL remota, workflowRunUrl, digest como comando ou código interno não previsto. `traceId` válido pode ser exibido como código de suporte.

## 3. Fronteira autorizada

Alterar ou criar somente:

~~~text
frontend/src/services/releaseDeployerClient.js
frontend/src/services/releaseDeployerClient.spec.js
frontend/src/services/releaseDeployerRollbackAttempt.js
frontend/src/services/releaseDeployerRollbackAttempt.spec.js
frontend/src/pages/ProductionDeploymentPage.vue
frontend/src/pages/ProductionDeploymentPage.spec.js
frontend/src/config/releaseDeployer.spec.js
docs/infrastructure/deployment/release-control/UI_DEPLOYER.md
docs/infrastructure/deployment/implementation/slices/S27-ui-rollback-recuperacao.report.md
~~~

Se a manutenção exigir alteração mínima em `frontend/src/components/configuracoes/PainelControle.vue` ou `frontend/src/router/routes.js`, registrar a razão; não criar rota nova: usar `/configuracoes/atualizacao-sistema`.

Preservar publisher S17, tentativa forward, contrato S24, backend, `release_control`, OpenAPI/YAML S25/S26, migrations, workflows, Docker, Compose, gateway, Nginx e arquivos de produção.

## 4. Proibições

Não alterar backend ou runtime deployer. Não executar rollback/deploy. Não acessar GitHub, GHCR, SSH, VPS, produção, rede, containers, volumes, secrets, `.env`, chaves ou tokens reais. Não criar S28. Não alterar task, tracker ou S01–S26.

## 5. Testes causais obrigatórios

Matar mutantes para:

- capability exata de três itens, ordem, scope e JWT;
- token em memória e ausência em storage, URL, log e payload;
- request fechado, reason, UUID v4 e prefixo;
- schema de rollback, operationType, source/target, restore e estados;
- POST/GET same-origin e ausência de endpoint inventado;
- release server-provided sem cálculo local de elegibilidade;
- storage separado, reload, retomada, descarte, replay, conflito e timeout;
- todos os dez estados, incluindo `ROLLED_BACK` diferente de sucesso e `UNCERTAIN` bloqueante;
- erro seguro, target divergente, estado desconhecido e resposta extra;
- não regressão de todos os testes forward S24 e do publisher S17.

## 6. Matriz terminal

Executar com CWD `/home/gregorio/git/baronesa/emporio/frontend` quando o comando exigir frontend e registrar exit, contagem, duração, saída literal e interpretação:

~~~bash
npm ci
npm run lint
npm run test:unit -- --run
npm test -- --run
npm run build
node --test runtime-entrypoint.spec.js
cd /home/gregorio/git/baronesa/emporio && python3 tools/releases/release_control_contract.py validate
cd /home/gregorio/git/baronesa/emporio && python3 tools/deploy/validate_rollback_runtime.py
cd /home/gregorio/git/baronesa/emporio && git diff --check
cd /home/gregorio/git/baronesa/emporio && git rev-parse --verify HEAD
cd /home/gregorio/git/baronesa/emporio && git tag --list
cd /home/gregorio/git/baronesa/emporio && git reflog show --all
cd /home/gregorio/git/baronesa/emporio && find .github/workflows -maxdepth 1 -type f -printf '%f\n' | sort
cd /home/gregorio/git/baronesa/emporio && find . -path './.git' -prune -o \( -name '.venv' -o -name '.coverage' -o -name '.pytest_cache' -o -name '.ruff_cache' -o -name '.mypy_cache' -o -name '__pycache__' -o -name '*.pyc' \) -print
~~~

Todos os comandos devem retornar exit 0, exceto `git rev-parse --verify HEAD`, que pode retornar 128 no workspace pré-Git. Remover `frontend/dist` após o build. Não considerar `node_modules` e `.quasar` como resíduos de aplicação quando preexistentes/dependências, mas registrá-los.

## 7. Aceite

Aceitar somente com UI forward preservada, capability/caminhos exatos, testes causais verdes, matriz terminal verde, ausência de segredo/acesso externo e relatório completo. A limitação ambiental documentada na S26 não autoriza containers nesta slice.

O relatório deve terminar com:

~~~text
IN_PROGRESS — aguardando revisão do orquestrador
~~~

Não declarar ACCEPTED e não criar S28.

## 8. Prompt formal

~~~text
Implemente a S27 em /home/gregorio/git/baronesa/emporio.
Estenda /configuracoes/atualizacao-sistema com rollback comercial e recuperação, preservando integralmente o forward S24.
Atualize o cliente para capabilities e JWT exatos de S26, use somente POST/GET de rollbacks, request fechado release+reason, Idempotency-Key deployer-rollback-<UUID v4>, token somente em memória e storage separado para retomada.
Não calcule elegibilidade, predecessor ou impacto no browser; não ofereça componente, imagem, digest, tag, migration, banco ou comando.
Trate exatamente os dez estados S26, incluindo ROLLED_BACK como compensação e UNCERTAIN como bloqueante.
Não altere backend, release_control, contratos S25/S26, publisher, Docker, workflows, produção ou secrets. Não use rede, containers, SSH, GitHub, GHCR, VPS ou produção.
Execute a matriz terminal, registre provas literais e crie somente o relatório S27.
Não altere a task, o tracker, S01–S26 e não crie S28.
Termine com: IN_PROGRESS — aguardando revisão do orquestrador
~~~
