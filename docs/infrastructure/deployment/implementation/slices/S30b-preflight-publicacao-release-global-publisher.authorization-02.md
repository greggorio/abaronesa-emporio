# S30b — authorization-02: provisionar a App publisher e retomar v0.1.0

> **Estado:** `AUTHORIZED`
> **Tipo:** correção da lacuna operacional da authorization-01 e retomada
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Contrato principal:** `S30b-preflight-publicacao-release-global-publisher.task.md`
> **Autoridade anterior:** `S30b-preflight-publicacao-release-global-publisher.authorization-01.md`
> **Relatório contínuo:** `S30b-preflight-publicacao-release-global-publisher.report.md`

## 1. Correção autorizada

O usuário determinou a correção adequada para que o executor prossiga depois
da parada fail-closed da authorization-01. Esta autorização remove somente a
proibição que impedia criar a identidade publisher inexistente e permite:

1. registrar uma GitHub App privada publisher pelo Manifest flow oficial;
2. instalar a App exclusivamente em `greggorio/abaronesa-emporio`;
3. armazenar a única chave privada retornada fora do repositório, com modo 600;
4. registrar App ID e installation ID em configuração local protegida;
5. configurar `RELEASE_PUBLISHER_ACTOR_IDS` com o bot confirmado;
6. retomar integralmente a authorization-01 a partir do gate da GitHub App;
7. publicar e validar a release `v0.1.0` nos termos já aprovados.

Não solicitar ao usuário nomes, IDs, paths, permissões ou metadados. Todos
estão fechados abaixo. A única pausa permitida é a interação inevitável com a
página oficial do GitHub para login/2FA, confirmação da criação e instalação.

Esta autorização não permite deploy, rollback, VPS ou produção.

## 2. Base obrigatória

O checkpoint anterior é:

```text
branch              main
HEAD pai            6e899af94432b97f3648806d0ff13023580d875e
origin/main         50f423a979d7723d0e15d56b1d72625ea2b8ebea
remoto main         50f423a979d7723d0e15d56b1d72625ea2b8ebea
ahead no pai        4 commits documentais
stage no pai        vazio
worktree no pai     limpo
tags                zero
GitHub Releases      zero
runs publish-release zero
```

O prompt informará o SHA do commit local que contém esta autorização e seu
SHA-256. Exigir antes da primeira mutação:

- `HEAD` no SHA do prompt;
- exatamente cinco commits documentais lineares em `origin/main..HEAD`;
- stage e worktree vazios;
- remoto ainda em `50f423a...`;
- hashes da seção 3 íntegros;
- nenhum run ativo, tag, release ou `publish-release.yml`;
- `RELEASE_PUBLISHER_ACTOR_IDS` ainda `MISSING`;
- candidato terminal e artifacts ainda disponíveis.

Divergência exige parada sem criar App. Não executar pull, push, merge, rebase,
amend ou alteração de código.

## 3. Integridade documental

Exigir:

```text
task
sha256 91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11

authorization-01
sha256 33543ec2cc399d3a82c9d21cd4ba815465e0b40927d91043a6bc55cb7a897961

relatório com a parada aceita pelo orquestrador
sha256 066f4180bd7470ff8cb73ca8174c44a28f82fbcf43f9c805cde8c2c8773a29cf
```

Esta authorization-02 terá o hash informado no prompt. Task,
authorization-01, authorization-02 e tracker são imutáveis durante a execução.
Somente o relatório contínuo pode ser acrescentado depois de sucesso ou parada.

## 4. Identidade e manifesto fechados

Criar a App na conta pessoal `greggorio` com estes valores exatos:

```text
name
Emporio Publisher 1315264421

expected slug
emporio-publisher-1315264421

owner login
greggorio

owner id
35626201

repository
greggorio/abaronesa-emporio

repository id
1315264421

homepage URL
https://github.com/greggorio/abaronesa-emporio

visibility
private / only on this account

webhook
inactive

events
none

request OAuth on install
false

repository permissions
actions: write
contents: read
metadata: read (implícita)
```

O Manifest JSON deve conter somente:

```json
{
  "name": "Emporio Publisher 1315264421",
  "url": "https://github.com/greggorio/abaronesa-emporio",
  "description": "Publisher local do controle de releases do Emporio",
  "redirect_url": "http://127.0.0.1:<PORT>/callback",
  "hook_attributes": {
    "url": "https://github.com/greggorio/abaronesa-emporio",
    "active": false
  },
  "public": false,
  "default_permissions": {
    "actions": "write",
    "contents": "read"
  },
  "default_events": [],
  "request_oauth_on_install": false,
  "setup_on_update": false
}
```

Substituir apenas `<PORT>` pela porta loopback efêmera. Não adicionar
Administration, Variables, Workflows, Secrets, Deployments, Packages ou outra
permissão. A App não recebe webhook, OAuth de usuário nem acesso a outro
repositório.

Se o nome exato não estiver disponível, parar antes de confirmar a criação e
registrar o conflito; não improvisar outro nome porque o slug esperado participa
dos vínculos posteriores.

## 5. Manifest flow oficial e interação humana mínima

Criar `mktemp -d`, state CSRF aleatório e servidor HTTP efêmero em
`127.0.0.1:<PORT>`. O servidor deve:

1. servir uma página local com formulário `POST` para
   `https://github.com/settings/apps/new?state=<STATE>`;
2. enviar o Manifest como string JSON no campo `manifest`;
3. receber `/callback?code=<CODE>&state=<STATE>`;
4. rejeitar método, path, state ausente/divergente, duplicidade ou callback
   depois do primeiro uso;
5. nunca imprimir state ou code;
6. trocar o code dentro de uma hora em
   `POST /app-manifests/<CODE>/conversions`;
7. exigir HTTP 201 e resposta fechada com App ID, slug, owner e PEM válidos;
8. persistir somente o PEM necessário e descartar client secret e webhook
   secret sem registrá-los.

Apresentar ao usuário somente a URL loopback inicial e a instrução:

```text
Confirme no GitHub a criação da App com o nome e as duas permissões exibidas.
Não altere os campos. Conclua login/2FA se solicitado.
```

Essa interação não reabre decisões e não requer resposta textual. O executor
continua automaticamente quando o callback válido chegar.

Usar a autenticação atual do `gh` somente para trocar o code do Manifest e
configurar a variável do repositório. Ela nunca é credencial do publisher.
Falha 401/403/404/422 na conversão exige parada sem repetir o flow ou registrar
uma segunda App.

## 6. Armazenamento seguro

Criar fora do repositório:

```text
/home/gregorio/.config/emporio/release-control/
```

Exigir diretórios modo 700. Gravar atomicamente:

```text
/home/gregorio/.config/emporio/release-control/publisher-github-app.pem
/home/gregorio/.config/emporio/release-control/publisher-github-app.env
```

Ambos devem ser arquivos regulares, não symlink, owner atual e modo 600. O PEM
deve conter a chave retornada pelo Manifest flow, ter no máximo 32 KiB e passar
em validação RSA sem imprimir parâmetros privados.

O `.env` deve conter exatamente nomes e valores operacionais não secretos:

```text
RELEASE_CONTROL_GITHUB_APP_ID=<APP_ID>
RELEASE_CONTROL_GITHUB_INSTALLATION_ID=<INSTALLATION_ID>
RELEASE_CONTROL_GITHUB_PRIVATE_KEY_PATH=/home/gregorio/.config/emporio/release-control/publisher-github-app.pem
```

Não persistir client secret, webhook secret, JWT, installation token, code ou
state. Nunca imprimir o PEM ou incluir segredo no argv/histórico.

Se já surgir qualquer um desses paths durante a execução, parar antes de
sobrescrever. Esta autorização não permite substituição ou rotação silenciosa.

## 7. Instalação exclusiva

Depois de validar e armazenar a App, apresentar/abrir somente:

```text
https://github.com/apps/emporio-publisher-1315264421/installations/new/permissions?suggested_target_id=35626201&repository_ids[]=1315264421
```

Instrução única ao usuário:

```text
Selecione Only select repositories, mantenha somente abaronesa-emporio,
revise Actions write e Contents read e clique Install.
```

O executor deve então usar JWT da App em memória e aguardar por no máximo cinco
minutos uma única instalação compatível. Exigir:

- account `greggorio`, ID `35626201`;
- `repository_selection=selected`;
- permissions exatamente `actions=write`, `contents=read`, `metadata=read`;
- lista de repositories com exatamente ID `1315264421`;
- nenhum outro repository ou instalação para esta App.

Registrar somente App ID, slug, installation ID, owner e matriz de permissões.
Não registrar JWT ou installation token. Ausência, duplicidade ou escopo maior
exige parada sem editar/excluir a App ou instalação.

## 8. Prova operacional mínima da App

Com a chave armazenada, mintar JWT e installation token somente em memória.
Antes de configurar a allowlist, provar por requests sanitizados:

- `GET /app` retorna o mesmo App ID, slug e owner;
- `GET /installation/repositories` retorna somente o repositório canônico;
- leitura do workflow `publish-release.yml` em `main` funciona;
- listagem de runs/artifacts e releases funciona;
- metadados do candidato terminal continuam acessíveis;
- token possui `actions=write` e `contents=read` na resposta de criação, sem
  transcrever o token.

Não testar `actions:write` por dispatch de ensaio. O primeiro e único dispatch
continua sendo o pedido real pela UI na retomada da authorization-01.

Derivar o ator público `emporio-publisher-1315264421[bot]`, exigir `type=Bot` e
ID decimal positivo. Configurar uma única vez:

```text
RELEASE_PUBLISHER_ACTOR_IDS=<BOT_ID>
```

Revalidar sem imprimir o valor: `PRESENT`, CSV válido, exatamente um ID e
identidade correspondente ao bot. Não conceder à App permissão de Variables; a
configuração usa a autenticação administrativa atual do `gh`, já autorizada.

## 9. Retomada integral da authorization-01

Depois das seções 4 a 8 verdes, retomar a authorization-01 a partir da seção 8
`Ambiente local isolado`, usando o `.env` seguro recém-criado como fonte dos
três nomes GitHub. Todas as demais decisões, metadados, proibições, prova de UI,
replay idempotente, restart, validação de `v0.1.0`, limpeza e relatório da
authorization-01 permanecem vigentes.

Não repetir a descoberta que já bloqueou, não criar outra App e não pedir nova
confirmação. O resultado terminal continua exigindo:

```text
uma App
uma instalação
um repository
uma allowlist com um bot
uma intenção UI
uma idempotency key
um operationId
um dispatch/run
uma tag v0.1.0
uma GitHub Release v0.1.0
nenhum deploy/rollback/produção
```

Se qualquer gate posterior falhar, preservar App, instalação, PEM, configuração
e variável como infraestrutura operacional; limpar somente os recursos locais
efêmeros da prova. Não excluir evidência ou credencial recém-provisionada.

## 10. Fronteira e negativos

Além das mutações expressamente autorizadas, permanecem proibidos:

- App pública, acesso a todos os repositórios ou outra conta;
- webhook ativo, evento inscrito ou OAuth de usuário;
- permissão além de Actions write, Contents read e Metadata read implícita;
- segunda App, segunda chave, segunda instalação ou rotação;
- token pessoal como credencial do runtime;
- segredo no repositório, relatório, stdout, argv ou histórico;
- alteração de código, workflow, task, tracker ou documentos de autoridade;
- commit, push, pull, merge, rebase ou amend pelo executor;
- dispatch manual, retry, rerun, nova idempotency key ou segunda release;
- deploy, rollback, SSH, VPS, GHCR ou produção.

Não abrir `ops/env/.env.production` nem tocar em banco/container preexistente.

## 11. Relatório contínuo

Acrescentar uma seção `Retomada authorization-02` ao relatório com:

- hashes dos quatro documentos de autoridade;
- snapshot inicial/final;
- forma literal do Manifest, com PORT/STATE/CODE redigidos;
- interação GitHub classificada como `CREATION_CONFIRMED` e
  `INSTALLATION_CONFIRMED`;
- App ID, slug, owner, installation ID, repository selection e permissões;
- paths e modos, nunca conteúdo dos arquivos;
- descarte de client secret/webhook secret/code/state;
- ator bot e allowlist somente como vínculo validado;
- prova read-only da installation token;
- toda a evidência da retomada da authorization-01;
- primeira causa e recursos externos preservados em caso de parada;
- limpeza e negativos.

O relatório permanece local, modificado, não staged e não commitado. O executor
não aceita a S30b e não cria próxima slice.

Encerrar com:

```text
IN_PROGRESS — authorization-02 concluída; aguardando aceite terminal da S30b pelo orquestrador
```

ou:

```text
BLOCKED — authorization-02 interrompida fail-closed na primeira causa
```

## 12. Critério de aceite

Somente o orquestrador aceita S30b. Se a retomada atingir o terminal previsto,
o orquestrador deverá registrar o aceite e criar no mesmo ciclo a próxima slice
de preparação segura da VPS, ainda sem autorizar qualquer mutação de produção.
