# Estrutura Editorial Alvo

Este documento define a estrutura editorial alvo de `docs/modules`, derivada do inventario validado em [MAPEAMENTO_MODULOS.md](./MAPEAMENTO_MODULOS.md).

O objetivo e transformar a pasta em uma representacao mais fiel dos dominios reais do sistema, reduzindo a mistura atual entre:
- modulos reais
- areas editoriais agregadoras
- submodulos
- capacidades isoladas
- iniciativas documentais

## Principios

- `modules` deve refletir **dominios funcionais reais**
- subdominios relevantes devem aparecer como **submodulos editoriais**
- capacidades pontuais nao devem competir com modulos reais no mesmo nivel
- o nome da pasta deve comunicar o dominio, nao apenas a origem da tela ou do time
- documentos existentes devem ser reaproveitados sempre que possivel, mas reposicionados quando estiverem editorialmente mal encaixados

## Estrutura Alvo Proposta

```text
modules/
├── produtos/
│   ├── README.md
│   ├── ficha-tecnica/
│   ├── skus/
│   ├── promocoes/
│   ├── harmonizacao/
│   ├── disponibilidade/
│   ├── importacao/
│   └── signage/
│
├── estoque/
│   ├── README.md
│   ├── validade/
│   ├── movimentacao/
│   └── estrutura/
│
├── suprimentos/
│   ├── README.md
│   ├── pedidos-de-compra/
│   ├── recebimento-de-mercadoria/
│   └── fornecedores/
│
├── producao/
│   └── README.md
│
├── financeiro/
│   ├── README.md
│   ├── contas-a-pagar/
│   ├── contas-a-receber/
│   ├── caixa/
│   ├── relatorios/
│   └── voucher-excedente/
│
├── vendas/
│   ├── README.md
│   ├── pedidos/
│   ├── mesas/
│   ├── conta/
│   └── pagamento/
│
├── clientes/
│   ├── README.md
│   ├── perfis/
│   ├── grupos/
│   ├── descontos/
│   └── analytics/
│
├── fidelizacao/
│   ├── README.md
│   ├── gamificacao/
│   ├── recompensas/
│   ├── resgates/
│   └── beneficios/
│
├── eventos/
│   └── README.md
│
├── quiz/
│   └── README.md
│
├── temas/
│   └── README.md
│
├── consumo-digital/
│   ├── README.md
│   ├── mesa-digital/
│   ├── qr-ordering/
│   ├── delivery/
│   ├── kds/
│   ├── waiter/
│   ├── pagamentos/
│   └── conta-digital/
│
├── dashboard/
│   ├── README.md
│   ├── escopo.md
│   ├── implementacao-atual.md
│   ├── aderencia.md
│   ├── relatorios/
│   │   └── escopo-relatorio-vendas.md
│   └── paineis/
│       └── painel-eventos-dashboard.md
│
└── funcionalidades/
    └── README.md
```

## Leitura da Estrutura

### Modulos editoriais de primeiro nivel

Os modulos de primeiro nivel recomendados sao:
- `produtos`
- `estoque`
- `suprimentos`
- `producao`
- `financeiro`
- `vendas`
- `clientes`
- `fidelizacao`
- `eventos`
- `quiz`
- `temas`
- `consumo-digital`
- `dashboard`

### Papel de `funcionalidades`

`funcionalidades/` deve permanecer apenas como agrupador residual para capacidades pequenas, temporarias ou ainda nao promovidas a submodulo.

Nao deve concorrer editorialmente com modulos reais.

## Reaproveitamento da Estrutura Atual

### Ja realizados

| Atual | Alvo | Status |
|------|------|--------|
| `gamificacao/` | `fidelizacao/gamificacao/` | ✅ Concluido |
| `backoffice/MODULO_PEDIDOS_DE_COMPRA.md` | `suprimentos/pedidos-compra/detalhamento.md` | ✅ Concluido |
| `backoffice/signage/*` | `produtos/signage/` | ✅ Ja existia |
| `consumo-digital-e-fidelizacao/*` | `consumo-digital/qr-ordering/` | ✅ Concluido e rebaixado a referencia historica |

### Pendentes

| Atual | Alvo | Status |
|------|------|--------|
| `financeiro/escopo_voucher_excedente.md` | `financeiro/voucher-excedente/` | ⏳ Pendente |
| `funcionalidades/site_service_mode.md` | `consumo-digital/waiter/` | ⏳ Pendente |

### Ja realizados (dashboard)

| Atual | Alvo | Status |
|------|------|--------|
| `dashboard/escopo-relatorio-vendas.md` | `dashboard/relatorios/` | ✅ Concluido |
| `dashboard/painel-eventos-dashboard.md` | `dashboard/paineis/` | ✅ Concluido |

## Decisoes Editoriais Relevantes

### 1. Vendas como modulo proprio

Motivo:
- dominio real com submodulos claros (pedidos, mesas, conta, pagamento)
- ja existe implementacao completa
- nao deve ser absorvido por outro modulo

### 2. Produtos vira modulo editorial proprio

Motivo:
- e um dos maiores dominios reais do sistema
- hoje esta diluido entre varios documentos e pastas
- comporta varios submodulos claros

### 4. Suprimentos separa compra de estoque

Motivo:
- pedidos de compra e recebimento de mercadoria existem como cadeia operacional propria
- embora conectados a estoque, representam outro recorte de negocio

### 5. Fidelizacao nao fica reduzida a "gamificacao"

Motivo:
- o dominio real inclui recompensas, resgates, beneficios e relacionamento
- `gamificacao` pode ser mantido como subrecorte importante, nao como nome totalizante

### 6. Consumo digital substitui o nome historico excessivamente agregador

Motivo:
- `consumo-digital-e-fidelizacao` mistura dois dominios
- `consumo-digital` e nome mais limpo para mesa, QR, delivery, KDS, waiter e conta digital
- `fidelizacao` passou a existir como modulo irmao, encerrando a validade editorial do nome combinado

### 7. Dashboard permanece como modulo editorial de apoio

Motivo:
- ha artefatos de relatorios e paineis suficientes para justificar area propria
- mas ele nao deve absorver dominios de negocio que pertencem a `financeiro`, `eventos` ou outros modulos

## Prioridade de Consolidacao

Ordem recomendada para a Etapa 2:

1. `produtos`
2. `estoque`
3. `suprimentos`
4. `financeiro`
5. `vendas`
6. `consumo-digital`
7. `clientes`
8. `fidelizacao`
9. `eventos`
10. `quiz`
11. `temas`
12. `dashboard`

## Criterio de Aprovacao

Esta estrutura sera considerada aprovada quando houver concordancia explicita sobre:
- os modulos editoriais de primeiro nivel
- os submodulos editoriais mais importantes
- o reposicionamento dos documentos ja existentes

Depois dessa aprovacao, o proximo passo oficial sera consolidar a especificacao do primeiro modulo escolhido.
