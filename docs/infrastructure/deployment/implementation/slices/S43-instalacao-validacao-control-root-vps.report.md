# S43 — Instalação e validação do control root na VPS

> **Data:** 04/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Executor:** CLI
> **Contrato:** `S43-instalacao-validacao-control-root-vps.task.md`
> **SHA-256 da task:** `53014914af79a017fd5c417e367650f4a6f4a821c5f291f1adbdc6a015300ae9`
> **Resultado:** `IN_PROGRESS — control root instalado e validado na VPS; aguardando aceite e implantação do control plane`

## 1. Autorização humana

A delegação continha literalmente a frase exigida pela §2:

> "Autorizo integralmente a S43: reconstruir do SHA remoto aceito o pacote
> determinístico do control root, usar a rota bootstrap root já existente
> exclusivamente para transferi-lo e instalá-lo em
> /opt/sistemas/emporio/shared/control, validar o helper como deploy-emporio e
> executar a reversão exata dessa instalação se uma verificação terminal
> falhar. Não autorizo iniciar serviços, Docker Compose, deploy, rollback,
> Nginx, TLS, backup, swap, reboot ou qualquer outra preparação da VPS."

Nenhuma dessas ações proibidas foi executada: nenhum serviço, Compose, deploy,
rollback, Nginx, TLS, backup, swap ou reboot.

## 2. Snapshot inicial

### 2.1 Local e GitHub

| Item | Exigido | Observado |
|---|---|---|
| `HEAD` | `7c2e129272e15e0898dae04b3b75314bf487ee82` | idêntico |
| `origin/main` (local e remoto) | `9731954d474fb68ec1384a525e1075f9a5542e24` | idêntico |
| divergência | ahead 1 / behind 0 | idêntico |
| SHA-256 da task | `53014914...5300ae9` | idêntico |
| stage | vazio | vazio |
| não rastreados | relatórios S39–S42 | apenas eles |
| `git diff --check` | 0 | 0 |
| CI `30902014368` | 13/13 jobs verdes | `success`, 0 jobs não-success |
| Publish Candidate `30902729166` | 11/11 jobs verdes, artifacts presentes | `success`, 0 jobs não-success, 18 artifacts |
| deploy/rollback runs | 0/0 | 0/0 |
| `v0.1.0` | inalterada | `target_commitish=38385c100ab8b0ae07099b6a5a7b016b7c2b7322` (idêntica antes/depois) |

### 2.2 VPS antes da mutação (root, rota bootstrap já confiada)

Sessão com `BatchMode=yes`, `StrictHostKeyChecking=yes`, `ConnectTimeout=10`,
`ForwardAgent=no`, `ForwardX11=no`, `ControlMaster=no`, `ControlPath=none`,
`NumberOfPasswordPrompts=0`; host key já presente em `known_hosts` desde a
S41, nenhuma aceita nesta sessão.

```text
hostname                              srv1006846
arquitetura                           x86_64
python3                               3.10.12
deploy-emporio                        uid=998 gid=998, groups=deploy-emporio,docker
senha                                 L (bloqueada)
/opt/sistemas/emporio/shared/control  drwx------ deploy-emporio:deploy-emporio, vazio (0 entradas)
.control-staging-*                    ausente
control-root temporário da S43        ausente
portas 8120/8180                      sem listener
systemd emporio/release-control        nenhuma unidade
docker (running/total/vol/net/img)     37 / 39 / 26 / 18 / 31
```

Target não vazio, symlink, owner/mode divergente, staging hostil ou helper
preexistente teriam interrompido aqui — nenhuma dessas condições ocorreu.

## 3. Reconstrução determinística local

Diretório de trabalho `mktemp -d`, modo `0700`, fora do repositório, com
subdiretórios `wheels/`, `build-a/`, `build-b/`.

Lock lido do objeto Git do `sourceSha` aceito (`git cat-file blob
9731954...:ops/deploy/control-root/requirements.lock`), idêntico ao da
working tree (sem drift). Sete wheels baixados por nome/versão exatos via
`pip download --no-deps --only-binary=:all:`, alvo CPython 3.10 /
`manylinux2014_x86_64`, todos os sete hashes conferindo byte a byte com o
lock:

```text
attrs-26.1.0-py3-none-any.whl                                         sha256=c647aa4a... OK
jsonschema-4.23.0-py3-none-any.whl                                    sha256=fbadb6f8... OK
jsonschema_specifications-2025.9.1-py3-none-any.whl                   sha256=98802fee... OK
PyYAML-6.0.2-cp310-cp310-manylinux_2_17_x86_64.manylinux2014_x86_64.whl sha256=ec031d5d... OK
referencing-0.37.0-py3-none-any.whl                                   sha256=381329a9... OK
rpds_py-0.30.0-cp310-cp310-manylinux_2_17_x86_64.manylinux2014_x86_64.whl sha256=0c0e95f6... OK
typing_extensions-4.15.0-py3-none-any.whl                             sha256=f0fa19c6... OK
```

`control_root_package.py` também lido do mesmo objeto Git (blob idêntico à
working tree). `build` executado duas vezes, saídas separadas:

```text
python3 tools/deploy/control_root_package.py build --repository . \
  --source-sha 9731954d474fb68ec1384a525e1075f9a5542e24 \
  --lock <lock-do-commit> --wheels <wheels/> --output build-a
  -> control-root-package:built:9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0

python3 tools/deploy/control_root_package.py build ... --output build-b
  -> control-root-package:built:9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0

cmp build-a/control-root.tar build-b/control-root.tar -> BYTE-IDENTICAL
diff *.tar.sha256                                       -> SIDECAR-IDENTICAL
digest == 9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0  (== aceito na S42)
```

Manifesto validado antes de qualquer transferência: `schemaVersion=1`,
`kind=emporio-control-root`, `repository=greggorio/abaronesa-emporio`,
`sourceSha` correto, `platform=linux/amd64`, `pythonAbi=cp310`,
`requirementsSha256` conferindo com o SHA-256 do lock local, 188 arquivos
ordenados deterministicamente por path posix.

Após o download dos wheels, nenhum comando de build/validação/instalação
acessou rede.

## 4. Transferência e instalação única

Diretório remoto exclusivo criado pela sessão root via `mktemp -d -p /root`,
modo `0700` (`/root/tmp.9BjqTEY0RZ`), fora de `/tmp` compartilhado e fora do
target.

Transferidos via `scp` apenas os três arquivos exigidos
(`control_root_package.py`, `control-root.tar`, `control-root.tar.sha256`);
modos ajustados para `0600` após a cópia. Confirmados como arquivos regulares,
sem link, owner `root`.

Hashes no host comparados byte a byte com os locais — os três idênticos:

```text
control-root.tar         9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0
control-root.tar.sha256  cd6d3aec3a731980e4fd6d81e463f1b973349133075f49f7643938c885dd64da
control_root_package.py  729c252772358217a50ad1588dfe6cfaefabb718c3316c84018755b945b4696f
```

Target reconfirmado vazio imediatamente antes do instalador. Instalador
executado pelo `/usr/bin/python3` do host, como root, com `--archive`,
`--sidecar` e `--source-sha` explícitos:

```text
control-root-package:installed:9731954d474fb68ec1384a525e1075f9a5542e24
exit: 0
```

Nenhum `apt`, `pip`, `curl`, `wget`, Docker ou shell do archive foi
executado. Instalação única; nenhum retry.

## 5. Provas no host real

```text
verify --root <target> --source-sha 9731954...   -> control-root-package:verified:9731954d474fb68ec1384a525e1075f9a5542e24  (exit 0)

manifesto (via python3 -c):
  sourceSha           9731954d474fb68ec1384a525e1075f9a5542e24
  platform            linux/amd64
  pythonAbi           cp310
  requirementsSha256  866069e6a4a5a7630545d81b349b04eca7ac923d7cc8b1686eb6d3cf7cf71671
  files               188
sidecar do manifesto:  sha256 do manifest.json == conteúdo do .sha256  (idênticos)

conjunto de arquivos: nenhum symlink/device/fifo sob o target;
  190 arquivos regulares + 47 diretórios = 237 entradas
  (190 = 188 do manifesto + os 2 arquivos internos do próprio manifesto)

owner/modo de toda a árvore: 100% deploy-emporio:deploy-emporio;
  diretórios 100% 0700; nenhum arquivo com bit de grupo/outro;
  único executável 0755: ops/deploy/deploy-release.sh (conforme manifesto)

imports (usuário deploy-emporio, sys.path apontado só para <target>/vendor):
  jsonschema 4.23.0  /opt/sistemas/emporio/shared/control/vendor/jsonschema/__init__.py
  yaml               /opt/sistemas/emporio/shared/control/vendor/yaml/__init__.py
  PASS: ambos resolvidos do vendor, jsonschema é 4.x

Draft 2020-12 (usuário deploy-emporio, vendor real, host real):
  válido aceito; 3 mutantes concretos rejeitados —
    prefixItems fora de ordem, item extra com items:false, $defs de digest violado
  PASS: os três levantaram jsonschema.ValidationError

capabilities (usuário deploy-emporio, helper instalado, comando exato do contrato):
  /usr/bin/python3 /opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py capabilities
  {"controlSha":"9731954d474fb68ec1384a525e1075f9a5542e24","deployRoot":"/opt/sistemas/emporio",
   "protocol":"emporio-deployment-transport","schemaVersion":1,"user":"deploy-emporio"}
  exit: 0 — todos os cinco campos exatamente os contratados
```

Nenhum `snapshot`, `install`, `execute` ou `cleanup` do helper foi chamado;
nenhum request de deploy foi fabricado.

## 6. Resíduo não intencional encontrado e corrigido

As provas de import e de Draft 2020-12 (§5), executadas como `deploy-emporio`
sem `-B`/`PYTHONDONTWRITEBYTECODE=1`, geraram `__pycache__`/`*.pyc` dentro do
próprio `vendor/` e de `tools/` do target — efeito colateral da forma como o
executor rodou as provas de leitura, não um defeito do pacote ou do
instalador. Contagem de entradas subiu de 237 para 310 (73 arquivos de
bytecode em 11 diretórios `__pycache__`).

Corrigido antes de prosseguir: os 11 diretórios `__pycache__` foram removidos
(`find ... -name __pycache__ -type d -exec rm -rf`), a árvore voltou a 237
entradas exatas, e `verify` foi reexecutado com `python3 -B` — via o próprio
blob do `control_root_package.py` (o arquivo já havia sido removido do
temporário remoto) passado por stdin, sem gravar novamente no disco local nem
remoto:

```text
git cat-file blob 9731954...:tools/deploy/control_root_package.py | \
  ssh root@31.97.251.16 python3 -B - verify --root <target> --source-sha 9731954...
-> control-root-package:verified:9731954d474fb68ec1384a525e1075f9a5542e24 (exit 0)
zero __pycache__ residual; 237 entradas confirmadas
```

Isto não caracteriza a exceção da seção 9 (nenhum código do pacote foi
alterado); nenhum commit ou push foi necessário.

## 7. Limpeza e provas negativas

```text
rm -rf /root/tmp.9BjqTEY0RZ            -> exit 0; confirmado ABSENTE
rm -rf <workdir local>                  -> confirmado ABSENTE
.control-staging-*                      -> ausente
docker (running/total/vol/net/img)      37 / 39 / 26 / 18 / 31   (idêntico ao baseline §2.2)
portas 8120/8180                        sem listener
systemd emporio/release-control          nenhuma unidade
.env / docker-compose / *.conf / current / previous / *backup*   ausentes sob /opt/sistemas/emporio
deploy/rollback runs (GitHub)            0 / 0
v0.1.0                                   inalterada (mesmo target_commitish)
git local (HEAD, stage, branch)          inalterado — nenhum commit, nenhum push
```

## 8. Correção causal (seção 9)

Não foi necessária. O único desvio observado (§6) foi um efeito colateral da
execução das provas de leitura pelo próprio executor, não um defeito no
builder, instalador, vendor, manifesto, `deployment-remote.py`,
`deployment_cli.py` ou binding de `controlSha`; corrigido por limpeza
dirigida, sem alterar nenhum arquivo do repositório. Nenhum commit, push, CI
ou segunda instalação foram executados.

## 9. Zero deploy, rollback e control plane

`release_control` não foi instalado nem iniciado; nenhum container, imagem,
volume, rede ou listener foi criado como efeito desta slice; nenhum workflow
de deploy ou rollback foi disparado; nenhum App, secret, variable, allowlist
ou environment foi criado ou alterado; nenhuma chave privada, Docker config,
`.env` ou sessão preexistente foi lida ou exposta.

## 10. Resíduos finais

```text
VPS:   /opt/sistemas/emporio/shared/control instalado e íntegro (único resíduo
       intencional da S43) — 237 entradas, verify verde, capabilities verde
       temporário root removido; sem staging; sem pycache
       Docker/portas/systemd idênticos ao baseline; deploy/rollback 0/0
local: temporário mktemp removido; nenhum arquivo novo no repositório
       git status --short --branch: apenas os quatro relatórios não rastreados
       (S39, S40, S41, S42) e este relatório (S43), todos fora do stage
       HEAD, origin/main e divergência inalterados desde o snapshot inicial
```

O executor não aceitou a S43, não alterou tracker/task e não criou a S44.

IN_PROGRESS — control root instalado e validado na VPS; aguardando aceite e implantação do control plane
