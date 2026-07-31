UPDATE dynamic_form_definitions
SET form_structure = jsonb_set(
  form_structure,
  '{actions}',
  COALESCE(form_structure->'actions', '[]'::jsonb) || jsonb_build_array(
    jsonb_build_object(
      'icon', 'description',
      'name', 'relatorioVendas',
      'type', 'CUSTOM',
      'color', 'primary',
      'label', 'Relatório',
      'props', jsonb_build_object(),
      'route', NULL,
      'method', 'POST',
      'endpoint', NULL,
      'condition', NULL,
      'inlineOnly', false,
      'opensDialog', true,
      'confirmTitle', NULL,
      'onDoubleClick', false,
      'confirmMessage', NULL,
      'successMessage', NULL,
      'dialogComponent', 'RelatorioVendasDialog',
      'requiresSelection', false,
      'reloadAfterSuccess', false
    )
  ),
  true
)
WHERE entity_type = 'vendas'
  AND NOT EXISTS (
    SELECT 1
    FROM jsonb_array_elements(COALESCE(form_structure->'actions', '[]'::jsonb)) AS action
    WHERE action->>'dialogComponent' = 'RelatorioVendasDialog'
       OR (
         action->>'label' = 'Relatório'
         AND action->>'type' = 'CUSTOM'
       )
  );
