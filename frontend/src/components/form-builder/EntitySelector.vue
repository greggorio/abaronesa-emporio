<template>
  <div class="entity-selector">
    <q-input v-model="searchFilter" dense filled placeholder="Buscar entidade..." class="search-input q-mb-md">
      <template v-slot:prepend>
        <q-icon name="search" size="20px" />
      </template>
    </q-input>

    <!-- Lista de Entidades -->
    <div class="entities-list">
      <div
        v-for="entity in filteredEntities"
        :key="entity.entityType"
        class="entity-item"
        :class="{ selected: isSelected(entity) }"
        @click="handleEntitySelect(entity)"
      >
        <q-icon :name="getEntityIcon(entity)" size="20px" :color="isSelected(entity) ? 'white' : getEntityColor(entity)" />
        <div class="entity-info">
          <div class="entity-name">{{ entity.className }}</div>
          <div class="entity-type">{{ entity.entityType }}</div>
        </div>
        <q-badge v-if="hasExistingConfig(entity)" color="positive" text-color="white" label="✓" rounded />
      </div>
    </div>

    <!-- Loading Overlay -->
    <transition name="fade">
      <div v-if="loadingConfigs" class="loading-overlay">
        <q-spinner-dots color="primary" size="40px" />
        <div class="text-caption text-grey-6 q-mt-sm">Verificando configurações...</div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useFormBuilderStore } from "@/stores/formBuilderStore";
import EntityDiscoveryService from "@/services/form-builder/EntityDiscoveryService";

const $q = useQuasar();
const store = useFormBuilderStore();

const searchFilter = ref("");
const existingConfigs = ref(new Set());
const loadingConfigs = ref(false);

// Computed
const filteredEntities = computed(() => {
  if (!searchFilter.value) {
    return store.availableEntities;
  }

  const needle = searchFilter.value.toLowerCase();
  return store.availableEntities.filter(
    (entity) => entity.className.toLowerCase().includes(needle) || entity.entityType.toLowerCase().includes(needle)
  );
});

// Methods
function isSelected(entity) {
  return store.selectedEntity?.entityType === entity.entityType;
}

async function handleEntitySelect(entity) {
  if (isSelected(entity)) return;

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
    }).onOk(async () => {
      await store.selectEntity(entity);
    });
  } else {
    await store.selectEntity(entity);
  }
}

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

function getEntityColor(entity) {
  const colorMap = {
    usuarios: "blue",
    categorias: "orange",
    produtos: "purple",
    clientes: "teal",
    fornecedores: "brown",
    pedidos: "green",
    vendas: "red",
    estoque: "indigo",
    financeiro: "amber",
  };

  return colorMap[entity.entityType.toLowerCase()] || "grey";
}

function hasExistingConfig(entity) {
  return existingConfigs.value.has(entity.entityType);
}

async function loadExistingConfigs() {
  loadingConfigs.value = true;
  try {
    for (const entity of store.availableEntities) {
      try {
        const existing = await EntityDiscoveryService.loadExistingDefinition(entity.entityType);
        if (existing) {
          existingConfigs.value.add(entity.entityType);
        }
      } catch (error) {
        // Ignorar erros 404
      }
    }
  } finally {
    loadingConfigs.value = false;
  }
}

// Lifecycle
onMounted(() => {
  loadExistingConfigs();
});
</script>

<style lang="scss" scoped>
.entity-selector {
  position: relative;
}

.search-input {
  :deep(.q-field__control) {
    background-color: #f8f9fa;
  }
}

.entities-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  padding-right: 4px;

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

.entity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #f8f9fa;
    border-color: #666;
  }

  &.selected {
    background: #666;
    border-color: #666;
    color: white;

    .entity-type {
      color: rgba(255, 255, 255, 0.8);
    }
  }
}

.entity-info {
  flex: 1;
  min-width: 0;

  .entity-name {
    font-weight: 500;
    font-size: 13px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .entity-type {
    font-size: 11px;
    color: #666;
    margin-top: 2px;
  }
}

.loading-overlay {
  text-align: center;
  padding: 20px;
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
</style>
