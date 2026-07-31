# QR Ordering — Especificação

## Definição

Protocolo de entrada na jornada de mesa digital via QR Code. O QR codifica o `slug` da mesa; ao escanear, o cliente acessa o cardápio e cria sua sessão de convidado — sem necessidade de cadastro prévio.

## Fluxo de acesso

1. Staff gera QR Code da mesa (contém URL com `mesaSlug`)
2. Cliente escaneia → frontend valida sessão ativa via `GET /api/mesas/{mesaSlug}/sessao`
3. Se não há sessão ativa: staff precisa abrir a mesa antes
4. Cliente informa nome de exibição → `guestToken` é gerado
5. A partir deste ponto, o cliente está na jornada de Mesa Digital

## Cardápio digital

O cardápio acessado via QR é o mesmo endpoint público `/api/cardapio`, com filtros aplicados:

- **Disponibilidade por horário**: `ProdutoDisponibilidade` define quando cada produto pode ser pedido
- **Disponibilidade por canal**: produtos podem ser habilitados/desabilitados por canal (mesa vs. delivery)
- **SKUs e variações**: cliente escolhe tamanho, sabor ou embalagem antes de adicionar ao carrinho
- **Promoções e descontos de grupo**: quando o convidado está identificado, o backend resolve o `grupoClienteId` e aplica o menor preço resultante

## Suporte a idiomas

A interface suporta três idiomas (PT, EN, ES). A detecção é automática via `Accept-Language`. Labels de mesa, cardápio e fluxo de pagamento são todos traduzidos — relevante para estabelecimentos com clientela internacional.

## Escopo

**Inclui:**
- QR Code único por mesa (baseado no slug)
- Validação de sessão ativa antes de permitir acesso
- Cardápio filtrado por disponibilidade e canal
- Criação de identidade anônima via guestToken
- Suporte a múltiplos idiomas

**Não inclui:**
- Cadastro de mesas (pertence à gestão operacional)
- Gestão de disponibilidade de produtos (pertence a `produtos/`)
- A experiência de consumo após a entrada (pertence a `mesa-digital/`)
