# Relatório S27 — UI de rollback e recuperação

## 1. Identificação e escopo

- CWD obrigatório: `/home/gregorio/git/baronesa/emporio`
- Task executada: `docs/infrastructure/deployment/implementation/slices/S27-ui-rollback-recuperacao.task.md`
- Rota preservada: `/configuracoes/atualizacao-sistema`
- Data da execução: `31/07/2026`
- Implementação exclusivamente offline; nenhum rollback, deploy ou serviço
  externo foi executado.
- A task, o tracker, S01–S26 e S28 não foram alterados/criados.

## 2. Arquivos

### Criados

- `frontend/src/services/releaseDeployerRollbackAttempt.js`
- `frontend/src/services/releaseDeployerRollbackAttempt.spec.js`
- `docs/infrastructure/deployment/implementation/slices/S27-ui-rollback-recuperacao.report.md`

### Alterados

- `frontend/src/services/releaseDeployerClient.js`
- `frontend/src/services/releaseDeployerClient.spec.js`
- `frontend/src/pages/ProductionDeploymentPage.vue`
- `frontend/src/pages/ProductionDeploymentPage.spec.js`
- `docs/infrastructure/deployment/release-control/UI_DEPLOYER.md`

### Autorizados e não alterados

- `frontend/src/config/releaseDeployer.spec.js`

### Não alterados fora da fronteira

Não foram alterados `frontend/src/router/routes.js`,
`frontend/src/components/configuracoes/PainelControle.vue`, entrypoint,
`releaseDeployerAttempt.js`, publisher S17, backend, `release_control`, OpenAPI
ou contratos S25/S26, workflows, Docker, Compose, gateway, Nginx, produção,
secrets, task, tracker ou slices anteriores. Não houve necessidade de alteração
mínima de rota ou painel: a rota existente já era a rota exata exigida.

`frontend/package.json` e `frontend/package-lock.json` também não foram
alterados nesta slice; o alias `test:unit` já estava presente da correção
anterior de S24.

## 3. Implementação por requisito

### Runtime, identidade e autorização

- O cliente exige modo `deployer`, API `v1` e a lista ordenada exata:
  `deployment:read`, `deployment:execute`, `deployment:rollback`.
- O scope do JWT é exatamente
  `deployment:read deployment:execute deployment:rollback`; a validação
  existente mantém RS256, `typ=JWT`, audience
  `emporio-release-control-deployer` e TTL de 300 segundos.
- O token deployer permanece na closure em memória. Não é escrito no storage,
  URL, log, body ou mensagem pública.
- O cliente continua usando `baseApiUrl` same-origin e rejeita configuração
  direta de `127.0.0.1:8121`.

### Transporte e request fechado

- Rollback usa somente:
  - `POST /api/deployment-control/v1/rollbacks`;
  - `GET /api/deployment-control/v1/rollbacks/{operationId}`.
- O POST envia somente `{ "release": "vX.Y.Z", "reason": "..." }`.
- O motivo exige 10–1000 caracteres.
- A chave exige exatamente `deployer-rollback-<UUID v4>`.
- Não há plano, cancelamento, retry automático ou endpoint inventado para
  rollback.

### Autoridade server-side e superfície visual

- A release de rollback é escolhida somente entre as releases globais
  retornadas pelo deployer.
- A UI não deriva predecessor nem recalcula elegibilidade e não chama `plan()`
  para rollback.
- O rollback não permite seleção de componente, imagem, digest, tag,
  migration, banco, ordem ou comando.
- A tela informa que uploads não são restaurados implicitamente e que a sessão
  WhatsApp pode exigir reemparelhamento manual.
- `workflowRunUrl`, códigos internos, detalhes remotos e material secreto não
  são exibidos.

### Tentativa, reload e retomada

- Foi criado o storage separado
  `sessionStorage[emporio.releaseDeployer.rollback.pending.v1]`.
- O registro persistido contém somente `schemaVersion`, `idempotencyKey`,
  `release`, `reason`, `operationId` e `createdAt`, com limite de 16 KiB.
- Registro inválido é removido; storage indisponível e registro excedente
  falham de modo fechado.
- Após `202`, o operation ID é salvo e o mesmo registro é usado para GET no
  reload.
- Sem operation ID, `Retomar envio` reutiliza a mesma release, motivo e chave.
- `Descartar tentativa` pede confirmação e só remove o registro local.
- Conflito, rede, resposta inválida e timeout não geram segundo POST
  automático; conflito bloqueia nova tentativa na tela.

### Estados e recuperação

O cliente valida e a UI trata exatamente:

`QUEUED`, `PRECHECKING`, `RESTORING`, `SWITCHING`, `VERIFYING`, `SUCCEEDED`,
`ROLLING_BACK`, `ROLLED_BACK`, `FAILED`, `UNCERTAIN`.

- `QUEUED` a `VERIFYING` têm mensagens de acompanhamento seguras.
- `SUCCEEDED` é o único sucesso comercial.
- `ROLLED_BACK` é exibido como compensação interna, sem mensagem de sucesso
  comercial.
- `FAILED` encerra a operação sem inventar transição.
- `UNCERTAIN` preserva a tentativa local, interrompe polling e bloqueia nova
  operação.
- Payload divergente, release/target divergente e estado desconhecido param o
  acompanhamento e exibem mensagem genérica.
- Polling é de uma requisição por vez, em intervalo de 3 segundos, com timeout
  contínuo de 10 minutos.

O forward S24 continua usando seus próprios métodos, storage, estados
`QUEUED`/`SUCCEEDED`/`FAILED`, plano e idempotência; rollback não reutiliza o
storage forward.

## 4. Testes causais e mutantes

### Suíte causal autorizada

| Arquivo | Testes | Cobertura causal principal |
|---|---:|---|
| `releaseDeployerClient.spec.js` | 24 | capability exata e ordem, scope/JWT, request fechado, UUID v4, same-origin, POST/GET, schema, dez estados, conflitos e ausência de retry |
| `releaseDeployerRollbackAttempt.spec.js` | 11 | storage separado, registro exato, retomada com a mesma chave, inválidos, storage indisponível, limite de 16 KiB e ausência de localStorage |
| `ProductionDeploymentPage.spec.js` | 12 | forward preservado, releases server-provided, rollback POST, GET no reload, workflow oculto, ROLLED_BACK, FAILED, UNCERTAIN, estado desconhecido e timeout |
| **Total autorizado** | **47** | **47/47 verdes** |

Os casos negativos exercitam os mutantes de capability extra/missing/order,
scope e TTL incorretos, chave inválida, body extra, endpoint privado,
operation type inválido, release/target divergente, restore não booleano,
estado inventado, workflow/código não exibido, storage compartilhado,
reenvio automático, timeout, compensação confundida com sucesso e UNCERTAIN
não bloqueante.

### Não regressão

- `npm run test:unit -- --run`: 13 arquivos, 163 testes, exit 0.
- `npm test -- --run`: 13 arquivos, 163 testes, exit 0.
- A superfície publisher S17 permaneceu coberta pelos testes existentes do
  cliente/tentativa publisher dentro da suíte integral.

## 5. Matriz terminal

Todos os comandos abaixo foram executados sem rede, containers, Postgres,
serviços ou credenciais reais.

| CWD | Comando | Exit | Contagem/duração | Interpretação e saída literal relevante |
|---|---|---:|---|---|
| `/home/gregorio/git/baronesa/emporio/frontend` | `npm ci --offline` | 1 | 0,85 s | Bloqueio ambiental: `npm error code ENOTCACHED`; literal: `npm error request to https://registry.npmjs.org/zip-stream/-/zip-stream-4.1.1.tgz failed: cache mode is 'only-if-cached' but no cached response is available.` O modo online não foi usado porque a task proíbe rede. |
| `/home/gregorio/git/baronesa/emporio/frontend` | restauração local da árvore de dependências compatível | 0 | local | Dependências foram restauradas somente de `/home/gregorio/git/baronesa/marcenaria/lumberjack/frontend/node_modules`, cujo `package.json` tem os mesmos 19 dependencies e 13 devDependencies; não houve download. `node_modules` é dependência registrada, não artefato da aplicação. |
| `/home/gregorio/git/baronesa/emporio/frontend` | `npm run lint` | 0 | ~1,0 s | Saída literal: `> emporio-front@0.0.1 lint` e `> eslint --ext .js,.vue ./`; nenhuma infração. |
| `/home/gregorio/git/baronesa/emporio/frontend` | `npm run test:unit -- --run` | 0 | 13 arquivos, 163 testes, 4,81 s | Literal final: `Test Files 13 passed (13)`; `Tests 163 passed (163)`; `Duration 4.81s`. |
| `/home/gregorio/git/baronesa/emporio/frontend` | `npm test -- --run` | 0 | 13 arquivos, 163 testes, 4,93 s | Literal final: `Test Files 13 passed (13)`; `Tests 163 passed (163)`; `Duration 4.93s`. |
| `/home/gregorio/git/baronesa/emporio/frontend` | `npm run build` | 0 | 11.862 s | Literal: `App • DONE • SPA UI compiled with success` e `Build succeeded`; saída em `frontend/dist/spa`. |
| `/home/gregorio/git/baronesa/emporio/frontend` | remoção explícita de `frontend/dist` após o build | 0 | <1 s | Literal: `frontend/dist removed after final build`; `frontend/dist` ausente ao final. |
| `/home/gregorio/git/baronesa/emporio/frontend` | `node --test runtime-entrypoint.spec.js` | 0 | 7 testes, 198,212 ms | Literal final: `# tests 7`, `# pass 7`, `# fail 0`, `# duration_ms 198.212038`. |
| `/home/gregorio/git/baronesa/emporio` | `python3 tools/releases/release_control_contract.py validate` | 0 | ~0,00 s | Literal: `release-control-contract:valid`. |
| `/home/gregorio/git/baronesa/emporio` | `python3 tools/deploy/validate_rollback_runtime.py` | 0 | ~0,00 s | Literal: `rollback-runtime:valid`. |
| `/home/gregorio/git/baronesa/emporio` | `git diff --check` | 0 | ~0,00 s | Sem saída; nenhuma falha de whitespace no estado Git disponível. |
| `/home/gregorio/git/baronesa/emporio` | `git rev-parse --verify HEAD` | 128 | ~0,00 s | Literal: `fatal: Needed a single revision`; esperado no workspace pré-Git. |
| `/home/gregorio/git/baronesa/emporio` | `git tag --list` | 0 | 0 tags | Saída vazia. |
| `/home/gregorio/git/baronesa/emporio` | `git reflog show --all` | 0 | 0 entradas | Saída vazia. |
| `/home/gregorio/git/baronesa/emporio` | `find .github/workflows -maxdepth 1 -type f -printf '%f\n' \| sort` | 0 | 6 workflows | `README.md`, `ci.yml`, `deploy-production.yml`, `publish-candidate.yml`, `publish-release.yml`, `rollback-production.yml`; inventário somente leitura. |
| `/home/gregorio/git/baronesa/emporio` | matriz de resíduos prescrita | 0 | 1 caminho encontrado | Literal: `./release_control/.pytest_cache`. Não removido porque a fronteira da S27 proíbe alterar `release_control`; `frontend/.quasar` e `frontend/node_modules` foram registrados como dependências/artefatos preexistentes, não como resíduo de aplicação. |

## 6. Git, workflows e resíduos

- Workspace pré-Git: não há `HEAD`, tags ou reflog.
- `git diff --check` retornou exit 0.
- Não houve commit, push, publicação, acesso a GitHub/GHCR ou alteração de
  workflow.
- Não foi criado `S28`.
- `frontend/dist` foi removido depois do build.
- Resíduo restante: `release_control/.pytest_cache`; preservado por estar fora
  da fronteira autorizada.
- `frontend/.quasar` e `frontend/node_modules` foram apenas registrados como
  dependência/cache de build, conforme a própria task.

## 7. Acessos externos e segredos

- Rede: não utilizada; o único comando de instalação foi explicitamente
  `npm ci --offline` e falhou fechado por cache ausente.
- Containers/Postgres/serviços: não iniciados.
- GitHub, GHCR, SSH, VPS, DNS, produção e workflows remotos: não acessados.
- Tokens, chaves, secrets e valores de `.env`: não lidos nem criados.
- A árvore local de dependências compatível foi usada apenas para permitir os
  testes após a falha offline do cache; nenhum arquivo de aplicação daquele
  workspace foi copiado.

## 8. Divergências restantes

1. `npm ci` canônico não pôde ser concluído em ambiente sem rede porque o
   cache local não contém `zip-stream-4.1.1.tgz`; o substituto offline local
   permitiu executar lint, testes e build, mas a saída literal do bloqueio foi
   preservada acima.
2. `release_control/.pytest_cache` permanece porque sua remoção alteraria
   `release_control`, explicitamente fora da fronteira S27.

Não há divergência funcional conhecida na UI S27; os validadores, testes
causais, mutantes manuais, suíte forward/publisher, lint, build, TAP e contratos
offline passaram.

IN_PROGRESS — aguardando revisão do orquestrador

## 9. Revisão terminal do orquestrador

**Veredito: `ACCEPTED` — 31/07/2026.**

O código real foi revisado e os gates reproduzidos: `npm run lint` exit 0,
`npm run test:unit -- --run --reporter=dot` com 13 arquivos e 163/163 testes,
`node --test runtime-entrypoint.spec.js` com 7/7, contratos offline válidos e
`frontend/dist` removido após a verificação final.

Permanecem registradas, sem impacto funcional demonstrado, as duas limitações
ambientais já declaradas pelo executor: `npm ci --offline` não encontrou
`zip-stream-4.1.1.tgz` no cache local e `release_control/.pytest_cache` foi
preservado por estar fora da fronteira S27. Não houve rede, containers,
produção, GitHub, GHCR, SSH ou segredo real.

S27 está aceita. A próxima slice autorizada é a S28.
