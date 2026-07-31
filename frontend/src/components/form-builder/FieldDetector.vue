<template>
  <div class="field-detector">
    <!-- Header com contador -->
    <div class="fields-header">
      <span class="text-weight-medium">Campos Disponíveis</span>
      <div class="text-caption text-grey-6">{{ store.unusedFields.length }} de {{ store.detectedFields.length }} campos disponíveis</div>
    </div>

    <!-- Lista de Campos -->
    <div class="fields-list">
      <draggable
        v-model="store.detectedFields"
        :group="{ name: 'fields', pull: 'clone', put: false }"
        :clone="cloneField"
        item-key="name"
        :sort="false"
        class="draggable-list"
      >
        <template #item="{ element: field }">
          <div v-if="!isFieldUsed(field)" class="field-card" @click="handleFieldClick(field)">
            <div class="field-card-content">
              <!-- Field Type Icon -->
              <div class="field-type-icon">
                <span class="type-tag">{{ getFieldTypeAbbr(field) }}</span>
              </div>

              <!-- Field Info -->
              <div class="field-info">
                <div class="field-name">{{ field.name }}</div>
                <div class="field-type">{{ humanize(field.name) }}</div>
              </div>

              <!-- Status indicators -->
              <div class="field-indicators">
                <q-icon v-if="isMediaField(field)" name="image" size="16px" color="orange">
                  <q-tooltip>Campo de mídia</q-tooltip>
                </q-icon>
                <q-icon v-if="isRelationField(field)" name="link" size="16px" color="blue">
                  <q-tooltip>Relacionamento</q-tooltip>
                </q-icon>
              </div>
            </div>
          </div>
        </template>
      </draggable>

      <!-- Empty State -->
      <div v-if="store.unusedFields.length === 0" class="empty-state">
        <q-icon name="check_circle" size="48px" color="positive" />
        <h6>Todos os campos adicionados</h6>
        <p>Você já adicionou todos os campos detectados ao formulário</p>
      </div>
    </div>

    <!-- Refresh button -->
    <q-btn flat dense size="sm" icon="refresh" label="Atualizar campos" class="full-width q-mt-sm" @click="refreshFields" />
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { useQuasar } from "quasar";
import { useFormBuilderStore } from "@/stores/formBuilderStore";
import FieldTypeMapper from "@/services/form-builder/FieldTypeMapper";
import EntityDiscoveryService from "@/services/form-builder/EntityDiscoveryService";
import draggable from "vuedraggable";

const $q = useQuasar();
const store = useFormBuilderStore();

// Methods
function isFieldUsed(field) {
  return store.allFields.some((f) => f.name === field.name);
}

function humanize(fieldName) {
  return FieldTypeMapper.humanizeFieldName(fieldName);
}

function getFieldTypeAbbr(field) {
  const typeMap = {
    String: "Str",
    Integer: "Int",
    Long: "Long",
    Double: "Dbl",
    Float: "Flt",
    BigDecimal: "Dec",
    Boolean: "Bool",
    LocalDate: "Date",
    LocalDateTime: "DtTm",
    LocalTime: "Time",
  };

  return typeMap[field.type] || field.type.substring(0, 3);
}

function isMediaField(field) {
  return FieldTypeMapper.isMediaField(field.name);
}

function isRelationField(field) {
  return FieldTypeMapper.isRelationField(field.name, field.type);
}

function handleFieldClick(field) {
  // Se houver apenas uma aba, adicionar direto
  if (store.formDefinition.tabs.length === 1) {
    addFieldQuick(field);
    return;
  }

  // Se houver múltiplas abas, mostrar dialog
  $q.dialog({
    component: {
      template: `
        <q-dialog ref="dialogRef" @hide="onDialogHide">
          <q-card style="width: 400px">
            <q-card-section>
              <div class="text-h6">Adicionar Campo</div>
              <div class="text-caption text-grey-6">{{ fieldLabel }}</div>
            </q-card-section>

            <q-separator />

            <q-card-section>
              <div class="text-subtitle2 q-mb-sm">Selecione a aba de destino:</div>
              <q-list>
                <q-item
                  v-for="tab in tabs"
                  :key="tab.value"
                  clickable
                  v-ripple
                  @click="selectTab(tab.value)"
                  class="q-mb-xs"
                  style="border: 1px solid #e0e0e0; border-radius: 4px;"
                >
                  <q-item-section avatar>
                    <q-icon :name="tab.icon || 'tab'" />
                  </q-item-section>
                  <q-item-section>
                    <q-item-label>{{ tab.label }}</q-item-label>
                    <q-item-label caption>{{ tab.fields }} campos</q-item-label>
                  </q-item-section>
                  <q-item-section side v-if="tab.value === currentTab">
                    <q-chip size="sm" color="primary" text-color="white">Atual</q-chip>
                  </q-item-section>
                </q-item>
              </q-list>
            </q-card-section>

            <q-card-actions align="right">
              <q-btn flat label="Cancelar" @click="onCancelClick" />
            </q-card-actions>
          </q-card>
        </q-dialog>
      `,
      props: ["fieldLabel", "tabs", "currentTab"],
      emits: ["ok", "hide"],
      setup(props, { emit }) {
        const { dialogRef, onDialogHide, onDialogOK, onDialogCancel } = $q.useDialogPluginComponent();

        return {
          dialogRef,
          onDialogHide,
          selectTab(tabName) {
            onDialogOK(tabName);
          },
          onCancelClick: onDialogCancel,
        };
      },
    },
    componentProps: {
      fieldLabel: humanize(field.name),
      tabs: store.formDefinition.tabs.map((tab) => ({
        value: tab.name,
        label: tab.label,
        icon: tab.icon,
        fields: tab.fields.length,
      })),
      currentTab: store.selectedTab,
    },
  }).onOk((tabName) => {
    store.addFieldToTab(field, tabName);
    $q.notify({
      message: "Campo adicionado",
      caption: `"${humanize(field.name)}" foi adicionado à aba selecionada`,
      color: "positive",
      icon: "check",
      timeout: 2000,
    });
  });
}

function addFieldQuick(field) {
  store.addFieldToTab(field);

  $q.notify({
    message: "Campo adicionado",
    caption: `"${humanize(field.name)}" foi adicionado à aba atual`,
    color: "positive",
    icon: "check",
    position: "top",
    timeout: 2000,
  });
}

function cloneField(field) {
  return FieldTypeMapper.generateFieldConfig(field, store.formDefinition.entityType);
}

async function refreshFields() {
  if (store.selectedEntity) {
    try {
      store.detectedFields = await EntityDiscoveryService.detectFields(store.selectedEntity.entityType);
      $q.notify({
        message: "Campos recarregados",
        color: "info",
        icon: "refresh",
        timeout: 1000,
      });
    } catch (error) {
      $q.notify({
        message: "Erro ao recarregar campos",
        caption: error.message,
        color: "negative",
        icon: "error",
      });
    }
  }
}
</script>

<style lang="scss" scoped>
.field-detector {
  height: 100%;
}

.fields-header {
  margin-bottom: 16px;

  .text-weight-medium {
    font-size: 13px;
    color: #333;
  }
}

.fields-list {
  max-height: calc(100vh - 450px);
  overflow-y: auto;
  padding-right: 4px;
  margin-bottom: 12px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: #f0f0f0;
    border-radius: 3px;
  }

  &::-webkit-scrollbar-thumb {
    background: #ccc;
    border-radius: 3px;

    &:hover {
      background: #999;
    }
  }
}

.draggable-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-card {
  background-color: white;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background-color: #f8f9fa;
    border-color: #999;
  }

  &.sortable-ghost {
    opacity: 0.5;
  }
}

.field-card-content {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-type-icon {
  flex-shrink: 0;

  .type-tag {
    display: inline-block;
    background-color: #f0f0f0;
    color: #666;
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 11px;
    font-weight: 500;
    font-family: monospace;
  }
}

.field-info {
  flex: 1;
  min-width: 0;

  .field-name {
    font-size: 12px;
    color: #999;
    font-family: monospace;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .field-type {
    font-size: 13px;
    font-weight: 500;
    color: #333;
    margin-top: 2px;
  }
}

.field-indicators {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #999;

  h6 {
    margin: 12px 0 8px 0;
    font-weight: 500;
    font-size: 14px;
    color: #666;
  }

  p {
    margin: 0;
    font-size: 13px;
  }
}
</style>
