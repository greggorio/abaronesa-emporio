<template>
  <div>
    <q-btn flat dense round size="sm" @click="showDialog = true">
      <span style="font-family: monospace; font-weight: 600">{JSON}</span>
      <q-tooltip>Editor JSON</q-tooltip>
    </q-btn>

    <q-dialog v-model="showDialog" maximized>
      <q-card class="json-editor-dialog">
        <!-- Header -->
        <q-card-section class="dialog-header">
          <div class="row items-center">
            <q-icon name="data_object" size="24px" class="q-mr-sm" />
            <div class="col">
              <h6 class="text-h6 q-ma-none">Editor JSON</h6>
              <div class="text-caption text-grey-6">Edite a configuração do formulário diretamente</div>
            </div>
            <q-btn flat dense round icon="close" @click="close" />
          </div>
        </q-card-section>

        <q-separator />

        <!-- Editor Content -->
        <q-card-section class="editor-content row q-col-gutter-md">
          <!-- Editor Panel -->
          <div class="col-6">
            <div class="panel-header q-mb-sm">
              <div class="row items-center">
                <div class="col">
                  <h6 class="text-subtitle1 text-weight-medium q-ma-none">Editor</h6>
                </div>
                <div class="col-auto q-gutter-sm">
                  <q-btn flat dense label="Formatar" icon="format_align_left" size="sm" @click="formatJson" />
                  <q-btn flat dense label="Validar" icon="fact_check" size="sm" @click="validateJson" />
                </div>
              </div>
            </div>

            <div class="editor-wrapper">
              <textarea v-model="jsonContent" class="json-textarea" spellcheck="false" @input="handleInput" />

              <!-- Error Message -->
              <transition name="fade">
                <div v-if="error" class="error-message q-mt-sm">
                  <q-icon name="error" size="16px" />
                  <span>{{ error }}</span>
                </div>
              </transition>
            </div>
          </div>

          <!-- Preview Panel -->
          <div class="col-6">
            <div class="panel-header q-mb-sm">
              <div class="row items-center">
                <div class="col">
                  <h6 class="text-subtitle1 text-weight-medium q-ma-none">Preview</h6>
                </div>
                <div class="col-auto">
                  <q-chip :color="isValid ? 'positive' : 'negative'" text-color="white" size="sm" :icon="isValid ? 'check' : 'close'">
                    {{ isValid ? "Válido" : "Inválido" }}
                  </q-chip>
                </div>
              </div>
            </div>

            <div class="preview-wrapper">
              <pre class="json-preview">{{ formattedPreview }}</pre>
            </div>
          </div>
        </q-card-section>

        <!-- Actions -->
        <q-separator />
        <q-card-actions align="right" class="dialog-actions">
          <q-btn flat label="Cancelar" @click="close" />
          <q-btn unelevated label="Aplicar Alterações" color="primary" :disable="!isValid" @click="applyChanges" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useQuasar } from "quasar";
import { useFormBuilderStore } from "@/stores/formBuilderStore";

const $q = useQuasar();
const store = useFormBuilderStore();

const showDialog = ref(false);
const jsonContent = ref("");
const error = ref("");
const parsedJson = ref(null);

// Computed
const isValid = computed(() => {
  return !error.value && parsedJson.value !== null;
});

const formattedPreview = computed(() => {
  if (!isValid.value) return "// JSON inválido";
  return JSON.stringify(parsedJson.value, null, 2);
});

// Watchers
watch(showDialog, (val) => {
  if (val) {
    // Initialize with current value
    jsonContent.value = JSON.stringify(store.formDefinition, null, 2);
    validateJson();
  }
});

// Methods
function handleInput() {
  validateJson();
}

function validateJson() {
  try {
    parsedJson.value = JSON.parse(jsonContent.value);
    error.value = "";
  } catch (e) {
    parsedJson.value = null;
    error.value = e.message;
  }
}

function formatJson() {
  if (isValid.value) {
    jsonContent.value = JSON.stringify(parsedJson.value, null, 2);
  }
}

function applyChanges() {
  if (isValid.value) {
    // Update store directly
    Object.assign(store.formDefinition, parsedJson.value);
    store.isDirty = true;

    $q.notify({
      message: "Configuração atualizada",
      caption: "As alterações foram aplicadas com sucesso",
      color: "positive",
      icon: "check",
    });

    close();
  }
}

function close() {
  showDialog.value = false;
  error.value = "";
  parsedJson.value = null;
}
</script>

<style lang="scss" scoped>
.json-editor-dialog {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.dialog-header {
  background-color: #f8f9fa;

  h6 {
    font-weight: 500;
    color: #333;
  }
}

.editor-content {
  flex: 1;
  overflow: hidden;
  background-color: #f5f5f5;
  padding: 16px;
}

.panel-header {
  h6 {
    color: #333;
  }
}

.editor-wrapper,
.preview-wrapper {
  height: calc(100vh - 280px);
  background-color: white;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
}

.json-textarea {
  width: 100%;
  height: 100%;
  padding: 16px;
  border: none;
  resize: none;
  font-family: "Consolas", "Monaco", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.5;
  color: #333;
  background-color: white;
  outline: none;

  &:focus {
    background-color: #f0f7ff;
  }
}

.json-preview {
  margin: 0;
  padding: 16px;
  height: 100%;
  overflow: auto;
  font-family: "Consolas", "Monaco", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.5;
  color: #333;
  background-color: white;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background-color: #ffebee;
  color: #c62828;
  border-radius: 4px;
  font-size: 13px;
}

.dialog-actions {
  background-color: #f8f9fa;
  padding: 16px;
}

// Transitions
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// Scrollbar styling
.json-preview,
.json-textarea {
  &::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }

  &::-webkit-scrollbar-track {
    background: #f0f0f0;
  }

  &::-webkit-scrollbar-thumb {
    background: #ccc;
    border-radius: 4px;

    &:hover {
      background: #999;
    }
  }
}
</style>
