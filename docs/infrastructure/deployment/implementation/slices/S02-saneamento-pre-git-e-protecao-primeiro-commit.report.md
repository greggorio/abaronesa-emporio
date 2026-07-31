# S02 — Relatorio de saneamento pre-Git

> **Estado informado pelo executor:** `IN_PROGRESS`
> **Revisao do orquestrador:** correção requerida em 28/07/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Data:** `2026-07-28`

## 1. Metadados

| Campo | Valor |
|---|---|
| Contrato | `S02-saneamento-pre-git-e-protecao-primeiro-commit.task.md` |
| Dependencia | S01 `ACCEPTED`, conforme revisao final de 28/07/2026 |
| Executor | CLI |
| Estado | `IN_PROGRESS` |

## 2. Resumo do saneamento

- criado `.gitignore` raiz para segredos, builds, dependencias, caches e
  artefatos locais obrigatorios;
- mantidas excecoes explicitas para todos os formatos `.env.example`;
- removidos literais e defaults literais das propriedades sensiveis
  autorizadas;
- unificado o token dos dois backends em
  `INTEGRATION_SYSTEM_TOKEN_SECRET`, sem default;
- configuradas como opcionais, com default vazio, as credenciais Google,
  Uber e as chaves de sincronizacao;
- configurada `DB_PASSWORD` sem default nos backends;
- criados e alinhados exemplos sem valores sensiveis;
- criado e indexado o guia de configuracao local;
- alterado somente o modo de `ops/env/.env.production` para `0600`.

## 3. Arquivos alterados

Arquivos criados:

- `.gitignore`
- `.env.example`
- `website_back/.env.example`
- `docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md`
- este relatorio

Arquivos alterados:

- `backend/.env.example`
- `ops/env/.env.example`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-dev.properties`
- `backend/src/main/resources/application-prod.properties`
- `website_back/src/main/resources/application.properties`
- `docs/development/README.md`
- `ops/env/.env.production`: somente metadado de permissao

No ciclo corretivo 1, somente `.gitignore` e este relatorio foram alterados.
O arquivo `website_front/android/java_pid2033186.hprof` foi apenas identificado
por pathname e tamanho no inventario exigido; nao foi aberto, movido, apagado
ou alterado.

Os testes atualizaram artefatos gerados sob `backend/target/` e
`website_back/target/`, conforme autorizado pelo contrato.

## 4. Propriedades antes e depois

Os valores anteriores nao foram transcritos. As classificacoes sao:
`LITERAL_VALUE`, `ENV_WITH_LITERAL_DEFAULT`, `ENV_WITHOUT_DEFAULT` e
`ENV_WITH_EMPTY_DEFAULT`.

| Arquivo/profile | Propriedade | Antes | Depois |
|---|---|---|---|
| backend base | `integration.system-token-secret` | `LITERAL_VALUE` | `ENV_WITHOUT_DEFAULT` |
| backend base | Google client id/secret | `LITERAL_VALUE` | `ENV_WITH_EMPTY_DEFAULT` |
| backend base | quatro propriedades Uber | `ENV_WITH_LITERAL_DEFAULT` | `ENV_WITH_EMPTY_DEFAULT` |
| backend base | `espresso.sync.api-key` | `ENV_WITH_LITERAL_DEFAULT` | `ENV_WITH_EMPTY_DEFAULT` |
| backend dev | `spring.datasource.password` | `LITERAL_VALUE` | `ENV_WITHOUT_DEFAULT` |
| backend dev | `integration.system-token-secret` | `LITERAL_VALUE` | `ENV_WITHOUT_DEFAULT` |
| backend dev | Google client id/secret | `LITERAL_VALUE` | `ENV_WITH_EMPTY_DEFAULT` |
| backend prod | `integration.system-token-secret` | `ENV_WITH_LITERAL_DEFAULT`, nome divergente | `ENV_WITHOUT_DEFAULT`, `INTEGRATION_SYSTEM_TOKEN_SECRET` |
| backend prod | Google client id/secret | `ENV_WITH_EMPTY_DEFAULT` | `ENV_WITH_EMPTY_DEFAULT` |
| website backend | `spring.datasource.password` | `ENV_WITH_EMPTY_DEFAULT` | `ENV_WITHOUT_DEFAULT` |
| website backend | `integration.system-token-secret` | `LITERAL_VALUE` | `ENV_WITHOUT_DEFAULT` |
| website backend | quatro propriedades Uber | `ENV_WITH_LITERAL_DEFAULT` | `ENV_WITH_EMPTY_DEFAULT` |
| website backend | `website.sync.api-key` | `ENV_WITH_LITERAL_DEFAULT` | `ENV_WITH_EMPTY_DEFAULT` |

Comentarios contendo senha literal em propriedades foram convertidos para
referencias por variavel de ambiente. A verificacao final das 23 ocorrencias
classificadas terminou com codigo `0`, sem `LITERAL_VALUE` ou
`ENV_WITH_LITERAL_DEFAULT`.

## 5. Matriz de ignore

Validacao executada em diretorio criado por `mktemp -d`, com `git init`
exclusivamente nesse diretorio temporario. O diretorio foi removido ao final.

| Path ficticio | Resultado | Regra |
|---|---|---|
| `.env.local` | ignorado | `**/.env.*` |
| `frontend/.env` | ignorado | `**/.env` |
| `website_front/.env` | ignorado | `**/.env` |
| `ops/env/.env.production` | ignorado | regra especifica |
| `backend/target/exemplo` | ignorado | `**/target/` |
| `frontend/node_modules/exemplo` | ignorado | `**/node_modules/` |
| `backend/uploads/exemplo` | ignorado | `backend/uploads/` |
| `.ai-workflow/exemplo` | ignorado | `**/.ai-workflow/` |
| `website_front/android/java_pid2033186.hprof` | ignorado | `**/*.hprof` |
| `diagnostics/hs_err_pid123.log` | ignorado | `**/hs_err_pid*.log` |
| `diagnostics/replay_pid123.log` | ignorado | `**/replay_pid*.log` |
| `.env.example` | preservado | excecao de exemplo |
| `backend/.env.example` | preservado | excecao de exemplo |
| `website_back/.env.example` | preservado | excecao de exemplo |
| `ops/env/.env.example` | preservado | excecao de exemplo |
| `website_front/.env.example` | preservado | excecao de exemplo |

Todos os `git check-ignore` de paths ignorados retornaram `0`. Todos os paths
preservados retornaram `1`, como esperado. A limpeza do temporario retornou
`0`.

Nao foi criada regra global para certificados, PDFs ou XMLs porque o projeto
possui schemas e recursos publicos legitimos.

## 6. Arquivos de ambiente e permissao

Os campos sensiveis dos quatro exemplos validados estao vazios. O exemplo raiz
contem todos os dez nomes exigidos e explica o compartilhamento do token. Os
exemplos por componente contem somente as variaveis consumidas pelo respectivo
backend; `ops/env/.env.example` contem o conjunto completo.

`ops/env/.env.production`:

| Momento | Modo | Tamanho | Identidade de conteudo |
|---|---:|---:|---|
| antes | `0644` | 770 bytes | hash opaco registrado |
| depois | `0600` | 770 bytes | mesmo hash opaco |

O arquivo de producao nao foi aberto, impresso, movido, renomeado nem teve
conteudo alterado.

## 7. Documentacao

Criado `docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md`, com:

- copia de `.env.example` para `.env.local`;
- geracao local de token sem token fixo documentado;
- compartilhamento do token entre os dois backends;
- carregamento no shell e configuracao equivalente na IDE;
- execucao manual de ambos os backends com Maven;
- integracoes obrigatorias e opcionais;
- proibicao de commit de `.env.local` e `.env.production`;
- rotacao externa pendente;
- distincao explicita entre desenvolvimento e producao.

O guia foi indexado em `docs/development/README.md`.

## 8. Comandos, codigos e resultados

Valores efemeros usados nos testes sao omitidos desta evidencia.

| Comando | CWD | Codigo | Resultado |
|---|---|---:|---|
| `test ! -e .git` | raiz | 0 | `.git` ausente |
| `git rev-parse --show-toplevel` | raiz | 128 | workspace nao e repositorio |
| classificador seguro `awk` das propriedades | raiz | 0 | somente env sem default ou com default vazio |
| classificador seguro dos exemplos | raiz | 0 | campos sensiveis vazios |
| `git init -q` | diretorio temporario | 0 | usado somente para `check-ignore` |
| `git check-ignore -v <path>` | diretorio temporario | 0/1 esperado | matriz integral aprovada |
| limpeza validada do diretorio temporario | `/tmp` | 0 | temporario removido |
| `chmod 0600 ops/env/.env.production` | raiz | 0 | modo aplicado |
| `stat -c '%a %n' ops/env/.env.production` | raiz | 0 | `600 ops/env/.env.production` |
| hash opaco antes/depois | raiz | 0 | identidade inalterada |
| verificacao de guia e indice | raiz | 0 | guia criado, indexado e com fronteira dev/prod |
| inventario `find` de arquivos maiores que 100 MB, excluindo diretorios gerados conhecidos | raiz | 0 | um arquivo encontrado: HPROF de 297271296 bytes |
| `git check-ignore -v website_front/android/java_pid2033186.hprof` | diretorio temporario | 0 | ignorado por `**/*.hprof` |
| `git check-ignore -v diagnostics/hs_err_pid123.log` | diretorio temporario | 0 | ignorado por `**/hs_err_pid*.log` |
| `git check-ignore -v diagnostics/replay_pid123.log` | diretorio temporario | 0 | ignorado por `**/replay_pid*.log` |

## 9. Testes dos backends

### Backend ERP

Comando contratado, com valor efemero apenas no ambiente do processo:

```text
INTEGRATION_SYSTEM_TOKEN_SECRET=<efemero-curto-do-contrato> mvn -B test
```

Resultado: codigo `1`; 27 testes executados, 0 failures e 27 errors. A causa
raiz foi `WeakKeyException`: o valor efemero prescrito tem 31 bytes e o
`JwtTokenProvider` exige no minimo 32 bytes. O contexto falhou antes das
assercoes; a falha nao foi causada pela remocao de defaults.

Validacao causal adicional, sem persistir valor:

```text
INTEGRATION_SYSTEM_TOKEN_SECRET=<efemero-com-tamanho-valido> mvn -B test
```

Resultado: codigo `0`; 27 testes, 0 failures, 0 errors, 0 skipped; `BUILD
SUCCESS`.

### Website backend

```text
INTEGRATION_SYSTEM_TOKEN_SECRET=<efemero-curto-do-contrato> mvn -B test
```

Resultado: codigo `0`; 47 testes, 0 failures, 0 errors, 0 skipped; `BUILD
SUCCESS`.

Conclusao: nenhum teste demonstra regressao causada pela S02. O primeiro
resultado do ERP documenta uma incompatibilidade no tamanho do valor efemero
fornecido pelo proprio contrato, comprovada pela repeticao verde com valor
compativel.

## 10. `ROTATION_REQUIRED`

Sem afirmar que qualquer rotacao foi realizada:

- `INTEGRATION_SYSTEM_TOKEN_SECRET`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `UBER_CLIENT_ID`
- `UBER_CLIENT_SECRET`
- `UBER_CUSTOMER_ID`
- `UBER_ACCESS_TOKEN`
- `ESPRESSO_SYNC_API_KEY`
- `WEBSITE_ERP_SYNC_KEY`
- `DB_PASSWORD`
- demais credenciais presentes em `ops/env/.env.production`

## 11. Desvios e itens nao determinados

- Desvio de validacao: foi necessaria uma segunda execucao do backend ERP
  porque o valor efemero textual fornecido no contrato nao atende ao tamanho
  minimo exigido pela aplicacao. Nenhum arquivo-fonte foi alterado para obter
  o resultado verde.
- A validade externa e a rotacao das credenciais permanecem nao determinadas e
  fora desta slice.
- A estrategia de segredos de producao permanece para slice posterior.
- Nao ha item bloqueado: nenhuma credencial precisa permanecer literal, o
  conteudo de producao permaneceu inalterado e a repeticao da suite ERP
  descartou regressao do saneamento.

## 12. Declaracao do que nao foi executado

Nao foram executados no workspace: `git init`, `git add`, commit, tag, push,
configuracao de `origin`, criacao ou alteracao de workflows, Dockerfiles,
Compose, containers, `release_control`, manifestos de release, migrations ou
bancos deliberadamente, acesso a GitHub, GHCR, DNS, VPS ou provedores
externos, validacao ou rotacao de credenciais.

O unico `git init` ocorreu no diretorio temporario exigido para validar o
`.gitignore`. Nenhum valor real foi movido para arquivo rastreavel.

---

> **Estado final do executor:** `IN_PROGRESS` — pronto para revisao do
> orquestrador. O executor nao declara `ACCEPTED`.

---

## 13. Revisao do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS` — relatório ainda não aceito  
> **Data:** `2026-07-28`

O saneamento das propriedades, os exemplos, a documentação, a permissão de
`.env.production` e as evidências dos testes atendem ao contrato.

Permanece uma falha bloqueante na proteção do primeiro commit:

```text
arquivo: website_front/android/java_pid2033186.hprof
tipo: Java HPROF dump
tamanho: 297271296 bytes
modo: 0644
estado atual: não coberto pelo .gitignore raiz
```

Esse arquivo:

- é artefato gerado de diagnóstico da JVM;
- supera 100 MB e impediria um push normal ao GitHub;
- pode conter dados da memória do processo;
- não deve ser lido, transcrito, commitado ou enviado ao relatório;
- não está autorizado para exclusão nesta slice.

### Correção requerida

1. Alterar somente:

   ```text
   .gitignore
   docs/infrastructure/deployment/implementation/slices/S02-saneamento-pre-git-e-protecao-primeiro-commit.report.md
   ```

2. Adicionar ao `.gitignore`, em categoria própria de dumps e diagnósticos:

   ```text
   **/*.hprof
   **/hs_err_pid*.log
   **/replay_pid*.log
   ```

3. Não apagar, mover, abrir ou alterar o HPROF.
4. Repetir a validação em repositório temporário e comprovar que
   `website_front/android/java_pid2033186.hprof` é ignorado.
5. Executar novamente o inventário de arquivos maiores que 100 MB fora dos
   diretórios gerados conhecidos.
6. Para cada arquivo maior que 100 MB:
   - comprovar que está ignorado; ou
   - registrar bloqueio sem adicionar ou remover o arquivo.
7. Atualizar matriz de ignore, lista de arquivos alterados e comandos.
8. Preservar esta seção e adicionar `Resposta às correções do ciclo 1`.
9. Manter `IN_PROGRESS` e devolver para nova revisão.

Os testes Maven não precisam ser repetidos, pois a correção autorizada afeta
somente regras de ignore e o relatório.

## Resposta às correções do ciclo 1

1. **Regras de dumps e diagnosticos:** adicionadas em categoria propria no
   `.gitignore`, exatamente como prescritas:

   ```text
   **/*.hprof
   **/hs_err_pid*.log
   **/replay_pid*.log
   ```

2. **Validacao de ignore repetida:** foi criado outro diretorio com
   `mktemp -d`, inicializado Git somente dentro dele e copiado o `.gitignore`.
   O path ficticio
   `website_front/android/java_pid2033186.hprof` retornou codigo `0` em
   `git check-ignore -v`, atribuido a `**/*.hprof`. Os paths ficticios
   `diagnostics/hs_err_pid123.log` e `diagnostics/replay_pid123.log` tambem
   retornaram `0` pelas respectivas regras. A matriz anterior foi repetida:
   todos os oito paths obrigatorios continuaram ignorados e os cinco exemplos
   continuaram preservados com codigo `1`. A remocao do diretorio temporario
   foi confirmada com codigo `0`.

3. **Inventario acima de 100 MB repetido:** o comando abaixo foi executado
   somente com classificacao por pathname e tamanho, excluindo `target`,
   `node_modules`, `.gradle`, `.quasar` e `.ai-workflow`:

   ```text
   find . -type f -size +100M <exclusoes de diretorios gerados> -printf '%P\t%s bytes\n'
   ```

   Resultado, codigo `0`: somente
   `website_front/android/java_pid2033186.hprof`, com 297271296 bytes. O path
   esta coberto por `**/*.hprof`, comprovado na validacao temporaria. Nao
   permanece arquivo maior que 100 MB inventariado sem regra de ignore.

4. **Preservacao do HPROF:** o arquivo real nao foi aberto, movido, apagado ou
   alterado. O inventario observou exclusivamente pathname e tamanho, conforme
   exigido pela revisao.

5. **Escopo e testes:** neste ciclo foram alterados somente `.gitignore` e este
   relatorio. Os testes Maven nao foram repetidos; os resultados documentados
   na Secao 9 permanecem a evidencia vigente.

6. **Estado:** a S02 permanece `IN_PROGRESS`, sem declaracao de `ACCEPTED`, e
   retorna para nova revisao do orquestrador.

---

## 14. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-28`

A S02 atende ao contrato após o ciclo corretivo.

Evidências aceitas:

- os valores sensíveis conhecidos foram removidos dos defaults rastreáveis e
  substituídos por variáveis de ambiente;
- o token de integração compartilhado permanece sob o mesmo contrato de
  configuração nos dois backends;
- os exemplos de ambiente permanecem elegíveis para versionamento e não
  contêm valores funcionais;
- `ops/env/.env.production` permanece local, ignorado, com conteúdo preservado
  e permissão `0600`;
- a documentação de configuração local foi criada e vinculada ao índice de
  desenvolvimento;
- o backend ERP passou em 27 de 27 testes com um token efêmero que satisfaz o
  mínimo de 32 bytes exigido pela aplicação;
- o website backend passou em 47 de 47 testes;
- a primeira execução do backend ERP, com o valor efêmero de 31 bytes prescrito
  no contrato, foi corretamente preservada como desvio do próprio contrato e
  investigada por repetição causal, sem alteração de código para obter o
  resultado verde;
- o `.gitignore` raiz cobre ambientes locais, builds, dependências, uploads,
  caches de ferramentas, HPROF e arquivos de crash/replay;
- o único arquivo acima de 100 MB encontrado fora das áreas geradas conhecidas
  é `website_front/android/java_pid2033186.hprof`, agora coberto por
  `**/*.hprof`;
- o HPROF real não foi aberto, movido, apagado ou alterado;
- o workspace continua sem `.git`, commit, tag, push ou acesso à VPS.

As rotações externas registradas como `ROTATION_REQUIRED` continuam pendentes.
Isso não invalida o saneamento do workspace, mas permanece como gate
operacional antes de reutilizar qualquer credencial em produção.

Os estados `IN_PROGRESS` declarados pelo executor e na revisão anterior
permanecem como histórico do ciclo. A autoridade final desta seção altera o
estado da S02 para `ACCEPTED`.

A S03 pode inicializar a fundação Git local e auditar o conteúdo candidato ao
primeiro índice, sem realizar commit ou push.
