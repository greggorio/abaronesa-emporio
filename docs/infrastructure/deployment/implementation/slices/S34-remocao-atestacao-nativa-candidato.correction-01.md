# S34 — correction-01: fechamento dos gates e hashes de publicação

> **Estado:** `AUTHORIZED`
> **Autoridade:** complementa a S34 sem alterar sua decisão arquitetural
> **Checkpoint:** commit do orquestrador com mensagem exata `docs: correct S34 closure gates`
> **Commit técnico final:** permanece `fix: remove unavailable candidate attestation contract`

## 1. Diagnóstico aceito

A parada da §6 foi correta. A implementação da fronteira original está
preservada, 13 validadores passaram e 900 de 902 testes ficaram verdes. As duas
falhas têm causas distintas e fechadas:

1. `tools/ci/tests/test_invocability.py` ainda fixa 27 comandos, mas a remoção
   causal de `image_result.py attest` reduz o inventário real para 26;
2. `validate_deployer_identity_bridge.py` mantém o snapshot Maven da S23 e não
   incorpora alterações já aceitas depois dela.

A segunda falha foi reproduzida em worktree isolado no `HEAD`, sem a S34. O
snapshot atual também ainda exige `java-danfe`, removido e aceito pela S33; após
apenas adicionar os três itens reportados, ele falharia por essa ausência.

Há ainda uma divergência nas fixtures já autorizadas pela S34:
`release-publication-plan.target.manifestSha256` e o outcome de publicação
devem identificar o manifesto da release, não o manifesto do candidato. O
produtor real em `release_publication.py` implementa essa distinção. Preservar
o mesmo hash nos três campos deixaria a fixture incompatível com o caminho
executável que ela documenta.

## 2. Ampliação mínima de fronteira

Ficam autorizados, além da §3 original:

```text
tools/ci/tests/test_invocability.py
tools/deploy/validate_deployer_identity_bridge.py
tools/deploy/tests/test_deployer_identity_bridge_contract.py
tools/releases/tests/test_release_publication.py
```

Alterar somente os arquivos necessários. `backend/pom.xml`, produtores de
release e schemas não recebem mudança adicional por esta correction.

## 3. Correções fechadas

### 3.1 Inventário invocável

Em `test_invocability.py`:

- renomear o teste `test_01_all_27_commands_stop_at_the_cli_boundary` para 26;
- alterar a expectativa literal de 27 para 26;
- preservar as demais expectativas e mutantes.

Não criar comando substituto e não reduzir outra cobertura.

### 3.2 Snapshot Maven do deployer

Em `validate_deployer_identity_bridge.py`, reconciliar
`KNOWN_MAVEN_DEPENDENCIES` com o POM aceito atual:

```text
adicionar com.squareup.okhttp3:okhttp-bom
adicionar commons-beanutils:commons-beanutils
adicionar org.apache.neethi:neethi
remover br.com.swconsultoria:java-danfe
```

Os overrides de BeanUtils e Neethi foram introduzidos no commit `5360356`, o
BOM OkHttp no `94c4b73` e `java-danfe` saiu no `db9cc90`. São
mudanças já aceitas, não dependências criadas pela S34.

Atualizar o comentário do snapshot para indicar o baseline aceito atual, sem
enfraquecer a comparação fechada. Preservar a rejeição de dependência Maven
arbitrária. Se necessário, ajustar
`test_deployer_identity_bridge_contract.py` apenas para fixar o novo conjunto e
provar que `org.tinylog:tinylog-impl` continua reprovado.

Não modificar o POM nem transformar a allowlist em leitura autorreferente do
próprio POM.

### 3.3 Hashes das fixtures de publicação

Usar `artifact_io.digest(artifact_io.canonical(...))` sobre os exemplos finais.
Com o conteúdo atual da S34, as identidades são:

```text
candidate-manifest.example.json = sha256:b818a0462d2333b74e36d6d09b1084fe97389be7e5602400cee6bb98ce8ee1c9
global-release.example.json      = sha256:cfa61fcf2d0d731fbcd68cb383e5491bfc6075733b378ff446bd28dbf702957e
```

Aplicar a semântica:

| Campo | Hash obrigatório |
|---|---|
| `global-release.candidate.manifestSha256` | candidato `b818a046…` |
| `release-publication-plan.candidate.manifestSha256` | candidato `b818a046…` |
| `release-publication-plan.target.manifestSha256` | release `cfa61fcf…` |
| `release-publication-outcome.manifestSha256` | release `cfa61fcf…` |

Adicionar em `test_release_publication.py` uma prova derivada dos conteúdos dos
exemplos, sem repetir hashes literais no teste: os dois bindings de candidato
devem igualar o digest canônico do candidato; target e outcome devem igualar o
digest canônico da release. Isso fecha a inconsistência pré-existente sem mudar
o produtor ou a forma dos contratos.

Se qualquer outra alteração mudar um dos exemplos, recalcular os valores pelo
produtor real e registrar os hashes efetivos no relatório.

## 4. Retomada focal

Antes de agir, confirmar:

```text
CWD = /home/gregorio/git/baronesa/emporio
branch = main
mensagem de HEAD = docs: correct S34 closure gates
origin/main = 0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb
commits em origin/main..HEAD = 2
stage = vazio
```

O worktree deve preservar exatamente os 26 arquivos modificados e o relatório
não rastreado descritos na primeira passagem, sem outro caminho. Não descartar,
restaurar ou reimplementar esse trabalho.

Depois das correções, executar:

```bash
python3 tools/ci/invocability.py
python3 tools/candidates/validate_candidate_workflow.py
python3 tools/deploy/validate_deployer_identity_bridge.py
python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json
python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v
git diff --check
```

As cinco suítes anteriormente verdes e os demais validadores não afetados não
precisam ser repetidos. Consolidar no relatório a matriz final de 902 testes
mais os testes novos desta correction, com zero falha.

## 5. Linhagem, relatório e commit

A inclusão de `.github/workflows/README.md` nos 21 caminhos da linhagem inicial
está aceita, pois a task o colocou no grupo `Workflow e contratos`. Não
recalcular apenas para excluí-lo.

Recalcular `sourceDiffSha256` e `sourceTreeSha` sobre todos os caminhos
técnicos/fixtures/testes efetivamente alterados, agora incluindo os caminhos
adicionados por esta correction. Documentação ativa e relatório continuam fora
da linhagem.

Acrescentar ao relatório uma seção da correction-01, preservando a parada
original. Registrar os novos paths, hashes corretos, comandos/exits, contagens,
linhagem e estado Git.

Preparar stage somente com os caminhos da task/correction realmente alterados
e o relatório. Executar `git diff --cached --check` e o secret scan rastreado;
exigir exit 0 e `unsupported=0`. Criar exatamente um commit técnico:

```text
fix: remove unavailable candidate attestation contract
```

Depois do commit, exigir stage e worktree vazios,
`git diff --check origin/main..HEAD` exit 0 e três commits locais sobre
`origin/main` — checkpoint S34, esta correction e o commit técnico.

Não fazer push, rerun, workflow dispatch, GHCR, release, deploy, rollback ou
qualquer efeito remoto. O relatório termina exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```
