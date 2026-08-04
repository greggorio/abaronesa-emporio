# Contrato de API, estados e seguranca do release control

> **Estado:** contrato verificável e runtimes publisher e deployer implementados
> localmente; integrações remotas ainda não executadas.

## Isolamento por modo

Cada instancia inicia em exatamente um modo por configuracao de
bootstrap:

```text
RELEASE_CONTROL_MODE=publisher
RELEASE_CONTROL_MODE=deployer
```

O modo e imutavel durante o processo e determina quais routers existem. Ele
nao e aceito em header, query, cookie ou body e nao e derivado do JWT. Ocultar
um menu nao substitui esse isolamento.

Os contratos autocontidos sao:

- [publisher.openapi.yml](./api/publisher.openapi.yml);
- [deployer.openapi.yml](./api/deployer.openapi.yml).

Ambos possuem apenas health checks, capabilities autenticadas e as rotas do
proprio modo.

## Requests mutaveis

Publicacao aceita somente `candidateId`, `versionBump`, `description` e
`changelog`. A versao final e calculada e reservada no servidor.

Deployment aceita somente a identidade de uma release global. A rota de
rollback valida release e `reason`, mas nesta versao responde sempre `409
RELEASE_NOT_ELIGIBLE`: o planner S18 e forward-only, portanto rollback
comercial nao e anunciado nem despachado.

Campos adicionais sao rejeitados. O cliente nunca fornece imagem, digest,
tag avulsa, componente, comando, path, URL, variavel, owner, repositorio ou
workflow.

O acompanhamento de publicação usa
`GET /api/release-publisher/v1/operations/{operationId}`. A identidade é a
operação local porque a SemVer ainda pode ser nula durante `REQUESTED`,
`VALIDATING` e `PUBLISHING`; não existe alias por `releaseId`.

## Idempotencia

Todos os tres POSTs exigem `Idempotency-Key`, limitada em formato e tamanho.
Seu escopo inclui modo, rota, ator autenticado e chave.

Os runtimes persistem hash canonico do request e HMAC da chave, nunca
segredo. Mesma chave e mesmo request retornam a operacao existente e informam
replay. Mesma chave com payload diferente retorna `409
IDEMPOTENCY_CONFLICT`. Retry ou timeout do cliente nao inicia segundo
workflow.

A retencao é configurável e vale 365 dias por padrão. Enquanto uma chave
estiver retida, sua unicidade nao pode ser enfraquecida. A chave nao e ID
publico da operacao.

## Concorrencia

Publicacoes concorrentes nao podem reservar a mesma versao semantica.

Deployment e rollback compartilham um unico lock global de producao, com no
maximo uma operacao ativa. Nova solicitacao nao cancela a atual; retorna `409
PRODUCTION_OPERATION_ACTIVE` e, quando autorizado, referencia opaca da
operacao. O lock transacional local e a futura `concurrency` do workflow sao
camadas complementares.

## Estados

O contrato machine-readable esta em
[state-machines.yml](./contracts/state-machines.yml).

- elegibilidade: `NOT_ELIGIBLE`, `READY`;
- publicacao: `REQUESTED -> VALIDATING -> PUBLISHING -> PUBLISHED`;
- deployment runtime S22: `QUEUED -> SUCCEEDED | ROLLED_BACK | FAILED`, sempre
  por reconciliador e com evidencia remota para sucesso ou compensacao;
- `PULLING`, `BACKING_UP`, `MIGRATING`, `UPDATING`, `VERIFYING` e
  `ROLLING_BACK` permanecem reservados para telemetria futura.

Falhas de publicacao podem levar estados nao terminais a `FAILED`. O runtime
deployer nao inventa estados intermediarios porque o workflow S21 publica
somente outcome terminal canonico.

Estados terminais nao possuem transicao de saida. Somente o reconciliador
interno avanca por evidencia do workflow. O cliente nao envia estado desejado.

O rollback comercial é anunciado em `capabilities` após a ativação S26/S27.
Sem instalação e cadeia elegíveis, a rota responde
`409 RELEASE_NOT_ELIGIBLE` sem operação ou dispatch.

A S25 fechou o contrato offline em
[ROLLBACK_COMERCIAL.md](./ROLLBACK_COMERCIAL.md),
[rollback.openapi.yml](./api/rollback.openapi.yml),
[rollback-state-machine.yml](./contracts/rollback-state-machine.yml) e
[rollback-security.yml](./contracts/rollback-security.yml). S26/S27 ativaram o
runtime e a UI sem enfraquecer esses artefatos.

## Reconciliacao

O registro minimo inclui IDs opacos de operacao/workflow, tipo, modo, estado,
ator, hashes de idempotencia/request, release alvo, timestamps e erro
sanitizado. `sourceCommit` e aplicavel a publicacao.

O publisher implementado usa Python 3.13 e FastAPI, com PostgreSQL 16,
SQLAlchemy 2 síncrono, Alembic e Psycopg 3. Tokens GitHub nunca entram no
registro.

Depois de reinicio, toda operacao nao terminal e reconciliada. O
`workflowRunId` e opcional antes da descoberta; depois da correlacao, run ID e
control SHA sao obrigatorios e imutaveis. Apenas o attempt do mesmo run pode
aumentar. Ausencia ou inconsistencia de evidencia remota falha fechado
e gera auditoria. Sucesso so e gravado depois da conclusao remota e validacao
do artefato/ambiente. Estado terminal nao regride. A UI consulta o estado
local reconciliado e nunca inventa sucesso.

Uma instalacao marcada como incerta nunca e apresentada como atual. Nesse
caso, `GET /api/deployment-control/v1/current` responde `409
CURRENT_INSTALLATION_UNRECONCILED` ate existir reconciliacao suficiente. Uma
instalacao declarada reconciliada tambem falha fechada quando seu snapshot nao
existe, o commit diverge ou o dominio de releases nao esta verde. A listagem
permanece disponivel, sempre com `eligible=false`, enquanto plano, novo
deployment e readiness ficam indisponiveis sem alterar a evidencia persistida.

## Autenticacao e autorizacao

Todas as rotas `/api/` exigem bearer JWT. Health e publico e retorna somente
estado generico. Publisher e deployer implementam RS256 exclusivamente e exigem
issuer, audience, `exp`, `sub`, scopes e JWKS configurados; a rotação segue o JWKS.

O ERP implementa duas pontes opt-in anteriores à UI:

- **Publisher:** somente `ROLE_SYSTEM` pode trocar sua sessão HS512 por um token
  RS256 de 300 segundos, audience `emporio-release-control` (fixa) e scopes
  `release:read release:publish`. O JWKS público expõe apenas uma chave RSA pública.
- **Deployer:** somente `ROLE_SYSTEM` pode trocar sua sessão HS512 por um token
  RS256 de 300 segundos, audience `emporio-release-control-deployer` (fixa) e scopes
  `deployment:read deployment:execute deployment:rollback`. O JWKS público expõe apenas uma chave RSA pública.

As pontes não alteram o token ERP nem relaxam o perfil de produção;
ambas os perfis `development` são estritamente loopback. As chaves privadas e audiences
são completamente isoladas entre publisher e deployer.

Roles logicas:

- `release:read`;
- `release:publish`;
- `deployment:read`;
- `deployment:execute`;
- `deployment:rollback`.

A [matriz de seguranca](./contracts/security-matrix.yml) cruza rota, modo,
role e perfil outbound. Uma role do modo oposto nao concede acesso porque a
rota sequer e registrada.

## Credenciais e proibicoes

Publisher, deployer, VPS e build usam perfis separados de privilegio minimo:

- publisher le commits/checks/artefatos e dispara apenas publicacao;
- deployer le releases/manifestos e dispara apenas producao;
- VPS somente le packages;
- build escreve packages somente no job autorizado.

GitHub App é o mecanismo outbound exclusivo dos dois modos, com credenciais
separadas.
Credenciais sao injetadas por ambiente/secret store e nao aparecem em logs,
respostas, persistencia ou arquivos rastreaveis.

Nenhum runtime acessa Git local, Docker socket ou SSH diretamente. CORS usa
allowlist HTTPS configurada, o payload JSON é limitado a 16 KiB, e os limites
locais são 120 GET/min e 5 POST/min. Toda mutacao e auditavel.

## Erros

Erros usam `ProblemDetails`, codigo estavel, trace ID e mensagem sanitizada,
sem stack trace. Os OpenAPI documentam autenticação, autorizacao, validacao,
conflito, rate limit e falha interna conforme cada operacao.

## Validacao

```bash
python3 tools/releases/release_control_contract.py validate

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_release_control_contract.py' \
  -v
```

O validador e local, fail-closed e nao acessa rede.

## Decisoes pendentes

- UI de producao e ponte de identidade para scopes deployer;
- rollback comercial/downgrade;
- configuração de credenciais reais e implantação dos runtimes;
- execução remota, publicação e operação em produção.

Essas decisoes nao alteram as fronteiras de autoridade definidas aqui.
