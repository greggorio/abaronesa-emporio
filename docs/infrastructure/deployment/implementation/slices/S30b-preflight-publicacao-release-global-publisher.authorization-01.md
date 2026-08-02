# S30b — authorization-01: publicação da release global v0.1.0

> **Estado:** `AUTHORIZED`
> **Tipo:** execução integral da etapa B pela UI/runtime publisher
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Contrato principal:** `S30b-preflight-publicacao-release-global-publisher.task.md`
> **Relatório contínuo:** `S30b-preflight-publicacao-release-global-publisher.report.md`

## 1. Autoridade humana e resultado

Em 02/08/2026, o usuário aprovou a release `v0.1.0`, os textos desta
autorização e autorizou integralmente a etapa B, incluindo:

- configuração de `RELEASE_PUBLISHER_ACTOR_IDS`;
- serviços e migrations locais;
- uma publicação pela UI/runtime;
- criação da tag `v0.1.0` e da GitHub Release;
- replay idempotente;
- restart local do publisher;
- limpeza dirigida.

Não existe autorização para deploy, rollback, VPS ou produção.

O executor deve conduzir a etapa ponta a ponta sem solicitar nova confirmação.
Informações técnicas descobríveis — IDs da GitHub App, ator, instalação,
paths seguros, portas efêmeras e credenciais locais descartáveis — pertencem
à execução, não são nova decisão humana.

Resultado esperado:

```text
UI development autenticada como ROLE_SYSTEM
  -> runtime publisher local
  -> um dispatch de publish-release.yml
  -> tag v0.1.0 e GitHub Release imutável
  -> operação PUBLISHED reconciliada
  -> replay da mesma chave sem novo dispatch
  -> restart do publisher sem redispatch
```

## 2. Metadados fechados

Usar literalmente:

```text
candidateId
candidate-50f423a979d7723d0e15d56b1d72625ea2b8ebea-30757430990-1

versionBump
MINOR

release esperada
v0.1.0

description
Primeira release global operacional do Empório, publicada pelo fluxo canônico de controle de releases.

changelog
- consolida os seis componentes comerciais em um BOM global imutável;
- vincula as imagens candidatas por digest;
- incorpora CI, scans de segurança e ensaio integrado aprovados;
- executa previamente as migrations dos bancos ERP e website;
- mascara as credenciais efêmeras do ambiente candidato;
- publica e reconcilia a release pelo runtime publisher e pela interface ERP.
```

Não resumir, reescrever, traduzir ou completar os textos. A ausência de release
anterior torna `MINOR` igual a `v0.1.0`; resultado diferente exige parada antes
da publicação ou, se detectado depois do POST, preservação fail-closed da
evidência sem segunda tentativa.

## 3. Base obrigatória

O checkpoint anterior a esta autorização é:

```text
branch              main
HEAD pai            73108f2501e96247374092cfbcc39377cfb7d528
origin/main         50f423a979d7723d0e15d56b1d72625ea2b8ebea
remoto main         50f423a979d7723d0e15d56b1d72625ea2b8ebea
ahead no pai        2 commits documentais
stage no pai        vazio
worktree no pai     limpo
tags                zero
GitHub Releases      zero
runs publish-release zero
```

O prompt informará o SHA do commit local que contém esta autorização e seu
SHA-256. Antes de qualquer mutação, exigir:

- `HEAD` igual ao SHA do prompt;
- exatamente três commits documentais lineares em `origin/main..HEAD`;
- remoto ainda em `50f423a...`;
- stage e worktree vazios;
- task e relatório com os hashes da seção 4;
- nenhum run ativo em `main`;
- zero tag, release e run `publish-release.yml`;
- zero run `deploy-production.yml` e `rollback-production.yml`;
- candidato terminal e três artifacts ainda válidos e não expirados.

Divergência exige parada antes de configurar variável, iniciar serviço ou
emitir POST. Não fazer pull, push, merge, rebase, amend ou reescrita de main.

## 4. Integridade da autoridade recebida

Exigir antes de agir:

```text
S30b task
sha256 91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11

S30b report com aceite da etapa A
sha256 1fd095cb747a7457858f6c9b4a9ac67cd17487514315d260a05fb027c754ba31
```

Esta autorização, a task, o tracker e os dois commits documentais anteriores
são imutáveis durante a execução. O único arquivo do repositório que poderá ser
editado é o relatório contínuo, depois do sucesso ou da primeira parada.

## 5. Fronteira autorizada

O executor pode:

1. usar APIs GitHub GET para revalidar toda a base;
2. localizar a configuração real da GitHub App publisher por nomes e paths
   seguros fora do repositório;
3. ler a chave privada da App somente dentro do processo que valida/minta seu
   JWT e installation token, sem imprimir, copiar ou persistir o conteúdo;
4. consultar `/app`, a instalação e o repositório para provar slug, ator bot,
   instalação, seleção e permissões mínimas;
5. configurar uma única vez a variável do repositório
   `RELEASE_PUBLISHER_ACTOR_IDS` com o ID decimal confirmado do ator bot;
6. criar um diretório `mktemp -d`, segredos descartáveis e chaves RSA locais
   somente para a prova;
7. iniciar PostgreSQL efêmero e isolado, aplicar as migrations do ERP e do
   publisher e criar um usuário ERP local descartável com papel `SYSTEM` pelo
   bootstrap opt-in existente;
8. iniciar backend ERP, publisher e frontend somente em loopback;
9. usar automação de navegador local para autenticar o usuário descartável e
   operar a interface real;
10. realizar os dois POSTs idempotentes descritos na seção 9, com uma única
    chave e um único dispatch;
11. parar e reiniciar somente o publisher local uma vez;
12. observar GitHub Actions, tag, release, assets e reconciliação;
13. limpar processos, container, redes, volumes, diretórios, browser e segredos
    criados nominalmente pela prova;
14. acrescentar a evidência sanitizada ao relatório contínuo.

Também é permitido usar as imagens locais já presentes pelos digests:

```text
postgres@sha256:4e6e670bb069649261c9c18031f0aded7bb249a5b6664ddec29c013a89310d50
node@sha256:f70403e87646dc51b45295f4b8b70cdad0b63d2297c4c9899119b03f7af7a6b3
```

Se dependências bloqueadas pelo lock precisarem ser materializadas, fazê-lo
somente em diretório temporário, com `uv sync --locked`, `npm ci` ou Maven sem
alterar lockfiles. Não instalar ferramenta global nem modificar o host.

## 6. Proibições permanentes

Não:

- abrir ou usar `ops/env/.env.production`;
- usar os PostgreSQL preexistentes nas portas 5432 ou 5434;
- reutilizar conta, senha, JWT, chave RSA ou banco de produção;
- imprimir token ERP/publisher, installation token, JWT da App, chave privada,
  senha, pepper, idempotency key ou conteúdo de arquivo de segredo;
- gravar segredo em arquivo do repositório, argumento de processo ou histórico;
- editar código, `.env` existente, task, authorization, tracker ou outro
  relatório;
- chamar o POST do publisher por `curl`, script HTTP ou dispatch manual como
  substituto da UI;
- executar `gh workflow run`, rerun, retry, cancel ou segundo dispatch;
- criar nova idempotency key depois do primeiro POST;
- criar outra tag/release, excluir draft, tag, release, run, artifact ou log;
- acessar GHCR, Docker de produção, SSH, VPS, deploy ou rollback;
- executar stage, commit, push, pull, merge, rebase ou amend;
- presumir sucesso diante de timeout, resposta incerta ou vínculo ambíguo.

O uso local de Docker está limitado ao PostgreSQL efêmero e, se necessário,
ao Node de desenvolvimento pelo digest autorizado. Não tocar no container
preexistente `baronesa-postgres` nem em seus volumes, portas ou rede.

## 7. Check seguro da GitHub App publisher

Antes de criar a variável do repositório ou iniciar serviços, localizar a
configuração publisher separada da deployer. Nunca aceitar a mesma chave/path
para os dois modos quando ambos forem observáveis.

Com JWT da App mantido apenas em memória, confirmar de forma sanitizada:

- App ID positivo e slug esperado pela configuração local;
- installation ID positivo e instalação pertencente ao repositório canônico;
- repositório `greggorio/abaronesa-emporio` incluído;
- `actions: write`, necessário para o único workflow dispatch;
- `contents: read`, necessário para refs, releases e assets;
- nenhum escopo administrativo ou de produção usado pelo runtime;
- chave privada regular, fora do repositório, não symlink e com modo restrito.

Derivar o login bot a partir do slug, consultar sua identidade pública e exigir
`type=Bot` e ID decimal positivo. Registrar somente App ID, installation ID,
slug, login bot, ID bot e nomes/níveis das permissões; nunca registrar JWT,
token ou chave.

Configurar:

```text
RELEASE_PUBLISHER_ACTOR_IDS=<ID decimal confirmado do bot>
```

Usar a API/CLI GitHub sem imprimir o valor anterior ou autenticação. Ler de
volta apenas `PRESENT` e confirmar que o contrato remoto recebeu uma lista CSV
válida com exatamente o ator confirmado, sem expor a lista no relatório.

Se App, instalação, chave, repositório ou permissão mínima estiver ausente ou
divergente, parar antes de mudar a variável e antes de qualquer publicação. Não
criar App, rotacionar chave ou ampliar permissão por conta própria.

## 8. Ambiente local isolado

Criar toda a prova sob um único diretório temporário nominal e registrar o path
sem registrar os arquivos sensíveis. Gerar valores aleatórios fortes para:

- senha administrativa do PostgreSQL efêmero;
- senha do usuário ERP `SYSTEM` descartável;
- JWT secret comercial local;
- hash pepper do publisher;
- chave RSA 3072 bits do emissor ERP e `kid` válido.

Usar PostgreSQL 16 efêmero, com storage descartável, bind somente loopback em
porta dinâmica livre e dois bancos distintos: ERP e publisher. Não usar volume
nomeado persistente nem portas 5432/5434.

Preparar o ERP local:

- aplicar as migrations reais do backend no banco isolado;
- iniciar com `ROOT_BOOTSTRAP_ENABLED=true` e credenciais descartáveis somente
  no ambiente do processo;
- exigir log sanitizado `Bootstrap root criou usuario SYSTEM` ou prova
  equivalente no banco sem mostrar email/hash/senha;
- habilitar a ponte publisher com issuer
  `http://127.0.0.1:8080/api/release-control/identity`;
- usar chave RSA efêmera fora do repositório;
- bind somente `127.0.0.1:8080`.

Preparar o publisher:

- profile `development`, mode `publisher`;
- banco publisher isolado e migration Alembic em `head`;
- issuer/audience/JWKS exatos do ERP local;
- CORS somente para o frontend loopback;
- GitHub App publisher validada na seção 7;
- bind somente `127.0.0.1:8090`;
- `/health/live` verde e `/health/ready` verde depois de pelo menos um sync
  válido de candidatos e releases;
- candidato terminal visível como `READY/PASSED/VALID` e releases vazias.

Preparar o frontend em diretório temporário, preservando `frontend/.env`:

```text
VITE_RELEASE_CONTROL_MODE=publisher
VITE_RELEASE_PUBLISHER_URL=http://127.0.0.1:8090
VITE_BASE_API_URL=http://127.0.0.1:8080
```

Bind somente `127.0.0.1:8084`. Exigir que a UI carregue, autentique o usuário
descartável e mostre:

```text
Painel de Controle -> Desenvolvimento -> Gerenciamento de Releases
```

Logs dos quatro processos devem ir para o diretório temporário, com inspeção
sanitizada. Nunca incorporar env completo, header ou body autenticado ao
relatório.

Falha antes do POST encerra a execução depois da limpeza dirigida, preservando
a variável do repositório apenas se já configurada corretamente.

## 9. Uma intenção, replay idempotente e único dispatch

Usar navegador automatizado contra a UI real. Preencher exatamente os quatro
campos da seção 2 e confirmar que a estimativa mostra `v0.1.0`.

Antes da primeira confirmação, instalar no navegador uma interceptação local
limitada ao primeiro:

```text
POST /api/release-publisher/v1/releases
```

A interceptação deve deixar o request alcançar o runtime e descartar somente a
resposta para a página. Isso simula resultado incerto no cliente depois da
persistência/dispatch, preservando no `sessionStorage` a mesma tentativa sem
`operationId`. Não alterar request, body ou header e não registrar bearer ou
idempotency key.

Depois:

1. confirmar a publicação uma única vez na UI;
2. exigir que a UI não faça retry automático;
3. constatar uma tentativa pendente sem imprimir seu conteúdo sensível;
4. usar a ação **Retomar envio** uma única vez;
5. exigir que o segundo POST reutilize request e chave idênticos;
6. exigir o mesmo `operationId` persistido pelo runtime;
7. confirmar exatamente um run `publish-release-<operationId>`, attempt 1;
8. confirmar nenhuma segunda operação, dispatch, tag ou release.

Os dois POSTs representam uma única intenção e uma única chave idempotente. Se
a primeira chamada não alcançar o runtime, não usar **Retomar envio**: parar
antes de qualquer nova tentativa porque não há prova de replay. Se a primeira
chamada alcançar o runtime mas seu estado ficar incerto, somente o replay
nominal acima está autorizado; nunca criar nova chave.

## 10. Restart controlado e reconciliação

Assim que o replay devolver o `operationId` e o run único estiver correlacionado
em estado não terminal, parar somente o publisher local. Registrar estado
sanitizado e contagens remotas, sem parar ERP, PostgreSQL ou frontend.

Reiniciar o publisher uma única vez com o mesmo banco e a mesma configuração.
Exigir:

- `/health/live` e `/health/ready` voltam a verde;
- a operação reaparece com o mesmo `operationId`;
- workflow run ID/attempt permanecem os mesmos;
- não há novo POST de dispatch nem segundo run;
- a reconciliação avança monotonicamente até `PUBLISHED` ou para fail-closed.

Se o run remoto concluir antes da parada do processo, ainda executar o restart
uma vez e provar a recuperação da mesma operação terminal sem redispatch,
registrando que a janela não terminal não pôde ser capturada. Não provocar
falha, cancelar run ou atrasar workflow artificialmente.

## 11. Evidência terminal remota

Aguardar sem intervenção o único run. Exigir:

- workflow `Publish Release`;
- evento `workflow_dispatch`, attempt 1 e `headSha=50f423a...`;
- display title `publish-release-<operationId>`;
- jobs `trust`, `prepare`, `publish` e `outcome` em `success`;
- mode `publish`, nunca `already_published` na primeira publicação;
- tag lightweight `v0.1.0` apontando para
  `50f423a979d7723d0e15d56b1d72625ea2b8ebea`;
- uma GitHub Release `v0.1.0`, `draft=false`, `prerelease=false`;
- exatamente `release.json`, `release.json.sha256` e `metadata.json`;
- artifact final `release-publication-outcome` único e não expirado;
- runtime local em `PUBLISHED`, release `v0.1.0` e mesmo workflow run.

Baixar assets e outcome somente em subdiretório temporário. Sem imprimir JSON
bruto, validar canonicalidade, schemas, sidecars e bindings entre:

```text
operationId
candidateId
sourceCommit
workflow run/attempt
tag
GitHub Release ID/URL
asset IDs/digests
manifestSha256
```

O BOM deve conter exatamente os seis componentes canônicos e copiar os mesmos
digests e immutable refs do candidato terminal. Descrição e changelog devem ser
byte a byte os textos aprovados. `deployable=true` e a cadeia SemVer deve conter
somente `v0.1.0`.

Após o terminal, provar novamente:

- uma operação local e um run remoto para a intenção;
- uma tag e uma release;
- replay não criou segunda evidência;
- restart não criou redispatch;
- zero run deploy/rollback e nenhum efeito em VPS/produção.

Falha de workflow, asset, binding, BOM, replay ou reconciliação exige parada sem
rerun, nova chave, nova release ou exclusão compensatória.

## 12. Limpeza dirigida

Depois do terminal ou da primeira parada:

1. parar frontend, publisher e ERP pelos PIDs/containers nominais;
2. parar/remover somente PostgreSQL, rede, storage e container criados pela
   prova;
3. remover somente o diretório `mktemp -d` nominal e seus segredos/logs;
4. confirmar portas efêmeras livres;
5. confirmar `baronesa-postgres`, portas 5432/5434 e volumes preexistentes
   inalterados;
6. preservar `RELEASE_PUBLISHER_ACTOR_IDS`, a release, tag, run, artifacts e
   logs remotos;
7. confirmar que o repositório só difere pelo relatório contínuo.

Não apagar cache/resíduo preexistente sem snapshot causal. Se uma limpeza
nominal falhar, registrar o alvo e parar; não ampliar a remoção.

## 13. Relatório contínuo

Acrescentar ao arquivo existente uma seção `Execução authorization-01` com:

- hashes dos três documentos de autoridade;
- snapshot Git/GitHub inicial e final;
- descoberta sanitizada da App, instalação, ator e permissões;
- configuração da variável apenas como `CONFIGURED` ou falha;
- topologia local, portas e PIDs/IDs sem segredos;
- migrations, health, readiness e usuário `SYSTEM` sanitizados;
- prova de navegação e campos/estimativa da UI;
- sequência dos dois POSTs classificada como
  `FIRST_RESPONSE_DROPPED` e `SAME_KEY_REPLAY`, sem mostrar chave;
- `operationId`, run, attempt, jobs e transições;
- restart e ausência de redispatch;
- tag, release, assets, outcome, BOM e bindings;
- primeira causa/ponto de parada quando aplicável;
- limpeza, resíduos e negativos preservados;
- comandos exatos e exits, redigindo qualquer argumento sensível.

Não registrar senha, token, JWT, PEM, idempotency key, env completo, header ou
body autenticado. Comando cujo argv contenha segredo deve aparecer no relatório
somente como forma sanitizada e com a explicação do redaction.

O relatório permanece modificado, local, não staged e não commitado. O executor
não aceita a S30b e não cria a próxima slice.

Encerrar com uma destas linhas exatas:

```text
IN_PROGRESS — authorization-01 concluída; aguardando aceite terminal da S30b pelo orquestrador
```

ou:

```text
BLOCKED — authorization-01 interrompida fail-closed na primeira causa
```

## 14. Critérios do orquestrador

Aceitar a S30b somente se toda a seção 11 for comprovada, a limpeza for
dirigida e nenhum segredo ou efeito de produção existir. Se aceita, o
orquestrador deverá no mesmo ciclo:

1. registrar o aceite no relatório e tracker;
2. preservar a release `v0.1.0` e sua evidência;
3. criar a próxima slice do roadmap de preparação segura da VPS;
4. entregar o prompt formal do próximo executor;
5. manter toda mutação de VPS/deploy/rollback condicionada a nova autorização
   humana específica.
