#!/bin/bash

echo "=== Teste de Chamado SSE ==="
echo ""
echo "Este script vai:"
echo "1. Criar um chamado"
echo "2. Verificar os logs do backend"
echo ""

# Criar chamado
echo "Criando chamado..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/chamados \
  -H "Content-Type: application/json" \
  -d '{
    "sessaoMesaId": 22,
    "tipo": "garcom"
  }')

echo "Resposta da API: $RESPONSE"
echo ""
echo "Agora verifique os logs do backend. Deve aparecer:"
echo "  1. '=== Publicando chamado.novo para KDS ==='"
echo "  2. 'Publishing KDS event type=chamado.novo to X emitters'"
echo "  3. Se X = 0, significa que não há KDS conectado"
echo "  4. Se X > 0, o evento foi enviado com sucesso"
echo ""
echo "Logs de heartbeat devem aparecer a cada 15 segundos:"
echo "  'Heartbeat sent. Mesa emitters: X, KDS emitters: Y'"
