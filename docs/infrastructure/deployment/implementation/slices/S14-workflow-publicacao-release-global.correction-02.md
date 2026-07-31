# S14 — Correção causal terminal 02

> **Estado:** `IN_PROGRESS — correção causal terminal 02 requerida`
>
> **Contratos anteriores:** task S14 imutável e correção causal 01 concluída
>
> **Relatório a atualizar:** `S14-workflow-publicacao-release-global.report.md`
>
> **Próxima slice:** S15 continua bloqueada.

## 1. Responsabilidade e finalidade

A correção 01 está implementada conforme seu contrato. Esta emenda existe
porque a revisão terminal encontrou divergências preexistentes do contrato-base
que o orquestrador deveria ter incluído na primeira correção:

1. `lookup`, `release_lookup` e `tag_lookup` convertem qualquer
   `CalledProcessError` em “ausente”; 401, 403, 409, 429, 5xx e falha de
   transporte não são ausência;
2. respostas de Git refs são usadas sem validar `ref`, URL, object type, SHA e
   object URL;
3. falhas de subprocesso podem escapar do CLI como traceback, contrariando
   códigos estáveis e logs sanitizados;
4. o checkout do job `outcome` usa `fetch-depth: 1`, embora a task determine
   `fetch-depth: 0` sempre.

A correção 01 também autorizou por engano
`docs/infrastructure/deployment/RELEASE_PUBLICATION.md`. O caminho canônico
definido pela task é
`docs/infrastructure/deployment/ci/RELEASE_PUBLICATION.md`. A responsabilidade
por esse erro de escopo é do orquestrador.

Esta é a auditoria terminal do transporte S14. Não há decisão arquitetural
delegada ao executor.

## 2. Escopo autorizado

Alterar somente:

- `.github/workflows/publish-release.yml`;
- `tools/releases/release_publication.py`;
- `tools/releases/validate_release_workflow.py`;
- `tools/releases/tests/test_release_publication.py`;
- `docs/infrastructure/deployment/ci/RELEASE_PUBLICATION.md`;
- `docs/infrastructure/deployment/implementation/slices/S14-workflow-publicacao-release-global.report.md`.

Não alterar:

- task S14 ou correção 01;
- tracker/README de implementação;
- schemas, exemplos e contratos S13;
- `ci.yml` ou `publish-candidate.yml`;
- Dockerfiles, Compose, migrations ou código comercial;
- Git, GitHub, GHCR, VPS ou produção;
- S15.

## 3. Resultado HTTP tipado e fail-closed

### 3.1 Transporte JSON

Refatorar `GhTransport.api` para executar `gh api --include` com array de
argumentos, `shell=False` implícito e `check=False`.

O parser deve:

- receber bytes, nunca imprimir stdout/stderr;
- aceitar separador de headers `\r\n\r\n` ou `\n\n`;
- limitar headers a 64 KiB e body JSON a 4 MiB;
- exigir uma única status line inicial no formato
  `HTTP/<versão> <status-decimal-de-3-dígitos> ...`;
- aceitar como sucesso somente status 2xx e return code zero;
- exigir body JSON válido quando o status de sucesso não for 204;
- exigir body vazio no status 204;
- nunca incluir body, stderr, token, descrição ou changelog no erro.

Criar erro interno tipado:

```text
RemoteHttpError(code="REMOTE_HTTP_ERROR", status=<inteiro>)
```

Regras:

- status HTTP não 2xx produz `RemoteHttpError`;
- processo sem status parseável, timeout, sinal ou return code incompatível
  produz `PublicationError("REMOTE_TRANSPORT_FAILED")`;
- resposta 2xx com headers/body inválidos produz
  `PublicationError("REMOTE_RESPONSE_INVALID")`;
- nenhum `CalledProcessError`, stdout ou stderr bruto pode escapar ao CLI.

### 3.2 GET opcional

Criar uma única primitiva `optional_get(endpoint)`:

- retorna o JSON validado em 2xx;
- retorna `None` exclusivamente para `RemoteHttpError.status == 404`;
- propaga de forma sanitizada qualquer outro status ou falha.

Usar exclusivamente essa primitiva em:

- `lookup(tag)`;
- `release_lookup(release_id)`;
- `tag_lookup(tag)`.

É proibido capturar `subprocess.CalledProcessError` nesses métodos.

Consequências obrigatórias:

- 404 pode provar ausência;
- 401, 403, 409, 429, 5xx, timeout ou resposta inválida nunca provam ausência;
- falha diferente de 404 antes do draft resulta em zero mutações;
- falha diferente de 404 durante a prova final de compensação resulta em
  `PUBLICATION_COMPENSATION_FAILED`.

## 4. Status HTTP por operação

Além do intervalo 2xx do transporte, cada mutação exige:

```text
POST /releases                         = 201
POST /git/refs                         = 201
PATCH /releases/<id>                   = 200
DELETE /releases/<id>                  = 204
DELETE /git/refs/tags/<tag>            = 204
```

GETs JSON exigem 200.

Uploads continuam usando `uploads.github.com`; return code diferente de zero,
timeout ou sinal deve virar `PublicationError("REMOTE_UPLOAD_FAILED")`, sem
stderr bruto. O upload permanece reconciliado por GET e comparação posterior.

Downloads binários continuam limitados durante streaming. Falha de criação do
processo, timeout/sinal, leitura ou return code deve produzir somente
`PublicationError("REMOTE_DOWNLOAD_FAILED")`.

## 5. Shape canônico de Git ref lightweight

Criar `validate_tag_ref(value, tag, expected_sha=None)`.

Exigir exatamente os seguintes bindings relevantes:

```text
ref=refs/tags/<SemVer>
url=https://api.github.com/repos/greggorio/abaronesa-emporio/git/refs/tags/<SemVer>
object.type=commit
object.sha=<40 hex minúsculos>
object.url=https://api.github.com/repos/greggorio/abaronesa-emporio/git/commits/<object.sha>
```

Se `expected_sha` for informado, `object.sha` deve ser idêntico.

Aplicar antes de usar qualquer ref em:

- história retornada por `matching-refs/tags/v`;
- resposta do POST de criação da tag;
- `tag_lookup`;
- `tag_points_to`;
- `final_state`;
- `delete_owned_tag`;
- provas finais de compensação.

O POST de criação da tag só retorna sucesso depois de validar a resposta como
ref lightweight exata. Se o POST criar a ref mas sua resposta for inválida, o
fluxo já marcado como `tag_attempted` deve reconciliar por GET tipado e
compensar somente se o ref canônico apontar para o SHA esperado.

Nenhum objeto `type=tag` anotado é aceito.

## 6. Falhas locais e sanitização do CLI

Encapsular todos os subprocessos externos do helper:

- `git fetch`;
- `git cat-file`;
- `git merge-base`;
- `git rev-parse`;
- `git archive`;
- `gh api`;
- upload por `gh api`.

Mapeamento:

```text
git fetch/ancestry/archive/checkout = GIT_CONTEXT_INVALID
gh JSON/status/transporte           = códigos das Seções 3 e 4
upload                              = REMOTE_UPLOAD_FAILED
download                            = REMOTE_DOWNLOAD_FAILED
```

`tarfile.TarError`, `zipfile.BadZipFile`, EOF/arquivo inválido e erros de
extração não podem produzir traceback; mapear para o código contextual já
usado pelo bundle/archive ou `ARCHIVE_ENTRY_INVALID`.

O `main` continua emitindo somente:

```text
release-publication:invalid:<CODIGO_ESTAVEL>
```

em stderr, com exit 3. Nenhuma exceção esperada desta superfície pode escapar
com traceback.

Não alterar mensagens de sucesso nem imprimir dados livres.

## 7. Workflow e validador

Em todos os quatro jobs de `publish-release.yml`:

```yaml
fetch-depth: 0
persist-credentials: false
```

Alterar o job `outcome` de 1 para 0.

O validador deve exigir `fetch-depth == "0"` para todo checkout. Remover a
aceitação de `"1"`.

Adicionar mutante que troca somente o checkout de `outcome` para
`fetch-depth: 1` e exigir falha do validador.

Não instalar dependências no workflow. A regra original de imports do runner
permanece.

## 8. Documentação canônica

Atualizar somente
`docs/infrastructure/deployment/ci/RELEASE_PUBLICATION.md` com:

- `notes.md` como body exato e não como asset;
- validação de IDs/assets/refs antes de endpoints;
- 404 como única ausência;
- outros status/falhas como fail-closed;
- códigos estáveis e ausência de traceback/body nos logs;
- revalidação integral antes do outcome;
- fronteira ainda não executada remotamente.

## 9. Provas causais obrigatórias

Adicionar testes independentes para:

1. parser aceita resposta `HTTP/2.0 200` + JSON;
2. parser aceita `HTTP/1.1 204` + body vazio;
3. 404 tipado retorna `None` somente em `optional_get`;
4. 401, 403, 409, 429 e 500 nunca retornam `None`;
5. return code sem status, status malformado, headers >64 KiB, body >4 MiB,
   204 com body e 2xx com JSON inválido falham sanitizados;
6. lookup 403 antes do draft produz zero POST/upload/tag/PATCH/DELETE;
7. falha 500 na prova final de compensação produz
   `PUBLICATION_COMPENSATION_FAILED`;
8. ref lightweight canônico é aceito;
9. mutantes de `ref`, ref URL, object type, SHA e object URL são rejeitados;
10. história com uma ref inválida falha antes de gerar snapshot;
11. resposta inválida do POST da tag é reconciliada e compensada; ref
    preexistente/divergente nunca é apagada;
12. upload/download/subprocesso Git falhos resultam nos códigos prescritos,
    exit 3, sem traceback, stdout/stderr bruto ou dado livre;
13. todos os checkouts possuem depth 0 e o mutante depth 1 falha;
14. documentação canônica contém as seis garantias da Seção 8;
15. os 205 testes anteriores continuam verdes.

Mocks de processo devem fornecer status line, headers, body, return code e
stderr sintético. Nenhum teste usa rede.

## 10. Matriz final

Executar e registrar comando, exit, contagem e interpretação:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py validate \
  --manifest ops/releases/examples/global-release.example.json

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py validate \
  --require-release-ready

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
```

Executar actionlint nos três workflows pelo digest fixado. Remover somente a
imagem efêmera se ausente no estado inicial.

Não executar Maven, NPM, Docker build, Compose, GitHub, GHCR ou VPS.

## 11. Relatório e estado

Acrescentar ao relatório S14:

- resposta item a item às 15 provas;
- tabela de status HTTP e comportamento;
- shape positivo/negativo de Git ref;
- demonstração de zero mutação em status não 404;
- demonstração de erro sanitizado sem traceback/body;
- arquivos alterados;
- comandos, exits e contagens;
- estado Git, workflows, caches e actionlint;
- confirmação de nenhum acesso remoto real.

Estado final:

```text
IN_PROGRESS — aguardando revisão terminal do orquestrador
```

Não declarar `ACCEPTED` e não criar S15.
