# S32 — Migração da linha Spring para Boot 3.5

> **Estado:** `PLANNED`
> **Tipo:** atualização coordenada de baseline e fechamento do grupo A da S30a
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Autoridade:** este contrato e o checkpoint documental que o adiciona
> **Relatório:** `S32-migracao-spring-boot-3-5.report.md`
> **Commit técnico:** `fix: upgrade Spring baseline to 3.5.16`

## 1. Resultado esperado

Fechar as 13 ocorrências do grupo A medidas pela S30a — seis CVEs únicos de
Spring presentes em `backend` e `website_back` — atualizando os dois componentes
como uma unidade compatível:

| Dependência coordenadora | Atual | Alvo fechado |
|---|---:|---:|
| Spring Boot parent | 3.3.13 | 3.5.16 |
| springdoc-openapi | 2.6.0 | 2.8.17 |

O resultado observável é:

- os dois projetos compilam e passam integralmente em `mvn verify`;
- as imagens `linux/amd64` são construídas pelo frontend e BuildKit pinados;
- o Trivy não encontra nenhum dos 13 achados Spring anteriores;
- `website_back` fica com zero HIGH/CRITICAL;
- no `backend` permanecem apenas os dois achados JasperReports já classificados
  como grupo C, sem achado novo;
- o adaptador de migrations preserva exatamente a semântica anterior no Flyway
  11: somente migrations resolvidas ainda não aplicadas são aceitas pelo modo
  `probe`; qualquer outro erro continua falhando fechado;
- um único commit local é criado, sem push.

CI verde e publicação não são critérios desta slice: os dois achados do grupo C
continuam reservados à S33.

## 2. Decisões técnicas fechadas

### 2.1 Baseline e overrides

Nos dois `pom.xml`:

1. alterar o parent para Spring Boot `3.5.16`;
2. alterar `springdoc.version` para `2.8.17`;
3. remover `jackson-bom.version` e `tomcat.version`, agora regressivos ou
   redundantes frente ao BOM do Boot 3.5.16;
4. preservar os overrides de segurança que o BOM alvo não supera:
   - ambos: PostgreSQL `42.7.12`;
   - `backend`: Thymeleaf `3.1.5.RELEASE`, Commons BeanUtils `1.11.0` e Neethi
     `3.2.2`;
   - `website_back`: Netty `4.1.136.Final`, Protobuf `3.25.5` e
     `grpc-netty-shaded` `1.75.0`.

Não adicionar versões avulsas para módulos Spring, Spring Security, Jackson ou
Tomcat. A unidade de compatibilidade é o BOM do Boot.

### 2.2 Adaptação causal do Flyway 11

O bump puro já foi confrontado em cópia isolada. Ele falha nos dois
`ProductionMigrationMain.java`: no Flyway `11.7.2`, os códigos
`RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED` e
`RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED` pertencem a `CoreErrorCode`, não a
`ErrorCode`.

Em ambos os componentes:

- manter a variável pela interface `ErrorCode` e comparar com os dois valores
  de `CoreErrorCode`;
- mover o classificador para a classe externa com a assinatura package-private
  `static boolean isPending(ValidateOutput migration)` e referenciá-lo por
  `ProductionMigrationMain::isPending`, sem reflection;
- adicionar testes com `ValidateOutput`/`ErrorDetails` reais do Flyway que
  provem os dois códigos aceitos e pelo menos um código diferente rejeitado;
- manter o catch e a saída sanitizada existentes. Não ampliar a lista de erros
  tolerados.

### 2.3 Reprodutibilidade das imagens

Substituir a primeira linha dos dois Dockerfiles por:

```dockerfile
# syntax=docker.io/docker/dockerfile@sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720
```

O validador Java passa a fechar comportamento e versões em ambos os componentes:

- frontend Docker pinado pelo digest acima;
- Boot `3.5.16` e springdoc `2.8.17`;
- ausência dos overrides Jackson/Tomcat;
- presença dos overrides protetivos listados na §2.1;
- uso dos dois valores de `CoreErrorCode` e ausência da forma obsoleta
  `ErrorCode.RESOLVED_*`.

Adicionar mutantes focais em `test_java_images_contract.py`, cobrindo os dois
componentes quando aplicável e exigindo estes erros exatos:

| Mutação | Erro |
|---|---|
| frontend sem o digest fechado | `DOCKERFILE_FRONTEND_INVALID:<componente>` |
| parent diferente de 3.5.16 | `SPRING_BOOT_BASELINE_INVALID:<componente>` |
| springdoc diferente de 2.8.17 | `SPRINGDOC_BASELINE_INVALID:<componente>` |
| reintrodução de Jackson ou Tomcat | `SPRING_BOM_OVERRIDE_FORBIDDEN:<componente>` |
| remoção de override protetivo | `PROTECTIVE_OVERRIDE_MISSING:<componente>:<propriedade>` |
| remoção de `CoreErrorCode` ou reintrodução de `ErrorCode.RESOLVED_` | `FLYWAY_ERROR_CODE_INVALID:<componente>` |

O contrato real deve retornar lista vazia. Não relaxar regras existentes.

## 3. Fronteira

Arquivos técnicos autorizados:

```text
backend/pom.xml
website_back/pom.xml
backend/Dockerfile
website_back/Dockerfile
backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java
website_back/src/main/java/com/baronesa/website/migration/ProductionMigrationMain.java
backend/src/test/java/com/baronesa/emporio/migration/ProductionMigrationMainTest.java
website_back/src/test/java/com/baronesa/website/migration/ProductionMigrationMainTest.java
tools/docker/java_images_contract.py
tools/docker/tests/test_java_images_contract.py
```

Evidências autorizadas:

```text
docs/infrastructure/deployment/implementation/slices/S32-trivy-findings.spring.after.json
docs/infrastructure/deployment/implementation/slices/S32-migracao-spring-boot-3-5.report.md
```

Não alterar outros componentes, workflows, Compose, arquivos de configuração,
migrations SQL, tasks anteriores ou este contrato. Não criar exceção Trivy.

## 4. Preflight

Antes da primeira alteração, confirmar e registrar:

```bash
test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"
test "$(git branch --show-current)" = "main"
test -z "$(git status --porcelain)"
test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"
test "$(git log -1 --format=%s)" = "docs: accept S31 and open S32 scope"
test "$(git rev-list --count origin/main..HEAD)" = "10"
git rev-parse HEAD
```

Todos os `test` devem retornar 0. O último comando fornece o SHA-base; a
mensagem e a contagem evitam a circularidade de embutir nesta task o SHA do
commit que a contém. Divergência exige parada antes de alterar arquivos.

## 5. Verificação local

### 5.1 Contrato e testes

Executar os 13 validadores canônicos e as sete suítes Python usadas na S31.
Executar também, em ambos os componentes:

```bash
mvn -B verify
```

Para o `backend`, usar um PostgreSQL efêmero pinado por digest, publicado apenas
em `127.0.0.1` e removido nominalmente ao final:

```text
postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
```

As credenciais são valores locais descartáveis, fornecidos por ambiente tanto
ao PostgreSQL quanto ao Maven; não registrar material externo. O
`website_back` executa seu `mvn verify` sem banco externo, como comprovado no
preflight do orquestrador.

Registrar a árvore efetiva de dependências para estes artefatos nos dois
componentes:

```text
org.springframework.boot:spring-boot
org.springframework.security:spring-security-web
org.springframework:spring-core
org.springframework:spring-expression
org.springframework:spring-webmvc
org.springframework:spring-webflux
org.flywaydb:flyway-core
org.springdoc:springdoc-openapi-starter-webmvc-ui
```

Ausência de um artefato que não pertence a um componente é resultado válido;
versão Spring fora da linha gerenciada pelo Boot 3.5.16 não é.

### 5.2 Build, probe e Trivy

Usar desde a primeira tentativa:

```text
BuildKit: docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
driver option: network=host
platform: linux/amd64
Trivy: aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e
Trivy network: host
DB: mirror.gcr.io/aquasec/trivy-db:2
```

Construir os dois Dockerfiles com `--load`, `--push=false`, `VCS_REF` igual ao
SHA-base e `IMAGE_VERSION=ci-<SHA-base>`. Os acessos de leitura autorizados são
Docker Hub para as referências pinadas, `repo.maven.apache.org`, os repositórios
Alpine já configurados nas bases e o DB Trivy acima. GHCR, login e push seguem
proibidos.

Contra um PostgreSQL vazio, executar `/app/bin/migrate probe` em cada imagem.
O resultado obrigatório é exit 10 e saída única `MIGRATIONS_PENDING`; outro
erro, stack trace ou exposição de configuração reprova a adaptação Flyway.

Escanear ambas as imagens com:

```text
--severity HIGH,CRITICAL
--ignore-unfixed=false
--exit-code 1
--skip-version-check
```

O scan do `website_back` deve retornar 0. O scan do `backend` deve retornar 1
somente pelos dois achados JasperReports já conhecidos. Qualquer achado Spring,
achado novo ou contagem diferente exige parada sem commit.

Emitir `S32-trivy-findings.spring.after.json` no esquema da S30a, incluindo
linhagem da árvore técnica medida, identidades das duas imagens, metadados dos
bancos, contagens e os achados residuais completos. O delta obrigatório é:

```text
grupo A: 13 ocorrências / 6 CVEs -> 0 / 0
resíduo total: 2 ocorrências / 2 CVEs, ambos grupo C no backend
```

### 5.3 Cleanup

Usar nomes com prefixo `s32-spring-<SHA-curto>` para builder, imagens,
contêineres e volumes. Antes de criar, registrar quais referências já existem.
Ao final remover nominalmente apenas os recursos criados nesta execução,
preservar recursos preexistentes e comprovar `Build Cache 0B`. Prune amplo é
proibido.

## 6. Aceite, parada e commit

A execução é aceita para revisão quando:

1. todos os validadores, suítes Python e os dois `mvn verify` retornam 0;
2. os mutantes novos falham com erros exatos e o contrato real passa;
3. os dois probes retornam 10 com `MIGRATIONS_PENDING` e sem informação extra;
4. o JSON reproduz a linhagem e o delta fechado na §5.2;
5. não há achado Spring nem achado novo;
6. cleanup nominal, `git diff --check` e secret scan rastreado estão limpos;
7. somente os 12 arquivos da §3 entram no commit técnico;
8. o commit usa exatamente `fix: upgrade Spring baseline to 3.5.16`, sem push.

Parar antes do commit diante do primeiro resultado incompatível com esses
critérios. Falha de transporte externa também interrompe a execução; registrar
o comando, exit e erro literal, sem inventar fallback ou correction. O executor
não aceita a slice nem altera o README.

## 7. Relatório

O relatório deve registrar de forma verificável:

- CWD, SHA-base, origin/main, branch, stage/worktree e commits locais;
- arquivos alterados e interpretação do delta;
- comandos relevantes, exits e resultados dos testes;
- versões resolvidas, prova dos códigos Flyway e probes;
- identidades das imagens, Trivy, bancos e árvore medida;
- achados anteriores, removidos, residuais e novos;
- acessos externos, cleanup, resíduos e secret scan;
- SHA final, mensagem do commit e confirmação explícita de ausência de push.

Terminar exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```
