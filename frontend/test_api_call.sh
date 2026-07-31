#!/bin/bash
# Teste para simular a chamada que o componente frontend fará (até 50 primeiros produtos mostrados)

echo "Testando a chamada de API como feita pelo componente frontend..."

# Obter o token
TOKEN=$(curl -s -X 'POST' \
  'http://localhost:8080/api/auth/login' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "email": "root@localhost",
  "password": "123456"
}' | jq -r '.accessToken')

echo "Usando token: ${TOKEN:0:20}..."  # Exibir só parte do token por segurança

# Testar o endpoint com o arquivo
echo "Enviando arquivo para o endpoint de preview..."
curl -X POST \
  http://localhost:8080/api/produtos/import/preview \
  -H "Authorization: Bearer $TOKEN" \
  -F 'file=@/home/gregorio/git/bakery/docs/Produtos_Espresso_11.25.xlsx' | jq '.total, .validos, .duplicadosInternos, .invalidos' 

echo "Teste concluído!"