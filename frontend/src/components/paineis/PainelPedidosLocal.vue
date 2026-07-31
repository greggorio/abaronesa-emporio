<template>
  <div cols="12" md="4" class="mb-4 flex" style="height: 310px">
    <div v-if="isLoading" class="flex justify-center items-center" style="height: 200px">
      <q-spinner color="primary" size="3em" />
      <span class="q-ml-md text-grey">Carregando dados de pedidos...</span>
    </div>
    <q-card v-else flat class="full-width">
      <q-card-section>
        <PedidosLocalChart :pedidosDiarios="pedidosDiarios" />
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import PedidosLocalChart from "./PedidosLocalChart.vue";

defineProps({
  pedidosDiarios: {
    type: Array,
    default: () => [],
  },
  isLoading: {
    type: Boolean,
    default: true,
  },
});
</script>
