# Temas — Especificação

## Visão geral

O módulo de Temas implementa um sistema completo de white-label multi-tenant. Cada tenant (ex: `espresso`, `villa`) possui um ou mais temas, dos quais um está ativo a qualquer momento. O tema ativo define a identidade visual completa do PWA e site, além de configurações para geração de APK Android customizado.

O tema é armazenado no banco Postgres (com colunas JSONB para tokens, assets e content) e servido por uma API dedicada no `espresso_back`. O ERP (`backend`) consulta o tema ativo via `EspressoThemeClient` para i18n e para aplicar em documentos (logo em DANFE, etc.).

```
[ThemeController] → [ThemeService] → [ThemeRepository] → [theme table]
                                            ↓
                               [ThemeAssignmentRepository] → [theme_assignment table]
                                            ↓
[ThemeContext (React)] ← SSE/WS ← [ThemeNotificationService]
       ↓
[CSS Custom Properties injetados no :root]
```

## Modelo de dados

### Theme

Tabela `theme`. Entidade central do módulo.

| Campo | Tipo | JSONB? | Detalhes |
|-------|------|--------|----------|
| `id` | `Long` | | PK, identity |
| `name` | `String(100)` | | Obrigatório |
| `baseThemeId` | `Long` | | FK para theme original (duplicação) |
| `status` | `ThemeStatus` | | `DRAFT`, `PUBLISHED`, `ARCHIVED` |
| `tokens` | `Map<String, String>` | ✅ | Design tokens em HSL (ex: `primary: "15 55% 68%"`) |
| `assets` | `Map<String, Object>` | ✅ | URLs de assets (logo, favicon, hero, og, android) |
| `content` | `Map<String, Object>` | ✅ | Conteúdo textual (hero, about, seo, business) |
| `createdAt` | `LocalDateTime` | | |
| `updatedAt` | `LocalDateTime` | | |
| `tenantId` | `String` | | Identificador do tenant, obrigatório |

### ThemeAssignment

Tabela `theme_assignment`. Agenda a ativação de temas por período.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK, identity |
| `tenantId` | `String` | Obrigatório |
| `themeId` | `Long` | FK → `theme.id` |
| `validFrom` | `LocalDateTime` | Início da validade |
| `validTo` | `LocalDateTime` | Fim da validade |
| `isActive` | `Boolean` | Se o assignment está ativo |
| `priority` | `Integer` | Prioridade entre assignments concorrentes |
| `createdAt` | `LocalDateTime` | |
| `updatedAt` | `LocalDateTime` | |

### ThemeConfig

Tabela `tenant_config`. Chave-valor para configurações do tenant.

| Campo | Tipo |
|-------|------|
| `key` | `String` (PK) |
| `value` | `String` |

Chave usada: `default_tenant_id`.

## Estrutura do JSONB — tokens

Mapa de chave → valor HSL. Exemplo seed (`V2__seed_data.sql`):

```json
{
  "background": "0 0% 100%",
  "foreground": "0 0% 3.9%",
  "primary": "15 55% 68%",
  "primary-foreground": "0 0% 100%",
  "secondary": "0 0% 100%",
  "secondary-foreground": "15 55% 68%",
  "accent": "19 23% 94%",
  "accent-foreground": "15 55% 68%",
  "muted": "19 23% 94%",
  "muted-foreground": "0 0% 45.1%",
  "destructive": "0 84.2% 60.2%",
  "destructive-foreground": "0 0% 98%",
  "border": "19 23% 94%",
  "input": "19 23% 94%",
  "ring": "15 55% 68%",
  "radius": "0.5rem"
}
```

Esses tokens são injetados como CSS custom properties no `:root` pelo `ThemeContext.tsx`:

```css
:root {
  --primary: 15 55% 68%;
  --background: 0 0% 100%;
  --radius: 0.5rem;
  ...
}
```

## Estrutura do JSONB — assets

```json
{
  "logo": "/uploads/themes/logo-abc123.png",
  "favicon": "/uploads/themes/favicon-xyz456.ico",
  "heroBackground": "/uploads/themes/hero-def789.jpg",
  "ogImage": "/uploads/themes/og-image.jpg",
  "android": {
    "appName": "Meu Bakery",
    "activityTitle": "Meu Bakery",
    "applicationId": "com.bakery.app",
    "namespace": "com.bakery.app",
    "packageName": "com.bakery.app",
    "versionName": "1.0.0",
    "versionCode": 1,
    "customUrlScheme": "meubakery",
    "googleServicesPath": "/uploads/themes/android/google-services.json",
    "firebaseAdminsdkPath": "/uploads/themes/android/firebase-adminsdk.json",
    "iconsZipPath": "/uploads/themes/android/icons.zip"
  }
}
```

## Estrutura do JSONB — content

```json
{
  "name": "Meu Bakery",
  "heroTitle": "Bem-vindo ao Meu Bakery",
  "heroSubtitle": "Experiência única",
  "heroCtaText": "Ver Cardápio",
  "heroSecondaryCtaText": "Saiba Mais",
  "heroCards": [
    { "title": "Ambiente Aconchegante", "description": "..." }
  ],
  "aboutTitle": "Nossa História",
  "aboutDescription1": "...",
  "aboutDescription2": "...",
  "aboutAddress": "Rua Exemplo, 123",
  "aboutHours": "Seg-Sáb: 18h-00h",
  "aboutFeatures": ["Wi-Fi Grátis", "Acessibilidade"],
  "navItems": [
    { "label": "Cardápio", "href": "/cardapio" },
    { "label": "Contato", "href": "/contato" }
  ],
  "businessType": "Restaurante",
  "socialLinks": {
    "instagram": "https://instagram.com/meubakery",
    "facebook": "https://facebook.com/meubakery"
  },
  "seoTitle": "Meu Bakery - O melhor da cidade",
  "seoDescription": "Venha conhecer",
  "seoSiteName": "Meu Bakery",
  "seoAuthor": "Meu Bakery",
  "seoUrl": "https://meubakery.com.br",
  "seoLocale": "pt_BR",
  "ogTitle": "Meu Bakery",
  "ogType": "website",
  "ogDescription": "Venha conhecer",
  "ogImageWidth": 1200,
  "ogImageHeight": 630,
  "twitterCard": "summary_large_image",
  "robots": "index, follow",
  "themeColor": "#d4a574"
}
```

## Endpoints da API

### Temas (`espresso_back` — `/api/themes`)

| Método | Path | Auth | Descrição |
|--------|------|------|-----------|
| `GET` | `/api/themes/active` | Autenticado | Tema ativo para o tenant do usuário |
| `GET` | `/api/themes/public/theme/active` | Público (cache) | Tema ativo (usado pelo PWA e ERP) |
| `GET` | `/api/themes` | Autenticado | Listar temas do tenant |
| `GET` | `/api/themes/{id}` | Autenticado | Buscar tema por ID |
| `POST` | `/api/themes` | Autenticado | Criar tema |
| `PUT` | `/api/themes/{id}` | Autenticado | Atualizar tema |
| `DELETE` | `/api/themes/{id}` | Autenticado | Excluir tema (bloqueado se atribuído) |
| `POST` | `/api/themes/{id}/duplicate` | Autenticado | Duplicar tema |
| `POST` | `/api/themes/{id}/schedule` | Autenticado | Agendar/ativar tema |
| `POST` | `/api/themes/assets/upload` | Autenticado | Upload de asset (logo, favicon, hero, og) |
| `POST` | `/api/themes/android/upload` | Autenticado | Upload de assets Android |

## Serviços

### ThemeService (268 linhas) — `espresso_back`

| Método | Descrição |
|--------|-----------|
| `getThemesByTenant(tenantId)` | Lista todos os temas do tenant |
| `getActiveTheme(tenantId)` | Determina o tema ativo: 1º) assignments agendados com validade atual, por prioridade; 2º) último PUBLISHED; 3º) fallback para default_tenant_id |
| `getThemeById(id)` | Busca por ID |
| `createTheme(dto)` | Cria tema, opcionalmente aciona redeploy |
| `updateTheme(id, dto)` | Atualiza tema, notifica ERP para marcar traduções como pendentes, opcionalmente aciona redeploy |
| `deleteTheme(id)` | Exclui (bloqueia se atribuído a tenants) |
| `duplicateTheme(id, name, tenantId)` | Copia tema com novo nome/tenant, seta baseThemeId |
| `scheduleTheme(themeId, schedule)` | Desativa assignments existentes, cria novo com validFrom/validTo/priority, salva default_tenant, broadcast WebSocket |

### ThemeNotificationService

Envia mensagem WebSocket para `/topic/theme/{tenantId}/refresh` quando o tema é alterado. O `ThemeContext.tsx` no frontend escuta e recarrega o tema.

### RedeployService

Cria arquivos `.redeploy_signal` e `.redeploy_status` no sistema de arquivos para triggering de restart da aplicação (necessário para mudanças de SEO, favicon e assets críticos).

### EspressoThemeClient (ERP `backend`)

Cliente HTTP que consulta `GET /api/themes/public/theme/active?tenantId=...` no espresso_back para obter o tema ativo do ERP.

### ThemeTranslationMapper (ERP `backend`)

Mapeia campos do tema para o sistema de `entity_translation` (i18n): name, seoTitle, seoSiteName, seoAuthor, heroTitle, heroSubtitle, heroCtaText, heroSecondaryCtaText, businessType, navItems, heroCards, aboutTitle, aboutDescription1/2, aboutAddress, aboutFeatures, aboutHours.

### ThemeTranslationSeedService (ERP `backend`)

Job agendado que cria entradas `PENDING` de tradução para o tema ativo sempre que o conteúdo é alterado.

## Frontend

### ThemeContext (`espresso_front/src/contexts/ThemeContext.tsx`)

Provider React que:
1. Busca o tema ativo via `${erpBaseUrl}/api/espresso/themes/public/theme/active?tenantId=...&_cb=${cacheBuster}`
2. Injeta todos os `tokens` como CSS custom properties: `document.documentElement.style.setProperty('--' + key, value)`
3. Escuta WebSocket em `/topic/theme/{tenantId}/refresh` para recarregar automaticamente
4. Envia `Accept-Language` header para receber conteúdo traduzido

### TemasPage (`espresso_front/src/pages/admin/TemasPage.tsx`)

CRUD completo de temas com:
- **Lista**: tabela com nome, status (DRAFT/PUBLISHED/ARCHIVED com badges coloridos), tenant, datas de criação/atualização, ações
- **Ativar tema**: dropdown que chama `POST /api/themes/{id}/schedule` com ativação imediata
- **Criar/Editar**: modal com 7 abas (Geral, Cores e Estilos, Imagens, Conteúdo, SEO, Android)
- **Duplicar**: cria cópia do tema com novo nome
- **Excluir**: com confirmação; bloqueado se tema estiver atribuído
- **Redeploy overlay**: exibido quando mudanças críticas (SEO, favicon) são detectadas

### AdminSidebar (`espresso_front/src/layouts/AdminSidebar.tsx`)

Menu "Sistema" → "Temas" (ícone Settings, rota `/admin/temas`).

### Rotas (`espresso_front/src/App.tsx`)

```tsx
<Route path="temas" element={<TemasPage />} />
```

## Regras de negócio

1. **Hierarquia de ativação**: (a) assignments agendados com validade atual, ordenados por prioridade; (b) último tema PUBLISHED; (c) tema do `default_tenant_id`
2. **Agendamento substitui**: ao agendar um tema, todos os assignments existentes do tenant são desativados
3. **Redeploy**: mudanças em SEO, favicon e assets críticos acionam redeploy do sistema via `RedeployService`
4. **Notificação em tempo real**: qualquer alteração no tema ativo dispara broadcast WebSocket para todos os clientes conectados
5. **Validação de assets**: upload de imagens aceita apenas JPEG, PNG, GIF, WebP, SVG. Upload Android valida estrutura do zip de ícones (mipmap densities + drawable XML)
6. **Prevenção de exclusão**: tema não pode ser excluído se estiver atribuído a algum tenant (via `ThemeAssignment`)
7. **i18n**: conteúdo textual do tema é automaticamente mapeado para tradução via `entity_translation` quando atualizado
8. **Fallback**: se o tenant não tem tema ativo, usa o tema do `default_tenant_id` configurado em `tenant_config`
