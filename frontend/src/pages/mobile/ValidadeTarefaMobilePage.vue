<template>
  <q-page class="q-pa-md bg-grey-2">
    <div class="text-h6 q-mb-xs">Operação de Validade</div>
    <div class="text-subtitle2 text-grey-7 q-mb-md">Retome a tarefa em aberto ou inicie uma nova contagem.</div>

    <q-banner v-if="temMultiplosRascunhos" dense rounded class="bg-orange-1 text-orange-10 q-mb-md">
      <template #avatar>
        <q-icon name="warning" color="orange-8" />
      </template>
      Foram encontrados {{ rascunhos.length }} rascunhos. A tela prioriza o mais recente até a regra ser endurecida no backend.
    </q-banner>

    <q-card v-if="rascunhoAtual" flat bordered class="q-mb-md task-card task-card--draft">
      <q-card-section class="column q-gutter-sm">
        <div class="row items-start justify-between q-col-gutter-md">
          <div class="col">
            <div class="text-subtitle1 text-weight-medium">Tarefa em aberto</div>
            <div class="text-body1 q-mt-xs">Tarefa #{{ rascunhoAtual.id }}</div>
            <div class="text-caption text-grey-7">Criada em {{ formatDate(rascunhoAtual.criadoEm) }}</div>
            <div v-if="itemCount(rascunhoAtual)" class="text-caption text-grey-7">{{ itemCount(rascunhoAtual) }} item(ns) lançados</div>
          </div>
          <q-chip color="orange-2" text-color="orange-10" icon="edit_note" size="sm">Em aberto</q-chip>
        </div>

        <div v-if="rascunhoAtual.observacao" class="draft-note">
          {{ rascunhoAtual.observacao }}
        </div>

        <q-btn
          color="primary"
          icon="play_arrow"
          label="Continuar tarefa em aberto"
          class="full-width"
          :loading="loadingTarefas"
          @click="abrirTarefa(rascunhoAtual)"
        />
      </q-card-section>
    </q-card>

    <q-card v-else flat bordered class="q-mb-md task-card">
      <q-card-section class="column q-gutter-sm">
        <div class="text-subtitle1 text-weight-medium">Nenhuma tarefa em aberto</div>
        <div class="text-body2 text-grey-7">Crie uma nova contagem para iniciar a conferência de validade.</div>
        <q-btn
          color="primary"
          icon="add_circle"
          label="Iniciar nova contagem"
          class="full-width"
          :loading="loadingCriar"
          @click="criarENavegar"
        />
      </q-card-section>
    </q-card>

  </q-page>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useQuasar } from 'quasar'
import { useRouter } from 'vue-router'
import { useApiRequest } from '@/composables/useApiRequest'

const $q = useQuasar()
const router = useRouter()
const { apiRequest } = useApiRequest()

const tarefas = ref([])
const loadingTarefas = ref(false)
const loadingCriar = ref(false)

const rascunhos = computed(() => {
  return [...tarefas.value]
    .filter(tarefa => tarefa?.status === 'RASCUNHO')
    .sort((a, b) => new Date(b?.criadoEm || 0).getTime() - new Date(a?.criadoEm || 0).getTime())
})

const rascunhoAtual = computed(() => rascunhos.value[0] || null)
const temMultiplosRascunhos = computed(() => rascunhos.value.length > 1)

const notifyError = (msg) => $q.notify({ type: 'negative', message: msg || 'Erro' })

async function carregarTarefas() {
  loadingTarefas.value = true
  try {
    const resp = await apiRequest('/api/validade/tarefas')
    tarefas.value = resp || []
  } catch (e) {
    notifyError('Falha ao carregar tarefas')
  } finally {
    loadingTarefas.value = false
  }
}

async function criarENavegar() {
  if (rascunhoAtual.value) {
    abrirTarefa(rascunhoAtual.value)
    return
  }
  if (loadingCriar.value) return
  loadingCriar.value = true
  try {
    const resp = await apiRequest('/api/validade/tarefas', { method: 'POST', body: { observacao: null } })
    router.push(`/mobile/validade/tarefa/${resp.id}`)
  } catch (e) {
    notifyError('Falha ao criar tarefa')
  } finally {
    loadingCriar.value = false
  }
}

function abrirTarefa(tarefa) {
  if (!tarefa?.id) return
  router.push(`/mobile/validade/tarefa/${tarefa.id}`)
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

function itemCount(tarefa) {
  return Array.isArray(tarefa?.itens) ? tarefa.itens.length : 0
}

onMounted(() => {
  carregarTarefas()
})
</script>

<style scoped>
.bg-grey-2 { background: #f7f7f9; }
.task-card {
  border-radius: 12px;
}
.task-card--draft {
  border-color: rgba(198, 124, 72, 0.4);
  box-shadow: 0 8px 24px rgba(198, 124, 72, 0.08);
}
.draft-note {
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff8f2;
  color: #6b4e3d;
  font-size: 0.875rem;
}
</style>
