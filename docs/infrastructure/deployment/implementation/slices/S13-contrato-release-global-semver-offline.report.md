# S13 — Relatorio do contrato de release global SemVer offline

> **Data:** `2026-07-29`  
> **CWD:** `/home/gregorio/git/baronesa/emporio`  
> **Estado:** `ACCEPTED — 29/07/2026`

## 1. Escopo executado

A S13 foi implementada exclusivamente como contrato e gerador offline:

- request publico identico ao body publisher S06;
- schema estrito da release global implantavel;
- SemVer numerico e deterministico;
- BOM integral copiado do candidato S12 validado;
- inventarios Flyway dos backends ERP e website;
- bundle canonico, atomico e recuperavel;
- exemplo ficticio regeneravel;
- 28 provas causais;
- documentacao viva de releases.

Nenhum workflow, tag, GitHub Release, runtime publisher, UI, deploy ou estado
remoto foi criado.

## 2. Arquivos criados

```text
ops/releases/global-release.schema.json
ops/releases/release-request.schema.json
ops/releases/examples/global-release.example.json
ops/releases/examples/release-request.example.json
tools/releases/global_release.py
tools/releases/tests/test_global_release.py
docs/infrastructure/deployment/release-control/RELEASES.md
docs/infrastructure/deployment/implementation/slices/S13-contrato-release-global-semver-offline.report.md
```

## 3. Arquivos alterados

```text
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md
```

A proposta foi alterada somente para substituir o exemplo conceitual
superado pelo contrato verificavel da S13 e remover a alegacao conceitual de
rollback SQL compativel.

Nao foram alterados:

```text
docs/infrastructure/deployment/implementation/slices/S13-contrato-release-global-semver-offline.task.md
docs/infrastructure/deployment/implementation/README.md
.github/workflows/ci.yml
.github/workflows/publish-candidate.yml
tools/releases/catalog.py
codigo comercial
migrations
Dockerfiles
Compose
gateway
contratos S06
```

## 4. Shapes finais

### 4.1 Request

Chaves exatas:

```text
candidateId
versionBump
description
changelog
```

`versionBump` aceita somente `MAJOR`, `MINOR` ou `PATCH`. Os textos preservam
UTF-8 e LF interno permitido no changelog, mas rejeitam vazio, whitespace
externo e controles proibidos. A versao final nunca e input do cliente.

### 4.2 Manifesto global

Chaves de topo exatas:

```text
schemaVersion
kind
deployable
release
repository
sourceCommit
publishedAt
description
changelog
candidate
publication
previousRelease
catalog
components
databases
```

Identidade fixa:

```text
schemaVersion=1
kind=global-release
deployable=true
repository=greggorio/abaronesa-emporio
```

O objeto `candidate` liga ID, hash dos bytes canonicos do candidato, artifact
ID/digest e run/attempt do candidato. `publication` registra run/attempt
distinto, ator GitHub, actor ID e `workflow_dispatch`.

### 4.3 Componentes

O BOM possui exatamente seis objetos, copiados integralmente do candidato e
na ordem:

```text
backend
website_back
frontend
website_front
whatsapp_service
gateway
```

Repository, tag, digest, immutableRef, labels, state/origin e provenance sao
validados. `release_control` e PostgreSQL nao integram o BOM.

### 4.4 Databases

Cada database possui exatamente:

```text
id
ownerComponent
engine
location
latestVersion
migrationSetSha256
backupPolicy
rollbackPolicy
migrations
```

Cada migration possui `version`, `path` POSIX relativo e SHA-256 dos bytes
exatos. O digest do conjunto usa o JSON canonico da lista ordenada.

Politicas fixas:

```text
backupPolicy=required_on_change
rollbackPolicy=restore_required
```

O helper nao interpreta SQL e nao alega reversibilidade.

### 4.5 Metadata

Chaves exatas:

```text
schemaVersion
stage
kind
release
repository
sourceCommit
publicationWorkflowRunId
publicationWorkflowAttempt
manifestSha256
```

O bundle final contem somente `release.json`, `release.json.sha256` e
`metadata.json`.

## 5. SemVer e releases anteriores

O parser aceita exclusivamente `vMAJOR.MINOR.PATCH`, com partes de zero a
`2147483647`. A ordenacao usa a tripla numerica. Foram implementados:

- bases iniciais `v0.0.0`;
- bumps MAJOR/MINOR/PATCH;
- ordenacao numerica, inclusive `v2.10.0 > v2.9.99`;
- rejeicao de pre-release, metadata, zero a esquerda e overflow;
- rejeicao de versao duplicada;
- rejeicao de candidato reutilizado, inclusive duplicidade historica;
- `previousRelease` igual a maior release anterior valida;
- resultado independente da ordem de `--existing-release`.

Cada release anterior exige JSON canonico, sidecar, metadata canonica e
validacao estrutural/semantica antes de participar da resolucao.

## 6. Inventarios Flyway reais

| Database | Owner | Location | Migrations | Latest | migrationSetSha256 |
|---|---|---|---:|---|---|
| `erp` | `backend` | `backend/src/main/resources/db/migration` | 50 | `20260331100000` | `sha256:569e332b67450a16f5e5819e0b55455ed5d04207acc0309590f6b254a3c77f84` |
| `website` | `website_back` | `website_back/src/main/resources/db/migration` | 14 | `15` | `sha256:eb411ec063201e6d557e8223950c71d3603a9fa16b51e5e924caa6fd954e6b3b` |

`.gitkeep` foi ignorado somente no root ERP. Arquivo desconhecido, symlink,
subdiretorio, root ausente, conjunto vazio, filename invalido ou versao
normalizada duplicada falha fechado.

## 7. Exemplo ficticio e hashes

O exemplo representa PATCH sem release anterior:

```text
release=v0.0.1
candidateId=candidate-1111111111111111111111111111111111111111-200-1
candidate manifestSha256=sha256:b927c6739dbe6aeb5c7828ebbc64a8b2d0e4bb5a01526ebe92c1c468da721381
publication workflowRunId=400
```

Hashes dos artefatos:

```text
c92cd3bd377ab39c453bf0df6526cf1d4c70c8f086e69da84a6a97ef54661b58  ops/releases/examples/global-release.example.json
3abc34f94e2d6f165bf59ebdb50fb5ef58aec3335d306dcd5f9a39ffbf60a16c  ops/releases/global-release.schema.json
178aa0d93dc089b2e56b87313259e27a6e6574c7c7d0a5ec922109c7a534dc6a  ops/releases/release-request.schema.json
324509230f99946d85202b85fcca47c8a044685d071e21537170256e48fb88e6  ops/releases/examples/release-request.example.json
```

O teste de drift compara os dois inventories do exemplo com os roots reais.

## 8. Atomicidade

Primeira escrita usa `artifact_io.atomic_bundle` e rejeita diretorio nao
vazio. Falha de staging deixa zero arquivo parcial.

O helper interno de overwrite nao e exposto pelo CLI. Ele:

1. exige bundle anterior completo;
2. cria e fsynca o novo bundle em staging;
3. substitui os tres arquivos individualmente;
4. fsynca o diretorio;
5. verifica os bytes finais;
6. restaura integralmente os bytes anteriores diante de falha de staging,
   primeiro/segundo/terceiro rename ou verificacao.

## 9. Casos causais

Os 28 casos nomeados da Secao 11 foram implementados:

1. exemplos request/release;
2. primeira release nos tres bumps;
3. bumps a partir de `v2.7.9`;
4. ordenacao numerica;
5. formatos e overflow;
6. versao anterior duplicada;
7. candidato reutilizado;
8. previous release adulterada;
9. candidate ID do request divergente;
10. candidato invalido;
11. copia integral dos seis componentes;
12. componente/digest/immutableRef/provenance adulterado;
13. inventarios deterministicos;
14. mudanca de byte SQL;
15. `.gitkeep`;
16. entradas e roots invalidos;
17. versao Flyway normalizada duplicada;
18. policies adulteradas;
19. candidate artifact binding;
20. publication binding;
21. sidecar/metadata anterior;
22. JSON anterior nao canonico;
23. primeira escrita atomica;
24. rollback integral de overwrite;
25. ordem de releases anteriores;
26. exits e prefixos dos CLIs;
27. chaves extras/ausentes;
28. drift das migrations reais.

### 9.1 Falha intermediaria

A primeira execucao isolada dos 28 testes teve dois subcasos vermelhos porque
o teste de policies exigia um codigo semantico especifico, enquanto o schema
estrito ja rejeitava corretamente o mutante antes dessa camada. O teste foi
corrigido para exigir a rejeicao causal, sem relaxar schema ou helper.

Reexecucao isolada:

```text
Ran 28 tests
OK
```

## 10. Matriz final da Secao 13

| Comando exato | Exit | Resultado e interpretacao |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json` | 0 | `global-release:valid`; exemplo e inventories reais coerentes |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | 140 testes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py` | 0 | `candidate:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py validate --require-release-ready` | 0 | `catalog:valid`; readiness permanece verde |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid`; contratos S06 preservados |
| `git diff --check` | 0 | nenhuma divergencia de whitespace detectada |
| `git diff --cached --name-only` | 0 | indice real vazio |
| `git rev-parse --verify HEAD` | 128 | `HEAD` inexistente, conforme estado protegido |
| `git tag --list` | 0 | zero tags |
| `git reflog` | 128 | branch `main` sem commits e sem reflog |
| `find . -type d -name __pycache__ -o -type f -name '*.pyc'` | 0 | zero resultados |
| `find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) -print \| sort` | 0 | somente `ci.yml` e `publish-candidate.yml` |

## 11. Estado protegido e limites

```text
indice Git real: 0 entradas
HEAD: inexistente
tags: 0
reflog: inexistente
caches Python: 0
workflows ativos: 2
S14: inexistente
```

Nao foram executados `git add`, commit, tag, push, `gh`, `curl`, API remota,
Maven, NPM, Docker build, Compose up, instalacao, publicacao ou acesso a
producao.

Nao foi criado workflow nem recurso remoto. A S14 nao foi criada.

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

---

## 12. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-29`

O codigo, os schemas, os exemplos e a documentacao correspondem ao contrato
fechado da S13:

- request publico e manifesto global possuem shapes estritos;
- SemVer e calculado numericamente e nunca recebido pronto do cliente;
- releases anteriores possuem bundle, sidecar e metadata validados;
- candidato reutilizado, duplicidade, overflow e bindings divergentes falham
  fechado;
- os seis componentes sao copiados integralmente e na ordem canonica;
- os inventories reais registram 50 migrations ERP e 14 website;
- fingerprints usam bytes exatos e JSON canonico;
- politicas conservadoras nao alegam rollback SQL;
- primeira escrita e overwrite interno preservam atomicidade e recuperacao;
- as 28 provas causais e a matriz de 140 testes estao persistidas;
- workflows, indice Git, `HEAD`, tags, reflog e caches permaneceram
  protegidos.

O orquestrador revisou as superficies correspondentes sem repetir a suite
persistida pelo executor. A validacao da cadeia completa de tags, GitHub
Releases e bundles remotos pertence a S14, pois nao existe estado remoto no
escopo offline da S13.

Decisao:

```text
S13 ACCEPTED — 29/07/2026
S14 autorizada
```
