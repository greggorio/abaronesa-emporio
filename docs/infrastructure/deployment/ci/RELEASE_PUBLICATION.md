# Publicacao transacional de release global

`publish-release.yml` e a fronteira mutavel, ainda nao executada remotamente,
entre candidato validado e release global implantavel. CI somente testa;
`publish-candidate.yml` produz candidato nao implantavel; o workflow de release
valida novamente candidato e historia, calcula SemVer e publica. O outcome e o
comprovante para o futuro reconciliador.

## Entrada e autorizacao

O disparo e exclusivamente manual (`workflow_dispatch`) em `main`. A futura UI
fornece `operation_id`, `candidate_id`, `version_bump`, `description` e
`changelog`. Ela nao controla versao final, commit, artifact, componente,
imagem, digest, repositorio, URL, path ou comando.

A repository variable `RELEASE_PUBLISHER_ACTOR_IDS` devera conter CSV estrito
de um a vinte IDs GitHub positivos, sem espacos, wildcard ou duplicidade. Nao
ha default e nenhum ID real e documentado. Identidade do dispatch, sender,
run, repository, ref e ancestry sao revalidados antes de downloads. Somente o
job de publicacao recebe `contents: write`; os demais usam leitura.

## Historia, idempotencia e concorrencia

GitHub Releases e tags SemVer sao a historia autoritativa. Cada release possui
exatamente `release.json`, `release.json.sha256` e `metadata.json`, ligados a
uma tag lightweight no `sourceCommit`. A primeira tem
`previousRelease: null`; as demais apontam para a anterior imediata.

O snapshot inclui IDs da release, tag/commit, digest do manifesto e IDs dos
tres assets. A concorrencia global `emporio-release-publication` nao cancela a
operacao anterior. Candidato ausente gera `publish`; candidato ja presente
retorna `already_published` sem tag, draft, asset ou release nova.

## Publicacao e compensacao

O job mutavel revalida handoff, candidato e snapshot; cria primeiro um draft,
envia os tres assets, baixa e compara seus bytes, revalida a historia, cria a
tag e somente entao publica o draft. O estado final remoto e conferido.
`notes.md` e usado byte a byte como body da GitHub Release e nao e publicado
como asset.

IDs de runs, artifacts, releases e assets, assim como o shape completo das refs
lightweight, sao validados antes que controlem qualquer endpoint. Somente HTTP
404 comprova ausencia; outros status, falhas de transporte, timeout ou resposta
invalida fecham o fluxo com erro.

Depois da criacao do draft, qualquer falha tenta remover apenas o draft cujo ID
foi devolvido a este processo e a tag criada por este run quando ainda aponta
ao commit esperado. Binding divergente nunca e apagado. Se a ausencia final
nao puder ser provada, o erro e `PUBLICATION_COMPENSATION_FAILED`.

O artifact `release-publication-outcome` so existe para estado remoto
comprovado ou `already_published` historico. Run vermelho sem outcome e
evidencia de falha; nao existe outcome verde sintetico.
Antes desse outcome, release, assets, ref, commit e seus bindings sao
integralmente revalidados. Falhas do helper geram somente codigos estaveis e
sanitizados, sem traceback, body remoto ou stderr bruto nos logs.

## Validacao local

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py validate \
  --manifest ops/releases/examples/global-release.example.json
```

Esta fronteira continua configurada e validada apenas localmente: o workflow
ainda nao foi executado no GitHub e nenhuma release, tag ou artifact remoto foi
criado.
