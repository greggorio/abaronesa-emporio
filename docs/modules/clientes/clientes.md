# Clientes — Especificação do Domínio

## Definição

Módulo que governa a identidade do cliente, seu relacionamento com o ecossistema e a continuidade da experiência autenticada. O cliente é modelado como um `Usuario` com `Role.CLIENTE`, complementado por `PerfilCliente` em relação 1:1 — esse par é criado junto e o perfil não existe sem o usuário.

## Escopo

**Inclui:**
- Cadastro e perfil do cliente (PF e PJ)
- Segmentação comercial por grupos de clientes
- Descontos por grupo por categoria e subcategoria, com lógica de precedência
- Superfície autenticada do cliente (área do cliente) — leitura consolidada de múltiplos módulos
- Favoritos e frequência de consumo derivados do histórico de pedidos
- Analytics básico: total de clientes e novos no período
- Auto-serviço: dados de checkout autenticado (`/me`) e busca por e-mail

**Não inclui:**
- Jornada transacional de consumo digital (pertence a `consumo-digital/`)
- Fidelização: pontos, recompensas e resgates (pertence a `fidelizacao/`)
- Financeiro do estabelecimento (pertence a `financeiro/`)
- Governança de eventos (pertence a `eventos/`)
- Regra de preço base e promoção de produtos (pertence a `produtos/`)

## Modelo de dados

### Usuario

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | Identificador |
| `nome` | String | Nome completo |
| `email` | String | Único no sistema |
| `telefone` | String | Contato |
| `roles` | Set\<Role\> | CLIENTE (pode acumular outros papéis) |
| `ativo` | Boolean | Soft delete |
| `emailVerificado` | Boolean | Verificação de e-mail (campos existem; fluxo ainda não operacionalizado) |
| `provider` | Enum | LOCAL ou GOOGLE |
| `providerId` | String | ID do provedor OAuth2 |
| `clienteOnline` | Boolean | Tracking de presença em tempo real |
| `criadoEm` | Timestamp | — |
| `ultimoLogin` | Timestamp | — |

### PerfilCliente

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `tipoPessoa` | Enum | PF ou PJ |
| `cpf` / `cnpj` | String | Documento fiscal |
| `inscricaoEstadual` | String | Para PJ |
| `dataNascimento` | Date | Para PF |
| `logradouro`, `numero`, `bairro`, `complemento`, `cidade`, `estado`, `cep` | String | Endereço completo |
| `codigoMunicipioIbge` | String | Para emissão fiscal |
| `origemCadastro` | Enum | LOJA_FISICA ou E_COMMERCE |
| `mensalista` | Boolean | Flag para assinatura (estrutura existe; feature não desenvolvida) |
| `grupoClienteId` | FK | Grupo comercial associado |

O método `isPerfilCompleto()` valida os campos obrigatórios por tipo: PF requer CPF; PJ requer CNPJ.

## Fronteiras

| Par | Divisão |
|-----|---------|
| `clientes` × `consumo-digital` | Identidade e superfícies ficam em `clientes`; jornada transacional em `consumo-digital` |
| `clientes` × `fidelizacao` | `clientes` governa o ator relacional; `fidelizacao` governa benefícios e recompensas |
| `clientes` × `produtos/promocoes` | `clientes` governa pertencimento ao grupo; `produtos/promocoes` governa preço base e promoção |

## Integrações

| Módulo | Natureza |
|--------|----------|
| `produtos/promocoes` | Expõe preços com desconto de grupo; quando promoção e desconto de grupo coexistem, aplica o menor (`origemDesconto = SOCIO`) |
| `consumo-digital` | Usa identidade e grupo do cliente no contexto do pedido digital |
| `fidelizacao` | Área do cliente exibe saldo de pontos e recompensas disponíveis (leitura) |
| `financeiro` | Área do cliente exibe contas abertas do cliente (leitura) |
| `eventos` | Área do cliente lista próximos eventos (leitura) |

## Decisões de domínio

- **Grupo padrão E-COMMERCE**: clientes criados via e-commerce recebem automaticamente o grupo "E-COMMERCE"; o grupo padrão está hardcoded — não é configurável
- **Resgate admin-only**: resgates de recompensa não têm endpoint direto para o cliente — operam via admin
- **Favoritos derivados**: não há marcação explícita; são calculados por frequência de consumo (somatório de `ItemPedido` por produto)
- **guestToken**: suporte a consumo anônimo via token de sessão, sem cadastro prévio; o `grupoClienteId` é resolvido a partir do token
- **OAuth2 preparado**: campos `provider` e `providerId` existem no modelo; fluxo de login via Google ainda não operacionalizado
- **ClienteRef desacoplado**: o `espresso_back` mantém uma cópia de referência dos clientes sincronizada via ERP (`POST /api/clientes-ref/sync`), sem sincronização reversa
