// src/stores/formBuilderStore.js
import { defineStore } from "pinia";
import { ref, reactive, computed } from "vue";
import EntityDiscoveryService from "@/services/form-builder/EntityDiscoveryService";
import FormBuilderService from "@/services/form-builder/FormBuilderService";
import FieldTypeMapper from "@/services/form-builder/FieldTypeMapper";

export const useFormBuilderStore = defineStore("formBuilder", () => {
  // Estado
  const availableEntities = ref([]);
  const selectedEntity = ref(null);
  const detectedFields = ref([]);
  const selectedField = ref(null);
  const selectedTab = ref("dados-gerais");
  const isDirty = ref(false);
  const loading = ref(false);
  const saving = ref(false);

  // Definição do formulário
  const formDefinition = reactive({
    entityType: "",
    programName: "",
    programIcon: "o_settings",
    tableOrder: "id",
    complexity: "SIMPLE",
    javaExtensionClass: null,
    tabs: [
      {
        name: "dados-gerais",
        label: "Dados Gerais",
        icon: "info",
        fields: [],
        order: 1,
      },
    ],
    actions: [
      {
        type: "ADD",
        label: "Adicionar",
        icon: "add",
        color: "primary",
      },
      {
        type: "EDIT",
        label: "Editar",
        icon: "edit",
        onDoubleClick: true,
      },
      {
        type: "DELETE",
        label: "Excluir",
        icon: "delete",
        color: "negative",
        confirmTitle: "Confirmação",
        confirmMessage: "Deseja realmente excluir este registro?",
      },
    ],
    tableColumns: [],
    customSlots: {},
    dialogConfig: {
      width: "800px",
      maxWidth: "95vw",
      maxHeight: "90vh",
      fullscreenMobile: true,
    },
  });

  // Computed
  const currentTab = computed(() => formDefinition.tabs.find((tab) => tab.name === selectedTab.value));

  const allFields = computed(() => formDefinition.tabs.flatMap((tab) => tab.fields));

  const unusedFields = computed(() => {
    const usedFieldNames = new Set(allFields.value.map((f) => f.name));
    return detectedFields.value.filter((field) => !usedFieldNames.has(field.name));
  });

  // Actions
  function getEntityLabel(entity) {
    return entity?.programName || entity?.className || entity?.entityType || "";
  }

  async function loadAvailableEntities() {
    loading.value = true;
    try {
      const entities = await EntityDiscoveryService.getAvailableEntities();
      availableEntities.value = [...entities].sort((a, b) =>
        getEntityLabel(a).localeCompare(getEntityLabel(b), "pt-BR", { sensitivity: "base" })
      );
    } finally {
      loading.value = false;
    }
  }

  async function selectEntity(entity) {
    loading.value = true;
    try {
      // Extrair entityType corretamente do Proxy
      let entityType = entity?.entityType;
      let className = entity?.className;

      if (!entityType) {
        throw new Error("entityType não encontrado no objeto");
      }

      selectedEntity.value = entity;
      formDefinition.entityType = entityType;
      formDefinition.programName = className || entityType;

      // Detectar campos
      detectedFields.value = await EntityDiscoveryService.detectFields(entityType);

      // Carregar definição existente se houver
      const existing = await EntityDiscoveryService.loadExistingDefinition(entityType);
      if (existing) {
        Object.assign(formDefinition, existing);
        isDirty.value = false;
      } else {
        // Gerar configuração inicial
        generateInitialConfig();
      }
    } catch (error) {
      console.error("Erro ao selecionar entidade:", error);
      throw error;
    } finally {
      loading.value = false;
    }
  }

  function generateInitialConfig() {
    // Limpar configuração anterior
    formDefinition.tabs[0].fields = [];
    formDefinition.tableColumns = [];

    // Gerar colunas da tabela automaticamente
    detectedFields.value
      .filter((f) => !["createdAt", "updatedAt", "version"].includes(f.name))
      .forEach((field, index) => {
        formDefinition.tableColumns.push({
          name: field.name,
          label: FieldTypeMapper.humanizeFieldName(field.name),
          type: FieldTypeMapper.mapJavaTypeToColumnType(field.type),
          order: index + 1,
          sortable: true,
          align: "left",
        });
      });

    isDirty.value = true;
  }

  function addFieldToTab(field, tabName = null) {
    const tab = tabName ? formDefinition.tabs.find((t) => t.name === tabName) : currentTab.value;

    if (!tab) return;

    const fieldConfig = FieldTypeMapper.generateFieldConfig(field, formDefinition.entityType);
    tab.fields.push(fieldConfig);
    isDirty.value = true;
  }

  function removeFieldFromTab(fieldName, tabName = null) {
    const tab = tabName ? formDefinition.tabs.find((t) => t.name === tabName) : currentTab.value;

    if (!tab) return;

    const index = tab.fields.findIndex((f) => f.name === fieldName);
    if (index > -1) {
      tab.fields.splice(index, 1);
      isDirty.value = true;
    }
  }

  function updateField(fieldName, updates) {
    // Procurar o campo em todas as tabs
    for (const tab of formDefinition.tabs) {
      const fieldIndex = tab.fields.findIndex((f) => f.name === fieldName);
      if (fieldIndex !== -1) {
        // Atualizar o campo com os novos valores
        tab.fields[fieldIndex] = { ...updates };
        isDirty.value = true;
        return;
      }
    }
  }

  function addTab(name, label, icon = "folder") {
    formDefinition.tabs.push({
      name,
      label,
      icon,
      fields: [],
      order: formDefinition.tabs.length + 1,
    });
    selectedTab.value = name;
    isDirty.value = true;
  }

  function removeTab(tabName) {
    if (formDefinition.tabs.length <= 1) return;

    const index = formDefinition.tabs.findIndex((t) => t.name === tabName);
    if (index > -1) {
      formDefinition.tabs.splice(index, 1);
      if (selectedTab.value === tabName) {
        selectedTab.value = formDefinition.tabs[0].name;
      }
      isDirty.value = true;
    }
  }

  function addAction(action) {
    formDefinition.actions.push(action);
    isDirty.value = true;
  }

  function removeAction(index) {
    formDefinition.actions.splice(index, 1);
    isDirty.value = true;
  }

  function updateAction(index, updates) {
    Object.assign(formDefinition.actions[index], updates);
    isDirty.value = true;
  }

  function moveField(fromTab, toTab, fieldName) {
    const fromTabObj = formDefinition.tabs.find((t) => t.name === fromTab);
    const toTabObj = formDefinition.tabs.find((t) => t.name === toTab);

    if (!fromTabObj || !toTabObj) return;

    const fieldIndex = fromTabObj.fields.findIndex((f) => f.name === fieldName);
    if (fieldIndex > -1) {
      const [field] = fromTabObj.fields.splice(fieldIndex, 1);
      toTabObj.fields.push(field);
      isDirty.value = true;
    }
  }

  // Função para limpar propriedades null/undefined/vazias
  function cleanObject(obj) {
    if (Array.isArray(obj)) {
      return obj.map((item) => cleanObject(item)).filter((item) => item !== null);
    } else if (obj !== null && typeof obj === "object") {
      const cleaned = {};
      for (const [key, value] of Object.entries(obj)) {
        if (value !== null && value !== undefined && value !== "") {
          if (typeof value === "object" && Object.keys(value).length === 0) {
            // Pular objetos vazios
            continue;
          }
          if (Array.isArray(value) && value.length === 0) {
            // Pular arrays vazios, exceto validations
            if (key !== "validations") continue;
          }
          cleaned[key] = cleanObject(value);
        }
      }
      return Object.keys(cleaned).length > 0 ? cleaned : null;
    }
    return obj;
  }

  async function saveFormDefinition() {
    saving.value = true;
    try {
      // Limpar objeto antes de salvar
      const cleanedDefinition = cleanObject(formDefinition);
      const saved = await FormBuilderService.saveDefinition(cleanedDefinition);
      Object.assign(formDefinition, saved);
      isDirty.value = false;
      FormBuilderService.clearDraft(formDefinition.entityType);
      return saved;
    } finally {
      saving.value = false;
    }
  }

  function saveDraft() {
    if (formDefinition.entityType) {
      FormBuilderService.saveDraft(formDefinition.entityType, formDefinition);
    }
  }

  function loadDraft() {
    if (formDefinition.entityType) {
      const draft = FormBuilderService.loadDraft(formDefinition.entityType);
      if (draft) {
        Object.assign(formDefinition, draft);
        isDirty.value = true;
        return true;
      }
    }
    return false;
  }

  function updateFromJson(jsonData) {
    Object.keys(jsonData).forEach((key) => {
      if (key === "tabs") {
        formDefinition.tabs = [...jsonData.tabs];
      } else if (key === "actions") {
        formDefinition.actions = [...jsonData.actions];
      } else if (key === "tableColumns") {
        formDefinition.tableColumns = [...jsonData.tableColumns];
      } else {
        formDefinition[key] = jsonData[key];
      }
    });

    isDirty.value = true;
  }

  function markDirty() {
    isDirty.value = true;
  }

  function reset() {
    selectedEntity.value = null;
    selectedField.value = null;
    selectedTab.value = "dados-gerais";
    detectedFields.value = [];
    isDirty.value = false;

    // Reset formDefinition
    Object.assign(formDefinition, {
      entityType: "",
      programName: "",
      programIcon: "o_settings",
      tableOrder: "id",
      complexity: "SIMPLE",
      javaExtensionClass: null,
      tabs: [
        {
          name: "dados-gerais",
          label: "Dados Gerais",
          icon: "info",
          fields: [],
          order: 1,
        },
      ],
      actions: [
        {
          type: "ADD",
          label: "Adicionar",
          icon: "add",
          color: "primary",
        },
        {
          type: "EDIT",
          label: "Editar",
          icon: "edit",
          onDoubleClick: true,
        },
        {
          type: "DELETE",
          label: "Excluir",
          icon: "delete",
          color: "negative",
          confirmTitle: "Confirmação",
          confirmMessage: "Deseja realmente excluir este registro?",
        },
      ],
      tableColumns: [],
      customSlots: {},
    });
  }

  return {
    // Estado
    availableEntities,
    selectedEntity,
    detectedFields,
    selectedField,
    selectedTab,
    formDefinition,
    isDirty,
    loading,
    saving,

    // Computed
    currentTab,
    allFields,
    unusedFields,

    // Actions
    loadAvailableEntities,
    selectEntity,
    addFieldToTab,
    removeFieldFromTab,
    updateField,
    addTab,
    removeTab,
    addAction,
    removeAction,
    updateAction,
    moveField,
    markDirty,
    saveFormDefinition,
    saveDraft,
    loadDraft,
    reset,
    updateFromJson,
  };
});
