<template>
  <div>
    <q-timeline color="info" class="history-timeline">
      <q-timeline-entry
        dense
        v-for="(item, index) in historyData"
        :key="index"
        :title="item.acao"
        :subtitle="formatDate(item.data)"
        icon="event_note"
        :color="getHistoryColor(item.acao)"
      >
        <div class="text-body2">{{ item.usuario }} • {{ item.acao }}</div>
      </q-timeline-entry>
    </q-timeline>
    <div v-if="!historyData || historyData.length === 0" class="text-center q-pa-md text-grey">Nenhum histórico disponível.</div>
  </div>
</template>

<script setup>
import { useTaskUtils } from "@/composables/useTaskUtils";

const { formatDate } = useTaskUtils();

const props = defineProps({
  historyData: {
    type: Array,
    default: () => [],
  },
});

// Cor para itens do histórico
function getHistoryColor(action) {
  if (action.includes("Criado")) return "info";
  if (action.includes("Editado")) return "secondary";
  if (action.includes("Movido")) return "accent";
  if (action.includes("Concluído")) return "positive";
  return "grey";
}
</script>

<style lang="scss" scoped>
.history-timeline {
  max-height: 300px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-left: 16px; // espaço extra pra não cortar a linha/ícone
  font-size: 12px;

  :deep(.q-timeline__entry) {
    padding-bottom: 16px;

    &:last-child {
      padding-bottom: 0;
    }

    .q-timeline__subtitle {
      opacity: 0.7;
    }

    .q-timeline__title {
      font-size: 13px;
    }

    .q-timeline__content {
      font-size: 12px;
    }
  }
}
</style>
