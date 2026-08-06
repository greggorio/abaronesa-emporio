# UI publisher de desenvolvimento

> **Estado:** interface local implementada; nenhuma publicação remota,
> credencial operacional, UI deployer ou preparação de produção foi realizada.

## Finalidade e limites

A interface permite que um usuário root do ERP, autenticado também como
`ROLE_SYSTEM`, solicite localmente a publicação de uma release global a partir
de um candidato já validado. Ela não executa Git, build, push, tag ou deploy,
não calcula BOM e não permite escolher componentes, imagens ou dependências.

A interface existe somente no build de desenvolvimento com o modo
`publisher`. Em produção ela é sempre desabilitada. Deployer, rollback e
atualização de produção permanecem futuros.

## Pré-requisitos locais

- backend ERP em `http://127.0.0.1:8080`, com a ponte S16 habilitada;
- usuário ERP root com `ROLE_SYSTEM`;
- publisher em `http://127.0.0.1:8090`, profile `development`;
- PostgreSQL local e chave RSA do emissor fora do repositório;
- frontend iniciado com `npm run dev`;
- variáveis Vite:

  ```text
  VITE_RELEASE_CONTROL_MODE=publisher
  VITE_RELEASE_PUBLISHER_URL=http://127.0.0.1:8090
  ```

O acesso fica em:

```text
Painel de Controle -> Desenvolvimento -> Gerenciamento de Releases
```

O card root é apenas ocultação visual. A autoridade real continua sendo o
exchange protegido por `ROLE_SYSTEM`.

## Identidade e segurança dos tokens

O cliente lê o token ERP por callback somente para chamar, sem body ou query:

```text
POST /api/release-control/identity/token
```

O token publisher RS256 retornado dura 300 segundos e permanece somente em
memória, na closure da instância do cliente. Ele nunca é exposto ao componente nem salvo
em `sessionStorage`, `localStorage`, cookie, IndexedDB, URL ou log. Um `401`
invalida o token, faz um único novo exchange e repete a chamada uma vez; um
segundo `401` termina.

Antes de qualquer lista ou publicação, o cliente valida fail-closed as
capabilities `release:read` e `release:publish`.

## Candidatos, releases e formulário

A UI aceita somente candidatos simultaneamente `READY`, com CI `PASSED` e
manifesto `VALID`. Releases publicadas são carregadas integralmente, em até dez
páginas, deduplicadas e ordenadas por SemVer numérica.

Elegível é o candidato ainda não absorvido por nenhuma release. Absorção segue a
linhagem: publicar um candidato absorve também todos os seus antecessores, porque
o manifesto é cumulativo e o mais recente já contém os anteriores. Um candidato
absorvido deixa de ser candidato e não reaparece na tela.

A tela comunica, não pergunta. Ela lista os commits do `main` que ainda não estão
na release corrente e que entrarão na próxima — data, sha curto e assunto do
commit. Não há seleção: escolher candidato obrigaria quem publica a raciocinar
sobre dependências entre eles, e não há o que escolher, já que a publicação usa
sempre o candidato mais recente. Absorção seletiva de um único candidato entre
vários já commitados no `main` — o caso do hotfix sobre uma release anterior —
está no roadmap, não neste MVP.

Campos do formulário:

- tipo de atualização `MAJOR`, `MINOR` ou `PATCH`;
- descrição de 1 a 500 caracteres após trim;
- changelog de 1 a 10000 caracteres após trim.

`PATCH` é o default. A tela mostra a maior release publicada e uma próxima
versão estimada. A estimativa é apenas orientação: o manifesto e o workflow
continuam sendo a autoridade da versão efetiva.

Antes do envio, a confirmação mostra o candidato efetivo, o incremento e a
estimativa.
Cancelar não cria tentativa nem chave idempotente.

## Idempotência e recuperação

Após confirmar e antes do POST, a UI grava somente:

```text
sessionStorage["emporio.releasePublisher.pending.v1"]
```

O registro contém schema 1, chave `publisher-ui-<UUID v4>`, request exato,
`operationId` opcional e timestamp UTC. O limite é 16 KiB. Registro inválido é
removido, e storage indisponível bloqueia a publicação.

Erro de rede, resposta inválida ou conflito não provoca reenvio automático.
A ação **Retomar envio** reutiliza exatamente request e chave persistidos.
**Descartar tentativa** exige confirmação e remove apenas o registro local;
ela não cancela operação remota.

Após reload:

- com `operationId`, a UI consulta o estado sem publicar novamente;
- sem `operationId`, a UI aguarda a ação explícita **Retomar envio**.

## Estados e polling

Os estados reconciliados são:

```text
REQUESTED -> VALIDATING -> PUBLISHING -> PUBLISHED
                                      \-> FAILED
```

O polling ocorre a cada três segundos, sem sobreposição, por no máximo dez
minutos contínuos. Rede, timeout ou saída da tela preservam a tentativa.
**Atualizar estado** retoma uma consulta manual depois da pausa.

`PUBLISHED` e `FAILED` param imediatamente o polling, removem a tentativa
pendente e atualizam candidatos e releases. O resultado terminal permanece
visível. Em falha, a UI não mostra o `errorCode` operacional.

## Mensagens de erro

A tela traduz somente os códigos públicos do contrato para mensagens locais.
Quando presente e válido, `traceId` aparece como código de suporte. Nunca são
mostrados `detail` remoto, body desconhecido, erro Axios bruto, stack trace,
token ou classificação interna da operação.

Mensagens específicas distinguem sessão ERP expirada, acesso negado,
publisher indisponível, conflito idempotente, conflito de versão, rate limit
e erro interno. Shape ou código desconhecido resulta em mensagem genérica.

## Roteiro de uso

1. Suba PostgreSQL, ERP, publisher e frontend conforme o onboarding.
2. Entre no ERP com o usuário root `ROLE_SYSTEM`.
3. Abra **Painel de Controle**, aba **Desenvolvimento**.
4. Selecione **Gerenciamento de Releases**.
5. Aguarde capabilities, candidatos e histórico serem validados.
6. Confira a lista de commits que entram, escolha o incremento e preencha
   descrição e changelog.
7. Confira a estimativa e confirme.
8. Acompanhe o estado reconciliado até o terminal.

## Troubleshooting

- Card ausente: confirme build dev, modo `publisher` e usuário root.
- Sessão expirada: autentique-se novamente no ERP.
- Acesso negado: confirme `ROLE_SYSTEM`; ser root visualmente não substitui a
  autorização.
- Publisher indisponível: confirme profile `development`, bind loopback e
  porta 8090.
- Nada a publicar: a release corrente já contém tudo que está no `main`. Isso
  não é erro; erros têm banner próprio.
- Envio incerto: use **Retomar envio**, preservando a mesma tentativa.
- Polling pausado: use **Atualizar estado**; não descarte sem antes considerar
  que a operação remota pode existir.

Nenhuma execução remota foi feita para produzir este guia. Credenciais reais,
rotação operacional, deployer e produção continuam fora do escopo.
