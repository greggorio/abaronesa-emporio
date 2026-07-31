<template>
  <div class="detail-item q-mb-md">
    <div class="detail-label text-caption q-mb-xs" style="color: #8a8c8c">Responsável</div>
    <div v-if="!isEditing" class="row items-center" @click="startEditing">
      <q-avatar size="24px" color="#84b9d4" text-color="white" class="q-mr-xs">
        {{ getInitials(assignee) }}
      </q-avatar>
      <div>{{ assignee || "Não atribuído" }}</div>
    </div>
    <q-select
      v-else
      v-model="selectedUser"
      :options="userOptions"
      @keyup.esc="cancelEdit"
      outlined
      autofocus
      dense
      class="q-mb-md no-border"
      @update:model-value="saveAssignee"
      bottom-slots
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
import { useTaskUtils } from '@/composables/useTaskUtils';

const { getInitials } = useTaskUtils();

const props = defineProps({
  assignee: {
    type: String,
    default: ''
  },
  assigneeId: {
    type: [Number, String],
    default: ''
  },
  userOptions: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['save']);

const isEditing = ref(false);
const selectedUser = ref(null);

function startEditing() {
  // Encontrar o usuário atual na lista de opções
  selectedUser.value = props.userOptions.find(user => user.value === props.assigneeId) || null;
  isEditing.value = true;
}

function saveAssignee() {
  emit('save', selectedUser.value);
  isEditing.value = false;
}

function cancelEdit() {
  isEditing.value = false;
}
</script>

<style lang="scss" scoped>
.no-border {
  :deep(.q-field__control) {
    box-shadow: none;
  }
}
</style>