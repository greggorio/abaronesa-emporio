<template>
  <div class="detail-item q-mb-md">
    <div class="detail-label text-caption q-mb-xs" style="color: #8a8c8c">Prioridade</div>
    <div v-if="!isEditing" class="row items-center" @click="startEditing">
      <q-icon name="flag" size="18px" :color="getPriorityIconColor(priority)" class="q-mr-xs" />
      <div>{{ priority || "Não definida" }}</div>
    </div>
    <q-select
      v-else
      v-model="localPriority"
      :options="['Baixa', 'Média', 'Alta', 'Urgente']"
      outlined
      dense
      class="no-border"
      @update:model-value="savePriority"
    />
  </div>
</template>

<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  priority: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["save"]);

const isEditing = ref(false);
const localPriority = ref(props.priority);

// Atualiza o valor local quando a prop muda
watch(
  () => props.priority,
  (newPriority) => {
    localPriority.value = newPriority;
  }
);

function getPriorityIconColor(priority) {
  switch (priority) {
    case "Baixa":
      return "green";
    case "Média":
      return "orange";
    case "Alta":
      return "deep-orange";
    case "Urgente":
      return "red";
    default:
      return "grey";
  }
}

function startEditing() {
  isEditing.value = true;
}

function savePriority() {
  emit("save", localPriority.value);
  isEditing.value = false;
}
</script>
