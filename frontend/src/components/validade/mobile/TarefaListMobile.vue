<template>
  <q-card flat bordered class="q-mb-md">
    <q-card-section class="column q-gutter-sm">
      <div class="row items-center justify-between">
        <div class="text-subtitle1">Tarefas</div>
        <q-btn flat dense icon="refresh" @click="$emit('refresh')" :loading="loading" />
      </div>
      <div v-if="showCreate" class="row q-col-gutter-sm">
        <div class="col-12">
          <q-input v-model="novaObservacao" label="Observação (opcional)" dense outlined />
        </div>
        <div class="col-12">
          <q-btn color="primary" icon="add_circle" label="Nova tarefa" class="full-width" @click="criar" :loading="loadingCriar" />
        </div>
      </div>
    </q-card-section>
    <q-separator />
    <q-card-section>
      <q-list bordered class="rounded-borders">
        <q-item v-for="t in tarefas" :key="t.id" clickable @click="$emit('select', t)" :active="tarefaSelecionada && tarefaSelecionada.id === t.id" active-class="bg-grey-3">
          <q-item-section>
            <div class="text-body2">Tarefa #{{ t.id }}</div>
            <div class="text-caption text-grey-7">Criada: {{ formatDate(t.criadoEm) }}</div>
            <div v-if="t.finalizadoEm" class="text-caption text-grey-7">Finalizada: {{ formatDate(t.finalizadoEm) }}</div>
          </q-item-section>
          <q-item-section side top>
            <q-chip :color="statusColor(t.status)" text-color="white" size="sm">{{ t.status }}</q-chip>
          </q-item-section>
        </q-item>
        <q-item v-if="!tarefas.length">
          <q-item-section class="text-caption text-grey-6">Nenhuma tarefa encontrada.</q-item-section>
        </q-item>
      </q-list>
    </q-card-section>
  </q-card>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  tarefas: { type: Array, default: () => [] },
  tarefaSelecionada: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  loadingCriar: { type: Boolean, default: false },
  showCreate: { type: Boolean, default: true }
})

const emit = defineEmits(['create', 'select', 'refresh'])

const novaObservacao = ref('')

const statusColor = (status) => {
  switch (status) {
    case 'FINALIZADA': return 'positive'
    case 'CANCELADA': return 'negative'
    default: return 'info'
  }
}

const formatDate = (dt) => dt ? new Date(dt).toLocaleString() : '-'

function criar() {
  emit('create', novaObservacao.value || null)
}
</script>

<style scoped>
.rounded-borders { border-radius: 8px; }
</style>
