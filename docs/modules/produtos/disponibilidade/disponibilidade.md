# Disponibilidade — Especificação do Domínio

## Definição

Controla quando e onde cada produto pode ser vendido. Opera em dois níveis: produto (regras individuais) e subcategoria (regras que se aplicam a todos os produtos de uma subcategoria). A disponibilidade é definida por dia da semana e faixa de horário. Se não há regras, o produto está sempre disponível.

## Escopo

**Inclui:**
- Disponibilidade por produto (dia da semana + horário)
- Disponibilidade por subcategoria (herança para todos os produtos da subcategoria)
- Ativação/inativação de regras
- Comportamento por canal de venda (mesa digital, delivery, presencial)
- Filtros adicionais de estoque para delivery

**Não inclui:**
- Cadastro raiz do produto (pertence a `produtos/`)
- Estoque (apenas lê para definir disponibilidade em delivery)

## Modelo de dados

### ProdutoDisponibilidade

Tabela `produto_disponibilidade`.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `produto_id` | BIGINT FK | Produto |
| `diaSemana` | VARCHAR(10) | Dia da semana (DOMINGO a SABADO) |
| `horarioInicio` | TIME | Início da janela de disponibilidade |
| `horarioFim` | TIME | Fim da janela de disponibilidade |
| `ativo` | BOOLEAN | DEFAULT TRUE |

FK: `produto_id` → `produto(id)`.

### SubcategoriaDisponibilidade

Tabela `subcategoria_disponibilidade`. Mesma estrutura, vinculada a `subcategoria_id`.

## Comportamento por canal

| Canal | Comportamento |
|-------|---------------|
| Mesa digital | Bloqueia compra quando produto indisponível |
| Delivery | Usa disponibilidade + estoque > 0 |
| Presencial | Sem restrição de disponibilidade (o garçom vê o cardápio completo) |

## Regras

- Se não há regras de disponibilidade para um produto, ele está **sempre disponível**
- Múltiplas regras podem coexistir (ex: segunda 18-23 + quarta 18-23)
- Disponibilidade de subcategoria se aplica a todos os produtos daquela subcategoria
- Validação de horário: `horarioInicio` deve ser anterior a `horarioFim`

## Endpoints

### ProdutoDisponibilidade

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/produto-disponibilidade/produto/{produtoId}` | Listar regras do produto |
| `GET` | `/api/produto-disponibilidade/{id}` | Buscar regra por ID |
| `POST` | `/api/produto-disponibilidade` | Criar regra |
| `PUT` | `/api/produto-disponibilidade/{id}` | Atualizar regra |
| `DELETE` | `/api/produto-disponibilidade/{id}` | Remover regra |

### SubcategoriaDisponibilidade

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/subcategoria-disponibilidade/subcategoria/{id}` | Listar regras da subcategoria |
| `GET` | `/api/subcategoria-disponibilidade/{id}` | Buscar regra |
| `POST` | `/api/subcategoria-disponibilidade` | Criar regra |
| `PUT` | `/api/subcategoria-disponibilidade/{id}` | Atualizar regra |
| `DELETE` | `/api/subcategoria-disponibilidade/{id}` | Remover regra |

## Serviços

### ProdutoDisponibilidadeService

CRUD com validação de horário.

### SubcategoriaDisponibilidadeService

CRUD com validação de horário.

## Frontend

| Componente | Descrição |
|-----------|-----------|
| `ProdutoDisponibilidadeTab.vue` | Aba no formulário do produto: cards de regras com seletor de dia e horário, banner "sempre disponível" quando não há regras |
| `SubcategoriaDisponibilidadeTab.vue` | Aba no formulário de subcategoria: mesmas regras aplicadas à subcategoria |

## Decisões de domínio

- **Disponibilidade como regras positivas** — a ausência de regras significa "sempre disponível". Não há regra de bloqueio explícito.
- **Dois níveis** — produto e subcategoria. Subcategoria serve como regra em massa para produtos similares.
- **Estoque como filtro adicional em delivery** — delivery considera disponibilidade + saldo em estoque; mesa digital considera apenas disponibilidade.
- **Presencial irrestrito** — o canal presencial não é limitado por disponibilidade; o cardápio é completo.

## Status de implementação

**IMPLEMENTADO**. Regras por produto e subcategoria, validação de horário, comportamento por canal documentado.
