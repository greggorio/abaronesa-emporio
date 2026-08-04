# S45 — correction-01: convergência da UI deployer e release operável

> **Data:** 04/08/2026
> **Estado:** `AUTHORIZED`
> **Contrato principal:** `S45-prontidao-producao-gate-c.task.md`
> **Relatório contínuo:** `S45-prontidao-producao-gate-c.report.md`
> **Deploy/rollback comercial:** proibidos

## 1. Evidência aceita e aceite retido

A execução original da S45 fechou corretamente capacidade, swap, configuração
protegida, TLS, isolamento do control plane e ensaio de backup/restore. Esses
resultados são aceitos como checkpoint e não devem ser repetidos sem causa.

O aceite terminal fica retido por dois bloqueios observáveis no código
publicado.

### 1.1 Scope incompatível

O cliente de produção exige literalmente:

```text
deployment:read deployment:execute deployment:rollback
```

em `frontend/src/services/releaseDeployerClient.js`, tanto na resposta da troca
quanto nas claims do JWT.

O backend publicado em `v0.1.0` emite:

```text
deployment:read deployment:execute
```

em `DeployerReleaseControlIdentityService.SCOPE`. Seu teste ainda exige que o
scope não contenha `rollback`. Assim, depois do primeiro start, a troca de
identidade responderia 200, mas o cliente descartaria a resposta como inválida
antes de consultar o control plane.

### 1.2 Namespace operacional não roteado

O Nginx do host encaminha somente:

```text
/api/release-control/v1/
```

para `127.0.0.1:8180`. Porém plano, release, current, operação, deploy e
rollback usam:

```text
/api/deployment-control/v1/
```

Esse namespace cai no gateway comercial, cujo contrato o rejeita explicitamente
com 404. Portanto o probe público de `capabilities=401` não prova que a UI
consegue planejar ou implantar.

São defeitos causais de prontidão. Não são motivo para criar S46 antes da
correção.

## 2. Consequência sobre a release

`v0.1.0` é imutável e contém o backend com scope incompatível. É proibido
retag, editar assets, substituir digest ou publicar novamente a mesma versão.

A primeira release operável será:

```text
tag/name       v0.1.1
previous       v0.1.0
tipo           patch estável, não draft, não prerelease
descrição      Corrige o scope da identidade deployer e o roteamento same-origin do control plane para o primeiro deploy de produção.
```

A `v0.1.0` permanece preservada como release inicial histórica e não será
implantada. Depois desta correction, a S46 fará o primeiro deploy acompanhado
de `v0.1.1`.

## 3. Correções causais

### 3.1 Backend e contrato cruzado

Alterar o backend para emitir exatamente, nessa ordem:

```text
deployment:read deployment:execute deployment:rollback
```

Atualizar os testes Java que ainda proíbem rollback. Fortalecer o validador e
seus mutantes para comparar, de forma executável:

- constante e resposta do backend;
- `DEPLOYER_SCOPE` do frontend;
- capabilities do frontend;
- capabilities e guards do runtime Python;
- contrato S27.

Qualquer diferença de ordem, ausência, duplicação, scope `release:*` ou
capability extra deve falhar. Preservar audience, RS256, TTL 300, issuer, `kid`,
subject e separação publisher/deployer.

### 3.2 Nginx same-origin

No host ERP, encaminhar ambos os namespaces para o mesmo control plane:

```text
/api/release-control/v1/
/api/deployment-control/v1/
```

No host website, ambos retornam 404 local e nunca alcançam control plane,
gateway ou outro tenant.

Preservar a rota de identidade deployer no gateway/backend e preservar o 404
defensivo do gateway para `/api/deployment-control/`. O acesso ao runtime só
pode ocorrer pela regra explícita do Nginx de host ERP.

Adicionar mutantes para ausência do segundo namespace, proxy no website,
proxy da identity para 8180, remoção do bloqueio defensivo no gateway, path
parcial e upstream diferente de loopback.

## 4. Gates, commit e candidato

Revalidar Git/GitHub antes de editar. Checkpoint esperado:

```text
HEAD/origin/main/remoto  de4c4872ee3a6994bb66ecd173c0ffe81fa5fbd4
stage/diff tracked       vazios
report S45 SHA-256       6878c523b7f69956bafe553fd05ffbb9c788a07757f34b51e946664d6b48cd42
CI                       30952146064, success, 13/13
Candidate                30952948377, success, 11/11, deployable=false
deploy/rollback runs     0/0
```

Executar testes causais, backend/frontend atingidos, oito suítes canônicas,
todos os validadores, secret scan completo e staged com `unsupported=0`,
`catalog:valid` e `git diff --check`.

Somente tudo verde:

1. criar um commit técnico normal, sem amend/rebase;
2. push único fast-forward;
3. exigir CI 13/13 e Publish Candidate 11/11 verdes no mesmo SHA;
4. exigir candidato `deployable=true`, seis imagens e três artifacts finais
   canônicos, vinculados ao SHA/run/attempt;
5. não executar workflow de deploy ou rollback.

Até dois ciclos causais adicionais permanecem autorizados dentro desses dois
contratos, sempre com teste, matriz, commit normal, push fast-forward e gates
remotos próprios. Não criar micro-slice.

## 5. Publicação de v0.1.1

Reutilizar exclusivamente a GitHub App publisher, instalação, PEM e
`RELEASE_PUBLISHER_ACTOR_IDS` já aceitos pela S30b. Não criar App, instalação,
chave ou allowlist nova. Se a identidade preservada divergir, parar sem
substituí-la.

Subir localmente somente os serviços efêmeros necessários ao fluxo publisher,
com banco efêmero e secrets gerados para a sessão. Executar migrations e gates
locais. Não reutilizar credencial de produção nem acessar banco comercial.

Na UI publisher:

1. selecionar explicitamente o candidato do commit corretivo, não o mais
   recente por posição;
2. confirmar estimativa `v0.1.1`, previous `v0.1.0` e descrição da seção 2;
3. criar exatamente uma intenção e uma idempotency key;
4. permitir exatamente um dispatch de `publish-release.yml`;
5. não repetir automaticamente diante de resposta perdida;
6. se necessário, usar apenas replay da mesma key/payload pela UI.

Exigir os quatro jobs `trust`, `prepare`, `publish`, `outcome` verdes; tag e
GitHub Release `v0.1.1`; três assets; sidecars; BOM de seis componentes e
digests idênticos ao candidato; operação reconciliada `PUBLISHED`; restart
controlado do publisher sem redispatch; uma tag/release nova e zero alteração
em `v0.1.0`.

Limpar somente serviços, banco, volume, imagens e temporários efêmeros criados
para a publicação. Preservar App/PEM/allowlist.

## 6. Aplicação da configuração Nginx e preparação final

Depois do commit remoto verde, instalar na VPS o blob versionado corrigido,
confrontar SHA-256, executar `nginx -t`, reload e repetir `nginx -t`. Não
reiniciar Nginx e não editar site alheio.

Sem token, exigir:

```text
ERP /api/release-control/v1/capabilities       401
ERP /api/deployment-control/v1/releases        401
ERP /api/deployment-control/v1/current         401
website nos três paths                         404
identity deployer antes do backend              502
```

Depois da publicação e sync, atualizar atomicamente somente os seis immutable
refs do `.env` comercial para o BOM de `v0.1.1`. Preservar PostgreSQL, secrets,
limites, URLs, `kid` e todos os demais valores. Executar Compose config em
quiet mode como `deploy-emporio` e não iniciar/puxar a stack.

Exigir no control plane:

- live/ready 200;
- sync de releases/deployments sem drift;
- `v0.1.1` publicada, com seis componentes e elegível para primeira instalação;
- zero current installation, operação, dispatch de deploy ou rollback;
- `v0.1.0` preservada e não selecionada para produção.

## 7. Proibições

- deploy ou rollback comercial;
- POST ao runtime deployer;
- iniciar, puxar ou migrar a stack comercial;
- modificar/retag/republicar `v0.1.0`;
- bypass manual do publisher ou criação de release por `gh release create`;
- nova App, chave ou instalação publisher/deployer;
- alterar swap, TLS, certificados, secrets comerciais ou control plane sem
  causa direta desta correction;
- update, reboot, firewall, DNS, prune, force, amend ou rebase;
- aceitar S45 ou criar S46.

## 8. Autorização cumulativa

A delegação deve conter literalmente:

```text
Autorizo a correction-01 da S45 a corrigir o scope deployer para deployment:read deployment:execute deployment:rollback, rotear também /api/deployment-control/v1/ exclusivamente pelo domínio ERP ao control plane, publicar pela UI/runtime a release patch v0.1.1 com o candidato corretivo e atualizar somente os seis immutable refs da configuração comercial para esse BOM. Autorizo os commits, pushes, CI/candidato, publicação global, reload Nginx e serviços publisher efêmeros necessários. Não autorizo deploy, rollback, start ou migrations da stack comercial.
```

## 9. Relatório e terminal

Não criar novo relatório. Acrescentar `Retomada correction-01` ao relatório
contínuo S45, mantendo-o não rastreado e fora do stage. Registrar evidência dos
dois defeitos, patch, testes, commits/runs, intenção publisher, release/assets,
sync, Nginx, env por fingerprints e negativos, sem secrets.

O executor não aceita S45 e não cria S46.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — Gate C corrigido e v0.1.1 elegível; aguardando aceite e autorização do primeiro deploy acompanhado
```

Na primeira causa fora da autoridade:

```text
BLOCKED — S45 correction-01 interrompida fail-closed na primeira causa técnica
```

## 10. Critérios de aceite

S45 somente será aceita quando backend, frontend, runtime e Nginx concordarem
nos scopes/namespaces reais, `v0.1.1` estiver publicada e elegível, o `.env`
apontar para seu BOM, o control plane continuar verde e não houver qualquer
deploy, rollback ou recurso comercial iniciado.
