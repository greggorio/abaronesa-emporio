# S02 — Saneamento pre-Git e protecao do primeiro commit

> **Estado:** `ACCEPTED` — revisão concluída em 28/07/2026  
> **Tipo:** implementacao de seguranca e documentacao  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencia:** S01 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Evidencia de entrada:** [S01-inventario-e-contratos-reais.report.md](./S01-inventario-e-contratos-reais.report.md)  
> **Relatorio de saida:** `S02-saneamento-pre-git-e-protecao-primeiro-commit.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro este contrato, a arquitetura e a revisao final da S01.

Esta slice prepara o workspace para uma futura inicializacao Git segura. Ela nao autoriza `git init` no workspace, commit, push, criacao de workflows, Docker ou acesso externo.

## Objetivo observavel

Ao final:

- nenhum valor literal sensivel conhecido permanecera como default nos arquivos Spring candidatos ao primeiro commit;
- os dois backends receberao valores sensiveis por variaveis de ambiente;
- existira um `.gitignore` raiz cobrindo segredos, builds, caches, uploads e artefatos locais conhecidos;
- arquivos `.env.example` continuarao elegiveis para versionamento e nao conterao segredos;
- o arquivo local `ops/env/.env.production` permanecera no workspace, ignorado e com permissao `0600`;
- existira documentacao operacional para iniciar os backends localmente sem reintroduzir segredos no codigo;
- o workspace continuara sem `.git`;
- nenhuma credencial sera rotacionada, validada externamente ou publicada nesta slice.

## Decisoes fixas desta slice

- O primeiro `git init` ocorrera somente em uma slice posterior.
- Valores reais nao serao movidos para outro arquivo rastreavel.
- `integration.system-token-secret` sera fornecido por `INTEGRATION_SYSTEM_TOKEN_SECRET`.
- Backend ERP e website backend deverao usar exatamente o mesmo `INTEGRATION_SYSTEM_TOKEN_SECRET`.
- Credenciais Google e Uber serao fornecidas por ambiente e nao terao default literal.
- `ESPRESSO_SYNC_API_KEY` e `WEBSITE_ERP_SYNC_KEY` nao terao default literal.
- Senha de banco do profile `dev` sera fornecida por ambiente, sem literal candidato ao primeiro commit.
- Exemplos terao valores vazios ou publicamente seguros; nunca tokens com aparencia funcional.
- Arquivos locais existentes nao serao apagados.
- Rotacao de Google, Uber, token de integracao e demais credenciais sera registrada como gate externo posterior.

## Arquivos de leitura prioritarios

```text
backend/src/main/resources/application.properties
backend/src/main/resources/application-dev.properties
backend/src/main/resources/application-prod.properties
backend/src/test/resources/application-test.properties
website_back/src/main/resources/application.properties
backend/.env.example
ops/env/.env.example
ops/env/.env.production
frontend/.env
website_front/.env
website_front/.env.example
backend/.gitignore
frontend/.gitignore
website_back/.gitignore
website_front/.gitignore
whatsapp_service/.gitignore
docs/development/README.md
docs/development/ONBOARDING_MINIMO.md
```

Ao ler arquivos com valores sensiveis, nao use comandos que transcrevam seus conteudos integralmente. Extraia nomes, linhas e classificacoes com valores redigidos.

## Escopo de escrita

Arquivos que podem ser criados ou alterados:

```text
.gitignore
.env.example
backend/.env.example
website_back/.env.example
ops/env/.env.example
backend/src/main/resources/application.properties
backend/src/main/resources/application-dev.properties
backend/src/main/resources/application-prod.properties
website_back/src/main/resources/application.properties
docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md
docs/development/README.md
docs/infrastructure/deployment/implementation/slices/S02-saneamento-pre-git-e-protecao-primeiro-commit.report.md
```

Alteracao de metadado autorizada:

```text
chmod 0600 ops/env/.env.production
```

Ferramentas de build podem atualizar artefatos gerados dentro de `target/`. Isso nao autoriza mudancas deliberadas em outros arquivos-fonte.

Qualquer necessidade de alterar arquivo fora da lista deve ser registrada no relatorio e devolvida ao orquestrador. Nao expanda o escopo por conta propria.

## Fora de escopo

- executar `git init` dentro do workspace, `git add`, `commit`, `tag` ou `push`;
- configurar `origin`;
- criar, excluir ou alterar workflows;
- criar Dockerfiles ou Compose;
- corrigir portas, entrypoints, `JAVA_OPTS`, health checks ou volumes;
- corrigir `VITE_VILLA_API_URL`, `ERP_API_BASE_URL` ou outras divergencias nao sensiveis;
- criar `release_control`;
- criar `components.yml` ou manifesto de release;
- apagar ou mover `ops/env/.env.production`;
- modificar os valores reais dentro de `.env.production`;
- copiar valores reais para `.env.local`;
- rotacionar credenciais no Google, Uber, GitHub ou qualquer sistema externo;
- testar validade das credenciais;
- acessar GitHub, GHCR, DNS ou VPS;
- instalar ferramentas de secret scanning;
- alterar migrations ou bancos;
- iniciar containers.

## Implementacao obrigatoria

### 1. `.gitignore` raiz

Criar `.gitignore` na raiz com cobertura comprovada para:

```text
.env
.env.*
**/.env
**/.env.*
ops/env/.env.production
**/target/
**/node_modules/
**/.quasar/
**/.gradle/
backend/uploads/
backend/outputs/
backend/nfe/xmls/
backend/relatorio_*
quality/**/.ai-workflow/
.ai-workflow/
**/.ai-workflow/
.claude/
**/.claude/
.opencode/
**/.opencode/
opencode.json
```

Preservar explicitamente exemplos:

```text
!.env.example
!**/.env.example
!**/.env.*.example
```

Antes de adicionar uma regra ampla para certificados, chaves, PDFs ou XMLs, verificar se o repositorio possui fixtures, schemas ou recursos publicos legitimos. Nao ocultar silenciosamente arquivos de produto necessarios.

Organizar o arquivo por categorias e documentar excecoes.

### 2. Propriedades Spring sensiveis

Nos arquivos Spring autorizados, remover somente os defaults literais sensiveis identificados.

Contrato esperado:

```properties
integration.system-token-secret=${INTEGRATION_SYSTEM_TOKEN_SECRET}
```

Esse nome tambem substituira a divergencia atual `INTEGRATION_TOKEN_SECRET` do profile `prod`.

Para integracoes opcionais que precisam permitir startup sem credencial:

```properties
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID:}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET:}
uber.client-id=${UBER_CLIENT_ID:}
uber.client-secret=${UBER_CLIENT_SECRET:}
uber.customer-id=${UBER_CUSTOMER_ID:}
uber.access-token=${UBER_ACCESS_TOKEN:}
espresso.sync.api-key=${ESPRESSO_SYNC_API_KEY:}
website.sync.api-key=${WEBSITE_ERP_SYNC_KEY:}
```

Aplicar propriedades somente ao componente que realmente as consome. Preservar nomes e semantica existentes fora do saneamento.

No profile `dev` do backend, remover tambem o literal da senha de banco:

```properties
spring.datasource.password=${DB_PASSWORD}
```

Comentarios com senha literal deverao ser removidos ou convertidos em exemplo de variavel, mesmo quando o valor parecer um default local conhecido.

Nao substituir segredo por `change-me`, UUID fixo, token de teste ou outro default rastreavel.

### 3. Exemplos de ambiente

Criar `.env.example` na raiz como referencia comum do desenvolvimento local.

Ele deve:

- listar `INTEGRATION_SYSTEM_TOKEN_SECRET` sem valor;
- listar credenciais Google e Uber sem valor;
- listar `DB_PASSWORD`, `ESPRESSO_SYNC_API_KEY` e `WEBSITE_ERP_SYNC_KEY` sem valor;
- indicar por comentario quais campos sao obrigatorios ou opcionais;
- explicar que os dois backends compartilham o token de integracao;
- nao duplicar URLs e configuracoes nao relacionadas sem necessidade;
- nao conter valor com aparencia de segredo.

Atualizar `backend/.env.example`, criar `website_back/.env.example` e alinhar `ops/env/.env.example` somente no necessario para que os nomes sensiveis estejam completos e consistentes.

Exemplos podem conter:

- strings vazias;
- URLs localhost publicas;
- nomes de banco de exemplo;
- comentarios explicativos.

Exemplos nao podem conter:

- senhas funcionais;
- token fixo reutilizavel;
- client secret;
- access token;
- chave privada;
- valor copiado dos arquivos atuais.

### 4. Protecao do arquivo de producao local

Executar:

```bash
chmod 0600 ops/env/.env.production
```

Nao abrir, imprimir, mover, renomear ou alterar seu conteudo.

Registrar permissao antes e depois.

### 5. Documentacao de usabilidade

Criar:

```text
docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md
```

O documento deve explicar:

- por que os segredos nao permanecem mais em `application.properties`;
- como criar `.env.local` a partir do exemplo raiz;
- como gerar um token local de integracao, sem fornecer token fixo;
- que o mesmo token deve ser carregado nos dois backends;
- como carregar as variaveis na sessao do shell;
- como configurar as mesmas variaveis na IDE;
- como executar `mvn spring-boot:run` em cada backend;
- quais integracoes sao opcionais;
- que `.env.local` e `.env.production` nunca devem ser commitidos;
- que os valores anteriores ainda exigem rotacao externa;
- que o procedimento local nao e o procedimento de producao.

Exemplo permitido de geracao:

```bash
openssl rand -hex 32
```

O valor gerado nao deve ser incluido na documentacao ou no relatorio.

Exemplo permitido de carregamento:

```bash
set -a
source .env.local
set +a
```

Atualizar `docs/development/README.md` com link e descricao do novo guia.

### 6. Registro de rotacao pendente

No relatorio, sem valores, registrar como `ROTATION_REQUIRED`:

```text
INTEGRATION_SYSTEM_TOKEN_SECRET
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
UBER_CLIENT_ID
UBER_CLIENT_SECRET
UBER_CUSTOMER_ID
UBER_ACCESS_TOKEN
ESPRESSO_SYNC_API_KEY
WEBSITE_ERP_SYNC_KEY
DB_PASSWORD
demais credenciais presentes em ops/env/.env.production
```

Nao afirmar que a rotacao foi realizada.

## Validacoes obrigatorias

### 1. Ausencia de Git

```bash
test ! -e .git
git rev-parse --show-toplevel
```

Esperado:

- primeiro comando: `0`;
- segundo comando: `128`.

### 2. Regras de ignore

Como `git check-ignore` exige um repositorio, validar em um diretorio temporario criado por `mktemp -d`:

1. criar o diretorio temporario;
2. executar `git init` somente dentro dele;
3. copiar o `.gitignore` para o temporario;
4. criar paths ficticios correspondentes a lista de teste;
5. executar `git check-ignore -v`;
6. remover somente o diretorio temporario criado pela validacao.

E proibido executar `git init` em `/home/gregorio/git/baronesa/emporio` ou em qualquer subdiretorio real do projeto.

Devem ser ignorados:

```text
.env.local
frontend/.env
website_front/.env
ops/env/.env.production
backend/target/exemplo
frontend/node_modules/exemplo
backend/uploads/exemplo
.ai-workflow/exemplo
```

Nao devem ser ignorados:

```text
.env.example
backend/.env.example
website_back/.env.example
ops/env/.env.example
website_front/.env.example
```

Registrar comando, codigo de saida e regra responsavel.

### 3. Classificacao segura das propriedades

Executar uma verificacao que imprima somente:

```text
arquivo:linha:chave=ENV_WITHOUT_DEFAULT
arquivo:linha:chave=ENV_WITH_EMPTY_DEFAULT
```

Falhar se qualquer chave sensivel conhecida resultar em:

```text
LITERAL_VALUE
ENV_WITH_LITERAL_DEFAULT
```

Nunca imprimir o valor.

Incluir na verificacao:

```text
backend/src/main/resources/application.properties
backend/src/main/resources/application-dev.properties
backend/src/main/resources/application-prod.properties
website_back/src/main/resources/application.properties
```

E as chaves:

```text
spring.datasource.password
integration.system-token-secret
spring.security.oauth2.client.registration.google.client-id
spring.security.oauth2.client.registration.google.client-secret
uber.client-id
uber.client-secret
uber.customer-id
uber.access-token
espresso.sync.api-key
website.sync.api-key
```

Uma credencial opcional pode resultar em `ENV_WITH_EMPTY_DEFAULT`. O token de integracao compartilhado e a senha de banco local devem resultar em `ENV_WITHOUT_DEFAULT`.

### 4. Exemplos sem segredos

Verificar:

- todos os nomes obrigatorios presentes;
- campos sensiveis vazios;
- nenhum valor original copiado;
- exemplos nao ignorados;
- arquivo de producao ignorado.

Nao compare imprimindo valores. Use hash, classificacao ou verificacao booleana quando necessario.

### 5. Permissoes

```bash
stat -c '%a %n' ops/env/.env.production
```

Esperado:

```text
600 ops/env/.env.production
```

### 6. Testes dos backends

Executar os testes existentes dos dois backends com valor efemero apenas no ambiente do processo:

```bash
INTEGRATION_SYSTEM_TOKEN_SECRET=s02-test-only-not-a-real-secret mvn -B test
```

Executar separadamente em:

```text
backend/
website_back/
```

Nao persistir o valor de teste em arquivo.

Se um teste exigir integracao externa ou banco indisponivel:

- registrar comando e codigo de saida;
- distinguir falha causada pela slice de falha preexistente/ambiental;
- nao desabilitar teste;
- nao alterar codigo fora do escopo para obter verde artificial.

### 7. Revisao documental

Confirmar:

- guia criado e indexado;
- comandos coerentes com o desenvolvimento manual aprovado;
- nenhum segredo presente nos exemplos ou na documentacao;
- diferenca entre desenvolvimento e producao explicita.

## Evidencia obrigatoria

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S02-saneamento-pre-git-e-protecao-primeiro-commit.report.md
```

O relatorio deve conter:

1. metadados, CWD e estado;
2. resumo do saneamento;
3. arquivos alterados;
4. tabela das propriedades antes/depois por classificacao, sem valores;
5. matriz dos arquivos ignorados e preservados;
6. permissao antes/depois de `.env.production`;
7. documentacao criada;
8. comandos, codigos de saida e resultados;
9. testes de backend separados;
10. lista `ROTATION_REQUIRED`;
11. desvios e itens nao determinados;
12. declaracao do que nao foi executado.

O relatorio deve iniciar com:

```markdown
# S02 — Relatorio de saneamento pre-Git

> **Estado informado pelo executor:** `IN_PROGRESS`
> **Revisao do orquestrador:** pendente
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Data:** `<data da execucao>`
```

O executor nao pode declarar `ACCEPTED`.

## Criterios de aceite

- `.gitignore` raiz cobre todos os caminhos obrigatorios;
- arquivos de exemplo permanecem elegiveis para versionamento;
- propriedades sensiveis conhecidas nao possuem literal nem default literal;
- os dois backends usam o mesmo nome de variavel para o token compartilhado;
- profiles `dev` e `prod` nao reintroduzem os literais removidos da configuracao base;
- chaves de sincronizacao nao possuem defaults literais;
- nenhum valor real aparece no diff, relatorio ou documentacao;
- `.env.production` permanece inalterado em conteudo, ignorado e com modo `0600`;
- guia local existe, esta indexado e preserva o fluxo manual do usuario;
- testes dos dois backends foram executados e interpretados;
- nenhuma falha causada pela slice permanece sem tratamento;
- rotacoes externas estao explicitamente pendentes;
- workspace continua sem `.git`;
- somente arquivos autorizados foram alterados;
- nenhum workflow, Dockerfile, Compose, servidor ou servico externo foi modificado.

## Condicoes de bloqueio

Bloqueie e devolva ao orquestrador se:

- um valor sensivel precisar permanecer literal para a aplicacao iniciar;
- existir outro segredo literal fora dos arquivos autorizados;
- a documentacao atual exigir um fluxo local incompatível com variaveis de ambiente;
- `.env.production` mudar de conteudo;
- um teste comprovar regressao causada pelo saneamento;
- for necessario inicializar Git para validar as regras.

Nao bloqueie por rotacao externa pendente: registre-a como gate antes do primeiro uso real/publicacao.

## Resposta final esperada do CLI

Ao concluir, responda somente com:

- caminho absoluto do relatorio;
- arquivos alterados;
- resultado das validacoes de ignore e propriedades;
- resultado separado dos testes de cada backend;
- lista de rotacoes pendentes, sem valores;
- itens bloqueados ou nao determinados;
- estado `pronto para revisao do orquestrador`.
