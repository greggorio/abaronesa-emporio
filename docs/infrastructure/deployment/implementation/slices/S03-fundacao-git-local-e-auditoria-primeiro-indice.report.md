# S03 — Relatorio de fundacao Git local e auditoria do primeiro indice

> **Estado informado pelo executor:** `IN_PROGRESS`
> **Revisao do orquestrador:** pendente
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Data:** `2026-07-28`

## 1. Metadados e escopo

| Campo | Resultado |
|---|---|
| Contrato | `S03-fundacao-git-local-e-auditoria-primeiro-indice.task.md` |
| Dependencias | S01 e S02 com revisoes finais `ACCEPTED` |
| Branch local | `main` |
| Remoto | `git@github.com:greggorio/abaronesa-emporio.git` |
| Commit local | inexistente |
| Estado | `IN_PROGRESS` |

## 2. Arquivos e metadados alterados

Arquivos criados:

- `README.md`;
- `.gitattributes`;
- este relatorio.

Arquivo corrigido:

- `docs/development/ONBOARDING_MINIMO.md`.

Arquivo alinhado ao onboarding:

- `docs/development/README.md`: removida somente a lacuna que dizia faltar um
  guia local amplo.

Metadados internos criados:

- `.git/`, por `git init -b main` e configuracao local de `origin`.

Nao foi necessario alterar `.gitignore`. Nenhum outro arquivo-fonte,
workflow, artefato Docker ou dado local foi alterado.

## 3. Preflight anterior ao `git init`

| Verificacao | Codigo | Evidencia |
|---|---:|---|
| `pwd` | 0 | `/home/gregorio/git/baronesa/emporio` |
| `test ! -e .git` | 0 | `.git` ausente |
| busca de `.git` fora de dependencias/caches | 0 | nenhuma ocorrencia |
| `git rev-parse --show-toplevel` | 128 | ainda nao era repositorio |

Nao havia repositorio inesperado ou aninhado; a inicializacao foi liberada.

## 4. Inicializacao Git

| Comando | Codigo | Resultado |
|---|---:|---|
| `git init -b main` | 0 | repositorio vazio inicializado |
| `git remote add origin git@github.com:greggorio/abaronesa-emporio.git` | 0 | remoto canonico configurado |
| `git symbolic-ref --short HEAD` | 0 | `main` |
| `git remote get-url origin` | 0 | URL canonica exata |

Nao foram configurados `user.name`, `user.email` ou valores globais. A
existencia/autenticacao do remoto nao foi testada com `git ls-remote`; isso
permanece nao determinado e nao afeta a fundacao local.

## 5. Lifecycle do indice temporario

1. Foi criado um diretorio fora do workspace com `mktemp -d`.
2. Antes de qualquer `git add`, a lista de candidatos foi produzida com:

   ```text
   git ls-files --others --exclude-standard -z
   ```

3. Essa lista foi auditada por pathname, tamanho, nome de risco e conteudo
   textual redigido.
4. Como nao surgiu bloqueio de segredo ou tamanho, foi executado:

   ```text
   GIT_INDEX_FILE=<diretorio-temporario>/index git add -A
   ```

5. Todas as consultas de candidatos usaram o mesmo `GIT_INDEX_FILE`.
6. O diretorio temporario, sua lista e seu indice foram removidos; a
   confirmacao retornou codigo `0`.
7. `git ls-files --stage` sem `GIT_INDEX_FILE` confirmou zero entradas no
   indice real.

O `git add` temporario materializou objetos Git locais sem criar commit ou
alterar o indice real, comportamento previsto pelo contrato.

## 6. Inventario do indice temporario

Total final: **2113 arquivos candidatos**, incluindo este relatorio.

| Primeiro nivel | Quantidade |
|---|---:|
| raiz | 4 |
| `.github` | 2 |
| `backend` | 959 |
| `deploy` | 6 |
| `docs` | 191 |
| `frontend` | 235 |
| `nodes` | 2 |
| `ops` | 10 |
| `quality` | 40 |
| `tools` | 2 |
| `website_back` | 198 |
| `website_front` | 457 |
| `whatsapp_service` | 7 |

Extensoes binarias inventariadas:

| Extensao | Quantidade |
|---|---:|
| `glb` | 2 |
| `ico` | 4 |
| `jar` | 1 |
| `jpg` | 101 |
| `mp3` | 1 |
| `png` | 40 |
| `webp` | 31 |
| `zip` | 1 |

## 7. Matriz de exclusoes e exemplos

Representantes consultados com `git check-ignore --no-index -v` e no indice
temporario:

| Categoria/path | Estado fisico | Regra/estado | Indice temporario |
|---|---|---|---|
| `.env` | ausente | ignorado por `**/.env` | ausente |
| `.env.local` | ausente | ignorado por `**/.env.*` | ausente |
| `frontend/.env` | presente | ignorado por `**/.env` | ausente |
| `ops/env/.env.production` | presente | regra especifica | ausente |
| `backend/target/**` | presente | ignore do componente | ausente |
| `**/node_modules/**` | representante ausente | ignore raiz/componente | ausente |
| `**/.gradle/**` | representante ausente | ignore do componente | ausente |
| `frontend/.quasar/**` | presente | ignore do componente | ausente |
| `backend/uploads/**` | presente | ignore do componente | ausente |
| `backend/outputs/**` | presente | ignore raiz | ausente |
| `backend/nfe/xmls/**` | representante ausente | ignore raiz | ausente |
| `**/*.hprof` | HPROF presente | ignore do componente/raiz | ausente |
| `**/hs_err_pid*.log` | representante ausente | ignore raiz | ausente |
| `**/replay_pid*.log` | representante ausente | ignore raiz | ausente |
| `**/.ai-workflow/**` | presente | ignore raiz | ausente |
| `**/.claude/**` | presente | ignore raiz | ausente |
| `**/.opencode/**` | presente | ignore raiz | ausente |

Exemplos preservados:

| Path | `git check-ignore` | Indice temporario |
|---|---:|---|
| `.env.example` | 1, nao ignorado | presente |
| `backend/.env.example` | 1, nao ignorado | presente |
| `website_back/.env.example` | 1, nao ignorado | presente |
| `website_front/.env.example` | 1, nao ignorado | presente |
| `ops/env/.env.example` | 1, nao ignorado | presente |

## 8. Auditoria de sensibilidade

Auditoria executada somente sobre candidatos antes e depois da materializacao
do indice temporario.

| Verificacao | Resultado |
|---|---|
| cabecalhos de chave privada | nenhum |
| prefixos reconheciveis de tokens de provedores | nenhum |
| nomes/extensoes `pfx`, `p12`, `jks`, `keystore`, `pem`, `key`, `id_rsa`, `id_ed25519` | nenhum candidato |
| `.env` diferente de exemplo | nenhum candidato |
| propriedades Spring saneadas na S02 | somente `ENV_WITHOUT_DEFAULT` ou `ENV_WITH_EMPTY_DEFAULT` |
| assignment textual generico sensivel | falsos positivos classificados abaixo |

Falsos positivos textuais, sem transcrever valores:

| Paths/linhas | Categoria | Classificacao |
|---|---|---|
| `backend/scripts/import_produtos_villa.py:69,110,241,296` | `token` | variavel/runtime |
| `backend/src/main/java/com/baronesa/emporio/dto/deserializer/StringOrMapDeserializer.java:24` | `token` | nome de variavel |
| `backend/src/main/java/com/baronesa/emporio/service/OpenAiConfigService.java:123` | `apiKey` | valor obtido de configuracao |
| `backend/src/main/java/com/baronesa/emporio/service/SessaoMesaService.java:87` | `token` | valor gerado em runtime |
| `docs/architecture/VISAO_GERAL_INTEGRACOES.md:336,341` | password/access token | documentacao de contrato |
| `frontend/src/components/AuthenticationPage.vue:190,204,218,232` | password | estado/formulario |
| `frontend/src/components/configuracoes/MercadoPagoConfig.vue:350` | token | estado de formulario |
| `frontend/src/components/configuracoes/OpenAIConfig.vue:219,258,310,355` | apiKey | estado de formulario |
| `frontend/src/components/configuracoes/PagSeguroConfig.vue:328` | token | estado de formulario |
| `frontend/src/stores/userStore.js:58` | password | payload de autenticacao |
| `frontend/test_api_call.sh:13`, `frontend/test_import_preview.sh:16`, `quality/e2e/erp-backoffice/test-config.json:5` | password | credencial local de teste conhecida, nao credencial externa |
| `website_front/public/firebase-messaging-sw.js:7` e copia Android equivalente | apiKey | identificador publico de cliente Firebase |
| `website_front/scripts/prepare_android_from_theme.py:187` | token | placeholder de transformacao |
| `website_front/src/hooks/useAuth.ts:16,43,100,144,163,186,245,253` | token | tipo/estado/runtime |
| `website_front/src/hooks/useDeliveryCheckout.ts:432` | token | estado/runtime |
| `website_front/src/lib/firebaseConfig.ts:5` | apiKey | identificador publico de cliente Firebase |
| `website_front/src/services/clientesDashboardService.ts:36` e `notificationService.ts:131` | token | parametro/runtime |

Nenhum valor potencialmente funcional de credencial externa permaneceu sem
tratamento entre os candidatos.

## 9. Arquivos grandes e especiais

### Candidatos acima de 5 MB

| Path | Tamanho | Estado |
|---|---:|---|
| `website_front/public/assets/models/pub_interior.glb` | 23318828 bytes | candidato |
| `website_front/android/app/src/main/assets/public/assets/models/pub_interior.glb` | 23318828 bytes | candidato |

Os dois modelos possuem o mesmo nome e tamanho, em arvore web e copia Android,
com custo conjunto aproximado de 46,6 MB. Permanecem abaixo de 100 MB e podem
ser candidatos, mas deduplicacao e eventual estrategia de ativos sao decisao
posterior. Nenhum hash integral foi calculado e Git LFS nao foi configurado.

### Candidatos acima de 100 MB

Nenhum.

### HPROF, uploads e certificados

| Item | Metadados | Estado |
|---|---|---|
| `website_front/android/java_pid2033186.hprof` | 297271296 bytes, modo 0644 | intacto, ignorado, fora do indice |
| `backend/uploads/**` | 139 arquivos | ignorados, fora do indice |
| PFX sob `backend/uploads/certificados/` | 4016 bytes, modo 0644 | identificado somente por pathname/metadados, ignorado |

O HPROF, o PFX e os uploads nao foram abertos, movidos, alterados ou apagados.
Nao apareceu certificado/chave como candidato.

## 10. Workflows e gate do primeiro push

Workflows candidatos:

| Path | Descoberta no monorepo | Gatilho/efeito observado | Classificacao |
|---|---|---|---|
| `.github/workflows/main.yml` | operacional, na raiz canonica | push em `main/master`, build e push de imagens, sem suite de testes comprovada | gate |
| `.github/workflows/deploy.yml` | operacional, na raiz canonica | push em `main`, imagens `latest`, SSH como `root` e deploy automatico, sem testes necessarios | gate bloqueante |
| `backend/.github/workflows/backend.yml` | nao descoberto pelo GitHub Actions do monorepo | push em `main/master`, build e push da imagem do backend, sem suite de testes comprovada | legado/inerto; candidato a saneamento |
| `frontend/.github/workflows/frontend.yml` | nao descoberto pelo GitHub Actions do monorepo | push em `main/master`, build e push da imagem do frontend, sem suite de testes comprovada | legado/inerto; candidato a saneamento |

Os dois workflows operacionais da raiz nao representam a arquitetura
aprovada. A S03 nao os corrige. **O primeiro push nao deve ocorrer** ate uma
slice posterior neutraliza-los ou substitui-los.

Os dois workflows aninhados continuam candidatos ao primeiro commit, mas sao
artefatos legados e inertes enquanto `emporio/` for a raiz do monorepo: o
GitHub Actions somente descobre workflows em `.github/workflows/` na raiz.
Eles devem ser removidos ou saneados na proxima slice, sem serem tratados como
workflows ativos. Workflows sob caches ignorados tambem nao foram classificados
como operacionais.

## 11. Documentacao criada e corrigida

- `README.md` descreve factualmente o monorepo, os cinco componentes, comandos
  locais, documentos canonicos e o estado incremental de Docker/CI/CD;
- `.gitattributes` normaliza texto em LF e classifica ativos binarios, sem LFS
  e sem tratar certificados/chaves privadas como ativos normais;
- `ONBOARDING_MINIMO.md` agora usa Emporio A Baronesa,
  `~/git/baronesa/emporio`, os cinco componentes reais, comandos dos manifests,
  portas confirmadas e PostgreSQL sem senha literal;
- `docs/development/README.md` permanece indexando o onboarding e nao declara
  mais a lacuna que ele resolveu.

Nao restaram no onboarding referencias a Bakery, `~/git/bakery`,
`espresso_front` ou `espresso_back`.

## 12. Validacao Git final

| Comando | CWD | Codigo | Resultado/interpretacao |
|---|---|---:|---|
| `git rev-parse --show-toplevel` | raiz | 0 | toplevel exato |
| `git symbolic-ref --short HEAD` | raiz | 0 | `main` |
| `git remote get-url origin` | raiz | 0 | remoto canonico |
| `git status --short` | raiz | 0 | somente conteudo nao rastreado resumido; esperado |
| `git ls-files --stage` | raiz | 0 | saida vazia; indice real vazio |
| `git rev-parse --verify HEAD` | raiz | 128 | branch ainda sem commit |
| `git tag --list` | raiz | 0 | zero tags |
| busca de `.git` aninhado | raiz | 0 | zero ocorrencias |

Existem 16 entradas resumidas por `git status --short`, todas nao rastreadas.
Nenhum path sensivel ignorado foi transcrito por essa saida.

## 13. Comandos relevantes

| Comando resumido | Codigo | Evidencia |
|---|---:|---|
| preflight Git completo | 0/128 esperado | liberou inicializacao |
| `git init -b main` | 0 | repositorio local |
| `git remote add origin ...` | 0 | remoto local |
| `git ls-files --others --exclude-standard -z` | 0 | 2113 candidatos na auditoria final |
| inventario por `stat` acima de 5/100 MB | 0 | dois GLBs; nenhum acima de 100 MB |
| scans textuais redigidos sobre candidatos | 0 | nenhum segredo funcional |
| `GIT_INDEX_FILE=<temporario>/index git add -A` | 0 | indice isolado materializado |
| inventarios via indice temporario | 0 | contagens e matrizes registradas |
| `git check-ignore --no-index -v` | 0/1 esperado | exclusoes e exemplos comprovados |
| inventario por metadados de HPROF/uploads/certificados | 0 | itens ignorados e intactos |
| remocao validada do diretorio temporario | 0 | lista e indice temporarios removidos |
| validacao Git final | 0/128 esperado | branch/remoto corretos, sem HEAD/tags, indice vazio |

O `git add` foi executado em dois ciclos de auditoria e sempre com
`GIT_INDEX_FILE` apontando para caminho absoluto em `/tmp`. O segundo ciclo
incluiu este relatorio e substitui as contagens do primeiro. Nao houve
`git add` no indice real.

## 14. Desvios, itens nao determinados e bloqueios

Desvios:

- o `git add` temporario informou quatro normalizacoes futuras de CRLF para LF
  (tres schemas XSD e `website_front/android/gradlew.bat`); nenhum desses
  arquivos foi alterado pela S03;
- os matches genericos de sensibilidade foram revisados como falsos positivos
  e registrados sem valores.

Itens nao determinados:

- disponibilidade e autenticacao do remoto, pois `git ls-remote` opcional nao
  foi executado;
- decisao futura sobre duplicacao/armazenamento dos GLBs;
- rotacoes externas ja registradas pela S02.

Gate:

- os dois workflows operacionais da raiz, especialmente o deploy automatico
  por push, uso de `latest` e SSH como `root`, bloqueiam o primeiro push ate
  correcao em slice posterior;
- os workflows aninhados de backend e frontend sao legados/inertes, nao
  ampliam o gate operacional, mas permanecem candidatos ao saneamento da
  proxima slice.

Nao ha bloqueio para a fundacao Git local ou para a auditoria do indice
temporario.

## 15. Declaracao do que nao foi executado

Nao houve:

- commit, amend, merge, rebase, tag ou push;
- `git add` contra o indice real;
- Git LFS;
- alteracao de workflow, Dockerfile, Compose, Nginx, CI/CD ou producao;
- build, teste Maven/npm ou instalacao de dependencia;
- abertura, movimentacao, alteracao ou exclusao de HPROF, PFX, uploads ou
  segredos;
- rotacao ou validacao externa de credenciais;
- acesso a GitHub, GHCR, DNS ou VPS;
- alteracao do contrato S03 ou do tracker das slices.

## 16. Resposta final do executor

- fundacao Git local concluida em `main`, com `origin` canonico;
- indice real vazio, sem HEAD, tag, commit ou push;
- indice temporario auditado e removido;
- 2113 candidatos, dois GLBs acima de 5 MB, nenhum candidato acima de 100 MB;
- HPROF, uploads e PFX fora do indice;
- nenhum segredo funcional candidato;
- workflows atuais registrados como gate para o primeiro push;
- documentacao raiz e onboarding entregues.

> **Estado final do executor:** `IN_PROGRESS` — aguardando revisao do
> orquestrador. O executor nao declara `ACCEPTED`.

---

## 17. Revisao do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS` — relatório ainda não aceito  
> **Data:** `2026-07-28`

A fundação Git local, o isolamento do índice temporário, a proteção dos
arquivos sensíveis e grandes e a documentação principal atendem ao contrato.
Foram confirmados:

- toplevel `/home/gregorio/git/baronesa/emporio`;
- branch `main`;
- `origin` canônico;
- índice real vazio;
- ausência de `HEAD`, tags e repositórios Git aninhados;
- HPROF, uploads e PFX ignorados;
- dois GLBs candidatos acima de 5 MB e nenhum candidato acima de 100 MB;
- ausência de commit, tag e push.

Restam duas divergências restritas:

### 17.1 Inventario incompleto dos workflows candidatos

O relatório registra somente:

```text
.github/workflows/deploy.yml
.github/workflows/main.yml
```

Porém, o índice candidato também contém:

```text
backend/.github/workflows/backend.yml
frontend/.github/workflows/frontend.yml
```

Esses dois workflows aninhados são inertes para o GitHub Actions enquanto
`emporio/` for a raiz do monorepo, mas continuam sendo arquivos candidatos ao
primeiro commit. Devem ser inventariados como artefatos legados, diferenciados
dos dois workflows operacionais da raiz e incluídos na decisão de saneamento
da próxima slice. A omissão contradiz a fotografia aceita da S01 e deixa a
Seção 10 incompleta.

### 17.2 Classificacao incorreta de `*.gltf`

O `.gitattributes` criado contém:

```text
*.glb binary
*.gltf binary
```

`GLB` é o contêiner binário e deve permanecer classificado como binário.
`GLTF`, por sua vez, é um formato textual baseado em JSON e não deve ser
forçado como binário. Não há arquivo `.gltf` candidato atualmente, mas a regra
criaria comportamento incorreto em futuras inclusões.

### Correcao requerida

Alterar somente:

```text
.gitattributes
docs/infrastructure/deployment/implementation/slices/S03-fundacao-git-local-e-auditoria-primeiro-indice.report.md
```

Executar:

1. remover somente a regra `*.gltf binary`;
2. preservar `*.glb binary`;
3. validar com `git check-attr` em paths fictícios, sem criar arquivos no
   workspace, que:
   - `modelo.glb` recebe classificação binária;
   - `modelo.gltf` permanece sob detecção textual automática e não recebe
     classificação binária;
4. atualizar o inventário para os quatro workflows de projeto:
   - dois operacionais na raiz;
   - dois legados/inertes aninhados;
5. registrar gatilho e efeito de alto nível de cada um, sem executá-los;
6. preservar o gate bloqueante do primeiro push para os workflows da raiz;
7. registrar os aninhados como candidatos a remoção ou saneamento na próxima
   slice, sem classificá-los como workflows ativos do monorepo;
8. confirmar novamente, sem repetir a auditoria completa:
   - índice real vazio;
   - `HEAD` inexistente;
   - nenhuma tag;
   - nenhum commit ou push;
9. preservar esta seção e adicionar `Resposta às correções do ciclo 1`;
10. manter `IN_PROGRESS` e devolver para nova revisão.

Não repetir o índice temporário, os scans de segredos, os inventários de
arquivos grandes ou testes de aplicação. Não alterar README, onboarding,
`.gitignore`, workflows ou metadados Git.

## Resposta às correções do ciclo 1

1. **Classificacao GLTF corrigida:** foi removida somente a regra
   `*.gltf binary` de `.gitattributes`. A regra `*.glb binary` foi preservada.

2. **Atributos validados sem criar arquivos:** o comando
   `git check-attr -a -- modelo.glb modelo.gltf` retornou codigo `0` e
   comprovou:

   ```text
   modelo.glb: binary: set
   modelo.glb: diff: unset
   modelo.glb: merge: unset
   modelo.glb: text: unset
   modelo.gltf: text: auto
   ```

   Portanto, GLB permanece binario e GLTF volta a deteccao textual automatica,
   sem atributo binario.

3. **Quatro workflows inventariados:** a Secao 10 agora diferencia:

   - `.github/workflows/deploy.yml` e `.github/workflows/main.yml` como
     workflows operacionais descobertos na raiz do monorepo;
   - `backend/.github/workflows/backend.yml` e
     `frontend/.github/workflows/frontend.yml` como arquivos candidatos
     legados e inertes, pois workflows aninhados nao sao descobertos pelo
     GitHub Actions do repositorio raiz.

   Os dois aninhados possuem gatilho de push em `main/master` e efeito de
   build/push de sua imagem, sem suite de testes comprovada. Eles foram
   registrados como candidatos a remocao ou saneamento na proxima slice, nao
   como workflows ativos.

4. **Gate preservado:** o primeiro push continua bloqueado pelos workflows
   operacionais da raiz, especialmente pelo deploy automatico em push, uso de
   `latest`, SSH como `root` e ausencia dos testes exigidos. Os aninhados nao
   sao tratados como gate operacional ativo, mas continuam no escopo do
   saneamento futuro.

5. **Estado Git reconfirmado:** `git ls-files --stage` retornou zero entradas;
   `git rev-parse --verify HEAD` retornou `128`; `git tag --list` retornou zero
   tags; a busca de `.git` aninhado retornou zero; o reflog permanece vazio.
   Branch `main` e `origin` canonico foram preservados. Nao houve commit, tag
   ou push.

6. **Escopo do ciclo:** foram alterados somente `.gitattributes` e este
   relatorio. Nao foram repetidos indice temporario, scans de segredos,
   inventario de arquivos grandes, testes ou qualquer operacao externa.
   README, onboarding, `.gitignore`, workflows, task, tracker e metadados Git
   permaneceram inalterados.

7. **Estado:** a S03 permanece `IN_PROGRESS`, aguardando nova revisao do
   orquestrador; o executor nao declara `ACCEPTED`.

---

## 18. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-28`

A S03 atende ao contrato após o ciclo corretivo.

Evidências aceitas:

- a raiz é um repositório Git válido em `main`;
- `origin` aponta exatamente para
  `git@github.com:greggorio/abaronesa-emporio.git`;
- não há repositórios Git aninhados;
- o índice real está vazio;
- `HEAD` não existe, não há tags e o reflog está vazio;
- nenhum commit ou push foi executado;
- a pré-auditoria ocorreu antes da materialização do índice temporário;
- o índice temporário contabilizou 2113 candidatos e foi removido;
- nenhum candidato acima de 100 MB foi encontrado;
- os dois GLBs de 23318828 bytes foram mantidos como decisão futura, sem LFS;
- HPROF, uploads e PFX permaneceram ignorados e intactos;
- não foi encontrada credencial externa potencialmente funcional entre os
  candidatos;
- os cinco arquivos `.env.example` obrigatórios permaneceram candidatos;
- `README.md`, `.gitattributes` e o onboarding representam o monorepo atual;
- `*.glb` permanece binário e `*.gltf` voltou à detecção textual automática;
- os dois workflows da raiz foram classificados como operacionais e
  bloqueantes para o primeiro push;
- os dois workflows aninhados foram classificados como legados/inertes e
  candidatos ao saneamento seguinte.

Os estados `IN_PROGRESS` anteriores permanecem como histórico. A autoridade
final desta seção altera o estado da S03 para `ACCEPTED`.

A S04 deve neutralizar os quatro workflows protótipos antes do primeiro push.
Ela não deve antecipar a CI definitiva, a publicação de candidatos ou qualquer
integração com produção.
