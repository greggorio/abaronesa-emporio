# S30b — Publicação da primeira release global pelo publisher

> **Estado:** `PLANNED — etapa A somente leitura`
> **Tipo:** comprovação operacional do caminho UI → publisher → release global
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Dependência:** S36 e S30a `ACCEPTED`
> **Relatório contínuo:** `S30b-preflight-publicacao-release-global-publisher.report.md`

## 1. Resultado terminal da slice

Comprovar, sem dispatch manual como atalho, o caminho já implementado:

```text
UI de desenvolvimento
  -> runtime publisher
  -> publish-release.yml
  -> release global imutável
  -> outcome reconciliado
```

O resultado terminal deve vincular uma única intenção humana de publicação a:

- candidato mais recente e válido;
- usuário ERP com `ROLE_SYSTEM`;
- `operationId` persistido pelo runtime;
- run e attempt únicos do `publish-release.yml`;
- tag SemVer e GitHub Release correspondentes;
- bundle global com os seis componentes e os mesmos digests do candidato;
- outcome terminal `PUBLISHED` reconciliado pelo runtime;
- replay com a mesma chave idempotente sem segunda operação ou release;
- restart controlado do publisher com recuperação sem redispatch;
- ausência de workflow de deploy, rollback ou efeito em produção.

Esta task é deliberadamente faseada. A etapa A abaixo está pronta para
delegação e não realiza mutação externa. A etapa B somente poderá ser aberta
por um documento `authorization-01` do orquestrador, depois de o usuário
fornecer os três metadados humanos e autorizar expressamente publicação, tag,
release, replay e restart local controlado.

## 2. Autoridade vigente: executar somente a etapa A

O executor deve executar as seções 3 a 9 e parar. Nesta delegação, não existe
autoridade para:

- iniciar ERP, PostgreSQL, publisher ou frontend;
- executar migration ou alterar banco local;
- abrir, ler ou copiar chave privada, token, senha ou arquivo de segredos;
- trocar token ERP, autenticar usuário ou chamar endpoint protegido do
  publisher;
- usar a UI para confirmar publicação;
- fazer `POST`, workflow dispatch, retry, replay ou redispatch;
- criar ou apagar tag, GitHub Release, asset, artifact, run ou log;
- reiniciar processo;
- executar Git stage, commit, push, pull, merge, rebase ou amend;
- acessar GHCR, Docker, SSH, VPS, deploy, rollback ou produção;
- editar implementação, contrato, tracker, handoff ou relatório de outra slice.

Chamadas GitHub estritamente `GET`, download dos três artifacts finais do
candidato e execução de validadores locais são permitidos. Se uma ferramenta
não deixar inequívoco que a operação é somente leitura, não a use.

## 3. Base obrigatória e integridade documental

A base remota aceita é:

```text
branch              main
origin/main         50f423a979d7723d0e15d56b1d72625ea2b8ebea
remoto main         50f423a979d7723d0e15d56b1d72625ea2b8ebea
mensagem remota     fix: close candidate integration security gates
tags remotas        zero
releases GitHub     zero
runs publish-release zero
```

O prompt de delegação informará o SHA do checkpoint documental local que
contém esta task. Exigir:

- `HEAD` nesse SHA e ancestralidade linear sobre `origin/main`;
- exatamente um commit local de documentação à frente do remoto;
- stage e worktree vazios;
- nenhum run `queued` ou `in_progress` em `main`;
- nenhuma tag, GitHub Release ou execução do `publish-release.yml` criada após
  o snapshot;
- hash desta task igual ao informado no prompt.

Executar antes de qualquer outra prova:

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD origin/main
git log --oneline --decorate -5
git merge-base --is-ancestor origin/main HEAD
git diff --check origin/main..HEAD
git diff --name-status origin/main..HEAD
git ls-remote origin refs/heads/main
git ls-remote --tags origin
gh auth status
gh run list --branch main --limit 30
gh run list --workflow publish-release.yml --branch main --limit 20
gh release list --limit 20
sha256sum docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.task.md
```

Não imprimir token ou configuração de autenticação. Divergência de Git,
autoridade, release, tag ou operação concorrente encerra a etapa A sem tentar
reparar.

## 4. Revalidação local dos contratos publisher

Executar uma única vez, em ordem:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_identity_bridge.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_ui.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  tools/releases/tests/test_release_control_contract.py \
  tools/releases/tests/test_global_release.py \
  tools/releases/tests/test_publisher_runtime_contract.py \
  tools/releases/tests/test_publisher_identity_bridge_contract.py \
  tools/releases/tests/test_publisher_ui_contract.py -v
git diff --check
git status --short
```

Registrar comando literal, exit, resumo e contagem de testes. Qualquer falha
encerra a etapa na primeira causa, sem editar código, instalar dependência ou
substituir o gate por outro.

Os validadores devem confirmar, no mínimo:

- workflow fixo `publish-release.yml`, inputs e permissões esperados;
- dispatch somente pelo runtime para `greggorio/abaronesa-emporio@main`;
- token publisher RS256, audience e scopes exatos;
- UI apenas em development, usuário root e autoridade real `ROLE_SYSTEM`;
- POST sem retry automático em resultado incerto;
- idempotência persistida e reconciliação sem redispatch;
- BOM global com os seis componentes e referências por digest;
- bundle exato `release.json`, `release.json.sha256`, `metadata.json`;
- estados monotônicos e validação cruzada de run, artifact, tag e release.

## 5. Candidato autorizado para o preflight

Revalidar somente o candidato terminal da S36:

```text
source commit
50f423a979d7723d0e15d56b1d72625ea2b8ebea

CI
30757174785 / attempt 1 / success / 13 de 13 jobs

Publish Candidate
30757430990 / attempt 1 / success / 11 de 11 jobs

candidateId
candidate-50f423a979d7723d0e15d56b1d72625ea2b8ebea-30757430990-1

candidate-effective-plan
artifact 8836368371
digest sha256:f409bdde9726cbabe48e57ba71726f05e8b5ba37ac28ab7419af24462b82dd84

candidate-manifest
artifact 8836442429
digest sha256:6b8e0bd3eafedefd8dfe55828c54c270b53f28cc995265345f5262b70e182bfd

candidate-outcome
artifact 8836442612
digest sha256:88864e8f49930d8fe68c267dd5ad41c1b94e0d0bc6c032c170fb65d522830d3d
```

Usar a API GitHub somente para confirmar unicidade, `headSha`, evento,
attempt, conclusão, jobs e metadados dos artifacts. Baixar somente os três
artifacts nominais para um diretório criado com `mktemp -d`. Sem imprimir os
JSONs brutos, validar:

- sidecars e schemas;
- vínculos com commit, CI, run e artifact metadata;
- predecessor do candidato anterior;
- plano efetivo em modo `continue`;
- seis componentes canônicos e nenhum herdado;
- checks e integração `passed`;
- referências `imageRepository@sha256:digest`;
- ausência de provenance/attestation;
- outcome `published`.

Remover apenas o diretório temporário nominal depois de registrar os resultados
sanitizados. Ausência, duplicidade ou divergência deixa a etapa `BLOCKED`; não
selecionar outro candidato por conta própria.

## 6. Inventário remoto pré-release

Com chamadas somente leitura, provar e registrar:

- zero GitHub Releases e zero refs `refs/tags/*` antes da primeira publicação;
- zero runs históricos ou ativos de `publish-release.yml`;
- zero runs de `deploy-production.yml` e `rollback-production.yml`;
- variável do repositório `RELEASE_PUBLISHER_ACTOR_IDS`: registrar somente
  `PRESENT` ou `MISSING`, nunca o valor;
- workflow em `main` idêntico ao commit do candidato e invocável apenas por
  `workflow_dispatch` com os cinco inputs fechados;
- nenhuma operação concorrente observável no GitHub.

Não tente usar endpoint que exija JWT da GitHub App, criar installation token
ou ler chave privada. Permissões operacionais da App não comprováveis por
metadado público devem ficar `PENDING_SECURE_RUNTIME_CHECK`, não `READY` por
inferência.

## 7. Matriz de prontidão segura

Produzir no relatório uma tabela com as colunas `Item`, `Estado`, `Evidência`
e `Próxima autoridade`. Usar somente estes estados:

```text
READY
PENDING_HUMAN_INPUT
PENDING_SECURE_RUNTIME_CHECK
BLOCKED
NOT_APPLICABLE
```

Cobrir exatamente:

1. Git e checkpoint documental;
2. contratos locais publisher/UI/workflow;
3. candidato terminal da S36;
4. ausência de release/tag/operação concorrente;
5. GitHub App publisher separada da deployer;
6. permissões mínimas da identidade publisher;
7. PostgreSQL local e migration atual;
8. backend ERP com a ponte publisher habilitável;
9. usuário root com `ROLE_SYSTEM`;
10. publisher development em loopback e readiness;
11. frontend development com modo publisher;
12. bump SemVer;
13. descrição de 1 a 500 caracteres;
14. changelog de 1 a 10000 caracteres;
15. autorização de publicação/tag/release;
16. autorização do replay idempotente;
17. autorização do restart local controlado;
18. isolamento de deploy, rollback, VPS e produção.

Para itens de configuração local, é permitido constatar somente nomes públicos,
paths documentados e presença/ausência de processo em porta loopback. Não abrir
`ops/env/.env.production`, `.env` real, chave PEM, banco, token ou storage do
browser. Ausência de prova segura resulta em pendência, não em falha inventada.

O executor não escolhe `PATCH`, `MINOR` ou `MAJOR`, não redige descrição ou
changelog e não presume a identidade do usuário autorizado.

## 8. Contrato já fechado para a futura authorization-01

Esta seção define o que o orquestrador deverá autorizar depois do preflight;
ela não concede autoridade nesta execução.

A autorização deverá fixar literalmente:

```text
candidateId
versionBump
description
changelog
usuário ERP autorizado
janela da prova
```

E deverá permitir, de forma enumerada:

1. preparar ambiente local publisher isolado e aplicar somente sua migration;
2. iniciar ERP, publisher e frontend em loopback;
3. confirmar readiness e listas pela UI autenticada;
4. criar exatamente uma tentativa na UI com os metadados fornecidos;
5. observar, sem intervenção, o único run correlacionado;
6. validar release, tag, bundle, BOM, digests e outcome reconciliado;
7. repetir a mesma tentativa/chave para provar idempotência sem segunda release;
8. reiniciar somente o publisher local e provar recuperação sem redispatch;
9. encerrar processos locais e limpar apenas resíduos criados pela prova.

Falha ou incerteza depois do POST não autoriza retry com nova chave, dispatch
manual, exclusão compensatória, nova release ou correção improvisada. A prova
deverá parar fail-closed e preservar evidência remota.

## 9. Relatório da etapa A e parada obrigatória

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.report.md
```

O relatório deve conter:

- CWD, data/hora e snapshot Git inicial/final;
- hash desta task;
- comandos exatos, exits, resultados e interpretação;
- contagem dos testes locais;
- identidade sanitizada dos runs e artifacts do candidato;
- inventário remoto pré-release;
- matriz de prontidão da seção 7;
- lista exata das pendências humanas e checks seguros;
- resíduos temporários e sua limpeza;
- negativos preservados;
- primeira causa e ponto de parada, se houver.

O relatório permanece local, não staged e não commitado. Ele não aceita a
S30b, não altera o tracker e não cria a `authorization-01`.

Encerrar com uma destas linhas exatas:

```text
IN_PROGRESS — preflight verde; aguardando metadados humanos, checks seguros e autorização explícita da publicação
```

ou, se um gate técnico da etapa A falhar:

```text
BLOCKED — preflight interrompido no primeiro gate técnico
```

## 10. Critérios de aceite terminal da S30b

Somente o orquestrador poderá marcar a S30b `ACCEPTED`, depois de revisar a
etapa A e a futura execução autorizada e confirmar cumulativamente:

- preflight local e remoto verde;
- metadados humanos preservados literalmente;
- uma única operação criada pela UI/runtime;
- run `publish-release.yml` único e integralmente verde;
- GitHub Release e tag únicas, imutáveis e vinculadas;
- três assets íntegros e BOM de seis digests idêntico ao candidato;
- outcome terminal `PUBLISHED` no runtime;
- replay idempotente sem segunda operação, run, tag ou release;
- restart local recuperado sem redispatch;
- nenhum deploy, rollback, acesso à VPS ou efeito de produção;
- relatório completo e sem segredo.
