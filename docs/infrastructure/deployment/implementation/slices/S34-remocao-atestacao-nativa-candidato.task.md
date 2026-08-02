# S34 — Remoção da atestação nativa indisponível

> **Estado:** `PLANNED`
> **Tipo:** correção causal do primeiro Publish Candidate
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Relatório:** `S34-remocao-atestacao-nativa-candidato.report.md`
> **Commit técnico:** `fix: remove unavailable candidate attestation contract`

## 1. Resultado esperado

O run `30742264661` provou o pipeline até o primeiro recurso incompatível com
o ambiente contratado: os seis componentes construíram, passaram no Trivy e
foram publicados no GHCR, mas `actions/attest-build-provenance` recusou o
repositório privado de propriedade de usuário. Nenhum candidato final foi
produzido.

Esta slice remove integralmente a atestação nativa do fluxo e dos contratos que
a carregam. Não cria placeholder, `attestationId = 0`, URL sintética, cosign ou
controle nominal equivalente.

A garantia preservada é a identidade imutável da imagem:

```text
digest = sha256:<64 hex>
immutableRef = imageRepository + "@" + digest
```

Ela continua vinculada ao componente, tag do SHA, commit, run/attempt, labels
OCI e checks de build/test/scan, e continua sendo exigida no candidato, na
release global e no plano de deploy.

Ao final deve existir um commit técnico local, sem push. A S30a e a S30b não
avançam nesta execução.

## 2. Decisões fechadas

### 2.1 Workflow do candidato

Em `.github/workflows/publish-candidate.yml`:

- remover `id-token: write` e `attestations: write` do job `build`;
- remover o step `actions/attest-build-provenance`;
- remover `gh attestation verify` e o subcomando `image_result.py attest`;
- fazer `image_result.py remote` produzir e validar diretamente o resultado
  final baseado no manifesto remoto inspecionado;
- enviar `component-result.json` imediatamente depois da inspeção remota;
- preservar a ordem build -> Trivy -> login -> push/inspect -> upload ->
  cleanup;
- preservar actions pinadas, `packages: write`, plataforma, tags, labels,
  scans, logout e limpeza da imagem local.

O workflow não recebe permissão ou step substituto.

### 2.2 Contrato de dados

Remover a propriedade `provenance` dos resultados de componente, manifestos de
candidato e releases globais. Remover também `attestationId`,
`attestationUrl`, `verifiedSubject` e `verifiedAt` de schemas, exemplos,
montagem e validação.

Os schemas permanecem nas versões atuais. Esta é uma correção pré-ativação:
não existe candidato final ou release real publicado no repositório, portanto
não há artefato válido anterior a migrar ou manter compatível.

Objetos com `provenance`, placeholders ou campos de attestation devem ser
rejeitados pelas formas fechadas dos schemas/validadores, não tolerados como
legado opcional.

### 2.3 Integridade por digest

Preservar e testar em todas as camadas:

- repositório canônico por componente;
- `digest` SHA-256 válido;
- igualdade exata `immutableRef == imageRepository@digest`;
- tag `imageRepository:sha-<commitSha>`;
- binding de commit, workflow run e attempt;
- labels OCI esperadas e checks `build/test/scan = passed`;
- sidecars e hashes dos bundles de candidato e release;
- propagação exclusiva de `immutableRef` ao plano e ao executor de deploy.

Substituir mutantes que alteravam somente `verifiedSubject` por mutantes que
troquem digest, repositório ou `immutableRef` entre componentes. Cada camada
deve reprovar a divergência por seu erro canônico já existente.

### 2.4 Primeiro candidato após a falha

O run reprovado não tem `candidate-outcome` e não pode ser predecessor.
`previous_candidate.discover` considera apenas runs concluídos em `success` com
outcome e manifesto válidos; portanto o próximo run continua em modo `first` e
`lineage.effective(..., first_release=True)` exige build dos seis componentes.

Fixar esse comportamento em teste: uma execução anterior reprovada ou sem
outcome não pode ser selecionada, herdada nem impedir a resolução
`first_release` com os seis `buildComponents`. Não reutilizar as imagens do SHA
`0d6f11f...`; o próximo candidato usa a tag do novo SHA.

### 2.5 Documentação ativa

Atualizar somente os documentos correntes que descrevem provenance/attestation
como parte positiva do candidato, release ou deploy. Explicar que o controle de
integridade adotado é a referência imutável por digest. Registros históricos de
runs, tasks e relatórios anteriores permanecem literais.

## 3. Fronteira autorizada

Workflow e contratos:

```text
.github/workflows/publish-candidate.yml
.github/workflows/README.md
tools/candidates/image_result.py
tools/candidates/validate_candidate_workflow.py
tools/ci/invocability.py
tools/releases/candidate_manifest.py
tools/releases/global_release.py
tools/deploy/deployment_plan.py
ops/releases/candidate-manifest.schema.json
ops/releases/global-release.schema.json
```

Fixtures e hashes derivados:

```text
ops/releases/examples/candidate-manifest.example.json
ops/releases/examples/global-release.example.json
ops/releases/examples/release-publication-plan.example.json
ops/releases/examples/release-publication-outcome.example.json
```

Testes focais:

```text
tools/candidates/tests/test_causal_corrections.py
tools/candidates/tests/test_definitive_contract.py
tools/candidates/tests/test_terminal_amendment.py
tools/releases/tests/test_candidate_manifest_v2.py
tools/releases/tests/test_global_release.py
tools/deploy/tests/test_deployment_plan.py
tools/deploy/tests/test_production_adapter.py
```

Documentação ativa:

```text
docs/infrastructure/deployment/ci/CI.md
docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md
docs/infrastructure/deployment/release-control/CANDIDATOS.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/RELEASES.md
```

Evidência:

```text
docs/infrastructure/deployment/implementation/slices/S34-remocao-atestacao-nativa-candidato.report.md
```

Alterar apenas os caminhos necessários dentro desta lista. Se um teste provar
um consumidor real fora dela, parar e registrar o caminho antes de ampliar a
fronteira.

`tools/releases/validate_release_workflow.py` pode continuar mencionando
`attestations:` como capacidade proibida ao workflow de release; uma proibição
negativa não é o contrato removido.

## 4. Preflight

Antes de alterar arquivos, exigir:

```text
CWD = /home/gregorio/git/baronesa/emporio
branch = main
mensagem de HEAD = docs: record candidate attestation failure and open S34 scope
origin/main = 0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb
commits em origin/main..HEAD = 1
stage = vazio
worktree = limpo
```

Registrar o SHA-base com `git rev-parse HEAD`. Divergência exige parada antes de
editar. Não consultar, copiar ou registrar segredo.

## 5. Verificação obrigatória

### 5.1 Provas focais

Exigir, no mínimo:

- workflow válido com permissões exatas e sem ação/comando de attestation;
- `image_result.py remote` produzindo resultado imediatamente válido;
- resultado, candidato, release e deploy rejeitando digest/immutableRef
  divergentes;
- formas fechadas rejeitando a reintrodução de `provenance`;
- exemplos de candidato e release válidos e canônicos;
- hashes derivados dos exemplos recalculados, nunca editados por aproximação;
- simulação do primeiro candidato após run falho resultando nos seis builds.

Não basta buscar ausência textual: executar os produtores e validadores reais
sobre diretórios temporários e confrontar seus JSONs.

### 5.2 Matriz local

Executar os 13 validadores canônicos:

```bash
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
```

Executar as oito suítes Python:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/security/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/gateway/tests -v
```

Também executar:

```bash
python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json
python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json
git diff --check
```

Esta slice não requer Maven, npm, build Docker, Trivy de imagem, GHCR ou acesso
remoto. O comportamento remoto será provado somente após aceite, em nova
autorização da S30a.

### 5.3 Linhagem, stage e commit

Calcular `sourceDiffSha256` pelo diff binário entre o SHA-base e o conteúdo
técnico final e `sourceTreeSha` por árvore temporária, usando somente os
caminhos técnicos/fixtures/testes da §3; documentos e relatório ficam fora da
linhagem técnica.

Preparar stage somente com caminhos autorizados realmente alterados e o
relatório. Executar `git diff --cached --check` e
`python3 tools/ci/secret_scan.py --tracked`; exigir exit 0 e `unsupported=0`.

Criar exatamente um commit local:

```text
fix: remove unavailable candidate attestation contract
```

Depois do commit, exigir stage e worktree vazios,
`git diff --check origin/main..HEAD` exit 0 e dois commits locais sobre
`origin/main`. Não fazer push.

## 6. Parada e relatório

Parar antes do commit se qualquer camada ainda exigir ou produzir provenance,
se a pinagem por digest for enfraquecida, se a resolução do primeiro candidato
não contiver os seis componentes, se um gate falhar ou se surgir caminho
necessário fora da fronteira.

Não fazer retry remoto, rerun, segundo push, login/pull/push no GHCR, exclusão
das imagens já publicadas, tag, release, deploy, rollback, SSH ou produção. As
imagens do SHA anterior permanecem imutáveis e sem candidato; limpeza de
retenção não pertence a esta slice.

O relatório deve registrar arquivos, alterações de schema, hashes derivados,
mutantes, comandos/exits, contagens, linhagem, secret scan, commit e ausência
de efeitos remotos. O executor não aceita a slice, não altera esta task nem o
README e não cria autorização remota.

Terminar exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```
