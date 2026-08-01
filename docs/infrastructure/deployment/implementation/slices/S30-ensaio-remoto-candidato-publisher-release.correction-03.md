# S30 — correction-03: fechar contrato CI, trust autenticado e guarda final

> **Estado:** AUTHORIZED pelo orquestrador em 01/08/2026
> **Slice:** S30 — ensaio remoto de candidato, publisher e release
> **Commit-base:** bf20c02fb374e9bd3bdabc1dc5f8e604b0a2a4c2
> **Remote:** git@github.com:greggorio/abaronesa-emporio.git
> **Branch:** main

## 1. Decisão terminal do orquestrador

A correction-02 provou remotamente E e F, mas não fechou S30:

- CI 30686261529 ainda falhou em contracts por G;
- Publish Candidate 30686325732 ainda falhou em trust por H;
- a suíte causal de candidatos ainda contém o teste irmão de E, dependente do
  ambiente externo;
- a revisão do código revelou a mesma chamada autenticada ausente em
  tools/candidates/publish_guard.py, que seria alcançada depois do trust.

S30 permanece REJECTED. Esta correction-03 deve fechar G, H, a ocorrência
irmã no publish_guard e o teste causal irmão de E em uma única execução.

### 1.1 Escolha arquitetural para H

Escolha fechada: resolver o HEAD canônico pela API do GitHub, sem reativar
credenciais persistentes no checkout.

Preservar obrigatoriamente:

- persist-credentials: false nos checkouts;
- repositório fixo greggorio/abaronesa-emporio;
- ref fixa main;
- igualdade exata para o mesmo SHA;
- classificação continue, superseded e falha fechada para história
  divergente, inexistente ou não ancestral;
- nenhuma publicação quando a verificação falhar.

O token GH_TOKEN: ${{ github.token }} deve existir somente nos passos que
executam a resolução autenticada do HEAD:

- passo trust.py do job trust;
- passo publish_guard.py do job publish.

Não usar persist-credentials: true, git fetch autenticado, gh ou credencial
gravada no workspace. O token não pode aparecer em argumentos, stdout, stderr,
artefatos ou mensagens de erro.

A implementação deve usar somente biblioteca padrão ou código já presente.
O endpoint deve ser fixo e não derivado do payload:

1. GET https://api.github.com/repos/greggorio/abaronesa-emporio/git/ref/heads/main;
2. validar resposta 2xx, ref == refs/heads/main, object.type == commit e SHA
   hexadecimal de 40 caracteres;
3. GET https://api.github.com/repos/greggorio/abaronesa-emporio/compare/<received_sha>...<main_sha>
   e consultar a comparação entre o SHA recebido e o SHA de main, mantendo a
   semântica de git merge-base --is-ancestor:
   - identical / mesmo SHA -> continue;
   - main à frente do SHA recebido (ahead na comparação received...main)
     -> superseded;
   - behind, diverged, resposta inválida, 404, timeout ou qualquer outro
     status -> falha fechada;
4. toda falha da API deve ser sanitizada e não pode incluir o token.

A mesma resolução deve ser usada pelo trust.py e pelo publish_guard.py. Não
deixar uma segunda chamada git fetch latente na guarda final.

## 2. Fronteira autorizada

Alterar somente:

- .github/workflows/ci.yml;
- .github/workflows/publish-candidate.yml;
- tools/ci/validate_ci.py;
- tools/ci/tests/test_ci.py;
- tools/candidates/trust.py;
- tools/candidates/publish_guard.py;
- tools/candidates/validate_candidate_workflow.py;
- tools/candidates/tests/test_definitive_contract.py;
- tools/candidates/tests/test_causal_corrections.py;
- docs/infrastructure/deployment/implementation/slices/S30-ensaio-remoto-candidato-publisher-release.report.md.

Não alterar task S30, correction-01, correction-02, S12 ou outros relatórios
históricos, candidate_plan.py, previous_candidate.py, lineage.py,
finalize_candidate.py, backend, frontend, release_control, outros workflows,
OpenAPI, schemas, .gitignore, HANDOFF, tracker, produção ou S31.

O relatório S30 pode ser atualizado somente com os fatos desta execução e deve
terminar com:

~~~
IN_PROGRESS — aguardando revisão do orquestrador
~~~

## 3. Correções obrigatórias

### G — comando canônico da CI

Em .github/workflows/ci.yml, corrigir somente a chamada para:

~~~
python3 tools/releases/release_control_contract.py validate
~~~

Atualizar REQUIRED_COMMANDS em tools/ci/validate_ci.py para exigir a forma
exata, de modo que a forma sem validate seja rejeitada.

Adicionar teste causal em tools/ci/tests/test_ci.py que:

- confirme a forma corrigida;
- remova validate e prove que o validador falha;
- não relaxe a inspeção para uma substring que aceite ambas as formas.

### H — trust por API sem credencial persistente

Implementar em trust.py um helper reutilizável para:

- exigir GH_TOKEN não vazio;
- chamar somente os endpoints canônicos descritos na Seção 1.1;
- validar status HTTP, JSON, ref, tipo e SHA;
- classificar comparação com os três resultados fechados;
- usar timeout;
- falhar sem imprimir token, headers ou corpo sensível;
- não executar git fetch, git remote, git rev-parse ou equivalente para
  resolver main.

Atualizar o passo de execução de trust.py para receber exatamente
GH_TOKEN: ${{ github.token }}. Manter o checkout com
persist-credentials: false e a ordem já aceita por F:

~~~
checkout -> persist workflow-run.json -> download candidate-plan -> trust.py
~~~

Atualizar publish_guard.py para usar o mesmo helper/API e remover sua chamada
latente a git fetch. Manter a guarda final de idempotência e lineage; apenas
substituir o transporte de resolução de main.

O passo existente de publish_guard.py deve continuar recebendo
GH_TOKEN: ${{ github.token }}. Não ampliar permissões globais nem adicionar
credencial a outro job.

### Testes causais mínimos de H

Os testes devem usar doubles locais, sem rede, e provar no mínimo:

- SHA idêntico -> continue;
- main à frente -> superseded;
- behind, diverged, 404, timeout, JSON inválido, ref errada, tipo errado e
  SHA inválido -> falha fechada;
- token ausente -> falha fechada;
- o endpoint não pode ser alterado para fork, outro repositório ou outra ref;
- token não aparece na mensagem de erro;
- trust.py e publish_guard.py não chamam git fetch nem dependem de origin/main;
- checkout sem persist-credentials: false, token ausente no passo correto ou
  token colocado em outro passo são rejeitados pelo contrato;
- a ordem checkout/persist/download/trust e os quatro gates de F continuam
  rejeitando seus mutantes.

### Teste irmão de E

Em tools/candidates/tests/test_causal_corrections.py, isolar
GITHUB_RUN_ID e GITHUB_RUN_ATTEMPT nos testes que chamam
finalize_candidate.finalize() usando os IDs da própria fixture. Adicionar
prova sob ambiente hostil e ambiente limpo, sem alterar produção, e manter a
prova de que um run alheio continua sendo rejeitado.

## 4. Validação local obrigatória

Executar a partir de /home/gregorio/git/baronesa/emporio:

~~~
python3 tools/ci/validate_ci.py
python3 tools/candidates/validate_candidate_workflow.py
python3 tools/releases/validate_release_workflow.py
python3 tools/releases/validate_publisher_ui.py
python3 tools/releases/validate_publisher_identity_bridge.py
python3 tools/deploy/validate_deploy_workflow.py
python3 tools/deploy/validate_rollback_contract.py
python3 tools/deploy/validate_rollback_runtime.py
python3 tools/releases/release_control_contract.py validate
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v
GITHUB_RUN_ID=999999999 GITHUB_RUN_ATTEMPT=99 PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v
python3 tools/ci/secret_scan.py --tracked
git diff --check
~~~

As duas execuções da suíte de candidatos devem passar; a segunda deve provar
que fixtures não dependem dos IDs do host. Qualquer teste que tente rede,
Docker, SSH ou produção deve ser substituído por double local.

Antes do commit, verificar que a lista staged contém exclusivamente os dez
caminhos da Seção 2, que git diff --cached --check retorna 0 e que nenhum
segredo ou resíduo foi criado.

## 5. Commit, push e observação remota

Depois de todos os gates locais:

~~~
git add .github/workflows/ci.yml \
  .github/workflows/publish-candidate.yml \
  tools/ci/validate_ci.py \
  tools/ci/tests/test_ci.py \
  tools/candidates/trust.py \
  tools/candidates/publish_guard.py \
  tools/candidates/validate_candidate_workflow.py \
  tools/candidates/tests/test_definitive_contract.py \
  tools/candidates/tests/test_causal_corrections.py \
  docs/infrastructure/deployment/implementation/slices/S30-ensaio-remoto-candidato-publisher-release.report.md
git diff --cached --check
git commit -m "fix: close CI contract and trust authentication"
git push origin main
~~~

Usar exatamente origin e main. Não usar force, tags, outra branch, outro
remote, --no-verify, git init, alteração de identidade ou commit adicional.

Observar somente os runs originados por esse SHA:

- CI deve concluir verde em plan, contracts, backend, os quatro jobs web,
  whatsapp e images;
- Publish Candidate deve concluir verde em trust, predecessor, build,
  assemble, integrated e publish, sem erro de autenticação;
- registrar os artifacts, manifestos, digests, provenance e attestations
  produzidos, sem copiar tokens ou headers;
- confirmar que nenhum release global, tag, deploy, rollback ou efeito de
  produção ocorreu.

Se qualquer gate falhar, não tentar uma quarta correção improvisada: registrar
o run, job, etapa, saída sanitizada, causa e fronteira no relatório e parar
para revisão do orquestrador.

## 6. Prompt formal de delegação

~~~
Execute exclusivamente a correction-03 da S30 em
/home/gregorio/git/baronesa/emporio.

Leia a task S30, a emenda-01, correction-01, correction-02, esta correction,
o HANDOFF, o tracker, o relatório S30, S12 e os validadores relacionados.

S30 continua REJECTED. Corrija G, H, a chamada equivalente em
publish_guard.py e o teste irmão de E. Para H, a decisão está fechada:
resolva o HEAD de greggorio/abaronesa-emporio:main pela API do GitHub usando
GH_TOKEN somente nos passos trust.py e publish_guard.py. Preserve
persist-credentials: false, a ordem checkout -> persistência -> download ->
trust, a semântica de lineage/idempotência e todas as permissões existentes.
Não use git fetch para resolver main, não persista credencial e não exponha
token.

Corrija ci.yml para chamar exatamente:
python3 tools/releases/release_control_contract.py validate
e faça validate_ci.py/test_ci.py rejeitarem a forma sem subcomando.

Atualize trust.py, publish_guard.py, o validador de candidate e os testes
causais/definitivos. Os doubles devem provar os caminhos identical/continue,
main à frente/superseded, behind/diverged/erro de API/token ausente, ausência
de fetch e ausência de vazamento de token. Isole os IDs de ambiente nos testes
de finalize_candidate da suíte causal.

Execute todos os comandos da Seção 4, inclusive a suíte de candidatos sob
GITHUB_RUN_ID=999999999 e GITHUB_RUN_ATTEMPT=99. Não use rede local adicional,
Docker, SSH, produção ou secrets reais.

Se os gates locais passarem, revise a lista staged e execute exatamente:
git add .github/workflows/ci.yml \
  .github/workflows/publish-candidate.yml \
  tools/ci/validate_ci.py \
  tools/ci/tests/test_ci.py \
  tools/candidates/trust.py \
  tools/candidates/validate_candidate_workflow.py \
  tools/candidates/publish_guard.py \
  tools/candidates/tests/test_definitive_contract.py \
  tools/candidates/tests/test_causal_corrections.py \
  docs/infrastructure/deployment/implementation/slices/S30-ensaio-remoto-candidato-publisher-release.report.md
git diff --cached --check
git commit -m "fix: close CI contract and trust authentication"
git push origin main

Observe somente a CI e o Publish Candidate desse SHA. Não publique release,
não crie tag, não execute publish-release.yml, deploy-production.yml ou
rollback-production.yml, não faça SSH/VPS/produção, não use force/--no-verify,
não crie credenciais e não crie S31.

Atualize o relatório S30 com comandos, exits, runs, artifacts, digests,
provenance, attestation, resíduos, acessos e divergências sanitizadas.
Termine exatamente com:
IN_PROGRESS — aguardando revisão do orquestrador
~~~
