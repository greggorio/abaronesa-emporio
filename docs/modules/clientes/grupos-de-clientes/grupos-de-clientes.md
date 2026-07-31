# Grupos de Clientes — Especificação

## Definição

Sub-domínio responsável pela classificação comercial do cliente e pela configuração de descontos por categoria e subcategoria associados a essa classificação.

## Entidades

### GrupoCliente

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | Identificador |
| `descricao` | String | Nome do grupo (UNIQUE) — ex: "SÓCIO", "E-COMMERCE" |

### GrupoClienteDesconto

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `grupoCliente` | FK | Grupo ao qual o desconto pertence |
| `categoria` | FK | Categoria de produto onde se aplica |
| `subcategoria` | FK (nullable) | Subcategoria específica — refinamento opcional |
| `descontoPercentual` | Decimal(5,2) | Percentual — ex: 10.00 para 10% |
| `ativo` | Boolean | Controla se o desconto está vigente |

**Unicidade:**
- Sem subcategoria: `(grupoClienteId, categoriaId)` — um desconto por categoria por grupo
- Com subcategoria: `(grupoClienteId, categoriaId, subcategoriaId)` — refinamento sem conflito

## Regras de desconto

- Descontos se aplicam por categoria, com refinamento opcional por subcategoria
- `PUT /api/grupos-clientes/{id}/descontos` opera por sincronização: deleta os anteriores e insere os novos — o chamador deve enviar sempre o conjunto completo desejado
- Quando desconto de grupo e promoção coexistem no mesmo item, o backend expõe o menor preço resultante com `origemDesconto = SOCIO` quando o desconto de grupo vence

## Fluxo de aplicação no cardápio

1. Backend resolve `grupoClienteId` a partir do `guestToken` da sessão autenticada
2. Carrega os descontos ativos do grupo
3. Para cada item do cardápio, calcula preço com desconto de grupo
4. Compara com promoção vigente do produto
5. Expõe o menor preço com a origem correspondente (`SOCIO` ou `PROMOCAO`)

## Escopo

**Inclui:**
- CRUD de grupos de clientes
- Configuração de descontos por grupo por categoria e subcategoria
- Vínculo cliente × grupo (via `PerfilCliente.grupoClienteId`)

**Não inclui:**
- Definição do preço base do produto
- Governança de promoções
- Fidelização, pontos e recompensas
