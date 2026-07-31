<template>
  <q-card-section class="header-section">
    <q-btn flat round dense icon="close" class="absolute-top-right q-ma-sm" style="cursor: pointer; z-index: 1000" @click="$emit('cancel')" />
    <div class="row items-center justify-between q-py-sm">
      <div class="column">
        <div v-if="!isEditingTitle" class="text-h5 text-weight-bold" @click="isEditingTitle = true" style="color: #1b254b">
          {{ taskData.title }}
        </div>
        <q-input
          v-else
          v-model="editableTitle"
          outlined
          dense
          bg-color="white"
          class="no-border"
          @blur="saveTitle"
          @keyup.enter="saveTitle"
          @keyup.esc="cancelTitleEdit"
          placeholder="Título da tarefa"
          autofocus
        />

        <div class="flex items-center q-pa-xs">
          <span>na lista</span>
          <q-chip square text-color="white" color="grey-6" size="sm" class="status-chip">
            {{ taskData.listName }}
          </q-chip>
        </div>
      </div>
    </div>
  </q-card-section>
</template>

<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  taskData: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(["save", "cancel"]);

const isEditingTitle = ref(false);
const editableTitle = ref(props.taskData.title);

watch(
  () => props.taskData.title,
  (newTitle) => {
    editableTitle.value = newTitle;
  }
);

function saveTitle() {
  if (editableTitle.value.trim() !== "") {
    emit("save", { title: editableTitle.value });
    isEditingTitle.value = false;
  }
}

function cancelTitleEdit() {
  editableTitle.value = props.taskData.title;
  isEditingTitle.value = false;
}
</script>

<style lang="scss" scoped>
.header-section {
  border-radius: 8px 8px 0 0;
  padding: 6px 10px;
  transition: all 0.3s ease;
}

.status-chip {
  transition: all 0.3s ease;
}

// Remover borda de inputs quando não em edição
.no-border {
  :deep(.q-field__control) {
    box-shadow: none;
  }
}
</style>
