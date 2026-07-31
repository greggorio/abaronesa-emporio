// src/services/form-builder/FieldTypeMapper.js

class FieldTypeMapper {
  /**
   * Mapeia tipo Java para tipo de campo do formulário
   */
  mapJavaTypeToFieldType(javaType) {
    const typeMap = {
      String: "TEXT",
      Integer: "NUMBER",
      Long: "NUMBER",
      Double: "NUMBER",
      Float: "NUMBER",
      BigDecimal: "CURRENCY",
      Boolean: "CHECKBOX",
      LocalDate: "DATE",
      LocalDateTime: "DATETIME",
      LocalTime: "TIME",
      List: "RELATION",
      Set: "RELATION",
    };
    return typeMap[javaType] || "TEXT";
  }

  /**
   * Mapeia tipo Java para tipo de coluna da tabela
   */
  mapJavaTypeToColumnType(javaType) {
    const typeMap = {
      String: "TEXT",
      Integer: "NUMBER",
      Long: "NUMBER",
      Double: "NUMBER",
      Float: "NUMBER",
      BigDecimal: "CURRENCY",
      Boolean: "BOOLEAN",
      LocalDate: "DATE",
      LocalDateTime: "DATETIME",
      LocalTime: "TIME",
    };
    return typeMap[javaType] || "TEXT";
  }

  /**
   * Detecta se é um campo de relacionamento
   */
  isRelationField(fieldName, fieldType) {
    return fieldName.endsWith("Id") || ["List", "Set"].includes(fieldType);
  }

  /**
   * Detecta se é um campo de mídia
   */
  isMediaField(fieldName) {
    const mediaFieldNames = ["cover", "imagem", "foto", "avatar", "logo", "banner"];
    return mediaFieldNames.includes(fieldName.toLowerCase());
  }

  /**
   * Detecta se é um campo de tabela (lista de itens)
   */
  isTableField(fieldName, fieldType) {
    const tableFieldNames = ["itens", "items", "produtos", "detalhes", "linhas"];
    return (
      tableFieldNames.includes(fieldName.toLowerCase()) ||
      fieldName.endsWith("Items") ||
      fieldName.endsWith("Itens") ||
      (fieldType === "List" && !this.isRelationField(fieldName, fieldType))
    );
  }

  /**
   * Detecta se é um campo lookup avançado
   */
  isLookupField(fieldName) {
    // Campos que tipicamente precisam de lookup avançado
    const lookupFields = ["fornecedorId", "clienteId", "produtoId", "categoriaId", "usuarioId"];
    return lookupFields.includes(fieldName) || fieldName.match(/^.+(Id)$/);
  }

  /**
   * Gera configuração padrão para campo
   */
  generateFieldConfig(field, entityType) {
    const fieldConfig = {
      name: field.name,
      type: this.mapJavaTypeToFieldType(field.type),
      label: this.humanizeFieldName(field.name),
      cols: "col-12",
      required: false,
      placeholder: "",
      props: {},
    };

    // Configurações específicas por tipo
    if (this.isMediaField(field.name)) {
      Object.assign(fieldConfig, {
        type: "FILE",
        component: "QFile",
        immediateUpload: true,
        uploadEndpoint: `/api/${entityType}/{id}/upload-${field.name}`,
        mediaBasePath: `/media/${entityType}/`,
        validationPreset: "image",
        showPreview: true,
        clearable: true,
        accept: "image/*",
        maxSize: 5 * 1024 * 1024,
        props: {
          filled: true,
          "stack-label": true,
          "use-chips": true,
          "max-files": 1,
        },
      });
    } else if (this.isTableField(field.name, field.type)) {
      // Configuração para campo TABLE
      Object.assign(fieldConfig, {
        type: "TABLE",
        component: "TableField",
        fieldType: "table",
        cols: "col-12",
        rowAddable: true,
        rowRemovable: true,
        required: field.name === "itens", // itens geralmente é obrigatório
        columns: this.generateDefaultTableColumns(field.name),
      });
    } else if (this.isLookupField(field.name)) {
      // Configuração para campo LOOKUP avançado
      const relatedEntity = field.name.replace(/Id$/, "");
      Object.assign(fieldConfig, {
        type: "LOOKUP",
        component: "LookupSelect",
        fieldType: "lookup",
        lookupEndpoint: `/api/${this.pluralize(relatedEntity)}/lookup/search`,
        displayColumns: this.getDefaultDisplayColumns(relatedEntity),
        allowCreate: this.canCreateEntity(relatedEntity),
        createDialogComponent: this.getCreateDialogComponent(relatedEntity),
        props: {
          "option-label": "label",
          "option-value": "id",
          "emit-value": true,
          "map-options": true,
        },
      });
    } else if (this.isRelationField(field.name, field.type) && !this.isLookupField(field.name)) {
      // SELECT simples para relacionamentos básicos
      const relatedEntity = field.name.replace(/Id$/, "");
      Object.assign(fieldConfig, {
        type: "SELECT",
        component: "q-select",
        optionsEndpoint: `/api/${this.pluralize(relatedEntity)}/options`,
        props: {
          "use-input": true,
          "emit-value": true,
          "map-options": true,
          "option-label": "label",
          "option-value": "value",
          "input-debounce": 300,
        },
      });
    } else if (field.type === "Boolean") {
      fieldConfig.component = "q-checkbox";
    } else if (fieldConfig.type === "NUMBER") {
      fieldConfig.props = {
        type: "number",
        step: field.type === "Integer" || field.type === "Long" ? 1 : 0.01,
      };
    } else if (fieldConfig.type === "CURRENCY") {
      fieldConfig.props = {
        type: "number",
        step: 0.01,
        prefix: "R$",
      };
    } else if (fieldConfig.type === "DATE") {
      fieldConfig.component = "q-input";
      fieldConfig.props = {
        mask: "##/##/####",
        "mask-reversed": true,
      };
    } else if (fieldConfig.type === "TEXTAREA" || field.name.includes("observa") || field.name.includes("descricao")) {
      fieldConfig.type = "TEXTAREA";
      fieldConfig.props = {
        type: "textarea",
        rows: 3,
      };
    }

    return fieldConfig;
  }

  /**
   * Gera colunas padrão para campos TABLE baseado no nome
   */
  generateDefaultTableColumns(fieldName) {
    if (fieldName === "itens" || fieldName === "items") {
      return [
        {
          name: "produtoId",
          label: "Produto",
          type: "LOOKUP",
          align: "left",
          lookupEndpoint: "/api/produtos/lookup/search",
          displayColumns: ["codigo", "descricao"],
          props: {
            "option-label": "label",
            "option-value": "id",
            dense: true,
          },
        },
        {
          name: "quantidade",
          label: "Quantidade",
          type: "NUMBER",
          align: "center",
          props: {
            min: 1,
            step: 1,
            dense: true,
          },
        },
        {
          name: "valorUnitario",
          label: "Valor Unit.",
          type: "CURRENCY",
          align: "right",
          props: {
            prefix: "R$",
            dense: true,
          },
        },
        {
          name: "valorTotal",
          label: "Total",
          type: "COMPUTED",
          align: "right",
          formula: "quantidade * valorUnitario",
          props: {
            prefix: "R$",
            readonly: true,
            dense: true,
          },
        },
      ];
    }

    // Retorna estrutura básica para outros casos
    return [
      {
        name: "descricao",
        label: "Descrição",
        type: "TEXT",
        align: "left",
      },
      {
        name: "valor",
        label: "Valor",
        type: "NUMBER",
        align: "right",
      },
    ];
  }

  /**
   * Retorna colunas padrão para exibição em lookup
   */
  getDefaultDisplayColumns(entity) {
    const displayColumnsMap = {
      fornecedor: ["cnpj", "razaoSocial", "nomeFantasia"],
      cliente: ["cpf", "nome", "email"],
      produto: ["codigo", "descricao", "preco"],
      categoria: ["nome", "descricao"],
      usuario: ["nome", "email"],
    };

    return displayColumnsMap[entity] || ["nome", "descricao"];
  }

  /**
   * Verifica se a entidade pode ser criada via quick create
   */
  canCreateEntity(entity) {
    const creatableEntities = ["fornecedor", "cliente", "produto", "categoria"];
    return creatableEntities.includes(entity);
  }

  /**
   * Retorna o componente de criação rápida
   */
  getCreateDialogComponent(entity) {
    const componentMap = {
      fornecedor: "FornecedorQuickCreate",
      cliente: "ClienteQuickCreate",
      produto: "ProdutoQuickCreate",
      categoria: "CategoriaQuickCreate",
    };

    return componentMap[entity] || null;
  }

  /**
   * Pluraliza nome da entidade (simplificado)
   */
  pluralize(entity) {
    const irregulars = {
      categoria: "categorias",
      fornecedor: "fornecedores",
      cliente: "clientes",
      usuario: "usuarios",
      produto: "produtos",
    };

    return irregulars[entity] || entity + "s";
  }

  /**
   * Humaniza nome do campo
   */
  humanizeFieldName(fieldName) {
    return (
      fieldName
        // Separar camelCase
        .replace(/([a-z])([A-Z])/g, "$1 $2")
        // Separar snake_case
        .replace(/_/g, " ")
        // Capitalizar primeira letra
        .replace(/^./, (str) => str.toUpperCase())
        // Casos especiais
        .replace(/Id$/, "")
        .replace(/Url/g, "URL")
        .replace(/Cep/g, "CEP")
        .replace(/Cpf/g, "CPF")
        .replace(/Cnpj/g, "CNPJ")
        .replace(/Nf/g, "NF")
        .replace(/Nfe/g, "NF-e")
        .trim()
    );
  }

  /**
   * Obtém ícone para tipo de campo
   */
  getFieldIcon(type) {
    const icons = {
      TEXT: "text_fields",
      NUMBER: "numbers",
      CURRENCY: "attach_money",
      DATE: "event",
      DATETIME: "schedule",
      TIME: "access_time",
      CHECKBOX: "check_box",
      SELECT: "list",
      TEXTAREA: "notes",
      FILE: "attach_file",
      EMAIL: "email",
      URL: "link",
      RELATION: "link",
      TABLE: "table_chart",
      LOOKUP: "search",
      COMPUTED: "calculate",
    };
    return icons[type] || "help";
  }
}

export default new FieldTypeMapper();
