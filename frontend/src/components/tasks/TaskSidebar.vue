<template>
  <div class="task-sidebar col-12 col-md-4">
    <q-card flat class="q-ma-md">
      <q-card-section>
        <div class="text-subtitle1 q-mb-md text-weight-bold" style="color: #1b254b">Detalhes</div>

        <!-- Status e botão para concluir -->
        <TaskStatusSection :list-name="taskData.status" @toggle-complete="$emit('toggle-complete')" />

        <q-separator class="q-my-md" />

        <!-- Detalhes da tarefa -->
        <div class="detail-sections">
          <!-- Data de vencimento -->
          <TaskDateSection :date="taskData.dueDate" @save="(newDate) => $emit('save-edit', { dueDate: newDate })" />

          <!-- Prioridade -->
          <TaskPrioritySection :priority="taskData.priority" @save="(newPriority) => $emit('save-edit', { priority: newPriority })" />

          <!-- Responsável -->
          <TaskAssigneeSection
            :assignee="taskData.assignee"
            :assignee-id="taskData.assigneeId"
            :user-options="userOptions"
            @save="
              (newAssignee) =>
                $emit('save-edit', {
                  assigneeId: newAssignee.value,
                  assignee: newAssignee.label.split(' - ')[1],
                })
            "
          />

          <!-- Observadores -->
          <TaskWatchersSection
            :watchers="taskData.watchers"
            :observer-options="observerOptions"
            @remove="(id) => $emit('remove-observer', id)"
            @save="(id) => $emit('save-observer', id)"
          />
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import TaskStatusSection from "./TaskStatusSection.vue";
import TaskDateSection from "./TaskDateSection.vue";
import TaskPrioritySection from "./TaskPrioritySection.vue";
import TaskAssigneeSection from "./TaskAssigneeSection.vue";
import TaskWatchersSection from "./TaskWatchersSection.vue";

const props = defineProps({
  taskData: {
    type: Object,
    required: true,
  },
  userOptions: {
    type: Array,
    default: () => [],
  },
  observerOptions: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["toggle-complete", "save-edit", "remove-observer", "save-observer"]);
</script>
