<template>
  <q-card class="action-editor" flat>
    <q-card-section>
      <div class="row items-center">
        <div class="col">
          <div class="text-subtitle1 text-weight-medium">Ações do Formulário</div>
          <div class="text-caption text-grey-6">Configure as ações disponíveis para os usuários</div>
        </div>
        <div class="col-auto">
          <q-btn unelevated color="primary" label="NOVA AÇÃO" icon="add" size="sm" @click="showAddActionDialog" />
        </div>
      </div>
    </q-card-section>

    <q-separator />

    <q-card-section class="q-pa-none">
      <q-list separator>
        <draggable v-model="store.formDefinition.actions" handle=".drag-handle" item-key="type" @change="handleActionsChange">
          <template #item="{ element: action, index }">
            <q-item class="action-item">
              <q-item-section avatar>
                <div class="drag-handle cursor-move">
                  <q-icon name="drag_indicator" color="grey-6" />
                </div>
              </q-item-section>

              <q-item-section avatar>
                <q-icon :name="action.icon" :color="action.color || 'grey-7'" />
              </q-item-section>

              <q-item-section>
                <q-item-label>{{ action.label }}</q-item-label>
                <q-item-label caption>
                  <q-chip size="xs" :color="getActionTypeColor(action.type)" text-color="white">
                    {{ action.type }}
                  </q-chip>
                  <span v-if="action.onDoubleClick" class="q-ml-sm">
                    <q-icon name="mouse" size="xs" />
                    Duplo clique
                  </span>
                  <span v-if="action.requiresSelection" class="q-ml-sm">
                    <q-icon name="check_box" size="xs" />
                    Requer seleção
                  </span>
                  <span v-if="action.opensDialog" class="q-ml-sm">
                    <q-icon name="open_in_new" size="xs" />
                    Abre diálogo
                  </span>
                </q-item-label>
              </q-item-section>

              <q-item-section side>
                <div class="row no-wrap">
                  <q-btn flat dense round icon="edit" size="sm" @click="editAction(action, index)" />
                  <q-btn flat dense round icon="delete" size="sm" color="negative" @click="removeAction(index)" />
                </div>
              </q-item-section>
            </q-item>
          </template>
        </draggable>
      </q-list>
    </q-card-section>

    <div v-if="store.formDefinition.actions.length === 0" class="text-center q-pa-lg text-grey-6">
      <q-icon name="touch_app" size="48px" />
      <div class="q-mt-sm">Nenhuma ação configurada</div>
    </div>
  </q-card>

  <!-- Dialog de Edição de Ação -->
  <q-dialog v-model="showActionDialog" position="standard" persistent>
    <q-card style="width: 600px; max-width: 90vw">
      <q-card-section>
        <div class="text-h6">{{ editingIndex === -1 ? "Nova Ação" : "Editar Ação" }}</div>
      </q-card-section>

      <q-separator />

      <q-card-section class="q-gutter-sm dialog-content">
        <q-select v-model="currentAction.type" label="Tipo de Ação" :options="actionTypes" filled emit-value map-options />

        <q-input v-model="currentAction.label" label="Label" filled />

        <q-input v-model="currentAction.icon" label="Ícone" filled>
          <template v-slot:append>
            <q-icon :name="currentAction.icon || 'help'" />
          </template>
        </q-input>

        <q-select v-model="currentAction.color" label="Cor" :options="colorOptions" filled emit-value map-options>
          <template v-slot:option="scope">
            <q-item v-bind="scope.itemProps">
              <q-item-section avatar>
                <q-icon name="circle" :color="scope.opt.value" />
              </q-item-section>
              <q-item-section>
                <q-item-label>{{ scope.opt.label }}</q-item-label>
              </q-item-section>
            </q-item>
          </template>
        </q-select>

        <!-- Opções Booleanas -->
        <div class="q-mt-md">
          <q-checkbox v-model="currentAction.onDoubleClick" label="Ativar no duplo clique" />
          <q-checkbox v-model="currentAction.requiresSelection" label="Requer item selecionado" />
          <q-checkbox v-model="currentAction.inlineOnly" label="Apenas inline (não aparece no topo)" />
        </div>

        <!-- Confirmação -->
        <q-expansion-item label="Confirmação" icon="warning" class="q-mt-md" v-model="hasConfirmation">
          <q-card>
            <q-card-section>
              <q-input v-model="currentAction.confirmTitle" label="Título da confirmação" filled dense class="q-mb-sm" />
              <q-input v-model="currentAction.confirmMessage" label="Mensagem de confirmação" filled dense type="textarea" rows="2" />
            </q-card-section>
          </q-card>
        </q-expansion-item>

        <!-- Ação Customizada -->
        <q-expansion-item v-if="currentAction.type === 'CUSTOM'" label="Configurações Customizadas" icon="settings" class="q-mt-md" default-opened>
          <q-card>
            <q-card-section>
              <q-input v-model="currentAction.name" label="Nome da ação" filled dense class="q-mb-sm" />

              <q-input v-model="currentAction.endpoint" label="Endpoint" filled dense placeholder="/api/entidade/acao" class="q-mb-sm" />

              <q-select v-model="currentAction.method" label="Método HTTP" :options="['GET', 'POST', 'PUT', 'DELETE']" filled dense class="q-mb-sm" />

              <q-checkbox v-model="currentAction.opensDialog" label="Abre diálogo" class="q-mb-sm" />

              <q-input
                v-if="currentAction.opensDialog"
                v-model="currentAction.dialogComponent"
                label="Componente do diálogo"
                filled
                dense
                placeholder="Ex: AjusteEstoqueDialog"
                class="q-mb-sm"
              />

              <q-checkbox
                v-if="currentAction.opensDialog"
                v-model="currentAction.reloadAfterSuccess"
                label="Recarregar após sucesso"
                class="q-mb-sm"
              />

              <q-input v-model="currentAction.successMessage" label="Mensagem de sucesso" filled dense />
            </q-card-section>
          </q-card>
        </q-expansion-item>

        <!-- Condição de Visibilidade -->
        <q-expansion-item label="Visibilidade Condicional" icon="visibility" class="q-mt-md">
          <q-card>
            <q-card-section>
              <q-input
                v-model="currentAction.condition"
                label="Condição"
                filled
                dense
                placeholder="Ex: selectedItems.length > 0"
                type="textarea"
                rows="2"
              >
                <template v-slot:append>
                  <q-icon name="help">
                    <q-tooltip class="bg-grey-8" max-width="300px">
                      Use expressões JavaScript. Variáveis disponíveis:
                      <br />
                      - selectedItems: itens selecionados
                      <br />
                      - formData: dados do formulário
                      <br />
                      - user: usuário atual
                    </q-tooltip>
                  </q-icon>
                </template>
              </q-input>
            </q-card-section>
          </q-card>
        </q-expansion-item>
      </q-card-section>

      <q-separator />

      <q-card-actions align="right">
        <q-btn flat label="Cancelar" @click="cancelEdit" />
        <q-btn flat label="Salvar" color="primary" @click="saveAction" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useQuasar } from "quasar";
import { useFormBuilderStore } from "@/stores/formBuilderStore";
import draggable from "vuedraggable";

const $q = useQuasar();
const store = useFormBuilderStore();

const showActionDialog = ref(false);
const editingIndex = ref(-1);
const currentAction = ref({});
const hasConfirmation = ref(false);

// Options
const actionTypes = [
  { label: "Adicionar", value: "ADD" },
  { label: "Editar", value: "EDIT" },
  { label: "Excluir", value: "DELETE" },
  { label: "Visualizar", value: "VIEW" },
  { label: "Duplicar", value: "DUPLICATE" },
  { label: "Exportar", value: "EXPORT" },
  { label: "Importar", value: "IMPORT" },
  { label: "Customizada", value: "CUSTOM" },
];

const colorOptions = [
  { label: "Primária", value: "primary" },
  { label: "Secundária", value: "secondary" },
  { label: "Positiva", value: "positive" },
  { label: "Negativa", value: "negative" },
  { label: "Info", value: "info" },
  { label: "Aviso", value: "warning" },
  { label: "Cinza", value: "grey" },
];

// Watch
watch(
  () => currentAction.value.type,
  (type) => {
    // Definir valores padrão baseado no tipo
    if (type === "ADD") {
      currentAction.value.icon = currentAction.value.icon || "add";
      currentAction.value.color = currentAction.value.color || "primary";
      currentAction.value.label = currentAction.value.label || "Adicionar";
    } else if (type === "EDIT") {
      currentAction.value.icon = currentAction.value.icon || "edit";
      currentAction.value.label = currentAction.value.label || "Editar";
    } else if (type === "DELETE") {
      currentAction.value.icon = currentAction.value.icon || "delete";
      currentAction.value.color = currentAction.value.color || "negative";
      currentAction.value.label = currentAction.value.label || "Excluir";
      hasConfirmation.value = true;
      currentAction.value.confirmTitle = currentAction.value.confirmTitle || "Confirmação";
      currentAction.value.confirmMessage = currentAction.value.confirmMessage || "Deseja realmente excluir este registro?";
    } else if (type === "VIEW") {
      currentAction.value.icon = currentAction.value.icon || "visibility";
      currentAction.value.label = currentAction.value.label || "Visualizar";
    }
  }
);

// Methods
function getActionTypeColor(type) {
  const colors = {
    ADD: "positive",
    EDIT: "info",
    DELETE: "negative",
    VIEW: "primary",
    CUSTOM: "purple",
  };
  return colors[type] || "grey";
}

function showAddActionDialog() {
  editingIndex.value = -1;
  currentAction.value = {
    type: "ADD",
    label: "",
    icon: "add",
    color: "primary",
    method: "POST",
  };
  hasConfirmation.value = false;
  showActionDialog.value = true;
}

function editAction(action, index) {
  editingIndex.value = index;
  currentAction.value = { ...action };
  hasConfirmation.value = !!(action.confirmTitle || action.confirmMessage);
  showActionDialog.value = true;
}

function removeAction(index) {
  $q.dialog({
    title: "Remover ação",
    message: "Deseja remover esta ação?",
    cancel: true,
    persistent: true,
  }).onOk(() => {
    store.removeAction(index);
  });
}

function saveAction() {
  // Limpar confirmação se não estiver habilitada
  if (!hasConfirmation.value) {
    delete currentAction.value.confirmTitle;
    delete currentAction.value.confirmMessage;
  }

  if (editingIndex.value === -1) {
    store.addAction(currentAction.value);
  } else {
    store.updateAction(editingIndex.value, currentAction.value);
  }

  showActionDialog.value = false;
}

function cancelEdit() {
  showActionDialog.value = false;
}

function handleActionsChange() {
  store.isDirty = true;
}
</script>

<style lang="scss" scoped>
.action-editor {
  .action-item {
    transition: all 0.2s;

    &:hover {
      background-color: $grey-2;
    }
  }

  .drag-handle {
    opacity: 0.5;

    &:hover {
      opacity: 1;
    }
  }

  .sortable-ghost {
    opacity: 0.5;
  }
}

.dialog-content {
  max-height: 60vh;
  overflow-y: auto;
}
</style>
