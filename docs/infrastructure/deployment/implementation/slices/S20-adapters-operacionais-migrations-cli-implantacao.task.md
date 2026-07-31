# S20 — Adapters operacionais, migrations e CLI de implantação

> **Estado:** `IN_PROGRESS`  
> **Tipo:** implementação local da fronteira operacional, sem acesso à produção  
> **Dependências:** S01 a S19 `ACCEPTED`  
> **Relatório de saída:** `S20-adapters-operacionais-migrations-cli-implantacao.report.md`

## 1. Objetivo fechado

Implemente o primeiro adapter operacional do núcleo transacional S19 e o
entrypoint que será chamado futuramente pelo workflow de produção.

A S20 deve entregar:

1. runner de processos sem shell e com saída sanitizada;
2. adapter Docker Compose para `PULL`, `BACKUP`, `MIGRATE`, `UPDATE`,
   `VERIFY` e `ROLLBACK`;
3. entrypoints Java exclusivos de Flyway, sem inicializar a aplicação
   comercial;
4. CLI e wrapper `ops/deploy/deploy-release.sh`;
5. backup PostgreSQL atômico e verificável;
6. smoke tests pelos dois virtual hosts através do gateway em loopback;
7. reconciliação atômica dos links `current` e `previous` depois do sucesso;
8. testes com runner fake e uma prova Docker local/efêmera das migrations.

Não implementar workflow, API deployer, UI de produção, bootstrap da VPS ou
primeiro deploy real.

## 2. Ordem de leitura obrigatória

Leia integralmente, nesta ordem:

1. esta task;
2. `docs/infrastructure/deployment/implementation/README.md`;
3. `docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md`;
4. `docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md`;
5. `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
6. `tools/deploy/deployment_plan.py`;
7. `tools/deploy/deployment_executor.py`;
8. `ops/compose/compose.prod.yml`;
9. `ops/env/.env.example`;
10. `ops/gateway/conf.d/emporio.conf`;
11. os dois Dockerfiles Java;
12. a seção “Execução do deploy na VPS” de
    `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`.

O executor não deve reinterpretar ou substituir as decisões S18/S19.

## 3. Fronteira de arquivos

### 3.1 Criar

```text
tools/deploy/production_adapter.py
tools/deploy/deployment_cli.py
tools/deploy/validate_production_adapter.py
tools/deploy/tests/test_production_adapter.py
tools/deploy/tests/test_production_adapter_contract.py
ops/deploy/deploy-release.sh
ops/deploy/README.md
ops/deploy/schemas/backup-manifest.schema.json
ops/deploy/examples/backup-manifest.example.json
backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java
backend/src/main/docker/migrate
backend/src/test/java/com/baronesa/emporio/migration/ProductionMigrationMainTest.java
website_back/src/main/java/com/baronesa/website/migration/ProductionMigrationMain.java
website_back/src/main/docker/migrate
website_back/src/test/java/com/baronesa/website/migration/ProductionMigrationMainTest.java
docs/infrastructure/deployment/release-control/OPERACAO_LOCAL_IMPLANTACAO.md
docs/infrastructure/deployment/implementation/slices/S20-adapters-operacionais-migrations-cli-implantacao.report.md
```

### 3.2 Alterar somente

```text
backend/Dockerfile
website_back/Dockerfile
ops/compose/compose.prod.yml
tools/compose/validate_compose.py
tools/compose/tests/test_compose.py
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md
docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md
```

Não altere task, tracker, schemas S18/S19, `deployment_plan.py`,
`deployment_executor.py`, workflows ou `release_control`.

## 4. Fora de escopo

Não:

- acessar GitHub, GHCR, DNS, VPS ou produção;
- fazer login em registry;
- publicar imagem;
- executar `git add`, commit, tag ou push;
- criar `.github/workflows/deploy-production.yml`;
- implementar download ou transporte de bundle;
- implementar rotas HTTP ou UI do modo deployer;
- criar usuário, diretório ou unit systemd no host real;
- alterar Nginx do host ou TLS;
- restaurar banco automaticamente;
- executar `docker compose down`;
- executar `docker system prune`, `docker image prune` ou remover recursos
  alheios;
- implementar retenção ou cópia externa de backup;
- promover release escolhendo componentes individualmente;
- criar S21.

Docker e PostgreSQL são permitidos somente numa stack local efêmera,
identificada com prefixo `s20-`, sem portas públicas e removida ao final.

## 5. Layout operacional canônico

O root padrão é:

```text
/opt/sistemas/emporio
```

O código deve derivar, sem argumentos livres:

```text
<root>/
├── shared/
│   ├── .env
│   ├── backups/
│   │   └── <operationId>/
│   └── deploy/
│       ├── installed-state.json
│       └── journals/
├── releases/
│   └── <vMAJOR.MINOR.PATCH>/
│       ├── manifest.json
│       ├── compose.prod.yml
│       ├── release.env
│       ├── deployment-plan.json
│       ├── installed-state.next.json
│       └── bundle.sha256
├── current -> releases/<release-confirmada>
└── previous -> releases/<release-anterior-confirmada>
```

O CLI recebe somente:

```text
deploy --operation-id <id> --release <vMAJOR.MINOR.PATCH>
```

`EMPORIO_DEPLOY_ROOT` pode substituir o root apenas para teste/local. Deve ser
absoluto. Root, pais, `.env`, release e artefatos críticos não podem ser
symlink, FIFO, device ou arquivo gravável por grupo/outros. O `.env` deve ser
regular, modo `0600`, e seu conteúdo nunca deve ser lido para logs.

Diretórios criados pela CLI usam `0700`; journals, estado, manifests e dumps
usam `0600`. Paths com `..`, NUL, quebra de linha, resolução para `/` ou fuga
do root falham antes de subprocesso.

## 6. CLI e wrapper

`deployment_cli.py` deve:

1. validar argumentos e paths;
2. exigir bundle em `releases/<release>`;
3. chamar `deployment_plan.validate_bundle`;
4. confirmar que `targetRelease` é exatamente `--release`;
5. validar `.env` e executar `docker compose config --quiet`;
6. numa atualização, exigir:
   - `installed-state.json` confirmado;
   - bundle histórico da origem íntegro e com release correspondente;
   - para journal novo ou não terminal, `current` apontando exatamente para
     `releases/<sourceRelease>`;
   - para replay do mesmo journal `SUCCEEDED`, aceitar somente uma das duas
     janelas reconciliáveis: `current` ainda na origem ou já no target; em
     ambos os casos o estado instalado precisa ser o target confirmado e
     `previous`, se já trocado, precisa apontar para a origem;
7. construir `ProductionDeploymentAdapter`;
8. chamar `deployment_executor.execute_deployment` sem duplicar sua máquina;
9. reconciliar links após retorno terminal;
10. emitir uma única linha JSON canônica e sanitizada.

Saída permitida:

```json
{"databaseRestoreRequired":false,"errorCode":null,"operationId":"deployment_0123456789abcdef","state":"SUCCEEDED"}
```

Não imprimir path, comando, stdout/stderr, manifesto, env, exception ou
traceback.

Exit codes:

| Código | Resultado |
|---|---|
| `0` | `SUCCEEDED` |
| `20` | `ROLLED_BACK` |
| `21` | journal terminal `FAILED` |
| `2`, `3`, `4`, `5` | preservar a classe pública S18/S19 |
| `6` | falha operacional sanitizada anterior ao journal |

`ops/deploy/deploy-release.sh` deve conter apenas:

- shebang Bash;
- `set -euo pipefail`;
- `umask 077`;
- resolução segura do root do repositório a partir do próprio arquivo;
- `exec python3 tools/deploy/deployment_cli.py "$@"`.

Não usar `eval`, `source`, `bash -c`, interpolação de comando ou `sudo`.

## 7. Runner de processos

`production_adapter.py` deve declarar:

```python
@dataclass(frozen=True)
class ProcessResult:
    return_code: int
    stdout: bytes

class ProcessRunner(Protocol):
    def run(
        self,
        argv: tuple[str, ...],
        *,
        timeout_seconds: int,
        stdout_file: Path | None = None,
    ) -> ProcessResult: ...
```

O runner real usa `subprocess.run` com:

- `shell=False`;
- argv como tupla;
- stdin em `DEVNULL`;
- `close_fds=True`;
- timeout explícito;
- ambiente mínimo fixo (`PATH`, `LANG`, `LC_ALL`);
- no máximo `65536` bytes capturados;
- stderr nunca incluído em diagnóstico público;
- stdout de `pg_dump` enviado diretamente a arquivo `0600`, nunca à memória.

Binários `docker` e `curl` são dependências explícitas. Seus paths resolvidos
devem ser absolutos, regulares, não symlink, pertencentes a root e não
graváveis por grupo/outros. Nenhuma credencial pode aparecer em argv.

O adapter aceita runner e clock injetados. Testes funcionais não executam
subprocessos reais.

## 8. Comando Compose canônico

Todo comando Compose usa exatamente esta base:

```text
docker compose
--project-name abaronesa-emporio
--env-file <root>/shared/.env
--env-file <bundle>/release.env
-f <bundle>/compose.prod.yml
```

O segundo env-file prevalece para as seis imagens comerciais. Serviço,
release e action vêm apenas de allowlists/contratos validados.

Timeouts:

| Operação | Segundos |
|---|---:|
| compose config/version | 30 |
| pull | 600 |
| subir/aguardar PostgreSQL | 180 |
| cada dump | 600 |
| cada migration | 600 |
| update/rollback | 300 |
| cada curl | 15 |

Não usar `--build`, tag mutável, `latest`, `down` ou shell do host.

## 9. Flyway exclusivo

Cada backend Java deve fornecer `/app/bin/migrate`, executável e pertencente a
root, que inicia `ProductionMigrationMain` dentro de `/app/app.jar` por
`PropertiesLauncher`. Ele não inicia `SpringApplication`, servidor HTTP,
seeders, schedulers ou beans comerciais.

`ProductionMigrationMain` possui dois modos:

```text
probe
migrate
```

Configuração Flyway exata:

- datasource vindo de `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`;
- `classpath:db/migration`;
- `baselineOnMigrate=true`;
- `validateOnMigrate=true`;
- `cleanDisabled=true`;
- nenhuma rescue/baseline-to-latest automática.

Comportamento:

| Modo | Exit | Marcador final |
|---|---:|---|
| probe, tudo aplicado e válido | `0` | `MIGRATIONS_APPLIED` |
| probe, existem pendências válidas | `10` | `MIGRATIONS_PENDING` |
| migrate, aplicação e validação final concluídas | `0` | `MIGRATIONS_APPLIED` |
| configuração, checksum ou Flyway inválido | `20` | `MIGRATIONS_FAILED` |

Exception, URL JDBC, usuário, senha e SQL nunca são impressos. O método
testável não chama `System.exit`; somente `main` converte o resultado.

No Compose de produção:

```text
backend.SPRING_FLYWAY_ENABLED="false"
website_back.FLYWAY_ENABLED="false"
```

O validador Compose deve exigir os dois valores. Assim, migrations pertencem
somente ao step `MIGRATE`; a inicialização normal das aplicações não disputa
o banco.

O adapter mapeia:

```text
erp     -> backend
website -> website_back
```

Probe e execução usam:

```text
docker compose ... run --rm --no-deps --entrypoint /app/bin/migrate <service> probe
docker compose ... run --rm --no-deps --entrypoint /app/bin/migrate <service> migrate
```

Marcador final desconhecido, exit inesperado ou saída acima do limite produz
falha fechada.

## 10. Semântica operacional por action

### 10.1 `PULL`

Probe:

- para cada serviço de `context.services`, executar `docker image inspect`;
- exigir que `RepoDigests` contenha exatamente o digest da immutableRef alvo;
- na primeira instalação, confirmar também que a imagem PostgreSQL resolvida
  pelo Compose existe localmente;
- todos presentes: `SUCCEEDED`;
- todos ausentes: `ABSENT`;
- conjunto parcial, digest divergente ou saída inválida: `UNKNOWN`.

Execute:

```text
docker compose ... pull --quiet <services>
```

Na primeira instalação, incluir `postgresql` antes dos seis serviços. Depois,
o segundo probe precisa comprovar os digests.

### 10.2 `BACKUP`

Probe:

- ausência do diretório final: `ABSENT`;
- staging próprio e seguro, sem final: `ABSENT`;
- final válido e integral: `SUCCEEDED`;
- final parcial, symlink, modo incorreto ou hash divergente: `FAILED`.

Execute:

1. `docker compose ... up -d --no-build --wait --wait-timeout 180 postgresql`;
2. criar `<operationId>.staging` em `shared/backups`;
3. para cada banco alterado, executar dentro de `postgresql`:

```text
sh -eu -c 'PGPASSWORD="$ERP_DB_PASSWORD" exec pg_dump --format=custom --no-owner --no-acl --username="$ERP_DB_USER" --dbname="$ERP_DB_NAME"'
sh -eu -c 'PGPASSWORD="$WEBSITE_DB_PASSWORD" exec pg_dump --format=custom --no-owner --no-acl --username="$WEBSITE_DB_USER" --dbname="$WEBSITE_DB_NAME"'
```

4. direcionar stdout diretamente para `erp.dump`/`website.dump`;
5. exigir dump não vazio;
6. criar e validar `backup-manifest.json`;
7. `fsync` dos arquivos e diretório;
8. renomear staging para `<operationId>` e fazer `fsync` do pai.

Somente variáveis já existentes dentro do container aparecem no `sh -c`;
nenhum valor secreto entra no argv do host.

O backup é obrigatório inclusive na primeira implantação: primeiro é criado o
PostgreSQL alvo com os dois bancos vazios/inicializados; depois ambos são
dumpados antes das migrations.

### 10.3 Manifesto do backup

Schema Draft 2020-12, propriedades fechadas:

```json
{
  "schemaVersion": 1,
  "kind": "deployment-backup",
  "operationId": "deployment_0123456789abcdef",
  "sourceRelease": null,
  "targetRelease": "v0.0.1",
  "createdAt": "2026-07-31T12:00:00Z",
  "databases": [
    {"id":"erp","file":"erp.dump","sha256":"sha256:<64-hex>","size":1024},
    {"id":"website","file":"website.dump","sha256":"sha256:<64-hex>","size":1024}
  ],
  "complete": true
}
```

A ordem dos bancos é sempre `erp`, `website`, filtrada pelos bancos alterados.

### 10.4 `MIGRATE`

Probe executa `probe` nos entrypoints correspondentes:

- todos `MIGRATIONS_APPLIED`: `SUCCEEDED`;
- todos ou algum `MIGRATIONS_PENDING`, sem falha: `ABSENT`;
- qualquer falha, marcador inválido ou mistura impossível: `FAILED`.

Execute chama `migrate` em ordem `erp`, `website`. Não executa SQL diretamente
e não chama a aplicação comercial.

### 10.5 `UPDATE`

Probe consulta containers pelo projeto Compose e exige, para cada serviço:

- container único;
- `Config.Image` igual à immutableRef esperada;
- estado `running`;
- health `healthy`.

Todos no alvo: `SUCCEEDED`; todos na origem/ausentes: `ABSENT`; estado misto,
duplicado, unhealthy ou não reconhecido: `UNKNOWN`.

Execute:

```text
docker compose ... up -d --no-build --no-deps --remove-orphans \
  --wait --wait-timeout 180 <servicesToUpdate>
```

Não atualizar `postgresql` nessa action.

### 10.6 `VERIFY`

Probe exige:

1. os sete serviços exatos do Compose em `running/healthy`;
2. as seis imagens comerciais iguais ao BOM alvo;
3. PostgreSQL saudável;
4. quatro requisições pelo loopback do gateway:

```text
Host: erp-emporio.abaronesa.net.br      GET /healthz
Host: erp-emporio.abaronesa.net.br      GET /
Host: emporio.abaronesa.net.br          GET /healthz
Host: emporio.abaronesa.net.br          GET /
```

URL base exata:

```text
http://127.0.0.1:<GATEWAY_LOOPBACK_PORT>
```

O port vem de configuração validada, default `8120`, inteiro entre
`1024..65535`; não é obtido lendo segredos do `.env`.

Execute repete `docker compose ... up -d --no-build --no-deps --wait` para os
seis serviços e então executa os mesmos checks. Não faz request destrutivo,
login ou acesso DNS.

### 10.7 `ROLLBACK`

Atualização:

- usar exclusivamente o bundle histórico
  `releases/<sourceRelease>`;
- validar esse bundle antes de comando;
- `up -d --no-build --no-deps --wait` para os seis serviços comerciais;
- verificar imagens da origem, sete health checks e os quatro smokes;
- nunca executar migration ou restore.

Primeira instalação:

- usar o bundle alvo;
- executar `docker compose ... rm -f -s` somente para os seis serviços
  comerciais;
- preservar `postgresql` e os quatro volumes;
- sucesso significa ausência dos seis containers comerciais.

`databaseRestoreRequired=true` permanece verdadeiro mesmo se o rollback de
imagens for comprovado.

## 11. Evidências

Cada `ProbeResult` usa instante UTC injetado e `evidence_id` sanitizado:

```text
pull:<12-hex>
backup:<12-hex>
migrate:<12-hex>
update:<12-hex>
verify:<12-hex>
rollback:<12-hex>
```

O sufixo é derivado de JSON canônico contendo apenas ids, digests, hashes,
estados e release já validados. Nunca inclui path, stdout, env ou segredo.

Probe não altera aplicação comercial, dados, backup ou link. O probe de
`MIGRATE` pode criar e remover somente o container helper efêmero produzido
por `docker compose run --rm`; o modo Java `probe` não chama `migrate`,
`repair`, `baseline` ou `clean`. Um teste deve comparar o inventário/tabelas
antes e depois de `probe` pendente e comprovar ausência de mutação de schema.
Qualquer outra ação mutável pertence a `execute`.

## 12. Links `current` e `previous`

Os links não são alterados antes de o core retornar.

Se o journal retornar `SUCCEEDED`:

1. validar novamente `installed-state.json`;
2. criar symlink temporário irmão para o target;
3. se havia origem, trocar `previous` para a origem;
4. trocar `current` para o target;
5. fazer `fsync` do diretório raiz;
6. reler ambos os links e o estado instalado.

Em replay de `SUCCEEDED`, reconciliar links idempotentemente.

Em `ROLLED_BACK` ou `FAILED`, manter `current` na origem. Na primeira
implantação falha/compensada, `current` e `previous` permanecem ausentes.

Link existente que aponta para fora de `releases`, para release inesperada ou
para alvo diferente do estado confirmado falha fechado. Não apagar diretório
de release.

## 13. Segurança e sanitização

Requisitos obrigatórios:

- nenhum socket Docker montado em container comercial ou `release_control`;
- adapter executado como processo externo dedicado;
- nenhum `shell=True`, `os.system`, `os.popen`, `eval` ou comando concatenado;
- nenhum valor de `.env` em argv, log, exception ou evidência;
- nenhum stdout/stderr bruto propagado;
- nenhum traceback na CLI;
- allowlists exatas de services, databases, actions e hosts;
- limite de tamanho para toda saída e JSON;
- leitura fail-closed de symlink, modo, owner e tipo;
- escrita atômica, `fsync`, releitura e verificação para backup e links;
- erro operacional público pertence a uma allowlist documentada.

## 14. Testes causais obrigatórios

Use runner e clock fakes. Cada item abaixo deve ter prova independente:

1. argv Compose exato e segundo env-file por último;
2. runner nunca usa shell e limita saída;
3. timeout e retorno não zero são sanitizados;
4. segredo fictício não aparece em argv, erro ou evidência;
5. root relativo, fuga, symlink e permissões inseguras falham antes do runner;
6. `.env` diferente de `0600` falha;
7. bundle/release divergente falha antes do runner;
8. source/current histórico divergente falha;
9. pull totalmente ausente executa uma vez;
10. pull parcial falha sem novo pull;
11. primeira instalação inclui PostgreSQL no pull;
12. backup sobe PostgreSQL antes de `pg_dump`;
13. dumps vão para arquivos, não stdout capturado;
14. staging interrompido pode ser retomado;
15. backup final adulterado falha sem sobrescrita;
16. dump vazio falha;
17. manifesto de backup canônico, modos e hashes;
18. ordem de migration `erp`, `website`;
19. pending permite migrate; invalid não permite;
20. entrypoint Java não inicializa Spring;
21. runtime normal possui Flyway desabilitado no Compose;
22. update usa somente serviços planejados e nunca PostgreSQL;
23. estado misto no probe update retorna `UNKNOWN`;
24. verify exige sete serviços e quatro smokes;
25. host ou port não allowlisted falha;
26. rollback de atualização usa bundle da origem;
27. rollback de primeira instalação remove somente seis serviços;
28. rollback nunca chama migration/restore;
29. `databaseRestoreRequired` não é reduzido;
30. sucesso troca `previous/current` somente depois do journal `SUCCEEDED`;
31. crash entre trocas de link é reconciliável no replay;
32. `ROLLED_BACK`/`FAILED` não promovem target;
33. operação terminal repetida não repete side effect;
34. saída CLI canônica e códigos `0/20/21`;
35. mensagens públicas não contêm path, payload, stdout ou traceback;
36. mutantes com `down`, `--build`, `latest`, `sudo`, prune ou shell falham;
37. o adapter importa e chama o core S19, sem copiar sua máquina;
38. contratos S18/S19 e releases continuam verdes.

## 15. Prova Docker local das migrations

Depois dos testes unitários:

1. construir somente as imagens Java locais:

```text
abaronesa-emporio-backend:s20
abaronesa-emporio-website-back:s20
```

2. criar PostgreSQL efêmero sem publicar porta;
3. criar os bancos `erp` e `website`;
4. executar `/app/bin/migrate probe` e comprovar exit `10`;
5. executar `/app/bin/migrate migrate` e comprovar exit `0`;
6. repetir `probe` e `migrate`, ambos exit `0`;
7. confirmar tabelas `flyway_schema_history`;
8. confirmar que nenhum servidor HTTP Java permaneceu;
9. remover somente recursos `s20-*`.

Use imagens/base já disponíveis localmente. Não fazer login, pull explícito ou
acesso a registry. Se dependência indispensável não estiver em cache, registre
o bloqueio; não amplie autorização de rede.

## 16. Validadores e comandos finais

Executar e registrar, no mínimo:

```bash
git diff --check

mvn -B -f backend/pom.xml verify
mvn -B -f website_back/pom.xml verify

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/compose/tests -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 tools/compose/validate_compose.py

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/deploy/tests -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_production_adapter.py

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_*.py'

bash -n ops/deploy/deploy-release.sh
```

Também executar `docker compose config` com env efêmero fictício e os testes
da Seção 15. Não reutilizar segredo real.

Remover `__pycache__`, `.pyc`, caches de teste, containers, networks, volumes e
imagens `:s20` criados pela slice. Não remover cache Maven/npm ou recurso
preexistente.

## 17. Documentação

`OPERACAO_LOCAL_IMPLANTACAO.md` e `ops/deploy/README.md` devem documentar:

- layout e permissões;
- inputs do CLI;
- sequência real de cada action;
- primeira instalação versus atualização;
- backup e ausência de restore automático;
- migrations exclusivas;
- probes/evidências;
- links e recuperação após crash;
- exit codes;
- diagnóstico sanitizado;
- pré-requisitos que a futura preparação da VPS deverá prover;
- procedimento local de teste sem produção.

Atualize `PLANO_IMPLANTACAO.md` e `TRANSACAO_IMPLANTACAO.md` somente para trocar
a fronteira “S20 futura” pelo adapter entregue, mantendo workflow, deployer,
VPS e produção como próximos passos.

## 18. Relatório obrigatório

O relatório deve conter:

- CWD;
- arquivos criados/alterados;
- decisões executadas sem alternativas abertas;
- comandos exatos, exit codes, duração, contagem e interpretação;
- matriz dos 38 casos causais;
- argv sanitizado de cada action;
- prova dos entrypoints Flyway e da stack efêmera;
- prova de backup atômico com conteúdo fictício;
- prova de rollback sem restore;
- prova dos links e retomada;
- falhas intermediárias e correções;
- resíduos antes/depois;
- `git diff --check`, status, índice, HEAD, tags, reflog e workflows;
- confirmação de zero GitHub/GHCR/VPS/produção;
- confirmação de que S21 não foi criada;
- divergências e itens não determinados.

O relatório não pode expor `.env`, dump, credencial ou stdout operacional.

## 19. Critérios de aceite

A S20 somente poderá ser aceita se:

1. o core S19 permanecer inalterado;
2. migrations forem executáveis sem iniciar aplicações;
3. aplicações normais não rodarem Flyway;
4. toda action possuir probe real e determinístico;
5. backup preceder migration e for atômico/verificável;
6. update usar somente digests e serviços planejados;
7. verify cobrir sete health checks e os dois virtual hosts;
8. rollback de imagem não alegar restore;
9. primeira instalação for definida sem escolhas do operador;
10. links forem pós-sucesso, atômicos e retomáveis;
11. nenhum segredo entrar em argv/log/evidência;
12. nenhum shell ou comando destrutivo proibido existir;
13. prova Docker local das migrations passar;
14. regressões S10, S18, S19 e releases permanecerem verdes;
15. estado Git e workspace permanecerem protegidos.

## 20. Resposta final esperada do executor

Informe:

- caminho absoluto do relatório;
- arquivos alterados;
- contagens de testes por suíte;
- resultado dos três validadores de deploy;
- resultado Maven e Docker local;
- ordem operacional comprovada;
- comportamento de backup/migration/update/verify/rollback;
- segurança do runner e sanitização;
- estado dos links;
- divergências e itens não determinados;
- confirmação de zero acesso externo/produção;
- confirmação de que não criou S21.

Mantenha:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED`.

## 21. Próxima fronteira

Depois do aceite da S20, a S21 prevista criará o workflow
`deploy-production.yml`, o transporte autenticado do bundle e a chamada
remota do CLI como usuário dedicado. A S21 não deve ser antecipada.
