# S33 — Remoção da cadeia JasperReports não utilizada

> **Estado:** `PLANNED`
> **Tipo:** redução de superfície e fechamento do grupo C da S30a
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Relatório:** `S33-remocao-java-danfe-jasperreports.report.md`
> **Commit técnico:** `fix: remove unused JasperReports dependency chain`

## 1. Resultado esperado

O único resíduo HIGH/CRITICAL é trazido por uma dependência sem consumidor:

```text
br.com.swconsultoria:java-danfe:1.8
+- net.sf.jasperreports:jasperreports:6.20.6
\- net.sf.jasperreports:jasperreports-fonts:6.20.6
```

Nenhum código, recurso ou configuração do `backend` referencia classes ou
recursos de `br.com.swconsultoria.impressao`, `java-danfe` ou JasperReports. O
DANFE usado pela aplicação é implementado por
`DanfePdfGeneratorService`, template Thymeleaf `danfe.html`, Flying Saucer/OpenPDF
e ZXing.

Esta slice remove a cadeia morta. Não atualiza para JasperReports 7 e não cria
exceção Trivy. Ao final:

- `java-danfe`, `jasperreports` e `jasperreports-fonts` não aparecem na árvore
  Maven nem no jar empacotado;
- um teste funcional gera um PDF pelo renderer real da aplicação;
- o scan do `backend` retorna zero HIGH/CRITICAL;
- os grupos A, B e C ficam em zero localmente;
- um único commit local é criado, sem push.

## 2. Decisões fechadas

### 2.1 Remoção do POM

Em `backend/pom.xml`, remover integralmente:

- a propriedade `java-danfe.version`;
- a dependência `br.com.swconsultoria:java-danfe` e suas exclusões.

Não adicionar JasperReports 7, substituto de `java-danfe`, fork, repositório,
exclusão Trivy ou dependência direta de JasperReports. Preservar `java-nfe`, o
renderer atual e todas as demais versões da S32.

### 2.2 Prova funcional do DANFE ativo

Criar
`backend/src/test/java/com/baronesa/emporio/nfe/service/DanfePdfGeneratorServiceTest.java`.
O teste deve:

- configurar `SpringTemplateEngine` com `ClassLoaderTemplateResolver` para
  `templates/*.html`, UTF-8 e modo HTML;
- usar `ConfigManager` mockado apenas para devolver os defaults recebidos;
- criar `DanfeModel` mínimo com listas vazias e sem chave, evitando QR ou
  acesso externo;
- chamar o `DanfePdfGeneratorService` real;
- exigir saída não vazia iniciada por `%PDF-`.

Sem `@SpringBootTest`, banco, filesystem externo, rede ou snapshot binário.

### 2.3 Contrato contra regressão

Estender `tools/docker/java_images_contract.py` sobre o `backend/pom.xml`:

| Condição | Erro exato |
|---|---|
| propriedade ou artefato `java-danfe` presente | `UNUSED_JAVA_DANFE_FORBIDDEN:backend` |
| propriedade ou artefato `jasperreports`/`jasperreports-fonts` presente | `JASPERREPORTS_FORBIDDEN:backend` |
| renderer ativo incompleto | `DANFE_RENDERER_REQUIRED:backend` |

O renderer obrigatório é:

```text
org.springframework.boot:spring-boot-starter-thymeleaf
org.xhtmlrenderer:flying-saucer-pdf-openpdf:9.1.22
com.google.zxing:core:3.5.3
com.google.zxing:javase:3.5.3
```

Adicionar mutantes focais em `test_java_images_contract.py` para cada família:
reintrodução da propriedade, reintrodução de `java-danfe`, dependência direta
de JasperReports e remoção ou downgrade de cada parte do renderer. Cada mutante
exige o erro exato correspondente; o contrato real retorna lista vazia.

## 3. Fronteira

Arquivos técnicos:

```text
backend/pom.xml
backend/src/test/java/com/baronesa/emporio/nfe/service/DanfePdfGeneratorServiceTest.java
tools/docker/java_images_contract.py
tools/docker/tests/test_java_images_contract.py
```

Evidências:

```text
docs/infrastructure/deployment/implementation/slices/S33-trivy-findings.backend.after.json
docs/infrastructure/deployment/implementation/slices/S33-remocao-java-danfe-jasperreports.report.md
```

Nenhum outro arquivo pode ser alterado. Em particular: não modificar código de
produção, template DANFE, Dockerfile, `java-nfe`, task, correction, README ou
workflow.

## 4. Preflight

Executar antes da primeira alteração:

```bash
test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"
test "$(git branch --show-current)" = "main"
test -z "$(git status --porcelain)"
test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"
test "$(git log -1 --format=%s)" = "docs: accept S32 and open S33 scope"
test "$(git rev-list --count origin/main..HEAD)" = "13"
git rev-parse HEAD
```

Todos os `test` devem retornar 0. O último comando é o SHA-base. Divergência
exige parada antes de alterar arquivos.

## 5. Verificação

### 5.1 Código, contrato e regressão

Executar os 13 validadores e as sete suítes Python canônicas. No `backend`, usar
o PostgreSQL efêmero pinado abaixo, publicado apenas em `127.0.0.1`, e executar:

```bash
mvn -B verify
mvn -B dependency:tree
```

Imagem PostgreSQL:

```text
postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
```

O `mvn verify` deve executar 85 testes ou mais, incluindo o teste de PDF, com
zero falhas. A árvore deve provar:

- ausência de `java-danfe`, `jasperreports` e `jasperreports-fonts`;
- presença das dependências declaradas do renderer e resolução de OpenPDF
  `1.3.11` pela cadeia Flying Saucer;
- `java-nfe` preservado.

Inspecionar também o jar repackaged e exigir ausência de:

```text
BOOT-INF/lib/java-danfe-*
BOOT-INF/lib/jasperreports-*
BOOT-INF/lib/jasperreports-fonts-*
```

### 5.2 Build e Trivy

Construir somente o `backend`, uma vez, com:

```text
frontend: docker.io/docker/dockerfile@sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720
BuildKit: docker.io/moby/buildkit@sha256:63db51c9b30208a7c2b1c40392c7ebb9ce2f85ba238a18a85420f8f5ea2d4684
driver option: network=host
platform: linux/amd64
Trivy: aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e
Trivy network: host
DB: mirror.gcr.io/aquasec/trivy-db:2
```

Usar `--load`, `--push=false`, `VCS_REF=<SHA-base>` e
`IMAGE_VERSION=ci-<SHA-curto>`. Acessos externos ficam limitados a Docker Hub
para as referências pinadas, Maven Central, repositórios Alpine da base e o DB
Trivy. GHCR, login e push são proibidos.

Escanear com `HIGH,CRITICAL`, `ignore-unfixed=false`, `exit-code=1` e
`skip-version-check`. O resultado obrigatório é exit 0 e zero achados. Qualquer
achado ou falha de transporte exige parada sem commit.

Emitir `S33-trivy-findings.backend.after.json` no esquema das medições
anteriores, com linhagem sobre os quatro arquivos técnicos, identidade da
imagem, bancos, contagens explícitas em zero e `findings: []`.

Delta obrigatório:

```text
backend: 2 HIGH JasperReports -> 0 HIGH/CRITICAL
grupo C: 2 ocorrências / 2 CVEs -> 0 / 0
resíduo local A+B+C: 0
```

### 5.3 Cleanup e Git

Usar prefixo `s33-jasper-<SHA-curto>` e remover nominalmente somente builder,
contêineres, volumes e imagens criados. Usar `docker rm -v` no PostgreSQL
efêmero. Preservar o volume anônimo
`358ec441441567726a50d4ea73ac6f4ab5501a30d0f8aed48beb0346848662b8`, o
`baronesa-postgres` e todos os recursos preexistentes. Comprovar Build Cache
`0B`; prune amplo é proibido.

Ao final:

- stage somente dos seis caminhos da §3;
- `git diff --cached --check` e `git diff --check origin/main..HEAD` em zero;
- secret scan rastreado `clean`, `unsupported=0`, sobre o conteúdo final;
- exatamente um commit técnico com a mensagem
  `fix: remove unused JasperReports dependency chain`;
- nenhum push.

## 6. Parada, aceite e relatório

Parar antes do commit diante de teste falho, dependência residual, PDF inválido,
achado Trivy, arquivo fora da fronteira ou cleanup divergente. Registrar o erro
literal; não criar correction ou alternativa por iniciativa própria.

O relatório deve registrar CWD, Git, arquivos, mutantes, testes, árvore Maven,
prova do PDF, conteúdo do jar, build, Trivy, JSON/linhagem, cleanup, secret scan,
commit e ausência de push. O executor não aceita a slice nem altera o índice.

Terminar exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```
