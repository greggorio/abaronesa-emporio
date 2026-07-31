# Bares – Continuation Guide (Dynamic Forms + Produtos)

This document captures the key decisions, structure, and step‑by‑step workflow needed to continue the Produtos implementation using dynamic form definitions and custom components. It is written so another coding agent can pick up exactly where we left off.

## Overview

- Dynamic forms are persisted in DB table `dynamic_form_definitions` and exposed to the frontend via `/api/<entity>/form-config`.
- We keep an authoritative JSON copy in the repo and follow a safe update flow:
  1) Read the JSON file from the repo.
  2) Apply requested changes in memory to build a DTO payload.
  3) POST the payload to `/api/form-builder/definitions/save`.
  4) After user confirms success, update the file in the repo with the same content.

## Where Things Are

- Product form JSON (authoritative file in repo):
  - `backend/src/main/resources/form-definitions/produtos.json`
- Backend – dynamic form services/registry:
  - `backend/src/main/java/com/smartdata/bares/dynamicform/controller/FormBuilderController.java`
  - `backend/src/main/java/com/smartdata/bares/dynamicform/service/DynamicFormService.java`
  - `backend/src/main/java/com/smartdata/bares/dynamicform/config/DynamicFormRegistry.java`
  - `backend/src/main/java/com/smartdata/bares/dynamicform/config/DynamicFormConfig.java`
  - `backend/src/main/java/com/smartdata/bares/dynamicform/dto/TabDTO.java` (now supports `visibilityCondition`)
- Backend – Produtos domain:
  - `backend/src/main/java/com/smartdata/bares/service/ProdutoService.java`
  - `backend/src/main/java/com/smartdata/bares/service/list/ProdutoListService.java`
  - `backend/src/main/java/com/smartdata/bares/entity/Produto.java`
  - `backend/src/main/java/com/smartdata/bares/entity/ProdutoSKU.java`
  - `backend/src/main/java/com/smartdata/bares/dto/ProdutoRequest.java`
  - `backend/src/main/java/com/smartdata/bares/dto/ProdutoSKUDTO.java`
- Frontend – generic dialog and composition:
  - `frontend/src/components/forms/GenericFormDialog.vue` (supports `tab.visibilityCondition` and custom components)
  - `frontend/src/components/forms/ProdutoVariacoesTab.vue` (custom component for the Variações tab)
  - `frontend/src/components/PrgContainerNew.vue`
  - `frontend/src/composables/useDynamicTable.js`

## Current State (Produtos)

- The product form is backed by `produtos.json` and has:
  - Tab "Dados Gerais" with fields: `nome`, `descricao`, `categoria` (select), `subcategoria` (text), `tipoPrecificacao` (select: UNICO, VARIACAO, TAMANHO).
  - Tab "Variações" with `component: "ProdutoVariacoesTab"` and `visibilityCondition: "formData.tipoPrecificacao && formData.tipoPrecificacao !== 'UNICO'"`.
  - Actions: ADD/EDIT/DELETE.
  - Table columns include `estoque` and `totalSkus` aligned with backend aggregation.

### Custom component – ProdutoVariacoesTab

- File: `frontend/src/components/forms/ProdutoVariacoesTab.vue`
- Props: `modelValue`, `recordId`, `tipoPrecificacao` (optional). It also checks `modelValue.tipoPrecificacao`.
- Emits `update:modelValue` merging `skus: [...]` shaped for the backend (`ProdutoSKUDTO` structure).
- Shows per‑SKU price fields only if the pricing type is not `UNICO`.

### GenericFormDialog – Tabs visibility

- File: `frontend/src/components/forms/GenericFormDialog.vue`
- Evaluates `tab.visibilityCondition` with the same evaluator used for field conditions.
- Registry includes `ProdutoVariacoesTab` for custom tab rendering.

## Backend – SKU Sync Logic

- File: `backend/src/main/java/com/smartdata/bares/service/ProdutoService.java`
- Changes implemented:
  - On create: save product → synchronize SKUs → save again.
  - On update: map request to entity → synchronize SKUs → save.
  - Method `sincronizarSkus(Produto produto, List<ProdutoSKUDTO> skusRequest, TipoPrecificacao tipoPrecificacao)`:
    - If `UNICO`: ensure exactly 1 SKU (principal), generate SKU code if missing, align prices from product and remove extras.
    - If `VARIACAO`/`TAMANHO`: remove missing SKUs, create new for `id=null`, update existing for `id!=null`, ensure at most one principal, auto‑generate code if empty.

## Backend – List Aggregations

- File: `backend/src/main/java/com/smartdata/bares/service/list/ProdutoListService.java`
- `entityToRow` adds:
  - `estoque`: sum of all `estoqueAtual` in SKUs (fallback to product `estoqueAtual`).
  - `totalSkus`: `produto.getSkus().size()`.

## How to Update Form Definitions (Workflow)

1) Read and parse `backend/src/main/resources/form-definitions/produtos.json`.
2) Apply the requested change in memory to build a payload DTO with keys:
   - `entityType`, `programName`, `programIcon`, `tableOrder`, `complexity`,
   - `tabs` (each may include `component` and optional `visibilityCondition`),
   - `actions`,
   - `tableColumns` (array of column objects).
3) POST JSON to `POST /api/form-builder/definitions/save` with `Content-Type: application/json`.
4) Wait for user confirmation of success.
5) Mirror the change in `backend/src/main/resources/form-definitions/produtos.json`.

Notes:
- Do not write to DB directly for form structure; always use the POST.
- After saving, the live frontend reads updated config via `/api/produtos/form-config`.

## Endpoints Reference

- Save/update definition: `POST /api/form-builder/definitions/save`
- Preview form (optional): `GET /api/form-builder/preview/{entityType}`
- List live form config for Produtos: `GET /api/produtos/form-config?pagina=0&tamanho=20`
- Force cache clear/reload (optional):
  - `POST /api/form-builder/cache/reload/{entityType}` or `POST /api/form-builder/cache/clear`

## JSON Snippets (Examples)

- Show Variações tab only when not `UNICO`:
```
{
  "name": "variacoes",
  "label": "Variações",
  "icon": "folder",
  "component": "ProdutoVariacoesTab",
  "visibilityCondition": "formData.tipoPrecificacao && formData.tipoPrecificacao !== 'UNICO'",
  "fields": [],
  "order": 2
}
```

- Add `tipoPrecificacao` field (Dados Gerais):
```
{
  "name": "tipoPrecificacao",
  "label": "Tipo de Precificação",
  "type": "SELECT",
  "component": "q-select",
  "cols": "col-12",
  "options": [
    { "label": "Preço Único", "value": "UNICO" },
    { "label": "Por Variação", "value": "VARIACAO" },
    { "label": "Por Tamanho", "value": "TAMANHO" }
  ],
  "props": { "emit-value": true, "map-options": true }
}
```

## Next Steps (Suggested, Small Increments)

1) Image upload for `imagemPrincipal` (Produtos)
   - Add `POST /api/produtos/{id}/upload-imagem-principal` (Multipart `arquivo`).
   - Switch JSON field from `TEXT` to `QFile` with `uploadEndpoint` and `immediateUpload`.

2) Persist midias (optional)
   - If we introduce a `ProdutoMidiasTab`, add specific endpoints or handle via product update, following the same pattern.

3) Fine‑tune table columns
   - Confirm `categoriaNome` and others get populated; if needed, adjust `entityToRow` mapping.

4) Validation UX
   - Add minimal validations for `Variações` (non‑empty `variacao`, prevent duplicates) – component already does basic checks.

## Gotchas / Conventions

- Always preserve `actions` in form structure when updating; do not overwrite them unintentionally.
- The frontend dialog supports both `showIf` (function in client code) and `visibilityCondition` (string persisted in DB) for tabs and `visibilityCondition` for fields.
- Custom tabs must be registered in `GenericFormDialog`’s registry to resolve by name.
- SKU code generation: backend helper `ProdutoSKU.gerarSKU()` is called if `sku` is empty; it uses product info to compose a code.

## Quick Verification Checklist

- After POSTing a definition:
  - `GET /api/produtos/form-config` returns the updated tabs and actions.
  - Open Produtos program: actions visible, tabs reflect `visibilityCondition`.
- Create product with `UNICO`:
  - Variações tab hidden; single SKU created, prices aligned to product.
- Create product with `VARIACAO` or `TAMANHO`:
  - Variações tab visible; rows added in the tab are persisted as SKUs; update and delete work.

