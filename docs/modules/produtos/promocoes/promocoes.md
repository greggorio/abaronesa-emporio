# Promoções — Especificação do Domínio

## Definição

Regras promocionais vinculadas ao produto, com efeito sobre o preço final. Cada promoção define um tipo de desconto (percentual ou valor fixo), um dia da semana e uma janela de horário de aplicação. Promoções competem com desconto de grupo (sócio); o backend escolhe o menor preço válido.

## Escopo

**Inclui:**
- Regras promocionais por produto
- Tipos: PERCENTUAL (desconto %) e VALOR (desconto em R$)
- Dia da semana de aplicação
- Janela de horário (início e fim)
- Status ativo/inativo
- Prevenção de sobreposição de regras conflitantes
- Dashboard de promoções: produtos ativos, impacto em vendas, vendas promocionais vs normais
- Exposição da origem do desconto (PROMOCAO vs SOCIO)

**Não inclui:**
- Desconto de grupo/sócio (pertence a `clientes/grupos-de-clientes/`)
- Fidelização e recompensas (pertence a `fidelizacao/`)
- Política comercial global

## Modelo de dados

### ProdutoPromocao

Tabela `produto_promocao`.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `produto_id` | BIGINT FK | Produto em promoção |
| `diaSemana` | VARCHAR(10) | Dia da semana (DOMINGO a SABADO, 1-7) |
| `horarioInicio` | TIME | Início da janela promocional |
| `horarioFim` | TIME | Fim da janela promocional |
| `tipoPromocao` | VARCHAR(10) | PERCENTUAL ou VALOR |
| `percentualDesconto` | DECIMAL(5,2) | Desconto percentual (para PERCENTUAL) |
| `valorPromocional` | DECIMAL(10,2) | Preço promocional (para VALOR) |
| `ativo` | BOOLEAN | DEFAULT TRUE |

FK: `produto_id` → `produto(id)`.

### Enums

**TipoPromocao:** `PERCENTUAL` (desconto sobre o preço de venda), `VALOR` (preço fixo promocional).

**DiaSemana:** `DOMINGO(1)`, `SEGUNDA(2)`, `TERCA(3)`, `QUARTA(4)`, `QUINTA(5)`, `SEXTA(6)`, `SABADO(7)`.

## Regras

### Precedência de descontos

Quando promoção e desconto de grupo coexistem no mesmo item:

```
precoEfetivo = MIN(precoComPromocao, precoComDescontoGrupo)
origemDesconto = "PROMOCAO" | "SOCIO"  // o que gerou o menor preço
```

### Prevenção de sobreposição

O repositório `findSobreposicao` detecta regras conflitantes (mesmo produto, mesmo dia da semana, horários sobrepostos) antes de criar ou atualizar uma promoção.

## Endpoints

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `GET` | `/api/produto-promocao/produto/{produtoId}` | — | Listar promoções de um produto |
| `GET` | `/api/produto-promocao/{id}` | — | Buscar promoção por ID |
| `POST` | `/api/produto-promocao` | ADMIN | Criar promoção |
| `PUT` | `/api/produto-promocao/{id}` | ADMIN | Atualizar promoção |
| `DELETE` | `/api/produto-promocao/{id}` | ADMIN | Remover promoção |
| `GET` | `/api/dashboard/promocoes?periodo=7d` | ADMIN | Dashboard de promoções |

## Dashboard

`GET /api/dashboard/promocoes?periodo=7d|30d` retorna métricas agregadas:

| Métrica | Descrição |
|---------|-----------|
| Produtos com promoção ativa | Quantidade de produtos em promoção no período |
| Vendas promocionais | Total de vendas com preço promocional |
| Vendas normais | Total de vendas sem promoção |
| Percentual promocional | % de vendas sob efeito de promoção |
| Por produto | Lista de produtos com vendas promocionais vs normais individuais |

## Serviços

### ProdutoPromocaoService

| Método | Descrição |
|--------|-----------|
| `criar(ProdutoPromocaoRequest)` | Valida campos, verifica sobreposição, persiste |
| `atualizar(Long, ProdutoPromocaoRequest)` | Valida e atualiza |
| `listarPorProduto(Long)` | Promoções do produto |
| `buscarPorId(Long)` | — |
| `deletar(Long)` | Remove regra promocional |

### DashboardPromocoesService

| Método | Descrição |
|--------|-----------|
| `getDashboardPromocoes(String periodo)` | KPIs de promoções no período |

## Frontend

| Componente | Descrição |
|-----------|-----------|
| `ProdutoPromocoesTab.vue` | Aba no formulário do produto: cards de regras promocionais com add/edit/delete, seletor de dia da semana e faixa de horário |
| `PainelProdutosPromocao.vue` | Painel de dashboard de promoções |

## Integrações

| Módulo | Natureza |
|--------|----------|
| `clientes/grupos-de-clientes` | Concorrência de desconto: menor preço vence com origem declarada |
| `consumo-digital/` | Leitura: expõe preço promocional no cardápio digital |
| `dashboard/` | Leitura: métricas de impacto promocional |

## Decisões de domínio

- **Promoção vs desconto de grupo** — a governança do desconto de grupo está em `clientes/grupos-de-clientes`, não aqui. A lógica de precedência é resolvida no backend no momento da consulta de preço.
- **Sobreposição impedida** — não é possível criar duas regras para o mesmo produto no mesmo dia e horário. A validação evita conflito.
- **Horário opcional** — a janela de horário define quando a promoção está ativa. Fora da janela, o preço normal é aplicado.
- **Sem promoção combinada** — cada promoção se aplica a um único produto. Não há promoções do tipo "leve 3 pague 2" ou combo.

## Status de implementação

**IMPLEMENTADO**. CRUD de regras promocionais, prevenção de sobreposição, dashboard de impacto. Coexiste com desconto de grupo.
