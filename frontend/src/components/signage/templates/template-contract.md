# Template Contract (Templates 01-04)

Este documento descreve o “contrato” entre o backend de templates e o frontend de signage, listando o endpoint envolvido, o mapeamento de cores e quais chaves cada template aceita em `resolvedColors`.

## 1. Endpoint de Elementos

- **GET** `/api/signage/templates/:id/elements`
- Retorna os elementos configuráveis por template e suas descrições.
  ```json
  {
    "templateId": "promo-hero",
    "elements": [
      { "key": "background", "label": "Cor de fundo", "description": "Cor principal do background", "defaultSource": "palette:background" },
      { "key": "headline", "label": "Cor do título", "description": "Cor do texto principal", "defaultSource": "palette:text" },
      { "key": "subtitle", "label": "Cor do subtítulo", "description": "Cor do texto secundário", "defaultSource": "palette:accent2" },
      { "key": "price", "label": "Cor do preço", "description": "Cor de destaque do valor", "defaultSource": "palette:vibrant" },
      { "key": "badge", "label": "Cor do selo", "description": "Cor do badge/destaque", "defaultSource": "palette:accent" },
      { "key": "separator", "label": "Cor do separador", "description": "Cor de linhas e divisores", "defaultSource": "palette:muted" }
    ]
  }
  ```

O frontend consome `element.key` e, quando o usuário mapeia cores diferentes do template, replica o valor em `resolvedColors[element.key]` (via `resolveColors`). Ou seja:

- `backend.elements[].key` → `frontend.resolveColors()[key]`
- Qualquer valor `palette:<token>` ou `custom:<hex>` enviado pelo usuário é convertido para o valor final que o template recebe.

## 2. Prioridade de Cores (aplicação)

1. `resolvedColors` preenchido → prioridade máxima (os templates aplicam esses valores diretamente em CSS e elementos).
2. `palette` + classes de tema → fallback automático (só acionado quando `resolvedColors` está vazio).
3. Defaults internos do template → usados quando nem `resolvedColors` nem `palette` possuem a cor solicitada.

## 3. Chaves suportadas em `resolvedColors`

- `background`: aplicado em `--bg-main`, `--bg-secondary`, `--bg-light`, variantes de fundo e em elementos de wrapper.
- `headline` / `text`: escreve em `--text-main`, afetando títulos e textos principais.
- `subtitle`: escreve em `--text-soft`, usado em subtítulos ou textos menores.
- `price`: cor inline para elementos de preço (`#price`, `#priceMain`, `#priceCents`).
- `badge`: sincroniza `--brand-primary`, `--accent`, `--accent-bright` e elementos de selo.
- `separator`: alimenta `--separator`, `--line` e variantes usadas em divisores/linhas.

## 4. Suporte por template

### Template 01 – Hero Promo (`template-01-hero-promo`)
- **Cores aplicadas:** `background`, `headline/text`, `subtitle`, `price`, `badge`, `separator`.
- `background` atualiza `--bg-main` e `--bg-secondary`.
- `headline/text` escreve `--text-main` e é usado por `#headline`.
- `subtitle` controla `--text-soft` aplicado em `#subtitle`.
- `price` pinta `#price` inline.
- `badge` abastece `--brand-primary` e `--accent` para o selo (`#badge`).
- `separator` define `--separator`, com fallback em linhas internas.

### Template 02 – Split View (`template-02-split-view`)
- **Cores aplicadas:** `background`, `headline/text`, `subtitle`, `price`, `badge`, `separator`.
- `price` cobre `#priceMain` e `#priceCents` (cores sincronizadas).
- `badge` alimenta `--brand-primary`, `--accent`, e `--accent-soft` presentes nas seções laterais.
- `separator` também atualiza `--line` para as divisórias do layout.

### Template 03 – New Release (`template-03-new-release`)
- **Cores aplicadas:** `background`, `headline/text`, `subtitle`, `price`, `badge`, `separator`.
- `separator` e `badge` seguem a mesma abordagem de CSS customizado (também atualiza `--accent-bright`).

### Template 04 – Minimalist Zen (`template-04-minimalist-zen`)
- **Cores aplicadas:** `background`, `headline/text`, `subtitle`, `price`, `badge`, `separator`.
- `background` ajusta múltiplas variáveis (`--bg-main`, `--bg-secondary`, `--bg-light`, `--bg-subtle`).
- `subtitle` estiliza o texto animado (`#subtitleText`).
- `price` colore `#priceMain` e `#priceCents`.
- `separator` define `--separator` e a variável auxiliar `--separator-rgb` usada em efeitos sintéticos.

## 5. Observações adicionais

- Cada template documentado acima mantém a mesma lista de chaves para facilitar o mapeamento de backend → `resolvedColors`.
- Elementos adicionais (`palette` tokens, `phrases`, `image`) permanecem inalterados; apenas o objeto `resolvedColors` determina as sobreposições de cor.
- Em previews ou rascunhos, o frontend decide se usa `resolvedColors` customizado (quando `useCustomMapping` é true) ou os valores persistidos em `base.resolvedColors`.

## 6. Fallbacks robustos

### Cores
1. `resolvedColors[elementKey]` (presente → prioridade máxima). Os templates leem esse objeto e aplicam o valor diretamente nos CSS vars/elementos envolvidos.
2. `palette[paletteKey]` (mapeamento semântico por elementos: `background`, `text`, `subtitle`, `price`, `badge`, `separator`). Cada template escreve os tokens de paleta (`--token-*`) e usa `palette` como fonte quando `resolvedColors` não preenche a chave.
3. Valor padrão embutido no template (capturado via `window.getComputedStyle` durante a primeira renderização).
4. Cor neutra segura (`#000000` para fundos/separadores e `#ffffff` para textos/destaques) garantindo legibilidade quando nenhuma fonte anterior estiver disponível.

### Imagens
1. `data.image` (payload real enviado do backend, inclui URL absoluta ou relativa).
2. `mockImage` (placeholder embutido em `shared.mockImage`) usado quando `data.image` está ausente ou falha ao carregar.
3. Esconder o elemento (`opacity: 0` + remoção do `src`) quando nem `data.image` nem `mockImage` conseguem carregar para evitar “broken image”.

Todos os templates aplicam essas regras no mesmo lugar do `srcdoc`, garantindo comportamento previsível mesmo em casos de falhas no backend.
