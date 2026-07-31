# Operação local de implantação

> **Estado:** adapter e CLI locais entregues pela S20. Este procedimento não
> autoriza deploy remoto, preparação da VPS ou acesso à produção.

## Layout e permissões

O CLI opera exclusivamente sobre o root canônico
`/opt/sistemas/emporio`, substituível por `EMPORIO_DEPLOY_ROOT` apenas em
teste/local:

```text
shared/.env
shared/backups/<operationId>/
shared/deploy/installed-state.json
shared/deploy/journals/<operationId>.json
releases/<release>/<bundle S18>
current -> releases/<release confirmada>
previous -> releases/<release anterior>
```

Diretórios criados pelo CLI usam `0700`. `.env`, journals, estado, manifestos
e dumps usam `0600`. O root precisa ser absoluto e não pode conter fuga,
symlink ou componente gravável por grupo/outros. `.env` deve existir como
arquivo regular `0600`; seu conteúdo não é emitido.

## Entrada

O operador fornece somente um operation ID e a release global alvo:

```bash
ops/deploy/deploy-release.sh deploy \
  --operation-id deployment_0123456789abcdef \
  --release v1.2.3
```

Não há input de componente, imagem, banco, comando, host ou path. O bundle em
`releases/v1.2.3` determina integralmente essas decisões.

## Sequência operacional

Antes do journal, o CLI valida argumentos, paths, `.env`, bundle, release,
estado e links. Também executa `docker compose config --quiet` com os dois
env-files na ordem compartilhado e release.

O núcleo S19 mantém lock, journal, retomada e máquina de estados. O adapter
materializa:

1. `PULL`: comprova ou obtém imagens pelos digests do plano;
2. `BACKUP`: sobe somente PostgreSQL, grava dumps em staging e publica
   manifesto atômico;
3. `MIGRATE`: usa exclusivamente `/app/bin/migrate` para ERP e website;
4. `UPDATE`: atualiza somente `servicesToUpdate`, sem PostgreSQL;
5. `VERIFY`: comprova sete serviços saudáveis, seis imagens e quatro smokes
   pelo gateway em loopback;
6. `ROLLBACK`: reativa o bundle histórico ou, na primeira instalação, remove
   apenas os seis serviços comerciais.

Cada ação faz probe antes do side effect. Evidência já comprovada evita
repetição. Saída, stderr, paths e valores de ambiente não entram em erro ou
evidence.

## Primeira instalação e atualização

Na primeira instalação, todas as seis imagens e PostgreSQL são requeridos. Os
dois bancos vazios são inicializados e recebem backup antes das migrations.
Rollback remove somente containers comerciais e preserva PostgreSQL e
volumes.

Na atualização, `current` deve apontar para a origem confirmada e o bundle
histórico precisa ser válido. Rollback usa exclusivamente esse bundle, sem
migration ou restore.

## Backup e restore

Cada dump PostgreSQL vai diretamente a arquivo, nunca à memória do processo.
O diretório final só aparece depois de dumps não vazios, hashes, manifesto
canônico, validação, `fsync` e rename do staging.

Rollback de imagens não restaura banco. Quando migrations podem ter iniciado,
`databaseRestoreRequired=true` permanece como alerta para uma decisão
operacional posterior. Restore automático e retenção estão fora da S20.

## Migrations exclusivas

Os runtimes comerciais possuem Flyway desabilitado. As migrations são
executadas somente pelos entrypoints:

```text
/app/bin/migrate probe
/app/bin/migrate migrate
```

`probe` apenas consulta/valida. Pendência retorna `10`, tudo aplicado retorna
`0` e falha sanitizada retorna `20`. `migrate` aplica migrations forward-only
e valida novamente, sem iniciar servidor HTTP, seeder, scheduler ou aplicação
Spring.

## Links e recuperação

`current` e `previous` nunca mudam antes de o journal terminar
`SUCCEEDED`. Em atualização, `previous` é reconciliado para a origem antes de
`current` apontar ao alvo. Um crash entre as duas trocas é recuperado pelo
replay do mesmo operation ID. Depois das trocas, o CLI faz `fsync` do root e
relê links e estado.

`FAILED` e `ROLLED_BACK` não promovem o alvo.

## Exit codes e diagnóstico

| Exit | Significado |
|---:|---|
| `0` | `SUCCEEDED` |
| `20` | `ROLLED_BACK` |
| `21` | `FAILED` |
| `2`–`5` | classes públicas S18/S19 |
| `6` | falha operacional anterior ao journal |

Sucesso ou terminal transacional produz uma única linha JSON canônica.
Falha anterior produz somente um código estável sanitizado, sem traceback,
stdout, stderr, path, manifesto ou segredo.

## Invocação pelo workflow S21

O [workflow de implantação](./WORKFLOW_IMPLANTACAO.md) instala um bundle S18 já
validado e chama exclusivamente este CLI como `deploy-emporio`. O transporte
aceita somente operation ID e release; não escolhe adapter, ação, componente ou
comando. Exits `0`, `20` e `21` são reconciliados com a única linha JSON
canônica. Perda durante ou após a invocação é resultado remoto indeterminado,
nunca sucesso presumido.

## Pré-requisitos futuros da VPS

A preparação futura deverá prover, sem ser realizada por esta slice:

- usuário dedicado sem privilégios de root;
- root e permissões canônicas;
- `.env` `0600`;
- Docker Compose e curl em binários root-owned e não graváveis;
- autenticação read-only no registry;
- bundles já transportados e validados;
- volumes, espaço de backup e observabilidade operacional.

## Teste local

Testes unitários usam runner/clock fakes. A prova Docker S20 usa somente
imagens Java `:s20` e recursos com prefixo `s20-`, sem porta publicada. Ela
comprova `probe -> migrate -> probe -> migrate`, histórico Flyway e ausência
de servidor HTTP, removendo apenas esses recursos ao final.

Esse procedimento local não acessa GitHub, GHCR, DNS, VPS ou produção. A S21
também foi implementada e validada somente localmente; a instalação do helper,
credenciais e primeira execução permanecem futuras.
