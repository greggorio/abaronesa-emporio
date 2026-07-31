# S06 — Contratos de API, estados e seguranca do release control

> **Estado:** `ACCEPTED` — `2026-07-28`  
> **Tipo:** contrato executavel e documentacao de seguranca  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S05 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Contrato de componentes:** [release-control/README.md](../../release-control/README.md)  
> **Relatorio de saida:** `S06-contratos-api-estados-seguranca-release-control.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretório indicado. Leia primeiro este
contrato, a arquitetura aprovada, a revisão final da S05 e o contrato de
componentes.

Esta slice define contratos verificáveis. Ela não cria o módulo executável
`release_control`, não escolhe framework, não cria banco e não implementa UI
ou integração real com GitHub.

Não altere esta task nem o tracker
`docs/infrastructure/deployment/implementation/README.md`.

## Objetivo observavel

Ao final existirão:

- dois contratos OpenAPI 3.1 separados, um por modo;
- contrato machine-readable de estados e transições;
- matriz machine-readable de autorização e credenciais;
- contrato documental de API, idempotência, concorrência, reconciliação e
  segurança;
- validador local fail-closed;
- testes positivos e negativos;
- nenhuma rota do modo oposto registrada em cada OpenAPI;
- nenhuma mutação que aceite imagem, digest, tag, componente ou comando;
- estado Git e ausência de workflows preservados.

## Fronteira aprovada

Uma única base de código futura poderá executar em exatamente um modo:

```text
RELEASE_CONTROL_MODE=publisher
RELEASE_CONTROL_MODE=deployer
```

O modo:

- é configuração de bootstrap;
- não pode vir do navegador;
- não pode ser selecionado por query, header, cookie ou body;
- não pode mudar durante o runtime;
- determina quais routers são registrados.

Ocultar opção na UI não constitui isolamento.

## Rotas exatas

### Comuns aos dois modos

Definir:

```text
GET /health/live
GET /health/ready
GET /api/release-control/v1/capabilities
```

Regras:

- health não revela configuração, credencial ou detalhes internos;
- capabilities exige autenticação;
- capabilities retorna somente modo, versão da API e capacidades permitidas;
- resposta não contém token, URL privada ou segredo.

### Publisher

Definir somente no contrato publisher:

```text
GET  /api/release-publisher/v1/candidates
GET  /api/release-publisher/v1/releases
POST /api/release-publisher/v1/releases
GET  /api/release-publisher/v1/releases/{releaseId}/status
```

O POST deve receber exatamente:

```text
candidateId
versionBump: MAJOR | MINOR | PATCH
description
changelog
```

Regras:

- versão final é calculada/reservada no servidor;
- cliente não envia tag, digest, imagem ou versão final arbitrária;
- candidato precisa estar elegível e vinculado a commit remoto da `main`;
- CI e manifesto candidato precisam estar verdes;
- descrição é obrigatória;
- changelog possui limites documentados;
- campos adicionais são rejeitados.

### Deployer

Definir somente no contrato deployer:

```text
GET  /api/deployment-control/v1/current
GET  /api/deployment-control/v1/releases
GET  /api/deployment-control/v1/releases/{releaseId}/plan
POST /api/deployment-control/v1/deployments
GET  /api/deployment-control/v1/deployments/{deploymentId}
POST /api/deployment-control/v1/rollbacks
```

Request de deployment deve aceitar exatamente:

```json
{
  "release": "v1.4.0"
}
```

Request de rollback deve aceitar:

```text
release
reason
```

`reason` é obrigatório para auditoria. O target precisa ser release global
anterior e elegível.

Regras:

- campos adicionais são rejeitados;
- requests não aceitam componentes individuais;
- requests não aceitam imagem, digest, tag avulsa, comando, path, URL ou
  variável de ambiente;
- plano é calculado no servidor pela comparação de manifestos;
- lista de componentes do plano é somente informativa.

## Identificadores e payloads

Definir schemas reutilizáveis, no mínimo, para:

- `CandidateSummary`;
- `GlobalReleaseSummary`;
- `GlobalReleaseDetail`;
- `CurrentInstallation`;
- `DeploymentPlan`;
- `ComponentPlanItem`;
- `PublicationOperation`;
- `DeploymentOperation`;
- `CapabilityResponse`;
- `ProblemDetails`;
- requests mutáveis.

Regras:

- `releaseId` usa versão semântica `vMAJOR.MINOR.PATCH`;
- commit usa SHA hexadecimal completo;
- IDs de operação são opacos e não sequenciais;
- datas usam UTC/RFC 3339;
- URLs de workflow são apenas HTTPS;
- digests, quando exibidos em respostas, usam `sha256:<64 hex>`;
- nenhum request aceita digest;
- todos os objetos mutáveis usam `additionalProperties: false`;
- paginação e filtros possuem limites explícitos;
- erros usam códigos estáveis e não expõem stack trace.

## Idempotencia

Os três POSTs exigem:

```text
Idempotency-Key
```

Contrato:

- chave com tamanho e formato limitados;
- escopo composto por modo, rota, ator autenticado e chave;
- servidor persiste hash canônico do request, nunca segredo;
- mesma chave e mesmo request retornam a operação existente;
- mesma chave com request diferente retorna `409 IDEMPOTENCY_CONFLICT`;
- retry não dispara segundo workflow;
- timeout do cliente não implica falha da operação;
- resposta diferencia criação nova de replay idempotente;
- retenção mínima e limpeza serão configuráveis; valor definitivo fica
  explicitamente pendente, sem enfraquecer a unicidade enquanto retido.

Não usar a chave como ID público da operação.

## Concorrencia

Definir:

- uma única publicação pode reservar a mesma versão semântica;
- uma única operação de produção pode estar ativa;
- deployment e rollback compartilham o mesmo lock global de produção;
- nova solicitação não cancela operação ativa;
- conflito retorna `409` com código estável e referência à operação ativa,
  quando autorizada;
- lock local e `concurrency` do workflow são camadas complementares;
- sucesso só ocorre depois da confirmação final remota e validação do
  artefato/ambiente;
- enforcement transacional será responsabilidade da implementação futura.

## Maquinas de estado

Criar contrato machine-readable contendo máquinas separadas.

### Elegibilidade de candidato

Estados:

```text
NOT_ELIGIBLE
READY
```

### Publicacao

Estados:

```text
REQUESTED
VALIDATING
PUBLISHING
PUBLISHED
FAILED
```

Fluxo principal:

```text
REQUESTED -> VALIDATING -> PUBLISHING -> PUBLISHED
```

Falha pode ocorrer a partir de estado não terminal para `FAILED`.

### Deployment

Estados:

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

`AVAILABLE` é estado de release elegível, não operação ativa.

Fluxo principal da operação:

```text
QUEUED
-> PULLING
-> BACKING_UP
-> MIGRATING
-> UPDATING
-> VERIFYING
-> SUCCEEDED
```

Regras:

- falha antes de mutação pode terminar em `FAILED`;
- falha depois do início de mutação deve entrar em `ROLLING_BACK`, salvo
  impossibilidade classificada;
- `ROLLING_BACK` termina em `ROLLED_BACK` ou `FAILED`;
- terminais não possuem transição de saída;
- transições inválidas falham fechado;
- somente reconciliador interno pode avançar estado por evidência do workflow;
- cliente não envia estado desejado.

O contrato deve declarar quais estados são:

- terminais;
- ativos;
- de sucesso;
- de falha;
- visíveis na UI.

## Reconciliação

Definir registro mínimo de operação:

```text
operationId
operationType
mode
state
actorId
idempotencyKeyHash
requestHash
targetRelease
sourceCommit, quando aplicável
workflowRunId
workflowRunUrl
createdAt
updatedAt
startedAt
finishedAt
errorCode
errorMessageSanitized
```

Regras:

- tecnologia de persistência permanece não determinada;
- tokens GitHub nunca são persistidos no registro;
- após reinício, operações não terminais são reconciliadas pelo
  `workflowRunId`;
- ausência ou inconsistência de evidência remota não vira sucesso;
- operação terminal não regride;
- estado local e remoto divergentes geram falha fechada/auditoria;
- UI consulta estado local reconciliado, não inventa sucesso.

## Autenticacao e autorizacao

Os OpenAPI devem usar bearer JWT como contrato de entrada, sem definir ainda o
provedor concreto.

Definir roles lógicas distintas:

```text
release:read
release:publish
deployment:read
deployment:execute
deployment:rollback
```

Matriz mínima:

- publisher read: candidates, releases, status e capabilities publisher;
- publisher publish: POST release;
- deployer read: current, releases, plan, deployment status e capabilities
  deployer;
- deployer execute: POST deployment;
- deployer rollback: POST rollback;
- role de um modo não concede rota do outro porque a rota não existe.

Regras:

- todas as rotas `/api/` exigem autenticação;
- health endpoints não exigem autenticação e não expõem detalhes;
- issuer, audience, algoritmo e rotação são configurações obrigatórias futuras;
- token expirado, issuer/audience inválidos ou role ausente falham fechado;
- modo não é derivado de claim;
- não documentar segredo JWT literal.

## Credenciais de saida

Definir perfis separados:

### Publisher

- ler commits/checks;
- ler candidatos/artefatos;
- disparar somente workflow de publicação;
- não possuir permissão de produção.

### Deployer

- ler releases/manifestos;
- disparar somente workflow de produção;
- não criar tag ou release.

### VPS

- leitura de imagens GHCR;
- sem permissão de escrita.

### Build

- escrita de packages somente no job autorizado.

Regras:

- credenciais não são compartilhadas entre modos;
- GitHub App de privilégio mínimo é preferida;
- token fine-grained permanece alternativa inicial explicitamente pendente;
- token não aparece em log, resposta, banco ou arquivo rastreável;
- credenciais são injetadas por ambiente/secret store;
- nenhum modo acessa Docker socket;
- nenhum modo abre SSH diretamente;
- cliente não fornece owner, repository, workflow ou URL arbitrários.

## Matriz de seguranca

Criar contrato machine-readable cobrindo:

- modos permitidos;
- rotas por modo;
- roles por operação;
- perfil de credencial de saída;
- recursos proibidos;
- campos proibidos em requests;
- ausência de Docker socket;
- ausência de Git local;
- ausência de SSH;
- ausência de seleção por componente;
- redaction de logs;
- CORS por allowlist configurada;
- rate limit para mutações e polling;
- limites de payload;
- rejeição de content type inesperado;
- auditabilidade das mutações.

## Contratos OpenAPI separados

Criar:

```text
docs/infrastructure/deployment/release-control/api/publisher.openapi.yml
docs/infrastructure/deployment/release-control/api/deployer.openapi.yml
```

Cada arquivo deve:

- usar OpenAPI `3.1.0`;
- ser autocontido, sem `$ref` remoto;
- conter apenas rotas comuns e do próprio modo;
- declarar bearer auth;
- declarar `Idempotency-Key` nos POSTs;
- documentar `200`, `202`, `400`, `401`, `403`, `404`, `409`, `422`, `429`
  e `500` conforme aplicável;
- usar schemas com propriedades fechadas;
- conter exemplos fictícios sem segredo;
- não declarar server de produção, IP ou credencial real.

## Artefatos machine-readable

Criar:

```text
docs/infrastructure/deployment/release-control/contracts/state-machines.yml
docs/infrastructure/deployment/release-control/contracts/security-matrix.yml
```

Também criar documento humano:

```text
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
```

Atualizar:

```text
docs/infrastructure/deployment/release-control/README.md
```

O README deve distinguir:

- contrato entregue;
- implementação ainda ausente;
- decisões ainda pendentes;
- próximos consumidores: backend do `release_control`, frontend e workflows.

## Validador local

Criar:

```text
tools/releases/release_control_contract.py
```

Reutilizar somente Python 3, PyYAML, jsonschema e biblioteca padrão já
declarados em `tools/releases/requirements.txt`.

O comando:

```bash
python3 tools/releases/release_control_contract.py validate
```

deve validar, no mínimo:

- YAML parseável e raiz esperada;
- OpenAPI 3.1;
- conjuntos exatos de paths por modo;
- ausência de path do modo oposto;
- conjunto comum exato;
- autenticação em toda rota `/api/`;
- health sem payload sensível;
- `Idempotency-Key` obrigatório nos três POSTs;
- requests mutáveis fechados;
- ausência de campos proibidos;
- enums de versão e estados;
- state machines completas;
- transições para estados existentes;
- terminais sem saída;
- fluxo principal alcançável;
- ausência de transição publisher/deployer cruzada;
- matriz de segurança coerente com os OpenAPI;
- credenciais de saída distintas;
- proibições de Git, Docker socket, SSH e seleção de componentes;
- documentação e artefatos referenciados existentes.

Exit codes:

- `0`: contratos válidos;
- `2`: contrato inválido ou input inválido;

Não acessar rede.

## Testes

Criar:

```text
tools/releases/tests/test_release_control_contract.py
```

Usar `unittest` e catálogos mutantes apenas em memória ou `/tmp`.

Cobrir, no mínimo:

1. contrato real válido;
2. path publisher ausente;
3. path publisher extra;
4. path deployer ausente;
5. path deployer extra;
6. rota publisher no deployer;
7. rota deployer no publisher;
8. API sem bearer auth;
9. health expondo campo sensível;
10. POST sem idempotency key;
11. request permitindo propriedades adicionais;
12. request contendo `digest`;
13. request contendo `image`;
14. request contendo `component`;
15. request contendo `command`;
16. version bump fora do enum;
17. release ID inválido;
18. estado ausente;
19. transição para estado desconhecido;
20. terminal com saída;
21. fluxo principal publisher quebrado;
22. fluxo principal deployment quebrado;
23. matriz com role incorreta;
24. credencial compartilhada entre modos;
25. Docker socket permitido;
26. Git local permitido;
27. SSH direto permitido;
28. seleção por componente permitida;
29. capability retornando modo incorreto;
30. rollback sem reason;
31. deployment com campo extra;
32. idempotency conflict documentado;
33. lock único de produção documentado;
34. reconciliação sem workflowRunId rejeitada;
35. documentação principal referenciada.

Pode adicionar casos além desses.

Executar:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_release_control_contract.py' \
  -v
```

## Escopo de escrita

Pode criar ou alterar somente:

```text
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
docs/infrastructure/deployment/release-control/api/publisher.openapi.yml
docs/infrastructure/deployment/release-control/api/deployer.openapi.yml
docs/infrastructure/deployment/release-control/contracts/state-machines.yml
docs/infrastructure/deployment/release-control/contracts/security-matrix.yml
tools/releases/release_control_contract.py
tools/releases/tests/test_release_control_contract.py
docs/infrastructure/deployment/implementation/slices/S06-contratos-api-estados-seguranca-release-control.report.md
```

Nenhum outro arquivo pode ser alterado.

## Fora de escopo

Não implementar:

- diretório executável `release_control/`;
- escolha de Java, Python, Node ou outro framework;
- banco, migration, ORM ou tabela;
- cliente GitHub real;
- GitHub App ou token;
- workflow YAML;
- manifesto global;
- candidato ou release;
- UI publisher ou deployer;
- integração com frontend atual;
- Dockerfile, Compose, Nginx ou VPS;
- endpoint real;
- Git local, shell ou Docker socket;
- commit, tag ou push.

Não instalar dependências nem acessar rede.

## Validacoes obrigatorias

Executar e registrar:

```bash
python3 tools/releases/release_control_contract.py validate

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_release_control_contract.py' \
  -v
```

Também registrar:

```bash
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog
find .github/workflows -type f \( -name '*.yml' -o -name '*.yaml' \)
find tools/releases -type d -name __pycache__ -o -type f -name '*.pyc'
```

## Evidencia obrigatoria

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S06-contratos-api-estados-seguranca-release-control.report.md
```

O relatório deve conter:

1. metadados, data e CWD;
2. arquivos criados/alterados;
3. paths por modo e paths comuns;
4. requests e respostas mutáveis;
5. máquinas de estado e transições;
6. idempotência e concorrência;
7. reconciliação;
8. autenticação, roles e credenciais de saída;
9. matriz de ameaças/proibições;
10. decisões pendentes explícitas;
11. validador e invariantes;
12. testes com comando, código, quantidade e resultado;
13. documentação criada;
14. estado Git, workflows e caches;
15. comandos relevantes com CWD, código, resultado e interpretação;
16. desvios, itens não determinados e bloqueios;
17. declaração de que não houve implementação runtime, instalação, commit,
    push ou acesso externo;
18. resposta final solicitada ao CLI.

## Criterios de aceite

- OpenAPI publisher e deployer são separados.
- Cada modo contém somente suas rotas.
- Modo não é controlável pelo cliente.
- Todos os POSTs exigem idempotência.
- Requests não aceitam overrides operacionais.
- Estados e transições são completos e fail-closed.
- Terminais não possuem saída.
- Deployment/rollback compartilham exclusão mútua.
- Reconciliação não inventa sucesso.
- Roles são mínimas e separadas.
- Credenciais outbound são separadas por função.
- Git, Docker socket e SSH direto estão proibidos.
- Validador local passa.
- Todos os testes passam.
- Documentação corresponde aos contratos.
- Nenhuma implementação runtime é afirmada.
- Estado Git e ausência de workflows permanecem preservados.
- Nenhum arquivo fora do escopo é alterado.

## Condicoes de bloqueio

Interrompa e registre `BLOCKED` se:

- for necessário escolher framework ou persistência para definir o contrato;
- publisher e deployer precisarem compartilhar rota mutável;
- request precisar aceitar imagem, digest, componente ou comando;
- modo precisar ser fornecido pelo cliente;
- segurança depender de credencial compartilhada;
- validação exigir schema remoto ou acesso de rede;
- for necessário alterar aplicação, workflow, Docker ou Git;
- houver ambiguidade que mude autoridade entre UI, API e GitHub Actions.

## Resposta final esperada do CLI

Responder de forma concisa com:

- caminho absoluto do relatório;
- artefatos criados;
- paths por modo;
- resultado do validador;
- quantidade e resultado dos testes;
- resumo de idempotência, concorrência e estados;
- decisões pendentes;
- estado Git/workflows/caches;
- bloqueios;
- estado `IN_PROGRESS`, aguardando revisão do orquestrador.

Não declarar `ACCEPTED`.
