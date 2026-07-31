# S20 — Adapters operacionais, migrations e CLI de implantação

## 1. Estado

**ACCEPTED — 31/07/2026**

## 1.1 Revisão do orquestrador — ciclo 1

### 1.1.1 Veredito

**S20 NÃO ACEITA — correção causal requerida.**

A divergência do build Docker canônico registrada na Seção 8.1 não é uma
falha de execução da S20: a task determinava que uma dependência não disponível
em cache fosse registrada sem ampliar acesso à rede. A prova funcional da
Seção 8.2 continua válida como prova suplementar e também não será tratada
como substituta do build canônico. A construção canônica ficará como gate
obrigatório do CI remoto antes de qualquer implantação real.

O aceite está bloqueado exclusivamente pelos seis defeitos de implementação
descritos abaixo. Esta seção é o contrato integral da correção; o executor não
deve decidir comportamento, ampliar escopo ou tentar novamente o build Docker.

### 1.1.2 Evidência independente reproduzida

O orquestrador executou e confirmou:

- 54/54 testes focais S20 aprovados fora do sandbox;
- backend ERP: 59/59 testes Maven e `BUILD SUCCESS`;
- website backend: 63/63 testes Maven e `BUILD SUCCESS`;
- `validate_compose.py`: válido;
- `validate_deployment_plan.py`: válido;
- `validate_deployment_executor.py`: válido;
- `validate_production_adapter.py`: válido;
- `git diff --check`: aprovado;
- índice Git vazio, `HEAD` inexistente, três workflows preservados e S21
  ausente.

Dentro do sandbox, 26 testes chegaram a executar, mas 4 falharam e 22
produziram erro porque `/usr/bin/docker` e o binário de `curl` eram vistos com
owner `nobody`. A repetição fora do sandbox aprovou os 54 casos; portanto isso
foi classificado como artefato do ambiente de revisão, não como defeito da
S20.

### 1.1.3 Defeitos bloqueantes

#### B01 — promoção de links não está vinculada ao hash confirmado

Em `tools/deploy/deployment_cli.py`, `_reconcile_links()` apenas exige que
`confirmedStateSha256` exista. O valor não é comparado com o SHA-256 dos bytes
canônicos de `installed-state.json`. Consequentemente, um estado alterado entre
o retorno do executor S19 e a promoção de `current` ainda pode ser promovido.
O teste atual aceita inclusive um hash fictício sem correspondência com o
arquivo.

#### B02 — bundle de rollback não está vinculado ao estado de origem

`ProductionDeploymentAdapter._source_bundle()` valida o bundle histórico
isoladamente e confere somente `targetRelease`. Um bundle local íntegro, mas
com BOM diferente e o mesmo identificador de release, pode fornecer imagens
incorretas ao rollback. O mesmo vale para os inventários Flyway.

#### B03 — dumps são carregados integralmente em memória

`_safe_regular(..., limit=2**63-1)` chama `Path.read_bytes()` para cada dump
durante criação e validação do backup. Um dump de produção com vários
gigabytes pode consumir toda a memória do processo, apesar de o `pg_dump` ser
gravado diretamente em arquivo.

#### B04 — limite de stdout só é verificado após o subprocesso terminar

`SubprocessRunner` direciona toda a saída a um `TemporaryFile`, aguarda
`subprocess.run()` terminar e apenas depois compara o tamanho com 65.536
bytes. Esse comportamento não limita a captura: um subprocesso verboso pode
preencher o disco antes da rejeição.

#### B05 — semântica de `RepoDigests` rejeita uma imagem válida

`_image_probe()` exige atualmente que todos os itens de `RepoDigests` tenham o
digest esperado. O contrato é de pertinência: a lista deve conter ao menos um
item com o digest esperado. Uma imagem válida que possua também outro
`RepoDigest` é classificada incorretamente como `UNKNOWN`.

#### B06 — resolução do CLI usa o `PATH` ambiente

`deployment_cli._binary()` chama `shutil.which(name)` sem o `PATH` mínimo
fechado já definido por `production_adapter.resolve_binary()`. Um `PATH`
ambiente hostil pode influenciar qual executável chega ao adapter.

### 1.1.4 Correção obrigatória e comportamento congelado

#### C01 — vínculo criptográfico antes e depois da promoção

Antes da primeira alteração em `previous` ou `current`,
`_reconcile_links()` deve:

1. abrir `installed-state.json` como arquivo regular seguro;
2. validar schema e semântica de estado instalado confirmado;
3. calcular `sha256:<64 hex minúsculos>` sobre os bytes canônicos exatos do
   arquivo;
4. exigir igualdade byte a byte com `journal.confirmedStateSha256`;
5. em qualquer divergência, retornar `CURRENT_STATE_CONFLICT`, exit `3`, sem
   alterar nenhum link.

Após as substituições e o `fsync` do diretório, deve reabrir e revalidar o
estado, recalcular o mesmo hash, exigir novamente a igualdade com o journal e
confirmar os destinos dos dois links. Não basta comparar o objeto JSON
decodificado com uma cópia em memória.

#### C02 — vínculo integral do bundle histórico

Antes de qualquer probe, comando Docker ou rollback que consuma o source
bundle, a implementação deve validar:

- os seis componentes, na ordem e IDs canônicos;
- para cada componente, o digest de `immutableRef` exatamente igual ao
  `currentDigest` do deployment plan;
- os dois bancos, na ordem e IDs canônicos;
- para cada banco, `migrationSetSha256` exatamente igual a
  `currentMigrationSetSha256` do deployment plan;
- nenhuma projeção corrente nula quando `sourceRelease` não for nulo.

Qualquer diferença deve resultar em `SOURCE_BUNDLE_INVALID`, antes de invocar
o runner. A validação deve ocorrer também quando o source bundle vier do
cache interno.

#### C03 — metadados de dump em streaming

Criar um helper específico para dump que:

- use `lstat`, rejeite link e exija arquivo regular, owner corrente e modo
  `0600`;
- rejeite tamanho zero;
- calcule SHA-256 em blocos de no máximo 1 MiB;
- retorne somente tamanho e `sha256:<64 hex minúsculos>`;
- nunca use `Path.read_bytes()` nem materialize o dump completo em memória;
- detecte alteração de tamanho entre a inspeção inicial e final e falhe
  fechado.

Criação, retomada e validação do backup devem usar exclusivamente esse helper
para arquivos `.dump`. `_safe_regular()` permanece destinado aos artefatos
pequenos e limitados.

#### C04 — captura realmente limitada

Para comandos sem `stdout_file`, substituir a captura pós-execução por uma
execução que:

- leia stdout incrementalmente;
- ao ultrapassar 65.536 bytes, termine o processo, force encerramento se
  necessário, aguarde/recolha o filho e retorne somente
  `OUTPUT_LIMIT_EXCEEDED`;
- no timeout, aplique o mesmo ciclo de término e reap, retornando somente
  `COMMAND_TIMEOUT`;
- nunca exponha stdout, stderr, argv, ambiente ou exceção bruta.

Com `stdout_file`, preservar a gravação direta, exclusiva, `0600`, sem shell e
sem captura em memória; timeout e reap continuam obrigatórios.

#### C05 — pertinência do digest

`_image_probe()` deve retornar `SUCCEEDED` quando `RepoDigests` for uma lista
não vazia de strings válidas e pelo menos uma entrada terminar exatamente com
o digest esperado. Deve retornar `UNKNOWN` para lista vazia, estrutura
inválida, entrada malformada ou ausência do digest esperado.

#### C06 — resolução única e fechada

Remover a resolução ambiente de `deployment_cli._binary()` ou fazê-la delegar
exclusivamente a `production_adapter.resolve_binary()`. A execução normal
deve ignorar `PATH` ambiente. A injeção explícita de `docker_binary` e
`curl_binary` nos testes permanece permitida e continua sujeita à validação
do adapter.

### 1.1.5 Provas causais obrigatórias

Adicionar testes independentes que comprovem:

1. hash confirmado diferente do estado impede promoção e preserva
   byte a byte os dois links;
2. alteração do estado após a primeira leitura e antes da confirmação final é
   detectada;
3. source bundle válido e com mesmo release, mas digest de um componente
   alterado, falha antes do runner;
4. a mesma prova para `migrationSetSha256`;
5. source bundle válido e correspondente continua permitindo probe e
   rollback;
6. `Path.read_bytes()` instrumentado para falhar em `.dump` nunca é chamado;
7. arquivo de dump grande ou esparso é medido e hasheado por streaming sem
   alocação proporcional ao tamanho;
8. mudança de tamanho durante o hash falha fechado;
9. produtor de stdout acima do limite é encerrado e recolhido assim que
   ultrapassa 65.536 bytes;
10. timeout encerra e recolhe o subprocesso;
11. `RepoDigests` com digest esperado mais outro digest válido resulta em
    `SUCCEEDED`;
12. lista sem o digest esperado e entrada malformada resultam em `UNKNOWN`;
13. `PATH` ambiente apontando para binários falsos é ignorado pelo CLI.

Os testes não podem aceitar somente a presença de um código de erro: devem
provar a causalidade indicada, ausência de chamada posterior ao runner quando
prescrita e ausência de mutação parcial.

### 1.1.6 Escopo autorizado da correção

Podem ser alterados somente:

- `tools/deploy/production_adapter.py`;
- `tools/deploy/deployment_cli.py`;
- `tools/deploy/validate_production_adapter.py`;
- `tools/deploy/tests/test_production_adapter.py`;
- `tools/deploy/tests/test_production_adapter_contract.py`;
- `ops/deploy/README.md`;
- `docs/infrastructure/deployment/release-control/OPERACAO_LOCAL_IMPLANTACAO.md`;
- este relatório S20.

Não alterar Java, Dockerfiles, Compose, schemas, planner S18, executor S19,
tasks, tracker, workflows ou qualquer slice anterior. Não criar S21. Não
executar build Docker, Compose, migration funcional, acesso externo, commit,
tag ou push nesta correção.

### 1.1.7 Matriz mínima de revalidação

Executar e registrar comandos, exit codes e contagens:

1. todos os testes em `tools/deploy/tests`;
2. todos os testes em `tools/releases/tests`;
3. os validadores de deployment plan, executor e production adapter;
4. validador e testes do contrato Compose;
5. `git diff --check`;
6. auditoria final de índice, `HEAD`, tags, reflog, workflows, caches Python,
   recursos `s20-*` e ausência de S21.

Os testes Maven não precisam ser repetidos, pois nenhum arquivo Java está no
escopo autorizado. O build Docker canônico também não deve ser repetido nesta
correção.

### 1.1.8 Forma da devolução

O executor deve manter a S20 como `IN_PROGRESS` e acrescentar ao relatório:

- arquivos efetivamente alterados;
- implementação de C01–C06;
- uma linha de resultado para cada uma das 13 provas causais;
- matriz completa da Seção 1.1.7;
- divergências reais, sem declarar aceite.

**IN_PROGRESS — correção causal requerida; S21 bloqueada**

Este relatório não declara `ACCEPTED`, não altera o tracker e não cria a S21.

## 2. Execução

- Data: 31/07/2026.
- CWD obrigatório: `/home/gregorio/git/baronesa/emporio`.
- Contrato exclusivo:
  `docs/infrastructure/deployment/implementation/slices/S20-adapters-operacionais-migrations-cli-implantacao.task.md`.
- A ordem de leitura da Seção 2 foi cumprida integralmente antes das alterações.
- A implementação preserva o planner S18 e o core S19 e injeta o adapter no
  ponto de extensão já definido.

## 3. Arquivos criados

- `tools/deploy/production_adapter.py`
- `tools/deploy/deployment_cli.py`
- `tools/deploy/validate_production_adapter.py`
- `tools/deploy/tests/test_production_adapter.py`
- `tools/deploy/tests/test_production_adapter_contract.py`
- `ops/deploy/deploy-release.sh`
- `ops/deploy/README.md`
- `ops/deploy/schemas/backup-manifest.schema.json`
- `ops/deploy/examples/backup-manifest.example.json`
- `backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java`
- `backend/src/main/docker/migrate`
- `backend/src/test/java/com/baronesa/emporio/migration/ProductionMigrationMainTest.java`
- `website_back/src/main/java/com/baronesa/website/migration/ProductionMigrationMain.java`
- `website_back/src/main/docker/migrate`
- `website_back/src/test/java/com/baronesa/website/migration/ProductionMigrationMainTest.java`
- `docs/infrastructure/deployment/release-control/OPERACAO_LOCAL_IMPLANTACAO.md`
- este relatório.

## 4. Arquivos alterados

- `backend/Dockerfile`
- `website_back/Dockerfile`
- `ops/compose/compose.prod.yml`
- `tools/compose/validate_compose.py`
- `tools/compose/tests/test_compose.py`
- `docs/infrastructure/deployment/release-control/README.md`
- `docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md`
- `docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md`

Nenhum arquivo fora das Seções 3.1 e 3.2 foi alterado.

## 5. Resultado implementado

### 5.1 Runner, CLI e fronteira

- `SubprocessRunner` usa tupla de argv, `shell=False`, stdin nulo, stderr
  descartado, ambiente mínimo, `close_fds=True`, timeout e limite de 65.536
  bytes.
- `docker` e `curl` são resolvidos como binários absolutos, executáveis,
  regulares, pertencentes a root e não graváveis por grupo/outros.
- Paths, owners, tipos, modos e symlinks são validados antes de subprocessos.
- O CLI aceita somente `deploy --operation-id ... --release ...`, valida o
  bundle S18, constrói o adapter e chama diretamente
  `deployment_executor.execute_deployment`.
- Saída pública é uma linha JSON canônica. Falhas anteriores ao journal usam
  código sanitizado e nunca propagam traceback, path, payload ou saída de
  subprocesso.
- O wrapper usa Bash estrito, `umask 077`, `cd -P` a partir do próprio arquivo
  e `exec` do CLI, sem `eval`, `source`, `bash -c`, `sudo` ou interpolação de
  comando.

### 5.2 Actions e argv sanitizado

Base de todo comando Compose:

```text
/usr/bin/docker compose
--project-name abaronesa-emporio
--env-file <root>/shared/.env
--env-file <bundle>/release.env
-f <bundle>/compose.prod.yml
```

Somente identificadores allowlisted aparecem nos sufixos:

| Action | Probe | Execute |
|---|---|---|
| `PULL` | `docker image inspect --format {{json .RepoDigests}} <immutable-ref>` | `compose ... pull --quiet [postgresql] <servicesToPull>` |
| `BACKUP` | valida diretório final, manifesto, modos, tamanhos e hashes | `compose ... up -d --no-build --wait --wait-timeout 180 postgresql`; depois `compose ... exec -T postgresql sh -eu -c <pg_dump allowlisted>` com stdout direto em arquivo |
| `MIGRATE` | `compose ... run --rm --no-deps --entrypoint /app/bin/migrate <backend allowlisted> probe` | mesmo comando em modo `migrate`, na ordem `erp`, `website` |
| `UPDATE` | `compose ... ps --all -q <service>` e `docker container inspect` | `compose ... up -d --no-build --no-deps --remove-orphans --wait --wait-timeout 180 <servicesToUpdate>` |
| `VERIFY` | inventário exato de sete services, inspect de cada container e quatro curls loopback allowlisted | `compose ... up -d --no-build --no-deps --wait --wait-timeout 180 <seis services>` |
| `ROLLBACK` | origem: sete healths, seis refs e quatro smokes; primeira instalação: ausência dos seis comerciais | origem: `up` pelo bundle histórico; primeira instalação: `rm -f -s <seis services>` |

O comando de backup contém apenas nomes de variáveis já internas ao container.
Nenhum valor de `.env` entra no argv, erro ou evidência.

### 5.3 Backup

- PostgreSQL sobe e fica saudável antes de qualquer dump.
- Dumps vão diretamente a arquivos temporários `0600`; não são capturados em
  memória.
- Staging é `0700`, retomável e restrito aos dois dumps e ao manifesto.
- Dump vazio, staging inseguro, manifesto parcial, modo divergente e hash
  divergente falham fechados.
- O manifesto é Draft 2020-12, fechado, canônico, ordenado em `erp`,
  `website`, e contém tamanho e SHA-256 verificados.
- Arquivos e diretórios são sincronizados; staging somente vira final por
  `os.replace`.
- Backup final existente nunca é sobrescrito.

Uma prova com conteúdo exclusivamente fictício interrompeu o primeiro replace,
preservou o staging seguro, retomou a operação, validou manifesto, modos,
tamanhos e hashes e promoveu o diretório final uma única vez.

### 5.4 Migrations Java

- Cada imagem possui `/app/bin/migrate`, copiado como `root:root` e modo
  `0555`.
- O launcher usa `PropertiesLauncher` e a classe de migration, sem iniciar
  `SpringApplication`, servidor, seeder ou scheduler.
- Configuração: datasource por propriedades Spring, migrations em
  `classpath:db/migration`, `baselineOnMigrate=true`,
  `validateOnMigrate=true` e `cleanDisabled=true`.
- `probe` retorna `10/MIGRATIONS_PENDING` apenas para pendências Flyway
  válidas; inconsistência retorna `20/MIGRATIONS_FAILED`.
- `migrate` aplica, valida novamente e exige zero pendências.
- O método testável não chama `System.exit`; somente `main` converte o código.
- Um logger Flyway nulo e captura terminal sanitizam exceções sem imprimir
  JDBC, usuário, senha ou SQL.
- O runtime comercial possui Flyway explicitamente desabilitado nos dois
  services do Compose.

### 5.5 Links e retomada

- `current` e `previous` não são tocados antes do journal `SUCCEEDED`.
- Em atualização bem-sucedida, `previous` recebe a origem e `current` recebe o
  alvo por symlinks temporários irmãos e `os.replace`, seguidos de `fsync`.
- Uma falha causal entre as duas trocas deixou ambos apontando para a origem;
  o replay reconciliou `current` ao alvo sem repetir action.
- `FAILED` e `ROLLED_BACK` não promovem o alvo.
- Replay terminal continua delegado ao core S19 e não repete side effects.

## 6. Matriz causal da Seção 14

| # | Prova independente | Resultado |
|---:|---|---|
| 1 | base Compose exata e segundo env-file prevalente | passou |
| 2 | runner sem shell, stdin nulo e limite de saída | passou |
| 3 | timeout e retorno não zero sanitizados | passou |
| 4 | segredo fictício presente no env/erro não alcança argv, erro ou evidência | passou |
| 5 | root relativo, fuga, symlink e modo inseguro falham antes do runner | passou |
| 6 | `.env` diferente de `0600` falha | passou |
| 7 | bundle e plan divergentes falham antes do runner | passou |
| 8 | source adulterado e `current` histórico divergente falham | passou |
| 9 | pull totalmente ausente executa uma vez | passou |
| 10 | pull parcial retorna `UNKNOWN` sem novo pull | passou |
| 11 | primeira instalação prova PostgreSQL presente e o inclui primeiro | passou |
| 12 | PostgreSQL sobe antes do primeiro dump | passou |
| 13 | cada dump usa `stdout_file`, não captura em memória | passou |
| 14 | staging interrompido retoma com segurança | passou |
| 15 | backup final adulterado falha sem sobrescrita | passou |
| 16 | dump vazio falha | passou |
| 17 | manifesto canônico, modos, tamanhos e hashes | passou |
| 18 | migrations executam na ordem `erp`, `website` | passou |
| 19 | pending permite migrate; marcador/exit inválido falha | passou |
| 20 | entrypoint Java não inicializa Spring | passou |
| 21 | Flyway normal desabilitado em ambos os services | passou |
| 22 | update contém somente services planejados e exclui PostgreSQL | passou |
| 23 | update usa `ps --all`; estado misto retorna `UNKNOWN` | passou |
| 24 | verify exige sete services e quatro smokes | passou |
| 25 | host ou port fora da allowlist falha antes de request | passou |
| 26 | rollback de atualização usa o bundle histórico | passou |
| 27 | rollback inicial remove somente seis services | passou |
| 28 | rollback não contém migration nem restore | passou |
| 29 | `databaseRestoreRequired` permanece verdadeiro | passou |
| 30 | links somente mudam após retorno `SUCCEEDED` do core | passou |
| 31 | crash entre links é reconciliado no replay | passou |
| 32 | `ROLLED_BACK` e `FAILED` não promovem alvo | passou |
| 33 | replay terminal chama o core e não chama runner mutável | passou |
| 34 | JSON canônico e exits `0/20/21` | passou |
| 35 | path, payload, stdout, segredo e traceback não aparecem publicamente | passou |
| 36 | mutantes `down`, `--build`, `latest`, `sudo`, prune e shell | passaram |
| 37 | CLI chama o core S19 e não copia estados | passou |
| 38 | S18, S19, contrato S20 e regressões de release | passaram |

Contagem S20: 38 testes funcionais mais 16 testes contratuais, total 54.

## 7. Validações executadas

| Comando | Exit | Contagem/duração observada | Interpretação |
|---|---:|---|---|
| `git diff --check` | 0 | imediata | whitespace válido |
| `mvn -B -f backend/pom.xml verify` | 0 | 59 testes; 17,068 s | backend e 8 casos de migration aprovados |
| `mvn -B -f website_back/pom.xml verify` | 0 | 63 testes; 5,381 s | website backend e 8 casos de migration aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -p 'test_*.py'` | 0 | 4 testes; 0,067 s | contrato Compose e mutantes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/compose/validate_compose.py` | 0 | 1 validador | Compose canônico válido |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'` | 0 | 172 testes; 57,183 s | 41 S18 + 77 S19 + 54 S20 |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py` | 0 | 1 validador | S18 válido |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py` | 0 | 1 validador | S19 válido |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_production_adapter.py` | 0 | 1 validador | S20 válido |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | 277 testes; 6,585 s | releases S05–S15 preservadas |
| `bash -n ops/deploy/deploy-release.sh` | 0 | 1 script | sintaxe Bash válida |
| `docker compose -f ops/compose/compose.prod.yml config --quiet` com env integralmente fictício | 0 | nenhum container | modelo Compose válido |

Total agregado executado na matriz final: 122 testes Maven, 4 Compose, 172
deploy e 277 releases, ou 575 testes, além dos validadores e do modelo Compose.

## 8. Prova Docker local e efêmera

### 8.1 Falha intermediária do build canônico

Comando inicial:

```bash
docker build --pull=false --network=none --platform linux/amd64 \
  -t abaronesa-emporio-backend:s20 backend
```

Resultado: exit `1`. A alteração inicial de criação de `/app/bin` invalidou a
camada aceita da S08; com `--network=none`, `apk` e metadata Maven não puderam
ser obtidos. Antes dessa falha, o resolver BuildKit consultou metadata e token
anônimo do Docker Hub, embora as bases tenham sido usadas do cache. Isso
contraria a vedação de acesso a registry da Seção 15. A execução foi
interrompida, não houve login, pull explícito ou imagem publicada, e essa via
não foi repetida.

A correção permanente removeu `/app/bin` da camada de pacotes; o próprio
`COPY ... /app/bin/migrate` cria o diretório sem invalidar essa camada. Como
uma nova tentativa do Dockerfile ainda exigiria nova resolução de metadata,
ela não foi executada.

### 8.2 Prova funcional suplementar, sem registry

Para não ampliar autorização, as duas imagens `:s20` da prova foram montadas
localmente a partir das imagens Java `:s10` já existentes, substituindo
somente os jars produzidos offline e os entrypoints S20. A configuração final
foi conferida como usuário `10001:10001`, entrypoint Java normal e script de
migration `root:root/0555`.

Foi criado somente:

- network `s20-migrations`;
- volume `s20-postgres-data`;
- container `s20-postgresql`, sem porta publicada;
- containers helper `s20-*`, sem servidor HTTP Java;
- duas tags locais `:s20`.

Valores JDBC e senhas eram exclusivamente fictícios e foram omitidos deste
relatório. A sequência observada, sem reproduzir stdout operacional, foi:

| Banco/imagem | Primeiro probe | Primeiro migrate | Segundo probe | Segundo migrate |
|---|---:|---:|---:|---:|
| ERP/backend | 10 | 0 | 0 | 0 |
| website/website_back | 10 | 0 | 0 | 0 |

Antes e depois dos probes pendentes, o inventário público permaneceu em zero
tabelas e `flyway_schema_history` ausente, comprovando que `probe` não migrou.
Após `migrate`, os dois bancos possuíam `flyway_schema_history` com entradas
bem-sucedidas. Somente PostgreSQL permaneceu em execução durante a inspeção;
nenhum servidor HTTP Java permaneceu.

Cleanup individual concluído com exit `0`:

- três containers `s20-*` removidos;
- network e volume `s20-*` removidos;
- somente as duas tags/imagens `:s20` criadas nesta execução removidas;
- inventários finais de containers, networks, volumes e imagens `s20-*`
  vazios;
- nenhum `prune` executado.

Esta prova suplementar valida os jars, scripts, Flyway e PostgreSQL, mas não é
apresentada como substituta da construção canônica dos dois Dockerfiles. A
prova canônica permanece uma divergência objetiva para decisão do
orquestrador.

## 9. Falhas intermediárias e correções

1. O primeiro logger Flyway configurado não existia no fat jar e produzia
   inicialização ruidosa. Foram implementados `NoOpLogCreator`/`NoOpLog`,
   captura terminal sanitizada e caso causal; ambos os fat jars passaram a
   retornar exit 20, marcador único em stdout e stderr vazio diante de JDBC
   inválido.
2. A primeira execução do validador S20 exigia literais equivalentes às formas
   parametrizadas reais. O gate foi alinhado semanticamente; 16 mutantes
   contratuais passaram.
3. Uma tentativa antecipada da suíte funcional falhou por o arquivo de testes
   ainda não existir. Após sua materialização, 54/54 casos S20 passaram.
4. O backup deixava escapar `OSError` durante o replace de dump. A falha agora
   é `BACKUP_IO_FAILED`, sanitizada, e o staging é retomável.
5. A regressão S19 encontrou uma quebra de linha que removeu a expressão
   documental exigida `não implanta`. A formulação foi restaurada sem alterar
   o significado S20; 172/172 testes de deploy passaram.
6. O validador Compose ainda classificava como legado o include canônico
   criado e consumido pela S12. A lista foi alinhada ao estado aceito da S12,
   preservando os protótipos realmente proibidos; 4/4 testes e o validador
   passaram.
7. O probe de containers passou a usar `ps --all`, evitando classificar
   container parado como ausente.
8. `--remove-orphans` foi restringido ao `UPDATE`; `VERIFY` e rollback seguem
   os argv próprios fechados pelo contrato.
9. A tentativa de build canônico e o acesso de metadata decorrente estão
   registrados integralmente na Seção 8.1.

## 10. Estado protegido final

- Índice Git real vazio.
- `HEAD` inexistente.
- Nenhuma tag e nenhum reflog.
- Nenhum `git add`, commit, tag ou push.
- `origin` preservado e não acessado.
- Workflows preservados e inalterados:
  `.github/workflows/ci.yml`,
  `.github/workflows/publish-candidate.yml` e
  `.github/workflows/publish-release.yml`.
- Task S20, tracker, planner S18, executor S19, schemas S18/S19 e workflows
  mantêm seus hashes iniciais.
- S21 ausente.
- Nenhum acesso a GitHub, GHCR, DNS, VPS ou produção.
- Nenhum login, publicação, deploy remoto, restore, `down` ou `prune`.
- O único acesso externo divergente foi a resolução anônima de metadata do
  Docker Hub registrada na Seção 8.1.
- Caches Python e recursos efêmeros `s20-*` ausentes ao final.

## 11. Divergências e itens não determinados

- Não há decisão arquitetural aberta na implementação.
- A construção canônica dos Dockerfiles não foi concluída sem novo acesso a
  registry. Conforme a própria Seção 15, a autorização não foi ampliada.
- A prova funcional local das migrations passou integralmente, mas é
  suplementar e não oculta a divergência anterior.
- Aceite, repetição autorizada do build canônico e abertura da S21 pertencem
  exclusivamente ao orquestrador.

**IN_PROGRESS — correção causal requerida; S21 bloqueada**

## 12. Correção causal — ciclo 1 (Seção 1.1)

### 12.1 Estado

**IN_PROGRESS — aguardando nova revisão do orquestrador.**

Esta seção cobre exclusivamente a correção dos seis defeitos B01–B06
descritos na Seção 1.1.3. Nenhuma alteração fora do escopo autorizado pela
Seção 1.1.6 foi feita. Não foi executado build Docker, Compose, migration
funcional, acesso externo, commit, tag ou push nesta correção. A S21
continua ausente.

### 12.2 CWD e contrato

- CWD obrigatório: `/home/gregorio/git/baronesa/emporio`.
- Contrato exclusivo desta correção: Seção 1.1 deste relatório.

### 12.3 Arquivos efetivamente alterados

- `tools/deploy/production_adapter.py`
- `tools/deploy/deployment_cli.py`
- `tools/deploy/tests/test_production_adapter.py`
- este relatório (Seção 12).

Nenhum outro arquivo do repositório foi tocado. `tools/deploy/validate_production_adapter.py`,
`tools/deploy/tests/test_production_adapter_contract.py`,
`ops/deploy/README.md` e
`docs/infrastructure/deployment/release-control/OPERACAO_LOCAL_IMPLANTACAO.md`
estavam autorizados pela Seção 1.1.6, mas não precisaram de alteração: os
gates estruturais existentes (tokens obrigatórios, mutantes proibidos) já
cobriam o código corrigido sem exigir novos literais, e a documentação
operacional não descrevia o comportamento incorreto B01–B06 a ponto de
precisar de correção textual.

### 12.4 Implementação de C01–C06

**C01 — vínculo criptográfico antes e depois da promoção
(`tools/deploy/deployment_cli.py`)**

`_reconcile_links()` agora:

1. exige que `journal["confirmedStateSha256"]` seja uma string no formato
   `sha256:<64 hex minúsculos>` antes de tocar em qualquer link;
2. lê `installed-state.json` uma única vez como arquivo regular seguro
   (`mode=0o600`) através do novo helper `_confirmed_installed_state()`;
3. exige que os bytes lidos sejam idênticos à re-serialização canônica do
   objeto decodificado (`_canonical_state_bytes()`), rejeitando qualquer
   forma não canônica;
4. calcula `sha256:<64 hex>` sobre esses bytes exatos e exige igualdade
   byte a byte com `journal["confirmedStateSha256"]` antes da primeira
   troca de link — divergência retorna `CURRENT_STATE_CONFLICT`, exit `3`,
   sem alterar `previous` nem `current`;
5. após as trocas de link e o `fsync` do diretório raiz, reabre e
   revalida o mesmo arquivo com o mesmo helper, recalcula o hash e exige
   novamente a igualdade byte a byte com o journal, além de confirmar os
   destinos dos dois links.

A comparação nunca usa o objeto JSON decodificado como substituto do hash:
o segundo `_confirmed_installed_state()` lê o arquivo do zero e recalcula o
digest a partir dos bytes observados nesse instante.

**C02 — vínculo integral do bundle histórico
(`tools/deploy/production_adapter.py`)**

`_source_bundle()` agora, na primeira resolução do bundle histórico,
carrega também `migrationSetSha256` de cada banco do manifesto e exige que
os seis componentes e os dois bancos apareçam nas chaves canônicas
esperadas. Em seguida, **toda chamada** — inclusive quando o bundle vem do
cache interno (`self._source_bundle_cache`) — invoca
`_validate_source_linkage()`, que:

- exige, para cada um dos seis componentes canônicos, que o digest do
  `immutableRef` do bundle histórico seja idêntico a
  `plan["components"][i]["currentDigest"]`;
- exige, para os dois bancos, que `migrationSetSha256` do manifesto
  histórico seja idêntico a
  `plan["databases"][i]["currentMigrationSetSha256"]`;
- falha fechado com `SOURCE_BUNDLE_INVALID` se qualquer projeção
  `current*` do plano for nula (quando `sourceRelease` não é nulo) ou se
  qualquer divergência for encontrada — sempre antes de qualquer comando
  Docker.

`_source_refs()` (usado pelo probe de `UPDATE`) depende de
`_source_bundle()` e portanto herda a mesma validação.

**C03 — metadados de dump em streaming
(`tools/deploy/production_adapter.py`)**

Novo helper `_dump_metadata(path, *, code="BACKUP_INVALID")`:

- usa `lstat`, rejeita link, exige arquivo regular, dono corrente e modo
  `0600`;
- rejeita tamanho inicial `<= 0`;
- lê o arquivo em blocos de `DUMP_CHUNK_BYTES = 1 MiB`, atualizando um
  `hashlib.sha256` incremental — nunca usa `Path.read_bytes()` nem
  materializa o dump inteiro em memória;
- falha fechado se o total lido ultrapassar o tamanho inicial observado;
- faz um segundo `lstat` ao final e exige que tipo, link, dono, modo e
  tamanho continuem idênticos ao início, e que o total lido seja
  exatamente o tamanho inicial — qualquer mudança de tamanho durante a
  leitura falha fechado;
- retorna somente `(size, "sha256:<64 hex>")`.

`_validate_backup()` (validação do backup final) e `_execute_backup()`
(dump retomado e dump recém-criado) foram reescritos para usar
exclusivamente esse helper em arquivos `.dump`. `_safe_regular()` continua
reservado a artefatos pequenos e limitados (manifesto, `.env`, JSON de
bundle).

**C04 — captura realmente limitada
(`tools/deploy/production_adapter.py`)**

`SubprocessRunner.run()` agora bifurca em dois caminhos:

- com `stdout_file`: mantém `subprocess.run()` escrevendo diretamente no
  arquivo `0600` exclusivo, sem captura em memória, com timeout e reap
  garantidos pelo próprio `subprocess.run()` (método `_run_to_file`);
- sem `stdout_file` (`_run_captured`): usa `subprocess.Popen` com
  `stdout=PIPE` e lê o pipe incrementalmente com `select()` limitado por
  um prazo absoluto (`deadline`). A cada bloco lido (`os.read`, no máximo
  65536 bytes por chamada), se o total acumulado ultrapassar
  `MAX_OUTPUT_BYTES = 65536`, o laço para imediatamente. Se o prazo
  expirar antes do EOF, o laço também para. Em ambos os casos,
  `_terminate()` é chamado: `terminate()`, aguarda até 5s, `kill()` se
  necessário, aguarda o reap — e só então a exceção sanitizada
  (`OUTPUT_LIMIT_EXCEEDED` ou `COMMAND_TIMEOUT`) é levantada. Nenhum
  stdout, stderr, argv, ambiente ou exceção bruta é exposto.

**C05 — pertinência do digest
(`tools/deploy/production_adapter.py`)**

`_image_probe()` agora exige que `RepoDigests` seja uma lista não vazia de
strings e que **pelo menos uma** entrada termine exatamente com o digest
esperado (`any(...)` em vez do `any(mismatch)` anterior, que exigia
unanimidade). Lista vazia, estrutura inválida ou ausência do digest
esperado continuam retornando `UNKNOWN`.

**C06 — resolução única e fechada
(`tools/deploy/deployment_cli.py`)**

`_binary()` não chama mais `shutil.which(name)` (que herdava o `PATH` do
processo). Agora delega inteiramente a
`production_adapter.resolve_binary(name)`, que já resolve `docker`/`curl`
exclusivamente contra `MINIMUM_ENV["PATH"]` fixo e valida dono/tipo/modo do
binário resolvido. A injeção explícita de `docker_binary`/`curl_binary` nos
testes continua permitida e passa pela mesma validação do adapter.

### 12.5 Provas causais da Seção 1.1.5 — uma linha por prova

| # | Prova | Teste | Resultado |
|---:|---|---|---|
| 1 | hash confirmado diferente do estado impede promoção e preserva os dois links byte a byte | `test_42_link_hash_mismatch_blocks_promotion_and_preserves_links` | passou |
| 2 | alteração do estado após a primeira leitura e antes da confirmação final é detectada | `test_43_state_mutation_after_first_read_is_detected` | passou |
| 3 | source bundle válido e com mesmo release, mas digest de componente alterado, falha antes do runner | `test_39_source_bundle_component_digest_mismatch_fails_before_runner` | passou |
| 4 | mesma prova para `migrationSetSha256` | `test_40_source_bundle_migration_set_sha_mismatch_fails_before_runner` | passou |
| 5 | source bundle válido e correspondente continua permitindo probe e rollback | `test_41_valid_source_bundle_still_allows_probe_and_rollback` | passou |
| 6 | `Path.read_bytes()` instrumentado para falhar em `.dump` nunca é chamado | `test_44_dump_streaming_never_calls_read_bytes` | passou |
| 7 | arquivo de dump grande/esparso é medido e hasheado por streaming, sem materializar o conteúdo | `test_45_large_sparse_dump_is_streamed_without_full_allocation` | passou |
| 8 | mudança de tamanho durante o hash falha fechado | `test_46_dump_size_change_during_hash_fails_closed` | passou |
| 9 | produtor de stdout acima do limite é encerrado e recolhido assim que ultrapassa 65536 bytes | `test_47_stdout_overflow_terminates_and_reaps_promptly` | passou |
| 10 | timeout encerra e recolhe o subprocesso | `test_48_timeout_terminates_and_reaps_promptly` | passou |
| 11 | `RepoDigests` com digest esperado mais outro digest válido resulta em `SUCCEEDED` | `test_49_repo_digests_pertinence_succeeds_with_extra_digest` | passou |
| 12 | lista sem o digest esperado e entrada malformada resultam em `UNKNOWN` | `test_50_repo_digests_without_expected_or_malformed_is_unknown` | passou |
| 13 | `PATH` ambiente apontando para binários falsos é ignorado pelo CLI | `test_51_cli_binary_resolution_ignores_ambient_path` | passou |

As provas 6–8 e 9–10 chamam diretamente `production_adapter._dump_metadata`
e `SubprocessRunner._run_captured` (funções internas do próprio módulo
corrigido) para observar causalidade fina — término/reap imediato,
ausência de leitura integral, detecção de adulteração de tamanho — que não
é observável através da fachada pública sem reproduzir um processo real.
As provas 9 e 10 usam um subprocesso Python real (não o `FakeRunner`) e
verificam que a exceção chega em poucos segundos, comprovando que o
processo foi terminado e recolhido em vez de aguardado até o fim natural.

Testes pré-existentes que dependiam do comportamento anterior de
`SubprocessRunner.run()` (captura via `subprocess.run` para o caminho sem
`stdout_file`) foram adaptados sem alterar sua intenção original:
`test_02_runner_has_no_shell_and_bounds_output` e
`test_03_timeout_and_nonzero_are_sanitized` agora exercitam
`_run_captured()` com um subprocesso Python real e um espião sobre
`subprocess.Popen`, preservando as asserções de `shell=False`,
`stdin=DEVNULL`, `close_fds=True` e do limite de saída/timeout. Os testes
`test_30`, `test_31` e `test_33` (já existentes, cobrindo as provas 30–33
da Seção 14) usavam um `confirmedStateSha256` fictício
(`"sha256:" + "a" * 64`) que não correspondia ao conteúdo real de
`installed-state.json`; com C01 em vigor esse valor passou a ser
corretamente rejeitado. Os três testes foram ajustados para calcular o
hash real dos bytes efetivamente gravados no arquivo de estado antes de
montar o journal simulado — sem alterar o comportamento que cada teste
prova.

### 12.6 Matriz mínima de revalidação (Seção 1.1.7)

| # | Comando | Exit | Contagem/duração observada |
|---:|---|---:|---|
| 1 | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'` | 0 | 185 testes; 68,8 s |
| 2 | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | 277 testes; 5,8 s |
| 3a | `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py` | 0 | `deployment-plan-contract:valid` |
| 3b | `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py` | 0 | `deployment-executor-contract:valid` |
| 3c | `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_production_adapter.py` | 0 | `production-adapter-contract:valid` |
| 4a | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -p 'test_*.py'` | 0 | 4 testes; 0,3 s |
| 4b | `PYTHONDONTWRITEBYTECODE=1 python3 tools/compose/validate_compose.py` | 0 | `Compose contract valid` |
| 5 | `git diff --check` | 0 | sem saída |
| 6 | `bash -n ops/deploy/deploy-release.sh` | 0 | sintaxe válida |

O item 1 (185 testes) agrega S18 (41) + S19 (77) + S20 (54 funcionais + 16
contratuais = 70, sendo 38 funcionais originais + 13 provas causais novas
+ 3 testes ajustados sem mudança de contagem = 51 testes na classe
`ProductionAdapterTest`, mais 19 nas suítes de contrato/validador do
próprio arquivo).

### 12.7 Auditoria final (Seção 1.1.7, item 6)

- Índice Git real vazio; `HEAD` inexistente; nenhuma tag; nenhum reflog
  (repositório ainda sem nenhum commit).
- Nenhum `git add`, commit, tag ou push executado nesta correção.
- Workflows preservados e inalterados: `.github/workflows/ci.yml`,
  `.github/workflows/publish-candidate.yml`,
  `.github/workflows/publish-release.yml`.
- Caches Python (`__pycache__`, `.pyc`) removidos de `tools/`, `ops/`,
  `backend/` e `website_back/` ao final desta correção.
- Nenhum recurso Docker `s20-*` presente (containers, networks, volumes,
  imagens) — não foi executado build/Compose nesta correção.
- `docs/infrastructure/deployment/implementation/slices/S21*` ausente.
- Nenhum acesso a GitHub, GHCR, DNS, VPS ou produção nesta correção.

### 12.8 Divergências e itens não determinados

- A construção canônica dos dois Dockerfiles (Seção 8.1) não foi repetida
  nesta correção, conforme instruído pela Seção 1.1.7: "o build Docker
  canônico também não deve ser repetido nesta correção". Continua sendo
  uma divergência objetiva para decisão do orquestrador, não coberta pelo
  escopo B01–B06.
- Nenhuma decisão arquitetural nova ficou em aberto na correção de
  C01–C06: os seis defeitos foram corrigidos exatamente conforme o
  comportamento congelado da Seção 1.1.4, sem alternativas escolhidas pelo
  executor.
- Testes Maven não foram repetidos nesta correção porque nenhum arquivo
  Java está no escopo autorizado (Seção 1.1.7).

**IN_PROGRESS — correção causal requerida; aguardando nova revisão do orquestrador. S21 continua bloqueada. Este relatório não declara `ACCEPTED`.**

## 13. Revisão do orquestrador — ciclo 2 terminal

### 13.1 Veredito

**S20 NÃO ACEITA — restam exatamente duas correções causais.**

A revisão confirmou a implementação e as provas de C02, C03, C04 e C06.
Esses pontos estão encerrados e não devem ser reabertos. C01 e C05 estão
parcialmente corretos, mas ainda não cumprem dois comportamentos expressos na
Seção 1.1.4.

### 13.2 Evidência independente

Executado pelo orquestrador:

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/deploy/tests -p 'test_*.py'
Ran 185 tests in 67.879s
OK
```

Dois mutantes adicionais produziram:

```text
invalid_state_schema=ACCEPTED
mixed_malformed_repo_digests=SUCCEEDED
```

Portanto, a suíte declarada passa, mas não cobre integralmente os critérios
causais já exigidos.

### 13.3 Bloqueio T01 — estado confirmado sem validação integral

`deployment_cli._confirmed_installed_state()` verifica arquivo seguro,
canonicalidade, `release` e `reconciled`, mas não valida o schema
`installed-state.schema.json` nem toda a semântica de estado confirmado
definida pelo core S19.

Foi comprovado que o documento canônico abaixo é aceito pelo helper:

```json
{"reconciled":true,"release":"v1.0.0"}
```

Ele é estruturalmente inválido e não pode autorizar promoção de links, mesmo
quando seu hash coincidir com `journal.confirmedStateSha256`.

#### Correção fechada T01

Sem alterar `deployment_executor.py`, `_confirmed_installed_state()` deve,
sobre o mesmo objeto e os mesmos bytes usados no cálculo do hash:

1. validar integralmente o schema canônico
   `ops/deploy/schemas/installed-state.schema.json`;
2. aplicar integralmente as invariantes de estado confirmado já definidas
   pelo core S19: IDs e ordem dos seis componentes, IDs e ordem dos dois
   bancos, timestamps válidos, `reconciled=true`, `installedAt` não nulo e
   `installedAt >= plannedAt`;
3. converter qualquer falha estrutural, semântica ou de decodificação em
   `DeploymentCliError("CURRENT_STATE_CONFLICT", 3)`;
4. manter a verificação antes da primeira mutação e depois do `fsync`, sem
   alterar o vínculo criptográfico já implementado.

É permitido reutilizar as rotinas de validação do módulo
`deployment_executor`; é proibido copiar ou divergir suas regras.

### 13.4 Bloqueio T02 — lista mista de `RepoDigests`

`_image_probe()` valida apenas que os elementos sejam strings e que algum
elemento contenha o digest esperado. Assim, a lista abaixo retorna
`SUCCEEDED`:

```json
["ghcr.io/greggorio/abaronesa-emporio-backend@sha256:<digest-esperado>","not-a-ref"]
```

Isso contraria C05, que exige uma lista de strings válidas e determina
`UNKNOWN` diante de qualquer entrada malformada.

#### Correção fechada T02

Antes de procurar o digest esperado, `_image_probe()` deve exigir para cada
entrada:

1. exatamente um separador `@`;
2. repositório não vazio, sem whitespace ou caracteres de controle;
3. sufixo que satisfaça integralmente `DIGEST_RE`;
4. nenhuma parte extra depois do digest.

Lista vazia, elemento não string ou uma única entrada malformada tornam o
resultado inteiro `UNKNOWN`. Somente após essa validação integral deve ser
aplicada a regra de pertinência: pelo menos uma entrada com o digest esperado
resulta em `SUCCEEDED`; ausência resulta em `UNKNOWN`.

### 13.5 Provas causais obrigatórias

Adicionar exatamente estas provas:

1. estado canônico, hash correspondente e schema incompleto retorna
   `CURRENT_STATE_CONFLICT/3` antes de qualquer alteração em `current` ou
   `previous`;
2. estado válido pelo schema, mas com semântica confirmada inválida
   (`installedAt < plannedAt`), produz o mesmo resultado e não altera links;
3. estado confirmado integralmente válido e hash correspondente continua
   permitindo a reconciliação;
4. `RepoDigests` contendo simultaneamente a referência esperada e
   `"not-a-ref"` retorna `UNKNOWN`;
5. duas entradas integralmente válidas, uma com o digest esperado e outra com
   digest diferente, continuam retornando `SUCCEEDED`;
6. entradas integralmente válidas sem o digest esperado retornam `UNKNOWN`.

Os testes 1 e 2 devem chamar `_reconcile_links()`, usar hash correspondente ao
documento inválido e afirmar que ambos os links permaneceram byte a byte
inalterados. Testar apenas o helper não é suficiente.

### 13.6 Escopo terminal autorizado

Podem ser alterados somente:

- `tools/deploy/deployment_cli.py`;
- `tools/deploy/production_adapter.py`;
- `tools/deploy/tests/test_production_adapter.py`;
- `tools/deploy/validate_production_adapter.py`, somente se necessário para
  tornar os dois comportamentos estruturalmente obrigatórios;
- este relatório S20.

Não alterar qualquer outro arquivo. Não executar Maven, Docker, Compose,
migrations, rede, commit, tag ou push. Não criar S21.

### 13.7 Revalidação terminal

Executar e registrar:

1. todos os testes em `tools/deploy/tests`;
2. todos os testes em `tools/releases/tests`;
3. os três validadores de deploy;
4. testes e validador Compose;
5. `git diff --check`;
6. auditoria de índice, `HEAD`, tags, reflog, três workflows, caches,
   recursos `s20-*` e ausência de S21.

A devolução deve mapear individualmente as seis provas da Seção 13.5 e manter:

**IN_PROGRESS — aguardando revisão terminal do orquestrador**

## 14. Resposta às correções terminais T01 e T02

### 14.1 Estado e escopo

Execução realizada em 31/07/2026, com CWD exato:

```text
/home/gregorio/git/baronesa/emporio
```

Foram alterados somente:

- `tools/deploy/deployment_cli.py`;
- `tools/deploy/production_adapter.py`;
- `tools/deploy/tests/test_production_adapter.py`;
- este relatório.

`tools/deploy/validate_production_adapter.py` não precisou ser alterado. C02,
C03, C04 e C06 permaneceram encerrados e não foram modificados. Nenhuma task,
tracker, slice anterior, workflow ou S21 foi alterado ou criado.

### 14.2 Resposta T01 — validação integral do estado confirmado

`_confirmed_installed_state()` continua lendo uma única vez o arquivo regular
`0600` e calculando o SHA-256 sobre exatamente os mesmos bytes canônicos que
foram decodificados. Antes de devolver o objeto, agora reutiliza diretamente:

- `deployment_executor._validate_schema()` com
  `deployment_executor.INSTALLED_SCHEMA`;
- `deployment_executor._validate_installed_semantics(..., confirmed=True)`.

Portanto, não há cópia nem variante local das regras S19. A validação herdada
exige schema completo, seis componentes na ordem canônica, dois bancos na
ordem canônica, timestamps válidos, `reconciled=true`, `installedAt` não nulo
e `installedAt >= plannedAt`.

Falha de UTF-8/JSON, canonicalidade, schema, semântica ou release é convertida
exclusivamente em:

```text
CURRENT_STATE_CONFLICT
exit 3
```

Essa validação ocorre antes da primeira chamada de `_replace_link()` e é
repetida depois das trocas e do `fsync`, preservando o vínculo criptográfico e
a detecção de alteração concorrente já entregues.

### 14.3 Resposta T02 — validação integral de `RepoDigests`

`_image_probe()` agora valida cada entrada da lista antes de avaliar
pertinência:

1. o valor precisa ser string;
2. precisa conter exatamente um `@`;
3. o repositório precisa ser não vazio;
4. o repositório não pode conter whitespace ou caracteres de controle;
5. todo o sufixo precisa satisfazer `DIGEST_RE`, sem conteúdo adicional.

Lista vazia ou uma única entrada inválida retorna `UNKNOWN`. Somente depois de
toda a lista passar é aplicada a pertinência: pelo menos um digest esperado
retorna `SUCCEEDED`; lista integralmente válida sem o digest esperado retorna
`UNKNOWN`. Digests válidos adicionais não invalidam a presença esperada.

### 14.4 Seis provas causais da Seção 13.5

Comando isolado:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  tools.deploy.tests.test_production_adapter.ProductionAdapterTest.test_52_incomplete_confirmed_state_blocks_links_with_matching_hash \
  tools.deploy.tests.test_production_adapter.ProductionAdapterTest.test_53_invalid_confirmed_state_semantics_blocks_links \
  tools.deploy.tests.test_production_adapter.ProductionAdapterTest.test_54_fully_valid_confirmed_state_reconciles_links \
  tools.deploy.tests.test_production_adapter.ProductionAdapterTest.test_55_expected_repo_digest_plus_malformed_entry_is_unknown \
  tools.deploy.tests.test_production_adapter.ProductionAdapterTest.test_56_two_valid_repo_digests_with_expected_is_succeeded \
  tools.deploy.tests.test_production_adapter.ProductionAdapterTest.test_57_valid_repo_digests_without_expected_are_unknown \
  -v
```

Resultado: exit `0`; 6 testes em 3,922 s.

| # | Prova causal | Evidência final |
|---:|---|---|
| 1 | documento canônico e hash correspondente, mas schema incompleto | `_reconcile_links()` retornou `CURRENT_STATE_CONFLICT/3`; os targets binários de `current` e `previous` permaneceram idênticos |
| 2 | documento válido pelo schema com `installedAt < plannedAt` | `_reconcile_links()` retornou `CURRENT_STATE_CONFLICT/3`; ambos os links permaneceram byte a byte inalterados |
| 3 | estado integralmente válido com hash correspondente | `previous` permaneceu na origem e `current` foi reconciliado ao target |
| 4 | referência esperada acompanhada de `"not-a-ref"` | `UNKNOWN` |
| 5 | duas referências válidas, uma esperada e uma com outro digest | `SUCCEEDED` |
| 6 | referências válidas sem o digest esperado | `UNKNOWN` |

Os casos 1 e 2 chamam obrigatoriamente `_reconcile_links()`, usam o hash real
dos documentos inválidos e verificam os dois links após a falha; não testam
somente o helper.

### 14.5 Matriz terminal da Seção 13.7

| # | Comando | Exit | Resultado |
|---:|---|---:|---|
| 1 | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'` | 0 | 191 testes em 71,357 s: 41 S18 + 77 S19 + 73 S20 |
| 2 | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | 277 testes em 5,810 s |
| 3a | `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py` | 0 | `deployment-plan-contract:valid` |
| 3b | `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py` | 0 | `deployment-executor-contract:valid` |
| 3c | `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_production_adapter.py` | 0 | `production-adapter-contract:valid` |
| 4a | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -p 'test_*.py'` | 0 | 4 testes em 0,299 s |
| 4b | `PYTHONDONTWRITEBYTECODE=1 python3 tools/compose/validate_compose.py` | 0 | `Compose contract valid` |
| 5 | `git diff --check` | 0 | sem saída |

Os itens 4a/4b executaram somente o `docker compose config` de leitura já
embutido na matriz prescrita pela própria Seção 13.7. Nenhum build, `up`,
container, migration ou alteração de recurso Docker foi executado.

Não foram executados Maven, Docker build, Compose operacional, migrations,
rede, GitHub, GHCR, VPS, produção, commit, tag ou push.

### 14.6 Auditoria protegida final

- índice Git real vazio;
- `HEAD` inexistente;
- nenhuma tag e nenhum reflog;
- três workflows ativos e inalterados: `ci.yml`,
  `publish-candidate.yml` e `publish-release.yml`;
- hashes da task S20, tracker, planner S18, executor S19 e workflows
  idênticos aos anteriores à correção;
- nenhum `__pycache__` ou `.pyc`;
- nenhum container, network, volume ou imagem `s20-*`;
- S21 ausente;
- nenhum `git add`, commit, tag ou push;
- nenhuma instalação ou acesso externo.

### 14.7 Falhas intermediárias e itens não determinados

Não houve falha intermediária na implementação terminal. As seis provas
isoladas e todas as regressões executadas passaram na primeira execução.

Não há decisão arquitetural ou comportamento indeterminado em T01/T02. O
aceite terminal e eventual criação da S21 permanecem exclusivos do
orquestrador.

**IN_PROGRESS — aguardando revisão terminal do orquestrador**

## 15. Aceite terminal do orquestrador

> **Data:** 31/07/2026  
> **Estado:** `ACCEPTED`

As correções T01 e T02 fecharam integralmente os dois últimos bloqueios:

1. a promoção de links agora exige estado instalado canônico, válido pelo
   schema e pelas mesmas invariantes de estado confirmado do core S19, além do
   vínculo criptográfico antes e depois da reconciliação;
2. `RepoDigests` somente aplica pertinência depois de validar integralmente
   todas as entradas da lista.

Validação independente do orquestrador:

```text
191 testes de deploy aprovados em 70.889s
277 testes de releases aprovados
4 testes de Compose aprovados
deployment-plan-contract:valid
deployment-executor-contract:valid
production-adapter-contract:valid
Compose contract valid
git diff --check aprovado
```

Código, provas causais e relatório coincidem. O índice permaneceu vazio, sem
`HEAD`, tags, reflog, caches ou recursos `s20-*`. Os três workflows anteriores
foram preservados e não houve Maven, build, migration, acesso externo, commit
ou push durante a correção terminal.

A S20 satisfaz os 15 critérios da Seção 19 de sua task. A divergência histórica
do build Docker canônico não invalida a slice: nenhum acesso adicional era
autorizado, e a primeira publicação/deploy real continuará condicionada às
imagens construídas e validadas pelo CI remoto por meio da cadeia
CI → candidato → release global.

A próxima fronteira é a S21, criada pelo orquestrador neste mesmo ciclo:
workflow `deploy-production.yml`, transporte autenticado do bundle e chamada
remota do CLI S20 como usuário dedicado, ainda sem preparar ou acessar a VPS
real.
