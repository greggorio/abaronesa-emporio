# Recompensas — Especificação do Domínio

## Definição

Catálogo de recompensas que o cliente pode obter com seus pontos de fidelidade. Governa o cadastro, a classificação por tipo, o custo em pontos, a disponibilidade (validade e estoque) e a consulta de elegibilidade por cliente.

## Escopo

**Inclui:**
- Cadastro, edição e inativação de recompensas
- Quatro tipos de recompensa: PRODUTO, DESCONTO_PERCENTUAL, DESCONTO_VALOR, BRINDE_GENERICO
- Custo em pontos por recompensa
- Estoque individual por recompensa
- Janela de validade (data de início e fim)
- Flags de ativação/inativação
- Consulta de recompensas disponíveis para um cliente (com elegibilidade)
- CRUD administrativo completo

**Não inclui:**
- Saldo de pontos do cliente (pertence a `gamificacao/`)
- Resgate e débito de pontos (pertence a `resgates/`)
- Sorteios e brindes do espresso_back (pertence ao subsistema `Reward` do `espresso_back`)

## Modelo de dados

### Recompensa

Tabela `recompensas` — catálogo de vantagens disponíveis para resgate.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador da recompensa |
| `nome` | VARCHAR(120) NOT NULL | Nome de exibição |
| `descricao` | VARCHAR(500) | Descrição detalhada |
| `pontos_necessarios` | INTEGER NOT NULL | Custo em pontos para resgatar |
| `tipo` | VARCHAR(30) NOT NULL | PRODUTO, DESCONTO_PERCENTUAL, DESCONTO_VALOR ou BRINDE_GENERICO |
| `desconto_percentual` | DECIMAL(5,2) | Percentual de desconto (para tipo DESCONTO_PERCENTUAL) |
| `desconto_valor` | DECIMAL(12,2) | Valor fixo de desconto (para tipo DESCONTO_VALOR) |
| `desconto_valor_maximo` | DECIMAL(12,2) | Teto do desconto percentual |
| `produto_id` | BIGINT | Produto específico (para tipo PRODUTO) |
| `estoque` | INTEGER | Quantidade disponível (null = ilimitado) |
| `ativo` | BOOLEAN DEFAULT TRUE | Flag de ativação/inativação |
| `validade_inicio` | DATE | Início da janela de validade |
| `validade_fim` | DATE | Fim da janela de validade |
| `imagem_url` | VARCHAR(500) | URL da imagem da recompensa |
| `criado_em` | TIMESTAMP | Data de criação |
| `atualizado_em` | TIMESTAMP | Data da última atualização |

### Tipos de recompensa

| Tipo | Descrição | Campos específicos |
|------|-----------|-------------------|
| `PRODUTO` | Um produto específico do cardápio | `produtoId` |
| `DESCONTO_PERCENTUAL` | Percentual de desconto na conta | `descontoPercentual`, `descontoValorMaximo` |
| `DESCONTO_VALOR` | Valor fixo de desconto na conta | `descontoValor` |
| `BRINDE_GENERICO` | Brinde não vinculado a produto (experiência, cortesia) | — |

## Regras de disponibilidade

Uma recompensa é considerada **disponível** quando todas as condições abaixo são satisfeitas:

1. `ativo = true`
2. `validadeInicio` é nulo OU `hoje >= validadeInicio`
3. `validadeFim` é nulo OU `hoje <= validadeFim`
4. `estoque` é nulo (ilimitado) OU `estoque > 0`

## Elegibilidade por cliente

Uma recompensa disponível é **elegível** para um cliente quando o saldo do cliente é maior ou igual a `pontosNecessarios`.

A resposta da consulta de recompensas para cliente inclui os campos:

| Campo | Descrição |
|-------|-----------|
| `podeResgatar` | `true` se disponível e saldo ≥ pontosNecessarios |
| `faltamPontos` | Quantos pontos faltam para atingir o resgate (0 se elegível) |

## Endpoints

### Admin (backend bakery)

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `GET` | `/api/admin/recompensas` | ADMIN | Lista todas as recompensas |
| `POST` | `/api/admin/recompensas` | ADMIN | Criar nova recompensa |
| `PUT` | `/api/admin/recompensas/{id}` | ADMIN | Atualizar recompensa |

### Cliente

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `GET` | `/api/clientes/me/gamificacao/recompensas` | Cliente autenticado | Recompensas disponíveis + elegíveis para o cliente logado |

### Admin - consulta por cliente

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `GET` | `/api/admin/clientes/{clienteId}/gamificacao/recompensas` | ADMIN | Recompensas elegíveis + saldo de um cliente específico |

## Serviços

### RecompensaService

CRUD do catálogo de recompensas.

### RecompensaValidator

Validação de integridade dos dados da recompensa antes da persistência.

### RecompensaClienteService

| Método | Descrição |
|--------|-----------|
| `getRecompensasDisponiveis()` | Lista recompensas disponíveis no catálogo |
| `getRecompensasDisponiveisParaCliente(Long saldo)` | Filtra recompensas que o cliente pode resgatar dado seu saldo |

### DashboardGamificacaoService

| Método | Descrição |
|--------|-----------|
| `getRecompensasDisponiveisParaCliente(Long clienteId)` | Recompensas elegíveis (disponíveis + saldo suficiente) para um cliente, com `podeResgatar` e `faltamPontos` |

## Frontend

### Admin (Vue/Quasar - frontend)

| Componente | Descrição |
|-----------|-----------|
| `RecompensasConfig.vue` | CRUD completo: tabela com todas as recompensas, formulário de criação/edição com campos por tipo, toggle de ativação |

### Cliente (espresso_front - React/TypeScript)

| Componente | Rota | Descrição |
|-----------|------|-----------|
| `RecompensasInbox.tsx` | `/areacliente/recompensas` | Lista de recompensas do cliente com status (AVAILABLE/REDEEMED/EXPIRED) |

## Integrações

| Módulo | Natureza |
|--------|----------|
| `gamificacao/` | Leitura: consulta saldo do cliente para determinar elegibilidade |
| `resgates/` | Escrita: ao resgatar, o estoque da recompensa é decrementado |
| `espresso_back` (Reward) | Subsistema separado: brindes e sorteios com entidade `Reward` própria, não vinculados ao catálogo de recompensas do bakery |

## Decisões de domínio

- **Estoque opcional** — `estoque = null` significa ilimitado. Apenas recompensas com controle de quantidade definem um valor.
- **Campos específicos por tipo** — `produtoId`, `descontoPercentual`, `descontoValor` e `descontoValorMaximo` são mutuamente exclusivos conforme o tipo. O validator deve garantir essa consistência.
- **Inativação não destrutiva** — recompensas são inativadas via `ativo = false`, nunca removidas fisicamente. Resgates já realizados mantêm o nome e os dados da recompensa no histórico.
- **Catálogo compartilhado** — todas as recompensas são visíveis a todos os clientes (quando disponíveis). Não há recompensas exclusivas por segmento de cliente.
- **Reward do espresso_back é outro domínio** — a entidade `Reward` no `espresso_back` gerencia brindes individuais e sorteios, com ciclo de próprio (AVAILABLE → REDEEMED → EXPIRED), sem vínculo com o catálogo de recompensas do bakery.

## Status de implementação

**EM_DESENVOLVIMENTO**. O CRUD de recompensas, a consulta por disponibilidade e a elegibilidade por cliente estão implementados. A validação de consistência entre tipo e campos específicos existe em `RecompensaValidator`.
