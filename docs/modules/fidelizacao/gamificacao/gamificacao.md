# Gamificação — Especificação do Domínio

## Definição

Sistema de pontos de fidelidade: governa o saldo de pontos do cliente, os movimentos de crédito e débito, o histórico de pontuação, as regras de conversão e expiração, e as métricas de dashboard. A pontuação nasce do consumo (`vendas/`) e é consumida por `resgates/`.

## Escopo

**Inclui:**
- Saldo de pontos por cliente
- Crédito de pontos por consumo (pedido finalizado)
- Débito de pontos por resgate de recompensa
- Ajustes manuais de saldo
- Histórico completo de movimentação (extrato)
- Regras de conversão valor → pontos (taxa e arredondamento configuráveis)
- Expiração de pontos por prazo (configurável)
- Prevenção de pontuação duplicada por referência
- Dashboard administrativo com KPIs e rankings
- Consulta de saldo em lote para integração com `espresso_back`

**Não inclui:**
- Catálogo de recompensas (pertence a `recompensas/`)
- Resgate e débito de pontos (pertence a `resgates/`)
- Cadastro de cliente (pertence a `clientes/`)
- Fechamento de pedido (pertence a `vendas/`)

## Modelo de dados

### MovimentoPontos

Tabela `movimentos_pontos` — registro de auditoria de toda movimentação de pontos.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador do movimento |
| `cliente_id` | BIGINT FK | Cliente dono dos pontos |
| `tipo` | VARCHAR(10) | GANHO, RESGATE ou AJUSTE |
| `origem` | VARCHAR(10) | VENDA, PROMO, MANUAL, SISTEMA ou RECOMPENSA |
| `referencia_tipo` | VARCHAR(30) | Tipo do evento que gerou o movimento (ex: ITEM_PEDIDO_ACEITO) |
| `referencia_id` | BIGINT | ID da entidade que gerou o movimento |
| `pontos` | INTEGER | Quantidade de pontos (positivo para GANHO, negativo para RESGATE) |
| `saldo_apos` | INTEGER | Saldo do cliente após o movimento |
| `data_hora` | TIMESTAMP | Data/hora do movimento |
| `observacao` | VARCHAR(500) | Descrição textual do movimento |

PK: `id`. FK: `cliente_id` → `usuarios(id)`.

### Enums

**TipoMovimentoPontos:**
| Valor | Descrição |
|-------|-----------|
| `GANHO` | Crédito de pontos por consumo, promoção ou ajuste |
| `RESGATE` | Débito de pontos por resgate de recompensa |
| `AJUSTE` | Correção manual de saldo |

**OrigemMovimentoPontos:**
| Valor | Descrição |
|-------|-----------|
| `VENDA` | Pontuação gerada por pedido finalizado |
| `PROMO` | Pontuação promocional |
| `MANUAL` | Ajuste manual pelo admin |
| `SISTEMA` | Ação automática do sistema (ex: expiração) |
| `RECOMPENSA` | Débito por resgate de recompensa |

## Regras de pontuação

### Cálculo de pontos por consumo

O sistema de gamificação no backend do Bakery (porta 8080) é o responsável por processar os eventos de consumo e converter em pontos:

1. Um evento `ITEM_PEDIDO_ACEITO` é disparado quando um item de pedido é aceito
2. O serviço calcula o valor total do item: `precoUnitario × quantidade`
3. Aplica a taxa de conversão: `pontosBrutos = valorItem / valorPara1Ponto`
4. Aplica o arredondamento configurado: `pontos = Ajustar(pontosBrutos, 0, arredondamento)`
5. Se `pontos <= 0`, o movimento é ignorado
6. Cria um registro `MovimentoPontos` com `tipo = GANHO`, `origem = VENDA`
7. Atualiza o saldo: `saldoApos = saldoAnterior + pontos`

### Prevenção de duplicidade

O sistema verifica se já existe um movimento com a mesma `referenciaTipo` + `referenciaId` antes de processar. Se existir, o evento é ignorado — garantindo idempotência na pontuação.

### Cálculo do saldo

O saldo de um cliente é o `saldoApos` do movimento mais recente (`findFirstByClienteOrderByDataHoraDesc`). Clientes sem movimentação têm saldo 0.

### Expiração de pontos

Parâmetro `gamificacao_expiracao_pontos_em_dias` (0 = nunca expira). Quando configurado com valor > 0, os pontos expiram após N dias da data do movimento. O mecanismo de expiração em si ainda não está implementado — o campo existe para configuração futura.

## Configurações

Gerenciadas na tabela `configuracoes` com prefixo `gamificacao_` e interface em `GamificacaoConfig.vue`:

| Chave | Default | Descrição |
|-------|---------|-----------|
| `gamificacao_ativo` | `true` | Habilita/desabilita o cálculo de pontos |
| `gamificacao_valor_para_1_ponto` | `5.00` | Valor em reais para gerar 1 ponto |
| `gamificacao_arredondamento` | `FLOOR` | Modo de arredondamento: FLOOR, CEIL ou ROUND |
| `gamificacao_expiracao_pontos_em_dias` | `0` | Dias para expirar pontos (0 = nunca) |

## Endpoints

### Cliente

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `GET` | `/api/clientes/me/gamificacao` | Cliente autenticado | Saldo + últimas 50 movimentações |

Resposta:
```json
{
  "saldo": 150,
  "extrato": [
    {
      "dataHora": "2025-12-01T14:30:00",
      "tipo": "GANHO",
      "origem": "VENDA",
      "pontos": 10,
      "saldoApos": 150,
      "referenciaTipo": "ITEM_PEDIDO_ACEITO",
      "referenciaId": 1234,
      "observacao": "Evento ITEM_PEDIDO_ACEITO (pedido #567)"
    }
  ]
}
```

### Admin

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `GET` | `/api/admin/gamificacao/dashboard` | ADMIN/SYSTEM | KPIs + rankings completos |
| `GET` | `/api/admin/gamificacao/saldos?ids=` | X-ERP-KEY ou ADMIN/SYSTEM | Saldos em lote por lista de IDs |
| `GET` | `/api/admin/gamificacao/clientes/com-pontos` | ADMIN/SYSTEM | Clientes com movimentação de pontos |

## Dashboard

### KPIs

Retornados por `GET /api/admin/gamificacao/dashboard`:

| KPI | Descrição |
|-----|-----------|
| `recompensasAtivas` | Total de recompensas ativas no catálogo |
| `participantesComPontos` | Clientes com pelo menos 1 movimento GANHO |
| `adesoesUltimos30Dias` | Clientes que pontuaram pela primeira vez nos últimos 30 dias |
| `pontosEmitidosUltimos30Dias` | Total de pontos emitidos (origem VENDA) nos últimos 30 dias |
| `saldoTotalAtivo` | Soma dos saldos atuais de todos os clientes |
| `pontosResgatadosUltimos30Dias` | Total de pontos resgatados nos últimos 30 dias |
| `taxaResgate` | `pontosResgatados / pontosEmitidos` nos últimos 30 dias |

### Rankings

| Ranking | Descrição |
|---------|-----------|
| `topPontuadoresUltimos30Dias` | Top 10 clientes que mais pontuaram (GANHO via VENDA) nos últimos 30 dias |
| `topSaldosAtuais` | Top 10 clientes com maior saldo atual |
| `ultimosResgates` | Últimos 10 resgates com cliente, recompensa, pontos e data |
| `topRecompensasResgatadasUltimos30Dias` | Top 5 recompensas mais resgatadas nos últimos 30 dias |

## Integração com espresso_back

O `espresso_back` consulta saldos em lote via `POST /api/admin/gamificacao/saldos` (autenticado por `X-ERP-KEY`). Este endpoint é público por design — o SecurityConfig libera `/api/admin/gamificacao/saldos` sem autenticação, validando a chave internamente.

O `ClientesDashboardService` (espresso) usa este endpoint para exibir pontos de fidelidade no dashboard do cliente.

## Serviços

### GamificacaoService

| Método | Descrição |
|--------|-----------|
| `registrarConsumoItemAceito(ItemPedido, Usuario)` | Disparado quando um item de pedido é aceito; extrai valor e chama `processarEvento` |
| `processarEvento(GamificacaoEvento)` | Núcleo da pontuação: valida configuração, calcula pontos, verifica duplicidade, persiste movimento |
| `getSaldoCliente(Long clienteId)` | Retorna saldo atual de um cliente |
| `getSaldosClientes(List<Long> ids)` | Retorna saldos em lote (DISTINCT ON por cliente) |

### GamificacaoConsultaService

| Método | Descrição |
|--------|-----------|
| `buscarMinhaGamificacao(Usuario)` | Saldo atual + últimas 50 movimentações para o cliente logado |

### DashboardGamificacaoService

| Método | Descrição |
|--------|-----------|
| `getDashboardAdmin()` | KPIs agregados + 4 rankings |
| `getRecompensasDisponiveisParaCliente(Long)` | Recompensas elegíveis para um cliente específico |
| `getClientesComPontos()` | Lista de clientes com movimentação de pontos |

## Frontend

### Cliente (espresso_front - React/TypeScript)

| Componente | Rota | Descrição |
|-----------|------|-----------|
| `GamificacaoExtrato.tsx` | `/areacliente/gamificacao` | Extrato completo: saldo + abas de ganhos e resgates + catálogo de recompensas disponíveis |
| `GamificacaoBanner.tsx` | Componente na `AreaCliente.tsx` | Banner com saldo e próxima recompensa atingível |
| `ClienteKPIs.tsx` | Componente na `AreaCliente.tsx` | Cards com saldo e contador de recompensas |

### Admin (espresso_front - React/TypeScript)

| Componente | Rota | Descrição |
|-----------|------|-----------|
| `GamificacaoAdmin.tsx` | `/admin/gamificacao` | Dashboard com KPIs, rankings, seletor de cliente com recompensas elegíveis e interface de resgate |

### Configuração (frontend - Vue/Quasar)

| Componente | Descrição |
|-----------|-----------|
| `GamificacaoConfig.vue` | Formulário de configuração: ativo, valor para 1 ponto, arredondamento, expiração |

## Distribuição entre backends

O módulo de gamificação (pontos, movimentos) vive exclusivamente no **backend bakery** (porta 8080). O `espresso_back` (porta 8085) possui o subsistema `Reward` para brindes e sorteios — semanticamente distinto do sistema de pontos de fidelidade — e consulta o bakery apenas para leitura de saldos em lote.

## Decisões de domínio

- **Saldo derivado do último movimento** — não há campo `saldo` na tabela `usuarios`; o saldo é sempre o `saldoApos` do movimento mais recente. Isso garante rastreabilidade total.
- **Idempotência por referência** — o sistema impede pontuação duplicada verificando `referenciaTipo + referenciaId`. Um mesmo item de pedido não pode gerar dois ganhos.
- **Arredondamento configurável** — FLOOR (default) é conservador (nunca arredonda para cima); CEIL e ROUND são opções para estabelecimentos que preferem generosidade.
- **Expiração configurada mas não implementada** — o campo existe na config seeder mas o job de expiração ainda não foi construído.
- **Pontos negativos para RESGATE** — movimentos de resgate armazenam valor negativo em `pontos`, permitindo que a soma direta da coluna indique saldo sem lógica adicional.
- **isGamificacaoAtiva como gate** — se a feature estiver desligada, eventos de consumo são ignorados silenciosamente.

## Status de implementação

**EM_DESENVOLVIMENTO**. O núcleo da pontuação (crédito via consumo, saldo, extrato, dashboard, configuração) está implementado no backend bakery. O mecanismo de expiração de pontos e a interface de ajuste manual de saldo ainda não foram implementados. O subsistema `Reward` do espresso_back (sorteios e brindes) opera separadamente.
