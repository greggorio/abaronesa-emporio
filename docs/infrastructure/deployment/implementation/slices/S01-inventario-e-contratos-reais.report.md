# S01 — Relatório de inventário e contratos reais

> **Estado informado pelo executor:** `IN_PROGRESS`
> **Revisão do orquestrador:** `ACCEPTED` em 28/07/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Data:** `2026-07-28`

---

## 1. Metadados da execução

| Campo                | Valor                                                                                  |
|----------------------|----------------------------------------------------------------------------------------|
| Executor             | CLI (Antigravity)                                                                      |
| Contrato             | `docs/infrastructure/deployment/implementation/slices/S01-inventario-e-contratos-reais.task.md` |
| Contrato arquitetural | `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`           |
| Relatório autorizado | `S01-inventario-e-contratos-reais.report.md`                                          |
| Diretório de trabalho | `/home/gregorio/git/baronesa/emporio`                                                |
| Início               | 2026-07-28T14:49:32−03:00                                                             |
| Arquivos alterados   | somente este relatório                                                                  |

---

## 2. Resumo executivo

O workspace `/home/gregorio/git/baronesa/emporio` contém os cinco componentes comerciais do projeto (backend, website\_back, frontend, website\_front, whatsapp\_service), além de artefatos de infraestrutura (Compose, Nginx, scripts, migrations), documentação e CI/CD existentes. O marcador `.git` não existe na raiz nem nos componentes, e `git rev-parse --show-toplevel` retorna código 128 por ausência de repositório Git. Existem dois workflows operacionais na raiz e dois workflows aninhados em componentes; os workflows de raiz publicam imagens Docker em GHCR por push em `main`, contrariando a arquitetura aprovada. O módulo `release_control` **não existe**. Valores literais sensíveis com aparência de credencial foram encontrados em arquivos candidatos ao primeiro commit; eles seriam enviados ao histórico caso ocorresse `git init` seguido de `git add .` sem saneamento. O entrypoint do `website_front` exige `VITE_VILLA_API_URL`, variável ausente nos Compose existentes, e aponta para `backend:8085` internamente, enquanto o website_back expõe `8085` — o serviço alvo está correto, porém o nome interno no entrypoint (`backend`) colide com o serviço `backend` ERP. A divergência de nomes `ERP_API_URL` vs. `ERP_API_BASE_URL` está presente entre os Compose e a aplicação.

---

## 3. Topologia Git

### 3.1 Estado da raiz

**Diretório das três verificações:** `/home/gregorio/git/baronesa/emporio`

```text
comando=ls -ld .git
código de saída=2
resultado=ls: cannot access '.git': No such file or directory

comando=find . -name .git -not -path '*/node_modules/*' -not -path '*/.ai-workflow/*' -print
código de saída=0
resultado=vazio

comando=git rev-parse --show-toplevel
código de saída=128
resultado=fatal: not a git repository (or any of the parent directories): .git
```

**Interpretação:** `.git` não existe; não há repositório Git na raiz ou nos componentes; `git rev-parse` falha com código 128 por ausência de repositório.

### 3.2 Remoto planejado

O remoto `git@github.com:greggorio/abaronesa-emporio.git` **ainda não foi configurado localmente**, confirmando o estado descrito no contrato arquitetural (`proposta-docker-ci-cd-producao-emporio.md:81`).

### 3.3 `.gitignore` por componente (sem `.gitignore` raiz)

| Componente           | `.gitignore` existe? | Cobre `.env`?                            |
|----------------------|----------------------|------------------------------------------|
| raiz `emporio/`      | **NÃO**              | —                                        |
| `backend/`           | sim                  | `.env.local`, `uploads/` — **não cobre `.env`** |
| `frontend/`          | sim                  | `.env.local`, `.env.*.local` — **não cobre `.env`** |
| `website_front/`     | sim                  | `.env.local`, `.env.*.local` — **não cobre `.env`** |
| `website_back/`      | sim                  | não menciona `.env`                      |
| `whatsapp_service/`  | sim                  | `node_modules/`                          |

**Risco imediato:** valores literais sensíveis com aparência de credencial estão em arquivos candidatos ao primeiro commit, mas não há evidência de versionamento Git operacional neste workspace. Sem saneamento e sem `.gitignore` na raiz, um futuro `git init` seguido de `git add .` os enviaria ao histórico.

### 3.4 Artefatos locais que exigirão regra de exclusão

```
backend/target/
backend/uploads/           # uploads reais de produção local
backend/relatorio_produtos.pdf
backend/relatorio_produtos.txt
backend/relatorio_produtos_layout.txt
backend/outputs/
backend/nfe/xmls/
frontend/.env              # arquivo .env com valor de VITE_BASE_API_URL
frontend/.quasar/
frontend/node_modules/
website_front/.env         # arquivo .env com variáveis de URL
website_front/node_modules/
website_back/target/
whatsapp_service/node_modules/
ops/env/.env.production    # RISCO CRÍTICO: valores literais sensíveis com aparência de credencial
backend/src/main/resources/application.properties  # RISCO CRÍTICO: valores literais sensíveis com aparência de credencial
website_back/src/main/resources/application.properties # RISCO: segredos potenciais
quality/e2e/**/.ai-workflow/evidence/
.ai-workflow/
.claude/
.opencode/
opencode.json
```

---

## 4. Matriz dos componentes

| Componente          | Estado     | Linguagem / Framework         | Java / Node       | Gerenciador  | Build file      | Comando dev      | Comando build prod     | Teste conhecido     | Artefato          | Dockerfile | Porta interna | Health check existente | Evidência                             |
|---------------------|------------|-------------------------------|-------------------|--------------|-----------------|------------------|------------------------|---------------------|-------------------|------------|---------------|------------------------|---------------------------------------|
| `backend`           | existente  | Java 21 / Spring Boot 3.3.5   | Java 21            | Maven        | `pom.xml`       | `mvn spring-boot:run` | `mvn clean package` | `mvn -B verify` (inferido) | `*.jar` (fat jar) | sim        | 8080          | `/actuator/health` (CONFIRMADO: `application-prod.properties:management.endpoints.web.exposure.include=health,info`) | `pom.xml:4`, `Dockerfile:1`, `application-prod.properties:31-32` |
| `website_back`      | existente  | Java 21 / Spring Boot 3.3.5   | Java 21            | Maven        | `pom.xml`       | `mvn spring-boot:run` | `mvn clean package` | `mvn -B verify` (inferido) | `*.jar` (fat jar) | sim        | 8085          | NAO DETERMINADO (não declarado no Dockerfile nem em properties encontradas) | `pom.xml:4`, `Dockerfile:21`, `application.properties:5` |
| `frontend`          | existente  | Node / Quasar 2 / Vue 3       | Node ^16/18/20     | npm          | `package.json`  | `quasar dev`     | `quasar build`         | `vitest run` (CONFIRMADO: `package.json:scripts.test`) | SPA em `dist/spa/` | sim | 80 | resposta HTTP local (inferido) | `package.json`, `Dockerfile`, `quasar.config.js` |
| `website_front`     | existente  | Node / React 18 / Vite / TypeScript | Node (não declarado engine) | npm | `package.json` | `vite` | `tsc && vite build` | nenhum script `test` definido (NAO DETERMINADO) | SPA em `dist/` | sim | 80 | resposta HTTP local (inferido) | `package.json`, `Dockerfile`, `vitest.config.ts` |
| `whatsapp_service`  | existente  | Node / Express 4 / whatsapp-web.js | Node 18 (Dockerfile: `node:18-bullseye`) | npm | `package.json` | `node index.js` | `node index.js` | nenhum script de teste (NAO DETERMINADO) | processo Node | sim | 3001 | `/status` (CONFIRMADO: `index.js:100`) |  `package.json`, `Dockerfile`, `index.js:10,100,194` |
| `gateway/deploy`    | prototipo  | Nginx                         | —                 | —            | —               | —                | —                      | —                   | container Nginx   | **NÃO** (sem Dockerfile próprio; usa `nginx:alpine` inline no Compose) | 80→8120 (previsto) | — | `deploy/docker-compose.yml:proxy` |
| `release_control`   | **ausente** | —                            | —                 | —            | —               | —                | —                      | —                   | —                 | **NÃO**    | —             | —                      | ausência confirmada: `ls release_control → EXIT:2` |

### Notas adicionais por componente

- **backend:** Dockerfile usa `-DskipTests` no build da imagem (`Dockerfile:6: RUN mvn clean package -DskipTests`). A arquitetura aprovada exige que testes passem em CI antes do build. O Dockerfile copia `nfe/schemas/` para `/home/gregorio/nfe/schemas/` — caminho hardcoded com `/home/gregorio/` (problema de portabilidade). O entrypoint Java padrão não consome `JAVA_OPTS`; o Compose define `JAVA_OPTS` mas o `ENTRYPOINT ["java", "-jar", "app.jar"]` não inclui `${JAVA_OPTS}`.
- **website_back:** Compose ops usa `ERP_API_BASE_URL: http://backend:8080`, mas a aplicação lê `erp.api.url=${ERP_API_URL:...}` — divergência confirmada de nome de variável.
- **website_back:** Uploads mapeados apenas em `./uploads:/app/uploads` no Compose antigo (deploy/docker-compose.emporio-website.yml); no novo Compose (deploy/docker-compose.yml) não há volume para uploads do website_back — dados persistentes em risco.
- **frontend:** Usa `window.RuntimeConfig.apiBaseUrl` em runtime via entrypoint, injetado via `VITE_BASE_API_URL`. Mecanismo funcional e correto.
- **website_front:** Entrypoint exige `VITE_VILLA_API_URL` mas o `.env.example` e `.env.production` do ops declaram `VITE_WEBSITE_API_URL`. O entrypoint também conecta em `http://backend:8085` para buscar tema — nome de serviço conflita com `backend` ERP (porta 8080); o serviço correto seria `website_back:8085`.
- **whatsapp_service:** Node 18 (Bullseye) — versão fora de suporte. Sem script de teste definido.

---

## 5. Matriz de configuração

| Variável                                        | Consumidor         | Origem atual                                         | Ambiente   | Sensibilidade | Divergência                                                                 | Evidência                                          |
|-------------------------------------------------|--------------------|------------------------------------------------------|------------|---------------|-----------------------------------------------------------------------------|----------------------------------------------------|
| `SPRING_DATASOURCE_URL`                         | backend, website_back | Compose (env)                                     | produção   | interna       | —                                                                           | `docker-compose.yml:31`, `docker-compose.emporio.yml:32` |
| `SPRING_DATASOURCE_USERNAME`                    | backend, website_back | Compose via `${DB_USER}`                          | produção   | sensível      | —                                                                           | ambos Compose                                      |
| `SPRING_DATASOURCE_PASSWORD`                    | backend, website_back | Compose via `${DB_PASSWORD}`                      | produção   | **sensível**  | —                                                                           | ambos Compose                                      |
| `DB_USER` / `DB_USERNAME`                       | Compose / website_back application.properties | `.env.production`, `.env.example` / aplicação | prod/dev | sensível | `website_back/application.properties` usa `${DB_USERNAME}` (com `NAME`), Compose usa `${DB_USER}` | `application.properties:22`, `docker-compose.emporio-website.yml:28` |
| `JAVA_OPTS`                                     | backend, website_back | Compose (env)                                     | produção   | interna       | **JAVA_OPTS definido no Compose, mas entrypoints Java não o consomem** — entrypoint é `["java", "-jar", "app.jar"]` sem `$JAVA_OPTS` | `backend/Dockerfile:27`, `ops/compose:44` |
| `ERP_BASE_URL`                                  | backend             | Compose / backend application.properties             | produção   | interna       | —                                                                           | `docker-compose.yml:48`, `application-prod.properties` |
| `APP_FRONTEND_URL`                              | backend             | Compose / application.properties                    | produção   | interna       | —                                                                           | `docker-compose.yml:47`                            |
| `WEBSITE_BASE_URL`                              | backend             | Compose / application.properties                    | produção   | interna       | —                                                                           | `docker-compose.yml:46`                            |
| `ECOMMERCE_BASE_URL` / `ANDROID_BASE_URL`       | backend             | Compose / application.properties                    | produção   | interna       | Ausentes no `.env.production` mais novo                                     | `ops/env/.env.example`                             |
| `ERP_API_URL`                                   | website_back        | Compose emporio-website `ERP_API_URL`               | produção   | interna       | **DIVERGÊNCIA:** deploy/docker-compose.yml usa `ERP_API_BASE_URL` (sem match com `${ERP_API_URL}`)   | `website_back/application.properties:73`, `deploy/docker-compose.yml:73`, `ops/compose/docker-compose.emporio-website.yml:37` |
| `INTEGRATION_SYSTEM_TOKEN_SECRET`               | backend, website_back | application.properties (literal sensível) / Compose via `${INTEGRATION_SYSTEM_TOKEN_SECRET}` | prod/dev | **CRÍTICO** | **Valor literal sensível com aparência de credencial em application.properties e website_back/application.properties** | ver Seção 12 |
| `UBER_CLIENT_ID` / `UBER_CLIENT_SECRET` / `UBER_CUSTOMER_ID` / `UBER_ACCESS_TOKEN` | backend, website_back | `application.properties` com `${NOME:default literal}` | prod/dev | **CRÍTICO** | Defaults literais embutidos nos dois backends; valores não transcritos | `backend/src/main/resources/application.properties:153-157`, `website_back/src/main/resources/application.properties:78-82` |
| `VITE_BASE_API_URL`                             | frontend            | `.env` / Compose env / entrypoint runtime            | build+runtime | pública  | —                                                                           | `frontend/.env`, `frontend/entrypoint.sh`          |
| `VITE_ERP_API_URL`                              | website_front       | `.env`, Compose via `VITE_ERP_API_URL`              | runtime    | pública       | entrypoint também exige `VITE_VILLA_API_URL` (nome legado)                  | `website_front/entrypoint.sh:12`, `ops/env/.env.production` |
| `VITE_WEBSITE_API_URL`                          | website_front       | `.env.example` e `.env.production`                  | runtime    | pública       | entrypoint usa `VITE_VILLA_API_URL` (incompatível)                          | `website_front/entrypoint.sh:16`, `ops/env/.env.example:VITE_WEBSITE_API_URL` |
| `VITE_SIGNAGE_TIMEZONE`                         | website_front       | `.env`                                              | runtime    | interna       | —                                                                           | `website_front/.env` (var names extraídos)         |
| `PUPPETEER_EXECUTABLE_PATH`                     | whatsapp_service    | Dockerfile (`ENV`)                                  | runtime    | interna       | —                                                                           | `whatsapp_service/Dockerfile:ENV`                  |
| `SESSION_DIR` / `PORT` / `BASE_COUNTRY_CODE`    | whatsapp_service    | Dockerfile (`ENV`) / index.js                       | runtime    | interna       | —                                                                           | `whatsapp_service/Dockerfile:ENV`, `index.js:10`  |
| `WHATSAPP_SERVICE_URL`                          | backend             | ops/compose/docker-compose.emporio.yml, ops/env/.env.production | produção | interna | —                                                                          | `ops/env/.env.production`, `ops/compose/docker-compose.emporio.yml:53` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`     | backend             | application.properties (hardcoded) / Compose/env   | prod       | **sensível**  | Valores literais sensíveis com aparência de credencial em `application.properties`; prod usa `${GOOGLE_CLIENT_ID:}` | ver Seção 12 |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI` | backend | application-prod.properties | produção | interna | URI aponta para domínio antigo (`espressoerp.smartdataerp.com.br`) | `application-prod.properties:32` |
| `REGISTRY` / `TAG`                             | Compose             | ops/env/.env.production                             | produção   | interna       | —                                                                           | `ops/env/.env.production`                          |
| `APP_REDEPLOY_ENABLED` / `APP_REDEPLOY_TRIGGER_PATH` | website_back | application.properties                         | ambos      | interna       | Recurso de redeploy automático via filesystem — incompatível com arquitetura | `website_back/application.properties:118-120`      |

### Propriedades Spring e profiles

- **backend:** profiles `dev` (padrão), `prod`, `test`. Perfil `prod` ativado via `SPRING_PROFILES_ACTIVE=prod` no Compose.
- **website_back:** profile único `application.properties`; ativação via `${SPRING_PROFILES_ACTIVE:dev}`.
- **backend `application.properties`:** referencia nome `spring.application.name=cafe-backend` (legado de projeto anterior — deve ser `emporio-backend`).

---

## 6. Dependências de build e runtime

| Origem              | Destino           | Tipo           | Contrato                                                    | Obrigatória | Evidência                                           |
|---------------------|-------------------|----------------|-------------------------------------------------------------|-------------|-----------------------------------------------------|
| `frontend` (ERP)    | `backend`         | HTTP REST      | `VITE_BASE_API_URL → backend:8080`; runtime-config.js      | sim         | `frontend/src/global.js`, `frontend/entrypoint.sh` |
| `website_front`     | `website_back`    | HTTP REST + SSE | `VITE_WEBSITE_API_URL → website_back:8085`; /api/, SSE /kds | sim        | `website_front/entrypoint.sh`, `website_back/KdsEventController.java` |
| `website_front`     | `backend`         | HTTP (busca tema) | entrypoint conecta em `http://backend:8085` (ERRO: deveria ser `website_back:8085`) | sim (com bug) | `website_front/entrypoint.sh:fetch_theme:INTERNAL_API_URL` |
| `website_back`      | `backend` (ERP)   | HTTP REST      | `erp.api.url=${ERP_API_URL}`; produtos, categorias, sync  | sim         | `website_back/application.properties:73`           |
| `backend` (ERP)     | `whatsapp_service` | HTTP REST     | `WHATSAPP_SERVICE_URL=http://whatsapp_service:3001`         | opcional    | `deploy/docker-compose.yml:53`, `backend/controller/WhatsAppController.java` |
| `backend`           | PostgreSQL (`emporio_erp_db`) | JDBC | Flyway migrations, JPA                                     | sim         | `deploy/docker-compose.yml:37`, `backend/pom.xml` |
| `website_back`      | PostgreSQL (`emporio_website_db`) | JDBC | Flyway migrations, JPA                                | sim         | `deploy/docker-compose.yml:62`, `website_back/pom.xml` |
| `website_front`     | Firebase          | SDK            | `firebase` dep em `package.json`; push notifications       | opcional    | `website_front/package.json:firebase` |
| `website_back`      | Firebase Admin    | SDK            | `firebase-admin` dep em `pom.xml`                          | opcional    | `website_back/pom.xml` |
| `backend`           | Google OAuth2     | OAuth2         | `spring-security-oauth2-client`                             | sim (autenticação) | `backend/pom.xml` |

### Grafo textual de dependências

```
frontend ──→ backend (ERP) ──→ PostgreSQL (emporio_erp_db)
                         └──→ whatsapp_service
                         └──→ Google OAuth2 (externo)

website_front ──→ website_back ──→ PostgreSQL (emporio_website_db)
              |               └──→ backend (ERP) [integração de produtos/categorias]
              |               └──→ Firebase Admin (externo)
              └──→ website_back [tema via http://backend:8085 — BUG: nome errado]
              └──→ Firebase SDK (externo, push notifications)
```

### Dependentes reversos

- **PostgreSQL:** consumido por `backend` (ERP) e `website_back`; bloqueante para ambos.
- **backend (ERP):** `website_back` depende dele para sincronização; `frontend` depende para todas as operações.
- **website_back:** `website_front` depende para todos os dados públicos e SSE.
- **whatsapp_service:** depende apenas de sessão persistente em volume; consumido opcionalmente pelo `backend`.

### Arquivos compartilhados entre componentes

- `ops/scripts/init-multiple-dbs.sh` é montado pelo Compose do PostgreSQL para criar ambos os bancos.
- `ops/compose/docker-compose.emporio.yml` e `deploy/docker-compose.yml` fazem referências cruzadas de contexto de build (`../backend`, `../website_back`, etc.) — o contexto de build ultrapassa a pasta do componente.

### Contratos que obrigam teste/promoção conjunta

- `backend` + `website_back`: compartilham `integration.system-token-secret` — devem usar o mesmo valor; mudança em um afeta o outro.
- `backend` + `frontend`: versão de API acoplada; mudanças de rota exigem compatibilidade.
- `website_back` + `website_front`: SSE e WebSocket (quiz); mudanças de protocolo exigem ambos.

---

## 7. Rascunho fundamentado do resolvedor (`ops/releases/components.yml`)

> Este rascunho é documental. O arquivo **não foi criado**.

```yaml
# RASCUNHO - ops/releases/components.yml
# Status de cada campo: CONFIRMADO | INFERIDO | NAO DETERMINADO

components:

  backend:
    id: backend                                        # CONFIRMADO: backend/pom.xml:artifactId
    paths:
      - backend/                                       # CONFIRMADO: Dockerfile, pom.xml
    image: ghcr.io/greggorio/abaronesa-emporio-backend # CONFIRMADO: proposta-docker-ci-cd:301
    build_cmd: mvn clean package -DskipTests           # CONFIRMADO: backend/Dockerfile:6 (DskipTests — deve mudar)
    test_cmd: mvn -B verify                            # INFERIDO: padrão Maven; não comprovado em CI local
    health_check: GET /actuator/health                 # CONFIRMADO: application-prod.properties:31
    port: 8080                                         # CONFIRMADO: application.properties:server.port
    migration: flyway                                  # CONFIRMADO: pom.xml, application.properties
    migration_location: backend/src/main/resources/db/migration/ # CONFIRMADO: application.properties
    persistence:
      - type: postgres
        db: emporio_erp_db                             # CONFIRMADO: deploy/docker-compose.yml:37
      - type: filesystem
        path: /app/uploads                             # CONFIRMADO: Dockerfile:21, pom.xml
        requires_volume: true
    depends_on:
      - postgres
    dependents: [website_back, frontend]

  website_back:
    id: website_back                                   # CONFIRMADO: website_back/pom.xml:artifactId
    paths:
      - website_back/                                  # CONFIRMADO
    image: ghcr.io/greggorio/abaronesa-emporio-website-backend # CONFIRMADO: proposta:302
    build_cmd: mvn clean package -DskipTests           # CONFIRMADO: website_back/Dockerfile:6
    test_cmd: mvn -B verify                            # INFERIDO
    health_check: NAO DETERMINADO                      # nenhum endpoint dedicado encontrado
    port: 8085                                         # CONFIRMADO: application.properties:5, Dockerfile:21
    migration: flyway                                  # CONFIRMADO: pom.xml, application.properties
    migration_location: website_back/src/main/resources/db/migration/ # CONFIRMADO
    persistence:
      - type: postgres
        db: emporio_website_db                         # CONFIRMADO: deploy/docker-compose.yml:62
      - type: filesystem
        path: /app/uploads                             # CONFIRMADO: Dockerfile:11-12
        requires_volume: true                          # RISCO: Compose recente não declara volume para este path
    depends_on:
      - postgres
      - backend
    dependents: [website_front]

  frontend:
    id: frontend                                       # CONFIRMADO: package.json:name=emporio-front
    paths:
      - frontend/                                      # CONFIRMADO
    image: ghcr.io/greggorio/abaronesa-emporio-frontend # CONFIRMADO: proposta:304
    build_cmd: quasar build                            # CONFIRMADO: package.json:scripts.build
    test_cmd: vitest run                               # CONFIRMADO: package.json:scripts.test
    health_check: HTTP 200 em /                        # INFERIDO: SPA Nginx
    port: 80                                           # CONFIRMADO: Dockerfile EXPOSE 80
    migration: nenhuma                                 # CONFIRMADO
    persistence: nenhuma                               # CONFIRMADO
    runtime_config: VITE_BASE_API_URL via entrypoint   # CONFIRMADO: entrypoint.sh, global.js
    depends_on:
      - backend
    dependents: []

  website_front:
    id: website_front                                  # CONFIRMADO: package.json:name=emporio-website-frontend
    paths:
      - website_front/                                 # CONFIRMADO
    image: ghcr.io/greggorio/abaronesa-emporio-website-frontend # CONFIRMADO: proposta:305
    build_cmd: tsc && vite build                       # CONFIRMADO: package.json:scripts.build
    test_cmd: NAO DETERMINADO                          # nenhum script test em package.json
    health_check: HTTP 200 em /                        # INFERIDO: SPA Nginx
    port: 80                                           # CONFIRMADO: Dockerfile EXPOSE 80
    migration: nenhuma                                 # CONFIRMADO
    persistence: nenhuma                               # CONFIRMADO
    runtime_config:
      - VITE_ERP_API_URL via entrypoint                # CONFIRMADO: entrypoint.sh:12
      - VITE_VILLA_API_URL (nome legado; exige renomear) # CONFIRMADO: entrypoint.sh:16 — BUG
    depends_on:
      - website_back
      - backend   # via entrypoint (busca tema) — BUG de nome
    dependents: []

  whatsapp_service:
    id: whatsapp_service                               # CONFIRMADO: package.json:name
    paths:
      - whatsapp_service/                              # CONFIRMADO
    image: ghcr.io/greggorio/abaronesa-emporio-whatsapp-service # CONFIRMADO: proposta:306
    build_cmd: N/A (sem build; node index.js)          # CONFIRMADO: package.json:scripts.start
    test_cmd: NAO DETERMINADO                          # sem script de teste
    health_check: GET /status                          # CONFIRMADO: index.js:100
    port: 3001                                         # CONFIRMADO: index.js:10, Dockerfile:EXPOSE 3001
    migration: nenhuma                                 # CONFIRMADO
    persistence:
      - type: filesystem
        path: /data/session                            # CONFIRMADO: Dockerfile:VOLUME ["/data"]
        requires_volume: true
    depends_on: []
    dependents: [backend]

  gateway:
    id: gateway                                        # INFERIDO: proposta define gateway/deploy
    paths:
      - deploy/nginx/                                  # CONFIRMADO: nginx configs existem
    image: ghcr.io/greggorio/abaronesa-emporio-gateway # CONFIRMADO: proposta:306
    build_cmd: NAO DETERMINADO                         # sem Dockerfile de gateway dedicado
    health_check: NAO DETERMINADO
    port: 8120 (loopback, previsto)                    # CONFIRMADO na proposta; não implementado
    depends_on:
      - backend
      - website_back
      - frontend
      - website_front
      - whatsapp_service
    dependents: []

  release_control:
    id: release_control                                # AUSENTE no workspace
    status: NAO DETERMINADO                            # módulo ainda não existe
    image: ghcr.io/greggorio/abaronesa-emporio-release-control # CONFIRMADO: proposta:857

  unresolved_path_strategy: fail-closed               # CONFIRMADO: proposta-docker-ci-cd:334
```

---

## 8. Banco, migrations e persistência

### 8.1 Backend ERP (`emporio_erp_db`)

| Campo                   | Valor / Estado                                                                     |
|-------------------------|------------------------------------------------------------------------------------|
| Banco                   | PostgreSQL (14 nos Compose antigos, 16-alpine no `deploy/docker-compose.yml`)      |
| Schema esperado         | `emporio_erp_db`                                                                   |
| Biblioteca de migration | Flyway (`flyway-core` + `flyway-database-postgresql` — `backend/pom.xml`)         |
| Local dos scripts       | `backend/src/main/resources/db/migration/`                                         |
| Quantidade de migrations | 50 arquivos SQL confirmados                                                        |
| Última migration visível | `V6__add_producao_propria_to_form.sql` (numeração curta), série `V20260xxx` mais recente | 
| Comportamento no startup | Flyway executa automaticamente (`spring.flyway.enabled=true`); `baseline-on-migrate=true` |
| Seeds                   | `ConfigSeeder.java` e `RootUserInitializer.java` existem (executados no startup)   |
| Rollback                | `spring.flyway.validate-on-migrate=true` presente; rollback de Flyway não configurado explicitamente — NAO DETERMINADO se migrations são backward-compatible |
| Uploads persistentes    | `/app/uploads` no container; volume nomeado `uploads_data` em `deploy/docker-compose.yml` |
| Risco de perda          | Uploads funcionam se volume estiver declarado; Compose antigos usam bind mount `./uploads:/app/uploads` — migração para volume nomeado exigirá atenção |

### 8.2 Website Backend (`emporio_website_db`)

| Campo                   | Valor / Estado                                                                     |
|-------------------------|------------------------------------------------------------------------------------|
| Banco                   | PostgreSQL (mesma instância, banco separado)                                       |
| Schema esperado         | `emporio_website_db`                                                               |
| Biblioteca de migration | Flyway (`flyway-core` em `website_back/pom.xml`)                                  |
| Local dos scripts       | `website_back/src/main/resources/db/migration/`                                   |
| Quantidade de migrations | 14 arquivos (V1 a V15, sem V13)                                                   |
| Última migration        | `V15__add_categoria_foto_ativo.sql`                                               |
| Comportamento no startup | Flyway automático; `DDL_AUTO:update` em dev (risco em produção)                   |
| Seeds                   | `V2__seed_data.sql` — seed via migration                                           |
| Rollback                | NAO DETERMINADO — falta verificação de backward-compatibility                     |
| Uploads persistentes    | `/app/uploads/galeria`, `/app/uploads/theme-assets` (Dockerfile:11-12); **sem volume declarado no Compose mais recente** — risco de perda ao recriar container |

### 8.3 WhatsApp session

- Sessão persistida em `/data/session` via volume Docker declarado em `whatsapp_service/Dockerfile` (`VOLUME ["/data"]`).
- `deploy/docker-compose.yml` declara volume nomeado `whatsapp_session:/data` — CONFIRMADO.

### 8.4 Inicialização dos bancos

- `ops/scripts/init-multiple-dbs.sh` cria `emporio_erp_db` e `emporio_website_db` no PostgreSQL via `POSTGRES_MULTIPLE_DATABASES` (declarado em `deploy/docker-compose.yml:db.environment`).
- O hook `docker-entrypoint-initdb.d` normalmente executa apenas na primeira inicialização do volume, mas isso não torna o script idempotente. `create_user_and_database()` executa `CREATE DATABASE` incondicionalmente (`ops/scripts/init-multiple-dbs.sh:5-11`); uma reexecução contra bancos existentes falharia. Registrar separadamente: execução normalmente limitada ao primeiro bootstrap do volume; script não idempotente se reexecutado.

---

## 9. Inventário Docker/Deploy

### 9.1 Dockerfiles

| Arquivo                          | Classificação     | Base (build)             | Base (prod)              | Notas                                                                                     |
|----------------------------------|-------------------|--------------------------|--------------------------|-------------------------------------------------------------------------------------------|
| `backend/Dockerfile`             | CANONICO_ATUAL    | `maven:3.9-eclipse-temurin-21` | `eclipse-temurin:21-jre-alpine` | `-DskipTests`; ENTRYPOINT sem `JAVA_OPTS`; path `/home/gregorio/nfe/` hardcoded |
| `website_back/Dockerfile`        | CANONICO_ATUAL    | `maven:3.9-eclipse-temurin-21` | `eclipse-temurin:21-jre-alpine` | `-DskipTests`; EXPOSE 8085; sem health check |
| `frontend/Dockerfile`            | CANONICO_ATUAL    | `node:18-alpine`         | `nginx:stable-alpine`    | Node 18 (fora de suporte); build via `quasar build` |
| `website_front/Dockerfile`       | CANONICO_ATUAL    | `node:18-alpine`         | `nginx:stable-alpine`    | Node 18 (fora de suporte); deps nativas (canvas); build via `tsc && vite build` |
| `whatsapp_service/Dockerfile`    | CANONICO_ATUAL    | `node:18-bullseye`       | (mesmo)                  | Node 18 (fora de suporte); Chromium instalado via `apt-get` |
| Gateway (sem Dockerfile próprio) | NAO_DETERMINADO  | —                        | —                        | Compose usa `nginx:alpine` inline sem Dockerfile versionado |

### 9.2 Docker Compose

| Arquivo                                         | Classificação  | Serviços                                        | Notas                                                                                                   |
|-------------------------------------------------|----------------|-------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `deploy/docker-compose.yml`                     | PROTOTIPO      | db, backend, website_back, whatsapp, frontend, website_front, proxy | Mais completo. Usa `build:` (proibido em prod). Proxy ocupa 80/443 (contradiz arquitetura). Sem health checks nos serviços principais. |
| `ops/compose/docker-compose.emporio.yml`        | PROTOTIPO      | db, backend, frontend                           | Compose apenas para ERP (sem website). Usa `build:`. Publica PostgreSQL via `${DB_HOST_PORT}:5432` (proibido em prod). |
| `ops/compose/docker-compose.emporio-website.yml`| PROTOTIPO      | db, backend (website_back), frontend (website_front) | Usa `build:`. Publica PostgreSQL. Volume `/app/redeploy` — mecanismo de redeploy automático incompatível. |
| `deploy/infra/docker-compose.yml`               | LEGADO         | traefik                                         | Monta Docker socket (`/var/run/docker.sock`) — proibido pela arquitetura aprovada. Usa `traefik:v2.10`. |

### 9.3 Scripts de deploy

| Arquivo                        | Classificação  | Função                                                                                    |
|--------------------------------|----------------|-------------------------------------------------------------------------------------------|
| `ops/deploy/deploy-tenant.sh`  | PROTOTIPO      | Deploy genérico para tenant; conecta via VPS; valida saúde de `backend:8080` e `frontend:80`; verifica apenas `erp-backend` e `erp-frontend` (nomes de imagem incompatíveis com arquitetura) |

### 9.4 Configurações Nginx

| Arquivo                                          | Classificação  | Notas                                                                                          |
|--------------------------------------------------|----------------|------------------------------------------------------------------------------------------------|
| `deploy/nginx/nginx.conf`                        | PROTOTIPO      | Configuração do host; não referencia domínios finais                                           |
| `deploy/nginx/conf.d/emporio.conf`               | PROTOTIPO      | Mais próximo da arquitetura final; roteia `emporio.abaronesa.net.br` e `erp-emporio.abaronesa.net.br`; usa nomes de serviço Docker — pressupõe container proxy, não Nginx do host |
| `deploy/nginx/emporio-erp.conf.template`         | PROTOTIPO      | Template com `SEU_DOMINIO`; aponta para `127.0.0.1:8080` (sem gateway)                       |
| `deploy/nginx/emporio-website.conf.template`     | PROTOTIPO      | Template com `SEU_DOMINIO`; aponta para `127.0.0.1:8085` (sem gateway)                       |
| `website_front/nginx.conf`                       | CANONICO_ATUAL | Configuração interna do SPA; serve corretamente `try_files $uri /index.html`                  |

### 9.5 Entrypoints

| Arquivo                       | Consumidor      | Funcionalidade                                         |
|-------------------------------|-----------------|--------------------------------------------------------|
| `frontend/entrypoint.sh`      | frontend        | Injeta `VITE_BASE_API_URL` em `runtime-config.js`     |
| `website_front/entrypoint.sh` | website_front   | Injeta múltiplas variáveis; busca tema do backend; exige `VITE_VILLA_API_URL` (nome legado) |

### 9.6 Volumes declarados no Compose mais recente (`deploy/docker-compose.yml`)

```
postgres_data    → PostgreSQL dados
whatsapp_session → /data (sessão WhatsApp)
uploads_data     → backend/uploads (/app/uploads)
```

**Ausente:** volume para `website_back/uploads`.

### 9.7 Networks

- `deploy/docker-compose.yml`: rede `emporio-net` (bridge) — todos os serviços na mesma rede.
- `ops/compose/docker-compose.emporio.yml`: rede `emporio_erp_net`.
- `ops/compose/docker-compose.emporio-website.yml`: rede `emporio_website_net`.

### 9.8 Nomes de imagem e tags

| Componente       | Compose antigo (`ops/compose`)                         | Compose mais recente (`deploy/docker-compose.yml`)               | Arquitetura aprovada                                                |
|------------------|--------------------------------------------------------|------------------------------------------------------------------|---------------------------------------------------------------------|
| backend          | `${BACKEND_IMAGE}` (livre)                             | `${REGISTRY:-ghcr.io/baronesa}/emporio-backend:${TAG:-latest}`  | `ghcr.io/greggorio/abaronesa-emporio-backend@sha256:<digest>`       |
| website_back     | `${BACKEND_IMAGE}` (livre)                             | `${REGISTRY:-ghcr.io/baronesa}/emporio-website-backend:${TAG:-latest}` | `ghcr.io/greggorio/abaronesa-emporio-website-backend@sha256:<digest>` |
| frontend         | `${FRONTEND_IMAGE}` (livre)                            | `${REGISTRY:-ghcr.io/baronesa}/emporio-frontend:${TAG:-latest}` | `ghcr.io/greggorio/abaronesa-emporio-frontend@sha256:<digest>`      |
| website_front    | `${FRONTEND_IMAGE}` (livre)                            | `${REGISTRY:-ghcr.io/baronesa}/emporio-website-frontend:${TAG:-latest}` | `ghcr.io/greggorio/abaronesa-emporio-website-frontend@sha256:<digest>` |
| whatsapp_service | — (não no Compose antigo)                             | `${REGISTRY:-ghcr.io/baronesa}/emporio-whatsapp-service:${TAG:-latest}` | `ghcr.io/greggorio/abaronesa-emporio-whatsapp-service@sha256:<digest>` |

**Divergências:** namespace `ghcr.io/baronesa` vs. `ghcr.io/greggorio` (arquitetura define `greggorio`); uso de `latest` proibido em produção.

---

## 10. Inventário CI/CD

### 10.1 Workflow `main.yml`

| Campo                    | Valor                                                                                      |
|--------------------------|--------------------------------------------------------------------------------------------|
| Caminho                  | `.github/workflows/main.yml`                                                               |
| Gatilhos                 | `push: branches: [main, master]`, `tags: ['v*']`                                          |
| Componentes cobertos     | `backend`, `frontend` — **NÃO cobre website_back, website_front, whatsapp_service**       |
| Testes                   | **Nenhum** — vai direto para build e push                                                  |
| Build e push             | sim — `docker/build-push-action@v5`                                                       |
| Registry e tags          | `ghcr.io/${{ github.repository_owner }}/erp-backend`, tags semver + branch + sha          |
| Secrets referenciados    | `GITHUB_TOKEN` (implícito via `permissions: packages: write`)                              |
| Acesso SSH               | não                                                                                        |
| Diretório remoto         | não aplicável                                                                              |
| Concorrência             | não configurada                                                                            |
| Environment GitHub       | não configurado                                                                            |
| Rollback                 | nenhum                                                                                     |
| **Erro de expressão Actions** | **`tags: ${{ id.meta-backend.outputs.tags }}`** — referência a contexto inexistente; deve ser `steps.meta-backend.outputs.tags` (linhas 46 e 64). O YAML é estruturalmente legível; o problema é avaliação da expressão pelo GitHub Actions. |
| Sobreposição             | **CONFLITANTE com `deploy.yml`** — ambos disparam em push para `main`                    |

### 10.2 Workflow `deploy.yml`

| Campo                    | Valor                                                                                      |
|--------------------------|--------------------------------------------------------------------------------------------|
| Caminho                  | `.github/workflows/deploy.yml`                                                             |
| Gatilhos                 | `push: branches: [main, master]`                                                           |
| Componentes cobertos     | backend, website_back, frontend, website_front, whatsapp_service                          |
| Testes                   | **Nenhum** — vai direto para build e push                                                  |
| Build e push             | sim — usa `latest` como única tag                                                         |
| Registry e tags          | `ghcr.io/${{ github.repository_owner }}/emporio-*:latest`                                |
| Secrets referenciados    | `GITHUB_TOKEN`, `VPS_SSH_KEY`                                                             |
| Acesso SSH               | **sim — acesso SSH como `root` em `31.97.251.16`** (contradiz arquitetura aprovada)       |
| Diretório remoto         | `/opt/sistemas/emporio`                                                                    |
| Concorrência             | não configurada                                                                            |
| Environment GitHub       | não configurado                                                                            |
| Rollback                 | nenhum                                                                                     |
| Erros sintáticos         | nenhum sintático, mas lógica `docker compose pull && up -d` sem validações                |
| Sobreposição             | **CONFLITANTE com `main.yml`** — dispara simultaneamente no mesmo push                   |

### 10.3 Workflows aninhados e cache

| Caminho | Classificação | Evidência e interpretação |
|---|---|---|
| `backend/.github/workflows/backend.yml` | legado/inerte no monorepo futuro | Workflow encontrado no componente, com build de imagem e contexto `.` (`backend/.github/workflows/backend.yml:1-46`). GitHub Actions descobrirá workflows somente sob `.github/workflows/` da raiz quando `emporio/` for o monorepo; este arquivo só voltaria a ser operacional se `backend/` fosse repositório independente. |
| `frontend/.github/workflows/frontend.yml` | legado/inerte no monorepo futuro | Workflow encontrado no componente, com filtro `frontend/**` e build de imagem (`frontend/.github/workflows/frontend.yml:1-48`). Pela mesma regra, não será descoberto na raiz do monorepo; só seria operacional em repositório independente. |
| `.ai-workflow/opencode/xdg/cache/.bun/install/cache/opencode-anthropic-auth@0.0.13@@@1/.github/workflows/publish.yml` | dependência/cache gerado; fora do inventário operacional | Está sob cache `.ai-workflow/`, não sob o `.github/workflows/` operacional da raiz (`.../.github/workflows/publish.yml:1-31`). Deve ser excluído do inventário operacional e de eventual commit por regra de exclusão. |
| `frontend/.ai-workflow/opencode/xdg/cache/.bun/install/cache/opencode-anthropic-auth@0.0.13@@@1/.github/workflows/publish.yml` | dependência/cache gerado; fora do inventário operacional | Está sob cache aninhado do frontend, portanto também não é workflow operacional do monorepo (`.../.github/workflows/publish.yml:1-31`). |

### 10.4 Divergências CI/CD críticas com a arquitetura aprovada

1. **Deploy automático em push para `main`** — `deploy.yml` executa deploy em produção a cada push, contrariando `proposta-docker-ci-cd:44`.
2. **SSH como root** — `deploy.yml` usa `username: root` — contraria `proposta-docker-ci-cd:676`.
3. **Sem testes antes do build** — ambos os workflows publicam imagens sem nenhuma validação.
4. **Uso exclusivo de `latest`** — `deploy.yml` usa `latest`, proibido pela arquitetura.
5. **Workflows conflitantes e concorrentes** — `main.yml` e `deploy.yml` disparam no mesmo evento; `main.yml` contém erro de avaliação de expressão Actions que o torna ineficaz nessa etapa, não erro sintático YAML.
6. **Sem mecanismo de concorrência** — sem `concurrency.group`, dois pushes rápidos geram dois workflows simultâneos.
7. **Sem environment GitHub** — sem `environment: production`, sem isolamento de secrets.

---

## 11. Pontos de integração do `release_control`

### 11.1 Frontend administrativo (ERP — `frontend/`)

| Ponto                    | Localização                                                    | Estado              |
|--------------------------|----------------------------------------------------------------|---------------------|
| Definição de rotas       | `frontend/src/router/routes.js`                                | CONFIRMADO          |
| Rota de configurações    | `/configuracoes` → `PainelControle.vue`                        | CONFIRMADO          |
| Rota prevista (publisher)| `/configuracoes/releases` — **NÃO EXISTE**                     | AUSENTE             |
| Rota prevista (deployer) | `/configuracoes/atualizacao-sistema` — **NÃO EXISTE**          | AUSENTE             |
| Menu e visibilidade      | `SideNavigation.vue` — controla itens por autenticação; sem controle por papel/modo | PARCIAL |
| Cliente HTTP             | `frontend/src/boot/axios.js` — instância Axios com `baseURL: baseApiUrl` | CONFIRMADO |
| Autenticação             | JWT via `Authorization: Bearer` + Google OAuth2               | CONFIRMADO: `SecurityConfig.java:125` |
| Papel ADMIN              | `/api/admin/**` protegido com `hasAnyRole("ADMIN", "SYSTEM")` | CONFIRMADO: `SecurityConfig.java:125` |
| Operações assíncronas    | SSE em `EventsController.java`; sem componentes de progresso para deploy | NAO DETERMINADO |
| Tela de versões anterior | **Nenhuma encontrada**                                         | AUSENTE             |

### 11.2 Backend ERP (`backend/`)

| Ponto                      | Localização                                               | Estado              |
|----------------------------|-----------------------------------------------------------|---------------------|
| Modelo de usuário          | `UserDTO.java`, `UserSummary.java`, `UserPrincipal.java`  | CONFIRMADO          |
| Papéis de segurança        | `ROLE_ADMIN`, `ROLE_SYSTEM` detectados                    | CONFIRMADO          |
| Padrão de endpoints admin  | `/api/admin/**` com `hasAnyRole("ADMIN","SYSTEM")`        | CONFIRMADO          |
| Jobs/Auditoria             | `V20251225001000__create_job_tables.sql` — tabelas de job existem | CONFIRMADO |
| SSE existente              | `SseEventsService.java`, `EventsController.java`          | CONFIRMADO          |
| WebSocket                  | `PrintWebSocketHandler.java`, `WebSocketConfig.java`      | CONFIRMADO          |
| Actuator/Observabilidade   | `/actuator/health` + `/actuator/info` expostos em prod    | CONFIRMADO          |
| Configuração por ambiente  | profiles dev/prod via `spring.profiles.active`            | CONFIRMADO          |

### 11.3 Website Backend (`website_back/`)

| Ponto                    | Localização                                               | Estado              |
|--------------------------|-----------------------------------------------------------|---------------------|
| SSE                      | `KdsEventService.java`, `KdsEventController.java`         | CONFIRMADO          |
| WebSocket (STOMP)        | `QuizWebSocketController.java`                            | CONFIRMADO          |
| Health                   | **sem endpoint dedicado detectado**                       | NAO DETERMINADO     |

### 11.4 Pontos reais de extensão para `release_control`

1. **Axios singleton** em `frontend/src/boot/axios.js` — ponto de extensão para adicionar cliente de release API sem duplicar configuração.
2. **Rota `/configuracoes`** com `PainelControle.vue` — ponto natural de inserção das novas rotas `/configuracoes/releases` e `/configuracoes/atualizacao-sistema`.
3. **`SideNavigation.vue`** — menu existente que pode receber visibilidade condicional por papel/modo do `release_control`.
4. **`/api/admin/**`** com `ROLE_ADMIN` — padrão de segurança do ERP que pode servir apenas como referência. Registrar endpoints do `release_control` no backend comercial não está autorizado nesta S01 e contradiz a separação operacional aprovada; eventual API deverá pertencer ao módulo/plano de controle separado.
5. **Tabelas de job** (`V20251225001000__create_job_tables.sql`) — podem servir apenas como referência de auditoria. Persistir o estado do `release_control` no banco ERP seria contraditório à separação operacional aprovada e não é uma conclusão desta S01.
6. **SSE em `SseEventsService.java`** — padrão do ERP que pode servir como referência para acompanhamento de workflows; não implica compartilhar endpoint, estado ou banco com o `release_control`.
7. **Separação prevista** (`/opt/sistemas/emporio/` vs. `/opt/sistemas/emporio-control/`) não tem contrapartida no código atual — `release_control` inicia do zero, fora do backend comercial e do banco comercial.

---

## 12. Riscos de segurança (sem valores)

### 12.1 Valores literais sensíveis com aparência de credencial

```
RISCO: backend/src/main/resources/application.properties
CAMPO: integration.system-token-secret
VALOR: REDACTED
ACAO FUTURA: remover do repositório antes do primeiro commit; rotacionar imediatamente

RISCO: backend/src/main/resources/application.properties
CAMPO: spring.security.oauth2.client.registration.google.client-id
VALOR: REDACTED
ACAO FUTURA: remover; usar apenas variável de ambiente; rotacionar

RISCO: backend/src/main/resources/application.properties
CAMPO: spring.security.oauth2.client.registration.google.client-secret
VALOR: REDACTED
ACAO FUTURA: remover; usar apenas variável de ambiente; rotacionar

RISCO: website_back/src/main/resources/application.properties
CAMPO: integration.system-token-secret
VALOR: REDACTED (mesmo valor do backend ERP confirmado pelo padrão)
ACAO FUTURA: remover; nunca versionar; usar variável de ambiente injetada

RISCO: backend/src/main/resources/application.properties e website_back/src/main/resources/application.properties
CAMPO: uber.client-id, uber.client-secret, uber.customer-id, uber.access-token
FORMATO: `${UBER_*:default literal}`; VALOR: REDACTED
ACAO FUTURA: remover e rotacionar; usar variáveis de ambiente

RISCO: ops/env/.env.production
CAMPO: vários (14 pares chave=valor, 770 bytes) — valores literais sensíveis com aparência de credenciais
VALOR: REDACTED
ACAO FUTURA: não versionar; mover para vault ou segredo de CI; arquivo deve ser excluído do histórico inicial

RISCO: frontend/.env
CAMPO: VITE_BASE_API_URL
VALOR: REDACTED (URL; pode expor endpoint interno)
ACAO FUTURA: adicionar .env ao .gitignore do frontend antes do commit

RISCO: website_front/.env
CAMPO: VITE_ERP_API_URL, VITE_WEBSITE_API_URL, VITE_SIGNAGE_TIMEZONE
VALOR: REDACTED
ACAO FUTURA: adicionar .env ao .gitignore do website_front antes do commit
```

### 12.2 Outros riscos

- **Sem `.gitignore` na raiz:** os valores literais sensíveis com aparência de credencial não têm evidência de versionamento Git operacional, mas um futuro `git init` seguido de `git add .` os exporia ao histórico.
- **`deploy/infra/docker-compose.yml` monta Docker socket** (`/var/run/docker.sock`) — proibido pela arquitetura; não deve ser usado.
- **SSH como root** em `deploy.yml` (secret `VPS_SSH_KEY`) — credencial armazenada como GitHub secret; deve ser substituída por usuário dedicado.
- **`backend/src/main/resources/application-prod.properties`** referencia domínio legado `espressoerp.smartdataerp.com.br` — deve ser corrigido antes do primeiro deploy real.
- **`spring.application.name=cafe-backend`** em `application.properties` — nome de projeto anterior; pode causar confusão em métricas/logs.

---

## 13. Confronto com a arquitetura aprovada

### CONFIRMADO PELO CÓDIGO

- Cinco componentes comerciais existem: `backend`, `website_back`, `frontend`, `website_front`, `whatsapp_service` (`deploy/docker-compose.yml`)
- Java 21 em backend e website_back (`pom.xml` de ambos)
- Flyway como mecanismo de migration nos dois backends
- Spring Boot Actuator presente no `backend` com endpoint `/actuator/health`
- Dois bancos PostgreSQL (`emporio_erp_db`, `emporio_website_db`) com usuário único (pendente de separação)
- Dockerfiles multi-stage por componente (exceto gateway)
- Uso de GHCR como registry (parcialmente: namespace incorreto em alguns Compose)
- Rede Docker privada (`emporio-net`)
- Volumes para dados persistentes (postgres, uploads, whatsapp session — parcialmente)
- Nginx do `website_front` serve SPA corretamente
- `frontend` usa mecanismo de runtime-config via entrypoint para injetar URL em runtime
- Configuração de WebSocket no backend (`WebSocketConfig.java`)
- SSE implementado no backend (`SseEventsService.java`)
- Roles ADMIN/SYSTEM no backend para endpoints administrativos
- `website_back` integra com `backend` ERP via HTTP (`erp.api.url`)

### CONTRADITO PELO CÓDIGO

1. **Deploy automático em push para `main`** — `deploy.yml` executa deploy a cada push (`proposta-docker-ci-cd:44,985`)
2. **SSH como root** — `deploy.yml:username: root` (`proposta-docker-ci-cd:676`)
3. **Uso de `latest` como referência de release** — `deploy.yml` e todos os Compose existentes usam `latest` (`proposta-docker-ci-cd:44,64,395`)
4. **Container proxy ocupando 80/443** — `deploy/docker-compose.yml:proxy.ports: 80:80, 443:443` (`proposta-docker-ci-cd:61`)
5. **Workflows duplicados e concorrentes** — `main.yml` e `deploy.yml` disputam o mesmo gatilho (`proposta-docker-ci-cd:1004`)
6. **`docker-compose.yml` contém `build:`** — todos os Compose existentes contêm `build:` (`proposta-docker-ci-cd:67,363`)
7. **Credencial administrativa única para os dois bancos** — `deploy/docker-compose.yml` usa `${DB_USER}` para ambos os bancos (`proposta-docker-ci-cd:68`)
8. **`JAVA_OPTS` definido mas não consumido** — entrypoints Java não incluem `${JAVA_OPTS}` (`proposta-docker-ci-cd:77`)
9. **Nodes 18 nos Dockerfiles** — Node 18 fora de suporte; arquitetura exige Node 24 LTS (`proposta-docker-ci-cd:439`)
10. **Namespace GHCR inconsistente** — Compose usa `ghcr.io/baronesa`, arquitetura define `ghcr.io/greggorio` (`proposta-docker-ci-cd:975`)
11. **`website_back` sem health check definido** — arquitetura exige health check para todos os serviços (`proposta-docker-ci-cd:369,1024`)
12. **Volume ausente para uploads do `website_back`** — Compose mais recente não declara volume para `/app/uploads` do website_back (`proposta-docker-ci-cd:75`)
13. **`website_front/entrypoint.sh` exige `VITE_VILLA_API_URL`** — variável renomeada para `VITE_WEBSITE_API_URL` no `.env.production`; incompatibilidade real (`proposta-docker-ci-cd:73`)
14. **Entrypoint do `website_front` conecta em `http://backend:8085`** — nome de serviço errado (deveria ser `website_back`); `backend` ERP usa porta 8080 (`proposta-docker-ci-cd:73`)
15. **Valores literais sensíveis com aparência de credencial em arquivos candidatos ao primeiro commit** — sem validação externa de validade; `application.properties` contém esses valores (`proposta-docker-ci-cd:806-831`)
16. **`release_control` ausente** — módulo de plano de controle não existe (`proposta-docker-ci-cd:160-286`)
17. **Gateway sem Dockerfile versionado** — arquitetura prevê `ops/gateway/Dockerfile` (`proposta-docker-ci-cd:941`)
18. **`deploy-tenant.sh` verifica apenas backend/frontend** (ignora website_back, website_front, whatsapp_service)
19. **`website_back/application.properties` declara `app.redeploy.enabled`** — mecanismo de redeploy via filesystem incompatível com arquitetura

### AINDA NÃO VERIFICÁVEL

- Compatibilidade de rollback das migrations (requer análise SQL de cada V*.sql)
- Política de backup e restore
- Usuário dedicado `deploy-emporio` na VPS (não acessamos VPS)
- Estado real do diretório `/opt/sistemas/emporio` na VPS
- Portas `8120` e `8121` livres na VPS
- Imagens já presentes no GHCR
- Certificados TLS no host
- `release_control`: tecnologia, banco e persistência mínima
- Compatibilidade do `website_front` com Node 24 LTS

---

## 14. Decisões ainda necessárias

1. **Saneamento de segredos** antes de qualquer `git init` ou `git add` — prioridade máxima.
2. **`.gitignore` raiz** cobrindo ao menos: `**/.env`, `**/target/`, `**/node_modules/`, `**/.quasar/`, `ops/env/.env.production`, `backend/uploads/`, `backend/outputs/`, `backend/nfe/xmls/`, `backend/relatorio_*`, `quality/**/.ai-workflow/`, `.ai-workflow/`, `.claude/`, `.opencode/`.
3. **Renomear `VITE_VILLA_API_URL`** → `VITE_WEBSITE_API_URL` em `website_front/entrypoint.sh` (ou atualizar `.env.production`).
4. **Corrigir `http://backend:8085`** → `http://website_back:8085` em `website_front/entrypoint.sh:INTERNAL_API_URL`.
5. **Adicionar `${JAVA_OPTS}`** aos entrypoints Java dos Dockerfiles de backend e website_back.
6. **Remover `build:`** de todos os Compose que serão usados em produção; criar Compose de produção sem `build:`.
7. **Definir namespace canônico** do GHCR (`ghcr.io/greggorio`) em todos os Compose e workflows.
8. **Desabilitar `deploy.yml`** ou reescrevê-lo completamente — ele contradiz múltiplos pontos da arquitetura.
9. **Corrigir erro de avaliação de expressão em `main.yml:46,64`**: `${{ id.meta-backend.outputs.tags }}` → `${{ steps.meta-backend.outputs.tags }}`.
10. **Adicionar health check ao `website_back`** (nenhum encontrado).
11. **Adicionar volume ao `website_back/uploads`** no Compose de produção.
12. **Decidir sobre `spring.application.name=cafe-backend`** (legado) — deveria ser `emporio-backend` ou valor descritivo.
13. **Atualizar URL do OAuth2 redirect** de `espressoerp.smartdataerp.com.br` para `erp-emporio.abaronesa.net.br`.
14. **Estratégia de usuários separados** no PostgreSQL (usuário de aplicação por banco, usuário admin separado).
15. **Tecnologia do `release_control`** — decisão pendente conforme `proposta-docker-ci-cd:996`.
16. **Atualização de Node** para 24 LTS nos três Dockerfiles Node (validar compatibilidade de whatsapp-web.js, Quasar e Vite).

---

## 15. Comandos executados e códigos de saída

| # | Comando (resumido)                                                      | CWD                | Código de saída | Resultado resumido                                                    |
|---|-------------------------------------------------------------------------|--------------------|-----------------|-----------------------------------------------------------------------|
| 1 | `find . -name ".git" -maxdepth 3`                                       | emporio/           | 0               | registro anterior inconsistente; invalidado pelas verificações 63–65 |
| 2 | `find . -name ".git" -not -path "*/node_modules/*"`                     | emporio/           | 0               | registro anterior inconsistente; invalidado pelas verificações 63–65 |
| 3 | `find . -name "Dockerfile*" -not -path "*/node_modules/*" ...`          | emporio/           | 0               | 5 Dockerfiles encontrados                                             |
| 4 | `find . -name "docker-compose*.yml" -o -name "compose*.yml" ...`        | emporio/           | 0               | 4 arquivos Compose                                                    |
| 5 | `find .github -type f`                                                   | emporio/           | 0               | 2 workflows: `deploy.yml`, `main.yml`                                 |
| 6 | `cat .github/workflows/main.yml`                                        | emporio/           | 0               | Workflow lido                                                         |
| 7 | `cat .github/workflows/deploy.yml`                                      | emporio/           | 0               | Workflow lido                                                         |
| 8 | `cat backend/pom.xml \| grep ...`                                        | emporio/           | 0               | Java 21, Spring Boot 3.3.5, Flyway confirmados                       |
| 9 | `cat frontend/package.json`                                             | emporio/           | 0               | Node ^16/18/20, Quasar 2, Vue 3, vitest                              |
| 10 | `cat website_back/pom.xml \| grep ...`                                  | emporio/           | 0               | Java 21, Spring Boot 3.3.5, Firebase Admin                           |
| 11 | `cat website_front/package.json`                                        | emporio/           | 0               | React 18, Vite, TypeScript, Radix UI, shadcn/ui                      |
| 12 | `cat whatsapp_service/package.json`                                     | emporio/           | 0               | Express 4, whatsapp-web.js 1.34.2                                    |
| 13 | `find ops -type f`                                                       | emporio/           | 0               | 11 arquivos em ops/                                                   |
| 14 | `find deploy -type f`                                                   | emporio/           | 0               | 6 arquivos em deploy/                                                 |
| 15 | `cat backend/Dockerfile`                                                | emporio/           | 0               | Lido — confirma -DskipTests, path hardcoded                          |
| 16 | `cat website_back/Dockerfile`                                           | emporio/           | 0               | Lido — EXPOSE 8085, -DskipTests                                      |
| 17 | `cat frontend/Dockerfile`                                               | emporio/           | 0               | Lido — node:18-alpine, quasar build                                  |
| 18 | `cat website_front/Dockerfile`                                          | emporio/           | 0               | Lido — node:18-alpine, native deps, vite build                       |
| 19 | `cat whatsapp_service/Dockerfile`                                       | emporio/           | 0               | Lido — node:18-bullseye, Chromium, PORT 3001                         |
| 20 | `cat ops/compose/docker-compose.emporio.yml`                            | emporio/           | 0               | Lido                                                                  |
| 21 | `cat ops/compose/docker-compose.emporio-website.yml`                    | emporio/           | 0               | Lido                                                                  |
| 22 | `cat deploy/docker-compose.yml`                                         | emporio/           | 0               | Lido — mais completo                                                  |
| 23 | `cat deploy/infra/docker-compose.yml`                                   | emporio/           | 0               | Lido — Traefik com Docker socket                                     |
| 24 | `cat ops/env/.env.example`                                              | emporio/           | 0               | Nomes de variáveis lidos (sem valores)                                |
| 25 | `cat backend/.env.example`                                              | emporio/           | 0               | Nomes de variáveis de exemplo lidos                                  |
| 26 | `ls -la ops/env/`                                                       | emporio/           | 0               | `.env.example` (1189b) e `.env.production` (770b) detectados         |
| 27 | `grep -oP '^[A-Z_]+(?==)' ops/env/.env.production`                     | emporio/           | 0               | 11 nomes de variáveis extraídos                                      |
| 28 | `grep -oP '^[A-Z_]+(?==)' frontend/.env website_front/.env`            | emporio/           | 0               | VITE_BASE_API_URL; VITE_ERP_API_URL, VITE_WEBSITE_API_URL, VITE_SIGNAGE_TIMEZONE |
| 29 | `find backend/src -name "application*.properties" ...`                  | emporio/           | 0               | 4 arquivos de configuração Spring                                    |
| 30 | `find website_back/src -name "application*.properties"`                 | emporio/           | 0               | 1 arquivo                                                             |
| 31 | `cat backend/src/main/resources/application.properties`                 | emporio/           | 0               | Lido — valores literais sensíveis com aparência de credencial detectados (não transcritos) |
| 32 | `cat backend/src/main/resources/application-prod.properties`            | emporio/           | 0               | Lido                                                                  |
| 33 | `cat website_back/src/main/resources/application.properties`            | emporio/           | 0               | Lido — valores literais sensíveis com aparência de credencial, inclusive propriedades Uber, detectados |
| 34 | `find backend/src/.../db/migration -name "*.sql" \| sort \| head -20`   | emporio/           | 0               | 20 das 50 migrations listadas                                        |
| 35 | `find website_back/src/.../db/migration -name "*.sql" \| sort`          | emporio/           | 0               | 14 migrations listadas                                               |
| 36 | `cat frontend/entrypoint.sh`                                            | emporio/           | 0               | Lido — injeta VITE_BASE_API_URL via runtime-config.js                |
| 37 | `cat website_front/entrypoint.sh \| head -60`                           | emporio/           | 0               | Lido — exige VITE_VILLA_API_URL (legado)                             |
| 38 | `cat website_front/nginx.conf`                                          | emporio/           | 0               | Lido                                                                  |
| 39 | `cat deploy/nginx/nginx.conf`                                           | emporio/           | 0               | Lido                                                                  |
| 40 | `cat deploy/nginx/emporio-*.conf.template`                              | emporio/           | 0               | Lidos — templates com placeholder SEU_DOMINIO                        |
| 41 | `cat deploy/nginx/conf.d/emporio.conf`                                  | emporio/           | 0               | Lido — roteamento correto de domínios mas dentro de container proxy  |
| 42 | `cat ops/deploy/deploy-tenant.sh`                                       | emporio/           | 0               | Lido — verifica apenas erp-backend/erp-frontend                      |
| 43 | `cat ops/scripts/init-multiple-dbs.sh`                                  | emporio/           | 0               | Lido — cria múltiplos bancos via POSTGRES_MULTIPLE_DATABASES         |
| 44 | `ls release_control`                                                    | emporio/           | **2**           | Diretório não encontrado — `release_control` ausente                  |
| 45 | `grep -r "configuracoes\|release\|version" frontend/src -l`             | emporio/           | 0               | 10 arquivos encontrados                                              |
| 46 | `cat frontend/src/router/routes.js \| grep -A2 "path\|name\|component"` | emporio/          | 0               | Rotas lidas; sem rota de releases                                    |
| 47 | `cat frontend/src/boot/axios.js`                                        | emporio/           | 0               | Lido                                                                  |
| 48 | `grep -n "hasRole\|ROLE_\|requestMatchers" .../SecurityConfig.java`     | emporio/           | 0               | `ROLE_ADMIN`, `ROLE_SYSTEM` confirmados em `/api/admin/**`            |
| 49 | `grep -r "SseEmitter\|WebSocket\|@MessageMapping" backend/src -l`      | emporio/           | 0               | SSE e WebSocket confirmados no backend                               |
| 50 | `grep -r "SseEmitter\|WebSocket\|@MessageMapping" website_back/src`     | emporio/           | 0               | SSE (KdsEventService) e WebSocket STOMP (QuizWebSocketController)    |
| 51 | `grep -n "app.get\|app.post\|listen\|health\|PORT" whatsapp_service/index.js` | emporio/  | 0               | `/status` na linha 100; listen na 194                                |
| 52 | `find . -name ".gitignore" -maxdepth 3`                                 | emporio/           | 0               | 5 .gitignore em componentes; **nenhum na raiz**                      |
| 53 | `cat frontend/.gitignore backend/.gitignore`                            | emporio/           | 0               | Lidos — não cobrem `.env` direto                                     |
| 54 | `find backend/src/main/resources/db/migration -name "*.sql" \| wc -l`  | emporio/           | 0               | 50 migrations no backend                                             |
| 55 | `wc -c ops/env/.env.production; grep -c "=" ops/env/.env.production`   | emporio/           | 0               | 770 bytes, 14 pares — arquivo com valores literais sensíveis; validade externa não testada |
| 56 | `find . -path '*/.github/workflows/*' -type f -print | sort`           | emporio/           | 0               | workflows da raiz, componentes e caches localizados                  |
| 57 | `git rev-parse --show-toplevel`                                        | emporio/           | **128**         | Git confirma que a raiz não é repositório                            |
| 58 | `git status --short --branch`                                          | emporio/           | **128**         | nenhuma operação Git disponível; raiz não é repositório              |
| 59 | `nl -ba .github/workflows/main.yml backend/.github/workflows/backend.yml frontend/.github/workflows/frontend.yml` | emporio/ | 0 | expressões e workflows aninhados conferidos com linhas              |
| 60 | `rg -n 'uber\.(client-id\|client-secret\|customer-id\|access-token)' backend/src/main/resources/application.properties website_back/src/main/resources/application.properties` | emporio/ | 0 | propriedades Uber confirmadas nos dois backends; defaults não transcritos |
| 61 | `nl -ba ops/scripts/init-multiple-dbs.sh`                               | emporio/           | 0               | `CREATE DATABASE` incondicional nas linhas 8-10                      |
| 62 | `rg -n 'release_control\|/api/admin\|job\|SSE' docs/.../S01-inventario-e-contratos-reais.report.md` | emporio/ | 0 | referências usadas apenas como padrões; fronteira comercial preservada |
| 63 | `ls -ld .git`                                                        | emporio/           | **2**           | `.git` inexistente: `No such file or directory`                      |
| 64 | `find . -name .git -not -path '*/node_modules/*' -not -path '*/.ai-workflow/*' -print` | emporio/ | 0 | saída vazia; nenhum marcador `.git` na raiz ou nos componentes     |
| 65 | `git rev-parse --show-toplevel`                                      | emporio/           | **128**         | ausência de repositório: `fatal: not a git repository`               |

---

## 16. Arquivos alterados

Somente o arquivo de relatório foi alterado nesta retomada:

```
docs/infrastructure/deployment/implementation/slices/S01-inventario-e-contratos-reais.report.md
```

Nenhum outro arquivo foi criado ou alterado.

---

## 17. Declaração do que não foi executado

Em conformidade com o escopo da S01, **não foram executados**:

- `git init`, `git add`, `git commit`, `git tag`, `git push`
- criação ou alteração de `.gitignore`
- movimentação, exclusão ou rotação de segredos
- correção de Dockerfiles, Compose, workflows ou variáveis
- criação do módulo `release_control`
- criação de `ops/releases/components.yml` ou `manifest.schema.json`
- instalação de dependências (`npm install`, `mvn install`)
- execução de builds ou suites de teste
- construção ou publicação de imagens Docker
- acesso ao GHCR ou disparo de GitHub Actions
- conexão, consulta ou alteração da VPS
- consulta ou alteração de DNS
- inicialização ou parada de containers ou processos
- alteração de banco de dados
- reformatação de arquivos existentes
- edição do contrato arquitetural

---

> **Estado final do executor:** material pronto para revisão do orquestrador.
> O executor não declara `ACCEPTED`. O estado permanece `IN_PROGRESS` até revisão e aceitação pelo orquestrador.

---

## 18. Revisão do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS` — relatório ainda não aceito  
> **Data:** `2026-07-28`

O relatório cobre a maior parte da estrutura obrigatória e preserva os valores sensíveis. Entretanto, a evidência ainda diverge do workspace em pontos materiais.

### Correções bloqueantes

1. **Inventário incompleto de workflows**
   - O relatório mapeia somente `.github/workflows/main.yml` e `.github/workflows/deploy.yml`.
   - Também existem `backend/.github/workflows/backend.yml` e `frontend/.github/workflows/frontend.yml`.
   - Os dois arquivos aninhados devem ser inventariados e classificados.
   - Deve ficar explícito que, quando `emporio/` for a raiz do monorepo, o GitHub Actions somente descobrirá workflows sob a `.github/workflows/` da raiz. Os workflows aninhados serão artefatos legados/inertes, salvo se esses diretórios voltarem a ser repositórios independentes.
   - Workflows encontrados dentro de caches `.ai-workflow/` devem ser classificados como dependência/cache gerado e excluídos do inventário operacional.

2. **Terminologia Git incorreta**
   - Como não existe repositório Git na raiz nem nos componentes, os segredos não estão atualmente “versionados” neste workspace.
   - Corrigir para “valores sensíveis hardcoded em arquivos candidatos ao primeiro commit” ou formulação equivalente.
   - Manter explícito que eles seriam enviados ao histórico caso ocorresse `git init` seguido de `git add .` sem saneamento.

3. **Inventário incompleto das credenciais Uber**
   - `backend/src/main/resources/application.properties` também contém `uber.client-id`, `uber.client-secret`, `uber.customer-id` e `uber.access-token`.
   - Nos dois backends, esses campos usam expressão de ambiente com default literal embutido. O relatório deve registrar esse formato sem revelar o default.
   - Corrigir a seção de segurança e as matrizes relacionadas.

4. **Idempotência do script de bancos**
   - `ops/scripts/init-multiple-dbs.sh` executa `CREATE DATABASE` sem verificar existência.
   - O hook oficial normalmente rodar apenas na primeira inicialização do volume não torna o script idempotente.
   - Corrigir a Seção 8.4 e registrar separadamente: “execução normalmente limitada ao primeiro bootstrap do volume” e “script não idempotente se reexecutado”.

### Correções de precisão

5. **Erro do workflow `main.yml`**
   - `${{ id.meta-backend.outputs.tags }}` e equivalentes são referências a um contexto inexistente.
   - Classificar como expressão inválida/erro de avaliação do workflow, não simplesmente erro sintático YAML.

6. **Fronteira do `release_control`**
   - Os padrões de segurança, SSE e jobs do ERP podem servir como referência.
   - O relatório não deve sugerir registrar endpoints do `release_control` no backend comercial nem persistir seu estado no banco ERP sem marcar isso como contraditório à separação operacional aprovada.
   - Ajustar a Seção 11.4 para preservar essa fronteira.

7. **Evidências ambíguas**
   - Substituir referências genéricas como `package.json`, `Dockerfile:ENV`, `ambos Compose` ou `ops/compose` por `arquivo:linha` nos achados centrais.
   - Não é necessário transformar cada parágrafo em transcrição, mas matrizes e conclusões bloqueantes precisam ser rastreáveis.

### Instrução para o ciclo corretivo

- Atualizar este relatório no mesmo caminho.
- Não alterar o contrato `.task.md`, o tracker ou qualquer arquivo fora deste relatório.
- Preservar esta seção de revisão.
- Adicionar uma seção final `Resposta às correções do ciclo 1` informando, item a item, o que foi corrigido e as novas evidências.
- Atualizar a lista de comandos com os comandos adicionais e seus códigos de saída.
- Não exibir valores sensíveis; classificar somente o formato das propriedades.
- Manter o estado `IN_PROGRESS` e devolver novamente para revisão do orquestrador.

## Resposta às correções do ciclo 1

1. **Workflows:** corrigido. O inventário agora inclui `.github/workflows/main.yml`, `.github/workflows/deploy.yml`, `backend/.github/workflows/backend.yml` e `frontend/.github/workflows/frontend.yml`, e classifica os dois `publish.yml` sob `.ai-workflow/` como cache/dependência gerada, fora do inventário operacional. Está explícito que, no monorepo, Actions só descobre `.github/workflows/` da raiz; os workflows aninhados são legados/inertes salvo repositórios independentes. Evidência: comando 56; Seção 10.3; caminhos e linhas dos workflows.

2. **Terminologia Git:** corrigido. O relatório não chama os valores de versionados: registra valores literais sensíveis com aparência de credencial em arquivos candidatos ao primeiro commit e o risco futuro de `git init` + `git add .` sem saneamento. A evidência vigente confirma que `.git` não existe e que não há repositório Git na raiz ou nos componentes. Evidência: comandos 63–65; Seções 2 e 3.1.

3. **Credenciais Uber:** corrigido. A matriz e a Seção 12 agora incluem `backend/src/main/resources/application.properties:153-157` e `website_back/src/main/resources/application.properties:78-82`, com os quatro campos nos dois backends e o formato `${NOME:default literal}`, sem revelar defaults. Evidência: comando 60.

4. **Idempotência:** corrigido. A Seção 8.4 distingue o comportamento normal do hook no primeiro bootstrap do volume da propriedade do script: `CREATE DATABASE` é incondicional e uma reexecução contra bancos existentes não é idempotente. Evidência: `ops/scripts/init-multiple-dbs.sh:5-11`, comando 61.

5. **`main.yml`:** corrigido. O achado foi reclassificado de erro sintático para erro de avaliação de expressão do GitHub Actions: as referências `id.meta-*.outputs.*` usam contexto inexistente; o YAML continua estruturalmente legível. Evidência: `.github/workflows/main.yml:31-47,49-65`, comando 59.

6. **Fronteira do `release_control`:** corrigido. A Seção 11.4 mantém `/api/admin/**`, tabelas de jobs e SSE do ERP somente como padrões de referência. Ela registra como contraditório à arquitetura aprovada registrar endpoints do plano de controle no backend comercial ou persistir seu estado no banco ERP; o módulo deve permanecer separado, e não existe ainda no workspace. Evidência: Seção 11.4, `release_control` ausente no comando 44 e separação prevista no contrato arquitetural.

7. **Evidências:** corrigido nos achados centrais. Foram adicionados caminhos e linhas para os workflows, propriedades Uber, script de bancos e expressão do Actions, além dos comandos 56–62 com códigos de saída. Referências genéricas remanescentes em inventário descritivo não alteram as conclusões bloqueantes.

**Estado do executor:** `IN_PROGRESS`. Esta retomada altera somente este relatório e o devolve para nova revisão do orquestrador; não declara aceitação da S01.

---

## 19. Revisão do orquestrador — ciclo 2

> **Resultado:** `IN_PROGRESS` — correção pontual ainda necessária  
> **Data:** `2026-07-28`

As correções do ciclo 1 resolveram os workflows omitidos, a classificação das propriedades Uber, a idempotência, a expressão Actions e a fronteira do `release_control`.

Permanece uma contradição factual bloqueante na topologia Git:

- o resumo executivo afirma que existe “marcador `.git` presente”;
- a Seção 3.1 afirma que o primeiro `find` localizou `./.git`;
- a mesma Seção 3.1 afirma depois que a busca foi vazia;
- a resposta ao ciclo 1 volta a mencionar “presença do marcador `.git`”;
- a verificação do orquestrador encontrou `ls: cannot access '/home/gregorio/git/baronesa/emporio/.git': No such file or directory`;
- `find /home/gregorio/git/baronesa/emporio -name .git`, excluindo caches e dependências, não encontrou marcador;
- `git rev-parse --show-toplevel` continua falhando porque o marcador não existe, e não porque exista uma estrutura inválida.

### Correção requerida

1. Reexecutar e registrar, sem criar ou remover nada:

   ```text
   ls -ld .git
   find . -name .git -not -path '*/node_modules/*' -not -path '*/.ai-workflow/*' -print
   git rev-parse --show-toplevel
   ```

2. Corrigir o resumo, a Seção 3.1, a lista de comandos e a resposta ao ciclo 1 para um único estado factual.
3. Se a nova evidência continuar igual à atual, registrar simplesmente:
   - `.git` inexistente;
   - nenhum repositório Git na raiz ou nos componentes;
   - `git rev-parse` com código 128 por ausência de repositório.
4. Substituir “segredo real” ou “credencial real” por “valor literal sensível com aparência de credencial”, salvo quando houver evidência de validação externa. A validade dessas credenciais não foi testada nesta slice.
5. Preservar as Seções 18 e 19 e adicionar `Resposta às correções do ciclo 2`.
6. Alterar somente este relatório e mantê-lo `IN_PROGRESS`.

## Resposta às correções do ciclo 2

1. **Verificações Git reexecutadas:** os três comandos solicitados foram executados, sem criar ou remover nada. `ls -ld .git` retornou código 2 com `No such file or directory`; `find` retornou código 0 com saída vazia; `git rev-parse --show-toplevel` retornou código 128 com `fatal: not a git repository`.

2. **Estado Git reconciliado:** `.git` não existe e não há repositório Git na raiz ou nos componentes. O código 128 de `git rev-parse` decorre da ausência de repositório, não de um marcador inválido. Resumo, Seção 3.1 e resposta ao ciclo 1 foram alinhados a esse único estado factual.

3. **Lista de comandos:** os registros 1 e 2 foram marcados como evidência anterior inconsistente e substituída. Os comandos 63–65 registram as verificações vigentes, seus códigos de saída e a saída vazia do `find`.

4. **Classificação de sensibilidade:** onde não existe validação externa, o relatório usa “valor literal sensível com aparência de credencial”. Nenhuma validade, uso ou autenticidade externa foi testada nesta slice.

5. **Preservação e escopo:** as Seções 18 e 19 foram preservadas. Somente este relatório foi alterado, a S02 não foi aberta e o estado permanece `IN_PROGRESS`.

---

## 20. Revisão final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-28`

A S01 atende ao contrato após os dois ciclos corretivos.

Evidências aceitas:

- `.git` ausente na raiz e nos componentes; `git rev-parse` retorna código 128;
- quatro workflows de projeto inventariados e caches separados do inventário operacional;
- valores sensíveis classificados sem afirmar validade externa e sem transcrever seus conteúdos;
- propriedades Uber mapeadas nos dois backends;
- `init-multiple-dbs.sh` corretamente classificado como não idempotente se reexecutado;
- expressão inválida do GitHub Actions corretamente diferenciada de erro sintático YAML;
- fronteira independente do futuro `release_control` preservada;
- matrizes de componentes, configuração, dependências, persistência, Docker e CI/CD suficientes para decompor as próximas slices;
- itens não determináveis mantidos explícitos, sem fabricar evidência;
- nenhuma implementação, mutação Git, operação externa ou alteração da VPS realizada.

Os estados `IN_PROGRESS` declarados pelo executor e nas respostas anteriores permanecem como histórico dos ciclos de revisão. A autoridade final desta seção altera o estado da S01 para `ACCEPTED`.

A S02 pode ser planejada, mas não foi aberta por esta revisão.
