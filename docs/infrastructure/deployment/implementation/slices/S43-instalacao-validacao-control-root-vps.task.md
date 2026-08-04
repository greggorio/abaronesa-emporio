# S43 — Instalação e validação do control root na VPS

> **Data:** 04/08/2026
> **Predecessora:** S42 aceita
> **Tipo:** preparação operacional limitada e reversível da VPS
> **Deploy/rollback/control plane:** proibidos

## 1. Objetivo

Reconstruir deterministicamente, a partir do commit remoto aceito, o pacote do
control root entregue pela S42, instalá-lo no diretório vazio
`/opt/sistemas/emporio/shared/control` e provar no host real que:

1. o archive e seus sidecars reproduzem os hashes aceitos;
2. a instalação ocorre com Python 3.10, sem rede e sem pacote global;
3. toda a árvore instalada pertence a `deploy-emporio`, com os modos fechados;
4. o runtime resolve `jsonschema` e PyYAML exclusivamente do vendor instalado;
5. Draft 2020-12 rejeita os mutantes que o `jsonschema 3.2.0` global aceitava;
6. `deployment-remote.py capabilities`, executado como `deploy-emporio`,
   devolve o `controlSha` instalado e o contrato esperado;
7. nenhum serviço, container, imagem, volume, rede, listener, deploy ou rollback
   é criado como efeito desta slice.

A S43 instala somente o control root. Ela não instala nem inicia o
`release_control`, não configura o control plane e não publica a stack
comercial.

## 2. Autorização humana e fronteira externa

A mensagem de delegação deve conter literalmente:

```text
Autorizo integralmente a S43: reconstruir do SHA remoto aceito o pacote determinístico do control root, usar a rota bootstrap root já existente exclusivamente para transferi-lo e instalá-lo em /opt/sistemas/emporio/shared/control, validar o helper como deploy-emporio e executar a reversão exata dessa instalação se uma verificação terminal falhar. Não autorizo iniciar serviços, Docker Compose, deploy, rollback, Nginx, TLS, backup, swap, reboot ou qualquer outra preparação da VPS.
```

Essa autorização cobre:

- consultas locais, GitHub e VPS necessárias ao baseline;
- download local dos sete wheels públicos exatos do lock;
- criação de temporários locais e remotos com modo fechado;
- transferência do instalador, archive e sidecar pela rota bootstrap já
  confiada;
- uma instalação no control root comprovadamente vazio;
- validações de leitura como `root` e como `deploy-emporio`;
- limpeza dirigida dos temporários;
- reversão exata do conteúdo instalado pela própria S43, somente nas condições
  da seção 10;
- caso o host real exponha um defeito causal reparável dentro da fronteira da
  seção 9: um commit técnico, um push fast-forward, CI/candidato e uma segunda
  instalação somente depois da reversão e da prova de root vazio.

Sem a frase literal, executar apenas o snapshot local e parar. Com ela, não
pedir nova confirmação para as ações listadas acima.

## 3. Identidades imutáveis

```text
repository       greggorio/abaronesa-emporio
source/control   9731954d474fb68ec1384a525e1075f9a5542e24
package sha256   9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0
target           /opt/sistemas/emporio/shared/control
host             31.97.251.16:22
bootstrap user   root (somente instalação e verificação desta slice)
runtime user     deploy-emporio
platform         linux/amd64
python ABI       CPython 3.10
```

O pacote deve ser construído do objeto Git de 40 hex acima, nunca do `HEAD`
documental, de `main` móvel ou da working tree. O archive gerado não é artifact
GitHub nem release.

Se uma correção causal autorizada for indispensável, o novo commit remoto de
código passa a ser a única identidade válida: reconstruir duas vezes, obter
novo hash, observar CI/candidato verdes e registrar claramente a substituição.
Não instalar bytes de commit local não publicado.

## 4. Snapshot inicial obrigatório

### 4.1 Local e GitHub

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD
git ls-remote origin refs/heads/main
git log --oneline --decorate -12
sha256sum docs/infrastructure/deployment/implementation/slices/S43-instalacao-validacao-control-root-vps.task.md
git diff --check
git diff --cached --name-only
gh run view 30902014368
gh run view 30902729166
gh run list --workflow deploy-production.yml --limit 100 --json databaseId
gh run list --workflow rollback-production.yml --limit 100 --json databaseId
gh api repos/greggorio/abaronesa-emporio/releases/tags/v0.1.0
```

Esperado:

- `origin/main` local e remoto em `9731954d474fb68ec1384a525e1075f9a5542e24`;
- apenas commits documentais do orquestrador podem estar à frente, sem behind;
- stage vazio;
- relatórios S39–S42 não rastreados;
- CI `30902014368` com 13/13 jobs verdes;
- Publish Candidate `30902729166` com 11/11 jobs verdes e artifacts presentes;
- zero runs de deploy e rollback;
- `v0.1.0` inalterada.

Divergência de código, remoto, stage ou runs interrompe antes do acesso à VPS.

### 4.2 VPS antes da mutação

Usar a rota bootstrap root já confiada com `BatchMode=yes`,
`StrictHostKeyChecking=yes`, `ConnectTimeout=10`, forwardings desligados e sem
aceitar host key nova. Não abrir nem transcrever chave, Docker config, `.env`,
token ou sessão existente.

Confirmar, antes de transferir qualquer byte:

```text
hostname                                  srv1006846
arquitetura                               x86_64
python3                                   3.10.x
deploy-emporio                            uid/gid existentes; senha bloqueada
grupos                                    deploy-emporio e docker somente
/opt/sistemas/emporio/shared/control      dir real, 0700, owner dedicado, vazio
.control-staging-* no parent              ausente
control-root temporário da S43            ausente
portas 8120 e 8180                        sem listener
systemd emporio/release-control            nenhuma unidade
deploy/rollback runs                      zero
```

Registrar também, sem inspecionar conteúdo de outros sistemas, as contagens de
containers em execução/total, volumes, redes e imagens. Variação preexistente
entre S41 e S43 não é falha automática; a própria S43 não pode alterá-las.

Se o target não estiver vazio, houver symlink, owner/mode divergente, staging
hostil ou helper já instalado, parar. Não sobrescrever, atualizar ou tentar
adotar conteúdo preexistente.

## 5. Reconstrução determinística local

Criar um único diretório `mktemp -d` fora do repositório, modo `0700`, com
subdiretórios separados para wheels e os dois builds.

1. Ler `ops/deploy/control-root/requirements.lock` do objeto Git do
   `sourceSha` aceito.
2. Baixar somente os sete filenames/versionamentos declarados, com
   `--only-binary=:all:`, `--no-deps`, alvo CPython 3.10 e
   `manylinux2014_x86_64` quando aplicável.
3. Não executar `pip install`; não usar cache como fonte de confiança; não
   aceitar sdist, wheel extra, filename alternativo ou hash divergente.
4. Executar `tools/deploy/control_root_package.py build` duas vezes, fornecendo
   explicitamente repository, source SHA, lock, wheels e outputs distintos.
5. Exigir archives e sidecars byte a byte idênticos.
6. Exigir SHA-256 do archive exatamente
   `9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0`.
7. Validar manifesto canônico, `sourceSha`, lock, conjunto de arquivos, modos e
   hashes antes da transferência.

Depois que os wheels forem baixados, a construção, validação, instalação e
provas do pacote não podem acessar rede para resolver dependências.

## 6. Transferência e instalação única

Criar na VPS, pela sessão root, um diretório temporário exclusivo retornado por
`mktemp -d`, sob diretório root-only, com modo `0700`. Não usar nome previsível,
`/tmp` compartilhado ou path dentro do target.

Transferir somente:

```text
control_root_package.py       blob do sourceSha aceito
control-root.tar              archive reproduzido
control-root.tar.sha256       sidecar reproduzido
```

Após a transferência:

- exigir arquivos regulares, sem links, owner root, modos no máximo `0600`;
- comparar no host os três hashes com os calculados localmente;
- provar novamente que o target permanece vazio;
- executar o instalador real pelo `/usr/bin/python3` do host, como root, com
  `--archive`, `--sidecar` e `--source-sha` explícitos;
- capturar o exit final e exigir exatamente
  `control-root-package:installed:<sourceSha>`;
- não executar `apt`, `pip`, `curl`, `wget`, Docker ou shell do archive.

Não há retry automático da instalação. Qualquer resposta ambígua ou exit não
zero segue a seção 10 antes de nova decisão.

## 7. Provas no host real

Executar e registrar, com exits individuais:

1. `control_root_package.py verify --root <target> --source-sha <sourceSha>`;
2. manifesto e sidecar canônicos, com `sourceSha`, `platform=linux/amd64`,
   `pythonAbi=cp310` e `requirementsSha256` corretos;
3. conjunto exato de paths do manifesto, sem arquivo extra, link, device ou
   FIFO;
4. hash, tamanho e modo de cada arquivo;
5. toda a cadeia e árvore sob o target com owner
   `deploy-emporio:deploy-emporio`, diretórios `0700`, scripts `0755` e demais
   arquivos `0600` conforme manifesto;
6. como `deploy-emporio`, imports de `jsonschema 4.23.0` e PyYAML vindos de
   `<target>/vendor`, nunca de `/usr/lib` ou site-packages global;
7. como `deploy-emporio`, três mutantes concretos com `prefixItems`/`$defs`
   rejeitados pelo Draft 2020-12 vendorizado;
8. como `deploy-emporio`, executar exatamente o helper instalado:

```text
/usr/bin/python3 /opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py capabilities
```

O JSON deve conter exatamente os valores contratados para:

```text
protocol       emporio-deployment-transport
schemaVersion  1
deployRoot     /opt/sistemas/emporio
user           deploy-emporio
controlSha     <sourceSha instalado>
```

Não chamar `snapshot`, `install`, `execute` ou `cleanup` do helper. Não
fabricar request de deploy para provar o binding; capabilities íntegro e a
matriz causal aceita da S42 fecham esta etapa.

## 8. Provas negativas e limpeza

Depois das verificações:

- remover de forma dirigida o diretório temporário remoto com instalador,
  archive e sidecar;
- remover o temporário local, incluindo wheels e os dois builds;
- provar ausência dos dois temporários e de `.control-staging-*`;
- confirmar que o target instalado permanece íntegro e é o único resíduo
  intencional da S43;
- comparar as contagens Docker com o baseline desta mesma execução;
- confirmar portas `8120`/`8180` livres, nenhuma unidade nova, nenhum `.env`,
  Compose, release comercial, link `current`/`previous`, backup, Nginx ou TLS;
- confirmar no GitHub zero deploy/rollback e `v0.1.0` inalterada;
- confirmar nenhum stage, commit ou push se não houve correção causal.

## 9. Correção causal excepcional, sem micro-slice

Se a execução real revelar defeito estritamente no builder, instalador,
vendor, manifesto, `deployment-remote.py`, `deployment_cli.py` ou binding de
`controlSha`, o executor pode repará-lo dentro desta mesma S43, sem pedir nova
slice, desde que:

1. pare antes de novo ato mutante;
2. reverta a instalação parcial/existente conforme a seção 10 e prove target
   vazio;
3. altere somente a fronteira causal já permitida pela S42;
4. acrescente teste que reproduza a causa real;
5. execute a matriz completa vigente, 22 validadores, secret scan
   `unsupported=0` e `git diff --check`;
6. crie no máximo um commit técnico e um push normal fast-forward;
7. observe CI e Publish Candidate verdes do novo SHA;
8. reconstrua duas vezes o pacote do novo SHA publicado;
9. execute no máximo uma segunda instalação, sem relaxar qualquer gate.

Não usar essa autorização para mudar arquitetura, dependências, target,
permissões, usuário, protocolo, workflow de deploy/rollback ou recursos da VPS.
Se a causa exigir isso, parar fail-closed.

## 10. Reversão exata

### 10.1 Falha antes ou durante o instalador

O instalador deve deixar o target vazio e remover seu staging. Confirmar isso,
remover somente o temporário remoto conhecido e parar. Se houver conteúdo não
previsto, não apagar: preservar evidência e reportar.

### 10.2 Falha depois de instalação concluída

Reverter somente se:

- o baseline provou o target vazio;
- o conteúdo presente é exatamente o conjunto do manifesto local confiável,
  mais os dois arquivos internos do manifesto;
- não existe path, link ou tipo adicional.

Remover os arquivos exatos do manifesto, os dois sidecars internos e depois
somente os diretórios vazios internos, em ordem descendente. Preservar o
diretório `control` vazio, seu owner e modo. Não usar glob, `rm -rf`, variável
não validada ou remoção recursiva de `/opt/sistemas/emporio`.

Após a reversão, provar target real `0700`, owner dedicado, vazio, ausência de
staging e baseline Docker/portas inalterado. A reversão não autoriza remover o
usuário, chave SSH, environment `production` ou qualquer recurso da S41.

## 11. Proibições

- iniciar `release_control`, PostgreSQL ou qualquer container;
- `docker pull`, login, build, run, compose, volume, network ou prune;
- executar workflow de deploy, rollback, release ou imagem;
- criar App, secret, variable, allowlist ou environment;
- instalar/atualizar pacote global local ou remoto;
- criar `.env`, migration, backup, systemd, Nginx, Certbot, TLS ou firewall;
- aplicar update, swap ou reboot;
- ler Docker config, chave privada, token, `.env` ou sessão preexistente;
- alterar outros sistemas do host multi-inquilino;
- sobrescrever target não vazio;
- incluir relatório, chave ou temporário em commit;
- amend, rebase, force push ou múltiplos commits/pushes;
- aceitar a S43 ou criar a S44.

## 12. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S43-instalacao-validacao-control-root-vps.report.md
```

Manter não rastreado, não staged e não commitado. Registrar:

- autorização literal, CWD, SHA-256 da task e snapshots;
- comandos exatos sanitizados, exits finais e interpretação;
- wheel filenames e hashes, dois builds e digest do archive;
- baseline da VPS sem expor segredo;
- transferência, hashes e instalação;
- manifesto, modos, owners, imports, Draft 2020-12 e capabilities;
- contagens Docker/portas/unidades antes e depois;
- temporários e limpeza;
- qualquer correção excepcional, commit/push/runs, se usada;
- prova de zero deploy/rollback/control plane;
- resíduos finais.

O executor não aceita S43 e não cria a próxima slice.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — control root instalado e validado na VPS; aguardando aceite e implantação do control plane
```

Na primeira causa não resolvida:

```text
BLOCKED — S43 interrompida fail-closed na primeira causa técnica
```

## 13. Critérios de aceite

S43 somente será aceita quando o pacote for reproduzido do SHA remoto, o digest
fechar, a instalação única estiver íntegra no target canônico, vendor e Draft
2020-12 forem efetivos no Python 3.10 real, capabilities como
`deploy-emporio` devolver o `controlSha` correto, temporários forem removidos e
Docker, serviços, portas, GitHub, release e workflows de produção permanecerem
inalterados.
