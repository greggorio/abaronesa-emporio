# S36 — authorization-01: retomada após fechamento da matriz local

> **Estado:** `AUTHORIZED`
> **Tipo:** continuação exata da S36 após revisão do orquestrador
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Contrato principal:** `S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.task.md`
> **Relatório contínuo:** `S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.report.md`

## 1. Autoridade e resultado esperado

O orquestrador aceitou a parada fail-closed e completou as duas evidências que o
wrapper anterior não capturou. A implementação e a matriz local estão aceitas
para progressão; não existe autorização para nova edição técnica.

Esta autorização permite somente:

1. revalidar que base, diff e remoto continuam idênticos aos revisados;
2. executar uma reconfirmação focal sem alterar arquivos;
3. preparar o stage exato;
4. executar os dois gates finais sobre o stage;
5. criar o único commit técnico prescrito;
6. fazer o único push normal e fast-forward da S36;
7. observar CI e Publish Candidate do SHA exato;
8. auditar máscaras e validar os artifacts finais;
9. acrescentar a evidência desta retomada ao relatório contínuo.

O executor não aceita S36/S30a e não cria S30b.

## 2. Estado obrigatório de retomada

Exigir:

```text
CWD                 /home/gregorio/git/baronesa/emporio
branch              main
HEAD                fcaf9d85de88a4036956619ac4fa7819899fa473
origin/main         68a3528b563ad0c19819da34ab106396ae679596
remoto main         68a3528b563ad0c19819da34ab106396ae679596
ahead               1 commit
stage               vazio
```

Os únicos caminhos modificados devem ser:

```text
docs/infrastructure/deployment/release-control/CANDIDATOS.md
tools/candidates/compose_env.py
tools/candidates/integrated_harness.py
tools/candidates/tests/test_causal_corrections.py
tools/candidates/tests/test_definitive_contract.py
tools/candidates/validate_candidate_workflow.py
```

Os únicos caminhos não rastreados devem ser:

```text
docs/infrastructure/deployment/implementation/slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.task.md
docs/infrastructure/deployment/implementation/slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.authorization-01.md
docs/infrastructure/deployment/implementation/slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.report.md
```

O contrato principal deve conservar:

```text
sha256  2a12b3981f024e5b31aa85995c921dd05763d66d6bd882673a6f809b30bcafd0
```

O relatório deve conter a revisão do orquestrador com a reprodução dos 353
testes de deploy, scanner `secret-scan:clean`/`unsupported=0`, 65 testes focais e
`candidate-workflow:valid`.

Revalidar Git/GitHub, ausência de runs ativos e autenticação sem registrar
credencial. Qualquer divergência exige parada antes do stage.

## 3. Preservação integral

Não editar nenhum dos seis arquivos implementados, o contrato principal ou esta
autorização. Não descartar, reformatar, regenerar ou tentar melhorar o patch.

O único arquivo que poderá ser editado, e somente depois da observação remota ou
de uma parada, é o relatório contínuo para acrescentar uma seção
`Retomada authorization-01`.

## 4. Reconfirmação focal

Executar uma única vez:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/candidates/tests/test_causal_corrections.py tools/candidates/tests/test_definitive_contract.py -v
python3 tools/candidates/validate_candidate_workflow.py
git diff --check
```

Exigir 65 testes em `OK`, `candidate-workflow:valid` e exits 0. Esta autorização
não manda repetir os outros validadores ou as oito suítes: suas evidências já
foram aceitas pelo orquestrador.

Falha encerra a autorização sem stage.

## 5. Stage e gates finais

Preparar stage exatamente com:

```text
docs/infrastructure/deployment/implementation/slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.task.md
docs/infrastructure/deployment/implementation/slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.authorization-01.md
docs/infrastructure/deployment/release-control/CANDIDATOS.md
tools/candidates/compose_env.py
tools/candidates/integrated_harness.py
tools/candidates/tests/test_causal_corrections.py
tools/candidates/tests/test_definitive_contract.py
tools/candidates/validate_candidate_workflow.py
```

Não incluir o relatório. Confirmar os oito caminhos com
`git diff --cached --name-status` e executar:

```bash
git diff --cached --check
python3 tools/ci/secret_scan.py --tracked
```

O scanner pode permanecer silencioso por vários minutos. Se a ferramenta de
execução devolver uma sessão ainda ativa, continuar consultando a mesma sessão
até obter o exit terminal; não iniciar outra invocação. Exigir exit 0,
`secret-scan:clean` e `unsupported=0`.

Falha real de qualquer gate exige `git restore --staged` somente dos oito
caminhos nominais acima, preservando o worktree, e parada sem commit.

## 6. Commit e preflight remoto

Com os gates finais verdes, criar exatamente:

```text
fix: close candidate integration security gates
```

Não usar amend ou `--no-verify`. Depois do commit, exigir:

- stage vazio;
- somente o relatório contínuo como caminho não rastreado;
- remoto ainda em `68a3528b563ad0c19819da34ab106396ae679596`;
- `origin/main` ancestral do novo `HEAD`;
- exatamente dois commits em `origin/main..HEAD`, nesta ordem: handoff
  `fcaf9d8` e o commit técnico novo;
- `git diff --check origin/main..HEAD` em exit 0;
- nenhum run `CI` ou `Publish Candidate` ativo em `main`.

Definir o novo `HEAD` como `TARGET_SHA`.

## 7. Único push

Executar uma única vez:

```bash
git push origin main:main
```

Exigir push normal, não forçado e fast-forward. Não fazer retry, segundo push,
pull, rebase, merge, amend ou novo commit. Falha preserva o estado e encerra a
autorização.

## 8. CI e Publish Candidate

Sem editar arquivo depois do push, localizar o único run `CI`, evento `push`,
com `headSha = TARGET_SHA`. Aguardar sem intervenção e exigir 13 jobs em
`success`.

Depois localizar o único `Publish Candidate`, evento `workflow_run`, do mesmo
SHA e vinculado à CI. Exigir attempt 1 e:

- `trust` e `predecessor` em `success`;
- seis jobs `build` em `success`;
- `assemble`, `integrated` e `publish` em `success`;
- modo `continue`;
- predecessor igual ao candidato publicado para `68a3528...`;
- seis componentes em `buildComponents`, nenhum herdado;
- nenhum workflow de release, deploy ou rollback.

Ausência por cinco minutos, duplicidade, attempt diferente de 1 ou falha de gate
exige parada sem rerun, dispatch, cancelamento ou correção.

## 9. Logs e artifacts

Aplicar integralmente as seções 9 e 10 do contrato principal:

- auditar somente os logs novos do Publish Candidate;
- registrar somente `<CHAVE> | MASKED` ou `<CHAVE> | UNMASKED`;
- nunca transcrever, hashear ou caracterizar valor encontrado;
- exigir `***` para as sete chaves depois de `Emit LF environment`;
- não usar `secret-scan:clean` como prova de máscara;
- baixar em diretório `mktemp -d` apenas `candidate-effective-plan`,
  `candidate-manifest` e `candidate-outcome` do run exato;
- validar sidecars, schemas, SHA, runs, predecessor, seis componentes, checks,
  integração, referências imutáveis, ausência de attestation e outcome
  `published`;
- remover somente os diretórios temporários nominais;
- não fazer login/pull/push manual no GHCR nem excluir evidência remota.

Valor `UNMASKED`, artifact ausente/inválido ou vínculo divergente exige parada
sem correção.

## 10. Relatório contínuo

Depois do sucesso ou parada, acrescentar ao relatório existente uma seção
`Retomada authorization-01` com:

- preflight e integridade dos dois documentos de autoridade;
- reconfirmação focal;
- stage exato e gates finais;
- commit, `TARGET_SHA` e push sanitizado;
- IDs, URLs, eventos, attempts e jobs dos dois workflows;
- tabela com nomes das sete chaves e somente `MASKED`/`UNMASKED`;
- identidade e vínculos dos artifacts;
- primeira causa e ponto de parada, se houver;
- estado Git, resíduos e negativos preservados.

O relatório permanece local, não rastreado e não commitado. Não existe segundo
push para publicá-lo.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — aguardando revisão e aceite da S30a pelo orquestrador
```

Em falha, terminar com `IN_PROGRESS —` e a causa objetiva. O executor não aceita
S36/S30a nem abre S30b.

## 11. Limites permanentes

Não editar código, testes, documentação versionada, workflow, Compose,
Dockerfile, entrypoint, schema, manifesto, outcome, probe ou catálogo. Não
acessar `ops/env/.env.production`, instalar ferramenta, executar prune, registrar
segredo, criar tag/release, executar deploy/rollback/SSH/VPS/produção, apagar
run/log/artifact ou tocar evidência histórica.

Qualquer necessidade fora desta autorização exige parada e devolução ao
orquestrador.
