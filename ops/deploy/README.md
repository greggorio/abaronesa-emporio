# Operação local de implantação

> A S20 fornece a fronteira operacional local do núcleo S19. Ela não autoriza
> acesso à VPS, produção, GitHub ou GHCR e não inclui workflow remoto.

## Layout e permissões

O root canônico é `/opt/sistemas/emporio`; somente testes locais podem
substituí-lo por `EMPORIO_DEPLOY_ROOT`, sempre absoluto e seguro.

```text
shared/.env                         0600
shared/backups/<operationId>/       0700
shared/deploy/journals/             0700
shared/deploy/installed-state.json  0600
releases/<vMAJOR.MINOR.PATCH>/      bundle S18
current                             link da release confirmada
previous                            link da origem confirmada
```

Root, pais, `.env`, release e artefatos críticos não podem ser symlink, FIFO,
device ou graváveis por grupo/outros. Diretórios criados usam `0700`; journal,
estado, manifesto e dumps usam `0600`.

## Entrada

O wrapper versionado aceita somente:

```bash
ops/deploy/deploy-release.sh deploy \
  --operation-id deployment_0123456789abcdef \
  --release v0.0.1
```

O CLI revalida o bundle S18, a release, o estado instalado, o bundle histórico
e os links antes de entregar a operação ao núcleo S19. A saída é uma única
linha JSON canônica e sanitizada.

## Sequência das ações

Todo Compose usa o projeto `abaronesa-emporio`, o `.env` compartilhado e,
por último, o `release.env` do bundle. As imagens comerciais vêm apenas do BOM
imutável.

- `PULL`: comprova os digests locais e baixa somente imagens ausentes
  autorizadas.
- `BACKUP`: inicia apenas PostgreSQL, grava dumps diretamente em arquivos e
  publica atomicamente o manifesto completo.
- `MIGRATE`: executa `/app/bin/migrate` em `erp`, depois `website`.
- `UPDATE`: atualiza somente `servicesToUpdate`, sem PostgreSQL.
- `VERIFY`: exige sete serviços saudáveis, seis imagens do BOM e quatro
  requisições pelo gateway loopback.
- `ROLLBACK`: numa atualização usa o bundle da origem; na primeira instalação
  remove somente os seis serviços comerciais.

Nenhuma ação usa `docker compose down`, `--build`, tag mutável, prune ou shell
do host.

## Primeira instalação e atualização

Na primeira instalação não há release de origem. PostgreSQL é iniciado com os
dois bancos vazios, ambos são copiados antes das migrations e um rollback
preserva PostgreSQL e volumes.

Na atualização, o estado confirmado, o bundle histórico e `current` devem
coincidir com `sourceRelease`. O rollback retorna às seis imagens desse bundle,
sem executar migration ou restore.

## Backup

O backup é obrigatório quando o plano exige migration, inclusive no primeiro
deploy. O staging contém `erp.dump` e/ou `website.dump`, seguido de
`backup-manifest.json`. O manifesto fecha propriedades, fixa ordem, nome,
tamanho e SHA-256 dos dumps. Somente depois de releitura, validação e `fsync`
o staging é renomeado para `<operationId>`.

Um dump vazio, parcial, com modo incorreto ou hash divergente falha fechado. A
S20 não restaura banco automaticamente. `databaseRestoreRequired=true`
continua verdadeiro mesmo quando o rollback de imagens tem sucesso.

## Migrations exclusivas

As aplicações normais iniciam com Flyway desabilitado no Compose:

```text
backend.SPRING_FLYWAY_ENABLED=false
website_back.FLYWAY_ENABLED=false
```

Somente os entrypoints `/app/bin/migrate` executam `probe` ou `migrate`, sem
iniciar servidor HTTP, scheduler, seeder ou aplicação comercial. O probe não
altera schema. A configuração Flyway preserva validação, baseline inicial e
`cleanDisabled=true`.

## Probes e evidências

Cada ação reconcilia por probe antes e depois do possível side effect.
Evidências contêm somente tipo, identificadores, digests, hashes, estados e
release já validados. Nunca contêm `.env`, path, stdout, stderr ou segredo.

## Links e recuperação

`current` e `previous` só mudam depois que o journal retorna `SUCCEEDED`. A
troca usa symlink temporário, rename atômico, `fsync` e releitura. Um replay
do mesmo journal reconcilia a janela de crash entre as trocas sem repetir
ações já comprovadas.

`FAILED` e `ROLLED_BACK` não promovem o alvo. Em falha da primeira instalação,
ambos os links permanecem ausentes.

## Exit codes

| Código | Resultado |
|---:|---|
| `0` | `SUCCEEDED` |
| `20` | `ROLLED_BACK` |
| `21` | journal terminal `FAILED` |
| `2`, `3`, `4`, `5` | classe pública preservada de S18/S19 |
| `6` | falha operacional sanitizada anterior ao journal |

Diagnósticos não expõem path, comando, payload, stdout, stderr, segredo ou
traceback.

## Transporte S21

A S21 acrescenta o helper versionado `deployment-remote.py`, sem instalá-lo no
host. Ele expõe apenas `capabilities`, `snapshot`, `install`, `execute` e
`cleanup`, exige o usuário não-root literal `deploy-emporio` e deriva todos os
paths de constantes. Snapshot é somente leitura; install valida e publica o
bundle S18 atomicamente; execute chama apenas este wrapper S20; cleanup remove
somente resíduos da operação.

O runner envia um tar não comprimido de seis arquivos, limitado a 16 MiB, para
`shared/deploy/incoming/<operationId>.tar.part`. Links, PAX, paths, arquivos
extras e modos inseguros são rejeitados antes da instalação. O protocolo e a
operação completa estão em
[`WORKFLOW_IMPLANTACAO.md`](../../docs/infrastructure/deployment/release-control/WORKFLOW_IMPLANTACAO.md).

## Pré-requisitos futuros da VPS

A preparação futura deverá instalar o control root e o helper S21, além de
prover usuário dedicado, root e permissões acima,
`.env` `0600`, binários confiáveis `docker` e `curl`, acesso somente de leitura
ao registry, bundles validados e acesso ao Docker host. A S20 não cria esses
recursos no host real.

## Teste exclusivamente local

Validadores e testes unitários não acessam rede nem produção:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_production_adapter.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  tools/deploy/tests/test_production_adapter_contract.py -v
PYTHONDONTWRITEBYTECODE=1 python3 tools/compose/validate_compose.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/compose/tests -p 'test_*.py'
```

A prova Docker prevista na S20 usa somente recursos locais efêmeros com
prefixo `s20-`, sem porta pública, e remove apenas o que ela própria criou.
