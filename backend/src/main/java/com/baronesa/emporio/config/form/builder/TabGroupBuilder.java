package com.baronesa.emporio.config.form.builder;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Builder para criar grupos de tabs no formato esperado pelo frontend
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TabGroupBuilder {
    private String type = "tab-group";
    private Map<String, Object> dataTransformation;
    private List<Map<String, Object>> tabs = new ArrayList<>();

    public static TabGroupBuilder create() {
        return new TabGroupBuilder();
    }

    public TabGroupBuilder dataTransformation(Map<String, List<String>> nestedFields) {
        this.dataTransformation = Map.of("nestedFields", nestedFields);
        return this;
    }

    public TabGroupBuilder addTab(Map<String, Object> tab) {
        this.tabs.add(tab);
        return this;
    }

    public TabGroupBuilder addTab(TabBuilder tabBuilder) {
        this.tabs.add(tabBuilder.build());
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);

        if (dataTransformation != null) {
            result.put("dataTransformation", dataTransformation);
        }

        result.put("tabs", tabs);
        return result;
    }
}