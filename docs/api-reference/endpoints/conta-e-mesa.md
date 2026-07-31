# Conta e Mesa

## Base Paths

```http
/api/mesas
/api/conta
```

## Papel

Estes endpoints cobrem a verificacao de sessao de mesa, criacao de convidado, fechamento de sessao e consulta da conta.

## Evidencia Material

- [SessaoMesaController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/SessaoMesaController.java)
- [ContaController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/ContaController.java)

## Endpoints de Sessao e Convidados

### Verificar Sessao da Mesa

```http
GET /api/mesas/{mesaSlug}/sessao
```

Retorna se existe sessao ativa para a mesa e alguns metadados basicos, como:

- `sessaoAtiva`
- `sessaoMesaId`
- `totalConvidados`
- `mesaRotulo`
- `abertaEm`
- `assistida`

### Criar Convidado na Mesa

```http
POST /api/mesas/{mesaSlug}/convidados
```

Cria convidado para a sessao da mesa e retorna `CriarConvidadoResponse`.

O fluxo atual tambem tenta:

- publicar SSE `guest.joined`
- registrar notificacao para o host da mesa

### Fechar Sessao da Mesa

```http
POST /api/mesas/sessoes/{sessaoMesaId}/fechar
```

Header relevante:

- `X-Guest-Token`

Regras observadas no controller:

- apenas o host pode fechar a sessao
- a conta precisa estar quitada (`devidoCentavos == 0`)

Se tudo estiver correto, responde:

- `200 OK`
- `{ "success": true }`

## Endpoint de Conta

### Consultar Conta

```http
GET /api/conta
```

Query params suportados:

- `sessaoMesaId`
- `sessaoConvidadoId`

Comportamento:

- se `sessaoMesaId` for informado, retorna `ContaMesaResponse`
- se `sessaoConvidadoId` for informado, retorna `ContaConvidadoResponse`
- se nenhum dos dois for informado, responde `400 Bad Request`

## Observacoes

- esses endpoints aparecem liberados no `SecurityConfig` para o fluxo publico de mesa
- o dominio funcional desta area continua em [../../modules/consumo-digital/mesa-digital/README.md](../../modules/consumo-digital/mesa-digital/README.md), [../../modules/consumo-digital/conta-digital/README.md](../../modules/consumo-digital/conta-digital/README.md) e [../../modules/consumo-digital/self-checkout/README.md](../../modules/consumo-digital/self-checkout/README.md)
