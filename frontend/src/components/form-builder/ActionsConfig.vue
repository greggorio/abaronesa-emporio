<template>
  <div class="actions-config">
    <!-- Actions Header -->
    <div class="actions-header">
      <div class="row items-center">
        <div class="col">
          <h5 class="q-ma-none">Ações do Formulário</h5>
          <div class="text-caption text-grey-6">Configure as ações disponíveis para os usuários</div>
        </div>
        <div class="col-auto">
          <q-btn unelevated color="primary" label="Nova Ação" icon="add" @click="showAddActionDialog" />
        </div>
      </div>
    </div>

    <!-- Actions Grid -->
    <div class="actions-grid q-mt-lg">
      <draggable
        v-model="store.formDefinition.actions"
        handle=".drag-handle"
        item-key="type"
        :animation="200"
        @change="handleActionsChange"
        class="row q-col-gutter-md"
      >
        <template #item="{ element: action, index }">
          <div class="col-12 col-md-6 col-lg-4">
            <q-card flat class="action-card">
              <q-card-section>
                <div class="row items-start">
                  <div class="col-auto">
                    <q-icon name="drag_indicator" class="drag-handle" size="20px" />
                  </div>
                  <div class="col">
                    <div class="row items-center q-mb-sm">
                      <q-avatar :icon="action.icon" :color="action.color || 'grey-4'" text-color="white" size="42px" />
                      <div class="q-ml-md">
                        <div class="text-h6">{{ action.label }}</div>
                        <q-chip size="sm" :color="getActionTypeColor(action.type)" text-color="white">
                          {{ action.type }}
                        </q-chip>
                      </div>
                    </div>

                    <!-- Action Properties -->
                    <div class="action-properties q-mt-md">
                      <div v-if="action.onDoubleClick" class="property-item">
                        <q-icon name="mouse" size="16px" />
                        <span>Ativado no duplo clique</span>
                      </div>
                      <div v-if="action.requiresSelection" class="property-item">
                        <q-icon name="check_box" size="16px" />
                        <span>Requer seleção</span>
                      </div>
                      <div v-if="action.opensDialog" class="property-item">
                        <q-icon name="open_in_new" size="16px" />
                        <span>Abre diálogo</span>
                      </div>
                      <div v-if="action.confirmTitle" class="property-item">
                        <q-icon name="warning" size="16px" />
                        <span>Requer confirmação</span>
                      </div>
                      <div v-if="action.inlineOnly" class="property-item">
                        <q-icon name="table_rows" size="16px" />
                        <span>Apenas inline</span>
                      </div>
                    </div>

                    <!-- Custom Action Details -->
                    <div v-if="action.type === 'CUSTOM'" class="custom-details q-mt-md">
                      <q-chip size="sm" icon="link" color="blue-2" text-color="blue-9">
                        {{ action.endpoint || "Sem endpoint" }}
                      </q-chip>
                      <q-chip size="sm" color="grey-3">
                        {{ action.method || "POST" }}
                      </q-chip>
                    </div>
                  </div>
                  <div class="col-auto">
                    <q-btn flat dense round icon="more_vert" size="sm">
                      <q-menu>
                        <q-list dense>
                          <q-item clickable @click="editAction(action, index)">
                            <q-item-section avatar>
                              <q-icon name="edit" />
                            </q-item-section>
                            <q-item-section>Editar</q-item-section>
                          </q-item>
                          <q-item clickable @click="duplicateAction(action, index)">
                            <q-item-section avatar>
                              <q-icon name="content_copy" />
                            </q-item-section>
                            <q-item-section>Duplicar</q-item-section>
                          </q-item>
                          <q-separator />
                          <q-item clickable @click="removeAction(index)">
                            <q-item-section avatar>
                              <q-icon name="delete" color="negative" />
                            </q-item-section>
                            <q-item-section>Remover</q-item-section>
                          </q-item>
                        </q-list>
                      </q-menu>
                    </q-btn>
                  </div>
                </div>
              </q-card-section>
            </q-card>
          </div>
        </template>
      </draggable>

      <!-- Empty State -->
      <div v-if="store.formDefinition.actions.length === 0" class="empty-actions">
        <q-icon name="touch_app" size="64px" color="grey-4" />
        <h6>Nenhuma ação configurada</h6>
        <p>Adicione ações para permitir interações com o formulário</p>
        <q-btn unelevated color="primary" label="Adicionar Primeira Ação" icon="add" @click="showAddActionDialog" class="q-mt-md" />
      </div>
    </div>

    <!-- Esta é a mesma dialog do ActionEditor original - mantida intacta -->
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

                <q-select
                  v-model="currentAction.method"
                  label="Método HTTP"
                  :options="['GET', 'POST', 'PUT', 'DELETE']"
                  filled
                  dense
                  class="q-mb-sm"
                />

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
  </div>
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

// Options - mantidas do original
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

// Watch - mantido do original
watch(
  () => currentAction.value.type,
  (type) => {
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

function duplicateAction(action, index) {
  const newAction = {
    ...action,
    label: `${action.label} (Cópia)`,
  };
  store.formDefinition.actions.splice(index + 1, 0, newAction);
  store.isDirty = true;
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
.actions-config {
  max-width: 1400px;
  margin: 0 auto;
}

.actions-header {
  background-color: $grey-1;
  padding: 20px;
  border-radius: 8px;

  h5 {
    font-weight: 500;
    color: $grey-9;
  }
}

.actions-grid {
  min-height: 300px;
}

.action-card {
  height: 100%;
  transition: all 0.2s;
  border: 1px solid $grey-3;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  .drag-handle {
    cursor: move;
    color: $grey-5;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .drag-handle {
    opacity: 0.6;
  }

  .action-properties {
    .property-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: $grey-7;
      margin-bottom: 4px;

      q-icon {
        opacity: 0.7;
      }
    }
  }

  .custom-details {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
}

.empty-actions {
  text-align: center;
  padding: 80px 40px;
  background-color: $grey-1;
  border-radius: 8px;
  min-height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  h6 {
    margin: 16px 0 8px 0;
    font-weight: 500;
    color: $grey-7;
  }

  p {
    margin: 0;
    font-size: 14px;
    color: $grey-6;
  }
}

.dialog-content {
  max-height: 60vh;
  overflow-y: auto;
}

// Sortable Ghost
.sortable-ghost {
  opacity: 0.5;
}
</style>
