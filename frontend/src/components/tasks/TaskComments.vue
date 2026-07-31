<template>
  <div>
    <div v-if="comments && comments.length > 0">
      <div v-for="comment in comments" :key="comment.id" class="comment-item q-mb-md">
        <div class="row items-start no-wrap">
          <q-avatar size="32px" color="info" text-color="white" class="q-mr-md">
            {{ getInitials(comment.usuario || "Usuário") }}
          </q-avatar>
          <div class="comment-content col">
            <div class="row items-center justify-between q-mb-xs">
              <div class="text-weight-bold">{{ comment.usuario || "Usuário" }}</div>
              <div class="text-caption text-grey">{{ formatDate(comment.data) }}</div>
            </div>
            <div class="comment-text">{{ comment.text }}</div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="text-center q-pa-md text-grey">Sem comentários. Seja o primeiro a comentar!</div>

    <!-- Input de comentário -->
    <div class="new-comment q-mt-md">
      <q-input v-model="newComment" placeholder="Adicione um comentário..." bg-color="white" outlined class="comment-input" dense bottom-slots>
        <template v-slot:after>
          <q-btn round dense flat color="primary" icon="send" @click="submitComment" :disable="!newComment.trim()" />
        </template>
      </q-input>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useTaskUtils } from "@/composables/useTaskUtils";
import { useApiRequest } from "@/composables/useApiRequest";
import { useQuasar } from "quasar";

const { apiRequest } = useApiRequest();
const { formatDate, getInitials } = useTaskUtils();
const q = useQuasar();

const props = defineProps({
  taskId: {
    type: [Number, String],
    required: true,
  },
  currentUserId: {
    type: [Number, String],
    default: null,
  },
});

const emit = defineEmits(["comments-loaded"]);
const newComment = ref("");
const comments = ref([]);
const isLoading = ref(false);

// Carregar comentários quando o componente é montado
onMounted(() => {
  loadComments();
});

// Carregar todos os comentários para a tarefa atual
async function loadComments() {
  isLoading.value = true;
  try {
    const apiURL = `/api/tasks/${props.taskId}/comments`;
    const response = await apiRequest(apiURL, "GET");
    comments.value = response || [];
    emit("comments-loaded", comments.value);
  } catch (error) {
    console.error("Erro ao carregar comentários:", error);
    q.notify({
      message: "Erro ao carregar comentários",
      color: "negative",
      icon: "error",
      position: "top",
    });
  } finally {
    isLoading.value = false;
  }
}

// Enviar novo comentário
async function submitComment() {
  if (!newComment.value.trim()) return;

  try {
    const apiURL = `/api/tasks/${props.taskId}/comments`;
    const response = await apiRequest(apiURL, "POST", { text: newComment.value });

    // Adicionar o novo comentário à lista sem precisar recarregar todos
    if (response) {
      comments.value.unshift(response);
      emit("comments-loaded", comments.value);
    }

    // Limpar o campo de entrada
    newComment.value = "";

    q.notify({
      message: "Comentário adicionado com sucesso",
      color: "positive",
      icon: "check_circle",
      position: "center",
    });
  } catch (error) {
    console.error("Erro ao adicionar comentário:", error);
    q.notify({
      message: "Erro ao adicionar comentário",
      color: "negative",
      icon: "error",
      position: "top",
    });
  }
}
</script>

<style lang="scss" scoped>
.comment-item {
  padding: 12px;
  border-radius: 8px;
  background-color: #f4f5f6;

  .comment-text {
    white-space: pre-line;
  }
}

.comment-input {
  .q-field__control {
    background-color: white;
  }
}
</style>
