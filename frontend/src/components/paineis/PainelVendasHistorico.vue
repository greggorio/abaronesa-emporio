<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="stacked_bar_chart" color="primary" size="sm" />
            <div class="title-text">
              <h5 class="panel-title q-my-none">Vendas por data</h5>
              <span class="subtitle">Últimos 30 dias</span>
            </div>
          </div>
          <div class="actions">
            <q-btn-toggle
              v-model="modo"
              dense
              size="sm"
              no-caps
              toggle-color="primary"
              :options="[
                { label: 'Valor', value: 'valor' },
                { label: 'Quantidade', value: 'quantidade' }
              ]"
            />
          </div>
        </div>
      </q-card-section>

      <div v-if="isLoading" class="flex column justify-center items-center chart-placeholder">
        <q-spinner color="primary" size="32px" />
        <div class="q-mt-sm text-grey">Carregando série...</div>
      </div>

      <q-card-section v-else class="chart-section">
        <apexchart
          :type="chartType"
          height="260"
          :options="chartOptions"
          :series="series"
        />
        <div v-if="!hasData" class="empty-state">
          <q-icon name="insights" size="md" color="grey-5" class="q-mb-xs" />
          <div class="text-grey-7">Sem dados suficientes para exibir o gráfico.</div>
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import apexchart from 'vue3-apexcharts';
import { useFormatarMoeda } from '@/composables/formatarMoeda';

const props = defineProps({
  dados: {
    type: Array,
    default: () => [],
  },
  isLoading: {
    type: Boolean,
    default: false,
  },
});

const modo = ref('valor'); // valor | quantidade
const { formatarMoeda } = useFormatarMoeda();

const chartType = computed(() => (modo.value === 'valor' ? 'area' : 'bar'));

const parsedData = computed(() =>
  (props.dados || [])
    .map((item) => {
      const x = toDate(item?.data);
      const valor = Number(item?.valor || 0);
      const quantidade = Number(item?.quantidade || 0);
      return x ? { x, valor, quantidade } : null;
    })
    .filter(Boolean)
);

const series = computed(() => {
  const data = parsedData.value.map((item) => ({
    x: item.x,
    y: modo.value === 'valor' ? item.valor : item.quantidade,
  }));

  return [
    {
      name: modo.value === 'valor' ? 'Faturamento' : 'Quantidade',
      data,
    },
  ];
});

const hasData = computed(() => (series.value?.[0]?.data || []).length > 0);

const chartOptions = computed(() => {
  const isValor = modo.value === 'valor';
  const palette = {
    primary: '#6B3E26', // marrom café (usado em PedidosLocalChart)
    secondary: '#C67C48', // caramelo
  };
  const color = isValor ? palette.primary : palette.secondary;

  return {
    chart: {
      id: 'vendas-historico',
      toolbar: { show: true },
      animations: { enabled: true, easing: 'easeinout', speed: 400 },
      foreColor: '#3d3b37',
    },
    colors: [color],
    dataLabels: { enabled: false },
    stroke: {
      curve: isValor ? 'smooth' : 'straight',
      width: isValor ? 3 : 0,
    },
    fill: isValor
      ? {
          type: 'gradient',
          gradient: {
            shadeIntensity: 0.9,
            opacityFrom: 0.8,
            opacityTo: 0.2,
            stops: [0, 70, 100],
          },
        }
      : { opacity: 0.85 },
    plotOptions: {
      bar: {
        columnWidth: '55%',
        borderRadius: 6,
      },
    },
    grid: {
      borderColor: '#eee7df',
      strokeDashArray: 4,
    },
    xaxis: {
      type: 'datetime',
      labels: {
        format: 'dd/MM',
        style: { colors: '#7b7166' },
      },
      axisBorder: { color: '#e0d6cb' },
      axisTicks: { color: '#e0d6cb' },
    },
    yaxis: {
      labels: {
        formatter: (val) =>
          isValor ? formatarMoeda(val || 0) : Math.round(val || 0),
        style: { colors: '#7b7166' },
      },
      decimalsInFloat: 0,
    },
    tooltip: {
      shared: false,
      x: { format: 'dd/MM' },
      y: {
        formatter: (val) => (isValor ? formatarMoeda(val || 0) : `${val} vendas`),
      },
    },
    legend: {
      show: false,
    },
  };
});

function toDate(value) {
  if (!value) return null;
  if (value instanceof Date) return value.getTime();
  if (typeof value === 'string') {
    const parsed = new Date(`${value}T00:00:00`);
    if (!isNaN(parsed.getTime())) return parsed.getTime();
  }
  return null;
}
</script>

<style scoped>
.dashboard-card {
  height: 100%;
  border-radius: 10px;
  background: linear-gradient(145deg, #ffffff, #f7f0e7);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08);
}

.header-section {
  padding-bottom: 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.panel-title {
  font-size: 18px;
  font-weight: 700;
  font-family: Arial, sans-serif;
  color: #553322;
}

.subtitle {
  color: #8c7d6f;
  font-size: 12px;
}

.chart-section {
  position: relative;
  min-height: 230px;
}

.chart-placeholder {
  min-height: 230px;
}

.empty-state {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 10px;
}
</style>
