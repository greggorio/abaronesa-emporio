package com.baronesa.emporio.dynamicform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "dynamic_form_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamicFormDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "entity_type", unique = true, nullable = false, length = 100)
    private String entityType;

    @Column(name = "program_name", nullable = false, length = 200)
    private String programName;

    @Column(name = "program_icon", length = 50)
    private String programIcon;

    @Column(name = "table_order", length = 100)
    @Builder.Default
    private String tableOrder = "id";

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private FormComplexityLevel complexity = FormComplexityLevel.SIMPLE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_structure", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> formStructure = new HashMap<>();

    @Column(name = "java_extension_class", length = 500)
    private String javaExtensionClass;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_slots", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> customSlots = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "table_columns", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> tableColumns = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dialog_config", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> dialogConfig = new HashMap<>();

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // Métodos auxiliares
    public boolean isSimple() {
        return complexity == FormComplexityLevel.SIMPLE;
    }

    public boolean requiresJavaExtension() {
        return complexity == FormComplexityLevel.MEDIUM && javaExtensionClass != null;
    }
}
