package com.baronesa.emporio.config.form.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Getter
@RequiredArgsConstructor
public enum FormFieldType {
    ID("QInput", "o_pin", "col-2 col-sm-2 col-md-2", 80, false, true, "form.field.type.id"),
    TEXT("QInput", "o_description", "col-12 col-sm-12 col-md-12", 250, true, true, "form.field.type.text"),
    NUMBER("QInput", "o_tag", "col-6 col-sm-6 col-md-6", 150, true, true, "form.field.type.number"),
    EMAIL("QInput", "o_email", "col-12 col-sm-12 col-md-12", 250, true, true, "form.field.type.email"),
    DATE("QDate", "o_event", "col-6 col-sm-6 col-md-6", 150, true, true, "form.field.type.date"),
    DATETIME("QDatetime", "o_schedule", "col-6 col-sm-6 col-md-6", 200, true, true, "form.field.type.datetime"),
    SELECT("QSelect", "o_list", "col-12 col-sm-12 col-md-12", 200, true, true, "form.field.type.select"),
    TEXTAREA("QTextarea", "o_description", "col-12 col-sm-12 col-md-12", 300, true, true, "form.field.type.textarea"),
    CHECKBOX("QCheckbox", "o_check_box", "col-6 col-sm-6 col-md-6", 100, false, true, "form.field.type.checkbox"),
    BOOLEAN("QBadge", "o_circle", "col-3 col-sm-3 col-md-3", 100, true, true, "form.field.type.boolean"),
    TOGGLE("QToggle", "o_toggle_on", "col-6 col-sm-6 col-md-6", 120, false, true, "form.field.type.toggle"),
    RADIO("QRadio", "o_radio_button_checked", "col-6 col-sm-6 col-md-6", 100, false, true, "form.field.type.radio"),
    FILE("QFile", "o_attach_file", "col-12 col-sm-12 col-md-12", 200, false, true, "form.field.type.file"),
    CURRENCY("QInput", "o_attach_money", "col-6 col-sm-6 col-md-6", 150, true, true, "form.field.type.currency"),
    PHONE("QInput", "o_phone", "col-6 col-sm-6 col-md-6", 150, true, true, "form.field.type.phone"),
    LOOKUP      ("LookupSelect",  "o_search",       "col-12 col-sm-12 col-md-12", 250, true,  true,  "form.field.type.lookup"),
    CHILD_TABLE ("TableField",    "o_table_rows",   "col-12",                     400, false, true,  "form.field.type.childtable"),
    COMPUTED    ("ComputedField", "o_functions",    "col-6 col-sm-6 col-md-6",    150, false, true,  "form.field.type.computed"),
    URL("QInput", "o_link", "col-12 col-sm-12 col-md-12", 250, true, true, "form.field.type.url");

    private final String component;
    private final String icon;
    private final String cols;
    private final int width;
    private final boolean sortable;
    private final boolean show;
    private final String i18nKey;

    public String getTableColumnType() {
        return switch (this) {
            case ID, NUMBER, CURRENCY -> "number";
            case DATE -> "date";
            case DATETIME -> "datetime";
            case EMAIL -> "email";
            case URL -> "url";
            case CHECKBOX, BOOLEAN, TOGGLE -> "boolean";
            default -> "text";
        };
    }

    /**
     * Retorna configurações adicionais específicas para cada tipo
     */
    public Map<String, Object> getDefaultProps() {
        return switch (this) {
            case DATETIME -> Map.of(
                    "with-seconds", false,
                    "format24h", true,
                    "mask", "####-##-## ##:##"
            );
            case BOOLEAN -> Map.of(
                    "color", "primary",
                    "text-color", "white"
            );
            case TOGGLE -> Map.of(
                    "color", "primary",
                    "keep-color", true
            );
            case CURRENCY -> Map.of(
                    "prefix", "R$",
                    "mask", "#.##0,00",
                    "fill-mask", "0",
                    "reverse-fill-mask", true
            );
            case PHONE -> Map.of(
                    "mask", "(##) #####-####"
            );
            case DATE -> Map.of(
                    "mask", "##/##/####"
            );
            default -> Map.of();
        };
    }

    /**
     * Indica se o campo deve ser renderizado de forma especial na tabela
     */
    public boolean hasSpecialTableRendering() {
        return switch (this) {
            case BOOLEAN, TOGGLE, DATETIME, CURRENCY, DATE -> true;
            default -> false;
        };
    }

    /**
     * Retorna o formato de exibição para campos especiais na tabela
     */
    public String getTableDisplayFormat() {
        return switch (this) {
            case DATE -> "DD/MM/YYYY";
            case DATETIME -> "DD/MM/YYYY HH:mm";
            case CURRENCY -> "currency";
            case BOOLEAN, TOGGLE -> "boolean-badge";
            default -> null;
        };
    }
}