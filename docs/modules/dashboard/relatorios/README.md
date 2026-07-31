> **DNA**
>
> - **id**: `relatorios`
> - **type**: `seed+node`
> - **label**: `Relatórios`
> - **ancestors**: `[dashboard, modules, docs]`
> - **maturity**: `seed`
> - **contract**: `submodulo-bakery@1.0`

# Relatórios

Relatórios é o sub-domínio que governa a geração de documentos exportáveis para análise histórica. Produz PDFs de vendas, movimento de caixa e vendas por produto, com validações de período e identificação do usuário gerador.

Pertenço a [`dashboard/`](../README.md).

## Domínio

- [`relatorios.md`](./relatorios.md) — os três relatórios disponíveis, estrutura do PDF, validações e stack de geração

## Maturidade

Estado atual: `seed`. O contrato `submodulo-bakery@1.0` ainda não foi formalizado. Até sua definição em `docs/contracts/`, este sub-domínio permanece `seed`.

## Leitura contextual

Os três relatórios existentes cobrem bem o núcleo operacional — vendas e caixa. O gap mais evidente é a ausência de exportação em CSV/Excel: o usuário recebe apenas PDF, sem possibilidade de importar os dados em planilhas externas.

Não há relatórios agendados nem envio automático por e-mail. Toda geração é sob demanda, acionada manualmente pelo usuário via interface.

## Exploração

- Estrutura e validações dos relatórios → [`relatorios.md`](./relatorios.md)
- Dados de vendas que alimentam os relatórios → [`../../vendas/README.md`](../../vendas/README.md)
- Movimento de caixa → [`../../financeiro/README.md`](../../financeiro/README.md)
