#!/bin/bash

# Teste do endpoint de preview de importação de produtos

echo "Iniciando teste do endpoint de preview de importação..."

# Verificar se o arquivo existe
if [ ! -f "../docs/Produtos_Espresso_11.25.xlsx" ]; then
    echo "Arquivo Produtos_Espresso_11.25.xlsx não encontrado em ../docs/"
    exit 1
fi

echo "Enviando requisição para o endpoint..."

# Fazer a requisição POST para o endpoint
response=$(curl -X POST \
  http://localhost:8080/api/produtos/import/preview \
  -H 'Content-Type: multipart/form-data' \
  -F 'file=@../docs/Produtos_Espresso_11.25.xlsx' \
  -s)

# Verificar se a resposta foi bem sucedida
if [ $? -eq 0 ]; then
    echo "Requisição realizada com sucesso!"
    echo "Resposta do servidor:"
    echo "$response" | jq .
else
    echo "Erro ao fazer a requisição"
fi