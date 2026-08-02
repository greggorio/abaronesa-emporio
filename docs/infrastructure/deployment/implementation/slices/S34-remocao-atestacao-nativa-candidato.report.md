# S34 — relatório de execução

> **Estado da execução:** primeira passagem parada antes do commit pela §6;
> retomada pela correction-01 concluída com commit técnico
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Autoridade:** `S34-remocao-atestacao-nativa-candidato.task.md` e
> `S34-remocao-atestacao-nativa-candidato.correction-01.md`
> **SHA-base da primeira passagem:** `7216238c71bbedd4c939d70e3bf481c279a249ee`
> **Checkpoint da retomada:** `3e736c56c5dbeeee919aabc318451e77ac5afc85`
> **Mensagem do commit:** `fix: remove unavailable candidate attestation contract`
> **Push:** não executado; nenhum efeito remoto

As seções 1 a 6 registram a primeira passagem e permanecem inalteradas,
inclusive a condição de parada. A seção 7 registra a retomada pela correction-01
e descreve o estado final.

## 1. Preflight da §4

| Item | Exigido | Observado | OK |
|---|---|---|:--:|
| CWD | `/home/gregorio/git/baronesa/emporio` | idem | sim |
| branch | `main` | `main` | sim |
| mensagem de `HEAD` | `docs: record candidate attestation failure and open S34 scope` | idem | sim |
| `HEAD` | — | `7216238c71bbedd4c939d70e3bf481c279a249ee` | — |
| `origin/main` | `0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb` | idem | sim |
| commits em `origin/main..HEAD` | 1 | 1 | sim |
| stage | vazio | vazio | sim |
| worktree | limpo | limpo | sim |

Nenhum segredo foi consultado, copiado ou registrado.

## 2. Mapeamento prévio da superfície

Antes de editar, foi levantada toda a ocorrência de
`provenance`/`attestation`/`attestationId`/`verifiedSubject`/`verifiedAt` em
`*.py`, `*.yml`, `*.json` e `*.md`. Resultado: **todo** código, schema, fixture
e teste com o contrato removido está dentro da fronteira da §3. Fora dela
permanecem apenas:

- registros históricos de slices (S11–S13, S18, S29, S30, S30a), que a §2.5
  manda preservar literais;
- `docs/infrastructure/deployment/implementation/HANDOFF_ORQUESTRADOR.md` e
  `RETOMADA_S30A.md`, não listados na §3 e não alterados;
- `tools/releases/validate_release_workflow.py`, cuja menção a `attestations:`
  é proibição negativa ao workflow de release — a §3 autoriza expressamente
  mantê-la, e ela foi mantida.

## 3. Alterações por camada

### 3.1 Workflow do candidato — §2.1

Em `.github/workflows/publish-candidate.yml`, job `build`:

- permissões reduzidas de
  `{contents: read, actions: read, packages: write, id-token: write, attestations: write}`
  para `{contents: read, actions: read, packages: write}`;
- removido o step `Attest exact immutable subject`
  (`actions/attest-build-provenance@977bb373…`);
- removido o step `Verify attestation then finalize result`, com
  `gh attestation verify` e `image_result.py attest`;
- `component-result.json` passa a ser enviado imediatamente após a inspeção
  remota.

Ordem preservada e verificada: build → Trivy → login → push/inspect → upload →
cleanup. Actions pinadas por SHA, `packages: write`, plataforma `linux/amd64`,
tags, labels OCI, scan, logout e `cleanup_image.py` intactos. Nenhuma permissão
ou step substituto foi introduzido.

### 3.2 Resultado de componente — §2.2

Em `tools/candidates/image_result.py`:

- `provenance` removido de `KEYS`;
- removida a validação `RESULT_PROVENANCE`;
- `RESULT_TIME` passou a exigir apenas `builtAt` terminado em `Z`;
- subcomando `attest` removido, junto de `utc_now()` e do import `datetime`,
  que ficaram sem uso;
- o subcomando `remote` passou a **montar e validar o resultado final na
  própria inspeção**, com `validate(...)` e `raise` antes de escrever o
  arquivo — não existe mais estado intermediário com placeholder.

O placeholder `{"attestationId":"0","attestationUrl":"","verifiedSubject":"","verifiedAt":""}`,
que o fluxo antigo gravava entre `remote` e `attest`, deixou de existir.

### 3.3 Contrato de candidato, release e deploy — §2.2 e §2.3

| Arquivo | Alteração |
|---|---|
| `tools/releases/candidate_manifest.py` | `provenance` fora de `COMPONENT_KEYS`; `PROVENANCE_KEYS` removido; bloco de validação removido; montagem em `finalize` sem `provenance`; rótulo `inherited provenance` renomeado para `inherited identity` |
| `tools/releases/global_release.py` | bloco `component_id + ":PROVENANCE"` removido; `IMMUTABLE` preservado |
| `tools/deploy/deployment_plan.py` | removidas as duas condições de `provenance`/`verifiedSubject` de `_validate_target_contract`; `INVALID_CONTRACT` continua exigindo repositório canônico, `DIGEST_RE` e `immutableRef == repository + "@" + digest` |
| `tools/candidates/validate_candidate_workflow.py` | `expected_build` sem `id-token`/`attestations`; nova regra fail-closed `ATTESTATION_FORBIDDEN` |
| `tools/ci/invocability.py` | `EXPECTED_COMMANDS` de 27 para 26; sintéticos `attestation-url` e `attestation-id` removidos |

A nova regra `ATTESTATION_FORBIDDEN` varre o workflow e todos os
`tools/candidates/*.py` procurando ação, comando, permissão ou campo de
attestation. Seus literais são montados por concatenação para que o próprio
validador não case consigo mesmo — a primeira formulação incluía
`-build-provenance` inteiro e reprovou o repositório real, o que foi corrigido
antes de qualquer outra etapa.

Um segundo falso positivo foi corrigido do mesmo modo: o token `swconsultoria`
não pode marcar `java-danfe` porque `java-nfe` compartilha o grupo — situação
já tratada na S33 e reconfirmada aqui pelo validador Java, que segue `valid`.

### 3.4 Schemas — §2.2

| Schema | Antes | Depois | Resíduo |
|---|---:|---:|---|
| `ops/releases/candidate-manifest.schema.json` | 7874 B | 7322 B | nenhum |
| `ops/releases/global-release.schema.json` | 7755 B | 7105 B | nenhum |

Em ambos foram removidos o `$def` `provenance` inteiro, a entrada em `required`
do componente e a propriedade `provenance`. As versões dos schemas permanecem
como estavam, conforme a §2.2. Com `additionalProperties: false` no componente,
um objeto que reintroduza `provenance` é **rejeitado pela forma fechada**, não
tolerado como legado.

### 3.5 Fixtures e hashes derivados

`provenance` removido dos seis componentes de cada exemplo:

| Fixture | Antes | Depois | Blocos removidos |
|---|---:|---:|---:|
| `ops/releases/examples/candidate-manifest.example.json` | 10185 B | 8394 B | 6 |
| `ops/releases/examples/global-release.example.json` | 22296 B | 20505 B | 6 |

Hash derivado recalculado pelo produtor real
(`artifact_io.digest(artifact_io.canonical(...))`), nunca por aproximação:

```text
manifestSha256 do candidate-manifest.example.json
  antes:  sha256:b927c6739dbe6aeb5c7828ebbc64a8b2d0e4bb5a01526ebe92c1c468da721381
  depois: sha256:b818a0462d2333b74e36d6d09b1084fe97389be7e5602400cee6bb98ce8ee1c9
```

Propagado para as três referências que o citavam:

| Fixture | Campo | Ocorrências |
|---|---|---:|
| `global-release.example.json` | `candidate.manifestSha256` | 1 |
| `release-publication-plan.example.json` | `candidate.manifestSha256`, `target.manifestSha256` | 2 |
| `release-publication-outcome.example.json` | `manifestSha256` | 1 |

`integration.receiptSha256` do exemplo de candidato é um valor sintético
(`sha256:cccc…`) aceito pelo contrato, não um hash derivado do conteúdo;
`validate_manifest` retorna `[]` com ele. Não foi tocado.

**Observação para o orquestrador, não corrigida por não pertencer à S34:** no
`release-publication-plan.example.json`, `target.manifestSha256` era idêntico ao
digest do *candidato*, e não ao digest do manifesto de *release*, embora
`release_publication.py:1328` compare esse campo com `digest(bundle["release.json"])`.
A inconsistência é anterior à S34 — o digest do `global-release.example.json` no
`HEAD` era `sha256:c92cd3bd…`, diferente do valor gravado. A relação existente
foi preservada e apenas atualizada para a nova entrada; corrigi-la seria mudar
semântica fora do escopo desta slice.

### 3.6 Mutantes substituídos — §2.3

Todo mutante que alterava apenas `verifiedSubject` foi substituído por mutantes
de digest, repositório ou `immutableRef`, e cada camada reprova pelo seu erro
canônico já existente.

| Teste | Antes | Depois | Erro canônico |
|---|---|---|---|
| `test_causal_corrections.py::test_08` (renomeado para `…_and_digest_are_bound`) | `provenance.verifiedSubject` divergente | `digest` trocado; `immutableRef` com digest divergente; `immutableRef` de outro componente; `imageRepository` trocado | `validate_pending` não vazio |
| `test_definitive_contract.py::test_15` | mutação de `provenance` extra | `provenance` reintroduzido → `RESULT_SHAPE`; três mutantes de digest/`immutableRef` → `RESULT_DIGEST` | `RESULT_SHAPE`, `RESULT_DIGEST` |
| `test_candidate_manifest_v2.py::test_04` | `verifiedSubject` divergente | digest, `immutableRef` divergente, `immutableRef` cruzado e reintrodução de `provenance` | `validate_manifest` não vazio |
| `test_global_release.py::test_12` (renomeado para `…_digest_or_immutable_tamper_rejected`) | `provenance.verifiedSubject` | digest, `immutableRef` errado, `immutableRef` com digest divergente, repositório trocado, `provenance` reintroduzido | `validate_release` não vazio |
| `test_deployment_plan.py::test_repository_digest_and_provenance_mutants_fail` (renomeado para `…_and_immutable_mutants_fail`) | mutante de `provenance` | `immutableRef` com digest divergente; `immutableRef` trocado com outro componente | `INVALID_CONTRACT` |
| `test_deployment_plan.py::change_component` | escrevia `verifiedSubject` | apenas `digest` e `immutableRef` | — |
| `test_production_adapter.py::test_39` | escrevia `verifiedSubject` | apenas `digest` e `immutableRef` | `SOURCE_BUNDLE_INVALID` |

A reintrodução de `provenance` é rejeitada em três camadas independentes:
`image_result.validate` (`RESULT_SHAPE`), `candidate_manifest.validate_manifest`
e `global_release.validate_release`, além das formas fechadas dos dois schemas.

### 3.7 Primeiro candidato após o run reprovado — §2.4

Dois testes novos em `tools/candidates/tests/test_terminal_amendment.py`,
ambos verdes:

**`test_09_failed_candidate_run_is_not_a_predecessor_and_keeps_first_release`** —
modela o run real `30742264661` com `conclusion: failure`. O duplo de `api`
afirma que `discover` consulta `status=success`, de modo que o run reprovado
sequer é retornado, e falha o teste se qualquer artefato for baixado. Provas:

```text
discover(...) -> ("first", None)
lineage.mode_for("first", None) -> "continue"
effective["predecessor"]["status"] == "first"
effective["predecessor"]["candidateId"] is None
lineage.validate_effective(effective, plan) == []
effective["resolution"]["buildComponents"] ==
  ["backend","website_back","frontend","website_front","whatsapp_service","gateway"]  (6)
effective["resolution"]["inheritedComponents"] == []
```

**`test_10_successful_run_without_outcome_cannot_be_inherited`** — um run
`success` sem `candidate-outcome` faz `discover` levantar `ValueError` contendo
`outcome`, provando que a ausência de outcome falha fechado e nunca é herdada
em silêncio.

Nenhuma imagem do SHA `0d6f11f8…` é reutilizada: a tag do componente deriva de
`sha-<commitSha>` do novo SHA, exigida por `RESULT_IDENTITY` e pelo padrão do
schema.

### 3.8 Documentação ativa — §2.5

| Arquivo | Alteração |
|---|---|
| `.github/workflows/README.md` | "gera e verifica provenance" → "fixa a identidade imutável do registry" |
| `docs/infrastructure/deployment/ci/CI.md` | "publica por digest com provenance verificada, preserva digest e provenance herdidos" → "publica por digest e vincula a referência imutável, preserva digest e referência herdados" |
| `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md` | "provenance, changelog e auditoria" → "referência imutável por digest, changelog e auditoria" |
| `docs/infrastructure/deployment/release-control/CANDIDATOS.md` | parágrafo da attestation substituído por declaração do controle por `immutableRef`, com a razão da remoção; "preservam `immutableRef` e provenance" → "`immutableRef` e digest" |
| `docs/infrastructure/deployment/release-control/README.md` | "digests e provenance verificadas" → "digests e referências imutáveis verificadas" |
| `docs/infrastructure/deployment/release-control/RELEASES.md` | "labels, checks ou provenance" → "labels, checks ou referência imutável"; removida "a attestation" da lista de heranças |

A única menção remanescente de attestation na documentação ativa é a nota
explicativa em `CANDIDATOS.md`, que registra a remoção e sua causa. Registros
históricos de runs, tasks e relatórios anteriores permanecem literais.

## 4. Verificação executada

### 4.1 Provas focais da §5.1

Não foi feita busca textual como prova: os produtores e validadores reais foram
executados e seus JSONs confrontados.

| Prova | Resultado |
|---|---|
| workflow com permissões exatas e sem ação/comando de attestation | `candidate-workflow:valid`, exit 0, com `ATTESTATION_FORBIDDEN` ativo |
| `image_result.py remote` produzindo resultado imediatamente válido | `validate(...)` chamado dentro de `remote`, com `raise` antes da escrita; `test_15` confirma `[]` para o valor canônico |
| resultado, candidato, release e deploy rejeitando digest/`immutableRef` divergentes | tabela da §3.6, todos verdes |
| formas fechadas rejeitando reintrodução de `provenance` | `RESULT_SHAPE`, `validate_manifest`, `validate_release` e `additionalProperties:false` nos dois schemas |
| exemplos de candidato e release válidos e canônicos | `candidate:valid` e `global-release:valid`, exit 0 |
| hashes derivados recalculados | §3.5, pelo produtor real |
| primeiro candidato após run falho com os seis builds | §3.7, `test_09` |

### 4.2 Treze validadores canônicos — todos exit 0

| Comando | Saída |
|---|---|
| `python3 tools/docker/validate_node_images.py validate` | `node-images-contract:valid` |
| `python3 tools/docker/java_images_contract.py validate` | `java-images-contract:valid` |
| `python3 tools/ci/validate_ci.py` | `ci:valid` |
| `python3 tools/candidates/validate_candidate_workflow.py` | `candidate-workflow:valid` |
| `python3 tools/releases/validate_release_workflow.py` | `release-workflow:valid` |
| `python3 tools/releases/validate_publisher_ui.py` | `publisher-ui:valid` |
| `python3 tools/releases/validate_publisher_identity_bridge.py` | `publisher-identity-bridge:valid` |
| `python3 tools/ci/validate_workflow_inventory.py` | `workflow-inventory:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | `release-control-contract:valid` |
| `python3 tools/security/bootstrap_contract.py validate` | `bootstrap-contract:valid` |

### 4.3 Oito suítes Python

| Suíte | Exit | Testes | Resultado |
|---|---:|---:|---|
| `tools/docker/tests` | 0 | 117 | `OK` |
| `tools/ci/tests` | **1** | 30 | `FAILED (failures=1)` |
| `tools/candidates/tests` | 0 | 70 | `OK` |
| `tools/releases/tests` | 0 | 298 | `OK` |
| `tools/deploy/tests` | **1** | 353 | `FAILED (errors=1)` |
| `tools/security/tests` | 0 | 26 | `OK` |
| `tools/compose/tests` | 0 | 4 | `OK` |
| `tools/gateway/tests` | 0 | 4 | `OK` |

Total: 902 testes; 900 verdes. `tools/candidates/tests` subiu de 68 para 70
casos pelos dois testes da §2.4. `tools/docker/tests` permanece em 117.

### 4.4 Exemplos e whitespace

```text
python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json
candidate:valid                                                        exit 0

python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json
global-release:valid                                                   exit 0

git diff --check                                                       exit 0
```

### 4.5 Secret scan

```text
secret-scan:clean:scanned=2457:allowed=336:unsupported=0:history_scanned=48852
```

Exit 0, `unsupported=0`. Executado sobre o worktree; o stage não foi preparado
por causa da parada da §6.

## 5. Condição de parada — caminhos necessários fora da fronteira

A §3 determina parar e registrar antes de ampliar a fronteira; a §6 determina
parar antes do commit se surgir caminho necessário fora dela. **Dois** caminhos
se enquadram, e por isso o commit não foi criado.

### 5.1 `tools/ci/tests/test_invocability.py` — causado pela S34

```text
FAIL: test_01_all_27_commands_stop_at_the_cli_boundary
AssertionError: 27 != 26
```

A remoção do comando `image_result.py attest` reduz o inventário de invocações
de 27 para 26. `tools/ci/invocability.py` **está** na fronteira e foi ajustado;
o teste que fixa o número **não está**, e o valor aparece tanto no corpo quanto
no nome do teste. A contagem foi confirmada listando o inventário real: 26
invocações `python3 tools/...`, sendo a única ausente exatamente a removida.
Não existe alternativa legítima dentro da fronteira — manter 27 exigiria
inventar um comando.

Ajuste necessário: `assertEqual(27, len(commands))` → `26` e renomear o teste.

### 5.2 `tools/deploy/validate_deployer_identity_bridge.py` — pré-existente, alheio à S34

```text
ERROR: test_real_identity_bridge_is_valid
deployer-identity:invalid — new Maven dependencies detected:
com.squareup.okhttp3:okhttp-bom, commons-beanutils:commons-beanutils, org.apache.neethi:neethi
```

Esta falha **não é da S34**. Prova executada: o mesmo teste foi rodado num
worktree isolado em `HEAD` (`git worktree add --detach <tmp> HEAD`) e falhou
identicamente; o worktree foi removido em seguida. A S34 não toca
`backend/pom.xml` nem qualquer entrada lida por esse validador.

Causa provável, para avaliação do orquestrador: o validador mantém lista fechada
de dependências gerenciadas e passou a acusar `okhttp-bom`, introduzido pela
correction-01 da S32, além de `commons-beanutils` e `neethi`, anteriores. O
defeito só aparece agora porque `tools/deploy/tests` **entrou na matriz nesta
slice** — S31, S32 e S33 executaram sete suítes, sem `deploy`; a §5.2 da S34
passou a exigir oito.

Nem o validador nem seu teste estão na fronteira da §3.

## 6. Estado final e negativos preservados

Vinte e seis arquivos alterados, **todos** dentro da fronteira da §3, conferidos
um a um:

```text
.github/workflows/README.md
.github/workflows/publish-candidate.yml
docs/infrastructure/deployment/ci/CI.md
docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md
docs/infrastructure/deployment/release-control/CANDIDATOS.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/RELEASES.md
ops/releases/candidate-manifest.schema.json
ops/releases/examples/candidate-manifest.example.json
ops/releases/examples/global-release.example.json
ops/releases/examples/release-publication-outcome.example.json
ops/releases/examples/release-publication-plan.example.json
ops/releases/global-release.schema.json
tools/candidates/image_result.py
tools/candidates/tests/test_causal_corrections.py
tools/candidates/tests/test_definitive_contract.py
tools/candidates/tests/test_terminal_amendment.py
tools/candidates/validate_candidate_workflow.py
tools/ci/invocability.py
tools/deploy/deployment_plan.py
tools/deploy/tests/test_deployment_plan.py
tools/deploy/tests/test_production_adapter.py
tools/releases/candidate_manifest.py
tools/releases/global_release.py
tools/releases/tests/test_candidate_manifest_v2.py
tools/releases/tests/test_global_release.py
```

Linhagem técnica sobre os 21 caminhos técnicos/fixtures/testes da §3, em ordem
de caminho, com os cinco documentos da "Documentação ativa" e este relatório
fora dela:

```text
sourceSha        = 7216238c71bbedd4c939d70e3bf481c279a249ee
sourceDiffSha256 = ac666ffd65a804f811d4ec30759e409784f809d23cf45c9c6cdfce581c0b46c6
sourceTreeSha    = b1a58753e09b08c18678a9ca0a4c52827fadb86e
```

`git diff-tree` entre a árvore do SHA-base e a árvore medida lista exatamente
esses 21 caminhos. `.github/workflows/README.md` foi incluído por constar do
grupo "Workflow e contratos" da §3, e não do grupo "Documentação ativa"; se o
orquestrador preferir tratá-lo como documento, a linhagem precisa ser
recalculada sobre 20 caminhos.

Negativos preservados: nenhum Maven, npm, build Docker, Trivy de imagem, GHCR,
login, pull, push, rerun, workflow dispatch, exclusão de imagem, tag, release,
deploy, rollback, SSH ou produção. As imagens do SHA `0d6f11f8…` permanecem
imutáveis e sem candidato. A task, este contrato e o README de implementação não
foram alterados; nenhuma autorização remota foi criada e a S30a/S30b não
avançaram.

Estado do Git ao encerrar: branch `main`, `HEAD` em
`7216238c71bbedd4c939d70e3bf481c279a249ee`, `origin/main` em
`0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb`, um commit local, **stage vazio**,
worktree com os 26 arquivos acima e este relatório não rastreado.

## 7. Retomada pela correction-01

> **Autoridade:** `S34-remocao-atestacao-nativa-candidato.correction-01.md`, `AUTHORIZED`
> **Checkpoint:** `3e736c56c5dbeeee919aabc318451e77ac5afc85`
> **Resultado:** três gates fechados; commit técnico único criado; sem push

### 7.1 Preflight da §4 da correction-01

| Item | Exigido | Observado | OK |
|---|---|---|:--:|
| CWD | `/home/gregorio/git/baronesa/emporio` | idem | sim |
| branch | `main` | `main` | sim |
| mensagem de `HEAD` | `docs: correct S34 closure gates` | idem | sim |
| `HEAD` | — | `3e736c56c5dbeeee919aabc318451e77ac5afc85` | — |
| `origin/main` | `0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb` | idem | sim |
| commits em `origin/main..HEAD` | 2 | 2 | sim |
| stage | vazio | vazio | sim |
| worktree | 26 modificados + relatório não rastreado | 27 entradas, exatamente essas | sim |

A implementação da primeira passagem foi integralmente preservada: nada foi
descartado, restaurado ou reimplementado.

### 7.2 Inventário invocável — §3.1

Em `tools/ci/tests/test_invocability.py`:

- `test_01_all_27_commands_stop_at_the_cli_boundary` renomeado para
  `test_01_all_26_commands_stop_at_the_cli_boundary`;
- expectativa literal `assertEqual(27, len(commands))` → `26`.

As demais expectativas do teste — `inventory_errors` vazio, `validate` sem
erros e o conjunto de scripts sem argumento — foram preservadas. Nenhum comando
substituto foi criado e nenhuma outra cobertura foi reduzida.

```text
python3 tools/ci/invocability.py
invocability:valid:commands=26:parse_args=23:argument-free=3        exit 0
```

### 7.3 Snapshot Maven do deployer — §3.2

Em `tools/deploy/validate_deployer_identity_bridge.py`, `KNOWN_MAVEN_DEPENDENCIES`
reconciliado com o POM aceito atual:

| Operação | Entrada | Origem já aceita |
|---|---|---|
| adicionar | `com.squareup.okhttp3:okhttp-bom` | `94c4b73` (S32 correction-01) |
| adicionar | `commons-beanutils:commons-beanutils` | `5360356` |
| adicionar | `org.apache.neethi:neethi` | `5360356` |
| remover | `br.com.swconsultoria:java-danfe` | `db9cc90` (S33) |

O comentário do snapshot passou a nomear o baseline aceito atual — S23 mais
esses três commits — sem enfraquecer a comparação. A natureza fechada foi
preservada: continuam existindo as duas direções, `unexpected` e `missing`, e a
allowlist **não** foi transformada em leitura autorreferente do próprio POM.
`backend/pom.xml` não foi tocado.

`tools/deploy/tests/test_deployer_identity_bridge_contract.py` não precisou de
ajuste: `test_new_arbitrary_maven_dependency_fails` já injeta
`org.tinylog:tinylog-impl` e segue reprovando, agora com a suíte inteira verde.

```text
python3 tools/deploy/validate_deployer_identity_bridge.py
deployer-identity:valid                                             exit 0
```

### 7.4 Hashes das fixtures de publicação — §3.3

Identidades recomputadas pelo produtor real
`artifact_io.digest(artifact_io.canonical(...))` sobre os exemplos finais, e
conferidas contra os valores da correction-01:

```text
candidate-manifest.example.json = sha256:b818a0462d2333b74e36d6d09b1084fe97389be7e5602400cee6bb98ce8ee1c9
global-release.example.json     = sha256:cfa61fcf2d0d731fbcd68cb383e5491bfc6075733b378ff446bd28dbf702957e
```

Semântica aplicada:

| Campo | Hash | Estado |
|---|---|---|
| `global-release.candidate.manifestSha256` | candidato `b818a046…` | já correto na primeira passagem |
| `release-publication-plan.candidate.manifestSha256` | candidato `b818a046…` | já correto na primeira passagem |
| `release-publication-plan.target.manifestSha256` | release `cfa61fcf…` | **corrigido** |
| `release-publication-outcome.manifestSha256` | release `cfa61fcf…` | **corrigido** |

A substituição no plano foi feita apenas dentro do bloco `target`, para não
tocar o binding de candidato que compartilhava o valor anterior.

Prova derivada acrescentada em
`tools/releases/tests/test_release_publication.py`, sem hash literal:
`test_00_example_manifest_bindings_are_derived_from_the_examples` recalcula os
dois digests a partir do conteúdo dos exemplos e exige que sejam distintos, que
os dois bindings de candidato igualem o digest do candidato e que `target` e
`outcome` igualem o digest da release. O teste fecha ainda o elo com o caminho
executável, exigindo
`release_digest == rp.digest(self.bundle()["release.json"])` — exatamente a
comparação de `release_publication.py:1328` que a fixture antes contradizia.

Dois guardas de não regressão do mesmo arquivo contam os testes existentes por
prefixo. O teste novo entra legitimamente nos dois conjuntos, e suas
expectativas foram incrementadas em exatamente uma unidade cada:
`test_c02_15_previous_205_tests_remain_part_of_discovery` de 65 para 66 e
`test_c02a_08_previous_220_tests_remain_part_of_discovery` de 80 para 81. O
propósito dos guardas — detectar remoção acidental de testes anteriores —
permanece intacto.

### 7.5 Matriz focal da §4

Validadores afetados e exemplos:

| Comando | Exit | Saída |
|---|---:|---|
| `python3 tools/ci/invocability.py` | 0 | `invocability:valid:commands=26:parse_args=23:argument-free=3` |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | `candidate-workflow:valid` |
| `python3 tools/deploy/validate_deployer_identity_bridge.py` | 0 | `deployer-identity:valid` |
| `python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json` | 0 | `candidate:valid` |
| `python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json` | 0 | `global-release:valid` |
| `git diff --check` | 0 | vazia |

Suítes exigidas pela correction:

| Suíte | Exit | Testes | Resultado |
|---|---:|---:|---|
| `tools/ci/tests` | 0 | 30 | `OK` |
| `tools/deploy/tests` | 0 | 353 | `OK` |
| `tools/releases/tests` | 0 | 299 | `OK` |

As cinco suítes já verdes foram reconfirmadas como evidência, sem alteração:

| Suíte | Exit | Testes | Resultado |
|---|---:|---:|---|
| `tools/docker/tests` | 0 | 117 | `OK` |
| `tools/candidates/tests` | 0 | 70 | `OK` |
| `tools/security/tests` | 0 | 26 | `OK` |
| `tools/compose/tests` | 0 | 4 | `OK` |
| `tools/gateway/tests` | 0 | 4 | `OK` |

**Matriz final consolidada: 903 testes, zero falha.** São os 902 da primeira
passagem mais `test_00_example_manifest_bindings_are_derived_from_the_examples`;
as duas falhas anteriores — `test_invocability` e
`test_deployer_identity_bridge_contract` — estão fechadas.

### 7.6 Linhagem recalculada

Sobre os 24 caminhos técnicos/fixtures/testes efetivamente alterados, agora
incluindo os três acrescentados pela correction-01. A inclusão de
`.github/workflows/README.md` está aceita e foi mantida. Os cinco documentos
ativos e este relatório continuam fora da linhagem.

```text
sourceSha        = 3e736c56c5dbeeee919aabc318451e77ac5afc85
sourceDiffSha256 = f4f040f2668fccec17e05209337ceaee46172c3ed6dc32083f203c415dc2b903
sourceTreeSha    = a603198bf56a873b50afc8a372ef0b7ddc2ebfe8
```

`git diff-tree` entre a árvore do checkpoint e a árvore medida lista exatamente
esses 24 caminhos. Caminhos acrescentados nesta retomada:

```text
tools/ci/tests/test_invocability.py
tools/deploy/validate_deployer_identity_bridge.py
tools/releases/tests/test_release_publication.py
```

`tools/deploy/tests/test_deployer_identity_bridge_contract.py` foi autorizado
pela correction mas **não** precisou de alteração, e por isso não entra na
linhagem nem no stage.

### 7.7 Gates finais, stage e commit

`git diff --check` exit 0. Stage preparado com os 29 caminhos técnicos,
fixtures, testes e documentação ativa realmente alterados, mais este relatório —
30 caminhos, todos autorizados pela task ou pela correction-01.
`git diff --cached --check` exit 0.

Saída literal de `python3 tools/ci/secret_scan.py --tracked`, exit 0:

```text
secret-scan:clean:scanned=2459:allowed=352:unsupported=0:history_scanned=51310
```

Como registrar essa saída altera o próprio relatório, ele foi levado a stage de
novo e o mesmo secret scan foi repetido, exit 0, com saída literal idêntica à
acima; o conteúdo commitado é o varrido pela última execução.
`git diff --cached --check` foi repetido e retornou exit 0.

`unsupported=0` nas duas execuções. Nenhum token, header, credencial ou valor de
configuração foi registrado neste relatório ou nas fixtures.

Commit técnico local único criado com a mensagem exata
`fix: remove unavailable candidate attestation contract`, sem `--no-verify`, sem
force, sem tag, sem outra branch, sem outro remote e sem commit adicional.
`git diff --check origin/main..HEAD` retornou exit 0.

Negativos preservados nesta retomada: `backend/pom.xml` intocado; produtores de
release e schemas sem mudança adicional; nenhum Maven, npm, build Docker, Trivy
de imagem, GHCR, login, pull, push, rerun, workflow dispatch, release, deploy,
rollback, SSH ou produção. As imagens do SHA `0d6f11f8…` permanecem imutáveis e
sem candidato. A task, a correction-01 e o README de implementação não foram
alterados; a S30a e a S30b não avançaram.

commit final = HEAD entregue

IN_PROGRESS — aguardando revisão do orquestrador

## 8. Revisão do orquestrador

> **Data:** 02/08/2026
> **Estado:** `ACCEPTED`

A implementação e a correction-01 estão aceitas. A revisão independente
confirmou:

- 30 testes de CI, 299 de releases e 353 de deploy, todos verdes;
- os cinco validadores focais com exit 0;
- digest canônico do candidato `sha256:b818a0462d2333b74e36d6d09b1084fe97389be7e5602400cee6bb98ce8ee1c9`;
- digest canônico da release `sha256:cfa61fcf2d0d731fbcd68cb383e5491bfc6075733b378ff446bd28dbf702957e`;
- `sourceDiffSha256 = f4f040f2668fccec17e05209337ceaee46172c3ed6dc32083f203c415dc2b903` e `sourceTreeSha = a603198bf56a873b50afc8a372ef0b7ddc2ebfe8` reproduzidos sobre os 24 caminhos da linhagem;
- ausência da action, das permissões e do comando de atestação no caminho executável; as ocorrências residuais de `provenance` são mutantes de rejeição intencionais;
- `git diff --check origin/main..HEAD` com exit 0 e estado Git limpo.

O commit `5a8178e2b7574ebd42441811b4a4cdae3a8f762e` remove o controle indisponível
sem criar substituto nominal. A identidade efetiva continua fechada por
`imageRepository@sha256:digest` no resultado, candidato, release e deploy.

S34 aceita. A ação seguinte está limitada pela authorization-03 da S30a.

ACCEPTED
