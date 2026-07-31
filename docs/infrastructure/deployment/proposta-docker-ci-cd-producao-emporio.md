# Proposta de Docker e CI/CD para Producao do Emporio

> **Status:** arquitetura aprovada para planejamento da implementacao — nao implementada  
> **Data da analise:** 28/07/2026  
> **Ultima decisao incorporada:** 28/07/2026  
> **Destino previsto:** VPS `31.97.251.16`  
> **Dominios previstos:** `emporio.abaronesa.net.br` e `erp-emporio.abaronesa.net.br`
> **Repositorio remoto:** `git@github.com:greggorio/abaronesa-emporio.git`

## Objetivo

Definir a estrutura de Docker, integracao continua e entrega continua do Emporio antes de alterar o projeto ou o servidor de producao.

Este documento e o contrato de referencia para a futura implementacao. Ele nao descreve uma infraestrutura ja implantada e nao autoriza deploy.

A aprovacao registrada neste documento compreende:

- commits e pushes continuarao sendo feitos pelo desenvolvedor no terminal;
- a interface do ambiente de desenvolvimento publicara releases elegiveis;
- a interface do ambiente de producao implantara ou revertera releases publicadas;
- uma release representa o sistema completo, nao uma versao escolhida isoladamente por componente;
- a producao nunca oferecera checkboxes para combinar versoes de frontend, backend ou website;
- um modulo de controle separado coordenara essas rotinas sem executar Git ou Docker a partir dos backends comerciais.

## Premissas

- O desenvolvimento local continuara sem Docker obrigatorio:
  - backends com `mvn spring-boot:run`
  - frontends com `npm run dev`
  - servicos auxiliares podem ser iniciados separadamente quando necessarios
- Builds de producao serao executados no GitHub Actions, nunca na VPS.
- A VPS somente armazenara configuracoes, volumes persistentes e imagens prontas.
- O projeto sera organizado como um monorepo com cinco componentes publicaveis:
  - `backend`
  - `website_back`
  - `frontend`
  - `website_front`
  - `whatsapp_service`
- Um sexto componente operacional, denominado neste documento `release_control`, sera independente dos cinco componentes comerciais.
- O `release_control` sera implantado em papeis mutuamente exclusivos de publicador e implantador.
- O `release_control` de producao permanecera fora da transacao que atualiza a aplicacao, para continuar disponivel durante falhas e rollback.
- O Nginx e o Certbot ja existentes no host continuarao responsaveis por `80/443` e TLS.
- PostgreSQL, APIs e WhatsApp nao serao publicados diretamente na internet.
- Producao nao usara `latest` como referencia de release.

## Parecer sobre os artefatos existentes

Os arquivos criados durante o estudo anterior devem ser tratados como prototipos, nao como base aprovada de producao.

### Aspectos que devem ser preservados

- Dockerfiles multi-stage por componente
- build de imagens fora da VPS
- uso de registry OCI, preferencialmente GHCR
- rede Docker privada
- volumes para dados persistentes
- atualizacao da stack com Docker Compose

### Aspectos que devem ser substituidos

- container proxy tentando ocupar `80/443`
- deploy automatico em todo push para `main`
- uso exclusivo da tag `latest`
- acesso SSH como `root`
- ausencia de health checks, smoke tests e rollback
- workflows duplicados ou concorrentes
- Compose de producao contendo secoes `build:`
- uma unica credencial administrativa para os dois bancos
- configuracoes e segredos reais dentro do workspace

### Incompatibilidades conhecidas que a implementacao devera corrigir

- `website_front` recebe variaveis diferentes das exigidas pelo seu entrypoint.
- O upstream interno usado pelo entrypoint do site aponta para servico/porta incorretos.
- `website_back` grava uploads sem volume persistente no Compose proposto.
- O nome da variavel de integracao ERP difere entre Compose e aplicacao.
- `JAVA_OPTS` e definido, mas os entrypoints Java atuais nao o consomem.
- Os Dockerfiles Node usam Node 18, que esta fora de suporte.
- Os prototipos ainda nao usam de forma consistente o namespace GHCR agora definido.
- `/opt/sistemas/emporio` ainda nao existe no servidor.
- A raiz local `emporio/` ainda nao e um repositorio Git nem esta vinculada ao remoto criado.

## Arquitetura de producao proposta

```text
Internet
   |
   v
Nginx do host + Certbot (80/443)
   |
   +-- /api/deployment-control/* -> 127.0.0.1:8121
   |                                  `-- release_control (deployer)
   |
   `-- demais rotas -> 127.0.0.1:8120
                         |
                         v
                      emporio-gateway (Nginx interno versionado)
   |
   +-- emporio.abaronesa.net.br/
   |      `-- website_front:80
   |
   +-- emporio.abaronesa.net.br/api/
   |      `-- website_back:8085
   |
   +-- emporio.abaronesa.net.br/ws
   |      `-- website_back:8085
   |
   +-- erp-emporio.abaronesa.net.br/
   |      `-- frontend:80
   |
   +-- erp-emporio.abaronesa.net.br/api/
   |      `-- backend:8080
   |
   `-- rotas internas necessarias
          `-- whatsapp_service:3001

Rede Docker privada
   +-- backend
   +-- website_back
   +-- frontend
   +-- website_front
   +-- whatsapp_service
   `-- postgres

Plano de controle, fora da transacao da aplicacao
   +-- release_control em desenvolvimento (papel publisher)
   |      `-- GitHub API / workflow de publicacao
   |
   `-- release_control em producao (papel deployer)
          +-- 127.0.0.1:8121
          `-- GitHub API / workflow de implantacao
```

### Nginx do host

O Nginx do host continuara responsavel por:

- certificados TLS
- redirecionamento HTTP para HTTPS
- convivencia entre todos os projetos da VPS
- encaminhamento das rotas comerciais dos dois dominios para o gateway interno
- encaminhamento prioritario das APIs de controle de producao para o `release_control`
- headers de proxy e upgrade de WebSocket

### Gateway interno

O gateway sera um container Nginx do projeto, publicado somente em loopback:

```yaml
ports:
  - "127.0.0.1:8120:8080"
```

A porta `8120` estava livre na data desta analise, mas devera ser validada novamente imediatamente antes da implantacao.

O gateway interno permite manter todo o roteamento da aplicacao versionado sem disputar `80/443` com o host.

O `release_control` de producao usara um listener separado, inicialmente previsto em `127.0.0.1:8121`. Essa porta tambem devera ser revalidada. O Nginx do host aplicara a rota especifica do controlador antes da rota generica `/api/`.

## Modulo de controle de releases e deploys

O projeto tera um modulo operacional especifico, referido inicialmente como:

```text
release_control
```

O nome definitivo podera ser ajustado na primeira slice depois de conferir as convencoes reais do monorepo. A responsabilidade, entretanto, esta aprovada: o modulo sera o plano de controle de releases e deployments e nao pertencera aos backends de negocio do ERP ou do website.

Uma unica base de codigo podera atender aos dois ambientes, mas cada instancia iniciara com exatamente um papel:

```text
RELEASE_CONTROL_MODE=publisher
RELEASE_CONTROL_MODE=deployer
```

O modo sera uma configuracao de bootstrap do servidor. Ele nao podera ser escolhido pelo navegador, por query string, por header ou pelo corpo de uma requisicao.

### Papel `publisher` — ambiente de desenvolvimento

Responsabilidades:

- consultar commits elegiveis da branch `main`;
- confirmar que o commit foi enviado ao repositorio remoto;
- confirmar que o CI e a geracao do candidato terminaram com sucesso;
- calcular a proxima versao semantica;
- receber tipo `MAJOR`, `MINOR` ou `PATCH`, descricao e changelog;
- solicitar ao GitHub Actions a publicacao da release;
- acompanhar o workflow ate sucesso ou falha;
- apresentar o manifesto global produzido.

Nao sao responsabilidades desse papel:

- executar `git add`, `git commit`, `git tag` ou `git push` localmente;
- compilar imagens no host de desenvolvimento;
- conectar na VPS;
- alterar uma instalacao de producao;
- acessar Docker.

Rotas de API previstas, sujeitas apenas a adequacao de nomenclatura na slice de contrato:

```text
GET  /api/release-publisher/v1/candidates
GET  /api/release-publisher/v1/releases
POST /api/release-publisher/v1/releases
GET  /api/release-publisher/v1/releases/{releaseId}/status
```

Rota de interface prevista:

```text
/configuracoes/releases
```

### Papel `deployer` — ambiente de producao

Responsabilidades:

- identificar a release global atualmente instalada;
- listar releases publicadas e elegiveis para producao;
- obter o manifesto imutavel da release de destino;
- calcular e exibir o plano de atualizacao;
- solicitar ao GitHub Actions o workflow de implantacao;
- acompanhar backup, migration, troca de containers, health checks e smoke tests;
- registrar sucesso, falha ou rollback;
- permitir rollback global para uma release anteriormente implantada e ainda compativel.

Nao sao responsabilidades desse papel:

- criar commits, tags ou releases;
- aceitar referencias arbitrarias de imagens;
- permitir selecao manual de componentes;
- montar o Docker socket;
- executar comandos Docker recebidos pela API;
- modificar arquivos de aplicacao fora do procedimento de deploy.

Rotas de API previstas:

```text
GET  /api/deployment-control/v1/current
GET  /api/deployment-control/v1/releases
GET  /api/deployment-control/v1/releases/{releaseId}/plan
POST /api/deployment-control/v1/deployments
GET  /api/deployment-control/v1/deployments/{deploymentId}
POST /api/deployment-control/v1/rollbacks
```

Rota de interface prevista:

```text
/configuracoes/atualizacao-sistema
```

Uma solicitacao de deploy recebera somente a identidade da release global:

```json
{
  "release": "v1.4.0"
}
```

O backend recusara campos que tentem substituir digests ou versoes de componentes.

### Isolamento efetivo entre ambientes

Ocultar botoes no frontend nao sera considerado isolamento.

- no modo `publisher`, as rotas de deployment nao serao registradas;
- no modo `deployer`, as rotas de publicacao nao serao registradas;
- cada modo tera credenciais distintas e de privilegio minimo;
- o frontend consultara uma capacidade autenticada do servidor para montar o menu correto;
- uma instalacao nao dependera da disponibilidade da outra;
- o catalogo compartilhado sera formado pelos artefatos imutaveis publicados no GitHub/GHCR, nao pelo banco do ambiente de desenvolvimento.

### Posicao no ciclo de vida

O `release_control` de producao nao sera atualizado como parte da mesma transacao que ele acompanha. Sua imagem, configuracao e procedimento de atualizacao terao ciclo operacional separado.

Na VPS, a separacao prevista sera:

```text
/opt/sistemas/emporio/          # stack comercial e releases globais
/opt/sistemas/emporio-control/  # plano de controle
```

A interface podera permanecer dentro do frontend administrativo para preservar a experiencia visual existente. A API de controle, as credenciais e o estado de acompanhamento, porem, ficarao no componente operacional separado. Caso a primeira slice revele que essa separacao nao e viavel com a estrutura real, qualquer alternativa exigira decisao arquitetural registrada antes da implementacao.

## Modelo de release global

Uma release do Emporio sera uma lista de materiais de software, ou BOM, que descreve um conjunto completo e validado. Ela nao representa apenas uma tag Git nem uma versao isolada de um servico.

O exemplo conceitual inicial foi substituido pelo contrato verificavel da S13:
`ops/releases/global-release.schema.json`, exemplo integral em
`ops/releases/examples/global-release.example.json` e documentação em
`release-control/RELEASES.md`. O contrato preserva o BOM de seis componentes,
provenance, changelog e auditoria, e usa fingerprints Flyway com
`required_on_change` e `restore_required`; ele não alega reversibilidade de
SQL. Nesta fase a geração é somente offline, sem tag ou publicação.

### Resolucao automatica de dependencias

A compatibilidade sera decidida antes de uma release aparecer para producao:

1. o repositorio declarara componentes, caminhos afetados e dependencias em um arquivo versionado;
2. o pipeline identificara os componentes atingidos pelo commit;
3. dependentes transitivos serao incluidos quando o contrato exigir;
4. componentes nao afetados herdarao do ultimo conjunto valido seus digests exatos;
5. testes de integracao validarao o conjunto completo;
6. somente entao o manifesto global sera publicado.

O nome inicial previsto para a declaracao e:

```text
ops/releases/components.yml
```

A estrutura real desse arquivo sera definida depois do levantamento de imports compartilhados, contratos HTTP, migrations e dependencias de build. Na duvida, o resolvedor devera falhar fechado ou incluir um conjunto maior; nunca presumir compatibilidade para economizar build.

### Plano calculado em producao

Ao receber uma release, o `release_control` comparara o manifesto atual com o manifesto de destino:

- digest igual: manter o componente em execucao;
- digest diferente: baixar e recriar o componente;
- migration nova: exigir backup e executar a etapa declarada;
- dependencia coordenada: atualizar o grupo na ordem definida;
- requisito ausente ou manifesto invalido: bloquear o deploy.

Portanto, a producao decide quais operacoes sao necessarias, mas nao inventa uma combinacao de versoes. A combinacao autorizada ja esta congelada no manifesto global.

## Docker Compose de producao

Havera um arquivo canonico para a stack comercial:

```text
ops/compose/compose.prod.yml
```

E um arquivo separado para o plano de controle:

```text
ops/compose/compose.control.yml
```

O `compose.prod.yml`:

- nao contera `build:`
- nao publicara PostgreSQL
- nao publicara APIs ou WhatsApp diretamente
- exigira referencias completas de imagem
- usara uma rede interna exclusiva
- declarara health checks para todos os servicos
- declarara limites de recursos e rotacao de logs
- usara volumes nomeados explicitamente

O `compose.control.yml`:

- executara somente as dependencias do `release_control`;
- publicara sua API apenas em `127.0.0.1:8121`;
- nao montara o Docker socket;
- nao sera incluido em `deploy-release.sh`;
- tera volume ou persistencia minima apenas se o contrato de reconciliacao exigir;
- usara uma rede diferente da stack comercial, salvo necessidade comprovada.

### Referencias imutaveis

Cada servico recebera uma referencia propria:

```text
BACKEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-backend@sha256:<digest>
WEBSITE_BACK_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-backend@sha256:<digest>
FRONTEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-frontend@sha256:<digest>
WEBSITE_FRONT_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-frontend@sha256:<digest>
WHATSAPP_IMAGE=ghcr.io/greggorio/abaronesa-emporio-whatsapp-service@sha256:<digest>
GATEWAY_IMAGE=ghcr.io/greggorio/abaronesa-emporio-gateway@sha256:<digest>
```

Tags como `main`, `latest` e `sha-<commit>` podem existir no registry para navegacao, mas o release implantado sera fixado por digest.

### Persistencia

Volumes previstos:

```text
emporio-postgres-data
emporio-backend-uploads
emporio-website-uploads
emporio-whatsapp-session
```

Dados que precisem sobreviver a recriacao de container nao poderao permanecer apenas no filesystem da imagem.

### PostgreSQL

A proposta inicial e utilizar um cluster PostgreSQL com:

- banco do ERP
- banco do website
- usuario de aplicacao distinto para cada banco
- usuario administrativo separado, usado apenas para manutencao e migrations
- nenhuma porta publicada no host

O script de inicializacao devera ser idempotente no que for aplicavel e atuar somente na primeira criacao do volume.

## Dockerfiles

### Java

- Java 21
- build multi-stage com Maven
- testes executados no CI antes do build da imagem
- runtime sem ferramentas de compilacao
- usuario nao-root
- suporte explicito a opcoes de JVM
- health check por endpoint Spring Boot Actuator
- imagem base testada com bibliotecas fiscais, fontes, certificados e processamento de midia

O uso de `-DskipTests` durante a construcao da imagem somente sera aceito se o mesmo commit ja tiver passado por `mvn verify` no CI.

### Node e frontends

- adotar Node 24 LTS, condicionado a teste de compatibilidade
- usar `npm ci`
- copiar somente artefatos necessarios
- servir SPAs por Nginx sem Node no runtime
- manter configuracao de URLs em runtime quando o componente ja suporta esse contrato
- executar como usuario sem privilegios quando a imagem permitir

### WhatsApp

- Node LTS suportado
- Chromium instalado de forma reprodutivel
- sessao persistida em volume dedicado
- endpoint de liveness separado do estado de autenticacao/QR
- limites de memoria, CPU e processos
- nenhum acesso ao Docker socket

## Fluxo de CI/CD

O processo sera dividido em quatro responsabilidades de workflow. A implementacao podera reutilizar workflows chamados por outros workflows, mas nao podera fundir autorizacoes de publicacao e producao em um unico gatilho implicito.

## 1. CI — `ci.yml`

Gatilhos:

- pull request para `main`
- push em `main`

Validacoes minimas:

### Backend ERP

- `mvn -B verify`
- compilacao e testes
- validacao de migrations

### Website backend

- `mvn -B verify`
- compilacao e testes
- validacao de migrations

### Frontend ERP

- `npm ci`
- lint
- Vitest
- build Quasar

### Website frontend

- `npm ci`
- TypeScript
- build Vite

### WhatsApp

- `npm ci`
- validacao sintatica
- testes disponiveis

### Infraestrutura

- `docker compose config`
- build de todos os Dockerfiles de producao sem push
- verificacao de vulnerabilidades
- verificacao de que nenhum segredo foi versionado

Falha em qualquer job impedira a criacao do candidato e a publicacao de release.

## 2. Candidato — `publish-candidate.yml`

Executado somente apos CI verde para `main`.

Responsabilidades:

- determinar o conjunto afetado conforme `ops/releases/components.yml`
- construir em paralelo as imagens afetadas
- usar cache do Docker Buildx
- publicar no GHCR com `GITHUB_TOKEN`
- adicionar labels OCI com repositorio, commit e data
- publicar tag `sha-<commit>`
- herdar digests exatos do ultimo conjunto valido para componentes nao afetados
- registrar todos os digests em um manifesto candidato
- executar validacoes integradas sobre o conjunto completo
- armazenar o manifesto candidato como artefato imutavel e consultavel

O manifesto candidato sempre declarara o commit do monorepo que originou a avaliacao. Um digest herdado mantera tambem sua procedencia original.

O `release_control` tera build, publicacao e atualizacao independentes desse manifesto. A implementacao do seu workflow operacional sera definida antes da primeira implantacao do plano de controle; ele nao podera ser promovido implicitamente por uma release comercial.

## 3. Release — `publish-release.yml`

Sera disparado pelo papel `publisher` da interface de desenvolvimento. O workflow:

1. valida a identidade e permissao do solicitante;
2. confirma que o commit pertence a `main`;
3. confirma CI verde e candidato valido;
4. calcula e valida a versao semantica;
5. impede duplicidade ou regressao de versao;
6. cria a tag Git semantica;
7. publica o manifesto global imutavel;
8. publica changelog e metadados de auditoria;
9. torna a release visivel para producao.

O fluxo normal do desenvolvedor sera:

```bash
git add .
git commit -m "descricao da alteracao"
git push
```

Nenhuma dessas operacoes sera executada pela interface. A interface somente atuara depois que o commit remoto estiver elegivel.

## 4. Producao — `deploy-production.yml`

Sera disparado pelo papel `deployer` da interface de producao por meio de uma integracao autenticada com o GitHub Actions. `workflow_dispatch` continuara disponivel como mecanismo operacional autorizado e de contingencia, sem se tornar o caminho cotidiano do usuario.

Configuracao conceitual:

```text
workflow_dispatch
```

O operador selecionara uma release global ja publicada. O workflow nao reconstruira imagens, nao recalculara dependencias e nao aceitara tags ou digests avulsos.

Configuracoes obrigatorias:

```yaml
environment: production
concurrency:
  group: emporio-production
  cancel-in-progress: false
```

Isso fornece:

- intencao explicita de deploy
- historico de deployments no GitHub
- isolamento dos segredos de producao
- um unico deploy por vez

Push em `main` nao alterara producao automaticamente.

### Credenciais dos dois papeis

As duas instancias de `release_control` nao compartilharao a mesma credencial:

- `publisher`: somente leitura de commits/checks e permissao para disparar o workflow de publicacao;
- `deployer`: somente leitura do catalogo e permissao para disparar o workflow de producao;
- VPS: somente leitura do GHCR;
- jobs de build: escrita no GHCR apenas durante publicacao de imagens.

Sera preferida uma GitHub App com permissoes minimas e tokens de curta duracao. Caso a primeira entrega use token fine-grained, a limitacao, rotacao e plano de substituicao deverao ser documentados.

## Contrato de usabilidade

As telas deverao comunicar o estado real dos workflows e nao apresentar sucesso antes da conclusao remota.

### Desenvolvimento — Gerar release

A tela devera apresentar:

- ultimo commit elegivel da `main` e possibilidade de escolher outro candidato elegivel;
- autor, data, resumo e SHA do commit;
- estado do CI e do manifesto candidato;
- release atual e proxima versao calculada;
- escolha `MAJOR`, `MINOR` ou `PATCH`;
- descricao obrigatoria e changelog;
- componentes afetados calculados, apenas para informacao;
- botao de publicacao habilitado somente quando todos os pre-requisitos estiverem verdes;
- link para o workflow e para a release publicada.

Estados minimos:

```text
NOT_ELIGIBLE
READY
REQUESTED
VALIDATING
PUBLISHING
PUBLISHED
FAILED
```

O usuario podera repetir a consulta de status, mas uma chave de idempotencia impedira que duplo clique ou timeout publique duas releases.

### Producao — Atualizar sistema

A tela devera apresentar:

- release atualmente instalada;
- ultima implantacao, autor e resultado;
- releases globais superiores e elegiveis;
- changelog consolidado;
- plano calculado com componentes mantidos, atualizados e migrations;
- aviso de backup e reversibilidade;
- uma unica acao de confirmacao para a release completa;
- progresso por etapa e link para o workflow;
- resultado dos health checks e smoke tests;
- rollback global quando permitido.

Estados minimos:

```text
AVAILABLE
QUEUED
PULLING
BACKING_UP
MIGRATING
UPDATING
VERIFYING
SUCCEEDED
ROLLING_BACK
ROLLED_BACK
FAILED
```

A interface nao exibira seletores independentes de frontend, backend, website ou WhatsApp. A lista de componentes do plano sera somente informativa.

### Concorrencia e idempotencia

- apenas uma publicacao de release podera reservar a mesma versao semantica;
- apenas um deployment de producao podera estar ativo;
- chamadas repetidas com a mesma chave retornarao a operacao ja criada;
- uma nova solicitacao nao cancelara silenciosamente uma operacao em andamento;
- estados locais serao reconciliados com o GitHub Actions depois de reinicio do `release_control`;
- sucesso somente sera persistido depois da validacao final do workflow.

## Execucao do deploy na VPS

O GitHub Actions conectara como um usuario dedicado, por exemplo:

```text
deploy-emporio
```

Nao sera utilizada a conta `root`.

O usuario tera somente as permissoes necessarias para:

- operar a stack `emporio`
- escrever no diretorio do projeto
- ler imagens do GHCR
- executar os comandos de validacao e rollback autorizados

A chave publica, o `known_hosts` e as permissoes desse usuario farao parte do bootstrap documentado do servidor.

### Script canonico

Um script versionado continuara sendo usado:

```text
ops/deploy/deploy-release.sh
```

O script nao sera executado manualmente no fluxo normal. O GitHub Actions sera o orquestrador e chamara o script remotamente.

O script existira para:

- centralizar a transacao de deploy
- permitir testes
- aplicar lock
- padronizar health checks e rollback
- permitir recuperacao emergencial controlada

### Sequencia de deploy

1. adquirir lock exclusivo com `flock`
2. validar o identificador do release
3. obter e validar o manifesto de imagens
4. validar `docker compose config`
5. fazer pull das imagens por digest
6. executar backup pre-migration
7. registrar o release atual como `previous`
8. executar migrations de forma controlada
9. atualizar apenas os containers necessarios
10. aguardar health checks
11. executar smoke tests pelos dois dominios
12. registrar sucesso e metadados
13. limpar imagens antigas sem remover os releases atual e anterior

Comando base:

```bash
docker compose \
  --env-file /opt/sistemas/emporio/shared/.env \
  -f /opt/sistemas/emporio/current/compose.prod.yml \
  up -d \
  --no-build \
  --remove-orphans \
  --wait \
  --wait-timeout 180
```

Nao sera usado `docker compose down` no fluxo normal.

## Health checks e smoke tests

### Health checks internos

- PostgreSQL: `pg_isready`
- backend: `/actuator/health`
- website backend: endpoint de health dedicado
- frontends: resposta HTTP local
- WhatsApp: liveness do processo HTTP
- gateway: endpoint interno de health

### Smoke tests externos

Minimo esperado:

- `https://emporio.abaronesa.net.br/`
- API publica do website
- WebSocket/SSE aplicavel
- `https://erp-emporio.abaronesa.net.br/`
- health autenticavel ou endpoint tecnico do backend
- fluxo de login sem executar operacoes destrutivas

O deploy somente sera concluido depois dessas validacoes.

## Releases e rollback

Estrutura prevista no host:

```text
/opt/sistemas/emporio/
├── shared/
│   ├── .env
│   └── backups/
├── releases/
│   ├── <release-1>/
│   │   ├── compose.prod.yml
│   │   ├── release.env
│   │   └── manifest.json
│   └── <release-2>/
│       ├── compose.prod.yml
│       ├── release.env
│       └── manifest.json
├── current -> releases/<release-atual>
└── previous -> releases/<release-anterior>
```

O mesmo plano de controle permitira solicitar rollback para uma release anterior elegivel. O workflow tambem podera ser acionado de forma operacional autorizada em uma contingencia da interface.

Rollback de imagens:

1. apontar `current` para o manifesto anterior
2. executar Compose com os digests anteriores
3. aguardar health checks
4. executar smoke tests
5. registrar o resultado

Rollback de imagem nao implica rollback automatico de banco.

## Migracoes e backup

- Migrations deverao ser backward-compatible.
- Mudancas destrutivas seguirao estrategia expand/contract.
- Backup sera obrigatorio antes de migration de producao.
- O backup local tera retencao definida.
- Existira copia fora da VPS.
- Restore sera testado periodicamente.
- O deploy nao sera considerado reversivel sem conhecer o impacto da migration.

## Segredos

Nao serao versionados:

- senhas de banco
- JWT secrets
- tokens entre servicos
- credenciais SMTP
- credenciais OAuth
- chaves fiscais
- chave privada SSH
- token de leitura do GHCR

No repositorio existira somente:

```text
ops/env/.env.example
```

Na VPS:

```text
/opt/sistemas/emporio/shared/.env
```

com proprietario restrito e permissao `0600`.

O arquivo `ops/env/.env.production` atualmente presente no workspace contem valor com aparencia de segredo real. Ele nao devera ser publicado, e o valor devera ser rotacionado antes da criacao/publicacao do repositorio.

## Registry

O repositorio remoto canonico e:

```text
git@github.com:greggorio/abaronesa-emporio.git
```

O namespace canonico do GHCR e:

```text
ghcr.io/greggorio
```

Os pacotes usarao o prefixo `abaronesa-emporio-` e deverao ser referenciados de forma identica em:

- workflows
- Compose
- manifestos
- login do servidor

O pacote do plano de controle, fora do manifesto comercial, sera:

```text
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:<digest>
```

O GitHub Actions publicara com `GITHUB_TOKEN`.

A VPS usara:

- imagens publicas sem credencial; ou
- token dedicado somente com `read:packages`

Tokens de escrita nao ficarao no servidor.

## Seguranca de supply chain

- actions de terceiros fixadas por commit SHA
- permissoes do `GITHUB_TOKEN` declaradas por job
- `contents: read` por padrao
- `packages: write` somente no job de publicacao
- imagens com labels OCI
- scan de imagem antes da promocao
- manifesto de release com digests
- nenhuma reutilizacao de build entre commits sem rastreabilidade

## Disponibilidade

Docker Compose com uma instancia por servico reduz o tempo de troca, mas nao garante zero downtime.

Decisao inicial recomendada:

- aceitar poucos segundos de indisponibilidade por componente
- nao executar `down`
- preparar o gateway para futura estrategia blue/green

Blue/green, Swarm ou Kubernetes nao fazem parte da primeira implementacao, salvo se zero downtime for requisito formal.

## Recursos da VPS

Estado observado na data da analise:

- 4 CPUs
- 15 GiB de RAM
- aproximadamente 4,7 GiB disponiveis
- sem swap
- aproximadamente 93 GiB livres em disco

A implementacao devera definir limites por servico, especialmente para:

- backend Java
- website backend Java
- Chromium/WhatsApp
- PostgreSQL

Builds jamais serao executados na VPS.

## Estrutura de arquivos alvo

```text
emporio/
├── .github/workflows/
│   ├── ci.yml
│   ├── publish-candidate.yml
│   ├── publish-release.yml
│   └── deploy-production.yml
├── backend/
│   └── Dockerfile
├── website_back/
│   └── Dockerfile
├── frontend/
│   └── Dockerfile
├── website_front/
│   └── Dockerfile
├── whatsapp_service/
│   └── Dockerfile
├── release_control/
│   ├── Dockerfile
│   └── README.md
└── ops/
    ├── compose/
    │   └── compose.prod.yml
    ├── releases/
    │   ├── components.yml
    │   └── manifest.schema.json
    ├── gateway/
    │   ├── Dockerfile
    │   └── emporio.conf
    ├── nginx-host/
    │   ├── emporio.abaronesa.net.br.conf
    │   └── erp-emporio.abaronesa.net.br.conf
    ├── deploy/
    │   ├── deploy-release.sh
    │   ├── rollback-release.sh
    │   └── smoke-test.sh
    ├── db/
    │   ├── init-databases.sh
    │   └── backup.sh
    └── env/
        └── .env.example
```

O desenho interno de `release_control/` sera definido na slice de contrato depois da leitura do stack real. A arvore acima fixa a fronteira operacional, nao escolhe antecipadamente framework, banco ou biblioteca.

## Fora de escopo

- alterar a forma de iniciar o projeto localmente
- instalar a stack na VPS nesta etapa
- criar ou alterar DNS
- emitir certificados nesta etapa
- migrar dados reais
- executar seed de producao
- adotar Kubernetes ou Docker Swarm
- permitir que a aplicacao comercial execute deploy ou acesse Docker diretamente
- realizar auto-deploy de producao em push para `main`
- disponibilizar ao usuario final uma tela de selecao de imagens
- atualizar o proprio `release_control` dentro da transacao da release comercial

## Decisoes confirmadas

- a raiz `emporio/` sera o monorepo associado a `git@github.com:greggorio/abaronesa-emporio.git`;
- o namespace de imagens sera `ghcr.io/greggorio`, com prefixo `abaronesa-emporio-`;
- commits e pushes permanecem no terminal;
- UI de desenvolvimento publica release, sem executar Git;
- UI de producao implanta e reverte release;
- release e global e imutavel;
- selecao manual de versoes por componente e proibida;
- dependencias sao resolvidas antes da publicacao e congeladas no manifesto;
- producao calcula o plano comparando manifestos;
- `release_control` tem papeis e rotas mutuamente exclusivos por ambiente;
- `release_control` nao acessa Docker diretamente;
- push em `main` nao altera producao;
- a implementacao sera conduzida em slices verificaveis com documentacao no mesmo ciclo.

## Decisoes operacionais ainda pendentes

- gateway interno em `127.0.0.1:8120`
- um cluster PostgreSQL com dois bancos e usuarios separados
- aceitacao de breve indisponibilidade durante troca de containers
- atualizacao dos componentes Node para Node 24 LTS
- politica de backup externo e retencao
- nome e politica do usuario `deploy-emporio`
- tecnologia interna e persistencia minima do `release_control`
- uso inicial de GitHub App ou token fine-grained
- politica de atualizacao independente do proprio `release_control`

A inicializacao da raiz local como repositorio Git e a configuracao do `origin` continuam sendo trabalho de implementacao, embora o destino remoto ja esteja decidido.

## Criterios de aceite da implementacao

- existe apenas um conjunto canonico de workflows
- CI falha quando qualquer componente falha
- nenhuma imagem e publicada antes dos testes
- nenhuma referencia de producao usa `latest`
- cada release registra os cinco digests
- cada release registra tambem o digest do gateway e a procedencia de digests herdados
- somente commits remotos da `main` com CI e candidato verdes podem virar release
- a criacao de release e a implantacao possuem APIs e credenciais distintas
- rotas incompativeis com o modo do `release_control` nao sao registradas
- a UI nunca executa commit, push ou selecao de imagem
- a producao recebe somente um identificador de release global
- o plano de deploy e calculado pela comparacao de manifestos
- dependencias transitivas declaradas sao respeitadas ou o release falha fechado
- push em `main` nao altera producao
- somente um deploy de producao ocorre por vez
- deploy e executado sem SSH como root
- nenhum servico da aplicacao monta Docker socket
- `release_control` permanece disponivel durante a troca da stack comercial
- somente o gateway publica porta, sempre em loopback
- PostgreSQL nao publica porta
- todos os servicos possuem health check
- uploads e sessao WhatsApp sobrevivem a recriacao
- backup e executado antes de migrations
- falha de health/smoke test aciona rollback de imagens
- release anterior permanece disponivel
- segredos nao existem no repositorio
- Compose e Nginx sao validados antes da instalacao
- os dois dominios respondem por HTTPS depois do deploy
- documentacao de uso, contrato e operacao corresponde ao comportamento entregue em cada slice

## Ordem recomendada de implementacao

1. inventariar o codigo real, sanear segredos e definir a raiz Git
2. congelar contratos de componentes, configuracao, migrations e dependencias
3. definir contratos de API, estados e seguranca do `release_control`
4. corrigir e validar Dockerfiles
5. criar Compose de producao e gateway interno
6. criar CI e manifesto candidato
7. criar publicacao da release global
8. implementar papel `publisher` e a UI de desenvolvimento
9. criar scripts de deploy, smoke test, backup e rollback
10. criar workflow de implantacao
11. implementar papel `deployer` e a UI de producao
12. preparar usuario, diretorios, Nginx e TLS na VPS
13. executar primeiro deploy acompanhado
14. validar restore, rollback e recuperacao do plano de controle

## Conducao da implementacao por slices

A implementacao sera conduzida pelo Codex no papel de orquestrador. A execucao de cada slice sera delegada a um CLI somente depois que o contrato da slice estiver escrito e revisado.

O plano detalhado nao sera gerado apenas a partir desta proposta. A primeira slice sera um inventario verificavel do monorepo, pois nomes de modulos, rotas, testes e dependencias devem partir do codigo real.

O acompanhamento das slices esta em [implementation/README.md](./implementation/README.md).

### Responsabilidades do orquestrador

- manter esta arquitetura como contrato;
- decompor o trabalho em slices pequenos, ordenados e independentemente verificaveis;
- definir arquivos permitidos, exclusoes, dependencias e criterios de aceite;
- fornecer ao CLI o contexto minimo suficiente e os artefatos canonicos;
- revisar o diff e a evidencia produzida, sem aceitar somente uma declaracao de sucesso;
- impedir o inicio de uma slice dependente enquanto a anterior divergir do contrato;
- atualizar a documentacao funcional, tecnica e operacional junto com a implementacao;
- registrar decisoes novas antes que elas se tornem convencoes silenciosas;
- interromper e replanejar quando o codigo real contradisser a proposta.
- ao aceitar uma slice, gerar imediatamente a proxima slice e fornecer o prompt de delegacao enquanto houver escopo pendente.

### Contrato obrigatorio de uma slice

Antes da delegacao, cada slice devera declarar:

```text
ID e titulo
objetivo observavel
dependencias e pre-condicoes
arquivos/diretorios permitidos
fora de escopo
comportamento esperado
contratos de API/dados afetados
documentacao a criar ou atualizar
validacoes obrigatorias
evidencias esperadas
criterios de aceite
condicao de bloqueio
```

Uma slice nao misturara, sem necessidade causal, implementacao de infraestrutura, alteracao funcional ampla e mutacao da VPS.

### Evidencia obrigatoria do executor CLI

O executor documentara seu trabalho em arquivo Markdown persistente. O relatorio devera conter:

- identificador da slice;
- diretorio de trabalho;
- resumo objetivo do que foi implementado;
- arquivos criados ou alterados;
- comandos exatos de validacao;
- codigo de saida de cada comando;
- resultado e interpretacao;
- artefatos produzidos;
- desvios, limitacoes e riscos remanescentes;
- documentacao atualizada;
- declaracao explicita do que nao foi executado.

Os relatorios serao armazenados inicialmente em:

```text
docs/infrastructure/deployment/implementation/slices/
```

O status de uma slice podera ser:

```text
PLANNED
IN_PROGRESS
BLOCKED
ACCEPTED
REJECTED
```

Saida verde de um teste parcial nao torna a slice `ACCEPTED` quando outros criterios continuam pendentes.

### Regra contra divergencia entre codigo e documentacao

Toda slice funcional devera avaliar e atualizar, quando aplicavel:

1. documentacao de usabilidade;
2. contrato de API e payloads;
3. documentacao de implementacao e configuracao;
4. procedimento operacional;
5. criterios e comandos de validacao;
6. changelog ou registro da decisao.

Se uma alteracao nao exigir mudanca documental, o relatorio da slice devera justificar por que o contrato existente permanece correto.

A documentacao nao sera escrita somente no final do projeto. Uma slice com codigo concluido e documentacao divergente permanecera `IN_PROGRESS`.

### Documentos vivos previstos

Durante a implementacao serao criados e mantidos, conforme as slices os tornarem necessarios:

```text
docs/infrastructure/deployment/release-control/
├── README.md
├── USABILIDADE.md
├── IMPLEMENTACAO.md
├── API.md
└── OPERACAO.md

docs/infrastructure/deployment/implementation/
├── README.md
└── slices/
    └── SNN-<nome-da-slice>.md
```

Arquivos vazios ou documentacao especulativa nao serao criados antecipadamente. Cada documento nascera junto da primeira entrega que produza informacao verificavel sobre ele.

### Macrofases para futura decomposicao

As macrofases abaixo orientam a ordem, mas ainda nao sao tasks delegaveis:

1. descoberta e contratos reais;
2. fundacao Git, segredos e qualidade;
3. imagens e Compose;
4. CI e catalogo de candidatos;
5. release global e papel `publisher`;
6. deploy transacional e rollback;
7. papel `deployer`;
8. bootstrap seguro da VPS;
9. primeiro release e implantacao acompanhados;
10. exercicio de falha, restore e documentacao operacional final.

Cada macrofase sera dividida em slices menores depois que suas pre-condicoes forem comprovadas.

## Regra de atualizacao

Durante a implementacao:

- registrar qualquer decisao diferente da arquitetura aprovada;
- manter este documento atualizado quando o contrato arquitetural mudar;
- manter detalhes verificaveis de uso e implementacao nos documentos vivos;
- nao marcar como implementado aquilo que existe apenas no plano;
- ao concluir o primeiro deploy, criar um documento separado de arquitetura real e procedimento operacional;
- substituir observacoes temporais desta analise por evidencia atual quando a respectiva slice revalidar o ambiente.

Este documento nao devera ser convertido silenciosamente em descricao de estado real.
