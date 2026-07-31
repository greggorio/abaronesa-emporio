<template>
  <q-card flat bordered>
    <q-card-section class="row items-center justify-between">
      <div class="text-subtitle2">Divergências</div>
      <q-btn flat dense icon="refresh" @click="$emit('refresh')" :loading="loading" />
    </q-card-section>
    <q-card-section>
      <q-list bordered class="rounded-borders">
        <q-item v-for="d in divergencias" :key="d.id" dense>
          <q-item-section>
            <div class="text-body2 text-weight-medium">{{ produtoLabel(d) }}</div>
            <div class="text-caption text-grey-7">Estoque agregado: {{ d.estoqueAgregado }} | Soma dos lotes: {{ d.somaLotes }}</div>
            <div class="text-caption text-grey-7">Diferença encontrada: {{ d.diferenca }}</div>
            <div class="text-caption">
              Situação:
              <q-badge :color="statusColor(d.acaoTomada)" class="q-ml-xs">{{ statusLabel(d.acaoTomada) }}</q-badge>
            </div>
          </q-item-section>
          <q-item-section side>
            <div class="column q-gutter-xs">
              <q-btn size="sm" flat color="grey-8" icon="do_not_disturb_on" label="Marcar como revisada" @click="tratar(d.id, 'IGNORAR')" :disable="d.acaoTomada !== 'PENDENTE'" />
              <q-btn size="sm" flat color="primary" icon="build" label="Gerar ajuste" @click="tratar(d.id, 'CRIAR_AJUSTE')" :disable="d.acaoTomada !== 'PENDENTE'" />
            </div>
          </q-item-section>
        </q-item>
        <q-item v-if="!divergencias.length">
          <q-item-section class="text-caption text-grey-6">Nenhuma divergência pendente para esta tarefa.</q-item-section>
        </q-item>
      </q-list>
    </q-card-section>
  </q-card>
</template>

<script setup>
import { useApiRequest } from '@/composables/useApiRequest'
import { useQuasar } from 'quasar'

defineProps({
  divergencias: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['treated', 'refresh'])
const { apiRequest } = useApiRequest()
const $q = useQuasar()

function produtoLabel(divergencia) {
  return divergencia?.produtoNome || divergencia?.skuCodigo || `SKU ${divergencia?.skuId ?? '-'}`
}

function statusLabel(status) {
  switch (status) {
    case 'IGNORAR': return 'Revisada'
    case 'CRIAR_AJUSTE': return 'Ajuste gerado'
    default: return 'Pendente'
  }
}

function statusColor(status) {
  switch (status) {
    case 'IGNORAR': return 'grey-7'
    case 'CRIAR_AJUSTE': return 'positive'
    default: return 'orange'
  }
}

async function tratar(id, acao) {
  try {
    await apiRequest(`/api/validade/divergencias/${id}/tratar`, {
      method: 'POST',
      body: { acao }
    })
    $q.notify({ type: 'positive', message: acao === 'IGNORAR' ? 'Divergência marcada como revisada' : 'Ajuste gerado para a divergência' })
    emit('treated')
  } catch (e) {
    $q.notify({ type: 'negative', message: 'Falha ao tratar divergência' })
  }
}
</script>

<style scoped>
.rounded-borders { border-radius: 8px; }
</style>
