# S04 — Saneamento dos workflows legados e primeiro push seguro

> **Estado:** `ACCEPTED` — revisão concluída em 28/07/2026  
> **Tipo:** seguranca operacional e saneamento de CI/CD legado  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01, S02 e S03 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Evidencia de entrada S01:** [S01-inventario-e-contratos-reais.report.md](./S01-inventario-e-contratos-reais.report.md)  
> **Evidencia de entrada S03:** [S03-fundacao-git-local-e-auditoria-primeiro-indice.report.md](./S03-fundacao-git-local-e-auditoria-primeiro-indice.report.md)  
> **Relatorio de saida:** `S04-saneamento-workflows-legados-e-primeiro-push-seguro.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretório indicado. Leia primeiro este
contrato, a arquitetura aprovada e as revisões finais da S01 e da S03.

Esta é uma slice de neutralização, não a implementação da CI definitiva. Seu
objetivo é garantir que o primeiro push manual não publique imagens, não
acesse a VPS e não implante produção por efeito de workflows protótipos.

Não altere este arquivo de task nem o tracker
`docs/infrastructure/deployment/implementation/README.md`.

## Objetivo observavel

Ao final:

- os dois workflows operacionais incompatíveis da raiz terão sido removidos;
- os dois workflows aninhados, legados e inertes terão sido removidos;
- `.github/workflows/` conterá somente documentação Markdown transitória;
- nenhum workflow GitHub Actions estará ativo;
- o primeiro push não poderá acionar build, publicação de imagem, SSH ou
  deploy por configuração presente no repositório;
- a ausência temporária de CI estará explícita e não será apresentada como
  arquitetura concluída;
- o índice Git real continuará vazio;
- não haverá commit, tag ou push;
- nenhuma configuração Git, aplicação, Docker ou produção será alterada;
- o relatório preservará evidência dos quatro alvos removidos e dos limites da
  slice.

## Justificativa da ordem

A arquitetura aprovada prevê:

1. congelar contratos reais dos componentes;
2. definir o contrato do `release_control`;
3. corrigir Dockerfiles e Compose;
4. criar a CI canônica;
5. criar publicação de candidatos, releases e deploy de produção.

Os workflows atuais não podem permanecer até essas etapas porque:

- fazem build/push sem a validação mínima exigida;
- dois workflows da raiz concorrem no mesmo push;
- um deles usa `latest`;
- um deles faz SSH como `root`;
- um deles implanta produção automaticamente em push para `main`;
- os dois aninhados são resíduos de repositórios separados e confundem a
  topologia do monorepo.

Portanto, esta slice cria um intervalo transitório deliberado sem workflows
ativos. Isso é mais seguro do que publicar os protótipos ou antecipar uma CI
baseada em Dockerfiles e contratos ainda não corrigidos.

## Decisoes fixas

- Commits e pushes continuam sendo realizados pelo usuário no terminal.
- Esta slice não executa commit, tag ou push.
- Esta slice não implementa `ci.yml`.
- Esta slice não implementa `publish-candidate.yml`.
- Esta slice não implementa `publish-release.yml`.
- Esta slice não implementa `deploy-production.yml`.
- Não haverá workflow placeholder em YAML.
- Arquivos Markdown dentro de `.github/workflows/` não são workflows ativos.
- Os quatro workflows legados serão removidos, não renomeados para outra
  extensão nem arquivados dentro do repositório.
- A evidência funcional já está preservada nos relatórios da S01 e da S03.
- Não tocar em workflows encontrados apenas dentro de caches ignorados.
- Não configurar GitHub Actions, secrets, environments ou regras de branch
  remotamente.
- Não acessar GitHub, GHCR, DNS ou VPS.
- O estado `ACCEPTED` somente pode ser atribuído pelo orquestrador.

## Alvos exatos de remocao

Remover somente:

```text
.github/workflows/deploy.yml
.github/workflows/main.yml
backend/.github/workflows/backend.yml
frontend/.github/workflows/frontend.yml
```

Classificação aceita:

| Path | Estado anterior | Motivo da remocao |
|---|---|---|
| `.github/workflows/deploy.yml` | operacional | deploy automático, `latest`, SSH como `root`, sem testes |
| `.github/workflows/main.yml` | operacional | build/push sem testes completos, cobertura parcial e conflito |
| `backend/.github/workflows/backend.yml` | legado/inerto | resíduo de repositório de componente |
| `frontend/.github/workflows/frontend.yml` | legado/inerto | resíduo de repositório de componente |

Não remover diretórios recursivamente. Remova cada arquivo pelo path exato.
Diretórios vazios sob `backend/.github/` e `frontend/.github/` podem permanecer;
não crie `.gitkeep`.

## Escopo de escrita

Arquivos que podem ser criados ou alterados:

```text
.github/workflows/README.md
docs/infrastructure/deployment/implementation/slices/S04-saneamento-workflows-legados-e-primeiro-push-seguro.report.md
```

Remoções autorizadas:

```text
.github/workflows/deploy.yml
.github/workflows/main.yml
backend/.github/workflows/backend.yml
frontend/.github/workflows/frontend.yml
```

Nenhum outro arquivo ou metadado pode ser alterado.

## Fora de escopo

Não executar nem implementar:

- `git add` no índice real ou temporário;
- commit, amend, merge, rebase, tag ou push;
- qualquer novo arquivo `.yml` ou `.yaml` em `.github/workflows/`;
- testes, lint, builds Maven/npm ou instalação de dependências;
- build ou publicação de imagem;
- login em registry;
- SSH, SCP ou acesso à VPS;
- GitHub Actions remoto;
- secrets, variables, environments ou branch protection;
- Dockerfile, Compose, Nginx ou scripts de deploy;
- contratos de componentes;
- `release_control`;
- manifesto candidato ou release global;
- alterações em código de aplicação;
- alterações em README raiz, onboarding, `.gitignore`, `.gitattributes`,
  arquivos de ambiente ou propriedades Spring;
- remoção de caches, HPROF, uploads, PFX, GLBs ou qualquer artefato fora dos
  quatro workflows exatos.

## Implementacao obrigatoria

### 1. Preflight

Registrar antes das remoções:

```bash
pwd
git rev-parse --show-toplevel
git symbolic-ref --short HEAD
git remote get-url origin
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
```

Resultados esperados:

- CWD e toplevel exatos;
- branch `main`;
- remoto canônico;
- índice real vazio;
- `HEAD` inexistente;
- nenhuma tag.

Confirmar a existência dos quatro alvos com consulta individual. Para cada
alvo, registrar:

- path;
- tamanho;
- classificação aceita;
- gatilho/efeito de alto nível;
- checksum SHA-256 do arquivo, apenas para identificação da evidência removida.

Não transcrever o conteúdo integral dos workflows no relatório.

Se qualquer alvo estiver ausente ou tiver sido alterado concorrentemente em
relação ao inventário aceito, não remova os demais silenciosamente. Registre o
estado e avalie se ainda é possível concluir sem ampliar o escopo.

### 2. Remocao controlada

Remover individualmente os quatro paths exatos.

Não usar:

```text
rm -rf
find ... -delete
git clean
git reset
git checkout
```

Não remover workflows sob `.ai-workflow`, `node_modules` ou outros caches
ignorados. Eles não são parte do repositório candidato.

### 3. Documentacao transitoria

Criar:

```text
.github/workflows/README.md
```

O documento deve explicar:

- não há workflow GitHub Actions ativo nesta etapa;
- os protótipos anteriores foram removidos antes do primeiro push;
- commits e pushes continuam manuais;
- nenhum push deve implantar produção automaticamente;
- a CI canônica ainda será implementada em slice posterior;
- a topologia futura prevista contém responsabilidades separadas:
  - `ci.yml`;
  - `publish-candidate.yml`;
  - `publish-release.yml`;
  - `deploy-production.yml`;
- criar um YAML nessa pasta exige validação contra a arquitetura aprovada;
- o README não representa um workflow nem produz execução no GitHub.

Não incluir código YAML executável, credenciais, IP da VPS ou instruções para
SSH.

### 4. Verificacao de ausencia de workflow ativo

Depois das remoções, comprovar:

```text
.github/workflows/deploy.yml              ausente
.github/workflows/main.yml                ausente
backend/.github/workflows/backend.yml     ausente
frontend/.github/workflows/frontend.yml   ausente
```

Inventariar arquivos `.yml` e `.yaml` sob qualquer diretório
`.github/workflows/`, excluindo:

```text
**/node_modules/**
**/.ai-workflow/**
```

Resultado obrigatório: nenhum arquivo.

Inventariar os diretórios `.github/workflows/` não ignorados e comprovar que:

- a raiz contém somente `README.md`;
- qualquer diretório aninhado remanescente está vazio;
- nenhum workflow aninhado candidato permaneceu.

Não afirme que arquivos em caches deixaram de existir; apenas confirme que
continuam ignorados e fora do inventário operacional.

### 5. Busca de residuos operacionais

Sem varrer caches ou dependências, confirmar que não existe arquivo de
workflow candidato contendo:

```text
appleboy/ssh-action
VPS_SSH_KEY
username: root
docker compose up
tags: ... latest
```

Essa verificação se limita a arquivos sob diretórios `.github/workflows/`.
Scripts e protótipos de deploy existentes em outras áreas pertencem a slices
posteriores e não devem ser alterados aqui.

### 6. Estado Git final

Registrar:

```bash
git rev-parse --show-toplevel
git symbolic-ref --short HEAD
git remote get-url origin
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog
git status --short
```

Resultados obrigatórios:

- toplevel, branch e remoto preservados;
- índice real vazio;
- `HEAD` inexistente;
- nenhuma tag;
- reflog vazio;
- nenhum commit ou push.

`git status --short` continuará mostrando arquivos não rastreados. Resuma sem
transcrever paths sensíveis ignorados.

## Evidencia obrigatoria

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S04-saneamento-workflows-legados-e-primeiro-push-seguro.report.md
```

O relatório deve conter:

1. metadados, data e CWD;
2. lista exata de arquivos criados e removidos;
3. preflight Git com comandos, códigos e interpretação;
4. matriz dos quatro workflows antes da remoção;
5. checksums SHA-256 dos quatro alvos;
6. método de remoção individual;
7. conteúdo e propósito do README transitório;
8. inventário final de `.github/workflows/`;
9. busca final de resíduos operacionais;
10. estado Git final;
11. tabela de comandos relevantes com CWD, código de saída, resultado e
    interpretação;
12. desvios, itens não determinados e bloqueios;
13. declaração explícita de que não houve commit, tag, push, workflow novo,
    teste, build, Docker, GitHub, GHCR ou VPS;
14. nota de recuperabilidade informando que os quatro workflows ainda não
    pertenciam a um commit local e, portanto, não podem ser restaurados pelo
    histórico Git deste repositório; seus contratos e classificações
    permanecem documentados na S01 e na S03;
15. resposta final solicitada ao CLI.

O relatório não deve:

- reproduzir integralmente os workflows removidos;
- revelar credenciais ou valores de arquivos locais;
- afirmar que CI/CD está concluída;
- afirmar que CI será executada no primeiro push;
- afirmar que produção foi consultada ou alterada.

## Criterios de aceite

- S01, S02 e S03 permanecem respeitadas.
- Somente os quatro workflows exatos foram removidos.
- `.github/workflows/README.md` foi criado e é factual.
- Não há `.yml` ou `.yaml` candidato em diretório `.github/workflows/`.
- Não existe workflow GitHub Actions ativo no monorepo.
- O primeiro push não pode publicar imagem ou acessar produção por workflow
  presente no repositório.
- A ausência temporária de CI está explícita.
- Nenhum workflow placeholder foi criado.
- O índice Git real permanece vazio.
- `HEAD`, tags e reflog permanecem vazios.
- Não houve commit nem push.
- Nenhuma ação externa ou arquivo fora do escopo foi alterado.
- O relatório contém evidência persistida suficiente para revisão.

## Condicoes de bloqueio

Interrompa e registre `BLOCKED` se:

- branch, remoto ou estado Git divergirem do preflight esperado;
- o índice real contiver entradas;
- algum dos quatro alvos tiver sido substituído ou alterado
  concorrentemente de forma relevante;
- surgir outro workflow candidato ativo fora dos quatro alvos;
- for necessário alterar arquivo fora do escopo;
- a remoção exigir comando recursivo ou destrutivo amplo;
- houver qualquer necessidade de acesso externo.

## Resposta final esperada do CLI

Responder de forma concisa com:

- caminho absoluto do relatório;
- quatro workflows removidos;
- arquivo Markdown criado;
- resultado da busca de workflows ativos;
- confirmação de ausência temporária de CI;
- informação de que os workflows removidos não são recuperáveis pelo histórico
  Git local, que ainda não possui commit;
- estado Git final;
- bloqueios e itens não determinados;
- confirmação de que não houve commit, tag ou push;
- estado `IN_PROGRESS`, aguardando revisão do orquestrador.

Não declarar `ACCEPTED`.
