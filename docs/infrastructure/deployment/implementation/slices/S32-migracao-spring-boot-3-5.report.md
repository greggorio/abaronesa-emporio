# S32 — relatório de execução

> **Estado da execução:** primeira passagem bloqueada na §5.2 por achado novo no
> `backend`; retomada pela correction-01 concluída com commit técnico
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Autoridade:** `S32-migracao-spring-boot-3-5.task.md` e
> `S32-migracao-spring-boot-3-5.correction-01.md`
> **SHA-base:** `5b859b039d064c536b9eaba1348babc6890e2e7f`
> **Checkpoint da retomada:** `f95376fffd3f5fa0fba23584452c988b8be4f83b`
> **Mensagem do commit:** `fix: upgrade Spring baseline to 3.5.16`
> **Push:** não executado

As seções 1 a 9 registram a primeira passagem e permanecem inalteradas, inclusive
a condição de parada. A seção 10 registra a retomada da correction-01 e é a que
descreve o estado final.

## 1. Preflight da §4

| Comando | Exit | Saída |
|---|---:|---|
| `test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"` | 0 | vazia |
| `test "$(git branch --show-current)" = "main"` | 0 | vazia |
| `test -z "$(git status --porcelain)"` | 0 | vazia |
| `test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"` | 0 | vazia |
| `test "$(git log -1 --format=%s)" = "docs: accept S31 and open S32 scope"` | 0 | vazia |
| `test "$(git rev-list --count origin/main..HEAD)" = "10"` | 0 | vazia |
| `git rev-parse HEAD` | 0 | `5b859b039d064c536b9eaba1348babc6890e2e7f` |

Branch `main`; `origin/main` em `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`; dez
commits locais; stage e worktree vazios antes da primeira alteração. Nenhuma
divergência. A task foi lida integralmente antes de qualquer alteração.

## 2. Arquivos alterados

Os dez arquivos técnicos da §3, todos dentro da fronteira:

```text
backend/Dockerfile
backend/pom.xml
backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java
backend/src/test/java/com/baronesa/emporio/migration/ProductionMigrationMainTest.java
tools/docker/java_images_contract.py
tools/docker/tests/test_java_images_contract.py
website_back/Dockerfile
website_back/pom.xml
website_back/src/main/java/com/baronesa/website/migration/ProductionMigrationMain.java
website_back/src/test/java/com/baronesa/website/migration/ProductionMigrationMainTest.java
```

Evidências criadas, autorizadas pela §3:

```text
docs/infrastructure/deployment/implementation/slices/S32-trivy-findings.spring.after.json
docs/infrastructure/deployment/implementation/slices/S32-migracao-spring-boot-3-5.report.md
```

Nenhum outro componente, workflow, Compose, arquivo de configuração, migration
SQL, task anterior ou o próprio contrato S32 foi alterado. Nenhuma exceção
Trivy foi criada.

### 2.1 Baseline e overrides — §2.1

Nos dois `pom.xml`: parent `spring-boot-starter-parent` de `3.3.13` para
`3.5.16`; `springdoc.version` de `2.6.0` para `2.8.17`; removidas as
propriedades `jackson-bom.version` e `tomcat.version`.

Overrides protetivos preservados literalmente:

| Componente | Override | Valor |
|---|---|---|
| ambos | `postgresql.version` | `42.7.12` |
| `backend` | `thymeleaf.version` | `3.1.5.RELEASE` |
| `backend` | `commons-beanutils` (dependencyManagement) | `1.11.0` |
| `backend` | `neethi` (dependencyManagement) | `3.2.2` |
| `website_back` | `netty.version` | `4.1.136.Final` |
| `website_back` | `protobuf-java` (dependencyManagement) | `3.25.5` |
| `website_back` | `grpc-netty-shaded` (dependencyManagement) | `1.75.0` |

Nenhuma versão avulsa foi acrescentada para módulos Spring, Spring Security,
Jackson ou Tomcat.

### 2.2 Adaptação causal do Flyway 11 — §2.2

Confirmado por `javap` sobre `flyway-core-11.7.2.jar` que a migração de API é
exatamente a descrita pela task:

```text
public interface org.flywaydb.core.api.ErrorCode {
}
public final class org.flywaydb.core.api.CoreErrorCode extends java.lang.Enum<...> implements org.flywaydb.core.api.ErrorCode {
  public static final org.flywaydb.core.api.CoreErrorCode RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED;
  public static final org.flywaydb.core.api.CoreErrorCode RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED;
```

`ErrorCode` deixou de ser enum e passou a ser interface; os dois códigos
pertencem a `CoreErrorCode`. Nos dois componentes:

- a variável permanece tipada pela interface `ErrorCode`;
- a comparação usa os dois valores de `CoreErrorCode`;
- o classificador foi movido de `FlywayOperations` para a classe externa, com
  assinatura package-private `static boolean isPending(ValidateOutput migration)`;
- a referência passou a ser `ProductionMigrationMain::isPending`, sem reflection;
- o `catch (Throwable ignored)` e a saída sanitizada permanecem inalterados;
- a lista de erros tolerados **não** foi ampliada: continuam exatamente dois.

Forma final idêntica nos dois componentes:

```java
    static boolean isPending(ValidateOutput migration) {
        ErrorCode code = migration.errorDetails.errorCode;
        return code == CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED
                || code == CoreErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED;
    }
```

Testes acrescentados nos dois componentes, com `ValidateOutput` e `ErrorDetails`
reais do Flyway, sem mock:

- `pendingClassifierAcceptsOnlyResolvedNotAppliedCodes` — aceita
  `RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED` e
  `RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED`;
- `pendingClassifierRejectsEveryOtherFlywayErrorCode` — rejeita
  `APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED` e
  `APPLIED_REPEATABLE_MIGRATION_NOT_RESOLVED`.

### 2.3 Reprodutibilidade e contrato das imagens — §2.3

A primeira linha dos dois Dockerfiles passou a ser:

```dockerfile
# syntax=docker.io/docker/dockerfile@sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720
```

`tools/docker/java_images_contract.py` passou a fechar, nos dois componentes:
frontend Docker pelo digest acima; parent Boot `3.5.16`; springdoc `2.8.17`;
ausência de qualquer propriedade cujo nome contenha `jackson` ou `tomcat`;
presença dos overrides protetivos com valor exato; presença dos dois valores de
`CoreErrorCode` e ausência da forma obsoleta `ErrorCode.RESOLVED_`.

A verificação da forma obsoleta usa `(?<!Core)\bErrorCode\.RESOLVED_`, porque
`CoreErrorCode.RESOLVED_` contém a subcadeia `ErrorCode.RESOLVED_`; sem essa
âncora a regra rejeitaria o código correto. O contrato lê agora também
`backend/pom.xml` e os dois `ProductionMigrationMain.java`, acrescentados a
`ContractFiles` e a `default_files()`. Nenhuma regra existente foi relaxada,
removida ou tornada condicional.

## 3. Matriz da §5.1

### 3.1 Treze validadores canônicos — todos exit 0

| Comando | Duração | Saída |
|---|---:|---|
| `python3 tools/docker/validate_node_images.py validate` | 0,038840 s | `node-images-contract:valid` |
| `python3 tools/docker/java_images_contract.py validate` | 0,039294 s | `java-images-contract:valid` |
| `python3 tools/ci/validate_ci.py` | 0,040043 s | `ci:valid` |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0,090390 s | `candidate-workflow:valid` |
| `python3 tools/releases/validate_release_workflow.py` | 0,103676 s | `release-workflow:valid` |
| `python3 tools/releases/validate_publisher_ui.py` | 0,028247 s | `publisher-ui:valid` |
| `python3 tools/releases/validate_publisher_identity_bridge.py` | 0,030342 s | `publisher-identity-bridge:valid` |
| `python3 tools/ci/validate_workflow_inventory.py` | 0,071467 s | `workflow-inventory:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0,114316 s | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0,056158 s | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0,032758 s | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | 0,094271 s | `release-control-contract:valid` |
| `python3 tools/security/bootstrap_contract.py validate` | 0,034011 s | `bootstrap-contract:valid` |

### 3.2 Sete suítes Python — todas exit 0

| Suíte | Duração | Resultado unittest |
|---|---:|---|
| `tools/docker/tests` | 1,170 s | 98 testes em 1,114 s; `OK` |
| `tools/ci/tests` | 4,504 s | 30 testes em 4,374 s; `OK` |
| `tools/candidates/tests` | 3,569 s | 68 testes em 3,439 s; `OK` |
| `tools/releases/tests` | 6,314 s | 298 testes em 6,171 s; `OK` |
| `tools/security/tests` | 0,080 s | 26 testes em 0,038 s; `OK` |
| `tools/compose/tests` | 0,126 s | 4 testes em 0,076 s; `OK` |
| `tools/gateway/tests` | 0,045 s | 4 testes em 0,001 s; `OK` |

Total: 528 testes, todos verdes. A suíte `tools/docker/tests` passou de 87 para
98 casos, pelos onze mutantes focais acrescentados.

### 3.3 Mutantes da §2.3 — erros exatos

O contrato real retorna lista vazia (`test_01_real_contract_is_valid`). Cada
mutante abaixo produz o erro exato exigido, verificado por `assertIn`:

| # | Mutação | Componentes | Erro exigido e observado |
|---:|---|---|---|
| 38 | frontend trocado por `# syntax=docker/dockerfile:1.7` | ambos | `DOCKERFILE_FRONTEND_INVALID:<componente>` |
| 39 | frontend com repositório certo e sem digest | `backend` | `DOCKERFILE_FRONTEND_INVALID:backend` |
| 40 | parent revertido para `3.3.13` | ambos | `SPRING_BOOT_BASELINE_INVALID:<componente>` |
| 41 | springdoc revertido para `2.6.0` | ambos | `SPRINGDOC_BASELINE_INVALID:<componente>` |
| 42 | `tomcat.version` reintroduzido | ambos | `SPRING_BOM_OVERRIDE_FORBIDDEN:<componente>` |
| 43 | `jackson-bom.version` reintroduzido | ambos | `SPRING_BOM_OVERRIDE_FORBIDDEN:<componente>` |
| 44 | override protetivo por propriedade removido | `backend`: `postgresql.version`, `thymeleaf.version`; `website_back`: `postgresql.version`, `netty.version` | `PROTECTIVE_OVERRIDE_MISSING:<componente>:<propriedade>` |
| 45 | override protetivo gerenciado rebaixado | `backend`: `commons-beanutils`, `neethi`; `website_back`: `protobuf-java`, `grpc-netty-shaded` | `PROTECTIVE_OVERRIDE_MISSING:<componente>:<artefato>` |
| 46 | um valor de `CoreErrorCode` substituído por outro código | ambos | `FLYWAY_ERROR_CODE_INVALID:<componente>` |
| 47 | forma obsoleta `ErrorCode.RESOLVED_` reintroduzida | ambos | `FLYWAY_ERROR_CODE_INVALID:<componente>` |
| 48 | idem, com igualdade estrita da lista de erros | `backend` | lista exatamente `["FLYWAY_ERROR_CODE_INVALID:backend"]` |

O caso 48 usa `assertEqual` sobre a lista inteira, provando que a regra nova não
dispara nenhum outro erro colateral.

### 3.4 `mvn -B verify` nos dois componentes

| Componente | Exit | Duração | Resultado |
|---|---:|---:|---|
| `website_back` | 0 | 5,171 s | `Tests run: 65, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` |
| `backend` | 0 | 24,902 s | `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` |

`ProductionMigrationMainTest` passou de 8 para 10 casos em cada componente.

O `backend` usou o PostgreSQL efêmero pinado
`postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297`,
container `s32-spring-5b859b0-postgres`, publicado exclusivamente em
`127.0.0.1` numa porta efêmera livre do host. A porta 5432 já estava ocupada
por instância preexistente do host, e a porta 5434 pelo container preexistente
`baronesa-postgres`; nenhuma das duas foi tocada. As credenciais são valores
locais descartáveis, gerados no processo e fornecidos por ambiente tanto ao
PostgreSQL quanto ao Maven, via `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`; nenhum material
externo foi usado e nenhum valor é registrado aqui.
`backend/src/test/resources/application-test.properties` **não** foi alterado —
está fora da fronteira da §3; a sobreposição ocorreu apenas por ambiente.
O `website_back` executou seu `mvn verify` sem banco externo.

### 3.5 Versões efetivamente resolvidas

`mvn -B dependency:list -DincludeScope=runtime`, exit 0 nos dois componentes:

| Artefato | `backend` | `website_back` |
|---|---|---|
| `org.springframework.boot:spring-boot` | 3.5.16 | 3.5.16 |
| `org.springframework.security:spring-security-web` | 6.5.11 | 6.5.11 |
| `org.springframework:spring-core` | 6.2.19 | 6.2.19 |
| `org.springframework:spring-expression` | 6.2.19 | 6.2.19 |
| `org.springframework:spring-webmvc` | 6.2.19 | 6.2.19 |
| `org.springframework:spring-webflux` | ausente | 6.2.19 |
| `org.flywaydb:flyway-core` | 11.7.2 | 11.7.2 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.8.17 | 2.8.17 |

A ausência de `spring-webflux` no `backend` é resultado válido pela §5.1: o
componente não declara WebFlux. Nenhuma versão Spring ficou fora da linha
gerenciada pelo Boot 3.5.16.

## 4. Paridade Docker da §5.2

### 4.1 Estado registrado antes de criar recursos

Nenhuma imagem, container, volume ou builder com prefixo `s32-spring` existia.
As quatro referências pinadas do caminho Docker — BuildKit, Trivy, base Maven e
base Temurin — estavam **ausentes** do daemon, assim como a imagem PostgreSQL
pinada. `docker system df` registrava `Build Cache 0B`.

### 4.2 Builder pinado com rede host

```text
docker buildx create --name s32-spring-5b859b0-builder --driver docker-container --driver-opt image=docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684 --driver-opt network=host --platform linux/amd64 --use
```

Exit 0; bootstrap exit 0. Prova literal:

```text
/buildx_buildkit_s32-spring-5b859b0-builder0|docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684|sha256:e0e67ea9c4bb07bdef683e29f70f3b623d1eb9c82274a9ec04e6bb0511449fb0|host|running
Driver Options:        network="host" image="docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684"
Status:                running
BuildKit version:      v0.31.2
Platforms:             linux/amd64*, ...
```

O BuildKit e o frontend pinados foram usados desde a primeira tentativa; não
houve build com builder default, frontend flutuante ou retry.

### 4.3 Builds `linux/amd64`

Cada build executado uma única vez, com `--load`, `--push=false`,
`VCS_REF=5b859b039d064c536b9eaba1348babc6890e2e7f` e `IMAGE_VERSION=ci-5b859b0`:

| Componente | Tag | Exit | Duração | Maven no estágio de build |
|---|---|---:|---:|---|
| `backend` | `s32-spring-5b859b0-backend:local` | 0 | 238,258 s | `BUILD SUCCESS` |
| `website_back` | `s32-spring-5b859b0-website-back:local` | 0 | 70,979 s | `BUILD SUCCESS` |

O frontend resolvido em ambos foi literalmente
`docker-image://docker.io/docker/dockerfile@sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720`.

Identidades das imagens:

```text
backend      | sha256:0ab3657677e80faf18abd865a2b8aad13fddfca1a97fccd445fa6f014f192864 | amd64 | linux | 10001:10001 | ["java","-jar","/app/app.jar"] | alpine 3.23.5
website_back | sha256:4356a2a83077a3e67406bf00917395e3f6f721b71593ec4f7f65abf8b3b977aa | amd64 | linux | 10001:10001 | ["java","-jar","/app/app.jar"] | alpine 3.23.5
```

Ambas trazem `org.opencontainers.image.revision=5b859b039d064c536b9eaba1348babc6890e2e7f`
e `org.opencontainers.image.version=ci-5b859b0`.

### 4.4 Probes `/app/bin/migrate probe`

Executados contra bancos **vazios** recém-criados no PostgreSQL efêmero
(`probe_backend` e `probe_website`), com o container na rede do host para
alcançar `127.0.0.1` na porta efêmera. Resultado nos dois componentes:

| Componente | Exit | stdout | bytes | linhas |
|---|---:|---|---:|---:|
| `backend` | 10 | `MIGRATIONS_PENDING` | 19 | 1 |
| `website_back` | 10 | `MIGRATIONS_PENDING` | 19 | 1 |

`cat -A` confirmou `MIGRATIONS_PENDING$` como conteúdo integral do stdout, sem
qualquer outra linha. Verificações explícitas sobre o stdout: não contém `jdbc`,
nome de banco, usuário ou senha; não contém `Exception` nem stack trace.

O stderr dos dois containers contém exclusivamente uma linha da própria JVM,
transcrita abaixo sem o espaço final que ela realmente possui após os dois
pontos, para não introduzir whitespace terminal neste arquivo:

```text
Picked up JAVA_TOOL_OPTIONS:
```

Ela decorre de `ENV JAVA_TOOL_OPTIONS=""`, que já existia nos dois Dockerfiles
antes da S32 e não foi tocada por esta slice. O valor é vazio: a linha não expõe
configuração alguma. O marcador contratual permanece o único conteúdo do stdout,
e o exit é 10 em ambos, como a §5.2 exige.

### 4.5 Trivy — resultado e condição de parada

Trivy pinado, rede host, banco `mirror.gcr.io/aquasec/trivy-db:2`, volume
`s32-spring-5b859b0-trivy-cache`, com `--severity HIGH,CRITICAL`,
`--ignore-unfixed=false`, `--exit-code 1` e `--skip-version-check`. Nenhuma
tentativa adicional foi necessária.

| Componente | Exit | Duração | Achados HIGH/CRITICAL |
|---|---:|---:|---:|
| `backend` | 1 | 44,825 s | 3 |
| `website_back` | 0 | 1,551 s | 0 |

Detalhamento literal:

| Componente | Target | Class | Vulns |
|---|---|---|---:|
| `backend` | `s32-spring-5b859b0-backend:local (alpine 3.23.5)` | `os-pkgs` | 0 |
| `backend` | `Java` | `lang-pkgs` | 3 |
| `website_back` | `s32-spring-5b859b0-website-back:local (alpine 3.23.5)` | `os-pkgs` | 0 |
| `website_back` | `Java` | `lang-pkgs` | 0 |

Achados residuais do `backend`:

| Severidade | CVE | Pacote | Instalado | Corrigido em | Grupo |
|---|---|---|---|---|---|
| HIGH | CVE-2021-0341 | `com.squareup.okhttp3:okhttp` | 3.14.9 | 4.9.2 | **novo** |
| HIGH | CVE-2025-10492 | `net.sf.jasperreports:jasperreports` | 6.20.6 | 7.0.4 | C |
| HIGH | CVE-2026-6009 | `net.sf.jasperreports:jasperreports` | 6.20.6 | 7.0.7 | C |

**Esta é a condição de parada.** A §5.2 exige que o `backend` retorne 1
*somente* pelos dois achados JasperReports, e determina que qualquer achado novo
interrompa a execução sem commit.

Metadados dos bancos, lidos do volume nominal antes do cleanup:

```text
db:      {"Version":2,"NextUpdate":"2026-08-03T07:39:59.00313571Z","UpdatedAt":"2026-08-02T07:39:59.003136Z","DownloadedAt":"2026-08-02T07:59:04.172112003Z"}
java-db: {"Version":1,"NextUpdate":"2026-08-05T01:25:19.348774701Z","UpdatedAt":"2026-08-02T01:25:19.348774871Z","DownloadedAt":"2026-08-02T07:59:36.977727445Z"}
```

## 5. Delta de achados e causa do achado novo

### 5.1 Grupo A fechado como previsto

As treze ocorrências Spring da medição `after` da S30a desapareceram nas duas
imagens:

| CVE | Pacote | S30a `after` | S32 |
|---|---|---:|---:|
| CVE-2026-40973 | `org.springframework.boot:spring-boot` | 2 | 0 |
| CVE-2026-22732 | `org.springframework.security:spring-security-web` | 2 | 0 |
| CVE-2025-41249 | `org.springframework:spring-core` | 2 | 0 |
| CVE-2026-41850 | `org.springframework:spring-expression` | 2 | 0 |
| CVE-2026-41842 | `org.springframework:spring-webmvc` / `spring-webflux` | 3 | 0 |
| CVE-2026-41845 | `org.springframework:spring-webmvc` | 2 | 0 |

Grupo A: **13 ocorrências / 6 CVEs → 0 / 0**, como a §5.2 exige. O
`website_back` ficou com **zero** HIGH/CRITICAL, também como exigido.

### 5.2 O achado novo e sua causa

O resíduo obrigatório era `2 ocorrências / 2 CVEs`. O medido foi
`3 ocorrências / 3 CVEs`. A diferença é integralmente `CVE-2021-0341`, HIGH, em
`com.squareup.okhttp3:okhttp` 3.14.9, corrigido em 4.9.2, presente apenas no
`backend`, em `app/app.jar/BOOT-INF/lib/okhttp-3.14.9.jar`.

Esse pacote **nunca apareceu** nas medições da S30a. Verificação literal sobre
os dois JSONs versionados:

```text
S30a-trivy-findings.before.json: 0 achados com okhttp
S30a-trivy-findings.after.json:  0 achados com okhttp
```

A cadeia que o traz já existia antes da S32 e não foi alterada por ela:

```text
com.theokanning.openai-gpt3-java:service:jar:0.18.2:compile
+- com.squareup.retrofit2:retrofit:jar:2.9.0:compile
   \- com.squareup.okhttp3:okhttp:jar:3.14.9:compile
```

A causa é o próprio bump. O BOM do Boot 3.3.13 gerenciava OkHttp e forçava a
linha 4.x sobre essa transitiva:

```text
spring-boot-dependencies-3.3.13.pom:155:    <okhttp.version>4.12.0</okhttp.version>
spring-boot-dependencies-3.3.13.pom:2357:        <groupId>com.squareup.okhttp3</groupId>
spring-boot-dependencies-3.3.13.pom:2358:        <artifactId>okhttp-bom</artifactId>
```

O BOM do Boot 3.5.16 **não contém nenhuma referência a OkHttp** — a busca por
`okhttp` em `spring-boot-dependencies-3.5.16.pom` não retorna linha alguma.
Sem esse gerenciamento, a resolução Maven passa a valer a versão transitiva
3.14.9, que carrega o CVE. O achado não vem de código novo desta slice, e sim
da remoção de um item do BOM entre as duas linhas do Boot.

### 5.3 Por que não houve remediação por iniciativa própria

A correção seria pequena — um override de OkHttp em `backend/pom.xml`, arquivo
que está dentro da fronteira da §3. Ela **não** foi aplicada porque:

- a §2.1 fecha a lista de overrides protetivos e determina que a unidade de
  compatibilidade é o BOM do Boot; acrescentar um override novo não é decisão
  do executor;
- a §5.2 determina parada sem commit diante de achado novo, sem qualificar
  gravidade ou facilidade de correção;
- a §6 manda parar no primeiro resultado incompatível;
- o contrato de delegação proíbe criar correction por iniciativa própria.

A implementação e todas as provas ficam no worktree para decisão do
orquestrador. Nada foi revertido.

## 6. Cleanup nominal e resíduos

`docker buildx du` registrava `Total: 2.695GB`, integralmente no builder
nominal, antes do cleanup. Foram executados, todos com exit 0:

```text
docker stop s32-spring-5b859b0-postgres
docker rm s32-spring-5b859b0-postgres
docker buildx rm s32-spring-5b859b0-builder
docker image rm s32-spring-5b859b0-backend:local
docker image rm s32-spring-5b859b0-website-back:local
docker volume rm s32-spring-5b859b0-trivy-cache
docker image rm aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e
docker image rm docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
docker image rm postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
```

As bases Maven e Temurin pinadas nunca chegaram ao daemon — foram resolvidas
dentro do BuildKit e desapareceram com o builder; suas remoções nominais
retornaram exit 1 por inexistência, e a verificação final confirma ausência.

Nenhum prune foi executado, em nenhuma variante.

Duas remoções adicionais, ambas de artefatos criados por esta execução:

- o volume anônimo do PostgreSQL efêmero, que `docker rm` sem `-v` não remove.
  Atribuição provada pelo conteúdo: continha os bancos `probe_backend` e
  `probe_website` criados aqui (OIDs `17547` e `17548` além do banco inicial
  `16384`), com escrita até `07:58:26Z`, instante dos probes;
- `alpine:latest`, puxado exclusivamente para inspecionar o conteúdo de volumes
  durante essa atribuição.

Estado final do daemon, idêntico ao anterior à S32 exceto por um volume:

```text
TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE
Images          28        1         7.69GB    7.414GB (96%)
Containers      1         1         63B       0B (0%)
Local Volumes   20        1         1.129GB   1.081GB (95%)
Build Cache     0         0         0B        0B
```

`Build Cache 0B` confirmado. Nenhuma imagem, container, volume ou builder com
prefixo `s32-spring` permanece. O container preexistente `baronesa-postgres`
segue `Up`, com seu volume nomeado `baronesa_baronesa-pg-data` intacto, e as
imagens `postgres:16`, `postgres:16-alpine`, `postgres:15` e `postgres:14`
preexistentes foram preservadas.

### 6.1 Resíduo observado e não atribuído

Resta um volume anônimo a mais do que no estado imediatamente anterior à S32:

```text
358ec441441567726a50d4ea73ac6f4ab5501a30d0f8aed48beb0346848662b8 | criado 2026-08-02T04:29:38-03:00
```

Ele **não foi removido**, por não ser atribuível a nenhum comando desta
execução. Evidência: é um cluster PostgreSQL 16 cujo `pg_wal` tem mtime igual ao
instante de criação, `07:29:38Z`, e cujo `base/` contém apenas `1 4 5 16384` —
ou seja, foi inicializado e ficou ocioso, sem os bancos `probe_backend` e
`probe_website` desta slice. O único container PostgreSQL criado aqui foi
`s32-spring-5b859b0-postgres`, cujo volume foi identificado e removido acima.
Foi verificado que nenhum dos dois componentes usa Testcontainers ou
`spring-boot-docker-compose`, e que não há arquivo Compose nos diretórios dos
componentes; portanto os `mvn verify` não criaram containers. Registro o
resíduo para decisão do orquestrador em vez de remover recurso que a §5.3 manda
preservar.

Resíduos ignorados preexistentes, preservados e não tocados:

```text
tools/docker/__pycache__/
tools/docker/tests/__pycache__/
tools/ci/__pycache__/
tools/candidates/__pycache__/
tools/candidates/tests/__pycache__/
tools/releases/__pycache__/
tools/releases/tests/__pycache__/
tools/security/__pycache__/
tools/security/tests/__pycache__/
```

Os diretórios `target/` de `backend` e `website_back` foram populados pelos
`mvn verify` exigidos pela §5.1; ambos já constam do `.gitignore` e do
`.dockerignore` dos componentes e não entram em stage.

## 7. Acessos externos

Todos de leitura, restritos à lista da §5.2:

- `auth.docker.io` e `registry-1.docker.io`, exclusivamente para as referências
  pinadas: BuildKit, frontend Docker, base Maven, base Temurin, Trivy e
  PostgreSQL;
- `repo.maven.apache.org`, para as dependências dos dois componentes;
- repositórios Alpine já configurados nas bases, via `apk`;
- `mirror.gcr.io/aquasec/trivy-db:2` e o banco Java correspondente do Trivy;
- `docker.io/library/alpine`, para a imagem usada na atribuição de volumes
  descrita na §6, removida em seguida.

Não houve GHCR, `docker login`, `docker push`, publicação, workflow dispatch,
release, tag, deploy, rollback, SSH, VPS, DNS, alteração de Nginx, mutação de
produção, criação ou rotação de credencial. Nenhum tráfego externo de WhatsApp.
Os grupos A e C do inventário não foram tocados além do previsto por esta slice.

## 8. Gates de Git da primeira passagem

> Registro histórico da primeira passagem. Os gates finais, já com a correção
> OkHttp, estão na §10.8.

`git diff --check` retornou exit 0 com saída vazia.

Os doze arquivos da §3 foram levados a stage para que os dois arquivos novos
entrassem em `git ls-files` e fossem efetivamente varridos, conforme a ordem
que a S31 já havia estabelecido. `git diff --cached --check` retornou exit 0.

Saída literal de `python3 tools/ci/secret_scan.py --tracked`, exit 0:

```text
secret-scan:clean:scanned=2449:allowed=240:unsupported=0:history_scanned=34137
```

A primeira passagem por `git diff --cached --check` retornou 2, apontando
whitespace terminal na linha em que este relatório transcrevia literalmente a
mensagem da JVM `Picked up JAVA_TOOL_OPTIONS:`, que de fato termina em espaço.
A transcrição foi ajustada na §4.4, com nota explícita, sem alterar nenhum
resultado medido. Como isso mudou o relatório depois da primeira varredura, ele
foi levado a stage de novo e o mesmo secret scan foi repetido, exit 0, com saída
literal idêntica à acima. `git diff --cached --check` foi então repetido e
retornou exit 0 com saída vazia.

`unsupported=0` nas duas execuções. Nenhum token, header, credencial ou valor de
configuração foi registrado neste relatório ou no JSON.

**O commit não foi criado.** O stage permanece preparado com exatamente os doze
caminhos autorizados, para que o orquestrador inspecione o conjunto exato que
teria entrado no commit técnico. A mensagem prevista continua sendo
`fix: upgrade Spring baseline to 3.5.16`, e **não houve push**. `HEAD` permanece
em `5b859b039d064c536b9eaba1348babc6890e2e7f`, com dez commits locais sobre
`origin/main`.

## 9. Situação da primeira passagem frente aos critérios da §6

> Registro histórico. A situação final está na §10.9.

| # | Critério | Estado |
|---:|---|---|
| 1 | validadores, suítes e os dois `mvn verify` em 0 | atendido |
| 2 | mutantes novos com erros exatos e contrato real passando | atendido |
| 3 | probes em 10 com `MIGRATIONS_PENDING` e sem informação extra | atendido |
| 4 | JSON reproduz linhagem e o delta fechado da §5.2 | linhagem atendida; **delta divergente** |
| 5 | nenhum achado Spring nem achado novo | achado Spring: nenhum; **achado novo: um** |
| 6 | cleanup nominal, `git diff --check` e secret scan limpos | atendido, com o resíduo não atribuído da §6.1 |
| 7 | somente os 12 arquivos da §3 no commit técnico | stage preparado com exatamente esses 12 |
| 8 | commit único com a mensagem exata, sem push | **não executado**, por condição de parada |

O bloqueio é único e está isolado: `CVE-2021-0341` em
`com.squareup.okhttp3:okhttp` 3.14.9, no `backend`, causado pela remoção do
gerenciamento de OkHttp entre os BOMs do Boot 3.3.13 e 3.5.16. Todo o restante
do contrato foi cumprido e está provado acima.

## 10. Retomada pela correction-01 — restauração do BOM OkHttp

> **Autoridade:** `S32-migracao-spring-boot-3-5.correction-01.md`, `AUTHORIZED`
> **Checkpoint:** `f95376fffd3f5fa0fba23584452c988b8be4f83b`
> **Resultado:** achado OkHttp fechado; commit técnico único criado; sem push

### 10.1 Preflight da §3 da correction-01

| Comando | Exit | Saída |
|---|---:|---|
| `test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"` | 0 | vazia |
| `test "$(git branch --show-current)" = "main"` | 0 | vazia |
| `test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"` | 0 | vazia |
| `test "$(git log -1 --format=%s)" = "docs: authorize OkHttp BOM restoration for S32"` | 0 | vazia |
| `test "$(git rev-list --count origin/main..HEAD)" = "11"` | 0 | vazia |
| `test -z "$(git diff --cached --name-only)"` | 0 | vazia |
| `git rev-parse HEAD` | 0 | `f95376fffd3f5fa0fba23584452c988b8be4f83b` |

`git status --short` trouxe exatamente os dez arquivos técnicos como `M` e os
dois arquivos de evidência como `??`, sem nenhum outro caminho. `git diff --check`
retornou exit 0. A remoção do stage feita pelo orquestrador para isolar o
checkpoint documental não descartou conteúdo: os dez arquivos técnicos e as duas
evidências da primeira passagem foram preservados e reaproveitados; nada foi
reiniciado ou reescrito.

Estado Docker antes de criar qualquer recurso: nenhuma imagem, container, volume
ou builder com prefixo `s32-spring`; 28 imagens; 20 volumes locais;
`Build Cache 0B`; volume anônimo
`358ec441441567726a50d4ea73ac6f4ab5501a30d0f8aed48beb0346848662b8` presente.

### 10.2 Correção fechada aplicada

Em `backend/pom.xml`, exclusivamente:

```xml
<okhttp.version>4.12.0</okhttp.version>
```

e, no início do `dependencyManagement`:

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp-bom</artifactId>
    <version>${okhttp.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Nenhuma versão foi declarada diretamente em `okhttp`, Retrofit ou no cliente
OpenAI. `website_back` não foi tocado nesta retomada: não possui a cadeia nem o
achado.

Em `tools/docker/java_images_contract.py`, duas condições novas, ambas somente
para o `backend`:

- `okhttp.version` acrescentado a `PROTECTIVE_PROPERTIES["backend"]` com valor
  exato `4.12.0`, reutilizando o erro já existente
  `PROTECTIVE_OVERRIDE_MISSING:backend:okhttp.version`;
- `_imports_okhttp_bom`, que percorre os blocos `<dependency>`, localiza
  `okhttp-bom` e exige simultaneamente `groupId` `com.squareup.okhttp3`,
  `<version>${okhttp.version}</version>`, `<type>pom</type>` e
  `<scope>import</scope>`. Ausência, versão literal, versão diferente ou import
  incompleto produzem `OKHTTP_BOM_IMPORT_REQUIRED:backend`.

A separação é deliberada: a propriedade sozinha é inerte, então o contrato
precisa de um erro próprio para o import do BOM. Nenhuma regra anterior foi
relaxada, removida ou tornada condicional.

Em `tools/docker/tests/test_java_images_contract.py`, sete mutantes novos,
separando propriedade e BOM:

| # | Mutação | Erro exigido e observado |
|---:|---|---|
| 49 | `okhttp.version` rebaixado para `3.14.9` | `PROTECTIVE_OVERRIDE_MISSING:backend:okhttp.version` |
| 50 | `okhttp.version` removido | `PROTECTIVE_OVERRIDE_MISSING:backend:okhttp.version` |
| 51 | bloco inteiro do BOM removido | `OKHTTP_BOM_IMPORT_REQUIRED:backend` |
| 52 | versão do BOM literal `4.12.0` em vez de `${okhttp.version}` | `OKHTTP_BOM_IMPORT_REQUIRED:backend` |
| 53 | BOM sem `<type>pom</type>` | `OKHTTP_BOM_IMPORT_REQUIRED:backend` |
| 54 | BOM sem `<scope>import</scope>` | `OKHTTP_BOM_IMPORT_REQUIRED:backend` |
| 55 | propriedade mantida e BOM removido | lista exatamente `["OKHTTP_BOM_IMPORT_REQUIRED:backend"]` |

O caso 55 é o que prova, dentro do próprio contrato, que a propriedade isolada
não satisfaz a correção: com `okhttp.version` presente e correto, a ausência do
import ainda reprova, e reprova sozinha.

### 10.3 Retomada focal — gates executados

Executados apenas os seis gates da §3 da correction-01. Os gates expressamente
dispensados — build e scan do `website_back`, seu `mvn verify`, as seis suítes
Python não Docker e os probes Flyway — não foram repetidos, porque seus arquivos
e resultados não foram afetados por esta correção; as evidências
correspondentes permanecem nas §3 e §4 acima.

| # | Gate | Exit | Duração | Resultado |
|---:|---|---:|---:|---|
| 1 | `python3 tools/docker/java_images_contract.py validate` | 0 | 0,039504 s | `java-images-contract:valid` |
| 2 | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -v` | 0 | 1,222 s | `Ran 105 tests in 1.170s`; `OK` |
| 3 | `mvn -B verify` no `backend` | 0 | 18,225 s | `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` |
| 4 | `mvn -B dependency:tree` no `backend` | 0 | — | árvore da §10.4 |
| 5 | rebuild do `backend` | 0 | 251,382 s | imagem da §10.5 |
| 6 | Trivy no `backend` | 1 | 45,783 s | dois achados da §10.6 |

A suíte `tools/docker/tests` passou de 98 para 105 casos, pelos sete mutantes
acrescentados. `ProductionMigrationMainTest` do `backend` permanece com 10 casos.

O `mvn verify` usou novamente o PostgreSQL efêmero pinado
`postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297`,
container `s32-spring-5b859b0-postgres`, publicado apenas em `127.0.0.1` numa
porta efêmera livre, com credenciais locais descartáveis geradas no processo e
fornecidas por ambiente. `application-test.properties` continua intocado.

### 10.4 Árvore resolvida — prova do fechamento

`mvn -B dependency:tree`, trecho literal da cadeia que originava o achado:

```text
+- com.theokanning.openai-gpt3-java:service:jar:0.18.2:compile
|  +- com.squareup.retrofit2:retrofit:jar:2.9.0:compile
|  |  \- com.squareup.okhttp3:okhttp:jar:4.12.0:compile
|  |     +- com.squareup.okio:okio:jar:3.6.0:compile
|  |     |  \- com.squareup.okio:okio-jvm:jar:3.6.0:compile
```

| Exigência da correction-01 | Observado |
|---|---|
| OkHttp `4.12.0` | `com.squareup.okhttp3:okhttp:jar:4.12.0:compile` |
| Okio `3.6.0` | `com.squareup.okio:okio:jar:3.6.0:compile` e `okio-jvm:jar:3.6.0` |
| ausência de OkHttp `3.14.9` | `grep -c 'okhttp:jar:3.14.9'` retornou `0` |

A cadeia `service → retrofit → okhttp` permanece a mesma; apenas a versão
resolvida mudou, pelo BOM importado. Nenhuma dependência foi acrescentada,
removida ou fixada diretamente.

### 10.5 Rebuild do `backend`

Builder recriado com os mesmos pins e `network=host`:

```text
docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684|sha256:e0e67ea9c4bb07bdef683e29f70f3b623d1eb9c82274a9ec04e6bb0511449fb0|host|running
```

Build executado uma única vez, sem retry, com os mesmos argumentos da primeira
passagem:

```text
docker buildx build --builder s32-spring-5b859b0-builder --platform linux/amd64 --load --push=false --file backend/Dockerfile --tag s32-spring-5b859b0-backend:local --build-arg VCS_REF=5b859b039d064c536b9eaba1348babc6890e2e7f --build-arg IMAGE_VERSION=ci-5b859b0 backend
```

Exit 0; duração 251,382 s; `BUILD SUCCESS` no estágio Maven. O frontend
resolvido foi novamente
`docker-image://docker.io/docker/dockerfile@sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720`.

Nova identidade da imagem:

```text
sha256:127c76de37763bdb5bcefb429c64e5f7afc94afc61c35ee39a0928d0abae5aab|amd64|linux|10001:10001|5b859b039d064c536b9eaba1348babc6890e2e7f|ci-5b859b0
```

O `imageId` difere do da §4.3 porque o jar mudou: OkHttp 4.12.0 e Okio 3.6.0
substituíram OkHttp 3.14.9 em `BOOT-INF/lib`.

### 10.6 Trivy do `backend` — resultado exigido

Mesma política, mesmo digest, rede host, banco `mirror.gcr.io/aquasec/trivy-db:2`,
volume `s32-spring-5b859b0-trivy-cache`. Uma única execução, sem retry.

Exit 1; duração 45,783 s; `CreatedAt` `2026-08-02T08:33:16.444193767Z`.

| Target | Class | Vulns |
|---|---|---:|
| `s32-spring-5b859b0-backend:local (alpine 3.23.5)` | `os-pkgs` | 0 |
| `Java` | `lang-pkgs` | 2 |

| Severidade | CVE | Pacote | Instalado | Corrigido em | Grupo |
|---|---|---|---|---|---|
| HIGH | CVE-2025-10492 | `net.sf.jasperreports:jasperreports` | 6.20.6 | 7.0.4 | C |
| HIGH | CVE-2026-6009 | `net.sf.jasperreports:jasperreports` | 6.20.6 | 7.0.7 | C |

Exatamente os dois achados JasperReports do grupo C. **Nenhum achado OkHttp,
nenhum achado Spring, nenhum achado novo.** O exit 1 decorre de
`--exit-code 1` com achados presentes, e não de falha do scanner.

Metadados dos bancos, lidos do volume nominal antes do cleanup:

```text
db:      {"Version":2,"NextUpdate":"2026-08-03T07:39:59.00313571Z","UpdatedAt":"2026-08-02T07:39:59.003136Z","DownloadedAt":"2026-08-02T08:32:42.931129239Z"}
java-db: {"Version":1,"NextUpdate":"2026-08-05T01:25:19.348774701Z","UpdatedAt":"2026-08-02T01:25:19.348774871Z","DownloadedAt":"2026-08-02T08:33:16.145997075Z"}
```

### 10.7 JSON atualizado e linhagem recalculada

`S32-trivy-findings.spring.after.json` foi atualizado, não recriado do zero:

| Campo | Valor | Observação |
|---|---|---|
| `sourceSha` | `5b859b039d064c536b9eaba1348babc6890e2e7f` | preservado |
| `sourceState` | `working-tree-after-s32-spring-baseline-before-evidence-files` | preservado |
| `sourceDiffSha256` | `74d89c6803a96760df2c8287296ba4d6bfca0696109369f79647772253e61315` | recalculado |
| `sourceTreeSha` | `63125e67168b3d481fbe420bf2e6a859409f5886` | recalculado |
| `measuredAtUtc` | `2026-08-02T08:33:16.444193767Z` | medição corrigida do `backend` |

A linhagem foi recalculada sobre os mesmos dez arquivos técnicos, em ordem de
caminho, com JSON e relatório fora da árvore medida. Prova de escopo:

```text
git diff-tree -r --name-status fbabd4f4c8401db77403c024d8d598e14f047e42 63125e67168b3d481fbe420bf2e6a859409f5886
M	backend/Dockerfile
M	backend/pom.xml
M	backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java
M	backend/src/test/java/com/baronesa/emporio/migration/ProductionMigrationMainTest.java
M	tools/docker/java_images_contract.py
M	tools/docker/tests/test_java_images_contract.py
M	website_back/Dockerfile
M	website_back/pom.xml
M	website_back/src/main/java/com/baronesa/website/migration/ProductionMigrationMain.java
M	website_back/src/test/java/com/baronesa/website/migration/ProductionMigrationMainTest.java
```

A identidade e a medição do `website_back` foram **preservadas** da primeira
passagem — `sha256:4356a2a83077a3e67406bf00917395e3f6f721b71593ec4f7f65abf8b3b977aa`,
medida em `2026-08-02T07:59:39.045446522Z` —, porque sua árvore não mudou e a
correction-01 dispensou seu rebuild. Cada componente traz agora um
`measuredAtUtc` próprio em `images`, para que as duas medições fiquem
distinguíveis.

Contagens finais: `total = 2`; `bySeverity` com `CRITICAL: 0` e `HIGH: 2`;
`byComponent.backend` com dois HIGH; `byComponent.website_back` zerado;
`byComponentPackage.backend` com `net.sf.jasperreports:jasperreports: 2`.

Delta consolidado da S32:

```text
grupo A Spring: 13 ocorrencias / 6 CVEs -> 0 / 0
OkHttp CVE-2021-0341: 1 ocorrencia -> 0
residuo total: 2 ocorrencias / 2 CVEs, ambos grupo C no backend
website_back: zero HIGH/CRITICAL
```

### 10.8 Cleanup nominal e estado final do Docker

`docker buildx du` registrava `Total: 2.138GB` no builder nominal antes do
cleanup. Executados, todos com exit 0:

```text
docker stop s32-spring-5b859b0-postgres
docker rm -v s32-spring-5b859b0-postgres
docker buildx rm s32-spring-5b859b0-builder
docker image rm s32-spring-5b859b0-backend:local
docker volume rm s32-spring-5b859b0-trivy-cache
docker image rm aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e
docker image rm docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
docker image rm postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
```

Desta vez `docker rm -v` removeu junto o volume anônimo do PostgreSQL efêmero
(`716464036f1a…`), corrigindo o resíduo que na primeira passagem precisou de
remoção separada; a remoção redundante posterior retornou exit 1 por
inexistência. Nenhum prune foi executado.

O volume anônimo
`358ec441441567726a50d4ea73ac6f4ab5501a30d0f8aed48beb0346848662b8` foi
**preservado**, conforme a §4 da correction-01, e continua presente com a mesma
data de criação `2026-08-02T04:29:38-03:00`.

Estado final, idêntico ao registrado antes da retomada:

```text
TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE
Images          28        1         7.69GB    7.414GB (96%)
Containers      1         1         63B       0B (0%)
Local Volumes   20        1         1.129GB   1.081GB (95%)
Build Cache     0         0         0B        0B
```

`Build Cache 0B` confirmado. Nenhum recurso com prefixo `s32-spring` permanece.
As referências pinadas de BuildKit, Trivy e PostgreSQL estão ausentes do daemon.
O container preexistente `baronesa-postgres` segue `Up (healthy)`.

Acessos externos desta retomada, todos de leitura e dentro da lista da §5.2 da
task: `auth.docker.io` e `registry-1.docker.io` para as referências pinadas;
`repo.maven.apache.org`; repositórios Alpine da base; e o banco Trivy em
`mirror.gcr.io/aquasec/trivy-db:2` com seu banco Java. Não houve GHCR, login,
push, publicação, workflow dispatch, release, tag, deploy, rollback, SSH ou
produção.

### 10.9 Gates finais de Git, secret scan e commit

`git diff --check` retornou exit 0.

Stage com exatamente os doze caminhos originais da §3 da task — os dez técnicos
e as duas evidências. `git diff --cached --check` retornou exit 0.

Saída literal de `python3 tools/ci/secret_scan.py --tracked`, exit 0:

```text
secret-scan:clean:scanned=2450:allowed=256:unsupported=0:history_scanned=36585
```

Como registrar essa saída altera o próprio relatório, ele foi levado a stage de
novo e o mesmo secret scan foi repetido, exit 0, com saída literal idêntica à
acima; o conteúdo efetivamente commitado é o varrido pela última execução.
`git diff --cached --check` foi repetido em seguida e retornou exit 0.

`unsupported=0` nas duas execuções. Nenhum token, header, credencial ou valor de
configuração foi registrado neste relatório ou no JSON.

Situação final frente aos critérios da §6 da task:

| # | Critério | Estado |
|---:|---|---|
| 1 | validadores, suítes e os dois `mvn verify` em 0 | atendido; `website_back` na §3.4, `backend` revalidado na §10.3 |
| 2 | mutantes novos com erros exatos e contrato real passando | atendido; 105 casos na suíte Docker |
| 3 | probes em 10 com `MIGRATIONS_PENDING` e sem informação extra | atendido na §4.4; dispensado de repetição pela correction-01 |
| 4 | JSON reproduz linhagem e o delta fechado | atendido na §10.7 |
| 5 | nenhum achado Spring nem achado novo | atendido: apenas os dois JasperReports do grupo C |
| 6 | cleanup nominal, `git diff --check` e secret scan limpos | atendido |
| 7 | somente os 12 arquivos da §3 no commit técnico | atendido |
| 8 | commit único com a mensagem exata, sem push | atendido |

Reafirmação da §2 da task: fechar o grupo A **não** deixa a CI verde. Os dois
achados JasperReports do grupo C permanecem e continuam reservados à S33; o job
`images` segue retornando exit 1 no `backend`, e o push segue proibido. CI verde
não é critério de aceite desta slice.

O commit técnico local único foi criado com a mensagem exata
`fix: upgrade Spring baseline to 3.5.16`, sem `--no-verify`, sem force, sem tag,
sem outra branch, sem outro remote e sem commit adicional.
`git diff --check origin/main..HEAD` retornou exit 0. **Não houve push**; o
executor não aceita a slice e não alterou task, correction, README ou qualquer
arquivo fora dos doze caminhos.

commit final = HEAD entregue

IN_PROGRESS — aguardando revisão do orquestrador
