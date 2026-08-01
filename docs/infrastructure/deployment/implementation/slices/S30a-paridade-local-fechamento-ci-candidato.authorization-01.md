# S30a — autorização 01: preservação local e prova técnica do resíduo Trivy

> **Estado:** `AUTHORIZED` pelo orquestrador em 01/08/2026
> **Base:** S30a, amendment-02 aceita e decisão explícita do usuário
> **Commit-base remoto:** `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`

## 1. Objetivo e resultado observável

Preservar em commits locais o trabalho já verificado e executar uma única
prova técnica sobre as 25 ocorrências Trivy restantes. A prova deve persistir
o pacote e a origem de cada achado, aplicar somente correções baratas e
dirigidas e medir o resíduo real antes de qualquer decisão de exceção.

Esta autorização não é uma amendment reativa. Ela substitui a inferência por
medição e fecha uma classe: imagem runtime, pacote vulnerável, contrato que
afirma a imagem e evidência correspondente.

## 2. Estado e autoridade

- S01–S29 permanecem `ACCEPTED`.
- S30 permanece `SPLIT`.
- A amendment-02 está `ACCEPTED`; a S30a permanece `IN_PROGRESS`.
- S30b e S31 continuam inexistentes e bloqueadas.
- O stage inicial deve estar vazio; não há autorização de push.
- Não ler, copiar ou transcrever qualquer segredo.

Antes de agir, registrar CWD, branch, `HEAD`, `origin/main`, stage e worktree.
Parar se a branch não for `main`, se `HEAD` ou `origin/main` divergirem do
commit-base remoto acima, ou se já houver conteúdo staged não explicado.

## 3. Preservação imediata em três commits locais

Revisar a lista literal staged antes de cada commit. Criar, nesta ordem:

1. `fix: close CI command contract and local parity`
   - classe CI/candidato: `ci.yml`, validadores CI/candidato, invocabilidade e
     seus testes;
   - classe de linhagem documental pendente: somente README, handoff, retomada,
     contratos, correções, autorização e relatórios de S30/S30a atualmente
     alterados ou novos.
2. `fix: bump hardened base images`
   - os seis Dockerfiles;
   - validadores Docker e seus testes.
3. `fix: patch application dependencies`
   - os dois POMs e os manifests/lockfiles WhatsApp efetivamente alterados.

Não incluir arquivo fora dessas classes. Depois dos três commits, provar stage
vazio, registrar os três SHAs e continuar sem push. Não usar force,
`--no-verify`, tag, outro remote/branch, `git init` ou alteração de identidade.

## 4. Evidência JSON obrigatória

Reconstruir somente `backend`, `website_back` e `whatsapp_service` a partir do
terceiro commit. Executar Trivy `0.70.0` pela imagem e digest já fixados, com
`HIGH,CRITICAL`, `ignore-unfixed=false` e saída JSON bruta temporária.

Normalizar e versionar em `docs/infrastructure/deployment/implementation/slices/`:

- `S30a-trivy-findings.before.json`;
- `S30a-trivy-findings.after.json`.

Cada ocorrência normalizada deve conter, sem campos vazios:

```text
component, target, class, type, vulnerabilityId, severity,
packageName, installedVersion, fixedVersion, packageOrigin
```

`packageOrigin` deve ser exatamente uma destas categorias:

```text
application-java | application-node | alpine-runtime | npm-runtime | unknown
```

Os JSONs também registram schemaVersion, versão/digest do scanner, metadados de
atualização do banco Trivy, SHA fonte, contagem por componente/severidade/origem
e data UTC. Deduplicar somente a tupla exata
`component/target/packageName/vulnerabilityId/installedVersion` e ordenar
deterministicamente por componente, target, packageName e vulnerabilityId. Não
versionar o JSON bruto completo nem caches do Trivy.

Se qualquer achado não puder ser classificado, mantê-lo como `unknown`; não
inferir pacote pelo nome da CVE e não aplicar correção a ele.

## 5. Correções técnicas autorizadas

### 5.1 Pacotes Alpine

Quando o JSON apontar `alpine-runtime`, alterar somente o Dockerfile runtime do
componente afetado. Usar upgrade nomeado e não pin de versão:

```text
apk upgrade --no-cache <somente os pacotes vulneráveis identificados>
```

Não usar `apk upgrade` sem lista de pacotes. Não migrar a distribuição, trocar
novamente o `FROM` ou inserir repositório Alpine diferente.

### 5.2 npm embarcado na imagem Node

Quando o target provar origem em npm do runtime, atualizar o npm global para
uma versão exata cuja árvore efetiva contenha as versões corrigidas. Registrar
antes e depois as versões de npm, `tar`, `undici` e `brace-expansion` que
existirem nessa árvore. Não usar `latest`.

### 5.3 Dependência da aplicação

Quando o target apontar `/app/node_modules` ou o lockfile copiado para a imagem,
classificar como `application-node`. Corrigir no lockfile/manifest dentro da
linha compatível e validar como dependência da aplicação, mesmo que
`npm audit` não a reporte.

Para `application-java`, esta autorização permite somente medir e classificar.
Não migrar Spring Boot, Spring Framework, Spring Security ou JasperReports e
não alterar código Java.

Chromium só pode ser alterado se aparecer literalmente como pacote/target no
JSON. Não persegui-lo por hipótese.

## 6. Condição para conservar uma mudança

Conservar uma correção somente quando, simultaneamente:

- remove ao menos o achado correspondente sem criar HIGH/CRITICAL novo;
- o componente constrói em `linux/amd64` com os mesmos build args da CI;
- o scan posterior é produzido pelo mesmo Trivy e banco da medição anterior;
- a regressão real do componente passa;
- os validadores da classe alterada e `git diff --check` passam.

Para backend/website_back, usar PostgreSQL efêmero no digest canônico e porta
host que não interfira em `127.0.0.1:5432`. Para WhatsApp, executar `npm ci` e
`npm run test`. Uma correção sem redução ou com regressão deve ser removida
somente dos arquivos experimentais afetados, preservando os três commits
iniciais.

Se houver ao menos uma correção conservada, criar um quarto commit local:

```text
fix: refresh vulnerable runtime packages
```

Esse commit pode conter apenas correções técnicas aprovadas por esta seção, os
dois JSONs normalizados e a nova seção curta do relatório. Se nenhuma correção
for conservada, versionar os JSONs e o relatório em:

```text
docs: record measured Trivy residual
```

Continuar sem push.

## 7. Parada e decisão seguinte

Ao concluir:

- remover imagens, containers, volumes e caches criados pela execução;
- provar que o PostgreSQL preexistente não foi afetado;
- provar stage vazio e registrar os commits locais;
- registrar delta `25 -> residual` por componente, pacote e origem;
- parar para revisão do orquestrador.

É proibido nesta autorização:

- criar `.trivyignore` ou `.trivyignore.yaml`;
- alterar política, severidade ou `exit-code` do Trivy;
- alterar workflows para exceções;
- fazer push, login ou acesso a GHCR;
- criar tag, release, candidato ou workflow dispatch;
- acessar SSH, VPS, DNS, deploy, rollback ou produção.

Se restar exceção, a decisão seguinte deve criar no mesmo ato uma slice de
remediação com responsável e data anterior à expiração. A referência inicial é
30 dias para `CRITICAL` e 90 dias para `HIGH`; nenhum prazo é autorizado agora.

## 8. Relatório curto

Acrescentar ao relatório S30a somente:

- preflight e SHAs dos commits;
- comandos/exits da prova;
- links para os dois JSONs;
- correções conservadas ou descartadas e motivo;
- regressões focalizadas;
- delta final, cleanup, stage e proibições preservadas.

Não repetir inventários já existentes no corpo do Markdown. Terminar
exatamente com:

```text
IN_PROGRESS — aguardando revisão do resíduo Trivy pelo orquestrador
```
