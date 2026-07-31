<template>
  <div class="detail-item">
    <div class="detail-label text-caption q-mb-xs" style="color: #8a8c8c; cursor: pointer" @click="startEditing">
      {{ isEditing ? "Adicionar Observador" : "Observadores" }}
      <q-icon size="xs" name="add_circle">
        <q-tooltip>Adicionar observador</q-tooltip>
      </q-icon>
    </div>
    <div v-if="!isEditing">
      <div v-if="watchers && watchers.length > 0" class="watchers-grid">
        <q-chip
          v-for="watcher in watchers"
          :key="watcher.id"
          removable
          @remove="$emit('remove', watcher.id)"
          dense
          size="md"
          class="q-mr-xs q-mb-xs"
          style="min-width: 0; width: fit-content"
        >
          {{ watcher.nome }}
        </q-chip>
      </div>
      <div v-else class="text-grey">Nenhum observador</div>
    </div>

    <q-select
      v-model="selectedObserver"
      v-else
      outlined
      @keyup.esc="cancelEdit"
      autofocus
      emit-value
      map-options
      dense
      bg-color="white"
      class="no-border"
      :options="observerOptions"
      bottom-slots
      @update:model-value="saveObserver"
    >
      <template v-slot:hint>
        <div class="text-red">
          Pressione
          <kbd>ESC</kbd>
          para cancelar
        </div>
      </template>
    </q-select>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const props = defineProps({
  watchers: {
    type: Array,
    default: () => []
  },
  observerOptions: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['remove', 'save']);

const isEditing = ref(false);
const selectedObserver = ref(null);

function startEditing() {
  isEditing.value = true;
  selectedObserver.value = null;
}

function saveObserver() {
  if (selectedObserver.value) {
    emit('save', selectedObserver.value);
    selectedObserver.value = null;
    isEditing.value = false;
  }
}

function cancelEdit() {
  isEditing.value = false;
}
</script>

<style lang="scss" scoped>
.watchers-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.no-border {
  :deep(.q-field__control) {
    box-shadow: none;
  }
}
</style>