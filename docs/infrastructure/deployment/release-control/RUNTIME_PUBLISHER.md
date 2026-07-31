# Runtime publisher

> **Estado:** runtime local implementado e validado; nenhuma execução remota,
> publicação ou release real foi realizada.

O runtime FastAPI inicia exclusivamente em `RELEASE_CONTROL_MODE=publisher`.
Seus componentes internos são configuração fail-closed, verificador JWT/JWKS,
serviço transacional, cliente GitHub App, sincronizador de candidatos/releases,
reconciliador periódico e API HTTP.

## Persistência e transações

Alembic mantém cinco entidades centrais: `rc_publication_operation`,
`rc_idempotency_key`, `rc_candidate_snapshot`, `rc_release_snapshot` e
`rc_audit_event`; `rc_sync_state` registra o estado operacional dos dois
domínios sincronizados. Há unicidade no escopo de idempotência e índice parcial
para no máximo uma publicação ativa. Audit é append-only por trigger.

O POST autentica, autoriza e limita taxa; limita content type/bytes; valida
request/chave; resolve replay numa transação curta; revalida o candidato fora
da transação; repete replay/elegibilidade/reserva atomicamente; faz commit; e
só então envia o dispatch. Timeout pós-envio vira `UNCERTAIN` e nunca provoca
redispatch.

A fronteira do dispatch distingue causalmente quatro resultados: falha antes
do POST termina em `FAILED/WORKFLOW_DISPATCH_NOT_SENT`; transporte falho
somente durante o POST mantém `REQUESTED/UNCERTAIN`; resposta HTTP negativa
termina em `FAILED/WORKFLOW_DISPATCH_REJECTED`; e `204` marca `SENT`. Não há
retry ou redispatch. Depois da persistência, o POST público responde `202` em
todos esses casos, refletindo o estado local.

## Fluxo remoto e reinício

O único dispatch permitido aponta para `greggorio/abaronesa-emporio`, `main` e
`publish-release.yml`. A correlação procura
`display_title=publish-release-<operationId>`. Run, attempt, repository,
branch, evento, SHA, artifacts, digests, sidecars, metadata, schemas e cadeia
de releases são revalidados antes de sucesso.

Um outcome `published` exige exatamente um artifact `candidate-manifest` e
cruza seu predecessor. Em `already_published`, o run proprietário é validado
integralmente e ligado ao artifact, metadata e manifesto. O outcome final cruza
`workflow.url`, `githubRelease.tagName`, a URL da release e, após sincronização,
release, candidato, source commit e digest do manifesto.

Estados são monotônicos:

```text
REQUESTED -> VALIDATING -> PUBLISHING -> PUBLISHED
     |             |              |
     +-------------+--------------+-> FAILED
```

Um advisory lock PostgreSQL permite um reconciliador por ciclo. Após restart,
operações não terminais são redescobertas pela evidência remota, sem novo
dispatch. Códigos de falha incluem `WORKFLOW_DISPATCH_UNCONFIRMED`,
`WORKFLOW_RUN_AMBIGUOUS`, `WORKFLOW_RUN_FAILED`,
`PUBLICATION_OUTCOME_AMBIGUOUS` e os códigos fail-closed de binding.

## Segurança

JWT aceita apenas RS256, issuer/audience configurados, `exp`, `sub` e scopes
`release:read`/`release:publish`. JWKS e token de instalação ficam apenas em
memória e expiram. A GitHub App requer acesso de leitura a Actions, artifacts,
releases e refs, e permissão para disparar o workflow fixo; credenciais de
deployer/build/VPS não são compartilhadas.

CORS é allowlist HTTPS, POST aceita somente JSON até 16 KiB, cursors são
canônicos e autenticados por HMAC, erros são ProblemDetails sanitizados e os
headers `nosniff`, `no-store` e `no-referrer` são obrigatórios. O rate limit
local pressupõe a única réplica desta fase.

Somente os códigos públicos allowlisted são preservados; códigos internos e
status 502 viram `500 INTERNAL_ERROR`, inclusive por um handler final de
exceções. Query inválida em GET retorna 400 e request inválido em POST retorna
422. Antes de qualquer download de release, os três assets têm identidade,
digest, tamanho inteiro não booleano e MIME validados: `release.json` até
2 MiB (`application/json`), sidecar até 128 B (`text/plain`) e metadata até
16 KiB (`application/json`); os bytes baixados são então conferidos.

## Perfil development e identidade local

O perfil `development` preserva a API GitHub oficial, mas restringe PostgreSQL,
issuer/JWKS e CORS ao ciclo local definido em
[IDENTIDADE_PUBLISHER.md](./IDENTIDADE_PUBLISHER.md). O banco usa
`sslmode=disable` somente em `localhost` ou `127.0.0.1`; issuer e JWKS usam
HTTP loopback e o JWKS é exatamente `<issuer>/jwks`; CORS aceita somente HTTP
loopback com porta explícita. `runtime` continua exigindo TLS e HTTPS, e `test`
continua sem rede não loopback.

O token recebido continua sendo validado exclusivamente como RS256, audience
`emporio-release-control` e scopes publisher. O JWT ERP HS512 nunca é aceito
diretamente.

## Retenção e fronteira

Chaves idempotentes ficam retidas pelo período configurado (365 dias por
padrão) e nunca são persistidas em claro. Esta slice não implementa deployer,
Docker/Compose, publicação do próprio runtime, workflow adicional, credencial
real, release real ou acesso a produção.

## Consumidor frontend de desenvolvimento

A UI documentada em [UI_PUBLISHER.md](./UI_PUBLISHER.md) consome este runtime
somente em build dev e URL HTTP loopback. Ela faz o exchange S16, mantém o
bearer publisher somente em memória, valida capabilities e DTOs, persiste
somente a tentativa idempotente e acompanha a operação por polling limitado.
Ela não altera o profile `runtime` nem introduz configuração da imagem de
produção.
