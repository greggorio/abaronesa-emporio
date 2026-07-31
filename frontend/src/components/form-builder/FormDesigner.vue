<template>
  <div class="form-designer">
    <!-- Container Principal -->
    <q-card flat class="designer-card">
      <!-- Header com ícone e título -->
      <div class="form-header">
        <q-icon :name="store.formDefinition.programIcon" size="28px" class="text-grey-7" />
        <div class="form-title">
          <h5 class="q-ma-none">{{ store.formDefinition.programName }}</h5>
          <div class="text-caption text-grey-6">{{ store.formDefinition.entityType }}</div>
        </div>
      </div>

      <q-separator />

      <!-- Configurações do Diálogo -->
      <q-expansion-item
        icon="settings_overscan"
        label="Configurações do Diálogo"
        header-class="text-grey-8"
        dense
        class="dialog-config-section q-ma-md"
      >
        <q-card flat class="q-pa-sm">
          <div class="row q-col-gutter-md">
            <div class="col-6">
              <q-input
                v-model="store.formDefinition.dialogConfig.width"
                label="Largura"
                hint="Ex: 1400px, 90vw"
                dense
                filled
                @update:model-value="store.markDirty()"
              />
            </div>
            <div class="col-6">
              <q-input
                v-model="store.formDefinition.dialogConfig.maxWidth"
                label="Largura Máxima"
                hint="Ex: 95vw, 100vw"
                dense
                filled
                @update:model-value="store.markDirty()"
              />
            </div>
            <div class="col-6">
              <q-input
                v-model="store.formDefinition.dialogConfig.maxHeight"
                label="Altura Máxima"
                hint="Ex: 90vh"
                dense
                filled
                @update:model-value="store.markDirty()"
              />
            </div>
            <div class="col-6">
              <q-toggle
                v-model="store.formDefinition.dialogConfig.fullscreenMobile"
                label="Fullscreen em Mobile"
                @update:model-value="store.markDirty()"
              />
            </div>
          </div>
        </q-card>
      </q-expansion-item>

      <q-separator />

      <!-- Tabs do Formulário -->
      <q-tabs v-model="store.selectedTab" dense class="form-tabs" active-color="primary" indicator-color="primary" align="left" narrow-indicator>
        <q-tab v-for="tab in store.formDefinition.tabs" :key="tab.name" :name="tab.name" class="tab-item">
          <div class="row items-center no-wrap">
            <span>{{ tab.label }}</span>
            <q-badge v-if="tab.fields.length > 0" :label="tab.fields.length" color="grey-5" text-color="white" class="q-ml-sm" rounded />
          </div>
        </q-tab>
      </q-tabs>

      <q-separator />

      <!-- Conteúdo das Tabs -->
      <q-tab-panels v-model="store.selectedTab" animated transition-prev="slide-right" transition-next="slide-left" class="tab-panels">
        <q-tab-panel v-for="tab in store.formDefinition.tabs" :key="tab.name" :name="tab.name" class="q-pa-none">
          <!-- Componente Customizado -->
          <div v-if="tab.component" class="custom-component-notice">
            <q-icon name="widgets" size="64px" color="grey-5" />
            <h6 class="q-mt-md q-mb-sm">Componente Customizado</h6>
            <p class="text-body2 text-grey-7">{{ tab.component }}</p>
            <q-chip icon="info" color="grey-2" text-color="grey-8">Esta aba usa um componente Vue personalizado</q-chip>
          </div>

          <!-- Lista de Campos -->
          <draggable
            v-else
            v-model="tab.fields"
            group="fields"
            item-key="name"
            :animation="200"
            handle=".drag-handle"
            class="fields-container"
            @change="handleFieldsChange"
          >
            <template #item="{ element: field }">
              <div class="field-item" @dblclick="editField(field)">
                <div class="field-content">
                  <!-- Drag Handle -->
                  <div class="drag-handle">
                    <q-icon name="drag_indicator" size="20px" />
                  </div>

                  <!-- Field Info -->
                  <div class="field-info">
                    <div class="field-header">
                      <span class="field-label">{{ field.label }}</span>
                      <q-chip size="sm" color="grey-2" text-color="grey-7" dense class="q-ml-sm">
                        {{ field.name }}
                      </q-chip>
                      <q-chip v-if="field.required" size="sm" color="red-1" text-color="red-9" icon="star" dense class="q-ml-xs">Obrigatório</q-chip>
                    </div>

                    <div class="field-meta">
                      <span class="meta-item">
                        <q-icon name="category" size="14px" />
                        {{ field.type }}
                      </span>
                      <span class="meta-item">
                        <q-icon name="view_column" size="14px" />
                        {{ field.cols }}
                      </span>
                      <span v-if="field.component" class="meta-item">
                        <q-icon name="widgets" size="14px" />
                        {{ field.component }}
                      </span>
                    </div>
                  </div>

                  <!-- Field Actions -->
                  <div class="field-actions">
                    <q-btn flat dense round icon="edit" size="sm" @click.stop="editField(field)">
                      <q-tooltip>Editar campo</q-tooltip>
                    </q-btn>
                    <q-btn flat dense round icon="content_copy" size="sm" @click.stop="duplicateField(field, tab)">
                      <q-tooltip>Duplicar campo</q-tooltip>
                    </q-btn>
                    <q-btn flat dense round icon="delete" size="sm" color="negative" @click.stop="removeField(field, tab)">
                      <q-tooltip>Remover campo</q-tooltip>
                    </q-btn>
                  </div>
                </div>

                <!-- Preview do Campo (opcional) -->
                <div v-if="showFieldPreview" class="field-preview">
                  <component :is="getFieldComponent(field)" v-bind="getFieldProps(field)" :model-value="getFieldDefaultValue(field)" disable dense />
                </div>
              </div>
            </template>

            <!-- Empty State -->
            <template #footer>
              <div v-if="tab.fields.length === 0" class="empty-fields">
                <q-icon name="drag_indicator" size="48px" />
                <h6>Arraste campos aqui</h6>
                <p>Selecione campos disponíveis no painel lateral e arraste para esta área</p>
              </div>
            </template>
          </draggable>
        </q-tab-panel>
      </q-tab-panels>
    </q-card>

    <!-- Toggle Preview -->
    <div class="text-right q-mt-md">
      <q-toggle v-model="showFieldPreview" label="Mostrar preview dos campos" size="sm" color="primary" />
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useQuasar } from "quasar";
import { useFormBuilderStore } from "@/stores/formBuilderStore";
import FieldTypeMapper from "@/services/form-builder/FieldTypeMapper";
import draggable from "vuedraggable";

const $q = useQuasar();
const store = useFormBuilderStore();

const showFieldPreview = ref(false);

// Methods
function getFieldIcon(type) {
  return FieldTypeMapper.getFieldIcon(type);
}

function getFieldComponent(field) {
  // Se tem componente customizado, usa ele
  if (field.component) {
    return field.component;
  }

  const componentMap = {
    TEXT: "q-input",
    NUMBER: "q-input",
    CURRENCY: "q-input",
    DATE: "q-input",
    DATETIME: "q-input",
    TIME: "q-input",
    TEXTAREA: "q-input",
    CHECKBOX: "q-checkbox",
    SELECT: "q-select",
    LOOKUP: "LookupSelect",
    TABLE: "TableField",
    COMPUTED: "q-input",
    FILE: "q-file",
  };

  return componentMap[field.type] || "q-input";
}

function getFieldProps(field) {
  const props = { ...field.props };

  props.label = field.label;
  props.filled = true;

  if (field.placeholder) {
    props.placeholder = field.placeholder;
  }

  if (field.type === "TEXTAREA") {
    props.type = "textarea";
    props.rows = props.rows || 3;
  } else if (field.type === "NUMBER" || field.type === "CURRENCY") {
    props.type = "number";
  }

  return props;
}

function getFieldDefaultValue(field) {
  if (field.type === "CHECKBOX") return false;
  if (field.type === "SELECT") return null;
  if (field.type === "NUMBER" || field.type === "CURRENCY") return 0;
  return "";
}

function editField(field) {
  console.log("Editing field:", field);
  store.selectedField = field;

  // Emit event to parent to open right panel
  const event = new CustomEvent("open-field-editor", { detail: field });
  window.dispatchEvent(event);
}

function duplicateField(field, tab) {
  const newField = {
    ...field,
    name: `${field.name}_copy`,
    label: `${field.label} (Cópia)`,
  };

  const index = tab.fields.findIndex((f) => f.name === field.name);
  tab.fields.splice(index + 1, 0, newField);
  store.isDirty = true;

  $q.notify({
    message: "Campo duplicado",
    color: "info",
    timeout: 1000,
  });
}

function removeField(field, tab) {
  $q.dialog({
    title: "Remover campo",
    message: `Deseja remover o campo "${field.label}"?`,
    cancel: true,
    persistent: true,
    ok: {
      label: "Remover",
      color: "negative",
    },
  }).onOk(() => {
    store.removeFieldFromTab(field.name, tab.name);
    $q.notify({
      message: "Campo removido",
      color: "warning",
      timeout: 1000,
    });
  });
}

function handleFieldsChange() {
  store.isDirty = true;
}
</script>

<style lang="scss" scoped>
.form-designer {
  height: 100%;
}

.designer-card {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.form-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background-color: #fafafa;
}

.form-title {
  h5 {
    font-weight: 500;
    color: #333;
    font-size: 18px;
  }
}

.form-tabs {
  background-color: white;
  text-transform: uppercase;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;

  :deep(.q-tab) {
    color: #666;
  }

  :deep(.q-tab--active) {
    color: $primary;
  }
}

.tab-item {
  text-transform: uppercase;
  font-weight: 500;
  font-size: 12px;
  letter-spacing: 0.5px;
}

.tab-panels {
  min-height: 400px;
  background-color: #fafafa;
}

.fields-container {
  padding: 24px;
  min-height: 350px;
}

.field-item {
  margin-bottom: 12px;
  background-color: white;
  border-radius: 6px;
  border: 1px solid #e0e0e0;
  transition: all 0.2s ease;
  overflow: hidden;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    border-color: #666;

    .drag-handle {
      opacity: 0.6;
    }

    .field-actions {
      opacity: 1;
    }
  }
}

.field-content {
  display: flex;
  align-items: center;
  padding: 16px;
  gap: 12px;
}

.drag-handle {
  cursor: move;
  color: #999;
  opacity: 0;
  transition: opacity 0.2s;
}

.field-info {
  flex: 1;

  .field-header {
    display: flex;
    align-items: center;
    margin-bottom: 4px;

    .field-label {
      font-weight: 500;
      font-size: 14px;
      color: #333;
    }
  }

  .field-meta {
    display: flex;
    align-items: center;
    gap: 16px;
    font-size: 12px;
    color: #666;

    .meta-item {
      display: flex;
      align-items: center;
      gap: 4px;

      .q-icon {
        opacity: 0.6;
      }
    }
  }
}

.field-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.field-preview {
  padding: 0 16px 16px 16px;
  background-color: #f8f9fa;
  border-top: 1px solid #e0e0e0;
}

.empty-fields {
  text-align: center;
  padding: 80px 40px;
  color: #999;

  .q-icon {
    color: #ddd;
    margin-bottom: 16px;
  }

  h6 {
    margin: 0 0 8px 0;
    font-weight: 500;
    color: #666;
  }

  p {
    margin: 0;
    font-size: 14px;
  }
}

.custom-component-notice {
  text-align: center;
  padding: 80px 40px;

  h6 {
    margin: 0;
    font-weight: 500;
    color: #666;
  }

  p {
    margin: 0 0 16px 0;
    color: #999;
  }
}

// Ghost element durante drag
.sortable-ghost {
  opacity: 0.4;
}

.sortable-drag {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15) !important;
}
</style>
