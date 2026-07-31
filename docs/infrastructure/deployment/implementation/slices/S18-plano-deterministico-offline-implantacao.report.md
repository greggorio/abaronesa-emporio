# S18 — Plano determinístico offline de implantação

## 1. Resumo

A S18 foi executada integralmente como planejamento offline. Foram entregues
schemas fechados, exemplos cruzáveis, planner, validador, testes causais,
emenda mínima do OpenAPI e documentação.

O planner recebe uma release global alvo, opcionalmente o estado instalado e
seu manifesto histórico pareado, o Compose canônico e um instante fornecido
pelo operador. Sem consultar relógio, rede ou ambiente operacional, ele:

- decide os seis componentes em ordem canônica;
- calcula `KEEP` ou `UPDATE` somente pela referência imutável;
- calcula deltas Flyway exclusivamente forward-only;
- torna backup obrigatório exatamente quando há migration;
- gera `release.env` apenas com a release e as seis imagens;
- produz uma intenção não reconciliada de próximo estado;
- materializa e revalida atomicamente um bundle de seis arquivos.

Nenhum deploy, backup, migration, health check, rollback, Docker, banco ou
acesso remoto foi executado.

Estado: **IN_PROGRESS — aguardando revisão do orquestrador**

## 2. CWD

Todos os comandos foram executados em:

```text
/home/gregorio/git/baronesa/emporio
```

## 3. Arquivos criados

- `ops/deploy/schemas/installed-state.schema.json`
- `ops/deploy/schemas/deployment-plan.schema.json`
- `ops/deploy/examples/installed-state.example.json`
- `ops/deploy/examples/deployment-plan.example.json`
- `tools/deploy/deployment_plan.py`
- `tools/deploy/validate_deployment_plan.py`
- `tools/deploy/tests/test_deployment_plan.py`
- `tools/deploy/tests/test_deployment_plan_contract.py`
- `docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md`
- `docs/infrastructure/deployment/implementation/slices/S18-plano-deterministico-offline-implantacao.report.md`

Os diretórios foram criados sem `__init__.py`.

## 4. Arquivos alterados

- `docs/infrastructure/deployment/release-control/README.md`
- `docs/infrastructure/deployment/release-control/RELEASES.md`
- `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`
- `tools/releases/release_control_contract.py`
- `tools/releases/tests/test_release_control_contract.py`

No OpenAPI, a única emenda funcional foi
`DeploymentPlan.properties.sourceRelease`, agora exatamente:

```yaml
oneOf:
  - {$ref: "#/components/schemas/ReleaseId"}
  - {type: "null"}
```

O validador e os testes S06 foram alterados apenas para proteger essa emenda.

## 5. Contratos entregues

### 5.1 Estado instalado

O schema Draft 2020-12:

- fecha propriedades adicionais em todos os níveis;
- exige seis componentes e dois bancos nas posições canônicas;
- restringe repositórios, digests, hashes, releases e timestamps;
- separa estado confirmado de intenção;
- aceita `reconciled=true` somente com `installedAt` UTC;
- aceita a intenção somente com `reconciled=false` e `installedAt=null`.

O planner reforça as relações temporais que não são expressáveis apenas pelo
JSON Schema: instalação confirmada não antecede planejamento e um estado
fornecido por `--current` deve estar reconciliado.

### 5.2 Plano

O plano contém sempre:

- seis componentes na ordem `backend`, `website_back`, `frontend`,
  `website_front`, `whatsapp_service`, `gateway`;
- dois bancos na ordem `erp`, `website`;
- serviços e variáveis de imagem fixos;
- `servicesToPull == servicesToUpdate`;
- ordem `VALIDATE`, `PULL`, `BACKUP`, `MIGRATE`, `UPDATE`, `VERIFY`,
  `COMMIT_STATE`;
- `backupRequired == migrationRequired`.

Na primeira implantação, `sourceRelease=null`, todos os componentes são
`UPDATE` e todas as migrations são pendentes. Em atualização, igualdade da
referência imutável produz `KEEP`; diferença produz `UPDATE`.

### 5.3 Manifesto atual e cadeia

`--current` e `--current-manifest` são obrigatoriamente pareados. O manifesto
histórico é revalidado e vinculado ao estado por:

- release;
- source commit;
- hash de JSON canônico sem newline;
- projeção integral dos seis componentes;
- projeção integral dos dois bancos e migrations.

Release igual, downgrade, salto de cadeia e `previousRelease` incompatível
falham fechados.

### 5.4 Migrations

O fingerprint usa exatamente o algoritmo canônico da S13. Para cada banco, o
estado atual precisa ser prefixo objeto a objeto da lista alvo. Remoção,
reordenação, renomeação ou mudança de versão/path/hash já aplicado retorna
`NON_FORWARD_MIGRATION`.

Os dois bancos são calculados de forma independente. Sem sufixo novo não há
migration nem backup. Com qualquer sufixo, ambos os flags ficam verdadeiros.
Na primeira implantação, os dois inventários integrais são pendentes e backup
também é obrigatório.

## 6. Bundle e atomicidade

O bundle contém exatamente:

```text
manifest.json
compose.prod.yml
release.env
deployment-plan.json
installed-state.next.json
bundle.sha256
```

Propriedades comprovadas:

- staging irmão em `0700`;
- diretório final em `0700`;
- arquivos em `0600`;
- JSON canônico UTF-8 com um LF final;
- Compose copiado byte a byte;
- `release.env` com sete linhas e LF final;
- cinco checksums em ordem fixa;
- `fsync` em cada arquivo, no staging e no diretório pai;
- rename somente depois da releitura e verificação;
- conflito nunca sobrescreve output preexistente;
- falhas de escrita, cada chamada de `fsync`, verify e rename não deixam
  staging;
- se o `fsync` do pai falha depois do rename, o bundle já integral permanece
  revalidável, sem diretório temporário ou remoção destrutiva.

`validate --bundle` verifica nomes, ausência de symlinks, modos, hashes,
canonicalidade, Compose, environment, schemas e coerência referencial.

## 7. Matriz causal

As 41 provas S18 cobrem:

| Grupo | Evidência causal |
|---|---|
| Primeira implantação | seis `UPDATE`, digests atuais nulos, dois inventários pendentes e backup |
| Atualização idêntica | seis `KEEP`, listas de serviços vazias e sem backup |
| Componentes | cada um dos seis alterado isoladamente produz somente seu `UPDATE` |
| Ordem e mapeamento | seis posições, serviço e variável exatos |
| Environment | sete linhas determinísticas derivadas somente do alvo |
| Prefixo Flyway | apenas o sufixo entra no plano |
| Dois bancos | deltas calculados de forma independente |
| Forward-only | remoção, reordenação e hash aplicado alterado falham |
| Cadeia | salto, igualdade, downgrade e previous incorreto falham |
| Estado histórico | ausência do par e hash/projeção divergentes falham |
| Imagens | repositório, digest, immutable ref e provenance divergentes falham |
| BOM | ID duplicado, ausente, extra ou fora de ordem falha |
| Compose | outro path, symlink e limite excedido falham |
| Output | destino preexistente com marcador vazio ou preenchido fica intacto |
| Escritas | falha injetada em cada uma das seis escritas remove staging |
| Fsync | falha injetada nas oito chamadas é tratada causalmente |
| Verify/rename | falha injetada não deixa staging ou conteúdo parcial |
| Bundle | nomes, modos, hashes e ordem do sidecar comprovados |
| Adulteração | payload alterado e arquivo extra falham |
| Symlink | entrada interna por symlink falha |
| JSON | forma não canônica falha mesmo com checksum recalculado |
| Tempo | formato, limites e ausência de leitura do relógio comprovados |
| Operação externa | planner não possui nem chama subprocesso/comando externo |
| Erros | uma linha sanitizada, sem traceback ou conteúdo de entrada |
| Próximo estado | projeção integral, não reconciliada e sem segredo |
| Contratos versionados | 13 mutantes cobrem arquivos, schemas, exemplos, mappings, CLI, relógio, comandos externos, OpenAPI e documentação |

Além dessas 41 provas, 63 testes focados do contrato release-control
confirmaram a emenda do OpenAPI, incluindo presença do `null`, presença do
`ReleaseId` e rejeição de tipos adicionais.

## 8. Comandos obrigatórios e resultados

### 8.1 Suíte S18

- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'`
- Exit code: `0`
- Resultado: `41` testes aprovados.
- Interpretação: regras funcionais, atomicidade e mutantes contratuais verdes.
- Artefatos/resíduos: nenhum cache Python; temporários dos testes removidos.

### 8.2 Validador versionado

- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py`
- Exit code: `0`
- Resultado: `deployment-plan-contract:valid`.
- Interpretação: schemas, exemplos, catálogo, Compose, OpenAPI, documentação e
  superfície do planner estão alinhados.
- Artefatos/resíduos: nenhum.

### 8.3 Geração efêmera

- Diretório pai criado por `mktemp`:
  `/tmp/s18-deployment-plan.hVzwAA`
- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/deployment_plan.py generate --target ops/releases/examples/global-release.example.json --compose ops/compose/compose.prod.yml --planned-at 2026-07-29T16:00:00Z --output /tmp/s18-deployment-plan.hVzwAA/bundle`
- Exit code: `0`
- Resultado: `deployment-plan:generated`.
- Interpretação: primeira implantação gerou seis `UPDATE`, 50 migrations ERP,
  14 migrations website e backup obrigatório.
- Artefatos/resíduos: bundle efêmero inspecionado e posteriormente removido.

### 8.4 Validação do mesmo bundle

- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/deployment_plan.py validate --bundle /tmp/s18-deployment-plan.hVzwAA/bundle`
- Exit code: `0`
- Resultado: `deployment-plan:valid`.
- Interpretação: os seis arquivos produzidos foram revalidados sem as entradas
  originais.
- Artefatos/resíduos: nenhum adicional.

### 8.5 Inspeção sanitizada de bytes, modos e hashes

- Comando exato: script Python local sobre
  `/tmp/s18-deployment-plan.hVzwAA/bundle`, calculando nomes, modos,
  checksums, ações e contagens sem imprimir conteúdo.
- Exit code: `0`
- Resultado:
  - bundle `0700`;
  - todos os arquivos `0600`;
  - seis nomes exatos;
  - cinco hashes verificados;
  - seis ações `UPDATE`;
  - migrations pendentes `[50, 14]`;
  - backup obrigatório.
- Interpretação: bytes finais e permissões correspondem ao contrato.
- Artefatos/resíduos: nenhum.

### 8.6 Limpeza da prova

- Comando exato: `rm -r /tmp/s18-deployment-plan.hVzwAA`
- Exit code: `0`
- Resultado: diretório efêmero removido.
- Interpretação: somente o recurso criado para a prova foi apagado.
- Artefatos/resíduos: zero.

### 8.7 Regressão dos contratos de release

- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'`
- Exit code: `0`
- Resultado: `277` testes aprovados.
- Interpretação: contratos S05–S17 permanecem verdes.
- Artefatos/resíduos: nenhum cache Python.

### 8.8 Validador release-control adicional

- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate`
- Exit code: `0`
- Resultado: `release-control-contract:valid`.
- Interpretação: a emenda OpenAPI permanece compatível com o contrato S06.
- Artefatos/resíduos: nenhum.

### 8.9 Testes focados release-control adicionais

- Comando exato:
  `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/releases/tests/test_release_control_contract.py -v`
- Exit code: `0`
- Resultado: `63` testes aprovados.
- Interpretação: as três provas novas de `sourceRelease` e os gates anteriores
  passaram.
- Artefatos/resíduos: nenhum.

## 9. Falhas intermediárias e correções

1. A primeira suíte mutante contratual teve `11/13` aprovações. Dois casos
   esperavam um código semântico posterior, mas o JSON Schema fechado barrava
   a mutação antes. As expectativas foram alinhadas ao primeiro gate causal,
   sem relaxar validação.
2. A primeira execução conjunta dos testes funcionais e contratuais teve
   `36/40` aprovações: um patch de rename apontava para o owner incorreto e
   três fixtures de cadeia/migration eram barradas antes da causa pretendida.
   O harness foi corrigido para manter as fixtures válidas até o gate causal.
3. A auditoria do planner identificou quatro endurecimentos antes da matriz
   final:
   - aceitar manifesto válido mesmo quando seus bytes de entrada não estavam
     reformatados canonicamente;
   - cruzar `sourceRelease` com `target.previousRelease` no bundle;
   - rejeitar `UPDATE` cujo digest atual já fosse o alvo;
   - revalidar timestamps e modos no bundle.
4. A cobertura de atomicidade foi ampliada de 40 para 41 testes para injetar
   falha em cada uma das oito chamadas de `fsync`, inclusive o fsync do pai
   posterior ao rename.
5. Uma inspeção Python auxiliar executada sem a flag de bytecode criou caches
   sob `tools/releases` e `tools/candidates`. Como o inventário inicial
   comprovava sua ausência, foram removidos exclusivamente por:
   `find tools -type f -name '*.pyc' -delete` e
   `find tools -depth -type d -name __pycache__ -empty -delete`.

Todas as correções permaneceram dentro dos arquivos autorizados.

## 10. Estado Git e higiene

### 10.1 Integridade textual

- Comando: `git diff --check`
- Exit: `0`
- Resultado: nenhuma saída.
- Interpretação: nenhum erro de whitespace.

### 10.2 Status

- Comando: `git status --short`
- Exit: `0`
- Resultado: raízes do workspace aparecem como não rastreadas, coerente com o
  repositório ainda sem primeiro commit.
- Interpretação: não houve materialização no índice.

### 10.3 Índice

- Comando: `git diff --cached --name-only`
- Exit: `0`
- Resultado: vazio.
- Interpretação: índice Git real vazio.

### 10.4 HEAD

- Comando: `git rev-parse --verify HEAD`
- Exit: `128`
- Resultado: `fatal: Needed a single revision`.
- Interpretação: HEAD inexistente.

### 10.5 Tags

- Comando: `git tag --list`
- Exit: `0`
- Resultado: vazio.
- Interpretação: nenhuma tag.

### 10.6 Reflog

- Comando: `git reflog`
- Exit: `128`
- Resultado: branch `main` sem commits.
- Interpretação: reflog inexistente.

### 10.7 Workflows

- Comando:
  `find .github/workflows -maxdepth 1 -type f -name '*.yml' -print | sort`
- Exit: `0`
- Resultado:
  - `.github/workflows/ci.yml`
  - `.github/workflows/publish-candidate.yml`
  - `.github/workflows/publish-release.yml`
- Interpretação: exatamente três workflows; nenhum foi alterado pela S18.

### 10.8 Caches e resíduos

- `find tools -type d -name __pycache__ -print`: exit `0`, vazio.
- `find tools -type f -name '*.pyc' -print`: exit `0`, vazio.
- `find /tmp -maxdepth 1 -type d \( -name 's18-*' -o -name 's18_*' \) -print | sort`:
  exit `0`, vazio.
- `find tools/deploy -type f -name '__init__.py' -print`: exit `0`, vazio.

## 11. Fronteiras protegidas

Os hashes de task e tracker antes e depois permaneceram:

```text
c84dbb5b10f223781da64d1ff2ae17fdf8a80c8af2940ee6d6c34ad7e73f24db  S18-plano-deterministico-offline-implantacao.task.md
889630e8ba0a2ce93305522917bfe13dc5b4d21a996595479ac3afee02e0c017  implementation/README.md
```

Também foi confirmado:

- nenhum `git add`, commit, tag ou push;
- nenhuma alteração em catálogo, manifesto global, Compose, máquina de
  estados ou workflow;
- nenhum Maven, npm, Docker, Podman, PostgreSQL, Flyway, actionlint ou
  instalação;
- nenhum GitHub, GHCR, VPS, DNS ou produção;
- nenhum diretório sob `/opt`;
- nenhum symlink `current` ou `previous`;
- nenhum S19.

## 12. Divergências e itens não determinados

Não há divergência conhecida ou decisão arquitetural não determinada dentro
da S18. A execução operacional do plano, confirmação do estado instalado,
backup, migrations, verificação, rollback e atualização de symlinks continuam
explicitamente reservados para slices futuras.

## 13. Estado final

**IN_PROGRESS — aguardando revisão do orquestrador**

## 14. Revisão terminal do orquestrador — 29/07/2026

### 14.1 Veredito

`ACCEPTED`.

A implementação, os contratos versionados e as evidências persistidas foram
revisados contra a task S18. Os testes do executor não foram reexecutados.

Foram confirmados:

- BOM integral e ordem canônica dos seis componentes;
- decisão `KEEP`/`UPDATE` exclusivamente por referência imutável;
- vínculo entre estado atual e manifesto histórico por hash e projeções;
- cadeia direta de releases e bloqueio de igualdade/downgrade;
- migrations estritamente forward-only nos dois bancos;
- equivalência `backupRequired == migrationRequired`, inclusive no primeiro
  deploy;
- intenção seguinte não reconciliada, sem falso `installedAt`;
- bundle fechado, revalidável, com modos e hashes definidos;
- materialização atômica e tratamento explícito da janela posterior ao rename;
- emenda mínima e protegida de `DeploymentPlan.sourceRelease`;
- ausência de efeitos Docker, banco, rede ou produção.

### 14.2 Ajuste documental não funcional

A matriz passou a descrever fielmente os marcadores usados na prova de output
preexistente. O comportamento funcional já rejeitava qualquer destino
preexistente, inclusive diretório vazio.

Não houve alteração funcional nem necessidade de novo ciclo do executor.

### 14.3 Estado final

**ACCEPTED — 29/07/2026**
