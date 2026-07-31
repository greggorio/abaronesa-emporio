<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="inventory_2" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Controle de Validade</h5>
          </div>
          <div class="row items-center q-gutter-sm">
            <q-btn
              dense
              flat
              icon="open_in_new"
              label="Abrir painel"
              @click="abrirPainel"
            />
            <q-btn
              dense
              flat
              icon="visibility"
              label="Prévia"
              @click="openDialog('TOTAL')"
            />
          </div>
        </div>
      </q-card-section>

      <div v-if="isLoading" class="flex justify-center items-center" style="height: 200px">
        <q-spinner color="primary" size="3em" />
        <span class="q-ml-md text-grey">Carregando dados...</span>
      </div>

      <q-card-section v-else class="metrics-section">
        <div class="row q-col-gutter-sm">
          <div v-for="card in cards" :key="card.status" class="col-4 col-sm-4 col-xs-6">
            <validade-card
              :title="card.title"
              :count="card.count"
              :status="card.status"
              :icon="card.icon"
              :color="card.color"
              @click="openDialog(card.status)"
            />
          </div>
        </div>
      </q-card-section>

      <q-card-section v-if="!isLoading" class="list-section">
        <div v-if="totalLoading" class="flex items-center q-py-sm">
          <q-spinner color="primary" size="2em" />
          <span class="q-ml-sm text-grey">Carregando lista...</span>
        </div>
        <div v-else-if="!totalRows.length" class="text-grey-6 q-py-sm">Sem dados.</div>
        <q-scroll-area
          v-else
          :thumb-style="thumbStyle"
          class="list-scroll"
        >
          <div class="list-row list-row--header">
            <div class="list-cell list-cell--produto">Produto</div>
            <div class="list-cell">SKU</div>
            <div class="list-cell">Validade</div>
            <div class="list-cell list-cell--right">Qtd</div>
            <div class="list-cell list-cell--right">Dias</div>
            <div class="list-cell">Status</div>
          </div>
          <div v-for="row in totalRowsComId" :key="row.rowId" class="list-row">
            <div class="list-cell list-cell--produto" :title="row.produtoNome || '-'">
              {{ row.produtoNome || '-' }}
            </div>
            <div class="list-cell">{{ row.skuCodigo || '-' }}</div>
            <div class="list-cell">
              <span :class="getDiasClass(row.diasParaVencer)">{{ formatarData(row.dataValidade) }}</span>
            </div>
            <div class="list-cell list-cell--right">{{ row.quantidade ?? '-' }}</div>
            <div class="list-cell list-cell--right">
              <span :class="getDiasClass(row.diasParaVencer)">{{ formatarDias(row.diasParaVencer) }}</span>
            </div>
            <div class="list-cell">
              <q-badge :color="getStatusColor(row.status)" class="text-white">
                {{ getStatusLabel(row.status) }}
              </q-badge>
            </div>
          </div>
        </q-scroll-area>
      </q-card-section>

    </q-card>

    <q-dialog v-model="dialogVisivel">
      <q-card class="dialog-card">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">{{ dialogTitle }}</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section class="q-pt-sm">
          <q-input
            v-model="dialogSearch"
            dense
            outlined
            placeholder="Buscar por produto, SKU, lote..."
            class="q-mb-sm"
            clearable
          >
            <template #prepend>
              <q-icon name="search" />
            </template>
          </q-input>
          <q-table
            :rows="dialogRowsFiltrados"
            :columns="columns"
            row-key="rowId"
            :loading="dialogLoading"
            flat
            dense
            :rows-per-page-options="[10, 20, 50]"
            :pagination="{ rowsPerPage: 10 }"
          >
            <template #body-cell-dataValidade="props">
              <q-td :props="props">
                <span :class="getDiasClass(props.row.diasParaVencer)">
                  {{ formatarData(props.row.dataValidade) }}
                </span>
              </q-td>
            </template>

            <template #body-cell-diasParaVencer="props">
              <q-td :props="props">
                <span :class="getDiasClass(props.row.diasParaVencer)">
                  {{ formatarDias(props.row.diasParaVencer) }}
                </span>
              </q-td>
            </template>

            <template #body-cell-status="props">
              <q-td :props="props">
                <q-badge :color="getStatusColor(props.row.status)" class="text-white">
                  {{ getStatusLabel(props.row.status) }}
                </q-badge>
              </q-td>
            </template>

            <template #no-data>
              <div class="full-width text-center q-pa-md text-grey-6">Sem dados.</div>
            </template>
          </q-table>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Fechar" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useApiRequest } from '@/composables/useApiRequest';
import ValidadeCard from '@/components/ValidadeCard.vue';

const router = useRouter();
const { apiRequest } = useApiRequest();
const thumbStyle = {
  right: '4px',
  width: '5px',
  backgroundColor: '#C67C48',
  opacity: 0.75,
  borderRadius: '4px',
};

const isLoading = ref(true);
const dialogVisivel = ref(false);
const dialogLoading = ref(false);
const dialogRows = ref([]);
const dialogSearch = ref('');
const dialogStatus = ref(null);
const totalRows = ref([]);
const totalLoading = ref(false);

const statusLabels = {
  VENCIDO: 'Vencido',
  CRITICO: 'Crítico',
  ATENCAO: 'Atenção',
  SEM_VIDA_UTIL: 'Sem Vida Útil',
  OK: 'OK',
  TOTAL: 'Total',
};

const cards = ref([
  { title: 'Vencido', status: 'VENCIDO', icon: 'report_problem', color: 'red', count: 0 },
  { title: 'Crítico', status: 'CRITICO', icon: 'warning', color: 'red', count: 0 },
  { title: 'Atenção', status: 'ATENCAO', icon: 'priority_high', color: 'orange', count: 0 },
  { title: 'Sem Vida Útil', status: 'SEM_VIDA_UTIL', icon: 'block', color: 'grey', count: 0 },
  { title: 'OK', status: 'OK', icon: 'check_circle', color: 'green', count: 0 },
  { title: 'Total', status: 'TOTAL', icon: 'inventory', color: 'blue', count: 0 },
]);

const columns = [
  { name: 'produtoNome', label: 'Produto', field: 'produtoNome', sortable: true, align: 'left' },
  { name: 'skuCodigo', label: 'SKU', field: 'skuCodigo', sortable: true, align: 'left' },
  { name: 'lote', label: 'Lote', field: 'lote', sortable: true, align: 'left' },
  { name: 'dataValidade', label: 'Validade', field: 'dataValidade', sortable: true, align: 'left' },
  { name: 'quantidade', label: 'Quantidade', field: 'quantidade', sortable: true, align: 'right' },
  { name: 'diasParaVencer', label: 'Dias p/ vencer', field: 'diasParaVencer', sortable: true, align: 'right' },
  { name: 'status', label: 'Status', field: 'status', sortable: true, align: 'left' },
];

const dialogRowsComId = computed(() =>
  dialogRows.value.map((row, index) => ({
    ...row,
    rowId: row.estoqueLoteId ?? row.id ?? `${row.lote ?? 'lote'}-${index}`,
  }))
);

const dialogRowsFiltrados = computed(() => {
  const termo = (dialogSearch.value || '').trim().toLowerCase();
  if (!termo) return dialogRowsComId.value;
  return dialogRowsComId.value.filter(row =>
    (row.produtoNome || '').toLowerCase().includes(termo) ||
    (row.skuCodigo || '').toLowerCase().includes(termo) ||
    (row.lote || '').toLowerCase().includes(termo)
  );
});

const totalRowsComId = computed(() =>
  totalRows.value.map((row, index) => ({
    ...row,
    rowId: row.estoqueLoteId ?? row.id ?? `${row.skuCodigo ?? 'sku'}-${index}`,
  }))
);


const dialogTitle = computed(() => {
  if (!dialogStatus.value || dialogStatus.value === 'TOTAL') {
    return 'Lotes - Todos os status';
  }
  return `Lotes - ${getStatusLabel(dialogStatus.value)}`;
});

const loadDashboard = async () => {
  isLoading.value = true;
  try {
    const data = await apiRequest('/api/validade/dashboard?somenteComSaldo=true');

    cards.value[0].count = data.vencido || 0;
    cards.value[1].count = data.critico || 0;
    cards.value[2].count = data.atencao || 0;
    cards.value[3].count = data.semVidaUtil || 0;
    cards.value[4].count = data.ok || 0;
    cards.value[5].count = data.total || 0;
  } catch (error) {
    console.error('Erro ao carregar dashboard de validade:', error);
  } finally {
    isLoading.value = false;
  }
};

const loadAlertas = async (status) => {
  dialogLoading.value = true;
  dialogRows.value = [];
  try {
    const params = new URLSearchParams({
      somenteComSaldo: 'true',
      limit: '200',
      offset: '0',
    });

    if (status && status !== 'TOTAL') {
      params.append('status', status);
    }

    const data = await apiRequest(`/api/validade/alertas?${params.toString()}`);
    dialogRows.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('Erro ao carregar alertas de validade:', error);
    dialogRows.value = [];
  } finally {
    dialogLoading.value = false;
  }
};

const loadTotalList = async () => {
  totalLoading.value = true;
  totalRows.value = [];
  try {
    const params = new URLSearchParams({
      somenteComSaldo: 'true',
      limit: '200',
      offset: '0',
    });
    const data = await apiRequest(`/api/validade/alertas?${params.toString()}`);
    totalRows.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('Erro ao carregar lista total de validade:', error);
    totalRows.value = [];
  } finally {
    totalLoading.value = false;
  }
};


const openDialog = async (status) => {
  dialogSearch.value = '';
  dialogStatus.value = status;
  dialogVisivel.value = true;
  await loadAlertas(status);
};

const abrirPainel = () => {
  router.push('/validade/painel');
};

const getStatusLabel = (status) => statusLabels[status] || status || '-';

const getStatusColor = (status) => {
  switch (status) {
    case 'VENCIDO':
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

const formatarData = (value) => {
  if (!value) return '-';
  try {
    if (Array.isArray(value)) {
      const [year, month, day] = value;
      const dd = String(day).padStart(2, '0');
      const mm = String(month).padStart(2, '0');
      const yy = String(year).slice(-2);
      return `${dd}/${mm}/${yy}`;
    }
    const date = new Date(value);
    if (isNaN(date.getTime())) return '-';
    const dd = String(date.getDate()).padStart(2, '0');
    const mm = String(date.getMonth() + 1).padStart(2, '0');
    const yy = String(date.getFullYear()).slice(-2);
    return `${dd}/${mm}/${yy}`;
  } catch (e) {
    return '-';
  }
};

const formatarDias = (dias) => {
  if (dias === null || dias === undefined || Number.isNaN(Number(dias))) return '-';
  const numero = Number(dias);
  if (numero < 0) return `${numero}`;
  return `${numero}`;
};

const getDiasClass = (dias) => {
  if (dias === null || dias === undefined || Number.isNaN(Number(dias))) return 'text-grey';
  const numero = Number(dias);
  if (numero < 0) return 'text-negative';
  if (numero <= 7) return 'text-warning';
  return 'text-positive';
};

onMounted(() => {
  loadDashboard();
  loadTotalList();
});
</script>

<style scoped>
.dashboard-card {
  background-color: #FBF6F2;
}

.header-section {
  background: linear-gradient(to right, #FBF6F2, #ffffff);
  border-bottom: 1px solid #D7B899;
  padding: 12px 16px 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-wrapper {
  display: flex;
  align-items: center;
}

.panel-title {
  color: #2A1F1B;
}

.metrics-section {
  padding: 6px 12px 2px;
}

.panel-helper {
  padding: 0 16px 8px;
}

.list-section {
  padding: 0 12px 8px;
}

.list-section .list-scroll {
  margin-top: 10px;
}

.list-scroll {
  height: 160px;
}

.list-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 0.6fr 0.6fr 0.9fr;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid rgba(215, 184, 153, 0.35);
  font-size: 0.72rem;
  color: #2A1F1B;
}

.list-row--header {
  font-weight: 600;
  font-size: 0.65rem;
  text-transform: uppercase;
  color: #6B3E26;
  border-bottom: 1px solid rgba(215, 184, 153, 0.6);
  padding-top: 0;
}

.list-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.list-cell--produto {
  max-width: 100%;
}

.list-cell--right {
  text-align: right;
}

.dialog-card {
  min-width: 960px;
  max-width: 95vw;
}

@media (max-width: 1024px) {
  .dialog-card {
    min-width: 90vw;
  }
}
</style>
