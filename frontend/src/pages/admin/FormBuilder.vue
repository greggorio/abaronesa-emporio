<template>
  <q-page class="form-builder-page">
    <!-- Header Principal -->
    <div class="main-header">
      <div class="header-content">
        <!-- Seletor de Entidade -->
        <div class="entity-selector-header">
          <q-select
            v-model="store.selectedEntity"
            :options="store.availableEntities"
            option-label="className"
            dense
            filled
            style="min-width: 250px"
            @update:model-value="handleEntityChange"
          >
            <template v-slot:prepend>
              <q-icon name="domain" />
            </template>
            <template v-slot:selected-item="scope">
              <div class="row items-center no-wrap" v-if="scope.opt">
                <q-icon :name="getEntityIcon(scope.opt)" size="20px" class="q-mr-sm" />
                <div>
                  <div class="text-weight-medium">{{ scope.opt.className }}</div>
                  <div class="text-caption text-grey-6">{{ scope.opt.entityType }}</div>
                </div>
              </div>
              <span v-else class="text-grey-6">Selecione uma entidade</span>
            </template>
            <template v-slot:option="scope">
              <q-item v-bind="scope.itemProps">
                <q-item-section avatar>
                  <q-icon :name="getEntityIcon(scope.opt)" />
                </q-item-section>
                <q-item-section>
                  <q-item-label>{{ scope.opt.programName ? scope.opt.programName : scope.opt.className }}</q-item-label>
                  <q-item-label caption>{{ scope.opt.entityType }}</q-item-label>
                </q-item-section>
              </q-item>
            </template>
          </q-select>
        </div>

        <!-- Ações do Header -->
        <div class="header-actions">
          <q-btn
            v-if="store.selectedEntity"
            unelevated
            color="primary"
            label="SALVAR"
            :loading="store.saving"
            :disable="!store.isDirty"
            @click="handleSave"
          />
        </div>
      </div>
    </div>

    <div class="form-builder-container">
      <!-- Sidebar Esquerda -->
      <div class="left-sidebar">
        <!-- Título da sidebar -->
        <div class="sidebar-section">
          <h6 class="section-title">CAMPOS DA ENTIDADE</h6>

          <!-- Buscar campos -->
          <q-input v-if="store.selectedEntity" v-model="fieldSearchFilter" dense filled placeholder="Buscar campos" class="field-search-input">
            <template v-slot:prepend>
              <q-icon name="search" size="18px" />
            </template>
          </q-input>
        </div>

        <!-- Campos Disponíveis -->
        <div v-if="store.selectedEntity" class="sidebar-section">
          <FieldDetector />
        </div>

        <!-- Estrutura de Abas -->
        <template v-if="store.selectedEntity">
          <div class="sidebar-section">
            <TabManager />
          </div>
        </template>
      </div>

      <!-- Área Central -->
      <div class="main-content">
        <!-- Main Tabs Navigation -->
        <div v-if="store.selectedEntity" class="main-tabs">
          <div class="tabs-row">
            <q-tabs v-model="mainTab" dense active-color="primary" indicator-color="primary" align="left" class="text-grey-7">
              <q-tab name="config" label="Configuração" />
              <q-tab name="preview" label="Preview" />
              <q-tab name="table" label="Tabela" />
              <q-tab name="actions" label="Ações" />
            </q-tabs>

            <div class="tabs-actions">
              <JsonEditor />
              <q-btn flat dense round icon="code" size="sm">
                <q-tooltip>{ }</q-tooltip>
              </q-btn>
            </div>
          </div>
        </div>

        <!-- Content Body -->
        <div class="content-body">
          <transition name="fade" mode="out-in">
            <!-- Empty State when no entity selected -->
            <EmptyState v-if="!store.selectedEntity" key="empty" />

            <!-- Tab Panels when entity is selected -->
            <q-tab-panels v-else v-model="mainTab" animated transition-prev="slide-right" transition-next="slide-left" class="bg-transparent">
              <!-- Configuration Tab -->
              <q-tab-panel name="config" class="q-pa-none">
                <FormDesigner />
              </q-tab-panel>

              <!-- Preview Tab -->
              <q-tab-panel name="preview" class="q-pa-none">
                <CentralPreview />
              </q-tab-panel>

              <!-- Table Tab -->
              <q-tab-panel name="table" class="q-pa-none">
                <TablePreview />
              </q-tab-panel>

              <!-- Actions Tab -->
              <q-tab-panel name="actions" class="q-pa-none">
                <ActionsConfig />
              </q-tab-panel>
            </q-tab-panels>
          </transition>
        </div>
      </div>

      <!-- Sidebar Direita - Com expansão/contração -->
      <div class="right-sidebar" :class="{ expanded: rightSidebarExpanded, collapsed: !rightSidebarExpanded }" v-if="store.selectedEntity">
        <!-- Conteúdo quando expandida -->
        <template v-if="rightSidebarExpanded">
          <!-- Header da Sidebar -->
          <div class="sidebar-header">
            <h6 class="text-subtitle1 q-ma-none">
              {{ store.selectedField ? "Propriedades: " + store.selectedField.label : "Propriedades" }}
            </h6>
            <q-btn flat dense round icon="close" size="sm" @click="closeRightSidebar" />
          </div>

          <q-separator />

          <!-- Conteúdo da Sidebar -->
          <div class="sidebar-content">
            <q-scroll-area class="fit">
              <!-- Editor de Campo - aparece quando um campo é selecionado -->
              <div v-if="store.selectedField" class="h-full">
                <FieldEditor @close="closeFieldEditor" />
              </div>

              <!-- Mensagem quando nenhum campo está selecionado -->
              <div v-else class="no-field-selected">
                <q-icon name="touch_app" size="48px" color="grey-4" />
                <p>Selecione um campo para editar suas propriedades</p>
              </div>
            </q-scroll-area>
          </div>
        </template>

        <!-- Conteúdo quando colapsada -->
        <template v-else>
          <div class="sidebar-collapsed-content">
            <div class="collapsed-tab" @click="openRightSidebar" @mouseenter="showTemporarySidebar" @mouseleave="hideTemporarySidebar">
              <q-icon name="chevron_left" size="20px" />
              <span>Propriedades</span>
            </div>
          </div>

          <!-- Sidebar temporária no hover -->
          <transition name="slide-left">
            <div v-if="temporarySidebarVisible" class="temporary-sidebar" @mouseenter="keepTemporarySidebar" @mouseleave="hideTemporarySidebar">
              <!-- Header da Sidebar -->
              <div class="sidebar-header">
                <h6 class="text-subtitle1 q-ma-none">
                  {{ store.selectedField ? "Propriedades: " + store.selectedField.label : "Propriedades" }}
                </h6>
              </div>

              <q-separator />

              <!-- Conteúdo da Sidebar -->
              <div class="sidebar-content">
                <q-scroll-area class="fit">
                  <!-- Editor de Campo - aparece quando um campo é selecionado -->
                  <div v-if="store.selectedField" class="h-full">
                    <FieldEditor @close="closeFieldEditor" />
                  </div>

                  <!-- Mensagem quando nenhum campo está selecionado -->
                  <div v-else class="no-field-selected">
                    <q-icon name="touch_app" size="48px" color="grey-4" />
                    <p>Selecione um campo para editar suas propriedades</p>
                  </div>
                </q-scroll-area>
              </div>
            </div>
          </transition>
        </template>
      </div>
    </div>
  </q-page>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from "vue";
import { useQuasar } from "quasar";
import { useFormBuilderStore } from "@/stores/formBuilderStore";
import { useRouter } from "vue-router";

// Componentes
import FieldDetector from "src/components/form-builder/FieldDetector.vue";
import FormDesigner from "src/components/form-builder/FormDesigner.vue";
import FieldEditor from "src/components/form-builder/FieldEditor.vue";
import TabManager from "src/components/form-builder/TabManager.vue";
import EmptyState from "src/components/form-builder/EmptyState.vue";
import CentralPreview from "src/components/form-builder/CentralPreview.vue";
import TablePreview from "src/components/form-builder/TablePreview.vue";
import ActionsConfig from "src/components/form-builder/ActionsConfig.vue";
import JsonEditor from "src/components/form-builder/JsonEditor.vue";

const $q = useQuasar();
const store = useFormBuilderStore();
const router = useRouter();

// Estado
const autoSaveEnabled = ref(true);
const mainTab = ref("config");
const fieldSearchFilter = ref("");
const rightSidebarExpanded = ref(true); // Começa expandida
const temporarySidebarVisible = ref(false);
let autoSaveTimer = null;
let temporarySidebarTimer = null;

// Methods para o ícone da entidade
function getEntityIcon(entity) {
  const iconMap = {
    usuarios: "person",
    categorias: "category",
    produtos: "inventory_2",
    clientes: "groups",
    fornecedores: "business",
    pedidos: "shopping_cart",
    vendas: "point_of_sale",
    estoque: "warehouse",
    financeiro: "attach_money",
  };

  return iconMap[entity.entityType.toLowerCase()] || "domain";
}

// Watchers
watch(
  () => store.selectedField,
  (newField) => {
    if (newField) {
      rightSidebarExpanded.value = true;
    }
  }
);

watch(
  () => store.isDirty,
  (isDirty) => {
    if (isDirty && autoSaveEnabled.value) {
      clearTimeout(autoSaveTimer);
      autoSaveTimer = setTimeout(() => {
        store.saveDraft();
      }, 2000);
    }
  }
);

// Lifecycle
onMounted(async () => {
  await store.loadAvailableEntities();

  const urlParams = new URLSearchParams(window.location.search);
  const entityType = urlParams.get("entity");
  if (entityType) {
    const entity = store.availableEntities.find((e) => e.entityType === entityType);
    if (entity) {
      await store.selectEntity(entity);
      checkForDraft();
    }
  }

  // Listen for field editor events
  window.addEventListener("open-field-editor", handleOpenFieldEditor);
  window.addEventListener("close-field-editor", handleCloseFieldEditor);

  // Prevenir saída acidental com alterações não salvas
  window.addEventListener("beforeunload", (e) => {
    if (store.isDirty) {
      e.preventDefault();
      e.returnValue = "";
    }
  });
});

onBeforeUnmount(() => {
  clearTimeout(autoSaveTimer);
  window.removeEventListener("open-field-editor", handleOpenFieldEditor);
  window.removeEventListener("close-field-editor", handleCloseFieldEditor);
});

// Methods
function handleEntityChange(entity) {
  if (!entity) return;

  if (store.isDirty) {
    $q.dialog({
      title: "Alterações não salvas",
      message: "Você tem alterações não salvas. Deseja continuar sem salvar?",
      cancel: {
        label: "Cancelar",
        color: "grey",
        flat: true,
      },
      ok: {
        label: "Continuar",
        color: "primary",
        flat: true,
      },
      persistent: true,
    })
      .onOk(async () => {
        await store.selectEntity(entity);
      })
      .onCancel(() => {
        // Reverter a seleção
        store.selectedEntity = { ...store.selectedEntity };
      });
  } else {
    store.selectEntity(entity);
  }
}

function handleOpenFieldEditor() {
  rightSidebarExpanded.value = true;
}

function handleCloseFieldEditor() {
  // Apenas limpar a seleção do campo
  store.selectedField = null;
}

function openRightSidebar() {
  rightSidebarExpanded.value = true;
}

function closeRightSidebar() {
  rightSidebarExpanded.value = false;
}

function closeFieldEditor() {
  store.selectedField = null;
}

function showTemporarySidebar() {
  clearTimeout(temporarySidebarTimer);
  temporarySidebarVisible.value = true;
}

function hideTemporarySidebar() {
  temporarySidebarTimer = setTimeout(() => {
    temporarySidebarVisible.value = false;
  }, 300);
}

function keepTemporarySidebar() {
  clearTimeout(temporarySidebarTimer);
}

function openJsonEditor() {
  if (jsonEditorRef.value) {
    jsonEditorRef.value.show();
  }
}

function checkForDraft() {
  if (store.loadDraft()) {
    $q.notify({
      message: "Rascunho anterior carregado",
      caption: "Suas alterações não salvas foram restauradas",
      color: "info",
      icon: "restore",
      actions: [
        {
          label: "Descartar",
          color: "white",
          handler: () => {
            store.reset();
            store.selectEntity(store.selectedEntity);
          },
        },
      ],
    });
  }
}

async function handleSave() {
  try {
    await store.saveFormDefinition();
    $q.notify({
      message: "Formulário salvo com sucesso!",
      color: "positive",
      icon: "check",
    });
  } catch (error) {
    $q.notify({
      message: "Erro ao salvar formulário",
      caption: error.message,
      color: "negative",
      icon: "error",
    });
  }
}
</script>

<style lang="scss" scoped>
.form-builder-page {
  height: 100vh;
  overflow: hidden;
  background-color: #f5f5f5;
  display: flex;
  flex-direction: column;
}

// Header Principal
.main-header {
  background-color: #f8f9fa;
  border-bottom: 1px solid #e0e0e0;
  flex-shrink: 0;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 5px;
  height: 60px;
}

.entity-selector-header {
  flex: 1;
  max-width: 270px;
  background-color: #ffffff;
}

.field-search-input {
  :deep(.q-field__control) {
    background-color: #f8f9fa;
  }
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

// Container Principal
.form-builder-container {
  display: flex;
  flex: 1;
  overflow: hidden;
  position: relative;
}

// Sidebar Esquerda
.left-sidebar {
  width: 280px;
  background-color: white;
  border-right: 1px solid #e0e0e0;
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-section {
  padding: 20px;

  &:not(:last-child) {
    border-bottom: 1px solid #f0f0f0;
  }
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #666;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0 0 15px 0;
}

// Área Central
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f5f5f5;
}

.main-tabs {
  background-color: white;
  padding: 0;
  border-bottom: 1px solid #e0e0e0;

  .tabs-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-right: 16px;
  }

  .tabs-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.content-body {
  flex: 1;
  overflow-y: auto;
  background-color: #f5f5f5;
}

:deep(.q-tab-panels) {
  background: transparent;
}

:deep(.q-tab-panel) {
  padding: 20px;
}

// Sidebar Direita
.right-sidebar {
  width: 350px;
  background-color: white;
  border-left: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: relative;

  &.collapsed {
    width: 40px;
  }

  .sidebar-collapsed-content {
    width: 40px;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: white;

    .collapsed-tab {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      width: 100%;
      padding: 16px 0;
      cursor: pointer;
      color: #666;
      transition: all 0.2s;

      &:hover {
        background-color: #f8f9fa;
        color: #333;
      }

      span {
        font-size: 10px;
        writing-mode: vertical-rl;
        text-orientation: mixed;
        margin-top: 8px;
        letter-spacing: 1px;
      }
    }
  }

  .temporary-sidebar {
    position: absolute;
    left: 40px;
    top: 0;
    bottom: 0;
    width: 350px;
    background: white;
    border: 1px solid #e0e0e0;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
    z-index: 200;
    display: flex;
    flex-direction: column;
  }
}

// Transição para sidebar temporária
.slide-left-enter-active,
.slide-left-leave-active {
  transition: transform 0.2s ease;
}

.slide-left-enter-from {
  transform: translateX(-100%);
}

.slide-left-leave-to {
  transform: translateX(-100%);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  min-height: 56px;

  h6 {
    font-weight: 500;
    color: #333;
  }
}

.sidebar-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.no-field-selected {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  color: #999;
  padding: 40px;

  p {
    margin-top: 16px;
    font-size: 14px;
  }
}

// Transições
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// Scrollbar customizada
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: #f0f0f0;
}

::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 3px;

  &:hover {
    background: #999;
  }
}

// Ajustes responsivos
@media (max-width: 1400px) {
  .left-sidebar {
    width: 260px;
  }

  .right-sidebar {
    width: 320px;
  }
}

@media (max-width: 1200px) {
  .header-content {
    padding: 12px 16px;
  }

  :deep(.q-tab-panel) {
    padding: 16px;
  }
}
</style>
