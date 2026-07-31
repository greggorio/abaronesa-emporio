<template>
  <div class="q-pa-md">
    <div class="row items-center q-mb-md">
      <div class="col">
        <div class="text-h5 text-primary">Agenda de Execução</div>
        <div class="text-body2 text-grey-7">Gerencie jobs agendados, edite cron, ative/desative e execute manualmente.</div>
      </div>
      <div class="col-auto">
        <q-btn color="primary" flat round icon="refresh" @click="loadJobs" :loading="loading" />
      </div>
    </div>

    <q-card flat bordered>
      <q-table
        :rows="jobs"
        :columns="columns"
        row-key="id"
        :loading="loading"
        flat
        :rows-per-page-options="[10, 20, 50]"
      >
        <template #body-cell-active="props">
          <q-td :props="props">
            <q-toggle
              size="sm"
              :model-value="props.row.active"
              @update:model-value="(val) => updateJob(props.row, { active: val })"
              color="primary"
            />
          </q-td>
        </template>

        <template #body-cell-cron="props">
          <q-td :props="props">
            <q-input
              dense
              standout
              v-model="props.row.cron"
              @blur="updateJob(props.row, { cron: props.row.cron })"
              @keyup.enter="updateJob(props.row, { cron: props.row.cron })"
              style="max-width: 220px"
            />
            <div class="text-caption text-grey-6">Próx: {{ formatDateTime(props.row.nextRunAt) || '-' }}</div>
          </q-td>
        </template>

        <template #body-cell-lastExecution="props">
          <q-td :props="props">
            <div v-if="props.row.lastExecution">
              <div>
                <q-badge :color="props.row.lastExecution.status === 'SUCCESS' ? 'green' : 'red'" align="top">
                  {{ props.row.lastExecution.status }}
                </q-badge>
                <span class="q-ml-sm text-caption">
                  {{ formatDateTime(props.row.lastExecution.finishedAt || props.row.lastExecution.startedAt) }}
                </span>
              </div>
              <div class="text-caption text-grey-7">
                Registros: {{ props.row.lastExecution.recordsAffected ?? '-' }}
              </div>
              <div class="text-caption text-grey-7 ellipsis" :title="props.row.lastExecution.message">{{ props.row.lastExecution.message }}</div>
            </div>
            <div v-else class="text-grey-6">Nunca executado</div>
          </q-td>
        </template>

        <template #body-cell-actions="props">
          <q-td :props="props">
            <q-btn size="sm" color="primary" flat icon="play_arrow" @click="runNow(props.row)" :loading="runningId === props.row.id">
              <q-tooltip>Executar agora</q-tooltip>
            </q-btn>
            <q-btn size="sm" color="secondary" flat icon="history" class="q-ml-sm" @click="openHistory(props.row)">
              <q-tooltip>Histórico</q-tooltip>
            </q-btn>
          </q-td>
        </template>
      </q-table>
    </q-card>

    <q-dialog v-model="historyDialog.open" persistent>
      <q-card style="min-width: 500px">
        <q-card-section class="row items-center">
          <div class="text-h6">Histórico — {{ historyDialog.job?.name }}</div>
          <q-space />
          <q-btn dense flat round icon="close" v-close-popup />
        </q-card-section>
        <q-separator />
        <q-card-section>
          <q-list bordered dense>
            <q-item v-for="exec in historyDialog.executions" :key="exec.id">
              <q-item-section>
                <div class="row items-center q-gutter-sm">
                  <q-badge :color="exec.status === 'SUCCESS' ? 'green' : 'red'">{{ exec.status }}</q-badge>
                  <div class="text-caption text-grey-7">Início: {{ formatDateTime(exec.startedAt) }}</div>
                  <div class="text-caption text-grey-7">Fim: {{ formatDateTime(exec.finishedAt) || '-' }}</div>
                  <div class="text-caption text-grey-7">Registros: {{ exec.recordsAffected ?? '-' }}</div>
                </div>
                <div class="text-body2">{{ exec.message }}</div>
              </q-item-section>
            </q-item>
            <q-item v-if="!historyDialog.executions.length">
              <q-item-section class="text-grey-6">Sem execuções registradas.</q-item-section>
            </q-item>
          </q-list>
        </q-card-section>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import { format } from "date-fns";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

const loading = ref(false);
const runningId = ref(null);
const jobs = ref([]);

const columns = [
  { name: "name", label: "Nome", field: "name", align: "left", sortable: true },
  { name: "key", label: "Chave", field: "key", align: "left", sortable: true },
  { name: "cron", label: "Cron", field: "cron", align: "left" },
  { name: "active", label: "Ativo", field: "active", align: "left" },
  { name: "lastExecution", label: "Última Execução", field: "lastExecution", align: "left" },
  { name: "actions", label: "Ações", field: "actions", align: "right" },
];

const historyDialog = ref({
  open: false,
  job: null,
  executions: [],
});

const formatDateTime = (val) => {
  if (!val) return "";
  try {
    return format(new Date(val), "dd/MM/yyyy HH:mm");
  } catch (e) {
    return val;
  }
};

const loadJobs = async () => {
  loading.value = true;
  try {
    const data = await apiRequest("/api/jobs");
    jobs.value = data || [];
  } catch (error) {
    $q.notify({ type: "negative", message: `Erro ao carregar jobs: ${error.message}` });
  } finally {
    loading.value = false;
  }
};

const updateJob = async (job, patch) => {
  try {
    const payload = { ...job, ...patch };
    const updated = await apiRequest(`/api/jobs/${job.id}`, "PUT", payload);
    jobs.value = jobs.value.map((j) => (j.id === job.id ? updated : j));
    $q.notify({ type: "positive", message: "Job atualizado" });
  } catch (error) {
    $q.notify({ type: "negative", message: `Erro ao atualizar: ${error.message}` });
    loadJobs();
  }
};

const runNow = async (job) => {
  runningId.value = job.id;
  try {
    const exec = await apiRequest(`/api/jobs/${job.id}/run`, "POST");
    $q.notify({ type: "positive", message: `Execução concluída (${exec.status})` });
    await loadJobs();
    if (historyDialog.value.open) {
      await loadHistory(job);
    }
  } catch (error) {
    $q.notify({ type: "negative", message: `Erro ao executar: ${error.message}` });
  } finally {
    runningId.value = null;
  }
};

const loadHistory = async (job) => {
  try {
    const executions = await apiRequest(`/api/jobs/${job.id}/executions?limit=20`);
    historyDialog.value.executions = executions || [];
  } catch (error) {
    $q.notify({ type: "negative", message: `Erro ao carregar histórico: ${error.message}` });
  }
};

const openHistory = async (job) => {
  historyDialog.value.job = job;
  historyDialog.value.open = true;
  await loadHistory(job);
};

onMounted(loadJobs);
</script>

<style scoped>
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}
</style>
