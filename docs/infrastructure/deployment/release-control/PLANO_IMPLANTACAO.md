# Plano determinístico de implantação

> **Estado:** contrato e planejamento exclusivamente offline. A S18 não implanta
> e não executa Docker, migrations, backup, health check, deploy, rollback ou
> acesso a qualquer ambiente remoto.

## Quatro artefatos com responsabilidades diferentes

Uma **release global** é o BOM implantável e validado pela cadeia de
publicação. Ela fixa os seis componentes por referências imutáveis, o commit
de origem e os inventários Flyway.

O **estado instalado** descreve o que foi efetivamente confirmado em produção.
Ele contém a release reconciliada, as seis imagens, os dois inventários de
migrations e o hash do manifesto que lhe deu origem. Um estado fornecido como
`--current` precisa estar confirmado: `reconciled=true` e `installedAt`
preenchido.

O **plano de implantação** é uma decisão determinística entre o estado atual e
a release alvo. Ele calcula `KEEP`/`UPDATE`, deltas de migrations, necessidade
de backup e a ordem futura de execução. O plano não executa essa ordem.

O **bundle** congela as entradas e as decisões do planejamento:

```text
manifest.json
compose.prod.yml
release.env
deployment-plan.json
installed-state.next.json
bundle.sha256
```

Os hashes de `bundle.sha256` permitem revalidar os cinco arquivos cobertos sem
confiar no diretório ou no processo que os transportou.

## Autoridade do BOM

O operador escolhe somente a release global alvo e, numa atualização, fornece
o estado confirmado e o manifesto histórico correspondente. Ele nunca escolhe
componentes, imagens, dependências, bancos ou ordem.

O planner sempre considera, nesta ordem:

1. `backend`;
2. `website_back`;
3. `frontend`;
4. `website_front`;
5. `whatsapp_service`;
6. `gateway`.

Cada `immutableRef` do estado atual é comparada à referência do BOM alvo:

- referência idêntica: `KEEP`;
- referência diferente ou primeira implantação: `UPDATE`.

Não há fechamento por paths nem promoção indireta de `KEEP` para `UPDATE`.
Compatibilidade entre os seis componentes já é responsabilidade da publicação
da release global.

## Migrations forward-only e backup

Os bancos aparecem sempre na ordem `erp`, `website`. Numa atualização, o
inventário atual precisa ser prefixo exato do inventário alvo, comparando cada
objeto integral `version`, `path` e `sha256`.

- inventários idênticos: nenhum delta;
- alvo com sufixo adicional: somente o sufixo fica pendente;
- remoção, reordenação, renomeação ou alteração de migration aplicada:
  `NON_FORWARD_MIGRATION`.

`migrationRequired` é verdadeiro quando ao menos um banco muda.
`backupRequired` possui exatamente o mesmo valor. Na primeira implantação,
todas as migrations estão pendentes e o backup também é obrigatório antes que
o executor operacional futuro entregue migrations ao runtime.

O planner não afirma reversibilidade. Backup e restore serão materializados
somente por uma etapa operacional futura.

## Estado atual e intenção seguinte

Quando `--current` é usado, `--current-manifest` também é obrigatório. O hash
canônico, release, commit, componentes e bancos do manifesto histórico devem
coincidir integralmente com o estado confirmado. Isso impede planejar sobre um
estado cuja origem não foi comprovada.

`installed-state.next.json` não afirma que a implantação aconteceu. Ele é uma
intenção com:

```text
reconciled=false
installedAt=null
```

Somente depois de `VERIFY`, uma implementação futura poderá derivar, fora do
bundle, um estado confirmado com `reconciled=true` e o instante real de
`installedAt`. O bundle nunca é modificado para simular essa confirmação.

No host de produção, `current` e `previous` serão referências operacionais
futuras para estados ou bundles já verificados. A S18 não cria diretórios sob
`/opt`, não troca symlinks e não define o procedimento operacional desses
nomes.

## Geração e validação

Primeira implantação:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/deployment_plan.py generate \
  --target ops/releases/examples/global-release.example.json \
  --compose ops/compose/compose.prod.yml \
  --planned-at 2026-07-29T16:00:00Z \
  --output /tmp/emporio-deployment-plan-example
```

Atualização:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/deployment_plan.py generate \
  --target /caminho/release-alvo.json \
  --current /caminho/installed-state.json \
  --current-manifest /caminho/release-atual.json \
  --compose ops/compose/compose.prod.yml \
  --planned-at 2026-07-29T17:00:00Z \
  --output /tmp/emporio-deployment-plan-update
```

Revalidação de um bundle:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/deployment_plan.py validate \
  --bundle /tmp/emporio-deployment-plan-example
```

Validação dos contratos versionados:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py
```

Os exemplos usam somente dados fictícios. O output deve ser um diretório
inexistente, seguro, abaixo do workspace ou de `/tmp`. O Compose aceito é
exatamente `ops/compose/compose.prod.yml`, copiado byte a byte.

## Códigos de saída

| Código | Significado |
|---|---|
| `0` | sucesso |
| `2` | uso ou argumento inválido |
| `3` | entrada, contrato ou bundle inválido |
| `4` | destino inseguro ou conflito de materialização |
| `5` | falha de I/O ou atomicidade |

Diagnósticos usam códigos sanitizados, como `NON_FORWARD_MIGRATION`,
`RELEASE_CHAIN_MISMATCH`, `CURRENT_STATE_MISMATCH`, `UNSAFE_PATH`,
`BUNDLE_CONFLICT` e `INVALID_CONTRACT`. Não devem conter manifesto bruto,
ambiente, token, credencial ou traceback.

## Fronteira e próximos passos

A S18 produz e valida somente um bundle offline. Ela não:

- autentica no GitHub ou GHCR;
- baixa imagens;
- executa Compose;
- acessa PostgreSQL;
- cria backup;
- executa migration;
- altera `current` ou `previous`;
- verifica saúde;
- implanta ou reverte produção.

A [transação local S19](./TRANSACAO_IMPLANTACAO.md) consome o bundle sem
reinterpretar o BOM e governa journal, lock, probes, retomada, compensação e
confirmação do estado por meio de um adapter injetado. A
[operação local S20](./OPERACAO_LOCAL_IMPLANTACAO.md) materializa o adapter,
backup, migrations, health, rollback e links sem alterar as decisões do plano
ou da transação. O [workflow S21](./WORKFLOW_IMPLANTACAO.md) consome este
planner sem alterar sua autoridade: no runner, fornece somente a release
publicada, o snapshot remoto confirmado e o Compose canônico. Preparação da
VPS e primeiro deploy permanecem futuros.
