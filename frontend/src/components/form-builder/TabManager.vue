<template>
  <div class="tab-manager">
    <div class="row items-center q-mb-sm">
      <div class="col">
        <div class="text-subtitle2 text-weight-medium">Estrutura de Abas</div>
        <div class="text-caption text-grey-6">{{ store.formDefinition.tabs.length }} aba(s)</div>
      </div>
      <div class="col-auto">
        <q-btn flat dense round icon="add" size="sm" color="primary" @click="showAddTabDialog">
          <q-tooltip>Adicionar aba</q-tooltip>
        </q-btn>
      </div>
    </div>

    <draggable v-model="store.formDefinition.tabs" handle=".drag-handle" item-key="name" :animation="200" @change="handleTabsChange">
      <template #item="{ element: tab }">
        <div class="tab-item q-mb-xs" :class="{ active: store.selectedTab === tab.name }" @click="store.selectedTab = tab.name">
          <div class="row items-center no-wrap q-pa-sm">
            <div class="drag-handle q-mr-sm">
              <q-icon name="drag_indicator" size="xs" color="grey-6" />
            </div>

            <q-icon :name="tab.icon || 'tab'" size="xs" class="q-mr-sm" />

            <div class="col">
              <div class="text-caption text-weight-medium">{{ tab.label }}</div>
              <div class="text-caption text-grey-6">
                {{ tab.fields.length }} campo(s)
                <span v-if="tab.component" class="q-ml-xs">
                  <q-icon name="widgets" size="xs" />
                  {{ tab.component }}
                </span>
              </div>
            </div>

            <div class="col-auto">
              <q-btn flat dense round icon="more_vert" size="xs" @click.stop>
                <q-menu auto-close>
                  <q-list dense>
                    <q-item clickable @click="editTab(tab)">
                      <q-item-section avatar>
                        <q-icon name="edit" size="xs" />
                      </q-item-section>
                      <q-item-section>Editar</q-item-section>
                    </q-item>

                    <q-item v-if="store.formDefinition.tabs.length > 1" clickable @click="removeTab(tab)">
                      <q-item-section avatar>
                        <q-icon name="delete" size="xs" color="negative" />
                      </q-item-section>
                      <q-item-section>Remover</q-item-section>
                    </q-item>
                  </q-list>
                </q-menu>
              </q-btn>
            </div>
          </div>
        </div>
      </template>
    </draggable>

    <!-- Dialog de Edição de Aba -->
    <q-dialog v-model="showEditDialog" persistent>
      <q-card style="width: 400px">
        <q-card-section>
          <div class="text-h6">{{ editingTab ? "Editar Aba" : "Nova Aba" }}</div>
        </q-card-section>

        <q-card-section class="q-gutter-sm">
          <q-input
            v-model="tabForm.name"
            label="Identificador"
            filled
            dense
            :disable="!!editingTab"
            :rules="[(val) => !!val || 'Campo obrigatório', (val) => /^[a-z0-9-]+$/.test(val) || 'Use apenas letras minúsculas, números e hífens']"
          />

          <q-input v-model="tabForm.label" label="Nome da Aba" filled dense :rules="[(val) => !!val || 'Campo obrigatório']" />

          <q-select
            v-model="tabForm.icon"
            label="Ícone"
            :options="iconOptions"
            filled
            dense
            clearable
            use-input
            input-debounce="300"
            @filter="filterIcons"
          >
            <template v-slot:prepend>
              <q-icon :name="tabForm.icon || 'tab'" />
            </template>
          </q-select>

          <q-input v-model="tabForm.component" label="Componente Customizado (opcional)" filled dense placeholder="Ex: PermissoesTab">
            <template v-slot:append>
              <q-icon name="help">
                <q-tooltip max-width="300px">Nome do componente Vue customizado. Se definido, a aba não permitirá adicionar campos.</q-tooltip>
              </q-icon>
            </template>
          </q-input>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Cancelar" @click="cancelEdit" />
          <q-btn flat label="Salvar" color="primary" :disable="!isFormValid" @click="saveTab" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { useQuasar } from "quasar";
import { useFormBuilderStore } from "@/stores/formBuilderStore";
import draggable from "vuedraggable";

const $q = useQuasar();
const store = useFormBuilderStore();

// Estado
const showEditDialog = ref(false);
const editingTab = ref(null);
const tabForm = ref({
  name: "",
  label: "",
  icon: "folder",
  component: "",
});

const allIcons = [
  "folder",
  "info",
  "settings",
  "security",
  "person",
  "business",
  "inventory",
  "shopping_cart",
  "attach_money",
  "description",
  "assessment",
  "assignment",
  "dashboard",
  "home",
  "work",
];

const iconOptions = ref(allIcons);

// Computed
const isFormValid = computed(() => {
  return tabForm.value.name && tabForm.value.label;
});

// Methods
function showAddTabDialog() {
  editingTab.value = null;
  tabForm.value = {
    name: "",
    label: "",
    icon: "folder",
    component: "",
  };
  showEditDialog.value = true;
}

function editTab(tab) {
  editingTab.value = tab;
  tabForm.value = {
    name: tab.name,
    label: tab.label,
    icon: tab.icon || "folder",
    component: tab.component || "",
  };
  showEditDialog.value = true;
}

function removeTab(tab) {
  if (tab.fields.length > 0) {
    $q.dialog({
      title: "Remover Aba",
      message: `A aba "${tab.label}" contém ${tab.fields.length} campo(s). Deseja realmente removê-la?`,
      cancel: true,
      persistent: true,
      ok: {
        label: "Remover",
        color: "negative",
      },
    }).onOk(() => {
      store.removeTab(tab.name);
      $q.notify({
        message: "Aba removida",
        color: "warning",
      });
    });
  } else {
    store.removeTab(tab.name);
  }
}

function saveTab() {
  if (editingTab.value) {
    // Editar aba existente
    Object.assign(editingTab.value, {
      label: tabForm.value.label,
      icon: tabForm.value.icon,
      component: tabForm.value.component || undefined,
    });
    store.isDirty = true;
  } else {
    // Adicionar nova aba
    const formattedName = tabForm.value.name.toLowerCase().replace(/\s+/g, "-");
    store.addTab(formattedName, tabForm.value.label, tabForm.value.icon || "folder");
    if (tabForm.value.component) {
      const newTab = store.formDefinition.tabs.find((t) => t.name === formattedName);
      if (newTab) newTab.component = tabForm.value.component;
    }
  }

  showEditDialog.value = false;
  $q.notify({
    message: editingTab.value ? "Aba atualizada" : "Aba adicionada",
    color: "positive",
  });
}

function cancelEdit() {
  showEditDialog.value = false;
}

function handleTabsChange() {
  store.isDirty = true;
}

function filterIcons(val, update) {
  if (val === "") {
    update(() => {
      iconOptions.value = allIcons;
    });
    return;
  }

  update(() => {
    const needle = val.toLowerCase();
    iconOptions.value = allIcons.filter((icon) => icon.toLowerCase().includes(needle));
  });
}
</script>

<style lang="scss" scoped>
.tab-item {
  background-color: white;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid $grey-4;

  &:hover {
    border-color: $primary;
    transform: translateX(2px);
  }

  &.active {
    background-color: $primary;
    color: white;
    border-color: $primary;

    .text-grey-6 {
      color: rgba(255, 255, 255, 0.8) !important;
    }

    .q-icon {
      color: white !important;
    }
  }

  .drag-handle {
    cursor: move;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .drag-handle {
    opacity: 0.5;
  }
}

.sortable-ghost {
  opacity: 0.5;
}
</style>
