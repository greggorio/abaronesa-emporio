# S24 — UI de produção para atualização forward

> **Estado do executor:** `IN_PROGRESS — aguardando revisão do orquestrador`
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Data:** 31/07/2026

## 1. Escopo executado

A UI deployer foi implementada exclusivamente na fronteira autorizada da
task. A superfície usa `/configuracoes/atualizacao-sistema`, a configuração
runtime `deployer`, capability autenticada exata e o proxy same-origin do ERP.
Não houve acesso ao listener privado, à produção ou a qualquer operação
remota.

## 2. Arquivos

### Criados

- `frontend/src/config/releaseDeployer.js`
- `frontend/src/config/releaseDeployer.spec.js`
- `frontend/src/services/releaseDeployerClient.js`
- `frontend/src/services/releaseDeployerClient.spec.js`
- `frontend/src/services/releaseDeployerAttempt.js`
- `frontend/src/services/releaseDeployerAttempt.spec.js`
- `frontend/src/pages/ProductionDeploymentPage.vue`
- `frontend/src/pages/ProductionDeploymentPage.spec.js`
- `docs/infrastructure/deployment/release-control/UI_DEPLOYER.md`
- este relatório

### Alterados

- `frontend/entrypoint.sh`
- `frontend/runtime-entrypoint.spec.js`
- `frontend/src/router/routes.js`
- `frontend/src/components/configuracoes/PainelControle.vue`
- `frontend/src/components/configuracoes/PainelControle.spec.js`
- `docs/infrastructure/deployment/release-control/README.md`

### Não alterados

Todos os arquivos fora da fronteira autorizada, incluindo backend, `release_control`,
OpenAPI, schemas, workflows, Docker, Compose, gateway, Nginx, publisher S17,
task, tracker e slices S01–S23. Como o workspace está pré-Git, a confirmação
de conteúdo foi feita por auditoria de caminhos e pelos comandos da matriz;
não houve `git add`, commit, tag ou push.

## 3. Implementação por requisito

- Runtime: `RELEASE_CONTROL_MODE` ausente vira `disabled`; somente `disabled`
  e `deployer` são aceitos; `publisher` e valores desconhecidos falham
  fechado. `window.RuntimeConfig` recebe `apiBaseUrl` e
  `releaseControlMode`.
- Isolamento: `releaseDeployer.js` recusa deployer em desenvolvimento e só
  aceita o valor production-only do runtime. O publisher S17 não foi
  reutilizado nem alterado semanticamente.
- Capability: o cliente aceita somente `mode=deployer`, `apiVersion=v1` e as
  duas capabilities `deployment:read` e `deployment:execute`, sem extras.
  O card e a rota só habilitam ação após essa resposta válida.
- Transporte: todas as URLs derivam de `baseApiUrl`; não existe URL de
  usuário nem chamada ao `127.0.0.1:8121`. A rota exata foi adicionada com
  `requiresAuth`.
- Identidade: o exchange é POST sem body em
  `/api/release-control/identity/deployer/token`. A resposta exige token
  Bearer, scope, TTL 300 e claims JWT RS256 com audience deployer. O bearer
  deployer fica somente em closure de memória e é renovado no máximo uma vez
  após 401.
- Dados: current, releases, plano e operação são validados com shapes
  estritos, SemVer, SHA, digest interno, data, URI HTTPS e operationId. 404 de
  current vira instalação limpa; 409 de instalação incerta bloqueia plano e
  POST. A elegibilidade é sempre a enviada pelo servidor, com zero ou mais de
  uma elegível bloqueando a ação.
- Plano/UI: os seis componentes aparecem somente como leitura, com origem,
  destino, migration e backup. Nenhum campo operacional é editável ou
  exibido.
- Solicitação: antes do POST é salvo somente o registro canônico em
  `emporio.releaseDeployer.pending.v1`, com limite de 16 KiB. O body do POST
  tem apenas `release` e o header adicional é `Idempotency-Key` com prefixo e
  UUID v4 exigidos.
- Recuperação: replay idempotente acompanha a operação; conflito encerra a
  tentativa sem novo POST; falha de rede e resposta inválida não fazem retry
  automático. Reload com operationId consulta somente status; sem operationId
  oferece `Retomar envio`; descarte confirmado remove somente o registro
  local.
- Estados: a UI trata apenas `QUEUED`, `SUCCEEDED` e `FAILED`. `QUEUED` é
  `Aguardando reconciliação`; o polling é de 3 s, serial, com timeout contínuo
  de 10 min. Estado fora do conjunto interrompe o acompanhamento com mensagem
  genérica. Estados terminais removem o registro.
- Segurança de apresentação: mensagens públicas são mapeadas pelos códigos
  previstos; `detail`, traceback, body desconhecido, token, chave, URL remota,
  referência de execução e código interno não são apresentados. Um traceId
  validado só aparece como código de suporte.

## 4. Testes causais e mutantes

Os testes S24 dedicados executam 47 casos no Vitest e 7 casos no `node --test`
(o runner Vitest usa seis casos equivalentes de ponte para o entrypoint). O
total da suíte frontend foi 143/143.

Foram cobertas rejeições mutantes de:

- runtime ausente, development, `publisher`, tipo inválido e modo desconhecido;
- capability extra, ausente, rollback, modo e versão incorretos;
- exchange com audience, scope, TTL e token type incorretos;
- bearer não persistido, 401 com no máximo um novo exchange e falha de rede
  sem retry;
- fields extras, SemVer, SHA, data, reconciliado, operationId e URL inválidos;
- current 404, 200 e 409; zero, uma e múltiplas releases elegíveis;
- plano com menos de seis componentes, duplicação ou shape divergente;
- body de deploy restrito a `release`, chave idempotente inválida e conflitos
  ativos com ou sem operationId;
- retomada com e sem operationId, persistência antes do POST, replay,
  conflito, timeout, `QUEUED`, `SUCCEEDED`, `FAILED` e estado não suportado;
- ausência de rota de operação de retorno, capability derivada extra e
  superfície publisher escondida em modo deployer.

Saídas literais relevantes:

```text
Test Files  12 passed (12)
Tests       143 passed (143)
TAP ... tests 7 ... pass 7 ... fail 0
```

## 5. Matriz terminal

| CWD | Comando | Exit | Contagem/resultado | Duração | Interpretação/artefatos |
|---|---|---:|---|---:|---|
| `/home/gregorio/git/baronesa/emporio/frontend` | `npm ci --cache /tmp/emporio-npm-cache --prefer-offline` | 0 | 766 pacotes instalados; auditoria reportou 34 vulnerabilidades da árvore existente | 6,74 s | Dependências instaladas usando cache; sem alteração de package.json/lock |
| frontend | `npm run lint` | 0 | ESLint sem problemas | 3,82 s | Lint verde |
| frontend | `npm run test:unit -- --run` | 1 | `npm error Missing script: "test:unit"` | 0,15 s | Divergência da base; script não existe e package.json está fora da fronteira |
| frontend | `npm test -- --run --reporter=dot` | 0 | 12 arquivos, 143 testes aprovados | 5,53 s | Runner equivalente configurado passou |
| frontend | `npm run build` | 0 | SPA compilada; chunk `ProductionDeploymentPage` gerado | 11,44 s | Build verde; `frontend/dist` foi removido após a verificação |
| frontend | `node --test runtime-entrypoint.spec.js` | 0 | 7 testes TAP aprovados | 0,28 s | Entry point terminal verde |
| `/home/gregorio/git/baronesa/emporio` | `python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` | 0,11 s | Contratos externos permaneceram válidos |
| root | `git diff --check` | 0 | saída vazia | 0,00 s | Nenhum erro de whitespace detectável pelo Git pré-inicialização |
| root | `git rev-parse --verify HEAD` | 128 | `fatal: Needed a single revision` | 0,00 s | Workspace permanece pré-Git, conforme esperado |
| root | `git tag --list` | 0 | saída vazia | 0,00 s | Nenhuma tag criada |
| root | `git reflog show --all` | 0 | saída vazia | 0,00 s | Nenhuma operação de histórico |
| root | `find .github/workflows -maxdepth 1 -type f -printf '%f\n' \| sort` | 0 | `README.md`, `ci.yml`, `deploy-production.yml`, `publish-candidate.yml`, `publish-release.yml` | 0,00 s | Workflows somente inventariados |
| root | busca de `.venv`, caches, `__pycache__` e `*.pyc` prescrita pela task | 0 | saída vazia | 0,00 s | Resíduos prescritos vazios |

`frontend/node_modules` existia antes da execução da task e foi renovado pelo
`npm ci` obrigatório; `frontend/node_modules/.vite` acompanha essa árvore de
dependências. `frontend/.quasar` já possuía metadata anterior. Não há
`frontend/dist`, `frontend/coverage` ou `.vite` de aplicação ao final. O
`website_front/dist` e `dist/coverage` internos de dependências foram apenas
observados fora da fronteira e preservados.

## 6. Estado Git, workflows e resíduos

O workspace não possui `HEAD`, tags ou reflog. Não foram feitos stage, commit,
push, publicação, acesso remoto ou alteração de workflow. O inventário final
dos cinco workflows está acima. A busca de resíduos exigida pela task foi
vazia; artefatos de build temporários foram removidos e são recuperáveis por
novo build.

## 7. Acessos externos e segredos

Não houve acesso a GitHub, GHCR, SSH, VPS, DNS, gateway, Nginx, produção ou
serviço deployer real. O único comando de dependências foi `npm ci` com cache
em `/tmp/emporio-npm-cache`. Não foram abertos ou criados segredos, chaves
privadas, tokens reais ou arquivos de produção. Os valores de token nos testes
são fixtures sintéticos e não são logados pela implementação.

## 8. Divergências restantes

- O workspace continua pré-Git; `git rev-parse --verify HEAD` retorna 128,
  conforme a condição prevista na task.

## 9. Estado final

IN_PROGRESS — aguardando revisão do orquestrador

## 13. Revisão terminal do orquestrador — aceite

Veredito: ACCEPTED — 31/07/2026.

A correção consolidada 01 foi reproduzida independentemente:

- frontend/package.json contém somente o alias autorizado test:unit;
- frontend/package-lock.json permaneceu inalterado, com SHA-256
  e8ee2e4241ea0bee3f54bad7f15cdfaf9822541802a4eadb7e768db7e2cfada7;
- npm run test:unit -- --run: exit 0, 12 arquivos e 143 testes;
- npm run lint: exit 0;
- release-control-contract.py validate: exit 0;
- git diff --check: exit 0;
- busca prescrita de resíduos: saída vazia;
- HEAD continua ausente, sem tags, reflog, commit, stage ou push;
- não existe arquivo S25 antes desta decisão.

O build e os testes TAP da matriz entregue também foram aprovados. O
frontend/dist gerado durante a revisão foi removido ao final. Nenhum acesso
externo, segredo, publicação ou ambiente remoto foi utilizado.

A divergência única da correction-01 foi resolvida sem alteração da UI,
contratos, lockfile, task ou arquivos fora da autorização.

S24: ACCEPTED.

A próxima fronteira contratual é S25:

~~~
docs/infrastructure/deployment/implementation/slices/S25-contrato-seguro-rollback-comercial.task.md
~~~

## 10. Registro histórico da revisão terminal anterior

Veredito anterior: REJECTED — correção consolidada obrigatória. Este registro
documenta o achado já corrigido na seção 11 e não representa a matriz atual.

A revisão independente confirmou a implementação e os testes causais:

- Vitest direcionado da S24: 47 casos próprios, além dos seis casos da ponte
  Vitest do entrypoint, todos aprovados;
- node --test runtime-entrypoint.spec.js: 7/7 aprovados;
- suíte frontend completa: 12 arquivos e 143 testes aprovados;
- lint, build, contrato release-control e higiene prescrita aprovados;
- git rev-parse --verify HEAD: exit 128, conforme o workspace pré-Git;
- workflows: cinco arquivos, sendo quatro workflows ativos e um README;
- busca prescrita de resíduos: saída vazia.

A matriz, porém, não está terminalmente verde. O comando obrigatório:

~~~
npm run test:unit -- --run
exit 1
npm error Missing script: "test:unit"
~~~

foi reproduzido nesta revisão. O frontend/package.json possui somente npm
test, e npm test -- --run passou com 143/143; esse equivalente não cumpre o
comando canônico. A task exige exit 0 para todos os comandos, exceto apenas o
git rev-parse, mas a fronteira original também proibia alterar
frontend/package.json. Trata-se de uma inconsistência objetiva da fronteira
que precisa ser fechada antes do aceite.

A correção única está em:

~~~
docs/infrastructure/deployment/implementation/slices/S24-ui-producao-atualizacao-forward.correction-01.md
~~~

S24 permaneceu IN_PROGRESS até a execução da correção consolidada. Não criar
S25.

## 11. Correção consolidada 01 — nova matriz terminal

### 11.1 Alteração autorizada

Foi adicionado somente o alias solicitado em `frontend/package.json`,
preservando scripts e dependências:

~~~json
"test:unit": "vitest run"
~~~

Não foram alterados `frontend/package-lock.json`, código da UI, task, tracker,
contratos, workflows ou qualquer outro arquivo da correção. O relatório foi
atualizado para registrar esta execução. S25 não existe.

### 11.2 Saída literal relevante do comando corrigido

Com CWD `/home/gregorio/git/baronesa/emporio/frontend`:

~~~text
$ npm run test:unit -- --run

> emporio-front@0.0.1 test:unit
> vitest run --run

 Test Files  12 passed (12)
      Tests  143 passed (143)
   Duration  8.79s (transform 6.98s, setup 0ms, collect 17.43s, tests 2.90s, environment 24.23s, prepare 3.04s)

DURATION_SECONDS=9.67
exit 0
~~~

### 11.3 Matriz terminal repetida

| CWD | Comando | Exit | Contagem/resultado | Duração | Interpretação/artefatos |
|---|---|---:|---|---:|---|
| `/home/gregorio/git/baronesa/emporio/frontend` | `npm ci --cache /tmp/emporio-npm-cache --prefer-offline` | 0 | 766 pacotes instalados; 767 auditados; 34 vulnerabilidades reportadas (12 moderadas, 18 altas, 4 críticas) | 4,42 s | Dependências reinstaladas pelo comando prescrito; package-lock permaneceu inalterado |
| frontend | `npm run lint` | 0 | ESLint sem problemas | 7,50 s | Lint verde |
| frontend | `npm run test:unit -- --run` | 0 | 12 arquivos, 143 testes aprovados | 9,67 s | Comando canônico corrigido passou; saída literal acima |
| frontend | `npm test -- --run --reporter=dot` | 0 | 12 arquivos, 143 testes aprovados | 9,90 s | Suíte equivalente também passou |
| frontend | `npm run build` | 0 | SPA compilada; saída em `frontend/dist/spa` durante o teste | 11,11 s | Build verde; `frontend/dist` removido ao final |
| frontend | `node --test runtime-entrypoint.spec.js` | 0 | 7 testes TAP; 7 pass; 0 fail | 0,29 s | Entry point terminal verde |
| `/home/gregorio/git/baronesa/emporio` | `python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` | 0,10 s | Contrato permaneceu válido |
| root | `git diff --check` | 0 | saída vazia | 0,00 s | Sem erro de whitespace detectável |
| root | `git rev-parse --verify HEAD` | 128 | `fatal: Needed a single revision` | 0,00 s | Única saída não zero esperada: workspace pré-Git |
| root | `git tag --list` | 0 | saída vazia | 0,00 s | Nenhuma tag criada |
| root | `git reflog show --all` | 0 | saída vazia | 0,00 s | Nenhum histórico alterado |
| root | `find .github/workflows -maxdepth 1 -type f -printf '%f\\n' \| sort` | 0 | `README.md`, `ci.yml`, `deploy-production.yml`, `publish-candidate.yml`, `publish-release.yml` | 0,00 s | Workflows somente inventariados |
| root | busca prescrita de `.venv`, caches, `__pycache__` e `*.pyc` | 0 | saída vazia | 0,00 s | Resíduos prescritos vazios |

Verificações adicionais: SHA-256 final de
`frontend/package-lock.json` = `e8ee2e4241ea0bee3f54bad7f15cdfaf9822541802a4eadb7e768db7e2cfada7`;
`frontend/dist`, `frontend/coverage` e `frontend/.vite` estão ausentes; a
busca de nomes S25 em `docs/infrastructure/deployment/implementation` não
retornou resultado. Não houve acesso externo, segredo, stage, commit, tag,
push ou publicação.

## 12. Estado final

IN_PROGRESS — aguardando revisão do orquestrador
