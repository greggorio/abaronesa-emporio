# Implementacao de Docker, CI/CD e Controle de Releases

> **Estado geral:** S01–S29, S30a–S30b, S31–S34 e S36–S43 aceitas; S35 ultrapassada; S44 aberta na implantação isolada do control plane deployer

Esta pasta registra os contratos e as evidencias das slices usadas para implementar a arquitetura definida em [proposta-docker-ci-cd-producao-emporio.md](../proposta-docker-ci-cd-producao-emporio.md).

Para transferir a condução a outro CLI a partir do estado remoto atual,
consulte o [handoff de fechamento](./HANDOFF_ORQUESTRADOR_FECHAMENTO.md). O
[handoff histórico](./HANDOFF_ORQUESTRADOR.md) permanece como contexto das
decisões anteriores ao primeiro commit.

## Regras

- O orquestrador escreve e aprova o contrato antes da delegacao.
- Antes da delegacao, o orquestrador fecha as escolhas de arquitetura,
  formatos, politicas e criterios de aceite que afetem a execucao.
- O executor CLI nao altera o contrato recebido.
- O executor executa decisoes expressas; nao e responsavel por escolher entre
  alternativas arquiteturais omitidas no contrato.
- Criterio novo descoberto depois da delegacao e emenda do orquestrador ou
  escopo futuro, nao erro retroativo do executor.
- Reincidencia de correcoes por expectativas nao escritas exige suspender a
  delegacao e concluir uma auditoria integral do contrato.
- Cada execucao produz um relatorio Markdown separado.
- O relatorio inclui comandos, codigos de saida, resultados, interpretacao e arquivos alterados.
- Codigo e documentacao pertencem ao mesmo criterio de aceite.
- Somente o orquestrador altera uma slice para `ACCEPTED` ou `REJECTED`.
- Uma slice dependente nao inicia enquanto sua pre-condicao permanecer pendente ou divergente.
- Sempre que uma slice for aceita e ainda existir escopo pendente, o orquestrador cria a proxima slice no mesmo ciclo e entrega o respectivo prompt de delegacao.

## Estados

```text
PLANNED
IN_PROGRESS
BLOCKED
ACCEPTED
REJECTED
```

## Convencao de arquivos

```text
slices/SNN-<nome>.task.md
slices/SNN-<nome>.report.md
```

O arquivo `.task.md` e o contrato imutavel da delegacao. O arquivo `.report.md` e criado pelo executor e revisado pelo orquestrador.

## Slices

| Slice | Contrato | Relatorio | Estado |
|---|---|---|---|
| S01 | [Inventario e contratos reais](./slices/S01-inventario-e-contratos-reais.task.md) | [Relatório](./slices/S01-inventario-e-contratos-reais.report.md) | `ACCEPTED` — 28/07/2026 |
| S02 | [Saneamento pre-Git e protecao do primeiro commit](./slices/S02-saneamento-pre-git-e-protecao-primeiro-commit.task.md) | [Relatório](./slices/S02-saneamento-pre-git-e-protecao-primeiro-commit.report.md) | `ACCEPTED` — 28/07/2026 |
| S03 | [Fundacao Git local e auditoria do primeiro indice](./slices/S03-fundacao-git-local-e-auditoria-primeiro-indice.task.md) | [Relatório](./slices/S03-fundacao-git-local-e-auditoria-primeiro-indice.report.md) | `ACCEPTED` — 28/07/2026 |
| S04 | [Saneamento dos workflows legados e primeiro push seguro](./slices/S04-saneamento-workflows-legados-e-primeiro-push-seguro.task.md) | [Relatório](./slices/S04-saneamento-workflows-legados-e-primeiro-push-seguro.report.md) | `ACCEPTED` — 28/07/2026 |
| S05 | [Contrato canonico de componentes e fechamento de dependencias](./slices/S05-contrato-componentes-e-fechamento-dependencias.task.md) | [Relatório](./slices/S05-contrato-componentes-e-fechamento-dependencias.report.md) | `ACCEPTED` — 28/07/2026 |
| S06 | [Contratos de API, estados e seguranca do release control](./slices/S06-contratos-api-estados-seguranca-release-control.task.md) | [Relatório](./slices/S06-contratos-api-estados-seguranca-release-control.report.md) | `ACCEPTED` — 28/07/2026 |
| S07 | [Saneamento do bootstrap sensivel antes dos Dockerfiles](./slices/S07-saneamento-bootstrap-sensivel-pre-docker.task.md) | [Relatório](./slices/S07-saneamento-bootstrap-sensivel-pre-docker.report.md) | `ACCEPTED` — 28/07/2026 |
| S08 | [Dockerfiles Java endurecidos e health do website backend](./slices/S08-dockerfiles-java-hardening-health.task.md) | [Relatório](./slices/S08-dockerfiles-java-hardening-health.report.md) | `ACCEPTED` — 28/07/2026 |
| S09 | [Imagens Node, frontends e contratos de runtime](./slices/S09-imagens-node-frontends-contratos-runtime.task.md) | [Relatório](./slices/S09-imagens-node-frontends-contratos-runtime.report.md) | `ACCEPTED` — 28/07/2026 |
| S10 | [Gateway canônico, Compose de produção e persistências](./slices/S10-gateway-compose-producao-persistencias.task.md) | [Relatório](./slices/S10-gateway-compose-producao-persistencias.report.md) | `ACCEPTED` — 29/07/2026 |
| S11 | [CI canônico e contrato de manifesto candidato](./slices/S11-ci-canonico-e-contrato-manifesto-candidato.task.md) | [Relatório](./slices/S11-ci-canonico-e-contrato-manifesto-candidato.report.md) | `ACCEPTED` — 29/07/2026 |
| S12 | [Workflow de publicação do candidato e proveniência](./slices/S12-publicacao-candidato-e-proveniencia.task.md) | [Relatório](./slices/S12-publicacao-candidato-e-proveniencia.report.md) | `ACCEPTED` — 29/07/2026 |
| S13 | [Contrato canônico da release global e resolução semântica offline](./slices/S13-contrato-release-global-semver-offline.task.md) | [Relatório](./slices/S13-contrato-release-global-semver-offline.report.md) | `ACCEPTED` — 29/07/2026 |
| S14 | [Workflow transacional de publicação da release global](./slices/S14-workflow-publicacao-release-global.task.md) | [Relatório](./slices/S14-workflow-publicacao-release-global.report.md) | `ACCEPTED` — 29/07/2026 |
| S15 | [Runtime publisher, persistência e reconciliação GitHub](./slices/S15-runtime-publisher-persistencia-reconciliacao-github.task.md) | [Relatório](./slices/S15-runtime-publisher-persistencia-reconciliacao-github.report.md) | `ACCEPTED` — 29/07/2026 |
| S16 | [Ponte de identidade RS256/JWKS e perfil local seguro do publisher](./slices/S16-ponte-identidade-rs256-jwks-perfil-local-publisher.task.md) | [Relatório](./slices/S16-ponte-identidade-rs256-jwks-perfil-local-publisher.report.md) | `ACCEPTED` — 29/07/2026 |
| S17 | [UI de desenvolvimento para publicação de releases globais](./slices/S17-ui-desenvolvimento-publicacao-releases-globais.task.md) | [Relatório](./slices/S17-ui-desenvolvimento-publicacao-releases-globais.report.md) | `ACCEPTED` — 29/07/2026 |
| S18 | [Plano determinístico offline de implantação](./slices/S18-plano-deterministico-offline-implantacao.task.md) | [Relatório](./slices/S18-plano-deterministico-offline-implantacao.report.md) | `ACCEPTED` — 29/07/2026 |
| S19 | [Núcleo transacional e journal de implantação](./slices/S19-nucleo-transacional-journal-implantacao.task.md) | [Relatório](./slices/S19-nucleo-transacional-journal-implantacao.report.md) | `ACCEPTED` — 31/07/2026 |
| S20 | [Adapters operacionais, migrations e CLI de implantação](./slices/S20-adapters-operacionais-migrations-cli-implantacao.task.md) | [Relatório](./slices/S20-adapters-operacionais-migrations-cli-implantacao.report.md) | `ACCEPTED` — 31/07/2026 |
| S21 | [Workflow e transporte autenticado de implantação em produção](./slices/S21-workflow-transporte-autenticado-implantacao-producao.task.md) | [Relatório](./slices/S21-workflow-transporte-autenticado-implantacao-producao.report.md) | `ACCEPTED` — 31/07/2026 |
| S22 | [Runtime deployer, persistência e reconciliação GitHub](./slices/S22-runtime-deployer-persistencia-reconciliacao-github.task.md) | [Relatório](./slices/S22-runtime-deployer-persistencia-reconciliacao-github.report.md) | `ACCEPTED` — 31/07/2026 |
| S23 | [Ponte de identidade RS256/JWKS do deployer](./slices/S23-ponte-identidade-deployer-rs256-jwks.task.md) | [Relatório](./slices/S23-ponte-identidade-deployer-rs256-jwks.report.md) | `ACCEPTED` — 31/07/2026 |
| S24 | [UI de produção para atualização forward](./slices/S24-ui-producao-atualizacao-forward.task.md) | [Relatório](./slices/S24-ui-producao-atualizacao-forward.report.md) | `ACCEPTED` — 31/07/2026 |
| S25 | [Contrato seguro de rollback comercial](./slices/S25-contrato-seguro-rollback-comercial.task.md) | [Relatório](./slices/S25-contrato-seguro-rollback-comercial.report.md) | `ACCEPTED` — 31/07/2026 |
| S26 | [Executor, workflow e runtime de rollback comercial](./slices/S26-executor-workflow-runtime-rollback.task.md) | [Relatório](./slices/S26-executor-workflow-runtime-rollback.report.md) | `ACCEPTED` — 31/07/2026 |
| S27 | [UI de rollback e recuperação](./slices/S27-ui-rollback-recuperacao.task.md) | [Relatório](./slices/S27-ui-rollback-recuperacao.report.md) | `ACCEPTED` — 31/07/2026 |
| S28 | [Empacotamento operacional isolado do release control](./slices/S28-empacotamento-operacional-release-control.task.md) | [Relatório](./slices/S28-empacotamento-operacional-release-control.report.md) | `ACCEPTED` — 31/07/2026 |
| S29 | [Gate do primeiro commit e ativação remota](./slices/S29-gate-primeiro-commit-ativacao-remota.task.md) | [Relatório](./slices/S29-gate-primeiro-commit-ativacao-remota.report.md) | `ACCEPTED` — 31/07/2026 |
| S30 | [Ensaio remoto de candidato, publisher e release](./slices/S30-ensaio-remoto-candidato-publisher-release.task.md) | [Relatório](./slices/S30-ensaio-remoto-candidato-publisher-release.report.md) | `SPLIT` — 01/08/2026; contrato-pai histórico, dividido em S30a e S30b |
| S30a | [Paridade local e fechamento da CI e do candidato](./slices/S30a-paridade-local-fechamento-ci-candidato.task.md) ([authorization-01](./slices/S30a-paridade-local-fechamento-ci-candidato.authorization-01.md), [authorization-02](./slices/S30a-paridade-local-fechamento-ci-candidato.authorization-02.md), [authorization-03](./slices/S30a-paridade-local-fechamento-ci-candidato.authorization-03.md)) | [Relatório](./slices/S30a-paridade-local-fechamento-ci-candidato.report.md) ([authorization-02](./slices/S30a-paridade-local-fechamento-ci-candidato.authorization-02.report.md), [authorization-03](./slices/S30a-paridade-local-fechamento-ci-candidato.authorization-03.report.md)) | `ACCEPTED` — 02/08/2026; fechamento terminal comprovado pela S36 no SHA `50f423a` |
| S30b | [Publicação da primeira release global pelo publisher](./slices/S30b-preflight-publicacao-release-global-publisher.task.md) ([authorization-01](./slices/S30b-preflight-publicacao-release-global-publisher.authorization-01.md), [authorization-02](./slices/S30b-preflight-publicacao-release-global-publisher.authorization-02.md), [authorization-03](./slices/S30b-preflight-publicacao-release-global-publisher.authorization-03.md)) | [Relatório contínuo](./slices/S30b-preflight-publicacao-release-global-publisher.report.md) | `ACCEPTED` — 03/08/2026; `v0.1.0`, run `30804834574`, release `364130074`, BOM de seis componentes; operação local histórica `SUPERSEDED` |
| S31 | [Remoção dos gerenciadores de pacotes do runtime do whatsapp_service](./slices/S31-remocao-npm-runtime-whatsapp.task.md) ([correction-01](./slices/S31-remocao-npm-runtime-whatsapp.correction-01.md), [correction-02](./slices/S31-remocao-npm-runtime-whatsapp.correction-02.md)) | [Relatório](./slices/S31-remocao-npm-runtime-whatsapp.report.md) | `ACCEPTED` — 02/08/2026; grupo B fechado, Trivy HIGH/CRITICAL 1 → 0, sem push |
| S32 | [Migração da linha Spring para Boot 3.5](./slices/S32-migracao-spring-boot-3-5.task.md) ([correction-01](./slices/S32-migracao-spring-boot-3-5.correction-01.md)) | [Relatório](./slices/S32-migracao-spring-boot-3-5.report.md) | `ACCEPTED` — 02/08/2026; grupo A fechado, OkHttp preservado em 4.12.0, sem push |
| S33 | [Remoção da cadeia JasperReports não utilizada](./slices/S33-remocao-java-danfe-jasperreports.task.md) | [Relatório](./slices/S33-remocao-java-danfe-jasperreports.report.md) | `ACCEPTED` — 02/08/2026; grupo C fechado, inventário Trivy HIGH/CRITICAL em zero, sem push |
| S34 | [Remoção da atestação nativa indisponível](./slices/S34-remocao-atestacao-nativa-candidato.task.md) ([correction-01](./slices/S34-remocao-atestacao-nativa-candidato.correction-01.md)) | [Relatório](./slices/S34-remocao-atestacao-nativa-candidato.report.md) | `ACCEPTED` — 02/08/2026; contrato nativo de atestação removido, integridade por digest preservada, sem push |
| S35 | [Migrações prévias da stack candidata](./slices/S35-migracoes-previas-stack-candidata.task.md) | — | `SUPERSEDED` — contrato local ultrapassado pela correção exploratória remota; não foi executado como task |
| S36 | [Fechamento terminal da S30a](./slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.task.md) ([authorization-01](./slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.authorization-01.md)) | [Relatório](./slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.report.md) | `ACCEPTED` — 02/08/2026; CI `30757174785`, candidato `30757430990`, sete máscaras e três artifacts válidos |
| S37 | [Inventário read-only da VPS e plano de preparação de produção](./slices/S37-inventario-read-only-vps-plano-preparacao-producao.task.md) | [Relatório](./slices/S37-inventario-read-only-vps-plano-preparacao-producao.report.md) | `ACCEPTED` — 03/08/2026; host multi-inquilino inventariado sem mutação, Empório greenfield e plano Gate A–E fechado |
| S38 | [Fechamento do Gate A e invariantes do deployer](./slices/S38-fechamento-gate-a-invariantes-deployer.task.md) | [Relatório](./slices/S38-fechamento-gate-a-invariantes-deployer.report.md) | `ACCEPTED` — 03/08/2026; 331 testes verdes, journal JSONB reparado, CI `30812658858` e candidato `30813218997` verdes |
| S39 | [Workflow independente da imagem do release control](./slices/S39-workflow-imagem-release-control.task.md) ([correction-01](./slices/S39-workflow-imagem-release-control.correction-01.md)) | [Relatório](./slices/S39-workflow-imagem-release-control.report.md) | `ACCEPTED` — 03/08/2026; Alpine em zero HIGH/CRITICAL, CI `30838546384`, candidato `30839217752`, workflow ativo e nunca executado |
| S40 | [Publicação inaugural da imagem do release control](./slices/S40-publicacao-inaugural-imagem-release-control.task.md) | [Relatório](./slices/S40-publicacao-inaugural-imagem-release-control.report.md) | `ACCEPTED` — 03/08/2026; run `30855327740`, quatro jobs verdes e imagem privada por digest `sha256:64b6f2be…22380` |
| S41 | [Bootstrap do transporte de produção](./slices/S41-bootstrap-transporte-producao-identidade-ssh.task.md) | [Relatório](./slices/S41-bootstrap-transporte-producao-identidade-ssh.report.md) | `ACCEPTED` — 03/08/2026; usuário dedicado, árvore `0700`, SSH estrito, Docker sem sudo e environment `production` configurado |
| S42 | [Pacote instalável do control root](./slices/S42-pacote-instalavel-control-root-dependencias-binding.task.md) | [Relatório](./slices/S42-pacote-instalavel-control-root-dependencias-binding.report.md) | `ACCEPTED` — 04/08/2026; pacote determinístico `9b5f8362…d8c0`, vendor Draft 2020-12, commit `9731954`, CI `30902014368` e candidato `30902729166` verdes |
| S43 | [Instalação e validação do control root na VPS](./slices/S43-instalacao-validacao-control-root-vps.task.md) | [Relatório](./slices/S43-instalacao-validacao-control-root-vps.report.md) | `ACCEPTED` — 04/08/2026; pacote `9b5f8362…d8c0` instalado uma vez, 237 entradas íntegras, vendor Draft 2020-12 e capabilities no SHA `9731954` verdes |
| S44 | [Implantação do control plane deployer na VPS](./slices/S44-implantacao-control-plane-deployer-vps.task.md) ([correction-01](./slices/S44-implantacao-control-plane-deployer-vps.correction-01.md), [correction-02](./slices/S44-implantacao-control-plane-deployer-vps.correction-02.md)) | [Relatório contínuo](./slices/S44-implantacao-control-plane-deployer-vps.report.md) | `ACCEPTED` — 04/08/2026; App exclusiva, imagem `sha256:d0d0cb16…e7040`, control plane healthy em `127.0.0.1:8180`, sync/restart verdes e zero operação/deploy/rollback |
| S45 | [Prontidão de produção e fechamento do Gate C](./slices/S45-prontidao-producao-gate-c.task.md) ([correction-01](./slices/S45-prontidao-producao-gate-c.correction-01.md)) | [Relatório contínuo](./slices/S45-prontidao-producao-gate-c.report.md) | `ACCEPTED` — 05/08/2026; capacidade/TLS/backup, scopes/namespaces, `v0.1.1` publicada e elegível, control plane verde e deploy/rollback em zero |
| S46 | [Primeiro deploy acompanhado de v0.1.1](./slices/S46-primeiro-deploy-acompanhado-v0.1.1.task.md) ([correction-01](./slices/S46-primeiro-deploy-acompanhado-v0.1.1.correction-01.md), [correction-02](./slices/S46-primeiro-deploy-acompanhado-v0.1.1.correction-02.md), [correction-03](./slices/S46-primeiro-deploy-acompanhado-v0.1.1.correction-03.md), [correction-04](./slices/S46-primeiro-deploy-acompanhado-v0.1.1.correction-04.md)) | [Relatório contínuo](./slices/S46-primeiro-deploy-acompanhado-v0.1.1.report.md) | `IN_PROGRESS` — três runs falhos confirmados antes de efeito comercial; correction-04 reconcilia a identidade SSH por probe do runner, recupera automaticamente a readiness e autoriza a conclusão comercial |
