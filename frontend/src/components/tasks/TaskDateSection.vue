<template>
  <div class="detail-item q-mb-md">
    <div class="detail-label text-caption q-mb-xs" style="color: #8a8c8c">Data de vencimento</div>
    <div class="row items-center" v-if="!isEditing" @click="startEditing">
      <q-icon name="event" size="18px" color="info" class="q-mr-xs" />
      <div :class="{ 'text-negative': isOverdue(date) }">
        {{ date ? formatDateFull(date) : "Não definida" }}
      </div>
    </div>
    <q-input v-else v-model="localDate" outlined dense bg-color="white" class="no-border">
      <template v-slot:append>
        <q-icon name="event" class="cursor-pointer">
          <q-popup-proxy cover transition-show="scale" transition-hide="scale">
            <q-date v-model="localDate" mask="YYYY-MM-DD" color="primary" @update:model-value="saveDate" />
          </q-popup-proxy>
        </q-icon>
      </template>
    </q-input>
  </div>
</template>

<script setup>
import { ref, watch } from "vue";
import { useTaskUtils } from "@/composables/useTaskUtils";

const { formatDateFull, isOverdue } = useTaskUtils();

const props = defineProps({
  date: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["save"]);

const isEditing = ref(false);
const localDate = ref(props.date);

// Atualiza o valor local quando a prop muda
watch(
  () => props.date,
  (newDate) => {
    localDate.value = newDate;
  }
);

function startEditing() {
  isEditing.value = true;
}

function saveDate() {
  emit("save", localDate.value);
  isEditing.value = false;
}
</script>
