# Resgates — Especificação do Domínio

## Definição

Processo de troca de pontos por recompensas. Governa a solicitação de resgate: validação da recompensa, verificação de saldo, débito de pontos, controle de estoque e registro da transação.

## Escopo

**Inclui:**
- Resgate de recompensa por cliente (via admin)
- Validação da recompensa: ativa, dentro da validade, com estoque disponível
- Verificação de saldo suficiente do cliente
- Débito de pontos (criação de MovimentoPontos tipo RESGATE)
- Decremento de estoque da recompensa
- Registro completo da transação com saldo anterior e posterior

**Não inclui:**
- Saldo de pontos (pertence a `gamificacao/`)
- Catálogo de recompensas (pertence a `recompensas/`)
- Resgate por autoatendimento do cliente (apenas admin)

## Modelo de dados

O resgate não possui tabela própria. A transação é registrada como um `MovimentoPontos` (tabela `movimentos_pontos`) com:

| Campo | Valor no resgate |
|-------|-----------------|
| `tipo` | `RESGATE` |
| `origem` | `RECOMPENSA` |
| `referencia_tipo` | `RECOMPENSA` |
| `referencia_id` | ID da recompensa resgatada |
| `pontos` | Valor negativo: `-pontosNecessarios` |
| `saldo_apos` | `saldoAnterior - pontosNecessarios` |
| `observacao` | `"Resgate da recompensa: {nome} - {observacao}"` |

O estoque da recompensa é decrementado no mesmo evento (`recompensa.estoque -= 1`).

### DTOs

**ResgateRecompensaRequest:**
| Campo | Tipo | Descrição |
|-------|------|-----------|
| `clienteId` | Long | ID do cliente |
| `recompensaId` | Long | ID da recompensa |
| `observacao` | String | Opcional — justificativa do admin |

**ResgateRecompensaResponse:**
| Campo | Tipo | Descrição |
|-------|------|-----------|
| `resgateId` | Long | ID do MovimentoPontos criado |
| `clienteId` | Long | ID do cliente |
| `recompensaId` | Long | ID da recompensa |
| `pontosDebitados` | Integer | Quantidade de pontos debitados |
| `saldoAnterior` | Integer | Saldo antes do resgate |
| `saldoApos` | Integer | Saldo após o resgate |
| `dataHora` | LocalDateTime | Momento do resgate |

## Fluxo de resgate

```
1. Admin envia POST /api/admin/gamificacao/resgates
   Body: { clienteId, recompensaId, observacao? }

2. Valida recompensa:
   ├── Recompensa existe?
   ├── ativo = true?
   ├── Dentro da validade (inicio/fim)?
   └── Estoque > 0 (ou null = ilimitado)?

3. Valida cliente:
   └── Cliente existe?

4. Verifica saldo:
   ├── saldoCliente = getSaldoCliente(clienteId)
   └── saldoCliente >= recompensa.pontosNecessarios?

5. Executa resgate (transação):
   ├── Cria MovimentoPontos (tipo=RESGATE, origem=RECOMPENSA,
   │      pontos=-pontosNecessarios, saldoApos=saldoAnterior - pontos)
   └── Se estoque != null: recompensa.estoque -= 1

6. Retorna ResgateRecompensaResponse
```

## Endpoints

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `POST` | `/api/admin/gamificacao/resgates` | ADMIN/SYSTEM | Executar resgate |

Body:
```json
{
  "clienteId": 1,
  "recompensaId": 5,
  "observacao": "Resgate solicitado pelo admin"
}
```

Response:
```json
{
  "resgateId": 100,
  "clienteId": 1,
  "recompensaId": 5,
  "pontosDebitados": 50,
  "saldoAnterior": 150,
  "saldoApos": 100,
  "dataHora": "2025-12-01T15:00:00"
}
```

## Regras

### Validação da recompensa

A recompensa é validada em `ResgateRecompensaService.validarRecompensa()`:

1. Deve existir no banco
2. `ativo = true` — recompensas inativas não podem ser resgatadas
3. Dentro da janela de validade:
   - Se `validadeInicio` existe: resgate deve ser após `validadeInicio` (inclusive)
   - Se `validadeFim` existe: resgate deve ser até `validadeFim` 23:59:59 (inclusive)
4. Estoque disponível: se `estoque` não for nulo, deve ser > 0

### Validação de saldo

- O saldo do cliente (`GamificacaoService.getSaldoCliente`) deve ser >= `pontosNecessarios` da recompensa
- Se insuficiente, retorna `BusinessException("Saldo insuficiente")`

### Atomicidade

O resgate é executado dentro de uma transação (`@Transactional`):
- Criação do `MovimentoPontos` com tipo RESGATE
- Decremento do estoque da recompensa (se controlado)
- Se qualquer etapa falha, toda a operação é revertida

## Serviços

### ResgateRecompensaService

| Método | Descrição |
|--------|-----------|
| `resgatarRecompensa(ResgateRecompensaRequest)` | Valida recompensa, cliente e saldo; debita pontos; atualiza estoque; retorna resposta |
| `validarRecompensa(Recompensa)` | Valida ativo, validade e estoque da recompensa |

Dependências:
- `RecompensaRepository` — busca e persistência da recompensa
- `UsuarioRepository` — busca do cliente
- `MovimentoPontosRepository` — persistência do movimento de RESGATE
- `GamificacaoService.getSaldoCliente()` — consulta de saldo

## Frontend

### Admin (espresso_front - React/TypeScript)

| Componente | Descrição |
|-----------|-----------|
| `GamificacaoAdmin.tsx` | Seletor de cliente + lista de recompensas elegíveis + botão de resgate |

O fluxo no admin:
1. Admin seleciona um cliente (autocomplete com clientes que têm pontos)
2. Sistema carrega as recompensas elegíveis para aquele cliente
3. Admin clica em "Resgatar" em uma recompensa
4. Confirmação: sistema debita pontos e registra o resgate

### Admin (espresso_front - React/TypeScript)

| Componente | Rota | Descrição |
|-----------|------|-----------|
| `RewardsAdminPanel.tsx` | `/admin/rewards` | Painel de recompensas do espresso_back (subsistema separado) |

## Integrações

| Módulo | Natureza |
|--------|----------|
| `gamificacao/` | Leitura: consulta saldo do cliente antes do resgate |
| `gamificacao/` | Escrita: cria MovimentoPontos tipo RESGATE com origem RECOMPENSA |
| `recompensas/` | Leitura: dados da recompensa (nome, pontosNecessarios, estoque) |
| `recompensas/` | Escrita: decrementa estoque da recompensa |

## Decisões de domínio

- **Resgate admin-only** — o cliente não possui endpoint direto de resgate. A solicitação é feita exclusivamente pelo admin, que valida a solicitação do cliente presencialmente.
- **Sem tabela própria de resgate** — o resgate é um `MovimentoPontos` com tipo `RESGATE`. Não há entidade separada; o histórico completo está na mesma tabela de auditoria.
- **Pontos negativos como débito** — o campo `pontos` no `MovimentoPontos` recebe valor negativo (`-pontosNecessarios`), permitindo que o somatório direto dos movimentos de um cliente reflita o saldo.
- **Estoque decrementado no resgate** — cada resgate consome uma unidade de estoque. Recompensas com `estoque = null` (ilimitadas) não são decrementadas.
- **BusinessException para erros de negócio** — saldo insuficiente, recompensa inativa, recompensa esgotada e recompensa fora da validade retornam HTTP 400 com mensagem descritiva.
- **Snapshot do saldo** — o `saldoApos` no movimento de RESGATE congela o saldo após o débito, garantindo rastreabilidade mesmo que expiração ou ajustes futuros alterem o saldo corrente.
- **Reward do espresso_back é separado** — o `espresso_back` possui seu próprio fluxo de resgate (`POST /api/rewards/{id}/redeem`) para o subsistema `Reward`, que não se confunde com o sistema de pontos de fidelidade do bakery.

## Status de implementação

**EM_DESENVOLVIMENTO**. O fluxo de resgate via admin está implementado: validação de recompensa, verificação de saldo, débito de pontos e decremento de estoque. Não há endpoint de auto-resgate para o cliente.
