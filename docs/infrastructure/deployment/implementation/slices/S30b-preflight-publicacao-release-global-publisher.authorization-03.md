# S30b — authorization-03: reconciliar a App publisher e retomar v0.1.0

> **Estado:** `AUTHORIZED`
> **Tipo:** correção do gate de identidade da authorization-02 e retomada
> **Executor previsto:** CLI com browser local
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Contrato principal:** `S30b-preflight-publicacao-release-global-publisher.task.md`
> **Autoridades cumulativas:** `authorization-01`, `authorization-02` e esta `authorization-03`
> **Relatório contínuo:** `S30b-preflight-publicacao-release-global-publisher.report.md`

## 1. Decisão e correção

A parada `manifest_identity_mismatch` foi segura e aceita pelo orquestrador,
mas não é uma pendência humana. A authorization-02 impôs como entrada o slug
`emporio-publisher-1315264421`, embora o slug seja uma saída derivada e
confirmada pelo GitHub durante o registro. O executor também descartou a
resposta antes de registrar a tupla sanitizada que divergiu.

Esta autorização corrige o gate:

- nome, owner, homepage, visibilidade e permissões continuam fechados;
- o slug real devolvido/exibido pelo GitHub passa a ser o slug canônico;
- uma App eventualmente criada pela tentativa anterior deve ser reutilizada;
- uma nova chave pode ser gerada para a App reconciliada porque a chave do
  Manifest foi perdida antes da persistência;
- a chave pública antiga sem PEM deve ser removida somente depois de a nova
  chave ter sido gerada, armazenada e validada;
- um novo Manifest flow só pode ocorrer se a inspeção administrativa confirmar
  que nenhuma App compatível foi criada na tentativa anterior;
- depois da reconciliação, retomar integralmente a publicação já autorizada.

Não pedir ao usuário valores, nomes, IDs, paths, permissões ou nova aprovação.
A única interação humana possível continua sendo login/2FA e cliques na
interface oficial do GitHub solicitados objetivamente pelo executor.

Esta autorização não permite deploy, rollback, VPS ou produção.

## 2. Base e integridade obrigatórias

Checkpoint pai:

```text
branch              main
HEAD pai            991bb35acc16d69cc2033f731ba3400fd96d266e
origin/main         50f423a979d7723d0e15d56b1d72625ea2b8ebea
remoto main         50f423a979d7723d0e15d56b1d72625ea2b8ebea
ahead no pai        5 commits documentais
stage no pai        vazio
worktree no pai     somente relatório modificado pela authorization-02
tags                zero
GitHub Releases      zero
runs publish-release zero
```

O prompt fornecerá o HEAD do commit que incorpora o relatório revisado, esta
autorização e o tracker. Antes da primeira mutação, exigir:

- HEAD exatamente no SHA do prompt;
- exatamente seis commits documentais lineares em `origin/main..HEAD`;
- stage e worktree vazios;
- remoto ainda em `50f423a...`;
- nenhum run ativo, tag, release ou publicação concorrente;
- `RELEASE_PUBLISHER_ACTOR_IDS` ainda ausente;
- hashes documentais íntegros.

Hashes de entrada:

```text
task
91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11

authorization-01
33543ec2cc399d3a82c9d21cd4ba815465e0b40927d91043a6bc55cb7a897961

authorization-02
96245fc58b5fdd32b7fac454614ca2d9caa694b4dad870c2c2625691cd598044

relatório com a parada da authorization-02 aceita
bb819f4ffdcc0ed36b33e85621c43d16c5514b3dbdfafc111dc5ee8af4b1b237
```

O prompt fornecerá o SHA-256 desta authorization-03. Task, authorizations e
tracker permanecem imutáveis. Somente o relatório contínuo pode ser
acrescentado durante a execução.

## 3. Identidade funcional fechada

A App válida deve possuir:

```text
name                  Emporio Publisher 1315264421
owner login           greggorio
owner id              35626201
owner type            User
homepage URL          https://github.com/greggorio/abaronesa-emporio
description           Publisher local do controle de releases do Emporio
visibility            private / only on this account
webhook                inactive
events                 none
OAuth on install       false
actions                write
contents               read
metadata               read (implícita)
```

`slug` não deve ser comparado com uma previsão local antes da reconciliação.
Exigir apenas que seja string não vazia, que a URL administrativa pertença à
App acima e que `GET /apps/<ACTUAL_SLUG>` com JWT da própria App devolva o mesmo
App ID, nome e owner. Depois disso, `<ACTUAL_SLUG>` é o vínculo canônico para
URL de instalação, bot e relatório.

## 4. Reconciliação administrativa antes de criar qualquer objeto

Abrir em sessão autenticada do usuário:

```text
https://github.com/settings/apps
```

Inspecionar as GitHub Apps pertencentes à conta, sem abrir Authorized Apps de
terceiros. Procurar uma candidata produzida pela authorization-02 usando em
conjunto nome, homepage, descrição, owner e horário da tentativa.

Registrar no relatório, sem secrets:

- quantidade de candidatas;
- App ID, nome, slug real e owner;
- homepage, visibilidade, webhook e OAuth;
- matriz de permissões e eventos;
- quantidade de chaves públicas;
- quantidade e escopo de instalações.

Classificar exatamente um dos casos abaixo.

### Caso A — uma única App compatível existe

Reutilizá-la. Não executar outro Manifest flow.

Se campos não secretos divergirem, corrigir pela interface administrativa
somente para os valores da seção 3. Se o nome exato não puder ser salvo por
conflito global, parar e registrar o conflito sem renomear por improviso.

Se não houver PEM operacional local, gerar exatamente uma nova chave privada
pela seção `Private keys`. O download deve ser capturado diretamente no path
temporário protegido, sem exibir conteúdo. Validar e armazenar conforme a
seção 6 da authorization-02.

Depois de provar por fingerprint que a nova chave local corresponde à nova
chave pública exibida pelo GitHub, remover somente as chaves públicas antigas
da mesma App para as quais nenhum PEM foi preservado. Nunca remover a nova
chave validada. O estado terminal deve conter exatamente uma chave pública e
um PEM correspondente.

Se a App já estiver instalada, não reinstalar. Validar e, se necessário,
restringir a instalação para somente `greggorio/abaronesa-emporio` e as
permissões da seção 3.

### Caso B — nenhuma App compatível existe

Executar uma única nova tentativa do Manifest flow da authorization-02, sem o
gate de slug previsto. Preservar imediatamente o PEM retornado em arquivo
temporário regular modo 600 antes dos demais gates; nunca imprimir a resposta
integral.

Validar a tupla sanitizada `id`, `name`, `slug`, `owner.login`, `owner.id`,
`external_url`, `permissions` e `events`. O nome e os demais campos devem
corresponder às seções 3 e 4 da authorization-02; qualquer slug real não vazio
é aceito e torna-se canônico.

Persistir o PEM e a configuração nos paths seguros definidos na seção 6 da
authorization-02. Prosseguir para a instalação usando o slug real.

### Caso C — mais de uma candidata ou objeto incompatível relacionado

Parar fail-closed com o inventário sanitizado. Não excluir, renomear, gerar
chave, criar outra App ou escolher arbitrariamente.

## 5. Instalação e prova da identidade reconciliada

Para App ainda não instalada, usar:

```text
https://github.com/apps/<ACTUAL_SLUG>/installations/new/permissions?suggested_target_id=35626201&repository_ids[]=1315264421
```

Apresentar somente:

```text
Selecione Only select repositories, mantenha somente abaronesa-emporio,
revise Actions write e Contents read e clique Install.
```

Com o PEM reconciliado, mintar JWT e installation token somente em memória e
exigir:

- App ID, nome, slug e owner consistentes entre settings, `GET /app` e JWT;
- exatamente uma instalação na conta `greggorio`;
- `repository_selection=selected`;
- exatamente o repository ID `1315264421`;
- `actions=write`, `contents=read` e `metadata=read`;
- leitura de workflow, runs, artifacts, release e candidato;
- exatamente uma chave pública cujo fingerprint corresponde ao PEM local.

Derivar `<ACTUAL_SLUG>[bot]`, exigir `type=Bot` e ID decimal positivo. Somente
então configurar uma vez:

```text
RELEASE_PUBLISHER_ACTOR_IDS=<BOT_ID>
```

A autenticação administrativa atual do `gh` configura a variável; ela não é a
identidade do publisher.

## 6. Retomada da publicação

Com App, chave, instalação, bot e allowlist verdes, retomar a
authorization-01 na seção `Ambiente local isolado`. Aplicam-se integralmente:

- serviços e migrations locais;
- bootstrap e autenticação previstos;
- publicação exclusivamente pela UI/runtime;
- uma intenção, uma idempotency key, um operationId e um dispatch/run;
- criação e validação de `v0.1.0` e sua GitHub Release;
- replay idempotente com a mesma chave;
- restart local do publisher;
- limpeza dirigida e negativos.

Não pedir nova confirmação e não repetir descoberta já fechada.

## 7. Fronteira e paradas

Além do que esta autorização permite expressamente, continuam proibidos:

- segunda App quando o Caso A for verdadeiro;
- segundo Manifest no Caso B;
- uso de slug previsto em lugar do slug confirmado;
- PAT como identidade do publisher;
- secret, PEM, JWT ou token em stdout, relatório, argv ou repositório;
- acesso a outro repositório ou conta;
- permissões adicionais, webhook ativo ou OAuth de usuário;
- alteração de código, workflows, task, authorizations ou tracker;
- commit, push, pull, merge, rebase ou amend pelo executor;
- retry, rerun ou segundo dispatch fora do replay idempotente contratado;
- deploy, rollback, SSH, VPS, GHCR ou produção.

Falha deve preservar a única App, instalação, chave e configuração válidas já
obtidas. Limpar somente temporários sem valor operacional. Não repetir uma
ação externa após resposta ambígua.

## 8. Relatório contínuo

Acrescentar `Retomada authorization-03` com:

- hashes dos cinco documentos;
- snapshot Git/GitHub inicial e final;
- caso A, B ou C e inventário sanitizado;
- tupla esperada e real, inclusive o campo que causou o mismatch anterior se
  ele puder ser identificado;
- App ID, nome, slug canônico, owner e URL;
- permissões, eventos, instalação e repository selection;
- quantidades de chaves antes/depois e prova de fingerprint, nunca o PEM;
- paths, owners e modos dos arquivos;
- bot/allowlist como vínculos validados, sem expor valores sensíveis;
- comandos, exits e toda a evidência da authorization-01 retomada;
- primeira causa, recursos preservados, limpeza e negativos.

O relatório fica modificado, não staged e não commitado. O executor não aceita
S30b e não cria a próxima slice.

Encerrar com:

```text
IN_PROGRESS — authorization-03 concluída; aguardando aceite terminal da S30b pelo orquestrador
```

ou:

```text
BLOCKED — authorization-03 interrompida fail-closed na primeira causa
```

## 9. Critério de aceite

Somente o orquestrador aceita S30b. Se `v0.1.0` e todas as provas terminais
forem confirmadas, o orquestrador deverá aceitar S30b e criar no mesmo ciclo a
próxima slice de preparação segura da VPS, ainda sem mutação de produção.
