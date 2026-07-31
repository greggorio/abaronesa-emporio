# S08 — Dockerfiles Java endurecidos e health do website backend

> **Estado:** `ACCEPTED` — `2026-07-28`  
> **Tipo:** infraestrutura de imagem, health check e contratos verificaveis  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S07 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Contrato de componentes:** [release-control/README.md](../../release-control/README.md)  
> **Relatorio de saida:** `S08-dockerfiles-java-hardening-health.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro:

1. esta task;
2. a revisao final da S07;
3. as secoes `Dockerfiles` e `Health checks` da arquitetura aprovada;
4. `ops/releases/components.yml`;
5. os Dockerfiles atuais de `backend` e `website_back`;
6. os POMs, properties e configuracoes de seguranca dos dois backends;
7. o validador e os testes do catalogo de componentes.

Nao altere esta task nem o tracker
`docs/infrastructure/deployment/implementation/README.md`.

Esta slice cobre somente as duas imagens Java. Nao implemente frontends,
WhatsApp, gateway, Compose, CI, release, deploy ou VPS.

## Objetivo observavel

Ao final:

- `backend/Dockerfile` e `website_back/Dockerfile` sao multi-stage Java 21;
- build usa Maven e runtime nao contem Maven/JDK;
- bases possuem tag exata e digest imutavel;
- processos executam como usuario nao-root;
- `JAVA_TOOL_OPTIONS` e consumido nativamente pela JVM;
- jars iniciam por `ENTRYPOINT` exec-form;
- imagens possuem health checks HTTP internos;
- backend contem schemas fiscais no path de producao aprovado;
- backend contem ffmpeg, certificados CA, timezone e fontes minimas;
- uploads/XML pertencem ao usuario da aplicacao;
- `website_back` possui health dedicado e publico, sem detalhes sensiveis;
- contextos de build possuem `.dockerignore`;
- nenhum segredo entra em layer, ARG, ENV, label ou historico;
- contratos estaticos e imagens construidas sao validados localmente;
- gates fechados sao atualizados no catalogo com evidencia;
- Compose, publicacao e push continuam ausentes.

## 1. Plataforma e bases

### 1.1 Target

O target inicial desta slice e:

```text
linux/amd64
```

Isso nao confirma ainda a arquitetura real da VPS. Registrar essa verificacao
como pendencia da futura slice de preparacao do host.

### 1.2 Imagens base

Usar:

- Maven 3.9.x com Eclipse Temurin JDK 21 no stage de build;
- Eclipse Temurin JRE 21 Alpine no runtime.

Regras:

- selecionar tags existentes com versao completa de Maven/JDK/JRE e linha da
  distribuicao;
- cada `FROM` deve conter tag legivel e `@sha256:<digest>`;
- nao usar `latest`;
- nao usar somente `21`, `21-jre-alpine`, `3.9` ou outro alias flutuante;
- registrar no relatorio tag, digest e plataforma, sem credencial;
- nao alterar o digest sem rebuild e nova validacao;
- usar o mesmo runtime base nos dois componentes quando tecnicamente
  compativel;
- nao instalar JDK, Maven, shell de build ou compilador no runtime.

Pode consultar/puxar somente as imagens base publicas necessarias no Docker
Hub e dependencias Maven publicas durante o build. Nao autenticar em registry
e nao acessar GitHub/GHCR/VPS.

## 2. Contrato comum dos Dockerfiles Java

Ambos devem:

- usar sintaxe Dockerfile compativel com BuildKit;
- possuir stages nomeados `build` e `runtime`;
- usar `WORKDIR` explicito;
- copiar primeiro `pom.xml` para aproveitar cache;
- usar `mvn -B` no build;
- usar cache BuildKit para `/root/.m2`;
- executar package com testes pulados somente depois de `mvn -B verify`
  aprovado para o mesmo workspace nesta slice;
- copiar somente o jar necessario para o runtime;
- ter exatamente um jar de aplicacao em `/app/app.jar`;
- definir usuario/grupo dedicados com UID/GID numericos estaveis e nao zero;
- preparar diretorios e ownership antes de `USER`;
- declarar `USER` no estado final;
- usar `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`;
- nao usar shell wrapper para expandir opcoes JVM;
- declarar `ENV JAVA_TOOL_OPTIONS=""` ou equivalente vazio, sem opcoes
  obrigatorias escondidas;
- usar `STOPSIGNAL SIGTERM`;
- expor somente a porta interna do componente;
- conter `HEALTHCHECK` com `curl --fail --silent --show-error`;
- usar `127.0.0.1`, nunca hostname externo;
- ter interval, timeout, retries e start-period explicitos;
- instalar `curl`, `ca-certificates` e `tzdata` no runtime;
- remover cache do gerenciador de pacotes;
- nao declarar `VOLUME` nesta fase;
- nao declarar senha, token, URL privada, IP de VPS ou dado de tenant;
- nao copiar `.env`, `.git`, target local, uploads, PFX, HPROF ou caches;
- nao usar Docker socket;
- incluir labels OCI para source, revision e version via ARGs nao sensiveis,
  com defaults neutros;
- nao fazer push.

ARGs permitidos:

```text
VCS_REF
IMAGE_VERSION
```

Nao aceitar por ARG:

- segredo;
- profile Spring;
- comando;
- jar/path arbitrario;
- URL de registry;
- imagem base arbitraria.

O profile default da imagem deve ser `prod`, ainda sobrescritivel em runtime
por `SPRING_PROFILES_ACTIVE`.

## 3. Backend ERP

Arquivo:

```text
backend/Dockerfile
```

Contrato adicional:

- porta `8080`;
- health `http://127.0.0.1:8080/actuator/health`;
- copiar `backend/nfe/schemas/` para `/app/nfe/schemas/`;
- schemas devem ser legiveis e nao gravaveis pelo usuario da aplicacao;
- criar `/app/nfe/xmls` gravavel pelo usuario da aplicacao;
- criar `/app/uploads` e subdiretorios necessarios gravaveis;
- instalar `ffmpeg`;
- instalar `fontconfig` e ao menos uma familia de fontes livre adequada a
  geracao de PDF/imagem;
- manter certificados CA;
- nao copiar PFX;
- nao criar path sob `/home/gregorio`;
- provar `java -version` Java 21;
- provar presenca de `ffmpeg`, `ffprobe`, `curl` e fontes;
- provar leitura de um XSD conhecido;
- provar escrita em `/app/uploads` e `/app/nfe/xmls` como usuario final;
- provar que `/app/nfe/schemas` nao e gravavel pelo usuario final.

`BACKEND_FISCAL_SCHEMA_PATH` somente pode ser fechado se o path na imagem e
as propriedades `prod` coincidirem e as provas de permissao passarem.

`BACKEND_JVM_OPTIONS` somente pode ser fechado se `JAVA_TOOL_OPTIONS` estiver
presente e a JVM demonstrar seu consumo sem shell wrapper. Use opcao
inofensiva e efemera em um `docker run` de verificacao, sem persisti-la na
imagem.

## 4. Website backend

Arquivos principais:

```text
website_back/Dockerfile
website_back/pom.xml
website_back/src/main/resources/application.properties
website_back/src/main/resources/application-prod.properties
website_back/src/main/java/com/baronesa/website/config/SecurityConfig.java
```

### 4.1 Health dedicado

Implementar:

```text
GET /actuator/health
```

Regras:

- adicionar `spring-boot-starter-actuator`;
- expor somente `health` no profile de producao;
- health publico para verificacao interna do container;
- detalhes `never` para chamada nao autenticada;
- nao expor env, configprops, beans, heapdump, mappings ou secrets;
- manter demais rotas e regras existentes;
- health deve refletir dependencias Spring/DB, nao retornar sucesso fixo
  inventado;
- adicionar teste focado que prove acesso sem JWT e payload sem detalhe
  sensivel;
- nao criar controller de health paralelo se Actuator satisfizer o contrato.

Se o teste completo do health depender de banco e nao houver fixture segura,
provar ao menos wiring, exposicao, seguranca e schema por teste focado. Nao
inventar conexao externa.

### 4.2 Imagem

Contrato:

- porta `8085`;
- health `http://127.0.0.1:8085/actuator/health`;
- criar como gravaveis:
  - `/app/uploads`;
  - `/app/uploads/galeria`;
  - `/app/uploads/theme-assets`;
  - `/app/uploads/android-assets`;
  - `/app/uploads/android-private`;
- runtime sem Maven/JDK;
- `curl`, CA e timezone presentes;
- provar Java 21;
- provar escrita nos diretorios como usuario final.

Nao marcar a persistencia de uploads como confirmada: isso depende do Compose
canonico da proxima macroetapa.

## 5. `.dockerignore`

Criar:

```text
backend/.dockerignore
website_back/.dockerignore
```

Cobrir no minimo:

```text
.git
.github
.env
.env.*
!.env.example
target
uploads
*.pfx
*.p12
*.pem
*.key
*.hprof
hs_err_pid*
replay_pid*
.idea
.vscode
.classpath
.project
.settings
```

Regras:

- nao excluir `pom.xml`, `src/` nem `backend/nfe/schemas/`;
- `.env.example` nao precisa ser copiado pelo Dockerfile;
- ignorar arquivos locais sem apagar ou mover nenhum deles;
- validar contextos sem abrir PFX, HPROF ou `.env.production`.

## 6. Validador local

Criar:

```text
tools/docker/java_images_contract.py
tools/docker/tests/test_java_images_contract.py
```

Usar Python 3 e biblioteca padrao. Nao instalar dependencia Python.

Comando:

```bash
python3 tools/docker/java_images_contract.py validate
```

Exit codes:

- `0`: contrato estatico valido;
- `2`: contrato invalido ou input invalido.

Validar fail-closed, no minimo:

1. arquivos obrigatorios existem;
2. dois stages exatos;
3. bases Java 21/Maven 3.9/JRE;
4. tags completas;
5. digests SHA-256 presentes;
6. ausencia de `latest`;
7. runtime sem Maven/JDK;
8. usuario final numerico nao-root;
9. entrypoint exec-form exato;
10. `JAVA_TOOL_OPTIONS`;
11. `SPRING_PROFILES_ACTIVE=prod`;
12. portas exatas;
13. health paths exatos;
14. health usa loopback;
15. health possui parametros completos;
16. packages comuns do runtime;
17. ffmpeg/fontes no backend;
18. schemas copiados para path correto;
19. nenhum path pessoal;
20. nenhum segredo em ARG/ENV/LABEL;
21. ARGs limitados aos dois permitidos;
22. `.dockerignore` cobre itens obrigatorios;
23. contextos essenciais nao sao ignorados;
24. Actuator presente no website backend;
25. producao expoe somente health sem detalhes;
26. seguranca libera exatamente o health necessario;
27. diretorios persistiveis pertencem ao usuario final por contrato;
28. nenhum `VOLUME`;
29. nenhum Docker socket;
30. documentacao obrigatoria existe.

O validador nao acessa Docker ou rede e nao imprime conteudo sensivel.

## 7. Testes do validador

Criar pelo menos 32 testes `unittest`:

- contrato real valido;
- um mutante para cada item minimo;
- path ausente;
- input invalido;
- output de erro sanitizado.

Mutantes somente em memoria ou `/tmp`.

Executar:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/docker/tests \
  -p 'test_java_images_contract.py' \
  -v
```

## 8. Build e verificacao das imagens

### 8.1 Pre-build

Antes dos `docker build`, executar:

```bash
cd backend
DB_PASSWORD=s08-ephemeral-db-password \
INTEGRATION_SYSTEM_TOKEN_SECRET=s08-ephemeral-integration-token-for-tests \
mvn -B verify
```

```bash
cd website_back
DB_PASSWORD=s08-ephemeral-db-password \
INTEGRATION_SYSTEM_TOKEN_SECRET=s08-ephemeral-integration-token-for-tests \
mvn -B verify
```

Se outra variavel for obrigatoria, usar fixture efemera somente no processo e
registrar apenas o nome.

### 8.2 Build local

A partir da raiz:

```bash
docker build --platform linux/amd64 \
  --build-arg VCS_REF=s08-local-validation \
  --build-arg IMAGE_VERSION=s08-local \
  -t abaronesa-emporio-backend:s08 \
  backend

docker build --platform linux/amd64 \
  --build-arg VCS_REF=s08-local-validation \
  --build-arg IMAGE_VERSION=s08-local \
  -t abaronesa-emporio-website-backend:s08 \
  website_back
```

Nao usar `--push`.

### 8.3 Inspect estatico das imagens

Registrar output sanitizado de:

```bash
docker image inspect abaronesa-emporio-backend:s08
docker image inspect abaronesa-emporio-website-backend:s08
docker history --no-trunc abaronesa-emporio-backend:s08
docker history --no-trunc abaronesa-emporio-website-backend:s08
```

Nao colar historico integral no relatorio. Registrar somente asserts:

- usuario;
- entrypoint;
- env nao sensivel;
- porta;
- healthcheck;
- labels;
- layers sem segredo;
- tamanho final;
- arquitetura.

### 8.4 Provas sem subir aplicacao

Executar como usuario final da imagem:

```bash
docker run --rm --entrypoint java \
  abaronesa-emporio-backend:s08 -version

docker run --rm --entrypoint java \
  abaronesa-emporio-website-backend:s08 -version
```

Executar checks de `id`, leitura/escrita, packages e paths com `sh -c`, sem
alterar host e sem iniciar conexao externa. Usar arquivo temporario somente
dentro do container descartavel e remove-lo no mesmo comando.

Provar `JAVA_TOOL_OPTIONS` com opcao inofensiva apenas no `docker run`, por
exemplo limite de heap ou propriedade ficticia. Nao persistir o valor.

Nao executar o jar contra banco externo nesta slice. O health em container
integrado sera repetido com PostgreSQL no Compose canonico.

### 8.5 Limpeza

As duas tags locais `:s08` sao artefatos de validacao e podem permanecer para
a proxima slice. Nao remover imagens, caches ou containers alheios.

Se criar container nomeado por engano, remover somente o alvo exato e
registrar. Nao executar prune.

## 9. Catalogo de componentes

Atualizar, com evidencia:

### `backend`

Remover:

```text
BACKEND_DOCKERFILE_HARDENING
BACKEND_JVM_OPTIONS
BACKEND_FISCAL_SCHEMA_PATH
```

Alterar:

```text
readiness: ready
```

Somente fazer isso se todas as provas do backend passarem.

### `website_back`

Remover:

```text
WEBSITE_BACK_HEALTH_CHECK
WEBSITE_BACK_DOCKERFILE_HARDENING
```

Alterar:

```text
health_check.status: confirmed
health_check.path: /actuator/health
```

Preservar:

```text
readiness: blocked
WEBSITE_BACK_UPLOAD_PERSISTENCE
persistence /app/uploads: pending
```

### Gates restantes

O total deve cair de 23 para 18. Nenhum gate de frontend, website_front,
WhatsApp ou gateway pode mudar.

Atualizar testes do catalogo e a documentacao de release-control. Executar:

```bash
python3 tools/releases/catalog.py validate
python3 tools/releases/catalog.py validate --require-release-ready
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_catalog.py' \
  -v
```

Esperado:

- validacao estrutural codigo `0`;
- readiness global codigo `3`;
- exatamente 18 gates pendentes;
- testes do catalogo aprovados.

## 10. Documentacao

Criar:

```text
docs/infrastructure/deployment/images/JAVA_IMAGES.md
```

Documentar:

- bases/tag/digest/plataforma;
- estrutura multi-stage;
- usuario e permissoes;
- packages de runtime;
- paths e persistencias futuras;
- JVM options;
- health checks;
- comandos de build e inspect;
- por que testes ocorrem antes do package `-DskipTests`;
- como atualizar bases/digests com rebuild completo;
- limites da evidencia desta slice;
- persistencia do website ainda pendente;
- runtime integrado com banco ainda pendente para Compose;
- nenhum push ou imagem de release foi produzido.

Atualizar:

```text
docs/infrastructure/deployment/release-control/README.md
```

Somente para refletir gates realmente fechados e apontar a documentacao das
imagens.

## 11. Escopo de escrita

Pode criar ou alterar somente:

```text
backend/Dockerfile
backend/.dockerignore
website_back/Dockerfile
website_back/.dockerignore
website_back/pom.xml
website_back/src/main/resources/application.properties
website_back/src/main/resources/application-prod.properties
website_back/src/main/java/com/baronesa/website/config/SecurityConfig.java
website_back/src/test/java/com/baronesa/website/**/*Health*Test.java
tools/docker/java_images_contract.py
tools/docker/tests/test_java_images_contract.py
ops/releases/components.yml
tools/releases/tests/test_catalog.py
docs/infrastructure/deployment/images/JAVA_IMAGES.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/implementation/slices/S08-dockerfiles-java-hardening-health.report.md
```

Se precisar alterar outro arquivo, nao altere. Registre bloqueio com path,
motivo e impacto.

## 12. Fora de escopo

Nao:

- alterar codigo funcional do backend ERP;
- alterar frontend, website_front ou WhatsApp;
- criar gateway, Compose ou Nginx;
- criar workflow GitHub Actions;
- criar ou alterar `release_control`;
- criar manifesto, release, tag ou deploy;
- acessar banco externo;
- configurar dados reais ou segredos;
- abrir PFX, HPROF, `.env.production` ou uploads;
- instalar ferramenta no host;
- autenticar em registry;
- acessar GitHub, GHCR, DNS ou VPS;
- fazer push de imagem;
- usar `git add`;
- criar commit, tag Git ou push;
- alterar esta task ou o tracker;
- executar prune ou limpeza Docker ampla.

## 13. Acesso externo permitido

Excepcionalmente nesta slice, somente para viabilizar builds locais:

- pull anonimo das bases declaradas no Docker Hub;
- download de dependencias Maven publicas exigidas pelos dois POMs;
- frontend BuildKit publico necessario a sintaxe do Dockerfile.

Registrar hosts acessados quando visiveis. Nenhum outro acesso externo esta
autorizado.

## 14. Estado protegido

Ao final registrar:

```bash
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f \
  \( -name '*.yml' -o -name '*.yaml' \) -print
find tools/docker tools/releases \
  \( -type d -name __pycache__ -o -type f -name '*.pyc' \) -print
docker ps --filter ancestor=abaronesa-emporio-backend:s08
docker ps --filter ancestor=abaronesa-emporio-website-backend:s08
```

Esperado:

- indice real vazio;
- `HEAD` inexistente;
- zero tags/reflog/workflow;
- zero cache Python;
- zero container de validacao em execucao;
- duas imagens locais `:s08`, sem push.

## 15. Relatorio obrigatorio

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S08-dockerfiles-java-hardening-health.report.md
```

Incluir:

1. arquivos alterados;
2. bases, tags, digests e plataforma;
3. contrato comum das imagens;
4. diferencas backend/website backend;
5. health do website e evidencia;
6. usuario, permissoes, packages e paths;
7. comandos Maven/Docker/Python, CWD e codigos;
8. asserts sanitizados de inspect/history;
9. tamanhos das imagens;
10. gates removidos e 18 restantes;
11. itens nao determinados;
12. acessos externos realizados;
13. escopo negativo;
14. estado Git, workflows, caches, imagens e containers;
15. bloqueios.

Nao reproduzir segredo, valor removido ou historico Docker integral.

Estado final:

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

Nao declarar `ACCEPTED`.

## 16. Criterios de aceite do orquestrador

A S08 somente podera ser aceita se:

- ambos os `mvn verify` passarem;
- validador Docker e seus mutantes passarem;
- ambos os Docker builds `linux/amd64` passarem;
- imagens usarem bases exatas com digest;
- runtime for Java 21 sem Maven/JDK;
- usuario final for nao-root;
- entrypoint, JVM options, portas e health forem corretos;
- backend provar ffmpeg, fontes, schemas e permissoes;
- website provar Actuator, seguranca e permissoes;
- nenhum segredo aparecer em imagem/history;
- Dockerfiles e `.dockerignore` estiverem documentados;
- somente os cinco gates comprovados forem removidos;
- backend mudar para `ready`;
- website backend permanecer `blocked` somente pela persistencia;
- readiness global continuar falhando fechado com 18 gates;
- nenhum componente fora dos dois Java for alterado;
- nao houver Compose, workflow, push, commit ou acesso nao autorizado;
- documentacao e implementacao permanecerem alinhadas.

A proxima slice prevista, apos aceite, sera a S09: imagens Node/frontends e
contratos de runtime de `frontend`, `website_front` e `whatsapp_service`.
