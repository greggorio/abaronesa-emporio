<template>
  <q-dialog v-model="showDialog" persistent seamless>
    <q-card class="task-detail-card" style="background-color: #ffffff">
      <!-- Componente do cabeçalho -->
      <TaskHeader :task-data="taskData" @save="saveEdit" @cancel="onCancel" />

      <div class="row items-center q-gutter-md">
        <div class="row items-center" @click="toggleWatch()">
          <div class="bg-white" style="width: 24px; height: 24px"></div>
          <q-chip
            style="cursor: pointer"
            square
            outline
            :icon="isUserWatching ? 'visibility_off' : 'visibility'"
            :label="isUserWatching ? 'Deixar de acompanhar tarefa' : 'Acompanhar tarefa'"
            color="primary"
            text-color="primary"
          />
        </div>
      </div>

      <!-- Conteúdo principal -->
      <div class="task-content-wrapper row no-wrap">
        <!-- Área principal - Descrição e abas -->
        <div class="task-main-content col-12 col-md-8">
          <q-card class="task-inner-card q-ma-md">
            <q-card-section>
              <!-- Componente de descrição -->
              <TaskDescription v-model="taskData.description" @save="saveEdit" />

              <!-- Abas: Comentários e Histórico com estilo mais clean -->
              <div class="q-mt-lg">
                <q-tabs
                  v-model="activeTab"
                  dense
                  class="text-grey"
                  active-color="primary"
                  indicator-color="primary"
                  align="left"
                  narrow-indicator
                  no-caps
                >
                  <q-tab name="comments" label="Comentários" />
                  <q-tab name="history" label="Histórico" />
                </q-tabs>

                <q-separator color="grey-3" />

                <q-tab-panels v-model="activeTab" animated transition-prev="fade" transition-next="fade">
                  <!-- Painel de Comentários -->
                  <q-tab-panel name="comments" class="q-pa-md">
                    <TaskComments :task-id="taskData.id" @comments-loaded="onCommentsLoaded" />
                  </q-tab-panel>

                  <!-- Painel de Histórico -->
                  <q-tab-panel name="history" class="q-pa-md">
                    <TaskHistory :history-data="taskData.historico" />
                  </q-tab-panel>
                </q-tab-panels>
              </div>
            </q-card-section>
          </q-card>
        </div>

        <!-- Barra lateral - Detalhes e ações -->
        <TaskSidebar
          :task-data="taskData"
          :user-options="userOptions"
          :observer-options="observadorOptions"
          @toggle-complete="toggleComplete"
          @save-edit="saveEdit"
          @remove-observer="removeObserver"
          @save-observer="saveObservador"
        />
      </div>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, reactive, watch, computed, onMounted, unref } from "vue";
import { uid, useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import TaskHeader from "./TaskHeader.vue";
import TaskDescription from "./TaskDescription.vue";
import TaskComments from "./TaskComments.vue";
import TaskHistory from "./TaskHistory.vue";
import TaskSidebar from "./TaskSidebar.vue";
import { useTaskUtils } from "@/composables/useTaskUtils";

const { apiRequest } = useApiRequest();
const { formatDateFull, formatDate, getInitials, isOverdue } = useTaskUtils();

const $q = useQuasar();
const activeTab = ref("comments");
const originalTaskData = ref({});
const users = ref([]);
const newObserver = ref(null);
const currentUserId = sessionStorage.getItem("user_id");

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  task: {
    type: Object,
    default: () => ({
      id: null,
      title: "",
      description: "",
      listName: "",
      dueDate: "",
      priority: "",
      assignee: "",
      watchers: [],
      coments: [],
      historico: [],
    }),
  },
  listOptions: {
    type: Array,
    default: () => [],
  },
  defaultListName: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["load-tasks", "update:modelValue", "cancel"]);

// Estado local que espelha o task do prop
const taskData = reactive({ ...props.task });

// Verifica se o usuário atual está acompanhando a tarefa
const isUserWatching = computed(() => {
  const userId = unref(currentUserId); // desembrulha o ref, se for o caso
  if (!taskData.watchers?.length) return false; // optional chaining + early return

  return taskData.watchers.some(
    (watcher) =>
      // força Number() em caso de string, ou vice‑versa
      Number(watcher.id) === Number(userId)
  );
});
// Sincroniza o showDialog com o modelValue
const showDialog = ref(props.modelValue);
watch(
  () => props.modelValue,
  (newVal) => {
    showDialog.value = newVal;
    if (newVal) {
      activeTab.value = "comments";
    }
  }
);

watch(
  () => showDialog.value,
  (newVal) => {
    emit("update:modelValue", newVal);
  }
);

// Assiste mudanças na tarefa vinda de props
watch(
  () => props.task,
  (newTask) => {
    if (newTask) {
      // Reset do taskData quando uma nova task é fornecida
      Object.assign(taskData, newTask);
      // Guarda uma cópia para possível cancelamento da edição
      originalTaskData.value = { ...newTask };

      if (props.task.id) {
        loadObservadores();
      }
    }
  },
  { deep: true }
);

// Inicializa a tarefa quando o componente é montado
if (!taskData.id && props.defaultListName) {
  taskData.listName = props.defaultListName;

  const currentDate = new Date().toISOString().split("T")[0];

  // Inicializa o histórico para novas tarefas
  if (!taskData.historico || !taskData.historico.length) {
    taskData.historico = [
      {
        id: uid(),
        data: currentDate,
        acao: "Criado",
        usuario: "greggorio",
      },
    ];
  }

  // Inicializa os comentários como array vazio
  if (!taskData.coments) taskData.coments = [];
}

// Alternar acompanhamento da tarefa
async function toggleWatch() {
  try {
    if (isUserWatching.value) {
      console.log("Desativando acompanhamento");
      const watcherId = currentUserId;
      await removeObserver(watcherId);
    } else {
      console.log("Ativando acompanhamento");
      await saveObservador(currentUserId);
    }
  } catch (error) {
    console.error("Erro ao alterar estado de acompanhamento:", error);
    $q.notify({
      message: "Ocorreu um erro ao alterar o acompanhamento da tarefa",
      color: "negative",
      icon: "error",
      position: "top-right",
    });
  }
}

async function removeObserver(observerId) {
  const apiURL = `/api/tasks/${taskData.id}/watchers/${observerId}?currentUserId=${currentUserId}`;
  await apiRequest(apiURL, "DELETE");
  loadObservadores();
  $q.notify({
    message: "Observador removido com sucesso",
    color: "info",
    icon: "check_circle",
    position: "center",
  });
}

async function saveObservador(observerId) {
  const apiURL = `/api/tasks/${taskData.id}/watchers?userId=${observerId}&currentUserId=${currentUserId}`;
  await apiRequest(apiURL, "POST");
  newObserver.value = null;
  loadObservadores();
  $q.notify({
    message: "Observador adicionado com sucesso",
    color: "positive",
    icon: "check_circle",
    position: "center",
  });
}

// Salvar a edição
async function saveEdit(values) {
  // Se valores forem passados, atualize o taskData com eles
  if (values) {
    if (values.status) {
      // transformar em uppercase
      taskData.status = values.statusCode;
    }
    if (values.assigneeId) {
      taskData.assignee = values.assignee;
      taskData.assigneeId = values.assigneeId;
    }
    if (values.description !== undefined) {
      taskData.description = values.description;
    }
    if (values.title !== undefined) {
      taskData.title = values.title;
    }
    if (values.dueDate !== undefined) {
      taskData.dueDate = values.dueDate;
    }
    if (values.priority !== undefined) {
      taskData.priority = values.priority;
    }
  }
  taskData.status = taskData.statusCode;

  const apiURL = `/api/tasks/${taskData.id}`;
  await apiRequest(apiURL, "PUT", taskData);
  emit("load-tasks");
}

function onCommentsLoaded(loadedComments) {
  console.log(`${loadedComments.length} comentários carregados`);
  // Pode atualizar contadores ou fazer outras ações quando comentários são carregados
}

// Alternar status para concluído
function toggleComplete() {
  const currentDate = new Date().toISOString().split("T")[0];
  const newStatus = taskData.listName === "Concluído" ? "Em andamento" : "Concluído";
  const action = taskData.listName === "Concluído" ? "Reaberto" : "Concluído";

  // Atualiza o status
  taskData.listName = newStatus;

  // Adiciona ao histórico
  if (!taskData.historico) taskData.historico = [];
  taskData.historico.push({
    id: uid(),
    data: currentDate,
    acao: action,
    usuario: "greggorio",
  });

  $q.notify({
    message: action === "Concluído" ? "Tarefa marcada como concluída!" : "Tarefa reaberta",
    color: action === "Concluído" ? "positive" : "info",
    icon: action === "Concluído" ? "check_circle" : "replay",
    position: "top-right",
  });
}

function onCancel() {
  emit("cancel");
  showDialog.value = false;
}

const userOptions = computed(() => {
  return users.value.map((user) => ({
    value: user.id,
    label: user.id + " - " + user.nome,
  }));
});

const observadorOptions = computed(() => {
  const watcherIds = taskData.watchers?.map((w) => w.id) || [];

  return userOptions.value.filter((option) => {
    return option.value !== taskData.assigneeId && !watcherIds.includes(option.value);
  });
});

const loadUsers = async () => {
  try {
    const apiURL = `/api/usuario`;
    const response = await apiRequest(apiURL);
    if (response) {
      users.value = response.objeto;
    }
  } catch (error) {
    console.error("Erro ao carregar usuários:", error);
  }
};

const loadObservadores = async () => {
  if (!taskData.id) {
    console.error("ID da tarefa não definido");
    return;
  }
  try {
    const apiURL = `/api/tasks/${props.task.id}/watchers`;
    const response = await apiRequest(apiURL);
    if (response) {
      taskData.watchers = response;
    }
  } catch (error) {
    console.error("Erro ao carregar observadores:", error);
  }
};

onMounted(async () => {
  await loadUsers();
  loadObservadores();
});
</script>

<style lang="scss" scoped>
.task-detail-card {
  max-width: 1100px;
  width: 90vw;
  max-height: 90vh;
  margin: 0 auto;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.task-content-wrapper {
  overflow-y: auto;
  flex: 1;

  @media (max-width: 767px) {
    flex-direction: column;
  }
}

.task-inner-card {
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.05);
}

// Estilização de abas mais clean e harmoniosa
:deep(.q-tabs) {
  min-height: 36px;

  .q-tab {
    padding: 0 16px;
    min-height: 36px;

    &__content {
      .q-tab__label {
        font-weight: 400;
        font-size: 14px;
      }
    }

    &--active {
      font-weight: 500;
    }
  }

  .q-tabs__content {
    color: #757575;
  }
}

:deep(.q-tab-panels) {
  background: transparent;
}
</style>
