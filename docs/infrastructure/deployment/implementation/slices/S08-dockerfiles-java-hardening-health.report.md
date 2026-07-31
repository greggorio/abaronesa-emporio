# S08 — Dockerfiles Java, hardening e health

Estado: **IN_PROGRESS — aguardando revisao do orquestrador**

Data da execucao: 28/07/2026  
CWD obrigatorio: `/home/gregorio/git/baronesa/emporio`

## 1. Resultado

Foram produzidas e validadas localmente as imagens Java do `backend` e do
`website_back`, exclusivamente para `linux/amd64`. Ambas usam build
multi-stage, runtime JRE 21 sem Maven/JDK, usuario numerico nao-root, health
check em loopback e bases fixadas por tag completa e digest.

Os cinco gates autorizados foram fechados somente depois dos `mvn verify`,
validacoes estaticas, builds, inspecoes e provas de runtime. O catalogo
permanece fail-closed com 18 gates pendentes.

## 2. Arquivos criados ou alterados

- `backend/Dockerfile`
- `backend/.dockerignore`
- `website_back/Dockerfile`
- `website_back/.dockerignore`
- `website_back/pom.xml`
- `website_back/src/main/resources/application-prod.properties`
- `website_back/src/main/java/com/baronesa/website/config/SecurityConfig.java`
- `website_back/src/test/java/com/baronesa/website/config/WebsiteHealthContractTest.java`
- `tools/docker/java_images_contract.py`
- `tools/docker/tests/test_java_images_contract.py`
- `ops/releases/components.yml`
- `tools/releases/tests/test_catalog.py`
- `docs/infrastructure/deployment/images/JAVA_IMAGES.md`
- `docs/infrastructure/deployment/release-control/README.md`
- este relatorio

Nenhum arquivo fora da lista autorizada pela task foi alterado.

## 3. Bases, digests e plataforma

Estagio de build das duas imagens:

- tag: `maven:3.9.11-eclipse-temurin-21-alpine`;
- digest do indice fixado no Dockerfile:
  `sha256:922927df2c662cdd47ddb116443d6bec4696cfae3de1a0ddac8fcc7b87ce61ae`;
- manifesto `linux/amd64` observado:
  `sha256:8fab75f6cd25265915f50c79911639ec01b2a807baef9aa8ba3ae987cc399009`.

Estagio de runtime das duas imagens:

- tag: `eclipse-temurin:21.0.8_9-jre-alpine-3.22`;
- digest do indice fixado no Dockerfile:
  `sha256:990397e0495ac088ab6ee3d949a2e97b715a134d8b96c561c5d130b3786a489d`;
- manifesto `linux/amd64` observado:
  `sha256:464a8f672e0b7825d7f09c405335cce072a1351fd50ac6effb8079a958969686`.

Os dois builds foram executados com `--platform linux/amd64`; o inspect
confirmou arquitetura `amd64`.

## 4. Contrato comum

- POM copiado antes do codigo e dependencias preparadas em cache BuildKit;
- compilacao/package no estagio Maven e copia de apenas um JAR executavel;
- runtime Temurin JRE 21 Alpine, sem Maven e sem `javac`;
- `USER 10001:10001`;
- entrypoint exec-form `["java", "-jar", "/app/app.jar"]`;
- `JAVA_TOOL_OPTIONS` declarado e substituivel em runtime;
- `SPRING_PROFILES_ACTIVE=prod`;
- `STOPSIGNAL SIGTERM`;
- health check com `curl`, loopback, intervalo, timeout, start period e retries;
- labels OCI de source, revision e version, sem credenciais;
- `.dockerignore` impede entrada de targets, Git, ambientes, dumps, certificados,
  uploads e outros artefatos indevidos no contexto.

## 5. Diferencas entre as imagens

### Backend ERP

- porta interna `8080`;
- pacotes adicionais `ffmpeg`, `fontconfig` e `ttf-dejavu`;
- schemas fiscais copiados para `/app/nfe/schemas` e tornados somente leitura;
- escrita comprovada como usuario final em `/app/uploads` e `/app/nfe/xmls`;
- subdiretorios de uploads preparados antes de `USER`;
- health em `http://127.0.0.1:8080/actuator/health`.

### Website backend

- porta interna `8085`;
- runtime comum com `curl`, certificados e timezone;
- escrita comprovada em `/app/uploads/galeria`,
  `/app/uploads/theme-assets`, `/app/uploads/android-assets` e
  `/app/uploads/android-private`;
- health em `http://127.0.0.1:8085/actuator/health`.

## 6. Health publico do website

Foi adicionada a dependencia Actuator. No profile `prod`:

- somente `health` e exposto;
- detalhes e componentes permanecem ocultos;
- Springdoc permanece desabilitado;
- somente o path exato `/actuator/health` recebe `permitAll`;
- nao ha liberacao ampla de `/actuator/**`.

Dois testes Java focados validam essas propriedades e a regra exata de
seguranca. O `mvn verify` do website confirmou esses testes. Nao foi iniciado
runtime integrado do JAR porque a task nao fornece fixture segura de banco;
a prova desta slice cobre wiring, exposicao, sanitizacao, seguranca, metadados
da imagem e health command. A resposta HTTP integrada sera comprovada junto ao
Compose e banco efemero.

## 7. Maven e testes Java

Variaveis efemeras e nao reais foram fornecidas somente ao processo de teste;
seus valores nao sao reproduzidos neste relatorio.

1. CWD `backend`

   `DB_PASSWORD=<ephemeral> INTEGRATION_SYSTEM_TOKEN_SECRET=<ephemeral> mvn -B verify`

   Codigo `0`; `BUILD SUCCESS`; 35 testes, zero falha e zero erro.

2. CWD `website_back`

   `DB_PASSWORD=<ephemeral> INTEGRATION_SYSTEM_TOKEN_SECRET=<ephemeral> mvn -B verify`

   Codigo `0`; `BUILD SUCCESS`; 49 testes, zero falha e zero erro.

Os testes ocorreram antes dos builds Docker. O `package -DskipTests` dentro da
imagem evita repetir a suite, mas nao substitui esses dois `verify`.

## 8. Validador Docker e mutantes

CWD em todos os comandos: raiz obrigatoria.

- `python3 tools/docker/java_images_contract.py validate`
  - codigo `0`;
  - `java-images-contract:valid`.
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -p 'test_java_images_contract.py' -v`
  - codigo `0`;
  - 36 testes aprovados.

A suite cobre os 30 invariantes minimos da task e mutantes adicionais para
input invalido, sanitizacao de erro e protecao de schemas.

## 9. Builds Docker

CWD: raiz obrigatoria.

- `docker build --platform linux/amd64 --build-arg VCS_REF=s08-local-validation --build-arg IMAGE_VERSION=s08-local -t abaronesa-emporio-backend:s08 backend`
  - codigo `0`;
  - imagem `sha256:eee2ff416170c9f2df9869fde29c894afdd170bca32bb2bcff716823549b4ee3`.
- `docker build --platform linux/amd64 --build-arg VCS_REF=s08-local-validation --build-arg IMAGE_VERSION=s08-local -t abaronesa-emporio-website-back:s08 website_back`
  - codigo `0`;
  - imagem `sha256:cf6acf41ff5f67a43f4b32d2e2017548fb9224b154b07bf8d1b4400dc0d1eada`.

Nenhuma imagem foi autenticada, publicada ou marcada como release.

## 10. Inspect, history e provas de runtime

Foram executados:

- `docker image inspect` sanitizado para user, entrypoint, env, portas, health,
  labels, arquitetura e tamanho;
- `docker history --no-trunc abaronesa-emporio-backend:s08`;
- `docker history --no-trunc abaronesa-emporio-website-back:s08`;
- `docker run --rm --entrypoint java <imagem> -version`;
- verificacoes com `command -v`, `id`, `test`, escrita e leitura;
- sobrescrita inofensiva de `JAVA_TOOL_OPTIONS` com propriedade de prova.

Asserts confirmados:

- user `10001:10001`;
- Java `21.0.8`, runtime Temurin;
- Maven, `javac` e diretorio Maven ausentes;
- entrypoint exato e profile `prod`;
- portas `8080` e `8085`;
- health em loopback e paths exatos;
- `JAVA_TOOL_OPTIONS` aceitou a propriedade de prova;
- `curl` existe nas duas imagens;
- `ffmpeg`, `ffprobe`, fontconfig e fonte DejaVu existem no backend;
- schema XSD legivel e nao gravavel pelo usuario final;
- todos os diretorios persistiveis prescritos aceitaram escrita e remocao;
- labels contem apenas metadados previstos;
- history nao revelou segredo, build arg sensivel, Maven ou JDK no estagio
  final.

Uma primeira sondagem de escrita terminou com codigo `1` por usar
acidentalmente `/app/xml`, que nao e o path contratual. Ela foi repetida com
`/app/nfe/xmls` e terminou com codigo `0`; nenhuma alteracao de imagem decorreu
desse erro de comando.

## 11. Tamanhos

- `abaronesa-emporio-backend:s08`: 487.205.312 bytes;
- `abaronesa-emporio-website-back:s08`: 324.205.670 bytes.

Os tamanhos foram obtidos por `docker image inspect`.

## 12. Catalogo e gates

Somente os cinco gates autorizados foram removidos:

- `BACKEND_DOCKERFILE_HARDENING`;
- `BACKEND_JVM_OPTIONS`;
- `BACKEND_FISCAL_SCHEMA_PATH`;
- `WEBSITE_BACK_HEALTH_CHECK`;
- `WEBSITE_BACK_DOCKERFILE_HARDENING`.

O `backend` passou a `ready`. O health do `website_back` passou a `confirmed`
em `/actuator/health`, mas sua readiness continua `blocked`, a persistencia
`/app/uploads` continua `pending` e
`WEBSITE_BACK_UPLOAD_PERSISTENCE` foi preservado.

Comandos, CWD raiz:

- `python3 tools/releases/catalog.py validate`
  - codigo `0`; `catalog:valid`.
- `python3 tools/releases/catalog.py validate --require-release-ready`
  - codigo esperado `3`; exatamente 18 gates listados.
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_catalog.py' -v`
  - primeira execucao: codigo `1`, 42 aprovacoes e duas falhas porque os testes
    11 e 12 ainda usavam o backend como fixture bloqueada;
  - fixtures migradas para o `website_back`, sem relaxar o validador;
  - repeticao: codigo `0`, 44 testes aprovados.

Nenhum gate de frontend, website_front, WhatsApp ou gateway foi alterado.

## 13. Acessos externos

Somente acessos autorizados e observados durante os builds:

- `docker.io` e `registry-1.docker.io`, para frontend BuildKit e bases;
- `dl-cdn.alpinelinux.org`, acionado pela base para pacotes runtime;
- `repo.maven.apache.org`, para dependencias Maven publicas;
- `maven-central.storage-download.googleapis.com`, mirror publico referenciado
  pela resolucao Maven;
- `jaspersoft.jfrog.io`, repositorio publico exigido por dependencia existente
  do backend.

Nao houve acesso ao GitHub, GHCR, VPS, DNS operacional, banco externo ou
registry autenticado.

## 14. Itens nao determinados e limites

- persistencia definitiva dos uploads do website aguarda Compose canonico;
- comportamento HTTP integrado e reconciliacao com banco aguardam ambiente
  efemero da slice de Compose;
- nao foram definidos Compose, gateway, workflow, release, deploy ou imagem
  publicavel;
- a atualizacao futura de bases exige nova resolucao de digests e repeticao
  integral de verify, validator, build, inspect e runtime probes.

## 15. Estado protegido

Comandos executados no CWD raiz:

- `git ls-files --stage`: codigo `0`, sem saida; indice real vazio;
- `git rev-parse --verify HEAD`: codigo `128`; HEAD inexistente;
- `git tag --list`: codigo `0`, sem saida;
- `git reflog show --all`: codigo `0`, sem saida;
- busca de workflow YAML em `.github/workflows`: codigo `0`, sem saida;
- busca de `__pycache__` e `*.pyc` em `tools/docker` e `tools/releases`:
  codigo `0`, sem saida;
- `docker ps --filter ancestor=abaronesa-emporio-backend:s08`: zero container;
- `docker ps --filter ancestor=abaronesa-emporio-website-backend:s08`: zero
  container;
- `docker image inspect` confirmou as duas imagens locais `:s08`.

Nao houve `git add`, commit, tag, push, instalacao no host, prune, acesso a
producao nem alteracao da task ou tracker.

## 16. Bloqueios

Nao ha bloqueio de execucao da S08. Permanecem deliberadamente fora desta
slice os itens descritos na secao anterior e os 18 gates tecnicos do catalogo.

Estado final: **IN_PROGRESS — aguardando revisao do orquestrador**

---

## 17. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-28`

A S08 atende integralmente ao contrato.

Evidencias aceitas:

- os dois `mvn -B verify` registrados terminaram com codigo `0`, com 35 testes
  no backend ERP e 49 no website backend;
- o validador Docker terminou com codigo `0` e sua suite mutante passou em 36
  de 36 testes;
- as duas imagens `linux/amd64` foram construidas, inspecionadas e exercitadas
  localmente;
- os quatro `FROM` usam tags completas e digests imutaveis, sem `latest`;
- os runtimes usam JRE 21, sem Maven, JDK ou `javac`;
- UID/GID final, entrypoint exec-form, `JAVA_TOOL_OPTIONS`, portas,
  `STOPSIGNAL` e health checks correspondem ao contrato;
- a imagem ERP contem ffmpeg, ffprobe, certificados, timezone, fontes e os
  schemas fiscais aprovados;
- as provas de leitura, escrita e nao escrita confirmam as permissoes dos
  schemas, XMLs e uploads;
- o website backend possui Actuator dedicado, exposicao restrita a health,
  detalhes ocultos e permissao publica limitada a `/actuator/health`;
- `.dockerignore`, labels OCI e historico sanitizado protegem os contextos e
  nao revelaram segredo;
- somente os cinco gates autorizados foram removidos;
- `backend` esta `ready` e `website_back` permanece `blocked` exclusivamente
  por `WEBSITE_BACK_UPLOAD_PERSISTENCE`;
- o catalogo estrutural e seus 44 testes passaram;
- readiness global continua falhando de forma fechada, com codigo `3` e
  exatamente 18 gates;
- indice Git, `HEAD`, tags, reflog, ausencia de workflows e caches foram
  preservados;
- nao houve Compose, publicacao, commit, push ou acesso a producao.

O teste HTTP integrado do website backend e a persistencia efetiva de uploads
continuam corretamente adiados para a slice do Compose. A prova estatica
focada de wiring, exposicao e seguranca satisfaz a excecao expressa da S08 e
nao fecha o gate de persistencia.

Os estados `IN_PROGRESS` anteriores permanecem como historico. A autoridade
final desta secao altera o estado da S08 para `ACCEPTED`.

A S09 pode agora implementar e validar exclusivamente as imagens e os
contratos de runtime de `frontend`, `website_front` e `whatsapp_service`, sem
antecipar gateway, Compose, CI, release ou producao.
