UPDATE dynamic_form_definitions
SET form_structure = jsonb_set(
    form_structure,
    '{tabs}',
    (
        SELECT jsonb_agg(
            CASE
                WHEN tab->>'name' = 'adicionais' THEN
                    jsonb_set(
                        tab,
                        '{fields}',
                        (tab->'fields') || jsonb_build_array(
                            jsonb_build_object(
                                'name', 'producaoPropria',
                                'label', 'Produção Própria',
                                'type', 'CHECKBOX',
                                'component', 'q-checkbox',
                                'placeholder', NULL,
                                'required', false,
                                'cols', 'col-3',
                                'props', '{}'::jsonb,
                                'validations', '[]'::jsonb,
                                'optionsEndpoint', NULL,
                                'options', NULL,
                                'formula', NULL,
                                'visibilityCondition', NULL,
                                'readOnly', NULL,
                                'lookupEndpoint', NULL,
                                'displayColumns', NULL,
                                'allowCreate', NULL,
                                'createDialogComponent', NULL,
                                'fieldType', NULL,
                                'columns', NULL,
                                'rowAddable', NULL,
                                'rowRemovable', NULL,
                                'prefix', NULL,
                                'suffix', NULL,
                                'accept', NULL,
                                'maxSize', NULL,
                                'showPreview', NULL,
                                'immediateUpload', NULL,
                                'showOnEdit', NULL
                            )
                        )
                    )
                ELSE tab
            END
        )
        FROM jsonb_array_elements(form_structure->'tabs') AS tab
    )
)
WHERE entity_type = 'produtos'
  AND NOT EXISTS (
      SELECT 1
      FROM jsonb_array_elements(form_structure->'tabs') AS t
      JOIN jsonb_array_elements(t->'fields') AS f ON true
      WHERE t->>'name' = 'adicionais'
        AND f->>'name' = 'producaoPropria'
  );
