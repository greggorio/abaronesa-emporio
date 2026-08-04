# S45 — Prontidão de produção e fechamento do Gate C

> **Data:** 04/08/2026
> **Predecessora:** S44 aceita
> **Tipo:** wiring operacional, capacidade, TLS e recuperação pré-deploy
> **Deploy/rollback comercial:** proibidos

## 1. Objetivo

Fechar a prontidão material para o primeiro deploy acompanhado de `v0.1.0`,
sem iniciar a stack comercial e sem criar uma intenção de implantação.

Ao final:

1. a VPS deve possuir margem de contenção explícita para a stack comercial;
2. o root canônico deve conter configuração real protegida, ainda sem release
   instalada;
3. a ponte RS256/JWKS do deployer deve estar corretamente ligada pelo Compose,
   com chave real pronta e frontend em modo `deployer`;
4. os dois domínios devem possuir TLS válido e proxy de produção preparado;
5. o caminho same-origin do deployer deve alcançar somente o control plane;
6. backup e restore devem ser ensaiados com dados descartáveis fora da stack
   comercial;
7. o control plane deve continuar estável, sincronizado e sem operação;
8. `deploy-production.yml` e `rollback-production.yml` devem permanecer com
   zero runs.

A S45 não publica nova release global e não sobe os sete serviços comerciais.
O primeiro deploy permanece exclusivamente na S46.

## 2. Autorização humana

A delegação deve conter literalmente:

```text
Autorizo integralmente a S45: corrigir e versionar o wiring de produção da ponte RS256/JWKS do deployer e do frontend em modo deployer, preparar a configuração comercial protegida em /opt/sistemas/emporio, criar e ativar um swapfile dedicado de 8 GiB com swappiness 10, instalar os dois server blocks Nginx, emitir os certificados TLS dos domínios do Empório e ensaiar backup/restore apenas com recursos descartáveis e isolados. Autorizo push fast-forward e CI/candidato decorrentes das correções causais. Não autorizo deploy, rollback, migrations comerciais, start da stack comercial, atualização de pacotes ou reboot da VPS.
```

Com essa frase, o executor deve gerar ou descobrir sozinho nomes, IDs, chaves,
senhas, fingerprints, paths e configurações que não dependam de preferência
humana. Não solicitar ao usuário credenciais geráveis, conteúdo de certificado,
e-mail do Certbot já configurado ou valores internos do Compose.

## 3. Decisões fechadas

### 3.1 Capacidade

O snapshot aceito antes da S45 é:

```text
RAM total       16.764.964.864 bytes
RAM available    3.459.567.616 bytes
swap             0
disco /          97.730.969.600 bytes disponíveis
CPU              4
stack comercial  ausente
```

Os limites comerciais existentes somam `4224 MiB` e continuam obrigatórios:

```text
postgresql       768m
backend            1g
website_back       1g
frontend          256m
website_front     256m
whatsapp_service  768m
gateway           128m
```

Não remover, aumentar ou tornar opcional nenhum limite. Para evitar OOM global
no host multi-inquilino, criar somente:

```text
/swapfile-emporio                 arquivo regular, root:root, 0600, 8 GiB
/etc/sysctl.d/90-emporio-swap.conf  vm.swappiness=10
/etc/fstab                        uma única entrada identificada do swapfile
```

Exigir filesystem compatível, espaço livre mínimo de 16 GiB antes da criação,
`mkswap` e `swapon` verdes, uma única entrada no `fstab` e swap ativo após
releitura. Não alterar swap preexistente inesperado. Reversão: `swapoff` do
arquivo exato, remoção da linha identificada e dos dois arquivos exatos.

Atualizações do SO e reboot ficam explicitamente adiados: afetam os demais
sistemas e não são pré-requisito técnico do primeiro deploy. Registrar o
estado corrente como risco operacional, sem executar `apt`, `unattended-upgrade`
ou reinício.

### 3.2 Backup do MVP

O backup transacional canônico permanece em:

```text
/opt/sistemas/emporio/shared/backups/<operationId>
```

Ele é suficiente para a compensação do primeiro deploy, desde que os dumps e
o manifesto sejam verificados antes das migrations. Cópia off-host não deve
ser inventada nesta slice; permanece dívida de recuperação de desastre para a
S47 e não substitui o backup local obrigatório da S46.

### 3.3 JWKS antes de existir backend

O backend comercial ainda não existe na VPS. Portanto é proibido fabricar um
JWKS estático, iniciar um backend parcial ou declarar que o endpoint público
respondeu.

A S45 fecha:

- wiring do Compose e do env;
- chave RSA real e protegida;
- `kid`, issuer e fingerprint;
- prova criptográfica offline e testes da implementação;
- Nginx pronto para encaminhar a rota de identidade ao gateway futuro.

Na S46, depois que o backend estiver healthy e antes de qualquer POST ao
deployer, será obrigatório confrontar o JWKS público, emitir um token pela
ponte autenticada e validar suas claims no control plane. Esse gate não cria
uma slice adicional.

## 4. Snapshot inicial e invariantes herdados

Revalidar antes de agir:

```text
HEAD/origin/main/remoto  7e84fb95974c77a2a710d73f812a4d6bed1e4eb1
stage/diff tracked       vazios
S39-S44 reports          não rastreados
CI                       30943179121, success, 13/13
Publish Candidate        30943915132, success, 11/11
Image workflow           30944695626, success, 4/4
release-control image    sha256:d0d0cb16eb9834767a9d30549fc86ffc37f44a963f274739b7f0c5ddaefe7040
deploy runs              0
rollback runs            0
v0.1.0                   release única, inalterada
```

VPS esperada:

```text
control plane unit       enabled/active
health live/ready        200/200
listener                 127.0.0.1:8180
containers               39 running / 41 total
volumes/networks/images  27 / 19 / 33
control plane            2 containers healthy
stack comercial          ausente
porta 8120               livre
Swarm                    inactive
```

Revalidar ainda App deployer, instalação, `DEPLOYER_ACTOR_IDS`, environment
`production`, transporte SSH e control root, sem abrir secrets. Divergência
material interrompe antes da primeira mutação.

## 5. Correção causal de produção

### 5.1 Compose comercial

Hoje o código da ponte existe no backend, mas `compose.prod.yml` não entrega a
chave nem suas quatro propriedades; o frontend também não recebe
`RELEASE_CONTROL_MODE=deployer`. Corrigir essa fronteira.

No serviço `backend`, acrescentar exclusivamente:

```text
RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED
RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER
RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_PATH
RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID
```

O path dentro do container deve ser fixo e absoluto:

```text
/run/secrets/release-control-deployer-identity-private-key
```

Adicionar secret Compose file-backed, montado somente no backend. Não colocar
PEM em env, imagem, release bundle ou outros serviços.

No `frontend`, entregar exatamente:

```text
RELEASE_CONTROL_MODE=${RELEASE_CONTROL_MODE:-disabled}
```

Na configuração real da VPS o valor será `deployer`; defaults de dev/test
continuam `disabled`.

Atualizar `ops/env/.env.example`, documentação, validadores e testes. Os testes
devem rejeitar:

- ponte habilitada sem issuer, key path ou `kid`;
- issuer HTTP ou domínio diferente em produção;
- key path relativo, dentro da imagem ou exposto a outro serviço;
- secret externo/Swarm, PEM literal ou modo inseguro;
- frontend `publisher`, modo desconhecido ou deployer habilitado por default;
- rota privada direta diferente de same-origin;
- remoção dos limites de memória ou porta gateway não loopback.

### 5.2 Configuração Nginx versionada

Adicionar uma configuração operacional versionada para os dois hosts:

```text
erp-emporio.abaronesa.net.br
emporio.abaronesa.net.br
```

Requisitos:

- HTTP serve somente ACME e redireciona o restante para HTTPS;
- TLS 1.2/1.3 pelos padrões atuais do host;
- upstream comercial único em `127.0.0.1:8120`;
- somente no host ERP, `/api/release-control/v1/` é encaminhado diretamente
  para `127.0.0.1:8180` com headers de proxy fechados;
- `/api/release-control/identity/deployer/` continua no gateway/backend;
- nenhum endpoint do control plane é publicado no domínio website;
- websocket, body limits e headers de segurança preservados;
- ausência do gateway antes da S46 produz `502`/`503`, nunca resposta de outro
  tenant;
- nenhuma edição dos 25 sites preexistentes.

Criar validador e testes mutantes para host errado, porta pública, proxy do
control plane no domínio website, colisão de rota identity/v1, TLS removido,
upstream não loopback e alteração de site externo.

## 6. Gates locais, commit e remoto

Executar testes causais e a matriz vigente completa:

- `release_control/tests` pelo lock;
- oito suítes canônicas;
- todos os validadores registrados, incluindo os novos;
- validação Compose com env sintético seguro;
- testes backend/frontend atingidos;
- secret scan `clean`, `unsupported=0` após stage;
- `catalog:valid` e `git diff --check`.

Somente tudo verde:

1. stagear apenas código, testes, documentação operacional, task/tracker/handoff
   já versionados; nunca relatórios;
2. criar um commit causal normal, sem amend/rebase;
3. reconfirmar remoto e fazer um push fast-forward;
4. aguardar CI e Publish Candidate do mesmo SHA verdes;
5. validar artifacts do candidato sem publicar release;
6. não executar `Publish Release Control Image`, pois nenhuma mudança na imagem
   operacional é necessária, salvo defeito causal real dentro desta fronteira.

Até dois commits causais adicionais são permitidos se provas reais locais ou
da preparação revelarem defeito estritamente nessa fronteira. Cada commit
exige matriz, push fast-forward e gates remotos próprios. Não criar micro-slice.

## 7. Configuração comercial protegida

Somente após gates remotos verdes, preparar:

```text
/opt/sistemas/emporio/shared/.env                         0600 deploy-emporio
/opt/sistemas/emporio/shared/secrets/                    0700 deploy-emporio
release-control-deployer-identity-private-key.pem        0400 uid 10001
/opt/sistemas/emporio/shared/backups/                    0700 deploy-emporio
```

O diretório impede acesso direto do service account do control plane; o Docker
daemon monta somente o arquivo no backend non-root. Provar leitura no container
de teste como UID `10001` sem imprimir bytes e provar negação pelos demais
usuários sem usar o grupo Docker como bypass.

Gerar na VPS, sem stdout, argv ou histórico:

- RSA 3072 PKCS#8 sem passphrase para a ponte deployer;
- `kid` aleatório conforme o contrato;
- três senhas PostgreSQL distintas;
- integration token, sync key e bootstrap root password;
- demais segredos internos geráveis exigidos pelo Compose.

Criar também, somente na estação local:

```text
/home/gregorio/.config/emporio/production/operator-bootstrap.env
```

Modo `0600`, contendo as credenciais bootstrap geradas e nunca exibidas. O
usuário não precisa fornecer esses valores. Não adicionar ao Git nem ao
relatório. Google OAuth, SMTP, Uber e Firebase permanecem desabilitados com
configuração segura; não inventar credencial externa funcional.

O `.env` real deve conter:

- os seis immutable refs exatos do BOM da `v0.1.0`;
- PostgreSQL comercial contratado:
  `postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297`;
- os sete limites da seção 3.1 explicitamente materializados;
- gateway `127.0.0.1:8120` pela topologia Compose;
- URLs HTTPS canônicas;
- ponte deployer habilitada, issuer canônico, key path fixo e `kid`;
- frontend em modo `deployer`;
- Flyway dos runtimes desabilitado;
- bootstrap root preparado para a primeira inicialização.

Executar `docker compose config --quiet` como `deploy-emporio`, sem imprimir o
render. Exigir sete serviços, seis imagens do BOM mais PostgreSQL por digest,
quatro volumes e duas redes; zero build, tag móvel, porta pública, socket,
Swarm ou placeholder.

Não criar `current`, `previous`, release bundle, container, volume ou network
comercial nesta etapa.

## 8. Capacidade, Nginx e TLS na VPS

### 8.1 Swap

Aplicar a decisão da seção 3.1 uma única vez. Capturar memória, swap, disco,
load e contagem Docker antes/depois. Confirmar que nenhum container reiniciou
e que control plane e sites externos permaneceram disponíveis.

### 8.2 Certificados e server blocks

Reconciliar as host keys, versões, sites e certificados antes de escrever.
Reutilizar conta Certbot existente. Preparar primeiro o challenge ACME por
webroot, executar `nginx -t`, reload, emitir certificados e só então instalar
os blocks TLS finais versionados.

Exigir:

- certificado válido e hostname correto em ambos os domínios;
- HTTP redirecionando para HTTPS, exceto ACME;
- ERP `/api/release-control/v1/capabilities` alcançando o control plane e
  respondendo a rejeição autenticada esperada sem token;
- website sem rota para o control plane;
- gateway ausente respondendo `502`/`503`, não conteúdo do vhost default;
- `events.abaronesa.net.br` e amostra representativa dos sites existentes
  preservadas;
- `nginx -t` verde antes e depois de cada reload;
- timer/renovação Certbot presentes sem alterar certificados alheios.

Se emissão ou reload falhar, remover somente blocks/challenges criados pela
S45, restaurar o estado Nginx anterior e parar. Não reiniciar Nginx nem editar
arquivo de outro tenant.

## 9. Ensaio isolado de backup e restore

Usar temporário `mktemp -d`, nomes com prefixo `emporio-s45-` e exclusivamente
o PostgreSQL comercial por digest. Não usar volume, network, DB, env ou
container de outro sistema.

1. criar dois bancos sintéticos e dados sentinela não sensíveis;
2. produzir dumps custom diretamente em arquivo;
3. calcular SHA-256, tamanho e manifesto fechado equivalente ao contrato;
4. destruir somente os bancos/volume descartáveis do ensaio;
5. restaurar em volume novo a partir dos dumps verificados;
6. confrontar schema, contagens e sentinelas;
7. adulterar uma cópia e provar rejeição antes do restore;
8. remover integralmente containers, volumes, redes, dumps e temporário.

Nenhum dump do control plane ou de banco de outro tenant é autorizado. O
ensaio não conta como backup comercial da S46; prova apenas que a rota e os
bytes de recuperação funcionam antes de existirem dados comerciais.

## 10. Gate C terminal

Revalidar após todas as mutações:

- unit do control plane `enabled/active` e dois containers healthy;
- live/ready 200, sync sem drift e `v0.1.0` elegível;
- zero current installation, operação, dispatch e publication operation;
- `127.0.0.1:8180` e porta `8120` ainda livre;
- shared env/key/backups com owners/modes corretos e sem exibição;
- swap 8 GiB ativo, `vm.swappiness=10`, fstab idempotente;
- TLS e redirects verdes; default vhost não atende os domínios;
- ensaio backup/restore verde e sem resíduo;
- stack comercial, migrations comerciais, deploy e rollback em zero;
- tag/release `v0.1.0` inalteradas;
- outros sistemas e contagens externas preservados;
- Git sincronizado, stage/diff tracked vazios e somente reports não rastreados.

## 11. Proibições

- POST ao runtime deployer, criação de idempotency key ou operação;
- `deploy-production.yml`, `rollback-production.yml` ou dispatch manual;
- iniciar serviço comercial, migration real ou pull dos seis images para start;
- fabricar JWKS público ou backend parcial;
- alterar release/tag `v0.1.0`;
- update, upgrade, instalação de pacote, reboot ou restart amplo;
- Nginx restart, edição de site alheio, firewall ou DNS;
- ler credencial GHCR, SSH key, PEM de App ou env protegido;
- imprimir secret, private key, `.env`, Compose renderizado ou header;
- prune, limpeza ampla, force, amend, rebase ou exclusão de evidência;
- aceitar S45 ou criar S46.

## 12. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S45-prontidao-producao-gate-c.report.md
```

Manter não rastreado, fora do stage e sem segredo. Registrar CWD, hashes,
comandos/exits, patch e testes, commits/runs/artifacts, capacidade, swap,
configuração protegida apenas por nomes/modes/fingerprints, TLS, Nginx, ensaio
backup/restore, control plane, negativos e reversões.

O executor não aceita S45 e não cria S46.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — Gate C fechado; aguardando aceite e autorização do primeiro deploy acompanhado da v0.1.0
```

Na primeira causa fora da autoridade:

```text
BLOCKED — S45 interrompida fail-closed na primeira causa técnica
```

## 13. Critérios de aceite

A S45 somente será aceita com wiring RS256/deployer versionado e remoto verde,
configuração real protegida, capacidade com swap e limites comprovados, TLS e
proxy corretos sem regressão de tenants, ensaio isolado de backup/restore,
control plane pronto e sincronizado e zero deploy/rollback/operação comercial.

O JWKS público, token real e fluxo UI são critérios iniciais da S46, pois
dependem do backend criado pelo primeiro deploy; não são evidência fabricável
na S45.
