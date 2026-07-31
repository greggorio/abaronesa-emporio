package com.baronesa.emporio.config.form.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActionType {
    ADD("add", "o_add", "primary", false, false),
    EDIT("edit", "o_edit", "primary", true, true),
    DELETE("delete", "o_delete", "negative", true, true),
    DUPLICATE("duplicate", "o_content_copy", "primary", true, true),
    EXPORT("export", "o_download", "primary", false, false),
    IMPORT("import", "o_upload", "primary", false, false),
    REFRESH("refresh", "o_refresh", "primary", false, false),
    VIEW("view", "o_visibility", "primary", true, true),
    PRINT("print", "o_print", "primary", true, false),
    CUSTOM("custom", "o_settings", "primary", false, false),
    NAVIGATE("navigate", "visibility", "primary", false, true);

    private final String action;
    private final String defaultIcon;
    private final String defaultColor;
    private final boolean requiresSelection;
    private final boolean inlineOnly;
}