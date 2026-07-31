# Endpoint de Preview de Importação de Produtos

## Descrição
Este endpoint permite fazer o upload de um arquivo XLS/XLSX contendo produtos e gerar um preview estruturado com os dados que seriam importados. O endpoint valida os dados e identifica potenciais problemas antes da importação real.

## Endpoint
```
POST /api/produtos/import/preview
```

## Formato de Requisição
- Método: `POST`
- Content-Type: `multipart/form-data`
- Parâmetro: `file` - Arquivo XLS ou XLSX contendo os dados dos produtos

## Mapeamento de Campos
O arquivo deve conter colunas com nomes semelhantes às seguintes (não diferencia maiúsculas de minúsculas e tolera acentos):

| Campo Mapeado | Variações Aceitas |
|---------------|------------------|
| `codigoInterno` | `codigo`, `código`, `cod`, `codigo_interno`, `código_interno`, `id`, `codigo produto`, `código produto` |
| `nome` e `descricao` | `descricao`, `descrição`, `nome`, `produto`, `descricao_produto`, `descrição_produto`, `nome_produto`, `item`, `texto`, `produto_descricao`, `produto_descrição` |
| `precoVenda` | `custo`, `preco`, `preço`, `valor`, `preco_venda`, `preço_venda`, `preco_custo`, `preço_custo`, `vl_custo`, `vl_venda`, `valor_venda`, `valor_custo`, `venda`, `unitario`, `unitário` |
| `unidadeMedida` | `unidade`, `unidade_medida`, `unidade base`, `un`, `und`, `medida`, `tipo_unidade`, `unidade_produto`, `sigla_unidade` |
| `ativo` | `ativo`, `status`, `situacao`, `situação`, `habilitado`, `ativo_inativo`, `status_ativo`, `ind_ativo`, `flag_ativo` |
| `categoriaId` | `grupo`, `categoria`, `grupo_produto`, `categoria_produto`, `classificacao`, `classificação`, `tipo`, `segmento`, `area`, `área`, `setor` |
| `ncm` | `ncm`, `codigo_ncm`, `código_ncm`, `ncm_produto`, `codigo_nomenclatura`, `código_nomenclatura`, `nomenclatura` |

## Resposta

O endpoint retorna um JSON com a seguinte estrutura (até 50 linhas válidas para preview):

```json
{
  "total": 124,
  "validos": 120,
  "duplicadosInternos": 2,
  "invalidos": 2,
  "linhasValidas": [
    {
      "nome": "Espresso",
      "descricao": "Espresso curto",
      "codigoInterno": "104",
      "precoCusto": "2.00",
      "precoVenda": "2.00",
      "margemLucro": 100,
      "tipoPrecificacao": "SIMPLES",
      "unidadeMedida": "UN",
      "unidadeBase": "UNIDADE",
      "tipoCalculoMargem": "SOBRE_CUSTO",
      "ativo": true,
      "ncm": "21011200",
      "grupo": "BEBIDAS",
      "categoriaId": null
    }
  ],
  "exemploInvalido": {
    "linha": 77,
    "mensagem": "Preço ausente"
  },
  "categoriasDetectadas": [
    {
      "nome": "BEBIDAS",
      "existe": true,
      "categoriaId": 1,
      "contagem": 15
    }
  ]
}
```

### Descrição dos Campos de Resposta:
- `total`: Número total de linhas no arquivo
- `validos`: Número de linhas válidas (com dados corretos e sem duplicação)
- `duplicadosInternos`: Número de linhas duplicadas (mesmo código interno)
- `invalidos`: Número de linhas inválidas (com dados faltando ou incorretos)
- `linhasValidas`: Array com até 50 primeiras linhas válidas para preview contendo todos os campos necessários para criação do produto
- `exemploInvalido`: Um exemplo de linha inválida com mensagem de erro
- `categoriasDetectadas`: Lista de categorias detectadas no arquivo com flags de existência e contagem de itens associados

## Exemplo de Chamada com cURL

```bash
curl -X POST \
  http://localhost:8080/api/produtos/import/preview \
  -H 'Content-Type: multipart/form-data' \
  -F 'file=@exemplo_produtos.xlsx'
```

## Critérios de Validação
- São consideradas inválidas linhas sem código ou preço
- São contabilizadas duplicatas internas pelo código interno
- Valores numéricos são normalizados (substitui vírgula por ponto e garante 2 casas decimais)
- Valores booleanos são normalizados (1, true, sim = true; outros = false)

## Exemplo de Arquivo Template

O arquivo de importação deve seguir o formato abaixo:

```
codigo | descricao | custo | unidade | ativo | grupo | ncm
1001   | Café Preto| 3.50  | UN      | 1     | 1     | 21011100
1002   | Refrigerante | 8.00 | UN      | 1     | 1     | 22021000
```

## Observações
- Apenas os 50 primeiros produtos válidos são retornados no preview (ordenados por nome ascendente) para desempenho
- Todos os números são formatados com 2 casas decimais
- O campo categoriaId é preenchido com o ID da categoria existente quando o nome do Grupo do XLS coincide com uma categoria cadastrada
- O campo unidadeBase faz mapeamento automático: ML/MILILITRO → MILILITRO, G/GRAMA → GRAMA, outros → UNIDADE
- O bloco categoriasDetectadas lista todas as categorias encontradas no XLS com flags de existência e contagem de itens
