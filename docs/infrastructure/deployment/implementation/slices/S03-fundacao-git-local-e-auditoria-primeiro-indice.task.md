# S03 — Fundacao Git local e auditoria do primeiro indice

> **Estado:** `ACCEPTED` — revisão concluída em 28/07/2026  
> **Tipo:** implementacao Git local, documentacao e auditoria de publicabilidade  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 e S02 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Evidencia de entrada S01:** [S01-inventario-e-contratos-reais.report.md](./S01-inventario-e-contratos-reais.report.md)  
> **Evidencia de entrada S02:** [S02-saneamento-pre-git-e-protecao-primeiro-commit.report.md](./S02-saneamento-pre-git-e-protecao-primeiro-commit.report.md)  
> **Relatorio de saida:** `S03-fundacao-git-local-e-auditoria-primeiro-indice.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro este
contrato, a arquitetura aprovada e as revisoes finais da S01 e da S02.

Esta slice autoriza inicializar o repositório Git local e configurar o remoto,
mas não autoriza criar o primeiro commit nem publicar qualquer conteúdo. O
índice Git real deve terminar vazio. A simulação do primeiro índice deve usar
um arquivo de índice temporário, isolado por `GIT_INDEX_FILE`.

Não altere este arquivo de task nem o índice geral
`docs/infrastructure/deployment/implementation/README.md`. O executor produz
somente a implementação autorizada e o relatório de saída.

## Objetivo observavel

Ao final:

- a raiz `emporio/` será reconhecida como um único repositório Git;
- a branch inicial local será `main`;
- `origin` apontará para
  `git@github.com:greggorio/abaronesa-emporio.git`;
- não haverá repositórios Git aninhados;
- não haverá commit, tag ou push;
- o índice Git real estará vazio;
- haverá uma simulação reproduzível do conteúdo candidato ao primeiro commit,
  usando índice temporário;
- nenhum arquivo ignorado, segredo conhecido, dump, upload ou artefato gerado
  entrará no índice temporário;
- arquivos grandes e binários candidatos serão inventariados sem ler dumps,
  certificados privados ou mídias integralmente;
- os workflows atualmente existentes e qualquer outro gate para o primeiro
  push serão classificados explicitamente;
- existirão `README.md` e `.gitattributes` mínimos na raiz;
- o onboarding mínimo deixará de mencionar o projeto legado Bakery e os
  módulos legados `espresso_front`/`espresso_back`;
- o relatório permitirá ao orquestrador decidir a próxima slice sem depender
  de evidência apenas no terminal.

## Decisoes fixas desta slice

- O remoto canônico é
  `git@github.com:greggorio/abaronesa-emporio.git`.
- A branch canônica é `main`.
- O projeto é um monorepo; nenhum componente receberá `.git` próprio.
- Commits e pushes continuarão sob responsabilidade do usuário no terminal.
- Esta slice não executa `git add` contra o índice real.
- Esta slice não executa `git commit`, `git tag` ou `git push`.
- `git ls-remote` é somente uma verificação opcional e de leitura. Falha de
  autenticação ou rede não autoriza alterar chaves, credenciais ou o remoto.
- A presença do remoto não significa que o conteúdo esteja pronto para push.
- Os workflows atuais são candidatos ao primeiro índice, mas a S01 já
  identificou comportamento incompatível com a arquitetura aprovada. Eles
  devem ser inventariados como gate; não devem ser corrigidos nesta slice.
- Nenhuma credencial pendente será rotacionada ou validada externamente.
- Nenhum arquivo local será apagado, movido ou truncado.
- O HPROF real não será aberto nem inspecionado por conteúdo.
- Arquivos legítimos acima de 5 MB não serão removidos nem enviados ao Git LFS
  sem uma decisão arquitetural posterior.
- O estado `ACCEPTED` somente pode ser atribuído pelo orquestrador.

## Estado de entrada conhecido

A revisão anterior confirmou:

- `.git` inexistente na raiz;
- `.gitignore` raiz presente;
- `website_front/android/java_pid2033186.hprof` com 297271296 bytes,
  protegido por `**/*.hprof`;
- `backend/uploads/` protegido por ignore;
- arquivos `.env.example` preservados;
- propriedades Spring conhecidas saneadas;
- dois modelos `pub_interior.glb`, cada um com 23318828 bytes, presentes em:

  ```text
  website_front/public/assets/models/pub_interior.glb
  website_front/android/app/src/main/assets/public/assets/models/pub_interior.glb
  ```

- workflows existentes com comportamento incompatível com a arquitetura;
- `README.md` e `.gitattributes` raiz ausentes;
- `docs/development/ONBOARDING_MINIMO.md` ainda descrevendo o projeto legado
  Bakery e diretórios que não representam este monorepo.

Revalide esses fatos. Não os trate como substitutos da evidência da execução.

## Escopo de leitura

O executor pode ler arquivos necessários sob:

```text
/home/gregorio/git/baronesa/emporio
```

Priorize:

```text
.gitignore
.env.example
.github/
backend/
frontend/
website_back/
website_front/
whatsapp_service/
deploy/
ops/
docs/
quality/
tools/
```

Não faça varredura de conteúdo integral em:

```text
**/*.hprof
backend/uploads/
**/target/
**/node_modules/
**/.gradle/
**/.quasar/
**/.ai-workflow/
```

Para esses caminhos, use apenas pathname, tipo, tamanho, modo, regra de ignore
e demais metadados estritamente necessários.

## Escopo de escrita

Arquivos que podem ser criados ou alterados:

```text
README.md
.gitattributes
.gitignore
docs/development/ONBOARDING_MINIMO.md
docs/development/README.md
docs/infrastructure/deployment/implementation/slices/S03-fundacao-git-local-e-auditoria-primeiro-indice.report.md
```

Alterações internas autorizadas:

```text
.git/
```

`README.md`, `.gitattributes` e o relatório são esperados.

`.gitignore` somente pode ser alterado se a auditoria encontrar uma categoria
local, gerada ou sensível que esteja realmente desprotegida. Nesse caso:

- registre o path e a classificação sem revelar conteúdo;
- use a regra mais estreita que cubra a categoria;
- não esconda código-fonte ou ativo funcional para obter uma validação verde;
- repita toda a auditoria do índice temporário;
- documente a alteração no relatório.

`docs/development/README.md` somente deve ser alterado para manter seu índice e
descrições alinhados ao onboarding corrigido.

Qualquer outra necessidade de escrita é bloqueio para o orquestrador.

## Fora de escopo

Não executar nem implementar:

- `git add` usando o índice real;
- commit, amend, merge, rebase, tag ou push;
- assinatura de commit ou configuração global de Git;
- criação, remoção ou modificação de branch remota;
- alteração de chaves SSH, tokens GitHub ou permissões do repositório;
- GitHub Actions, regras de branch, environments ou secrets;
- correção ou remoção dos workflows atuais;
- Dockerfiles, Compose, Nginx, scripts de deploy ou `release_control`;
- Git LFS;
- exclusão ou deduplicação dos arquivos GLB;
- remoção de HPROF, uploads, caches, dependências ou outros arquivos locais;
- abertura ou leitura de HPROF, PFX, chaves privadas ou conteúdo de uploads;
- build, teste Maven, teste npm ou instalação de dependências;
- rotação ou validação externa de credenciais;
- acesso ao GHCR, DNS ou VPS.

## Implementacao obrigatoria

### 1. Preflight imutavel

Antes de inicializar Git, registre:

```bash
pwd
test ! -e .git
find . -name .git \
  -not -path '*/node_modules/*' \
  -not -path '*/.ai-workflow/*' \
  -print
git rev-parse --show-toplevel
```

Resultados esperados antes da inicialização:

- CWD exato da slice;
- `.git` ausente;
- nenhum `.git` aninhado;
- `git rev-parse` com código 128.

Se existir um `.git` inesperado ou outro repositório aninhado, não execute
`git init`; registre bloqueio.

### 2. Inicializacao Git local

Com o preflight aprovado:

```bash
git init -b main
git remote add origin git@github.com:greggorio/abaronesa-emporio.git
```

Se `origin` já existir por alteração concorrente, não o sobrescreva
automaticamente. Compare o valor:

- se for exatamente o remoto canônico, preserve e registre;
- se divergir, pare e registre bloqueio;
- não use `git remote set-url` sem nova autorização.

Não configure `user.name`, `user.email` ou opções globais nesta slice.

### 3. README raiz

Criar `README.md` conciso e factual contendo:

- nome Empório A Baronesa;
- explicação de que a raiz é um monorepo;
- tabela dos cinco componentes:
  - `backend` — ERP Spring Boot;
  - `frontend` — ERP Quasar/Vue;
  - `website_back` — API pública Spring Boot;
  - `website_front` — site/PWA React/Vite;
  - `whatsapp_service` — integração WhatsApp Node;
- desenvolvimento local manual, sem afirmar que Docker de produção está
  implantado;
- comandos de entrada reais:

  ```text
  backend: mvn spring-boot:run
  website_back: mvn spring-boot:run
  frontend: npm run dev
  website_front: npm run dev
  whatsapp_service: npm start
  ```

- orientação para variáveis locais por
  `docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md`;
- links para:
  - `docs/development/ONBOARDING_MINIMO.md`;
  - o índice de documentação;
  - a arquitetura de deploy aprovada;
  - o tracker de implementação;
- aviso de que a arquitetura Docker/CI/CD e o controle de releases estão em
  implementação incremental;
- aviso de que `.env`, uploads, dumps e credenciais não devem ser commitidos.

Não documente os workflows atuais como pipeline aprovada ou operacional.

### 4. Onboarding minimo

Corrigir `docs/development/ONBOARDING_MINIMO.md` para o projeto atual:

- Empório A Baronesa;
- repositório `~/git/baronesa/emporio`;
- nomes reais dos cinco componentes;
- fluxo local manual aceito pelo usuário;
- referências ao guia de segredos;
- comandos obtidos dos `pom.xml` e `package.json`;
- portas somente quando confirmadas no código/configuração;
- dependência de PostgreSQL sem publicar senha literal;
- limites claros: o documento não afirma que o deploy proposto já está
  implantado.

Remover referências factualmente legadas a:

```text
Bakery
~/git/bakery
espresso_front
espresso_back
```

Atualizar `docs/development/README.md` apenas se necessário para eliminar uma
descrição que passe a contradizer o onboarding corrigido.

### 5. Gitattributes minimo

Criar `.gitattributes` sem regras de Git LFS e sem mascarar arquivos
sensíveis. Deve, no mínimo:

- usar detecção automática de texto;
- preservar LF para scripts shell, YAML, JSON, propriedades, Markdown, Java,
  JavaScript, TypeScript e CSS;
- classificar imagens, fontes, PDFs, mídias e modelos GLB como binários;
- classificar o `gradle-wrapper.jar` e demais JARs rastreáveis como binários.

Não classifique PFX, P12, JKS, PEM ou chaves privadas como ativos normais. Se
algum desses formatos aparecer como candidato ao índice, trate-o na auditoria
de sensibilidade.

### 6. Simulacao isolada do primeiro indice

Após todas as alterações autorizadas, derive primeiro a lista de candidatos
sem adicioná-los a índice algum:

```bash
git ls-files --others --exclude-standard -z
```

Mantenha qualquer lista auxiliar somente em diretório criado por `mktemp -d`,
fora do workspace. Execute sobre essa lista as verificações de sensibilidade e
de tamanho das Seções 8 e 9. Isso deve ocorrer antes de `git add`, porque mesmo
um índice temporário faz o Git materializar blobs no object database local.

Somente se a pré-auditoria não encontrar bloqueio, defina um caminho absoluto
de índice dentro do diretório temporário e execute:

```bash
GIT_INDEX_FILE=<caminho-absoluto-temporario>/index git add -A
```

Regras:

- nunca exporte `GIT_INDEX_FILE` de modo persistente;
- nunca execute `git add` sem `GIT_INDEX_FILE`;
- não execute o `git add` temporário antes da pré-auditoria;
- use o índice temporário para todos os comandos de inventário de candidatos;
- remova somente o diretório temporário criado pela própria execução ao final;
- confirme que o índice real continua vazio.

Produza, a partir do índice temporário:

- total de arquivos candidatos;
- total por diretório de primeiro nível;
- lista de candidatos maiores que 5 MB;
- lista de candidatos maiores que 100 MB;
- lista de extensões binárias e quantidades;
- lista de workflows candidatos;
- lista de arquivos com nomes/extensões sensíveis;
- comprovação das exclusões obrigatórias.

Não transcreva conteúdo binário ou valores sensíveis.

### 7. Matriz de exclusoes obrigatorias

Comprove que os seguintes tipos não entram no índice temporário:

```text
.env
.env.local
**/.env
ops/env/.env.production
**/target/**
**/node_modules/**
**/.gradle/**
**/.quasar/**
backend/uploads/**
backend/outputs/**
backend/nfe/xmls/**
**/*.hprof
**/hs_err_pid*.log
**/replay_pid*.log
**/.ai-workflow/**
**/.claude/**
**/.opencode/**
```

Comprove também que estes exemplos continuam candidatos:

```text
.env.example
backend/.env.example
website_back/.env.example
website_front/.env.example
ops/env/.env.example
```

Use `git check-ignore -v` e consultas ao índice temporário. Diferencie:

- ignorado por regra;
- ausente fisicamente;
- presente e candidato.

Não fabrique arquivos sensíveis reais. Se precisar verificar uma regra para
um path ausente, use apenas diretório temporário fora do workspace ou
`git check-ignore --no-index`.

### 8. Auditoria de sensibilidade

Audite somente arquivos candidatos no índice temporário.

A auditoria deve procurar, no mínimo:

- cabeçalhos de chave privada;
- tokens com prefixos reconhecíveis de provedores;
- assignments não vazios para `password`, `secret`, `token`, `api-key`,
  `access-token`, `client-secret` e equivalentes;
- arquivos com extensões ou nomes de risco:

  ```text
  *.pfx
  *.p12
  *.jks
  *.keystore
  *.pem
  *.key
  id_rsa
  id_ed25519
  ```

- arquivos `.env` que não sejam exemplos públicos;
- defaults literais sensíveis nos arquivos Spring saneados pela S02.

Não imprima valores. Para achados, registre somente:

```text
path
linha, quando textual
nome da propriedade ou categoria
classificacao
acao
```

Documentação que menciona nomes de variáveis, padrões fictícios ou valores
vazios não é automaticamente um segredo. Classifique falsos positivos.

Se um valor potencialmente funcional entrar no índice temporário:

- não realize commit ou push;
- não revele o valor;
- corrija apenas se estiver dentro do escopo autorizado;
- caso contrário, registre bloqueio.

### 9. Arquivos grandes e especiais

Inventarie sem ler conteúdo integral:

- todos os candidatos acima de 5 MB;
- todos os candidatos acima de 100 MB;
- os dois `pub_interior.glb`;
- o HPROF real;
- certificados/chaves e uploads encontrados apenas por pathname, extensão,
  tamanho, modo e estado de ignore.

Critérios:

- nenhum candidato acima de 100 MB é aceitável;
- o HPROF deve permanecer fisicamente intacto e fora do índice;
- `backend/uploads/**`, inclusive qualquer PFX, deve permanecer fora do índice;
- os dois GLBs conhecidos podem permanecer candidatos por estarem abaixo do
  limite do GitHub, mas a duplicação e o custo aproximado devem ser
  explicitamente registrados como decisão pendente;
- não instalar nem configurar Git LFS.

### 10. Gate dos workflows

Liste todos os arquivos candidatos sob `.github/workflows/` e classifique seu
gatilho e efeito de alto nível, sem executar workflows.

Registre expressamente:

- os workflows existentes ainda não representam a arquitetura aprovada;
- qualquer workflow que faça deploy automático por push em `main`, use
  `latest`, conecte como `root` ou não execute os testes necessários é gate
  para o primeiro push;
- a S03 não corrige esse gate;
- o primeiro push não deve ocorrer até uma slice posterior neutralizar ou
  substituir esses workflows.

Workflows encontrados apenas dentro de dependências ou caches ignorados não
devem ser confundidos com workflows operacionais do monorepo.

### 11. Validacao final Git

Registrar:

```bash
git rev-parse --show-toplevel
git symbolic-ref --short HEAD
git remote get-url origin
git status --short
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
find . -name .git \
  -not -path './.git' \
  -not -path '*/node_modules/*' \
  -not -path '*/.ai-workflow/*' \
  -print
```

Resultados obrigatórios:

- toplevel exato `/home/gregorio/git/baronesa/emporio`;
- branch `main`;
- `origin` exato;
- nenhum arquivo no índice real;
- nenhum `.git` aninhado;
- `git rev-parse --verify HEAD` falhando por branch ainda sem commit;
- nenhuma tag local.

`git status --short` pode listar arquivos não rastreados. Isso é esperado. O
relatório deve resumir a saída sem transcrever paths sensíveis ignorados.

Pode executar, como diagnóstico opcional:

```bash
git ls-remote origin
```

Não altere nada em resposta a uma falha dessa verificação.

## Evidencia obrigatoria

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S03-fundacao-git-local-e-auditoria-primeiro-indice.report.md
```

O relatório deve conter:

1. metadados, data e CWD;
2. lista exata de arquivos alterados;
3. preflight anterior ao `git init`;
4. comandos de inicialização, códigos de saída e interpretação;
5. branch e remoto configurados;
6. comprovação de ausência de repositórios aninhados;
7. comprovação de que o índice real está vazio;
8. método e lifecycle do índice temporário;
9. contagem de candidatos total e por diretório;
10. matriz de paths ignorados e exemplos preservados;
11. auditoria de sensibilidade com valores redigidos;
12. inventário de arquivos acima de 5 MB e de 100 MB;
13. estado dos GLBs, HPROF, uploads e certificados/chaves;
14. inventário e gate dos workflows;
15. documentação criada/corrigida;
16. tabela de todos os comandos relevantes com CWD, código de saída,
    resultado e interpretação;
17. desvios, falsos positivos, itens não determinados e bloqueios;
18. declaração explícita de que não houve commit, tag, push, Git LFS,
    alteração de workflow, Docker, VPS ou rotação de credencial;
19. resposta final solicitada ao CLI.

Não inclua:

- valor de segredo;
- conteúdo de `.env.production`;
- hash de segredo usado como substituto de redação;
- conteúdo de HPROF, PFX, chave privada ou upload;
- saída integral desnecessária de `git status`;
- afirmação de acesso remoto quando não houver evidência.

## Criterios de aceite

- S01 e S02 permanecem respeitadas.
- A raiz é um repositório Git válido.
- A branch local é `main`.
- `origin` tem exatamente o remoto canônico.
- Não existe `.git` aninhado.
- Não existe commit, tag ou push criado pela slice.
- O índice real termina vazio.
- A simulação usa somente índice temporário e é removida ao final.
- Nenhum path sensível/gerado/ignorado entra no índice temporário.
- Os cinco `.env.example` obrigatórios permanecem candidatos.
- Não há valor potencialmente funcional não resolvido entre os candidatos.
- Não há candidato acima de 100 MB.
- O HPROF e `backend/uploads/**` permanecem ignorados e intactos.
- Os dois GLBs são classificados, sem remoção ou LFS.
- Os workflows incompatíveis são registrados como gate para o primeiro push.
- `README.md`, `.gitattributes` e onboarding são factuais e coerentes.
- O relatório contém evidência persistida suficiente para revisão.
- Somente arquivos e metadados autorizados foram alterados.

## Condicoes de bloqueio

Interrompa a parte afetada e registre `BLOCKED` se:

- surgir `.git` inesperado ou repositório aninhado antes da inicialização;
- `origin` preexistir com URL divergente;
- for impossível manter o índice real vazio;
- um segredo potencialmente funcional entrar como candidato e não puder ser
  saneado dentro do escopo;
- houver candidato acima de 100 MB sem regra legítima de ignore;
- a única forma de passar a auditoria for ocultar código ou ativo funcional;
- houver necessidade de modificar workflow, Docker ou outro arquivo fora do
  escopo;
- qualquer comando implicar commit, push, exclusão de arquivo local ou
  alteração externa.

Falha opcional de `git ls-remote origin` por rede/autenticação não é, sozinha,
bloqueio da fundação Git local. Registre-a como item não determinado.

## Resposta final esperada do CLI

Responder de forma concisa com:

- caminho absoluto do relatório;
- arquivos alterados;
- resultado do preflight e da inicialização Git;
- branch e remoto;
- confirmação de índice real vazio;
- resumo da auditoria do índice temporário;
- candidatos acima de 5 MB e acima de 100 MB;
- estado dos workflows como gate;
- bloqueios e itens não determinados;
- confirmação de que não houve commit, tag ou push;
- estado `IN_PROGRESS`, aguardando revisão do orquestrador.

Não declarar `ACCEPTED`.
