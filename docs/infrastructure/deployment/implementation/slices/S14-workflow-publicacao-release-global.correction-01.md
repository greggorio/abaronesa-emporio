# S14 — Correção causal 01

> **Estado:** `IN_PROGRESS — correção causal 01 requerida`
>
> **Contrato-base imutável:** `S14-workflow-publicacao-release-global.task.md`
>
> **Relatório a atualizar:** `S14-workflow-publicacao-release-global.report.md`
>
> **Próxima slice:** S15 continua bloqueada.

## 1. Natureza desta correção

Esta correção fecha cinco lacunas encontradas na revisão integral da S14.

Quatro são divergências da implementação em relação a requisitos já expressos:

1. o shape usado para workflow runs não é o shape real da REST API;
2. IDs remotos ainda não validados controlam endpoints;
3. bytes não canônicos de metadata/outcome do candidato são aceitos;
4. assets e estado final da release não são revalidados integralmente.

O quinto item é uma omissão do orquestrador no contrato-base: `notes.md` foi
definido, mas seu uso como `body` da GitHub Release não foi expresso na
sequência mutável. Esse item é uma emenda prospectiva, não um erro retroativo
do executor.

Não há escolha de arquitetura nesta correção. Implementar exatamente os
comportamentos abaixo.

## 2. Escopo autorizado

Alterar somente:

- `.github/workflows/publish-release.yml`, se necessário para transportar
  `notes.md` sem interpolação em shell;
- `tools/releases/release_publication.py`;
- `tools/releases/validate_release_workflow.py`, somente se o workflow mudar;
- `tools/releases/tests/test_release_publication.py`;
- `tools/releases/tests/test_release_workflow.py`, somente se o workflow ou o
  validador mudar;
- `docs/infrastructure/deployment/RELEASE_PUBLICATION.md`;
- `docs/infrastructure/deployment/implementation/slices/S14-workflow-publicacao-release-global.report.md`.

Não alterar:

- contrato-base `.task.md`;
- tracker/README de implementação;
- schemas e exemplos S13/S14, salvo se um teste demonstrar incompatibilidade
  factual e o executor parar antes da alteração;
- `catalog.py`, contratos comerciais, Dockerfiles, Compose ou migrations;
- CI e publicação de candidato;
- Git, GitHub, GHCR, VPS ou produção;
- S15.

## 3. Shape canônico de workflow run

Eliminar qualquer leitura de `run.workflow.name`. A resposta de
`GET /repos/{owner}/{repo}/actions/runs/{run_id}` deve usar os campos reais de
topo.

### 3.1 Run atual

Antes de produzir confiança, exigir:

```text
id=<GITHUB_RUN_ID como inteiro positivo>
run_attempt=<GITHUB_RUN_ATTEMPT como inteiro positivo>
name=Publish Release
workflow_id=<inteiro positivo>
path=.github/workflows/publish-release.yml@main
event=workflow_dispatch
status=in_progress
head_branch=main
head_sha=<GITHUB_SHA>
repository.full_name=greggorio/abaronesa-emporio
repository.owner.login=greggorio
head_repository.full_name=greggorio/abaronesa-emporio
actor.login=<GITHUB_ACTOR>
actor.id=<GITHUB_ACTOR_ID como inteiro positivo>
triggering_actor.login=<string não vazia>
triggering_actor.id=<inteiro positivo>
```

`repository.id`, `head_repository.id`, `actor.id` e `triggering_actor.id`
devem ser inteiros positivos. `repository.id == head_repository.id`. O
publisher autorizado continua sendo `actor`; `triggering_actor` é validado,
mas não substitui a allowlist nem precisa ser igual ao ator original em rerun.

Não exigir `conclusion` enquanto o próprio run está em andamento.

### 3.2 Run do candidato

Antes de listar artifacts, exigir:

```text
id=<run ID extraído do candidate ID como inteiro positivo>
run_attempt=<attempt extraído como inteiro positivo>
name=Publish Candidate
workflow_id=<inteiro positivo>
path=.github/workflows/publish-candidate.yml@main
event=workflow_run
status=completed
conclusion=success
head_branch=main
head_sha=<SHA extraído do candidate ID>
repository.full_name=greggorio/abaronesa-emporio
repository.owner.login=greggorio
head_repository.full_name=greggorio/abaronesa-emporio
```

`repository.id` e `head_repository.id` devem ser inteiros positivos e iguais.
`actor` e `triggering_actor` devem possuir `login` não vazio e `id` inteiro
positivo, sem exigir que sejam o publisher atual.

Fixtures positivas devem reproduzir esse shape de topo. Uma fixture que
contenha apenas `workflow: {"name": ...}` deve falhar.

## 4. Validação antes de dados remotos controlarem endpoints

Criar validadores reutilizáveis e chamar cada um antes do primeiro endpoint
formado com um ID retornado remotamente.

### 4.1 Artifact de Actions

Antes de baixar um artifact, validar:

```text
id=<inteiro positivo>
name=<nome esperado exato>
expired=false
size_in_bytes=<inteiro positivo e <= 2 MiB>
digest=sha256:<64 hex minúsculos>
url=https://api.github.com/repos/greggorio/abaronesa-emporio/actions/artifacts/<id>
archive_download_url=https://api.github.com/repos/greggorio/abaronesa-emporio/actions/artifacts/<id>/zip
workflow_run.id=<candidate run ID como inteiro>
workflow_run.head_sha=<candidate SHA>
```

Se `workflow_run.repository_id` ou `workflow_run.head_repository_id` estiver
presente, deve ser inteiro positivo e igual ao ID do repositório validado no
candidate run.

Somente depois dessa validação é permitido chamar:

```text
/repos/greggorio/abaronesa-emporio/actions/artifacts/<id>/zip
```

Artifact inválido deve falhar sem qualquer chamada de download.

### 4.2 Release e asset

Antes de usar `release.id` em endpoint, exigir inteiro positivo e:

```text
url=https://api.github.com/repos/greggorio/abaronesa-emporio/releases/<id>
```

Antes de baixar um asset, validar:

```text
id=<inteiro positivo>
name=<um dos três nomes canônicos, sem duplicidade>
url=https://api.github.com/repos/greggorio/abaronesa-emporio/releases/assets/<id>
state=uploaded
content_type=<content type exato da tabela S14>
size=<inteiro positivo e <= limite do asset>
```

O conjunto deve conter exatamente três assets. Validar todo o conjunto antes
do primeiro download; não baixar o primeiro asset para descobrir que o segundo
é inválido.

Somente depois dessa validação é permitido chamar:

```text
/repos/greggorio/abaronesa-emporio/releases/assets/<id>
```

Aplicar esse contrato igualmente a:

- leitura da história;
- verificação do draft depois dos uploads;
- reconciliação final;
- construção do outcome;
- modo `already_published`.

## 5. Canonicalidade dos artifacts do candidato

Depois de extração segura e antes de usar os valores:

- bytes de `metadata.json` devem ser exatamente
  `canonical(expected_metadata)`;
- bytes de `outcome.json` devem ser exatamente `canonical(parsed_outcome)`;
- sidecars continuam obrigatórios;
- JSON inválido, UTF-8 inválido ou bytes não canônicos devem produzir código
  estável e sanitizado;
- nenhuma dessas falhas pode iniciar mutação remota.

Não basta comparar o objeto resultante de `json.loads`.

## 6. Corpo canônico da GitHub Release

Esta seção corrige a omissão do orquestrador.

No modo `publish`, ler `release/notes.md` já validado no handoff e passá-lo
como dado ao transporte. A assinatura lógica passa a ser:

```text
create_draft(tag, source_commit, notes_bytes)
final_state(release_id, tag, source_commit, notes_bytes)
```

Regras:

- `notes_bytes` deve ser UTF-8 estrito;
- tamanho deve ser de 1 a 16 KiB;
- o texto decodificado é enviado integralmente no campo JSON `body`;
- nenhum trim, normalização de LF ou reconstrução é permitido;
- o payload de criação contém exatamente `tag_name`, `target_commitish`,
  `name`, `body`, `draft` e `prerelease`;
- `body` nunca é interpolado em `run:` nem passado em argv;
- a resposta do POST deve fornecer `id` inteiro positivo; imediatamente depois
  deve ocorrer `GET /repos/greggorio/abaronesa-emporio/releases/<id>`;
- essa reconsulta, e não somente a resposta do POST, deve confirmar URL, tag,
  name, target commit, body, `draft=true` e `prerelease=false` antes do
  primeiro upload;
- reconciliação final deve confirmar o mesmo body e target commit, agora com
  `draft=false`;
- divergência de body após a criação pertence ao draft desta tentativa e exige
  compensação segura.

O `notes.md` não vira um quarto asset: os assets permanecem exatamente os três
definidos no contrato-base.

## 7. Sequência mutável corrigida

Executar nesta ordem:

1. validar handoff, incluindo `notes.md`;
2. revalidar confiança, candidato, história e snapshot;
3. provar ausência de tag e release alvo;
4. criar draft com body;
5. extrair ID positivo da resposta e validar integralmente o draft por GET;
6. subir os três assets;
7. reconsultar release e validar metadados de todos os assets;
8. baixar os três assets e comparar bytes;
9. revalidar snapshot ignorando somente o draft comprovadamente pertencente à
   tentativa;
10. criar a tag no SHA exato;
11. publicar o draft;
12. reconsultar release, assets e tag;
13. validar ID/URL/tag/name/target/body/state, metadados e bytes dos assets;
14. somente então produzir publication handoff/outcome.

Se a resposta à criação do draft não fornecer ID inteiro positivo, falhar sem
tentar endpoint derivado. Se fornecer ID, mas a reconsulta não passar, é
permitido usar o ID apenas para compensação quando uma segunda reconsulta
independente provar URL, tag, name, target, body, `draft=true` e
`prerelease=false`. Sem essa prova, não deletar recurso.

## 8. Provas causais adicionais obrigatórias

Adicionar testes independentes para:

1. current run no shape REST real de topo é aceito;
2. candidate run no shape REST real de topo é aceito;
3. shape antigo com apenas `workflow.name` é rejeitado;
4. current run com `name`, `workflow_id`, `path`, repository,
   head_repository, actor ou triggering_actor divergente é rejeitado;
5. candidate run com esses bindings divergentes é rejeitado antes de listar
   artifacts;
6. artifact com ID zero/string, URL divergente, size inválido ou
   `workflow_run` divergente falha com zero downloads;
7. se o segundo artifact for inválido, nenhum dos dois é baixado;
8. `metadata.json` semanticamente igual, porém não canônico, falha;
9. `outcome.json` semanticamente igual, porém não canônico, falha;
10. release/asset com ID zero/string ou URL divergente falha antes de endpoint
    derivado;
11. segundo asset com state/content type/size inválido impede todos os
    downloads;
12. draft é criado com `body` byte-a-byte equivalente ao `notes.md`;
13. body omitido ou alterado falha antes de upload;
14. resposta/reconsulta de draft com ID/URL/tag/name/target/state divergente
    falha antes de upload e não apaga recurso sem prova de ownership;
15. metadado de asset divergente após upload causa compensação;
16. body, target ou metadado de asset divergente na reconciliação final causa
    compensação e impede outcome;
17. caminho positivo preserva exatamente três assets e produz outcome somente
    após todas as provas;
18. os 187 testes previamente reportados continuam verdes.

Mocks devem registrar endpoints e mutações, permitindo afirmar contagem zero
nos testes “antes de download/upload”.

## 9. Matriz de validação

Executar e registrar comando exato, exit e contagem:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py validate \
  --manifest ops/releases/examples/global-release.example.json

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py validate \
  --require-release-ready

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
```

Executar actionlint nos três workflows pelo mesmo digest fixado e remover
somente a imagem efêmera caso ela não existisse antes.

Não executar Maven, NPM, Docker build, Compose, GitHub, GHCR ou VPS.

## 10. Evidência e estado final

Acrescentar ao relatório S14:

- resposta item a item às 18 provas;
- shapes positivos usados;
- tabela de endpoints bloqueados antes/depois da validação;
- payload do draft com valores livres redigidos, mas confirmação de body
  exato por digest/tamanho;
- comandos, exits, contagens e interpretação;
- arquivos alterados;
- estado Git, workflows, caches e imagem actionlint;
- confirmação de nenhuma mutação/acesso remoto real.

Estado final obrigatório:

```text
IN_PROGRESS — aguardando nova revisão do orquestrador
```

Não declarar `ACCEPTED` e não criar S15.
