# S35 — Migrações prévias da stack candidata

> **Estado:** `PLANNED`
> **Tipo:** correção causal do primeiro startup integrado
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Relatório:** `S35-migracoes-previas-stack-candidata.report.md`
> **Commit técnico:** `fix: migrate candidate databases before integrated startup`

## 1. Resultado esperado

O Publish Candidate `30746220083` comprovou build, scan, push e assemble dos
seis componentes, mas o banco efêmero nasceu vazio e o harness executou o
`compose up` sem o passo de migrations. O runtime do `backend` usa
`ddl-auto=validate` e Flyway desligado, por isso não deve criar o schema durante
o startup.

Antes de subir a stack, o harness deve aplicar, em ordem determinística, as
migrations dos dois bancos que o deploy de produção trata no passo `MIGRATE`:

```text
erp      -> backend
website  -> website_back
```

Depois das duas migrations, o fluxo existente continua com `up`, health,
probes, recibo e cleanup. Ao final deve existir um único commit técnico local,
sem push. A S30a não avança nesta execução.

## 2. Decisões fechadas

### 2.1 Ordem e comandos

Preservar o `pull` único e executar, usando o mesmo projeto, os mesmos dois
arquivos Compose e o ambiente já sanitizado:

```text
docker compose ... run --rm -T --entrypoint /app/bin/migrate backend migrate
docker compose ... run --rm -T --entrypoint /app/bin/migrate website_back migrate
```

A ordem obrigatória é:

```text
pull -> migrate backend -> migrate website_back -> up
```

Não usar `--no-deps`: no ambiente efêmero, o primeiro `compose run` deve iniciar
o PostgreSQL e honrar `depends_on.postgresql.condition=service_healthy`. O
segundo reutiliza o mesmo banco e volume já saudáveis.

Qualquer migration com exit não zero aborta antes do `up`. O bloco `finally`
continua executando `down -v --remove-orphans`, remoção dirigida das seis
imagens, prova de ausência, contagem de resíduos e logout.

### 2.2 Paridade com produção

Usar os entrypoints `/app/bin/migrate` já existentes nas próprias imagens
candidatas. Eles invocam a API do Flyway diretamente e não dependem da
auto-configuração Spring, portanto os flags de runtime permanecem desligados.

Não criar serviços `backend_migrate` ou `website_back_migrate` no Compose: o
contrato de integração continua exigindo exatamente os sete serviços
canônicos. Não alterar `DDL_AUTO`, `SPRING_FLYWAY_ENABLED` ou `FLYWAY_ENABLED`.

O `website_back` atualmente consegue iniciar em banco vazio porque
`DDL_AUTO` assume `update`, mesmo com `FLYWAY_ENABLED=false`. Isso não dispensa
sua migration no ensaio: produção também trata o banco `website`, e a stack
candidata deve detectar migrations ausentes nos dois componentes.

### 2.3 Contrato e recibo

Fixar no validador do workflow a presença das duas migrations antes do `up`.
Os testes devem provar o argv exato, a ordem e a parada causal para falha tanto
no `backend` quanto no `website_back`.

As migrations são precondições do startup, não probes. Não alterar
`PROBES`, schemas, exemplos, hashes derivados, forma do recibo ou catálogo de
serviços.

## 3. Fronteira autorizada

Preservar e concluir somente o trabalho já presente nestes quatro caminhos:

```text
tools/candidates/integrated_harness.py
tools/candidates/validate_candidate_workflow.py
tools/candidates/tests/test_definitive_contract.py
docs/infrastructure/deployment/release-control/CANDIDATOS.md
```

E criar:

```text
docs/infrastructure/deployment/implementation/slices/S35-migracoes-previas-stack-candidata.report.md
```

Qualquer consumidor necessário fora dessa lista exige parada antes de editar.

## 4. Preflight e preservação do trabalho

Antes de agir, confirmar:

```text
CWD = /home/gregorio/git/baronesa/emporio
branch = main
mensagem de HEAD = docs: record integrated migration failure and open S35 scope
origin/main = cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af
commits em origin/main..HEAD = 1
stage = vazio
```

O worktree deve conter exatamente os quatro caminhos modificados da §3, sem
outro arquivo não rastreado. Não descartar nem reimplementar o patch: preservá-lo
e refiná-lo para executar também a migration de `website_back`.

Registrar o SHA-base com `git rev-parse HEAD`. Divergência exige parada antes
de editar.

## 5. Testes causais

Atualizar os dois testes já acrescentados:

1. sucesso: provar exatamente uma migration de `backend` e uma de
   `website_back`, nessa ordem, ambas depois do `pull` e antes do `up`;
2. falha: exercitar separadamente falha na primeira e na segunda migration;
   em ambos os casos não pode existir `up` ou recibo, mas todo o cleanup
   dirigido deve ser tentado.

O teste deve confrontar o validador para impedir a remoção silenciosa de
qualquer um dos dois comandos. Não reduzir cobertura ou relaxar erros
fail-closed existentes.

## 6. Verificação obrigatória

Executar na forma final:

```text
python3 tools/candidates/validate_candidate_workflow.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v
git diff --check
```

Resultados obrigatórios:

- `candidate-workflow:valid`;
- 72 testes de candidates, zero falha;
- os dois comandos e sua ordem provados pelos testes;
- `git diff --check` exit 0.

As outras sete suítes e os demais validadores já passaram sobre o patch
preservado e não são afetados pelo refinamento. Consolidar no relatório a
matriz herdada de 905 testes, distinguindo a suíte candidates repetida após a
forma final.

## 7. Linhagem, stage e commit

Calcular `sourceDiffSha256` e `sourceTreeSha` sobre os quatro caminhos técnicos
e de documentação ativa da §3, tomando o checkpoint da §4 como `sourceSha`.
O relatório fica fora da linhagem.

Criar o relatório com CWD, SHA-base, diff preservado, decisões aplicadas,
comandos, exits, resultados, linhagem e estado Git. Preparar stage somente com
os quatro caminhos da §3 e o relatório.

Executar:

```text
git diff --cached --check
python3 tools/ci/secret_scan.py --tracked
```

Exigir exit 0 e `unsupported=0`. Depois criar exatamente um commit:

```text
fix: migrate candidate databases before integrated startup
```

Não usar amend, `--no-verify`, segundo commit, push ou tag. Confirmar que o
commit tocou exatamente os cinco caminhos autorizados, o stage e o worktree
estão vazios e `git diff --check origin/main..HEAD` retorna exit 0.

## 8. Paradas e negativos

Esta slice não autoriza:

- alterar Compose, workflow, Dockerfiles, entrypoints ou código Java;
- mudar flags Flyway/Hibernate ou criar serviço de migration;
- alterar probes, recibos, schemas, exemplos, hashes ou catálogo;
- executar Docker, GHCR, Maven, npm, Trivy, workflow remoto ou deploy;
- push, rerun, dispatch, release, rollback, SSH, VPS ou produção;
- aceitar a S35/S30a ou abrir nova autorização remota.

Falha local exige parada sem commit. O relatório termina exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```
