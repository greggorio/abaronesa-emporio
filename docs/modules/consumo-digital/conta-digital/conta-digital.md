# Conta Digital — Especificação

## Definição

Superfície de acompanhamento em tempo real do consumo do cliente durante a sessão de mesa. Exibe o que foi pedido, os valores discriminados por componente, o que já foi pago e o saldo a pagar — por convidado e consolidado para a mesa.

## Estrutura da conta (ContaMesaResponse)

| Campo | Descrição |
|-------|-----------|
| `subtotalCentavos` | Consumo puro: somatório de (preço unitário × quantidade) de todos os itens não-cancelados |
| `taxaServicoCentavos` | Valor da taxa de serviço calculada sobre o subtotal |
| `couvertCentavos` | Couvert artístico: valor por pessoa × número de convidados |
| `descontosCentavos` | Descontos aplicados (ex: desconto de grupo) |
| `totalMesaCentavos` | subtotal + taxa + couvert − descontos |
| `pagoCentavos` | Soma dos pagamentos com status PAID |
| `pagoBaseCentavos` | Componente pago referente ao consumo |
| `pagoTaxaServicoCentavos` | Componente pago referente à taxa de serviço |
| `devidoCentavos` | totalMesa − pago |
| `pessoas` | Array com breakdown individual por convidado |
| `selfCheckoutLiberado` | Se convidados podem pagar sem staff |

## Como é calculado

`ContaService.contaMesa()`:
1. Busca todos os `ItemPedido` da sessão
2. Filtra apenas itens com status diferente de CANCELED
3. Soma subtotal (preço unitário × quantidade por item)
4. Se `taxa_servico_ativo = true`: aplica o percentual configurado em `ConfigManager`
5. Agrupa itens por convidado para o breakdown individual
6. Se `couvert_artistico_ativo = true`: calcula valor total (valor por pessoa × convidados)
7. Soma pagamentos com status PAID
8. Retorna o response completo

## Sincronização em tempo real

A conta é consultada via `GET /api/mesas/{sessaoMesaId}/conta`. Para manter todos os dispositivos atualizados sem polling, o sistema usa SSE:

| Evento | Efeito na conta |
|--------|-----------------|
| `item.added` | Subtotal aumenta |
| `item.canceled` | Subtotal diminui |
| `payment.made` | `pagoCentavos` aumenta; `devidoCentavos` diminui |
| `account.updated` | Recalculação geral (ex: mudança de configuração) |

## Configurações que afetam a conta

Todas as configurações abaixo são lidas via `ConfigManager` em tempo de execução:

| Configuração | Padrão | Efeito |
|--------------|--------|--------|
| `taxa_servico_ativo` | false | Ativa/desativa componente de taxa |
| `taxa_servico_percentual` | 10% | Percentual aplicado sobre o subtotal |
| `couvert_artistico_ativo` | false | Ativa/desativa couvert por pessoa |
| `couvert_artistico_valor` | — | Valor do couvert por convidado |
| `mesa_self_checkout_mesa_enabled` | false | Permite pagar a mesa inteira sem dividir |

## Escopo

**Inclui:**
- Consulta em tempo real da conta da sessão
- Breakdown por convidado
- Taxa de serviço discriminada
- Couvert artístico discriminado
- Saldo pago vs. devido
- Sincronização via SSE

**Não inclui:**
- Ajustes retroativos pós-fechamento da mesa
- Taxas escalonadas ou cálculos avançados de divisão
