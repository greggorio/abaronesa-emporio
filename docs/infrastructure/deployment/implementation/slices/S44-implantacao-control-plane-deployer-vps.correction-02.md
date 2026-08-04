# S44 — correction-02: schemas no path runtime efetivo

> **Data:** 04/08/2026
> **Estado:** `AUTHORIZED`
> **Contrato principal:** `S44-implantacao-control-plane-deployer-vps.task.md`
> **Autoridade anterior:** `S44-implantacao-control-plane-deployer-vps.correction-01.md`
> **Relatório contínuo:** `S44-implantacao-control-plane-deployer-vps.report.md`
> **Escopo:** corrigir o path absoluto dos schemas na imagem e concluir a S44

## 1. Parada aceita e causa fechada

A parada após a segunda tentativa foi fail-closed e está aceita. A reversão da
VPS restaurou o baseline. A GitHub App deployer, sua instalação, o PEM local e
`DEPLOYER_ACTOR_IDS` são recursos válidos e devem ser reutilizados.

A causa terminal está fechada pelo runtime real:

```text
arquivo Python efetivo
/app/src/emporio_release_control/artifacts.py

ROOT = Path(__file__).resolve().parents[3]
ROOT efetivo na imagem = /

schema procurado
/ops/releases/global-release.schema.json

schema empacotado pelo commit c951ceb
/app/ops/releases/global-release.schema.json
```

O mesmo cálculo é usado por `deployment_artifacts.py` e
`rollback_artifacts.py`. Portanto, a correção causal é publicar os cinco
schemas em `/ops`, não em `/app/ops`. Não alterar os validadores de artifact,
enfraquecer schemas ou introduzir fallback de busca.

## 2. Autorização do commit adicional

O limite numérico da seção 9 da task e da correction-01 fica superseded. Fica
autorizado o commit corretivo necessário para:

```dockerfile
COPY --from=builder --chown=10001:10001 /build/ops /ops
```

e para atualizar apenas os testes e validadores causais correspondentes.

O estado terminal deve ter:

- cinco schemas regulares sob `/ops/releases` e `/ops/deploy/schemas`;
- owner `10001:10001`, sem escrita por grupo/outros;
- nenhum schema sob `/app/ops`;
- nenhuma alteração nos paths esperados pelo código;
- imagem ainda non-root, read-only, sem capabilities e sem Docker socket.

Este commit deve ser novo, normal e fast-forward. Amend, rebase, force ou
reescrita de `c951ceb` continuam proibidos.

## 3. Prova causal obrigatória na imagem real

Teste de string do Dockerfile ou inspeção do builder não basta. Antes do commit
e novamente sobre o commit final:

1. construir a imagem pelo contexto raiz e Dockerfile reais;
2. executá-la como `10001:10001`, `--network none`, read-only e sem secret;
3. importar os módulos instalados em `/app/src`;
4. exigir que as cinco constantes de schema resolvam para paths absolutos sob
   `/ops` e que todos sejam arquivos regulares legíveis;
5. exigir ausência de `/app/ops`;
6. baixar fora do container somente os três assets publicados da release
   `v0.1.0`, montá-los read-only e executar dentro da imagem
   `validate_release_bundle` sobre os bytes reais;
7. exigir retorno do manifest `v0.1.0`, seis componentes e sidecar/metadata
   válidos;
8. executar ao menos uma validação causal para cada schema de outcome de
   deploy e rollback usando fixtures canônicas, sem dispatch;
9. confirmar zero rede durante as validações internas e remover image/temp
   locais de forma dirigida.

Adicionar mutantes que rejeitem:

- destino `/app/ops`;
- ausência de qualquer um dos cinco schemas;
- path relativo ou fallback;
- cópia ampla de `ops/` além da allowlist do contexto;
- schema extra, symlink ou modo gravável quando observável no runtime.

## 4. Gates e publicação

Depois da prova causal:

- release_control tests;
- oito suítes canônicas;
- todos os validadores registrados;
- secret scan `clean`, `unsupported=0`;
- `catalog:valid` e `git diff --check`;
- um commit causal e um push fast-forward;
- CI e Publish Candidate do novo SHA verdes;
- uma execução nova de `Publish Release Control Image`, sem inputs;
- quatro jobs, artifacts, manifesto, sidecar, outcome e package version verdes;
- novo immutable ref vinculado ao SHA/run/attempt.

Não repetir ou rerodar CI/candidato/imagem dos SHAs `9699214` ou `c951ceb`.
Preservar todas as versões anteriores do package.

## 5. Checkpoint de retomada

```text
HEAD/origin/main/remoto  c951ceb7f5525505a4d1fe12d04fc9a4ad50fdff
stage                    vazio
task SHA-256             0ca6665a5efc26f912d39f563132ee6c90d4270526ab0de5959b61938e70422f
correction-01 SHA-256    8ed558507e4959ceb36bc60d6fb6e23b732ee5ec2c972303d5750d4d64183630
report SHA-256           fdc33c674340f8c13c7139724d4d197d65f76796e48d8855600f06ab29d04062
```

Evidência remota herdada:

```text
CI                    30938485570, success
Publish Candidate     30939132677, success
Image workflow        30939873305, success
imagem anterior       ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:a12badc37a97c1dab9c7ff8f787320b978a4a76f27b19ecce6843cf4bd0f69a5
```

Identidade preservada:

```text
App ID                4487372
slug                  emporio-deployer-1315264421
installation          151259606
bot actor ID          313092947
DEPLOYER_ACTOR_IDS     313092947
```

Revalidar com JWT/token somente em memória e fingerprint, sem abrir a página de
Apps, criar App, gerar nova chave, reinstalar ou reescrever a variável. Se a
identidade divergir, parar sem criar substituta.

VPS esperada no baseline restaurado:

- 37/39 containers, 26 volumes, 18 redes, 31 imagens;
- porta 8180 livre;
- root operacional, `/etc/emporio`, user/unit e recursos Compose ausentes;
- control root da S43 íntegro;
- zero deploy/rollback e `v0.1.0` inalterada.

## 6. Nova preparação e tentativa

Somente depois de imagem final publicada e identidade revalidada:

1. recriar os recursos VPS da S44 a partir do baseline;
2. reutilizar o PEM local preservado e gerar novos DB password/pepper sem
   exibição;
3. usar exclusivamente:

```text
PostgreSQL
postgres@sha256:589f3b24f30e60a2b33f79543ed51c8f897589bcde5c59f4dc0e814551eeeb0f

release_control
<novo immutable ref produzido por esta correction>
```

4. validar Compose real em quiet mode;
5. pull dos dois digests, sem login/build/tag na VPS;
6. executar a próxima tentativa pela unit systemd;
7. provar migrations, containers healthy, live/ready 200, sync releases e
   deployments sem drift, snapshot `v0.1.0`, zero current installation e zero
   operação/dispatch;
8. executar o restart controlado e repetir as provas;
9. preservar os recursos estáveis como resíduo intencional da S44.

Não chamar API autenticada, POST, deploy ou rollback.

## 7. Convergência sem nova microcorreção

Para evitar nova parada por limite artificial, se a prova real revelar outro
defeito causal estritamente no startup/readiness do mesmo control plane, ficam
autorizados até dois commits/publicações/tentativas adicionais dentro da S44,
desde que cada um:

- tenha causa distinta reproduzida por teste;
- permaneça em `release_control/`, Dockerfile, pacote operacional, workflow da
  imagem e seus testes/validadores;
- execute matriz completa, CI, candidato e publicação imutável;
- reverta a tentativa falha antes de reiniciar;
- não altere App, permissões, deploy/rollback workflows, stack comercial,
  control root, Nginx/TLS, backup, swap ou host compartilhado.

Parar somente se a solução exigir nova decisão arquitetural, permissão externa,
outro host/repositório ou ação fora dessas fronteiras. Não transformar defeito
causal coberto em nova slice.

## 8. Autorização cumulativa

A delegação deve conter literalmente:

```text
Autorizo a correction-02 da S44 a criar o commit adicional que instala os cinco schemas do release control no path runtime absoluto /ops, publicar e validar a nova imagem imutável e refazer a preparação/start do control plane a partir do baseline revertido. Autorizo também até dois ciclos causais adicionais, somente dentro da fronteira de startup/readiness da S44, sem deploy, rollback ou stack comercial. Reutilize a App deployer, instalação, PEM e DEPLOYER_ACTOR_IDS já validados; não os recrie.
```

Task, correction-01 e correction-02 formam autoridade cumulativa. As demais
proibições e critérios de aceite permanecem.

## 9. Relatório e terminal

Não criar novo relatório. Acrescentar `Retomada correction-02` ao relatório
contínuo, mantendo-o não rastreado e fora do stage. Registrar hashes, prova
causal em container, commit/runs/digest, revalidação da identidade, nova
preparação, health/sync/restart, tentativas e negativos.

O executor não aceita S44 e não cria S45.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — control plane deployer implantado e estável; aguardando aceite e Gate C de prontidão
```

Na primeira causa fora da autoridade:

```text
BLOCKED — S44 correction-02 interrompida fail-closed na primeira causa técnica
```
