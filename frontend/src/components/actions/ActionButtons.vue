<!-- components/actions/ActionButtons.vue -->
<template>
  <!-- Botões de ação no topo (header) -->
  <div v-if="context === 'top'" class="action-buttons-top">
    <q-btn
      dense
      flat
      v-for="action in actions"
      :key="action.action"
      color="secondary"
      :icon="action.icon"
      :label="action.label"
      :loading="isLoading(action.action)"
      :disable="disabled"
      @click="emitAction(action)"
      class="q-ml-sm text-caption"
    />
  </div>

  <!-- Botões de ação por linha da tabela -->
  <div v-else-if="context === 'row'" class="action-buttons-row">
    <!-- Se tiver mais de 3 ações, usa dropdown -->
    <q-btn-dropdown v-if="hasDropdown" flat round dense icon="more_vert" size="sm" @click.stop>
      <q-list dense>
        <q-item
          v-for="action in actions"
          :key="action.action"
          clickable
          v-close-popup
          @click="emitAction(action, row)"
          :disable="isLoading(action.action, row?.id)"
        >
          <q-item-section avatar v-if="action.icon">
            <q-icon :name="action.icon" :color="action.color" size="sm" />
          </q-item-section>
          <q-item-section>
            <q-item-label>{{ action.label }}</q-item-label>
          </q-item-section>
          <q-item-section side v-if="isLoading(action.action, row?.id)">
            <q-spinner size="sm" />
          </q-item-section>
        </q-item>
      </q-list>
    </q-btn-dropdown>

    <!-- Se tiver até 3 ações, mostra botões diretos -->
    <template v-else>
      <q-btn
        v-for="action in actions"
        :key="action.action"
        flat
        round
        dense
        :icon="action.icon"
        :color="action.color"
        size="sm"
        @click.stop="emitAction(action, row)"
        :loading="isLoading(action.action, row?.id)"
        :disable="disabled"
      >
        <q-tooltip v-if="action.label">
          {{ action.label }}
        </q-tooltip>
      </q-btn>
    </template>
  </div>

  <!-- Botões de ação com seleção múltipla (FAB) -->
  <q-fab
    v-else-if="context === 'selection' && actions.length > 0 && selected.length > 0"
    v-model="fabOpen"
    :label="fabLabel"
    vertical-actions-align="right"
    color="primary"
    icon="keyboard_arrow_up"
    direction="up"
    class="action-fab"
  >
    <q-fab-action
      v-for="action in actions"
      :key="action.action"
      :color="action.color || 'primary'"
      :icon="action.icon"
      :label="action.label"
      :loading="isLoading(action.action)"
      :disable="disabled"
      @click="handleSelectionAction(action)"
    />
  </q-fab>
</template>

<script setup>
import { ref, computed } from "vue";

// Props
const props = defineProps({
  // Contexto de renderização: 'top', 'row', ou 'selection'
  context: {
    type: String,
    required: true,
    validator: (value) => ["top", "row", "selection"].includes(value),
  },
  // Lista de ações a serem renderizadas
  actions: {
    type: Array,
    required: true,
    default: () => [],
  },
  // Dados da linha (para contexto 'row')
  row: {
    type: Object,
    default: null,
  },
  // Itens selecionados (para contexto 'selection')
  selected: {
    type: Array,
    default: () => [],
  },
  // Estado de loading (pode ser string ou função)
  loading: {
    type: [String, Function],
    default: null,
  },
  // Desabilitar todos os botões
  disabled: {
    type: Boolean,
    default: false,
  },
});

// Emits
const emit = defineEmits(["action"]);

// Estado local
const fabOpen = ref(false);

// Computed
const hasDropdown = computed(() => {
  return props.context === "row" && props.actions.length > 3;
});

const fabLabel = computed(() => {
  const count = props.selected.length;
  if (count === 0) return "";
  return `${count} selecionado${count > 1 ? "s" : ""}`;
});

// Methods
function isLoading(action, itemId = null) {
  if (!props.loading) return false;

  if (typeof props.loading === "function") {
    return props.loading(action, itemId);
  }

  if (itemId) {
    return props.loading === `${action}-${itemId}`;
  }

  return props.loading === action;
}

function emitAction(action, target = null) {
  console.log("Emitindo ação:", action, "com alvo:", target);
  emit("action", action, target);
}

function handleSelectionAction(action) {
  fabOpen.value = false;
  emitAction(action, props.selected);
}
</script>

<style scoped>
.action-buttons-top {
  display: flex;
  align-items: center;
  margin-left: 8px;
  gap: 8px;
}

.action-buttons-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-fab {
  left: 20px;
  z-index: 1000;
}

/* Animação suave para o FAB */
.q-fab {
  transition: all 0.3s ease;
}

/* Estilo para dropdown de ações */
:deep(.q-btn-dropdown__arrow) {
  margin-left: 0;
}
</style>
