package com.baronesa.emporio.config.form.builder;


import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Builder para criar tabs individuais
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TabBuilder {
    private String name;
    private String label;
    private String icon;
    private String component;
    private List<Map<String, Object>> fields = new ArrayList<>();
    private Object showIf;
    private Map<String, Object> props;

    public static TabBuilder create(String name, String label) {
        TabBuilder builder = new TabBuilder();
        builder.name = name;
        builder.label = label;
        return builder;
    }

    public TabBuilder icon(String icon) {
        this.icon = icon;
        return this;
    }

    public TabBuilder component(String component) {
        this.component = component;
        return this;
    }

    public TabBuilder showIf(String condition) {
        this.showIf = condition;
        return this;
    }

    public TabBuilder showIf(Map<String, Object> condition) {
        this.showIf = condition;
        return this;
    }

    public TabBuilder props(Map<String, Object> props) {
        this.props = props;
        return this;
    }

    public TabBuilder prop(String key, Object value) {
        if (this.props == null) {
            this.props = new HashMap<>();
        }
        this.props.put(key, value);
        return this;
    }

    public TabBuilder addField(Map<String, Object> field) {
        this.fields.add(field);
        return this;
    }

    public TabBuilder addFields(List<Map<String, Object>> fields) {
        this.fields.addAll(fields);
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("label", label);

        if (icon != null) {
            result.put("icon", icon);
        }

        if (component != null) {
            result.put("component", component);
        } else if (!fields.isEmpty()) {
            result.put("fields", fields);
        }

        if (showIf != null) {
            result.put("showIf", showIf);
        }

        if (props != null && !props.isEmpty()) {
            result.put("props", props);
        }

        return result;
    }
}