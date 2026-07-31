# S24 — UI de produção para atualização forward

> **Estado:** `PLANNED`
> **Tipo:** frontend administrativo, integração deployer e recuperação de UI
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Dependências:** S01 a S23 `ACCEPTED`
> **Relatório de saída:** `S24-ui-producao-atualizacao-forward.report.md`

## Instrução para delegação

Execute integralmente esta task. Leia, nesta ordem:

1. esta task inteira;
2. a revisão terminal da S23;
3. `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`;
4. `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
5. `docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md`;
6. `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`;
7. `docs/infrastructure/deployment/release-control/IDENTIDADE_DEPLOYER.md`;
8. `docs/infrastructure/deployment/release-control/README.md`;
9. `frontend/src/config/releasePublisher.js` e os serviços/clientes da UI S17,
   apenas como padrão de isolamento e recuperação;
10. `frontend/src/components/configuracoes/PainelControle.vue`;
11. `frontend/src/router/routes.js`;
12. `frontend/src/boot/axios.js`, `frontend/src/global.js` e
    `frontend/src/stores/userStore.js`;
13. `frontend/entrypoint.sh`, `frontend/runtime-entrypoint.spec.js` e
    `frontend/quasar.config.js`.

O executor implementa somente as decisões abaixo. Não escolhe nova rota,
claim, scope, estado, endpoint, formato de storage, política de retry,
componente ou alternativa de integração.

Não altera esta task, o tracker ou cria S25.

## 1. Resultado observável

Adicionar ao frontend administrativo uma UI de produção que permita somente a
atualização forward para a próxima release global elegível. A interface usa o
ERP da mesma origem para obter o token deployer e o proxy público do host para
as rotas do `release_control` deployer.

Ao final:

- a rota da interface é exatamente `/configuracoes/atualizacao-sistema`;
- a UI funciona somente quando a configuração runtime do frontend é
  `deployer` e o servidor confirma as capabilities exatas;
- a produção mostra instalação atual, releases, elegibilidade, plano global e
  estado reconciliado;
- o POST de deploy envia somente a identidade da release global;
- a operação é idempotente, retomável após reload e acompanhada sem inventar
  progresso intermediário;
- instalação incerta e operação ativa bloqueiam nova atualização e recebem
  mensagem pública segura;
- nenhuma seleção de componente, digest, tag, workflow, imagem ou comando é
  oferecida;
- publicação e rollback não aparecem na UI nem em capabilities derivadas pelo
  cliente;
- o publisher de desenvolvimento continua byte/semanticamente inalterado.

## 2. Decisões fechadas de integração

```
rota UI                  /configuracoes/atualizacao-sistema
origem da API            baseApiUrl já usado pelo frontend
troca de identidade      POST /api/release-control/identity/deployer/token
capabilities             GET  /api/release-control/v1/capabilities
instalação atual         GET  /api/deployment-control/v1/current
releases                 GET  /api/deployment-control/v1/releases
plano                    GET  /api/deployment-control/v1/releases/{releaseId}/plan
solicitação              POST /api/deployment-control/v1/deployments
operação                 GET  /api/deployment-control/v1/deployments/{deploymentId}
rollback                 nunca chamado e nunca exibido
token                    somente memória, nunca storage, URL ou log
scope aceito             deployment:read deployment:execute
capabilities aceitas     exatamente deployment:read deployment:execute
TTL esperado             300 segundos
request de deploy        {"release":"vX.Y.Z"}
header obrigatório       Idempotency-Key
prefixo da chave         deployer-ui-<UUID v4>
storage                  sessionStorage[emporio.releaseDeployer.pending.v1]
limite do storage        16 KiB
```

O `baseApiUrl` em produção é a origem HTTPS do ERP. A UI não usa
`127.0.0.1:8121`, porta Docker, URL configurável pelo usuário ou chamada
direta ao `release_control`; o Nginx do host encaminha
`/api/deployment-control/*` ao listener privado do deployer.

O backend ERP continua sendo a autoridade do usuário `ROLE_SYSTEM`. O
frontend pode ocultar ou desabilitar superfícies, mas nunca substitui
autenticação, scope ou capability do servidor.

## 3. Configuração runtime fechada

O arquivo `frontend/entrypoint.sh` deve manter a validação atual de
`VITE_BASE_API_URL` e adicionar ao `window.RuntimeConfig`:

```json
{"apiBaseUrl":"https://erp-emporio.example","releaseControlMode":"deployer"}
```

Regras do valor runtime `RELEASE_CONTROL_MODE`:

- ausente significa `disabled`;
- `disabled` e `deployer` são os únicos valores aceitos no container de
  produção;
- `publisher` é inválido no entrypoint de produção e deve falhar fechado;
- qualquer terceiro valor falha fechado sem iniciar o Nginx;
- o valor nunca contém URL, token, chave ou credencial.

Criar `frontend/src/config/releaseDeployer.js` com uma função pura de
resolução. Em build de desenvolvimento, o modo deployer é sempre recusado;
`frontend/.env` continua exclusivo do publisher local S17. Em build de
produção, somente `window.RuntimeConfig.releaseControlMode === "deployer"`
habilita a configuração. Ausência, tipo inválido ou valor desconhecido resulta
em `disabled`.

Não alterar o contrato de `releasePublisher.js`: publisher e deployer devem
continuar com configurações e clientes separados.

## 4. Capability e menu

Antes de habilitar o card e a ação de atualização, o frontend deve trocar a
sessão ERP pelo token deployer e consultar capabilities. O shape aceito é
exatamente:

```json
{
  "mode": "deployer",
  "apiVersion": "v1",
  "capabilities": ["deployment:read", "deployment:execute"]
}
```

Qualquer campo adicional, capability desconhecida, ausência de qualquer uma
das duas capabilities ou presença de `deployment:rollback` torna a resposta
inválida e desabilita a UI.

O card de **Atualização do sistema** somente pode aparecer após a capability
válida. Enquanto a capability está sendo carregada, não oferecer botão de
ação. Acesso direto à rota também passa pelo mesmo gate e nunca concede
autorização por si só.

## 5. Releases, instalação e plano

O cliente valida os schemas do OpenAPI antes de entregar dados ao componente.
Não deve aceitar shape desconhecido, campos extras ou formatos inválidos.

- `GET /current` `200`: mostrar release, commit, data e estado reconciliado.
- `GET /current` `404`: tratar como instalação limpa; a primeira release
  elegível pode ser apresentada.
- `GET /current` `409 CURRENT_INSTALLATION_UNRECONCILED`: mostrar estado
  **Instalação incerta**, não apresentar a instalação como atual e bloquear
  plano e POST.
- `GET /releases` `200`: apresentar releases globais e o campo `eligible`;
  não recalcular elegibilidade no browser.
- Deve existir no máximo uma release `eligible=true\); zero significa
  “nenhuma atualização disponível”; mais de uma é resposta inconsistente e
  bloqueia a ação com mensagem genérica.
- A release elegível selecionada é a única opção de atualização. Não existe
  campo editável de versão, componente, digest ou tag.
- O plano deve exibir os seis componentes em modo somente leitura, além de
  `sourceRelease`, `targetRelease`, `migrationRequired` e
  `backupRequired`. Digests podem ser consumidos para validação, mas não são
  apresentados como campos selecionáveis ou editáveis.
- O plano é sempre consultado novamente antes da confirmação final; resposta
  inválida ou divergente bloqueia o POST.

## 6. Solicitação, idempotência e recuperação

Antes do POST, persistir no `sessionStorage` somente o registro validado:

```json
{
  "schemaVersion": 1,
  "idempotencyKey": "deployer-ui-<UUID v4>",
  "release": "v1.4.0",
  "operationId": null,
  "createdAt": "2026-07-31T12:00:00.000Z"
}
```

Depois de um `202`, preencher `operationId`. Registro inválido, storage
indisponível ou conteúdo acima de 16 KiB bloqueia a ação. A chave e o registro
não podem aparecer em logs públicos.

O POST deve conter somente `release` no JSON e o header `Idempotency-Key`.
Não reenviar automaticamente após erro de rede, resposta inválida ou conflito.
Após reload:

- com `operationId`, consultar exclusivamente o status dessa operação;
- sem `operationId`, oferecer **Retomar envio** com a mesma chave e release;
- **Descartar tentativa** exige confirmação e remove apenas o registro local;
- descartar nunca cancela operação remota.

Replay idempotente com o mesmo resultado pode continuar o acompanhamento;
`IDEMPOTENCY_CONFLICT` encerra a tentativa sem novo POST.

## 7. Estados reconciliados e mensagens

O runtime deployer não inventa estados intermediários. A UI deve tratar
`QUEUED` como **Aguardando reconciliação** e os estados terminais `SUCCEEDED`
e `FAILED` conforme o payload local. Se o servidor enviar qualquer estado
fora do enum aceito, parar o polling e mostrar erro genérico.

Polling:

- intervalo fixo de 3 segundos;
- uma requisição por vez;
- timeout contínuo de 10 minutos;
- reload, saída da tela ou erro de rede preservam a tentativa;
- ação explícita **Atualizar estado** retoma consulta manual;
- estado terminal interrompe imediatamente o polling e remove o registro
  pendente.

Traduções públicas mínimas:

```
401 UNAUTHORIZED                       Sessão expirada. Entre novamente.
403 FORBIDDEN                          Você não possui permissão para atualizar.
404 NOT_FOUND                          Release ou operação não encontrada.
409 CURRENT_INSTALLATION_UNRECONCILED  A instalação está incerta; consulte o suporte.
409 IDEMPOTENCY_CONFLICT               A tentativa salva não corresponde à solicitação.
409 PRODUCTION_OPERATION_ACTIVE        Já existe uma atualização em andamento.
409 RELEASE_NOT_ELIGIBLE               Esta release não está elegível.
422 UNPROCESSABLE                      A resposta não pode ser usada para atualizar.
429 RATE_LIMITED                       Muitas solicitações. Aguarde e tente novamente.
500 INTERNAL_ERROR                     O serviço encontrou um erro interno.
503 SERVICE_UNAVAILABLE                O serviço de atualização está indisponível.
```

Nunca mostrar `detail`, traceback, body desconhecido, token, chave, URL
remota, digest como comando, `workflowRunUrl` ou código interno não previsto.
`traceId` válido pode aparecer apenas como código de suporte.

## 8. Fronteira autorizada

Alterar ou criar somente:

```
frontend/entrypoint.sh
frontend/runtime-entrypoint.spec.js
frontend/src/config/releaseDeployer.js
frontend/src/config/releaseDeployer.spec.js
frontend/src/services/releaseDeployerClient.js
frontend/src/services/releaseDeployerClient.spec.js
frontend/src/services/releaseDeployerAttempt.js
frontend/src/services/releaseDeployerAttempt.spec.js
frontend/src/pages/ProductionDeploymentPage.vue
frontend/src/pages/ProductionDeploymentPage.spec.js
frontend/src/router/routes.js
frontend/src/components/configuracoes/PainelControle.vue
frontend/src/components/configuracoes/PainelControle.spec.js
docs/infrastructure/deployment/release-control/UI_DEPLOYER.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/implementation/slices/S24-ui-producao-atualizacao-forward.report.md
```

Não alterar:

- qualquer arquivo publisher S17, inclusive `releasePublisher.js`, seus
  clientes, tentativa ou componente, salvo importação não mutante se
  indispensável e previamente reportada como bloqueio;
- backend Java, `release_control`, OpenAPI, JSON Schema, YAML de estados,
  matriz de segurança, migrations, workflows, Dockerfile ou Compose;
- gateway, Nginx, VPS, DNS, TLS, produção, `ops/env/.env.production` ou
  qualquer segredo/arquivo sensível;
- task, tracker ou qualquer slice S01–S23;
- qualquer rota de rollback, publicação, seleção de componentes ou operação
  GitHub/GHCR;
- `git add`, commit, tag, push ou publicação remota.

## 9. Testes causais obrigatórios

Os testes devem falhar contra mutantes relevantes, não somente testar o caminho
feliz:

- modo runtime inválido, `publisher` no entrypoint e ausência de
  `releaseControlMode`;
- capabilities com campo extra, capability ausente, rollback, modo errado ou
  versão errada;
- exchange com audience/scope/TTL/token type incorreto;
- token não persistido em storage/log e 401 com no máximo um novo exchange;
- schema inválido, campo extra, SemVer/SHA/data/operationId inválidos;
- current `404`, `200` e `409` com bloqueio correto;
- zero, um e mais de um item elegível;
- plano que não contém exatamente os seis componentes;
- POST com qualquer campo extra ou header idempotente inválido;
- replay, conflito, rede incerta, reload com e sem operationId;
- `QUEUED`, `SUCCEEDED`, `FAILED`, estado desconhecido, timeout e refresh;
- `PRODUCTION_OPERATION_ACTIVE` com e sem `activeOperationId`;
- rollback ausente em menu, cliente, request e capabilities derivadas;
- rota protegida por autenticação e publisher não aparecendo em modo deployer.

## 10. Matriz terminal

Executar no CWD indicado e registrar comando, exit, contagem, duração,
interpretação, artefatos e saída relevante:

```bash
cd /home/gregorio/git/baronesa/emporio/frontend
npm ci
npm run lint
npm run test:unit -- --run
npm run build
node --test runtime-entrypoint.spec.js

cd /home/gregorio/git/baronesa/emporio
python3 tools/releases/release_control_contract.py validate
git diff --check
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f -printf '%f\n' | sort
find . -path './.git' -prune -o \( -name '.venv' -o -name '.coverage' \
  -o -name '.pytest_cache' -o -name '.ruff_cache' -o -name '.mypy_cache' \
  -o -name '__pycache__' -o -name '*.pyc' \) -print
```

Os comandos de dependência podem usar cache em `/tmp`, mas o `frontend`
precisa terminar sem `node_modules` novo, `coverage`, `.vite` ou `dist`
novo; resíduo preexistente deve ser identificado por metadata e preservado.

Todos os comandos devem terminar em exit `0`, exceto `git rev-parse --verify
HEAD`, que permanece exit `128` enquanto o workspace estiver pré-Git. A busca
final de resíduos deve ser vazia.

## 11. Critérios de aceite do orquestrador

A S24 só será aceita se:

- a UI só habilitar deployer por configuração runtime válida e capability
  autenticada exata;
- a rota e todos os endpoints coincidirem com este contrato e o OpenAPI;
- o browser nunca acessar `8121` diretamente e o POST enviar somente release;
- current, releases, plan e operação forem validados e renderizados sem
  inventar estado;
- a atualização limitar-se à única release marcada elegível pelo servidor;
- idempotência, reload, timeout, conflito e instalação incerta forem seguros;
- rollback e publisher não aparecerem no modo deployer;
- token, chave, detalhe remoto e segredo não forem persistidos ou exibidos;
- testes mutantes, lint, build, contrato e toda a matriz passarem;
- a fronteira autorizada, resíduos e estado Git forem preservados;
- a documentação explicar o uso, proxy same-origin, limites e recuperação;
- o relatório permanecer literal `IN_PROGRESS — aguardando revisão do
  orquestrador` e não criar S25.

## 12. Condições de bloqueio

Pare sem improvisar se for necessário:

- alterar OpenAPI, backend, `release_control`, Nginx, gateway, Docker ou
  workflow para fazer a UI funcionar;
- expor `8121` ao browser ou criar uma nova origem/CORS sem contrato aceito;
- alterar a ponte publisher ou compartilhar token/chave;
- escolher mais de uma release elegível ou calcular elegibilidade localmente;
- anunciar rollback ou enviar qualquer componente/digest no POST;
- criar um estado de progresso que o runtime não reconcilia;
- abrir arquivo sensível ou acessar GitHub, GHCR, SSH, VPS ou produção.

## 13. Formato da resposta do executor

Responder somente com:

1. caminho absoluto do relatório;
2. arquivos criados, alterados e não alterados na fronteira;
3. implementação por requisito;
4. testes causais e mutantes com saída literal relevante;
5. matriz terminal com CWD, comandos, exits, contagens e durações;
6. estado Git, workflows e resíduos;
7. acessos externos e ausência de segredos;
8. divergências restantes, ou `nenhuma` com prova;
9. estado literal:

```
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED`, não criar S25 e não modificar esta task.
