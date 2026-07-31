# S17 — UI de desenvolvimento para publicação de releases globais

## 1. Resumo e estado

A S17 foi executada integralmente dentro da fronteira autorizada. A UI
publisher foi incorporada ao painel de controle do frontend somente para
usuário `root`, ambiente de desenvolvimento e modo local `publisher`.

O fluxo entregue:

- troca a identidade ERP exclusivamente pela rota definida na S16;
- mantém o bearer publisher somente em memória;
- valida capabilities antes de liberar a interface;
- lista somente candidatos elegíveis e o histórico necessário para estimar a
  próxima SemVer;
- exige confirmação local antes de criar a tentativa;
- persiste apenas a tentativa idempotente fechada em `sessionStorage`;
- permite recuperação explícita de envio incerto;
- acompanha a operação por polling não sobreposto;
- apresenta estados e erros públicos sanitizados.

Não houve publicação real, acesso externo, alteração de backend, runtime
publisher Python, workflow, Docker, Compose ou produção.

Estado: **IN_PROGRESS — aguardando revisão do orquestrador**

## 2. Arquivos criados

- `frontend/src/config/releasePublisher.js`
- `frontend/src/config/releasePublisher.spec.js`
- `frontend/src/services/releasePublisherClient.js`
- `frontend/src/services/releasePublisherClient.spec.js`
- `frontend/src/services/releasePublisherAttempt.js`
- `frontend/src/services/releasePublisherAttempt.spec.js`
- `frontend/src/components/configuracoes/ReleasePublisherConfig.vue`
- `frontend/src/components/configuracoes/ReleasePublisherConfig.spec.js`
- `frontend/src/components/configuracoes/PainelControle.spec.js`
- `tools/releases/validate_publisher_ui.py`
- `tools/releases/tests/test_publisher_ui_contract.py`
- `docs/infrastructure/deployment/release-control/UI_PUBLISHER.md`
- `docs/infrastructure/deployment/implementation/slices/S17-ui-desenvolvimento-publicacao-releases-globais.report.md`

O teste dedicado de `PainelControle.vue` foi indispensável para provar
causalmente a combinação `root` + desenvolvimento + modo `publisher` e as duas
negações previstas no contrato.

## 3. Arquivos alterados

- `frontend/.env`
- `frontend/src/components/configuracoes/PainelControle.vue`
- `docs/infrastructure/deployment/release-control/README.md`
- `docs/infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md`
- `docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md`
- `docs/development/ONBOARDING_MINIMO.md`
- `docs/development/README.md`

Nenhum arquivo fora da lista autorizada foi alterado pela execução.

## 4. Ativação local e localização visual

`releasePublisher.js` resolve a configuração sem consultar
`window.RUNTIME_CONFIG` ou outro fallback:

- em produção, o recurso permanece desabilitado independentemente das
  variáveis recebidas;
- em desenvolvimento, somente `disabled` e `publisher` são aceitos;
- o modo `publisher` exige URL HTTP loopback, com porta explícita;
- a origem é normalizada antes de ser entregue ao cliente;
- uma configuração inválida falha fechada.

O cartão foi adicionado ao `PainelControle.vue`, sem criar rota Vue. Ele só é
materializado quando o usuário é `root` e a configuração local está habilitada.
Usuário não-root, produção, modo ausente, modo inválido ou URL inválida não
ganham acesso à superfície.

## 5. Exchange e token somente em memória

O cliente executa o exchange ERP sem body ou query string e exige o shape
fechado da resposta. Requisições concorrentes compartilham um único exchange
em andamento.

O bearer recebido fica em variável privada da closure do cliente. Ele não é
gravado em `sessionStorage`, `localStorage`, IndexedDB, cookie, URL, fixture ou
log. Em `401`, o cliente descarta o token, realiza no máximo um novo exchange e
repete a requisição original uma única vez. Falha de rede em `POST` nunca é
reenviada automaticamente.

## 6. Contrato do cliente

O cliente implementa e valida estritamente:

- capabilities do modo publisher, sem ausências, duplicatas ou extras;
- paginação de candidatos com cursor opaco;
- somente candidatos `READY`, com os bindings exigidos;
- deduplicação idêntica e rejeição de duplicatas divergentes;
- histórico de releases limitado a dez páginas;
- ordenação SemVer por segmentos decimais, sem conversão numérica insegura;
- criação de publicação com payload fechado e `Idempotency-Key`;
- consulta da operação pelo `operationId`;
- cruzamento entre path, resposta, candidato e operação;
- `ProblemDetails` público, com mensagem e `traceId` sanitizados;
- fallback genérico para resposta remota malformada.

O browser não seleciona componentes, não calcula BOM e não resolve
dependências.

## 7. Formulário, estimativa e estados visuais

A interface carrega capabilities, candidatos elegíveis e histórico antes de
habilitar o formulário. Ela apresenta candidato, incremento SemVer e campos
textuais definidos no contrato, produzindo a estimativa por aritmética de
strings decimais.

Antes do envio existe confirmação explícita. Cancelar a confirmação não cria
UUID nem tentativa persistida. Os estados visuais cobrem configuração
desabilitada, carregamento, pronto, confirmação, envio, envio incerto,
acompanhamento, pausa por timeout, sucesso e falha. Erros não exibem
`errorCode`, detalhe remoto, token, stack ou payload interno.

## 8. Idempotência, recuperação e polling

A tentativa utiliza uma única chave canônica de `sessionStorage`, schema
fechado, UUID v4, request normalizado, instante de criação e `operationId`
opcional. O limite serializado é 16 KiB. Registro inválido é removido e
indisponibilidade do storage desabilita o fluxo fail-closed.

A tentativa é persistida antes do `POST`. Em resultado incerto, a UI preserva
o mesmo request e a mesma chave; reenviar exige ação explícita. Descartar exige
confirmação local e não simula cancelamento remoto.

Quando há `operationId`, a restauração consulta o estado sem republicar.
O polling usa intervalo de três segundos, uma requisição por vez e limite de
dez minutos. Estado terminal para o polling, limpa a tentativa e recarrega as
listas. Timeout pausa o polling e preserva a recuperação manual. O unmount
cancela o timer.

## 9. Testes causais nominais

### 9.1 Configuração

- desenvolvimento desabilitado por padrão;
- configuração publisher canônica;
- produção sempre desabilitada;
- modos, protocolos, hosts, credenciais, paths, query, fragmento e portas
  inválidos rejeitados;
- loopback IPv4 `127.0.0.1` e `localhost` com porta explícita;
- ausência de consulta a runtime config.

### 9.2 Cliente

- exchange exato, sem body, e token privado;
- single-flight do exchange;
- capabilities completas, sem duplicatas ou extras;
- refresh único no primeiro `401` e parada no segundo;
- ausência de retry automático de `POST`;
- candidatos estritos, cursor opaco e deduplicação;
- histórico limitado, deduplicado e ordenado por SemVer;
- payload e idempotency key exatos;
- binding completo da consulta da operação;
- `ProblemDetails` válido sanitizado e fallback para resposta inválida.

### 9.3 Tentativa persistida

- criação do registro UUID v4 fechado;
- restauração com e sem `operationId`;
- rejeição acima de 16 KiB;
- falha fechada quando o storage está indisponível;
- probe usando apenas a chave canônica;
- limpeza terminal sem tocar em `localStorage`.

### 9.4 Componente e painel

- configuração desabilitada não cria sessão;
- carga somente de candidatos `READY`, release atual e estimativa;
- cancelamento anterior à criação da tentativa;
- persistência anterior ao `POST`;
- envio incerto e retry explícito com a mesma tentativa;
- descarte confirmado sem cancelamento remoto;
- restauração por `operationId` sem novo `POST`;
- polling de três segundos sem sobreposição;
- terminal com limpeza e atualização;
- cancelamento do timer no unmount;
- timeout recuperável;
- falha sem códigos/detalhes/tokens/stacks internos;
- mensagem pública e `traceId` sanitizado;
- cartão visível apenas para root local publisher.

### 9.5 Validador estrutural e mutantes

Os 16 testes mutantes cobrem ativação, bloqueio em produção, painel, ausência
de nova rota, endpoint de exchange, fronteira de armazenamento, capabilities,
candidatos, paginação, proibição de seleção de componentes, schema da
tentativa, retry de POST, polling, sanitização, documentação e presença da
superfície causal.

## 10. Matriz obrigatória final

### 10.1 Lint do frontend

- CWD: `/home/gregorio/git/baronesa/emporio/frontend`
- Comando exato: `npm run lint`
- Exit code: `0`
- Resultado: ESLint concluiu sem erro.
- Interpretação: os arquivos JavaScript e Vue entregues respeitam as regras
  estáticas do frontend.
- Artefatos/resíduos: nenhum resíduo novo identificado.

### 10.2 Testes do frontend

- CWD: `/home/gregorio/git/baronesa/emporio/frontend`
- Comando exato: `npm test`
- Exit code: `0`
- Resultado: `8` arquivos e `97` testes aprovados.
- Interpretação: todos os testes do frontend, incluindo as superfícies causais
  da S17, passaram. Avisos de componentes Quasar não resolvidos em testes
  legados e APIs depreciadas já pertencem ao harness existente e não causaram
  falha.
- Artefatos/resíduos: nenhum coverage criado; cache preexistente preservado.

### 10.3 Build do frontend

- CWD: `/home/gregorio/git/baronesa/emporio/frontend`
- Comando exato: `npm run build`
- Exit code: `0`
- Resultado: SPA compilada com sucesso em `frontend/dist/spa`.
- Interpretação: a UI é compatível com o alvo atual
  `es2019|edge88|firefox78|chrome87|safari13.1`. Permaneceram apenas avisos de
  chunks grandes e combinador CSS depreciado.
- Artefatos/resíduos: `frontend/dist` já existia antes da S17 e foi preservado,
  conforme a regra de higiene.

### 10.4 Validador da UI

- CWD: `/home/gregorio/git/baronesa/emporio`
- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_ui.py`
- Exit code: `0`
- Resultado: `publisher-ui:valid`.
- Interpretação: configuração, fronteiras, UI, cliente, tentativa, testes e
  documentação satisfazem o contrato estrutural fail-closed.
- Artefatos/resíduos: nenhum bytecode Python.

### 10.5 Mutantes da UI

- CWD: `/home/gregorio/git/baronesa/emporio`
- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/releases/tests/test_publisher_ui_contract.py -v`
- Exit code: `0`
- Resultado: `16` testes aprovados.
- Interpretação: cada violação causal prevista foi detectada.
- Artefatos/resíduos: diretórios temporários removidos; nenhum bytecode.

### 10.6 Regressão dos contratos de release

- CWD: `/home/gregorio/git/baronesa/emporio`
- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'`
- Exit code: `0`
- Resultado: `274` testes aprovados.
- Interpretação: a S17 não regrediu os contratos S05–S16.
- Artefatos/resíduos: nenhum bytecode Python.

### 10.7 Contrato release-control

- CWD: `/home/gregorio/git/baronesa/emporio`
- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate`
- Exit code: `0`
- Resultado: `release-control-contract:valid`.
- Interpretação: os artefatos machine-readable e documentais continuam
  coerentes.
- Artefatos/resíduos: nenhum bytecode Python.

### 10.8 Integridade textual

- CWD: `/home/gregorio/git/baronesa/emporio`
- Comando exato: `git diff --check`
- Exit code: `0`
- Resultado: nenhuma saída.
- Interpretação: não existem erros de whitespace detectáveis.
- Artefatos/resíduos: nenhum.

### 10.9 Índice Git real

- CWD: `/home/gregorio/git/baronesa/emporio`
- Comando exato: `git diff --cached --name-only`
- Exit code: `0`
- Resultado: nenhuma saída.
- Interpretação: o índice Git real permanece vazio.
- Artefatos/resíduos: nenhum.

### 10.10 HEAD

- CWD: `/home/gregorio/git/baronesa/emporio`
- Comando exato: `git rev-parse --verify HEAD`
- Exit code: `128`
- Resultado: `fatal: Needed a single revision`.
- Interpretação: HEAD continua inexistente, como exigido.
- Artefatos/resíduos: nenhum.

### 10.11 Tags

- CWD: `/home/gregorio/git/baronesa/emporio`
- Comando exato: `git tag --list`
- Exit code: `0`
- Resultado: nenhuma saída.
- Interpretação: nenhuma tag existe.
- Artefatos/resíduos: nenhum.

### 10.12 Reflog

- CWD: `/home/gregorio/git/baronesa/emporio`
- Comando exato: `git reflog`
- Exit code: `128`
- Resultado: branch `main` sem commits.
- Interpretação: reflog continua inexistente.
- Artefatos/resíduos: nenhum.

## 11. Verificações complementares de higiene

Todos os comandos desta seção foram executados em
`/home/gregorio/git/baronesa/emporio`.

| Comando exato | Exit | Resultado | Interpretação e resíduos |
|---|---:|---|---|
| `find .github/workflows -maxdepth 1 -type f -name '*.yml' -print \| sort` | 0 | `ci.yml`, `publish-candidate.yml`, `publish-release.yml` | Exatamente três workflows ativos; nenhum criado ou alterado pela S17. |
| `find tools/releases -type d -name __pycache__ -print` | 0 | vazio | Nenhum cache Python. |
| `find tools/releases -type f -name '*.pyc' -print` | 0 | vazio | Nenhum bytecode Python. |
| `find /tmp -maxdepth 1 -type d -name 's17-publisher-ui-*' -print` | 0 | vazio | Nenhum diretório mutante temporário restante. |
| `find . -maxdepth 4 -type d \( -name coverage -o -name .vite \) -print \| sort` | 0 | somente caches/coverage sob `node_modules` | Os caches encontrados eram preexistentes; nenhum foi removido. |
| `find docs/infrastructure/deployment/implementation/slices -maxdepth 1 -type f -name 'S18*' -print` | 0 | vazio | S18 não foi criada. |

Inventário inicial preservado:

- `frontend/node_modules`: existia;
- `frontend/dist`: existia;
- `frontend/node_modules/.vite`: existia;
- `frontend/.vite`: não existia e não foi criado;
- `frontend/coverage`: não existia e não foi criado.

## 12. Falhas intermediárias e correções

1. A primeira execução dos testes focados do componente encontrou quatro
   falhas no harness assíncrono/stubs. Os stubs e a sincronização foram
   corrigidos sem relaxar o comportamento; a suíte final do componente passou.
2. O primeiro validador documental exigiu a declaração explícita de token
   somente em memória. A documentação foi ajustada e o validador passou.
3. A primeira versão do harness mutante tentou copiar conteúdo não pertencente
   à superfície, incluindo dependências, e esgotou o espaço temporário. Somente
   os diretórios `/tmp/s17-publisher-ui-*` criados por essa tentativa foram
   removidos. O harness passou a copiar apenas arquivos requeridos e superfícies
   de router; os 16 mutantes passaram.
4. O primeiro lint detectou duas referências de constantes locais em
   `defineProps`. Os defaults foram tornados independentes do macro; lint
   final passou.
5. O primeiro build rejeitou literais `BigInt` no alvo atual. Comparação e
   incremento SemVer foram implementados com strings decimais; build final
   passou no alvo Safari 13.1/es2019.
6. A primeira regressão completa encontrou uma frase histórica da S16 que
   afirmava genericamente que a UI não existia. A documentação autorizada foi
   temporalmente esclarecida: ela não foi implementada na S16 e foi entregue
   pela S17. A regressão final de 274 testes passou.
7. A detecção de porta explícita precisou considerar que `URL` normaliza portas
   padrão. A validação passou a examinar a autoridade original; os casos
   causais finais passaram.

Nenhuma falha intermediária exigiu expansão da fronteira funcional.

## 13. Zero rede e publicação real

- Nenhuma chamada real ao ERP, publisher, GitHub, GHCR, VPS, DNS ou produção.
- Os transportes HTTP foram simulados nos testes.
- Nenhum `git add`, commit, tag ou push.
- Nenhuma instalação de dependência.
- Nenhum token, request de usuário ou chave idempotente real foi usado ou
  persistido.
- Nenhum Maven, pytest do publisher ou actionlint foi executado.

## 14. Divergências e itens não determinados

Não há divergência conhecida em relação ao contrato S17 nem decisão
arquitetural pendente tomada pelo executor. Os avisos não bloqueantes do
harness/build foram registrados sem ampliar o escopo.

## 15. Estado protegido final

- índice Git real vazio;
- HEAD inexistente;
- nenhuma tag;
- nenhum reflog;
- exatamente três workflows ativos;
- nenhum cache Python;
- nenhum resíduo temporário da suíte mutante;
- dependências, `dist` e caches preexistentes preservados;
- nenhuma publicação ou acesso externo;
- S18 inexistente.

**IN_PROGRESS — aguardando revisão do orquestrador**

## 16. Revisão terminal do orquestrador — 29/07/2026

### 16.1 Veredito

`ACCEPTED`.

A implementação foi revisada contra o contrato da S17 e contra as evidências
persistidas neste relatório. Não foram reexecutados testes do executor.

Foram confirmados:

- ativação estritamente local e fail-closed em produção;
- integração exclusiva no painel já existente, sem rota nova;
- token publisher mantido somente em memória;
- troca de identidade, capabilities e respostas validadas estritamente;
- publicação global sem seleção de componentes;
- idempotência persistida somente para a tentativa, sem persistir bearer token;
- tratamento explícito de resultado incerto, sem repetição automática de `POST`;
- polling single-flight com prazo e limpeza determinísticos;
- mensagens sanitizadas e ausência de segredo nos estados renderizados;
- documentação de uso e implementação alinhada ao comportamento entregue.

### 16.2 Ajuste documental não funcional

A expressão “IPv4, IPv6 e `localhost`” foi corrigida para “IPv4
`127.0.0.1` e `localhost`”. O contrato e o código já recusavam IPv6; portanto,
não houve alteração funcional nem necessidade de novo ciclo do executor.

### 16.3 Estado final

**ACCEPTED — 29/07/2026**
