<template>
  <div class="contagens-container">
    <q-card flat class="bg-white">
      <q-card-section class="q-pa-none">
        <div class="row items-center justify-between q-px-md q-pt-md q-pb-sm">
          <div class="text-h6 text-secondary">Contagens Realizadas</div>
          <q-input v-model="filtroData" type="date" dense outlined class="col-12 col-md-3" label="Filtrar por data" />
        </div>

        <q-separator />

        <!-- Cards com resumo das contagens -->
        <div class="row q-pa-md q-col-gutter-md">
          <div class="col-12 col-md-3">
            <q-card class="stat-card bg-blue-1 text-center">
              <q-card-section>
                <div class="text-h3 text-primary">{{ props.contagens.length }}</div>
                <div class="text-subtitle2">Contagens Realizadas</div>
              </q-card-section>
            </q-card>
          </div>

          <div class="col-12 col-md-3">
            <q-card class="stat-card bg-green-1 text-center">
              <q-card-section>
                <div class="text-h3 text-positive">{{ totalItens }}</div>
                <div class="text-subtitle2">Itens</div>
              </q-card-section>
            </q-card>
          </div>

          <div class="col-12 col-md-3">
            <q-card class="stat-card bg-purple-1 text-center">
              <q-card-section>
                <div class="text-h3 text-purple">{{ usuariosAtivos }}</div>
                <div class="text-subtitle2">Usuários Ativos</div>
              </q-card-section>
            </q-card>
          </div>

          <div class="col-12 col-md-3">
            <q-card class="stat-card bg-orange-1 text-center">
              <q-card-section>
                <div class="text-h3 text-orange">{{ tempoMedio }}</div>
                <div class="text-subtitle2">Tempo Médio</div>
              </q-card-section>
            </q-card>
          </div>
        </div>

        <!-- Tabela de contagens -->
        <div class="q-pa-md">
          <q-table
            dense
            :rows="props.contagens"
            :columns="colunas"
            row-key="id"
            separator="cell"
            :pagination="{ rowsPerPage: 10 }"
            class="contagens-table"
          >
            <template v-slot:header-cell="props">
              <q-th :props="props" style="background-color: #f5f1ed" class="text-secondary">
                {{ props.col.label }}
              </q-th>
            </template>

            <template v-slot:body-cell-id="props">
              <q-td :props="props">
                <q-chip square color="secondary" text-color="white" size="sm">#{{ props.value }}</q-chip>
              </q-td>
            </template>

            <template v-slot:body-cell-status="props">
              <q-td :props="props" class="text-center">
                <q-badge :color="getStatusColor(props.value)" class="q-py-xs q-px-sm">
                  {{ getStatusDisplay(props.value) }}
                </q-badge>
              </q-td>
            </template>

            <template v-slot:body-cell-qtd_itens="props">
              <q-td :props="props" class="text-center">
                <q-badge color="info" class="q-py-xs q-px-sm">
                  {{ props.value }}
                </q-badge>
              </q-td>
            </template>

            <template v-slot:body-cell-acoes="props">
              <q-td :props="props" class="text-center">
                <q-btn round flat size="sm" icon="visibility" color="secondary" @click="verDetalhes(props.row)">
                  <q-tooltip>Ver detalhes</q-tooltip>
                </q-btn>

                <q-btn round flat size="sm" icon="description" color="secondary" @click="gerarRelatorio(props.row)">
                  <q-tooltip>Gerar relatório</q-tooltip>
                </q-btn>
              </q-td>
            </template>

            <template v-slot:no-data>
              <div class="full-width row flex-center q-pa-lg">
                <q-icon name="assignment" size="2em" color="grey-6" class="q-mr-sm" />
                <span class="text-grey-6">Nenhuma contagem encontrada</span>
              </div>
            </template>
          </q-table>
        </div>
      </q-card-section>
    </q-card>

    <!-- Dialog para detalhes da contagem -->
    <q-dialog v-model="detalhesDialog" persistent>
      <q-card style="width: 800px; max-width: 90vw">
        <q-card-section class="row items-center bg-secondary text-white">
          <div class="text-h6">Detalhes da Contagem #{{ contagemSelecionada?.id }}</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section>
          <div class="row q-col-gutter-md">
            <div class="col-12 col-md-6">
              <q-list bordered separator>
                <q-item>
                  <q-item-section avatar>
                    <q-icon name="event" color="secondary" />
                  </q-item-section>
                  <q-item-section>
                    <q-item-label caption>Data de Início</q-item-label>
                    <q-item-label>{{ contagemSelecionada?.data_inicio_formatada }}</q-item-label>
                  </q-item-section>
                </q-item>

                <q-item>
                  <q-item-section avatar>
                    <q-icon name="update" color="secondary" />
                  </q-item-section>
                  <q-item-section>
                    <q-item-label caption>Última Atualização</q-item-label>
                    <q-item-label>{{ contagemSelecionada?.ultima_atualizacao_formatada }}</q-item-label>
                  </q-item-section>
                </q-item>

                <q-item>
                  <q-item-section avatar>
                    <q-icon name="list_alt" color="secondary" />
                  </q-item-section>
                  <q-item-section>
                    <q-item-label caption>Status</q-item-label>
                    <q-item-label>
                      <q-badge :color="getStatusColor(contagemSelecionada?.status)">
                        {{ getStatusDisplay(contagemSelecionada?.status) }}
                      </q-badge>
                    </q-item-label>
                  </q-item-section>
                </q-item>
              </q-list>
            </div>

            <div class="col-12 col-md-6">
              <q-list bordered separator>
                <q-item>
                  <q-item-section avatar>
                    <q-icon name="inventory_2" color="secondary" />
                  </q-item-section>
                  <q-item-section>
                    <q-item-label caption>Total de Itens</q-item-label>
                    <q-item-label>{{ contagemSelecionada?.qtd_itens || 0 }}</q-item-label>
                  </q-item-section>
                </q-item>

                <q-item>
                  <q-item-section avatar>
                    <q-icon name="person" color="secondary" />
                  </q-item-section>
                  <q-item-section>
                    <q-item-label caption>Usuário</q-item-label>
                    <q-item-label>{{ contagemSelecionada?.nome_usuario || "N/A" }}</q-item-label>
                  </q-item-section>
                </q-item>

                <q-item>
                  <q-item-section avatar>
                    <q-icon name="notes" color="secondary" />
                  </q-item-section>
                  <q-item-section>
                    <q-item-label caption>Observação</q-item-label>
                    <q-item-label class="text-wrap">{{ contagemSelecionada?.observacao || "Sem observações" }}</q-item-label>
                  </q-item-section>
                </q-item>
              </q-list>
            </div>
          </div>

          <!-- Progresso da contagem -->
          <div class="q-mt-lg">
            <div class="text-subtitle1 q-mb-sm">Progresso da Contagem</div>
            <q-linear-progress :value="calcularProgresso(contagemSelecionada)" size="25px" :color="getProgressoColor(contagemSelecionada)">
              <div class="absolute-full flex flex-center">
                <q-badge color="transparent" text-color="white">{{ Math.round(calcularProgresso(contagemSelecionada) * 100) }}% concluído</q-badge>
              </div>
            </q-linear-progress>
          </div>

          <!-- Timeline de atividades -->
          <div class="q-mt-lg">
            <div class="text-subtitle1 q-mb-sm">Histórico da Contagem</div>
            <q-timeline color="secondary">
              <q-timeline-entry title="Contagem Iniciada" :subtitle="contagemSelecionada?.nome_usuario || 'Sistema'">
                <template v-slot:icon>
                  <q-icon name="play_arrow" />
                </template>
                <div>Início em {{ contagemSelecionada?.data_inicio_formatada }}</div>
                <div class="text-caption">
                  {{ contagemSelecionada?.observacao }}
                </div>
              </q-timeline-entry>

              <q-timeline-entry title="Última Atualização" subtitle="Sistema">
                <template v-slot:icon>
                  <q-icon name="update" />
                </template>
                <div>Atualizado em {{ contagemSelecionada?.ultima_atualizacao_formatada }}</div>
                <div class="text-caption">{{ contagemSelecionada?.qtd_itens }} itens contados</div>
              </q-timeline-entry>

              <q-timeline-entry v-if="contagemSelecionada?.status === 'FINALIZADA'" title="Contagem Finalizada" subtitle="Sistema">
                <template v-slot:icon>
                  <q-icon name="check_circle" />
                </template>
                <div>Contagem marcada como finalizada</div>
              </q-timeline-entry>

              <q-timeline-entry v-if="contagemSelecionada?.status === 'CANCELADA'" title="Contagem Cancelada" subtitle="Sistema">
                <template v-slot:icon>
                  <q-icon name="cancel" />
                </template>
                <div>Esta contagem foi cancelada</div>
              </q-timeline-entry>
            </q-timeline>
          </div>
        </q-card-section>

        <q-card-actions align="right" class="bg-grey-2">
          <q-btn flat label="Fechar" color="primary" v-close-popup />
          <q-btn flat label="Exportar Dados" color="secondary" @click="exportarDados" icon="download" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";

// Props
const props = defineProps({
  contagens: {
    type: Array,
    required: true,
    default: () => [],
  },
});

// Estado
const filtroData = ref("");
const detalhesDialog = ref(false);
const contagemSelecionada = ref(null);

// Estatísticas
const totalItens = computed(() => {
  return props.contagens.reduce((total, contagem) => total + (contagem.qtd_itens || 0), 0);
});

const usuariosAtivos = computed(() => {
  const usuariosIds = new Set(props.contagens.filter((contagem) => contagem.usuario_id > 0).map((contagem) => contagem.usuario_id));

  return usuariosIds.size || (props.contagens.length > 0 ? 1 : 0); // Se não há usuários reais, mostrar pelo menos 1 se houver contagens
});

const tempoMedio = computed(() => {
  if (props.contagens.length === 0) return "0 min";

  // Calcular tempo médio entre data_inicio e ultima_atualizacao
  let tempoTotalMinutos = 0;

  props.contagens.forEach((contagem) => {
    try {
      const dataInicio = new Date(contagem.data_inicio);
      const dataFim = new Date(contagem.ultima_atualizacao);
      const diffMs = dataFim - dataInicio;
      const diffMinutos = Math.floor(diffMs / (1000 * 60));
      tempoTotalMinutos += diffMinutos;
    } catch (e) {
      // Em caso de erro, usar valor estimado
      tempoTotalMinutos += 25;
    }
  });

  const mediaMinutos = Math.round(tempoTotalMinutos / props.contagens.length);

  if (mediaMinutos < 60) {
    return `${mediaMinutos} min`;
  } else {
    const horas = Math.floor(mediaMinutos / 60);
    const minutos = mediaMinutos % 60;
    return `${horas}h ${minutos}m`;
  }
});

// Filtragem de contagens
const contagensFiltradas = computed(() => {
  if (!filtroData.value) return props.contagens;

  return props.contagens.filter((contagem) => {
    try {
      if (!contagem.data_inicio_formatada) return false;

      const parts = contagem.data_inicio_formatada.split("/");
      if (parts.length < 3) return false;

      const dataParte = parts[2].split(" ")[0]; // Pega apenas o ano da data formatada
      const dataConvertida = `${dataParte}-${parts[1]}-${parts[0]}`;
      return dataConvertida === filtroData.value;
    } catch (e) {
      console.error("Erro ao filtrar data:", e);
      return false;
    }
  });
});

// Colunas da tabela
const colunas = ref([
  { name: "id", align: "left", label: "ID", field: "id", sortable: true },
  { name: "data_inicio_formatada", align: "left", label: "Data Início", field: "data_inicio_formatada", sortable: true },
  { name: "nome_usuario", align: "left", label: "Usuário", field: "nome_usuario", sortable: true },
  { name: "qtd_itens", align: "center", label: "Itens", field: "qtd_itens", sortable: true },
  { name: "status", align: "center", label: "Status", field: "status", sortable: true },
  { name: "acoes", align: "center", label: "Ações", field: "id", sortable: false },
]);

// Funções auxiliares
const getStatusDisplay = (status) => {
  switch (status) {
    case "EM_ANDAMENTO":
      return "Em Andamento";
    case "FINALIZADA":
      return "Finalizada";
    case "CANCELADA":
      return "Cancelada";
    default:
      return status || "Desconhecido";
  }
};

const getStatusColor = (status) => {
  switch (status) {
    case "FINALIZADA":
      return "positive";
    case "EM_ANDAMENTO":
      return "info";
    case "CANCELADA":
      return "negative";
    default:
      return "grey";
  }
};

// Métodos
const verDetalhes = (contagem) => {
  contagemSelecionada.value = contagem;
  detalhesDialog.value = true;
};

const gerarRelatorio = (contagem) => {
  console.log("Gerar relatório para contagem:", contagem.id);
  // Implementação futura
};

const exportarDados = () => {
  console.log("Exportar dados da contagem:", contagemSelecionada.value.id);
  // Implementação futura
};

const calcularProgresso = (contagem) => {
  if (!contagem) return 0;

  // Baseado apenas no status oficial
  switch (contagem.status) {
    case "FINALIZADA":
      return 1;
    case "EM_ANDAMENTO":
      // Estimativa baseada na quantidade de itens contados
      return contagem.qtd_itens > 0 ? 0.65 : 0.2;
    case "CANCELADA":
      // Contagem cancelada mostra progresso parcial
      return 0.3;
    default:
      return 0;
  }
};

const getProgressoColor = (contagem) => {
  if (!contagem) return "grey";

  switch (contagem.status) {
    case "FINALIZADA":
      return "positive";
    case "EM_ANDAMENTO":
      return "info";
    case "CANCELADA":
      return "negative";
    default:
      return "grey";
  }
};
</script>

<style scoped>
.contagens-container {
  width: 100%;
  margin: 0 auto;
}

.stat-card {
  border-radius: 8px;
  transition: all 0.2s ease;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.12);
}

.contagens-table {
  border-radius: 8px;
  overflow: hidden;
}

/* Fix para o problema de cores no Quasar */
:deep(.q-table th) {
  background-color: #f5f1ed !important;
}
</style>
