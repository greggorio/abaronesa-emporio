# Contrato futuro de rollback comercial

> **Estado:** contrato offline futuro; não é consumido pelo runtime atual.
> **Slice:** S25. **Ativação mínima prevista:** S26, após aceite deste contrato.

Este documento fecha a política de rollback comercial; esta slice não executa rollback,
restore, migration, troca de imagens, workflow, API ou operação de produção.
O operador não escolhe componente, imagem, digest, tag, migration, banco,
ordem ou comando. A elegibilidade é calculada e anunciada pelo servidor.

## Limite com o runtime atual

O runtime deployer atual continua forward-only. `deployment:rollback` não é
anunciada em `capabilities`, a rota reservada permanece indisponível e nenhum
dos quatro artefatos desta slice é importado pelo backend, `release_control`,
frontend, publisher, workflow ou Docker.

Os arquivos machine-readable desta página são a especificação futura:

- [OpenAPI futuro](./api/rollback.openapi.yml);
- [máquina de estados futura](./contracts/rollback-state-machine.yml);
- [política de segurança futura](./contracts/rollback-security.yml).

## Elegibilidade fechada

Somente o servidor pode anunciar `READY`. Todos os critérios abaixo são
obrigatórios:

1. a instalação atual existe, está reconciliada e seu snapshot, commit,
   manifesto e estado instalado foram verificados;
2. não há outra operação de produção ativa;
3. o alvo é uma release global imutável, publicada, `deployable: true`, com
   SemVer válida, anterior à atual e da mesma cadeia;
4. o alvo é exatamente o predecessor publicado da release atual. Um salto,
   predecessor divergente, candidato ou release não implantável é inelegível;
5. os seis componentes e os dois inventários de migration do alvo são
   conhecidos pelo servidor e vinculados ao manifesto global;
6. a decisão de restore, a identidade do backup e o impacto de migrations
   foram verificados antes de qualquer troca.

O cliente envia apenas a release global anunciada e o motivo. A release
anunciada não pode ser escolhida a partir de componentes ou de referências de
imagem. Compensação forward de uma implantação que falhou continua sendo uma
operação distinta; nunca é convertida em rollback comercial.

## Migrations, restore e banco

Os inventários `erp` e `website` são comparados integralmente por `version`,
`path` e `sha256`. Qualquer migration aplicada desde o alvo exige restore,
exceto quando houver prova explícita e integral de reversibilidade para todo o
delta. A ausência de prova é tratada como `databaseRestoreRequired: true`.

Um restore obrigatório só pode começar depois de existir backup anterior à
release atual, compatível com o estado reconciliado e verificado por hash,
completude e validade temporal. Backup ausente, expirado, parcial ou com hash
divergente bloqueia a operação. `databaseRestoreRequired` permanece `true` até
existir evidência terminal do restore e da verificação do alvo; trocar imagens
sem restore não conclui rollback.

O registro canônico de backup contém exatamente a identidade necessária para
reconciliação:

```yaml
backupId: backup_<opaco>
sourceRelease: vX.Y.Z
sourceStateSha256: sha256:<64-hex>
databases: [erp, website]
artifactSha256: sha256:<64-hex>
createdAt: 2026-07-31T00:00:00Z
expiresAt: 2027-07-31T00:00:00Z
```

Ele não contém path, credencial, dump, conteúdo de dump ou URL privada. A
retenção mínima é de 365 dias, sem renovação silenciosa. A renovação, quando
futuramente contratada, será uma decisão auditável e explícita.

## Uploads e sessão WhatsApp

Uploads não são apagados nem restaurados implicitamente. Restore de upload
exige evidência específica e uma operação contratada, fora da troca de
release. A sessão WhatsApp não é restaurada automaticamente. Incompatibilidade
de sessão ou volume termina em estado seguro e exige reemparelhamento manual;
não é convertida em sucesso por a aplicação voltar a responder.

## API futura e idempotência

A API futura está reservada, mas indisponível até S26:

```text
POST /api/deployment-control/v1/rollbacks
GET  /api/deployment-control/v1/rollbacks/{operationId}
```

O request tem exatamente `release` e `reason`. `reason` é obrigatório, sem
controle de caracteres, com 10 a 1000 caracteres. O header obrigatório é
`Idempotency-Key: deployer-rollback-<UUID v4>`.

O escopo da chave é modo, rota, ator e chave. Replay idêntico devolve a mesma
operação; payload divergente com a mesma chave devolve
`IDEMPOTENCY_CONFLICT`. Um lock global compartilhado com deployment forward
permite no máximo uma operação ativa; concorrência retorna
`PRODUCTION_OPERATION_ACTIVE`. Timeout, resposta inválida, conflito ou falha
de rede não inicia retry automático nem um segundo workflow.

O scope futuro é `deployment:rollback`, mas ele permanece ausente da
capability atual. A rota futura não é ativada nesta slice.

## Estados e evidência

A máquina futura usa somente estes estados:

```text
QUEUED, PRECHECKING, RESTORING, SWITCHING, VERIFYING, SUCCEEDED,
ROLLING_BACK, ROLLED_BACK, FAILED, UNCERTAIN
```

`PRECHECKING` valida instalação, cadeia, predecessor, migration, backup e
lock. `RESTORING` só existe quando restore é obrigatório. `SWITCHING` só
ocorre depois dos prechecks. `VERIFYING` exige evidência do alvo, banco,
links, volumes e dos seis componentes.

`SUCCEEDED` significa instalação reconciliada no alvo anterior, com restore
verificado quando obrigatório. `FAILED` só encerra falha comprovada antes de
qualquer troca. Depois de side effect, a operação precisa compensar com
evidência completa em `ROLLED_BACK`; falha da compensação ou incerteza de
banco, links, volume, operação ou journal termina `UNCERTAIN` e bloqueia nova
operação até reconciliação humana.

`ROLLED_BACK` neste contrato é compensação da tentativa parcial, não é sinônimo
de rollback comercial concluído. Estados terminais não repetem side effects e
não possuem transição de saída.

## Proibições de ativação

Esta slice não altera runtime, backend, `release_control`, frontend,
publisher, workflows, Docker, Compose, Nginx, gateway, VPS, DNS, TLS,
produção, secrets, `.env`, chaves, tokens, schemas ativos ou capabilities.
Não há execução remota, acesso de rede, container, volume, GitHub, GHCR, SSH ou
VPS. A validação é local, fail-closed e não habilita a rota futura.
