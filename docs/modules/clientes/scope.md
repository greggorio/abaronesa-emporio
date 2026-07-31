# Clientes — Escopo

## O que é este módulo

Clientes governa a identidade de quem consome no ecossistema Bakery, a classificação comercial desse cliente e a frequência de consumo derivada do seu histórico. O módulo responde a três perguntas centrais: quem é este cliente, a que segmento comercial ele pertence, e o que ele consome com frequência.

O cliente é modelado como um `Usuario` com `Role.CLIENTE`, complementado por `PerfilCliente` em relação 1:1 — o par é criado junto e o perfil não existe sem o usuário.

## Sub-domínios

| Sub-domínio | Responsabilidade |
|-------------|-----------------|
| `clientes/` (raiz) | Cadastro, perfil (PF/PJ), identidade e auto-serviço (`/me`) |
| `grupos-de-clientes/` | Classificação comercial e descontos por categoria/subcategoria |
| `area-do-cliente/` | Superfície autenticada de leitura consolidada |
| `favoritos/` | Frequência e afinidade de consumo derivadas do histórico de pedidos |

## Inclui

- Cadastro e perfil do cliente (pessoa física e jurídica)
- Segmentação comercial por grupos de clientes
- Descontos por grupo por categoria e subcategoria, com lógica de precedência frente a promoções
- Superfície autenticada do cliente (área do cliente) — agregação de leitura de múltiplos módulos
- Favoritos calculados por frequência de consumo (somatório de `ItemPedido` por produto)
- Analytics básico: total de clientes e novos no período
- Auto-serviço: dados de checkout autenticado (`/me`) e busca por e-mail
- Suporte a consumo anônimo via `guestToken`

## Não inclui

- Jornada transacional de consumo digital → `consumo-digital/`
- Fidelização: pontos, recompensas e resgates → `fidelizacao/`
- Financeiro do estabelecimento → `financeiro/`
- Governança de eventos → `eventos/`
- Preço base e promoção de produtos → `produtos/`

## Fronteiras

| Par | Divisão |
|-----|---------|
| `clientes` × `consumo-digital` | Identidade e superfícies ficam em `clientes`; jornada transacional em `consumo-digital` |
| `clientes` × `fidelizacao` | `clientes` governa o ator relacional; `fidelizacao` governa benefícios e recompensas |
| `clientes` × `produtos/promocoes` | `clientes` governa pertencimento ao grupo; `produtos/promocoes` governa preço base e promoção |
