# S25 — contrato seguro de rollback comercial

> **Estado do executor:** `IN_PROGRESS — aguardando revisão do orquestrador`
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Data:** 31/07/2026

## 1. Escopo executado

A S25 foi implementada exclusivamente como contrato offline futuro. Nenhum
rollback, restore, migration, troca de imagem, runtime, backend,
`release_control`, frontend, publisher, workflow, Docker, produção ou
capability foi habilitado.

O contrato fecha elegibilidade de predecessor global imutável e publicado,
cadeia, migrations, restore de banco, backup, uploads, sessão WhatsApp,
compensação forward, API, idempotência, lock, estados e recovery. Os artefatos
machine-readable declaram `future_only`, `runtime_consumer: none` e ativação
reservada para S26.

## 2. Arquivos

### Criados

- `docs/infrastructure/deployment/release-control/ROLLBACK_COMERCIAL.md`
- `docs/infrastructure/deployment/release-control/api/rollback.openapi.yml`
- `docs/infrastructure/deployment/release-control/contracts/rollback-state-machine.yml`
- `docs/infrastructure/deployment/release-control/contracts/rollback-security.yml`
- `tools/deploy/validate_rollback_contract.py`
- `tools/deploy/tests/test_rollback_contract.py`
- este relatório

### Alterados somente para referência, sem ativação

- `docs/infrastructure/deployment/release-control/README.md`
- `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`
- `docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md`

### Não alterados

Não foram alterados a task S25, tracker, S01–S24, `deployer.openapi.yml`,
`state-machines.yml`, `security-matrix.yml`, schemas ativos, backend,
`release_control`, frontend, publisher S17, workflows, Docker, Compose,
Nginx, gateway, CLI S20, adapters, VPS, produção, secrets, `.env`, chaves ou
tokens. Não foi criado S26.

O workspace permanece pré-Git; portanto o inventário Git mostra os artefatos
do workspace como não versionados, mas não houve `git add`, commit, tag ou
push. As únicas escritas desta execução foram os sete arquivos criados, as
três referências documentais autorizadas e este relatório.

## 3. Decisões fechadas

- Somente instalação atual reconciliada pode iniciar a operação futura.
- O alvo é exatamente o predecessor global publicado, imutável, implantável e
  da mesma cadeia; salto, predecessor divergente, candidato e release
  `deployable: false` são inelegíveis.
- Elegibilidade é calculada pelo servidor; operador não seleciona componente,
  digest, tag, imagem, migration, banco, ordem ou comando.
- Migrations `erp` e `website` são comparadas integralmente por
  `version`, `path` e `sha256`. Delta aplicado desde o alvo exige restore,
  salvo prova explícita de reversibilidade integral.
- Backup compatível, anterior à release atual, completo e verificado é
  obrigatório quando restore é necessário. Retenção mínima é 365 dias, sem
  renovação silenciosa.
- O registro canônico contém somente `backupId`, `sourceRelease`,
  `sourceStateSha256`, bancos `erp` e `website`, `artifactSha256`, `createdAt`
  e `expiresAt`; não contém path, credencial, dump ou URL privada.
- Uploads não são apagados nem restaurados implicitamente. Sessão WhatsApp não
  é restaurada automaticamente; incompatibilidade exige estado seguro e
  reemparelhamento manual.
- Request futuro contém exatamente `release` e `reason`; `reason` tem 10–1000
  caracteres. O header é `Idempotency-Key` com prefixo
  `deployer-rollback-<UUID v4>`.
- Replay idêntico retorna a mesma operação; payload divergente retorna
  `IDEMPOTENCY_CONFLICT`. O lock `production_global` é compartilhado com
  deployment e permite uma operação ativa; conflito retorna
  `PRODUCTION_OPERATION_ACTIVE`. Não há retry automático.
- O scope futuro é `deployment:rollback`, mas a capability atual permanece
  exatamente `deployment:read` e `deployment:execute`; a rota permanece
  reservada e indisponível.
- Estados futuros são exatamente `QUEUED`, `PRECHECKING`, `RESTORING`,
  `SWITCHING`, `VERIFYING`, `SUCCEEDED`, `ROLLING_BACK`, `ROLLED_BACK`,
  `FAILED` e `UNCERTAIN`.
- `SUCCEEDED` exige alvo reconciliado, restore verificado quando necessário,
  banco, links e seis componentes comprovados. Falha antes de side effect pode
  ser `FAILED`; falha após side effect exige compensação em `ROLLED_BACK` com
  evidência completa. Incerteza termina `UNCERTAIN` e bloqueia nova operação
  até reconciliação humana.
- `ROLLED_BACK` é compensação de tentativa parcial, não sucesso do rollback
  comercial solicitado. Estados terminais não repetem side effects.

## 4. Artefatos machine-readable

- `rollback.openapi.yml` define somente os dois endpoints futuros, request
  fechado, reason, header UUID v4, operação `rollback`, estados e ProblemDetails.
- `rollback-state-machine.yml` define estados, transições exclusivas do
  reconciliador, condições de restore, verificação, compensação e incerteza.
- `rollback-security.yml` define elegibilidade server-side, capability futura
  ausente no runtime atual, política de migration/backup, dados persistentes,
  idempotência, lock, recovery e proibições de ativação.
- `ROLLBACK_COMERCIAL.md` é a explicação humana vinculada aos três artefatos e
  explicita a separação entre compensação forward e rollback comercial.
- `validate_rollback_contract.py` cruza os quatro artefatos, referências nos
  documentos ativos e invariantes que não cabem apenas em YAML/OpenAPI.

## 5. Testes causais e mutantes

`tools/deploy/tests/test_rollback_contract.py` executou 22 testes, sendo um
caso real e 21 mutantes, todos aprovados. Os mutantes rejeitados cobrem:

- arquivo obrigatório ausente;
- body com campo extra e limites de `reason` incorretos;
- UUID v4 ou operação futura incorretos;
- estado `UNCERTAIN` removido, transição impossível, transição removida e
  ator cliente;
- salto de release permitido, capability atual com rollback, retenção de
  backup reduzida e path de backup liberado;
- restore implícito de upload, retry automático e limites de motivo alterados;
- compensação forward removida, ativação documental indevida e referência
  ausente no README;
- marcador forward-only atual removido e capability futura habilitada.

Saída literal relevante:

```text
Ran 22 tests in 0.468s

OK
DURATION_SECONDS=0.53
```

## 6. Matriz terminal completa

Todos os comandos foram executados com CWD
`/home/gregorio/git/baronesa/emporio`.

| Comando | Exit | Contagem/resultado | Duração | Interpretação e artefatos |
|---|---:|---|---:|---|
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | `rollback-contract:valid` | 0,08 s | Os quatro artefatos e referências autorizadas são consistentes; sem artefato runtime |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/deploy/tests/test_rollback_contract.py -v` | 0 | 22 testes; 22 aprovados | 0,53 s | Testes causais e mutantes verdes; sem bytecode gerado |
| `python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` | 0,10 s | Contratos ativos permaneceram válidos |
| `git diff --check` | 0 | saída vazia | 0,00 s | Nenhum erro de whitespace detectável pelo Git pré-inicialização |
| `git rev-parse --verify HEAD` | 128 | `fatal: Needed a single revision` | 0,00 s | Única exceção prevista: workspace sem HEAD |
| `git tag --list` | 0 | saída vazia | 0,00 s | Nenhuma tag criada |
| `git reflog show --all` | 0 | saída vazia | 0,00 s | Nenhum histórico criado ou alterado |
| `find .github/workflows -maxdepth 1 -type f -printf "%f\\n" \| sort` | 0 | `README.md`, `ci.yml`, `deploy-production.yml`, `publish-candidate.yml`, `publish-release.yml` | 0,00 s | Workflows somente inventariados |
| `find . -path "./.git" -prune -o \( -name ".venv" -o -name ".coverage" -o -name ".pytest_cache" -o -name ".ruff_cache" -o -name ".mypy_cache" -o -name "__pycache__" -o -name "*.pyc" \) -print` | 0 | saída vazia | 0,18 s | Resíduos prescritos vazios |

Saídas literais adicionais:

```text
rollback-contract:valid
DURATION_SECONDS=0.08

release-control-contract:valid
DURATION_SECONDS=0.10

fatal: Needed a single revision
Command exited with non-zero status 128
DURATION_SECONDS=0.00
```

## 7. Git, workflows e resíduos

`git rev-parse --verify HEAD` continua em exit 128; tags e reflog estão
vazios. Não houve stage, commit, push, publicação ou alteração de workflow. O
inventário dos cinco arquivos de `.github/workflows` acima é somente leitura.
A busca prescrita de `.venv`, coverage, caches, `__pycache__` e `*.pyc` foi
vazia. Não há S26.

## 8. Acessos externos e segredos

Não houve acesso a GitHub, GHCR, SSH, VPS, DNS, gateway, Nginx, produção,
rede, containers, volumes ou serviço deployer real. Não foram abertos ou
criados secrets, `.env`, chaves privadas, tokens ou credenciais. Os valores
contratuais são sintéticos e não representam segredo operacional.

## 9. Divergências restantes

Nenhuma divergência contratual ou de implementação foi encontrada. A única
condição não zero é `git rev-parse --verify HEAD` em exit 128, comprovada na
matriz e autorizada pela própria task para este workspace pré-Git.

## 10. Estado final

IN_PROGRESS — aguardando revisão do orquestrador

## 11. Revisão terminal do orquestrador — aceite

**Veredito: ACCEPTED — 31/07/2026.**

A revisão independente confirmou:

- `python3 tools/deploy/validate_rollback_contract.py`: exit 0, `rollback-contract:valid`;
- suíte causal: exit 0, 22/22 testes, incluindo 21 mutantes;
- `python3 tools/releases/release_control_contract.py validate`: exit 0;
- `git diff --check`: exit 0;
- `git rev-parse --verify HEAD`: exit 128, condição prevista do workspace pré-Git;
- tags e reflog vazios; workflows somente inventariados; busca prescrita de resíduos vazia;
- artefatos declarados future-only, sem ativação de runtime, capability, API, workflow ou acesso externo;
- divergências: nenhuma.

S25 está aceita. O próximo contrato é [S26-executor-workflow-runtime-rollback.task.md](./S26-executor-workflow-runtime-rollback.task.md).
