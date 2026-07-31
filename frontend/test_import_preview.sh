#!/bin/bash

# Script para testar o endpoint de preview de importação de produtos

echo "Testando endpoint de preview de importação de produtos..."
echo

# Primeiro, vamos obter o token de autenticação
echo "Obtendo token de autenticação..."
TOKEN=$(curl -s -X 'POST' \
  'http://localhost:8080/api/auth/login' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "email": "root@localhost",
  "password": "123456"
}' | jq -r '.accessToken')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "Erro: Não foi possível obter o token de autenticação"
    exit 1
fi

echo "Token obtido com sucesso"
echo

# Agora vamos testar o endpoint de preview
echo "Executando teste de upload do arquivo Produtos_Espresso_11.25.xlsx..."
echo

curl -X POST \
  http://localhost:8080/api/produtos/import/preview \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: multipart/form-data' \
  -F 'file=@/home/gregorio/git/bakery/docs/Produtos_Espresso_11.25.xlsx' | jq .

echo
echo "Teste concluído!"