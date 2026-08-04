# S42 — Pacote instalável do control root, dependências isoladas e binding do SHA

> **Data:** 03/08/2026
> **Predecessora:** S41 aceita
> **Tipo:** implementação local, um commit/push e validação remota
> **VPS/produção:** proibidas

## 1. Objetivo

Fechar o artefato que será instalado em
`/opt/sistemas/emporio/shared/control` antes de tocar novamente na VPS:

1. empacotar somente a fronteira operacional necessária ao helper/CLI;
2. incluir dependências Python pinadas, verificadas e isoladas do Python global;
3. suportar exatamente Linux `amd64` e CPython `3.10` do host inventariado;
4. produzir manifesto canônico, sidecar e archive determinístico;
5. instalar inicialmente apenas em control root vazio, por staging seguro;
6. vincular o helper remoto ao `controlSha` do workflow;
7. provar em container limpo que Draft 2020-12 é realmente aplicado;
8. versionar a implementação e validar CI/Publish Candidate.

A S42 **não instala** o pacote na VPS. Ela entrega o pacote instalável e suas
provas para a próxima slice.

## 2. Causa técnica obrigatória

A VPS usa:

```text
Python       3.10.12
jsonschema   3.2.0 (pacote global do Ubuntu 22.04)
```

Os schemas do deploy declaram Draft 2020-12 e usam `$defs` e `prefixItems`. A
prova read-only executada pelo orquestrador mostrou que o `jsonschema 3.2.0`
aceita um array que viola `prefixItems`. Portanto, instalar o código atual sobre
o Python global enfraqueceria silenciosamente invariantes de ordem.

É proibido:

- usar o `jsonschema` global como runtime contratado;
- instalar/atualizar pacote Python global na VPS;
- ignorar `prefixItems`, `$defs` ou a declaração Draft 2020-12;
- copiar um venv produzido pelo Python 3.13 da workstation;
- depender de resolução não pinada durante o deploy.

## 3. Autorização e fronteira externa

A mensagem de delegação deve conter literalmente:

```text
Autorizo integralmente a S42, incluindo implementação do pacote instalável do control root, um único commit causal, um único push fast-forward e observação de CI e Publish Candidate. Não autorizo acesso ou mutação da VPS, deploy ou rollback.
```

Sem a frase, executar somente snapshot e análise. Com ela, não solicitar nova
confirmação para commit/push dentro desta task.

## 4. Contrato do pacote

### 4.1 Identidade

O pacote representa exatamente um commit do default branch:

```text
repository    greggorio/abaronesa-emporio
kind          emporio-control-root
platform      linux/amd64
python        cp310
target        /opt/sistemas/emporio/shared/control
```

Nenhuma tag, branch móvel ou working tree sujo pode ser identidade. O builder
recebe um SHA de 40 hex, confirma que o objeto é commit local e materializa cada
arquivo diretamente desse objeto Git, não da árvore de trabalho.

### 4.2 Conteúdo fechado

Definir uma allowlist explícita e causal contendo apenas o necessário para:

- `ops/deploy/deployment-remote.py`;
- `ops/deploy/deploy-release.sh`;
- schemas de deploy usados pelo helper, CLI, executor e adapter;
- `deployment_cli.py`, `deployment_executor.py`, `deployment_plan.py` e
  `production_adapter.py`;
- fechamento de imports em `tools/releases` e `tools/candidates` realmente
  alcançado por `deployment_plan.py`;
- schemas/catálogo de release consumidos por esse fechamento;
- `ops/compose/compose.prod.yml` canônico;
- runtime vendorizado e manifesto do control root.

Testes, examples, docs, workflows, código das aplicações, `.git`, `.env`,
relatórios e qualquer arquivo não allowlisted são proibidos no archive.

O validador deve rejeitar arquivo extra, ausente, duplicado, path traversal,
symlink, hardlink, device, FIFO, tamanho excessivo ou modo divergente.

### 4.3 Dependências isoladas

Criar lock próprio do control root, independente do `release_control`, contendo
somente:

- `jsonschema` na linha `4.x` compatível com Python 3.10;
- PyYAML necessário ao catálogo;
- dependências transitivas mínimas.

Regras:

- versões exatas e hashes SHA-256 de todos os wheels;
- somente wheel puro ou manylinux `x86_64` compatível com glibc do Ubuntu 22.04;
- nenhum sdist, compilação, índice alternativo ou dependência flutuante;
- builder falha se hash, nome, versão, ABI ou plataforma divergir;
- nenhum acesso a rede durante instalação/validação do archive já construído;
- runtime vendorizado dentro do pacote, em path fixo e não global;
- `deployment-remote.py` e `deployment_cli.py` devem preferir exclusivamente
  esse vendor quando executados do control root;
- no checkout normal sem vendor, testes/desenvolvimento continuam usando o
  ambiente já provisionado, sem baixar nada implicitamente.

Não adicionar FastAPI, SQLAlchemy, Alembic ou dependências do runtime HTTP ao
control root.

### 4.4 Manifesto e sidecar

Definir schema fechado e JSON canônico contendo no mínimo:

```text
schemaVersion
kind
repository
sourceSha
platform
pythonAbi
requirementsSha256
files[]: path, mode, size, sha256
createdAt
```

O conjunto e a ordem dos arquivos são determinísticos. Timestamp deve derivar
do commit, normalizado em UTC, não do relógio da execução.

O sidecar contém somente o SHA-256 hex do manifesto seguido de LF. O archive
possui sidecar próprio externo, evitando identidade circular.

Dois builds do mesmo SHA, lock e plataforma devem ser byte a byte idênticos.

### 4.5 Instalador inicial

Implementar instalador standard library, executável somente como root, com
target literal e sem opção de escolher comando/path. Ele deve:

1. aceitar somente archive, sidecar e `--source-sha`;
2. validar archive/manifest/sidecars integralmente antes de escrever no target;
3. exigir host `linux/amd64`, CPython 3.10 e usuário `deploy-emporio` existente;
4. exigir `/opt/sistemas/emporio/shared/control` como diretório real `0700`, do
   usuário dedicado e vazio;
5. extrair em staging irmão criado `0700`, sem seguir links;
6. reler e revalidar todos os bytes extraídos;
7. publicar no control root sem janela de conteúdo parcial;
8. aplicar owner `deploy-emporio:deploy-emporio`, diretórios `0700`, scripts
   `0755` e arquivos regulares `0600` conforme manifesto;
9. `fsync` de arquivos/diretórios críticos;
10. remover staging exato em falha e deixar o control root vazio;
11. recusar upgrade/substituição de root não vazio nesta primeira versão.

Não usar `tar` externo para confiar em paths, `sudo`, shell interpolation,
`eval`, symlink de `control`, package manager do host ou execução pós-instalação.

## 5. Binding do control SHA

Hoje `controlSha` é transportado no request/outcome, mas o helper instalado não
prova que seus próprios bytes pertencem a esse SHA. Fechar o elo:

1. `deployment-remote.py capabilities` lê e valida o manifesto instalado e
   todos os arquivos críticos;
2. capabilities retorna também o `controlSha` instalado;
3. o transporte fornece/valida o SHA esperado derivado do request confiável;
4. qualquer SHA divergente, manifesto ausente, arquivo adulterado ou vendor
   incompleto falha como `REMOTE_CAPABILITY_MISMATCH` antes de upload/snapshot;
5. snapshot/install/execute/cleanup nunca rodam se capabilities não fechar;
6. o SHA não pode vir de variável mutável da VPS.

Atualizar testes, schemas/documentação e todos os call sites de forma coerente.
Não criar modo de compatibilidade que aceite helper sem manifesto.

## 6. Snapshot inicial

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD
git ls-remote origin refs/heads/main
git log --oneline --decorate -12
sha256sum docs/infrastructure/deployment/implementation/slices/S42-pacote-instalavel-control-root-dependencias-binding.task.md
gh run list --workflow ci.yml --limit 5
gh run list --workflow publish-candidate.yml --limit 5
gh run list --workflow deploy-production.yml --limit 100 --json databaseId
gh run list --workflow rollback-production.yml --limit 100 --json databaseId
git diff --check
git diff --cached --name-only
```

Estado esperado:

- remoto `main` em `daaa7061ab9f7a722b17e37c0f060f45141225e7`;
- HEAD contém apenas commits documentais posteriores, à frente e sem behind;
- stage vazio;
- relatórios S39, S40 e S41 não rastreados;
- zero deploy/rollback;
- VPS não será acessada nesta slice.

Qualquer divergência de código/resíduo alheio interrompe antes de editar.

## 7. Fronteira de arquivos

Permitidos somente arquivos necessários dentro de:

```text
ops/deploy/
tools/deploy/control_root_package.py
tools/deploy/validate_control_root_package.py
tools/deploy/deployment_transport.py
tools/deploy/deployment_cli.py
tools/deploy/tests/
docs/infrastructure/deployment/release-control/
.github/workflows/ci.yml
```

Não alterar runtime/UI do release control, aplicações Java/Node, migrations,
Compose comercial, workflows de candidato/release/deploy/rollback, catálogos,
state machines ou configuração real de produção.

`deploy-production.yml` não deve mudar; somente o código chamado por ele pode
fechar o binding.

## 8. Testes causais obrigatórios

Cobrir ao menos:

1. source SHA ausente, inválido, não commit ou divergente;
2. working tree/untracked reports nunca entrando no pacote;
3. arquivo allowlisted ausente e arquivo extra;
4. archive com traversal, symlink, hardlink, device, FIFO ou modo inseguro;
5. wheel sem hash, sdist, ABI/plataforma errada ou dependência extra;
6. lock/requirements adulterado;
7. manifesto não canônico, sidecar divergente ou arquivo adulterado;
8. dois builds do mesmo input não idênticos;
9. root, plataforma, Python, owner ou target divergentes;
10. target não vazio e staging preexistente/hostil;
11. falha de extração deixando target vazio e sem staging;
12. helper sem manifesto ou com `controlSha` divergente;
13. mutação em helper, CLI, schema, Compose ou vendor detectada antes do SSH
    avançar para snapshot/upload;
14. JSON Schema Draft 2020-12 rejeitando concretamente mutantes de
    `prefixItems` e `$defs` que o `jsonschema 3.2.0` aceitaria;
15. capabilities verde somente com pacote íntegro do SHA esperado;
16. nenhuma chamada Docker, SSH, GHCR ou VPS durante os testes locais.

## 9. Prova em runtime isolado

Depois dos testes unitários, construir o pacote para um SHA-fixture e executar
em container descartável `linux/amd64` com Python 3.10, sem `jsonschema` ou
PyYAML globais utilizáveis.

No container:

- criar usuário/grupo de fixture equivalente;
- instalar em `/opt/sistemas/emporio/shared/control` pelo instalador real;
- bloquear rede depois que os wheels já estiverem no pacote;
- executar helper como usuário não-root;
- provar capabilities com `controlSha` exato;
- provar imports vindos do vendor;
- provar versão `jsonschema 4.x` e Draft 2020-12 efetivo;
- adulterar cópia isolada e provar falha antes de qualquer ação;
- confirmar zero container/volume/rede/imagem residual após cleanup dirigido.

Fixar por digest a imagem base usada na prova. Nenhum push de imagem.

## 10. Matriz local

Executar testes direcionados e depois:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -v
python3 tools/deploy/validate_control_root_package.py
python3 tools/deploy/validate_deploy_workflow.py
python3 tools/deploy/validate_production_adapter.py
python3 tools/deploy/validate_deployer_runtime.py
python3 tools/ci/validate_ci.py
python3 tools/ci/validate_workflow_inventory.py
python3 tools/releases/catalog.py validate
python3 tools/ci/secret_scan.py --tracked
git diff --check
```

Executar também os 17 validadores e oito suítes canônicas vigentes. Todos os
exits devem ser `0`, sem skip/falha nova; secret scan `clean`, `unsupported=0`.

## 11. Commit, prova do SHA e push

Somente com os gates pré-commit verdes:

1. stagear exclusivamente o patch permitido; relatórios ficam fora;
2. rodar secret scan novamente com os arquivos novos staged;
3. criar um único commit causal, sem amend;
4. construir duas vezes o pacote do **novo HEAD** e provar bytes/hashes iguais;
5. executar a prova isolada final contra esse SHA;
6. se falhar depois do commit, parar sem amend/segundo commit/push;
7. reconfirmar que o remoto ainda é o SHA inicial;
8. fazer um único push normal fast-forward de `main`;
9. observar CI e Publish Candidate do mesmo SHA até terminais;
10. validar jobs e artifacts;
11. confirmar zero deploy/rollback e nenhuma mutação da VPS.

O package de control root gerado localmente é prova temporária, não artifact
GitHub nem release nesta slice. Removê-lo de forma dirigida após registrar
hashes. A próxima slice deve reconstruí-lo deterministicamente do SHA remoto.

## 12. Proibições

- acessar ou mutar a VPS;
- criar/alterar environment, secrets, vars, Apps ou allowlists;
- executar deploy, rollback ou qualquer workflow manual;
- instalar pacote global na workstation/host;
- publicar archive, imagem, tag ou release;
- incluir segredo, `.env`, chave, Docker config ou relatório no pacote/commit;
- usar source tree suja como origem;
- rebase, amend, force, segundo commit ou segundo push;
- relaxar schemas, modes, path checks ou fail-closed existente.

## 13. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S42-pacote-instalavel-control-root-dependencias-binding.report.md
```

Manter não rastreado, não staged e não commitado. Registrar:

- autorização, snapshots e SHA-256 da task;
- causa `jsonschema 3.2.0` reproduzida por teste;
- fechamento de arquivos/imports e lock completo;
- schema/manifesto/sidecars e determinismo;
- testes causais, mutantes, comandos, exits e contagens;
- prova isolada Python 3.10/Draft 2020-12;
- commit/push e runs remotos;
- hashes do pacote temporário e cleanup;
- zero VPS/deploy/rollback;
- resíduos finais.

O executor não aceita S42, não cria a próxima slice e não instala o pacote.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — pacote do control root versionado e validado; aguardando aceite e instalação na VPS
```

Na primeira causa não resolvida:

```text
BLOCKED — S42 interrompida fail-closed na primeira causa técnica
```

## 14. Critérios de aceite

S42 somente será aceita com pacote fechado e determinístico, dependências
vendorizadas e hashadas, Draft 2020-12 efetivo em Python 3.10, instalador inicial
fail-closed, binding real do helper ao `controlSha`, matriz completa verde, um
commit/push fast-forward, CI/candidato verdes e zero mutação da VPS.
