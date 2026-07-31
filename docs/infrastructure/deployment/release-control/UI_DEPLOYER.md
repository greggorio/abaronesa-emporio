# UI deployer de produção

Esta é a UI administrativa da atualização forward global. A rota é
`/configuracoes/atualizacao-sistema` e só é habilitada quando o frontend de
produção recebe `window.RuntimeConfig.releaseControlMode = "deployer"` e o
servidor confirma exatamente `deployment:read deployment:execute deployment:rollback`.

## Transporte e identidade

Todas as chamadas usam `baseApiUrl`, a origem HTTPS pública do ERP. O browser
nunca conhece a porta privada do `release_control`: o host encaminha as rotas
`/api/deployment-control/*` pelo proxy same-origin. A sessão ERP é trocada em
`POST /api/release-control/identity/deployer/token`, sem body; o token RS256
recebido fica somente na closure do cliente enquanto a página está viva.

O cliente valida `Bearer`, TTL `300`, scope e audience do JWT antes de usar o
token. Uma resposta `401` pode provocar no máximo um novo exchange explícito;
erros de rede, resposta inválida e conflitos não são reenviados
automaticamente.

## Fluxo fechado

Depois da capability, a UI consulta a instalação atual, as releases globais e
o plano. A elegibilidade é exclusivamente o campo `eligible` enviado pelo
servidor. Zero releases elegíveis não oferece ação; mais de uma bloqueia a
confirmação. O plano mostra os seis componentes e os metadados de migração e
backup em modo somente leitura.

Antes do POST o plano é consultado novamente. A solicitação contém somente
`{"release":"vX.Y.Z"}` e o header `Idempotency-Key` no formato
`deployer-ui-<UUID v4>`. A tentativa validada fica em
`sessionStorage[emporio.releaseDeployer.pending.v1]`, sem bearer, segredo,
URL ou detalhes remotos, e nunca excede 16 KiB.

## Rollback comercial

O rollback usa somente `POST /api/deployment-control/v1/rollbacks` e
`GET /api/deployment-control/v1/rollbacks/{operationId}`. O POST contém
exatamente `{"release":"vX.Y.Z","reason":"..."}` e o header
`Idempotency-Key: deployer-rollback-<UUID v4>`. A lista apresentada é a lista
global retornada pelo servidor; a UI não calcula predecessor ou elegibilidade
e não chama `plan()` para rollback.

A tentativa fica separada em
`sessionStorage[emporio.releaseDeployer.rollback.pending.v1]`, com somente
`schemaVersion`, release, motivo, chave, operationId e createdAt, limitada a
16 KiB. A mesma chave, release e motivo são preservados na retomada. O token
deployer continua somente na closure em memória e não entra no registro,
URL, log ou payload.

São aceitos exatamente `QUEUED`, `PRECHECKING`, `RESTORING`, `SWITCHING`,
`VERIFYING`, `SUCCEEDED`, `ROLLING_BACK`, `ROLLED_BACK`, `FAILED` e
`UNCERTAIN`. `ROLLED_BACK` é mostrado como compensação sem sucesso comercial;
`UNCERTAIN` mantém o contexto seguro local e bloqueia nova operação. O polling
usa uma requisição por vez a cada três segundos, por no máximo dez minutos.
Estado, release ou payload divergente, erro de rede, timeout e conflito
interrompem o acompanhamento sem retry automático.

A área de rollback informa que uploads não são restaurados implicitamente e
que a sessão WhatsApp pode exigir reemparelhamento manual. Ela não exibe
workflow, URL remota, componentes, imagem, digest, tag, migration, comando,
detalhes internos ou material secreto; um `traceId` validado pode ser mostrado
como código de suporte.

## Recuperação

- Com `operationId`, um reload consulta somente o status daquela operação.
- Sem `operationId`, a tela oferece **Retomar envio** com a mesma release e
  chave.
- **Descartar tentativa** exige confirmação e remove apenas o registro local;
  não cancela uma operação remota.
- `QUEUED` aparece como **Aguardando reconciliação**. A UI acompanha a cada
  três segundos, com uma requisição por vez e timeout contínuo de dez minutos.
- `SUCCEEDED` e `FAILED` encerram o acompanhamento e removem o registro
  pendente. Estado não suportado, instalação incerta, operação ativa, conflito
  ou falha de rede bloqueiam a ação e preservam o contexto seguro necessário.

A UI não oferece publicação, cancelamento, edição de release, escolha de
componente ou campos operacionais. Também não exibe identificadores internos,
detalhes de erro, URL remota, referência de execução ou material secreto;
apenas um `traceId` validado pode ser mostrado como código de suporte.
