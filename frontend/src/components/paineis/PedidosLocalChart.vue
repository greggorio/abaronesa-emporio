<template>
  <div id="chart">
    <span class="panel-title">Pedidos por Local de Preparação</span>
    <apexchart type="area" height="250" :options="chartOptions" :series="series"></apexchart>
  </div>
</template>

<script setup>
import { computed } from "vue";
import apexchart from "vue3-apexcharts";

const props = defineProps({
  pedidosDiarios: {
    type: Array,
    required: true,
  },
});

// Função para converter data YYYY-MM-DD para timestamp
const parseDate = (dateStr) => {
  if (!dateStr) return null;

  // Verifica se é string ou Date
  if (typeof dateStr === 'string') {
    // Se for YYYY-MM-DD
    if (dateStr.includes("-") && dateStr.length === 10) {
      return new Date(dateStr + "T00:00:00").getTime();
    }
  } else if (dateStr instanceof Date) {
    return dateStr.getTime();
  }

  return null;
};

const series = computed(() => {
  // Filtra dados válidos
  const validData = props.pedidosDiarios.filter((item) => item && item.data);

  return [
    {
      name: "Bar",
      data: validData
        .map((item) => ({
          x: parseDate(item.data),
          y: item.pedidosBar || 0,
        }))
        .filter((item) => item.x !== null),
    },
    {
      name: "Cozinha",
      data: validData
        .map((item) => ({
          x: parseDate(item.data),
          y: item.pedidosCozinha || 0,
        }))
        .filter((item) => item.x !== null),
    },
  ];
});

const chartOptions = computed(() => ({
  chart: {
    height: 250,
    type: "area",
    toolbar: {
      show: true,
    },
  },
  dataLabels: {
    enabled: false,
  },
  stroke: {
    curve: "smooth",
    width: 2,
  },
  colors: ["#6B3E26", "#C67C48"], // primary e secondary do tema café
  fill: {
    type: "gradient",
    gradient: {
      shadeIntensity: 1,
      opacityFrom: 0.7,
      opacityTo: 0.9,
      stops: [0, 90, 100],
    },
  },
  xaxis: {
    type: "datetime",
    labels: {
      format: "dd/MM",
      datetimeFormatter: {
        year: "yyyy",
        month: "MMM",
        day: "dd/MM",
        hour: "HH:mm",
      },
    },
  },
  yaxis: {
    labels: {
      formatter: function (val) {
        return Math.round(val);
      },
    },
  },
  tooltip: {
    x: {
      format: "dd/MM/yyyy",
    },
    y: {
      formatter: function (value) {
        return `${Math.round(value)} pedidos`;
      },
    },
  },
  legend: {
    position: "top",
    horizontalAlign: "right",
  },
  title: {
    text: "",
    align: "left",
    style: {
      fontSize: "16px",
      fontWeight: "bold",
    },
  },
  subtitle: {
    text: "Quantidade de pedidos",
    align: "left",
  },
}));
</script>

<style scoped>
.panel-title {
  font-size: 18px;
  font-weight: bold;
  font-family: Arial, sans-serif;
  color: #6B3E26;
}
</style>
