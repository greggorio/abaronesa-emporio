<template>
  <q-card class="bg-white" style="border-radius: 12px">
    <q-tabs
      v-model="activeTab"
      style="background-color: transparent"
      class="text-secondary"
      narrow-indicator
      dense
      align="left"
      active-color="secondary"
      indicator-color="secondary"
    >
      <q-tab name="divergencias" label="Divergências" icon="warning" />
      <q-tab name="contagens" label="Contagens" icon="check_box" />
      <q-tab name="leituras" label="Leituras" icon="qr_code_scanner" />
      <q-tab name="historico" label="Histórico" icon="history" />
      <q-tab name="ajustes" label="Ajustes" icon="settings_applications" />
    </q-tabs>

    <q-separator />

    <q-tab-panels v-model="activeTab" animated class="bg-white">
      <q-tab-panel name="divergencias">
        <DivergenciasPanel :produtos="inventario.itens" />
      </q-tab-panel>

      <q-tab-panel name="contagens">
        <ContagensPanel :contagens="inventario.contagens" />
      </q-tab-panel>

      <q-tab-panel name="leituras">
        <LeiturasPanel :produtos="inventario.itens" />
      </q-tab-panel>

      <q-tab-panel name="historico">
        <HistoricoPanel :eventos="eventosData" />
      </q-tab-panel>

      <q-tab-panel name="ajustes">
        <AjustesPanel :ajustes="inventario.ajustes" />
      </q-tab-panel>
    </q-tab-panels>
  </q-card>
</template>

<script setup>
import { ref, defineProps } from "vue";
import DivergenciasPanel from "src/components/inventario/tab/DivergenciasPanel.vue";
import ContagensPanel from "src/components/inventario/tab/ContagensPanel.vue";
import LeiturasPanel from "src/components/inventario/tab/LeiturasPanel.vue";
import HistoricoPanel from "src/components/inventario/tab/HistoricoPanel.vue";
import AjustesPanel from "src/components/inventario/tab/AjustesPanel.vue";

const props = defineProps({
  inventario: {
    type: Object,
    required: true,
  },
});

const activeTab = ref("divergencias");

//TODO: IMPLEMENTAR AQUI O EVENTO DE HISTÓRICO
const eventosData = [
  {
    titulo: "Inventário iniciado",
    subtitulo: "21/04/2025 - 08:30",
    icone: "play_circle",
    descricao: "O inventário foi iniciado por nome_usuario Monte. Foram carregados 342 itens para contagem.",
  },
  {
    titulo: "Primeira contagem finalizada",
    subtitulo: "21/04/2025 - 12:45",
    icone: "check_circle",
    descricao: "Ana Silva finalizou a primeira contagem com 85 itens processados.",
  },
  // ...mais eventos
];
</script>
