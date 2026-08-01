# S31 — Remoção dos gerenciadores de pacotes do runtime do `whatsapp_service`

> **Estado:** `PLANNED`
> **Tipo:** remediação por redução de superfície, com fechamento do contrato que a permitiu
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Autoridade:** derivada da `authorization-01` da S30a, já `ACCEPTED`. Não depende da conclusão da S30a, que permanece `IN_PROGRESS`.
> **Commit-base:** o checkpoint documental de refinamento do orquestrador, mensagem exata `docs: refine S31 delegation contract`. O executor executa o preflight fechado da §4.0 antes de qualquer alteração, registra o SHA literal no relatório e para diante de qualquer divergência.
> **Relatório de saída:** `S31-remocao-npm-runtime-whatsapp.report.md`

## 1. Por que esta slice existe

A medição `before` aceita na `authorization-01` da S30a
(`S30a-trivy-findings.before.json`) registra cinco achados HIGH/CRITICAL no
`whatsapp_service`. Os cinco têm `packageOrigin=npm-runtime` e todos apontam
para o mesmo diretório:

```text
usr/local/lib/node_modules/npm/node_modules/
```

| Severidade | CVE | Pacote | Instalado | Corrigido em |
|---|---|---|---|---|
| CRITICAL | CVE-2026-59873 | `tar` | 7.5.15 | 7.5.19 |
| HIGH | CVE-2026-59874 | `tar` | 7.5.15 | 7.5.18 |
| HIGH | CVE-2026-12151 | `undici` | 6.26.0 | 6.27.0 |
| HIGH | CVE-2026-13149 | `brace-expansion` | 5.0.6 | 5.0.7 |
| HIGH | CVE-2026-14257 | `brace-expansion` | 5.0.6 | 5.0.8 |

A afirmação vale para essa medição, e não para o histórico do componente.
Medições anteriores da S30a, registradas no relatório em `:181`, `:307` e
`:499`, tiveram outros achados de origem distinta — base Alpine e dependências
da aplicação — fechados por bumps de imagem-base em commits anteriores. O que a
medição `before` estabelece é que, **uma vez fechadas as demais origens, a
superfície residual do componente é integralmente o CLI npm da imagem.**

Medição `after`, depois do commit `3ffda1a`, um achado remanescente:
CVE-2026-14257, `brace-expansion` 5.0.7, corrigido em 5.0.8.

Três conclusões que o dado sustenta:

1. **Nenhum dos cinco veio da aplicação.** O `package-lock.json` do serviço
   resolve `brace-expansion` em `2.1.4`, acima do corrigido `2.1.3`.
2. **Nenhum gerenciador é usado em runtime.** O `CMD` é `["node", "index.js"]`,
   que ignora `scripts.start`; o `HEALTHCHECK` é `node -e`; as dependências
   chegam por `COPY --from=dependencies`; Chromium vem do `apk`. Não há
   `child_process`, `spawn` nem `exec` em `index.js` ou `app.js`.
3. **A linha que remedia é a linha que introduz.** O commit `3ffda1a`
   acrescentou `whatsapp_service/Dockerfile:14`, que instala `npm@12.0.2`
   globalmente no estágio de runtime. Ela fechou quatro dos cinco achados e
   reabriu o quinto, porque a versão que instala embute `brace-expansion` 5.0.7.
   Cada release futura do npm re-sorteia as dependências embutidas.

O contrato já pretendia proibir isso. `tools/docker/validate_node_images.py:66`
emite `GLOBAL_CLI_FORBIDDEN` quando encontra a string literal `npm install -g`.
A linha 14 escreve `npm --global install`, semanticamente idêntico, sintaxe
diferente, e passa. É a mesma causa estrutural nomeada na S30a §1: **o contrato
afirma texto, não comportamento.**

Esta slice não persegue um CVE. Remove a superfície residual inteira e fecha a
brecha do contrato que permitiu reintroduzi-la.

## 2. Escopo, sequência e o que esta slice deliberadamente não faz

O inventário residual da S30a tem nove CVEs únicos em dezesseis ocorrências,
em três grupos de causa distinta. Contagens derivadas de
`S30a-trivy-findings.after.json`, reproduzíveis por:

```bash
jq -r 'def grp: if (.packageName|test("^org.springframework")) then "A-spring"
       elif (.packageName|test("jasperreports")) then "C-jasper" else "B-npm" end;
  [.findings[] | {g: grp, id: .vulnerabilityId}] | group_by(.g)[]
  | "\(.[0].g): ocorrencias=\(length) cves=\([.[].id]|unique|length)"' \
  docs/infrastructure/deployment/implementation/slices/S30a-trivy-findings.after.json
```

| Grupo | Causa | CVEs únicos | Ocorrências |
|---|---|---|---|
| A — Spring | parent `spring-boot-starter-parent` 3.3.13 em `backend` e `website_back` | 6 | 13 |
| B — npm | CLI npm na imagem do `whatsapp_service` | 1 | 1 |
| C — JasperReports | fixado transitivamente por `java-danfe` 1.8 | 2 | 2 |

Esta slice trata **somente o grupo B**.

Consequência que o executor precisa internalizar: **fechar o grupo B não deixa a
CI verde.** As quinze ocorrências dos grupos A e C permanecem, o job `images`
continua retornando exit 1 em `backend` e `website_back`, e
`publish-candidate.yml` continua recusando a execução. Portanto:

> **CI verde não é critério de aceite desta slice. Esta slice não faz push.**

O critério é o delta Trivy do componente `whatsapp_service` e a impossibilidade
contratual de regressão. O push ocorre quando o grupo A estiver fechado e o
grupo C tiver decisão de exceção registrada.

Não criar S32, S33, S30b ou qualquer outra slice. Não alterar a task, as
amendments, a authorization ou o relatório da S30a.

## 3. Objetivo observável

Ao final:

1. a imagem de runtime do `whatsapp_service` não contém nenhum gerenciador de
   pacotes — `npm`, `npx`, `corepack`, `yarn`, `yarnpkg` — nem os diretórios que
   os hospedam, provado por inspeção do filesystem da imagem construída;
2. `node` permanece e é o único runtime;
3. o scan Trivy local do `whatsapp_service`, com `severity: HIGH,CRITICAL` e
   `ignore-unfixed: false`, retorna **zero achados**;
4. o serviço continua funcional: o container sobe, `/health/live` responde 200 e
   `/status` responde 200 com `connected=false` e `hasQr=false`, sem gerenciador
   presente e sem inicialização externa de WhatsApp;
5. `tools/docker/validate_node_images.py` rejeita a reintrodução de CLI global
   de pacotes em qualquer variante sintática da família fechada, e rejeita a
   presença de qualquer gerenciador no estágio de runtime do `whatsapp_service`;
6. exatamente um commit local, sem push;
7. `backend`, `website_back`, `frontend`, `website_front`, `gateway`,
   `release_control` e produção intocados.

## 4. Decisões fechadas do orquestrador

Estas decisões estão fechadas. O executor implementa; não escolhe alternativa.

### 4.0 Preflight Git e identificação do checkpoint

Antes de alterar qualquer arquivo, executar e registrar literalmente:

```bash
test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"
test "$(git branch --show-current)" = "main"
test -z "$(git status --porcelain)"
test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"
test "$(git log -1 --format=%s)" = "docs: refine S31 delegation contract"
test "$(git rev-list --count origin/main..HEAD)" = "6"
git rev-parse HEAD
```

Os seis `test` devem retornar exit 0. A saída de `git rev-parse HEAD` é o
`SHA-base` da execução. Ela não é comparada com um SHA autorreferente embutido
nesta task: a identidade do checkpoint é fechada por branch, worktree vazio,
`origin/main`, mensagem exata e quantidade de commits locais. Qualquer
divergência exige parada antes da primeira alteração.

### 4.1 Remover a instalação global de npm

Excluir integralmente de `whatsapp_service/Dockerfile` a linha introduzida por
`3ffda1a`:

```dockerfile
RUN npm --global install --no-audit --no-fund npm@12.0.2
```

Não substituir por outra versão de npm. Não trocar por `corepack enable`,
`yarn` ou `pnpm`. A decisão é remover, não atualizar.

### 4.2 Purgar os gerenciadores de pacotes do estágio de runtime

No estágio `runtime`, e somente nele, remover os gerenciadores que a imagem base
`node:24.18.1-alpine3.24` traz.

A enumeração da imagem exata, já realizada, encontrou:

```text
/usr/local/lib/node_modules/npm
/usr/local/lib/node_modules/corepack
/opt/yarn-v1.22.22
/usr/local/bin/npm
/usr/local/bin/npx
/usr/local/bin/corepack
/usr/local/bin/yarn
/usr/local/bin/yarnpkg
```

`pnpm` e `pnpx` estão ausentes. O executor **reconfirma a enumeração** contra a
imagem base pinada por digest antes de remover, registra o resultado literal no
relatório e para se divergir desta lista — divergência significa que o digest
mudou ou que a base não é a esperada.

Requisitos:

- a remoção ocorre no estágio de runtime, antes de `USER 10001:10001`;
- remove os oito caminhos, diretórios e symlinks;
- o estágio `dependencies` permanece intacto e continua executando `npm ci`;
- `COPY --from=dependencies /workspace/node_modules ./node_modules` permanece
  como está; as dependências da aplicação não são tocadas;
- preferir dobrar a remoção no `RUN apk add --no-cache …` já existente, para não
  acrescentar camada;
- `node` permanece.

Provar a ausência inspecionando a imagem construída, não o Dockerfile.

### 4.3 Fechar a brecha do contrato

Em `tools/docker/validate_node_images.py`:

1. **Corrigir `GLOBAL_CLI_FORBIDDEN`.** A verificação por string literal
   `"npm install -g"` deve reconhecer instalação global de CLI
   independentemente da ordem e da forma da flag. Família fechada a cobrir:
   flags `-g`, `--global`, `--location=global`; subcomandos `install`, `i`,
   `add`; flag antes ou depois do subcomando. `quasar build` permanece proibido.

2. **Criar `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME`.** Para o `whatsapp_service`,
   isolar o estágio de runtime pelo alias — do `FROM … AS runtime` até o próximo
   `FROM` **ou até o fim do arquivo**, o que vier primeiro; hoje não há `FROM`
   posterior, e a regra não pode depender disso. No trecho isolado, exigir
   ausência de invocação de `npm`, `npx`, `corepack`, `yarn` e `yarnpkg`, e
   exigir a remoção efetiva dos oito caminhos da §4.2.

   **A remoção deve ser verificada como comando, não como texto.** Presença
   literal dos caminhos não pode ser aceita como prova: os caminhos precisam ser
   argumento de uma remoção real. Caso contrário esta slice reproduz, no próprio
   validador, o defeito que ela existe para corrigir.

A verificação `NPM_CI_REQUIRED` da linha 64 permanece: `npm ci` continua
obrigatório, no estágio `dependencies`. A regra 2 é escopada ao runtime e não
pode disparar por causa dela.

Não relaxar, remover ou tornar condicional nenhuma verificação existente.

### 4.4 Mutantes obrigatórios

Em `tools/docker/tests/test_validate_node_images.py`. Usar teste parametrizado
para a família fechada, exigindo o erro **exato** de cada caso.

#### Família global exaustiva — casos 1 a 18

Gerar o produto cartesiano completo:

```text
flags       = -g | --global | --location=global
subcomandos = install | i | add
posição     = antes | depois do subcomando
```

Para cada uma das dezoito combinações, inserir no runtime uma destas formas:

```text
RUN npm <flag> <subcomando> some-cli
RUN npm <subcomando> <flag> some-cli
```

As dezoito devem produzir exatamente `GLOBAL_CLI_FORBIDDEN`. Isso inclui a
variante que escapou, `npm --global install`, e impede que exemplos
representativos sejam confundidos com cobertura integral da família declarada.

#### Demais rejeições — casos 19 a 26

| # | Mutante | Erro esperado |
|---|---|---|
| 19 | `RUN npx some-cli` no runtime | `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` |
| 20 | `RUN corepack enable` no runtime | `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` |
| 21 | `RUN yarn global add some-cli` no runtime | `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` |
| 22 | remoção da linha de purga | `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` |
| 23 | purga com `rm` trocado por `echo`, mantendo os caminhos no texto | `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` |
| 24 | purga reduzida a comentário, mantendo os caminhos no texto | `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` |
| 25 | purga omitindo `/opt/yarn-v1.22.22`, `yarn` e `yarnpkg` | `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` |
| 26 | remoção de `npm ci` do estágio `dependencies` | `NPM_CI_REQUIRED` |

#### Aceitações — casos 27 e 28

| # | Caso | Exigência |
|---|---|---|
| 27 | o Dockerfile corrigido íntegro | nenhum erro |
| 28 | asserção focalizada sobre o mesmo Dockerfile, com `npm ci` no estágio `dependencies` | não contém `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` |

O mutante 26 e o caso 28 provam que a regra nova não canibalizou a antiga, em
nenhuma das duas direções. Registrar no relatório o resultado literal dos 28
casos, incluindo cada uma das dezoito combinações parametrizadas.

### 4.5 Prova por paridade local

Docker local está **autorizado** para esta slice, exclusivamente para build,
inspeção, scan e smoke locais do `whatsapp_service`.

Estão autorizados **somente acessos de leitura** necessários à reprodução do
build e do scan:

- pull da imagem base pinada por digest;
- pull da imagem Trivy pinada por digest e download dos bancos de
  vulnerabilidade do Trivy;
- downloads feitos por `apk add --no-cache` exclusivamente dos repositórios
  configurados em `/etc/apk/repositories` na imagem base pinada;
- downloads e chamadas de protocolo feitos por `npm ci` exclusivamente aos
  hosts de registry já presentes no `whatsapp_service/package-lock.json`
  versionado — hoje `registry.npmjs.org` — incluindo endpoints de metadados e
  auditoria usados pelo próprio npm.

Qualquer outro destino de rede exige parada. Permanecem proibidos GHCR,
`docker login`, `docker push` e qualquer publicação.

Executar antes do commit e registrar:

- build de `whatsapp_service/Dockerfile` com o mesmo contexto, plataforma
  `linux/amd64`, `load: true` e `push: false` que `ci.yml` usa;
- inspeção do filesystem da imagem construída provando ausência dos oito
  caminhos da §4.2 e presença de `node`;
- scan Trivy com `severity: HIGH,CRITICAL` e `ignore-unfixed: false`, usando a
  imagem oficial fixada por digest já adotada pela S30a, sem instalar binário no
  host:

  ```text
  aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e
  ```

- **smoke funcional determinístico**, com o container recebendo
  `WHATSAPP_INITIALIZATION_DISABLED=true` e uma porta livre do host. Sem essa
  variável, `index.js:39` cai no `else` e chama `service.initialize()`, que sobe
  Puppeteer e tenta sessão real de WhatsApp — efeito externo proibido pela §6.
  Asserções exigidas:

  | Rota | Esperado |
  |---|---|
  | `/health/live` | HTTP 200 |
  | `/status` | HTTP 200, corpo com `connected=false` e `hasQr=false` |

  Provar também que `npm`, `npx`, `corepack`, `yarn` e `yarnpkg` não são
  invocáveis dentro do container em execução.

- **cleanup escopado**: remover apenas o container, a tag, a imagem e o cache
  criados por esta execução, identificados nominalmente. Proibido `docker system
  prune`, `docker image prune`, `docker builder prune` sem filtro, ou remoção de
  qualquer imagem preexistente, inclusive a base e a do Trivy.

Emitir `S31-trivy-findings.whatsapp.after.json` no diretório de slices, seguindo
integralmente o esquema de `S30a-trivy-findings.after.json`. O arquivo é
obrigatório mesmo com lista vazia, e deve conter: `schemaVersion`, `scanner` com
digest da imagem Trivy, `trivyDatabase` com versões e timestamps dos bancos,
`measurement`, `measuredAtUtc`, `sourceSha`, `sourceTreeSha`, `sourceState`,
`sourceDiffSha256`, `images` com o componente e seu `imageId`, `counts` com
totais zerados e `findings: []`.

Semântica fechada da linhagem:

- `measurement` é `after`;
- `sourceSha` é o SHA-base confirmado no preflight da §4.0;
- `sourceState` é
  `working-tree-after-s31-remediation-before-evidence-files`;
- `sourceDiffSha256` é o SHA-256 do `git diff --binary` entre `sourceSha` e os
  três arquivos de implementação (`whatsapp_service/Dockerfile`, validador e
  testes), em ordem de caminho;
- `sourceTreeSha` é a árvore Git medida formada pelo `sourceSha` com somente
  esses três arquivos de implementação sobrepostos, calculada em índice Git
  temporário. JSON e relatório ficam fora dessa árvore para evitar
  autorreferência.

**Não alterar a política do Trivy nesta slice.** Não criar `.trivyignore`, não
introduzir exceção, não alterar `severity`, `ignore-unfixed` ou `exit-code` em
`ci.yml` ou `publish-candidate.yml`.

### 4.6 Um commit, sem push

Depois de todos os gates locais, exatamente um commit:

```bash
git commit -m "fix: remove package managers from whatsapp runtime image"
```

Sem push, sem force, sem tag, sem outra branch, sem outro remote, sem
`--no-verify`, sem alteração de identidade, sem commit adicional. O commit fica
local, empilhado sobre o checkpoint documental do orquestrador.

Antes do commit, `git diff --cached --check` deve retornar 0. As nove
ocorrências documentais de whitespace apontadas na S30a §15.1 **já foram
reparadas pelo orquestrador** no checkpoint documental; o executor não as
reencontra e não deve tocar naqueles arquivos. Depois do commit,
`git diff --check origin/main..HEAD` deve retornar 0, e o executor registra o
resultado literal — regredi-lo é falha de aceite.

## 5. Fronteira autorizada

Alterar ou criar somente:

- `whatsapp_service/Dockerfile`;
- `tools/docker/validate_node_images.py`;
- `tools/docker/tests/test_validate_node_images.py`;
- `docs/infrastructure/deployment/implementation/slices/S31-trivy-findings.whatsapp.after.json` (novo);
- `docs/infrastructure/deployment/implementation/slices/S31-remocao-npm-runtime-whatsapp.report.md` (novo).

Não alterar: `whatsapp_service/package.json`, `package-lock.json`, `index.js`,
`app.js`, `.dockerignore`, qualquer Dockerfile de outro componente,
`tools/docker/java_images_contract.py`, `ci.yml`, `publish-candidate.yml`,
`publish-release.yml`, `deploy-production.yml`, `rollback-production.yml`,
Compose, gateway, `backend`, `website_back`, `frontend`, `website_front`,
`release_control`, os `pom.xml`, tasks, corrections, amendments e relatórios da
S30/S30a, README de implementação, HANDOFF, tracker, OpenAPI, schemas ou
produção.

O índice em `docs/infrastructure/deployment/implementation/README.md` já contém
a linha da S31 e é mantido **pelo orquestrador**, não pelo executor.

## 6. Comportamentos negativos

Não publicar release, não criar tag, não fazer push, não executar
`publish-release.yml`, `deploy-production.yml` ou `rollback-production.yml`, não
fazer deploy, rollback, SSH, VPS, DNS, alteração de Nginx, mutação de produção,
cleanup destrutivo, criação ou rotação de credencial. Não usar `docker push`,
`docker login`, GHCR ou qualquer publicação em registry — pulls de leitura estão
autorizados apenas nos termos da §4.5. Não instalar binário no host. Não relaxar
Trivy, scanner de segredos, pinagem por SHA ou permissões de workflow. Não
alterar a imagem base nem seu digest. Não produzir tráfego externo de WhatsApp
nem criar sessão real. Não tocar nos grupos A e C do inventário.

## 7. Matriz de validação local obrigatória

```bash
cd /home/gregorio/git/baronesa/emporio
python3 tools/docker/validate_node_images.py validate
python3 tools/docker/java_images_contract.py validate
python3 tools/ci/validate_ci.py
python3 tools/candidates/validate_candidate_workflow.py
python3 tools/releases/validate_release_workflow.py
python3 tools/releases/validate_publisher_ui.py
python3 tools/releases/validate_publisher_identity_bridge.py
python3 tools/ci/validate_workflow_inventory.py
python3 tools/deploy/validate_deploy_workflow.py
python3 tools/deploy/validate_rollback_contract.py
python3 tools/deploy/validate_rollback_runtime.py
python3 tools/releases/release_control_contract.py validate
python3 tools/security/bootstrap_contract.py validate
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/security/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/gateway/tests -v
git diff --check
```

Acrescentar a paridade local da §4.5. Depois dos gates funcionais:

1. concluir o JSON e uma versão provisoriamente final do relatório;
2. fazer stage exclusivamente dos cinco caminhos da §5;
3. executar `python3 tools/ci/secret_scan.py --tracked` e registrar sua saída
   literal no relatório;
4. fazer stage novamente do relatório e repetir o mesmo secret scan; a segunda
   saída deve ser `clean`, ter `unsupported=0` e coincidir com a primeira;
5. se as duas saídas divergirem, parar antes do commit; não iterar nem ajustar
   allowlist;
6. confirmar novamente a lista staged e executar `git diff --cached --check`,
   que deve retornar 0.

Essa ordem é obrigatória porque `secret_scan.py --tracked` usa `git ls-files`:
os dois arquivos novos só entram na varredura depois do stage.

## 8. Critérios de aceite

A slice só é aceita quando, simultaneamente:

1. os treze validadores e as sete suítes retornam exit 0;
2. os 26 mutantes da §4.4 são rejeitados com o erro exato prescrito, e os dois
   casos de aceitação passam sem erro, com resultado literal dos 28 casos
   registrado;
3. a reconfirmação da enumeração da base coincide com a lista da §4.2, e a
   inspeção da imagem construída prova ausência dos oito caminhos e presença de
   `node`;
4. o scan Trivy local do `whatsapp_service` retorna zero achados HIGH/CRITICAL,
   com `S31-trivy-findings.whatsapp.after.json` emitido no esquema da §4.5;
5. o smoke determinístico prova `/health/live` em 200 e `/status` em 200 com
   `connected=false` e `hasQr=false`, com `WHATSAPP_INITIALIZATION_DISABLED=true`
   e sem gerenciador invocável no container;
6. o cleanup removeu apenas os artefatos criados pela execução, sem prune amplo;
7. as duas execuções finais do scanner de segredos, já com os cinco caminhos
   staged, permanecem `clean`, com `unsupported=0` e saída idêntica;
8. existe exatamente um commit novo, local, sem push, tocando apenas os caminhos
   da §5, e `git diff --check origin/main..HEAD` retorna 0;
9. nenhum efeito em release, tag, deploy, rollback, tráfego de WhatsApp ou
   produção;
10. o relatório atende à §10.

## 9. Condições de parada

Parar e registrar o bloqueio, sem improvisar correção fora da fronteira, diante
de:

- qualquer gate do preflight Git da §4.0 divergente;
- enumeração da base divergente da lista da §4.2;
- qualquer achado HIGH/CRITICAL remanescente no `whatsapp_service` após a purga;
- falha do smoke, ou qualquer indício de que o runtime dependa de gerenciador;
- necessidade de alterar `package.json`, `package-lock.json`, `index.js`,
  `app.js` ou a imagem base para concluir;
- colisão entre `PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME` e `NPM_CI_REQUIRED` que
  não se resolva dentro da §5;
- qualquer defeito novo em arquivo não autorizado;
- qualquer efeito observado em produção ou tráfego externo de WhatsApp.

Não fazer push em nenhuma hipótese, inclusive se todos os gates passarem.

## 10. Relatório

Além do JSON exigido pela §4.5, criar somente:

`docs/infrastructure/deployment/implementation/slices/S31-remocao-npm-runtime-whatsapp.report.md`

Deve conter: CWD; autoridade lida; SHA-base confirmado por `git rev-parse HEAD`
antes das alterações; arquivos criados e alterados; implementação por decisão
fechada; reconfirmação da enumeração da base; matriz local com comandos, exits e
durações; resultado literal dos 28 casos da §4.4; paridade local com build,
inspeção, scan, smoke e cleanup; as duas saídas finais idênticas do secret scan;
delta de achados do componente; mensagem exata do commit; resíduos; acessos
externos; divergências.

Sobre o SHA do commit final: **o relatório está dentro do commit e não pode
conter o próprio SHA** — acrescentá-lo mudaria o SHA de novo. O relatório
registra o SHA-base, a mensagem exata e a referência literal
`commit final = HEAD entregue`. O SHA final aparece no handoff pós-commit e na
revisão do orquestrador, nunca dentro do commit.

Sem qualquer token, header ou segredo. Terminar exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

## 11. Próxima fronteira, apenas informativa

Não autorizadas e não devem ser criadas:

- **S32 — grupo A.** Elevar o parent `spring-boot-starter-parent` de 3.3.13 para
  a linha 3.5 em `backend` e `website_back`, com `springdoc` acompanhando e
  limpeza dos overrides que se tornam obsoletos ou regressivos. Fecha **seis
  CVEs e treze ocorrências**, incluindo o único CRITICAL.
- **S33 — grupo C.** `jasperreports` 6.20.6, fixado transitivamente por
  `java-danfe` 1.8, que é a última versão publicada. Os fixes exigem a linha
  7.0.x, um major, sobre o caminho fiscal de DANFE/DANFCE. É o único grupo sem
  correção viável hoje e o único escopo legítimo de exceção temporária —
  **dois CVEs e duas ocorrências, somente no `backend`**.

O push e a CI verde dependem de S32; a decisão de exceção depende de S33.
