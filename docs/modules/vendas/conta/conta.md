# Conta — Especificação

## Serviço

### ContaService (366 linhas)

Responsável por consolidar todos os valores de uma sessão e produzir a conta detalhada por mesa ou por convidado.

#### `contaMesa(Long sessaoMesaId)` → `ContaMesaResponse`

Agrega todos os pedidos da sessão e calcula:

```java
subtotalCentavos        = soma de todos os ItemPedido(preco * quantidade) com status != CANCELED
taxaServicoCentavos     = subtotal * percentualTaxaServico / 100  (percentual global configurável)
couvertCentavos         = soma de SessaoCobranca(valor) where status=ATIVA AND !isento
totalMesaCentavos       = subtotal + taxaServico + couvert

// Pagamentos já realizados (por convidado)
pagoCentavos            = soma de Pagamento(valor) where status=PAID
pagoBaseCentavos        = soma de Pagamento(valorBase) where status=PAID
pagoTaxaServicoCentavos = soma de Pagamento(valorTaxaServico) where status=PAID
pagoCouvertCentavos     = soma de Pagamento(valorCouvert) where status=PAID

devidoCentavos          = totalMesaCentavos - pagoCentavos

// Breakdown por pessoa
pessoas[] = para cada SessaoConvidado da sessão:
  subtotal       = soma de itens do convidado
  ajustes        = rateio de itens não alocados + couvert rateado
  pago           = soma de PagamentoAlocacao do convidado
  devido         = subtotal + ajustes - pago
```

#### `contaConvidado(Long sessaoConvidadoId)` → `ContaConvidadoResponse`

Similar ao `contaMesa`, mas filtra apenas:
- Itens do convidado específico (`ItemPedido` onde `Pedido.sessaoConvidado = convidado`)
- Rateio de couvert da mesa dividido por número de convidados
- Pagamentos alocados via `PagamentoAlocacao` para este convidado

## DTOs

### ContaMesaResponse (39 linhas, record)

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `totalMesaCentavos` | `int` | Total bruto da mesa |
| `taxaServicoCentavos` | `int` | Valor da taxa de serviço |
| `descontosCentavos` | `int` | Descontos aplicados |
| `pagoCentavos` | `int` | Total já pago |
| `devidoCentavos` | `int` | Saldo devedor |
| `pessoas` | `List<Pessoa>` | Breakdown por convidado |
| `subtotalCentavos` | `int` | Soma dos itens |
| `pagoBaseCentavos` | `int` | Total base já pago |
| `pagoTaxaServicoCentavos` | `int` | Taxa de serviço já paga |
| `taxaServicoPendenteCentavos` | `int` | Taxa de serviço ainda não paga |
| `couvertCentavos` | `int` | Total de couvert |
| `pagoCouvertCentavos` | `int` | Couvert já pago |
| `devidoCouvertCentavos` | `int` | Couvert ainda devido |
| `devidoTotalCentavos` | `int` | Total devido (com taxa + couvert) |
| `selfCheckoutLiberado` | `boolean` | Se cliente pode pagar sozinho |

Nested `Pessoa` record:
| Campo | Descrição |
|-------|-----------|
| `sessaoConvidadoId` | ID do convidado |
| `nome` | Nome de exibição |
| `subtotalCentavos` | Consumo individual |
| `ajustesCentavos` | Rateios |
| `pagoCentavos` | Já pago |
| `devidoCentavos` | Ainda devido |
| `deveCouvert` | Se deve couvert |
| `host` | Se é anfitrião |

### ContaConvidadoResponse (28 linhas, record)

| Campo | Descrição |
|-------|-----------|
| `sessaoConvidadoId` | ID do convidado |
| `nome` | Nome |
| `grupoClienteId` | Grupo de cliente (para desconto) |
| `grupoClienteDescricao` | Descrição do grupo |
| `subtotalCentavos` | Consumo do convidado |
| `ajustesCentavos` | Rateios/couvert |
| `pagoCentavos` | Já pago |
| `devidoCentavos` | Ainda devido |
| `itens` | `List<Item>` com `pedidoId`, `pedidoStatus`, `produtoNome`, `quantidade`, `precoCentavos`, `observacoes`, `skuDescricao` |

## Controller

### ContaController (39 linhas)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/conta?sessaoMesaId=` | Conta consolidada da mesa |
| `GET` | `/api/conta?sessaoConvidadoId=` | Conta individual do convidado |

Um dos dois parâmetros é obrigatório (mas não ambos).

## Regras de negócio

1. **Taxa de serviço**: percentual único configurado globalmente. Aplicada sobre o subtotal (itens, excluindo couvert). Pode ser incluída ou não pelo cliente no momento do pagamento (`Pagamento.incluiTaxaServico`)
2. **Couvert artístico**: cobrança por convidado com valor fixo. Pode ser isento (menores de idade, eventos). Controlado por `SessaoCobranca` com status `ATIVA`
3. **Alocação de pagamentos**: quando um pagamento é feito e alocado a convidados específicos via `PagamentoAlocacao`, o valor pago de cada convidado é calculado pela soma de suas alocações. Pagamentos não alocados (mesa inteira) rateiam proporcionalmente ao consumo
4. **Self-checkout**: a flag `selfCheckoutLiberado` na sessão controla se o cliente pode ver a conta e iniciar pagamento pelo PWA. É liberada pelo staff ou automaticamente
5. **Items cancelados**: itens com status `CANCELED` não entram no cálculo da conta
