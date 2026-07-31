<template>
  <q-card class="full-width">
    <q-card-section>
      <div class="row items-center q-mb-md">
        <div class="text-h6">Lotes - {{ statusTitulo }}</div>
        <q-space />
        <q-btn 
          v-if="hasMore" 
          @click="$emit('load-more')" 
          label="Carregar mais" 
          color="primary" 
          size="sm" 
          :loading="loadingMore"
        />
      </div>
      
      <q-table
        :rows="rows"
        :columns="columns"
        row-key="estoqueLoteId"
        :pagination="pagination"
        :loading="loading"
        :rows-per-page-options="[10, 20, 50]"
        @request="onRequest"
      >
        <template v-slot:body-cell-dataValidade="props">
          <q-td :props="props">
            <span :class="{
              'text-red': props.row.diasParaVencer < 0,
              'text-orange': props.row.diasParaVencer >= 0 && props.row.diasParaVencer <= 7,
              'text-green': props.row.diasParaVencer > 7
            }">
              {{ props.row.dataValidade ? formatDate(props.row.dataValidade) : 'N/A' }}
            </span>
          </q-td>
        </template>
        
        <template v-slot:body-cell-diasParaVencer="props">
          <q-td :props="props">
            <span :class="{
              'text-red': props.row.diasParaVencer < 0,
              'text-orange': props.row.diasParaVencer >= 0 && props.row.diasParaVencer <= 7,
              'text-green': props.row.diasParaVencer > 7
            }">
              {{ props.row.diasParaVencer < 0 ? `${Math.abs(props.row.diasParaVencer)} dias atrás` : `${props.row.diasParaVencer} dias` }}
            </span>
          </q-td>
        </template>
        
        <template v-slot:body-cell-percentualVidaRestante="props">
          <q-td :props="props">
            <span v-if="props.row.percentualVidaRestante !== null">
              {{ props.row.percentualVidaRestante.toFixed(2) }}%
            </span>
            <span v-else>N/A</span>
          </q-td>
        </template>
        
        <template v-slot:body-cell-status="props">
          <q-td :props="props">
            <q-chip 
              :color="getStatusColor(props.row.status)" 
              text-color="white" 
              size="sm"
            >
              {{ props.row.status }}
            </q-chip>
          </q-td>
        </template>
      </q-table>
    </q-card-section>
  </q-card>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  status: {
    type: String,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  },
  loadingMore: {
    type: Boolean,
    default: false
  },
  rows: {
    type: Array,
    default: () => []
  },
  hasMore: {
    type: Boolean,
    default: false
  }
});

defineEmits(['load-more']);

const pagination = ref({
  page: 1,
  rowsPerPage: 10,
  sortBy: 'dataValidade',
  descending: false
});

const statusTitulos = {
  'VENCIDO': 'Lotes Vencidos',
  'CRITICO': 'Lotes Críticos',
  'ATENCAO': 'Lotes em Atenção',
  'SEM_VIDA_UTIL': 'Lotes sem Vida Útil',
  'OK': 'Lotes OK'
};

const statusTitulo = computed(() => statusTitulos[props.status] || props.status);

const columns = [
  { name: 'produtoNome', label: 'Produto', field: 'produtoNome', sortable: true },
  { name: 'skuCodigo', label: 'SKU', field: 'skuCodigo', sortable: true },
  { name: 'lote', label: 'Lote', field: 'lote', sortable: true },
  { name: 'dataValidade', label: 'Validade', field: 'dataValidade', sortable: true },
  { name: 'quantidade', label: 'Quantidade', field: 'quantidade', sortable: true },
  { name: 'diasParaVencer', label: 'Dias para Vencer', field: 'diasParaVencer', sortable: true },
  { name: 'vidaUtilDias', label: 'Vida Útil (dias)', field: 'vidaUtilDias', sortable: true },
  { name: 'percentualVidaRestante', label: '% Vida Restante', field: 'percentualVidaRestante', sortable: true },
  { name: 'status', label: 'Status', field: 'status', sortable: true }
];

const formatDate = (dateString) => {
  if (!dateString) return 'N/A';
  const date = new Date(dateString);
  return date.toLocaleDateString('pt-BR');
};

const getStatusColor = (status) => {
  switch (status) {
    case 'VENCIDO':
      return 'red';
    case 'CRITICO':
      return 'red';
    case 'ATENCAO':
      return 'orange';
    case 'SEM_VIDA_UTIL':
      return 'grey';
    case 'OK':
      return 'green';
    default:
      return 'blue';
  }
};

const onRequest = (details) => {
  pagination.value.page = details.pagination.page;
  pagination.value.rowsPerPage = details.pagination.rowsPerPage;
  pagination.value.sortBy = details.pagination.sortBy;
  pagination.value.descending = details.pagination.descending;
};
</script>