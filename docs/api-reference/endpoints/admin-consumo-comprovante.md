# API de Comprovante de Consumo

Documentação para a API de geração de comprovantes de consumo do sistema.

## Endpoint

### Gerar Comprovante de Consumo

**POST** `/api/admin/consumo/comprovante`

Gera um comprovante de consumo em formato PDF com base nos dados fornecidos.

#### Headers

- `Authorization: Bearer {token}` - Token de autenticação JWT obtido no login
- `Content-Type: application/json` - Tipo de conteúdo da requisição
- `Accept: application/pdf` - Tipo de conteúdo esperado na resposta

#### Request Body

| Campo | Tipo | Obrigatório | Descrição | Validação |
|-------|------|-------------|-----------|-----------|
| `valorTotal` | number | Sim | Valor total do consumo | Deve ser maior ou igual a zero |
| `nomeCliente` | string | Não | Nome do cliente | Máximo de 100 caracteres |

#### Exemplos de Requisição

**Com nome do cliente:**
```json
{
  "valorTotal": 50.0,
  "nomeCliente": "João Silva"
}
```

**Sem nome do cliente:**
```json
{
  "valorTotal": 75.5
}
```

#### Resposta

- **Status Code:** 200 OK
- **Content-Type:** application/pdf
- **Content-Disposition:** inline; filename=comprovante_consumo_{id}.pdf

Retorna um arquivo PDF contendo o comprovante de consumo com:
- Informações da empresa (razão social, endereço, CNPJ)
- Data e hora da emissão
- Nome do cliente (se fornecido)
- Frase "Consumo" e o valor total
- Mensagem "OBRIGADO E VOLTE SEMPRE!"

#### Exemplo de Código Frontend (JavaScript)

```javascript
async function gerarComprovanteConsumo(valorTotal, nomeCliente = null) {
  const token = localStorage.getItem('accessToken');

  const response = await fetch('http://localhost:8080/api/admin/consumo/comprovante', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Accept': 'application/pdf'
    },
    body: JSON.stringify({
      valorTotal: valorTotal,
      nomeCliente: nomeCliente
    })
  });

  if (!response.ok) {
    const errorData = await response.json();
    throw new Error(errorData.error?.message || 'Erro ao gerar comprovante');
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  window.open(url, '_blank');
}
```

#### Tratamento de Erros

- **400 Bad Request** - Campos inválidos
- **401 Unauthorized** - Token inválido ou expirado
- **500 Internal Server Error** - Erro interno no servidor

#### Observações

- O campo `nomeCliente` é opcional e pode ser omitido da requisição
- O PDF gerado tem formato 80mm com layout otimizado para impressoras térmicas
- O nome do arquivo gerado inclui um identificador único para evitar conflitos
- O endpoint requer autenticação com permissões de administrador
