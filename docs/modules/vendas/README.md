# Vendas

**Status**: ESTAVEL

## Quem sou

Módulo de Vendas — coração comercial do sistema. Gerencia o ciclo completo de venda presencial e digital: mesa (ocupação, sessão, convidados), pedido (itens, KDS, baixa de estoque), conta (consolidação, rateio, taxa de serviço, couvert) e pagamento (PIX, cartão, dinheiro, voucher com split).

## Para que existo

Viabilizar a operação de vendas do Bakery com fluidez — do momento em que o cliente senta à mesa e faz o pedido via QR code (self-checkout) ou com auxílio do garçom, até o fechamento da conta com pagamento processado, emissão de NFC-e, movimento de caixa registrado e baixa automática de estoque.

## A quem pertenço

Bakery — integra-se diretamente com **Produtos** (itens do cardápio), **Estoque** (baixa automática na aceitação do pedido), **Produção** (consumo de insumos via ficha técnica), **Financeiro** (movimento de caixa, contas a receber), **Fidelização** (acúmulo de pontos) e **Clientes** (identificação do consumidor).

## Domínio imediato

- **mesas/** — Cadastro de mesas (slug, rótulo, referência), sessões de consumo (abertura/fechamento), convidados por sessão, cobranças (couvert artístico), sessão assistida e auto-serviço via QR code
- **pedidos/** — Pedidos por convidado ou mesa, itens com status (QUEUED → ACCEPTED → PREPARING → READY → DELIVERED → CANCELED), integração com KDS via SSE, cancelamento com motivo, baixa de estoque e insumos
- **conta/** — Consolidação da conta por mesa ou por convidado, cálculo de subtotal, taxa de serviço (percentual configurável), couvert artístico, descontos, rateio entre convidados, valores pagos e devidos
- **pagamento/** — Self-checkout (PIX/cartão via Mercado Pago ou PagSeguro) e pagamento assistido pelo staff (split por convidado ou mesa, dinheiro/cartão/PIX/voucher). Webhook de confirmação, alocação por convidado, emissão de NFC-e e comprovante PDF. Resolução de pagamentos self-checkout pelo garçom
- **movimento-caixa/** — Registro de entradas e saídas do caixa (pagamentos de mesa, gorjetas, sangria, reforço, estornos), fechamento de caixa

## Coerente / Desalinhado

- **Coerente**: ciclo completo ponta-a-ponta (mesa → pedido → conta → pagamento → caixa → estoque). Self-checkout via PWA com QR code. Integração em tempo real via SSE para KDS e chamados. Emissão fiscal (NFC-e) e não fiscal (comprovante)
- **Desalinhado**: não há integração com programas de fidelidade no ato do pagamento (pontos por valor). Não há V10 (venda para viagem) como fluxo separado — usa balcão expresso. Não há comanda física com código de barras

## Caminhos de exploração

Leia `vendas.md` para a especificação completa do módulo. Navegue pelos sub-domínios na ordem do fluxo: `mesas/` → `pedidos/` → `conta/` → `pagamento/`. Para o registro contábil das vendas, veja `movimento-caixa/`.
