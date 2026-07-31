# Relatórios — Especificação

## Relatórios disponíveis

| Relatório | Endpoint | Parâmetros | Obrigatórios |
|-----------|----------|-----------|--------------|
| Vendas | `GET /api/relatorios/vendas/pdf` | `dataInicio`, `dataFim` | Sim |
| Movimento de Caixa | `GET /api/relatorios/movimento-caixa/pdf` | `data` (padrão: hoje) | Não |
| Vendas por Produto | `GET /api/relatorios/vendas-produtos/pdf` | `dataInicio`, `dataFim`, `produtoId` (opt), `detalhado` (bool) | dataInicio e dataFim |

## Validações comuns

- **Ordem cronológica**: `dataInicio` deve ser anterior a `dataFim`
- **Sem data futura**: nenhum parâmetro de data pode estar além de hoje
- **Limite de período**: máximo de 90 dias entre `dataInicio` e `dataFim`
- **Auditoria**: o usuário que gerou o relatório é registrado via `SecurityUtils` e impresso no rodapé do PDF

## Estrutura do Relatório de Vendas

O PDF de vendas contém:

1. **Header**: logo da empresa, razão social, CNPJ
2. **Período solicitado**
3. **Resumo**: faturamento total, valor base (consumo), taxa de serviço, couvert artístico, número de vendas, ticket médio
4. **Breakdown por forma de pagamento**: PIX, cartão crédito, cartão débito, dinheiro, voucher — valor total por forma
5. **Listagem de movimentos**: tabela com cada venda e seu método de pagamento
6. **Rodapé**: usuário gerador e timestamp de geração

## Stack de geração

| Camada | Tecnologia |
|--------|-----------|
| Template | Thymeleaf (HTML → XML) |
| Renderização PDF | Flying Saucer (iText) |
| Logo | Base64 ou arquivo local |
| Locale | pt_BR |

## Gaps

- **CSV/Excel**: não implementado — apenas PDF disponível
- **Relatórios agendados**: sem geração automática ou envio por e-mail
- **Relatório de financeiro**: contas a pagar/receber não têm relatório exportável próprio (apenas visualização no painel)
