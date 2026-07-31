# S13 — Contrato canonico da release global e resolucao semantica offline

> **Estado:** `ACCEPTED` — 29/07/2026  
> **Tipo:** contrato de dados, SemVer, BOM implantavel e migrations  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S12 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Contrato de candidatos:** [release-control/CANDIDATOS.md](../../release-control/CANDIDATOS.md)  
> **Relatorio de saida:** `S13-contrato-release-global-semver-offline.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro:

1. esta task;
2. a Secao 30 da revisao final da S12;
3. as secoes `Modelo de release global`, `Resolucao automatica de
   dependencias`, `Release — publish-release.yml`, `Contrato de usabilidade`
   e `Macrofases para futura decomposicao` da arquitetura;
4. `ops/releases/candidate-manifest.schema.json` e o exemplo candidato;
5. `tools/releases/candidate_manifest.py`, `artifact_io.py`, catalogo e
   testes aceitos;
6. o OpenAPI publisher e os contratos S06;
7. as migrations reais dos dois backends.

Nao altere esta task nem o tracker:

```text
docs/infrastructure/deployment/implementation/README.md
```

Esta slice fecha o contrato autocontido da release global e seu gerador
offline. Ela deliberadamente nao cria `publish-release.yml`, tag Git, GitHub
Release, backend/UI de `release_control` ou deploy. A S14 consumira este
contrato para implementar o workflow mutavel de publicacao.

## 1. Objetivo observavel

Ao final:

- existe um JSON Schema estrito para a release global implantavel;
- existe um request estrito, identico ao body publisher S06;
- existe um exemplo integral, ficticio e validado;
- um helper offline valida candidato final v2 e produz atomicamente uma
  release global completa;
- a proxima versao e calculada de forma deterministica a partir das releases
  anteriores validas;
- o mesmo candidato nao pode originar duas releases;
- o BOM contem exatamente os seis componentes do candidato, na ordem
  canonica e com referencias imutaveis;
- os dois conjuntos Flyway sao inventariados por path e digest;
- qualquer mudanca futura de migrations pode ser detectada comparando dois
  manifestos, sem inspecionar imagens em producao;
- nenhuma release e publicada e nenhum estado remoto e consultado.

## 2. Fronteira obrigatoria

### 2.1 Criar

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

### 2.2 Alterar somente se necessario para integrar o contrato

```text
tools/releases/validate_candidate_manifest.py
tools/releases/tests/test_candidate_manifest_v2.py
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md
```

Alterar a proposta arquitetural somente se o contrato definitivo substituir
algum exemplo conceitual. Registrar explicitamente a diferenca, sem reescrever
o documento como se toda a arquitetura ja estivesse implantada.

### 2.3 Nao alterar

- `.github/workflows/ci.yml`;
- `.github/workflows/publish-candidate.yml`;
- `tools/releases/catalog.py`;
- codigo comercial, migrations, Dockerfiles, Compose ou gateway;
- OpenAPI, maquinas de estado e matriz de seguranca S06;
- task S13 e tracker;
- qualquer task ou relatorio futuro.

### 2.4 Proibido executar

- `git add`, commit, tag ou push;
- `gh`, `curl`, login ou acesso a API do GitHub;
- build/push de imagem, Maven, NPM, Compose up ou acesso a producao;
- instalacao ou atualizacao de dependencias;
- criacao da S14.

## 3. Request canonico

`ops/releases/release-request.schema.json` usa JSON Schema draft 2020-12,
`additionalProperties: false` e exatamente:

```json
{
  "candidateId": "candidate-1111111111111111111111111111111111111111-200-1",
  "versionBump": "PATCH",
  "description": "Correcao operacional ficticia",
  "changelog": "Ajustes ficticios validados pelo pipeline."
}
```

Regras:

- chaves obrigatorias exatamente `candidateId`, `versionBump`,
  `description`, `changelog`;
- `candidateId`: string de 12 a 128 caracteres, como no OpenAPI S06;
- `versionBump`: somente `MAJOR`, `MINOR` ou `PATCH`;
- `description`: 1 a 500 caracteres, uma linha, sem controles;
- `changelog`: 1 a 10000 caracteres, permite LF e tab, sem outros controles;
- ambos os textos devem ser UTF-8, nao vazios depois de trim e ja devem chegar
  sem whitespace externo; o helper rejeita, nao corrige silenciosamente;
- o `candidateId` deve ser exatamente o do candidato fornecido. Como o
  candidato validado possui o pattern canonico
  `^candidate-[a-z0-9._-]+$`, nenhum ID fora desse pattern pode chegar ao
  manifesto global, sem tornar o request publico mais restritivo que o
  OpenAPI aceito.

O exemplo do request deve ser inequivocamente ficticio e validar no schema.

## 4. SemVer definitivo

O unico formato aceito e:

```text
vMAJOR.MINOR.PATCH
```

Cada parte e decimal sem sinal e sem zero a esquerda, salvo o proprio `0`.
Pre-release, build metadata, espacos e prefixo ausente sao rejeitados. Cada
parte deve estar entre `0` e `2147483647`.

Ordenacao usa a tripla numerica, nunca texto nem data.

Sem release anterior, a base e `v0.0.0`:

```text
MAJOR -> v1.0.0
MINOR -> v0.1.0
PATCH -> v0.0.1
```

Com `v2.7.9` como ultima release:

```text
MAJOR -> v3.0.0
MINOR -> v2.8.0
PATCH -> v2.7.10
```

O helper deve:

- validar todas as releases anteriores recebidas;
- ordenar pela versao numerica;
- rejeitar versoes duplicadas;
- rejeitar `candidateId` ja presente em release anterior;
- escolher como `previousRelease` a maior versao;
- falhar em overflow do componente incrementado;
- nunca aceitar versao final fornecida pelo cliente.

Nao ha requisito de contiguidade entre releases existentes. Reserva
concorrente e revalidacao remota pertencem ao workflow S14.

## 5. Manifesto global exato

`ops/releases/global-release.schema.json` usa draft 2020-12,
`additionalProperties: false` em todos os objetos e exige exatamente as
chaves de topo:

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

Identidade:

```json
{
  "schemaVersion": 1,
  "kind": "global-release",
  "deployable": true,
  "repository": "greggorio/abaronesa-emporio"
}
```

Regras dos demais campos:

- `release`: SemVer da Secao 4;
- `sourceCommit`: SHA completo copiado de `candidate.commitSha`;
- `publishedAt`: UTC RFC 3339 normalizado, terminando em `Z`;
- `description` e `changelog`: copiados byte semanticamente do request,
  preservando LF interno;
- `previousRelease`: `null` na primeira ou maior release anterior valida;
- `catalog`: copia exata do candidato.

### 5.1 Candidate binding

Objeto `candidate` com exatamente:

```text
candidateId
manifestSha256
artifactId
artifactDigest
workflowRunId
workflowAttempt
```

Regras:

- ID, run e attempt sao copiados do candidato;
- `manifestSha256` e `sha256:` mais o digest dos bytes canonicos validados de
  `candidate.json`;
- `artifactId` e decimal positivo recebido pelo CLI;
- `artifactDigest` usa `sha256:<64 hex>` recebido pelo CLI;
- o binding remoto entre artifact ID/digest e bytes sera responsabilidade
  explicita da S14; a S13 valida formato e binding local do manifesto.

### 5.2 Publication binding

Objeto `publication` com exatamente:

```text
workflowRunId
workflowAttempt
actor
actorId
event
```

Regras:

- run ID e actor ID sao strings decimais positivas;
- attempt e inteiro positivo;
- `actor` tem 1 a 100 caracteres, sem whitespace externo ou controles;
- `event` e constante `workflow_dispatch`;
- run de publicacao deve ser diferente do run do candidato.

Esse ator e a identidade GitHub que disparou o workflow. Identidade do usuario
da futura UI permanecera na operacao auditavel do `release_control` e nao
sera inventada nesta slice.

### 5.3 Components

`components`:

- possui exatamente seis itens;
- preserva a ordem:
  `backend`, `website_back`, `frontend`, `website_front`,
  `whatsapp_service`, `gateway`;
- copia integralmente cada objeto do candidato, sem reduzir proveniencia;
- preserva `built`/`inherited` e `originCandidateId`;
- exige `immutableRef == imageRepository + "@" + digest`;
- exige `provenance.verifiedSubject == immutableRef`;
- exige repositorio de imagem canonico de cada ID;
- nao permite `release_control` nem PostgreSQL.

O gerador deve primeiro chamar o validador canonico do candidato S12. Nao
reimplementar uma versao permissiva desse contrato.

## 6. Contrato de migrations

`databases` possui exatamente dois itens, nesta ordem:

```text
erp
website
```

Shapes exatos:

```json
{
  "id": "erp",
  "ownerComponent": "backend",
  "engine": "flyway",
  "location": "backend/src/main/resources/db/migration",
  "latestVersion": "20260331100000",
  "migrationSetSha256": "sha256:<64 hex>",
  "backupPolicy": "required_on_change",
  "rollbackPolicy": "restore_required",
  "migrations": [
    {
      "version": "1",
      "path": "backend/src/main/resources/db/migration/V1__init.sql",
      "sha256": "sha256:<64 hex>"
    }
  ]
}
```

O segundo item usa:

```text
id=website
ownerComponent=website_back
location=website_back/src/main/resources/db/migration
```

Regras de inventario:

- considerar somente arquivos regulares `V<version>__<description>.sql`;
- `version` admite somente segmentos numericos separados por `.` ou `_`;
- ordenar pela versao numerica Flyway; segmentos ausentes equivalem a zero
  apenas para comparacao e duplicidades normalizadas falham;
- `path` e sempre relativo POSIX, sob a location canonica;
- `sha256` e calculado sobre bytes exatos do SQL;
- `migrationSetSha256` e o digest dos bytes JSON canonicos da lista
  `migrations`;
- `latestVersion` e a versao textual do ultimo item ordenado;
- `.gitkeep` e o unico arquivo nao migration permitido;
- arquivo desconhecido, symlink, diretorio inesperado, nome invalido,
  duplicidade de versao, root ausente ou conjunto vazio falha fechado;
- o helper nunca interpreta SQL nem alega reversibilidade;
- `backupPolicy` e sempre `required_on_change`;
- `rollbackPolicy` e sempre `restore_required`.

Essas politicas sao deliberadamente conservadoras. O futuro deploy compara
`migrationSetSha256`; quando mudar, backup e restore tornam-se obrigatorios.

## 7. Bundle atomico

O gerador cria no diretorio de saida exatamente:

```text
release.json
release.json.sha256
metadata.json
```

`release.json` usa JSON canonico UTF-8, uma linha e LF final.
`release.json.sha256` usa o sidecar canonico ja implementado em
`artifact_io.py`.

`metadata.json` usa JSON canonico e exatamente:

```text
schemaVersion=1
stage=final
kind=global-release
release
repository
sourceCommit
publicationWorkflowRunId
publicationWorkflowAttempt
manifestSha256
```

`manifestSha256` e o digest de `release.json`.

Reutilizar `artifact_io.atomic_bundle`. Em primeira escrita, destino existente
falha. Em overwrite explicitamente autorizado pelo helper interno, falha em
staging, rename, sidecar ou metadata deve restaurar integralmente o bundle
anterior. O CLI de producao desta slice nao expoe flag de overwrite.

## 8. CLI obrigatoria

`tools/releases/global_release.py` oferece:

```bash
python3 tools/releases/global_release.py validate \
  --manifest ops/releases/examples/global-release.example.json
```

Sucesso:

```text
global-release:valid
```

Falha: exit `3`, prefixo sanitizado:

```text
global-release:invalid:
```

Geracao:

```bash
python3 tools/releases/global_release.py generate \
  --candidate ops/releases/examples/candidate-manifest.example.json \
  --candidate-artifact-id 300 \
  --candidate-artifact-digest sha256:<64-hex> \
  --request ops/releases/examples/release-request.example.json \
  --published-at 2026-07-29T15:00:00Z \
  --workflow-run-id 400 \
  --workflow-attempt 1 \
  --actor greggorio \
  --actor-id 1 \
  --output <diretorio>
```

Para releases anteriores, repetir:

```text
--existing-release <path/release.json>
```

Cada manifesto anterior exige sidecar e metadata adjacentes validos. A ordem
dos argumentos nao altera o resultado. Sem `--existing-release`, aplica-se a
primeira release.

O CLI usa somente os dois roots de migration canonicos do workspace. Funcoes
internas podem receber roots temporarios para testes, mas argumentos do CLI
nao aceitam path arbitrario de migration.

## 9. Exemplo global

`global-release.example.json` deve:

- ser integral e nao conter placeholders como `<digest>`;
- usar somente IDs, SHAs, digests, runs e timestamps ficticios;
- corresponder ao candidato exemplo;
- representar `PATCH` sem release anterior, portanto `v0.0.1`;
- incluir inventario real e hashes atuais das migrations versionadas;
- ser regeneravel deterministicamente pelo helper com os inputs ficticios
  documentados;
- nunca ser apresentado como release publicada.

O exemplo pode mudar quando migrations versionadas mudarem. O teste deve
detectar drift e orientar regeneracao deliberada.

## 10. Validacao semantica fail-closed

A validacao deve rejeitar separadamente:

1. chave ausente ou extra em qualquer shape;
2. identity, SemVer, SHA, digest, timestamp ou URL invalido;
3. texto vazio, externo ou com controle proibido;
4. ordem, ausencia, duplicidade ou componente extra;
5. repositorio/digest/immutableRef/provenance divergente;
6. candidato nao final, `deployable` diferente de false ou integracao nao
   aprovada;
7. candidate binding divergente;
8. publication run igual ao candidate run;
9. previous release divergente da maior release recebida;
10. versao diferente do bump calculado;
11. release anterior invalida, duplicada ou com sidecar/metadata divergente;
12. candidato ja usado;
13. database ausente, extra, fora de ordem ou associado ao owner errado;
14. inventory, latestVersion, digest de migration ou policy divergente;
15. bundle parcial ou escrita nao atomica.

O schema e necessario, mas nao substitui esses bindings semanticos.

## 11. Provas obrigatorias

Adicionar testes causais que exercitem funcoes e CLI reais. A suite deve
comprovar, no minimo, estes casos nomeados:

1. request e release exemplos validos;
2. primeira release para MAJOR, MINOR e PATCH;
3. bump de `v2.7.9` para os tres tipos;
4. ordenacao numerica com `v2.10.0` superior a `v2.9.99`;
5. formato invalido, pre-release, zero a esquerda e overflow;
6. versao anterior duplicada;
7. candidato ja publicado;
8. previous release adulterada;
9. request candidate ID divergente;
10. manifesto candidato invalido;
11. seis componentes copiados integralmente e na ordem;
12. digest, immutableRef ou provenance de componente adulterado;
13. inventarios ERP e website deterministicamente ordenados;
14. alteracao de um byte SQL muda migration e set digests;
15. `.gitkeep` ignorado;
16. arquivo desconhecido, symlink, subdiretorio e root ausente rejeitados;
17. versao Flyway duplicada normalizada rejeitada;
18. policies de backup/rollback adulteradas;
19. candidate artifact binding invalido;
20. publication binding invalido;
21. sidecar ou metadata anterior divergente;
22. JSON nao canonico anterior rejeitado;
23. bundle novo atomico e sem arquivo parcial;
24. falhas injetadas preservam bundle anterior no helper de overwrite;
25. ordem de `--existing-release` nao muda bytes gerados;
26. CLI validate/generate usa exits e prefixos prescritos;
27. mutantes de chave extra/ausente falham;
28. exemplo global detecta drift das migrations reais.

Nao substituir essas provas por busca textual. O executor escolhe a
organizacao dos metodos, nao o comportamento esperado.

## 12. Documentacao viva

Criar `RELEASES.md` contendo:

- diferenca entre candidato e release global;
- formato SemVer e exemplos de bump;
- BOM completo e por que o usuario nao escolhe componentes;
- origem e proveniencia de imagens herdadas;
- significado dos fingerprints de migration;
- `required_on_change` e `restore_required`;
- bundle e verificacao de sidecar/metadata;
- comandos offline de validar e gerar;
- fronteira desta slice: nenhuma tag, GitHub Release ou producao;
- manutencao quando componente, migration ou schema mudar.

Atualizar o README de release control com link e estado real. Nao alegar que
`publish-release.yml`, publisher runtime, UI ou deploy ja existem.

## 13. Matriz final obrigatoria

Executar, com `PYTHONDONTWRITEBYTECODE=1`:

```bash
python3 tools/releases/global_release.py validate \
  --manifest ops/releases/examples/global-release.example.json

python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py'

python3 tools/releases/validate_candidate_manifest.py

python3 tools/releases/catalog.py validate --require-release-ready

python3 tools/releases/release_control_contract.py validate
```

Tambem:

```bash
git diff --check
git diff --cached --name-only
git rev-parse --verify HEAD
git tag --list
git reflog
find . -type d -name __pycache__ -o -type f -name '*.pyc'
find .github/workflows -maxdepth 1 -type f \
  \( -name '*.yml' -o -name '*.yaml' \) -print | sort
```

Resultados exigidos:

- todos os validadores e testes verdes;
- indice real vazio;
- `HEAD`, tags e reflog inexistentes;
- zero cache Python;
- exatamente `ci.yml` e `publish-candidate.yml` ativos;
- nenhum workflow, release, tag ou recurso remoto criado.

Nao e necessario repetir Maven, NPM, Docker build, `actionlint` ou Compose:
esta slice nao altera essas superficies.

## 14. Relatorio obrigatorio

Criar o relatorio indicado no cabecalho com:

- CWD;
- arquivos criados e alterados;
- decisoes implementadas sem alternativas;
- shape final do request, manifesto, database e metadata;
- lista dos casos causais e resultado;
- comandos exatos, exits, contagens e interpretacao;
- evidencia do exemplo e inventories reais;
- estado Git e ausencia de residuos;
- limites nao executados;
- divergencia factual, se houver.

Estado final do executor:

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

Nao declarar `ACCEPTED`.

## 15. Criterios de aceite do orquestrador

A S13 somente podera ser aceita se:

- S12 estiver registrada como aceita;
- request, schema, exemplo e helper coincidirem;
- SemVer e bump forem numericos e deterministicos;
- o cliente nao fornecer versao final;
- releases anteriores forem validadas com sidecar e metadata;
- candidato reutilizado, duplicidade e regressao falharem fechado;
- candidato final S12 for validado pelo helper canonico;
- os seis componentes forem copiados integralmente, em ordem e sem
  `release_control`;
- bindings de candidate/publication forem estritos;
- os dois inventories Flyway forem completos e deterministicos;
- nenhuma alegacao falsa de reversibilidade existir;
- bundle for canonico, atomico e recuperavel;
- casos positivos, negativos e mutantes passarem;
- documentacao corresponder ao codigo;
- workflows existentes permanecerem inalterados;
- estado Git protegido e proibicoes forem respeitados.

A proxima slice prevista, apos aceite, sera a S14: workflow
`publish-release.yml`, verificacao remota do candidato, reserva serializada de
SemVer, tag e GitHub Release, ainda sem UI e sem deploy de producao.

## 16. Condicoes de bloqueio

Parar e documentar se:

- o candidato final v2 nao puder ser validado sem afrouxar S12;
- uma migration real violar o naming Flyway prescrito;
- for necessario interpretar SQL para produzir o contrato;
- o bundle exigir rede ou credencial;
- for necessario alterar workflow, codigo comercial ou producao;
- o indice Git real deixar de estar vazio;
- qualquer comando tentar publicar ou criar tag.
