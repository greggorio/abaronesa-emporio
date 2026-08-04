# Releases globais offline

> **Estado:** contrato, gerador offline e runtime publisher local implementados;
> nenhuma publicação/tag/GitHub Release real ou deploy foi executado.

## Candidato e release global

O candidato é uma avaliação completa dos seis componentes comerciais e, após
a integração verde, registra `deployable: true`. Essa propriedade o torna
elegível à promoção, mas não o transforma em artefato consumível pelo deployer:
somente a release global `kind: global-release`, também `deployable: true`, é
implantável. A promoção copia integralmente o candidato validado e acrescenta
SemVer, identidade da publicação, descrição, changelog e fingerprints das
migrations.

O usuário escolhe somente um candidato íntegro e o tipo de incremento
`MAJOR`, `MINOR` ou `PATCH`. Ele não escolhe componentes, imagens, digests,
paths ou a versão final. O BOM sempre preserva, nesta ordem:

1. `backend`;
2. `website_back`;
3. `frontend`;
4. `website_front`;
5. `whatsapp_service`;
6. `gateway`.

Cada componente é copiado sem reduzir seus labels, checks ou referência
imutável. Imagens herdadas mantêm o digest, o commit, o run e o
`originCandidateId` do candidato que as originou.

## SemVer determinístico

O único formato aceito é `vMAJOR.MINOR.PATCH`, sem pre-release, metadata ou
zeros à esquerda. A maior release anterior válida é a base:

| Base | MAJOR | MINOR | PATCH |
|---|---|---|---|
| nenhuma (`v0.0.0`) | `v1.0.0` | `v0.1.0` | `v0.0.1` |
| `v2.7.9` | `v3.0.0` | `v2.8.0` | `v2.7.10` |

A ordenação é numérica. Versões duplicadas, overflow ou reutilização de um
candidato falham fechados. Reserva concorrente e revalidação remota serão
responsabilidade da S14.

## Fingerprints Flyway

A release inventaria os dois roots:

```text
backend/src/main/resources/db/migration
website_back/src/main/resources/db/migration
```

Cada SQL versionado registra path relativo, versão Flyway textual e SHA-256
dos bytes exatos. `migrationSetSha256` é o SHA-256 do JSON canônico da lista
ordenada. Assim, uma comparação entre dois manifestos detecta qualquer
mudança no conjunto sem inspecionar imagens ou interpretar SQL.

O contrato não alega reversibilidade de migration:

- `required_on_change`: mudança do fingerprint exige backup;
- `restore_required`: rollback de dados exige restauração.

## Bundle e integridade

O gerador cria exatamente:

```text
release.json
release.json.sha256
metadata.json
```

`release.json` e metadata usam JSON canônico UTF-8 com LF final. O sidecar
contém o SHA-256 cru do manifesto. Releases anteriores só participam da
resolução quando os três arquivos, canonicalidade, schema, semântica e
bindings são válidos.

A primeira escrita é atômica e não sobrescreve destino existente. O helper
interno de overwrite, usado apenas por testes e futuras integrações
controladas, restaura integralmente o bundle anterior se staging, rename ou
verificação falhar. O CLI desta slice não expõe overwrite.

## Uso offline

Validar o exemplo integral:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py validate \
  --manifest ops/releases/examples/global-release.example.json
```

Gerar uma primeira release fictícia em um diretório novo:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py generate \
  --candidate ops/releases/examples/candidate-manifest.example.json \
  --candidate-artifact-id 300 \
  --candidate-artifact-digest sha256:3333333333333333333333333333333333333333333333333333333333333333 \
  --request ops/releases/examples/release-request.example.json \
  --published-at 2026-07-29T15:00:00Z \
  --workflow-run-id 400 \
  --workflow-attempt 1 \
  --actor greggorio \
  --actor-id 1 \
  --output /tmp/emporio-global-release-example
```

Para cada release anterior, acrescentar
`--existing-release <bundle/release.json>`. A ordem desses argumentos não
altera o resultado.

## Manutenção

- Mudança no request público deve permanecer compatível com o OpenAPI
  publisher S06.
- Mudança no shape do candidato ou componente exige atualização coordenada do
  schema global, gerador, exemplo e testes.
- Nova migration altera deliberadamente o exemplo e seus fingerprints após
  revisão.
- Novo componente comercial exige primeiro alterar o catálogo e o contrato do
  candidato; a release nunca aceita um sétimo componente isoladamente.
- Mudança de schema requer novo `schemaVersion`; não relaxar manifestos
  antigos silenciosamente.

## Fronteira de publicacao

O contrato offline desta pagina permanece autoritativo para SemVer, BOM e
fingerprints. A S14 adiciona `publish-release.yml`, schemas de plan/outcome e
helper com transporte injetavel. GitHub Releases e a cadeia de tags sao a
historia autoritativa; candidato ja publicado e reconciliado sem mutacao.

A ordem mutavel e draft, tres assets verificados, tag lightweight e
publicacao, com compensacao restrita aos recursos criados pelo proprio run.
Consulte [RELEASE_PUBLICATION.md](../ci/RELEASE_PUBLICATION.md). O workflow
ainda nao foi executado remotamente; runtime publisher, UI e deploy continuam
fora desta fronteira.

## Planejamento offline de implantação

A S18 posiciona um contrato determinístico entre a release global publicada e
uma futura execução operacional. O planner recebe o manifesto alvo e,
opcionalmente, o estado confirmado mais seu manifesto histórico; decide os
seis componentes por digest, calcula migrations estritamente forward-only,
backup e próximo estado, e materializa um bundle verificável.

O operador não seleciona componentes, imagens, bancos ou ordem. O arquivo
`installed-state.next.json` é apenas intenção não reconciliada; ele não afirma
sucesso e não substitui o estado confirmado. Nenhum Docker, banco, backup,
migration, deploy ou rollback é executado nessa fase.

O contrato, os comandos e a fronteira estão em
[PLANO_IMPLANTACAO.md](./PLANO_IMPLANTACAO.md).
