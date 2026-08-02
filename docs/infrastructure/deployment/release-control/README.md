# Contrato de componentes e resolucao de releases

> **Estado:** contrato estrutural e runtimes publisher/deployer locais
> implementados; execução remota e produção ainda não realizadas.

## Contratos de API e seguranca

A S06 adicionou contratos verificáveis; S15 implementou o modo publisher e
S22 acrescentou o modo deployer, com bootstrap mutuamente exclusivo:

- [API, estados, idempotencia, concorrencia e seguranca](./CONTRATO_API_ESTADOS_SEGURANCA.md);
- [OpenAPI publisher](./api/publisher.openapi.yml);
- [OpenAPI deployer](./api/deployer.openapi.yml);
- [maquinas de estado](./contracts/state-machines.yml);
- [matriz de seguranca](./contracts/security-matrix.yml).
- [runtime publisher](./RUNTIME_PUBLISHER.md).
- [runtime deployer](./RUNTIME_DEPLOYER.md).
- [identidade RS256/JWKS do publisher](./IDENTIDADE_PUBLISHER.md).
- [identidade RS256/JWKS do deployer](./IDENTIDADE_DEPLOYER.md).
- [UI publisher de desenvolvimento](./UI_PUBLISHER.md).
- [UI deployer de produção](./UI_DEPLOYER.md).
- [plano determinístico offline de implantação](./PLANO_IMPLANTACAO.md).
- [transação local e journal de implantação](./TRANSACAO_IMPLANTACAO.md).
- [operação local de implantação](./OPERACAO_LOCAL_IMPLANTACAO.md).
- [workflow e transporte autenticado de implantação](./WORKFLOW_IMPLANTACAO.md).
- [contrato futuro de rollback comercial](./ROLLBACK_COMERCIAL.md).
- [OpenAPI futuro de rollback](./api/rollback.openapi.yml).
- [máquina de estados futura de rollback](./contracts/rollback-state-machine.yml).
- [matriz de segurança futura de rollback](./contracts/rollback-security.yml).

Publisher e deployer preservam routers, reconcilers e credenciais separados.
Ambos usam FastAPI, PostgreSQL 16, JWT RS256/JWKS e GitHub App. O publisher
acompanha publicação por `operationId`; o deployer sincroniza releases,
determina a próxima release forward elegível, despacha somente
`deploy-production.yml` e reconcilia o outcome S21. Nenhum workflow foi
executado remotamente.

## Catalogo, candidato e release

O [catalogo de componentes](../../../../ops/releases/components.yml) descreve
o conjunto comercial, seus paths, dependencias e gates. Ele nao contem
versoes implantaveis.

O contrato do candidato combina o resultado do resolvedor com imagens
construídas ou herdadas. Seu schema estrito, exemplo fictício e CLI
offline estao em `ops/releases/candidate-manifest.schema.json`,
`ops/releases/examples/` e `tools/releases/candidate_manifest.py`. O candidato
e explicitamente `deployable: false`: nao e uma release global e nao pode ser
consumido pelo deployer.

`ci.yml` valida e constrói sem push; em push verde para `main`, emite somente o
plano autoritativo. `publish-candidate.yml` está configurado para consumir esse
plano, publicar imagens afetadas e montar o manifesto não implantável com
digests e referências imutáveis verificadas. Candidatos incrementais e documentais são
revalidados contra o candidato anterior selecionado e vinculado ao run,
artifact e SHA. Nenhum workflow foi executado remotamente.

O contrato definitivo é schema v2 e separa plano CI, plano efetivo,
bundle pending, recibo integrado, manifesto final e outcome. Lineage é
selecionada por distância no grafo Git, não por data do run.
O [contrato de releases globais](./RELEASES.md) acrescenta schema, request,
SemVer, BOM implantável, inventários Flyway e gerador offline. O workflow
`publish-release.yml` foi configurado para validar candidato e historia,
publicar draft/assets/tag com compensacao e emitir outcome reconciliado. Ele
ainda nao foi executado remotamente. O runtime publisher local pode despachar e
reconciliar esse contrato quando futuramente receber credenciais autorizadas;
Uma UI publisher estritamente local está implementada no painel de
desenvolvimento. A UI deployer de produção é habilitada somente por runtime
deployer e capability autenticada exata; o deploy remoto continua sem
execução neste workspace.

A S18 acrescenta o contrato offline que transforma uma release global e,
opcionalmente, um estado instalado confirmado em um bundle determinístico de
planejamento. Ele decide o BOM integral, deltas forward-only de migrations e
backup obrigatório sem executar qualquer operação. Consulte
[PLANO_IMPLANTACAO.md](./PLANO_IMPLANTACAO.md). A S19 acrescenta o
[núcleo transacional](./TRANSACAO_IMPLANTACAO.md), com journal durável, lock,
probes, retomada e compensação por adapter injetado. A S20 materializa o
[adapter e CLI locais](./OPERACAO_LOCAL_IMPLANTACAO.md), incluindo backup,
migrations exclusivas, health, rollback e reconciliação de links. O
[workflow e transporte S21](./WORKFLOW_IMPLANTACAO.md) agora configuram a
fronteira autenticada entre GitHub Actions e o CLI S20, ainda sem execução
remota. Bootstrap da VPS, credenciais reais e primeiro deploy continuam
futuros.

A S25 fecha exclusivamente o contrato offline futuro de rollback comercial.
Os quatro artefatos são referências machine-readable reservadas para S26 e
não são consumidos pelo runtime atual; `deployment:rollback` permanece ausente
da capability anunciada e a operação de rollback continua indisponível.

A ponte de identidade local permite que um usuário ERP `ROLE_SYSTEM` troque a
sessão HS512 por um token RS256 curto e específico ao publisher. Ela é opt-in,
publica somente JWKS e é consumida pela UI local sem persistir o bearer
publisher. Isso não representa credencial operacional configurada ou produção.

## BOM comercial

O conjunto comercial completo possui exatamente:

1. `backend`;
2. `website_back`;
3. `frontend`;
4. `website_front`;
5. `whatsapp_service`;
6. `gateway`.

`release_control` e operacional e permanece fora desse BOM. Seu build,
publicacao e atualizacao terao ciclo independente. PostgreSQL, Google,
Firebase e outros sistemas externos sao dependencias de runtime, nao imagens
comerciais.

## Dependencias e fechamento

As dependencias apontam do consumidor para o provedor. Quando um provedor
muda, o resolvedor inclui seus consumidores diretos e transitivos na
revalidacao.

```text
frontend      -> backend
website_back  -> backend
website_front -> website_back
backend       -> whatsapp_service
gateway       -> backend, website_back, frontend, website_front, whatsapp_service
```

`buildComponents` contem somente componentes diretamente alterados.
`validationComponents` contem o fechamento de consumidores. Componentes que
nao forem reconstruidos aparecerao em `inheritedComponents` e, num candidato
futuro, herdarao digests exatos do ultimo conjunto valido. O catalogo nao
armazena tags nem digests.

No primeiro release nao existe conjunto anterior: os seis componentes sao
construidos e validados.

## Politica de paths

- paths de componente selecionam build direto e fechamento de validacao;
- paths globais revalidam os seis sem obrigar rebuild de todos;
- mudancas apenas em `docs/**` ou `README.md` nao selecionam componentes;
- qualquer path desconhecido aplica `fail_closed_all`, com build e validacao
  dos seis e aviso explicito.

Paths absolutos, vazios e com `..` sao rejeitados.
Um prefixo relativo `./` e normalizado sem remover o ponto inicial de nomes
ocultos; por exemplo, `.github/workflows/**` continua sendo path global.

## Imagens Java comprovadas

Os contratos, comandos de build local e criterios de manutencao das imagens
Java estao documentados em
[`../images/JAVA_IMAGES.md`](../images/JAVA_IMAGES.md). A S08 confirmou o
Dockerfile, consumo de opcoes JVM e schemas fiscais do `backend`, que agora
esta `ready`. Tambem confirmou Dockerfile e health do `website_back`; ele
esta `ready`, incluindo a persistencia de uploads confirmada pelo Compose
canonico aceito na S10.

## Imagens Node comprovadas

Os contratos das imagens Node/Nginx, configuracao publica em runtime e
liveness estao em
[`../images/NODE_IMAGES.md`](../images/NODE_IMAGES.md). A S09 confirmou Node
24, testes, builds e health de `frontend`, `website_front` e
`whatsapp_service`; os tres componentes estao `ready`. O website usa
exclusivamente `VITE_WEBSITE_API_URL`/`websiteApiUrl` para sua API, enquanto o
WhatsApp preserva `/data/session` e separa `/health/live` do estado de
autenticacao.

## Gates e readiness

O catalogo esta estruturalmente valido e os seis componentes comerciais estao
`ready`. Os cinco gates que estavam pendentes foram encerrados e aceitos na
S10; `readiness_gates` esta vazio em todos os componentes.

O modo estrutural deve passar:

```bash
python3 tools/releases/catalog.py validate
```

O modo de readiness tambem deve passar:

```bash
python3 tools/releases/catalog.py validate --require-release-ready
```

Uma futura regressao ou novo gate pendente deve fazer esse comando falhar
fechado. O estado verde atual nao autoriza remover validacoes.

Um componente somente pode mudar para `ready` quando, simultaneamente:

- nao possuir gate pendente;
- build e teste estiverem confirmados e tiverem comando;
- health check estiver confirmado, com path (`inferred` nao basta);
- toda persistencia declarada estiver confirmada.

O validador rejeita qualquer combinacao parcial. Readiness tecnico verde nao
significa candidato publicado, release global ou deploy de producao.

## Resolver mudancas

```bash
python3 tools/releases/catalog.py resolve --changed backend/src/main/App.java
python3 tools/releases/catalog.py resolve --changed docs/README.md
python3 tools/releases/catalog.py resolve --first-release
```

A saida JSON e deterministica e usa a ordem canonica do catalogo.

## Manutencao

Para adicionar ou alterar componente, dependencia, path ou gate:

1. atualizar `ops/releases/components.yml`;
2. atualizar `ops/releases/components.schema.json`;
3. preservar um grafo aciclico e o fechamento conservador;
4. preservar as listas canonicas de paths globais, documentais e de cada
   componente, alterando-as somente junto da decisao contratual;
5. adicionar casos positivos e negativos em
   `tools/releases/tests/test_catalog.py`;
6. atualizar este documento;
7. executar validacao estrutural, readiness e todos os testes.

Contrato, schema, resolvedor, testes e documentacao devem mudar juntos. Um
novo componente comercial exige decisao arquitetural explicita; nao deve ser
inferido silenciosamente por path novo.

Existe o contrato e a automação configurada para candidato, ainda sem execução
remota ou candidato publicado. A UI de release publisher é local e a UI
deployer é somente a superfície same-origin descrita em [UI_DEPLOYER.md](./UI_DEPLOYER.md);
nenhum deploy de produção foi executado. O detalhamento operacional está em
[`CANDIDATOS.md`](./CANDIDATOS.md).
## Readiness tecnico apos S10

Os seis componentes comerciais possuem contratos tecnicos `ready`. Para
`gateway`, o build canônico é `docker buildx build --platform linux/amd64
--load` e o teste canônico é `python3 tools/gateway/validate_gateway.py`; seu
health é `/healthz`. O website backend possui persistencia confirmada em
`/app/uploads`. Esse estado não implica CI, manifesto, publicação ou deploy.
