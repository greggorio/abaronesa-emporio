# S06 — Relatorio dos contratos de API, estados e seguranca

> **Estado informado pelo executor:** `IN_PROGRESS`
> **Revisao do orquestrador:** pendente
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Data:** `2026-07-28`

## 1. Metadados

| Campo | Resultado |
|---|---|
| Contrato | `S06-contratos-api-estados-seguranca-release-control.task.md` |
| Dependencias | S01 a S05 `ACCEPTED` |
| Tipo | contrato verificavel, sem runtime |
| Validador | Python 3, PyYAML e biblioteca padrao |
| Estado | `IN_PROGRESS` |

Nenhuma dependencia foi instalada e nenhuma decisao de framework ou
persistencia foi tomada.

## 2. Arquivos criados e alterados

Criados:

- `docs/infrastructure/deployment/release-control/api/publisher.openapi.yml`;
- `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`;
- `docs/infrastructure/deployment/release-control/contracts/state-machines.yml`;
- `docs/infrastructure/deployment/release-control/contracts/security-matrix.yml`;
- `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
- `tools/releases/release_control_contract.py`;
- `tools/releases/tests/test_release_control_contract.py`;
- este relatorio.

Alterado:

- `docs/infrastructure/deployment/release-control/README.md`, somente para
  indexar os contratos e registrar consumidores/decisoes pendentes.

Nenhum arquivo fora do escopo foi alterado.

## 3. Isolamento e paths por modo

`RELEASE_CONTROL_MODE` e configuracao de bootstrap, aceita somente
`publisher` ou `deployer`, imutavel no runtime e nao selecionavel pelo
cliente.

### Comuns

- `GET /health/live`;
- `GET /health/ready`;
- `GET /api/release-control/v1/capabilities`.

Health e publico e retorna somente status generico. Capabilities exige
autenticacao e retorna somente modo, versao da API e capacidades permitidas.

### Publisher

- `GET /api/release-publisher/v1/candidates`;
- `GET /api/release-publisher/v1/releases`;
- `POST /api/release-publisher/v1/releases`;
- `GET /api/release-publisher/v1/releases/{releaseId}/status`.

### Deployer

- `GET /api/deployment-control/v1/current`;
- `GET /api/deployment-control/v1/releases`;
- `GET /api/deployment-control/v1/releases/{releaseId}/plan`;
- `POST /api/deployment-control/v1/deployments`;
- `GET /api/deployment-control/v1/deployments/{deploymentId}`;
- `POST /api/deployment-control/v1/rollbacks`.

Cada OpenAPI 3.1.0 possui conjunto exato e nao contem rota do modo oposto.
Nao ha server de producao, IP, segredo ou `$ref` remoto.

## 4. Requests e respostas

| Mutacao | Request exato | Resultado |
|---|---|---|
| publicar | `candidateId`, `versionBump`, `description`, `changelog` | `PublicationOperation` |
| implantar | `release` | `DeploymentOperation` |
| rollback | `release`, `reason` obrigatorio | `DeploymentOperation` |

Todos os schemas mutaveis usam `additionalProperties: false`. Requests nao
aceitam modo, imagem, digest, tag, componente, comando, path, URL, workflow,
owner, repositorio ou ambiente. A versao final e calculada/reservada no
servidor; o plano de deployment e calculado pela comparacao dos manifestos e
sua lista de componentes e somente informativa.

Schemas de resposta incluem candidatos, release global, instalacao atual,
plano, item de plano, operacoes, capabilities e `ProblemDetails`.
Release usa `vMAJOR.MINOR.PATCH`, commit usa 40 hex, digest exibido usa
`sha256:<64 hex>`, IDs sao opacos, datas sao RFC 3339 e workflow URL e HTTPS.

Paginacao limita cada pagina a no maximo 100 itens. Descricao, changelog,
reason, cursor, IDs e payloads possuem limites. Erros possuem codigos estaveis
e mensagem sanitizada, sem stack trace.

## 5. Maquinas de estado

Contrato: `contracts/state-machines.yml`.

### Elegibilidade

```text
NOT_ELIGIBLE <-> READY
```

### Publicacao

```text
REQUESTED -> VALIDATING -> PUBLISHING -> PUBLISHED
```

Estados nao terminais podem falhar para `FAILED`. `PUBLISHED` e `FAILED` sao
terminais e nao possuem saida.

### Deployment

```text
QUEUED -> PULLING -> BACKING_UP -> MIGRATING
       -> UPDATING -> VERIFYING -> SUCCEEDED
```

`AVAILABLE` descreve release elegivel. Falha antes da mutacao pode ir a
`FAILED`; depois da mutacao entra em `ROLLING_BACK`, que termina em
`ROLLED_BACK` ou `FAILED`.

Transicoes para `PUBLISHED`, `SUCCEEDED` e `ROLLED_BACK` exigem evidencia
remota. Estados terminais nao possuem saida; somente `reconciler` ou
`internal_request` autorizado aparecem como atores. Cliente nao envia estado.

O contrato declara para cada estado terminalidade, atividade, sucesso, falha
e visibilidade na UI.

## 6. Idempotencia e concorrencia

Os tres POSTs exigem `Idempotency-Key` com formato/tamanho limitado.

- escopo: modo, rota, ator autenticado e chave;
- persistencia futura: hash canonico do request e hash da chave;
- mesma chave/request retorna operacao existente;
- chave igual com request diferente retorna `409 IDEMPOTENCY_CONFLICT`;
- retry nao inicia segundo workflow;
- timeout do cliente nao implica falha;
- resposta informa replay;
- chave nao e ID publico;
- retencao definitiva permanece pendente.

Publicacao possui lock unico por versao semantica.

Deployment e rollback compartilham `production_global`, com maximo de uma
operacao ativa. Nova solicitacao nao cancela a atual e retorna
`PRODUCTION_OPERATION_ACTIVE`. Lock transacional local e futura concurrency de
workflow sao complementares.

## 7. Reconciliacao

O registro contratual exige IDs de operacao/workflow, tipo, modo, estado,
ator, hashes, release alvo e timestamps. Campos aplicaveis incluem commit,
inicio/fim e erro sanitizado. Token GitHub nunca e persistido.

Tecnologia de persistencia permanece pendente.

Operacao nao terminal exige `workflowRunId` e e reconciliada apos reinicio.
Evidencia remota ausente ou inconsistente falha fechado e gera auditoria.
Sucesso exige conclusao remota e validacao do artefato/ambiente. Estado
terminal nao regride. A UI le estado local reconciliado.

## 8. Autenticacao, roles e credenciais

Toda rota `/api/` exige bearer JWT. Health permanece publico. Issuer,
audience, allowlist de algoritmo e rotacao sao configuracoes futuras
obrigatorias. Modo nao vem de claim.

| Modo | Role | Autoridade |
|---|---|---|
| publisher | `release:read` | candidates, releases, status, capabilities |
| publisher | `release:publish` | POST release |
| deployer | `deployment:read` | current, releases, plan, status, capabilities |
| deployer | `deployment:execute` | POST deployment |
| deployer | `deployment:rollback` | POST rollback |

Credenciais outbound sao isoladas:

- publisher: leitura de commits/checks/artefatos e dispatch de publicacao;
- deployer: leitura de releases/manifestos e dispatch de producao;
- VPS: somente leitura de packages;
- build: escrita de packages no job autorizado.

Publisher nao possui producao; deployer nao cria tag/release. GitHub App de
privilegio minimo e preferida; token fine-grained inicial permanece pendente.
Credenciais entram por ambiente/secret store e nao aparecem em log, resposta,
persistencia ou arquivo rastreavel.

## 9. Matriz de ameacas e proibicoes

O contrato machine-readable exige:

- rotas e roles exatas por modo;
- credencial outbound coerente;
- modo nao controlavel pelo cliente;
- nenhuma selecao por componente;
- nenhum Git local;
- nenhum Docker socket;
- nenhum SSH direto;
- nenhum workflow/repositorio/URL arbitrario;
- nenhum segredo em log;
- CORS por allowlist;
- rate limit para mutacao e polling;
- limite de payload;
- somente `application/json`;
- rejeicao de content type inesperado;
- auditoria de toda mutacao;
- erros sanitizados.

## 10. Decisoes pendentes

- framework e linguagem do modulo;
- tecnologia de persistencia, migration e enforcement transacional;
- provedor JWT concreto;
- GitHub App ou token fine-grained inicial;
- retencao de idempotencia e historico de operacoes;
- limites numericos de rate/payload;
- CORS allowlist por ambiente.

Nenhuma pendencia enfraquece isolamento, idempotencia, lock ou fail-closed.

## 11. Validador local

Comando:

```bash
python3 tools/releases/release_control_contract.py validate
```

Resultado: codigo `0`, `release-control-contract:valid`.

O validador confirma:

- YAML e OpenAPI 3.1;
- paths exatos e isolamento por modo;
- bearer auth/health publico;
- capability do modo correto;
- ID semantico e enums de estados;
- idempotencia e requests fechados;
- fields proibidos;
- estados/transicoes/main flows/terminais;
- evidencia remota nas transicoes de sucesso;
- bootstrap, locks e reconciliacao;
- matriz de roles e credenciais;
- proibicoes de Git/Docker/SSH/selecao;
- existencia da documentacao referenciada.

Contrato invalido ou input invalido retorna codigo `2`. O validador nao usa
rede.

## 12. Testes

Comando exato:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_release_control_contract.py' \
  -v
```

Resultado final: codigo `0`; **41 testes aprovados**, zero falhas e zero
erros.

Os 35 casos minimos foram cobertos. Casos adicionais validam ator cruzado,
modo controlavel pelo cliente, estado de operacao divergente, transicao de
sucesso sem evidencia remota e `workflowRunId` ausente do registro.

Mutantes existiram somente em memoria. Nenhum arquivo de contrato real foi
alterado pelos testes.

## 13. Documentacao

- dois OpenAPI autocontidos e separados;
- estados e matriz de seguranca machine-readable;
- `CONTRATO_API_ESTADOS_SEGURANCA.md` descreve uso, limites e pendencias;
- `release-control/README.md` indexa a entrega e seus futuros consumidores.

Nenhum documento afirma que servico, UI, workflow ou integracao existe.

## 14. Estado Git, workflows e caches

| Verificacao | Resultado |
|---|---|
| `git ls-files --stage` | zero entradas |
| `git rev-parse --verify HEAD` | codigo 128; HEAD inexistente |
| `git tag --list` | zero tags |
| `git reflog show --all` | zero entradas |
| workflow YAML em `.github/workflows/` | zero |
| `__pycache__`/`.pyc` em `tools/releases` | zero |

Nao houve `git add`, commit, tag ou push.

## 15. Comandos relevantes

| Comando | CWD | Codigo | Resultado |
|---|---|---:|---|
| leitura de task/revisoes/contrato | raiz | 0 | precondicoes confirmadas |
| primeira validacao local | raiz | 2 | erro sintatico YAML localizado na matriz |
| validacao apos quoting dos paths parametrizados | raiz | 0 | contrato valido |
| primeira suite completa | raiz | 0 | 38/38 |
| suite ampliada final | raiz | 0 | 41/41 |
| validacao final | raiz | 0 | contrato valido |
| verificacoes Git/workflow/cache | raiz | 0/128 esperado | estado protegido |

## 16. Desvios, itens nao determinados e bloqueios

Desvio:

- a primeira validacao encontrou paths parametrizados com chaves sem quoting
  em flow mappings YAML da matriz. Os tres paths foram somente colocados entre
  aspas; nenhuma regra foi relaxada.

Itens nao determinados: as decisoes tecnicas listadas na Secao 10.

Bloqueios para a S06: nenhum.

## 17. Declaracao do que nao foi executado

Nao houve:

- modulo executavel `release_control`;
- escolha de framework, linguagem runtime ou persistencia;
- banco, migration, ORM ou tabela;
- endpoint, UI, cliente GitHub ou workflow real;
- Git local, shell remoto, Docker socket ou SSH no futuro servico;
- criacao de workflow YAML, Dockerfile, Compose ou Nginx;
- instalacao de dependencia ou acesso de rede;
- `git add`, indice temporario, commit, tag ou push;
- acesso a GitHub, GHCR, DNS ou VPS;
- alteracao da task S06 ou do tracker.

## 18. Resposta final do executor

- OpenAPI publisher e deployer separados;
- estados, idempotencia, lock e reconciliacao formalizados;
- seguranca/roles/credenciais separadas e fail-closed;
- validador local codigo `0`;
- 41 testes aprovados;
- pendencias tecnicas explicitas, sem escolha antecipada;
- indice, HEAD, tags, reflog, workflows e caches vazios.

> **Estado final do executor:** `IN_PROGRESS` — aguardando revisao do
> orquestrador. O executor nao declara `ACCEPTED`.

---

## 19. Revisao do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS — CORRECOES REQUERIDAS`  
> **Data:** `2026-07-28`

A S06 permanece em andamento. O conjunto entregue cobre a maior parte do
contrato, mas ainda possui divergencias que afetam consumidores futuros e o
carater fail-closed do validador.

### 19.1 Achados bloqueantes

1. **O conflito de operacao global ativa nao consegue representar a resposta
   prometida.**

   O contrato da task exige que `409 PRODUCTION_OPERATION_ACTIVE` possa
   retornar referencia opaca a operacao ativa, quando o ator estiver
   autorizado. O documento humano repete essa regra. Entretanto, a resposta
   `409` referencia `ProblemDetails`, que usa `additionalProperties: false` e
   nao declara `activeOperationId`. Esse campo foi colocado em
   `DeploymentOperation`, isto e, na resposta normal `202/200`, onde nao
   resolve o conflito.

   Corrigir os dois POSTs do deployer para que o schema do `409` admita
   explicitamente a referencia opaca autorizada. Remover
   `activeOperationId` da operacao normal, salvo se existir outra necessidade
   contratual documentada. Preservar erros fechados e a regra de nao expor a
   referencia a ator sem autorizacao.

2. **`GlobalReleaseDetail` do publisher e insatisfativel.**

   O schema combina por `allOf`:

   - `GlobalReleaseSummary`, que ja rejeita propriedades adicionais; e
   - outro objeto fechado que declara apenas `manifestSchemaVersion` e
     `componentDigests`.

   Assim, a primeira parte rejeita os campos de detalhe e a segunda rejeita
   os campos do resumo. Nenhum payload completo consegue satisfazer as duas
   partes simultaneamente. Substituir a composicao por schema fechado
   semanticamente valido, preferencialmente plano e explicito, ou usar
   composicao compativel com JSON Schema 2020-12/OpenAPI 3.1 sem produzir
   essa contradicao.

3. **O validador aceita `Idempotency-Key` invalida quando ela e referenciada.**

   `_has_required_idempotency` considera a mera presenca do `$ref`
   suficiente. Se `components.parameters.IdempotencyKey.required` mudar para
   `false`, ou se nome/local/formato/limites divergirem, o contrato continua
   sendo declarado valido. O validador deve resolver e verificar o parametro
   referenciado, incluindo:

   - nome `Idempotency-Key`;
   - local `header`;
   - `required: true`;
   - limites e pattern canônicos.

   Adicionar mutantes que comprovem a rejeicao de pelo menos `required:
   false` e deriva de formato/limite.

4. **As propriedades obrigatorias das maquinas de estado nao estao protegidas
   pelo validador.**

   Embora o YAML real declare `terminal`, `active`, `success`, `failure` e
   `ui_visible`, o validador aceita a remocao ou alteracao arbitraria desses
   metadados. A task exige que essa classificacao seja parte do contrato e
   que a validacao seja fail-closed.

   Validar a presenca booleana das cinco propriedades em cada estado, suas
   classificacoes canônicas e, no minimo, as invariantes:

   - estado terminal nao e ativo;
   - estado de sucesso nao e de falha;
   - estados terminais permanecem sem saida;
   - `AVAILABLE` nao e operacao ativa;
   - `PUBLISHED` e `SUCCEEDED` sao sucessos terminais;
   - `FAILED` e falha terminal;
   - `ROLLED_BACK` preserva a semantica aprovada para a operacao compensada.

   Adicionar mutantes para metadado ausente e classificacao incoerente.

5. **Partes relevantes da matriz de seguranca nao participam da decisao
   fail-closed.**

   O validador confere algumas roles e proibicoes, mas hoje aceita, por
   exemplo, desligar `mutation_audit`, trocar `cors`, remover exigencia de
   issuer/audience/allowlist de algoritmo ou permitir content type
   inesperado. Validar semanticamente os campos de autenticacao e transporte
   que a propria S06 congelou como obrigatorios, com testes mutantes
   representativos.

### 19.2 Preservacoes obrigatorias

- nao implementar runtime, framework, persistencia, UI ou integracao GitHub;
- nao criar workflow, Dockerfile, Compose, Nginx ou manifesto global;
- nao instalar dependencias nem acessar rede/VPS;
- alterar somente arquivos permitidos pela task S06 e este relatorio;
- manter indice Git vazio, `HEAD` inexistente e ausencia de commit, tag e
  push;
- nao alterar a task S06 nem o tracker;
- executar novamente o validador e somente a suite prescrita da S06;
- registrar CWD, comandos exatos, codigos de saida, quantidade final de
  testes e arquivos alterados;
- manter o estado final do executor como `IN_PROGRESS`.

### 19.3 Criterio para nova revisao

O proximo relatorio deve responder cada um dos cinco achados, mostrar os
novos mutantes e registrar:

```bash
python3 tools/releases/release_control_contract.py validate

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_release_control_contract.py' \
  -v
```

A S07 nao sera criada enquanto estes contratos permanecerem divergentes.

---

## 20. Resposta as correcoes do ciclo 1

> **Estado do executor:** `IN_PROGRESS`  
> **Data:** `2026-07-28`  
> **CWD:** `/home/gregorio/git/baronesa/emporio`

### 20.1 Resposta individual aos cinco achados

1. **Resposta `409` e `activeOperationId`: corrigido.**
   Os dois POSTs do deployer agora usam o schema fechado
   `DeploymentConflictProblem`. Ele admite `activeOperationId` como referencia
   opaca opcional somente para `PRODUCTION_OPERATION_ACTIVE` e ator autorizado.
   O campo foi removido de `DeploymentOperation`. O schema tambem representa
   `IDEMPOTENCY_CONFLICT` sem exigir ou expor a referencia.

2. **`GlobalReleaseDetail` do publisher: corrigido.**
   A composicao contraditoria por `allOf` foi substituida por objeto plano,
   fechado e explicito. Os seis campos do resumo e do detalhe pertencem ao
   mesmo schema e sao obrigatorios.

3. **`Idempotency-Key` referenciada: corrigido.**
   O validador resolve `#/components/parameters/IdempotencyKey` e exige
   exatamente nome `Idempotency-Key`, local `header`, `required: true`, tipo
   string, limites 16/128 e pattern canonico. Mutantes cobrem `required:
   false`, pattern permissivo e limite divergente.

4. **Metadados das maquinas de estado: corrigido.**
   Cada estado agora precisa conter exatamente cinco flags booleanas:
   `terminal`, `active`, `success`, `failure` e `ui_visible`. O validador
   protege as classificacoes canonicas e as invariantes solicitadas, incluindo
   `AVAILABLE`, `PUBLISHED`, `SUCCEEDED`, `FAILED` e `ROLLED_BACK`. Mutantes
   cobrem metadado ausente e classificacoes incoerentes.

5. **Matriz de seguranca fail-closed: corrigido.**
   O validador congela semanticamente todo o bloco `authentication` e todo o
   bloco `transport`. Mutantes cobrem issuer, audience, allowlist de algoritmo,
   CORS, content type inesperado e auditoria de mutacoes.

### 20.2 Arquivos alterados no ciclo

- `docs/infrastructure/deployment/release-control/api/publisher.openapi.yml`;
- `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`;
- `tools/releases/release_control_contract.py`;
- `tools/releases/tests/test_release_control_contract.py`;
- este relatorio.

Nao foram alterados `state-machines.yml` nem `security-matrix.yml`: seus valores
ja eram canonicos; a correcao necessaria foi faze-los participar integralmente
da decisao fail-closed.

### 20.3 Validacoes prescritas

Ambos os comandos foram executados em
`/home/gregorio/git/baronesa/emporio`.

```bash
python3 tools/releases/release_control_contract.py validate
```

Codigo de saida `0`; resultado:
`release-control-contract:valid`.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_release_control_contract.py' \
  -v
```

Codigo de saida `0`; **58 testes aprovados**, zero falhas e zero erros. Os
testes 42 a 58 sao os novos mutantes e verificacoes direcionados aos cinco
achados.

### 20.4 Estado Git, workflows e caches

Comandos executados no mesmo CWD:

| Comando exato | Codigo | Resultado |
|---|---:|---|
| `git ls-files --stage` | 0 | nenhuma entrada; indice real vazio |
| `git rev-parse --verify HEAD` | 128 | `HEAD` inexistente |
| `git tag --list` | 0 | nenhuma tag |
| `git reflog` | 128 | branch `main` ainda sem commits; nenhuma entrada |
| `find .github/workflows -type f \( -name '*.yml' -o -name '*.yaml' \)` | 0 | nenhum workflow YAML |
| `find tools/releases -type d -name __pycache__ -o -type f -name '*.pyc'` | 0 | nenhum cache Python |

Nao houve `git add`, commit, tag, push, instalacao de dependencia ou acesso
externo. Nao foram implementados runtime, framework, persistencia, workflow,
Docker, Compose, UI ou integracao externa. A task S06 e o tracker nao foram
alterados.

### 20.5 Estado final do ciclo

As cinco correcoes bloqueantes foram implementadas sem ampliar o escopo. Nao
ha bloqueio tecnico novo registrado pelo executor.

> **Estado final do executor:** `IN_PROGRESS` — aguardando nova revisao do
> orquestrador. O executor nao declara `ACCEPTED`.

---

## 21. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-28`

A S06 atende ao contrato apos o ciclo corretivo.

Evidencias aceitas:

- publisher e deployer permanecem isolados em OpenAPI 3.1 autocontidos;
- o `409` de deployment e rollback usa schema fechado proprio e pode
  representar `activeOperationId` somente sob a regra de autorizacao
  documentada;
- a operacao normal nao expoe a referencia de uma operacao concorrente;
- `GlobalReleaseDetail` do publisher e plano, fechado e satisfativel;
- `Idempotency-Key` referenciada e validada por nome, local, obrigatoriedade,
  tipo, limites e pattern canonico;
- os cinco metadados de cada estado possuem classificacao canonica protegida
  por validacao;
- terminais, sucessos, falhas, `AVAILABLE` e `ROLLED_BACK` preservam a
  semantica aprovada;
- autenticacao e transporte da matriz de seguranca participam integralmente
  da decisao fail-closed;
- os 17 novos mutantes cobrem os cinco achados do ciclo 1;
- o validador final registrado terminou com codigo `0`;
- a suite final registrada passou em 58 de 58 testes;
- task, tracker, indice Git, `HEAD`, tags, reflog, ausencia de workflow e
  ausencia de cache Python foram preservados pelo executor;
- nao houve commit, push, instalacao ou acesso externo.

Os estados `IN_PROGRESS` anteriores permanecem como historico. A autoridade
final desta secao altera o estado da S06 para `ACCEPTED`.

Durante a preparacao da proxima etapa foram identificados defaults sensiveis
e dados legados em inicializadores Java, alem de log de valores de
configuracao. Esse achado nao pertence ao contrato da S06, mas bloqueia com
seguranca o inicio dos Dockerfiles. A S07 tratara exclusivamente esse
saneamento residual; os Dockerfiles Java serao retomados na S08.
