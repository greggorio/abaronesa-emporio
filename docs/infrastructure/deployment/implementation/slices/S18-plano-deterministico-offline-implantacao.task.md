# S18 — Plano determinístico offline de implantação

> **Estado:** `ACCEPTED — 29/07/2026`  
> **Tipo:** contrato de deploy, planejamento offline e segurança causal  
> **Executor previsto:** CLI  
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Dependências:** S01 a S17 `ACCEPTED`  
> **Relatório de saída:** `S18-plano-deterministico-offline-implantacao.report.md`

## Instrução para delegação

Execute integralmente esta slice. Leia, nesta ordem:

1. esta task inteira;
2. a revisão terminal da S17;
3. `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`;
4. `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
5. `docs/infrastructure/deployment/release-control/RELEASES.md`;
6. `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`;
7. `docs/infrastructure/deployment/release-control/contracts/state-machines.yml`;
8. `ops/releases/global-release.schema.json`;
9. `ops/releases/examples/global-release.example.json`;
10. `ops/releases/components.yml`;
11. `ops/compose/compose.prod.yml`;
12. `tools/releases/global_release.py`.

O executor implementa as decisões abaixo. Não escolha modelo de estado
instalado, ordem dos componentes, regra de migração, política de downgrade,
nomes de variáveis, conteúdo do bundle, atomicidade ou códigos de saída.

Não altere esta task nem o tracker:

```text
docs/infrastructure/deployment/implementation/README.md
```

## 1. Resultado observável

Ao final, uma ferramenta Python exclusivamente offline recebe:

- o manifesto canônico da release global alvo;
- opcionalmente o estado reconciliado da instalação atual;
- o Compose canônico de produção;
- um caminho de saída ainda inexistente.

Ela valida integralmente as entradas e materializa atomicamente um bundle de
planejamento contendo:

```text
manifest.json
compose.prod.yml
release.env
deployment-plan.json
installed-state.next.json
bundle.sha256
```

O plano decide sozinho:

- quais dos seis componentes ficam em `KEEP` ou passam por `UPDATE`;
- quais imagens imutáveis serão usadas;
- se existem migrations novas em cada banco;
- se backup é obrigatório;
- quais serviços serão atualizados e em qual ordem;
- qual será o próximo estado instalado após sucesso.

O usuário não seleciona componentes, imagens, dependências, bancos ou ordem.
Nenhuma operação Docker, GitHub, GHCR, SSH, banco ou produção ocorre na S18.

## 2. Fronteira autorizada

### 2.1 Criar

```text
ops/deploy/schemas/installed-state.schema.json
ops/deploy/schemas/deployment-plan.schema.json
ops/deploy/examples/installed-state.example.json
ops/deploy/examples/deployment-plan.example.json
tools/deploy/deployment_plan.py
tools/deploy/validate_deployment_plan.py
tools/deploy/tests/test_deployment_plan.py
tools/deploy/tests/test_deployment_plan_contract.py
docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md
docs/infrastructure/deployment/implementation/slices/S18-plano-deterministico-offline-implantacao.report.md
```

Crie os diretórios pais apenas quando ausentes. Não crie `__init__.py`; os
testes devem carregar o módulo pelo caminho ou ajustar `sys.path` localmente.

### 2.2 Alterar somente

```text
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/RELEASES.md
docs/infrastructure/deployment/release-control/api/deployer.openapi.yml
tools/releases/release_control_contract.py
tools/releases/tests/test_release_control_contract.py
```

As duas superfícies Python acima só podem ser alteradas para validar e testar
a emenda exata do `DeploymentPlan.sourceRelease` definida na seção 8. Se o
validador atual já aceitar a emenda sem mudança, preserve
`release_control_contract.py` e acrescente apenas a prova causal ao teste.

### 2.3 Ler sem alterar

```text
ops/releases/global-release.schema.json
ops/releases/components.yml
ops/compose/compose.prod.yml
docs/infrastructure/deployment/release-control/contracts/state-machines.yml
tools/releases/global_release.py
```

## 3. Fora de escopo

Não:

- alterar máquina de estados, catálogo, manifesto global ou Compose;
- alterar qualquer parte do OpenAPI além de
  `DeploymentPlan.properties.sourceRelease`;
- implementar runtime deployer, endpoint, banco ou UI de produção;
- criar shell script operacional, workflow ou serviço systemd;
- executar `docker`, `docker compose`, `podman`, `psql`, `pg_dump` ou Flyway;
- acessar GitHub, GHCR, VPS, DNS ou produção;
- baixar ou publicar imagens;
- criar diretórios sob `/opt`;
- criar ou trocar symlinks `current`/`previous`;
- implementar backup, restore, health check, smoke test ou rollback;
- decidir downgrade por conta própria;
- adicionar dependência Python;
- criar S19;
- executar `git add`, commit, tag ou push.

## 4. Entradas canônicas

### 4.1 Manifesto alvo

O manifesto alvo deve passar por:

```python
tools/releases/global_release.py::validate_release
```

e pelo JSON Schema:

```text
ops/releases/global-release.schema.json
```

Erros em qualquer uma das duas validações interrompem o processo antes de
criar o diretório final.

O array `components` deve conter exatamente uma ocorrência de cada ID, nesta
ordem:

```text
backend
website_back
frontend
website_front
whatsapp_service
gateway
```

O array `databases` deve conter exatamente:

```text
erp
website
```

com `erp.ownerComponent=backend` e
`website.ownerComponent=website_back`.

### 4.2 Estado instalado atual

O argumento `--current` é opcional:

- ausente: primeira implantação;
- presente: o arquivo deve existir, ser arquivo regular, ter no máximo
  `2 MiB`, ser UTF-8 sem BOM e satisfazer integralmente
  `installed-state.schema.json`.

Não use `null`, objeto parcial ou arquivo vazio para representar primeira
implantação.

`installed-state.schema.json` usa JSON Schema draft 2020-12, fecha propriedades
adicionais e exige exatamente:

```json
{
  "schemaVersion": 1,
  "kind": "installed-state",
  "environment": "production",
  "release": "v1.2.3",
  "sourceCommit": "<40 hex minúsculos>",
  "manifestSha256": "sha256:<64 hex minúsculos>",
  "plannedAt": "2026-07-29T11:50:00Z",
  "installedAt": "2026-07-29T12:00:00Z",
  "reconciled": true,
  "components": [
    {
      "id": "backend",
      "immutableRef": "ghcr.io/greggorio/abaronesa-emporio-backend@sha256:<64 hex>"
    }
  ],
  "databases": [
    {
      "id": "erp",
      "ownerComponent": "backend",
      "migrationSetSha256": "sha256:<64 hex>",
      "migrations": [
        {
          "version": "1",
          "path": "backend/src/main/resources/db/migration/V1__init.sql",
          "sha256": "sha256:<64 hex>"
        }
      ]
    }
  ]
}
```

No exemplo acima, os arrays foram abreviados apenas para leitura. O schema e
os arquivos reais exigem seis componentes e dois bancos, com os IDs e a ordem
canônica definidos nesta task. Cada migration contém exatamente `version`,
`path` e `sha256`.

O schema define dois estados válidos:

- estado confirmado: `reconciled=true`, `installedAt` em UTC e
  `installedAt >= plannedAt`;
- intenção: `reconciled=false` e `installedAt=null`.

O argumento `--current` aceita exclusivamente estado confirmado. A intenção
existe apenas como `installed-state.next.json` dentro do bundle.

O estado instalado não contém segredo, valores de `.env`, nomes de containers,
IDs Docker nem bearer tokens.

### 4.3 Compose

`--compose` é obrigatório e, nesta slice, deve resolver exatamente para o
arquivo regular:

```text
ops/compose/compose.prod.yml
```

após `Path.resolve()`. Symlink, outro caminho ou arquivo com mais de `1 MiB`
deve falhar. A ferramenta lê seus bytes, mas não os transforma.

## 5. Validações cruzadas fail-closed

Antes de calcular o plano:

1. valide schemas e invariantes sem tolerar campos extras;
2. rejeite IDs duplicados, ausentes ou fora de ordem;
3. confirme `immutableRef == imageRepository + "@" + digest` no manifesto;
4. confirme que o repositório de cada componente corresponde exatamente:

| Componente | Repositório |
|---|---|
| `backend` | `ghcr.io/greggorio/abaronesa-emporio-backend` |
| `website_back` | `ghcr.io/greggorio/abaronesa-emporio-website-backend` |
| `frontend` | `ghcr.io/greggorio/abaronesa-emporio-frontend` |
| `website_front` | `ghcr.io/greggorio/abaronesa-emporio-website-frontend` |
| `whatsapp_service` | `ghcr.io/greggorio/abaronesa-emporio-whatsapp-service` |
| `gateway` | `ghcr.io/greggorio/abaronesa-emporio-gateway` |

5. confirme que cada `provenance.verifiedSubject` é o `immutableRef`;
6. confirme que cada migration tem path único no banco;
7. confirme que cada migration tem versão única no banco;
8. confirme que `latestVersion` é a versão do último item do banco alvo;
9. confirme que `migrationSetSha256` é calculado pelo algoritmo canônico já
   usado em `tools/releases/global_release.py`, sem inventar outro algoritmo;
10. no estado atual, confirme que todos os `immutableRef` usam o repositório
    canônico do respectivo ID;
11. no estado atual, confirme ordem, unicidade e ownership dos bancos;
12. confirme que a release atual é estritamente menor que a alvo;
13. confirme que `target.previousRelease == current.release`;
14. em primeira implantação, exija `target.previousRelease == null`;
15. rejeite release igual, downgrade, salto de cadeia e manifesto atual
    divergente.

“Manifesto atual divergente” significa que o estado atual apresenta
`manifestSha256` diferente do hash fornecido pelo operador para o manifesto
que originou esse estado. Como a S18 não recebe o manifesto histórico, o CLI
de geração deve exigir também:

```text
--current-manifest <arquivo>
```

sempre que `--current` for usado. Esse arquivo:

- segue os mesmos limites e validações do manifesto alvo;
- deve ter `release == current.release` e
  `sourceCommit == current.sourceCommit`;
- seus componentes, projetados exatamente para `id` e `immutableRef`, devem
  ser iguais aos componentes do estado atual;
- seus bancos, projetados exatamente para `id`, `ownerComponent`,
  `migrationSetSha256` e `migrations`, devem ser iguais aos bancos do estado
  atual;
- deve produzir exatamente `current.manifestSha256`;
- não é aceito sem `--current`, nem `--current` sem ele.

O hash canônico de um manifesto é:

```text
sha256: + SHA-256 dos bytes JSON canônicos UTF-8
```

onde JSON canônico é produzido por:

```python
json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
```

sem newline final.

## 6. Regra exata de componentes

Ordem fixa:

```text
backend
website_back
frontend
website_front
whatsapp_service
gateway
```

Para primeira implantação:

- seis ações `UPDATE`;
- `currentDigest = null`;
- `targetDigest` extraído do `immutableRef` alvo.

Para atualização:

- mesmo `immutableRef`: `KEEP`;
- `immutableRef` diferente: `UPDATE`;
- `currentDigest` e `targetDigest` são os sufixos `sha256:...`.

Não aplique o fechamento de paths do catálogo nesta fase: a release global já
é um BOM integral e cada digest é a decisão final. Não converta `KEEP` em
`UPDATE` por dependência indireta. Compatibilidade é garantida pela publicação
global; o deploy executa o BOM alvo integral.

## 7. Regra exata de migrations

Para cada banco, em ordem `erp`, `website`:

### 7.1 Primeira implantação

- todas as migrations alvo são `pendingMigrations`;
- `changed = true`;
- `backupRequired = true`; o executor operacional futuro deve produzir backup
  até mesmo do banco inicial antes de entregar a migration ao runtime;
- `migrationRequired = true`.

### 7.2 Atualização

A lista atual deve ser prefixo exato da lista alvo, comparando, em cada
posição, o objeto integral `version`, `path` e `sha256`.

- listas idênticas: `changed = false`, `pendingMigrations = []`;
- alvo com sufixo adicional: `changed = true` e apenas o sufixo entra em
  `pendingMigrations`;
- remoção, reordenação, renomeação, alteração de hash ou de versão já aplicada:
  erro terminal `NON_FORWARD_MIGRATION`.

```text
migrationRequired = existe banco changed=true
backupRequired = migrationRequired
```

Essa equivalência vale tanto para primeira implantação quanto para
atualização.

O planner não executa migrations. Ele apenas determina o delta.

## 8. Schema definitivo do plano

`deployment-plan.schema.json` usa draft 2020-12, fecha propriedades adicionais
em todos os níveis e exige:

```json
{
  "schemaVersion": 1,
  "kind": "deployment-plan",
  "environment": "production",
  "sourceRelease": null,
  "targetRelease": "v0.0.1",
  "targetSourceCommit": "<sha>",
  "targetManifestSha256": "sha256:<hash>",
  "plannedAt": "2026-07-29T16:00:00Z",
  "firstInstallation": true,
  "components": [
    {
      "component": "backend",
      "service": "backend",
      "imageVariable": "BACKEND_IMAGE",
      "action": "UPDATE",
      "currentDigest": null,
      "targetDigest": "sha256:<digest>",
      "targetImmutableRef": "ghcr.io/...@sha256:<digest>"
    }
  ],
  "servicesToPull": ["backend"],
  "servicesToUpdate": ["backend"],
  "databases": [
    {
      "id": "erp",
      "ownerComponent": "backend",
      "changed": true,
      "currentMigrationSetSha256": null,
      "targetMigrationSetSha256": "sha256:<hash>",
      "pendingMigrations": [
        {"version": "1", "path": "...", "sha256": "sha256:<hash>"}
      ]
    }
  ],
  "migrationRequired": true,
  "backupRequired": true,
  "executionOrder": [
    "VALIDATE",
    "PULL",
    "BACKUP",
    "MIGRATE",
    "UPDATE",
    "VERIFY",
    "COMMIT_STATE"
  ]
}
```

No schema real:

- `sourceRelease` é `null` só na primeira implantação;
- `plannedAt` é exatamente o valor validado de `--planned-at`;
- há exatamente seis componentes e dois bancos;
- `servicesToPull` e `servicesToUpdate` contêm somente serviços com `UPDATE`,
  na ordem canônica;
- ambos os arrays são idênticos nesta versão do contrato;
- `executionOrder` é sempre exatamente o array acima, mesmo quando uma etapa
  for no-op;
- `service` e `imageVariable` seguem:

| Componente | Serviço Compose | Variável |
|---|---|---|
| `backend` | `backend` | `BACKEND_IMAGE` |
| `website_back` | `website_back` | `WEBSITE_BACK_IMAGE` |
| `frontend` | `frontend` | `FRONTEND_IMAGE` |
| `website_front` | `website_front` | `WEBSITE_FRONT_IMAGE` |
| `whatsapp_service` | `whatsapp_service` | `WHATSAPP_IMAGE` |
| `gateway` | `gateway` | `GATEWAY_IMAGE` |

O schema do plano deve ser compatível com os campos já publicados pelo
`DeploymentPlan` do OpenAPI. Ele pode ser mais rico, mas não pode mudar o
sentido de `sourceRelease`, `targetRelease`, `components`,
`migrationRequired` ou `backupRequired`.

### 8.1 Emenda exata do OpenAPI para primeira implantação

O OpenAPI atual exige `sourceRelease` como `ReleaseId`, mas uma primeira
implantação não possui release de origem. Altere exclusivamente:

```yaml
sourceRelease:
  oneOf:
    - {$ref: "#/components/schemas/ReleaseId"}
    - {type: "null"}
```

Não use `v0.0.0`, string vazia ou outro sentinela. Acrescente teste causal que
falhe se o `null` for removido, se o `$ref` for removido ou se outro tipo for
aceito. Nenhum endpoint, permissão, estado ou outro schema muda nesta emenda.

## 9. `release.env`

Produza exatamente sete linhas, LF, com newline final:

```text
RELEASE_ID=<target.release>
BACKEND_IMAGE=<immutableRef de backend>
WEBSITE_BACK_IMAGE=<immutableRef de website_back>
FRONTEND_IMAGE=<immutableRef de frontend>
WEBSITE_FRONT_IMAGE=<immutableRef de website_front>
WHATSAPP_IMAGE=<immutableRef de whatsapp_service>
GATEWAY_IMAGE=<immutableRef de gateway>
```

Valores são derivados apenas do manifesto alvo. Não copie variável da
configuração compartilhada, não aceite interpolação, aspas, espaços ou
comentários.

## 10. Próximo estado instalado

`installed-state.next.json` representa uma intenção de próximo estado, ainda
não uma instalação confirmada:

- os campos `release`, `sourceCommit`, `manifestSha256`, componentes e bancos
  são derivados integralmente do alvo;
- `environment=production`;
- `reconciled=false`;
- `installedAt=null`;
- `plannedAt` vem do argumento obrigatório `--planned-at`;
- `--planned-at` deve ser UTC RFC 3339 estrito, com segundos, sufixo `Z`, sem
  frações;
- `--planned-at` deve ser maior ou igual a `target.publishedAt` e, quando
  houver estado atual, estritamente maior que `current.installedAt`;
- o planner não lê relógio do sistema;
- componentes preservam somente `id` e `immutableRef`;
- bancos preservam `id`, `ownerComponent`, `migrationSetSha256` e migrations.

O executor operacional futuro não renomeia nem modifica esse arquivo dentro
do bundle. Somente após `VERIFY`, ele deve derivar um arquivo de estado
externo ao bundle, trocar `reconciled` para `true`, preencher `installedAt`
com o instante UTC real da confirmação e gravá-lo atomicamente. Essa operação
será contratada em slice futura.

## 11. Materialização atômica do bundle

CLI:

```text
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/deployment_plan.py generate \
  --target <manifesto.json> \
  [--current <installed-state.json> --current-manifest <manifesto-atual.json>] \
  --compose ops/compose/compose.prod.yml \
  --planned-at 2026-07-29T16:00:00Z \
  --output <diretorio>
```

Regras:

- `--output` deve ser inexistente;
- aceitar `--output` somente abaixo do workspace resolvido ou abaixo de
  `/tmp`, nunca igual ao workspace, `/tmp`, `/` ou a um diretório home;
- rejeitar qualquer destino preexistente, symlink em componente existente do
  caminho ou path fora das duas raízes permitidas;
- criar staging irmão com modo `0700`;
- arquivos JSON em forma canônica, UTF-8, LF único no final e modo `0600`;
- `compose.prod.yml` é cópia byte a byte e modo `0600`;
- `release.env` conforme seção 9 e modo `0600`;
- `bundle.sha256` contém cinco linhas no formato
  `<64 hex><dois espaços><nome>`, em ordem:

```text
manifest.json
compose.prod.yml
release.env
deployment-plan.json
installed-state.next.json
```

As cinco entradas acima são os arquivos cobertos; `bundle.sha256` não inclui a
si próprio.

- hashes calculados sobre bytes finais;
- `fsync` em cada arquivo e no staging;
- renomear staging para `--output` somente após releitura e verificação de
  todos os hashes;
- `fsync` no diretório pai após rename;
- em qualquer falha, remover somente o staging criado pela execução;
- nunca remover, sobrescrever ou reparar `--output` preexistente, ainda que
  vazio;
- não emitir conteúdo de manifesto ou ambiente em erros;
- não deixar arquivo temporário, diretório parcial ou cache Python.

## 12. Comandos adicionais

```text
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/deployment_plan.py validate \
  --bundle <diretorio>

PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py
```

`validate --bundle`:

- exige exatamente os seis arquivos do bundle;
- rejeita symlink em qualquer nível;
- revalida hashes, schemas, canonicalidade JSON, bytes do Compose canônico,
  `release.env`, coerência do plano e próximo estado;
- não precisa receber novamente as entradas.

`validate_deployment_plan.py` valida os arquivos versionados, exemplos,
documentação, mapeamentos e superfície CLI.

## 13. Códigos de saída e erros

```text
0 = sucesso
2 = uso/argumento inválido
3 = entrada, contrato ou bundle inválido
4 = destino inseguro ou conflito de materialização
5 = falha de I/O/atomicidade
```

`NON_FORWARD_MIGRATION`, `RELEASE_CHAIN_MISMATCH`,
`CURRENT_STATE_MISMATCH`, `UNSAFE_PATH`, `BUNDLE_CONFLICT` e
`INVALID_CONTRACT` são códigos sanitizados possíveis em stderr. Uma linha por
erro, sem traceback, JSON bruto, URL com credencial, token ou variável
sensível.

## 14. Testes causais obrigatórios

Use apenas biblioteca padrão e dependências já presentes. Cubra no mínimo:

1. primeira implantação gera seis `UPDATE`;
2. atualização integralmente igual gera seis `KEEP`;
3. alteração de cada componente isolado gera somente seu `UPDATE`;
4. ordem fixa dos seis itens;
5. mapeamento exato serviço/variável;
6. `release.env` integral e imutável;
7. migration atual como prefixo produz somente o sufixo;
8. migration idêntica não exige backup;
9. dois bancos alterados produzem deltas independentes;
10. remoção de migration falha;
11. reordenação de migration falha;
12. hash alterado de migration aplicada falha;
13. salto de cadeia falha;
14. downgrade e release igual falham;
15. primeira implantação com `previousRelease` não nulo falha;
16. atualização com `previousRelease` incorreto falha;
17. estado sem manifesto histórico pareado falha;
18. hash do manifesto histórico divergente falha;
19. repositório, digest ou provenance divergente falha;
20. ID duplicado, ausente, extra ou fora de ordem falha;
21. Compose diferente, symlink ou grande demais falha;
22. output preexistente, vazio ou não, falha sem alteração;
23. falha injetada em cada write, fsync, verify e rename não deixa staging;
24. bundle alterado depois da geração falha na validação;
25. arquivo extra no bundle falha;
26. symlink interno no bundle falha;
27. JSON não canônico no bundle falha;
28. timestamp inválido ou leitura do relógio falha;
29. nenhum comando externo é chamado;
30. erros não contêm dados sensíveis;
31. mutantes removendo qualquer gate acima são detectados;
32. zero `__pycache__`, `.pyc` ou resíduo temporário ao final.

Não simule aprovação somente procurando strings. Os testes devem chamar as
funções reais e provar a causa.

## 15. Documentação

`PLANO_IMPLANTACAO.md` deve explicar:

- diferença entre release global, estado instalado, plano e bundle;
- por que o usuário nunca seleciona componentes;
- regra KEEP/UPDATE por digest;
- migração forward-only e backup obrigatório em atualização;
- backup obrigatório também na primeira implantação antes das migrations;
- papel futuro de `current` e `previous`;
- que `installed-state.next.json` é intenção não reconciliada e que o estado
  confirmado deve ser derivado somente após verificação;
- exemplos exatos dos três comandos da seção 11/12;
- códigos de saída e diagnóstico sanitizado;
- fronteira: S18 não implanta nada.

Atualize os dois READMEs autorizados apenas para apontar esse documento e
posicionar a S18 entre publicação global e execução operacional futura.

## 16. Validação mínima e relatório

Execute e registre:

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/deploy/tests \
  -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py

PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/deployment_plan.py generate \
  --target ops/releases/examples/global-release.example.json \
  --compose ops/compose/compose.prod.yml \
  --planned-at 2026-07-29T16:00:00Z \
  --output <diretorio efemero sob /tmp>

PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/deployment_plan.py validate \
  --bundle <mesmo diretorio>
```

Remova apenas o diretório efêmero criado para essa prova.

Também execute a regressão local dos contratos:

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py'
```

Não repita Maven, npm, Docker ou actionlint.

O relatório deve conter:

- `IN_PROGRESS — aguardando revisão do orquestrador`;
- CWD;
- arquivos criados/alterados;
- para cada comando: comando exato, exit code, resultado e interpretação;
- contagem dos testes;
- matriz de casos causais;
- evidência de bytes, modos e hashes do bundle sem transcrever dados sensíveis;
- falhas intermediárias e correções;
- confirmação de ausência de rede, comandos externos e resíduos;
- `git status --short`, índice, HEAD, tags, reflog e workflows ativos;
- divergências e itens não determinados;
- confirmação de que task, tracker e S19 não foram alterados/criados.

## 17. Critérios de aceite

A S18 só pode ser aceita se:

1. contratos fechados e exemplos válidos;
2. planner determinístico e sem relógio;
3. seis componentes sempre decididos pelo BOM alvo;
4. migrations estritamente forward-only;
5. backup calculado exatamente conforme seção 7, inclusive na primeira
   implantação;
6. manifesto atual e estado atual vinculados por hash;
7. bundle integral, atômico, revalidável e sem segredo;
8. nenhum side effect operacional ou comando externo;
9. testes causais e mutantes cobrirem gates;
10. documentação e implementação coincidirem;
11. regressão dos contratos existentes permanecer verde;
12. estado Git protegido e nenhum resíduo.

## 18. Resposta final esperada do executor

Informe:

- caminho absoluto do relatório;
- arquivos alterados;
- resultados dos comandos e contagens;
- comportamento comprovado para primeira instalação, atualização e migrations;
- resultado da prova de atomicidade;
- divergências e itens não determinados;
- confirmação de zero rede/Docker/VPS/GitHub/GHCR;
- confirmação de que não criou S19;
- estado `IN_PROGRESS — aguardando revisão do orquestrador`.
