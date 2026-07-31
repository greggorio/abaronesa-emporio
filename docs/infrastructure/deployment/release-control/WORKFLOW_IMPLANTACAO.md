# Workflow autenticado de implantação

> A S21 configura localmente o transporte entre GitHub Actions e o CLI S20.
> Nenhum workflow foi executado e nenhum recurso de produção foi acessado.

## Fronteiras de autoridade

O operador informa somente `operation_id` e a release global SemVer. A release
publicada fixa o BOM, os digests e os inventários Flyway; o planner S18 calcula
`KEEP`/`UPDATE`, migrations e backup. O transporte não aceita componente,
imagem, digest, host, path, comando ou URL como input e não reinterpreta o BOM.

O grafo é fixo:

```text
trust -> prepare -> deploy -> outcome
```

Somente `deploy` usa o environment `production`. Os demais jobs não recebem os
secrets SSH. A concorrência global `emporio-production`, sem cancelamento,
impede duas implantações simultâneas.

## Trust e release publicada

`trust` vincula repository, owner, evento, `main`, SHA, run, attempt, actor ID,
operação e release. A autorização usa apenas a allowlist numérica
`DEPLOYER_ACTOR_IDS`, nunca o login textual.

`prepare` consulta a tag exata e valida integralmente a release publicada antes
do primeiro download: estado da release, ref lightweight, três assets exatos,
IDs, URLs, MIME, tamanhos, sidecar, metadata, JSON canônico, seis componentes e
dois inventários Flyway. A validação reutiliza os contratos S13/S14. O handoff
contém somente request, manifesto, sidecar e metadata, com diretório `0700` e
arquivos `0600`.

O artifact baixado pelo job `deploy` é tratado como ingresso não confiável,
inclusive porque o transporte de artifacts não preserva os modos Unix. O
ingresso aceita somente diretório `0700`/`0755` e os quatro arquivos regulares
`0600`/`0644`, sem escrita de grupo/outros, links, tipos especiais ou entradas
extras. Os bytes são lidos por descritor com proteção contra troca de arquivo,
validados e copiados para um diretório novo `0700`, com arquivos `0600` e
`fsync`. Somente essa cópia privada, relida e idêntica byte a byte, pode chegar
ao planejamento, ao SSH ou à reconciliação do resultado; ela é removida ao fim.

## Snapshot e planejamento

Depois de validar e rematerializar privadamente o handoff, `deploy` lê a
configuração SSH, confirma as capabilities do helper remoto e solicita um
snapshot somente leitura. Host/porta inválidos, OpenSSH ausente ou falha de
materialização anteriores a qualquer processo remoto produzem um resultado
local `CONFIRMED/FAILED`, com código estável, persistido antes do exit não zero;
não são classificados como resultado remoto indeterminado.

- `FIRST_INSTALL` exige ausência conjunta de estado, `current` e `previous`.
- `UPDATE` exige estado instalado confirmado, manifesto correspondente e
  `current` apontando para a mesma release.

O snapshot é validado novamente no runner. O planner S18 recebe exclusivamente
a release alvo, o snapshot confirmado e o Compose canônico do checkout. Seu
bundle de seis arquivos é validado antes e depois de ser empacotado num tar não
comprimido, regular e limitado a 16 MiB.

## Transporte e execução remota

O OpenSSH usa usuário literal `deploy-emporio`, host e porta vindos das
variables protegidas e key/known_hosts do environment. Host key checking,
identidade exclusiva, ausência de forwarding e autenticação não interativa são
obrigatórios. `ssh` e `scp` são resolvidos em PATH mínimo, sem shell.

O protocolo remoto possui somente:

```text
capabilities
snapshot --operation-id <id> --release <semver>
install --operation-id <id> --release <semver> --archive-sha256 <sha256>
execute --operation-id <id> --release <semver>
cleanup --operation-id <id>
```

O archive chega apenas a `shared/deploy/incoming/<operationId>.tar.part`.
`install` extrai manualmente para staging, revalida o bundle S18 e publica por
rename atômico. Destino idêntico permite replay; destino divergente nunca é
sobrescrito. `execute` chama exclusivamente `ops/deploy/deploy-release.sh` com
`deploy`, operation ID e release.

## Resultado, incerteza e retry

Uma linha S20 canônica com exit `0`, `20` ou `21` produz, respectivamente,
`SUCCEEDED`, `ROLLED_BACK` ou `FAILED` confirmado. Perda durante ou após
`execute`, divergência de binding ou resposta inválida produz
`INDETERMINATE`; o workflow nunca inventa sucesso.

O job `outcome` revalida o documento completo contra o schema e cruza operação,
release, run, attempt e SHA com o request privado. Ausência do resultado produz
`REMOTE_RESULT_UNAVAILABLE`; documento presente ilegível, não canônico,
incompleto, com campos extras ou bindings divergentes produz
`REMOTE_RESULT_INVALID`. Em ambos os casos o outcome canônico `0600` é
persistido antes do exit `4`. Somente `CONFIRMED/SUCCEEDED` com `errorCode`
nulo termina com exit `0`; `ROLLED_BACK` e `FAILED` exigem código de erro válido.

O cleanup tenta remover somente o `.tar.part` e snapshots da operação. Falha
de cleanup torna o workflow malsucedido com `REMOTE_CLEANUP_FAILED`, sem
reescrever o estado confirmado do CLI.

Retry autorizado reutiliza o mesmo `operationId`. Snapshot, instalação e
journal reconciliam os mesmos bytes e efeitos já comprovados; release ou bytes
divergentes falham fechado.

## Ativação futura

Antes de qualquer execução real, uma slice de bootstrap deverá instalar o
control root, helper, usuário e permissões na VPS, além de configurar variables,
secrets, environment e allowlist no GitHub. A S21 não realiza essa preparação,
não publica workflow e não acessa produção.
