# S14 — Ajuste corretivo terminal 02-A

> **Estado:** `IN_PROGRESS — dois defeitos da correção 02`
>
> **Natureza:** correção de implementação contra contrato já escrito
>
> **S15:** continua bloqueada.

## 1. Escopo

Alterar somente:

- `tools/releases/release_publication.py`;
- `tools/releases/tests/test_release_publication.py`;
- `docs/infrastructure/deployment/implementation/slices/S14-workflow-publicacao-release-global.report.md`.

Não alterar workflow, validador, documentação operacional, contratos
anteriores, tracker ou S15.

## 2. Return code incompatível

`parse_http_response` deve avaliar compatibilidade entre status e return code
antes de produzir `RemoteHttpError`.

Tabela exata:

| Status parseado | Return code | Resultado |
|---|---:|---|
| 2xx | 0 | continuar validação da resposta |
| 2xx | diferente de 0 | `REMOTE_TRANSPORT_FAILED` |
| não 2xx | diferente de 0 e positivo | `RemoteHttpError(status)` |
| não 2xx | 0 | `REMOTE_TRANSPORT_FAILED` |
| qualquer | negativo | `REMOTE_TRANSPORT_FAILED` |

Consequentemente, `HTTP 404` com return code zero nunca representa ausência.
Somente `HTTP 404` acompanhado de return code positivo do processo pode chegar
a `optional_get` como `RemoteHttpError(404)`.

Adicionar provas independentes para as cinco linhas relevantes da tabela,
incluindo explicitamente:

```text
HTTP/1.1 404 + returncode=1 -> optional_get pode retornar None
HTTP/1.1 404 + returncode=0 -> REMOTE_TRANSPORT_FAILED
HTTP/1.1 500 + returncode=0 -> REMOTE_TRANSPORT_FAILED
HTTP/1.1 200 + returncode=1 -> REMOTE_TRANSPORT_FAILED
returncode=-9 -> REMOTE_TRANSPORT_FAILED
```

## 3. Ownership da tag após POST

Um status HTTP diferente de `201` nunca prova que a tag foi criada por este
run, mesmo que uma reconsulta encontre o mesmo nome e SHA.

Implementar o seguinte estado:

```text
tag_attempted=true  imediatamente antes do POST
tag_owned=true      somente após POST 201 e prova canônica
```

Adicionar erro interno tipado:

```text
RemoteResponseError(
  code="REMOTE_RESPONSE_INVALID",
  status=<status 2xx já parseado>
)
```

`parse_http_response` deve usar esse tipo quando status 2xx possui
headers/body/JSON inválidos. `GhTransport.api` deve usar o mesmo tipo quando o
status 2xx válido diverge de `expected_status`. O CLI continua exibindo somente
o `code`, nunca o status/body.

`GhTransport.create_tag(tag, sha)` continua retornando normalmente apenas
quando ownership foi provado. Em `publish_transaction`:

- definir `tag_owned=false` antes da tentativa;
- definir `tag_attempted=true` imediatamente antes da chamada;
- definir `tag_owned=true` somente depois que `create_tag` retornar;
- remover a promoção de ownership por `tag_points_to` no bloco de exceção;
- chamar `delete_owned_tag` somente quando `tag_owned=true`.

Regras:

1. POST 201 com resposta canônica exata:
   - `tag_owned=true`;
   - fluxo continua.
2. POST 201 com body/shape inválido:
   - `create_tag` captura somente
     `RemoteResponseError(status=201)` e executa um GET tipado;
   - se a ref canônica exata apontar para o SHA esperado, considerar
     a criação reconciliada e retornar normalmente;
   - ausente, divergente ou erro não prova ownership e falha sem DELETE.
3. POST com 409, 422, outro não 2xx ou falha de transporte:
   - não capturar como `RemoteResponseError(status=201)`;
   - `tag_owned=false`;
   - não reconciliar para adquirir ownership;
   - nunca apagar a tag encontrada depois, mesmo que nome e SHA coincidam;
   - a presença final da tag deve produzir
     `PUBLICATION_COMPENSATION_FAILED`.
4. Somente `tag_owned=true` autoriza `delete_owned_tag`.
5. `tag_points_to` pode provar estado remoto, mas não transforma tag não
   possuída em tag própria.

Não usar apenas `tag_attempted` + igualdade de SHA como ownership.

## 4. Provas causais

Adicionar testes independentes para:

1. matriz de compatibilidade status/return code da Seção 2;
2. POST 422 com tag preexistente no mesmo SHA:
   - zero `DELETE /git/refs/tags/...`;
   - resultado `PUBLICATION_COMPENSATION_FAILED`;
3. POST 409 com tag preexistente no mesmo SHA possui o mesmo comportamento;
4. falha de transporte ambígua seguida de GET com mesma tag/SHA não autoriza
   DELETE;
5. POST 201 com resposta canônica marca ownership;
6. POST 201 com body inválido e GET canônico exato recupera ownership;
7. POST 201 com body inválido e GET divergente/ausente não autoriza DELETE;
8. os 220 testes anteriores permanecem verdes.

Mocks devem registrar método, endpoint, status e DELETEs. Nenhum teste usa
rede.

## 5. Validação e relatório

Executar:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  tools/releases/tests/test_release_publication.py

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py
```

Não repetir actionlint porque nenhum workflow pode ser alterado.

Acrescentar ao relatório:

- resposta às oito provas;
- contagem anterior e nova;
- eventos que demonstram zero DELETE nos casos não possuídos;
- comandos, exits e estado protegido.

Estado final:

```text
IN_PROGRESS — aguardando aceite terminal do orquestrador
```

Não declarar `ACCEPTED` e não criar S15.
