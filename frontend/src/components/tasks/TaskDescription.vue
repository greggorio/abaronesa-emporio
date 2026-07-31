<template>
  <div class="description-container q-pa-md q-mb-md rounded-borders" style="background-color: #f4f5f6">
    <div class="text-subtitle2 q-mb-sm" style="color: #8a8c8c">Descrição</div>
    <div v-if="!isEditing" class="description-text" @click="startEditing" style="color: #1b254b">
      {{ modelValue || "Sem descrição fornecida." }}
    </div>
    <q-input
      v-else
      v-model="localDescription"
      @blur="saveDescription"
      @keyup.enter="saveDescription"
      @keyup.esc="cancelEdit"
      type="textarea"
      autofocus
      outlined
      autogrow
      bg-color="white"
      placeholder="Adicione uma descrição..."
      class="no-border"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update:modelValue', 'save']);

const isEditing = ref(false);
const localDescription = ref(props.modelValue);

watch(() => props.modelValue, (newValue) => {
  if (!isEditing.value) {
    localDescription.value = newValue;
  }
});

function startEditing() {
  isEditing.value = true;
}

function saveDescription() {
  isEditing.value = false;
  emit('save', { description: localDescription.value });
}

function cancelEdit() {
  localDescription.value = props.modelValue;
  isEditing.value = false;
}
</script>

<style lang="scss" scoped>
.description-container {
  border-radius: 8px;
  position: relative;

  .description-text {
    white-space: pre-line;
    min-height: 40px;
  }
}

.no-border {
  :deep(.q-field__control) {
    box-shadow: none;
  }
}
</style>