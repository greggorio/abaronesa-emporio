# Endpoint de Confirmação de Importação de Produtos

## Descrição
Este endpoint permite confirmar a importação de produtos a partir de um arquivo XLS/XLSX previamente analisado no preview. O endpoint processa o arquivo, mapeia categorias automaticamente a partir do campo "Grupo", cria produtos em lote e retorna um resumo da operação.

## Endpoint
```
POST /api/produtos/import/confirm
```

## Formato de Requisição
- Método: `POST`
- Content-Type: `multipart/form-data`
- Parâmetro: `file` - Arquivo XLS ou XLSX contendo os dados dos produtos

## Processamento
- Reprocessa o XLS para obter todas as linhas válidas com campos completos
- Para cada grupo (coluna "Grupo"), tenta encontrar uma categoria existente por nome (case/acentuação-insensitive)
- Se a categoria não existir, ela é criada automaticamente
- Se o grupo estiver vazio, usa uma categoria padrão ("Geral")
- Para cada item válido, cria um produto com os campos mapeados:
  - `nome`, `descricao` ← descrição do XLS
  - `codigoInterno` ← código do XLS
  - `categoriaId` ← ID encontrado/criado
  - `tipoPrecificacao` ← SIMPLES
  - `unidadeMedida`, `unidadeBase`
  - `tipoCalculoMargem` ← SOBRE_CUSTO
  - `precoCusto`, `precoVenda` (valor do "Custo" do XLS)
  - `margemLucro` (usar default 100)
  - `ativo`, `ncm`
- Produtos com código já existente são ignorados (duplicados) e contabilizados separadamente

## Resposta

O endpoint retorna um JSON com o seguinte resumo:

```json
{
  "total": 308,
  "processados": 300,
  "criadas": 295,
  "ignoradasDuplicadas": 5,
  "erros": 8,
  "categoriasCriadas": [
    { "nome": "BEBIDAS", "id": 12 },
    { "nome": "ALIMENTOS", "id": 15 }
  ],
  "amostrasErro": [
    { "linha": 77, "mensagem": "Preço inválido" }
  ]
}
```

### Descrição dos Campos de Resposta:
- `total`: Número total de linhas no arquivo
- `processados`: Número de linhas processadas (válidas)
- `criadas`: Número de produtos criados com sucesso
- `ignoradasDuplicadas`: Número de produtos ignorados por terem código já existente
- `erros`: Número de linhas que geraram erro durante o processamento
- `categoriasCriadas`: Lista de categorias criadas automaticamente a partir dos grupos do XLS
- `amostrasErro`: Até 5 exemplos de linhas que geraram erro com mensagem explicativa

## Exemplo de Chamada com cURL

```bash
curl -X POST \
  http://localhost:8080/api/produtos/import/confirm \
  -H 'Content-Type: multipart/form-data' \
  -H 'Authorization: Bearer TOKEN_JWT_AQUI' \
  -F 'file=@exemplo_produtos.xlsx'
```

## Observações
- Categorias novas são criadas automaticamente a partir do campo "Grupo" do XLS
- Se uma categoria com nome semelhante (case/acentuação-insensitive) já existir, seu ID é reutilizado
- Produtos com código interno já existente são ignorados, sem interromper a importação
- O campo "Custo" do XLS é utilizado tanto para `precoCusto` quanto para `precoVenda` durante a importação
- Quando o campo "Grupo" está vazio, o produto é associado à categoria padrão "Geral"