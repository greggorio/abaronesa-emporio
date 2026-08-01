# S30a — Paridade local e fechamento da CI e do candidato — relatório

## 1. Escopo, autoridade e resultado

- **CWD:** `/home/gregorio/git/baronesa/emporio`.
- **Commit-base:** `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`.
- **Autoridade lida:** task S30a; task S30; amendment-01 e corrections 01, 02 e
  03 da S30; relatório S30; HANDOFF, em especial a Seção 32; tracker; `ci.yml`;
  `publish-candidate.yml`; validadores e testes de CI e candidato.
- **Resultado:** parada obrigatória antes do stage/commit. Os seis builds locais
  passaram, mas os seis scans Trivy retornaram exit 1 por achados
  `HIGH/CRITICAL`.
- **Política preservada:** `severity: HIGH,CRITICAL`, `ignore-unfixed: false` e
  `exit-code: 1`; nenhuma regra do Trivy foi alterada.

O código da S30a foi implementado e validado localmente para entregar ao
orquestrador uma correção reproduzível, mas não foi staged, commitado ou enviado
ao remoto porque a Seção 4.4 determina a parada.

## 2. Arquivos desta execução

Criados ou alterados, todos na fronteira da Seção 5:

- `.github/workflows/ci.yml`;
- `tools/ci/validate_ci.py`;
- `tools/ci/tests/test_ci.py`;
- `tools/candidates/validate_candidate_workflow.py`;
- `tools/candidates/tests/test_definitive_contract.py`;
- `tools/ci/invocability.py`;
- `tools/ci/tests/test_invocability.py`;
- este relatório.

As alterações preexistentes em HANDOFF, tracker, relatório/corrections S30 e
task S30a foram preservadas e não foram absorvidas por esta execução.
`publish-candidate.yml` foi apenas lido. Nenhum arquivo fora da Seção 5 foi
alterado pela implementação.

## 3. Implementação das decisões fechadas

### 3.1 Família G

`ci.yml` passou a chamar exatamente:

```text
python3 tools/security/bootstrap_contract.py validate
python3 tools/docker/java_images_contract.py validate
```

As outras cinco chamadas auditadas do job `contracts` não foram modificadas.
`validate_ci.py` agora exige a forma exata com `validate` para os três
contratos com subcomando e rejeita a linha bare por igualdade de linha inteira.

### 3.2 Gate de invocabilidade

`tools/ci/invocability.py` extrai as linhas `python3 tools/**.py` dos dois
workflows, substitui expressões Actions por valores fixos sintaticamente
válidos e testa cada CLI isoladamente. Depois da inclusão do próprio gate há 29
linhas; `validate_ci.py` e `invocability.py` são as duas auto-invocações
excluídas da recursão, fechando exatamente **27 comandos**.

Resultado literal:

```text
invocability:valid:commands=27:parse_args=24:argument-free=3
```

Para os 24 CLIs com `argparse`, um subprocesso temporário chama o script com a
linha sintetizada, deixa o parser original validar os argumentos e interrompe
imediatamente depois de `parse_args`. Um audit hook rejeita rede, socket,
subprocesso e escrita fora do diretório temporário antes dessa fronteira. Os
três entrypoints sem parser e sem argumentos são verificados estaticamente e
não têm o corpo executado.

O teste agregado aplicou simultaneamente os seis mutantes prescritos e recebeu
seis recusas na mesma chamada:

1. `release_control_contract.py` sem `validate`;
2. `bootstrap_contract.py` sem `validate`;
3. `java_images_contract.py` sem `validate`;
4. `catalog.py` com `--unknown-s30a`;
5. `candidate_plan.py` com subcomando `nonexistent`;
6. `trust.py` sem a flag obrigatória `--event`.

`test_02_six_prescribed_mutants_are_all_reported_together` passou e exige
literalmente seis erros, provando que o gate não para no primeiro recusado.
O próprio job `contracts` passou a executar
`python3 tools/ci/invocability.py`; os validadores de CI e candidato e suas
suítes rejeitam a remoção ou a divergência do inventário.

### 3.3 PostgreSQL imutável

O service `postgres` do job `backend` passou a usar:

```text
postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
```

Banco, usuário, senha sintética, porta e healthcheck permaneceram inalterados.
O validador exige simultaneamente o valor canônico e a forma
`postgres:<tag>@sha256:<64 hex>`. Os testes rejeitam `postgres:latest`, tag de
versão sem digest, digest ausente, imagem divergente e as mutações anteriores
de env, porta e healthcheck.

## 4. Matriz local da Seção 7

Todos os comandos abaixo foram executados depois da implementação, sem rede de
teste, Docker de produção, SSH ou segredo real.

| Comando | Exit | Resultado sanitizado |
|---|---:|---|
| `python3 tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `python3 tools/candidates/validate_candidate_workflow.py` | 0 | `candidate-workflow:valid` |
| `python3 tools/releases/validate_release_workflow.py` | 0 | `release-workflow:valid` |
| `python3 tools/releases/validate_publisher_ui.py` | 0 | `publisher-ui:valid` |
| `python3 tools/releases/validate_publisher_identity_bridge.py` | 0 | `publisher-identity-bridge:valid` |
| `python3 tools/ci/validate_workflow_inventory.py` | 0 | `workflow-inventory:valid` |
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | `rollback-runtime:valid` |
| `python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` |
| `python3 tools/security/bootstrap_contract.py validate` | 0 | `bootstrap-contract:valid` |
| `python3 tools/docker/java_images_contract.py validate` | 0 | `java-images-contract:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v` | 0 | `Ran 30 tests` — `OK` — 6 s |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v` | 0 | `Ran 68 tests` — `OK` — 4 s |
| `GITHUB_RUN_ID=999999999 GITHUB_RUN_ATTEMPT=99 PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v` | 0 | `Ran 68 tests` — `OK` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v` | 0 | `Ran 298 tests` — `OK` — 8 s |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/security/tests -v` | 0 | `Ran 26 tests` — `OK` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -v` | 0 | `Ran 57 tests` — `OK` — 1 s |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -v` | 0 | `Ran 4 tests` — `OK` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/gateway/tests -v` | 0 | `Ran 4 tests` — `OK` |
| `python3 tools/ci/secret_scan.py --tracked` | 0 | `secret-scan:clean:scanned=2429:allowed=80:unsupported=0:history_scanned=9715` — 33 s |
| `git diff --check` | 0 | saída vazia |

## 5. Paridade local dos seis builds

Os builds reproduziram contexto, Dockerfile, `linux/amd64`, `--load`, ausência
de push, tag e build args do job `images`. SHA usado na tag/build args:
`0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`.

| Componente | Contexto / Dockerfile | Exit | Duração | Imagem local |
|---|---|---:|---:|---|
| backend | `backend` / `backend/Dockerfile` | 0 | 147 s | `sha256:131f473f1722d1bf09afe01dda43fd73de26f15733e6734cd0abfb7fa6ae0732` |
| website_back | `website_back` / `website_back/Dockerfile` | 0 | 59 s | `sha256:b69cb6afccfd86d96b8f090ea0df0f91db9c8ce16ac6acf41f86067b5ae1d302` |
| frontend | `frontend` / `frontend/Dockerfile` | 0 | 26 s | `sha256:69061c561e97f679941cd2d89f8da629fafa5319940f5f90b86eb0738d72b382` |
| website_front | `website_front` / `website_front/Dockerfile` | 0 | 75 s | `sha256:6c0c69f9b230f16dc25a7305ef5b51efd0caed9ab43627dd1c2e76ec5415ce4e` |
| whatsapp_service | `whatsapp_service` / `whatsapp_service/Dockerfile` | 0 | 18 s | `sha256:429f76d30d4092bc408c67a09ce414fdb9ae80865fb1ff5c651af09c4808006e` |
| gateway | `ops/gateway` / `ops/gateway/Dockerfile` | 0 | 4 s | `sha256:83cdbb1eec786da10ad460ae2d759a46572dbacf87e7adf2674bef35fe0ae39e` |

Todos foram inspecionados como `linux/amd64`. Não houve `docker login`,
`docker push` ou acesso a GHCR.

## 6. Trivy — gate bloqueante e inventário

O scanner foi executado somente pela imagem oficial fixada:

```text
aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e
```

Template efetivo por imagem:

```text
trivy image --format table --severity HIGH,CRITICAL --ignore-unfixed=false --exit-code 1 --no-progress <imagem-local-exata>
```

Os seis componentes retornaram exit 1. O inventário suplementar em JSON foi
deduplicado por componente, severidade e identificador. Em todos os itens o
banco Trivy informou versão corrigida (`fix_available=yes`); não houve grupo
sem correção disponível.

| Componente | Severidade | Correção disponível | Identificadores únicos |
|---|---|---|---|
| backend | CRITICAL | sim (11/11) | CVE-2025-24813, CVE-2026-22732, CVE-2026-31789, CVE-2026-33845, CVE-2026-40477, CVE-2026-40478, CVE-2026-41293, CVE-2026-41901, CVE-2026-42010, CVE-2026-43512, CVE-2026-43515 |
| backend | HIGH | sim (59/59) | CVE-2024-50379, CVE-2024-56337, CVE-2024-57699, CVE-2025-10492, CVE-2025-15467, CVE-2025-22228, CVE-2025-22235, CVE-2025-32988, CVE-2025-32990, CVE-2025-41249, CVE-2025-48734, CVE-2025-48988, CVE-2025-48989, CVE-2025-49146, CVE-2025-52520, CVE-2025-53506, CVE-2025-55752, CVE-2025-64720, CVE-2025-65018, CVE-2025-66293, CVE-2025-68973, CVE-2025-69421, CVE-2026-1584, CVE-2026-2100, CVE-2026-22184, CVE-2026-22695, CVE-2026-22801, CVE-2026-24734, CVE-2026-24880, CVE-2026-25210, CVE-2026-25646, CVE-2026-28387, CVE-2026-28388, CVE-2026-28389, CVE-2026-28390, CVE-2026-33846, CVE-2026-34483, CVE-2026-3833, CVE-2026-40200, CVE-2026-40973, CVE-2026-41284, CVE-2026-41842, CVE-2026-41845, CVE-2026-41850, CVE-2026-42009, CVE-2026-42198, CVE-2026-42402, CVE-2026-42403, CVE-2026-42498, CVE-2026-43513, CVE-2026-45186, CVE-2026-45447, CVE-2026-54291, CVE-2026-54512, CVE-2026-54513, CVE-2026-56131, CVE-2026-56408, CVE-2026-6009, GHSA-r7wm-3cxj-wff9 |
| website_back | CRITICAL | sim (8/8) | CVE-2025-24813, CVE-2026-22732, CVE-2026-31789, CVE-2026-33845, CVE-2026-41293, CVE-2026-42010, CVE-2026-43512, CVE-2026-43515 |
| website_back | HIGH | sim (72/72) | CVE-2024-50379, CVE-2024-56337, CVE-2024-7254, CVE-2025-15467, CVE-2025-22228, CVE-2025-22235, CVE-2025-24970, CVE-2025-32988, CVE-2025-32990, CVE-2025-41249, CVE-2025-48988, CVE-2025-48989, CVE-2025-49146, CVE-2025-52520, CVE-2025-53506, CVE-2025-55163, CVE-2025-55752, CVE-2025-64720, CVE-2025-65018, CVE-2025-66293, CVE-2025-68973, CVE-2025-69421, CVE-2026-1584, CVE-2026-2100, CVE-2026-22184, CVE-2026-22695, CVE-2026-22801, CVE-2026-24734, CVE-2026-24880, CVE-2026-25210, CVE-2026-25646, CVE-2026-28387, CVE-2026-28388, CVE-2026-28389, CVE-2026-28390, CVE-2026-33846, CVE-2026-33870, CVE-2026-33871, CVE-2026-34483, CVE-2026-3833, CVE-2026-40200, CVE-2026-40973, CVE-2026-41284, CVE-2026-41842, CVE-2026-41845, CVE-2026-41850, CVE-2026-42009, CVE-2026-42198, CVE-2026-42498, CVE-2026-42579, CVE-2026-42583, CVE-2026-42584, CVE-2026-42587, CVE-2026-43513, CVE-2026-44249, CVE-2026-45186, CVE-2026-45416, CVE-2026-45447, CVE-2026-45674, CVE-2026-47691, CVE-2026-50010, CVE-2026-54291, CVE-2026-54512, CVE-2026-54513, CVE-2026-55831, CVE-2026-55833, CVE-2026-56131, CVE-2026-56408, CVE-2026-56745, CVE-2026-56819, CVE-2026-59901, GHSA-r7wm-3cxj-wff9 |
| frontend | CRITICAL | sim (1/1) | CVE-2026-31789 |
| frontend | HIGH | sim (15/15) | CVE-2026-22184, CVE-2026-27135, CVE-2026-28387, CVE-2026-28388, CVE-2026-28389, CVE-2026-28390, CVE-2026-33630, CVE-2026-40200, CVE-2026-45186, CVE-2026-45447, CVE-2026-56131, CVE-2026-56408, CVE-2026-5773, CVE-2026-6276, CVE-2026-6732 |
| website_front | CRITICAL | sim (1/1) | CVE-2026-31789 |
| website_front | HIGH | sim (15/15) | CVE-2026-22184, CVE-2026-27135, CVE-2026-28387, CVE-2026-28388, CVE-2026-28389, CVE-2026-28390, CVE-2026-33630, CVE-2026-40200, CVE-2026-45186, CVE-2026-45447, CVE-2026-56131, CVE-2026-56408, CVE-2026-5773, CVE-2026-6276, CVE-2026-6732 |
| whatsapp_service | CRITICAL | sim (2/2) | CVE-2026-31789, CVE-2026-59873 |
| whatsapp_service | HIGH | sim (29/29) | CVE-2024-12905, CVE-2024-37890, CVE-2025-48387, CVE-2025-59343, CVE-2025-64756, CVE-2026-13149, CVE-2026-14257, CVE-2026-22184, CVE-2026-23745, CVE-2026-23950, CVE-2026-24842, CVE-2026-25547, CVE-2026-26960, CVE-2026-26996, CVE-2026-27903, CVE-2026-27904, CVE-2026-28387, CVE-2026-28388, CVE-2026-28389, CVE-2026-28390, CVE-2026-29786, CVE-2026-31802, CVE-2026-33671, CVE-2026-40200, CVE-2026-45447, CVE-2026-4867, CVE-2026-48779, CVE-2026-48815, CVE-2026-59874 |
| gateway | HIGH | sim (11/11) | CVE-2026-22184, CVE-2026-27135, CVE-2026-33630, CVE-2026-40200, CVE-2026-45186, CVE-2026-45447, CVE-2026-56131, CVE-2026-56408, CVE-2026-5773, CVE-2026-6276, CVE-2026-6732 |

O inventário contabiliza 224 ocorrências componente/identificador após a
deduplicação interna de pacotes. Repetições entre componentes foram mantidas,
pois a decisão de correção precisa conhecer cada imagem afetada.

## 7. Cleanup e resíduos

- As seis tags `abaronesa-emporio-ci-*:ci-0bd563b...` foram removidas.
- Cada uma foi consultada com `docker image inspect` e resultou ausente.
- A imagem Trivy fixada, baixada por esta execução, foi removida e verificada
  ausente.
- O cache temporário `/tmp/emporio-s30a-trivy.Etu3OE` foi removido. Como os
  arquivos do banco pertenciam a root no container, a limpeza final foi feita
  por um container efêmero `nginx:stable-alpine` já existente no host; ele usou
  `--rm` e não deixou container ou imagem nova.
- O cache BuildKit era `0B` antes da execução, chegou a `4.246GB`, foi removido
  com `docker buildx prune --force` e voltou a `0B`.
- `docker system df` voltou a `27` imagens e `7.535GB`, os mesmos valores do
  preflight; nenhum volume foi criado.

A limpeza removida era integralmente local e criada nesta execução: seis
imagens CI, imagem/cache Trivy e cache BuildKit. Não houve cleanup remoto.

## 8. Git, stage e fronteira de parada

```text
HEAD          0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06
origin/main   0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06
novo commit   não criado
push          não executado
staged list   não criada
```

Como o gate Trivy falhou, as condições para o pre-commit da Seção 4.5 não
existem. Por isso não foram executados `git add`, revisão staged nem
`git diff --cached --check`; executar esses passos depois do bloqueio violaria
a ordem fechada. `git diff --check` do working tree retornou 0.

Não há run remoto da S30a, job, step, `candidate-manifest`,
`candidate-outcome`, digest publicado, provenance ou attestation a cruzar. A
ausência histórica de `read:packages` permanece registrada no HANDOFF; esta
execução não chegou à fronteira GHCR, não criou credencial e não tentou
reautenticação.

## 9. Acessos externos e efeitos negativos

Houve somente acesso de Docker local aos registries públicos necessários aos
builds e ao scanner: resolução/pull da imagem Trivy no Docker Hub e download
dos bancos Trivy por `mirror.gcr.io`. Não houve GitHub API, GHCR, `docker
login`, `docker push`, release, tag, workflow dispatch, SSH, VPS, deploy,
rollback, DNS, Nginx de produção ou qualquer efeito de produção.

`publish-release.yml`, `deploy-production.yml` e `rollback-production.yml` não
foram executados. S30b e S31 não foram criadas.

## 10. Causa, fronteira e decisão pendente

- **Gate:** paridade local Trivy da Seção 4.4.
- **Passo:** scan de cada uma das seis imagens comerciais.
- **Saída sanitizada:** exit 1 em todos os componentes; inventário completo na
  Seção 6.
- **Causa:** imagens e dependências atuais possuem achados HIGH/CRITICAL sob a
  política canônica; todos têm correção indicada pelo banco usado.
- **Fronteira:** Dockerfiles, imagens-base, POMs, manifests npm e política Trivy
  estão fora da Seção 5. Nenhuma correção foi improvisada.
- **Decisão humana necessária:** definir a correção dos componentes/dependências
  e a nova fronteira antes de autorizar commit/push da S30a.

## 11. Amendment-01 — atualização das imagens-base

### 11.1 Autoridade, fronteira e referências aplicadas

Não existe artefato `S30a-*.amendment-01.md` no checkout. A instrução direta
do orquestrador recebida nesta execução foi tratada como a emenda completa e
como autoridade da execução; nenhum documento de task ou emenda foi criado.

Somente as linhas `FROM` dos seis Dockerfiles foram alteradas. POMs,
`package.json`, `package-lock.json`, código de aplicação, política Trivy,
usuários, healthchecks e estrutura multi-stage permaneceram inalterados.

| Família | Referência anterior | Referência aplicada |
|---|---|---|
| Maven / Java 21 | `maven:3.9.11-eclipse-temurin-21-alpine@sha256:922927df2c662cdd47ddb116443d6bec4696cfae3de1a0ddac8fcc7b87ce61ae` | `maven:3.9.16-eclipse-temurin-21-alpine@sha256:d88e5b38297858f65f97bc7e7964c760ab988fd18ace41589176f1468c49a489` |
| Temurin / Java 21 | `eclipse-temurin:21.0.8_9-jre-alpine-3.22@sha256:990397e0495ac088ab6ee3d949a2e97b715a134d8b96c561c5d130b3786a489d` | `eclipse-temurin:21.0.11_10-jre-alpine-3.23@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c` |
| Node 24 / Alpine | `node:24.13.0-alpine3.23@sha256:cd6fb7efa6490f039f3471a189214d5f548c11df1ff9e5b181aa49e22c14383e` | `node:24.18.1-alpine3.24@sha256:f70403e87646dc51b45295f4b8b70cdad0b63d2297c4c9899119b03f7af7a6b3` |
| nginx 1.x / Alpine | `nginx:1.29.5-alpine3.23@sha256:1eff5a5f3fcf8431a0abb7eddf5471fec24e5e1905a2581aeacdb07a4479b92b` | `nginx:1.31.3-alpine3.24@sha256:4a73073bd557c65b759505da037898b61f1be6cbcc3c2c3aeac22d2a470c1752` |
| nginx unprivileged 1.x / Alpine | `nginxinc/nginx-unprivileged:1.29.5-alpine3.23@sha256:42a7d7f2ee23e9f5a1dcdf3647ba5c585bbd18f79e79cd817e70e8cd61c55779` | `nginxinc/nginx-unprivileged:1.31.3-alpine3.24@sha256:59ccf0943b0b8e8d9e6ea9039a39555730f544701a655c596f7df7d096c593f5` |

As referências foram resolvidas nos manifests oficiais do Docker Hub para
`linux/amd64`. Para Temurin, `3.23` é a variante Alpine publicada para o patch
Java 21 selecionado; não havia variante `3.24` dessa tag.

### 11.2 Nova paridade dos seis builds

Os seis builds reproduziram a Seção 4.4 com `docker buildx build`, plataforma
`linux/amd64`, `--load`, os build args canônicos e sem login ou push.

| Componente | Exit | Duração | Imagem local produzida |
|---|---:|---:|---|
| backend | 0 | 90 s | `sha256:0c20e089b4937ca572c2949981f385dc221faa8582918c57d117a7423d77816c` |
| website_back | 0 | 55 s | `sha256:c2d7b014aed9440f07aba2d07ddb0dd7941998defc2135d5492b137a8064081d` |
| frontend | 0 | 28 s | `sha256:d50ce7747307fc40b5188fce8a24255530ddeceaa64dae9ad93bea21772fc66b` |
| website_front | 0 | 70 s | `sha256:7714d448a7879ea3b7ecb09c1ea9c0fe1ce401f9e702bd1633cc6e98726caef1` |
| whatsapp_service | 0 | 19 s | `sha256:166dabce0f062c766e857d20a12c69b8a8e63c5d76fe67437bd3ee4e7ddaeed6` |
| gateway | 0 | 4 s | `sha256:13be69c3de1977db0210dbb89906dd258d2e64adaec095534e3658c753676a06` |

Todas as imagens inspecionadas reportaram `linux/amd64`.

### 11.3 Novo inventário Trivy e delta contra 224 ocorrências

Foi mantida exatamente a imagem oficial fixada e o template da Seção 6. Os
scans de `frontend`, `website_front` e `gateway` ficaram limpos (exit 0). Os
scans de `backend`, `website_back` e `whatsapp_service` retornaram exit 1.
Todas as 110 ocorrências remanescentes têm correção disponível no banco usado.

| Componente | Severidade | Correção disponível | Identificadores únicos |
|---|---|---|---|
| backend | CRITICAL | sim (8/8) | CVE-2025-24813, CVE-2026-22732, CVE-2026-40477, CVE-2026-40478, CVE-2026-41293, CVE-2026-41901, CVE-2026-43512, CVE-2026-43515 |
| backend | HIGH | sim (35/35) | CVE-2024-50379, CVE-2024-56337, CVE-2024-57699, CVE-2025-10492, CVE-2025-22228, CVE-2025-22235, CVE-2025-41249, CVE-2025-48734, CVE-2025-48988, CVE-2025-48989, CVE-2025-49146, CVE-2025-52520, CVE-2025-53506, CVE-2025-55752, CVE-2026-2100, CVE-2026-24734, CVE-2026-24880, CVE-2026-34483, CVE-2026-40973, CVE-2026-41284, CVE-2026-41842, CVE-2026-41845, CVE-2026-41850, CVE-2026-42198, CVE-2026-42402, CVE-2026-42403, CVE-2026-42498, CVE-2026-43513, CVE-2026-54291, CVE-2026-54512, CVE-2026-54513, CVE-2026-56131, CVE-2026-56408, CVE-2026-6009, GHSA-r7wm-3cxj-wff9 |
| website_back | CRITICAL | sim (5/5) | CVE-2025-24813, CVE-2026-22732, CVE-2026-41293, CVE-2026-43512, CVE-2026-43515 |
| website_back | HIGH | sim (48/48) | CVE-2024-50379, CVE-2024-56337, CVE-2024-7254, CVE-2025-22228, CVE-2025-22235, CVE-2025-24970, CVE-2025-41249, CVE-2025-48988, CVE-2025-48989, CVE-2025-49146, CVE-2025-52520, CVE-2025-53506, CVE-2025-55163, CVE-2025-55752, CVE-2026-2100, CVE-2026-24734, CVE-2026-24880, CVE-2026-33870, CVE-2026-33871, CVE-2026-34483, CVE-2026-40973, CVE-2026-41284, CVE-2026-41842, CVE-2026-41845, CVE-2026-41850, CVE-2026-42198, CVE-2026-42498, CVE-2026-42579, CVE-2026-42583, CVE-2026-42584, CVE-2026-42587, CVE-2026-43513, CVE-2026-44249, CVE-2026-45416, CVE-2026-45674, CVE-2026-47691, CVE-2026-50010, CVE-2026-54291, CVE-2026-54512, CVE-2026-54513, CVE-2026-55831, CVE-2026-55833, CVE-2026-56131, CVE-2026-56408, CVE-2026-56745, CVE-2026-56819, CVE-2026-59901, GHSA-r7wm-3cxj-wff9 |
| frontend | HIGH/CRITICAL | não aplicável (0) | nenhum |
| website_front | HIGH/CRITICAL | não aplicável (0) | nenhum |
| whatsapp_service | CRITICAL | sim (1/1) | CVE-2026-59873 |
| whatsapp_service | HIGH | sim (13/13) | CVE-2024-12905, CVE-2024-37890, CVE-2025-48387, CVE-2025-59343, CVE-2026-12151, CVE-2026-13149, CVE-2026-14257, CVE-2026-26996, CVE-2026-27903, CVE-2026-27904, CVE-2026-4867, CVE-2026-48779, CVE-2026-59874 |
| gateway | HIGH/CRITICAL | não aplicável (0) | nenhum |

| Componente | CRITICAL antes → agora | Delta | HIGH antes → agora | Delta | Total antes → agora | Delta |
|---|---:|---:|---:|---:|---:|---:|
| backend | 11 → 8 | -3 | 59 → 35 | -24 | 70 → 43 | -27 |
| website_back | 8 → 5 | -3 | 72 → 48 | -24 | 80 → 53 | -27 |
| frontend | 1 → 0 | -1 | 15 → 0 | -15 | 16 → 0 | -16 |
| website_front | 1 → 0 | -1 | 15 → 0 | -15 | 16 → 0 | -16 |
| whatsapp_service | 2 → 1 | -1 | 29 → 13 | -16 | 31 → 14 | -17 |
| gateway | 0 → 0 | 0 | 11 → 0 | -11 | 11 → 0 | -11 |
| **Total** | **23 → 14** | **-9** | **201 → 96** | **-105** | **224 → 110** | **-114** |

No `whatsapp_service`, `CVE-2026-12151` entrou no inventário e 17
identificadores saíram; nos demais grupos não houve identificador novo. O
resultado reduz o inventário em 114 ocorrências, mas não fecha o gate por
permanecerem ocorrências HIGH/CRITICAL.

### 11.4 Matriz da Seção 7 e fronteiras bloqueadas

Os onze validadores anteriores ao contrato Java passaram. O comando
`python3 tools/docker/java_images_contract.py validate` retornou exit 2 com:

```text
java-images-contract:invalid:BASE_TAG_INVALID:backend
java-images-contract:invalid:BASE_TAG_INVALID:website_back
```

| Suíte / verificação | Resultado |
|---|---|
| `tools/ci/tests` | 30 testes, exit 0 |
| `tools/candidates/tests` | 68 testes, exit 0 |
| `tools/candidates/tests` com IDs hostis | 68 testes, exit 0 |
| `tools/releases/tests` | 298 testes, exit 0 |
| `tools/security/tests` | 26 testes, exit 0 |
| `tools/docker/tests` | 57 testes, **exit 1; 7 falhas** |
| `tools/compose/tests` | 4 testes, exit 0 |
| `tools/gateway/tests` | 4 testes, exit 0 |
| scanner de segredos `--tracked` | exit 0; `scanned=2429`, `allowed=80`, `unsupported=0`, `history_scanned=9715` |
| `git diff --check` | exit 0 |

As sete falhas Docker são causadas por âncoras das referências antigas em
`java_images_contract.py`, `validate_node_images.py` e seus testes: duas
falhas Java de contrato/âncora e cinco falhas Node/nginx de contrato/mutantes.
Esses arquivos não são um dos seis Dockerfiles nem o relatório e, portanto,
ficam fora da fronteira expressamente autorizada pela emenda. Eles não foram
alterados para acomodar as novas tags.

Há dois gates locais bloqueantes:

1. **Trivy / três imagens:** scans de `backend`, `website_back` e
   `whatsapp_service`, exit 1, devido às 110 ocorrências inventariadas acima;
   a fronteira seria dependências de aplicação ou nova decisão sobre bases e
   política, todas não autorizadas nesta execução.
2. **Matriz Docker:** validador Java exit 2 e suíte Docker exit 1, porque os
   contratos hardcoded ainda exigem as referências anteriores; a fronteira
   seria alterar validadores/testes fora dos caminhos permitidos.

Conforme a ordem fechada, o pre-commit não foi aberto: a lista staged permaneceu
vazia, os dois commits condicionais não foram criados e nenhum push foi feito.
Não houve `force`, tag, outro remote/branch, `no-verify` ou execução remota.

### 11.5 Cleanup da emenda

As seis tags e imagens produzidas, a imagem Trivy fixada e o diretório
`/tmp/emporio-s30a-amendment-trivy.P1H7kp` foram removidos. O prune do BuildKit
foi executado e a verificação final mostrou `Build Cache 38 / 0B`; `docker
system df` voltou a `27` imagens e `7.535GB`. Não restou imagem
`abaronesa-emporio-ci-*` nem `aquasec/trivy` da execução.

## 12. Amendment-02 — validadores Docker e patch de dependências

### 12.1 Autoridade, estado inicial e fronteira

Foram lidos a task S30a, a amendment-02, a amendment-01 preservada na Seção 11
deste relatório e o HANDOFF Seção 32. Não existe arquivo autônomo da
amendment-01 no checkout. A amendment-02 está em estado `AUTHORIZED` e mantém
como commit-base `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`.

Esta execução alterou somente:

- `tools/docker/java_images_contract.py`;
- `tools/docker/validate_node_images.py`;
- `tools/docker/tests/test_java_images_contract.py`;
- `tools/docker/tests/test_validate_node_images.py`;
- `backend/pom.xml`;
- `website_back/pom.xml`;
- `whatsapp_service/package-lock.json`;
- este relatório.

Os seis Dockerfiles da amendment-01 foram preservados. `package.json`, código
de aplicação, workflows, política Trivy e `.trivyignore` não foram alterados.

### 12.2 Correção A — referências e mutantes Docker

O contrato Java passou a exigir por igualdade as referências completas de
Maven e Temurin, incluindo os digests da amendment-01. O contrato Node passou
a exigir as referências completas de Node e nginx da amendment-01. Permanecem
rejeitados digest ausente ou divergente, alias flutuante, `latest`, Node 18,
Java fora da linha 21 e base nginx divergente da linha aprovada.

Foram acrescentados mutantes que substituem as bases aprovadas pelas
referências anteriores: Maven no contrato Java e Node/nginx no contrato Node.
Todos foram recusados. A prova focal final foi:

```text
python3 tools/docker/java_images_contract.py validate    exit 0
python3 tools/docker/validate_node_images.py validate    exit 0
test_java_images_contract + test_validate_node_images    59 testes, exit 0
```

### 12.3 Correção B — dependências efetivas

Os dois parents Spring Boot foram atualizados somente de `3.3.5` para
`3.3.13`. O lockfile do WhatsApp foi recalculado sem mudar os intervalos de
`package.json`; as resoluções diretas finais são `express 4.22.2`, `qrcode
1.5.4` e `whatsapp-web.js 1.34.7`.

O primeiro scan ainda mostrou transitivos corrigíveis dentro da mesma linha
maior. Foram então fixadas versões por propriedades gerenciadas ou
`dependencyManagement`, sem adicionar dependência ao grafo:

| Componente | Dependências efetivas fixadas |
|---|---|
| backend | PostgreSQL `42.7.12`; Jackson `2.18.8`; Tomcat `10.1.55`; Thymeleaf `3.1.5.RELEASE`; Commons BeanUtils `1.11.0`; Neethi `3.2.2` |
| website_back | PostgreSQL `42.7.12`; Jackson `2.18.8`; Tomcat `10.1.55`; protobuf-java `3.25.5`; grpc-netty-shaded `1.75.0`; Netty `4.1.136.Final` |

Não foram aplicados Spring Boot `3.5`, Spring Framework `6.2`, Spring Security
`6.5` nem JasperReports `7`, pois isso ultrapassaria a linha menor fechada ou a
linha maior aprovada pela emenda.

### 12.4 Rede de regressão com PostgreSQL fixado

O host já possuía um PostgreSQL preexistente escutando em
`127.0.0.1:5432`. Ele não foi interrompido. O container desta execução usou o
digest canônico na porta interna `5432`, publicada somente em
`127.0.0.1:55432`; as regressões receberam explicitamente a URL desse
container:

```text
postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
POSTGRES_DB=testdb POSTGRES_USER=test POSTGRES_PASSWORD=test
status=running health=healthy host=127.0.0.1:55432 container=5432
```

Depois da versão final dos POMs/lockfile:

| Componente / comando | Resultado |
|---|---|
| `backend: mvn -B verify` | exit 0; 82 testes; 19,98 s |
| `website_back: mvn -B verify` | exit 0; 63 testes; 5,34 s |
| `whatsapp_service: npm ci` | exit 0; 10,40 s; `found 0 vulnerabilities` |
| `whatsapp_service: npm run test` | exit 0; 7 testes; 0,45 s |

O npm do host executou em Node `22.20.0` e informou `EBADENGINE` porque o
projeto exige Node 24; não houve falha, e a imagem construída/testada conserva
Node 24. Nenhum componente exigiu reversão de dependência.

### 12.5 Paridade final dos seis builds

Os seis componentes foram construídos com `docker buildx build`, plataforma
`linux/amd64`, `--load`, `push=false` e os build args da matriz. Backend e
website_back foram reconstruídos após cada ajuste transitivo; a tabela registra
somente a imagem final correspondente ao working tree final.

| Componente | Exit | Duração final | ID final |
|---|---:|---:|---|
| backend | 0 | 20 s | `sha256:7bc7fe2fe68c166b597784e43d70657fbfeae53a191d43763cf81eaf305d3551` |
| website_back | 0 | 10 s | `sha256:e95f2706912abcae4add0f1bdffd344a46d424ad894a1fef08ae97e6280c611a` |
| frontend | 0 | 25 s | `sha256:65d86341c2ab26a4d78c4e468203f3cead070c6fad704ced4b19c535cc8ec218` |
| website_front | 0 | 65 s | `sha256:13148fbda0451b7b42811c05a9f614679e7dad5ad31ca589c8030d403e9e85ca` |
| whatsapp_service | 0 | 35 s | `sha256:9edb62b5daa77e60732c39a739da8082f31cdeae08b002ff8ef6e7b8dca341a2` |
| gateway | 0 | 5 s | `sha256:83ba2e12b02c348b41fb00aa8db6b94e4c6b2d98bd252d5ca032498fcc90216a` |

Todas foram inspecionadas como `linux/amd64`.

### 12.6 Trivy — inventário final e delta contra 110

Foi usada exclusivamente a imagem oficial fixada da Seção 6, mantendo
`HIGH,CRITICAL`, `ignore-unfixed=false` e `exit-code=1`. `frontend`,
`website_front` e `gateway` ficaram limpos. `backend`, `website_back` e
`whatsapp_service` retornaram exit 1.

| Componente | Severidade | Correção disponível | Identificadores únicos |
|---|---|---|---|
| backend | CRITICAL | sim (1/1) | CVE-2026-22732 |
| backend | HIGH | sim (10/10) | CVE-2025-10492, CVE-2025-41249, CVE-2026-2100, CVE-2026-40973, CVE-2026-41842, CVE-2026-41845, CVE-2026-41850, CVE-2026-56131, CVE-2026-56408, CVE-2026-6009 |
| website_back | CRITICAL | sim (1/1) | CVE-2026-22732 |
| website_back | HIGH | sim (8/8) | CVE-2025-41249, CVE-2026-2100, CVE-2026-40973, CVE-2026-41842, CVE-2026-41845, CVE-2026-41850, CVE-2026-56131, CVE-2026-56408 |
| frontend | HIGH/CRITICAL | não aplicável (0) | nenhum |
| website_front | HIGH/CRITICAL | não aplicável (0) | nenhum |
| whatsapp_service | CRITICAL | sim (1/1) | CVE-2026-59873 |
| whatsapp_service | HIGH | sim (4/4) | CVE-2026-12151, CVE-2026-13149, CVE-2026-14257, CVE-2026-59874 |
| gateway | HIGH/CRITICAL | não aplicável (0) | nenhum |

| Componente | CRITICAL 110 → final | Delta | HIGH 110 → final | Delta | Total 110 → final | Delta |
|---|---:|---:|---:|---:|---:|---:|
| backend | 8 → 1 | -7 | 35 → 10 | -25 | 43 → 11 | -32 |
| website_back | 5 → 1 | -4 | 48 → 8 | -40 | 53 → 9 | -44 |
| frontend | 0 → 0 | 0 | 0 → 0 | 0 | 0 → 0 | 0 |
| website_front | 0 → 0 | 0 | 0 → 0 | 0 | 0 → 0 | 0 |
| whatsapp_service | 1 → 1 | 0 | 13 → 4 | -9 | 14 → 5 | -9 |
| gateway | 0 → 0 | 0 | 0 → 0 | 0 | 0 → 0 | 0 |
| **Total** | **14 → 3** | **-11** | **96 → 22** | **-74** | **110 → 25** | **-85** |

As 25 ocorrências finais têm correção indicada, mas as correções disponíveis
exigem uma das fronteiras não autorizadas: Spring Boot/Spring Framework/Spring
Security em linha menor diferente, JasperReports 7, atualização de pacotes da
base Alpine ou alteração do npm embarcado na base Node. A política Trivy não
foi alterada e `.trivyignore` não foi criado.

### 12.7 Matriz local final

Os doze validadores da Seção 7 retornaram exit 0. O validador Node adicional
também retornou exit 0.

| Suíte / verificação | Resultado |
|---|---|
| `tools/ci/tests` | 30 testes, exit 0 |
| `tools/candidates/tests` | 68 testes, exit 0 |
| `tools/candidates/tests` com IDs hostis | 68 testes, exit 0 |
| `tools/releases/tests` | 298 testes, exit 0 |
| `tools/security/tests` | 26 testes, exit 0 |
| `tools/docker/tests` da matriz | 59 testes, exit 0 |
| `tools/compose/tests` | 4 testes, exit 0 |
| `tools/gateway/tests` | 4 testes, exit 0 |
| `tools/docker/tests` adicional da emenda | 59 testes, exit 0 |
| scanner de segredos `--tracked` | exit 0; `scanned=2429`, `allowed=80`, `unsupported=0`, `history_scanned=9715` |
| `git diff --check` | exit 0 |

### 12.8 Cleanup, Git e parada

O container PostgreSQL foi parado e removido com seu volume anônimo. Foram
removidas as seis imagens finais, quatro imagens intermediárias substituídas,
a imagem PostgreSQL, a imagem Trivy e o cache Trivy. O prune do BuildKit
removeu `6.675GB`; a verificação final mostrou:

```text
Images          27        1         7.535GB
Containers       1        1         63B
Local Volumes   19        1         1.081GB
Build Cache     38        0         0B
```

Não restou container `emporio-s30a-amendment02-postgres`, imagem
`abaronesa-emporio-ci-*`, imagem Trivy/PostgreSQL desta execução ou diretório
temporário. O PostgreSQL preexistente em `127.0.0.1:5432` permaneceu intacto.

O gate Trivy final falhou nos três componentes inventariados. Portanto, a
condição “se tudo passar” não ocorreu: a lista staged permaneceu vazia, nenhum
dos três commits condicionais foi criado e nenhum push foi executado. Não houve
force, tag, outro remote/branch, `no-verify`, release, deploy, rollback, SSH ou
efeito de produção.

IN_PROGRESS — aguardando revisão do orquestrador

## 13. Revisão terminal do orquestrador — amendment-02

> **Data:** 01/08/2026  
> **Resultado da amendment-02:** `ACCEPTED`  
> **Estado da S30a:** `IN_PROGRESS`

A amendment-02 cumpriu integralmente sua fronteira. Os validadores Docker e
seus mutantes passaram com 59 testes; as regressões reais passaram com 82
testes no backend, 63 no website_back e 7 no WhatsApp; os seis builds foram
concluídos; a matriz local retornou exit 0; e o cleanup preservou o PostgreSQL
preexistente e devolveu o BuildKit a `0B`.

O resíduo Trivy caiu de 110 para 25 ocorrências. Essa parada não é falha do
executor: a política Trivy e as linhas de dependência restantes estavam fora da
autoridade recebida. O aceite desta amendment não aceita a S30a, não autoriza
exceção de segurança e não autoriza push.

A continuidade foi autorizada em
[S30a — autorização de preservação e prova técnica do resíduo Trivy](./S30a-paridade-local-fechamento-ci-candidato.authorization-01.md).

IN_PROGRESS — preservação local e prova técnica autorizadas
