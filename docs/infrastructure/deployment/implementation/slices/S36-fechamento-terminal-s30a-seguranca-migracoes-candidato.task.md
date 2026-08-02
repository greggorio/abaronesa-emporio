# S36 — Fechamento terminal da S30a: segurança dos logs e migrations do candidato

> **Estado:** `PLANNED`
> **Tipo:** correção técnica e comprovação remota terminal da S30a
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Relatório:** `S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.report.md`
> **Commit técnico:** `fix: close candidate integration security gates`

## 1. Resultado observável

Fechar em um único ciclo os dois itens que ainda impedem o aceite formal da
S30a:

1. as sete credenciais efêmeras geradas para a stack candidata são registradas
   no protocolo `::add-mask::` antes de serem gravadas em `GITHUB_ENV`, sem
   reproduzir seus valores em outra mensagem de stdout ou stderr;
2. o harness aplica, nesta ordem, as migrations das imagens candidatas
   `backend` e `website_back` antes de iniciar os sete serviços.

Depois da forma final local, criar um único commit, fazer um único push normal
e observar a CI e o Publish Candidate produzidos para o SHA exato. O ciclo só
produz evidência terminal quando ambos os workflows ficam verdes, os logs novos
classificam os sete valores como `***` e os artefatos finais formam uma única
identidade válida.

Esta slice não aceita a S30a. O executor entrega implementação e evidência; o
aceite, a reconciliação do tracker/relatório S30a e a criação da S30b pertencem
ao orquestrador após a revisão.

## 2. Base, dependências e preflight

A base observada pelo orquestrador em 02/08/2026 é:

```text
CWD                 /home/gregorio/git/baronesa/emporio
branch              main
HEAD                fcaf9d85de88a4036956619ac4fa7819899fa473
mensagem de HEAD    docs: hand off implementation closure
origin/main         68a3528b563ad0c19819da34ab106396ae679596
remoto main         68a3528b563ad0c19819da34ab106396ae679596
ahead               1 commit
stage               vazio
tags remotas        zero
releases GitHub     zero
runs ativos         zero
```

O único desvio esperado no worktree é este contrato novo e não rastreado:

```text
docs/infrastructure/deployment/implementation/slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.task.md
```

Antes de editar:

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD origin/main
git log --oneline --decorate -12
git diff --check
git ls-remote origin refs/heads/main
gh run list --branch main --limit 20
gh release list --limit 20
git ls-remote --tags origin
```

Exigir a base acima, `origin/main` ancestral de `HEAD`, nenhum run `queued` ou
`in_progress` de `CI` ou `Publish Candidate` e nenhuma alteração além do
contrato. Revalidar autenticação Git/GitHub sem imprimir token, header ou
credencial. Divergência exige parada antes de editar ou fazer mutação externa.

Não fazer pull, rebase, merge, amend, reset, squash ou reescrita dos sete commits
exploratórios publicados. O commit local do handoff deve ser preservado e será
enviado junto com o commit técnico no único push desta slice.

## 3. Decisões fechadas

### 3.1 Conjunto exato de valores sensíveis

Definir no código, por nome exato e sem heurística de substring, este conjunto:

```text
CANDIDATE_ROOT_PASSWORD
POSTGRES_ADMIN_PASSWORD
ERP_DB_PASSWORD
WEBSITE_DB_PASSWORD
INTEGRATION_SYSTEM_TOKEN_SECRET
ERP_WEBSITE_SYNC_KEY
GOOGLE_CLIENT_SECRET
```

Para cada chave, emitir no stream de comandos do runner:

```text
::add-mask::<valor>
```

As sete emissões devem ocorrer antes de abrir/gravar `GITHUB_ENV`. Depois disso,
gravar todas as variáveis atuais no arquivo, com LF real, sem mudar nomes,
aliases, valores públicos ou formato `KEY=VALUE`.

Preservar `candidate-compose-env:written`, mas nenhuma mensagem diferente do
comando `add-mask` pode conter um dos sete valores. Não mascarar por aproximação
`CANDIDATE_ROOT_EMAIL`, `POSTGRES_ADMIN_USER`, nomes de banco, usuários, IDs de
imagem, portas ou qualquer outra chave pública.

Os testes usarão sentinelas descartáveis e poderão confrontar os valores em
memória. O relatório, comandos exibidos e evidência remota nunca registram os
valores, nem mesmo as sentinelas completas usadas no teste.

### 3.2 Ordem das migrations

Preservar o `pull` único e usar o mesmo projeto, os mesmos dois arquivos Compose
e o mesmo ambiente sanitizado nos quatro passos:

```text
pull
docker compose ... run --rm -T --entrypoint /app/bin/migrate backend migrate
docker compose ... run --rm -T --entrypoint /app/bin/migrate website_back migrate
up
```

A ordem obrigatória é:

```text
pull -> migrate backend -> migrate website_back -> up
```

Não usar `--no-deps`. Não criar serviços auxiliares de migration. Usar os
entrypoints existentes nas próprias imagens candidatas e manter desligados os
flags Flyway/Hibernate do runtime.

Falha no `backend` impede a migration de `website_back` e o `up`. Falha no
`website_back` ocorre depois do sucesso do `backend` e também impede o `up`.
Nos dois casos não há recibo, e o `finally` tenta integralmente diagnóstico e
cleanup dirigido: `down -v --remove-orphans`, remoção/ausência das seis imagens,
contagem de resíduos do projeto e logout.

### 3.3 Contrato do candidato preservado

Preservar sem alteração:

- exatamente os sete serviços canônicos;
- somente o gateway publicado em loopback efêmero;
- catálogo, plano efetivo, schemas, manifesto, outcome e recibo;
- lista e semântica dos probes;
- checks `build`, `test`, `scan` e integração;
- referências `imageRepository@digest` e proibição de `latest`;
- permissões, actions pinadas, autenticação somente leitura no job integrado e
  retenções de artifacts;
- cleanup cumulativo e comportamento fail-closed.

O validador deve fixar semanticamente os dois comandos de migration e sua ordem
antes do `up`; não basta uma busca genérica por `/app/bin/migrate`.

## 4. Fronteira autorizada

O executor pode alterar somente:

```text
tools/candidates/compose_env.py
tools/candidates/integrated_harness.py
tools/candidates/validate_candidate_workflow.py
tools/candidates/tests/test_causal_corrections.py
tools/candidates/tests/test_definitive_contract.py
docs/infrastructure/deployment/release-control/CANDIDATOS.md
```

O executor deve preservar sem editar, mas incluir no commit:

```text
docs/infrastructure/deployment/implementation/slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.task.md
```

Depois da execução, criar somente como evidência local, fora do commit e sem
segundo push:

```text
docs/infrastructure/deployment/implementation/slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.report.md
```

Se uma correção causal exigir outro caminho, parar antes de editá-lo e registrar
o caminho e a razão no relatório. Não ampliar a fronteira por conveniência.

## 5. Testes causais obrigatórios

### 5.1 Máscaras

Provar no mínimo:

1. o conjunto sensível contém exatamente as sete chaves da §3.1;
2. cada uma produz exatamente um comando `::add-mask::` antes da primeira
   gravação em `GITHUB_ENV`;
3. stdout fora desses sete comandos não reproduz valor sensível;
4. todas as variáveis, sensíveis e públicas, continuam gravadas uma vez e com LF
   real em `GITHUB_ENV`;
5. chaves públicas representativas não são mascaradas;
6. trocar uma chave sensível por uma pública ou omitir uma das sete faz o teste
   falhar.

Não registrar os valores capturados na saída do unittest ou no relatório.

### 5.2 Migrations

Provar no mínimo:

1. sucesso: exatamente uma migration de `backend` e uma de `website_back`, na
   ordem prescrita, ambas depois do `pull` e antes do `up`;
2. falha do `backend`: a segunda migration e o `up` não ocorrem, não há recibo e
   todo o cleanup dirigido é tentado;
3. falha do `website_back`: a primeira migration ocorre, a segunda falha, o
   `up` não ocorre, não há recibo e todo o cleanup dirigido é tentado;
4. o validador rejeita ausência, duplicação, inversão ou troca de qualquer um
   dos dois comandos e rejeita migration posicionada depois do `up`.

Não reduzir cobertura nem relaxar erros fail-closed existentes.

## 6. Matriz local antes do commit

Executar os scripts validadores explicitamente enumerados abaixo. A lista tem
17 comandos no estado atual; ela prevalece sobre a contagem resumida de 16 no
handoff e evita omitir um gate canônico.

```bash
python3 tools/docker/validate_node_images.py validate
python3 tools/docker/java_images_contract.py validate
python3 tools/ci/validate_ci.py
python3 tools/ci/invocability.py
python3 tools/ci/migrations_contract.py
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
python3 tools/compose/validate_compose.py
python3 tools/gateway/validate_gateway.py
```

Executar as oito suítes canônicas:

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

Executar também:

```bash
python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json
python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json
python3 tools/ci/secret_scan.py --tracked
git diff --check
```

Todos os comandos devem retornar exit 0; o scanner deve informar
`secret-scan:clean` e `unsupported=0`. Registrar comandos exatos, exits,
contagens e resultados sanitizados. Não fixar no contrato uma contagem histórica
de testes: registrar o total realmente descoberto na execução.

Esta slice não exige build Docker, Trivy local, Maven ou npm adicionais: esses
gates permanecem na CI canônica. Não instalar ferramenta nem alterar o host para
contornar falha local.

## 7. Stage, commit e única mutação remota

Somente com toda a §6 verde, preparar stage exclusivamente com o contrato e os
seis caminhos da §4 que tenham a forma final esperada. O relatório ainda não
existe neste ponto.

Executar:

```bash
git diff --cached --check
python3 tools/ci/secret_scan.py --tracked
```

Exigir exit 0 e `unsupported=0`, revisar `git diff --cached --name-status` e
criar exatamente um commit:

```text
fix: close candidate integration security gates
```

Não usar amend ou `--no-verify`. Confirmar worktree e stage vazios,
`git diff --check origin/main..HEAD` em exit 0 e a sequência histórica intacta.
Definir o novo `HEAD` como `TARGET_SHA`.

Revalidar imediatamente antes do push:

- remoto ainda em `68a3528b563ad0c19819da34ab106396ae679596`;
- `origin/main` ancestral de `TARGET_SHA`;
- exatamente dois commits em `origin/main..TARGET_SHA`: o handoff `fcaf9d8` e o
  commit técnico desta slice;
- nenhum run `CI` ou `Publish Candidate` ativo em `main`.

Então executar uma única vez:

```bash
git push origin main:main
```

O push deve ser normal, não forçado e fast-forward. Falha antes ou durante o
push encerra a autorização; não fazer retry automático, segundo push, pull,
rebase ou correção adicional.

## 8. Observação remota

Depois do push, não editar arquivo algum. Localizar o único run `CI`, evento
`push`, cujo `headSha = TARGET_SHA`; aguardar sem intervenção e exigir 13 jobs em
`success`.

Com a CI verde, localizar o único `Publish Candidate`, evento `workflow_run`,
para o mesmo SHA e vinculado à CI observada. Exigir attempt 1 e:

- `trust` e `predecessor` em `success`;
- seis jobs da matriz `build` em `success`;
- `assemble`, `integrated` e `publish` em `success`;
- plano em modo `continue`, predecessor igual ao candidato publicado do SHA
  `68a3528b563ad0c19819da34ab106396ae679596`, seis componentes em
  `buildComponents` e nenhum herdado;
- nenhuma execução de release, deploy ou rollback.

Se a CI não ficar verde, não aguardar o candidato como compensação. Se qualquer
run não aparecer em até cinco minutos, houver duplicidade, attempt diferente de
1 ou job obrigatório falhar, registrar a primeira causa sanitizada e parar. Não
fazer rerun, dispatch, cancelamento, aprovação manual ou correção pós-push.

## 9. Auditoria segura dos logs novos

Auditar somente os logs do `Publish Candidate` associado a `TARGET_SHA`. Se for
necessário baixar o log, usar diretório nominal criado com `mktemp -d`, mantê-lo
fora do repositório e removê-lo ao final.

Para cada chave da §3.1, registrar no relatório apenas:

```text
<NOME_DA_CHAVE> | MASKED
```

Exigir que suas ocorrências nos blocos de ambiente posteriores ao step
`Emit LF environment` estejam representadas por `***`. Nunca copiar, imprimir,
hashear, contar caracteres ou incluir prefixo/sufixo do valor encontrado.

Se qualquer ocorrência estiver sem máscara, registrar somente:

```text
<NOME_DA_CHAVE> | UNMASKED
```

e parar. Não transcrever o valor, não abrir logs históricos e não apagar run,
log ou artifact remoto. `secret-scan:clean` não é prova deste gate.

## 10. Validação dos artefatos

Somente após `Publish Candidate = success`, baixar para outro diretório nominal
criado por `mktemp -d`, a partir do run exato:

```text
candidate-effective-plan
candidate-manifest
candidate-outcome
```

Validar sidecars e exigir:

- `candidate_manifest.py validate` em `candidate.json` com exit 0 e
  `candidate:valid`;
- `tools/candidates/outcome.py::validate` sem erros;
- `commitSha = TARGET_SHA` nos três artefatos;
- `sourceCi.runId/attempt` vinculados à CI observada;
- workflow run/attempt do manifesto e outcome vinculados ao Publish Candidate;
- predecessor igual ao candidato válido de `68a3528...`;
- seis componentes canônicos, todos `built`, checks `passed`, integração
  `passed` e `immutableRef = imageRepository + "@" + digest`;
- ausência de campos de provenance/attestation;
- outcome `published`, com `candidateId`, artifact id e artifact digest iguais
  aos metadados retornados pela API para `candidate-manifest`.

Remover somente os diretórios temporários nominais. Não fazer login, pull ou
push manual no GHCR e não apagar imagens, artifacts, runs ou logs remotos.

## 11. Relatório do executor

Após sucesso ou parada, criar o relatório indicado no cabeçalho. Ele permanece
local, não rastreado e não commitado; não existe segundo push para publicá-lo.

O relatório deve conter:

- CWD, base inicial, SHA do contrato e estado Git observado;
- resumo causal das duas correções;
- arquivos alterados;
- comandos exatos, exits, duração e resultado da matriz local;
- nome e SHA do commit criado, `TARGET_SHA` e saída sanitizada do push;
- IDs, URLs, eventos, attempts, conclusões e jobs dos dois runs;
- tabela `MASKED`/`UNMASKED` somente com nomes das sete chaves;
- identidade e vínculos dos três artefatos, sem segredo;
- primeira causa e ponto de parada, se houver;
- resíduos locais e cleanup de diretórios temporários;
- declaração explícita dos negativos preservados.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — aguardando revisão e aceite da S30a pelo orquestrador
```

Em falha, terminar com `IN_PROGRESS —` seguido da causa objetiva e do ponto de
parada. O relatório nunca aceita S36/S30a nem abre S30b.

## 12. Critérios de aceite pelo orquestrador

A revisão só poderá aceitar esta execução quando, simultaneamente:

1. o diff está restrito à fronteira e o contrato não foi alterado pelo executor;
2. os testes provam o conjunto exato de sete máscaras e a ordem de emissão antes
   de `GITHUB_ENV`;
3. os testes provam as duas migrations, sua ordem e as duas falhas independentes;
4. os 17 validadores, oito suítes, exemplos, scanner e diff check estão verdes;
5. existe um único commit técnico com a mensagem prescrita e um único push
   fast-forward, sem reescrita da história;
6. CI e Publish Candidate do `TARGET_SHA` estão verdes em attempt 1;
7. os logs novos mostram `***` para as sete chaves, sem valor transcrito;
8. plano, manifesto e outcome são válidos, íntegros e cruzados ao mesmo SHA e
   aos dois runs;
9. nenhuma release, tag, deploy, rollback, VPS, produção ou exclusão remota
   ocorreu;
10. o relatório está completo e termina com o estado prescrito.

Saída verde parcial não equivale a aceite. Somente o orquestrador atualiza o
tracker, consolida o relatório S30a, marca S30a `ACCEPTED`, preserva S35 como
`SUPERSEDED` e cria S30b.

## 13. Limites e condições de parada

Esta slice não autoriza:

- editar o contrato, tracker, handoffs, relatório consolidado S30a, S35 ou
  qualquer documento de S30b;
- alterar workflow, Compose, Dockerfile, entrypoint, código Java/Node, schema,
  manifesto, outcome, probes ou catálogo;
- relaxar scanner, pinagem de actions, permissões, Trivy ou fail-closed;
- acessar `ops/env/.env.production` ou registrar token, senha, header, PFX,
  chave privada ou sessão WhatsApp;
- instalar ferramentas, executar prune amplo ou tocar containers/volumes
  preexistentes do host;
- rerun, dispatch, segundo commit, segundo push, tag, release, deploy, rollback,
  SSH, VPS ou produção;
- apagar ou alterar runs, logs, artifacts ou imagens remotas;
- aceitar S30a/S36 ou iniciar S30b.

Parar diante de base divergente, caminho necessário fora da fronteira, gate
local vermelho, remoto alterado, concorrência de workflow, falha de push, run
ambíguo, falha de CI/candidato, valor não mascarado, artifact ausente/inválido
ou qualquer efeito de produção. Preservar a evidência e não improvisar correção
fora deste contrato.
