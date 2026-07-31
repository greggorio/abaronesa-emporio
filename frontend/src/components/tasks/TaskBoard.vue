<template>
  <q-page class="q-pa-md" style="background-color: #f5f1ed">
    <div class="row q-col-gutter-md no-wrap">
      <!-- Listas existentes com draggable -->
      <draggable v-model="taskData.listas" item-key="name" group="lists" class="row q-col-gutter-md no-wrap" @change="onListDragChange">
        <template #item="{ element: lista }">
          <div class="col-xs-13 col-sm-3 col-md-3 col-lg-3" style="min-width: 300px">
            <q-card class="text-primary" style="background-color: #FFFFFF; cursor: pointer; border: 1px solid #E0D5C7">
              <q-card-section class="text-custom-intermediario row items-center text-primary">
                {{ lista.name }}
                <q-chip square color="secondary" text-color="white" size="xs">
                  {{ lista.type }}
                </q-chip>
                <q-space />
                <q-chip color="accent" text-color="primary" size="sm" class="q-ml-sm">
                  {{ lista.tasks.length }}
                </q-chip>
                <q-btn flat round dense icon="more_horiz" size="sm" @click.stop>
                  <q-menu>
                    <q-list style="min-width: 120px">
                      <q-item clickable v-close-popup :disable="lista.tasks.length > 0" @click.stop="deleteList(lista)">
                        <q-item-section>Excluir lista</q-item-section>
                      </q-item>
                    </q-list>
                  </q-menu>
                </q-btn>
              </q-card-section>

              <q-card-section class="q-pt-none">
                <!-- Tasks draggable -->
                <draggable
                  v-model="lista.tasks"
                  :group="'tasks'"
                  item-key="id"
                  class="draggable-list"
                  ghost-class="bg-grey-1"
                  @change="(event) => onDragChange(event, lista.id, lista.name)"
                >
                  <template #item="{ element }">
                    <q-item
                      style="background-color: #FDFBF9; border: 1px solid #E0D5C7"
                      class="q-mb-sm shadow-1 rounded-borders q-pa-sm"
                      clickable
                      @click="openTaskDialog(element, lista.name)"
                    >
                      <q-item-section>
                        <q-item-label class="text-weight-medium text-subtitle1" style="color: #6B3E26">{{ element.title }}</q-item-label>

                        <div class="row items-center q-gutter-xs q-mt-xs flex-wrap">
                          <q-icon name="notes" size="14px" color="grey-7" v-if="element.description">
                            <q-tooltip>Possui descrição</q-tooltip>
                          </q-icon>

                          <q-chip
                            square
                            dense
                            size="sm"
                            icon="event"
                            text-color="primary"
                            :color="isOverdue(element.dueDate) ? 'negative' : 'accent'"
                            v-if="element.dueDate"
                          >
                            {{ formatDate(element.dueDate) }}
                          </q-chip>

                          <q-chip square dense size="sm" :color="getPriorityColor(element.priority)" text-color="white" v-if="element.priority">
                            {{ element.priority }}
                          </q-chip>

                          <q-chip square dense size="sm" color="accent" text-color="primary" icon="person" v-if="element.assignee">
                            {{ element.assignee }}
                          </q-chip>

                          <q-badge outline :color="getStatusColor(element.statusCode)" class="text-bold q-px-sm">
                            {{ element.status }}
                          </q-badge>

                          <div v-if="element.totalDurationSeconds" class="row items-center text-caption text-grey-7 q-ml-sm">
                            <q-icon name="access_time" size="16px" class="q-mr-xs" />
                            {{ formatDuration(element.totalDurationSeconds) }}
                          </div>

                          <div v-if="element.hasActiveSession" class="row items-center text-caption text-green-8 q-ml-sm">
                            <q-icon name="play_circle" size="16px" class="q-mr-xs" />
                            Em andamento
                          </div>
                        </div>
                      </q-item-section>

                      <q-item-section side class="column items-end justify-between">
                        <div class="row items-center q-gutter-sm">
                          <q-icon
                            v-if="element.hasActiveSession && element.activeSessionUserId === getCurrentUserId()"
                            name="pause"
                            color="warning"
                            size="20px"
                            @click.stop="stopWork(element.id)"
                          />
                          <q-icon v-if="!element.hasActiveSession" name="play_arrow" color="positive" size="20px" @click.stop="startWork(element.id)" />
                          <q-icon
                            v-if="element.statusCode !== 'COMPLETED'"
                            name="radio_button_unchecked"
                            color="grey-7"
                            size="18px"
                            @click.stop="completeTask(element.id)"
                          />
                        </div>

                        <q-btn flat round dense icon="more_vert" size="sm" @click.stop>
                          <q-menu>
                            <q-list style="min-width: 120px">
                              <q-item clickable v-close-popup @click.stop="openTaskDialog(element, lista.name)">
                                <q-item-section>Editar</q-item-section>
                              </q-item>
                              <q-item clickable v-close-popup @click.stop="confirmDeleteTask(element, lista.name)">
                                <q-item-section>Excluir</q-item-section>
                              </q-item>
                            </q-list>
                          </q-menu>
                        </q-btn>
                      </q-item-section>
                    </q-item>
                  </template>
                </draggable>

                <div class="text-center q-mt-sm">
                  <div v-if="adicionandoTarefaId !== lista.id">
                    <q-btn
                      flat
                      class="q-mt-sm"
                      icon="add"
                      label="Adicionar tarefa"
                      color="primary"
                      size="sm"
                      @click="adicionandoTarefaId = lista.id"
                    />
                  </div>
                  <div v-else>
                    <q-input v-model="novaTarefaNome" label="Nome da nova tarefa" dense autofocus />
                    <div class="row q-gutter-sm q-mt-sm justify-center">
                      <q-btn flat label="Adicionar" color="primary" size="sm" @click="addTask(lista.id)" />
                      <q-btn flat label="Cancelar" color="grey" size="sm" @click="cancelarAdicionarTarefa" />
                    </div>
                  </div>
                </div>
              </q-card-section>
            </q-card>
          </div>
        </template>
      </draggable>

      <!-- Botão ou input para nova lista -->
      <div class="col-auto" style="min-width: 250px">
        <q-card class="text-primary" style="background-color: #FFFFFF; border: 1px solid #E0D5C7">
          <q-card-section class="q-pa-sm">
            <div v-if="!criandoNovaLista">
              <q-btn flat icon="add" label="Criar nova lista" color="primary" @click="criandoNovaLista = true" />
            </div>
            <div v-else>
              <q-input v-model="newListName" label="Nome da nova lista" dense color="primary" />
              <div class="row q-gutter-sm q-mt-sm">
                <q-btn flat label="Adicionar" color="primary" @click="salvarLista" />
                <q-btn flat label="Fechar" color="secondary" @click="cancelarNovaLista" />
              </div>
            </div>
          </q-card-section>
        </q-card>
      </div>
    </div>

    <!-- Diálogo de edição de tarefa -->
    <task-edit-dialog
      v-model="taskDialog"
      :task="editingTask"
      :list-options="taskData.listas.map((l) => l.name)"
      :default-list-name="editingTask.listName"
      @save="saveTask"
      @cancel="taskDialog = false"
      @load-tasks="loadTasks"
    />

    <!-- Diálogo de confirmação de exclusão -->
    <q-dialog v-model="confirmDialog">
      <q-card>
        <q-card-section class="row items-center">
          <q-avatar icon="delete" color="negative" text-color="white" />
          <span class="q-ml-sm">Tem certeza que deseja excluir esta tarefa?</span>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Cancelar" color="primary" v-close-popup />
          <q-btn flat label="Excluir" color="negative" v-close-popup @click="deleteTask" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script setup>
import { ref, onMounted, reactive, watch, onUnmounted } from "vue";
import { useQuasar } from "quasar";
import draggable from "vuedraggable";
import { uid } from "quasar";
import TaskEditDialog from "./TaskDetailDialog.vue";
import { useApiRequest } from "@/composables/useApiRequest";
import eventBus from "@/eventBus";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Estado dos diálogos
const taskDialog = ref(false);
const confirmDialog = ref(false);
const taskToDelete = ref(null);
const deleteListName = ref("");

// Determina a cor do badge com base no status
function getStatusColor(statusCode) {
  const statusColors = {
    BACKLOG: "grey",
    PLANNED: "info",
    IN_PROGRESS: "warning",
    PAUSED: "secondary",
    BLOCKED: "negative",
    REVIEW: "accent",
    COMPLETED: "positive",
    CANCELLED: "grey-7",
  };
  return statusColors[statusCode] || "grey";
}

// Formata a duração em segundos para um formato legível
function formatDuration(seconds) {
  if (!seconds) return "0s";

  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;

  let result = "";
  if (hours > 0) result += `${hours}h `;
  if (minutes > 0) result += `${minutes}m `;
  if (secs > 0 && hours === 0) result += `${secs}s`;

  return result.trim();
}

// Objeto para edição/criação de tarefas
const editingTask = reactive({
  id: null,
  title: "",
  description: "",
  listName: "",
  dueDate: "",
  priority: "",
  assignee: "",
  watchers: [],
  comments: [],
  historico: [],
});

const taskData = reactive({
  listas: [],
});

// Estrutura de dados para as tarefas
const taskDataOld = reactive({
  listas: [
    {
      name: "Atividade",
      created_by: "greggorio",
      type: "GERAL",
      tasks: [
        {
          id: uid(),
          title: "Criar site",
          description: "Desenvolver o site institucional da empresa",
          dueDate: "2025-05-15",
          priority: "Alta",
          assignee: "João",
          watchers: ["Maria", "Pedro"],
          comments: [
            {
              id: uid(),
              text: "Ótimo trabalho!",
              id_usuario: "1",
              data: "2025-04-20",
            },
          ],
          historico: [
            {
              id: uid(),
              data: "2025-04-20",
              acao: "Criado",
              usuario: "João",
            },
            {
              id: uid(),
              data: "2025-04-21",
              acao: "Atribuído a Maria",
              usuario: "João",
            },
          ],
        },
        {
          id: uid(),
          title: "Implementar FKs",
          description: "Adicionar chaves estrangeiras ao banco de dados",
          dueDate: "2025-04-25",
          priority: "Média",
          assignee: "Maria",
          watchers: [],
          comments: [],
          historico: [],
        },
        {
          id: uid(),
          title: "Criar processo para automatização de commits no git",
          description: "Implementar hooks para automatizar commits",
          dueDate: "2025-05-05",
          priority: "Baixa",
          assignee: "Pedro",
          watchers: [],
          comments: [],
          historico: [],
        },
        {
          id: uid(),
          title: "Criar processo para automatização de deploy",
          description: "Configurar CI/CD para deploys automáticos",
          dueDate: "2025-05-10",
          priority: "Alta",
          assignee: "Ana",
          watchers: [],
          comments: [],
          historico: [],
        },
        {
          id: uid(),
          title: "Revisar mecanismo de log",
          description: "Verificar e melhorar o sistema de logs",
          dueDate: "2025-04-30",
          priority: "Média",
          assignee: "Carlos",
          watchers: [],
          comments: [],
          historico: [],
        },
        {
          id: uid(),
          title: "Implementar versionamento",
          description: "Adicionar controle de versão para a API",
          dueDate: "2025-05-20",
          priority: "Média",
          assignee: "Lucas",
          watchers: [],
          comments: [],
          historico: [],
        },
        {
          id: uid(),
          title: "Implementar tratamento para sessão expirada (403)",
          description: "Adicionar handler para respostas 403",
          dueDate: "2025-04-22",
          priority: "Alta",
          assignee: "Julia",
          watchers: [],
          comments: [],
          historico: [],
        },
      ],
    },
    {
      name: "Em andamento",
      created_by: "greggorio",
      type: "GERAL",
      tasks: [],
    },
    {
      name: "Pausado",
      created_by: "greggorio",
      type: "INDIVIDUAL",
      tasks: [
        {
          id: uid(),
          title: "Configurar servidor com domínio",
          description: "Configurar o servidor para usar www.smardataerp.com.br",
          dueDate: "2025-05-01",
          priority: "Alta",
          assignee: "Roberto",
          watchers: [],
          comments: [],
          historico: [],
        },
      ],
    },
    {
      name: "Concluído",
      created_by: "greggorio",
      type: "INDIVIDUAL",
      tasks: [],
    },
  ],
});

const criandoNovaLista = ref(false);
const newListName = ref("");
const novaTarefaNome = ref("");
const adicionandoTarefaId = ref(null);

// Lógica de ações
function cancelarNovaLista() {
  criandoNovaLista.value = false;
  newListName.value = "";
}

function cancelarAdicionarTarefa() {
  novaTarefaNome.value = "";
  adicionandoTarefaId.value = null;
}

async function addTask(listaId) {
  try {
    const response = await apiRequest("/api/tasks", "POST", {
      title: novaTarefaNome.value,
      priority: "Baixa",
      listId: listaId,
      createdBy: sessionStorage.getItem("user_id"),
    });
    if (response) {
      $q.notify({
        type: "positive",
        message: `Tarefa cadastrada com sucesso!`,
        position: "center",
      });
    }
  } catch (error) {
    console.error("Erro ao salvar tarefa:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao salvar tarefa",
    });
  }
  adicionandoTarefaId.value = null;
  novaTarefaNome.value = "";

  loadTasks();
}

async function salvarLista() {
  try {
    const response = await apiRequest("/api/tasklists", "POST", {
      name: newListName.value,
      createdBy: sessionStorage.getItem("user_id"),
    });
    if (response) {
      $q.notify({
        type: "positive",
        message: `Lista cadastrada com sucesso!`,
        position: "center",
      });
    }
    loadTasks();
  } catch (error) {
    console.error("Erro ao salvar lista:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao salvar lista",
    });
  }
  criandoNovaLista.value = false;
  newListName.value = "";
}

// Observador para detectar mudanças em taskData e salvar no localStorage
watch(
  () => JSON.stringify(taskData),
  (newVal) => {
    console.log("Dados alterados, salvando no localStorage...");
    saveToLocalStorage();
  },
  { deep: true }
);

// Verifica se a tarefa está atrasada
function isOverdue(dueDate) {
  if (!dueDate) return false;
  // Parse date as local time (YYYY-MM-DD format)
  const [year, month, day] = dueDate.split('-').map(Number);
  const dueDateLocal = new Date(year, month - 1, day);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return dueDateLocal < today;
}

// Formata a data para exibição
function formatDate(dateString) {
  if (!dateString) return "";

  // Parse date as local time (YYYY-MM-DD format)
  const [year, month, day] = dateString.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  const now = new Date();
  const currentYear = now.getFullYear();
  const dateYear = date.getFullYear();

  // Mapeamento dos nomes dos meses abreviados em português
  const months = ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"];

  const dayFormatted = date.getDate().toString().padStart(2, "0");
  const monthName = months[date.getMonth()];

  // Se for do ano atual
  if (dateYear === currentYear) {
    return `${dayFormatted} ${monthName}`;
  }
  // Se for de um ano anterior
  else {
    return `${dayFormatted} ${monthName}, ${dateYear}`;
  }
}

// Retorna a cor com base na prioridade
function getPriorityColor(priority) {
  switch (priority) {
    case "Baixa":
      return "green-4";
    case "Média":
      return "amber-4";
    case "Alta":
      return "orange-7";
    case "Urgente":
      return "red-7";
    default:
      return "grey-3";
  }
}

// Retorna a cor com base no tipo de lista
function getTypeColor(type) {
  switch (type) {
    case "GERAL":
      return "info";
    case "INDIVIDUAL":
      return "secondary";
    case "GRUPO":
      return "positive";
    default:
      return "grey-7";
  }
}

// Encontra uma lista pelo nome
function findListByName(name) {
  return taskData.value.listas.find((lista) => lista.name === name);
}

// Abre o diálogo para criar/editar uma tarefa
function openTaskDialog(task = null, listaName = null) {
  if (task) {
    // Editando uma tarefa existente
    Object.assign(editingTask, task);
    editingTask.listName = listaName;
  } else {
    // Criando uma nova tarefa
    const currentDate = new Date().toISOString().split("T")[0];
    Object.assign(editingTask, {
      id: null,
      title: "",
      description: "",
      listName: listaName || taskData.value.listas[0].name,
      dueDate: "",
      priority: "",
      assignee: "",
      watchers: [],
      comments: [],
      historico: [
        {
          id: uid(),
          data: currentDate,
          acao: "Criado",
          usuario: "greggorio",
        },
      ],
    });
  }
  taskDialog.value = true;
}

// Salva a tarefa atual
function saveTask() {
  if (!editingTask.title) return;

  const currentDate = new Date().toISOString().split("T")[0];

  if (editingTask.id) {
    // Atualizando uma tarefa existente
    let foundInList = null;
    let indexInList = -1;

    // Procurar a tarefa em todas as listas
    for (const lista of taskData.value.listas) {
      const index = lista.tasks.findIndex((t) => t.id === editingTask.id);
      if (index !== -1) {
        foundInList = lista;
        indexInList = index;
        break;
      }
    }

    if (foundInList && indexInList !== -1) {
      // Adicionar ao histórico
      if (!editingTask.historico) editingTask.historico = [];

      editingTask.historico.push({
        id: uid(),
        data: currentDate,
        acao: `Editado`,
        usuario: "greggorio",
      });

      // Se a lista mudou
      if (foundInList.name !== editingTask.listName) {
        // Adicionar ao histórico a mudança de lista
        editingTask.historico.push({
          id: uid(),
          data: currentDate,
          acao: `Movido de ${foundInList.name} para ${editingTask.listName}`,
          usuario: "greggorio",
        });

        // Remover da lista atual
        foundInList.tasks.splice(indexInList, 1);

        // Adicionar na nova lista
        const newList = findListByName(editingTask.listName);
        if (newList) {
          newList.tasks.push({ ...editingTask });
        }
      } else {
        // Atualizar na mesma lista
        foundInList.tasks[indexInList] = { ...editingTask };
      }
    }
  } else {
    // Criando uma nova tarefa
    const newTask = {
      ...editingTask,
      id: uid(),
    };

    // Encontrar a lista correta e adicionar a tarefa
    const targetList = findListByName(editingTask.listName);
    if (targetList) {
      targetList.tasks.push(newTask);
    }
  }

  taskDialog.value = false;
  saveToLocalStorage();

  $q.notify({
    message: editingTask.id ? "Tarefa atualizada com sucesso!" : "Tarefa criada com sucesso!",
    color: "positive",
    icon: "check_circle",
  });
}

// Confirmar exclusão de tarefa
function confirmDeleteTask(task, listaName) {
  taskToDelete.value = task;
  deleteListName.value = listaName;
  confirmDialog.value = true;
}

// Excluir a tarefa
function deleteTask() {
  if (!taskToDelete.value) return;

  const lista = findListByName(deleteListName.value);
  if (lista) {
    const index = lista.tasks.findIndex((t) => t.id === taskToDelete.value.id);
    if (index !== -1) {
      lista.tasks.splice(index, 1);
    }
  }

  saveToLocalStorage();

  $q.notify({
    message: "Tarefa excluída com sucesso!",
    color: "negative",
    icon: "delete",
  });

  taskToDelete.value = null;
  deleteListName.value = "";
}

async function onDragChange(event, listId, listName) {
  if (event.added) {
    const task = event.added.element;
    const taskId = task.id;

    const userId = getCurrentUserId();

    const apiURL = `/api/tasks/${taskId}/move?listId=${listId}&userId=${userId}`;

    try {
      await apiRequest(apiURL, "PUT");

      $q.notify({
        message: `Tarefa movida para ${listName}`,
        color: "info",
        icon: "move_to_inbox",
      });
    } catch (error) {
      console.error("Erro ao mover tarefa:", error);
      $q.notify({
        message: `Erro ao mover tarefa: ${error.message || "Erro desconhecido"}`,
        color: "negative",
        icon: "error",
      });
    }

    loadTasks();
  }
}

function getCurrentUserId() {
  const user = sessionStorage.getItem("user_id");
  return user.id || 1;
}

function onListDragChange(event) {
  if (event.moved) {
    $q.notify({ message: "Listas reordenadas", color: "info", icon: "swap_vert" });
    saveToLocalStorage();
  }
}

// Salvar no localStorage
function saveToLocalStorage() {
  try {
    localStorage.setItem("taskBoard", JSON.stringify(taskData));
    console.log("Dados salvos no localStorage com sucesso!");
  } catch (e) {
    console.error("Erro ao salvar no localStorage:", e);
  }
}

const loadTasks = async () => {
  console.log("Carregando tarefas do backend...");
  try {
    // Corrigindo o URL para corresponder ao endpoint do backend
    const apiURL = `/api/taskboard`;
    const response = await apiRequest(apiURL);

    if (response && response.listas) {
      // Para reactive, atualizamos as propriedades diretamente
      taskData.listas = response.listas; // Correto para reactive
    }
  } catch (error) {
    console.error("Erro ao carregar tarefas:", error);
  }
};

onMounted(() => {
  loadTasks();
});

// Iniciar trabalho em uma tarefa
async function startWork(taskId) {
  const userId = getCurrentUserId();
  try {
    await apiRequest(`/api/tasks/${taskId}/time/start?userId=${userId}`, "POST");
    loadTasks(); // Recarregar para atualizar status
    $q.notify({
      message: "Trabalho iniciado",
      color: "positive",
    });
  } catch (error) {
    console.error("Erro ao iniciar trabalho:", error);
    $q.notify({
      message: error.response?.data?.error || "Não foi possível iniciar o trabalho",
      color: "negative",
    });
  }
}

// Parar trabalho em uma tarefa
async function stopWork(taskId) {
  const userId = getCurrentUserId();

  // Precisamos primeiro obter o ID da entrada de tempo ativa
  try {
    // Obter a tarefa atualizada para ter o ID da sessão ativa
    const taskData = await apiRequest(`/api/tasks/${taskId}`, "GET");
    const activeEntries = await apiRequest(`/api/tasks/${taskId}/time`, "GET");
    const activeEntry = activeEntries.find((entry) => entry.endTime === null);

    if (activeEntry) {
      const apiURL = `/api/tasks/${taskId}/time/${activeEntry.id}/stop?userId=${userId}`;
      console.log("API URL:", apiURL);
      await apiRequest(apiURL, "POST");
      loadTasks(); // Recarregar para atualizar status
      $q.notify({
        message: "Trabalho pausado",
        color: "info",
      });
    }
  } catch (error) {
    console.error("Erro ao pausar trabalho:", error);
    $q.notify({
      message: "Não foi possível pausar o trabalho",
      color: "negative",
    });
  }
}

// Concluir uma tarefa
async function completeTask(taskId) {
  const userId = getCurrentUserId();
  try {
    await apiRequest(`/api/tasks/${taskId}/time/status?status=COMPLETED&userId=${userId}`, "PUT");
    loadTasks(); // Recarregar para atualizar status
    $q.notify({
      message: "Tarefa concluída",
      color: "positive",
    });
  } catch (error) {
    console.error("Erro ao concluir tarefa:", error);
    $q.notify({
      message: "Não foi possível concluir a tarefa",
      color: "negative",
    });
  }
}
</script>

<style scoped>
.draggable-list {
  min-height: 50px;
}

.q-card--bordered {
  border: 0px solid #ddd;
  transition: all 0.2s;
  background: #f1f5f9;
}

.q-card--bordered:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.q-chip {
  margin-right: 2px;
}

.task-title {
  font-size: 0.85rem;
  font-weight: 500;
  color: #424242;
  line-height: 1.2;
  padding-right: 12px;
}

.text-custom-intermediario {
  font-size: 1rem;
  line-height: 1.5;
  font-weight: 500;
}
</style>
