# Implementacao de Docker, CI/CD e Controle de Releases

> **Estado geral:** S01 a S29 e S31 a S33 aceitas; S30 dividida; S30a pronta para fechamento remoto

Esta pasta registra os contratos e as evidencias das slices usadas para implementar a arquitetura definida em [proposta-docker-ci-cd-producao-emporio.md](../proposta-docker-ci-cd-producao-emporio.md).

Para transferir a condução a outro CLI sem perder autoridade, estado ou
critérios de encerramento, consulte o
[handoff do orquestrador](./HANDOFF_ORQUESTRADOR.md).

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
| S30a | [Paridade local e fechamento da CI e do candidato](./slices/S30a-paridade-local-fechamento-ci-candidato.task.md) ([authorization-01](./slices/S30a-paridade-local-fechamento-ci-candidato.authorization-01.md), [authorization-02](./slices/S30a-paridade-local-fechamento-ci-candidato.authorization-02.md)) | [Relatório](./slices/S30a-paridade-local-fechamento-ci-candidato.report.md) | `IN_PROGRESS` — inventário A/B/C zerado; push único e observação de CI/candidato autorizados pela authorization-02 |
| S31 | [Remoção dos gerenciadores de pacotes do runtime do whatsapp_service](./slices/S31-remocao-npm-runtime-whatsapp.task.md) ([correction-01](./slices/S31-remocao-npm-runtime-whatsapp.correction-01.md), [correction-02](./slices/S31-remocao-npm-runtime-whatsapp.correction-02.md)) | [Relatório](./slices/S31-remocao-npm-runtime-whatsapp.report.md) | `ACCEPTED` — 02/08/2026; grupo B fechado, Trivy HIGH/CRITICAL 1 → 0, sem push |
| S32 | [Migração da linha Spring para Boot 3.5](./slices/S32-migracao-spring-boot-3-5.task.md) ([correction-01](./slices/S32-migracao-spring-boot-3-5.correction-01.md)) | [Relatório](./slices/S32-migracao-spring-boot-3-5.report.md) | `ACCEPTED` — 02/08/2026; grupo A fechado, OkHttp preservado em 4.12.0, sem push |
| S33 | [Remoção da cadeia JasperReports não utilizada](./slices/S33-remocao-java-danfe-jasperreports.task.md) | [Relatório](./slices/S33-remocao-java-danfe-jasperreports.report.md) | `ACCEPTED` — 02/08/2026; grupo C fechado, inventário Trivy HIGH/CRITICAL em zero, sem push |
