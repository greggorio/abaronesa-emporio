<template>
  <q-dialog v-model="isOpen" persistent>
    <q-card style="min-width: 400px; max-width: 600px">
      <q-card-section class="row items-center bg-primary text-white">
        <q-icon name="smart_toy" size="32px" class="q-mr-sm" />
        <div class="text-h6">Resposta do Assistente</div>
        <q-space />
        <q-btn icon="close" flat round dense v-close-popup @click="handleClose" />
      </q-card-section>

      <q-card-section class="q-pt-md">
        <div class="text-body1" style="white-space: pre-wrap">
          {{ message }}
        </div>
      </q-card-section>

      <q-card-actions align="right">
        <q-btn flat label="Fechar" color="primary" @click="handleClose" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, watch } from "vue";

// Props
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  message: {
    type: String,
    required: true,
  },
});

// Emits
const emit = defineEmits(["update:modelValue", "close"]);

// Estado local
const isOpen = ref(props.modelValue);

// Watchers
watch(
  () => props.modelValue,
  (newVal) => {
    isOpen.value = newVal;
  }
);

watch(isOpen, (newVal) => {
  emit("update:modelValue", newVal);
});

// Métodos
const handleClose = () => {
  isOpen.value = false;
  emit("close");
};
</script>

<style scoped>
/* Estilos específicos se necessário */
</style>
