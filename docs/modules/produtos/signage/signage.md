# Signage — Especificação do Domínio

## Definição

Exposição de produtos em painéis digitais (telas de cozinha, menu board, totens de autoatendimento). O sistema gera conteúdo visual para cada produto com suporte a templates, paletas de cores geradas por IA, renderização de vídeo e sincronização automática com o signage-api externo.

## Escopo

**Inclui:**
- Configuração de signage por produto (ativado/desativado)
- Templates de signage com design, cores e textos
- Geração de frases e paleta de cores via IA (OpenAI)
- Renderização de vídeo do signage (HTML → MP4)
- Armazenamento e gerenciamento de vídeos
- Sincronização automática com signage-api (a cada 5 min)
- Preview do signage no formulário do produto
- Geração de imagem por IA
- Mapeamento de cores para elementos do template

**Não inclui:**
- Hardware dos painéis
- Gerenciamento de dispositivos (signage-api)
- Agendamento de exibição

## Modelo de dados

### ProductSignage

Tabela `product_signage`.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `produto_id` | BIGINT FK UNIQUE | Produto |
| `enabled` | BOOLEAN | Signage ativado |
| `status` | VARCHAR(15) | PENDING, AI_GENERATED, RENDERED, LINKED, ERROR |
| `templatePreference` | VARCHAR(50) | Template preferido |
| `palette` | JSONB | Paleta de cores |
| `phrases` | JSONB | Frases geradas (headline, subtitle, cta) |
| `colorMapping` | JSONB | Mapeamento cor → elemento do template |
| `templateApplied` | VARCHAR(50) | Template atualmente aplicado |
| `metadataSource` | VARCHAR(15) | AUTO_AI, AUTO_VIBRANT, MANUAL |
| `generatedImagePath` | VARCHAR(500) | Caminho da imagem gerada |
| `mp4Url` | VARCHAR(500) | URL do vídeo renderizado |
| `useAiImage` | BOOLEAN | Usar imagem gerada por IA |
| `aiImageHash` | VARCHAR(64) | Hash da imagem IA |
| `aiGeneratedAt` | TIMESTAMP | Data da geração IA |
| `aiRevision` | INTEGER | Número da revisão IA |
| `durationSeconds` | INTEGER | Duração do vídeo |
| `signageScreenId` | VARCHAR(50) | ID da tela no signage-api |
| `lastSyncAt` | TIMESTAMP | Última sincronização |
| `renderHash` | VARCHAR(64) | Hash do render |

FK: `produto_id` → `produto(id)`.

### SignageTemplate

Tabela `signage_templates`.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `templateId` | VARCHAR(50) | ID único do template (ex: "clean-elegance") |
| `name` | VARCHAR(100) | Nome de exibição |
| `description` | TEXT | Descrição |
| `imageMode` | VARCHAR(15) | ISOLATED, CLIPPED, FULL_BLEED |
| `requiredTexts` | JSONB | Campos de texto esperados |
| `colorSlots` | JSONB | Slots de cor do template |
| `htmlTemplate` | TEXT | Template HTML |
| `cssTemplate` | TEXT | CSS do template |
| `isActive` | BOOLEAN | Template ativo |
| `aiMode` | VARCHAR(20) | Modo IA |
| `aiPrompt` | TEXT | Prompt para geração de conteúdo |
| `aiPromptVersion` | INTEGER | Versão do prompt |
| `aiEnabled` | BOOLEAN | IA habilitada |
| `aiOutputSize` | VARCHAR(10) | Tamanho da saída IA |

### Enums

**ProductSignageStatus:** `PENDING` (aguardando), `AI_GENERATED` (IA concluída), `RENDERED` (vídeo pronto), `LINKED` (sincronizado com signage-api), `ERROR`.

**ProductSignageMetadataSource:** `AUTO_AI` (geração automática), `AUTO_VIBRANT` (paleta extraída da imagem), `MANUAL` (configurado manualmente).

## Pipeline de signage

```
1. Produto é marcado com signage enabled
2. Se metadataSource = AUTO_AI:
   ├── ProductSignageAiService gera frases e paleta via OpenAI
   └── Status → AI_GENERATED
3. Usuário pode ajustar manualmente (template, cores, frases)
4. Renderização:
   ├── SignageRenderService envia HTML+CSS para serviço externo
   └── Retorna vídeo MP4 (status → RENDERED)
5. SignageVideoStorageService baixa e armazena o vídeo
6. Sync (automático a cada 5 min ou manual):
   └── SignageSyncService envia produto + URL do vídeo para signage-api
       └── Status → LINKED
```

## Endpoints

### Signage

Os endpoints de signage são integrados ao `ProdutoController`:

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/produtos/{id}/signage/preview` | Preview do signage |
| `POST` | `/api/produtos/{id}/signage/generate-ai-image` | Gerar imagem por IA |
| `POST` | `/api/produtos/{id}/signage/render` | Renderizar vídeo |
| `PATCH` | `/api/produtos/{id}/signage` | Atualizar configuração |

### Templates

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/signage/templates` | Listar templates ativos |
| `GET` | `/api/signage/templates/{templateId}` | Detalhes do template |
| `GET` | `/api/signage/templates/{templateId}/elements` | Elementos do template |
| `PATCH` | `/api/signage/templates/{templateId}` | Atualizar template |

## Serviços

### ProductSignageAiService

Geração de conteúdo via OpenAI:
- `generate(produto, imageUrl)` → retorna JSON com `headline`, `subtitle`, `cta` (frases) + `palette` (cores hex)
- `generateAiImage()` → geração de imagem

### SignageRenderService

- `renderHtml(SignageRenderRequestDTO)` → envia HTML para serviço de renderização externo, retorna URL do vídeo

### SignageVideoStorageService

- Download do vídeo do render service para `uploads/signage/videos/{productId}/`
- Gerenciamento de retenção (mantém últimas N versões)

### SignageSyncService

- `syncProduct(produto)` → envia para signage-api
- `syncAll()` → sincroniza todos os produtos enabled com vídeo
- Gera JWT para autenticação com signage-api

### SignageSyncJobService

- `@Scheduled(fixedRate=300000)` → executa syncAll a cada 5 minutos

### ProductSignageJobService

- Busca registros elegíveis com `AUTO_AI` e dispara geração IA

## Frontend

### Backoffice (Vue)

| Componente | Descrição |
|-----------|-----------|
| `ProdutoSignageTab.vue` | Aba no formulário do produto com 3 sub-abas: Config (enable/disable, template, cores), Templates (lista/aplica templates), Video (preview, render, AI generation) |
| `SignageTemplatePreview.vue` | Preview do template |
| `SignageColorPicker.vue` | Seletor de cores |
| `SignageColorMapper.vue` | Mapeamento cor → elemento |
| `SignageTemplateAiDialog.vue` | Diálogo de geração IA |

### Admin (espresso_front - React)

| Componente | Rota |
|-----------|------|
| `SignageAdmin.tsx` | `/admin/signage` |
| `SignageDevicesAdmin.tsx` | `/admin/signage/devices` |
| `SignageScenesAdmin.tsx` | `/admin/signage/scenes` |
| `SignageScheduleAdmin.tsx` | `/admin/signage/schedule` |
| `SignageSyncGroupsAdmin.tsx` | `/admin/signage/sync-groups` |

## Decisões de domínio

- **Pipeline assíncrono** — a geração de conteúdo IA e a renderização de vídeo são operações assíncronas. O status do signage (PENDING → AI_GENERATED → RENDERED → LINKED) reflete o avanço no pipeline.
- **Sync automático a cada 5 min** — o job `SignageSyncJobService` sincroniza produtos habilitados com o signage-api. Não há sincronização em tempo real.
- **Fallback de custo** — se o SKU não tem preço de custo, usa o preço de custo do produto. Se nenhum existe, assume zero.
- **Template como design system** — templates definem cores, textos e layout. O usuário escolhe o template e o sistema (ou IA) preenche o conteúdo.

## Status de implementação

**IMPLEMENTADO**. Pipeline completo: configuração → IA (frases + paleta) → renderização (HTML → MP4) → armazenamento → sync com signage-api. Job automático a cada 5 min.
