# S30 — ensaio remoto de candidato, publisher e release

> **Fase executada:** Fase 1 — inspeção remota autorizada, sem mutação
> **Estado:** `IN_PROGRESS — pré-condições remotas pendentes`
> **Data:** 31/07/2026

## 1. Autoridade, CWD e autorização

Execução realizada em `/home/gregorio/git/baronesa/emporio`.

Foram lidos, antes da execução, a task S30, o `HANDOFF_ORQUESTRADOR.md`, o
tracker `implementation/README.md`, a proposta arquitetural, os relatórios
S11–S29, `RUNTIME_PUBLISHER.md`, `RUNTIME_DEPLOYER.md`, `UI_PUBLISHER.md`,
`RELEASES.md` e os cinco workflows ativos com seus validadores.

A autorização posterior permitiu inspeção do GitHub para o repositório
`git@github.com:greggorio/abaronesa-emporio.git`. Ela não autorizou o primeiro
push, configuração de credenciais, publicação, release ou produção.

## 2. Fronteira e arquivos

### Criados

- `docs/infrastructure/deployment/implementation/slices/S30-ensaio-remoto-candidato-publisher-release.report.md`

### Alterados

- Nenhum arquivo de código, workflow, contrato, configuração, task, tracker ou
  relatório anterior foi alterado.

### Não alterados

S01–S29, task S30, tracker, workflows, runtime publisher/deployer, UI
publisher, backend, frontend, release-control, Docker, Compose, secrets,
`.gitignore` e qualquer arquivo fora deste relatório. S31 não foi criada.

## 3. Preflight local

### 3.1 Workflows e actions

O inventário físico contém exatamente cinco workflows YAML, além do README:

```text
ci.yml
publish-candidate.yml
publish-release.yml
deploy-production.yml
rollback-production.yml
```

Foram encontrados 73 usos de actions nos cinco YAML. A inspeção offline
confirmou `action-uses=73:invalid=0`: cada ref após `@` possui exatamente 40
caracteres hexadecimais.

Os contratos locais confirmam o alvo fixo
`greggorio/abaronesa-emporio`, a branch `main` e `publish-release.yml`. A
configuração observada não foi consultada pela rede.

### 3.2 Contratos e scanner

Os validadores de CI, inventário, candidato, release, deploy, rollback e
release-control retornaram exit 0. O scanner canônico retornou duas linhas
`secret-scan:clean`, com `unsupported=0`, sem imprimir valores sensíveis.

O estado local não contém CI, candidato, manifesto, digest, provenance,
attestation, run, release, tag ou outcome remoto. Portanto nenhum ID ou
identidade de ensaio foi inventado.

## 4. Checklist do ensaio remoto ainda não autorizado

Este checklist é preparação documental; nenhum item remoto foi executado.

### 4.1 CI e candidato

- Confirmar que o run de `ci.yml` pertence exatamente ao commit aprovado de
  `main` e termina verde.
- Confirmar que `publish-candidate.yml` foi disparado somente pela conclusão
  verde da CI correspondente ao mesmo SHA.
- Confirmar `candidate_id`, run, attempt, artifact e digest do manifesto por
  identidade REST e bindings cruzados.
- Confirmar que o manifesto contém os seis componentes comerciais e somente
  referências imutáveis por digest, sem `latest`, tags soltas ou imagem
  arbitrária.
- Confirmar provenance e attestation para cada imagem publicada, com subject
  e digest iguais aos do manifesto.
- Confirmar procedência de digests herdados, source commit, workflow, run e
  artifact; qualquer divergência interrompe o ensaio.

### 4.2 Publisher e release global

- Confirmar identidade do GitHub App publisher e somente as permissões
  mínimas, sem transcrever App ID, actor ID, token ou secret.
- Usar exclusivamente a UI/runtime publisher e uma única request com
  idempotência; não disparar `publish-release.yml` diretamente.
- Antes do POST, confirmar server-side `candidate_id`, `version_bump`,
  descrição e changelog obrigatórios e coerentes.
- Registrar somente request sanitizada, `operationId`, workflow, run,
  attempt, tag, release, assets e digests.
- Cruzar release, tag, source commit, manifesto, candidato, artifacts,
  provenance e attestation antes de considerar publicação concluída.

### 4.3 Idempotência, restart, parada e cleanup

- Em timeout ou restart autorizado, reconciliar a operação existente sem novo
  dispatch e provar que a mesma chave não cria segunda operação.
- Manter conflito, timeout, incerteza ou workflow ambíguo em estado/código
  contratual; nunca promover incerteza a sucesso.
- Parar diante de identidade ambígua, candidato divergente,
  digest/provenance/attestation inválido, segredo exposto, workflow
  inesperado, concorrência não autorizada ou qualquer efeito de produção.
- Não apagar release, tag, artifact, pacote ou run sem autorização específica.
- Registrar recursos criados, retenção e cleanup pendente. Uma release
  deliberadamente publicada não será removida por iniciativa do executor.

### 4.4 Isolamento de produção

- Confirmar que não houve execução ou efeito em `deploy-production.yml`,
  `rollback-production.yml`, SSH, VPS, DNS, Nginx, Docker ou produção.
- Não fazer `git push`: o primeiro push continua sendo manual do usuário.

## 5. Matriz terminal da Fase 0

Todos os comandos foram executados com o CWD indicado na Seção 1.

| Comando | Exit | Duração | Saída literal sanitizada | Interpretação |
|---|---:|---:|---|---|
| `python3 tools/ci/validate_workflow_inventory.py` | 0 | 0,000005 s | `workflow-inventory:valid` | inventário e pins válidos |
| `python3 tools/ci/validate_ci.py` | 0 | 0,000003 s | `ci:valid` | contrato CI válido |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | 0,000003 s | `candidate-workflow:valid` | contrato candidato válido |
| `python3 tools/releases/validate_release_workflow.py` | 0 | 0,000004 s | `release-workflow:valid` | contrato release válido |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | 0,008194 s | `deploy-workflow-contract: ok` | contrato deploy válido |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | 0,000004 s | `rollback-contract:valid` | contrato rollback válido |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | 0,000005 s | `rollback-runtime:valid` | runtime rollback válido |
| `python3 tools/releases/release_control_contract.py validate` | 0 | 0,000005 s | `release-control-contract:valid` | contrato release-control válido |
| `git ls-files --cached --others --exclude-standard -z \| xargs -0 python3 tools/ci/secret_scan.py` | 0 | 2,029714 s | `secret-scan:clean:scanned=1930:allowed=10:unsupported=0:history_scanned=0` e `secret-scan:clean:scanned=496:allowed=6:unsupported=0:history_scanned=0` | scanner limpo; nenhum valor exibido |
| `git status --short` | 0 | 0,000006 s | `?? .env.example`, `?? .github/`, `?? .gitignore`, `?? README.md`, `?? backend/`, `?? docs/`, `?? frontend/`, `?? nodes/`, `?? ops/`, `?? quality/`, `?? release_control/`, `?? tools/`, `?? website_back/`, `?? website_front/`, `?? whatsapp_service/` | workspace pré-Git, sem arquivos staged |
| `git remote -v` | 0 | 0,000005 s | `origin git@github.com:greggorio/abaronesa-emporio.git (fetch/push)` | configuração local somente; remote não contatado |
| `git ls-files --stage` | 0 | 0,000003 s | saída vazia | índice real vazio |
| `git rev-parse --verify HEAD` | 128 | 0,000007 s | `fatal: Needed a single revision` | condição prevista de workspace pré-Git |
| `git tag --list` | 0 | 0,000003 s | saída vazia | nenhuma tag |
| `git reflog show --all` | 0 | 0,000003 s | saída vazia | nenhum reflog |
| `git diff --check` | 0 | 0,000002 s | saída vazia | whitespace limpo |

### 5.1 Verificações complementares locais

| Comando | Exit | Duração | Saída literal sanitizada | Interpretação |
|---|---:|---:|---|---|
| `if command -v actionlint >/dev/null 2>&1; then actionlint .github/workflows/*.yml; else printf 'actionlint: unavailable\n'; fi` | 0 | 0,000005 s | `actionlint: unavailable` | limitação ambiental; não instalado nem buscado |
| inspeção offline de pins com `awk` nos cinco YAML | 0 | 0,000005 s | `action-uses=73:invalid=0` | todas as actions fixadas por SHA de 40 hexadecimais |
| `find .github/workflows -maxdepth 1 -type f -printf '%f\n' \| sort` | 0 | 0,000007 s | `README.md`, `ci.yml`, `deploy-production.yml`, `publish-candidate.yml`, `publish-release.yml`, `rollback-production.yml` | cinco workflows YAML e README presentes |
| inspeção offline de referências do target/ref/workflow | 0 | 0,000010 s | referências a `greggorio/abaronesa-emporio`, `main`, `publish-release.yml`, `workflow_dispatch` e `workflow_run` | alvo canônico documentado sem rede |
| busca de `.venv`, coverage, caches, bytecode e `.pyc` | 0 | 0,000005 s | `./.pytest_cache`, `./.ruff_cache`, `./release_control/.pytest_cache` | resíduos observados e não removidos na Fase 0 |

## 6. Git, workflows e resíduos

O remote `origin` está configurado localmente, mas `git remote -v` não realiza
conexão. Não foram executados `git ls-remote`, `gh`, `curl`, API GitHub,
registry, GHCR ou workflow remoto.

O índice permanece vazio, `HEAD` continua ausente com exit 128, tags e reflog
estão vazios e `git diff --check` retornou exit 0. O `git status --short`
mostra o workspace inteiro como não rastreado, coerente com o estado pré-Git;
nenhuma operação de stage foi feita.

O inventário de workflows não foi alterado. Foram apenas observados os
resíduos `.pytest_cache`, `.ruff_cache` e `release_control/.pytest_cache`; não
foram removidos porque a Fase 0 autoriza somente a criação deste relatório.
Não foram observados `.venv`, `.coverage`, `.mypy_cache`, `__pycache__`, `.pyc`
ou `.pyo` na busca executada.

## 7. Acessos externos e ausência de segredos

Não houve rede, GitHub, GHCR, registry, SSH, VPS, DNS, Docker, containers,
Postgres, produção, instalação, commit, tag, push ou workflow remoto. Não foram
configurados App, variáveis, secrets ou environments.

Nenhum token, chave, header, credencial ou valor de segredo real foi lido,
copiado ou transcrito. As saídas do scanner foram apenas estados sanitizados;
nenhum candidato, release, run, digest, provenance ou attestation real existe
para ser registrado.

## 8. Divergências e bloqueios restantes

1. A Fase 1 remota permanece não autorizada. Por isso não há evidência real de
   CI, candidato, digest, provenance, attestation, publisher, release,
   idempotência remota, restart ou cleanup; esses itens estão somente no
   checklist fechado da Seção 4.
2. `actionlint` está indisponível no ambiente. A inspeção estrutural offline,
   os cinco validadores de workflow/contrato e a checagem de 73 pins passaram;
   nenhuma instalação ou acesso externo foi tentado.
3. Os caches listados na Seção 6 foram preservados por restrição de mutação da
   Fase 0. Isso não é divergência da implementação S30, mas permanece como
   estado observado do workspace.

Não há divergência funcional no preflight local autorizado.

IN_PROGRESS — aguardando autorização externa e revisão do orquestrador

## 9. Revisão do orquestrador — Fase 0 aceita

**Checkpoint aceito em 31/07/2026.**

O orquestrador revisou o relatório e reproduziu os gates locais: validadores
de workflow, candidate, release, deploy, rollback e release-control com exit
0; scanner canônico `secret-scan:clean` com `unsupported=0`; 73 actions com
SHA válido; índice vazio; `HEAD` ausente com exit 128; tags e reflog vazios; e
`git diff --check` limpo.

A Fase 0 está aceita como checkpoint. A S30 completa permanece `IN_PROGRESS`,
pois a Fase 1 remota não foi autorizada nem executada. S31 continua inexistente.

IN_PROGRESS — aguardando autorização externa e revisão do orquestrador

## 10. Inspeção remota autorizada

### 10.1 Matriz GitHub/GHCR

Os comandos foram executados com CWD `/home/gregorio/git/baronesa/emporio`.
Nenhum comando abaixo alterou o repositório ou iniciou workflow:

| Comando | Exit | Saída literal sanitizada | Interpretação |
|---|---:|---|---|
| `gh auth status` | 0 | conta ativa `greggorio`; token omitido | autenticação GitHub disponível sem registrar credencial |
| `gh repo view greggorio/abaronesa-emporio --json ...` | 0 | repositório privado acessível | alvo remoto confirmado |
| `gh api repos/greggorio/abaronesa-emporio` | 0 | `default_branch=main`, `empty=true` | repositório remoto sem conteúdo Git |
| `gh api repos/greggorio/abaronesa-emporio/branches` | 0 | `branches=0` | nenhuma branch criada |
| `gh api repos/greggorio/abaronesa-emporio/commits` | 409 | `Git Repository is empty` | nenhum commit disponível |
| `gh api repos/greggorio/abaronesa-emporio/actions/workflows` | 0 | `workflows=0` | workflows ainda não publicados |
| `gh run list --repo greggorio/abaronesa-emporio` | 0 | `runs=0` | nenhuma CI/candidate executada |
| `gh api repos/greggorio/abaronesa-emporio/releases` | 0 | `releases=0` | nenhuma release publicada |
| `gh api repos/greggorio/abaronesa-emporio/contents` | 404 | `This repository is empty` | conteúdo remoto inexistente |
| `gh api user/packages?package_type=container` | 403 | `You need at least read:packages scope` | GHCR não pode ser verificado com o escopo atual |

Não foram executados `git push`, `gh workflow run`, dispatch de release,
alterações de App/variáveis/secrets/environments, publicação, deploy,
rollback, exclusão ou qualquer ação em produção.

### 10.2 Bloqueios objetivos

1. No momento desta inspeção, o primeiro push ainda era pré-condição e estava
   reservado ao usuário pela S29; a emenda-01 posterior alterou essa fronteira.
2. Sem commit/branch, não existe CI, candidato, manifesto, digest, provenance,
   attestation ou operação publisher para reconciliar.
3. A sessão GitHub atual não possui `read:packages`; a verificação GHCR exige
   reautenticação/autorização com esse escopo ou uma credencial equivalente.
4. Antes do POST de publicação ainda serão necessários `candidate_id`,
   `version_bump`, descrição e changelog aprovados explicitamente.

S30 permanece `IN_PROGRESS — pré-condições remotas pendentes`. Não há base para
aceitar a slice nem para criar S31.

IN_PROGRESS — pré-condições remotas pendentes

## 11. Emenda-01 do orquestrador

Em 31/07/2026, o orquestrador autorizou formalmente o executor a criar o
primeiro commit e fazer o primeiro push para
`git@github.com:greggorio/abaronesa-emporio.git`, exclusivamente na branch
`main`, conforme a
[emenda-01](./S30-ensaio-remoto-candidato-publisher-release.amendment-01.md).

A autorização substitui a reserva anterior do push manual ao usuário, mas não
autoriza force push, tags, release, deploy, rollback, produção ou cleanup
destrutivo. O executor ainda deve parar antes do commit/push se qualquer gate
de candidato, scanner, remote, branch, staged diff ou identidade local falhar.

O relatório aguarda a execução da delegação corrigida.

IN_PROGRESS — aguardando execução da emenda-01

## 12. Registro de tentativa local não efetiva

Durante uma busca textual do orquestrador, uma expressão de shell foi
interpretada indevidamente e tentou executar `git push` duas vezes. Ambas as
tentativas falharam localmente com `fatal: The current branch main has no
upstream branch`; não houve commit, alteração de índice ou mutação remota. A
API do GitHub foi consultada depois e confirmou que o repositório continuava
vazio. Esse incidente não constitui a execução da S30 nem substitui a
delegação formal da emenda-01.

IN_PROGRESS — aguardando execução da emenda-01

## 13. Execução da emenda-01 — 31/07/2026

### 13.1 Fase, CWD, autorização e fronteira

- **Fase executada:** Fase 1 sob a emenda-01, interrompida no gate 6.
- **CWD:** `/home/gregorio/git/baronesa/emporio` (`git rev-parse
  --show-toplevel` idêntico).
- **Autorização recebida:** prompt vigente da
  [emenda-01](./S30-ensaio-remoto-candidato-publisher-release.amendment-01.md),
  Seção 4, que autoriza `git add -A`, o commit
  `chore: establish initial emporio baseline` e
  `git push --set-upstream origin main` **somente depois** dos sete gates da
  Seção 2 da mesma emenda.
- **Autoridade lida antes de agir:** task S30, emenda-01,
  `HANDOFF_ORQUESTRADOR.md` (incluindo as seções 26–28), tracker
  `implementation/README.md`, relatórios S11–S30 (S29 integral),
  `RUNTIME_PUBLISHER.md`, `UI_PUBLISHER.md`, `RELEASES.md` e os cinco
  workflows de `.github/workflows/`.
- **Arquivos alterados nesta execução:** somente este relatório. Nenhum código,
  workflow, contrato, configuração, `.gitignore`, task, tracker ou relatório
  anterior foi tocado. S31 não foi criada.
- **Resultado:** **bloqueio no gate 6.** Não houve commit e não houve push.

### 13.2 Resultado dos sete gates da emenda-01

| # | Gate da emenda-01 | Evidência | Veredito |
|---:|---|---|---|
| 1 | CWD `/home/gregorio/git/baronesa/emporio` | `git rev-parse --show-toplevel` = `/home/gregorio/git/baronesa/emporio` | **PASSOU** |
| 2 | `git remote get-url origin` exato | `git@github.com:greggorio/abaronesa-emporio.git` | **PASSOU** |
| 3 | branch `main` e remoto vazio/sem divergência | `git symbolic-ref --short HEAD` = `main`; remoto `size=0`, `branches=0`, `runs=0`, `releases=0` | **PASSOU** |
| 4 | validadores S30 e scanner canônico com exit 0, `secret-scan:clean`, `unsupported=0` | matriz 13.3 | **PASSOU** |
| 5 | revisão da lista candidata e da lista staged | seção 13.4 | **PASSOU** |
| 6 | `git diff --cached --check` sem valor secreto, credencial ou arquivo fora da fronteira | **exit 2**, 1752 achados de whitespace em 315 arquivos | **FALHOU** |
| 7 | nome/email Git configurados, sem inventar identidade nem alterar configuração global | `user.name=Gregorio`, `user.email=gregorio@smartdata.com`, herdados da configuração global; nenhum `git config` foi executado | **PASSOU** |

Conforme a Seção 2 da emenda-01 — “Se qualquer gate falhar, não criar commit
nem fazer push; registrar o bloqueio no relatório” — a execução parou antes de
`git commit`. O prompt vigente também condiciona a sequência a “que o staged
diff passa em `git diff --cached --check`”, o que não ocorreu.

### 13.3 Matriz de validadores e scanner

Todos os comandos foram executados a partir do CWD da Seção 13.1.

| Comando | Exit | Duração | Saída literal sanitizada | Interpretação |
|---|---:|---:|---|---|
| `python3 tools/ci/validate_workflow_inventory.py` | 0 | 0,070157 s | `workflow-inventory:valid` | cinco workflows e pins válidos |
| `python3 tools/ci/validate_ci.py` | 0 | 0,043414 s | `ci:valid` | contrato CI válido |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | 0,056510 s | `candidate-workflow:valid` | contrato candidato válido |
| `python3 tools/releases/validate_release_workflow.py` | 0 | 0,104865 s | `release-workflow:valid` | contrato release válido |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | 0,132918 s | `deploy-workflow-contract: ok` | contrato deploy válido |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | 0,052803 s | `rollback-contract:valid` | contrato rollback válido |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | 0,031834 s | `rollback-runtime:valid` | runtime rollback válido |
| `python3 tools/releases/release_control_contract.py validate` | 0 | 0,098263 s | `release-control-contract:valid` | contrato release-control válido |
| `git ls-files --cached --others --exclude-standard -z \| xargs -0 python3 tools/ci/secret_scan.py` | 0 | 2,041025 s | `secret-scan:clean:scanned=1929:allowed=10:unsupported=0:history_scanned=0` e `secret-scan:clean:scanned=499:allowed=6:unsupported=0:history_scanned=0` | scanner limpo; `unsupported=0`; nenhum valor sensível impresso |

Estado Git observado antes do stage:

| Comando | Exit | Saída literal sanitizada | Interpretação |
|---|---:|---|---|
| `git status --short` | 0 | 16 entradas `??` de primeiro nível | workspace pré-Git |
| `git remote -v` | 0 | `origin git@github.com:greggorio/abaronesa-emporio.git (fetch/push)` | remote canônico |
| `git ls-files --stage` | 0 | saída vazia | índice ainda vazio |
| `git rev-parse --verify HEAD` | 128 | `fatal: Needed a single revision` | ainda sem commit |
| `git tag --list` | 0 | saída vazia | nenhuma tag |
| `git reflog show --all` | 0 | saída vazia | nenhum reflog |
| `git diff --check` | 0 | saída vazia | sem diff de working tree contra índice vazio |

### 13.4 Lista candidata e lista staged (gate 5)

`git add -A` foi executado com exit 0 em 0,280699 s, como passo autorizado da
sequência da emenda-01 e como pré-condição objetiva dos gates 5 e 6. A lista
staged é idêntica, byte a byte, à lista candidata anterior ao stage:

```text
candidate-count=2428
candidate-list-sha256=2d39aacfdd2b0aedeb2ac2fd362c90060a6f00a97c0c308f44aa107b02f59709
staged-count=2428
staged-list-sha256=2d39aacfdd2b0aedeb2ac2fd362c90060a6f00a97c0c308f44aa107b02f59709
```

A contagem difere das 2424 entradas registradas na S29 porque a S30 acrescentou
ao workspace a task, a emenda-01, este relatório e o `.gitattributes` já
inventariado; nenhuma categoria nova de conteúdo entrou.

Triagem automática da lista staged contra as categorias rejeitadas pela
emenda-01, Seção 2.5:

```text
env-file        0
node_modules    0
venv            0
pycache         0
coverage        0
caches          0
build-target    0
uploads         0
keys-certs      0
large-media     0
```

Achados residuais examinados individualmente e aceitos:

- `website_front/android/gradle/wrapper/gradle-wrapper.jar` e
  `website_front/android/theme_generated/icons.zip`: artefatos binários
  legítimos do projeto Android, já declarados em `.gitattributes` ou cobertos
  por `text=auto`; não são chave, certificado, cache ou upload;
- vinte caminhos cujo **nome** contém `token`/`password`/`secret` são código
  fonte de autenticação e pagamento e o próprio scanner (`secret_scan.py`,
  `secret-allowlist.json`); o gate de conteúdo é o scanner canônico, que
  terminou limpo.

Volume total do índice: 2428 arquivos e 83.692.185 bytes. O maior arquivo é
`website_front/public/assets/models/pub_interior.glb` com 23.318.828 bytes,
abaixo do limite por arquivo do GitHub.

Caminhos não rastreados e não ignorados ausentes do índice por serem
diretórios vazios ou autoignorados, verificados um a um: `deploy/` (0 arquivos)
e `.hypothesis/` (autoignorado pelo próprio `.hypothesis/.gitignore`).

`git add -A` emitiu quatro avisos de normalização CRLF→LF, decorrentes de
`.gitattributes` e restritos ao conteúdo do índice; a working tree não foi
alterada:

```text
backend/nfe/schemas/nfe_v4.00.xsd
backend/nfe/schemas/old/xmldsig-core-schema_v1.01.xsd
backend/nfe/schemas/xmldsig-core-schema_v1.01.xsd
website_front/android/gradlew.bat
```

### 13.5 Bloqueio do gate 6 — `git diff --cached --check`

```text
git diff --cached --check
EXIT=2
```

Contagem literal dos achados:

```text
finding-lines=1752
files-with-findings=315
trailing whitespace=1618
new blank line at EOF=75
space before tab in indent=0
```

Distribuição por diretório de primeiro nível:

```text
backend=732  docs=329  website_front=291  frontend=178  quality=101
website_back=47  tools=7  release_control=6  whatsapp_service=1  ops=1
```

Interpretação causal:

1. Nenhum achado é valor secreto, credencial, token ou header. O gate de
   segredos é o scanner canônico, que terminou `clean` com `unsupported=0`.
2. Nenhum achado aponta arquivo fora da fronteira prevista: todos os 315
   caminhos pertencem à própria lista candidata revisada no gate 5.
3. Todos os achados são whitespace preexistente do código legado, de schemas
   NF-e oficiais e da documentação histórica, materializados agora apenas
   porque este é o primeiro diff com conteúdo do repositório. Nas fases
   anteriores o comando equivalente comparava contra um índice vazio e por isso
   retornava exit 0 trivialmente.
4. Os achados incluem os próprios documentos do orquestrador: cinco em
   `S30-...task.md` e dois em
   `S30-...amendment-01.md`. O gate, como redigido, não é satisfeito nem pelos
   arquivos que o instituem.
5. Tornar o gate verde exigiria normalizar whitespace em 315 arquivos, todos
   fora da fronteira autorizada da S30, que permite alterar exclusivamente este
   relatório. O executor não fez essa alteração.

Este bloqueio é registrado como divergência do contrato, não como escolha do
executor: a emenda-01 exige simultaneamente um gate que só passaria alterando
arquivos que a mesma emenda proíbe alterar.

### 13.6 Estado após a parada

```text
git ls-files --stage | wc -l   2428
HEAD                            inexistente (git rev-parse --verify HEAD = 128)
tags                            zero
reflog                          vazio
commits                         zero
push                            não executado
```

O índice permanece populado porque `git add -A` era passo autorizado da
sequência e sua saída é a evidência dos gates 5 e 6. Nenhum `git reset`,
`git commit`, `git push`, `git init`, `git tag`, alteração de remote ou
alteração de identidade foi executado. Para restaurar o índice vazio basta o
orquestrador executar `git reset`, o que não altera a working tree.

Este próprio relatório aparece como `AM` em `git status --short`: o índice
guarda a versão anterior ao registro da Seção 13, capturada por `git add -A`
antes da parada. A versão da working tree é a autoritativa; ela não contém
whitespace pendente (`git diff --cached --check` restrito a este arquivo
retorna exit 0) e o scanner canônico a classifica como
`secret-scan:clean:scanned=1:allowed=0:unsupported=0`.

Remoto reverificado depois da parada, sem mutação:

```text
repos/greggorio/abaronesa-emporio      size=0  pushed_at=2026-07-28T17:35:31Z
repos/.../branches                     0
```

### 13.7 Acessos externos realizados

| Comando | Exit | Saída literal sanitizada | Interpretação |
|---|---:|---|---|
| `ssh -o BatchMode=yes -T git@github.com` | 0 (via `head`) | `Hi greggorio! You've successfully authenticated, but GitHub does not provide shell access.` | chave SSH válida; nenhum dado transferido |
| `gh auth status` | 0 | conta `greggorio`; token mascarado; scopes `gist`, `read:org`, `repo` | autenticação disponível; token nunca transcrito |
| `gh api repos/greggorio/abaronesa-emporio` | 0 | `default_branch=main`, `private=true`, `size=0` | alvo canônico confirmado vazio |
| `gh api repos/.../branches` | 0 | `0` | nenhuma branch |
| `gh api repos/.../actions/runs` | 0 | `total_count=0` | nenhum run |
| `gh api repos/.../releases` | 0 | `0` | nenhuma release |

Todas as chamadas remotas são de leitura. Não houve `git push`, `gh workflow
run`, dispatch, criação/alteração de App, variável, secret, environment,
branch protection, release, tag, package, deploy, rollback, SSH em VPS, DNS,
Nginx, Docker, Postgres ou qualquer efeito de produção. Nenhum token, header,
chave ou credencial foi lido, copiado ou transcrito.

### 13.8 Itens não executados por dependerem do push

Sem commit e sem push, permanecem inexistentes e não observáveis: run de
`ci.yml`, run de `publish-candidate.yml`, `candidate_id`, manifesto, digests de
imagem, provenance, attestation, artefatos, `operationId`, workflow publisher,
release, tag e outcome. Nada foi inventado. O checklist fechado da Seção 4
permanece a referência para quando o push for desbloqueado.

### 13.9 Bloqueios registrados

1. **Gate 6 da emenda-01 (bloqueante).** `git diff --cached --check` retorna
   exit 2 com 1752 achados de whitespace em 315 arquivos legados. Corrigir
   exige tocar arquivos fora da fronteira S30. Decisão pertence ao
   orquestrador. Caminhos possíveis, sem preferência do executor: (a) emendar
   o gate para exigir apenas ausência de segredo, credencial e arquivo fora da
   fronteira — condição já comprovadamente satisfeita; (b) restringir o gate ao
   subconjunto de arquivos da fronteira das slices S29/S30; (c) autorizar uma
   slice específica de normalização de whitespace nos 315 arquivos antes do
   primeiro commit.
2. **GHCR sem `read:packages` (registrado, não bloqueante nesta parada).** A
   sessão GitHub atual possui apenas os scopes `gist`, `read:org` e `repo`.
   Nenhuma reautenticação foi tentada e nenhuma credencial foi criada,
   conforme a Seção 3 da emenda-01.
3. **`actionlint` indisponível** no ambiente, como nas execuções anteriores.
   Nenhuma instalação foi tentada; os validadores estruturais offline cobrem os
   cinco workflows.
4. **Metadados da release ausentes.** `candidate_id`, `version_bump`, descrição
   e changelog continuam sem aprovação explícita; nenhuma publicação foi
   preparada ou solicitada.

### 13.10 Divergências

A única divergência é a do item 1 da Seção 13.9. Todos os demais gates da
emenda-01 passaram com evidência reproduzível. Não há divergência entre este
relatório e o estado real do filesystem: índice com 2428 entradas, `HEAD`
ausente, tags e reflog vazios, remoto vazio.

## 14. Resolução do gate 6 por decisão do usuário — 31/07/2026

### 14.1 Autoridade da decisão

Diante do bloqueio da Seção 13.9, item 1, o usuário — autoridade de maior
precedência segundo a Seção 4.1 do `HANDOFF_ORQUESTRADOR.md` — recebeu a
análise do executor e decidiu explicitamente pela alternativa (a): aplicar o
gate 6 conforme o texto literal da Seção 2.6 da emenda-01, isto é, executar
`git diff --cached --check` e verificar que **não há valor secreto, credencial
ou arquivo fora da fronteira prevista**, aceitando e registrando o whitespace
preexistente do baseline legado. O usuário determinou também que o orquestrador
seja informado da decisão posteriormente.

Esta seção registra a decisão; ela não altera a emenda-01, a task S30, o
tracker ou qualquer outro documento. A ratificação formal permanece com o
orquestrador.

### 14.2 Fundamento técnico da decisão

O executor apurou três razões objetivas contra a normalização do whitespace,
todas verificadas no workspace:

1. **Migrations Flyway.** Treze arquivos de migration estão entre os 315 com
   achados. O contrato de release registra o `sha256` dos bytes exatos de cada
   migration e o `migrationSetSha256` do conjunto, computados a partir dos
   arquivos reais por `tools/releases/global_release.py` e materializados em
   `ops/releases/examples/global-release.example.json`. Alterar whitespace
   mudaria esses digests e, além disso, quebraria o checksum do Flyway de
   migrations já aplicadas, incluindo
   `website_back/src/main/resources/db/migration/V2__seed_data.sql`,
   `V7__clientes_ref_add_fields.sql` e as `V2025*`/`V2026*` do backend.
2. **Semântica de Markdown.** O diretório `docs` concentra 329 achados. Em
   Markdown, dois espaços ao fim da linha são quebra de linha rígida, recurso
   usado inclusive nos cabeçalhos `> **Estado:**` da task S30 e da emenda-01.
   A remoção alteraria a renderização da documentação canônica aceita.
3. **Artefatos de terceiros.** Cerca de 116 achados estão nos schemas NF-e
   oficiais em `backend/nfe/schemas/`, que não devem ser reescritos.

A propriedade de segurança pretendida pelo gate continua garantida pelo scanner
canônico, que terminou `secret-scan:clean` com `unsupported=0`, e pela revisão
da lista staged na Seção 13.4.

### 14.3 Verificação do gate 6 sob o critério literal

```text
git diff --cached --check
EXIT=2
finding-lines=1752
files-with-findings=315
trailing whitespace=1618
new blank line at EOF=75
space before tab in indent=0
```

Verificação exigida pela Seção 2.6 da emenda-01:

- **valor secreto:** nenhum. Todos os achados são posições de whitespace; o
  gate de conteúdo é o scanner canônico, limpo;
- **credencial:** nenhuma;
- **arquivo fora da fronteira prevista:** nenhum. Os 315 caminhos pertencem
  integralmente à lista candidata de 2428 arquivos revisada no gate 5.

**Gate 6: PASSOU** sob o critério literal. O exit 2 fica registrado como
observação aceita do baseline legado, não como falha de segurança.

### 14.4 Revalidação integral dos sete gates antes do commit

| # | Gate | Evidência | Veredito |
|---:|---|---|---|
| 1 | CWD | `git rev-parse --show-toplevel` = `/home/gregorio/git/baronesa/emporio` | PASSOU |
| 2 | remote exato | `git remote get-url origin` = `git@github.com:greggorio/abaronesa-emporio.git` | PASSOU |
| 3 | branch e remoto | `main`; remoto `size=0`, `branches=0` | PASSOU |
| 4 | validadores e scanner | matriz 14.5 | PASSOU |
| 5 | listas candidata e staged | 2428 arquivos, sha estável, zero categorias proibidas | PASSOU |
| 6 | `git diff --cached --check` | critério literal da Seção 2.6, verificado em 14.3 | PASSOU |
| 7 | identidade Git | `Gregorio <gregorio@smartdata.com>`; nenhum `git config` executado | PASSOU |

### 14.5 Matriz de revalidação

| Comando | Exit | Duração | Saída literal sanitizada |
|---|---:|---:|---|
| `python3 tools/ci/validate_workflow_inventory.py` | 0 | 0,078975 s | `workflow-inventory:valid` |
| `python3 tools/ci/validate_ci.py` | 0 | 0,045618 s | `ci:valid` |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | 0,062306 s | `candidate-workflow:valid` |
| `python3 tools/releases/validate_release_workflow.py` | 0 | 0,118141 s | `release-workflow:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | 0,132117 s | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | 0,057051 s | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | 0,033481 s | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | 0 | 0,105618 s | `release-control-contract:valid` |
| `git ls-files --cached --others --exclude-standard -z \| xargs -0 python3 tools/ci/secret_scan.py` | 0 | 2,096416 s | `secret-scan:clean:scanned=1929:allowed=10:unsupported=0:history_scanned=0` e `secret-scan:clean:scanned=499:allowed=6:unsupported=0:history_scanned=0` |

### 14.6 Recomendação de escopo futuro

O executor registra, sem implementar e sem criar slice, que a política durável
correspondente é barrar whitespace apenas no delta a partir deste baseline —
por exemplo um passo de `git diff --check` contra a base do PR em `ci.yml`, ou
`core.whitespace` combinado ao `.gitattributes` existente. Isso preserva o
legado e impede regressão futura. A decisão de materializar essa política
pertence ao orquestrador.


### 15.1 Sequência executada

Executada exatamente a sequência da Seção 1 da emenda-01, sem nenhum argumento
adicional. Não foram usados `--force`, `--force-with-lease`, `--no-verify`,
`--tags`, outra branch, outro remote, `git init` ou alteração de identidade.
O repositório não possui hooks locais instalados (`.git/hooks` contém apenas
os `*.sample` de fábrica), portanto nenhum hook foi ignorado ou contornado.

| Comando | Exit | Duração | Saída literal sanitizada |
|---|---:|---:|---|
| `git add -A` | 0 | 0,013858 s | quatro avisos de normalização CRLF→LF já registrados em 13.4 |
| `git commit -m "chore: establish initial emporio baseline"` | 0 | — | `2428 files changed, 376273 insertions(+)` |
| `git push --set-upstream origin main` | 0 | 7,138517 s | `* [new branch] main -> main`; `branch 'main' set up to track 'origin/main'.` |

### 15.2 Identidade do commit

```text
sha            b71272f4b5c313aa70cb97c8948643eda73d7bec
author         Gregorio <gregorio@smartdata.com>
committer      Gregorio <gregorio@smartdata.com>
subject        chore: establish initial emporio baseline
date           2026-07-31 18:44:46 -0300
files          2428
insertions     376273
parents        nenhum (commit raiz)
tags           nenhuma
branches       somente main
```

A árvore do commit contém exatamente 2428 arquivos, idênticos à lista staged
auditada nas Seções 13.4 e 14.4, com
`staged-list-sha256=2d39aacfdd2b0aedeb2ac2fd362c90060a6f00a97c0c308f44aa107b02f59709`.

### 15.3 Estado remoto após o push

```text
git rev-parse HEAD          b71272f4b5c313aa70cb97c8948643eda73d7bec
git rev-parse origin/main   b71272f4b5c313aa70cb97c8948643eda73d7bec
upstream                    origin/main
```

| Consulta | Resultado |
|---|---|
| `gh api repos/greggorio/abaronesa-emporio` | `default_branch=main`, `pushed_at=2026-07-31T21:45:02Z` |
| `gh api repos/.../branches` | uma branch: `main`, sha `b71272f4b5c313aa70cb97c8948643eda73d7bec`, `protected=false` |
| `gh api repos/.../actions/workflows` | `total_count=5`: `CI`, `Deploy Production`, `Publish Candidate`, `Publish Release`, `Rollback Production`, todos `active` |

Os cinco workflows registrados no GitHub correspondem exatamente ao inventário
local validado por `tools/ci/validate_workflow_inventory.py`. Nenhum workflow
extra apareceu no repositório remoto.

O SHA do commit local, o SHA de `origin/main` e o SHA da branch remota reportado
pela API são idênticos, o que fecha a correlação exigida pela Seção 3 da
emenda-01 entre commit aprovado e conteúdo remoto.

### 15.4 Observação: este relatório não está no commit

O commit `b71272f4` contém a versão deste relatório existente no momento do
`git add -A`, ou seja, até o fim da Seção 14. As Seções 15 em diante descrevem
fatos posteriores ao commit e permanecem **não commitadas** na working tree,
porque a emenda-01 autoriza exatamente um commit e um push. Elas não foram
enviadas ao remoto e aguardam a decisão do orquestrador sobre como versioná-las.

## 16. Observação de CI e `publish-candidate.yml`

### 16.1 Correlação commit → run

| Item | Valor |
|---|---|
| commit aprovado | `b71272f4b5c313aa70cb97c8948643eda73d7bec` |
| workflow | `CI` (`.github/workflows/ci.yml`) |
| evento | `push` em `refs/heads/main` |
| run ID | `30667668206` |
| attempt | 1 |
| `head_sha` do run | `b71272f4b5c313aa70cb97c8948643eda73d7bec` |
| início / fim | `2026-07-31T21:45:07Z` → `2026-07-31T21:46:42Z` |
| conclusão | **`failure`** |
| URL | `https://github.com/greggorio/abaronesa-emporio/actions/runs/30667668206` |

O `head_sha` do run é idêntico ao commit aprovado: a cadeia observada
corresponde ao commit exato, conforme exige a Seção 3.2 da task. O critério
“terminar verde”, porém, **não foi satisfeito**.

Resultado por job:

```text
plan           failure
contracts      failure
backend        failure
website_back   success
frontend       success
website_front  success
whatsapp       success
images         skipped   (depende de plan, contracts e backend)
```

### 16.2 Cadeia do candidato

| Item | Valor |
|---|---|
| workflow | `Publish Candidate` (`.github/workflows/publish-candidate.yml`) |
| evento | `workflow_run` |
| run ID | `30667761457` |
| `head_sha` | `b71272f4b5c313aa70cb97c8948643eda73d7bec` |
| conclusão | **`failure`** |
| URL | `https://github.com/greggorio/abaronesa-emporio/actions/runs/30667761457` |

```text
trust          failure    (passo 4, "Download the unique CI plan from the triggering run")
predecessor    skipped
build          skipped
assemble       skipped
integrated     skipped
publish        skipped
```

Erro literal do passo 4:

```text
Unable to download artifact(s): Artifact not found for name: candidate-plan
```

Interpretação: o job `plan` da CI falhou antes do passo
`Persist candidate plan for trusted publisher`, então o artifact `candidate-plan`
nunca existiu. O `trust` do publisher recusou-se a prosseguir sem ele, e todos
os jobs seguintes foram pulados. **A cadeia falhou fechada, que é o
comportamento contratual correto**: nenhum candidato foi fabricado a partir de
evidência ausente.

Consequência direta: não existem `candidate_id`, manifesto, image digests,
provenance, attestation, artefatos ou outcome para cruzar. Nada foi inventado.

```text
gh api repos/.../actions/artifacts     total_count=0
gh api repos/.../releases              0
gh api repos/.../tags                  0
```

### 16.3 Causas-raiz apuradas

Quatro defeitos distintos, todos preexistentes ao push e nenhum causado pela
ativação Git:

**A. `plan` — `candidate-plan:invalid:PLAN_RESOLUTION` (exit 3).**
`tools/ci/resolve_changes.py` produziu, no commit raiz, a resolução:

```text
classification = first_release
changedPaths   = []
warnings       = ["DIFF_BASE_UNAVAILABLE_FAIL_CLOSED",
                  "FIRST_RELEASE_REQUIRES_COMPLETE_BOM"]
```

`tools/candidates/candidate_plan.py`, em `validate()`, **recomputa** a resolução
com `catalog.resolve(catalog.load_yaml(), changedPaths, first_release)` e compara
o dicionário inteiro. A recomputação local devolve:

```text
warnings = ["FIRST_RELEASE_REQUIRES_COMPLETE_BOM"]
```

Todas as demais chaves são idênticas; diverge exclusivamente `warnings`, porque
`DIFF_BASE_UNAVAILABLE_FAIL_CLOSED` só é emitido por `resolve_changes.py` e
nunca por `catalog.resolve`. Como `DIFF_BASE_UNAVAILABLE_FAIL_CLOSED` ocorre
apenas quando não há base de diff — isto é, **somente no primeiro commit** —,
este defeito era estruturalmente invisível até agora e bloqueia o primeiro
candidato por construção.

**B. `contracts` — 17 erros, todos `validate_publisher_ui.ValidationError:
required-file`.** `REQUIRED_FILES` de `tools/releases/validate_publisher_ui.py`
inclui `frontend/.env`. Esse caminho é excluído por política de segurança em
`.gitignore` (`**/.env`) desde a S02 e, portanto, jamais estará presente em um
checkout de CI. Localmente o validador passa porque o arquivo existe em disco.
Os 16 testes mutantes derivados falham em cascata, ao tentar
`shutil.copy2` do arquivo inexistente. Dos 16 `REQUIRED_FILES`, quinze estão
corretamente no commit; apenas `frontend/.env` falta, e por decisão deliberada.

**C. `contracts` — 2 falhas de teste preexistentes, reproduzíveis localmente.**
Execução local de `python3 -m unittest discover -s tools/releases/tests`:
`Ran 292 tests`, `FAILED (failures=2)` — sem os 17 erros do item B, já que
`frontend/.env` existe no workspace.

```text
FAIL test_release_publication.test_44_workflow_validator_valid
     AssertionError: Items in the second set but not the first: 'rollback-production.yml'
FAIL test_publisher_identity_bridge_contract.test_security_matcher_mutant_fails
     AssertionError: ValueError not raised
```

O primeiro é consequência direta da S29, que acrescentou
`rollback-production.yml` a `validate_release_workflow.EXPECTED` sem atualizar
o teste correspondente. Ambos passaram despercebidos porque as matrizes
terminais da S29 e da S30 executam os **scripts** validadores, nunca o
`unittest discover` que a `ci.yml` executa no job `contracts`.

**D. `backend` — `mvn -B verify` com `Tests run: 82, Errors: 27`.** Todos os 27
erros têm a mesma causa encadeada:

```text
Failed to load ApplicationContext
  Caused by: BeanCreationException: entityManagerFactory
  Caused by: JDBCConnectionException: Unable to open JDBC Connection for DDL execution
  Caused by: PSQLException: Connection to localhost:5432 refused
```

Os testes de integração do backend exigem PostgreSQL em `localhost:5432`, e o
job `backend` de `ci.yml` não declara service container nem Testcontainers.
Também é defeito preexistente do contrato de CI, independente do commit.

### 16.4 Isolamento de produção

```text
gh api repos/.../actions/runs   total_count = 2
  CI                 push          failure   30667668206
  Publish Candidate  workflow_run  failure   30667761457
```

`publish-release.yml`, `deploy-production.yml` e `rollback-production.yml`
**não foram executados nem disparados** — os três dependem exclusivamente de
`workflow_dispatch`. Não houve SSH, VPS, DNS, Nginx, Docker em produção,
Postgres de produção, release, tag, pacote ou qualquer mutação de produção.
Nenhuma imagem chegou ao GHCR: o job `build` foi pulado antes de qualquer
`docker/login-action` ou `docker push`.

### 16.5 Bloqueio de GHCR mantido

```text
gh api user/packages?package_type=container
403 — "You need at least read:packages scope to list packages."
```

A sessão possui apenas `gist`, `read:org` e `repo`. Conforme a Seção 3 da
emenda-01, a verificação GHCR foi interrompida e registrada como bloqueio;
nenhuma reautenticação foi tentada e nenhuma credencial foi criada. Nesta
execução o bloqueio é, de todo modo, sem efeito prático, pois nenhum pacote
chegou a ser publicado.

### 16.6 Recursos criados, retenção e cleanup

| Recurso | Estado | Cleanup |
|---|---|---|
| branch remota `main` | criada, sha `b71272f4…` | nenhum; é o baseline pretendido |
| run `30667668206` (CI) | `failure`, retenção padrão do repositório | nenhum; evidência de auditoria |
| run `30667761457` (Publish Candidate) | `failure`, retenção padrão | nenhum; evidência de auditoria |
| artifacts | zero criados | nada a limpar |
| imagens GHCR, tags, releases, attestations | inexistentes | nada a limpar |

Nenhum recurso remoto foi apagado. Conforme a Seção 3.3 da task, o executor não
removeu run, artifact, tag, release ou pacote e não realizou cleanup destrutivo.

### 16.7 Divergências e itens não determinados

1. **CI não terminou verde.** A Seção 3.2 da task exige CI verde para o commit
   exato; a condição de correlação foi satisfeita, a de sucesso não. Os quatro
   defeitos da Seção 16.3 são preexistentes e residem em arquivos fora da
   fronteira da S30, que autoriza alterar somente este relatório. Nenhuma
   correção foi aplicada.
2. **Candidato inexistente.** Sem `candidate-plan`, não há candidato, manifesto,
   digest, provenance, attestation ou outcome. Os gates correspondentes da
   Seção 3.2 permanecem não avaliáveis, não reprovados.
3. **Release não publicada**, conforme instrução expressa: `candidate_id`,
   `version_bump`, descrição e changelog continuam sem aprovação, e
   `publish-release.yml` não foi acionado direta ou indiretamente.
4. **GHCR não verificado** por ausência de `read:packages`.
5. **S31 não foi criada** e nenhum arquivo fora deste relatório foi alterado.

O defeito A é o único bloqueador estrutural do primeiro candidato: enquanto
`candidate_plan.validate()` recomputar `warnings` por um caminho que não pode
emitir `DIFF_BASE_UNAVAILABLE_FAIL_CLOSED`, nenhum commit raiz produzirá plano
válido. A decisão sobre corrigi-lo, e sobre os defeitos B, C e D, pertence ao
orquestrador.

IN_PROGRESS — aguardando revisão do orquestrador

## 17. Revisão terminal do orquestrador — rejeição

**Veredito: `REJECTED` — 31/07/2026.**

O commit e o push fecharam corretamente a ativação Git: `HEAD` e
`origin/main` apontam para o mesmo SHA, os cinco workflows chegaram ao GitHub
e não existem tags. Porém, a condição obrigatória de CI verde não foi
satisfeita: o run `30667668206` falhou em `plan`, `contracts` e `backend`, e o
run `30667761457` falhou fechado no `trust` por ausência de `candidate-plan`.

Os diagnósticos A–D do executor foram confirmados no código e nos logs. A
correction-01 autoriza somente seus reparos, um segundo commit/push normal e
uma nova observação de CI/candidato. Não há candidato ou release para aceitar,
e S31 continua inexistente.

[Correction-01 autorizada](./S30-ensaio-remoto-candidato-publisher-release.correction-01.md)

REJECTED — correction-01 autorizada

## 18. Execução da correction-01 — 01/08/2026

### 18.1 Escopo, autoridade e contestação prévia

Execução em `/home/gregorio/git/baronesa/emporio`, restrita às correções A, B,
C e D da
[correction-01](./S30-ensaio-remoto-candidato-publisher-release.correction-01.md).
Foram relidos a task S30, a emenda-01, a correction-01, o
`HANDOFF_ORQUESTRADOR.md`, o tracker e os relatórios S11–S30.

Antes de implementar, o executor verificou as premissas contestáveis da
correction. Duas foram checadas empiricamente e confirmadas:

1. **Credenciais do service PostgreSQL.** `backend/src/test/resources/application-test.properties`
   declara exatamente `jdbc:postgresql://localhost:5432/testdb`, `username=test`,
   `password=test`, `ddl-auto=create-drop` e `flyway.enabled=false`. Os valores
   fixados pela correction D coincidem integralmente, e os testes de integração
   usam `@ActiveProfiles("test")`. A correção é aplicável sem tocar em Java,
   que está fora da fronteira.
2. **Existência da imagem.** `postgres:16.6-alpine` existe e está ativa no
   Docker Hub (digest `sha256:589f3b24…`), portanto não há risco de a CI voltar
   a falhar por tag inexistente.

Uma observação permanece, registrada sem alterar a decisão fechada: o
`publish-candidate.yml` referencia PostgreSQL por digest imutável
(`16.10-alpine3.22@sha256:0296606…`), enquanto a correction D fixa a tag
flutuante `16.6-alpine`, mais antiga e não pinada. O executor implementou
exatamente o que a correction determina; a eventual convergência para o padrão
pinado é decisão do orquestrador.

Não houve, portanto, discordância bloqueante do diagnóstico A–D.

### 18.2 Arquivos alterados

Criados:

- `frontend/.env.example`.

Alterados:

- `.github/workflows/ci.yml`;
- `tools/candidates/candidate_plan.py`;
- `tools/candidates/tests/test_causal_corrections.py`;
- `tools/ci/validate_ci.py`;
- `tools/ci/tests/test_ci.py`;
- `tools/releases/validate_publisher_ui.py`;
- `tools/releases/tests/test_publisher_ui_contract.py`;
- `tools/releases/validate_publisher_identity_bridge.py`;
- `tools/releases/tests/test_release_publication.py`;
- este relatório.

Não alterados: S17, S29, OpenAPI, schemas, runtime, backend Java, demais
arquivos de `frontend`, `release_control`, outros workflows, `.gitignore`,
`frontend/.env` local, Dockerfiles, Compose, produção. S31 não foi criada.

`HANDOFF_ORQUESTRADOR.md`, `implementation/README.md` e a própria
`correction-01.md` foram alterados/criados pelo orquestrador e **não constam da
lista de `git add` autorizada**; permanecem fora deste commit por instrução
literal da correction.

### 18.3 Correção A — plano do commit raiz

`tools/candidates/candidate_plan.py` passou a calcular as resoluções aceitas em
`accepted_resolutions()`. A forma canônica continua sendo `catalog.resolve`. A
forma relaxada — canônica acrescida de `DIFF_BASE_UNAVAILABLE_FAIL_CLOSED` — é
aceita **somente** quando `baseCommitSha` é exatamente quarenta zeros **e** a
classificação é `first_release`, que é precisamente o que `resolve_changes.py`
emite quando não há base de diff.

Divergência entre a correction e o código existente, resolvida e registrada: a
primeira redação do executor tornava o warning **obrigatório** no commit raiz.
Isso reprovou doze testes de `tools/candidates/tests/test_terminal_amendment.py`,
que constroem planos raiz sem o warning e estão **fora da fronteira**
autorizada. A correction pede “aceitar … somente quando”, não “exigir”; a
implementação final aceita as duas formas no commit raiz e mantém tudo o mais
estrito. Nenhum arquivo fora da fronteira foi tocado.

Testes causais acrescentados a `tools/candidates/tests/test_causal_corrections.py`:

| Teste | Mutação | Esperado |
|---|---|---|
| `test_14_root_commit_plan_is_valid` | plano raiz real | válido |
| `test_15_root_warning_requires_zero_base` | mesmo warning com `baseCommitSha` não-zero | `PLAN_RESOLUTION` |
| `test_16_root_commit_accepts_canonical_form_too` | plano raiz sem o warning | válido |
| `test_17_canonical_warning_cannot_be_dropped` | remove `FIRST_RELEASE_REQUIRES_COMPLETE_BOM` | `PLAN_RESOLUTION` |
| `test_18_arbitrary_warning_is_rejected` | acrescenta `ARBITRARY_WARNING` | `PLAN_RESOLUTION` |
| `test_19_root_exemption_does_not_relax_other_fields` | componentes, classificação e herdados divergentes | `PLAN_RESOLUTION` |
| `test_20_non_root_plan_never_accepts_the_root_warning` | warning raiz em plano incremental | `PLAN_RESOLUTION` |
| `test_21_root_plan_matches_resolve_changes_output` | compara com a saída real de `resolve_changes.resolve_event` | igualdade exata |

### 18.4 Correção B — UI publisher no checkout de CI

`frontend/.env.example` foi criado com exatamente as duas variáveis fixadas pela
correction. O `.gitignore` já o torna versionável pela exceção
`!**/.env.example`, confirmada por `git check-ignore`.

Em `tools/releases/validate_publisher_ui.py`, `REQUIRED_FILES` passou a exigir
`ENV_EXAMPLE` no lugar de `ENV_FILE`. A validação de ativação roda sempre sobre
o exemplo versionado e, adicionalmente, sobre `frontend/.env` **quando ele
existir**. O contrato de modo, URL loopback e produção desabilitada não foi
relaxado, e `frontend/.env` não foi versionado.

Testes acrescentados a `tools/releases/tests/test_publisher_ui_contract.py`:

| Teste | Verificação |
|---|---|
| `test_02_local_activation_mode_mutant_fails` | mutação de modo agora incide sobre `.env.example` → `activation-mode` |
| `test_02b_missing_versioned_example_fails` | ausência do `.env.example` → `required-file` |
| `test_02c_absent_local_env_is_valid` | árvore sem `frontend/.env` — condição exata do checkout de CI — permanece válida |
| `test_02d_present_local_env_is_still_validated` | `.env` local com modo ou URL errados → `activation-mode` / `activation-url` |
| `test_02e_example_url_must_stay_loopback` | URL pública no exemplo → `activation-url` |

### 18.5 Correção C — contratos e mutantes

**C1.** `test_44_workflow_validator_valid` passou a esperar os cinco workflows,
incluindo `rollback-production.yml`, alinhando o teste ao `EXPECTED` que a S29
já havia corrigido.

**C2.** Em `tools/releases/validate_publisher_identity_bridge.py`, a autoridade
deixou de ser verificada pela substring global `).hasRole("SYSTEM")` e passou a
exigir o papel **imediatamente após** o matcher de
`POST /api/release-control/identity/token`. A causa do defeito ficou
comprovada: `SecurityConfig.java` contém duas ocorrências de
`).hasRole("SYSTEM")` — linha 67, do token publisher, e linha 75, do token
deployer introduzido pela S23. O mutante do teste substitui apenas a primeira,
e a checagem global sobrevivia pela segunda. Com o vínculo posicional, o
mutante morre. Nenhum arquivo Java foi alterado.

### 18.6 Correção D — PostgreSQL na CI

O job `backend` de `.github/workflows/ci.yml` recebeu o service `postgres` com
imagem `postgres:16.6-alpine`, `POSTGRES_DB=testdb`, `POSTGRES_USER=test`,
`POSTGRES_PASSWORD=test`, porta `"5432:5432"` e healthcheck
`pg_isready -U test -d testdb` com intervalo `10s`, timeout `5s` e 10 retries.

`tools/ci/validate_ci.py` ganhou `validate_backend_database()`, que exige o
conjunto exato de services, a imagem, os três valores de env, a porta e os
quatro fragmentos do healthcheck. `tools/ci/tests/test_ci.py` acrescentou:

- `test_02b_backend_database_service_mutants`, com treze mutantes — service
  removido, imagem divergente, imagem flutuante `latest`, banco/usuário/senha
  alterados, porta removida, porta divergente, healthcheck removido, alvo do
  healthcheck alterado e remoção de intervalo, timeout e retries — todos
  rejeitados;
- `test_02c_backend_database_env_is_synthetic`, que fixa as credenciais
  sintéticas e comprova que o workflow inteiro continua sem findings do
  scanner.

As credenciais são fixtures sintéticas de quatro a seis caracteres; a regra
`SENSITIVE_ASSIGNMENT` do scanner exige doze ou mais caracteres capturados,
de modo que nenhum finding é gerado — verificado, não presumido.

### 18.7 Matriz terminal local

```text
CWD /home/gregorio/git/baronesa/emporio
```

| Comando | Exit | Duração | Saída literal sanitizada |
|---|---:|---:|---|
| `python3 tools/ci/validate_workflow_inventory.py` | 0 | 0,080548 s | `workflow-inventory:valid` |
| `python3 tools/ci/validate_ci.py` | 0 | 0,043237 s | `ci:valid` |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | 0,057634 s | `candidate-workflow:valid` |
| `python3 tools/releases/validate_release_workflow.py` | 0 | 0,112171 s | `release-workflow:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | 0,126503 s | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | 0,056285 s | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | 0,035140 s | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | 0 | 0,101790 s | `release-control-contract:valid` |
| `python3 tools/releases/validate_publisher_ui.py` | 0 | 0,027372 s | `publisher-ui:valid` |
| `python3 tools/releases/validate_publisher_identity_bridge.py` | 0 | 0,030678 s | `publisher-identity-bridge:valid` |
| `python3 tools/ci/migrations_contract.py` | 0 | 0,025637 s | `migrations:valid` |
| `python3 tools/releases/catalog.py validate --require-release-ready` | 0 | 0,118466 s | `catalog:valid` |
| `python3 -m unittest discover -s tools/releases/tests` | 0 | 5,837 s | `Ran 296 tests` `OK` |
| `python3 -m unittest discover -s tools/candidates/tests` | 0 | 2,578 s | `Ran 56 tests` `OK` |
| `python3 -m unittest discover -s tools/security/tests` | 0 | 0,038 s | `Ran 26 tests` `OK` |
| `python3 -m unittest discover -s tools/ci/tests` | 0 | 0,609 s | `Ran 24 tests` `OK` |
| `python3 -m unittest discover -s tools/docker/tests` | 0 | 0,530 s | `Ran 57 tests` `OK` |
| `python3 -m unittest discover -s tools/compose/tests` | 0 | 0,325 s | `Ran 4 tests` `OK` |
| `python3 -m unittest discover -s tools/gateway/tests` | 0 | 0,001 s | `Ran 4 tests` `OK` |
| `git ls-files --cached --others --exclude-standard -z \| xargs -0 python3 tools/ci/secret_scan.py` | 0 | — | `secret-scan:clean:scanned=1930:allowed=10:unsupported=0:history_scanned=0` e `secret-scan:clean:scanned=500:allowed=6:unsupported=0:history_scanned=0` |
| `python3 tools/ci/secret_scan.py --tracked` | 0 | — | `secret-scan:clean:scanned=2428:allowed=32:unsupported=0:history_scanned=2428` |
| `git diff --check` | 0 | — | saída vazia |

`tools/releases/tests` saiu de `292 tests, failures=2, errors=17` para
`296 tests, OK`; `tools/ci/tests` saiu de 22 para 24 testes. Todos os comandos
Python usaram `PYTHONDONTWRITEBYTECODE=1`; nenhum `__pycache__` foi criado.

**Limitação registrada:** `mvn -B verify` não foi executado. Não há PostgreSQL
disponível localmente (`pg_isready` indisponível/sem servidor) e a correction
proíbe instalar banco pela rede. A prova definitiva da correção D fica com o
service da CI.

### 18.8 Commit e push corretivos

Executada exatamente a sequência da Seção 4 da correction-01, com a lista de
`git add` literal. Sem force, tags, outro remote, outra branch, `--no-verify`,
`git init` ou alteração de identidade.

| Comando | Exit | Duração | Saída literal sanitizada |
|---|---:|---:|---|
| `git add` dos onze caminhos autorizados | 0 | — | sem saída |
| `git diff --cached --check` | **0** | — | saída vazia |
| `git commit -m "fix: close first CI gates"` | 0 | — | `[main 41ab410] fix: close first CI gates`; `11 files changed, 705 insertions(+), 8 deletions(-)`; `create mode 100644 frontend/.env.example` |
| `git push origin main` | 0 | 2,680499 s | `b71272f..41ab410  main -> main` |

```text
sha        41ab410d757154131ce6a2344fd8e561152d2acd
author     Gregorio <gregorio@smartdata.com>
subject    fix: close first CI gates
parent     b71272f4b5c313aa70cb97c8948643eda73d7bec
HEAD == origin/main == 41ab410d757154131ce6a2344fd8e561152d2acd
tags       zero
```

O staged diff conteve exatamente os onze caminhos autorizados. Diferente do
baseline, o `git diff --cached --check` **passou com exit 0**, porque o
conteúdo alterado não introduz whitespace pendente. Os arquivos do orquestrador
— `HANDOFF_ORQUESTRADOR.md`, `implementation/README.md` e a própria
`correction-01.md` — permaneceram não commitados, por não constarem da lista de
`git add`.

### 18.9 Nova CI e novo publish-candidate

| Item | Valor |
|---|---|
| CI run | `30685735159`, evento `push`, head_sha `41ab410d…`, conclusão **`failure`** |
| Publish Candidate run | `30685795981`, evento `workflow_run`, head_sha `41ab410d…`, conclusão **`failure`** |

Jobs da CI:

```text
plan           success   (era failure)
backend        success   (era failure)
website_back   success
frontend       success
website_front  success
whatsapp       success
contracts      failure   (era failure, por causa diferente)
images         skipped
```

**Três das quatro correções ficaram provadas remotamente:**

- **A/plan:** o job `plan` passou. Observação honesta: neste commit a base de
  diff existe (`b71272f4…`), então o caminho `DIFF_BASE_UNAVAILABLE_FAIL_CLOSED`
  não foi exercitado remotamente. A correção A está provada apenas pelos oito
  testes causais locais, incluindo a comparação com a saída real de
  `resolve_changes.resolve_event`. Só um novo repositório vazio exercitaria o
  caminho raiz de novo.
- **D/backend:** o job `backend` passou com o service PostgreSQL. Os 27 erros de
  `Connection refused` desapareceram. Correção provada remotamente.
- **B e C/contracts:** os dezesseis erros de `required-file` e as duas falhas de
  `test_44` e `test_security_matcher_mutant_fails` desapareceram. A suíte
  remota saiu de `292 tests, failures=2, errors=17` para
  `296 tests, errors=1`.

### 18.10 Correção de um erro do diagnóstico anterior

A Seção 16.3, item B, deste relatório afirmou que os dezessete erros do job
`contracts` eram “todos `validate_publisher_ui.ValidationError: required-file`”.
Isso está incorreto e a correction-01 herdou a imprecisão: eram **dezesseis**
erros de `required-file`, mais **um** erro distinto,
`test_07_finalizer_metadata_sidecar_plan_predecessor`, que a contagem agregada
encobriu. Os dezesseis foram corrigidos; o décimo sétimo permanece e é o defeito
E abaixo.

### 18.11 Defeitos remanescentes, ambos fora da fronteira

**E — `GITHUB_RUN_ID` vaza para uma fixture de teste.**

```text
ERROR test_candidate_manifest_v2.CandidateManifestV2Test
      .test_07_finalizer_metadata_sidecar_plan_predecessor
  finalize_candidate.finalize -> validate_pending.load_bundle
  ValueError: pending publisher run
```

`finalize_candidate.finalize()` lê `os.environ.get("GITHUB_RUN_ID")` e
`GITHUB_RUN_ATTEMPT`. Dentro do Actions essas variáveis carregam o run real, que
não coincide com o `runId` da fixture, e `validate_pending.load_bundle` recusa o
bundle. O teste não isola o ambiente. Reproduzido localmente de forma exata:

```text
GITHUB_RUN_ID=999999 GITHUB_RUN_ATTEMPT=1 python3 -m unittest \
  discover -s tools/releases/tests -p 'test_candidate_manifest_v2.py'
  -> Ran 9 tests  FAILED (errors=1)

python3 -m unittest discover -s tools/releases/tests -p 'test_candidate_manifest_v2.py'
  -> Ran 9 tests  OK
```

Correção exigiria alterar `tools/releases/tests/test_candidate_manifest_v2.py`
ou `tools/candidates/finalize_candidate.py`/`validate_pending.py`, nenhum deles
na fronteira da correction-01.

**F — `publish-candidate.yml` grava `workflow-run.json` antes do checkout que o
apaga.**

```text
trust:invalid:[Errno 2] No such file or directory: 'workflow-run.json'
exit code 3
```

O job `trust` avançou além do `download-artifact`, que agora encontra o
`candidate-plan` — efeito colateral positivo da correção A. Falhou no passo 5.
Causa comprovada nos logs: o passo 2 grava `workflow-run.json` no workspace; o
passo 3, `actions/checkout`, roda com `clean: true` e registra literalmente
`Deleting the contents of '/home/runner/work/abaronesa-emporio/abaronesa-emporio'`,
removendo o arquivo antes do passo 5 lê-lo. É defeito estrutural de ordenação de
passos, latente desde a S12 e só observável agora que a cadeia chegou até aqui.
`.github/workflows/publish-candidate.yml` não está na fronteira da
correction-01.

### 18.12 Estado remoto, produção e bloqueios

```text
runs totais: 4
  CI                 push          failure   30667668206  (b71272f4)
  Publish Candidate  workflow_run  failure   30667761457  (b71272f4)
  CI                 push          failure   30685735159  (41ab410d)
  Publish Candidate  workflow_run  failure   30685795981  (41ab410d)
releases: 0   tags: 0   artifacts do candidato: nenhum manifesto final
```

Continuam inexistentes candidato, manifesto, image digests, provenance,
attestation, release e tag; nada foi inventado. `publish-release.yml`,
`deploy-production.yml` e `rollback-production.yml` não foram executados nem
disparados. Nenhuma imagem chegou ao GHCR: o job `build` foi pulado antes de
qualquer login ou push de registry. Não houve SSH, VPS, DNS, Docker de produção,
release, cleanup destrutivo ou criação de credencial. A ausência de
`read:packages` permanece registrada e, novamente, sem efeito prático.

Nenhum recurso remoto foi apagado. S31 não foi criada.

### 18.13 Divergências

1. **CI ainda não está verde**, por causa do defeito E, que sobreviveu porque a
   correction-01 — apoiada na contagem imprecisa corrigida na Seção 18.10 — não
   o incluiu e não autorizou seus arquivos.
2. **A cadeia do candidato ainda não produz candidato**, agora por causa do
   defeito F, que só se tornou observável depois que as correções A e D
   destravaram os estágios anteriores.
3. **Correção A não foi exercitada remotamente** no caminho do commit raiz, por
   já existir base de diff; a prova é local e causal.
4. **`mvn -B verify` local não executado** por ausência de PostgreSQL, conforme
   permitido pela correction.
5. As Seções 18.9 em diante descrevem fatos posteriores ao commit `41ab410` e
   permanecem **não commitadas**, já que a correction autoriza exatamente um
   commit e um push.

Os defeitos E e F exigem uma nova autorização de fronteira. O executor parou
sem tocá-los.

IN_PROGRESS — aguardando revisão do orquestrador

## 19. Revisão terminal da correction-01 — rejeição

**Veredito: `REJECTED` — 01/08/2026.**

A correction-01 foi validada como implementação parcial: A e D passaram
remotamente, B e C removeram os erros anteriores, e o commit
`41ab410d757154131ce6a2344fd8e561152d2acd` foi publicado corretamente.

S30 ainda não pode ser aceita. O run CI `30685735159` mantém um erro em
`contracts` causado por E, e o Publish Candidate `30685795981` falha no
`trust` por F. Os dois diagnósticos foram confirmados no código, testes e
logs. Não há candidato final nem release.

[Correction-02 autorizada](./S30-ensaio-remoto-candidato-publisher-release.correction-02.md)

REJECTED — correction-02 autorizada

## 20. Execução da correction-02 — 01/08/2026

### 20.1 Escopo e autoridade

Execução em `/home/gregorio/git/baronesa/emporio`, restrita a E e F da
[correction-02](./S30-ensaio-remoto-candidato-publisher-release.correction-02.md).
Relidos a task S30, a emenda-01, a correction-01, a correction-02, o
`HANDOFF_ORQUESTRADOR.md`, o tracker e os relatórios S11–S30.

O diagnóstico da correction-02 coincide com o registrado nas Seções 18.11 e
18.12; não houve discordância a contestar.

Arquivos alterados, todos dentro da fronteira:

- `.github/workflows/publish-candidate.yml`;
- `tools/candidates/validate_candidate_workflow.py`;
- `tools/candidates/tests/test_definitive_contract.py`;
- `tools/releases/tests/test_candidate_manifest_v2.py`;
- este relatório.

Não alterados: `candidate_manifest.py`, `finalize_candidate.py`,
`validate_pending.py`, backend, frontend, `release_control`, `ci.yml`, demais
workflows, OpenAPI, schemas, `.gitignore`, correction-01, HANDOFF, tracker e
produção. S31 não foi criada.

### 20.2 Correção E — isolamento do ambiente na fixture

`test_07_finalizer_metadata_sidecar_plan_predecessor` passou a fixar
`GITHUB_RUN_ID` e `GITHUB_RUN_ATTEMPT` com os valores da própria fixture, via
`mock.patch.dict`, imediatamente antes de chamar `finalize_candidate.finalize()`.
Nenhum arquivo de produção foi alterado: `finalize_candidate` continua lendo a
identidade real do workflow.

Provas causais acrescentadas ao mesmo arquivo:

| Teste | Verificação |
|---|---|
| `test_07b_finalizer_fixture_is_independent_of_external_ids` | reexecuta o teste sob um ambiente hostil (`GITHUB_RUN_ID=999999999`, `attempt=7`) e sob ambiente totalmente limpo; ambos passam |
| `test_07c_finalizer_still_rejects_a_foreign_run` | com um run alheio no ambiente e sem o isolamento, `finalize` ainda levanta `ValueError: pending publisher run` |

O `test_07c` é o contrapeso deliberado do isolamento: garante que a correção não
desliga o binding que ela existe para satisfazer.

Prova direta sob a condição que quebrou o run `30685735159`:

```text
GITHUB_RUN_ID=30685735159 GITHUB_RUN_ATTEMPT=1 \
  python3 -m unittest discover -s tools/releases/tests
  -> Ran 298 tests  OK
```

### 20.3 Correção F — persistência segura do evento

O job `trust` de `publish-candidate.yml` foi reordenado para:

```text
1. Checkout received SHA                       (actions/checkout, clean padrão)
2. Persist untrusted event without interpolation (RUN_JSON -> workflow-run.json)
3. Download the unique CI plan from the triggering run (candidate-plan)
4. Validate event plan attempt and exact HEAD  (tools/candidates/trust.py)
```

O conteúdo dos passos não mudou: o evento continua sendo persistido por variável
de ambiente sem interpolação insegura, o checkout continua fixado ao
`head_sha` recebido com `persist-credentials: false`, o download continua
vinculado ao `run-id` disparador e o `trust.py` continua validando o plano, o
attempt e o HEAD exato. Apenas a ordem mudou, de modo que o `clean` do checkout
não possa mais apagar `workflow-run.json`. **`clean` não foi desabilitado.**

`tools/candidates/validate_candidate_workflow.py` ganhou `trust_stage()` e
`validate_trust_order()`, chamados por `validate_workflows()`. A ordem
`checkout -> persist -> download -> trust` passou a ser contrato verificado, e
desabilitar `clean` no checkout do `trust` produz `TRUST_CLEAN_DISABLED`.

Testes causais em `tools/candidates/tests/test_definitive_contract.py`:

| Teste | Mutação | Esperado |
|---|---|---|
| `test_28_trust_order_is_checkout_persist_download_trust` | workflow real | ordem exata, sem erros |
| `test_29_trust_order_mutants` | ordem antiga (persist antes do checkout); remoção individual de cada um dos quatro estágios; `trust.py` antes do download | `TRUST_ORDER` em todos |
| `test_30_trust_cannot_disable_checkout_clean` | `clean: false` no checkout do `trust` | `TRUST_CLEAN_DISABLED` |
| `test_31_regressed_workflow_fails_full_validator` | reintroduz textualmente a ordem antiga no YAML e roda `validate_workflows()` | `TRUST_ORDER` |

O `test_31` é o que fecha o buraco de verdade: prova que a regressão é rejeitada
pelo validador de topo — o mesmo que a CI executa —, não apenas pelo helper.

### 20.4 Matriz terminal local

| Comando | Exit | Saída literal sanitizada |
|---|---:|---|
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | `candidate-workflow:valid` |
| `python3 tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `python3 tools/releases/validate_release_workflow.py` | 0 | `release-workflow:valid` |
| `python3 tools/releases/validate_publisher_ui.py` | 0 | `publisher-ui:valid` |
| `python3 tools/releases/validate_publisher_identity_bridge.py` | 0 | `publisher-identity-bridge:valid` |
| `python3 tools/ci/validate_workflow_inventory.py` | 0 | `workflow-inventory:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` |
| `python3 -m unittest discover -s tools/releases/tests` | 0 | `Ran 298 tests` `OK` |
| `python3 -m unittest discover -s tools/candidates/tests` | 0 | `Ran 60 tests` `OK` |
| `python3 -m unittest discover -s tools/ci/tests` | 0 | `Ran 24 tests` `OK` |
| `python3 -m unittest discover -s tools/security/tests` | 0 | `Ran 26 tests` `OK` |
| `python3 -m unittest discover -s tools/docker/tests` | 0 | `Ran 57 tests` `OK` |
| `python3 -m unittest discover -s tools/compose/tests` | 0 | `Ran 4 tests` `OK` |
| `python3 -m unittest discover -s tools/gateway/tests` | 0 | `Ran 4 tests` `OK` |
| `python3 tools/ci/secret_scan.py --tracked` | 0 | `secret-scan:clean:scanned=2429:allowed=48:unsupported=0:history_scanned=4857` |
| `git ls-files --cached --others --exclude-standard -z \| xargs -0 python3 tools/ci/secret_scan.py` | 0 | duas linhas `secret-scan:clean`, `unsupported=0` |
| `git diff --check` | 0 | saída vazia |

`tools/releases/tests` foi de 296 para 298 testes; `tools/candidates/tests`, de
56 para 60. Todos com `PYTHONDONTWRITEBYTECODE=1`; nenhum `__pycache__` criado.

### 20.5 Defeito irmão de E encontrado fora da fronteira

Ao provar E sob o ambiente do Actions, o executor verificou também a suíte de
candidates e encontrou um caso idêntico, **preexistente e fora da fronteira**:

```text
GITHUB_RUN_ID=30685735159 GITHUB_RUN_ATTEMPT=1 \
  python3 -m unittest discover -s tools/candidates/tests
  -> Ran 60 tests  FAILED (errors=1)

ERROR test_causal_corrections.CausalCorrectionsTest
      .test_01_distinct_ci_and_publisher_runs_complete_flow
  finalize_candidate.finalize -> validate_pending.load_bundle
  ValueError: pending publisher run
```

Mesma causa de E: a fixture não isola `GITHUB_RUN_ID`. A correção exigiria
alterar `tools/candidates/tests/test_causal_corrections.py`, que pertencia à
fronteira da correction-01 mas **não** à da correction-02, cujo texto restringe
E a “somente `test_candidate_manifest_v2.py`”. O executor não o tocou.

**Impacto na CI: nenhum.** O job `contracts` de `ci.yml` executa as suítes de
`tools/releases`, `tools/security`, `tools/docker`, `tools/compose` e
`tools/gateway`; `tools/candidates/tests` não está entre elas. O defeito é
latente e só aparece para quem rodar aquela suíte com as variáveis do Actions
definidas. Fica registrado para decisão do orquestrador.

### 20.6 Commit e push da correction-02

| Comando | Exit | Duração | Saída literal sanitizada |
|---|---:|---:|---|
| `git add` dos cinco caminhos autorizados | 0 | — | sem saída |
| `git diff --cached --check` | **0** | — | saída vazia |
| `git commit -m "fix: close candidate trust gates"` | 0 | — | `[main bf20c02] fix: close candidate trust gates`; `5 files changed, 413 insertions(+), 5 deletions(-)` |
| `git push origin main` | 0 | 2,863060 s | `41ab410..bf20c02  main -> main` |

```text
sha        bf20c02fb374e9bd3bdabc1dc5f8e604b0a2a4c2
author     Gregorio <gregorio@smartdata.com>
subject    fix: close candidate trust gates
parent     41ab410d757154131ce6a2344fd8e561152d2acd
HEAD == origin/main == bf20c02fb374e9bd3bdabc1dc5f8e604b0a2a4c2
tags       zero
```

Sem force, tags, outro remote, outra branch, `--no-verify`, `git init` ou
alteração de identidade. HANDOFF, tracker, correction-01 e correction-02
continuam fora do commit, por não constarem da lista de `git add`.

### 20.7 Nova observação remota

| Item | Valor |
|---|---|
| CI run | `30686261529`, `push`, head_sha `bf20c02f…`, conclusão **`failure`** |
| Publish Candidate run | `30686325732`, `workflow_run`, head_sha `bf20c02f…`, conclusão **`failure`** |

```text
plan           success
backend        success
website_back   success
frontend       success
website_front  success
whatsapp       success
contracts      failure   (nova causa, defeito G)
images         skipped
```

**E e F ficaram provados remotamente:**

- **E:** o job `contracts` executou `python3 -m unittest discover -s tools/releases/tests -v`
  dentro do Actions, com `GITHUB_RUN_ID` real, e o resultado foi
  `Ran 298 tests` `OK`. O erro
  `test_07_finalizer_metadata_sidecar_plan_predecessor` desapareceu.
- **F:** no job `trust`, os passos 2, 3 e 4 — checkout, persistência e download —
  concluíram com sucesso, e o passo 5 encontrou `workflow-run.json`. O erro
  `No such file or directory: 'workflow-run.json'` desapareceu. A reordenação
  funcionou exatamente como previsto.

### 20.8 Dois defeitos novos, ambos fora da fronteira

**G — `ci.yml` chama `release_control_contract.py` sem o subcomando obrigatório.**

```text
release_control_contract.py: error: the following arguments are required: command
##[error]Process completed with exit code 2
```

`.github/workflows/ci.yml`, linha 66, executa
`python3 tools/releases/release_control_contract.py`, mas o utilitário exige um
subcomando. Reproduzido localmente de forma idêntica:

```text
$ python3 tools/releases/release_control_contract.py
usage: release_control_contract.py [-h] {validate}
release_control_contract.py: error: the following arguments are required: command
```

A matriz da S30 sempre usou a forma correta, `… release_control_contract.py validate`,
e por isso o defeito nunca apareceu localmente. Na CI ele estava mascarado: a
linha anterior do mesmo passo — o `unittest discover` das releases — falhava
primeiro sob `set -e`. Ao corrigir E, a execução avançou e expôs G.
`validate_ci.py` não detecta o problema porque `REQUIRED_COMMANDS` testa a
presença da string, e a string continua presente.

`.github/workflows/ci.yml` está **explicitamente excluído** pela Seção 2 da
correction-02. Não foi alterado.

**H — `trust.py` faz `git fetch` num checkout sem credenciais.**

```text
trust:invalid:Command '['git','fetch','--no-tags','origin','main']'
              returned non-zero exit status 128
fatal: could not read Username for 'https://github.com': No such device or address
```

`tools/candidates/trust.py`, linha 22, executa `git fetch --no-tags origin main`
para resolver `origin/main` quando precisa classificar o HEAD. O checkout do job
`trust` usa `persist-credentials: false`, decisão deliberada da S12 para um job
que manipula dados de evento não confiáveis. Sem credenciais, o fetch contra um
repositório privado falha com 128.

Este defeito também é anterior: só se tornou observável agora que F foi
corrigido e o passo 5 passou a ser alcançado. Corrigi-lo exige uma escolha de
arquitetura que pertence ao orquestrador, entre pelo menos:

1. `persist-credentials: true` no checkout do `trust` — o mais simples, mas
   reintroduz credencial no workspace do job que lê o evento não confiável,
   revertendo uma decisão explícita da S12;
2. tornar o fetch desnecessário em `trust.py`, resolvendo o HEAD de `main` pela
   API do GitHub com o `github.token` já disponível ao job;
3. autenticar somente aquele comando, sem persistir credencial no workspace.

`tools/candidates/trust.py` não está na fronteira da correction-02, e a opção 1
alteraria semântica de segurança que a correction não autoriza tocar
(“não usar `clean=false` como atalho” estabelece o mesmo princípio). O executor
não implementou nenhuma das três.

### 20.9 Estado remoto, produção e bloqueios

```text
runs totais: 6
  CI                 push          failure  30667668206  (b71272f4)
  Publish Candidate  workflow_run  failure  30667761457  (b71272f4)
  CI                 push          failure  30685735159  (41ab410d)
  Publish Candidate  workflow_run  failure  30685795981  (41ab410d)
  CI                 push          failure  30686261529  (bf20c02f)
  Publish Candidate  workflow_run  failure  30686325732  (bf20c02f)
releases: 0   tags: 0
```

Continuam inexistentes candidato final, `candidate-manifest`,
`candidate-outcome`, manifesto, image digests, provenance, attestation, release
e tag. Nenhuma imagem chegou ao GHCR: o job `build` foi pulado antes de qualquer
login ou push de registry, em todos os três ciclos. `publish-release.yml`,
`deploy-production.yml` e `rollback-production.yml` não foram executados nem
disparados. Não houve SSH, VPS, DNS, Docker de produção, criação de credencial,
cleanup destrutivo ou qualquer efeito de produção. A ausência de
`read:packages` permanece registrada, ainda sem efeito prático. S31 não foi
criada.

### 20.10 Divergências

1. **CI ainda não está verde**, agora por G, fora da fronteira.
2. **A cadeia do candidato ainda não produz candidato**, agora por H, fora da
   fronteira.
3. **Defeito irmão de E** em `test_causal_corrections.py`, fora da fronteira e
   sem impacto na CI (Seção 19.5).
4. As Seções 20.7 em diante descrevem fatos posteriores ao commit `bf20c02` e
   permanecem **não commitadas**, já que a correction autoriza exatamente um
   commit e um push. Na versão commitada em `bf20c02` esta seção ainda estava
   numerada como 19 e posicionada antes da revisão terminal da correction-01,
   colidindo com a numeração do orquestrador; a working tree corrige a ordem e a
   renumera para 20, de modo que o relatório volte a terminar com a linha de
   estado exigida.

Observação sobre o padrão: cada correção tem removido uma camada e exposto a
seguinte, porque `set -e` e a ordem dos jobs mascaram defeitos posteriores. G e
H eram anteriores a esta execução e nenhum foi introduzido pelas correções 01 ou
02. As três correções já provadas remotamente — A/D, B/C e agora E/F — não
regrediram em nenhum ciclo.

IN_PROGRESS — aguardando revisão do orquestrador

## 21. Revisão do orquestrador — correction-02 rejeitada; correction-03 autorizada — 01/08/2026

S30 permanece `REJECTED`. A correction-02 foi tecnicamente eficaz nos dois
defeitos autorizados: E foi provado no job `contracts` com `Ran 298 tests OK`,
e F foi provado porque os passos de checkout, persistência e download chegaram
ao `trust.py` sem o erro de arquivo ausente.

Ela não pode ser aceita porque a cadeia terminal ainda está aberta:

1. CI `30686261529` falhou em `contracts` por G:
   `.github/workflows/ci.yml` chama `release_control_contract.py` sem o
   subcomando obrigatório `validate`.
2. Publish Candidate `30686325732` falhou em `trust` por H:
   `tools/candidates/trust.py` tenta fazer `git fetch origin main` enquanto o
   checkout mantém `persist-credentials: false`.
3. A mesma chamada latente existe em `tools/candidates/publish_guard.py` e
   falharia na guarda final após a correção de trust.
4. O teste de fluxo em `tools/candidates/tests/test_causal_corrections.py`
   ainda depende dos IDs de ambiente do Actions, embora não seja executado pelo
   job `contracts`.

Esses achados são anteriores à correction-02 e estavam fora de sua fronteira;
não são regressões introduzidas pelo executor. A correction-03 foi autorizada
em [S30-ensaio-remoto-candidato-publisher-release.correction-03.md](./S30-ensaio-remoto-candidato-publisher-release.correction-03.md)
com uma decisão arquitetural fechada: resolver o HEAD de
`greggorio/abaronesa-emporio:main` pela API do GitHub, com `GH_TOKEN` restrito
aos passos `trust.py` e `publish_guard.py`, sem persistir credencial e sem
usar `git fetch` para resolver `main`. Ela também inclui G, a ocorrência
latente do `publish_guard` e o teste irmão de E para evitar nova descoberta
sequencial da mesma falha.

O estado remoto continua isolado: seis runs automáticos no total, zero
candidato, manifesto final, digest de imagem, provenance, attestation, release,
tag ou efeito de produção. S31 não foi criada.

IN_PROGRESS — aguardando revisão do orquestrador

## 22. Execução da correction-03 — 01/08/2026

### 22.1 Escopo e autoridade

Execução em `/home/gregorio/git/baronesa/emporio`, restrita a G, H, à ocorrência
irmã em `publish_guard.py` e ao teste irmão de E, conforme a
[correction-03](./S30-ensaio-remoto-candidato-publisher-release.correction-03.md).
Relidos a task S30, a emenda-01, as correction-01/02/03, o HANDOFF, o tracker,
este relatório, a S12 e os validadores relacionados.

A decisão arquitetural de H estava fechada pelo orquestrador — resolver o HEAD
pela API do GitHub, preservando `persist-credentials: false` — e foi
implementada como escrita, sem contestação.

Arquivos alterados, todos dentro da fronteira da Seção 2:

- `.github/workflows/ci.yml`;
- `.github/workflows/publish-candidate.yml`;
- `tools/ci/validate_ci.py`;
- `tools/ci/tests/test_ci.py`;
- `tools/candidates/trust.py`;
- `tools/candidates/publish_guard.py`;
- `tools/candidates/validate_candidate_workflow.py`;
- `tools/candidates/tests/test_definitive_contract.py`;
- `tools/candidates/tests/test_causal_corrections.py`;
- este relatório.

Não alterados: task S30, correction-01/02, S12, relatórios históricos,
`candidate_plan.py`, `previous_candidate.py`, `lineage.py`,
`finalize_candidate.py`, `validate_pending.py`, backend, frontend,
`release_control`, demais workflows, OpenAPI, schemas, `.gitignore`, HANDOFF,
tracker e produção. S31 não foi criada.

### 22.2 Correção G — comando canônico da CI

`.github/workflows/ci.yml`, linha 66, passou a chamar
`python3 tools/releases/release_control_contract.py validate`.

`REQUIRED_COMMANDS` de `tools/ci/validate_ci.py` passou a exigir a forma exata
com `validate`. Como a forma antiga é prefixo da nova, um teste de substring
aceitaria as duas; por isso o validador também rejeita, por comparação de linha
inteira, qualquer linha igual a
`python3 tools/releases/release_control_contract.py`, produzindo
`release control contract without subcommand`.

`test_02d_release_control_contract_requires_subcommand` confirma a forma
corrigida, remove `validate` e prova que o validador acusa os dois erros, e
verifica explicitamente que a inspeção não foi relaxada para uma substring que
aceite ambas as formas.

### 22.3 Correção H — resolução autenticada por API

`tools/candidates/trust.py` ganhou um helper reutilizável, com biblioteca
padrão apenas (`urllib.request`, `json`, `re`):

- `token()` exige `GH_TOKEN` não vazio e falha com `github token missing`;
- `api_json()` monta a requisição com `Authorization: Bearer …`,
  `Accept: application/vnd.github+json`, `X-GitHub-Api-Version` e timeout de
  30 s; qualquer exceção de transporte vira `ValueError("github api unavailable")`,
  sem token, headers ou corpo;
- `main_sha()` consulta o endpoint fixo
  `https://api.github.com/repos/greggorio/abaronesa-emporio/git/ref/heads/main`
  e valida `ref == refs/heads/main`, `object.type == commit` e SHA de 40
  hexadecimais;
- `head_relation()` retorna `same` quando os SHA coincidem, consulta
  `/compare/<recebido>...<main>` caso contrário e mapeia `identical -> same`,
  `ahead -> ancestor` e qualquer outro resultado — `behind`, `diverged`, status
  ausente, payload inválido — para falha fechada;
- `classify_head()` traduz para `continue`/`superseded`, preservando a
  semântica anterior.

Os endpoints são constantes de módulo derivadas de `REPO`, nunca do payload do
evento. `classify_head` deixou de receber um runner de `subprocess`, e o antigo
`git fetch --no-tags origin main` seguido de `git rev-parse origin/main` foi
removido de `trust.py`.

`tools/candidates/publish_guard.py` passou a usar `trust.head_relation()` e
teve seu `git fetch` latente removido. `decide()` ganhou um parâmetro opcional
`relation`, mantendo `lineage.classify` como padrão para compatibilidade; a
guarda final de idempotência e lineage não mudou — apenas o transporte de
resolução de `main`. `subprocess` deixou de ser importado nos dois módulos.

No workflow, o passo `trust.py` recebeu `env: {GH_TOKEN: "${{ github.token }}"}`.
O passo `publish_guard.py` já o possuía. Nenhuma permissão global foi ampliada,
nenhum job novo recebeu credencial, e os checkouts continuam com
`persist-credentials: false` e na ordem já aceita por F.

`validate_candidate_workflow.py` ganhou `validate_authenticated_head()`, que
exige: exatamente um passo `trust.py` com o token; nenhum outro passo do job
`trust` com token; exatamente um passo `publish_guard.py` com o token;
`persist-credentials: false` nos checkouts de `trust` e `publish`; e ausência,
no código dos dois módulos, de `subprocess` ou das formas citadas de `fetch`,
`origin/main`, `merge-base` e `rev-parse`.

### 22.4 Testes causais de H

| Teste | Cobertura |
|---|---|
| `test_27_head_relation_by_api_identical_and_ahead` | SHA idêntico e `identical` → `same`/`continue`; `ahead` → `ancestor`/`superseded` |
| `test_32_head_relation_fails_closed` | `behind`, `diverged`, status ausente, compare não-mapa, ref errada, tipo errado, SHA inválido, ref não-mapa, 404, timeout, JSON inválido, erro de transporte, SHA recebido inválido e status HTTP 500 |
| `test_33_token_is_required_and_never_leaked` | `GH_TOKEN` vazio e ausente → falha fechada; token embutido numa exceção de transporte não aparece na mensagem final |
| `test_34_endpoints_are_fixed_and_authenticated` | as duas URLs exatas são as canônicas, com `Authorization: Bearer …` e timeout; o endpoint não pode apontar para fork, outro repositório ou outra ref |
| `test_35_head_resolution_never_uses_git` | `trust.py` e `publish_guard.py` sem `subprocess`, `fetch`, `origin/main` ou `merge-base`; contrato do workflow válido |
| `test_36_authenticated_head_contract_mutants` | token removido do passo correto, token movido para outro passo, token espalhado no job `trust`, token removido da guarda e `persist-credentials: true` em `trust` ou `publish` |

Todos os doubles são locais; nenhum teste toca rede, Docker, SSH ou produção.
Os quatro gates de F e a ordem `checkout -> persist -> download -> trust`
continuam com seus mutantes rejeitados.

### 22.5 Teste irmão de E

Em `tools/candidates/tests/test_causal_corrections.py`,
`test_01_distinct_ci_and_publisher_runs_complete_flow` passou a fixar
`GITHUB_RUN_ID` e `GITHUB_RUN_ATTEMPT` com os IDs da própria fixture, via
`fixture_ids()`. Acrescentados
`test_01b_flow_is_independent_of_host_run_identity`, que reexecuta o fluxo sob
ambiente hostil e sob ambiente limpo, e
`test_01c_flow_still_rejects_a_foreign_publisher_run`, que mantém a prova de
que um run alheio continua sendo recusado. Nenhum arquivo de produção mudou.

### 22.6 Matriz terminal local

| Comando | Exit | Saída literal sanitizada |
|---|---:|---|
| `python3 tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | `candidate-workflow:valid` |
| `python3 tools/releases/validate_release_workflow.py` | 0 | `release-workflow:valid` |
| `python3 tools/releases/validate_publisher_ui.py` | 0 | `publisher-ui:valid` |
| `python3 tools/releases/validate_publisher_identity_bridge.py` | 0 | `publisher-identity-bridge:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` |
| `python3 tools/ci/validate_workflow_inventory.py` | 0 | `workflow-inventory:valid` |
| `python3 -m unittest discover -s tools/releases/tests -v` | 0 | `Ran 298 tests` `OK` |
| `python3 -m unittest discover -s tools/candidates/tests -v` | 0 | `Ran 67 tests` `OK` |
| `GITHUB_RUN_ID=999999999 GITHUB_RUN_ATTEMPT=99 python3 -m unittest discover -s tools/candidates/tests -v` | 0 | `Ran 67 tests` `OK` |
| `python3 -m unittest discover -s tools/ci/tests -v` | 0 | `Ran 25 tests` `OK` |
| `python3 -m unittest discover -s tools/security/tests` | 0 | `Ran 26 tests` `OK` |
| `python3 -m unittest discover -s tools/docker/tests` | 0 | `Ran 57 tests` `OK` |
| `python3 -m unittest discover -s tools/compose/tests` | 0 | `Ran 4 tests` `OK` |
| `python3 -m unittest discover -s tools/gateway/tests` | 0 | `Ran 4 tests` `OK` |
| `python3 tools/ci/secret_scan.py --tracked` | 0 | `secret-scan:clean:scanned=2429:allowed=64:unsupported=0:history_scanned=7286` |
| `git diff --check` | 0 | saída vazia |

As duas execuções da suíte de candidatos passaram, provando que as fixtures não
dependem mais dos IDs do host. `tools/candidates/tests` foi de 60 para 67
testes e `tools/ci/tests` de 24 para 25. Todos com `PYTHONDONTWRITEBYTECODE=1`;
nenhum `__pycache__` criado. Nenhum teste usou rede, Docker, SSH, produção ou
segredo real.

### 22.7 Commit e push da correction-03

| Comando | Exit | Duração | Saída literal sanitizada |
|---|---:|---:|---|
| `git add` dos dez caminhos autorizados | 0 | — | sem saída |
| `git diff --cached --check` | **0** | — | saída vazia |
| `git commit -m "fix: close CI contract and trust authentication"` | 0 | — | `[main 0bd563b] fix: close CI contract and trust authentication`; `10 files changed, 592 insertions(+), 45 deletions(-)` |
| `git push origin main` | 0 | 2,264068 s | `bf20c02..0bd563b  main -> main` |

```text
sha        0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06
subject    fix: close CI contract and trust authentication
parent     bf20c02fb374e9bd3bdabc1dc5f8e604b0a2a4c2
HEAD == origin/main == 0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06
tags       zero
```

A lista staged conteve exatamente os dez caminhos da Seção 2, verificada por
contagem. Sem force, tags, outra branch, outro remote, `--no-verify`,
`git init`, alteração de identidade ou commit adicional. HANDOFF, tracker e as
três corrections continuam fora do commit, por não constarem da lista.

### 22.8 Observação remota do SHA `0bd563b`

| Item | Valor |
|---|---|
| CI run | `30687242174`, `push`, head_sha `0bd563b7…`, conclusão **`failure`** |
| Publish Candidate run | `30687306886`, `workflow_run`, head_sha `0bd563b7…`, conclusão **`failure`** |

```text
plan           success
backend        success
website_back   success
frontend       success
website_front  success
whatsapp       success
contracts      failure   (nova causa, defeito G-linha-67)
images         skipped
```

**G provado remotamente.** O log do job `contracts` mostra, em sequência, a
suíte `Ran 298 tests` `OK`, depois `release-control-contract:valid`. A chamada
que antes abortava o passo agora executa corretamente.

**H provado remotamente.** No job `trust`, o passo 5 deixou de falhar com
`Command '['git','fetch','--no-tags','origin','main']' returned non-zero exit
status 128`. A saída agora é:

```text
trust:invalid:run state
```

`run state` vem de `trust.event()`, que exige `conclusion == "success"` do run
de CI disparador. Como a CI deste SHA terminou vermelha, a recusa é o
comportamento fail-closed correto. O ponto decisivo é causal: em `trust.main()`,
`classify_head()` é executado **antes** do `raise`, e qualquer falha da
resolução autenticada seria acrescentada à lista de erros. Como a única
mensagem é `run state`, a resolução do HEAD pela API do GitHub com `GH_TOKEN`
**funcionou**, sem erro de autenticação, sem `git fetch` e com
`persist-credentials: false` preservado.

### 22.9 Defeito remanescente e auditoria completa da família

**G-linha-67 — mesma classe de G, na linha seguinte.**

```text
usage: bootstrap_contract.py [-h] {validate}
bootstrap_contract.py: error: the following arguments are required: command
##[error]Process completed with exit code 2
```

Para não repetir a descoberta sequencial de uma falha por ciclo, o executor
auditou localmente **todos** os utilitários invocados pelo job `contracts`:

| Chamada em `ci.yml` | Exit local | Situação |
|---|---:|---|
| `tools/releases/catalog.py validate --require-release-ready` | 0 | correta |
| `tools/releases/release_control_contract.py validate` | 0 | corrigida nesta correction |
| **`tools/security/bootstrap_contract.py`** (linha 67) | **2** | **falta `validate`** |
| **`tools/docker/java_images_contract.py`** (linha 69) | **2** | **falta `validate`** |
| `tools/compose/validate_compose.py` | 0 | correta |
| `tools/gateway/validate_gateway.py` | 0 | correta |
| `tools/ci/migrations_contract.py` | 0 | correta |

A família está **fechada**: restam exatamente duas chamadas, nas linhas 67 e 69
de `.github/workflows/ci.yml`. Ambas exigem o subcomando `validate`. O reparo
seria idêntico ao de G, e o guard já implementado em `validate_ci.py` pode ser
generalizado para as três formas.

`.github/workflows/ci.yml`, `tools/ci/validate_ci.py` e `tools/ci/tests/test_ci.py`
**estão** na fronteira autorizada da correction-03, mas a Seção 3 dela limita G
a “corrigir somente a chamada para
`python3 tools/releases/release_control_contract.py validate`”, e a Seção 5
determina expressamente: “Se qualquer gate falhar, não tentar uma quarta
correção improvisada: registrar … e parar para revisão do orquestrador.” O
executor parou sem tocar nas linhas 67 e 69.

### 22.10 Estado remoto, produção e bloqueios

```text
runs totais: 8   releases: 0   tags: 0
artifacts: 3 — todos candidate-plan, dos runs 30685735159, 30686261529 e 30687242174
```

O único artifact existente é o `candidate-plan` da CI, com retenção de 7 dias.
Continuam inexistentes `candidate-manifest`, `candidate-outcome`, manifesto
final, image digests, provenance, attestation, release e tag. Nenhuma imagem
chegou ao GHCR: o job `build` foi pulado em todos os quatro ciclos, antes de
qualquer `docker/login-action` ou `docker push`. `publish-release.yml`,
`deploy-production.yml` e `rollback-production.yml` não foram executados nem
disparados. Não houve SSH, VPS, DNS, Docker de produção, criação de credencial,
cleanup destrutivo ou qualquer efeito de produção. A ausência de
`read:packages` permanece registrada, ainda sem efeito prático. S31 não foi
criada.

Nenhum token, header, credencial ou valor secreto foi lido, copiado ou
transcrito em nenhuma etapa desta execução.

### 22.11 Divergências

1. **CI ainda não está verde**, por G-linha-67, cuja correção a Seção 5 da
   correction-03 proíbe improvisar. A auditoria da Seção 22.9 entrega a lista
   completa e fechada da família.
2. **A cadeia do candidato ainda não produz candidato**, porque o `trust`
   recusa corretamente um run de CI vermelho. Este bloqueio é consequência de 1,
   não um defeito novo: `predecessor`, `build`, `assemble`, `integrated` e
   `publish` só serão exercitados quando a CI ficar verde.
3. **G e H foram provados remotamente** e não regrediram; A, B, C, D, E e F
   também permanecem provados nos ciclos anteriores.
4. As Seções 22.8 em diante descrevem fatos posteriores ao commit `0bd563b` e
   permanecem **não commitadas**, já que a correction autoriza exatamente um
   commit e um push.

IN_PROGRESS — aguardando revisão do orquestrador
