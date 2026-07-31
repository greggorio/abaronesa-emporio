# QR Ordering Local Runtime

## Visao Geral

Este documento descreve o ambiente local minimo para executar o fluxo de QR Ordering com backend ERP e PWA.

Ele deve ser lido como referencia tecnica de runtime local, nao como handoff de sessao.

## Repositorios e Caminhos

- ERP backend (Spring Boot): `~/git/bakery/backend`
- Site/PWA (React + Vite): `~/git/bakery/espresso_front`
- Backend complementar do site, quando aplicavel: `~/git/bakery/espresso_back`

## Topologia de Runtime

- o PWA conversa diretamente com a API HTTP do ERP
- base URL de desenvolvimento: `http://localhost:8080`
- configuracao no frontend via `VITE_ERP_API_URL`
- SSE entre ERP e PWA por `/api/events/sessoes/{sessaoMesaId}`

Eventos SSE usados no fluxo:

- `order.created`
- `kds.status_changed`
- `payment.updated`
- `table.closed`

## Banco de Dados

- engine: PostgreSQL
- banco de desenvolvimento documentado: `villa_erp_db`
- driver JDBC: `org.postgresql.Driver`
- ORM: Hibernate/JPA
- em desenvolvimento, o documento registra `spring.jpa.hibernate.ddl-auto=update`

Verificacoes rapidas com `psql`:

- `psql postgresql://postgres:postgres@localhost:5432/villa_erp_db -c "\conninfo"`
- `psql postgresql://postgres:postgres@localhost:5432/villa_erp_db -c "\dt"`

## Build e Execucao

### Backend do ERP

Pre-requisitos:

- Java 21
- Maven
- PostgreSQL em execucao

Comando:

```bash
cd ~/git/bakery/backend
mvn -q -DskipTests spring-boot:run
```

Health check:

- `GET http://localhost:8080/api/health`

### Frontend do Site/PWA

Pre-requisitos:

- Node 18+ para o fluxo aqui documentado
- npm ou bun

Configurar `~/git/bakery/espresso_front/.env`:

```env
VITE_ERP_API_URL=http://localhost:8080
```

Comando:

```bash
cd ~/git/bakery/espresso_front
npm run dev
```

Acesso tipico:

- `/m/:mesa_slug`
- `/admin/mesas`

## Seguranca no Estado Atual

O `SecurityConfig` atual nao usa um `permitAll` irrestrito para tudo. O desenho observado e:

- autenticacao obrigatoria por padrao para rotas nao liberadas explicitamente
- liberacao especifica para rotas publicas do fluxo QR, cardapio, pagamentos publicos e webhooks
- rotas administrativas protegidas por role

No fluxo de mesa:

- o convidado e identificado por `guest_token`
- o token e mantido no browser em `localStorage`
- o primeiro convidado da sessao se torna host

## Logica de Negocio Resumida

- a rota `/m/:mesa_slug` exige identificacao inicial do convidado
- pedidos pertencem a uma `SessaoConvidado` dentro de uma `SessaoMesa`
- o KDS conduz o estado dos itens
- a conta pode ser consultada por convidado ou por mesa
- o pagamento pode ocorrer via self-checkout ou fluxo administrativo
- a sessao e encerrada automaticamente quando o devido chega a zero

## Contratos de API Mais Relevantes

### Cardapio

- `GET /api/public/cardapio`

### Mesa e convidado

- `POST /api/mesas/{mesa_slug}/convidados`
- `GET /api/mesas/{mesa_slug}/sessao`

### Pedidos

- `POST /api/pedidos`
- `GET /api/pedidos/{pedido_id}`

### Conta

- `GET /api/conta?sessaoConvidadoId=...`
- `GET /api/conta?sessaoMesaId=...`

### Pagamentos

- `POST /api/pagamentos/intent`
- `POST /api/pagamentos/webhook`
- `GET /api/payments/status`

### Admin

- `GET /api/admin/mesas/sessoes?status=open|closed`
- `POST /api/admin/mesas/sessoes/{id}/pagamentos`
- `POST /api/admin/mesas/sessoes/{id}/fechar`

## Eventos SSE

Assinatura:

- `GET /api/events/sessoes/{sessaoMesaId}`

Eventos principais:

- `order.created`
- `kds.status_changed`
- `payment.updated`
- `table.closed`

## Modelo de Dados Envolvido

- `SessaoMesa`
- `SessaoConvidado`
- `Pedido`
- `ItemPedido`
- `Pagamento`
- `Produto` e `Categoria`

## Configuracoes de Ambiente

### Backend

No `application.properties` de desenvolvimento, o documento considera:

- `spring.datasource.url=jdbc:postgresql://localhost:5432/villa_erp_db`
- `spring.datasource.username=postgres`
- `spring.datasource.password=postgres`
- `spring.jpa.hibernate.ddl-auto=update`

### Frontend

- `.env`: `VITE_ERP_API_URL=http://localhost:8080`

## Exemplos Rapidos de Fluxo

### Criar convidado

```bash
curl -s -X POST http://localhost:8080/api/mesas/T-12/convidados \
  -H 'Content-Type: application/json' \
  -d '{"nome_exibicao":"Maria"}'
```

### Criar pedido

```bash
curl -s -X POST http://localhost:8080/api/pedidos \
  -H 'X-Guest-Token: <token>' \
  -H 'X-Sessao-Mesa: <id>' \
  -H 'Content-Type: application/json' \
  -d '{"itens":[{"produtoId":4,"quantidade":2}]}'
```

### Criar intent PIX

```bash
curl -s -X POST http://localhost:8080/api/pagamentos/intent \
  -H 'Content-Type: application/json' \
  -H 'X-Guest-Token: <token>' \
  -d '{"escopo":"mesa","sessaoMesaId":<id>,"metodo":"pix","payerName":"Maria","payerEmail":"maria@example.com"}'
```

## Onde Aprofundar

- contratos de API: [../api-reference/README.md](../api-reference/README.md)
- consumo digital: [../modules/consumo-digital/README.md](../modules/consumo-digital/README.md)
- infraestrutura e ambiente: [../infrastructure/README.md](../infrastructure/README.md)
