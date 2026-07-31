# S12 — Publicação de candidato e proveniência — relatório de execução

> **Estado:** `ACCEPTED — 29/07/2026`  
> **Data:** 29/07/2026  
> **CWD obrigatório:** `/home/gregorio/git/baronesa/emporio`

## 1. Estado inicial e limites

A execução iniciou sem `HEAD`, tags, reflog ou conteúdo no índice Git real,
com `origin` canônico e somente `ci.yml` ativo. A S11 estava `ACCEPTED`.

Foram respeitadas as exclusões: não houve `git add`, commit, tag, push, login
ou push local no GHCR, workflow remoto, Maven, NPM, build Docker, Trivy,
Compose candidato real, release, deploy, GitHub privado ou acesso à VPS. A
task S12 e o tracker não foram alterados; a S13 não foi criada.

## 2. Arquivos criados

- `.github/workflows/publish-candidate.yml`;
- `ops/compose/testing/compose.candidate.yml`;
- `tools/candidates/assemble_candidate.py`;
- `tools/candidates/candidate_plan.py`;
- `tools/candidates/compose_env.py`;
- `tools/candidates/image_result.py`;
- `tools/candidates/integrated_harness.py`;
- `tools/candidates/previous_candidate.py`;
- `tools/candidates/probe_candidate.py`;
- `tools/candidates/validate_candidate_workflow.py`;
- `tools/candidates/tests/test_candidate_workflow.py`;
- `docs/infrastructure/deployment/release-control/CANDIDATOS.md`;
- este relatório.

## 3. Arquivos alterados

- `.github/workflows/ci.yml`;
- `.github/workflows/README.md`;
- `ops/releases/candidate-manifest.schema.json`;
- `ops/releases/examples/candidate-manifest.example.json`;
- `tools/ci/resolve_changes.py`;
- `tools/ci/validate_ci.py`;
- `tools/releases/candidate_manifest.py`;
- `tools/releases/tests/test_candidate_manifest.py`;
- `docs/infrastructure/deployment/ci/CI.md`;
- `docs/infrastructure/deployment/release-control/README.md`.

## 4. Grafo, fronteira e permissões

O grafo implementado é:

```text
workflow_run -> trust -> previous -> build seletivo -> assemble
                                                \-> integrated -> publish_manifest
```

`trust` é somente leitura e, antes do checkout, valida payload e consulta pela
API canônica o run e seus artifacts. São exigidos: CI verde, evento `push`,
branch `main`, repositório canônico, SHA lowercase completo e exatamente um
`candidate-plan` não expirado pertencente ao run. Depois, checkout explícito
do SHA, prova de ancestralidade em `origin/main`, download ligado ao run e
revalidação de SHA, run, ref, catálogo e resolução.

Permissões globais: `contents: read`, `actions: read`, `packages: read`.
Somente `build`, posterior a `trust` e `previous`, recebe `packages: write`,
`id-token: write` e `attestations: write`. Nenhum job recebe escrita em
contents.

## 5. Plano e matriz seletiva

Em `ci.yml`, o plano determinístico é gerado, validado e enviado somente
quando `github.event_name == 'push'` e `github.ref == 'refs/heads/main'`.
Contém versão, repositório, SHA, ref, run/attempt, SHA-256 do catálogo e a
resolução S05 integral. Nome fixo `candidate-plan`, retenção de 7 dias,
ausência de overwrite e falha se o arquivo faltar.

A matriz é derivada apenas de `buildComponents`, na ordem canônica. O primeiro
candidato exige seis componentes; matriz vazia pula `build` com segurança e
prossegue somente quando os seis componentes podem ser herdados.

## 6. Candidato anterior

A descoberta consulta no máximo 10 páginas de runs verdes de
`publish-candidate.yml`, ignora o SHA corrente, exige evento `workflow_run` e
um único artifact `candidate-manifest` válido. O ZIP deve conter exatamente
manifesto, sidecar e metadata. Shape, checksum, schema, identidade, BOM e
provenance são validados antes da seleção. Ausência é explícita e só é aceita
para primeiro candidato; origem ausente, autorreferente ou circular falha
fechada.

## 7. Build, scan, push e attestation

Cada componente afetado é construído uma única vez para `linux/amd64` com
`load: true` e `push: false`. A imagem exata é examinada pelo Trivy antes de
qualquer autenticação, com bloqueio para HIGH/CRITICAL. Somente depois ocorre
login e `docker push` da mesma tag SHA. O digest remoto é capturado e
normalizado para referência imutável.

A provenance é gerada para o mesmo repository/digest, enviada ao registry e
verificada por `gh attestation verify`. Apenas o resultado marcado
`verification: verified` entra na montagem.

Isso está **CONFIGURADO, NÃO EXECUTADO**. Nenhuma imagem, digest ou attestation
real foi produzida nesta slice.

## 8. Provenance e BOM completo

Schema, fixture, gerador e testes agora exigem provenance em cada componente.
Itens `built` vinculam provider, run, SHA, subject repository/digest,
attestation ID/URL e verificação. Itens `inherited` preservam referência
imutável e provenance do candidato de origem. Divergências, propriedades
extras, provenance ausente ou não verificada são rejeitadas.

O manifesto permanece com exatamente seis componentes e
`deployable: false`.

## 9. Harness integrado

O override usa exclusivamente seis variáveis de imagem por digest, nomes de
projeto/rede/volume exclusivos, PostgreSQL fixado por digest, URLs `.invalid`,
segredos efêmeros e WhatsApp sem inicialização externa. Somente o gateway
publica porta, em `127.0.0.1`.

O harness configura, sobe sete serviços com `--no-build --wait`, confirma
publicação exclusiva do gateway, atravessa os frontends, autentica no ERP,
prova o upstream WhatsApp e rejeita rotas de controle/host desconhecido. O
`finally` remove somente o projeto exato e falha se restarem containers,
volumes ou redes dele.

Isso está **CONFIGURADO, NÃO EXECUTADO**; nenhum Compose candidato foi iniciado
localmente.

## 10. Artifacts e retenção

- `candidate-plan`: 7 dias;
- candidato anterior e resultados de componente: 1 dia;
- candidato pendente de integração: 1 dia;
- `candidate-manifest` final: 30 dias.

O artifact final contém somente `candidate.json`, sidecar SHA-256 e
`metadata.json`, e só é enviado após montagem e integração verdes. Não há
Git tag, GitHub Release, release global ou deploy.

## 11. Actions e ferramentas fixadas

Pins confirmados por consulta somente leitura às fontes oficiais:

- `actions/checkout` v6.0.2:
  `de0fac2e4500dabe0009e67214ff5f5447ce83dd`;
- `actions/upload-artifact` v4.6.2:
  `ea165f8d65b6e75b540449e92b4886f43607fa02`;
- `actions/download-artifact` v5.0.0:
  `634f93cb2916e3fdff6788551b99b062d0335ce0`;
- `docker/setup-buildx-action` v4.0.0:
  `4d04d5d9486b7bd6fa91e7baf45bbb4f8b9deedd`;
- `docker/build-push-action` v7.0.0:
  `d08e5c354a6adb9ed34480a06d141179aa583294`;
- `aquasecurity/trivy-action` v0.36.0:
  `ed142fd0673e97e23eac54620cfb913e5ce36c25`;
- `docker/login-action` v3.6.0:
  `5e57cd118135c172c3672efd75eb46360885c0ef`;
- `actions/attest-build-provenance` v3.0.0:
  `977bb373ede98d70efdf65b84cb5f73e068dcc2a`.

O PostgreSQL do harness foi fixado em `16.10-alpine3.22` por SHA-256. O
`actionlint` foi executado pela imagem fixada
`sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9`.

## 12. Validações e comandos

Todos os comandos tiveram CWD
`/home/gregorio/git/baronesa/emporio`.

| Comando exato | Exit | Resultado |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v` | 0 | 9 testes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/candidates/validate_candidate_workflow.py` | 0 | `candidate-workflow:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v` | 0 | 7 testes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json` | 0 | `candidate:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v` | 0 | 141 testes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py validate --require-release-ready` | 0 | `catalog:valid` |
| `docker run --rm -v "$PWD:/repo:ro" -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color .github/workflows/ci.yml .github/workflows/publish-candidate.yml` | 0 | dois workflows aprovados |
| `docker image rm docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 0 | imagem efêmera removida |

Total das suítes: **157 testes aprovados**.

## 13. Mutantes

Os mutantes cobrem trigger manual, CI não verde, PR/fork, branch/repositório
divergentes, SHA curto, permissão global de escrita, action mutável, login
antes do scan, build com push, tag latest, SSH/release, ausência de
attestation/verificação, job extra, plano em PR, retenção inválida, imagem por
tag, bind público, nomes estáticos e provenance ausente/divergente/não
verificada.

Os testes de manifesto também preservam determinismo, checksum, rollback do
par, primeiro candidato, matriz vazia, herança exata, autorreferência,
circularidade dirigida e validação causal.

## 14. Falhas intermediárias e correções

1. O primeiro validador do harness esperava oito ocorrências literais de
   `${CANDIDATE_PROJECT}`, mas o contrato possui sete, incluindo a forma
   obrigatória `${CANDIDATE_PROJECT:?candidate project required}`. A validação
   passou a contar as sete formas sem perder o mutante de nome estático.
2. Na primeira bateria, três mutantes (`push: true`, `latest` e `ssh`) não
   foram rejeitados porque a regex usava escapes duplicados. A expressão foi
   corrigida; a suíte de 7 testes passou integralmente na repetição.
3. Foram removidos dois arquivos `.pyc` preexistentes para entregar o estado
   obrigatório sem caches Python. Nenhum arquivo-fonte foi alterado por essa
   limpeza.

## 15. Documentação

Foram alinhados o inventário dos dois workflows, a separação CI/candidato, a
fronteira confiável, matriz seletiva, provenance, herança, harness, retenções e
a distinção entre configuração local e execução remota. A documentação
mantém explícito que readiness verde e candidato não implantável não
constituem publicação de release nem deploy.

## 16. Estado protegido final

- índice Git real vazio: `git diff --cached --quiet` retornou 0;
- `HEAD` inexistente: `git rev-parse --verify HEAD` retornou 128;
- nenhuma tag e nenhum reflog;
- origin preservado:
  `git@github.com:greggorio/abaronesa-emporio.git`;
- exatamente dois workflows ativos: `ci.yml` e `publish-candidate.yml`;
- nenhum cache Python;
- nenhum artifact ou índice temporário da S12;
- nenhuma imagem `actionlint` residual;
- nenhum recurso Compose com prefixo `candidate-*`;
- recursos Docker preexistentes de outros projetos não foram alterados;
- nenhuma S13, publicação, execução remota ou acesso a produção.

## 17. Riscos e itens não determinados

A primeira execução remota ainda deve comprovar APIs e outputs reais das
actions, disponibilidade de permissões de attestations, scans, push por digest,
descoberta do candidato anterior, comportamento do primeiro candidato e os
probes da stack. CVEs reais podem bloquear a ativação. Essas evidências
pertencem à futura ativação acompanhada e não foram simuladas.

## 18. Bloqueios

Não foi encontrado bloqueio local para revisão. A execução remota continua
deliberadamente fora desta slice.

## 19. Estado da slice

`IN_PROGRESS — aguardando revisão do orquestrador`

Não declarar `ACCEPTED`.

---

## 20. Revisao do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS`  
> **Data:** `2026-07-29`

Os 157 testes relatados e o `actionlint` nao bastam para aceitar a S12. A
leitura do workflow e dos helpers encontrou caminhos operacionais que nao
estao cobertos pelos validadores atuais e que impediriam ou enfraqueceriam a
primeira execucao remota.

### 20.1 Bloqueios

1. **Validacao final quebra candidatos incrementais e documentais.**
   `candidate_manifest.py` exige o candidato anterior sempre que ha
   `inheritedComponents`, mas `publish_manifest` baixa somente
   `candidate-pending-integration` e executa a validacao sem `--previous`.
   Assim, somente o primeiro candidato, com seis componentes construidos,
   consegue chegar ao artifact final.

2. **As variaveis de imagem do harness nao possuem o mesmo contrato.**
   `compose_env.py` emite `BACKEND_IMAGE`, `WEBSITE_BACK_IMAGE`,
   `FRONTEND_IMAGE`, `WEBSITE_FRONT_IMAGE`, `WHATSAPP_IMAGE` e
   `GATEWAY_IMAGE`; o override exige as seis variantes
   `CANDIDATE_*_IMAGE`. O `docker compose config` do job integrado falharia
   por variavel obrigatoria ausente.

3. **O probe nao usa a configuracao Compose que subiu a stack.**
   `integrated_harness.py` sobe com os dois `-f`, mas chama
   `probe_candidate.py` apenas com `--project`. O probe executa
   `docker compose -p <projeto> ps` sem os arquivos Compose e sem
   `COMPOSE_FILE`, no root, onde nao existe arquivo Compose default.

4. **O gateway candidato publica duas portas.** Uma prova somente de
   configuracao, com valores integralmente ficticios, executou:

   ```text
   docker compose -f ops/compose/compose.prod.yml \
     -f ops/compose/testing/compose.candidate.yml config --format json
   ```

   O comando terminou com codigo 0 e mostrou simultaneamente
   `127.0.0.1:8120 -> 8080` e `127.0.0.1:<porta-efemera> -> 8080`. O override
   adiciona a porta, em vez de substituir a porta do Compose de producao.
   Isso viola o requisito de um unico bind de loopback e pode conflitar com
   outra stack.

5. **O segredo JWT efemero e insuficiente para HS512.**
   `secrets.token_hex(32)` fornece 32 bytes de entropia, enquanto a prova da
   S10 ja exigiu pelo menos 64 bytes para o backend ERP. A autenticacao root
   usada pelo proprio probe pode falhar antes de testar os upstreams.

6. **O job integrado nao autentica no GHCR.** Declarar
   `packages: read` nao autentica o Docker. Imagens privadas nao serao
   baixadas pelo Compose. O login deve ocorrer no proprio job integrado com
   action fixada, token somente leitura e sem ampliar permissoes.

7. **Falta a prova de uma API do `website_back`.** O probe cobre os dois
   roots, login/API ERP e WhatsApp via ERP, mas nenhuma rota que demonstre o
   upstream do backend do website. Isso nao satisfaz a matriz minima da
   secao 11 da task.

8. **A busca do candidato anterior nao falha ao esgotar o limite.**
   Depois da decima pagina cheia sem decisao,
   `previous_candidate.py` grava `no_previous`, indistinguivel do fim normal
   da lista. Alem disso, o manifesto extraido nao e ligado ao `run.id` e
   `head_sha` do run selecionado, e `metadata.json` tem somente `kind` e
   `deployable`. Um artifact valido isoladamente pode, portanto, ser
   associado ao run errado sem rejeicao causal.

9. **O fechamento do artifact e do cleanup esta incompleto.**
   O upload final nao registra os IDs/digest/URL publicos retornados pela
   action, embora isso seja criterio explicito da task. O `finally` remove
   containers, volumes e redes, mas nao remove de forma dirigida as seis
   imagens do manifesto relacionadas ao run.

### 20.2 Correcoes obrigatorias

O executor deve corrigir somente os artefatos ja autorizados pela S12 e:

- fazer a validacao final consumir o mesmo candidato anterior validado quando
  houver heranca, preservando o primeiro candidato sem anterior;
- unificar os nomes das seis variaveis de imagem entre emissor, override e
  validador;
- garantir que `ps` e probes usem exatamente os mesmos dois arquivos Compose
  e o mesmo projeto usados em `up`;
- produzir exatamente um bind do gateway na porta efemera, comprovado pelo
  modelo resultante de `docker compose config`;
- gerar segredo de integracao com no minimo 64 bytes aleatorios e testar esse
  limite sem registrar o valor;
- autenticar o job integrado no GHCR com `packages: read`, action por SHA e
  sem permissao de escrita;
- adicionar probe deterministico de uma API do `website_back`;
- distinguir fim da lista de limite esgotado e ligar candidato anterior,
  sidecar e metadata ao run, artifact e SHA selecionados;
- registrar os identificadores publicos retornados pelo upload final e
  remover somente as seis imagens imutaveis do manifesto no cleanup;
- ampliar validador e mutantes para que cada defeito acima falhe
  individualmente, inclusive primeiro, incremental, docs-only, pagina cheia,
  porta duplicada, ausencia de login read-only e probe sem os `-f`;
- corrigir a documentacao e este relatorio, retirando alegacoes que ainda nao
  correspondem ao codigo.

### 20.3 Limites da retomada

Nao executar workflow remoto, Maven, NPM, build Docker, Trivy, Compose `up`,
login/push no GHCR, attestation, commit, tag, push ou acesso a producao. E
permitido executar os validadores/testes locais prescritos, `actionlint` e
`docker compose config` com valores ficticios, sem iniciar containers.

Preservar indice real vazio, ausencia de `HEAD`, tags, reflog, S13, caches e
residuos. A task e o tracker permanecem sob autoridade do orquestrador.

Estado:

```text
S12 IN_PROGRESS — correcoes bloqueantes do ciclo 1
S13 nao autorizada
```

## 21. Resposta às correções do ciclo 1

> **Estado:** `IN_PROGRESS — aguardando nova revisão do orquestrador`  
> **Data:** `2026-07-29`  
> **CWD:** `/home/gregorio/git/baronesa/emporio`

### 21.1 Resposta individual aos bloqueios

1. **Validação final com herança.** Foi criado
   `tools/candidates/finalize_candidate.py`. O job final baixa também
   `validated-previous`; quando `inheritedComponents` não está vazio, o helper
   exige e fornece o candidato anterior ao validador. Primeiro candidato
   continua válido sem anterior. Testes causais cobrem primeiro, incremental,
   docs-only e anterior ausente.

2. **Variáveis de imagem.** `compose_env.py`, override, workflow e validador
   usam canonicamente as seis variáveis `CANDIDATE_*_IMAGE`. O emissor também
   fornece os aliases sem prefixo exigidos pela interpolação do Compose de
   produção, sempre com o mesmo valor por digest; divergência é testada.

3. **Mesmos arquivos Compose.** O harness forma uma única lista com projeto,
   Compose de produção e override. `config`, `up` e `down` usam essa lista, e
   o probe recebe explicitamente os mesmos `--compose`, `--override` e
   `--project`; seus dois comandos `ps` usam os dois `-f`.

4. **Bind único.** O override deixou de acrescentar `ports`. A porta efêmera
   alimenta simultaneamente `CANDIDATE_GATEWAY_PORT` e
   `GATEWAY_LOOPBACK_PORT`, fazendo o bind original do Compose resultar em uma
   única publicação. O validador executa `docker compose config --format json`
   com fixtures fictícias e exige exatamente:

   ```json
   {"gateway":[{"host_ip":"127.0.0.1","mode":"ingress","protocol":"tcp","published":"49123","target":8080}]}
   ```

   Porta duplicada, bind público, target divergente ou publicação por outro
   serviço falham causalmente.

5. **Segredo HS512.** `INTEGRATION_SYSTEM_TOKEN_SECRET` usa
   `secrets.token_hex(64)`: 64 bytes aleatórios, representados por 128
   caracteres hexadecimais. O teste mede o resultado sem registrar seu valor.

6. **Pull privado no integrado.** O job mantém exatamente
   `contents/actions/packages: read`, usa `docker/login-action` v3.6.0 fixada
   por SHA antes do harness e executa `docker logout ghcr.io` com `if:
   always()`. Não há `packages: write` fora do job `build`.

7. **API do website backend.** Além dos roots, login ERP e WhatsApp via ERP, o
   probe chama pelo host do website a rota pública determinística
   `/api/themes?tenantId=candidate.invalid`, que é roteada ao
   `website_back`.

8. **Paginação.** Página com menos de 50 runs encerra normalmente e grava
   `no_previous`. Se todas as páginas até o limite estiverem cheias sem
   decisão, o helper falha com `pagination limit exhausted without decision`.
   Os dois caminhos possuem testes separados.

9. **Vínculo do anterior.** O run precisa estar concluído/verde, ser
   `workflow_run` em `main`, pertencer ao repositório canônico e possuir SHA
   completo. A associação do artifact precisa repetir run ID e head SHA. O
   digest SHA-256 do ZIP baixado é conferido; o ZIP contém somente manifesto,
   sidecar e metadata. Manifesto e metadata repetem run, SHA, candidate ID e
   checksum. `selection.json` registra run ID, head SHA, artifact ID, URL e
   digest públicos.

10. **Outputs do artifact final.** O upload possui `id: publish-artifact`. O
    step seguinte valida e registra no `GITHUB_STEP_SUMMARY` os outputs
    públicos `artifact-id`, `artifact-url` e `artifact-digest`; valores com
    shape divergente bloqueiam o job.

11. **Cleanup de imagens.** No `finally`, após `down`, o harness percorre
    somente as seis entradas do manifesto validado e executa `docker image rm`
    sobre cada `immutableRef`. Não há glob, tag navegacional ou prune.

12. **Testes causais.** A suíte candidata passou de 7 para 16 testes. Os nove
    novos casos cobrem os três tipos de candidato, aliases/segredo,
    modelo Compose e porta duplicada, login somente leitura, propagação dos
    dois `-f`, API website, paginação cheia/fim normal, vínculo do anterior e
    cleanup dirigido. Os mutantes do workflow verificam também ausência do
    login integrado, `packages: write`, validação final sem anterior e outputs
    públicos ausentes.

13. **Documentação.** Foram corrigidos README dos workflows, CI,
    `CANDIDATOS.md` e README de release-control para registrar o comportamento
    efetivo, ainda **CONFIGURADO, NÃO EXECUTADO**.

### 21.2 Arquivos alterados neste ciclo

- `.github/workflows/publish-candidate.yml`;
- `.github/workflows/README.md`;
- `ops/compose/testing/compose.candidate.yml`;
- `tools/candidates/assemble_candidate.py`;
- `tools/candidates/compose_env.py`;
- `tools/candidates/finalize_candidate.py` (novo);
- `tools/candidates/integrated_harness.py`;
- `tools/candidates/previous_candidate.py`;
- `tools/candidates/probe_candidate.py`;
- `tools/candidates/validate_candidate_workflow.py`;
- `tools/candidates/tests/test_candidate_workflow.py`;
- `tools/candidates/tests/test_cycle1_corrections.py` (novo);
- `docs/infrastructure/deployment/ci/CI.md`;
- `docs/infrastructure/deployment/release-control/CANDIDATOS.md`;
- `docs/infrastructure/deployment/release-control/README.md`;
- este relatório.

A task S12 e o tracker não foram alterados.

### 21.3 Comandos, códigos de saída e quantidades

Todos os comandos abaixo foram executados no CWD registrado.

| Comando exato | Exit | Evidência |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v` | 0 | 9 testes |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/candidates/validate_candidate_workflow.py` | 0 | `candidate-workflow:valid`; inclui `docker compose config --format json` fictício |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v` | 0 | 16 testes |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json` | 0 | `candidate:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v` | 0 | 141 testes |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py validate --require-release-ready` | 0 | `catalog:valid` |
| `docker run --rm -v "$PWD:/repo:ro" -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color .github/workflows/ci.yml .github/workflows/publish-candidate.yml` | 0 | dois workflows aprovados |
| `docker image rm docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 0 | imagem efêmera removida |

Total das três suítes: **166 testes aprovados**.

A prova Compose isolada usou somente digests, credenciais e domínios
fictícios, executou os dois `-f` com `config --format json`, não iniciou
container e terminou com código 0. O modelo mostrou exclusivamente o gateway
em `127.0.0.1:49123 -> 8080`.

### 21.4 Falhas intermediárias deste ciclo

1. A primeira execução do validador Compose falhou porque a fixture ainda não
   fornecia email/senha do bootstrap exigidos pelo override. As duas variáveis
   foram adicionadas à fixture fictícia; nenhuma regra de produção foi
   relaxada.
2. Um mutante antigo procurava `127.0.0.1` diretamente no override, mas o bind
   agora vem corretamente do Compose base com a porta sobrescrita por
   ambiente. O mutante foi substituído por inserção explícita de uma porta
   pública adicional, e o modelo resultante é testado separadamente.

Após essas correções, validador, 16 testes candidatos, bateria completa e
`actionlint` passaram.

### 21.5 Fronteiras preservadas

Não foram executados Maven, NPM, Docker build, Compose `up`, Trivy real,
workflow remoto, login/push GHCR local, attestation, commit, tag, push,
release, deploy ou acesso à produção. O login presente no YAML foi somente
configurado e não executado localmente.

O comportamento remoto permanece **CONFIGURADO, NÃO EXECUTADO**. IDs, URLs,
digests, pulls, probes e attestations reais ainda dependerão da futura
ativação acompanhada.

### 21.6 Estado final

- índice Git real vazio;
- `HEAD` inexistente;
- nenhuma tag ou reflog;
- origin canônico preservado;
- exatamente `ci.yml` e `publish-candidate.yml`;
- nenhuma S13;
- nenhum cache Python;
- nenhum artifact, índice temporário, container, volume, rede ou imagem
  residual da S12;
- nenhum commit, push, publicação, release ou deploy.

`IN_PROGRESS — aguardando nova revisão do orquestrador`

---

## 22. Revisao do orquestrador — ciclo 2

> **Resultado:** `IN_PROGRESS`  
> **Data:** `2026-07-29`

As correcoes da secao 21 fecharam os bloqueios estruturais do ciclo 1, mas a
revisao do caminho executavel encontrou falhas que os 166 testes atuais nao
exercitam. A S12 ainda nao pode ser ativada remotamente.

### 22.1 Bloqueios

1. **Protocolos de arquivo do GitHub recebem `\n` literal.** Os helpers
   abaixo usam `"\\n"` no codigo Python, produzindo os dois caracteres barra
   invertida e `n`, em vez de LF:

   - `validate_candidate_workflow.py`, nos outputs `matrix` e `has_builds`;
   - `image_result.py`, no JSON de resultado e no output `digest`;
   - `compose_env.py`, em todas as entradas de `GITHUB_ENV`;
   - `assemble_candidate.py`, no `metadata.json`.

   Uma execucao isolada de `compose_env.py`, com manifesto e imagem
   ficticios, produziu **0** quebras LF, **29** sequencias literais `\n` e
   **29** atribuicoes. Isso impede o runner de importar as variaveis, impede
   a propagacao correta de outputs e torna os JSONs de resultado/metadata
   invalidos.

2. **O download real do candidato anterior nao pode executar.**
   `previous_candidate.download()` chama:

   ```text
   gh api <url> --method GET --output <arquivo>
   ```

   A versao instalada do `gh api` nao possui `--output`, conforme
   `gh api --help`. Alem disso, o download deve ser derivado do artifact ID
   validado no repositorio canonico, e nao executar uma URL de download
   recebida do payload. O teste atual injeta `fetch` e, por isso, nao cobre
   o comando real.

3. **O formato do output `artifact-digest` esta incorreto.** O workflow exige
   `sha256:<64 hex>`, mas a documentacao da action fixada mostra que
   `artifact-digest` retorna somente os 64 caracteres hexadecimais. A
   referencia exata e:
   [actions/upload-artifact no SHA fixado](https://github.com/actions/upload-artifact/blob/ea165f8d65b6e75b540449e92b4886f43607fa02/README.md#outputs).
   Assim, o step posterior ao upload falharia mesmo depois de o artifact ter
   sido criado, deixando o workflow vermelho.

4. **O metadata atual nao e validado antes do upload.**
   `finalize_candidate.py` valida apenas manifesto, anterior e sidecar.
   Mesmo depois de corrigir a quebra de linha, `metadata.json` adulterado ou
   incoerente ainda poderia ser enviado e so seria descoberto na execucao
   seguinte.

5. **A remocao das imagens e best-effort, sem confirmacao.**
   `integrated_harness.py` usa `docker image rm` com `check=False`, descarta
   stdout/stderr e nao verifica se as seis referencias continuam presentes.
   Portanto, o job pode ficar verde mesmo deixando imagens residuais, em
   desacordo com o cleanup dirigido exigido.

### 22.2 Correcoes obrigatorias

O executor deve:

- substituir apenas nos caminhos operacionais os `\\n` literais por LF real;
- criar provas em nivel de CLI/arquivo, nao somente inspecao textual, que
  confirmem:
  - uma linha distinta por output em `GITHUB_OUTPUT`;
  - uma linha distinta por variavel em `GITHUB_ENV`;
  - JSON valido para `result.json` antes e depois de `attest`;
  - JSON valido para `metadata.json`;
- implementar download binario sem shell e sem opcao inexistente, usando
  endpoint canonico derivado do repository e artifact ID ja validados;
- adicionar teste do comando real com double de subprocesso, rejeitando
  `--output`, URL arbitraria e qualquer destino fora do arquivo esperado;
- aceitar e registrar o output da action `artifact-digest` no formato real de
  64 hex, mantendo `sha256:` somente nos contratos/API que efetivamente usam
  o prefixo;
- validar `metadata.json` no fechamento final, exigindo igualdade exata com
  manifesto, run, SHA, candidate ID e checksum antes do upload;
- tornar a remocao das seis imagens verificavel e fail-closed: uma referencia
  que continue local depois da tentativa deve falhar o harness, sem glob,
  tag livre ou prune;
- incluir mutantes causais para os cinco bloqueios e atualizar documentacao e
  relatorio sem alegar execucao remota.

### 22.3 Limites e estado

Continuam proibidos Maven, NPM, Docker build, Compose `up`, Trivy real,
workflow remoto, login/push GHCR, attestation, commit, tag, push e acesso a
producao. Sao permitidos os validadores/testes locais, `actionlint`, doubles
de subprocesso e `docker compose config` com dados ficticios.

A revisao confirmou indice vazio, `HEAD` inexistente, zero tags, exatamente
`ci.yml` e `publish-candidate.yml`, zero S13 e zero cache Python.

Estado:

```text
S12 IN_PROGRESS — correcoes bloqueantes do ciclo 2
S13 nao autorizada
```



## 23. Retificacao de governanca e fechamento definitivo do contrato

### 23.1 Classificacao dos ciclos anteriores

Por determinacao do orquestrador, os ciclos 1 e 2 ficam reclassificados como
**emendas de contrato causadas por requisitos incompletos do orquestrador**.
Eles nao representam rejeicao de uma escolha arquitetural que o executor
devesse ter adivinhado.

O prompt corretivo anterior foi retirado. Nenhuma nova execucao foi
solicitada com base apenas nos cinco achados da secao 22.

### 23.2 Auditoria integral realizada antes da nova delegacao

O orquestrador auditou em conjunto:

- os dois workflows;
- todos os helpers de `tools/candidates`;
- plano da CI, manifesto/schema e resolvedor;
- Compose candidato e harness;
- relacao entre candidatos incrementais, Git lineage e concorrencia;
- formatos efetivos das actions fixadas e dos comandos externos utilizados.

A auditoria identificou que a correcao pontual ainda deixaria decisoes
arquiteturais abertas. A task recebeu, por isso, a secao
`20. Emenda corretiva definitiva do orquestrador`, que passa a ser a
autoridade superior para esta retomada.

### 23.3 Decisoes agora fechadas

A emenda define sem delegar escolhas ao executor:

- grafo exato de jobs e permissoes;
- plano original v2 e plano efetivo cumulativo;
- selecao do predecessor por ancestralidade e distancia Git;
- first release, mesmo SHA, descendant, unrelated, expiracao e no-change;
- dupla verificacao de `origin/main` e idempotencia;
- download binario seguro por artifact ID;
- build unico, digest remoto e component result estrito;
- schema candidato v2;
- separacao `pending -> integration receipt -> final`;
- nove probes integrados e cleanup fail-closed;
- metadata, digests e elegibilidade do artifact;
- outcome obrigatorio para distinguir no-op de predecessor expirado;
- matriz causal de testes e comandos permitidos/proibidos.

Tambem registra explicitamente que eventual requisito novo nao escrito nao
pode ser usado como erro retroativo do executor.

### 23.4 Evidencias externas usadas para fechar formatos

- O output `artifact-digest` da action fixada e definido como SHA-256 em
  hexadecimal sem prefixo:
  [actions/upload-artifact no SHA fixado](https://github.com/actions/upload-artifact/blob/ea165f8d65b6e75b540449e92b4886f43607fa02/README.md#outputs).
- A inspecao remota de imagem usa o manifesto retornado por
  `docker buildx imagetools inspect --format`:
  [Docker CLI reference](https://docs.docker.com/reference/cli/docker/buildx/imagetools/inspect/).
- A politica de concorrencia nao depende de sintaxe nova de fila; a seguranca
  foi fechada por lineage, HEAD e idempotencia, mantendo compatibilidade com
  o `actionlint` prescrito.

### 23.5 Alteracoes e estado

Nesta intervencao do orquestrador foram alterados somente:

```text
docs/infrastructure/deployment/implementation/README.md
docs/infrastructure/deployment/implementation/slices/S12-publicacao-candidato-e-proveniencia.task.md
docs/infrastructure/deployment/implementation/slices/S12-publicacao-candidato-e-proveniencia.report.md
```

Nenhum codigo, workflow, teste ou configuracao de runtime foi alterado.
Nenhum teste de implementacao, workflow remoto, build, Compose, commit, push,
publicacao ou acesso a producao foi executado nesta intervencao documental.

Estado:

```text
S12 IN_PROGRESS — contrato corretivo definitivo pronto para delegacao
S13 nao autorizada
```

## 24. Triagem da devolucao posterior ao contrato definitivo

> **Resultado:** `IN_PROGRESS — retomada nao executada`  
> **Data:** `2026-07-29`

### 24.1 Classificacao

A devolucao recebida reproduz o resumo e o apontamento da execucao anterior:

```text
166 testes
relatorio na linha 377
```

A linha 377 inicia a Secao 21, `Resposta às correções do ciclo 1`, anterior
a emenda definitiva. O relatorio nao possui nova secao de execucao depois da
Secao 23, que foi escrita pelo orquestrador.

Por isso, esta devolucao nao e classificada como implementacao rejeitada nem
abre um novo ciclo de correcoes. A implementacao da Secao 20 integral ainda
nao foi executada ou documentada.

### 24.2 Evidencias no codigo

Os artefatos continuam anteriores ao contrato definitivo:

- `candidate_plan.py` ainda gera `schemaVersion: 1`, sem
  `baseCommitSha` e sem leitura de `GITHUB_EVENT_PATH`;
- o workflow ainda possui jobs `previous` e `publish_manifest`, nao o grafo
  `trust -> predecessor -> build -> assemble -> integrated -> publish`;
- nao existem `candidate-effective-plan` nem `candidate-outcome`;
- `image_result.py` ainda deriva digest de `.RepoDigests` local;
- `previous_candidate.py` ainda usa `gh api --output`, URL do payload e
  `extractall`;
- os helpers ainda escrevem sequencias literais `\\n`;
- schema e exemplo do candidato permanecem em `schemaVersion: 1`;
- o workflow ainda exige prefixo `sha256:` no output cru
  `artifact-digest`.

Esses pontos nao sao requisitos novos: todos ja estao decididos nas
subsecoes 20.2 a 20.14 da task.

### 24.3 Acao necessaria

Executar a Secao 20 **integralmente**, das subsecoes 20.2 a 20.14, e adicionar
ao final deste relatorio a secao
`## 25. Resposta a emenda corretiva definitiva`, contendo:

- arquivos efetivamente alterados;
- comandos e exit codes da nova execucao;
- testes novos e totais;
- evidencias individuais da matriz 20.14;
- estado protegido final.

Nao reutilizar a Secao 21 como resposta. Nao alterar a task nem o tracker.
Nao declarar `ACCEPTED` e nao criar a S13.

Estado:

```text
S12 IN_PROGRESS — retomada da Secao 20 ainda nao executada
S13 nao autorizada
```


## 25. Resposta a emenda corretiva definitiva

> **Estado:** `IN_PROGRESS — aguardando revisão do orquestrador`  
> **Data:** `2026-07-29`  
> **CWD:** `/home/gregorio/git/baronesa/emporio`

Esta é uma nova execução da Seção 20.2–20.14. As evidências e contagens desta
seção substituem, para a emenda definitiva, as evidências históricas da Seção
21.

### 25.1 Implementação literal da emenda

- O grafo agora possui exatamente `trust -> predecessor -> build -> assemble
  -> integrated -> publish`. Foram removidos os jobs antigos `previous` e
  `publish_manifest`.
- O plano CI é schema v2, lê `GITHUB_EVENT_PATH`, contém
  `baseCommitSha`, hashes prefixados e é publicado somente como
  `candidate-plan/candidate-plan.json`.
- `predecessor` valida outcomes, artifacts e lineage Git; seleciona o
  ancestral mais próximo por distância e produz
  `candidate-effective-plan.json` e o contexto estrito do predecessor.
- Os modos são somente `continue`, `already_published`, `no_changes` e
  `superseded`. Modos terminais pulam build/integração/candidato e ainda
  produzem outcome.
- O build usa somente a matriz efetiva. O digest remoto vem exclusivamente de
  `docker buildx imagetools inspect ... .Manifest.digest`; não há leitura de
  digest local. Labels, resultado, attestation, logout, remoção da tag e
  inspeção de ausência são estritos.
- Downloads anteriores usam endpoint canônico por artifact ID, argumentos
  diretos e stdout binário em file handle. ZIP é limitado a 2 MiB, tem digest
  conferido e entradas lidas individualmente, sem extração coletiva.
- O schema candidato passou a v2 com `sourceCi`, `predecessor` e
  `integration`; checks por componente possuem somente build/test/scan.
- `assemble` gera somente `pending.json`, sidecar e metadata pending.
  `integrated` gera somente receipt e sidecar. `publish` vincula plano,
  predecessor, pending, receipt, metadata e manifesto antes do upload final.
- O harness executa os comandos pull/up prescritos, os mesmos project/`-f`,
  sete services running/healthy, oito probes HTTP via biblioteca padrão e o
  nono probe interno via `compose exec`.
- Cleanup exige down, ausência das seis imagens imutáveis, zero recurso do
  projeto e logout. Qualquer resíduo falha o job.
- Todo caminho verde publica `candidate-outcome`. Digest da action permanece
  64 hex cru; digest REST permanece `sha256:<64 hex>`.
- Não há condição Python protegida por `assert` nos helpers de segurança. Os
  protocolos de arquivo e outputs usam LF real.

### 25.2 Arquivos alterados nesta execução

```text
.github/workflows/ci.yml
.github/workflows/publish-candidate.yml
.github/workflows/README.md
ops/compose/docker-compose.emporio.yml
ops/releases/candidate-manifest.schema.json
ops/releases/examples/candidate-manifest.example.json
tools/candidates/artifact_io.py
tools/candidates/assemble_candidate.py
tools/candidates/candidate_plan.py
tools/candidates/compose_env.py
tools/candidates/finalize_candidate.py
tools/candidates/image_result.py
tools/candidates/integrated_harness.py
tools/candidates/lineage.py
tools/candidates/outcome.py
tools/candidates/outcome_context.py
tools/candidates/previous_candidate.py
tools/candidates/probe_candidate.py
tools/candidates/publish_guard.py
tools/candidates/trust.py
tools/candidates/validate_candidate_workflow.py
tools/candidates/tests/test_definitive_contract.py
tools/releases/candidate_manifest.py
tools/releases/validate_candidate_manifest.py
tools/releases/tests/test_candidate_manifest_v2.py
docs/infrastructure/deployment/ci/CI.md
docs/infrastructure/deployment/release-control/CANDIDATOS.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/implementation/slices/S12-publicacao-candidato-e-proveniencia.report.md
```

`ops/compose/docker-compose.emporio.yml` foi criado como entrada canônica que
inclui o Compose de produção existente, pois a matriz literal 20.14 exige
esse path. Nenhum serviço ou valor do Compose de produção foi duplicado ou
alterado.

A task S12, o tracker e código comercial não foram alterados.

### 25.3 Nova matriz 20.14

| Comando exato | Exit | Resultado e interpretação |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/ci/validate_ci.py` | 0 | `ci:valid`; CI mantém trigger e permissões aceitos |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -p 'test_*.py'` | 0 | 9 testes CI |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py` | 0 | schema/exemplo v2 válidos |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | 112 testes release |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/candidates/validate_candidate_workflow.py` | 0 | grafo, permissões, artifacts e protocolos definitivos válidos |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -p 'test_*.py'` | 0 | 27 testes definitivos novos |
| `docker run --rm -v "$PWD:/repo:ro" -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color .github/workflows/ci.yml .github/workflows/publish-candidate.yml` | 0 | ambos os workflows aprovados |
| `docker image rm docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 0 | imagem efêmera removida |
| `docker compose -f ops/compose/docker-compose.emporio.yml -f ops/compose/testing/compose.candidate.yml config` com valores integralmente fictícios | 0 | sete services e configuração mesclada válidos; nenhum container iniciado |

Total atual: **148 testes aprovados**. Os **36 testes diretamente afetos à
emenda** são os 27 testes candidatos definitivos e os 9 testes de manifesto
v2/pending/receipt/final. Eles não são os 166 testes reapresentados no ciclo
anterior.

### 25.4 Evidências causais de 20.14

1. Plano v2: shape exato, propriedades extras, evento/ref/SHA/base/run/attempt
   inválidos e LF real.
2. Git temporário real: same, ancestor, descendant, unrelated, história
   reescrita, distância mais próxima, empate e diff NUL cumulativo.
3. Resolução: first com seis, fechamento transitivo incremental e
   `no_changes`.
4. Outcomes: quatro estados, campos nulos, digest raw e ausência,
   duplicidade ou expiração em run verde.
5. Download: comando real `gh api`, endpoint derivado do ID, stdout binário,
   digest/limite ZIP, traversal e entrada extra.
6. Imagem: `imagetools`, digest remoto, rejeição de protocolo legado, labels,
   checks e provenance estritos.
7. Pipeline: schema v2, pending -> receipt -> final, sidecars e adulterações
   de metadata, plano, predecessor, pending e receipt.
8. Harness com doubles de subprocesso: sete services, bind único, pull/up
   exatos, nove probes, receipt, cleanup positivo e resíduo causal.
9. HEAD: igualdade, avanço na mesma lineage e lineage não relacionada.
10. Workflow: jobs/permissões exatos, pins, artifacts, outcome, ausência dos
    jobs e protocolos antigos.

### 25.5 Falhas intermediárias da nova execução

- O primeiro validador encontrou os próprios nomes de protocolos proibidos
  nas fixtures de mutação. As fixtures passaram a construir esses nomes sem
  reintroduzi-los nos helpers operacionais.
- A fixture inicial de component result usava a versão humana antiga do
  exemplo. O exemplo foi alinhado ao candidate ID determinístico exigido.
- O double positivo do cleanup inicialmente devolvia sucesso também para
  `docker image inspect`, simulando imagem residual. O double foi corrigido
  para representar ausência; um teste separado mantém o mutante residual.
- O path literal de Compose da matriz 20.14 não existia. Foi criada a entrada
  canônica por `include`, sem alterar o Compose de produção.

Depois dessas correções, toda a nova matriz terminou com exit 0.

### 25.6 Limites e estado protegido

Não foram executados Maven, NPM, Docker build, Compose `up`, Trivy real,
workflow remoto, login/push GHCR, attestation remota, commit, tag, push,
release, deploy ou acesso à produção. `actionlint` e `docker compose config`
foram as únicas execuções Docker autorizadas.

Estado final verificado:

- índice Git real vazio;
- `HEAD` inexistente;
- nenhuma tag ou reflog;
- origin canônico preservado;
- exatamente `ci.yml` e `publish-candidate.yml`;
- task e tracker preservados;
- nenhuma S13;
- nenhum cache Python;
- nenhum artifact temporário ou imagem do actionlint;
- zero container, volume ou network `candidate-*`;
- nenhuma publicação ou execução externa proibida.

`IN_PROGRESS — aguardando revisão do orquestrador`

---

## 26. Revisao da execucao da emenda definitiva

> **Resultado:** `IN_PROGRESS — correcoes causais`  
> **Data:** `2026-07-29`

### 26.1 Classificacao

A Secao 25 e uma nova execucao real da emenda e substitui corretamente as
evidencias antigas. O grafo, os artefatos principais e a maior parte dos
protocolos 20.2–20.14 foram implementados.

A S12 ainda nao pode ser aceita porque a revisao encontrou seis divergencias
causais de requisitos ja expressos na Secao 20. Nenhuma decisao arquitetural
nova foi adicionada nesta revisao.

### 26.2 Bloqueios objetivos

1. **Os IDs dos dois workflows foram ligados como se fossem o mesmo run.**
   `lineage.effective()` copia `sourceCi.runId` do plano produzido pela CI,
   enquanto `pending_from()` grava em `workflow.runId` o `GITHUB_RUN_ID` do
   workflow `Publish Candidate`. Esses IDs representam runs distintos.
   Entretanto, `candidate_manifest.validate_manifest()` exige:

   ```python
   value["sourceCi"]["runId"] == value["workflow"]["runId"]
   ```

   Todo candidato remoto real falharia na finalizacao. O exemplo e os testes
   escondem a falha usando artificialmente `100` nos dois campos.

2. **Outcome e artifact publicado nao sao vinculados entre si na leitura.**
   `previous_candidate.discover()` valida apenas run ID e SHA do outcome.
   Nao compara `candidateId`, `candidateArtifactId` e
   `candidateArtifactDigest` do outcome com o manifesto e o artifact REST
   efetivamente baixados; tambem nao liga `workflowAttempt` ao run. Assim,
   um outcome internamente valido pode apontar para outro candidato. Isso
   diverge diretamente de 20.11.

3. **A classificacao de lineage pode retornar antes de examinar toda a
   historia.** `lineage.nearest()` retorna imediatamente ao encontrar
   `same` ou `descendant`. Um candidato `unrelated` posterior na lista nao e
   observado, apesar de 20.5 exigir classificacao de cada SHA e falha fechada
   para lineage nao relacionada. Os testes atuais cobrem os casos apenas de
   forma isolada.

4. **Os contratos chamados de estritos ainda aceitam adulteracoes.**

   - `resolution` no schema v2 e apenas `{"type": "object"}`;
   - a verificacao de predecessor em `validate_manifest()` e tautologica;
   - o schema nao condiciona `first` a campos nulos nem `selected` a
     identificadores preenchidos;
   - nao existe validador estrito de `candidate-effective-plan`;
   - bindings de tag, labels, commit, run, estado built/inherited e
     procedencia nao sao conferidos integralmente;
   - `trust.event()` nao exige `repository.full_name` canonico.

   Isso contraria 20.4, 20.5, 20.7, 20.8 e 20.10.

5. **O bundle pendente e o recibo nao sao validados antes do uso.**
   `integrated` baixa `candidate-pending` e chama `compose_env.py` antes de
   conferir sidecar, metadata, shape e bindings. O harness usa diretamente
   IDs e referencias desse JSON. Na finalizacao, campos extras do receipt
   sao ignorados em vez de rejeitados. Portanto, ainda nao existe a
   revalidacao exata exigida em 20.9 e 20.10.

6. **O cleanup pode interromper antes de executar todas as etapas
   obrigatorias.** No harness, uma excecao de `check_output` durante a
   contagem de residuos impede o logout. No step de cleanup do build, o shell
   fail-fast pode parar em `docker logout` ou `docker image rm` antes do
   inspect. O contrato 20.7/20.9 exige tentar todas as limpezas dirigidas,
   acumular erros e somente entao falhar.

### 26.3 Correcoes fechadas

O executor deve aplicar exatamente:

1. manter `sourceCi.runId/attempt` ligados ao run da CI e
   `workflow.runId/attempt` ligados ao run de publicacao; remover a igualdade
   entre eles e validar cada par contra seu artefato de origem;
2. na descoberta, validar identidade canonica completa do run e igualdade
   exata entre outcome, artifact REST, manifesto e metadata, inclusive
   attempt, candidate ID, artifact ID e digest;
3. classificar o conjunto completo antes de decidir; qualquer `unrelated`,
   empate ou combinacao ambigua falha antes de `same`, `descendant` ou
   ancestral;
4. implementar e usar validadores estritos para effective plan, pending,
   receipt e manifesto final, sem propriedades extras, incluindo:
   - `resolution` integral igual ao resolvedor canonico;
   - predecessor `first` com cinco nulos ou `selected` com cinco valores
     validos;
   - bindings de component/tag/digest/labels/commit/run/attempt/state/origin
     e provenance;
   - repository e owner canonicos no trust;
5. validar os tres arquivos do pending, sidecar, metadata e bindings **antes**
   de `compose_env` e antes de qualquer comando Docker; validar o receipt
   exato antes de produzir o final;
6. tornar os cleanups do build e do harness acumulativos: tentar down,
   remocoes, inspections, contagens e logout mesmo quando uma etapa falhar,
   e retornar falha ao final se houver qualquer erro;
7. corrigir o protocolo atomico para remover arquivos de staging tambem
   quando a criacao de um staging posterior falhar.

### 26.4 Provas causais obrigatorias

Adicionar testes que exercitem as funcoes/CLIs reais para:

- CI run ID diferente do publisher run ID em um fluxo positivo completo;
- outcome com candidate ID, artifact ID, digest ou attempt divergente;
- listas combinadas `same + unrelated` e `descendant + unrelated`;
- effective plan com chave extra, resolution adulterada e predecessor
  semanticamente invalido;
- pending com sidecar, metadata, componente, imagem ou binding adulterado,
  provando que nenhum comando Docker e iniciado;
- receipt com chave extra, probe/servico duplicado ou binding divergente;
- repository `full_name` nao canonico no trust;
- tempos, tags, labels, state/origin e provenance divergentes;
- falha individual em down, remove, inspect, contagem e logout, provando que
  as demais limpezas ainda sao tentadas;
- falha durante staging parcial com zero arquivo temporario ou final
  residual;
- `publish_guard` para mesmo SHA, HEAD avancado e lineage nao relacionada;
- LF real em `GITHUB_ENV`, alem de `GITHUB_OUTPUT`.

Depois das correcoes, repetir integralmente a matriz 20.14 e registrar novos
totais na secao
`## 27. Resposta as correcoes causais da execucao integral`. Nao executar
build, Compose up, publicacao, commit ou push.

### 26.5 Estado

Esta revisao nao alterou codigo, workflow, schema ou teste. Foram alterados
somente task, tracker e este relatorio pelo orquestrador.

```text
S12 IN_PROGRESS — correcoes causais da execucao integral
S13 nao autorizada
```

## 27. Resposta as correcoes causais da execucao integral

> **CWD:** `/home/gregorio/git/baronesa/emporio`  
> **Data:** `2026-07-29`  
> **Estado:** `IN_PROGRESS — aguardando revisão do orquestrador`

### 27.1 Resposta individual aos achados da Secao 26

1. **Runs distintos:** `sourceCi.runId/attempt` permanece ligado ao run da CI
   (`100/1` na fixture causal), enquanto `workflow.runId/attempt`, resultados,
   pending, receipt, manifesto e metadata permanecem ligados ao run de
   `Publish Candidate` (`200/1` na fixture). A igualdade indevida entre os IDs
   foi removida. O fluxo positivo completo pending -> receipt -> final usa
   deliberadamente IDs diferentes.
2. **Outcome e artifact:** a descoberta exige identidade canonica completa do
   run e liga `workflowAttempt`, candidate ID, artifact ID e digest do outcome
   ao artifact REST, ao manifesto, ao sidecar e ao metadata final. Mutantes
   independentes para os quatro campos falham pela causa esperada.
3. **Lineage:** `nearest()` primeiro classifica todos os candidatos. Qualquer
   `unrelated`, duplicidade de estado terminal, combinacao ambigua ou empate
   falha antes de retornar `already_published`, `superseded` ou predecessor.
   As combinacoes `same + unrelated` e `descendant + unrelated` foram
   exercitadas.
4. **Contratos estritos:** effective plan, predecessor, resolution, pending,
   componentes, receipt e manifesto final rejeitam propriedades extras e
   bindings divergentes. A resolution deve ser exatamente a saida recalculada
   pelo resolvedor S05. Componentes conferem repository, tag, digest,
   immutable ref, labels, commit, run, attempt, tempos, state, origin, checks e
   provenance. O trust exige `repository.full_name`, owner e head repository
   canonicos.
5. **Pending antes do Docker:** o job integrado baixa plano efetivo e contexto,
   valida os tres arquivos do pending, sidecar, metadata, predecessor,
   componentes e run de publicacao antes de `compose_env`, antes do login GHCR
   e antes do harness. `compose_env` e o harness repetem a validacao. Mutantes
   de sidecar, metadata, componente, imagem e binding comprovam zero chamada
   Docker.
6. **Receipt e final:** o recibo possui shape exato, listas canonicas sem
   duplicidade e bindings integrais. O finalizador revalida pending, effective,
   predecessor e receipt antes da escrita e reabre o bundle final para conferir
   arquivos, sidecar, metadata e manifesto.
7. **Cleanup e staging:** os cleanups de build e integracao tentam todas as
   operacoes dirigidas e acumulam erros antes de retornar falha. Ha provas
   separadas para falha em down, remove, inspect, contagem e logout. O protocolo
   atomico tambem remove stagings ja criados quando um staging posterior falha;
   a prova termina sem arquivo temporario ou final.

### 27.2 Arquivos alterados nesta resposta

```text
.github/workflows/publish-candidate.yml
ops/releases/candidate-manifest.schema.json
ops/releases/examples/candidate-manifest.example.json
tools/candidates/artifact_io.py
tools/candidates/assemble_candidate.py
tools/candidates/cleanup_image.py
tools/candidates/compose_env.py
tools/candidates/finalize_candidate.py
tools/candidates/integrated_harness.py
tools/candidates/lineage.py
tools/candidates/outcome_context.py
tools/candidates/previous_candidate.py
tools/candidates/publish_guard.py
tools/candidates/trust.py
tools/candidates/validate_candidate_workflow.py
tools/candidates/validate_pending.py
tools/candidates/tests/test_causal_corrections.py
tools/candidates/tests/test_definitive_contract.py
tools/releases/candidate_manifest.py
tools/releases/tests/test_candidate_manifest_v2.py
docs/infrastructure/deployment/release-control/CANDIDATOS.md
docs/infrastructure/deployment/implementation/slices/S12-publicacao-candidato-e-proveniencia.report.md
```

A task S12, o tracker, codigo comercial, migrations, Dockerfiles, Compose e
gateway nao foram alterados.

### 27.3 Falhas intermediarias e correcoes

Na primeira execucao intermediaria depois do endurecimento, duas fixtures
antigas passaram a falhar corretamente:

- a fixture do finalizador fornecia um effective plan parcial e recebeu
  `EFFECTIVE_SHAPE`;
- a fixture de trust omitia `repository.full_name` e recebeu
  `run repository`.

As fixtures foram atualizadas para os contratos completos; nenhum validador
foi relaxado. A repeticao integral posterior ficou verde. O `actionlint` foi
repetido depois da ultima alteracao do workflow e a imagem efemera foi
novamente removida. O Compose foi somente materializado por `config`; nenhum
container foi iniciado.

### 27.4 Matriz 20.14 repetida integralmente

| Comando exato | Exit | Resultado e interpretacao |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -p 'test_*.py'` | 0 | 9 testes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py` | 0 | `candidate:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | 112 testes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/candidates/validate_candidate_workflow.py` | 0 | `candidate-workflow:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -p 'test_*.py'` | 0 | 40 testes aprovados |
| `docker run --rm -v "$PWD:/repo:ro" -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color .github/workflows/ci.yml .github/workflows/publish-candidate.yml` | 0 | dois workflows aprovados |
| `docker image rm docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 0 | imagem efemera removida |
| `env CANDIDATE_PROJECT=candidate-200-1 CANDIDATE_BACKEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-backend@sha256:1111111111111111111111111111111111111111111111111111111111111111 CANDIDATE_WEBSITE_BACK_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-backend@sha256:2222222222222222222222222222222222222222222222222222222222222222 CANDIDATE_FRONTEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-frontend@sha256:3333333333333333333333333333333333333333333333333333333333333333 CANDIDATE_WEBSITE_FRONT_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-frontend@sha256:4444444444444444444444444444444444444444444444444444444444444444 CANDIDATE_WHATSAPP_IMAGE=ghcr.io/greggorio/abaronesa-emporio-whatsapp-service@sha256:5555555555555555555555555555555555555555555555555555555555555555 CANDIDATE_GATEWAY_IMAGE=ghcr.io/greggorio/abaronesa-emporio-gateway@sha256:6666666666666666666666666666666666666666666666666666666666666666 BACKEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-backend@sha256:1111111111111111111111111111111111111111111111111111111111111111 WEBSITE_BACK_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-backend@sha256:2222222222222222222222222222222222222222222222222222222222222222 FRONTEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-frontend@sha256:3333333333333333333333333333333333333333333333333333333333333333 WEBSITE_FRONT_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-frontend@sha256:4444444444444444444444444444444444444444444444444444444444444444 WHATSAPP_IMAGE=ghcr.io/greggorio/abaronesa-emporio-whatsapp-service@sha256:5555555555555555555555555555555555555555555555555555555555555555 GATEWAY_IMAGE=ghcr.io/greggorio/abaronesa-emporio-gateway@sha256:6666666666666666666666666666666666666666666666666666666666666666 POSTGRES_IMAGE=docker.io/library/postgres@sha256:7777777777777777777777777777777777777777777777777777777777777777 POSTGRES_ADMIN_USER=candidate_admin POSTGRES_ADMIN_PASSWORD=candidate_admin_password ERP_DB_NAME=candidate_erp ERP_DB_USER=candidate_erp_user ERP_DB_PASSWORD=candidate_erp_password WEBSITE_DB_NAME=candidate_web WEBSITE_DB_USER=candidate_web_user WEBSITE_DB_PASSWORD=candidate_web_password INTEGRATION_SYSTEM_TOKEN_SECRET=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa GOOGLE_CLIENT_ID=candidate.invalid GOOGLE_CLIENT_SECRET=candidate_google_secret CANDIDATE_ROOT_EMAIL=root@candidate.invalid CANDIDATE_ROOT_PASSWORD=candidate_root_password GATEWAY_LOOPBACK_PORT=49123 docker compose -f ops/compose/docker-compose.emporio.yml -f ops/compose/testing/compose.candidate.yml config >/dev/null` | 0 | sete services e modelo mesclado validos; nenhum container iniciado |

Total final: **161 testes aprovados** (`9 + 112 + 40`). Foram adicionados
**13 testes candidatos causais** nesta resposta, cobrindo os mutantes da
Secao 26.4 por chamadas aos helpers reais, arquivos temporarios e doubles de
subprocesso.

### 27.5 Evidencias causais e estado protegido final

- fluxo completo com CI run `100` e publisher run `200`;
- outcome adulterado separadamente em candidate ID, artifact ID, digest e
  attempt;
- listas combinadas `same + unrelated` e `descendant + unrelated`;
- effective plan com chave extra, resolution adulterada e predecessor
  semanticamente invalido;
- pending adulterado em sidecar, metadata, componente, imagem e binding, com
  zero comando Docker;
- receipt com chave extra, servico/probe duplicado e binding divergente;
- trust com `repository.full_name` nao canonico;
- mutantes de tempo, tag, label, state, origin e provenance;
- falhas individuais de down, remove, inspect, contagem e logout com todas as
  demais tentativas observadas;
- staging parcial sem residuo;
- `publish_guard` para mesmo SHA, HEAD avancado e lineage nao relacionada;
- LF real comprovado em `GITHUB_ENV` e `GITHUB_OUTPUT`.

Verificacoes finais:

```text
CWD=/home/gregorio/git/baronesa/emporio
indice Git real: 0 entradas
HEAD: inexistente (exit 128)
tags: 0
reflog: 0
workflows ativos: ci.yml e publish-candidate.yml (2)
__pycache__ e *.pyc: 0
S13: inexistente
imagem actionlint fixada: ausente
containers candidate-*: 0
volumes candidate-*: 0
networks candidate-*: 0
```

Nao houve Maven, NPM, Docker build, Compose up, publicacao, workflow remoto,
login/push GHCR, attestation real, acesso a producao, `git add`, commit, tag ou
push. A unica operacao externa foi o pull efemero permitido da imagem
`actionlint`, removida ao final.

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

## 28. Revisao das correcoes causais e emenda terminal

> **Resultado:** `IN_PROGRESS — emenda do orquestrador`  
> **Data:** `2026-07-29`

### 28.1 Resultado da revisao

As treze correcoes da Secao 27 satisfazem os bloqueios registrados na Secao
26. Os bindings de runs distintos, lineage integral, validadores estritos,
pending antes de Docker, receipt, cleanups cumulativos e rollback parcial
estao coerentes entre codigo, testes e evidencias persistidas.

A S12 nao e aceita ainda por uma incompatibilidade remanescente no proprio
contrato do orquestrador, e nao por falha nas correcoes da Secao 27.

### 28.2 Incompatibilidade comprovada

Para candidato selecionado com paths cumulativos vazios,
`lineage.effective()` chama:

```python
catalog.resolve(catalog.load_yaml(), [])
```

O resolvedor S05 rejeita esse input com:

```text
CatalogError: at least one --changed path or --first-release is required
```

Isso impede a criacao do effective plan e do outcome para:

- `no_changes`;
- `already_published`;
- `superseded` quando ha candidato descendente.

Os testes anteriores verificavam a decisao do modo separadamente, mas nao
executavam a criacao e validacao do effective plan terminal completo.

### 28.3 Decisao do orquestrador

A task recebeu a Secao 21, `Emenda terminal do orquestrador`, que define:

- effective plan v2 com campo `mode`;
- resolution exata para cada modo terminal;
- proibicao de pending em modo terminal;
- validacao referencial completa de outcome `already_published`;
- oito provas causais fechadas.

Essa emenda nao altera `catalog.py`, nao reabre a arquitetura e nao converte
a lacuna em erro retroativo do executor.

### 28.4 Intervencao desta revisao

O orquestrador executou apenas uma chamada diagnostica direta ao helper para
confirmar a incompatibilidade; nenhuma suite foi repetida. Os tres arquivos
`*.pyc` criados por essa importacao foram removidos imediatamente e nenhum
cache permaneceu.

Foram alterados somente task, tracker e este relatorio. Nenhum codigo,
workflow, schema ou teste foi alterado pelo orquestrador.

A resposta deve ser registrada como:

```text
## 29. Resposta a emenda terminal do orquestrador
```

Estado:

```text
S12 IN_PROGRESS — emenda terminal do orquestrador
S13 nao autorizada
```

## 29. Resposta a emenda terminal do orquestrador

> **CWD:** `/home/gregorio/git/baronesa/emporio`  
> **Data:** `2026-07-29`  
> **Estado:** `IN_PROGRESS — aguardando revisão do orquestrador`

### 29.1 Implementacao literal da emenda

1. `candidate-effective-plan.json` passou para `schemaVersion: 2` e possui
   exatamente o campo adicional `mode`, limitado a `continue`,
   `already_published`, `no_changes` e `superseded`.
2. `continue` preserva a resolution integral do resolvedor S05. Para
   predecessor `selected`, paths cumulativos vazios falham; o resolvedor nao
   recebe mais esse input invalido.
3. `no_changes`, `already_published` e `superseded` sao resolvidos
   exclusivamente pelo adaptador `lineage.py`, com arrays de build e validacao
   vazios, os seis componentes herdados e o warning exato de cada modo.
4. `already_published` e `no_changes` exigem predecessor `selected`.
   `superseded` aceita predecessor `first` quando `main` avancou antes da
   descoberta ou `selected` quando existe candidato descendente.
5. `write_outputs()` exige que o modo informado coincida com o modo persistido
   no effective plan. Os tres modos terminais emitem matriz vazia,
   `has_builds=false` e exit zero.
6. Build e assemble continuam condicionados a `mode == continue`; integrated
   depende de assemble. Os modos terminais seguem diretamente para o outcome.
   `pending_from()` e `validate_pending()` rejeitam effective plan cujo modo
   nao seja `continue`, antes de qualquer chamada Docker.
7. A descoberta de `already_published` consulta
   `/repos/greggorio/abaronesa-emporio/actions/artifacts/<artifact-id>`,
   valida artifact, digest, run verde, manifesto, sidecar, metadata, candidate
   ID, commit e bindings. Referencias repetidas ao mesmo candidato sao
   deduplicadas antes da classificacao de lineage.
8. `predecessorCandidateId` deve ser igual ao proprio `candidateId` em
   `already_published`. Em `published`, deve ser exatamente o predecessor do
   manifesto, inclusive `null` no primeiro candidato. O outcome produzido
   pelo guard final usa o candidato reencontrado como predecessor quando
   retorna `already_published`.

`tools/releases/catalog.py` nao foi alterado.

### 29.2 Arquivos alterados

```text
.github/workflows/publish-candidate.yml
tools/candidates/lineage.py
tools/candidates/outcome.py
tools/candidates/previous_candidate.py
tools/candidates/validate_candidate_workflow.py
tools/candidates/tests/test_causal_corrections.py
tools/candidates/tests/test_definitive_contract.py
tools/candidates/tests/test_terminal_amendment.py
tools/releases/candidate_manifest.py
tools/releases/tests/test_candidate_manifest_v2.py
docs/infrastructure/deployment/ci/CI.md
docs/infrastructure/deployment/release-control/CANDIDATOS.md
docs/infrastructure/deployment/implementation/slices/S12-publicacao-candidato-e-proveniencia.report.md
```

A task S12, o tracker, `tools/releases/catalog.py`, codigo comercial,
migrations, Dockerfiles, Compose e gateway nao foram alterados.

### 29.3 Oito provas obrigatorias

1. Os quatro modos passam por `lineage.effective()` e
   `lineage.validate_effective()` com schema v2.
2. Os CLIs reais de predecessor para `no_changes`, `already_published` e
   `superseded` retornam exit zero, matriz vazia e `has_builds=false`.
3. O validador do workflow confirma os gates de build/assemble e a producao de
   outcomes terminais; os tres shapes de outcome passam.
4. Classification, warning ou lista herdada terminal adulterados produzem
   `EFFECTIVE_RESOLUTION`.
5. Pending ligado a effective terminal falha antes de qualquer chamada do
   runner Docker.
6. `already_published` falha separadamente para artifact inexistente,
   expirado, nome incorreto, digest divergente, run divergente, candidate ID
   divergente e metadata divergente.
7. `predecessorCandidateId` divergente falha em `already_published` e
   `published`.
8. Os outputs terminais usam LF real, matriz vazia e `has_builds=false`; a
   matriz 20.14 integral permaneceu verde.

Foram adicionados exatamente **8 testes causais** em
`test_terminal_amendment.py`.

### 29.4 Matriz 20.14

| Comando exato | Exit | Resultado e interpretacao |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -p 'test_*.py'` | 0 | 9 testes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py` | 0 | `candidate:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | 112 testes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/candidates/validate_candidate_workflow.py` | 0 | `candidate-workflow:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -p 'test_*.py'` | 0 | 48 testes aprovados |
| `docker run --rm -v "$PWD:/repo:ro" -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color .github/workflows/ci.yml .github/workflows/publish-candidate.yml` | 0 | dois workflows aprovados |
| `docker image rm docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 0 | imagem efemera removida |
| `env CANDIDATE_PROJECT=candidate-200-1 CANDIDATE_BACKEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-backend@sha256:1111111111111111111111111111111111111111111111111111111111111111 CANDIDATE_WEBSITE_BACK_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-backend@sha256:2222222222222222222222222222222222222222222222222222222222222222 CANDIDATE_FRONTEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-frontend@sha256:3333333333333333333333333333333333333333333333333333333333333333 CANDIDATE_WEBSITE_FRONT_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-frontend@sha256:4444444444444444444444444444444444444444444444444444444444444444 CANDIDATE_WHATSAPP_IMAGE=ghcr.io/greggorio/abaronesa-emporio-whatsapp-service@sha256:5555555555555555555555555555555555555555555555555555555555555555 CANDIDATE_GATEWAY_IMAGE=ghcr.io/greggorio/abaronesa-emporio-gateway@sha256:6666666666666666666666666666666666666666666666666666666666666666 BACKEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-backend@sha256:1111111111111111111111111111111111111111111111111111111111111111 WEBSITE_BACK_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-backend@sha256:2222222222222222222222222222222222222222222222222222222222222222 FRONTEND_IMAGE=ghcr.io/greggorio/abaronesa-emporio-frontend@sha256:3333333333333333333333333333333333333333333333333333333333333333 WEBSITE_FRONT_IMAGE=ghcr.io/greggorio/abaronesa-emporio-website-frontend@sha256:4444444444444444444444444444444444444444444444444444444444444444 WHATSAPP_IMAGE=ghcr.io/greggorio/abaronesa-emporio-whatsapp-service@sha256:5555555555555555555555555555555555555555555555555555555555555555 GATEWAY_IMAGE=ghcr.io/greggorio/abaronesa-emporio-gateway@sha256:6666666666666666666666666666666666666666666666666666666666666666 POSTGRES_IMAGE=docker.io/library/postgres@sha256:7777777777777777777777777777777777777777777777777777777777777777 POSTGRES_ADMIN_USER=candidate_admin POSTGRES_ADMIN_PASSWORD=candidate_admin_password ERP_DB_NAME=candidate_erp ERP_DB_USER=candidate_erp_user ERP_DB_PASSWORD=candidate_erp_password WEBSITE_DB_NAME=candidate_web WEBSITE_DB_USER=candidate_web_user WEBSITE_DB_PASSWORD=candidate_web_password INTEGRATION_SYSTEM_TOKEN_SECRET=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa GOOGLE_CLIENT_ID=candidate.invalid GOOGLE_CLIENT_SECRET=candidate_google_secret CANDIDATE_ROOT_EMAIL=root@candidate.invalid CANDIDATE_ROOT_PASSWORD=candidate_root_password GATEWAY_LOOPBACK_PORT=49123 docker compose -f ops/compose/docker-compose.emporio.yml -f ops/compose/testing/compose.candidate.yml config >/dev/null` | 0 | sete services e modelo mesclado validos; nenhum container iniciado |

Total final: **169 testes aprovados** (`9 + 112 + 48`). Nao houve falha
intermediaria de implementacao; a execucao preliminar e a repeticao integral
passaram. O pull esperado da imagem fixada do `actionlint` nao e falha e a
imagem foi removida no comando seguinte.

### 29.5 Estado protegido final

```text
CWD=/home/gregorio/git/baronesa/emporio
indice Git real: 0 entradas
HEAD: inexistente (exit 128)
tags: 0
reflog: 0
workflows ativos: ci.yml e publish-candidate.yml (2)
__pycache__ e *.pyc: 0
S13: inexistente
imagem actionlint fixada: ausente
containers candidate-*: 0
volumes candidate-*: 0
networks candidate-*: 0
```

Nao houve Maven, NPM, Docker build, Compose up, publicacao, workflow remoto,
login/push GHCR, attestation real, acesso a producao, `git add`, commit, tag ou
push. A unica operacao externa foi o pull efemero permitido da imagem
`actionlint`, removida ao final.

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

---

## 30. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-29`

A emenda terminal foi implementada literalmente e fecha a incompatibilidade
que pertencia ao contrato do orquestrador:

- o effective plan v2 identifica explicitamente os quatro modos;
- `continue` continua usando o resolvedor canonico S05 e exige paths quando
  existe predecessor;
- os tres modos terminais usam resolutions canonicas, matriz vazia e nao
  habilitam build, assemble ou integracao;
- pending ligado a modo terminal falha antes de qualquer operacao Docker;
- outcomes `already_published` e `published` possuem os bindings referenciais
  exigidos;
- `catalog.py` permaneceu inalterado;
- as oito provas causais cobrem helpers e CLIs reais;
- a matriz persistida registra 169 testes, `actionlint` e
  `docker compose config` aprovados;
- indice, `HEAD`, tags, reflog, caches, recursos efemeros e acesso externo
  proibido permaneceram ausentes.

O orquestrador revisou o codigo correspondente e nao repetiu as suites
persistidas pelo executor. Nao ha divergencia material entre a task, o codigo
e a Secao 29.

Decisao:

```text
S12 ACCEPTED — 29/07/2026
S13 autorizada
```
