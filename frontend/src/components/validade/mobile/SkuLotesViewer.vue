<template>
  <q-card flat bordered class="q-mb-md">
    <q-card-section class="row items-center justify-between">
      <div class="text-subtitle2">Lotes do SKU {{ skuId || '-' }}</div>
      <q-btn flat dense icon="refresh" @click="carregar" :loading="loading" />
    </q-card-section>
    <q-card-section>
      <q-list bordered class="rounded-borders">
        <q-item v-for="l in lotes" :key="l.id" dense>
          <q-item-section>
            <div class="text-body2">Lote: {{ l.lote || 'SEM_LOTE' }} | Val: {{ l.dataValidade || 'SEM_VALIDADE' }}</div>
            <div class="text-caption text-grey-7">
              Qtd: {{ getQuantidadeExibida(l) }}
              <q-badge v-if="isPendente(l)" color="orange" class="q-ml-xs">PENDENTE</q-badge>
            </div>
          </q-item-section>
          <q-item-section side v-if="tarefaId">
            <q-btn size="sm" flat color="negative" label="Zerar lote" icon="restart_alt" @click="setZero(l)" :disable="loading" />
          </q-item-section>
        </q-item>
        <q-item v-if="!lotes.length">
          <q-item-section class="text-caption text-grey-6">Nenhum lote encontrado para este SKU.</q-item-section>
        </q-item>
      </q-list>
    </q-card-section>
  </q-card>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useApiRequest } from '@/composables/useApiRequest'
import { useQuasar } from 'quasar'

const props = defineProps({
  skuId: { type: [Number, String], default: null },
  tarefaId: { type: [Number, String], default: null }
})
const emit = defineEmits(['refresh-tarefa'])

const { apiRequest } = useApiRequest()
const $q = useQuasar()

const lotes = ref([])
const pendencias = ref(new Map())
const loading = ref(false)

watch(() => props.skuId, () => carregar())

function isPendente(lote) {
  return pendencias.value.has(lote.id)
}

function getQuantidadeExibida(lote) {
  const pendente = pendencias.value.get(lote.id)
  if (pendente) {
    return pendente.quantidade
  }
  return lote.quantidade
}

async function carregar() {
  lotes.value = []
  if (!props.skuId) return
  loading.value = true
  try {
    const resp = await apiRequest(`/api/estoque/sku/${props.skuId}/lotes`)
    lotes.value = resp || []
  } catch (e) {
    lotes.value = []
  } finally {
    loading.value = false
  }
}

async function setZero(lote) {
  if (!props.tarefaId || !props.skuId) return
  loading.value = true
  try {
    await apiRequest(`/api/validade/tarefas/${props.tarefaId}/itens`, {
      method: 'POST',
      body: [{
        skuId: Number(props.skuId),
        lote: lote.lote || null,
        dataValidade: lote.dataValidade || null,
        quantidade: 0,
        acao: 'SET'
      }]
    })
    pendencias.value.set(lote.id, { quantidade: 0 })
    $q.notify({ type: 'positive', message: 'Lote marcado como zerado (aplica ao finalizar)' })
    await carregar()
    emit('refresh-tarefa')
  } catch (e) {
    $q.notify({ type: 'negative', message: 'Falha ao zerar lote' })
  } finally {
    loading.value = false
  }
}

defineExpose({
  carregar
})
</script>

<style scoped>
.rounded-borders { border-radius: 8px; }
</style>
