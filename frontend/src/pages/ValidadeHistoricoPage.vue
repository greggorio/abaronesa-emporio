<template>
  <q-page class="q-pa-md bg-grey-2">
    <div class="text-h6 q-mb-xs">Histórico de Validade</div>
    <div class="text-subtitle2 text-grey-7 q-mb-md">Consulte tarefas concluídas e canceladas fora da rotina operacional.</div>

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-6 col-sm-3">
        <q-card flat bordered class="summary-card">
          <q-card-section>
            <div class="text-caption text-grey-7">Total</div>
            <div class="text-h6">{{ tarefasHistorico.length }}</div>
          </q-card-section>
        </q-card>
      </div>
      <div class="col-6 col-sm-3">
        <q-card flat bordered class="summary-card">
          <q-card-section>
            <div class="text-caption text-grey-7">Finalizadas</div>
            <div class="text-h6 text-positive">{{ totalFinalizadas }}</div>
          </q-card-section>
        </q-card>
      </div>
      <div class="col-6 col-sm-3">
        <q-card flat bordered class="summary-card">
          <q-card-section>
            <div class="text-caption text-grey-7">Canceladas</div>
            <div class="text-h6 text-negative">{{ totalCanceladas }}</div>
          </q-card-section>
        </q-card>
      </div>
      <div class="col-6 col-sm-3">
        <q-card flat bordered class="summary-card">
          <q-card-section>
            <div class="text-caption text-grey-7">Com itens</div>
            <div class="text-h6">{{ totalComItens }}</div>
          </q-card-section>
        </q-card>
      </div>
    </div>

    <q-card flat bordered class="q-mb-md">
      <q-card-section class="row q-col-gutter-md items-end">
        <div class="col-12 col-md-4">
          <q-input
            v-model="filtros.busca"
            dense
            outlined
            clearable
            label="Buscar por ID ou observação"
          >
            <template #prepend>
              <q-icon name="search" />
            </template>
          </q-input>
        </div>
        <div class="col-12 col-md-3">
          <q-select
            v-model="filtros.status"
            dense
            outlined
            clearable
            emit-value
            map-options
            label="Status"
            :options="statusOptions"
          />
        </div>
        <div class="col-6 col-md-2">
          <q-input v-model="filtros.dataInicial" dense outlined type="date" label="De" />
        </div>
        <div class="col-6 col-md-2">
          <q-input v-model="filtros.dataFinal" dense outlined type="date" label="Até" />
        </div>
        <div class="col-12 col-md-1 row justify-end">
          <q-btn flat dense icon="refresh" :loading="loading" @click="carregarTarefas" />
        </div>
      </q-card-section>
    </q-card>

    <q-card flat bordered>
      <q-card-section class="row items-center justify-between">
        <div>
          <div class="text-subtitle1 text-weight-medium">Tarefas encerradas</div>
          <div class="text-caption text-grey-7">{{ tarefasFiltradas.length }} registro(s) encontrados</div>
        </div>
        <q-btn flat color="primary" icon="inventory_2" label="Ir para operação" @click="abrirOperacao" />
      </q-card-section>
      <q-separator />

      <q-card-section v-if="loading" class="flex flex-center q-py-xl">
        <q-spinner color="primary" size="40px" />
      </q-card-section>

      <q-card-section v-else-if="!tarefasFiltradas.length" class="text-grey-7">
        Nenhuma tarefa encontrada com os filtros atuais.
      </q-card-section>

      <q-list v-else separator>
        <q-item
          v-for="tarefa in tarefasFiltradas"
          :key="tarefa.id"
          clickable
          class="history-item"
          @click="abrirTarefa(tarefa)"
        >
          <q-item-section>
            <div class="row items-center q-col-gutter-sm">
              <div class="col">
                <div class="text-body1 text-weight-medium">Tarefa #{{ tarefa.id }}</div>
                <div class="text-caption text-grey-7">Criada em {{ formatDate(tarefa.criadoEm) }}</div>
                <div class="text-caption text-grey-7">{{ statusDateLabel(tarefa) }} {{ formatDate(statusDate(tarefa)) }}</div>
                <div class="text-caption text-grey-7">{{ itemCount(tarefa) }} item(ns) lançados</div>
                <div v-if="tarefa.observacao" class="history-note q-mt-xs">{{ tarefa.observacao }}</div>
              </div>
              <div class="col-auto">
                <q-chip :color="statusColor(tarefa.status)" text-color="white" size="sm">
                  {{ statusLabel(tarefa.status) }}
                </q-chip>
              </div>
            </div>
          </q-item-section>
        </q-item>
      </q-list>
    </q-card>
  </q-page>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useQuasar } from 'quasar'
import { useApiRequest } from '@/composables/useApiRequest'

const router = useRouter()
const $q = useQuasar()
const { apiRequest } = useApiRequest()

const tarefas = ref([])
const loading = ref(false)
const filtros = reactive({
  busca: '',
  status: null,
  dataInicial: '',
  dataFinal: '',
})

const statusOptions = [
  { label: 'Finalizada', value: 'FINALIZADA' },
  { label: 'Cancelada', value: 'CANCELADA' },
]

const tarefasHistorico = computed(() => {
  return [...tarefas.value]
    .filter((tarefa) => ['FINALIZADA', 'CANCELADA'].includes(tarefa?.status))
    .sort((a, b) => {
      const dateA = new Date(statusDate(a) || a?.criadoEm || 0).getTime()
      const dateB = new Date(statusDate(b) || b?.criadoEm || 0).getTime()
      return dateB - dateA
    })
})

const tarefasFiltradas = computed(() => {
  return tarefasHistorico.value.filter((tarefa) => {
    const busca = filtros.busca.trim().toLowerCase()
    const alvoBusca = [String(tarefa?.id || ''), tarefa?.observacao || '']
      .join(' ')
      .toLowerCase()

    if (busca && !alvoBusca.includes(busca)) return false
    if (filtros.status && tarefa?.status !== filtros.status) return false

    const tarefaDate = statusDate(tarefa)
    const dateValue = tarefaDate ? new Date(tarefaDate).toISOString().slice(0, 10) : ''

    if (filtros.dataInicial && (!dateValue || dateValue < filtros.dataInicial)) return false
    if (filtros.dataFinal && (!dateValue || dateValue > filtros.dataFinal)) return false

    return true
  })
})

const totalFinalizadas = computed(() => tarefasHistorico.value.filter((tarefa) => tarefa.status === 'FINALIZADA').length)
const totalCanceladas = computed(() => tarefasHistorico.value.filter((tarefa) => tarefa.status === 'CANCELADA').length)
const totalComItens = computed(() => tarefasHistorico.value.filter((tarefa) => itemCount(tarefa) > 0).length)

function notifyError(message) {
  $q.notify({ type: 'negative', message: message || 'Erro ao carregar histórico' })
}

function statusDate(tarefa) {
  return tarefa?.finalizadoEm || tarefa?.updatedAt || tarefa?.criadoEm || null
}

function statusDateLabel(tarefa) {
  return tarefa?.status === 'CANCELADA' ? 'Encerrada em' : 'Finalizada em'
}

function statusLabel(status) {
  return status === 'CANCELADA' ? 'Cancelada' : 'Finalizada'
}

function statusColor(status) {
  return status === 'CANCELADA' ? 'negative' : 'positive'
}

function itemCount(tarefa) {
  return Array.isArray(tarefa?.itens) ? tarefa.itens.length : 0
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function carregarTarefas() {
  loading.value = true
  try {
    const response = await apiRequest('/api/validade/tarefas')
    tarefas.value = Array.isArray(response) ? response : []
  } catch (error) {
    notifyError('Falha ao carregar histórico de tarefas')
  } finally {
    loading.value = false
  }
}

function abrirTarefa(tarefa) {
  if (!tarefa?.id) return
  router.push(`/mobile/validade/tarefa/${tarefa.id}`)
}

function abrirOperacao() {
  router.push('/validade/operacao')
}

onMounted(() => {
  carregarTarefas()
})
</script>

<style scoped>
.bg-grey-2 { background: #f7f7f9; }
.summary-card {
  border-radius: 12px;
}
.history-item {
  align-items: flex-start;
}
.history-note {
  padding: 8px 10px;
  border-radius: 8px;
  background: #f6f3ef;
  color: #6b4e3d;
  font-size: 0.875rem;
}
</style>
