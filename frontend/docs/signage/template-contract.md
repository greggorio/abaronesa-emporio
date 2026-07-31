# Contrato de Templates Signage

**Versão:** 1.0  
**Data:** 2026-02-08  
**Status:** Ativo

---

## 1. Visão Geral

Este documento define o contrato técnico entre o sistema de customização de templates signage e os templates individuais. Estabelece as chaves suportadas, mapeamentos semânticos, estrutura de dados e comportamento de fallback para garantir consistência visual em todos os templates.

### 1.1 Arquitetura

- **Formato:** Templates exportam `srcdoc` (HTML/CSS/JS inline)
- **Injeção de dados:** Via `window.__SIGNAGE_PREVIEW__` + evento `__SIGNAGE_PREVIEW_READY__`
- **Customização:** `SignageColorMapper.vue` captura intenção via `elementMappings`
- **Resolução:** Frontend converte `elementMappings` em `resolvedColors` antes de injetar no template

---

## 2. Estrutura de Dados

### 2.1 Payload do Template

```typescript
interface SignagePreviewPayload {
  badge?: string;
  headline?: string;
  subtitle?: string;
  cta?: string;
  price?: string;
  image?: string;
  palette: Palette;
  resolvedColors?: ResolvedColors;
}

interface Palette {
  vibrant: string;
  muted: string;
  lightVibrant: string;
  darkVibrant: string;
  lightMuted: string;
  darkMuted: string;
  isDark?: boolean;
}

interface ResolvedColors {
  background?: string;
  text?: string;
  headline?: string;
  subtitle?: string;
  price?: string;
  badge?: string;
  separator?: string;
}
```

### 2.2 Estrutura elementMappings

O `elementMappings` é a representação da intenção do usuário antes da resolução:

```typescript
interface ElementMappings {
  background: "auto" | "palette:background" | "custom:#HEX";
  headline: "auto" | "palette:text" | "custom:#HEX";
  subtitle: "auto" | "palette:accent2" | "custom:#HEX";
  price: "auto" | "palette:vibrant" | "custom:#HEX";
  badge: "auto" | "palette:accent" | "custom:#HEX";
  separator: "auto" | "palette:muted" | "custom:#HEX";
}
```

**Regra:** `auto` significa omitir a chave no `resolvedColors` (deixa o template usar seu próprio default).

---

## 3. Chaves de resolvedColors Suportadas

### 3.1 Mapeamento por Chave Semântica

| Chave        | Descrição                 | CSS Vars (Default)            | Comportamento                                  |
| ------------ | ------------------------- | ----------------------------- | ---------------------------------------------- |
| `background` | Cor de fundo principal    | `--bg-main`, `--bg-secondary` | Aplica cor sólida ou derived via `color-mix()` |
| `text`       | Cor do texto principal    | `--text-main`                 | Fallback: aceita também `headline`             |
| `headline`   | Cor do título             | `--text-main`                 | Alias de `text`                                |
| `subtitle`   | Cor do texto secundário   | `--text-soft`                 | Texto de menor destaque                        |
| `price`      | Cor do elemento de preço  | Aplicação direta no DOM       | Pode ter IDs diferentes por template           |
| `badge`      | Cor do selo/destaque      | `--brand-primary`             | Elementos de destaque visual                   |
| `separator`  | Cor de linhas e divisores | `--separator`                 | Borders, lines, divisórias                     |

### 3.2 Mapeamento por Template

#### Template 01 - Hero Promo

```javascript
const colorConfig = {
  background: { cssVars: ["--bg-main", "--bg-secondary"] },
  text: { cssVars: "--text-main" },
  subtitle: { cssVars: "--text-soft" },
  badge: { cssVars: "--brand-primary" },
  separator: { cssVars: "--separator" },
  price: {
    /* aplicação direta */
  },
};
```

**IDs de Preço:** `id="price"` (único elemento)

#### Template 02 - Editorial Overlay Enhanced

```javascript
const colorConfig = {
  background: { cssVars: ["--bg-main", "--bg-secondary"] },
  text: { cssVars: "--text-main" },
  subtitle: { cssVars: "--text-soft" },
  badge: { cssVars: ["--brand-primary", "--accent", "--accent-soft"] },
  separator: { cssVars: ["--separator", "--line"] },
  price: {
    /* aplicação direta */
  },
};
```

**IDs de Preço:** `id="priceMain"` + `id="priceCents"` (elementos separados)

#### Template 03 - New Release

```javascript
const colorConfig = {
  background: { cssVars: ["--bg-main", "--bg-secondary"] },
  text: { cssVars: "--text-main" },
  subtitle: { cssVars: "--text-soft" },
  badge: { cssVars: ["--brand-primary", "--accent", "--accent-bright"] },
  separator: { cssVars: "--separator" },
  price: {
    /* aplicação direta */
  },
};
```

**IDs de Preço:** `id="priceMain"` + `id="priceCents"` (elementos separados)

#### Template 04 - Clean Elegance

```javascript
const colorConfig = {
  background: { cssVars: ["--bg-main", "--bg-secondary"] },
  text: { cssVars: "--text-main" },
  subtitle: { cssVars: "--text-soft" },
  badge: { cssVars: ["--brand-primary"] },
  separator: { cssVars: "--separator" },
  price: {
    /* aplicação direta */
  },
};
```

**IDs de Preço:** `id="priceMain"` + `id="priceCents"` (elementos separados)

---

## 4. Mapeamento Semântico de Cores

### 4.1 Paleta Base → Cores do Template

Cada template define tokens CSS que derivam da paleta:

```css
:root {
  --token-vibrant: var(--palette.vibrant);
  --token-muted: var(--palette.muted);
  --token-light-vibrant: var(--palette.lightVibrant);
  --token-dark-vibrant: var(--palette.darkVibrant);
  --token-light-muted: var(--palette.lightMuted);
  --token-dark-muted: var(--palette.darkMuted);
}
```

### 4.2 Mapeamento Padrão

| Chave resolvedColors | Palette Keys (Fallback) | CSS Vars Resultantes |
| -------------------- | ----------------------- | -------------------- |
| `background`         | `background`            | `--bg-main`          |
| `text` / `headline`  | `text`                  | `--text-main`        |
| `subtitle`           | `accent2`               | `--text-soft`        |
| `price`              | `vibrant`               | Aplicação direta     |
| `badge`              | `accent`                | `--brand-primary`    |
| `separator`          | `muted`                 | `--separator`        |

---

## 5. Estrutura de colorConfig

Cada template implementa um `colorConfig` que define como resolver e aplicar cores:

```javascript
const colorConfig = {
  background: {
    resolvedKeys: ["background"], // Chaves em resolvedColors
    paletteKeys: ["background"], // Chaves em palette
    cacheKey: "background", // Chave para caching de defaults
    safe: "#000000", // Fallback final
    cssVars: ["--bg-main", "--bg-secondary"], // Variáveis CSS a aplicar
  },
  text: {
    resolvedKeys: ["text", "headline"], // Suporta múltiplas chaves
    paletteKeys: ["text"],
    cacheKey: "text",
    safe: "#ffffff",
    cssVars: "--text-main",
  },
  // ... demais chaves
};
```

---

## 6. Comportamento de Fallback

### 6.1 Sequência de Resolução

A função `pickColorValue()` implementa a seguinte ordem de fallback:

```
1. resolvedColors[resolvedKeys[0..n]]
   └─ Se encontrado e não-vazio → usa este valor

2. palette[paletteKeys[0..n]]
   └─ Se encontrado e não-vazio → usa este valor

3. defaults[cacheKey]
   └─ Se capturado anteriormente → usa valor computado do template

4. safe
   └─ Cor neutra de segurança (#000000 ou #ffffff)
```

### 6.2 Diagrama de Fallback

```
┌─────────────────────────────────────┐
│     window.__SIGNAGE_PREVIEW__      │
│         resolvedColors              │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   resolvedColors[key] existe?       │
│   (resolvedKeys = ['key1', 'key2']) │
└──────────────┬──────────────────────┘
      Sim ─────┤
               │ Não
               ▼
┌─────────────────────────────────────┐
│      palette[key] existe?           │
│    (paletteKeys = ['key1', ...])    │
└──────────────┬──────────────────────┘
      Sim ─────┤
               │ Não
               ▼
┌─────────────────────────────────────┐
│  defaults[cacheKey] foi capturado?  │
└──────────────┬──────────────────────┘
      Sim ─────┤
               │ Não
               ▼
┌─────────────────────────────────────┐
│        config.safe                  │
│   (#000000 ou #ffffff)              │
└─────────────────────────────────────┘
```

### 6.3 Exemplos de Fallback

```javascript
// Caso 1: resolvedColors populado
resolvedColors = { badge: '#ff0000' }
palette = { accent: '#00ff00' }
→ Aplica '#ff0000'

// Caso 2: resolvedColors.vazio, palette preenchido
resolvedColors = {}
palette = { accent: '#00ff00' }
→ Aplica '#00ff00'

// Caso 3: ambos vazios, default capturado
resolvedColors = {}
palette = {}
defaults.badge = 'rgb(128, 128, 128)'
→ Aplica 'rgb(128, 128, 128)'

// Caso 4: nada disponível
resolvedColors = {}
palette = {}
defaults = {}
→ Aplica safe = '#ffffff'
```

---

## 7. Comportamento de Tema (Light/Dark)

### 7.1 Regra de Prioridade

**IMPORTANTE:** Quando `resolvedColors` tem qualquer chave preenchida, **não** se aplica tema via classe.

```javascript
const hasResolved = Object.keys(resolvedColors).length > 0;

root.classList.remove("is-dark", "is-light");

if (!hasResolved) {
  if (palette.isDark === true) {
    root.classList.add("is-dark");
  } else if (palette.isDark === false) {
    root.classList.add("is-light");
  }
}
```

### 7.2 Matriz de Tema

| palette.isDark   | resolvedColors | Resultado                             |
| ---------------- | -------------- | ------------------------------------- |
| `true`           | vazio          | `.is-dark` aplicado                   |
| `false`          | vazio          | `.is-light` aplicado                  |
| `null/undefined` | vazio          | Sem classe (usa defaults do template) |
| qualquer         | não-vazio      | Ignorado (cores resolvidas dominam)   |

---

## 8. Contrato Backend → Frontend

### 8.1 Endpoint

```
GET /api/signage/templates/:id/elements
```

### 8.2 Resposta Esperada

```json
{
  "templateId": "promo-hero",
  "elements": [
    {
      "key": "background",
      "label": "Cor de fundo",
      "description": "Cor principal do background",
      "defaultSource": "palette:background"
    },
    {
      "key": "headline",
      "label": "Cor do título",
      "description": "Cor do texto principal",
      "defaultSource": "palette:text"
    },
    {
      "key": "subtitle",
      "label": "Cor do subtítulo",
      "description": "Cor do texto secundário",
      "defaultSource": "palette:accent2"
    },
    {
      "key": "price",
      "label": "Cor do preço",
      "description": "Cor de destaque do valor",
      "defaultSource": "palette:vibrant"
    },
    {
      "key": "badge",
      "label": "Cor do selo",
      "description": "Cor do badge/destaque",
      "defaultSource": "palette:accent"
    },
    {
      "key": "separator",
      "label": "Cor do separador",
      "description": "Cor de linhas e divisores",
      "defaultSource": "palette:muted"
    }
  ]
}
```

---

## 9. Mapeamento de IDs de Preço por Template

### 9.1 Tabela de Referência

| Template               | ID Principal | ID Centavos  | Observação       |
| ---------------------- | ------------ | ------------ | ---------------- |
| 01 - Hero Promo        | `price`      | N/A          | Preço único      |
| 02 - Editorial Overlay | `priceMain`  | `priceCents` | Partes separadas |
| 03 - New Release       | `priceMain`  | `priceCents` | Partes separadas |
| 04 - Clean Elegance    | `priceMain`  | `priceCents` | Partes separadas |

### 9.2 Normalização de Preço

Todos os templates implementam `normalizePrice()` com suporte a duplicação controlada:

```javascript
function normalizePrice(value) {
  if (value == null) return "";
  const str = String(value).trim();
  if (!str) return "";
  return str.replace(/^R\$\s*/i, "").replace(/^\$\s*/, "");
}
```

---

## 10. Casos Edge e Tratamento

### 10.1 Cores Ausentes

```javascript
// Se resolvedColors.subtitle = '' (string vazia)
→ Trata como não existente
→ Vai para fallback de palette
```

### 10.2 Imagem Quebrada

```javascript
applyImageWithFallback(imageElement, source) {
  // 1. Tenta source primário
  // 2. Se erro e fallback existe → usa mockImage
  // 3. Se nada → hide (opacity: 0)
}
```

### 10.3 Formato HEX Inválido

```javascript
// adjustColor() sanitiza e retorna original se inválido
if (!/^#([0-9a-fA-F]{6})$/.test(sanitized)) return hex;
```

---

## 11. Histórico de Versões

| Versão | Data       | Alterações                                            |
| ------ | ---------- | ----------------------------------------------------- |
| 1.0    | 2026-02-08 | Versão inicial baseada na análise dos templates 01-04 |

---

## 12. Referências

- **Escopo do Projeto:** `.ai-workflow/plans/plan-customizacão-de-templates-20260207174303/escopo-original.md`
- **Template 01:** `src/components/signage/templates/template-01-hero-promo.js`
- **Template 02:** `src/components/signage/templates/template-02-split-view.js`
- **Template 03:** `src/components/signage/templates/template-03-new-release.js`
- **Template 04:** `src/components/signage/templates/template-04-minimalist-zen.js`
- **Shared:** `src/components/signage/templates/shared.js`
