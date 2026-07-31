# Runtime deployer

O processo `RELEASE_CONTROL_MODE=deployer` registra exclusivamente health,
capabilities e as rotas `/api/deployment-control/v1/**`. Ele usa FastAPI,
PostgreSQL 16, SQLAlchemy 2, Alembic, Psycopg 3, JWT RS256/JWKS e uma credencial
GitHub App separada do publisher. O modo e imutavel durante o bootstrap.

## Autoridade e fronteiras

O runtime sincroniza releases globais publicadas e despacha exclusivamente:

```text
repository = greggorio/abaronesa-emporio
ref        = main
workflow   = deploy-production.yml
```

Owner, repositorio, ref, workflow, URL, imagem, digest, componente, comando e
path nunca sao recebidos do cliente. O processo nao usa Git local, shell,
Docker, socket Docker, SSH, `gh` ou filesystem operacional. Somente no profile
`test` a API GitHub pode apontar para HTTP loopback.

## Elegibilidade e plano

Sem instalacao atual, somente a primeira release da cadeia, com
`previousRelease=null`, e elegivel. Com instalacao reconciliada, apenas a
release SemVer imediatamente seguinte cujo `previousRelease` coincide com a
release atual pode ser implantada. Inventarios Flyway atuais devem ser
prefixos integrais dos inventarios alvo. Salto, downgrade, release corrente,
predecessor divergente e migration nao forward falham fechados.

O plano HTTP projeta sempre os seis componentes canonicos. `KEEP` significa
digest atual igual ao alvo; os demais recebem `UPDATE`. Na primeira instalacao
os seis possuem `currentDigest=null` e `UPDATE`. `backupRequired` e igual a
`migrationRequired`. Essa projecao e informativa: o planner S18 continua sendo
a autoridade operacional no workflow.

## Implantacao, idempotencia e exclusao

`POST /api/deployment-control/v1/deployments` aceita somente
`{"release":"vX.Y.Z"}` e exige `Idempotency-Key`, scope
`deployment:execute`, content type e limites validos. O HMAC da chave, nunca a
chave bruta, e persistido. Replay do mesmo ator, rota, chave e request devolve
a mesma operacao; request divergente devolve `IDEMPOTENCY_CONFLICT`.

Existe no maximo uma operacao de producao ativa, protegida por advisory lock e
indice parcial sobre `active_slot=1`. A criacao da operacao, idempotencia, slot
e audit ocorre numa transacao antes do dispatch. O POST GitHub nunca e
repetido automaticamente:

- falha comprovada antes do POST: `FAILED/WORKFLOW_DISPATCH_NOT_SENT`;
- `400`, `401`, `403`, `404`, `422` ou `429`:
  `FAILED/WORKFLOW_DISPATCH_REJECTED`;
- `204`: dispatch `SENT`;
- transporte incerto ou outro status, inclusive `5xx`: operacao permanece
  `QUEUED`, ativa e `UNCERTAIN`.

## Reconciliacao e instalacao atual

O reconciliador descobre somente runs `Deploy Production` do workflow e ref
fixos, correlacionados pelo `display_title=deploy-production-<operationId>`.
Antes da descoberta, o run ID e opcional. Depois da correlacao, run ID e
control SHA sao imutaveis; somente o attempt do mesmo run pode aumentar.

O artifact `deployment-workflow-outcome` e validado integralmente: identidade
REST, digest, URL, run, SHA, ZIP fechado, arquivo unico, tamanho, JSON canonico,
schema S21 e todos os bindings. O runtime nao inventa `PULLING`, `BACKING_UP`,
`MIGRATING`, `UPDATING`, `VERIFYING` ou `ROLLING_BACK`: ele transita de
`QUEUED` somente para o estado terminal sustentado pelo outcome.

Somente `CONFIRMED/SUCCEEDED` com `databaseRestoreRequired=false` atualiza
`rc_current_installation` e libera o slot como sucesso. A combinacao
`CONFIRMED/SUCCEEDED` com restore requerido e inconsistente, permanece ativa e
e registrada como `DEPLOYMENT_OUTCOME_RESTORE_CONFLICT`. `FAILED` ou
`ROLLED_BACK` preserva a instalacao anterior;
restore requerido a marca como nao reconciliada. Resultado `INDETERMINATE`,
artifact ausente/ambiguo/invalido ou binding divergente mantem a operacao
ativa, a instalacao incerta e readiness indisponivel. Nessa condicao,
`GET /current` responde `409 CURRENT_INSTALLATION_UNRECONCILED`. A mesma
classificacao vale quando faltam campos obrigatorios, o snapshot instalado nao
existe, seu commit diverge ou o dominio de releases esta vermelho. A listagem
de releases continua respondendo `200`, mas apresenta todos os itens como
inelegiveis; plano e novo deployment respondem `409`. Leituras nunca apagam ou
reescrevem essa evidencia.

## Rollback indisponivel

O planner S18 e forward-only. Por isso, `deployment:rollback` nao aparece em
capabilities. A rota de rollback permanece reservada e protegida; depois de
validar autenticacao, scope, rate limit, body e idempotency header, responde
sempre `409 RELEASE_NOT_ELIGIBLE`, grava somente `rollback.rejected` e nao cria
operacao, idempotencia ou dispatch.

A especificação futura da S25 está em
[ROLLBACK_COMERCIAL.md](./ROLLBACK_COMERCIAL.md),
[rollback.openapi.yml](./api/rollback.openapi.yml),
[rollback-state-machine.yml](./contracts/rollback-state-machine.yml) e
[rollback-security.yml](./contracts/rollback-security.yml). O runtime atual
não importa esses artefatos, não anuncia `deployment:rollback` e não habilita
a rota futura; a ativação permanece reservada para S26.

## Identidade e seguranca

O deployer valida tokens RS256 emitidos pela ponte de identidade do backend ERP
(quando habilitada). A ponte é completamente isolada da ponte publisher:

- **Rotas distintas:** GET/POST em `/api/release-control/identity/deployer/**`.
- **Chave privada distinta:** nunca compartilhada com o publisher.
- **Audience distinta:** `emporio-release-control-deployer` (configurável, fixa em emissão).
- **Scope distinto:** `deployment:read deployment:execute` (fixa, nunca inclui rollback).
- **TTL:** 300 segundos, igual ao publisher.

Para detalhes sobre habilitação, geração de chaves e ciclo local, veja
[IDENTIDADE_DEPLOYER.md](./IDENTIDADE_DEPLOYER.md).

## Readiness e seguranca

Readiness exige migration `0002_deployer_runtime`, banco, chave GitHub App,
sync de releases, reconciliacao e instalacao limpa ou reconciliada. A resposta
publica e apenas `{"status":"ok"}` ou `{"status":"unavailable"}`. JWT aceita
somente RS256 com issuer, audience, `exp`, `sub` e scopes exatos. Logs, audit e
ProblemDetails usam codigos estaveis e nunca incluem JWT, chave idempotente,
token, private key, body remoto, URL arbitraria, stdout, stderr ou traceback.

## Validacao local

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
```

Os testes remotos usam transporte HTTP injetado ou loopback. Nenhum GitHub,
GHCR, SSH, VPS ou ambiente de producao e acessado.
