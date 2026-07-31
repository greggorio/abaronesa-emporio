<template>
  <q-dialog v-model="dialogVisible" persistent :maximized="isMobile">
    <q-card flat :style="cardStyle" class="form-dialog-card">
      <!-- Header com toolbar -->
      <q-toolbar class="bg-grey-3">
        <q-toolbar-title class="text-h6">
          {{ computedTitle }}
        </q-toolbar-title>
        <q-btn flat round dense icon="close" @click="handleCancel" />
      </q-toolbar>

      <!-- Body -->
      <q-card-section class="q-pt-md">
        <q-form @submit.prevent="handleSubmit" ref="formRef">
          <!-- Renderização com abas -->
          <template v-if="hasTabStructure">
            <q-tabs v-model="activeTab" dense class="text-grey" active-color="primary" indicator-color="primary" align="left" narrow-indicator>
              <q-tab v-for="tab in visibleTabs" :key="tab.name" :name="tab.name" :label="tab.label" :icon="tab.icon" />
            </q-tabs>

            <q-separator />

            <q-tab-panels v-model="activeTab" animated class="q-pt-md">
              <q-tab-panel v-for="tab in visibleTabs" :key="tab.name" :name="tab.name" class="q-pa-none">
                <!-- Componente customizado para a aba -->
                <component
                  v-if="tab.component"
                  :is="resolveComponent(tab.component)"
                  v-model="formData"
                  v-bind="resolveTabProps(tab)"
                  :record-id="formData.id"
                  @update:model-value="onCustomComponentUpdate"
                />

                <!-- Campos normais da aba -->
                <div v-else class="row q-col-gutter-md">
                  <FormField
                    v-for="field in getTabFields(tab)"
                    :key="field.name"
                    :field="field"
                    :model-value="formData[field.name]"
                    :error="errors[field.name]"
                    :loading="fieldStates[field.name]?.loading"
                    :disabled="fieldStates[field.name]?.disabled"
                    :options="fieldStates[field.name]?.options"
                    :record-id="formData.id"
                    @update:model-value="updateFieldValue(field.name, $event)"
                    @field-event="handleFieldEvent"
                  />
                </div>
              </q-tab-panel>
            </q-tab-panels>
          </template>

          <!-- Renderização simples (sem abas) -->
          <template v-else>
            <div class="row q-col-gutter-md">
              <FormField
                v-for="field in visibleFields"
                :key="field.name"
                :field="field"
                :model-value="formData[field.name]"
                :error="errors[field.name]"
                :loading="fieldStates[field.name]?.loading"
                :disabled="fieldStates[field.name]?.disabled"
                :options="fieldStates[field.name]?.options"
                :record-id="formData.id"
                @update:model-value="updateFieldValue(field.name, $event)"
                @field-event="handleFieldEvent"
              />
            </div>
          </template>
        </q-form>
      </q-card-section>

      <!-- Actions -->
      <q-card-actions align="right">
        <q-btn flat :label="config.labels?.cancel || 'Cancelar'" color="secondary" @click="handleCancel" />
        <q-btn flat :label="config.labels?.save || 'Salvar'" color="primary" @click="handleSubmit" :loading="saving" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed, watch, reactive, nextTick, provide } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import FormField from "./FormField.vue";
import VencimentosTab from "@/components/forms/VencimentosTab.vue";
import RecebimentosTab from "@/components/forms/RecebimentosTab.vue";
import CartaoJurosManager from "./CartaoJurosManager.vue";
import PermissoesTab from "./PermissoesTab.vue";
import GrupoClienteDescontosTab from "./GrupoClienteDescontosTab.vue";
import ProdutoVariacoesTab from "./ProdutoVariacoesTab.vue";
import ProdutoPrecificacaoTab from "./ProdutoPrecificacaoTab.vue";
import ProdutoMidiaTab from "./ProdutoMidiaTab.vue";
import ProdutoValidadeTab from "./ProdutoValidadeTab.vue";
import ProdutoSignageTab from "./ProdutoSignageTab.vue";
import ProdutoFichaTecnicaTab from "./ProdutoFichaTecnicaTab.vue";
import ParcelasCrediarioTab from "./ParcelasCrediarioTab.vue";
import DocumentoFiscalTab from "./DocumentoFiscalTab.vue";
import InventarioFormDialog from "./InventarioFormDialog.vue";
import MesaQRCodeTab from "./MesaQRCodeTab.vue";
import EmbalagensTab from "./EmbalagensTab.vue";
import RecebimentoItensTab from "./RecebimentoItensTab.vue";
import ProdutoHarmonizacaoTab from "./ProdutoHarmonizacaoTab.vue";
import ProdutoDisponibilidadeTab from "./ProdutoDisponibilidadeTab.vue";
import SubcategoriaDisponibilidadeTab from "./SubcategoriaDisponibilidadeTab.vue";
import ProdutoPromocoesTab from "./ProdutoPromocoesTab.vue";
import ComprovanteFiscalTab from "./ComprovanteFiscalTab.vue";

// Importar o composable de upload
import { useFileUpload } from "@/composables/useFileUpload";

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  registro: {
    type: Object,
    default: () => ({}),
  },
  form_definitions: {
    type: [Array, Object],
    required: true,
  },
  config: {
    type: Object,
    default: () => ({
      title: {
        new: "Novo Registro",
        edit: "Editar Registro",
      },
      labels: {
        save: "Salvar",
        cancel: "Cancelar",
      },
      style: {
        minWidth: "500px",
        maxWidth: "70vw",
      },
      features: {
        tabs: true,
        validation: true,
        transformation: true,
        dependencies: true,
        customComponents: true,
      },
      validators: {},
      transformers: {},
      components: {},
      hooks: {},
    }),
  },
  dialogStyle: {
    type: Object,
    default: () => ({
      width: "800px",
      maxWidth: "95vw",
      maxHeight: "90vh",
    }),
  },
  fullscreenMobile: {
    type: Boolean,
    default: true,
  },
});

// Emits
const emit = defineEmits(["update:modelValue", "salvar-registro", "fechar-dialogo", "field-change", "tab-change", "custom-event"]);

// Composables
const $q = useQuasar();
const { apiRequest } = useApiRequest();
const { uploadedFiles, uploadMultipleFiles, clearAllFiles } = useFileUpload();

// State
const formRef = ref(null);
const formData = ref({});
const errors = ref({});
const saving = ref(false);
const activeTab = ref("");
const fieldStates = reactive({});

// Registry para componentes customizados
const componentRegistry = new Map([
  ["VencimentosTab", VencimentosTab],
  ["RecebimentosTab", RecebimentosTab],
  ["CartaoJurosManager", CartaoJurosManager],
  ["PermissoesTab", PermissoesTab],
  ["GrupoClienteDescontosTab", GrupoClienteDescontosTab],
  ["ProdutoVariacoesTab", ProdutoVariacoesTab],
  ["ProdutoPrecificacaoTab", ProdutoPrecificacaoTab],
  ["ProdutoMidiaTab", ProdutoMidiaTab],
  ["ProdutoValidadeTab", ProdutoValidadeTab],
  ["ProdutoSignageTab", ProdutoSignageTab],
  ["ProdutoFichaTecnicaTab", ProdutoFichaTecnicaTab],
  ["ParcelasCrediarioTab", ParcelasCrediarioTab],
  ["DocumentoFiscalTab", DocumentoFiscalTab],
  ["ComprovanteFiscalTab", ComprovanteFiscalTab],
  ["InventarioFormDialog", InventarioFormDialog],
  ["MesaQRCodeTab", MesaQRCodeTab],
  ["EmbalagensTab", EmbalagensTab],
  ["RecebimentoItensTab", RecebimentoItensTab],
  ["ProdutoHarmonizacaoTab", ProdutoHarmonizacaoTab],
  ["ProdutoDisponibilidadeTab", ProdutoDisponibilidadeTab],
  ["SubcategoriaDisponibilidadeTab", SubcategoriaDisponibilidadeTab],
  ["ProdutoPromocoesTab", ProdutoPromocoesTab],
]);

// Registry para validadores customizados
const validatorRegistry = new Map([
  ["required", (val) => (val !== null && val !== undefined && val !== "") || "Campo obrigatório"],
  ["email", (val) => /^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$/.test(val) || "E-mail inválido"],
  ["phone", (val) => /^\(\d{2}\) \d{5}-\d{4}$/.test(val) || "Telefone inválido"],
  // Adicionar outros validadores conforme necessário
]);

// Computed
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const isEditing = computed(() => !!formData.value?.id);

const TECHNICAL_TITLE_FIELDS = new Set([
  "id",
  "createdat",
  "created_at",
  "updatedat",
  "updated_at",
  "deletedat",
  "deleted_at",
  "version",
  "createdby",
  "created_by",
  "updatedby",
  "updated_by",
  "deletedby",
  "deleted_by",
  "imageurl",
  "image_url",
  "imagepath",
  "image_path",
]);

function normalizeFieldName(name) {
  return String(name || "")
    .trim()
    .toLowerCase();
}

function getFirstMeaningfulTitleValue(data) {
  if (!data || typeof data !== "object") return null;

  for (const [rawFieldName, rawValue] of Object.entries(data)) {
    const fieldName = normalizeFieldName(rawFieldName);
    if (TECHNICAL_TITLE_FIELDS.has(fieldName)) continue;
    if (typeof rawValue !== "string") continue;

    const value = rawValue.trim();
    if (value) return value;
  }

  return null;
}

function truncateRecordTitle(value) {
  if (value.length <= 40) return value;
  return `${value.slice(0, 40)}...`;
}

function buildEditRecordTitle(data) {
  const recordId = data?.id;
  if (recordId === null || recordId === undefined || recordId === "") return null;

  const firstMeaningfulValue = getFirstMeaningfulTitleValue(data);
  if (!firstMeaningfulValue) return null;

  return `#${recordId} - ${truncateRecordTitle(firstMeaningfulValue)}`;
}

const computedTitle = computed(() => {
  if (typeof props.config.title === "string") return props.config.title;

  if (!isEditing.value) {
    return props.config.title?.new || "Novo Registro";
  }

  return buildEditRecordTitle(formData.value) || props.config.title?.edit || "Editar Registro";
});

const isMobile = computed(() => {
  return $q.screen.lt.md; // true para telas menores que 'md' (< 1024px)
});

const cardStyle = computed(() => {
  // Em mobile, usar 100% da largura disponível (se fullscreenMobile for true)
  if (isMobile.value && props.fullscreenMobile) {
    return {
      width: '100%',
      maxWidth: '100%',
      height: '100%',
      maxHeight: '100%',
    };
  }

  // Em desktop ou mobile sem fullscreen, usar configuração personalizada ou padrão
  return {
    width: props.dialogStyle?.width || props.config.style?.width || '800px',
    maxWidth: props.dialogStyle?.maxWidth || props.config.style?.maxWidth || '95vw',
    maxHeight: props.dialogStyle?.maxHeight || props.config.style?.maxHeight || '90vh',
  };
});

const hasTabStructure = computed(() => {
  if (!props.config.features?.tabs) return false;
  return props.form_definitions?.type === "tab-group" || props.form_definitions?.some?.((def) => def.type === "tab-group");
});

const formStructure = computed(() => {
  if (hasTabStructure.value) {
    return Array.isArray(props.form_definitions) ? props.form_definitions.find((def) => def.type === "tab-group") : props.form_definitions;
  }
  return { fields: props.form_definitions };
});

const visibleTabs = computed(() => {
  if (!hasTabStructure.value) return [];

  return (formStructure.value.tabs || []).filter((tab) => {
    // 1) Compatibilidade: função showIf no cliente
    if (tab.showIf && typeof tab.showIf === "function") {
      return tab.showIf(formData.value);
    }

    // 2) Nova: visibilityCondition como string vinda do form-builder/DB
    if (tab.visibilityCondition) {
      const result = evaluateVisibilityCondition(tab.visibilityCondition, formData.value, isEditing.value);
      if (!result) return false;
    }

    // 3) Compatibilidade: showOnEdit (se fornecido)
    if (tab.showOnEdit && !isEditing.value) {
      return false;
    }

    return true;
  });
});

const visibleFields = computed(() => {
  if (hasTabStructure.value) return [];

  const fields = Array.isArray(props.form_definitions) ? props.form_definitions : props.form_definitions.fields || [];

  return processFields(fields);
});

const allFields = computed(() => {
  if (!hasTabStructure.value) return visibleFields.value;

  let fields = [];
  visibleTabs.value.forEach((tab) => {
    if (tab.fields) {
      fields = [...fields, ...processFields(tab.fields)];
    }
  });
  return fields;
});

const fieldDependencies = computed(() => {
  const deps = {};
  allFields.value.forEach((field) => {
    if (field.dependsOn) {
      deps[field.dependsOn] = deps[field.dependsOn] || [];
      deps[field.dependsOn].push(field);
    }
  });
  return deps;
});

// Methods
function evaluateVisibilityCondition(condition, formData, isEditing = false) {
  if (!condition || typeof condition !== 'string') {
    return true; // Mostrar por padrão se condição inválida
  }

  try {
    // Sanitização básica - verificar padrões perigosos
    const dangerousPatterns = ['eval', 'Function', 'constructor', 'prototype', '__proto__', 'import', 'require'];
    const lowerCondition = condition.toLowerCase();
    
    for (const pattern of dangerousPatterns) {
      if (lowerCondition.includes(pattern.toLowerCase())) {
        console.warn('Condição de visibilidade contém padrão perigoso:', pattern, 'em:', condition);
        return true; // Mostrar por segurança
      }
    }

    // Criar função com escopo limitado e strict mode
    const fn = new Function('formData', 'data', 'isEditing', `
      "use strict";
      return ${condition};
    `);
    
    const result = fn(formData, formData, isEditing);
    return Boolean(result);
  } catch (error) {
    console.warn('Erro ao avaliar condição de visibilidade:', condition, error.message);
    return true; // Mostrar campo em caso de erro
  }
}

function processFields(fields) {
  return fields.filter((field) => {
    // 1. Processar showIf existente (manter compatibilidade)
    if (field.showIf && typeof field.showIf === "function") {
      return field.showIf(formData.value);
    }
    
    // 2. NOVO: Processar visibilityCondition do form-builder
    if (field.visibilityCondition) {
      console.log('🔍 Avaliando visibilityCondition para campo:', field.name, 'condição:', field.visibilityCondition, 'formData:', formData.value);
      const result = evaluateVisibilityCondition(field.visibilityCondition, formData.value, isEditing.value);
      console.log('✅ Resultado da avaliação:', result);
      return result;
    }
    
    // 3. Lógica showOnEdit (manter existente)
    if (field.showOnEdit === true && !isEditing.value) {
      return false;
    }
    if (field.showOnEdit === false && isEditing.value) {
      return false;
    }
    return true;
  });
}

function getTabFields(tab) {
  if (!tab.fields) return [];
  return processFields(tab.fields);
}

function resolveComponent(componentName) {
  console.log("Resolvendo componente:", componentName);

  // Primeiro verifica no registry local
  if (componentRegistry.has(componentName)) {
    console.log("Componente encontrado no registry local");
    return componentRegistry.get(componentName);
  }

  // Depois verifica nos componentes customizados via config
  if (props.config.components?.[componentName]) {
    console.log("Componente encontrado via config");
    return props.config.components[componentName];
  }

  // Por fim, retorna o nome do componente (assumindo que está registrado globalmente)
  console.log("Retornando nome do componente (registro global esperado)");
  return componentName;
}

function resolveTabProps(tab) {
  if (!tab.props) return {};

  const resolvedProps = { ...tab.props };

  // Resolver propriedades dinâmicas
  Object.keys(resolvedProps).forEach((key) => {
    if (typeof resolvedProps[key] === "string" && resolvedProps[key].startsWith("$")) {
      const path = resolvedProps[key].substring(1);
      resolvedProps[key] = getNestedValue(formData.value, path);
    }
  });

  return resolvedProps;
}

function getNestedValue(obj, path) {
  return path.split(".").reduce((acc, part) => acc?.[part], obj);
}

function updateFieldValue(fieldName, value) {
  console.log(`Atualizando campo ${fieldName}:`, value);

  // Converter para número se o campo for do tipo number
  const field = allFields.value.find((f) => f.name === fieldName);
  if (field && field.component === "q-input" && field.props?.type === "number") {
    value = value !== "" && value !== null && value !== undefined ? Number(value) : value;
    console.log(`Campo ${fieldName} convertido para número:`, value);
  }

  formData.value[fieldName] = value;

  // Executar hook onFieldChange se existir
  if (props.config.hooks?.onFieldChange) {
    props.config.hooks.onFieldChange(fieldName, value, formData.value);
  }

  // Emitir evento
  emit("field-change", { field: fieldName, value, data: formData.value });

  // Verificar dependências
  if (fieldDependencies.value[fieldName]) {
    updateDependentFields(fieldName, value);
  }
}

async function updateDependentFields(parentField, parentValue) {
  const dependents = fieldDependencies.value[parentField] || [];

  for (const field of dependents) {
    // Atualizar estado do campo
    if (!fieldStates[field.name]) {
      fieldStates[field.name] = {};
    }

    fieldStates[field.name].disabled = !parentValue;

    if (!parentValue) {
      formData.value[field.name] = null;
      fieldStates[field.name].options = [];
      continue;
    }

    // Se tem endpoint dependente, carregar opções
    if (field.dependsOnEndpoint) {
      fieldStates[field.name].loading = true;

      try {
        const endpoint = field.dependsOnEndpoint.replace("{value}", parentValue);
        const response = await apiRequest(endpoint);

        // Suportar diferentes formatos de resposta
        let options = [];
        if (response.data && Array.isArray(response.data)) {
          options = response.data;
        } else if (Array.isArray(response)) {
          options = response;
        } else if (response.success && response.data) {
          options = response.data;
        }

        fieldStates[field.name].options = options;
      } catch (error) {
        console.error(`Erro ao carregar opções para ${field.name}:`, error);
        fieldStates[field.name].options = [];
      } finally {
        fieldStates[field.name].loading = false;
      }
    }
  }
}

// Função para carregar opções de campos com optionsEndpoint
async function loadFieldOptions(field) {
  if (!field.optionsEndpoint) return;

  const stateName = field.name;

  if (!fieldStates[stateName]) {
    fieldStates[stateName] = reactive({
      loading: false,
      disabled: false,
      options: [],
    });
  }

  fieldStates[stateName].loading = true;

  try {
    const response = await apiRequest(field.optionsEndpoint);

    // Suportar diferentes formatos de resposta
    let options = [];

    if (response.data && Array.isArray(response.data)) {
      options = response.data;
    } else if (Array.isArray(response)) {
      options = response;
    } else if (response.success && response.data) {
      options = response.data;
    }

    fieldStates[stateName].options = options;
  } catch (error) {
    console.error(`Erro ao carregar opções para ${field.name}:`, error);
    fieldStates[stateName].options = [];

    $q.notify({
      type: "negative",
      message: `Erro ao carregar opções de ${field.label || field.name}`,
    });
  } finally {
    fieldStates[stateName].loading = false;
  }
}

// Função para carregar todas as opções iniciais
async function loadInitialOptions() {
  const promises = [];

  for (const field of allFields.value) {
    if (field.optionsEndpoint && field.component === "q-select") {
      promises.push(loadFieldOptions(field));
    }
  }

  await Promise.all(promises);
}

function onCustomComponentUpdate(newValue) {
  console.log("Componente customizado atualizou:", newValue);
  Object.assign(formData.value, newValue);

  // Forçar reatividade
  nextTick(() => {
    // Trigger para garantir que mudanças sejam detectadas
  });
}

function handleFieldEvent(event) {
  console.log("handleFieldEvent recebido:", event);
  const { type, field, data, url, action } = event;

  switch (type) {
    case "file-selected":
      console.log(`Arquivo selecionado para ${field}:`, data);
      break;
    case "file-uploaded":
      if (data?.url || data?.[field]) {
        formData.value[field] = data.url || data[field];
      }
      break;
    case "file-removed":
      formData.value[field] = null;
      break;
    case "custom-action":
      emit("custom-event", event);
      break;
    default:
      console.log("Tipo de evento não tratado:", type);
  }
}

async function transformData(data, direction = "toForm") {
  let transformed = { ...data };

  // Aplicar transformador customizado se existir
  if (props.config.transformers?.[direction]) {
    transformed = await props.config.transformers[direction](transformed);
  }

  // Aplicar transformação baseada na estrutura
  if (formStructure.value.dataTransformation) {
    if (direction === "toForm") {
      transformed = flattenData(transformed, formStructure.value.dataTransformation);
    } else {
      transformed = nestData(transformed, formStructure.value.dataTransformation);
    }
  }

  return transformed;
}

function flattenData(data, transformation) {
  const flattened = { ...data };

  if (transformation.nestedFields) {
    Object.entries(transformation.nestedFields).forEach(([nestedObject, fields]) => {
      if (data[nestedObject]) {
        fields.forEach((field) => {
          if (data[nestedObject][field] !== undefined) {
            flattened[field] = data[nestedObject][field];
          }
        });
        delete flattened[nestedObject];
      }
    });
  }

  return flattened;
}

function nestData(data, transformation) {
  const nested = { ...data };

  if (transformation.nestedFields) {
    Object.entries(transformation.nestedFields).forEach(([nestedObject, fields]) => {
      const nestedData = {};
      let hasData = false;

      fields.forEach((field) => {
        if (data[field] !== undefined) {
          nestedData[field] = data[field];
          hasData = true;
          delete nested[field];
        }
      });

      if (hasData) {
        nested[nestedObject] = nestedData;
      }
    });
  }

  return nested;
}

async function validate() {
  errors.value = {};

  // Validação via q-form
  const isValid = await formRef.value.validate();

  // Validações customizadas
  if (props.config.hooks?.onValidate) {
    const customErrors = await props.config.hooks.onValidate(formData.value);
    if (customErrors && Object.keys(customErrors).length > 0) {
      Object.assign(errors.value, customErrors);
      return false;
    }
  }

  return isValid;
}

async function handleSubmit() {
  saving.value = true;

  try {
    // Hook beforeSave
    if (props.config.hooks?.beforeSave) {
      const proceed = await props.config.hooks.beforeSave(formData.value);
      if (!proceed) {
        saving.value = false;
        return;
      }
    }

    // Validar
    const isValid = await validate();
    if (!isValid) {
      saving.value = false;
      return;
    }

    // Transformar dados para o backend
    const dataToSave = await transformData(formData.value, "toBackend");

    // Remover campos de arquivo (serão enviados separadamente)
    const fileFields = allFields.value.filter((f) => f.component === "QFile").map((f) => f.name);

    // Preservar valores de arquivos existentes que não foram alterados
    fileFields.forEach((field) => {
      // Se tem valor no formData original mas não é um File (é uma URL/string)
      if (formData.value[field] && typeof formData.value[field] === "string") {
        // Manter o valor existente
        dataToSave[field] = formData.value[field];
      } else if (formData.value[field] instanceof File) {
        // Se é um arquivo novo, remover (será enviado via upload)
        delete dataToSave[field];
      } else {
        // Se não tem valor, remover
        delete dataToSave[field];
      }
    });

    // Emitir evento de salvar
    let callbackExecuted = false;

    emit("salvar-registro", dataToSave, async (savedRecord) => {
      callbackExecuted = true;

      try {
        // Upload de arquivos pendentes para novos registros
        if (!isEditing.value && Object.keys(uploadedFiles.value).length > 0 && savedRecord?.id) {
          // Mapear endpoints dos campos de arquivo
          const fieldEndpoints = {};
          allFields.value.forEach((field) => {
            if (field.component === "QFile" && field.uploadEndpoint) {
              fieldEndpoints[field.name] = field.uploadEndpoint;
            }
          });

          await uploadMultipleFiles(savedRecord.id, fieldEndpoints);
        }

        // Hook afterSave
        if (props.config.hooks?.afterSave) {
          await props.config.hooks.afterSave(savedRecord);
        }

        // Notificar sucesso
        $q.notify({
          type: "positive",
          message: isEditing.value
            ? props.config.messages?.updateSuccess || "Registro atualizado com sucesso!"
            : props.config.messages?.createSuccess || "Registro criado com sucesso!",
        });

        handleCancel();
      } catch (error) {
        $q.notify({
          type: "negative",
          message: error.message || "Erro ao processar registro",
        });
      } finally {
        saving.value = false;
      }
    });

    // Timeout de segurança
    setTimeout(() => {
      if (!callbackExecuted && saving.value) {
        saving.value = false;
      }
    }, 500);
  } catch (error) {
    saving.value = false;
    $q.notify({
      type: "negative",
      message: error.message || "Erro ao salvar registro",
    });
  }
}

function handleCancel() {
  emit("update:modelValue", false);
  emit("fechar-dialogo");

  // Limpar estados
  formData.value = {};
  errors.value = {};
  activeTab.value = "";

  // Limpar arquivos do composable
  clearAllFiles();

  // Limpar estados de campos
  Object.keys(fieldStates).forEach((key) => {
    delete fieldStates[key];
  });
}

// Watchers
watch(
  () => props.registro,
  async (newValue) => {
    console.log("Registro mudou:", newValue);

    if (!newValue) {
      formData.value = {};
      return;
    }

    // Transformar dados para o formulário
    formData.value = await transformData(newValue, "toForm");

    // Definir aba ativa
    if (hasTabStructure.value && visibleTabs.value.length > 0) {
      activeTab.value = visibleTabs.value[0].name;
    }

    // Carregar opções de campos com optionsEndpoint
    await loadInitialOptions();

    // Inicializar campos dependentes
    await nextTick();

    for (const field of allFields.value) {
      if (field.dependsOn) {
        const parentValue = formData.value[field.dependsOn];
        if (parentValue) {
          await updateDependentFields(field.dependsOn, parentValue);
        }
      }
    }
  },
  { immediate: true, deep: true }
);

watch(
  () => props.registro,
  (newValue) => {
    if (newValue) {
      formData.value = { ...newValue };
    } else {
      formData.value = {};
    }

    // Garantir arrays para campos de tabela
    ensureTableFieldArrays();
  },
  { immediate: true }
);

function ensureTableFieldArrays() {
  const checkFields = (fields) => {
    if (!fields) return;

    fields.forEach((field) => {
      if (
        (field.component === "TableField" || field.component === "CHILD_TABLE" || field.fieldType === "table") &&
        (formData.value[field.name] === null || formData.value[field.name] === undefined)
      ) {
        formData.value[field.name] = [];
      }
    });
  };

  // Verificar em diferentes estruturas
  if (props.form_definitions) {
    if (props.form_definitions.type === "tab-group" && props.form_definitions.tabs) {
      props.form_definitions.tabs.forEach((tab) => {
        checkFields(tab.fields);
      });
    } else if (Array.isArray(props.form_definitions)) {
      checkFields(props.form_definitions);
    }
  }
}

watch(activeTab, (newTab, oldTab) => {
  console.log("Aba mudou de", oldTab, "para", newTab);
  console.log("Componente na aba?", visibleTabs.value.find((t) => t.name === newTab)?.component);
  console.log("FormData atual:", formData.value);

  if (newTab !== oldTab && props.config.hooks?.onTabChange) {
    props.config.hooks.onTabChange(newTab, oldTab, formData.value);
  }
  emit("tab-change", { from: oldTab, to: newTab });
});

// Log para debug
watch(
  formData,
  (newVal) => {
    console.log("FormData atualizado:", newVal);
  },
  { deep: true }
);

// Provide para componentes filhos
provide("formContext", {
  formData,
  updateField: updateFieldValue,
  fieldStates,
  isEditing,
});

// Public API
defineExpose({
  validate,
  getFormData: () => formData.value,
  setFormData: (data) => {
    formData.value = data;
  },
  resetForm: () => handleCancel(),
});
</script>

<style scoped>
.q-dialog__inner {
  padding: 16px;
}

.q-card {
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.2), 0 2px 2px rgba(0, 0, 0, 0.14), 0 3px 1px -2px rgba(0, 0, 0, 0.12);
}

.q-tab-panels {
  background: transparent;
}

.q-tab-panel {
  padding: 0;
}

/* Estilos responsivos para mobile */
@media (max-width: 1023px) {
  .q-dialog__inner {
    padding: 0;
  }

  .form-dialog-card {
    border-radius: 0 !important;
  }

  .form-dialog-card .q-card-section {
    padding: 12px;
  }

  .form-dialog-card .q-toolbar {
    min-height: 56px;
  }

  .form-dialog-card .q-card-actions {
    padding: 12px;
    position: sticky;
    bottom: 0;
    background: white;
    border-top: 1px solid rgba(0, 0, 0, 0.12);
    z-index: 1;
  }

  .body--dark .form-dialog-card .q-card-actions {
    background: #1e1e1e;
    border-top-color: rgba(255, 255, 255, 0.12);
  }

  .body--dark .form-dialog-card .q-toolbar {
    background: #2a2a2a !important;
  }

  /* Melhorar espaçamento dos campos em mobile */
  .form-dialog-card .row.q-col-gutter-md {
    margin: -8px;
  }

  .form-dialog-card .row.q-col-gutter-md > * {
    padding: 8px;
  }

  /* Tabs em mobile */
  .q-tabs {
    overflow-x: auto;
  }

  .q-tab {
    min-width: auto;
    padding: 0 12px;
  }
}

/* Ajustes para telas muito pequenas (< 600px) */
@media (max-width: 599px) {
  .form-dialog-card .q-toolbar-title {
    font-size: 1.1rem;
  }

  .form-dialog-card .q-btn {
    padding: 8px 12px;
  }

  /* Fazer tabs scrolláveis horizontalmente */
  .q-tabs {
    flex-wrap: nowrap;
  }
}
</style>
