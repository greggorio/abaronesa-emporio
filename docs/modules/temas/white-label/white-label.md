# White Label — Especificação

## Entidade

### Theme (`Theme.java`, espresso_back)

Tabela `theme`.  Ver `temas.md` para detalhes completos dos campos.

**Campos principais**:
- `id` (Long, PK)
- `name` (String, obrigatório, max 100)
- `baseThemeId` (Long, nullable — tema original na duplicação)
- `status` (`ThemeStatus`: DRAFT, PUBLISHED, ARCHIVED)
- `tokens` (Map<String,String>, JSONB)
- `assets` (Map<String,Object>, JSONB)
- `content` (Map<String,Object>, JSONB)
- `tenantId` (String, obrigatório)
- `createdAt`, `updatedAt` (LocalDateTime)

### ThemeStatus (enum)

| Valor | Descrição |
|-------|-----------|
| `DRAFT` | Rascunho — em edição, não visível |
| `PUBLISHED` | Publicado — disponível para ativação |
| `ARCHIVED` | Arquivado — não pode ser ativado |

## Design Tokens (coluna `tokens`)

Armazenados em HSL (Hue Saturation Lightness) para permitir manipulação programática de cores. A principal vantagem do HSL sobre HEX/RGB é que o Tailwind CSS e o shadcn/ui usam HSL como formato nativo.

### Tokens obrigatórios

| Token | Exemplo | Uso |
|-------|---------|-----|
| `background` | `0 0% 100%` | Fundo da página |
| `foreground` | `0 0% 3.9%` | Texto principal |
| `primary` | `15 55% 68%` | Cor primária (botões, links) |
| `primary-foreground` | `0 0% 100%` | Texto sobre primary |
| `secondary` | `0 0% 100%` | Cor secundária |
| `secondary-foreground` | `15 55% 68%` | Texto sobre secondary |
| `accent` | `19 23% 94%` | Cor de destaque |
| `accent-foreground` | `15 55% 68%` | Texto sobre accent |
| `muted` | `19 23% 94%` | Fundo de elementos muted |
| `muted-foreground` | `0 0% 45.1%` | Texto muted |
| `destructive` | `0 84.2% 60.2%` | Ações destrutivas |
| `destructive-foreground` | `0 0% 98%` | Texto sobre destructive |
| `border` | `19 23% 94%` | Cor de borda |
| `input` | `19 23% 94%` | Cor de input |
| `ring` | `15 55% 68%` | Cor do ring de foco |
| `radius` | `0.5rem` | Raio de borda |

### Injeção no CSS

O `ThemeContext.tsx` itera sobre o mapa de tokens e chama:

```typescript
Object.entries(theme.tokens).forEach(([key, value]) => {
  document.documentElement.style.setProperty(`--${key}`, value)
})
```

Os componentes React usam essas variáveis via Tailwind:

```tsx
<div className="bg-primary text-primary-foreground" />
```

## Assets (coluna `assets`)

Gerenciados via upload no `ThemeAssetController`.

### Upload de imagens

`POST /api/themes/assets/upload` — multipart file.

**Validações**:
- Tipo: `image/jpeg`, `image/png`, `image/gif`, `image/webp`, `image/svg+xml`
- Tamanho: configurado no servidor

**Campos**:
| Key | Descrição |
|-----|-----------|
| `logo` | Logo do estabelecimento |
| `favicon` | Favicon (ico ou png) |
| `heroBackground` | Imagem de fundo do hero |
| `ogImage` | Imagem para Open Graph (1200×630 recomendado) |

## Conteúdo (coluna `content`)

Dividido em seções:

### Hero
`heroTitle`, `heroSubtitle`, `heroCtaText`, `heroSecondaryCtaText`, `heroCards[]`

### About
`aboutTitle`, `aboutDescription1`, `aboutDescription2`, `aboutAddress`, `aboutHours`, `aboutFeatures[]`

### Navegação
`navItems[]` — array de `{label, href}`

### Negócio
`businessType`, `socialLinks` — `{instagram, facebook, ...}`

### SEO
`seoTitle`, `seoDescription`, `seoSiteName`, `seoAuthor`, `seoUrl`, `seoLocale`, `robots`, `themeColor`

### Open Graph
`ogTitle`, `ogType`, `ogDescription`, `ogImageWidth`, `ogImageHeight`

### Twitter
`twitterCard`

## DTOs

| DTO | Tipo | Campos |
|-----|------|--------|
| `ThemeDTO` | record | `name`, `baseThemeId`, `status`, `tokens`, `assets`, `content`, `tenantId` |
| `ThemeResponseDTO` | record | `id`, `name`, `baseThemeId`, `status`, `tokens`, `assets`, `content`, `tenantId`, `createdAt`, `updatedAt` |
| `ThemeScheduleDTO` | record | `themeId`, `validFrom`, `validTo`, `priority` |

## Repositório

`ThemeRepository` — estende `JpaRepository<Theme, Long>`.

| Método | Descrição |
|--------|-----------|
| `findByTenantId(String)` | Todos os temas do tenant |
| `findByTenantIdAndStatus(String, ThemeStatus)` | Temas por status |
| `findByTenantIdAndBaseThemeId(String, Long)` | Temas derivados de outro |
| `findByTenantIdAndName(String, String)` | Busca por nome |

## Controller

`ThemeController` — `@RequestMapping("/api/themes")`.

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/themes/active` | Tema ativo (autenticado) |
| `GET` | `/api/themes/public/theme/active` | Tema ativo (público, cache) |
| `GET` | `/api/themes` | Listar do tenant |
| `GET` | `/api/themes/{id}` | Buscar por ID |
| `POST` | `/api/themes` | Criar |
| `PUT` | `/api/themes/{id}` | Atualizar |
| `DELETE` | `/api/themes/{id}` | Excluir |
| `POST` | `/api/themes/{id}/duplicate` | Duplicar |
| `POST` | `/api/themes/{id}/schedule` | Agendar/ativar |
| `POST` | `/api/themes/assets/upload` | Upload de asset |

## ThemeAssetController

`POST /api/themes/assets/upload` — upload de imagens (multipart).

## Regras

1. **Status PUBLISHED**: necessário para que o tema possa ser ativado. Temas DRAFT não aparecem como elegíveis para ativação
2. **Duplicação**: cria cópia integral do tema com `baseThemeId` apontando para o original. Permite criar variações sazonais a partir de um tema base
3. **Exclusão protegida**: tema não pode ser excluído se tiver assignments ativos (`ThemeAssignment`). É necessário primeiro remover o agendamento
4. **Notificação**: qualquer alteração em tema PUBLISHED dispara `ThemeNotificationService.broadcast()` para atualizar todos os clientes conectados
5. **Tradução**: ao atualizar conteúdo textual, o ERP é notificado para marcar traduções como PENDING no `entity_translation`
6. **Cache**: o endpoint público `/api/themes/public/theme/active` usa cache HTTP para evitar consultas repetidas ao banco
