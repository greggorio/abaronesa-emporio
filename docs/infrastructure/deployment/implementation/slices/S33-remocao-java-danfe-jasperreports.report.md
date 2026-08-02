# S33 — relatório de execução

> **Estado da execução:** concluída; commit técnico único criado
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Autoridade:** `S33-remocao-java-danfe-jasperreports.task.md`
> **SHA-base:** `93f4629dfa4469c36f1103069902a0a7272e4a49`
> **Mensagem do commit:** `fix: remove unused JasperReports dependency chain`
> **Push:** não executado

## 1. Preflight da §4

| Comando | Exit | Saída |
|---|---:|---|
| `test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"` | 0 | vazia |
| `test "$(git branch --show-current)" = "main"` | 0 | vazia |
| `test -z "$(git status --porcelain)"` | 0 | vazia |
| `test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"` | 0 | vazia |
| `test "$(git log -1 --format=%s)" = "docs: accept S32 and open S33 scope"` | 0 | vazia |
| `test "$(git rev-list --count origin/main..HEAD)" = "13"` | 0 | vazia |
| `git rev-parse HEAD` | 0 | `93f4629dfa4469c36f1103069902a0a7272e4a49` |

Branch `main`; `origin/main` em `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`; treze
commits locais; worktree e stage vazios antes da primeira alteração. Nenhuma
divergência. A task foi lida integralmente antes de qualquer alteração.

## 2. Arquivos alterados e criados

Os quatro arquivos técnicos da §3:

```text
backend/pom.xml
backend/src/test/java/com/baronesa/emporio/nfe/service/DanfePdfGeneratorServiceTest.java
tools/docker/java_images_contract.py
tools/docker/tests/test_java_images_contract.py
```

As duas evidências autorizadas:

```text
docs/infrastructure/deployment/implementation/slices/S33-trivy-findings.backend.after.json
docs/infrastructure/deployment/implementation/slices/S33-remocao-java-danfe-jasperreports.report.md
```

Nenhum outro arquivo foi tocado. Em particular, não foram alterados código de
produção, o template `danfe.html`, os Dockerfiles, `java-nfe`, tasks,
corrections, README ou workflows. Nenhuma exceção Trivy foi criada e não houve
migração para JasperReports 7.

### 2.1 Confirmação independente da premissa

Antes da remoção, a busca por consumidores retornou vazia:

```text
grep -rln "swconsultoria.impressao\|jasperreports\|JasperReport\|java-danfe" backend/src
(nenhum resultado)
```

Nenhuma classe, recurso ou configuração do `backend` referencia a cadeia. O DANFE
efetivamente usado é `DanfePdfGeneratorService`, que processa o template
Thymeleaf `danfe` e renderiza por `ITextRenderer` do Flying Saucer/OpenPDF, com
ZXing apenas para o QR.

### 2.2 Remoção no POM — §2.1

Removidos integralmente de `backend/pom.xml`:

- a propriedade `<java-danfe.version>1.8</java-danfe.version>`;
- o bloco de dependência `br.com.swconsultoria:java-danfe` com suas três
  exclusões (`xml-apis`, `xercesImpl`, `xalan`).

Nada foi acrescentado em substituição. `java-nfe` `4.00.42`, o renderer e todas
as versões fixadas pela S32 — incluindo o `okhttp-bom` `4.12.0` — permanecem
inalterados.

### 2.3 Prova funcional do DANFE ativo — §2.2

Criado
`backend/src/test/java/com/baronesa/emporio/nfe/service/DanfePdfGeneratorServiceTest.java`,
conforme prescrito:

- `SpringTemplateEngine` configurado com `ClassLoaderTemplateResolver`, prefixo
  `templates/`, sufixo `.html`, `TemplateMode.HTML` e UTF-8;
- `ConfigManager` mockado por Mockito devolvendo **apenas o default recebido**
  (`thenAnswer(invocation -> invocation.getArgument(1))`);
- `DanfeModel` mínimo, com `produtos` e `duplicatas` como listas vazias e
  `chaveAcesso` nulo, o que faz `ensureQrBase64` retornar sem gerar QR;
- chamada ao `DanfePdfGeneratorService` real, construído diretamente;
- asserções de saída não nula, não vazia e iniciada pelos bytes `%PDF-`.

Sem `@SpringBootTest`, sem banco, sem filesystem externo, sem rede e sem
snapshot binário. O campo `@Value("${nfe_logo_path:#{null}}")` permanece nulo
fora do contexto Spring, e `nfe_logo_base64`/`nfe_logo_path` resolvem para o
default vazio, de modo que o renderer não acessa nada fora do classpath da
própria aplicação.

### 2.4 Contrato contra regressão — §2.3

`tools/docker/java_images_contract.py` passou a validar o `backend/pom.xml`
também quanto à cadeia DANFE:

| Condição | Erro |
|---|---|
| propriedade ou artefato `java-danfe` presente | `UNUSED_JAVA_DANFE_FORBIDDEN:backend` |
| propriedade ou artefato `jasperreports` ou `jasperreports-fonts` presente | `JASPERREPORTS_FORBIDDEN:backend` |
| renderer ativo incompleto | `DANFE_RENDERER_REQUIRED:backend` |

O renderer exigido é fechado por grupo, artefato e — quando aplicável — versão
exata:

```text
org.springframework.boot:spring-boot-starter-thymeleaf   (versao gerenciada pelo BOM)
org.xhtmlrenderer:flying-saucer-pdf-openpdf:9.1.22
com.google.zxing:core:3.5.3
com.google.zxing:javase:3.5.3
```

Uma observação relevante sobre o token de busca: a primeira formulação incluía
`swconsultoria` como marcador de `java-danfe` e o contrato reprovou o POM real
com `UNUSED_JAVA_DANFE_FORBIDDEN:backend`. A causa é que `java-nfe` **também**
pertence ao grupo `br.com.swconsultoria`, que a §2.1 manda preservar. O token
foi corrigido para o artefato `java-danfe`, e `jasperreports` cobre
`jasperreports-fonts` por subcadeia. O caso 67 fixa essa distinção como
regressão.

Nenhuma regra anterior foi relaxada, removida ou tornada condicional.

## 3. Mutantes focais

O contrato real retorna lista vazia (`test_01_real_contract_is_valid` e
`test_67`). Doze mutantes novos, cada um exigindo o erro exato por `assertIn`:

| # | Mutação | Erro exigido e observado |
|---:|---|---|
| 56 | propriedade `java-danfe.version` reintroduzida | `UNUSED_JAVA_DANFE_FORBIDDEN:backend` |
| 57 | dependência `br.com.swconsultoria:java-danfe` reintroduzida | `UNUSED_JAVA_DANFE_FORBIDDEN:backend` |
| 58 | dependência direta `net.sf.jasperreports:jasperreports` | `JASPERREPORTS_FORBIDDEN:backend` |
| 59 | dependência direta `jasperreports-fonts` | `JASPERREPORTS_FORBIDDEN:backend` |
| 60 | propriedade `jasperreports.version` reintroduzida | `JASPERREPORTS_FORBIDDEN:backend` |
| 61 | `spring-boot-starter-thymeleaf` removido | `DANFE_RENDERER_REQUIRED:backend` |
| 62 | `flying-saucer-pdf-openpdf` removido | `DANFE_RENDERER_REQUIRED:backend` |
| 63 | `flying-saucer-pdf-openpdf` rebaixado para `9.1.20` | `DANFE_RENDERER_REQUIRED:backend` |
| 64 | `com.google.zxing:core` rebaixado para `3.4.1` | `DANFE_RENDERER_REQUIRED:backend` |
| 65 | `com.google.zxing:javase` rebaixado para `3.4.1` | `DANFE_RENDERER_REQUIRED:backend` |
| 66 | `com.google.zxing:javase` removido | `DANFE_RENDERER_REQUIRED:backend` |
| 67 | POM real, com `java-nfe` e grupo `br.com.swconsultoria` presentes | lista exatamente `[]` |

O caso 67 é o que impede a regra nova de canibalizar `java-nfe`: prova que o
grupo compartilhado continua aceito enquanto o artefato removido permanece
proibido.

## 4. Matriz da §5.1

### 4.1 Treze validadores canônicos — todos exit 0

| Comando | Duração | Saída |
|---|---:|---|
| `python3 tools/docker/validate_node_images.py validate` | 0,037398 s | `node-images-contract:valid` |
| `python3 tools/docker/java_images_contract.py validate` | 0,040649 s | `java-images-contract:valid` |
| `python3 tools/ci/validate_ci.py` | 0,044515 s | `ci:valid` |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0,095374 s | `candidate-workflow:valid` |
| `python3 tools/releases/validate_release_workflow.py` | 0,116538 s | `release-workflow:valid` |
| `python3 tools/releases/validate_publisher_ui.py` | 0,030816 s | `publisher-ui:valid` |
| `python3 tools/releases/validate_publisher_identity_bridge.py` | 0,031356 s | `publisher-identity-bridge:valid` |
| `python3 tools/ci/validate_workflow_inventory.py` | 0,070600 s | `workflow-inventory:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0,119876 s | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0,052577 s | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0,032362 s | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | 0,098751 s | `release-control-contract:valid` |
| `python3 tools/security/bootstrap_contract.py validate` | 0,040280 s | `bootstrap-contract:valid` |

### 4.2 Sete suítes Python — todas exit 0

| Suíte | Duração | Resultado unittest |
|---|---:|---|
| `tools/docker/tests` | 1,235 s | 117 testes em 1,185 s; `OK` |
| `tools/ci/tests` | 4,647 s | 30 testes em 4,506 s; `OK` |
| `tools/candidates/tests` | 3,514 s | 68 testes em 3,383 s; `OK` |
| `tools/releases/tests` | 6,540 s | 298 testes em 6,402 s; `OK` |
| `tools/security/tests` | 0,087 s | 26 testes em 0,041 s; `OK` |
| `tools/compose/tests` | 0,314 s | 4 testes em 0,268 s; `OK` |
| `tools/gateway/tests` | 0,042 s | 4 testes em 0,001 s; `OK` |

Total: 547 testes, todos verdes. `tools/docker/tests` passou de 105 para 117
casos, pelos doze mutantes acrescentados.

### 4.3 `mvn -B verify` do `backend`

Exit 0; duração 20,641 s; `Tests run: 85, Failures: 0, Errors: 0, Skipped: 0`;
`BUILD SUCCESS`. O mínimo contratual de 85 testes foi atingido, com o teste novo
incluído na execução:

```text
[INFO] Running com.baronesa.emporio.nfe.service.DanfePdfGeneratorServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.658 s -- in com.baronesa.emporio.nfe.service.DanfePdfGeneratorServiceTest
```

A suíte anterior tinha 84 casos; o teste de PDF é o 85º. `ProductionMigrationMainTest`
permanece com 10 casos.

O banco usado foi o PostgreSQL efêmero pinado
`postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297`,
container `s33-jasper-93f4629-postgres`, publicado exclusivamente em `127.0.0.1`
numa porta efêmera livre do host, com credenciais locais descartáveis geradas no
processo e fornecidas por ambiente. `application-test.properties` não foi
alterado: está fora da fronteira e a sobreposição ocorreu apenas por
`SPRING_DATASOURCE_*`. O container preexistente `baronesa-postgres` e a
instância do host na porta 5432 não foram tocados.

### 4.4 Prova do PDF

O teste exerce o caminho real de renderização e afirma o cabeçalho do formato:

```java
byte[] pdf = service.generateDanfePdf(minimalDanfe());
assertNotNull(pdf);
assertTrue(pdf.length > PDF_MAGIC.length, "PDF gerado não pode ser vazio");
assertTrue(startsWithPdfMagic(pdf), "Saída deve começar por %PDF-");
```

`PDF_MAGIC` é `"%PDF-"` em US-ASCII, comparado byte a byte. A execução passou,
o que prova que a remoção da cadeia JasperReports não afeta a geração do DANFE:
o PDF continua sendo produzido pelo renderer ativo.

### 4.5 Árvore Maven

`mvn -B dependency:tree`, exit 0. Ausências exigidas, por contagem literal de
ocorrências no arquivo de saída:

| Termo | Ocorrências |
|---|---:|
| `java-danfe` | 0 |
| `jasperreports` | 0 |
| `jasperreports-fonts` | 0 |

Presenças exigidas, trecho literal:

```text
+- br.com.swconsultoria:java-nfe:jar:4.00.42:compile
+- org.springframework.boot:spring-boot-starter-thymeleaf:jar:3.5.16:compile
|  \- org.thymeleaf:thymeleaf-spring6:jar:3.1.5.RELEASE:compile
|     \- org.thymeleaf:thymeleaf:jar:3.1.5.RELEASE:compile
+- org.xhtmlrenderer:flying-saucer-pdf-openpdf:jar:9.1.22:compile
|  +- com.github.librepdf:openpdf:jar:1.3.11:compile
|  \- org.xhtmlrenderer:flying-saucer-core:jar:9.1.22:compile
+- com.google.zxing:core:jar:3.5.3:compile
+- com.google.zxing:javase:jar:3.5.3:compile
```

OpenPDF resolve em `1.3.11` pela cadeia Flying Saucer, como a §5.1 exige, e
`java-nfe` está preservado.

### 4.6 Conteúdo do jar repackaged

Inspeção de `target/emporio-backend-0.0.1-SNAPSHOT.jar`:

| Prefixo | Ocorrências |
|---|---:|
| `BOOT-INF/lib/java-danfe-` | 0 |
| `BOOT-INF/lib/jasperreports-` | 0 |
| `BOOT-INF/lib/jasperreports-fonts-` | 0 |

Entradas do renderer e do `java-nfe` efetivamente empacotadas:

```text
BOOT-INF/lib/core-3.5.3.jar
BOOT-INF/lib/flying-saucer-core-9.1.22.jar
BOOT-INF/lib/flying-saucer-pdf-openpdf-9.1.22.jar
BOOT-INF/lib/java-nfe-4.00.42.jar
BOOT-INF/lib/javase-3.5.3.jar
BOOT-INF/lib/openpdf-1.3.11.jar
BOOT-INF/lib/thymeleaf-3.1.5.RELEASE.jar
BOOT-INF/lib/thymeleaf-spring6-3.1.5.RELEASE.jar
```

## 5. Build e Trivy da §5.2

### 5.1 Estado registrado antes de criar recursos

Nenhuma imagem, container, volume ou builder com prefixo `s33-jasper` existia.
`docker system df` registrava 28 imagens, 1 container, 20 volumes locais e
`Build Cache 0B`. O volume anônimo
`358ec441441567726a50d4ea73ac6f4ab5501a30d0f8aed48beb0346848662b8` estava
presente e permaneceu intocado durante toda a execução.

### 5.2 Builder pinado

```text
docker buildx create --name s33-jasper-93f4629-builder --driver docker-container --driver-opt image=docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684 --driver-opt network=host --platform linux/amd64 --use
```

Exit 0; bootstrap exit 0. Prova literal:

```text
docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684|sha256:e0e67ea9c4bb07bdef683e29f70f3b623d1eb9c82274a9ec04e6bb0511449fb0|host|running
```

### 5.3 Build do `backend`

Executado uma única vez, sem retry:

```text
docker buildx build --builder s33-jasper-93f4629-builder --platform linux/amd64 --load --push=false --file backend/Dockerfile --tag s33-jasper-93f4629-backend:local --build-arg VCS_REF=93f4629dfa4469c36f1103069902a0a7272e4a49 --build-arg IMAGE_VERSION=ci-93f4629 backend
```

Exit 0; duração 223,407 s; `BUILD SUCCESS` no estágio Maven. O frontend resolvido
foi
`docker-image://docker.io/docker/dockerfile@sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720`.

Identidade da imagem:

```text
sha256:b5272f07ccb848707b1a271b143d8910c62633d560d773ad86aa6dfe0c64105e|amd64|linux|10001:10001|93f4629dfa4469c36f1103069902a0a7272e4a49|ci-93f4629
```

`/etc/alpine-release` na imagem: `3.23.5`.

### 5.4 Trivy — zero achados

Mesma política das medições anteriores, rede host, banco
`mirror.gcr.io/aquasec/trivy-db:2`, volume `s33-jasper-93f4629-trivy-cache`.
Uma única execução, sem retry.

**Exit 0**; duração 54,555 s; `CreatedAt` `2026-08-02T09:02:54.088479638Z`.

| Target | Class | Type | Vulns | Secrets |
|---|---|---|---:|---:|
| `s33-jasper-93f4629-backend:local (alpine 3.23.5)` | `os-pkgs` | `alpine` | 0 | 0 |
| `Java` | `lang-pkgs` | `jar` | 0 | 0 |

Total de achados HIGH/CRITICAL: **zero**. Com `--exit-code 1` e
`--ignore-unfixed=false`, o exit 0 é a própria prova do gate.

Metadados dos bancos, lidos do volume nominal antes do cleanup:

```text
db:      {"Version":2,"NextUpdate":"2026-08-03T07:39:59.00313571Z","UpdatedAt":"2026-08-02T07:39:59.003136Z","DownloadedAt":"2026-08-02T09:02:19.432089816Z"}
java-db: {"Version":1,"NextUpdate":"2026-08-05T01:25:19.348774701Z","UpdatedAt":"2026-08-02T01:25:19.348774871Z","DownloadedAt":"2026-08-02T09:02:53.827717505Z"}
```

### 5.5 Delta e fechamento do inventário

| Medição | `backend` |
|---|---:|
| S32, após o baseline Spring | 2 HIGH JasperReports |
| S33, após a remoção | **0 HIGH/CRITICAL** |

Delta obrigatório atingido:

```text
backend: 2 HIGH JasperReports -> 0 HIGH/CRITICAL
grupo C: 2 ocorrencias / 2 CVEs -> 0 / 0
residuo local A+B+C: 0
```

Situação consolidada dos três grupos da S30a, medidos localmente:

| Grupo | Causa | Fechado por | Resíduo |
|---|---|---|---:|
| A — Spring | parent 3.3.13 em `backend` e `website_back` | S32 | 0 |
| B — npm | CLI npm na imagem do `whatsapp_service` | S31 | 0 |
| C — JasperReports | `java-danfe` sem consumidor no `backend` | S33 | 0 |

`CVE-2025-10492` e `CVE-2026-6009` deixaram de existir na imagem porque o
artefato que os continha não é mais empacotado — não houve exceção, supressão
nem alteração de política.

### 5.6 JSON de evidência e linhagem

Emitido
`docs/infrastructure/deployment/implementation/slices/S33-trivy-findings.backend.after.json`,
no esquema das medições anteriores, com `findings: []` e contagens explícitas em
zero.

| Campo | Valor |
|---|---|
| `measurement` | `after` |
| `sourceSha` | `93f4629dfa4469c36f1103069902a0a7272e4a49` |
| `sourceState` | `working-tree-after-s33-jasperreports-removal-before-evidence-files` |
| `sourceDiffSha256` | `1616c3319203d86c19cf6d7991c32c255673f844d412924979a1550b860cc744` |
| `sourceTreeSha` | `5ebb197b8dd1798d84c003fbd9c3d65372569961` |
| `measuredAtUtc` | `2026-08-02T09:02:54.088479638Z` |

A linhagem cobre os quatro arquivos técnicos, em ordem de caminho, com JSON e
relatório fora da árvore medida. Como
`DanfePdfGeneratorServiceTest.java` é arquivo novo, foi necessário `git add -N`
para torná-lo visível a `git diff --binary`; isso apenas registra intenção de
adição no índice e é integralmente superado pelo stage final dos seis caminhos.

Prova de escopo da árvore medida:

```text
git diff-tree -r --name-status <arvore-do-SHA-base> 5ebb197b8dd1798d84c003fbd9c3d65372569961
M	backend/pom.xml
A	backend/src/test/java/com/baronesa/emporio/nfe/service/DanfePdfGeneratorServiceTest.java
M	tools/docker/java_images_contract.py
M	tools/docker/tests/test_java_images_contract.py
```

## 6. Cleanup nominal e estado final do Docker

`docker buildx du` registrava `Total: 2.025GB` no builder nominal antes do
cleanup. Executados, todos com exit 0:

```text
docker stop s33-jasper-93f4629-postgres
docker rm -v s33-jasper-93f4629-postgres
docker buildx rm s33-jasper-93f4629-builder
docker image rm s33-jasper-93f4629-backend:local
docker volume rm s33-jasper-93f4629-trivy-cache
docker image rm aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e
docker image rm docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
docker image rm postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
```

O `docker rm -v` removeu junto o volume anônimo do PostgreSQL efêmero, sem
resíduo. Nenhum prune foi executado, em nenhuma variante.

Prova final:

- builder, container BuildKit, container PostgreSQL, imagem e volume nominais:
  ausentes;
- imagens pinadas de BuildKit, Trivy e PostgreSQL: ausentes do daemon;
- volume anônimo `358ec441441567726a50d4ea73ac6f4ab5501a30d0f8aed48beb0346848662b8`:
  **preservado**;
- container preexistente `baronesa-postgres`: `Up (healthy)`, intocado;
- estado idêntico ao registrado antes da execução:

```text
TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE
Images          28        1         7.69GB    7.414GB (96%)
Containers      1         1         63B       0B (0%)
Local Volumes   20        1         1.129GB   1.081GB (95%)
Build Cache     0         0         0B        0B
```

`Build Cache 0B` confirmado.

Resíduos ignorados preexistentes, preservados e não tocados: os diretórios
`__pycache__` sob `tools/` e o `target/` do `backend`, populado pelo `mvn verify`
exigido pela §5.1 e já coberto por `.gitignore` e `.dockerignore`.

## 7. Acessos externos

Todos de leitura e restritos à lista da §5.2:

- `auth.docker.io` e `registry-1.docker.io`, exclusivamente para as referências
  pinadas: frontend Docker, BuildKit, base Maven, base Temurin, Trivy e
  PostgreSQL;
- `repo.maven.apache.org`, para as dependências do `backend`;
- repositórios Alpine já configurados na base, via `apk`;
- `mirror.gcr.io/aquasec/trivy-db:2` e o banco Java correspondente do Trivy.

Não houve GHCR, `docker login`, `docker push`, publicação, workflow dispatch,
release, tag, deploy, rollback, SSH, VPS, DNS, alteração de Nginx, mutação de
produção, criação ou rotação de credencial. Nenhum tráfego externo de WhatsApp.
O teste de PDF não acessa rede nem filesystem externo.

## 8. Gates finais de Git, secret scan e commit

`git diff --check` retornou exit 0.

Stage com exatamente os seis caminhos da §3 — os quatro técnicos e as duas
evidências. `git diff --cached --check` retornou exit 0.

Saída literal de `python3 tools/ci/secret_scan.py --tracked`, exit 0:

```text
secret-scan:clean:scanned=2454:allowed=288:unsupported=0:history_scanned=41486
```

Como registrar essa saída altera o próprio relatório, ele foi levado a stage de
novo e o mesmo secret scan foi repetido, exit 0, com saída literal idêntica à
acima; o conteúdo efetivamente commitado é o varrido pela última execução.
`git diff --cached --check` foi repetido em seguida e retornou exit 0.

`unsupported=0` nas duas execuções. Nenhum token, header, credencial ou valor de
configuração foi registrado neste relatório ou no JSON.

Situação frente aos critérios da task:

| Critério | Estado |
|---|---|
| 13 validadores e sete suítes Python em 0 | atendido |
| mutantes novos com erros exatos e contrato real vazio | atendido; 117 casos na suíte Docker |
| `mvn verify` com 85 testes ou mais, zero falhas | atendido: 85 testes |
| PDF real iniciado por `%PDF-` pelo renderer ativo | atendido |
| `java-danfe`, `jasperreports` e `jasperreports-fonts` fora da árvore e do jar | atendido |
| renderer atual e `java-nfe` preservados | atendido |
| Trivy exit 0 com zero HIGH/CRITICAL | atendido |
| grupos A, B e C em zero | atendido |
| cleanup nominal com `docker rm -v` e `Build Cache 0B` | atendido |
| volume `358ec441…` preservado | atendido |
| somente os seis caminhos da §3 no commit | atendido |
| commit único com a mensagem exata, sem push | atendido |

O commit técnico local único foi criado com a mensagem exata
`fix: remove unused JasperReports dependency chain`, sem `--no-verify`, sem
force, sem tag, sem outra branch, sem outro remote e sem commit adicional.
`git diff --check origin/main..HEAD` retornou exit 0. **Não houve push** — ele
pertence à retomada terminal da S30a, após o aceite desta slice. O executor não
aceita a slice e não alterou a task, o README ou qualquer arquivo fora dos seis
caminhos.

commit final = HEAD entregue

IN_PROGRESS — aguardando revisão do orquestrador
