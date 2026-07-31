# Procedimento Operacional Padrao de Deploy

Este documento define o fluxo minimo esperado para qualquer deploy manual ou automatizado do ecossistema Bakery.

Ele complementa os guias especificos:
- [deploy-bakery.md](./deploy-bakery.md)
- [deploy-espresso.md](./deploy-espresso.md)

## Objetivo

Padronizar o que deve acontecer antes, durante e depois de qualquer deploy para reduzir erro operacional, facilitar rollback e melhorar rastreabilidade.

## Checklist Pre-Deploy

- Confirmar qual stack sera alterada: `bakery` ou `espresso`
- Confirmar se ha alteracao de infraestrutura, configuracao ou banco
- Confirmar se existe janela segura para aplicacao da mudanca
- Garantir acesso SSH ao servidor
- Garantir que as imagens ou artefatos corretos foram gerados
- Garantir que o `docker-compose.yml` ou arquivos equivalentes estao atualizados

## Fluxo Padrao

1. Validar localmente a mudanca que sera enviada
2. Gerar os artefatos ou imagens necessarios
3. Transferir os artefatos para o servidor
4. Atualizar a stack com `docker compose up -d --force-recreate`
5. Validar health checks e logs
6. Registrar o resultado do deploy e qualquer desvio observado

## Validacao Pos-Deploy

Minimo esperado:
- `docker compose ps` com servicos saudaveis
- endpoint de health respondendo
- logs sem erro critico repetitivo
- validacao manual da superficie mais sensivel alterada no deploy

## Quando Exigir Cuidado Extra

Aplicar validacao reforcada quando houver:
- migracoes de banco
- alteracao de variaveis de ambiente
- alteracao de contrato entre servicos
- mudancas em auth, pagamentos ou fluxos de pedido

Nesses casos:
- executar backup ou snapshot viavel antes da mudanca
- preferir deploy com acompanhamento manual
- deixar rollback preparado antes de subir a nova versao

## Rollback

Se a validacao pos-deploy falhar:

1. parar a escalada da mudanca
2. identificar se o problema esta em imagem, configuracao ou banco
3. restaurar a versao anterior da stack ou imagem
4. validar health checks novamente
5. registrar causa e acao corretiva

## Responsabilidade Documental

Sempre que o processo real mudar, este SOP e os guias especificos de deploy devem ser atualizados na mesma frente de trabalho.
