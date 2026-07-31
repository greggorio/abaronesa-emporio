# Cardapio Publico

## Base Path

```http
/api/public/cardapio
```

## Papel

Esses endpoints expoem o catalogo publico consumido por site, mesa digital e delivery.

## Evidencia Material

- [CardapioController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/CardapioController.java)

## Endpoints Principais

### Cardapio Completo

```http
GET /api/public/cardapio
```

Retorna lista de `CardapioCategoriaDTO`.

Header aceito:

- `X-Guest-Token` opcional

### Produtos em Destaque

```http
GET /api/public/cardapio/destaques
```

Retorna lista de `CardapioProdutoDTO`.

Segundo o comentario do controller:

- prioriza produtos com `destaque = true`
- completa com produtos normais se houver menos de 6 destaques

### Cardapio V2

```http
GET /api/public/cardapio/v2
```

Retorna lista de `CardapioCategoriaV2DTO`.

Header aceito:

- `X-Guest-Token` opcional

O comentario do controller indica que esta versao expoe SKUs por produto.

### Cardapio de Delivery

```http
GET /api/public/cardapio/delivery
```

Retorna lista de `CardapioCategoriaV2DTO`.

O comentario atual do controller indica que:

- por enquanto replica a versao `v2`
- pode receber regras proprias de delivery depois

## Observacoes

- esses endpoints aparecem publicos no `SecurityConfig`
- o dominio funcional do catalogo continua em [../../modules/produtos/README.md](../../modules/produtos/README.md)
- o consumo digital desses contratos aparece em [../../modules/consumo-digital/README.md](../../modules/consumo-digital/README.md)
