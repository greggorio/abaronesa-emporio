# S44 — correction-01: referência OCI canônica do PostgreSQL amd64

> **Data:** 04/08/2026
> **Estado:** `AUTHORIZED`
> **Contrato principal:** `S44-implantacao-control-plane-deployer-vps.task.md`
> **Relatório contínuo:** `S44-implantacao-control-plane-deployer-vps.report.md`
> **Escopo:** corrigir somente a forma da referência imutável do PostgreSQL e
> retomar a S44 do checkpoint publicado

## 1. Diagnóstico corrigido

A parada da S44 foi segura e está aceita, mas o digest não desapareceu do
registry. A causa é a composição incorreta entre tag e digest de plataforma.

A tag oficial:

```text
postgres:16.6-alpine
```

aponta para o índice multiarch:

```text
sha256:1d04b9ba1d4996401f2552b51beda8187f175c0645c091e4781134fc9c9a3eef
```

Dentro desse índice, o manifest oficial `linux/amd64`, ainda `active`, é:

```text
sha256:589f3b24f30e60a2b33f79543ed51c8f897589bcde5c59f4dc0e814551eeeb0f
```

Por isso:

```text
postgres:16.6-alpine@sha256:589f3b...eb0f  -> falha de verificação
postgres@sha256:589f3b...eb0f              -> resolve
```

O primeiro formato afirma simultaneamente uma tag cujo digest é o índice e um
digest diferente pertencente ao filho amd64. O segundo é a identidade OCI
canônica por repositório e digest e resolve tanto localmente quanto na VPS.

Não há motivo causal para atualizar PostgreSQL 16.6 para 16.14 nem para escolher
novo Alpine. A correction preserva os mesmos bytes `linux/amd64` inicialmente
selecionados e altera somente a forma da referência.

## 2. Decisão vinculante

A referência da seção 3.2 da task fica substituída por:

```text
postgres@sha256:589f3b24f30e60a2b33f79543ed51c8f897589bcde5c59f4dc0e814551eeeb0f
```

Esta é a única referência PostgreSQL autorizada na S44. Continuam proibidos:

- tag sem digest;
- `latest`, `16`, `16-alpine` ou qualquer tag móvel;
- `tag@digest` quando a tag aponta para índice diferente;
- mudança para PostgreSQL 16.14 ou outro patch;
- digest escolhido pelo executor;
- pull antes de resolver a referência nas duas pontas.

Antes da primeira mutação, exigir novamente:

```text
local  docker manifest inspect postgres@sha256:589f3b...eb0f  exit 0
VPS    docker manifest inspect postgres@sha256:589f3b...eb0f  exit 0
Docker Hub tag metadata                                      linux/amd64, active
```

Se qualquer uma dessas três provas divergir, parar. Não substituir novamente.

## 3. Checkpoint aceito

```text
HEAD/origin/main/remoto  9699214582c8e74ed6c005eb2ad1e04ec950f5aa
stage                    vazio
task SHA-256             0ca6665a5efc26f912d39f563132ee6c90d4270526ab0de5959b61938e70422f
report SHA-256           bb5893476d209cc57ab35455120461cf5cba41f9fbaf56d5c882cb465039a058
CI                       30929281890, success, 13/13
Publish Candidate        30930060013, success, 11/11
Image workflow           30930719302, success, 4/4
```

Imagem operacional aceita para a retomada:

```text
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:2a214f0c575ab4391855a4cd3b1f3727bce8593e90e265bd89a6564171caff37
```

O package version `1098483203`, manifesto `8901092793` e outcome `8901100126`
já foram validados. Não repetir CI, Publish Candidate, publicação da imagem,
download dos artifacts ou scan sem nova alteração causal de código.

Estado externo preservado:

- zero App deployer, instalação, PEM ou variável;
- `DEPLOYER_ACTOR_IDS` ausente;
- zero tentativas de start;
- root operacional, `/etc/emporio`, user/unit, containers, volume e rede alvo
  ausentes;
- zero deploy/rollback;
- `v0.1.0` inalterada.

## 4. Retomada

Depois das três provas da seção 2:

1. retomar diretamente na seção 6 da task, provisionando a GitHub App deployer;
2. configurar `DEPLOYER_ACTOR_IDS` somente após validar App, instalação,
   repositório, permissões e bot ID;
3. preparar a VPS usando a imagem operacional e a referência PostgreSQL desta
   correction;
4. executar Compose config, pull, start, migrations, health, sync e restart;
5. manter todas as demais fronteiras, proibições, reversão, relatório e
   critérios da task.

A inspeção da página de GitHub Apps que deixou de ser necessária na execução
bloqueada não é uma prova herdada. Na retomada, executar a reconciliação
administrativa da seção 6 uma única vez antes de criar qualquer App, sem pedir
ao usuário dados que possam ser descobertos.

Esta correction não altera código e não consome o último commit corretivo ainda
disponível na seção 9. Se um novo defeito real exigir esse commit, aplicar o
loop causal original. A simples escrita da referência canônica no env protegido
da VPS não exige commit, push ou nova publicação.

## 5. Autorização cumulativa

A delegação deve conter literalmente:

```text
Autorizo a correction-01 da S44 a usar exclusivamente postgres@sha256:589f3b24f30e60a2b33f79543ed51c8f897589bcde5c59f4dc0e814551eeeb0f, revalidar essa referência localmente e na VPS e retomar do checkpoint 9699214582c8e74ed6c005eb2ad1e04ec950f5aa a criação da App deployer e a implantação do control plane. Preservo integralmente a autorização original da S44 e suas proibições de deploy, rollback e stack comercial.
```

Task, correction e relatório formam autoridade cumulativa. Sem a frase literal,
executar somente o snapshot.

## 6. Relatório e terminal

Não criar novo relatório. Acrescentar ao relatório contínuo uma seção
`Retomada correction-01`, contendo:

- hashes da task, correction e relatório anterior;
- as três provas da referência OCI;
- confirmação de que runs/artifacts já verdes não foram repetidos;
- toda a execução restante exigida pela task;
- estado final de App, variável, VPS, health, sync, restart e negativos.

O relatório permanece não rastreado, não staged e não commitado. O executor não
aceita S44 nem cria S45.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — control plane deployer implantado e estável; aguardando aceite e Gate C de prontidão
```

Na primeira causa não resolvida:

```text
BLOCKED — S44 correction-01 interrompida fail-closed na primeira causa técnica
```
