# Workflows GitHub Actions

Existem exatamente cinco workflows ativos: `ci.yml`, `publish-candidate.yml`,
`publish-release.yml`, `deploy-production.yml` e `rollback-production.yml`. A CI executa em pull requests e pushes para `main`;
somente o push em `main` produz o artifact machine-readable `candidate-plan`.

A CI possui gates separados para contratos/infraestrutura, backend ERP,
website backend, frontend ERP, website frontend e WhatsApp. Somente depois de
todos esses gates ela constroi as seis imagens comerciais para `linux/amd64`,
sem push, e executa scan fail-closed de vulnerabilidades. O checkout completo
tambem alimenta o scan local de segredos e a resolucao conservadora de paths.

`publish-candidate.yml` aceita exclusivamente a conclusão verde da CI causada
por push confiável em `main`. Antes de checkout ou permissão de escrita ele
reconfere execução, repositório, SHA, branch e artifact pela API. Constrói
somente os componentes afetados, faz scan antes do login, publica a mesma
imagem por digest, gera e verifica provenance, herda o restante do candidato
anterior validado e testa o BOM completo por digest numa stack efêmera.

O job integrado conserva somente `packages: read`, autentica no GHCR para
pull das imagens privadas e encerra a sessão ao final. O modelo Compose é
validado antes do `up`: os dois arquivos e o mesmo projeto são usados também
por `ps` e probes, existe exatamente um bind do gateway na porta efêmera em
`127.0.0.1`, e nenhuma outra porta é publicada. A limpeza remove o projeto
exato e somente as seis referências imutáveis do manifesto.

O artifact candidato continua `deployable: false`. `publish-release.yml` e
somente manual, valida a historia e publica draft/assets/tag de forma
compensavel; ele nao e deploy e nao acessa VPS. Os workflows de CI e
publicação foram validados localmente, mas ainda não foram executados no GitHub. No
primeiro push, a ausência de candidato anterior exige build dos seis
componentes.

O contrato definitivo usa o grafo `trust -> predecessor -> build -> assemble
-> integrated -> publish`. A CI entrega plano v2; `predecessor` resolve a
lineage cumulativa e produz o plano efetivo. Todo término verde publica
`candidate-outcome`; `already_published`, `no_changes` e `superseded` não
constroem nem publicam novo candidato. O bundle pending e o recibo de
integração são artifacts internos de um dia, enquanto candidato e outcome
possuem retenção de 30 dias.

O contrato de release usa `trust -> prepare -> publish -> outcome`, allowlist
de IDs GitHub, concorrencia global e escrita somente no job mutavel. A
repository variable `RELEASE_PUBLISHER_ACTOR_IDS` nao tem default e devera ser
configurada antes da ativacao.

`deploy-production.yml` é exclusivamente manual e aceita somente operation ID
e release SemVer. Seu grafo `trust -> prepare -> deploy -> outcome` valida a
release global publicada, obtém um snapshot autenticado, gera o bundle S18 no
runner e chama remotamente apenas o CLI S20. Somente `deploy` usa o environment
`production`; a concorrência `emporio-production` não cancela operação em
andamento. Resultado remoto indeterminado nunca é promovido a sucesso. O
workflow está configurado e validado localmente, mas ainda não foi executado.

`rollback-production.yml` é exclusivamente manual (`workflow_dispatch`), recebe
somente `operation_id` e `release`, mantém `contents: read` e apenas valida o
envelope versionado do rollback. Não publica, não usa SSH, Docker ou segredo e
não foi executado remotamente.
