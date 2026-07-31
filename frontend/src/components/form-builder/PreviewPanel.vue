<template>
  <q-card class="preview-panel" flat>
    <q-card-section class="q-pb-sm">
      <div class="row items-center">
        <div class="col">
          <div class="text-subtitle1 text-weight-medium">Visualização</div>
          <div class="text-caption text-grey-6">Preview em tempo real do formulário</div>
        </div>
      </div>
    </q-card-section>

    <!-- Mode Selector -->
    <q-card-section class="q-pt-none">
      <q-btn-group spread unelevated class="mode-selector">
        <q-btn
          :label="'Formulário'"
          :icon="'article'"
          :color="previewMode === 'form' ? 'primary' : 'grey-3'"
          :text-color="previewMode === 'form' ? 'white' : 'grey-8'"
          @click="previewMode = 'form'"
          no-caps
        />
        <q-btn
          :label="'Tabela'"
          :icon="'table_chart'"
          :color="previewMode === 'table' ? 'primary' : 'grey-3'"
          :text-color="previewMode === 'table' ? 'white' : 'grey-8'"
          @click="previewMode = 'table'"
          no-caps
        />
        <q-btn
          :label="'JSON'"
          :icon="'code'"
          :color="previewMode === 'json' ? 'primary' : 'grey-3'"
          :text-color="previewMode === 'json' ? 'white' : 'grey-8'"
          @click="previewMode = 'json'"
          no-caps
        />
      </q-btn-group>
    </q-card-section>

    <q-separator />

    <!-- Preview do Formulário -->
    <q-card-section v-if="previewMode === 'form'" class="preview-content">
      <div v-if="!store.selectedEntity" class="text-center text-grey-6 q-pa-xl">
        <q-icon name="visibility" size="48px" />
        <div class="q-mt-md">Selecione uma entidade para visualizar o preview</div>
      </div>

      <div v-else>
        <!-- Simular o GenericFormDialog -->
        <div class="preview-form-header q-mb-md">
          <div class="row items-center">
            <q-icon :name="store.formDefinition.programIcon" size="24px" class="q-mr-sm" />
            <span class="text-h6">{{ store.formDefinition.programName }}</span>
          </div>
        </div>

        <!-- Tabs -->
        <q-tabs v-model="previewTab" dense class="text-grey" active-color="primary" indicator-color="primary" align="left">
          <q-tab v-for="tab in store.formDefinition.tabs" :key="tab.name" :name="tab.name" :label="tab.label" :icon="tab.icon" />
        </q-tabs>

        <q-separator />

        <q-tab-panels v-model="previewTab" animated>
          <q-tab-panel v-for="tab in store.formDefinition.tabs" :key="tab.name" :name="tab.name">
            <div v-if="tab.component" class="text-center q-pa-xl bg-grey-2 rounded-borders">
              <q-icon name="widgets" size="48px" color="primary" />
              <div class="text-h6 q-mt-md">Componente Customizado</div>
              <div class="text-caption">{{ tab.component }}</div>
              <q-btn flat dense label="Configurar aba" icon="settings" size="sm" class="q-mt-md" @click="configureTab(tab)" />
              <div class="text-caption text-grey q-mt-sm">Campos não podem ser adicionados em abas com componente customizado</div>
            </div>

            <div v-else class="row q-col-gutter-md">
              <div v-for="field in tab.fields" :key="field.name" :class="field.cols || 'col-12'">
                <component
                  :is="getFieldComponent(field)"
                  v-bind="getFieldProps(field)"
                  :model-value="previewData[field.name]"
                  @update:model-value="(val) => (previewData[field.name] = val)"
                />
              </div>
            </div>

            <div v-if="tab.fields.length === 0 && !tab.component" class="text-center q-pa-xl text-grey-6">
              <q-icon name="inbox" size="48px" />
              <div class="q-mt-md">Nenhum campo nesta aba</div>
            </div>
          </q-tab-panel>
        </q-tab-panels>

        <!-- Actions -->
        <q-separator class="q-mt-md" />
        <q-card-actions align="right">
          <q-btn
            v-for="(action, index) in store.formDefinition.actions"
            :key="index"
            flat
            :label="action.label"
            :icon="action.icon"
            :color="action.color"
            class="q-ml-sm"
          />
        </q-card-actions>
      </div>
    </q-card-section>

    <!-- Preview da Tabela -->
    <q-card-section v-else-if="previewMode === 'table'" class="preview-content">
      <q-table :columns="tableColumns" :rows="sampleRows" row-key="id" flat dense>
        <template v-slot:top>
          <div class="col">
            <div class="text-h6">Lista de {{ store.formDefinition.programName }}</div>
          </div>
          <q-space />
          <q-btn
            v-for="(action, index) in tableActions"
            :key="index"
            flat
            :label="action.label"
            :icon="action.icon"
            :color="action.color"
            size="sm"
            class="q-ml-sm"
          />
        </template>
      </q-table>
    </q-card-section>

    <!-- Preview JSON -->
    <q-card-section v-else-if="previewMode === 'json'" class="preview-content">
      <q-input :model-value="formDefinitionJson" type="textarea" filled readonly class="json-preview" :rows="20" />
      <q-btn label="Copiar JSON" icon="content_copy" color="primary" class="q-mt-sm" @click="copyJson" />
    </q-card-section>
  </q-card>
</template>

<script setup>
import { ref, computed, reactive } from "vue";
import { useQuasar } from "quasar";
import { useFormBuilderStore } from "@/stores/formBuilderStore";

const $q = useQuasar();
const store = useFormBuilderStore();

const previewMode = ref("form");
const previewTab = ref("dados-gerais");
const previewData = reactive({});

// Computed
const tableColumns = computed(() => {
  return store.formDefinition.tableColumns.map((col) => ({
    name: col.name,
    label: col.label,
    field: col.name,
    align: col.align || "left",
    sortable: col.sortable !== false,
  }));
});

const tableActions = computed(() => {
  return store.formDefinition.actions.filter((action) => !action.inlineOnly && !action.onDoubleClick);
});

const sampleRows = computed(() => {
  // Gerar dados de exemplo
  const rows = [];
  for (let i = 1; i <= 3; i++) {
    const row = { id: i };
    store.formDefinition.tableColumns.forEach((col) => {
      if (col.type === "NUMBER") {
        row[col.name] = Math.floor(Math.random() * 100);
      } else if (col.type === "CURRENCY") {
        row[col.name] = (Math.random() * 1000).toFixed(2);
      } else if (col.type === "DATE") {
        row[col.name] = new Date().toLocaleDateString();
      } else if (col.type === "BOOLEAN") {
        row[col.name] = Math.random() > 0.5;
      } else {
        row[col.name] = `${col.label} ${i}`;
      }
    });
    rows.push(row);
  }
  return rows;
});

const formDefinitionJson = computed(() => {
  const definition = {
    ...store.formDefinition,
    tabs: store.formDefinition.tabs.map((tab) => ({
      ...tab,
      fields: tab.fields.map((field) => {
        // Limpar propriedades vazias
        const cleanField = {};
        Object.entries(field).forEach(([key, value]) => {
          if (value !== null && value !== undefined && value !== "") {
            if (typeof value === "object" && Object.keys(value).length === 0) {
              return;
            }
            cleanField[key] = value;
          }
        });
        return cleanField;
      }),
    })),
  };

  return JSON.stringify(definition, null, 2);
});

// Methods
function getFieldComponent(field) {
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
    FILE: "q-file",
    EMAIL: "q-input",
    URL: "q-input",
    PHONE: "q-input",
  };

  return componentMap[field.type] || "q-input";
}

function getFieldProps(field) {
  const props = { ...field.props };

  props.label = field.label;
  props.filled = true;
  props.dense = true;

  if (field.placeholder) {
    props.placeholder = field.placeholder;
  }

  if (field.required) {
    props.rules = [(val) => !!val || `${field.label} é obrigatório`];
  }

  if (field.type === "TEXTAREA") {
    props.type = "textarea";
    props.rows = props.rows || 3;
  } else if (field.type === "NUMBER" || field.type === "CURRENCY") {
    props.type = "number";
  } else if (field.type === "EMAIL") {
    props.type = "email";
  } else if (field.type === "URL") {
    props.type = "url";
  } else if (field.type === "PHONE") {
    props.mask = "(##) #####-####";
  } else if (field.type === "DATE") {
    props.mask = "##/##/####";
  }

  if (field.type === "SELECT" && field.options) {
    props.options = field.options;
  }

  return props;
}

function configureTab(tab) {
  $q.dialog({
    title: "Configurar Aba",
    message: "Edite as propriedades da aba",
    position: "top",
    options: {
      type: "form",
      model: { ...tab },
      items: [
        { label: "Nome", model: "name", type: "text", disable: true },
        { label: "Label", model: "label", type: "text" },
        { label: "Ícone", model: "icon", type: "text" },
        {
          label: "Componente customizado",
          model: "component",
          type: "text",
          hint: "Nome do componente Vue (ex: PermissoesTab)",
        },
      ],
    },
    cancel: true,
  }).onOk((data) => {
    // Não temos acesso direto ao store aqui, então apenas notificamos
    $q.notify({
      message: "Use o botão de configuração no FormDesigner para editar a aba",
      color: "info",
    });
  });
}

function copyJson() {
  navigator.clipboard.writeText(formDefinitionJson.value);
  $q.notify({
    message: "JSON copiado para a área de transferência",
    color: "positive",
    icon: "check",
    timeout: 1000,
  });
}
</script>

<style lang="scss" scoped>
.preview-panel {
  height: fit-content;

  .mode-selector {
    width: 100%;
    border: 1px solid $grey-4;
  }

  .preview-content {
    max-height: 400px;
    overflow-y: auto;
    background-color: $grey-1;
  }

  .preview-form-header {
    padding: 12px;
    background-color: $grey-2;
    border-radius: 4px;
  }

  .json-preview {
    font-family: "Consolas", "Monaco", monospace;
    font-size: 12px;

    :deep(textarea) {
      line-height: 1.4;
    }
  }
}
</style>
