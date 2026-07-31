<template>
  <div class="central-preview">
    <!-- Preview Header -->
    <div class="preview-header">
      <div class="row items-center">
        <q-icon :name="store.formDefinition.programIcon" size="32px" class="q-mr-md text-primary" />
        <div>
          <h5 class="q-ma-none">{{ store.formDefinition.programName }}</h5>
          <div class="text-caption text-grey-6">Modo de {{ previewData.id ? "Edição" : "Criação" }}</div>
        </div>
      </div>
    </div>

    <!-- Form Container -->
    <q-card flat class="form-container q-mt-lg">
      <!-- Form Tabs -->
      <q-tabs v-model="previewTab" dense class="text-grey-7" active-color="primary" indicator-color="primary" align="left">
        <q-tab v-for="tab in store.formDefinition.tabs" :key="tab.name" :name="tab.name">
          <div class="row items-center no-wrap">
            <q-icon v-if="tab.icon" :name="tab.icon" size="18px" class="q-mr-xs" />
            <span>{{ tab.label }}</span>
          </div>
        </q-tab>
      </q-tabs>

      <q-separator />

      <!-- Tab Panels -->
      <q-tab-panels v-model="previewTab" animated transition-prev="slide-right" transition-next="slide-left">
        <q-tab-panel v-for="tab in store.formDefinition.tabs" :key="tab.name" :name="tab.name">
          <!-- Custom Component Tab -->
          <div v-if="tab.component" class="custom-component-notice">
            <q-icon name="widgets" size="64px" color="primary" />
            <h6 class="q-mt-md q-mb-sm">Componente Customizado</h6>
            <p class="text-body2 text-grey-7">{{ tab.component }}</p>
            <q-chip icon="info" color="blue-1" text-color="blue-9">Esta aba renderizará um componente Vue personalizado</q-chip>
          </div>

          <!-- Fields -->
          <div v-else class="row q-col-gutter-md">
            <div v-for="field in tab.fields" :key="field.name" :class="field.cols || 'col-12'">
              <component
                :is="getFieldComponent(field)"
                v-bind="getFieldProps(field)"
                :model-value="previewData[field.name]"
                @update:model-value="(val) => (previewData[field.name] = val)"
              />
            </div>

            <!-- Empty State -->
            <div v-if="tab.fields.length === 0" class="col-12 empty-tab">
              <q-icon name="inbox" size="48px" color="grey-4" />
              <h6>Nenhum campo nesta aba</h6>
              <p>Arraste campos da lista à esquerda para adicionar</p>
            </div>
          </div>
        </q-tab-panel>
      </q-tab-panels>

      <!-- Form Actions -->
      <q-separator />
      <q-card-actions align="right" class="q-pa-md">
        <q-btn
          v-for="(action, index) in formActions"
          :key="index"
          :flat="action.type !== 'ADD'"
          :unelevated="action.type === 'ADD'"
          :label="action.label"
          :icon="action.icon"
          :color="action.color || 'grey-7'"
          class="q-ml-sm"
        />
      </q-card-actions>
    </q-card>

    <!-- Preview Options -->
    <div class="preview-options q-mt-lg">
      <q-card flat>
        <q-card-section>
          <div class="row items-center">
            <div class="col">
              <h6 class="text-subtitle1 q-ma-none">Opções de Preview</h6>
            </div>
          </div>

          <div class="q-mt-md q-gutter-sm">
            <q-toggle v-model="showFieldHints" label="Mostrar dicas dos campos" color="primary" />
            <q-toggle v-model="fillSampleData" label="Preencher com dados de exemplo" color="primary" @update:model-value="toggleSampleData" />
          </div>
        </q-card-section>
      </q-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch } from "vue";
import { useFormBuilderStore } from "@/stores/formBuilderStore";

const store = useFormBuilderStore();

// Estado
const previewTab = ref("dados-gerais");
const previewData = reactive({});
const showFieldHints = ref(false);
const fillSampleData = ref(false);

// Computed
const formActions = computed(() => {
  return store.formDefinition.actions.filter((action) => !action.inlineOnly);
});

// Watch for tab changes
watch(
  () => store.formDefinition.tabs,
  () => {
    if (store.formDefinition.tabs.length > 0 && !store.formDefinition.tabs.find((t) => t.name === previewTab.value)) {
      previewTab.value = store.formDefinition.tabs[0].name;
    }
  },
  { deep: true }
);

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

  if (field.placeholder) {
    props.placeholder = field.placeholder;
  }

  if (field.required) {
    props.rules = [(val) => !!val || `${field.label} é obrigatório`];
  }

  if (showFieldHints.value) {
    props.hint = `Campo: ${field.name} | Tipo: ${field.type}`;
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

function toggleSampleData(value) {
  if (value) {
    // Preencher com dados de exemplo
    store.formDefinition.tabs.forEach((tab) => {
      tab.fields.forEach((field) => {
        if (field.type === "TEXT") {
          previewData[field.name] = `Exemplo de ${field.label}`;
        } else if (field.type === "NUMBER") {
          previewData[field.name] = Math.floor(Math.random() * 100);
        } else if (field.type === "CURRENCY") {
          previewData[field.name] = (Math.random() * 1000).toFixed(2);
        } else if (field.type === "DATE") {
          previewData[field.name] = new Date().toLocaleDateString("pt-BR");
        } else if (field.type === "EMAIL") {
          previewData[field.name] = "exemplo@email.com";
        } else if (field.type === "PHONE") {
          previewData[field.name] = "(11) 99999-9999";
        } else if (field.type === "CHECKBOX") {
          previewData[field.name] = true;
        } else if (field.type === "SELECT" && field.options && field.options.length > 0) {
          previewData[field.name] = field.options[0].value;
        }
      });
    });
  } else {
    // Limpar dados
    Object.keys(previewData).forEach((key) => {
      delete previewData[key];
    });
  }
}
</script>

<style lang="scss" scoped>
.central-preview {
  max-width: 1200px;
  margin: 0 auto;
}

.preview-header {
  background-color: $grey-1;
  padding: 20px;
  border-radius: 8px;

  h5 {
    font-weight: 500;
    color: $grey-9;
  }
}

.form-container {
  background-color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.custom-component-notice {
  text-align: center;
  padding: 80px 40px;

  h6 {
    margin: 0;
    font-weight: 500;
    color: $grey-8;
  }

  p {
    margin: 0 0 16px 0;
    color: $grey-6;
  }
}

.empty-tab {
  text-align: center;
  padding: 80px 40px;
  color: $grey-5;

  h6 {
    margin: 16px 0 8px 0;
    font-weight: 500;
    color: $grey-7;
  }

  p {
    margin: 0;
    font-size: 14px;
  }
}

.preview-options {
  h6 {
    font-weight: 500;
    color: $grey-8;
  }
}
</style>
