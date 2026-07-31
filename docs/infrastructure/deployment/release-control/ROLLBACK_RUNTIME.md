# Runtime local do rollback comercial

Este documento registra a ativação local da S26. O runtime deployer anuncia
exatamente `deployment:read`, `deployment:execute` e `deployment:rollback` e
expõe somente `POST /api/deployment-control/v1/rollbacks` e
`GET /api/deployment-control/v1/rollbacks/{operationId}` para a operação
comercial. O corpo é fechado em `release` e `reason`; a release elegível é
calculada no servidor.

A operação é persistida como `operationType=rollback` com journal, evidência,
predecessor imediato, hash do estado corrente e `databaseRestoreRequired`.
Ela compartilha o lock global `production_global` com o forward, aceita replay
idêntico e rejeita divergência do mesmo `Idempotency-Key`.

O alvo precisa ser release global publicada, imutável e deployável, predecessor
imediato da instalação reconciliada. Migrações são comparadas por versão,
caminho e SHA-256. Delta não comprovadamente reversível exige backup verificado
de `erp` e `website`, com retenção mínima de 365 dias; ausência, expiração,
parcialidade ou hash divergente bloqueia a operação. Uploads e sessão WhatsApp
não são restaurados implicitamente.

Os estados persistidos são `QUEUED`, `PRECHECKING`, `RESTORING`, `SWITCHING`,
`VERIFYING`, `SUCCEEDED`, `ROLLING_BACK`, `ROLLED_BACK`, `FAILED` e
`UNCERTAIN`. `ROLLED_BACK` é compensação interna do executor, nunca sucesso
comercial. `UNCERTAIN` bloqueia nova operação até reconciliação humana.

O workflow versionado `rollback-production.yml` e o envelope
`emporio-commercial-rollback-transport` vinculam cada comando ao operation ID,
estado, release e digest de evidência. O envelope não carrega path, comando,
credencial, token, dump ou URL privada. Este ciclo não executa workflow, rede,
rollback real, containers ou produção.
