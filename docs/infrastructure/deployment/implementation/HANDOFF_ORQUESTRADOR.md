# Handoff do orquestrador — Docker, CI/CD e controle de releases

> **ATENÇÃO:** este é o handoff histórico iniciado antes do primeiro commit.
> Para assumir o estado atual a partir do candidato publicado em 02/08/2026,
> use [HANDOFF_ORQUESTRADOR_FECHAMENTO.md](./HANDOFF_ORQUESTRADOR_FECHAMENTO.md).
> As seções abaixo permanecem somente como histórico arquitetural.

> **Projeto:** Empório A Baronesa
> **Workspace:** `/home/gregorio/git/baronesa/emporio`
> **Repositório remoto:** `git@github.com:greggorio/abaronesa-emporio.git`
> **Data do snapshot:** 01/08/2026
> **Estado do programa:** S01–S29 aceitas; S30 rejeitada — correction-03 autorizada
> **Finalidade:** permitir que outro CLI assuma exclusivamente o papel de
> orquestrador até a conclusão técnica e operacional desta implementação.

## 1. Missão do novo orquestrador

Conduzir a implementação aprovada de Docker, CI/CD, releases globais e
implantação de produção, mantendo código, contratos, documentação de uso e
procedimentos operacionais coerentes em cada slice.

O orquestrador:

- inspeciona o estado real antes de decidir;
- escreve contratos executáveis de slices;
- fecha decisões de arquitetura antes de delegar;
- delega implementação a um executor CLI distinto;
- revisa código, testes e relatório, não apenas a narrativa do executor;
- aceita ou rejeita formalmente cada slice;
- cria a próxima slice e o prompt de delegação no mesmo ciclo em que aceita a
  anterior;
- não executa a implementação da slice em nome do executor, salvo nova ordem
  explícita do usuário;
- não realiza commit, push, publicação ou mutação de produção em nome do
  usuário sem autorização explícita para aquela operação.

O objetivo final não é somente “ter arquivos Docker”. O sistema deve permitir:

1. commits e pushes manuais pelo desenvolvedor no terminal;
2. CI automática e publicação de candidatos imutáveis;
3. publicação de release global pela UI do ambiente de desenvolvimento;
4. atualização da release global pela UI de produção;
5. resolução automática do BOM completo e de suas dependências;
6. implantação transacional, observável e recuperável;
7. operação real na VPS com documentação correspondente.

## 2. Regra fundamental de autoridade

O executor executa; o orquestrador decide.

Uma task não pode deixar decisões relevantes em aberto. Antes da delegação, o
orquestrador deve determinar explicitamente:

- comportamento positivo e negativo;
- nomes, rotas, estados, códigos, schemas e versões;
- arquivos autorizados e proibidos;
- política de segurança e falha;
- testes causais e matriz de regressão;
- documentação afetada;
- resíduos permitidos;
- acessos externos autorizados;
- condição exata de aceite.

Se um critério novo for descoberto após a execução:

- reconhecer quando a omissão pertence ao contrato do orquestrador;
- consolidar todos os achados conhecidos em uma única emenda;
- não rejeitar repetidamente o executor por expectativas não escritas;
- não transformar uma escolha omitida em erro retroativo do executor;
- manter a mesma slice até a correção ser aceita.

O relatório do executor nunca declara uma slice aceita e nunca cria a próxima.

## 3. Ordem obrigatória de leitura ao assumir

Ler integralmente, nesta ordem:

1. este handoff;
2. [tracker das slices](./README.md);
3. [arquitetura aprovada](../proposta-docker-ci-cd-producao-emporio.md);
4. [task S22](./slices/S22-runtime-deployer-persistencia-reconciliacao-github.task.md);
5. [relatório S22](./slices/S22-runtime-deployer-persistencia-reconciliacao-github.report.md);
6. [correção S22 vigente](./slices/S22-runtime-deployer-persistencia-reconciliacao-github.correction-01.md);
7. [índice do release control](../release-control/README.md);
8. [contrato de API, estados e segurança](../release-control/CONTRATO_API_ESTADOS_SEGURANCA.md);
9. [runtime deployer](../release-control/RUNTIME_DEPLOYER.md);
10. OpenAPI, máquinas de estado e matriz de segurança referenciados nesses
    documentos.

Para revisar uma slice histórica, ler a task, todas as correções e o relatório
daquela slice. Não é necessário reler todos os relatórios anteriores para
assumir a S22.

## 4. Precedência quando houver divergência

Aplicar esta ordem:

1. instrução mais recente e explícita do usuário;
2. task ativa mais sua última correção formal;
3. contratos machine-readable: OpenAPI, JSON Schema, YAML de estados e matriz;
4. código e testes reproduzíveis do workspace;
5. documentação canônica aceita;
6. relatório do executor;
7. proposta arquitetural original.

O relatório é evidência histórica, não autoridade sobre o filesystem atual.
Se o relatório disser “sem caches” e os caches existirem, registrar o estado
real e não aceitar a slice até a divergência ser resolvida.

Não alterar silenciosamente um contrato machine-readable para fazer o código
passar. Primeiro determinar qual autoridade está correta e documentar a decisão.

## 5. Estado atual verificado

Snapshot local verificado em 31/07/2026:

```text
CWD                           /home/gregorio/git/baronesa/emporio
Git top-level                 /home/gregorio/git/baronesa/emporio
origin                        git@github.com:greggorio/abaronesa-emporio.git
HEAD                          inexistente
índice real                   vazio
tags                          zero
reflog                        vazio
repositórios Git aninhados    zero
tasks S01–S22                 22
reports S01–S22               22
S23                           inexistente
```

Workflows ativos na raiz:

```text
.github/workflows/ci.yml
.github/workflows/publish-candidate.yml
.github/workflows/publish-release.yml
.github/workflows/deploy-production.yml
```

O quinto arquivo do diretório é somente `README.md`.

Estado de resíduos no momento deste handoff:

```text
release_control/.venv/
release_control/.coverage
release_control/.pytest_cache/
release_control/.ruff_cache/
release_control/.mypy_cache/
release_control/**/__pycache__/
release_control/**/*.pyc
```

Esses caminhos estão ignorados pelo Git, mas contradizem a declaração final do
relatório S22 e os critérios da correção. Não os apagar durante uma revisão
puramente documental. O executor da correção deve removê-los ao final e o
orquestrador deve comprovar a ausência antes do aceite.

O arquivo sensível `ops/env/.env.production` possui modo `0600`. Não abrir,
transcrever, mover, apagar ou versionar esse arquivo. Aplicar a mesma cautela a
HPROF, certificados, uploads e outros artefatos sensíveis inventariados na S02.

## 6. Estado das slices

O tracker [implementation/README.md](./README.md) é a lista canônica.

- S01–S21: `ACCEPTED`.
- S22: `IN_PROGRESS`, rejeitada no ciclo atual e submetida à correção 01.
- S23: não existe e não pode ser criada antes do aceite terminal da S22.

O tracker ainda mostra `—` na coluna de relatório da S22. Ao aceitar a S22,
substituir esse marcador pelo link do relatório e registrar a data de aceite.
Não fazer essa atualização antes do aceite.

Correções históricas relevantes existem para S14 e S15. Elas continuam parte
da autoridade histórica dessas slices, mas não estão abertas.

## 7. Arquitetura que não deve regredir

### 7.1 BOM comercial global

Uma release contém exatamente seis componentes:

```text
backend
website_back
frontend
website_front
whatsapp_service
gateway
```

`release_control` é operacional e permanece fora do BOM comercial. PostgreSQL
e serviços externos são dependências de runtime, não componentes versionados
da release.

A produção recebe somente a identidade da release global. Nunca oferecer
checkboxes, tags, digests ou seleção manual de componentes.

### 7.2 Fechamento de dependências

```text
frontend      -> backend
website_back  -> backend
website_front -> website_back
backend       -> whatsapp_service
gateway       -> backend, website_back, frontend, website_front, whatsapp_service
```

O catálogo e o resolvedor em `ops/releases/` e `tools/releases/` são a
autoridade. Primeiro candidato seleciona os seis. Path desconhecido falha
fechado selecionando os seis. Alteração somente documental não publica imagem.

### 7.3 Dois modos do plano de controle

```text
RELEASE_CONTROL_MODE=publisher
RELEASE_CONTROL_MODE=deployer
```

- `publisher`: ambiente de desenvolvimento; publica releases.
- `deployer`: ambiente de produção; lista, planeja e implanta releases.

Os routers, credenciais, scopes e reconcilers são isolados. O modo não pode ser
escolhido pelo cliente. Nenhum runtime acessa Git local, Docker socket ou SSH.

### 7.4 Fluxo de CI/CD

```text
commit/push manual em main
        |
        v
ci.yml: testes, contratos, builds e scans sem push
        |
        v
publish-candidate.yml: imagens GHCR por digest + candidato não implantável
        |
        v
UI publisher -> publish-release.yml: release global imutável
        |
        v
UI deployer -> deploy-production.yml -> CLI transacional na VPS
        |
        v
outcome canônico -> reconciliador deployer -> estado local
```

Push em `main` nunca altera produção. Produção nunca usa `latest`.

### 7.5 Topologia prevista de produção

- Nginx/Certbot do host permanecem em `80/443`.
- gateway comercial interno: `127.0.0.1:8120`.
- release control deployer: `127.0.0.1:8121`.
- PostgreSQL, backends e WhatsApp não publicam portas externas.
- o plano de controle fica fora da transação que troca a stack comercial.
- o usuário operacional previsto é dedicado e não root.

As portas, paths, usuário e estado do host devem ser revalidados antes de
qualquer mutação na VPS. A proposta não substitui evidência atual do servidor.

### 7.6 Rollback atual

O executor transacional consegue compensar uma implantação que falha durante
a troca. Isso não equivale a rollback comercial solicitado depois do sucesso.

Na versão atual:

- S18 é forward-only;
- a API reservada de rollback retorna `409 RELEASE_NOT_ELIGIBLE`;
- `deployment:rollback` não é anunciado;
- nenhuma UI deve prometer downgrade seguro.

Rollback comercial só pode ser ativado depois de um contrato explícito de
planner, migrations, backup/restore, workflow, runtime e UI.

## 8. Trabalho ativo: S22

### 8.1 Veredito vigente

```text
REJECTED — correção consolidada obrigatória
```

A rejeição não encerra a slice. Ela permanece `IN_PROGRESS` até a correção
retornar e ser revisada.

Foram reproduzidos:

```text
python3 tools/deploy/validate_deployer_runtime.py
    exit 0 — deployer-runtime:valid

python3 tools/releases/release_control_contract.py validate
    exit 0 — release-control-contract:valid

python3 tools/deploy/validate_deployment_executor.py
    exit 3 — state-machine-transitions

python3 tools/releases/validate_publisher_runtime.py
    exit 2 — deployer-router
```

Os dois últimos conflitos decorrem de validadores legados que a task S22
obrigou executar, mas não autorizou alterar. Isso foi reconhecido como defeito
do contrato do orquestrador.

### 8.2 Correção vigente

A única instrução executável agora é:

[S22-runtime-deployer-persistencia-reconciliacao-github.correction-01.md](./slices/S22-runtime-deployer-persistencia-reconciliacao-github.correction-01.md)

Ela congela:

- comandos canônicos de ambiente e validação;
- compatibilidade dos validadores S19 e S15;
- leitura da instalação e elegibilidade;
- rejeição de sucesso incompatível com restore;
- persistência de incerteza em evidência remota inconsistente;
- ciclo reconciliador resiliente;
- corrida do slot ativo com `activeOperationId`;
- constraint de binding do workflow;
- enum público deployer;
- testes causais e matriz terminal.

Não emitir uma segunda correção antes de revisar o retorno integral da primeira.
Se o retorno ainda divergir, fazer uma auditoria completa e consolidar todos os
achados restantes em uma única emenda terminal, reconhecendo qualquer omissão
do contrato.

### 8.3 Prompt vigente para o executor

```text
CWD: /home/gregorio/git/baronesa/emporio

Execute integralmente:

docs/infrastructure/deployment/implementation/slices/S22-runtime-deployer-persistencia-reconciliacao-github.correction-01.md

Implemente somente as correções A–I e altere exclusivamente os arquivos da
fronteira autorizada. Execute a matriz terminal da Seção 14 e atualize:

docs/infrastructure/deployment/implementation/slices/S22-runtime-deployer-persistencia-reconciliacao-github.report.md

Não altere task, tracker ou arquivos fora da fronteira. Não crie S23. Não faça
commit, push, publicação, acesso ao GitHub real, GHCR, SSH, VPS ou produção.

Mantenha o estado:

IN_PROGRESS — aguardando revisão terminal do orquestrador
```

## 9. Como revisar o retorno da S22

Não aceitar com base na contagem declarada. Aplicar o seguinte roteiro:

1. confirmar CWD, HEAD, índice, tags, reflog e workflows;
2. conferir os arquivos realmente alterados contra a fronteira da correção;
3. ler a nova seção do relatório sem apagar a história anterior;
4. revisar diretamente os caminhos causais de serviço, reconciliação,
   migration, schemas e validadores legados;
5. comprovar que os testes novos falham contra mutantes relevantes;
6. executar a matriz terminal exatamente como corrigida;
7. confirmar cobertura de branches mínima de 90%;
8. confirmar ausência de `.venv`, coverage, caches, chaves e containers
   efêmeros;
9. confirmar que não houve acesso externo, commit, push ou S23;
10. comparar documentação humana com comportamento e contratos
    machine-readable.

Se tudo estiver verde:

1. acrescentar ao relatório uma revisão terminal objetiva com evidências;
2. atualizar a S22 para `ACCEPTED` no tracker, com data e link do relatório;
3. criar a task S23 no mesmo ciclo;
4. entregar ao usuário “S22 aprovada” e o prompt completo da S23.

Se houver divergência:

1. manter S22 `IN_PROGRESS`;
2. não criar S23;
3. distinguir defeito de execução de omissão do contrato;
4. fornecer uma única correção fechada com arquivos e provas exatas.

## 10. Ciclo padrão das próximas slices

### 10.1 Antes de escrever uma task

- revalidar Git e resíduos;
- ler as exclusões da slice aceita anterior;
- inspecionar o código consumidor real;
- identificar contratos humanos e machine-readable afetados;
- resolver decisões técnicas antes da delegação;
- separar mutação local, GitHub e VPS quando a prova independente justificar;
- definir rollback da própria mudança de infraestrutura.

### 10.2 Conteúdo obrigatório da task

```text
ID e título
estado e dependências
objetivo observável
decisões fechadas
fronteira de arquivos autorizada
arquivos proibidos
fora de escopo
comportamentos positivos e negativos
contratos de API/dados/segurança
documentação a atualizar
testes causais e mutantes
matriz de regressão
evidências e resíduos
acessos externos autorizados/proibidos
critérios de aceite
condições de bloqueio
formato do relatório do executor
próxima fronteira apenas informativa
```

### 10.3 Relatório exigido do executor

O relatório deve conter:

- CWD e autoridade lida;
- arquivos criados, alterados e removidos;
- implementação por requisito;
- comandos exatos, exits, contagens e durações;
- interpretação causal;
- testes/mutantes adicionados;
- documentação atualizada;
- estado Git e resíduos;
- acessos externos realizados ou ausência deles;
- divergências reais e itens não determinados;
- estado literal `IN_PROGRESS — aguardando revisão do orquestrador`.

### 10.4 Critério de aceite

Uma slice só é aceita quando código, contratos, documentação, regressões,
segurança e estado do workspace estão simultaneamente coerentes. Teste parcial
verde não compensa regressão vermelha.

## 11. Roadmap residual previsto

Este roadmap orienta a continuidade até o encerramento. Os números posteriores
à S22 são uma previsão, não tasks automaticamente autorizadas. O orquestrador
deve revalidar a menor dependência real antes de materializar cada contrato e
pode dividir uma etapa se houver duas mutações independentes.

### S23 — ponte de identidade do deployer

Objetivo previsto:

- emitir token RS256 curto somente para usuário ERP autorizado em produção;
- audience e scopes exclusivos `deployment:read deployment:execute`;
- configurar JWKS/issuer sem reutilizar credenciais publisher;
- não anunciar rollback;
- profiles development/test/production fail-closed;
- documentação de provisionamento e rotação sem segredos reais.

Não incluir UI, Docker, VPS ou acesso GitHub real.

### S24 — UI de produção para atualização forward

Objetivo previsto:

- exibir instalação atual, releases elegíveis e plano global;
- permitir somente atualização para a próxima release elegível;
- solicitar deployment com idempotência;
- acompanhar estado reconciliado sem inventar progresso intermediário;
- tratar instalação incerta e conflito de operação ativa;
- esconder publicação no modo deployer e não oferecer rollback.

Exigir testes de store, componentes, rotas, refresh/reload e erros públicos.

### S25 — contrato seguro de rollback comercial

Antes de implementar downgrade, decidir e documentar:

- elegibilidade da release anterior;
- compatibilidade reversa de migrations;
- quando restore de banco é obrigatório;
- retenção e identidade de backups;
- planner e bundle de rollback;
- tratamento de uploads e sessão WhatsApp;
- limites entre compensação de falha e rollback solicitado;
- estados, API, idempotência, lock e recovery.

Esta slice deve ser primeiro contratual/offline. Não ativar capability ou UI.

### S26 — executor, workflow e runtime de rollback

Objetivo previsto:

- implementar o contrato aceito em S25 no núcleo/adapters;
- versionar workflow/handoff/outcome próprios ou extensão explicitamente
  segura do contrato atual;
- reconciliar rollback sem downgrade implícito;
- restaurar banco somente segundo evidência e política aceitas;
- preservar slot global e restart safety;
- ativar a rota somente depois de provas locais integrais.

Ainda não realizar rollback real na VPS.

### S27 — UI de rollback e recuperação

Objetivo previsto:

- anunciar `deployment:rollback` somente quando o servidor realmente suportar;
- confirmar impacto e motivo;
- exibir necessidade de restore e indisponibilidade;
- acompanhar outcome e instalação incerta;
- impedir qualquer seleção por componente;
- documentar uso, limites e recuperação manual.

### S28 — empacotamento operacional do release control

Objetivo previsto:

- Dockerfile endurecido, não root e com health do `release_control`;
- Compose/perfil operacional separado da stack comercial;
- persistência PostgreSQL e migrations do plano de controle;
- listener somente loopback;
- templates de systemd/env/paths e ownership;
- ciclo independente de atualização do próprio control root;
- backup e recuperação da persistência do plano de controle.

Não modificar a VPS nessa slice.

### S29 — gate do primeiro commit e ativação remota

Objetivo previsto:

- repetir scanner de segredos e auditoria do índice candidato;
- validar todos os workflows e ações fixadas;
- produzir checklist exato do primeiro commit/push;
- preservar a decisão de que o desenvolvedor executa Git pelo terminal;
- prever o efeito remoto do primeiro push, inclusive CI e publicação de
  candidato;
- somente após autorização explícita, acompanhar o push feito pelo usuário e
  registrar os run IDs/resultados.

O executor não deve executar `git add`, commit ou push por inferência.

### S30 — ensaio remoto de candidato, publisher e release

Objetivo previsto:

- configurar permissões mínimas e GitHub App publisher;
- observar CI real e candidato publicado por digest;
- validar proveniência e artefatos no GitHub/GHCR;
- publicar uma release global pela UI de desenvolvimento;
- reconciliar reinício e idempotência;
- registrar IDs sem transcrever tokens;
- provar que produção não foi alterada.

Esta é mutação externa e exige autorização explícita, plano de cleanup e
critérios de parada.

### S31 — preparação segura da VPS

Preferir duas fases na mesma macrofronteira: artefatos/preflight local e, só
depois de revisão, execução remota autorizada.

Objetivo previsto:

- revalidar portas, DNS, Nginx, Docker, disco e serviços existentes;
- criar usuário `deploy-emporio` ou outro nome previamente congelado;
- provisionar paths, ownership, permissões, systemd e control root;
- instalar autenticação GHCR somente leitura;
- configurar GitHub App deployer, known hosts, environment e secrets;
- instalar Nginx host sem disputar `80/443`;
- provisionar PostgreSQL/bancos/volumes e política de backup;
- instalar `release_control` fora da stack comercial;
- comprovar health e ausência de segredos em logs.

Não realizar a implantação comercial até o preflight ser aceito.

### S32 — primeiro release e implantação acompanhados

Objetivo previsto:

- validar backup real antes de migration;
- publicar/selecionar release global imutável;
- executar implantação exclusivamente pelo fluxo UI -> runtime -> Actions ->
  usuário dedicado -> CLI;
- comprovar digests, migrations, health, smoke e persistências;
- comprovar os dois domínios por HTTPS;
- registrar instalação atual reconciliada;
- não executar comandos manuais que contornem o journal para “fazer passar”.

Essa slice deve conter runbook de abortar, critérios de rollback e janela de
manutenção aprovada.

### S33 — exercícios de falha e encerramento operacional

Objetivo previsto:

- exercitar falha de pull, migration, health e smoke em ambiente controlado;
- comprovar compensação de imagens;
- comprovar rollback comercial e restore segundo o contrato aceito;
- comprovar retomada após reinício e plano de controle disponível;
- configurar alertas, retenção, limpeza segura e monitoramento;
- documentar rotação de credenciais e atualização do `release_control`;
- criar arquitetura real de produção e runbook operacional final;
- reconciliar proposta, documentação e comportamento implantado;
- produzir auditoria final de critérios de aceite.

Somente depois dessa auditoria o programa completo pode ser declarado
concluído.

## 12. Gates antes de qualquer acesso externo

Antes de GitHub real, GHCR, SSH ou VPS:

1. slice local predecessora aceita;
2. objetivo e mutações externas enumerados;
3. credenciais e permissões mínimas definidas;
4. alvos exatos resolvidos por leitura;
5. rollback/cleanup da própria ação definido;
6. segredos nunca impressos;
7. autorização explícita do usuário;
8. evidência persistente sanitizada;
9. condição de parada em caso de divergência.

Não usar SSH root como mecanismo normal de deploy. Não executar prune, apagar
volumes, substituir Nginx, alterar DNS/TLS, migrar dados reais ou reiniciar
serviços fora de uma task que autorize exatamente a ação.

## 13. Gates de segurança permanentes

- nunca abrir ou transcrever `.env.production` em relatório;
- nunca versionar chave, token, senha, PFX, HPROF, upload ou sessão WhatsApp;
- usar valores efêmeros válidos em testes criptográficos;
- scanners devem ler arquivos candidatos sem vazar achados sensíveis;
- nenhum container comercial ou release control monta Docker socket;
- GitHub Apps publisher e deployer são distintas;
- credencial da VPS lê packages, não publica;
- workflows usam ações fixadas por SHA;
- `latest` é proibido em produção;
- PostgreSQL e serviços internos não publicam porta;
- somente gateway e control plane usam loopback no host;
- erro público e log são sanitizados;
- resultado incerto nunca vira sucesso presumido.

## 14. Definição de conclusão do programa

Não declarar finalização enquanto todos os itens abaixo não tiverem evidência
real e atual:

### Repositório e CI

- monorepo possui primeiro commit seguro e `main` remota;
- scanner de segredos e CI passam no GitHub;
- existe somente o conjunto canônico de workflows;
- candidato real foi publicado por digest, sem `latest`;
- proveniência e artefatos foram revalidados.

### Release publisher

- usuário autorizado publica release pela UI de desenvolvimento;
- SemVer e BOM global são calculados no servidor;
- release e assets são imutáveis e reconciliáveis;
- retry/restart não duplica publicação.

### Produção

- usuário, paths, permissões, Nginx, TLS, systemd e secrets estão instalados;
- `release_control` deployer permanece disponível fora da stack comercial;
- UI de produção mostra current, elegibilidade e plano corretos;
- somente uma operação global ocorre por vez;
- primeiro deploy foi executado sem SSH root e sem Docker socket na aplicação;
- os seis digests instalados correspondem à release;
- migrations, health e smoke passaram;
- PostgreSQL, uploads e sessão WhatsApp sobreviveram à recriação;
- ambos os domínios respondem por HTTPS.

### Recuperação e operação

- falha controlada aciona compensação correta;
- release anterior e backups necessários permanecem disponíveis;
- rollback comercial e restore foram comprovados ou, se o usuário reduzir o
  escopo, a indisponibilidade ficou formalmente aceita e removida dos critérios
  arquiteturais;
- reinício e reconciliação não inventam estado;
- monitoramento, alertas, retenção e rotação estão documentados;
- atualização independente do `release_control` tem procedimento comprovado;
- arquitetura real e runbook final correspondem à VPS instalada.

### Encerramento documental

- tracker marca todas as slices como `ACCEPTED`;
- nenhum relatório declara sucesso contrário ao filesystem;
- proposta permanece histórica ou é explicitamente reconciliada;
- existe documento separado de arquitetura real de produção;
- existe procedimento operacional de deploy, rollback, restore e incidente;
- auditoria final registra comandos, IDs sanitizados, resultados e riscos
  residuais aceitos pelo usuário.

## 15. Comandos mínimos de retomada

Executar primeiro apenas leitura:

```bash
cd /home/gregorio/git/baronesa/emporio
git rev-parse --show-toplevel
git rev-parse --verify HEAD
git ls-files --stage
git tag --list
git reflog show --all
git remote -v
find .github/workflows -maxdepth 1 -type f -printf '%f\n' | sort
find release_control -maxdepth 3 \
  \( -name '.venv' -o -name '.coverage' -o -name '.pytest_cache' \
     -o -name '.ruff_cache' -o -name '.mypy_cache' -o -name '__pycache__' \
     -o -name '*.pyc' \) -print
```

Depois, reler a correção S22 e aguardar ou solicitar o relatório do executor.
Não iniciar a matriz pesada antes de existir uma implementação de correção para
revisar.

## 16. Primeira mensagem recomendada ao assumir

```text
Assumi a orquestração a partir da S22. S01–S21 permanecem aceitas; S22 está
IN_PROGRESS sob a correção causal consolidada 01; S23 permanece bloqueada.

Vou revisar o próximo retorno contra a task, a correção, o código real e a
matriz terminal. Não aceitarei a slice por declaração do relatório e não criarei
S23 antes do aceite formal. Quando a S22 for aceita, gerarei no mesmo ciclo o
contrato fechado e o prompt de delegação da S23.
```

## 17. Atualização do snapshot após a revisão da S23

Esta seção atualiza o snapshot deste documento sem apagar a narrativa histórica
das rejeições e correções anteriores.

Após a revisão terminal da correction-02, o estado canônico atual é:

- S01–S23: ACCEPTED;
- S24: PLANNED, com dependência S23 aceita;
- S25: inexistente e bloqueada;
- resíduos Python, coverage e caches: busca recursiva vazia;
- índice Git real, tags e reflog: vazios; HEAD ainda inexistente;
- nenhum commit, push, GitHub, GHCR, SSH, VPS ou produção executado;
- a próxima delegação autorizada é exclusivamente a task S24.

O [tracker](./README.md) e a seção terminal do
[relatório S23](./slices/S23-ponte-identidade-deployer-rs256-jwks.report.md)
passam a prevalecer sobre as seções deste handoff que descrevem S22 em
correção. O contrato fechado da próxima slice está em
[S24-ui-producao-atualizacao-forward.task.md](./slices/S24-ui-producao-atualizacao-forward.task.md).

Não iniciar S24 por inferência: primeiro delegar o prompt contido no contrato e
manter a revisão terminal como autoridade exclusiva para o aceite.

## 18. Atualização do snapshot após a revisão da S24

Esta seção atualiza o snapshot operacional sem apagar a narrativa histórica
da delegação da S24.

Após a revisão terminal do retorno inicial da S24, o estado canônico atual é:

- S01–S23: ACCEPTED;
- S24: IN_PROGRESS, rejeitada neste ciclo por divergência da matriz terminal;
- correção consolidada: correction-01, limitada ao alias test:unit em
  frontend/package.json;
- S25: inexistente e bloqueada;
- resíduos prescritos: busca recursiva vazia;
- índice Git real, tags e reflog: vazios; HEAD ainda inexistente;
- nenhum commit, push, GitHub, GHCR, SSH, VPS, DNS ou produção executado.

O [tracker](./README.md) e a seção terminal do
[relatório S24](./slices/S24-ui-producao-atualizacao-forward.report.md)
prevalecem sobre a seção 17, que registra o snapshot anterior à execução.
A correção fechada está em
[S24-ui-producao-atualizacao-forward.correction-01.md](./slices/S24-ui-producao-atualizacao-forward.correction-01.md).

Não aceitar a S24 nem criar S25 antes de reproduzir o comando canônico
npm run test:unit -- --run com exit 0 e revisar novamente a matriz inteira.

## 19. Atualização do snapshot após o aceite da S24

Após a revisão terminal da correction-01, o estado canônico atual é:

- S01–S24: ACCEPTED;
- S25: PLANNED, com dependência S24 aceita;
- S26: inexistente e bloqueada;
- resíduos prescritos: busca recursiva vazia;
- índice Git real, tags e reflog: vazios; HEAD ainda inexistente;
- nenhum commit, push, GitHub, GHCR, SSH, VPS, DNS ou produção executado.

O [tracker](./README.md), o relatório terminal da
[S24](./slices/S24-ui-producao-atualizacao-forward.report.md) e a task
[S25](./slices/S25-contrato-seguro-rollback-comercial.task.md) passam a ser a
referência operacional. A S25 é exclusivamente contratual/offline: não ativa
capability, API, workflow, runtime ou UI de rollback.

A próxima delegação autorizada é somente a task S25. Não iniciar S26 antes do
aceite formal da S25.

## 20. Atualização do snapshot após o aceite da S25

Após a revisão terminal do contrato de rollback comercial, o estado canônico
atual é:

- S01–S25: ACCEPTED;
- S26: PLANNED, com dependência S25 aceita;
- S27: inexistente e bloqueada;
- rollback comercial: contrato implementável, ainda sem runtime/capability ativa;
- nenhum rollback, deploy, GitHub, GHCR, SSH, VPS, DNS ou produção executado;
- workspace pré-Git: HEAD, tags e reflog ausentes; resíduos prescritos vazios.

O [tracker](./README.md) e o aceite terminal do
[relatório S25](./slices/S25-contrato-seguro-rollback-comercial.report.md)
prevalecem sobre as seções históricas anteriores. A próxima delegação
autorizada é exclusivamente a task
[S26-executor-workflow-runtime-rollback.task.md](./slices/S26-executor-workflow-runtime-rollback.task.md).

Não iniciar S27 antes do aceite formal da S26.

## 21. Atualização do snapshot após o aceite da S26

Após a revisão terminal da correction-01, o estado canônico atual é:

- S01–S26: ACCEPTED;
- S27: PLANNED, com dependência S26 aceita;
- S28: inexistente e bloqueada;
- runtime rollback: ativo localmente; nenhuma operação real executada;
- suíte integral `release_control/tests`: não executada porque a fixture PostgresContainer é proibida pela S26; testes offline e oficiais sem fixture verdes;
- nenhum acesso GitHub, GHCR, SSH, VPS, DNS ou produção; HEAD, tags e reflog ausentes.

O [tracker](./README.md) e o aceite terminal do
[relatório S26](./slices/S26-executor-workflow-runtime-rollback.report.md)
prevalecem sobre as seções históricas anteriores. A próxima delegação
autorizada é exclusivamente a task
[S27-ui-rollback-recuperacao.task.md](./slices/S27-ui-rollback-recuperacao.task.md).

Não iniciar S28 antes do aceite formal da S27.

## 22. Atualização do snapshot após o aceite da S27

Após a revisão terminal da S27, o estado canônico atual é:

- S01–S27: `ACCEPTED`;
- S28: `PLANNED`, com dependência S27 aceita;
- S29: inexistente e bloqueada;
- UI forward e rollback: implementadas localmente, sem operação real executada;
- S27 passou lint, 163/163 testes, TAP, build e validadores offline;
- `npm ci --offline` permanece limitado pelo cache ausente de
  `zip-stream-4.1.1.tgz`;
- `release_control/.pytest_cache` permanece fora da fronteira S27;
- nenhum acesso a GitHub, GHCR, SSH, VPS, DNS, produção, rede, container ou
  segredo real; HEAD, tags e reflog continuam ausentes.

O [tracker](./README.md) e o aceite terminal do
[relatório S27](./slices/S27-ui-rollback-recuperacao.report.md) prevalecem
sobre as seções históricas anteriores. A próxima delegação autorizada é
exclusivamente a task
[S28-empacotamento-operacional-release-control.task.md](./slices/S28-empacotamento-operacional-release-control.task.md).

Não iniciar S29 antes do aceite formal da S28.

## 23. Atualização do snapshot após o aceite da S28

Após a revisão terminal da S28, o estado canônico atual é:

- S01–S28: `ACCEPTED`;
- S29: `PLANNED`, com dependência S28 aceita;
- S30: inexistente e bloqueada;
- empacotamento isolado do `release_control`: declarado e validado offline;
- `uv lock --check` na raiz permanece inadequado porque o lock está em
  `release_control/`; a checagem equivalente do diretório correto passou;
- `release_control/.pytest_cache` permanece preexistente e fora da fronteira;
- o inventário atual contém cinco workflows YAML na raiz, embora o README de
  workflows ainda declare quatro; `rollback-production.yml` ainda usa uma tag
  de action em vez de SHA. Esses dois pontos são a fronteira explícita da S29;
- nenhum commit, push, acesso GitHub/GHCR, SSH, VPS, produção ou alteração
  remota; HEAD, índice real, tags e reflog continuam sem conteúdo.

O [tracker](./README.md) e o aceite terminal do
[relatório S28](./slices/S28-empacotamento-operacional-release-control.report.md)
prevalecem sobre as seções históricas anteriores. A próxima delegação
autorizada é exclusivamente a task
[S29-gate-primeiro-commit-ativacao-remota.task.md](./slices/S29-gate-primeiro-commit-ativacao-remota.task.md).

Não iniciar S30 antes do aceite formal da S29.

## 24. Atualização do snapshot após a rejeição da S29

Após a revisão terminal da S29, o estado canônico atual é:

- S01–S28: `ACCEPTED`;
- S29: `IN_PROGRESS`, rejeitada neste ciclo por falha do scanner canônico;
- correction-01 da S29: autorizada para corrigir falsos positivos comprovados
  sem enfraquecer a detecção de segredos;
- S30: inexistente e bloqueada;
- workflows, contratos e validador de inventário: verdes;
- scanner: exit 123 com oito fingerprints não allowlisted, todos classificados
  para correção como marcadores sintéticos, placeholder ou falso positivo de
  parsing YAML;
- `actionlint`: indisponível no ambiente, sem instalação ou rede;
- nenhum commit, push, acesso remoto, segredo real ou workflow remoto
  executado.

O [tracker](./README.md), a rejeição terminal do
[relatório S29](./slices/S29-gate-primeiro-commit-ativacao-remota.report.md) e a
[correction-01](./slices/S29-gate-primeiro-commit-ativacao-remota.correction-01.md)
prevalecem sobre a seção 23. Não iniciar S30 antes do novo aceite formal da
S29.

## 25. Atualização do snapshot após o aceite da correction-01 da S29

Após a revisão terminal da correction-01, o estado canônico atual é:

- S01–S29: `ACCEPTED`;
- S30: `PLANNED`, com dependência S29 aceita e autorização externa pendente;
- S31: inexistente e bloqueada;
- scanner canônico S29: exit 0, `secret-scan:clean` e `unsupported=0`;
- testes causais da correction-01: 13 unittest, 13 pytest e 21 subtestes;
- validadores de workflow, release, candidate, deploy, rollback e
  release-control: verdes;
- workspace pré-Git preservado: índice vazio, `HEAD` ausente, tags e reflog
  vazios; `actionlint` continua indisponível no ambiente;
- nenhum GitHub, GHCR, rede, segredo real, commit, push, SSH, VPS, Docker,
  produção ou workflow remoto foi acessado.

O [tracker](./README.md) e o aceite terminal do
[relatório S29](./slices/S29-gate-primeiro-commit-ativacao-remota.report.md)
prevalecem sobre a seção 24. A próxima delegação é exclusivamente a task
[S30-ensaio-remoto-candidato-publisher-release.task.md](./slices/S30-ensaio-remoto-candidato-publisher-release.task.md).

S30 deve começar pelo preflight local. Seu ensaio remoto, configuração de
GitHub App/variáveis, publicação de candidato e release exigem autorização
externa explícita; a task não autoriza essas mutações por si só. Não iniciar
S31 antes do aceite formal da S30.

## 26. Atualização do snapshot após o checkpoint local da S30

Após a revisão da Fase 0, o estado canônico atual é:

- S01–S29: `ACCEPTED`;
- S30: `IN_PROGRESS`; Fase 0 aceita, Fase 1 externa não autorizada;
- S31: inexistente e bloqueada;
- preflight S30: validadores verdes, 73 actions com SHA válido e scanner
  `secret-scan:clean` com `unsupported=0`;
- índice, `HEAD`, tags e reflog continuam vazios/ausentes conforme o workspace
  pré-Git;
- nenhum GitHub, GHCR, registry, rede, workflow remoto, segredo, commit,
  push, SSH, VPS, Docker, produção ou cleanup remoto foi executado.

O [relatório S30](./slices/S30-ensaio-remoto-candidato-publisher-release.report.md)
registra o checklist remoto e as divergências ambientais. A próxima ação é
uma autorização externa explícita para a Fase 1 da S30; não criar S31 antes do
aceite completo da S30.

## 27. Atualização do snapshot após a inspeção remota da S30

A autorização para GitHub foi utilizada somente para inspeção do repositório
`greggorio/abaronesa-emporio`. O estado canônico atual é:

- S01–S29: `ACCEPTED`;
- S30: `IN_PROGRESS`, Fase 0 aceita e inspeção remota sem mutação concluída;
- S31: inexistente e bloqueada;
- repositório remoto privado acessível, mas vazio: `main` declarada, 0
  branches, 0 commits, 0 workflows, 0 runs e 0 releases;
- primeiro push continua reservado ao usuário pela S29;
- sessão GitHub atual não possui `read:packages`, impedindo verificação GHCR;
- candidate, manifest, digest, provenance, attestation e release ainda não
  existem remotamente;
- nenhum push, dispatch, App/configuração, segredo, deploy, rollback ou
  alteração de produção foi executado.

O [relatório S30](./slices/S30-ensaio-remoto-candidato-publisher-release.report.md)
registra a matriz remota e os bloqueios. Para continuar, é necessário o
primeiro push, a menos que a emenda-01 seja aplicada. A publicação também
exige aprovação dos metadados da release. Não criar S31 antes do aceite
completo da S30.

## 28. Emenda-01 — primeiro commit e push pelo executor

Por decisão explícita do usuário e emenda formal do orquestrador, o executor
da S30 está autorizado a criar o primeiro commit e fazer push somente para
`git@github.com:greggorio/abaronesa-emporio.git`, branch `main`, usando o
commit message `chore: establish initial emporio baseline`.

A autorização é condicionada aos gates da
[emenda-01](./slices/S30-ensaio-remoto-candidato-publisher-release.amendment-01.md):
scanner e validadores verdes, revisão da lista staged, `git diff --cached
--check`, remote/branch exatos e ausência de segredos. Force push, tags,
outras branches/remotes, release, deploy, rollback e produção permanecem
proibidos.

A próxima delegação é o prompt da emenda-01 no contrato S30. Depois do push, o
executor deve observar CI e candidato; a publicação da release continua
dependendo de metadados explicitamente aprovados. S31 permanece inexistente.

## 29. Atualização do snapshot após a rejeição da S30

Após a revisão do primeiro ensaio remoto, o estado canônico atual é:

- S01–S29: `ACCEPTED`;
- S30: `REJECTED`, com correction-01 autorizada;
- S31: inexistente e bloqueada;
- commit baseline `b71272f4b5c313aa70cb97c8948643eda73d7bec` publicado em
  `origin/main`, sem tags;
- CI `30667668206`: falha em `plan`, `contracts` e `backend`;
- Publish Candidate `30667761457`: falha fechada no `trust` por ausência do
  artifact `candidate-plan`;
- zero candidatos, manifestos, digests, provenance, attestations, imagens
  GHCR, releases ou recursos de produção;
- correction-01 limitada aos quatro defeitos A–D e a um segundo commit/push
  normal na mesma branch.

O [relatório S30](./slices/S30-ensaio-remoto-candidato-publisher-release.report.md)
e a [correction-01](./slices/S30-ensaio-remoto-candidato-publisher-release.correction-01.md)
prevalecem sobre o snapshot anterior. Não aceitar S30 nem criar S31 antes da
nova revisão terminal.

## 30. Atualização do snapshot após a rejeição da correction-01 da S30

Após a revisão do commit `41ab410d757154131ce6a2344fd8e561152d2acd`, o estado
canônico atual é:

- S01–S29: `ACCEPTED`;
- S30: `REJECTED`, com correction-02 autorizada;
- S31: inexistente e bloqueada;
- CI `30685735159`: `plan` e `backend` verdes, `contracts` ainda falho por E;
- Publish Candidate `30685795981`: falho no `trust` por F;
- zero candidatos finais, releases, tags, imagens GHCR, provenance ou
  attestations;
- correction-02 limitada a isolar a fixture E e reordenar/validar a
  persistência do `workflow-run.json` em F.

O [relatório S30](./slices/S30-ensaio-remoto-candidato-publisher-release.report.md)
e a [correction-02](./slices/S30-ensaio-remoto-candidato-publisher-release.correction-02.md)
prevalecem sobre o snapshot anterior. Não aceitar S30 nem criar S31 antes da
nova revisão terminal.

## 31. Atualização do snapshot após a rejeição da correction-02 da S30

Após a revisão do commit `bf20c02fb374e9bd3bdabc1dc5f8e604b0a2a4c2`, o estado
canônico atual é:

- S01–S29: `ACCEPTED`;
- S30: `REJECTED`, com [correction-03](./slices/S30-ensaio-remoto-candidato-publisher-release.correction-03.md)
  autorizada;
- S31: inexistente e bloqueada;
- E e F foram provados remotamente: o job `contracts` executou 298 testes
  verdes e o job `trust` passou checkout, persistência e download;
- CI `30686261529`: `plan`, `backend`, web, WhatsApp e demais gates
  prévios verdes; `contracts` falhou por G, porque `ci.yml` chama
  `release_control_contract.py` sem `validate`;
- Publish Candidate `30686325732`: falhou por H no `trust.py`, que tenta
  `git fetch origin main` com `persist-credentials: false`;
- a revisão do código encontrou a mesma chamada sem credencial em
  `publish_guard.py`, que precisa ser fechada na mesma correction;
- zero candidato final, manifestos, digests, provenance, attestations,
  imagens GHCR, releases ou tags; nenhum deploy, rollback, SSH, VPS, DNS ou
  efeito de produção ocorreu;
- a correction-03 escolhe resolver `main` pela API GitHub usando `GH_TOKEN`
  somente nos passos `trust.py` e `publish_guard.py`, preservando
  `persist-credentials: false` e a semântica de lineage;
- a correction também corrige o teste causal irmão de E em
  `test_causal_corrections.py` e exige uma única commit/push normal, seguido
  de observação da CI e do Publish Candidate;
- não criar S31 antes do aceite terminal da S30.

O [relatório S30](./slices/S30-ensaio-remoto-candidato-publisher-release.report.md)
e a correction-03 prevalecem sobre o snapshot anterior. O executor deve
alterar somente a fronteira da correction-03 e terminar o relatório com
`IN_PROGRESS — aguardando revisão do orquestrador`.

## 32. Transferência da orquestração e divisão da S30 — 01/08/2026

Por decisão explícita do usuário, a orquestração passou para este agente ao
final do ciclo da correction-03. O estado canônico atual é:

- S01–S29: `ACCEPTED`;
- S30: `SPLIT` — contrato-pai histórico, dividido em S30a e S30b;
- S30a: `PLANNED`, delegação autorizada;
- S30b e S31: inexistentes e bloqueadas;
- `main` remota em `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`, quatro commits,
  zero tags, zero releases;
- oito runs automáticos, todos `failure`; três artifacts, todos `candidate-plan`;
- nenhuma imagem em GHCR, nenhum manifesto final, provenance, attestation,
  deploy, rollback ou efeito de produção.

### 32.1 Diagnóstico que motivou a mudança

A S30 acumulou quatro ciclos remotos e oito defeitos preexistentes (A–H, mais
G-linha-67 e o irmão de E). Nenhum foi introduzido pelas correções. A causa
estrutural é dupla:

1. os contratos afirmam **texto** e não **comportamento** — `validate_ci.py`
   exigia a presença da string de um comando sem nunca provar que ele é
   invocável;
2. `set -e` num passo de onze comandos, somado à dependência entre jobs, revela
   um defeito por execução remota, ao custo de um commit permanente e uma
   rodada de CI por defeito.

### 32.2 Decisões de processo

- **Prova local antes da remota.** Docker local passa a ser autorizado para
  build e scan de leitura. O GitHub Actions deixa de ser o depurador.
- **Fronteira por classe, não por lista de arquivos.** A lista fechada obrigou
  o executor a parar três vezes com a correção de uma linha na mão.
- **Gate de invocabilidade.** Todo comando de utilitário nos workflows passa a
  ser provado executável pela própria CI, fechando a família G de uma vez.
- **Divisão da S30.** Um aceite que exigia simultaneamente CI verde, candidato,
  release pela UI, idempotência e restart nunca convergiria; S30a fecha CI e
  candidato, S30b trata da release.
- **Evidência por ciclo em apêndice.** O relatório da S30 chegou a 1.900 linhas
  num arquivo único; relatórios futuros separam evidência por ciclo.

### 32.3 Decisões técnicas fechadas

- `ci.yml` linhas 67 e 69 recebem o subcomando `validate`; a auditoria fechou a
  família e as outras cinco chamadas do job `contracts` estão corretas.
- O PostgreSQL do job `backend` converge para o digest imutável já usado pelo
  `publish-candidate.yml`, abandonando a tag flutuante `16.6-alpine`.
- A política do Trivy **não** é alterada especulativamente: a S30a mede os
  achados localmente e para antes do commit se houver HIGH/CRITICAL, para que a
  decisão seja tomada com inventário real.

### 32.4 Bloqueios que dependem do usuário

- `read:packages` ausente na sessão GitHub; vai travar a verificação GHCR no
  momento em que o `build` publicar. Resolver antes, não no momento da falha.
- Política do Trivy, caso a paridade local acuse HIGH/CRITICAL sem correção
  disponível.

O contrato fechado da próxima delegação é
[S30a](./slices/S30a-paridade-local-fechamento-ci-candidato.task.md). Não criar
S30b nem S31 antes do aceite formal da S30a.
