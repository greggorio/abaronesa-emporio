# Retomada da S30a — instruções para o próximo orquestrador

> **Data:** 01/08/2026
> **Workspace:** `/home/gregorio/git/baronesa/emporio`
> **Remote:** `git@github.com:greggorio/abaronesa-emporio.git` — branch `main`
> **HEAD local = origin/main = `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`**

Leia **somente este documento** antes de agir. Ele substitui, para efeito de
retomada, a leitura integral do relatório S30 (1.900 linhas), das três
corrections e das duas amendments. Aqueles arquivos permanecem como evidência
histórica; não são pré-requisito.

---

## 1. Antes de tudo: o que deu errado no processo

Este programa gastou seis ciclos remotos para fechar oito defeitos. Todos os
defeitos eram preexistentes e latentes. O custo não veio deles — veio do método.
Não repita:

| Antipadrão | Consequência real | O que fazer |
|---|---|---|
| Contratos afirmam **texto**, não comportamento | `validate_ci.py` exigia a string de um comando sem provar que ele roda; nasceram os defeitos G e G-linha-67 | Já corrigido: `tools/ci/invocability.py` prova os 27 comandos. Mantenha e estenda |
| `set -e` num passo de 11 comandos | Cada execução revelava **um** defeito; o seguinte só aparecia no ciclo seguinte | Rode tudo localmente e colete **todas** as falhas antes de qualquer push |
| Fronteira por **lista de arquivos** | Duas emendas minhas quebraram a matriz por omitir arquivos da mesma classe (validadores Docker) | Use fronteira por **classe**: "os Dockerfiles e tudo que afirma suas referências" |
| GitHub Actions como depurador | ~10 min e um commit permanente por defeito | Docker está disponível local. Prove antes de empurrar |
| Aceite com critérios acoplados | S30a exigia CI verde **e** candidato verde **e** manifesto; nada podia ser aceito enquanto um CVE bloqueasse | Separe o que é entregável do que depende de terceiros |

**Regra prática:** uma emenda por ciclo é sinal de contrato mal escrito, não de
executor ruim. Se precisar de uma terceira, o problema é a fronteira.

---

## 2. Estado real, verificado

### Remoto

```text
4 commits em main; 0 tags; 0 releases; 0 imagens em GHCR
8 runs, todos failure; 3 artifacts, todos candidate-plan
```

Nunca executados remotamente: job `images` da CI e os jobs `predecessor`,
`build`, `assemble`, `integrated`, `publish` do `publish-candidate`.
Essa é a maior superfície ainda não provada — 45 passos.

### Local — 21 arquivos alterados + 8 novos, **verificados e não commitados**

Tudo abaixo foi reproduzido de forma independente pelo orquestrador anterior:

- `.github/workflows/ci.yml` — subcomando `validate` nas três chamadas;
  PostgreSQL por digest imutável; gate de invocabilidade no job `contracts`
- `tools/ci/invocability.py` + teste — prova os 27 comandos dos dois workflows
  na fronteira de `parse_args`; mata 6 classes de mutante
- `tools/ci/validate_ci.py`, `tools/candidates/validate_candidate_workflow.py`
  e testes — contratos de subcomando, digest do Postgres, ordem do `trust`,
  token autenticado
- Seis `Dockerfile` — imagens-base atualizadas, digest SHA-256 preservado
- `tools/docker/*` e testes — referências novas, sem afrouxar critério
- `backend/pom.xml`, `website_back/pom.xml` — Spring Boot `3.3.5 -> 3.3.13`
- `whatsapp_service/package-lock.json` — lockfile atualizado dentro do semver

**Gates locais, todos exit 0:** 12 validadores; 30 + 68 + 298 + 26 + 59 + 4 + 4
testes; suíte de candidatos também sob IDs hostis; `secret_scan --tracked`;
`git diff --check`. Regressões reais passaram com PostgreSQL em container:
backend 82 testes, website_back 63, whatsapp 7. Docker limpo, BuildKit `0B`.

**Nada disso está commitado.** É o primeiro item da retomada.

---

## 3. O único bloqueio real: Trivy

```text
224 -> 110 -> 25 ocorrências   (-89%)
frontend, website_front, gateway: ZERO
backend 11 | website_back 9 | whatsapp_service 5
```

As 25 ocorrências são **16 CVEs distintos**:

```text
CVE-2026-22732 (CRITICAL)  CVE-2026-59873 (CRITICAL)
CVE-2025-10492  CVE-2025-41249  CVE-2026-2100   CVE-2026-6009
CVE-2026-40973  CVE-2026-41842  CVE-2026-41845  CVE-2026-41850
CVE-2026-56131  CVE-2026-56408  CVE-2026-12151  CVE-2026-13149
CVE-2026-14257  CVE-2026-59874
```

Fato decisivo: as correções disponíveis exigem **Spring em linha menor
diferente**, **JasperReports 7**, **pacotes da base Alpine** ou **o npm
embarcado na imagem Node**. Os dois últimos **não são acionáveis** — dependem de
rebuild upstream. Forçá-los com `apk upgrade` destruiria a reprodutibilidade do
digest fixado.

Ou seja: **é impossível zerar por esforço próprio.** Bloquear o programa nisso
significa bloqueio indefinido.

### Decisão recomendada

`.trivyignore.yaml` com os 16 CVEs, cada entrada com justificativa e
**data de expiração ≤ 90 dias**, mais um validador que rejeite entrada sem prazo,
sem justificativa ou com curinga. O gate volta a vermelho sozinho no vencimento.

Sustentação: candidato é `deployable: false`. Publicar candidato em GHCR **não**
coloca nada em produção — produção exige release e deploy, ambos gates
separados e ainda inexistentes. A remediação de Spring/JasperReports vira slice
própria, não pré-requisito da prova da cadeia.

Alternativas, se o usuário preferir: mapear CVE a CVE o que é acionável e
migrar Spring/Jasper antes (prazo indefinido, duas migrações reais), ou bloquear
até zerar (risco concreto de alguém desabilitar o gate inteiro).

---

## 4. Caminho até o verde — faça nesta ordem

1. **Commitar o que já está verificado.** Três commits locais, sem push ainda:
   `fix: close CI command contract and local parity`,
   `fix: bump hardened base images`,
   `fix: patch application dependencies`.
   Isso destrava trabalho verificado que está em risco há três ciclos.
2. **Resolver o Trivy** conforme a decisão da Seção 3.
3. **Rodar a paridade local completa** antes de empurrar: seis builds, seis
   scans, matriz inteira. Docker está autorizado e disponível.
4. **Um push.** A partir daí a CI deve ficar verde nos oito jobs pela primeira
   vez, e o `publish-candidate` vai exercitar 45 passos nunca executados.
5. **Esperar defeitos novos nesses 45 passos** — `build`, GHCR, attestation,
   `integrated` com compose e Postgres. Quando aparecerem, **não** emita uma
   emenda por defeito: colete todos, corrija em lote, fronteira por classe.

---

## 5. Contexto operacional que evita retrabalho

- **`read:packages` já está no token** (`gist, read:org, read:packages, repo`).
  A verificação GHCR está destravada.
- **Docker local funciona**; Trivy não está instalado — use a imagem oficial
  `aquasec/trivy:0.70.0@sha256:be1190af…`.
- **`mvn -B verify` precisa de PostgreSQL**: suba o container com o mesmo digest
  do `ci.yml` (`postgres:16.10-alpine3.22@sha256:02966064…`, `testdb/test/test`).
- **A conta tem pacotes GHCR antigos** (ex.: `cafe-frontend`) sem relação com
  este projeto. Ao cruzar digests, filtre por repositório.
- **`actionlint` não está disponível** e a task proíbe instalar.
- **Nunca use** force, tags, outro remote/branch, `--no-verify`, `git init` ou
  alteração de identidade global.
- **Proibido sem autorização específica:** `publish-release.yml`,
  `deploy-production.yml`, `rollback-production.yml`, SSH, VPS, DNS, produção,
  cleanup remoto destrutivo, criação de credencial.

---

## 6. Escopo restante do programa

```text
S30a  CI verde + candidato publicado por digest        <- em andamento
S30b  release global pela UI publisher, reconciliação  <- não criada
S31   preparação da VPS
S32   primeiro release e implantação acompanhados
S33   exercícios de falha e encerramento operacional
```

A S30 original foi dividida porque acumulava CI, candidato, release, idempotência
e restart num único aceite — e por isso nunca convergiu. Mantenha slices com um
objetivo observável cada.

---

## 7. Sugestão franca sobre o método

O rito de contrato fechado, relatório extenso e aceite formal por slice fazia
sentido enquanto o trabalho era projetar contratos. Para **caçar defeitos
latentes de ambiente**, ele é caro e lento: transforma cada descoberta num ciclo
completo de documentação.

Para o restante desta implementação, considere:

- fronteira ampla — todo o repositório exceto caminhos de produção — com
  objetivo único e verificável por execução;
- relatório curto: comandos, exits, divergências. Sem reprodução de inventários
  inteiros no corpo do documento;
- prova local obrigatória antes de qualquer push;
- decisões de política tomadas com dado medido, e **uma vez só**.

O que já está no repositório sustenta isso: o gate de invocabilidade, a paridade
local com Docker e as regressões reais existem agora. A parte cara já foi paga.
