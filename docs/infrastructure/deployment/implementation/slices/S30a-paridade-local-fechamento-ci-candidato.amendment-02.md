# S30a — amendment-02: fronteira dos validadores Docker e patch de dependências

> **Estado:** `AUTHORIZED` pelo orquestrador em 01/08/2026
> **Base:** `S30a-paridade-local-fechamento-ci-candidato.task.md` e sua amendment-01
> **Commit-base:** `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`

## 1. Reconhecimento de defeito do contrato

A amendment-01 autorizou alterar os seis Dockerfiles, mas **não** os validadores
que fixam as referências das imagens-base. A matriz falhou por omissão do
orquestrador, não por erro do executor:

```text
tools/docker/java_images_contract.py validate -> BASE_TAG_INVALID:website_back
python3 -m unittest discover -s tools/docker/tests -> FAILED (failures=7)
```

Isto é exatamente o defeito que a Seção 32.2 do HANDOFF diz para evitar:
fronteira por lista de arquivos quando a mudança é de classe. A correção é do
contrato, não do executor, e nada do que ele entregou é rejeitado.

O resultado da amendment-01 está aceito: `224 -> 110` ocorrências, com
`frontend`, `website_front` e `gateway` zerados e os seis Dockerfiles alterados
somente nas linhas `FROM`, preservando digest, multi-stage, usuário não-root e
healthcheck.

## 2. Fronteira autorizada

Acrescentar à fronteira da S30a, exclusivamente:

- `tools/docker/java_images_contract.py`;
- `tools/docker/validate_node_images.py`;
- `tools/docker/tests/test_java_images_contract.py`;
- `tools/docker/tests/test_validate_node_images.py`;
- `backend/pom.xml`;
- `website_back/pom.xml`;
- `whatsapp_service/package.json`;
- `whatsapp_service/package-lock.json`.

Permanece autorizado o que a task e a amendment-01 já autorizavam. Continua
proibido alterar: código de aplicação Java, Vue, React ou Node; `frontend` e
`website_front` (já limpos); política do Trivy; `.trivyignore`; workflows além
do que a S30a já autorizou; `release_control`; OpenAPI; schemas; `.gitignore`;
HANDOFF; tracker; produção; qualquer slice nova.

## 3. Correção A — validadores Docker

Atualizar os dois validadores e seus testes para as referências efetivamente
aplicadas pela amendment-01:

```text
maven:3.9.16-eclipse-temurin-21-alpine@sha256:d88e5b38…
eclipse-temurin:21.0.11_10-jre-alpine-3.23@sha256:3f08b138…
node:24.18.1-alpine3.24@sha256:f70403e8…
nginx:1.31.3-alpine3.24@sha256:4a73073b…
nginxinc/nginx-unprivileged:1.31.3-alpine3.24@sha256:59ccf094…
```

**Não enfraquecer nenhuma verificação.** Continuam obrigatoriamente rejeitados:
tag flutuante, `latest`, alias sem digest, Node 18, ausência de `@sha256:` e
qualquer base fora da linha maior aprovada (Java 21, Node 24, nginx 1.x). Os
testes que hoje falham devem voltar a passar provando a nova referência, não
afrouxando o critério. Preservar os mutantes existentes e acrescentar um que
prove que a referência antiga passa a ser recusada.

## 4. Correção B — patch de dependências

O resíduo de 110 é integralmente de dependências de aplicação. Aplicar somente
salto de **patch dentro da mesma linha menor**, sem migração:

- `backend/pom.xml` e `website_back/pom.xml`: `spring-boot-starter-parent`
  de `3.3.5` para `3.3.13`;
- `whatsapp_service`: atualizar o lockfile dentro dos intervalos semver já
  declarados em `package.json`, sem promover `express` a 5.x nem trocar
  `whatsapp-web.js` de linha maior.

Se algum CVE remanescente vier de dependência transitiva não governada pelo
parent — por exemplo protobuf, commons-beanutils ou pgjdbc — é permitido fixá-la
por `<dependencyManagement>` no POM correspondente, sempre dentro da mesma linha
maior da biblioteca. Não adicionar dependência nova nem remover existente.

## 5. Rede de regressão obrigatória

O salto de dependência só é aceitável com a suíte real executada. Docker local
está autorizado; subir PostgreSQL com o digest fixado do `ci.yml`:

```text
postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
POSTGRES_DB=testdb POSTGRES_USER=test POSTGRES_PASSWORD=test  porta 5432
```

Executar e registrar:

```bash
cd backend && mvn -B verify
cd website_back && mvn -B verify
cd whatsapp_service && npm ci && npm run test
```

**Condição de reversão fechada:** se `mvn -B verify` ou o teste do WhatsApp
falhar após o salto, reverter **apenas** a mudança de dependência do componente
afetado, preservar a Correção A, registrar a falha com saída sanitizada e parar.
Não perseguir a atualização, não migrar de minor, não alterar código de
aplicação para acomodar o salto.

Ao final, derrubar o container PostgreSQL, remover imagens e caches criados e
comprovar BuildKit em `0B`.

## 6. Nova medição

Refazer os seis builds e os seis scans Trivy conforme a Seção 4.4 da S30a, com a
imagem oficial fixada, e produzir o inventário no mesmo formato, com delta por
componente contra as **110** ocorrências atuais. Não alterar a política do Trivy
e não criar `.trivyignore`.

## 7. Matriz e commits

Executar integralmente a matriz da Seção 7 da S30a, mais
`python3 -m unittest discover -s tools/docker/tests` e o scanner de segredos.
Todos devem retornar exit 0.

Se os gates locais passarem, criar **três commits locais e nenhum push**:

```text
fix: close CI command contract and local parity   (arquivos da S30a)
fix: bump hardened base images                    (os seis Dockerfiles + validadores Docker)
fix: patch application dependencies               (POMs, package.json, lockfile)
```

Sem force, tags, outro remote, outra branch, `--no-verify`, `git init`,
alteração de identidade ou `git push`.

## 8. Relatório

Atualizar somente o relatório S30a, com seção própria desta emenda, e terminar
exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```
