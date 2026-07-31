# S11 — CI canonico e contrato de manifesto candidato

> **Estado:** `ACCEPTED` — 29/07/2026  
> **Tipo:** CI, supply chain e contrato de dados de candidato  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S10 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Contrato de componentes:** [release-control/README.md](../../release-control/README.md)  
> **Relatorio de saida:** `S11-ci-canonico-e-contrato-manifesto-candidato.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro:

1. esta task;
2. a revisao final da S10;
3. as secoes `Modelo de release global`, `Fluxo de CI/CD`, `Registry`,
   `Seguranca de supply chain`, `Estrutura de arquivos alvo` e `Criterios de
   aceite da implementacao` da arquitetura;
4. `.github/workflows/README.md`;
5. `ops/releases/components.yml`, seu schema, resolvedor e testes;
6. os contratos S06 de API, estados, idempotencia e seguranca;
7. os Dockerfiles, Compose, gateway e validadores aceitos nas S08 a S10;
8. os comandos reais de teste nos `pom.xml` e `package.json`;
9. `.gitignore`, `.gitattributes` e os guias de segredos.

Nao altere esta task nem o tracker
`docs/infrastructure/deployment/implementation/README.md`.

Esta slice ativa somente a CI de verificacao e implementa localmente o
contrato do manifesto candidato. Ela nao publica imagens, candidato,
artefato remoto, tag Git ou release; nao cria os workflows
`publish-candidate.yml`, `publish-release.yml` ou `deploy-production.yml`; nao
faz commit/push e nao acessa producao.

## 1. Objetivo observavel

Ao final:

- existe exatamente um workflow ativo, `.github/workflows/ci.yml`;
- a CI dispara somente em `pull_request` para `main` e `push` em `main`;
- nenhum push executa publicacao ou deploy;
- todos os componentes e contratos aceitos possuem gates de CI reais;
- todos os Dockerfiles de producao sao construidos para `linux/amd64`, sem
  push;
- imagens construidas sao examinadas por scanner de vulnerabilidades;
- o repositorio e examinado por detector de segredos;
- actions e imagens auxiliares sao imutavelmente fixadas;
- permissoes do `GITHUB_TOKEN` sao minimas e explicitas;
- existe schema estrito do manifesto candidato;
- existe gerador/validador local deterministico e fail-closed;
- primeiro candidato exige os seis componentes construidos;
- candidato incremental herda digests exatos e procedencia do ultimo conjunto
  valido;
- build, validacao e heranca respeitam o resolvedor S05;
- o candidato nunca e confundido com release implantavel;
- nenhuma publicacao, release, deploy ou acesso a VPS ocorre.

## 2. Artefatos permitidos

Criar ou atualizar somente:

```text
.github/workflows/ci.yml
.github/workflows/README.md
ops/releases/candidate-manifest.schema.json
ops/releases/examples/candidate-manifest.example.json
tools/ci/**
tools/releases/candidate_manifest.py
tools/releases/tests/test_candidate_manifest.py
docs/infrastructure/deployment/ci/CI.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/implementation/slices/S11-ci-canonico-e-contrato-manifesto-candidato.report.md
```

`tools/ci/**` pode conter validadores, testes e configuracoes estritamente
necessarios para CI, scan e migrations. Nao duplicar contratos que ja
pertencem a `tools/releases`, `tools/docker`, `tools/compose`,
`tools/gateway` ou `tools/security`.

Se for indispensavel alterar um teste existente para integra-lo a CI, pare e
registre a necessidade antes de expandir a lista.

## 3. Fora de escopo

Nao:

- criar `publish-candidate.yml`, `publish-release.yml` ou
  `deploy-production.yml`;
- usar `workflow_run`, `workflow_dispatch`, `repository_dispatch`,
  `schedule` ou `pull_request_target`;
- publicar imagem, pacote, artifact remoto, attestation ou SBOM;
- autenticar no GHCR ou conceder `packages: write`;
- conceder `contents: write`, `actions: write`, `id-token: write`,
  `deployments: write` ou permissoes equivalentes;
- usar SSH, SCP, rsync, Docker socket remoto, self-hosted runner ou segredo de
  VPS;
- criar tag, commit, release GitHub ou branch;
- implementar `release_control`, suas UIs ou clientes GitHub;
- implementar versao semantica, changelog de release ou promocao;
- criar script de deploy, backup, restore, smoke de producao ou rollback;
- alterar codigo comercial, migrations, Dockerfiles, Compose ou gateway;
- abrir ou alterar `.env.production`;
- executar GitHub Actions remotamente;
- acessar GHCR com escrita, DNS ou VPS.

## 4. Workflow canonico `ci.yml`

### 4.1 Gatilhos e concorrencia

Usar exclusivamente:

```yaml
on:
  pull_request:
    branches: [main]
  push:
    branches: [main]
```

Nao aplicar `paths-ignore`: mudancas documentais ainda devem validar contratos
e o proprio workflow.

Definir `concurrency` por workflow/ref, com cancelamento apenas de execucoes
anteriores da mesma PR/ref. Timeout deve existir em todos os jobs.

O workflow deve funcionar no primeiro push de um repositorio sem commit
anterior e em pushes subsequentes. Logica de diff, quando necessaria, deve
tratar:

- PR: base remota da PR;
- push comum: `before`;
- primeiro push: resolver como `--first-release`;
- SHA nulo ou base indisponivel: falhar fechado selecionando os seis.

Nao confiar em checkout raso para calcular mudancas. Paths devem ser
entregues ao resolvedor existente sem perder nomes ocultos, espacos ou
quebras de linha; se o formato de transporte seguro nao puder representar um
path, falhar fechado.

### 4.2 Actions e permissoes

- `permissions: contents: read` no nivel global;
- declarar permissoes por job quando diferirem;
- nenhuma permissao de escrita;
- usar somente actions necessarias;
- toda action, inclusive oficial, deve usar commit SHA completo de 40
  caracteres, com comentario informando a versao humana;
- nenhum `@main`, `@master`, `@v4` ou tag mutavel;
- runner hospedado pelo GitHub, `ubuntu-24.04` ou versao explicitamente
  justificada e nao `self-hosted`;
- Java 21 e Node 24, coerentes com as imagens aceitas;
- caches devem ser nativos das actions oficiais ou Buildx e nao podem conter
  segredos.

E permitido consultar, em modo somente leitura, os repositorios oficiais das
actions escolhidas para confirmar os SHAs. Registrar URL, tag humana, SHA e
data da consulta no relatorio. Nao usar action abandonada ou sem release
identificavel.

### 4.3 Jobs minimos

Os nomes podem ser ajustados, mas a separacao e os gates devem permanecer
claros.

#### Contratos e infraestrutura

Executar:

```text
python3 tools/releases/catalog.py validate --require-release-ready
python3 -m unittest discover -s tools/releases/tests -v
python3 tools/releases/release_control_contract.py
python3 tools/security/bootstrap_contract.py
python3 -m unittest discover -s tools/security/tests -v
python3 tools/docker/java_images_contract.py
python3 -m unittest discover -s tools/docker/tests -v
python3 tools/compose/validate_compose.py
python3 -m unittest discover -s tools/compose/tests -v
python3 tools/gateway/validate_gateway.py
python3 -m unittest discover -s tools/gateway/tests -v
```

Primeiro confirme os nomes reais dos entrypoints. Nao invente comando ausente;
documente qualquer ajuste factual.

Validar tambem:

- sintaxe e semantica local de `ci.yml`;
- actions fixadas por SHA;
- trigger e permissoes;
- ausencia de publicacao/deploy;
- Compose resolvido com fixtures nao sensiveis;
- schema e exemplos do candidato;
- estrutura/nome/unicidade das migrations Flyway dos dois backends.

O validador de migrations deve ser deterministico, sem alterar SQL, e no
minimo rejeitar versoes duplicadas, nomes invalidos e ordem/arquivo
ininterpretavel. A aplicacao real das migrations continua coberta pelos
testes/startup aceitos; se a CI puder aplica-las em PostgreSQL efemero sem
novo segredo ou alteracao comercial, incluir a prova. Caso contrario,
documentar explicitamente essa fronteira, sem afirmar que houve aplicacao.

#### Backend ERP

Em `backend/`:

```text
mvn -B verify
```

#### Website backend

Em `website_back/`:

```text
mvn -B verify
```

#### Frontend ERP

Em `frontend/`:

```text
npm ci
npm run lint
npm run test
npm run build
```

#### Website frontend

Em `website_front/`:

```text
npm ci
npm run test
npm run build
```

O build deve executar o TypeScript conforme o script real.

#### WhatsApp

Em `whatsapp_service/`:

```text
npm ci
node --check index.js
node --check app.js
npm run test
```

Se algum arquivo de entrypoint citado nao existir, corrigir o comando a partir
do codigo real e registrar.

#### Build e scan das imagens

O job de imagens depende explicitamente de todos os jobs de teste e contratos.
Construir, sem push, os seis componentes na ordem canonica ou por matriz:

```text
backend
website_back
frontend
website_front
whatsapp_service
gateway
```

Requisitos:

- plataforma `linux/amd64`;
- context e Dockerfile lidos do catalogo ou validados contra ele;
- `VCS_REF=${{ github.sha }}`;
- `IMAGE_VERSION=ci-${{ github.sha }}`;
- tag estritamente local e inequivoca;
- Buildx com cache GHA;
- `push: false`;
- nenhuma autenticacao de registry;
- scan de cada imagem local;
- falhar para vulnerabilidades `CRITICAL` e `HIGH`, salvo baseline
  machine-readable, temporaria, justificada por CVE e com expiracao;
- nenhuma excecao global, `continue-on-error` ou exit code ignorado;
- remover imagens locais somente no runner efemero, sem `docker system prune`.

O scanner/action ou imagem auxiliar deve estar fixado por SHA/digest. Registrar
o criterio escolhido. Se as imagens atuais nao passarem, a CI deve permanecer
fail-closed e o relatorio deve listar somente IDs de CVE, pacote, severidade e
acao necessaria, sem relaxar o gate.

### 4.4 Scan de segredos

O scan deve cobrir todos os arquivos que seriam versionados no checkout,
inclusive historico disponivel, e respeitar arquivos deliberadamente
ignorados. Requisitos:

- ferramenta conhecida ou validador local documentado;
- versao/action/imagem imutavelmente fixada;
- nenhuma telemetria ou upload do conteudo;
- falhar ao detectar segredo;
- allowlist somente por fingerprint/regra e justificativa, nunca pelo valor;
- nenhuma impressao do segredo no log;
- cobrir ao menos chaves privadas, tokens GitHub, Google, Uber, senhas e
  assignments sensiveis conhecidos no projeto;
- testes/mutantes com valores estritamente ficticios.

Se a ferramenta exigir historico Git, usar checkout completo. Como o
repositorio local ainda nao possui `HEAD`, a validacao local deve usar uma
fixture ou indice temporario e registrar a fronteira; nao executar `git add`
no indice real.

## 5. Contrato do manifesto candidato

Criar:

```text
ops/releases/candidate-manifest.schema.json
ops/releases/examples/candidate-manifest.example.json
tools/releases/candidate_manifest.py
tools/releases/tests/test_candidate_manifest.py
```

O candidato e um artefato de CI intermediario. Ele nao e release global, nao
possui versao semantica implantavel e nao pode ser consumido pelo deployer.

### 5.1 Identidade e origem

O schema, JSON Schema Draft 2020-12, deve ser fechado com
`additionalProperties: false` em todos os objetos e exigir:

- `schemaVersion`;
- `candidateId` opaco e restrito;
- repositorio canonico `greggorio/abaronesa-emporio`;
- commit SHA completo, lowercase, de 40 hex;
- ref exata `refs/heads/main`;
- data RFC 3339 UTC;
- ID e tentativa do workflow;
- URL HTTPS do workflow no repositorio canonico;
- versao e checksum SHA-256 do catalogo usado;
- resultado da resolucao de paths;
- exatamente seis componentes na ordem canonica;
- checksum do arquivo emitido em sidecar, nao autorreferente.

IDs e URLs devem ser dados, nunca comandos ou paths. Rejeitar controle,
whitespace periferico, traversal, URL com userinfo e host/repo divergente.

### 5.2 Resolucao e particionamento

Registrar:

- `changedPaths`;
- `firstRelease`;
- `buildComponents`;
- `validationComponents`;
- `inheritedComponents`;
- avisos fail-closed do resolvedor.

Invariantes:

- a ordem e sempre a ordem canonica do catalogo;
- `buildComponents` coincide com a saida do resolvedor;
- `validationComponents` coincide com o fechamento transitivo;
- `buildComponents` e `inheritedComponents` sao disjuntos;
- a uniao deles contem exatamente os seis componentes;
- primeiro candidato constroi/valida os seis e nao herda nenhum;
- path desconhecido conserva o warning, constroi e valida os seis;
- mudanca somente documental pode produzir build vazio, mas ainda deve
  preservar os seis componentes herdados;
- path global revalida os seis sem forcar rebuild;
- path absoluto, vazio ou com `..` falha.

### 5.3 Componentes e procedencia

Cada um dos seis componentes deve registrar:

- ID canonico;
- repositorio de imagem exato do catalogo;
- digest `sha256:<64 hex>`;
- referencia imutavel `<repositorio>@<digest>`;
- `sourceCommitSha`;
- `workflowRunId` e tentativa que produziram a imagem;
- data de build em UTC;
- estado `built` ou `inherited`;
- candidato de origem quando herdado;
- resultados `passed` de build, teste, health contratual e scan;
- labels OCI esperadas: source, revision e version.

Regras:

- componente `built` usa o commit/workflow atual e nao possui candidato
  herdado;
- componente `inherited` preserva digest, commit, workflow, data, labels e
  candidato de origem do conjunto anterior;
- repositorio e `immutableRef` devem corresponder ao componente;
- tags sem digest, `latest`, digest curto e namespace alternativo falham;
- nao aceitar componente extra, ausente, repetido ou fora de ordem;
- `release_control` e PostgreSQL nao podem aparecer no BOM;
- status diferente de `passed` invalida o candidato;
- heranca sem manifesto anterior valido falha;
- o candidato anterior deve ser valido pelo mesmo contrato;
- cadeia de heranca deve ser finita e nao pode apontar para si mesma;
- nenhuma combinacao parcial pode ser emitida.

### 5.4 Gerador e validador

Fornecer CLI local com comandos separados, por exemplo:

```text
python3 tools/releases/candidate_manifest.py validate --manifest <arquivo>
python3 tools/releases/candidate_manifest.py generate ...
```

O formato exato pode ser ajustado, mas deve:

- consumir o catalogo canonico;
- reutilizar o resolvedor S05, sem reimplementar fechamento;
- receber metadados CI explicitamente;
- receber referencias/digests construidos por arquivo ou argumentos
  estruturados, nunca por `eval`;
- receber candidato anterior somente quando necessario;
- emitir JSON canonico e deterministico;
- usar escrita atomica;
- nao sobrescrever arquivo existente sem flag explicita segura;
- emitir sidecar `<manifest>.sha256`;
- validar novamente antes de persistir;
- nunca consultar GitHub, GHCR, Docker ou rede;
- nunca publicar;
- usar codigos de saida distintos para uso invalido, contrato invalido e
  ausencia de candidato anterior;
- nao incluir segredo, credencial, environment integral ou caminho local.

Data, IDs e digests dos testes devem ser fixtures explicitas. Nao usar o
relogio, Git atual ou aleatoriedade silenciosamente em testes deterministas.

## 6. Testes obrigatorios

Criar testes positivos e negativos independentes. Cobrir no minimo:

1. exemplo canonico valido;
2. geracao deterministica byte a byte;
3. checksum sidecar correto;
4. primeiro candidato com seis builds;
5. candidato incremental com heranca;
6. mudanca de backend e fechamento transitivo;
7. mudanca de website backend;
8. mudanca frontend;
9. mudanca WhatsApp;
10. mudanca gateway;
11. docs-only;
12. path global;
13. path desconhecido fail-closed;
14. path invalido;
15. componente ausente/extra/repetido/fora de ordem;
16. repositorio trocado;
17. digest/tag invalido;
18. `immutableRef` divergente;
19. particoes divergentes;
20. procedencia built divergente;
21. procedencia inherited divergente;
22. candidato anterior ausente/invalido;
23. heranca circular ou autorreferente;
24. status de validacao nao aprovado;
25. `release_control` ou PostgreSQL no BOM;
26. SHA/ref/repository/workflow URL invalidos;
27. propriedade extra em cada familia de objeto;
28. tentativa de sobrescrita;
29. arquivo parcial nao deixado apos falha;
30. manifesto sem segredo ou path local.

Adicionar testes/mutantes do workflow para:

- trigger extra;
- permissao de escrita;
- action por tag;
- runner self-hosted;
- checkout raso;
- ausencia de job/comando;
- `continue-on-error`;
- publicacao/login/SSH;
- build com push;
- ausencia de scan;
- plataforma divergente;
- componente omitido.

## 7. Validacao local da slice

Executar e registrar, sem repetir suites comerciais pesadas ja comprovadas
fora do necessario:

```text
python3 tools/ci/validate_ci.py
python3 -m unittest discover -s tools/ci/tests -v
python3 tools/releases/candidate_manifest.py validate \
  --manifest ops/releases/examples/candidate-manifest.example.json
python3 -m unittest discover -s tools/releases/tests -v
python3 tools/releases/catalog.py validate --require-release-ready
```

Validar `ci.yml` também com parser/validador de GitHub Actions reconhecido,
fixado e executado de forma efemera, se disponivel. Nao instalar pacote
global no host. Diferenciar:

- contrato local do workflow;
- sintaxe reconhecida;
- execucao real no GitHub, que permanece **NAO EXECUTADA** ate o usuario
  criar commit e fazer push.

Nao repetir builds Docker ou Maven/NPM locais somente para simular os jobs
inteiros: as suites S08-S10 ja registram esses resultados. Esta slice valida
que os comandos corretos e gates estao no workflow. A primeira execucao
remota sera evidencia futura, nao deve ser inventada.

## 8. Documentacao

### `.github/workflows/README.md`

Substituir o estado transitorio por:

- `ci.yml` e o unico workflow ativo;
- triggers;
- jobs e gates;
- ausencia de publicacao/deploy;
- workflows futuros ainda ausentes;
- primeiro push sera a primeira execucao real.

### `docs/infrastructure/deployment/ci/CI.md`

Documentar:

- mapa dos jobs e dependencias;
- runtimes e caches;
- comandos por componente;
- build sem push;
- scanner de vulnerabilidades e politica de falha;
- scan de segredos e allowlists;
- migrations;
- permissoes e action pins;
- comportamento de primeiro push/diff;
- diagnostico de falhas;
- fronteira entre CI, candidato, release e deploy;
- como reproduzir apenas validadores locais;
- o que ainda nao existe.

### Release-control README

Atualizar somente as secoes de CI/candidato para registrar:

- contrato local implementado;
- candidato ainda nao publicado;
- `publish-candidate.yml` ainda ausente;
- release e deploy ainda ausentes;
- candidato nao e implantavel.

## 9. Estado Git e primeiro push

Preservar:

- indice Git real vazio;
- `HEAD` inexistente;
- nenhuma tag e nenhum reflog;
- nenhum commit ou push;
- `origin` inalterado;
- nenhum workflow alem de `ci.yml`;
- nenhum cache Python;
- nenhum artifact, manifesto gerado fora da fixture versionavel ou imagem
  local residual da S11.

O workflow sera apenas configurado localmente. O usuario continuara
responsavel por:

```text
git add .
git commit -m "..."
git push
```

Nao executar esses comandos.

## 10. Relatorio obrigatorio

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S11-ci-canonico-e-contrato-manifesto-candidato.report.md
```

Registrar:

1. CWD e estado inicial;
2. inventario factual de comandos/runtimes;
3. arquivos criados/alterados;
4. grafo de jobs e dependencias;
5. triggers, concorrencia e permissoes;
6. actions/imagens auxiliares com origem, versao e SHA/digest;
7. matriz de comandos por componente;
8. estrategia de primeiro push e diff;
9. migrations;
10. build sem push;
11. scan de vulnerabilidades;
12. scan de segredos;
13. schema do candidato;
14. algoritmo de geracao/heranca;
15. testes e mutantes com quantidades;
16. comandos exatos, exit codes e interpretacao;
17. falhas intermediarias e repeticoes;
18. fronteiras nao executadas;
19. estado Git/workflows/caches/residuos;
20. riscos e itens nao determinados;
21. bloqueios.

Nao reproduzir segredos, manifests completos de ferramentas, logs extensos ou
conteudo integral do workflow no relatorio.

Estado final obrigatorio:

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

Nao declarar `ACCEPTED`.

## 11. Criterios de aceite do orquestrador

A S11 somente podera ser aceita se:

- S10 estiver registrada como aceita;
- `ci.yml` for o unico workflow ativo;
- triggers forem somente PR/main e push/main;
- nenhuma permissao de escrita existir;
- actions estiverem fixadas por SHA;
- nenhum login, push, upload remoto, SSH ou deploy existir;
- todos os componentes possuirem testes corretos;
- Docker builds dependerem de todos os gates e usarem `push: false`;
- scans de vulnerabilidade e segredo forem fail-closed;
- migrations tiverem gate factual, sem alegacao excessiva;
- primeiro push e diff falharem fechado;
- schema candidato for estrito;
- exemplo for valido e inequivocamente ficticio;
- gerador for deterministico, atomico e offline;
- resolvedor S05 for reutilizado;
- primeiro candidato abranger os seis;
- incremental preservar digests e procedencia;
- particoes e ordem canonica forem verificadas;
- candidato nao puder ser tratado como release/deploy;
- testes positivos, negativos e mutantes passarem;
- documentacao corresponder ao workflow e ao codigo;
- execucao remota nao for alegada;
- estado Git protegido for preservado;
- nenhuma publicacao, commit, push ou producao ocorrer.

A proxima slice prevista, apos aceite, sera a S12: publicacao do candidato e
proveniencia das imagens no GitHub Actions/GHCR, ainda sem publicacao de
release global e sem deploy de producao.

## 12. Condicoes de bloqueio

Parar e documentar se:

- um comando real divergir do catalogo aceito;
- uma suite exigida nao existir;
- o scanner exigir segredo/licenca ou upload de codigo;
- nao for possivel fixar action/imagem por SHA/digest verificavel;
- a CI exigir permissao de escrita;
- o contrato candidato nao puder reutilizar o resolvedor S05;
- for necessario alterar codigo comercial, migration, Dockerfile, Compose ou
  gateway;
- qualquer ferramenta tentar publicar, criar commit/tag ou acessar producao;
- o indice real deixar de estar vazio;
- surgir workflow concorrente fora de `ci.yml`.
