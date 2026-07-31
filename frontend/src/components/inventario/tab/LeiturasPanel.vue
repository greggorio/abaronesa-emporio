<template>
  <div class="leituras-container">
    <q-card flat class="bg-white">
      <q-card-section class="q-pa-none">
        <div class="row items-center justify-between q-px-md q-pt-md q-pb-sm">
          <div class="text-h6 text-secondary">Leituras Realizadas</div>
          <div class="row q-col-gutter-sm items-center">
            <div class="col-auto">
              <q-select
                v-model="filtroContagem"
                :options="opcoesContagem"
                label="Contagem"
                dense
                outlined
                emit-value
                map-options
                options-dense
                style="min-width: 150px"
              />
            </div>
            <div class="col-auto">
              <q-input v-model="filtroProduto" label="Buscar produto" dense outlined clearable>
                <template v-slot:append>
                  <q-icon name="search" />
                </template>
              </q-input>
            </div>
          </div>
        </div>

        <q-separator />

        <!-- Cards estatísticos -->
        <div class="row q-pa-md q-col-gutter-md">
          <div class="col-12 col-md-4">
            <q-card class="bg-blue-1 text-center">
              <q-card-section>
                <div class="text-h3 text-primary">{{ totalLeituras }}</div>
                <div class="text-subtitle2">Total de Leituras</div>
              </q-card-section>
            </q-card>
          </div>

          <div class="col-12 col-md-4">
            <q-card class="bg-green-1 text-center">
              <q-card-section>
                <div class="text-h3 text-positive">{{ produtosLidos.length }}</div>
                <div class="text-subtitle2">Produtos Lidos</div>
              </q-card-section>
            </q-card>
          </div>

          <div class="col-12 col-md-4">
            <q-card class="bg-purple-1 text-center">
              <q-card-section>
                <div class="text-h3 text-purple">{{ contagensUnicas.length }}</div>
                <div class="text-subtitle2">Sessões de Contagem</div>
              </q-card-section>
            </q-card>
          </div>
        </div>

        <!-- Gráfico de leituras por período -->
        <div class="q-pa-md">
          <div class="text-subtitle1 text-secondary q-mb-md">Leituras por Período</div>
          <div style="height: 200px; position: relative" class="bg-grey-2 rounded-borders flex flex-center">
            <div class="bar-chart row items-end justify-around full-width q-px-md">
              <div v-for="(periodo, index) in periodos" :key="index" class="bar-container flex flex-center">
                <div
                  class="bar q-mx-xs"
                  :style="{
                    height: calcularAlturaGrafico(periodo.quantidade),
                    width: '25px',
                    backgroundColor: `rgba(25, 118, 210, ${0.3 + (periodo.quantidade / maxLeituras) * 0.7})`,
                  }"
                >
                  <q-tooltip>{{ periodo.horario }}: {{ periodo.quantidade }} leituras</q-tooltip>
                </div>
                <div class="text-caption absolute-bottom text-center">{{ periodo.label }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Tabela de leituras -->
        <div class="q-pa-md">
          <q-table :rows="leiturasFiltradas" :columns="colunas" row-key="id" separator="cell" :pagination="{ rowsPerPage: 10 }">
            <template v-slot:header-cell="props">
              <q-th :props="props" class="bg-[#f5f1ed] text-secondary">
                {{ props.col.label }}
              </q-th>
            </template>

            <template v-slot:body-cell-codigo="props">
              <q-td :props="props">
                <q-chip square color="secondary" text-color="white" size="sm">
                  {{ props.value }}
                </q-chip>
              </q-td>
            </template>

            <template v-slot:body-cell-usuario="props">
              <q-td :props="props">
                <div class="row items-center">
                  <q-avatar size="sm" color="grey-4" text-color="white" class="q-mr-xs">
                    {{ iniciais(props.value) }}
                  </q-avatar>
                  {{ props.value }}
                </div>
              </q-td>
            </template>

            <template v-slot:body-cell-data_hora="props">
              <q-td :props="props">
                {{ props.value }}
              </q-td>
            </template>

            <template v-slot:body-cell-quantidade="props">
              <q-td :props="props" class="text-center">
                <q-badge :color="props.value > 1 ? 'warning' : 'positive'" class="q-py-xs q-px-sm">
                  {{ props.value }}
                </q-badge>
              </q-td>
            </template>

            <template v-slot:body-cell-sequencia="props">
              <q-td :props="props" class="text-center">
                <q-chip dense size="sm" color="grey-6" text-color="white">{{ props.value }}ª</q-chip>
              </q-td>
            </template>

            <template v-slot:body-cell-acao="props">
              <q-td :props="props" class="text-center">
                <q-btn round flat size="sm" icon="visibility" color="secondary" @click="verDetalhesLeitura(props.row)">
                  <q-tooltip>Ver detalhes</q-tooltip>
                </q-btn>
              </q-td>
            </template>
          </q-table>
        </div>

        <!-- Resumo de contagens -->
        <div class="q-pa-md">
          <div class="text-subtitle1 text-secondary q-mb-md">Resumo das Contagens</div>
          <div class="row q-col-gutter-md">
            <div v-for="(contagem, index) in contagensInfo" :key="index" class="col-12 col-md-6">
              <q-card flat bordered>
                <q-card-section class="q-pa-sm">
                  <div class="row items-center q-mb-sm">
                    <q-chip color="secondary" text-color="white" class="q-mr-sm">#{{ contagem.id }}</q-chip>
                    <div class="text-subtitle1">Contagem {{ contagem.id }}</div>
                  </div>

                  <q-list dense>
                    <q-item>
                      <q-item-section>
                        <q-item-label caption>Data/Hora</q-item-label>
                        <q-item-label class="text-weight-medium">{{ contagem.data_formatada }}</q-item-label>
                      </q-item-section>
                    </q-item>

                    <q-item>
                      <q-item-section>
                        <q-item-label caption>Total de leituras</q-item-label>
                        <q-item-label class="text-weight-medium">{{ contagem.totalLeituras }}</q-item-label>
                      </q-item-section>
                    </q-item>

                    <q-item>
                      <q-item-section>
                        <q-item-label caption>Produtos contados</q-item-label>
                        <q-item-label class="text-weight-medium">{{ contagem.produtosContados }}</q-item-label>
                      </q-item-section>
                    </q-item>

                    <q-item>
                      <q-item-section>
                        <q-item-label caption>Operador</q-item-label>
                        <q-item-label class="text-weight-medium">{{ contagem.usuario }}</q-item-label>
                      </q-item-section>
                    </q-item>
                  </q-list>
                </q-card-section>
              </q-card>
            </div>
          </div>
        </div>
      </q-card-section>
    </q-card>

    <!-- Dialog de detalhes da leitura -->
    <q-dialog v-model="detalhesDialog">
      <q-card style="width: 700px; max-width: 90vw">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Detalhes da Leitura</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-separator />

        <q-card-section class="q-pa-md">
          <div v-if="leituraSelecionada">
            <div class="row q-col-gutter-md">
              <div class="col-6">
                <div class="text-caption">Produto</div>
                <div class="text-body1">{{ leituraSelecionada.produto }}</div>
              </div>

              <div class="col-6">
                <div class="text-caption">Código</div>
                <div class="text-body1">{{ leituraSelecionada.codigo }}</div>
              </div>

              <div class="col-6">
                <div class="text-caption">Quantidade</div>
                <div class="text-body1">{{ leituraSelecionada.quantidade }}</div>
              </div>

              <div class="col-6">
                <div class="text-caption">Contagem</div>
                <div class="text-body1">#{{ leituraSelecionada.contagem_id }}</div>
              </div>

              <div class="col-6">
                <div class="text-caption">Data e Hora</div>
                <div class="text-body1">{{ leituraSelecionada.data_hora_formatada }}</div>
              </div>

              <div class="col-6">
                <div class="text-caption">Usuário</div>
                <div class="text-body1">{{ leituraSelecionada.usuario }}</div>
              </div>

              <div class="col-6">
                <div class="text-caption">Sequência</div>
                <div class="text-body1">{{ leituraSelecionada.sequencia }}ª leitura</div>
              </div>

              <div class="col-6">
                <div class="text-caption">Categoria</div>
                <div class="text-body1">{{ leituraSelecionada.categoria }}</div>
              </div>
            </div>

            <q-separator class="q-my-md" />

            <div>
              <div class="text-caption">Sequência de leituras deste produto</div>
              <q-timeline color="secondary" layout="comfortable">
                <q-timeline-entry
                  v-for="(historico, index) in sequenciaLeituras"
                  :key="index"
                  :title="`Leitura #${historico.sequencia}`"
                  :subtitle="historico.data_hora_formatada"
                  icon="inventory_2"
                  :color="historico.id === leituraSelecionada.id ? 'primary' : ''"
                >
                  <div>Quantidade: {{ historico.quantidade_contada }} - Contagem #{{ historico.contagem_id }}</div>
                </q-timeline-entry>
              </q-timeline>
            </div>
          </div>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Fechar" color="primary" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";

// Props
const props = defineProps({
  produtos: {
    type: Array,
    required: true,
  },
});

// Estado
const filtroContagem = ref(null);
const filtroProduto = ref("");
const detalhesDialog = ref(false);
const leituraSelecionada = ref(null);
const sequenciaLeituras = ref([]);

const produtosLidos = computed(() => {
  const produtosSet = new Set();
  todasLeituras.value.forEach((leitura) => {
    produtosSet.add(leitura.produto_id);
  });
  return Array.from(produtosSet);
});

// Extrair todas as leituras dos produtos
const todasLeituras = computed(() => {
  const leituras = [];

  props.produtos.forEach((produto) => {
    if (!produto.contagens) return;

    produto.contagens.forEach((contagem) => {
      if (!contagem.detalhes_contagens) return;

      contagem.detalhes_contagens.forEach((detalhe) => {
        if (detalhe.quantidade_contada > 0) {
          leituras.push({
            id: detalhe.id,
            produto: produto.descricao_produto,
            codigo: produto.codigo_produto,
            categoria: produto.categoria_nome,
            quantidade: detalhe.quantidade_contada,
            data_hora_formatada: detalhe.data_hora_formatada,
            data_hora_contagem: detalhe.data_hora_contagem,
            usuario: contagem.nome_usuario,
            sequencia: detalhe.sequencia,
            contagem_id: contagem.contagem_id,
            produto_id: produto.produto_id,
          });
        }
      });
    });
  });

  // Ordenar por data/hora mais recente
  return leituras.sort((a, b) => new Date(b.data_hora_contagem) - new Date(a.data_hora_contagem));
});

// Contagens únicas
const contagensUnicas = computed(() => {
  const ids = new Set();
  todasLeituras.value.forEach((leitura) => {
    ids.add(leitura.contagem_id);
  });
  return Array.from(ids).sort((a, b) => a - b);
});

// Informações sobre cada contagem
const contagensInfo = computed(() => {
  const info = {};

  todasLeituras.value.forEach((leitura) => {
    const id = leitura.contagem_id;

    if (!info[id]) {
      // Obter a primeira leitura para esta contagem para extrair informações
      const primeiraLeitura = todasLeituras.value.find((l) => l.contagem_id === id);

      info[id] = {
        id: id,
        usuario: primeiraLeitura.usuario,
        data_formatada: primeiraLeitura.data_hora_formatada,
        totalLeituras: 0,
        produtosContados: new Set(),
      };
    }

    info[id].totalLeituras++;
    info[id].produtosContados.add(leitura.produto_id);
  });

  // Converter para array e adicionar contagem de produtos
  return Object.values(info)
    .map((item) => {
      item.produtosContados = item.produtosContados.size;
      return item;
    })
    .sort((a, b) => a.id - b.id);
});

// Opções para filtros
const opcoesContagem = computed(() => {
  return [{ label: "Todas", value: null }, ...contagensUnicas.value.map((id) => ({ label: `Contagem #${id}`, value: id }))];
});

// Leituras filtradas
const leiturasFiltradas = computed(() => {
  let resultado = [...todasLeituras.value];

  if (filtroContagem.value !== null) {
    resultado = resultado.filter((l) => l.contagem_id === filtroContagem.value);
  }

  if (filtroProduto.value) {
    const termo = filtroProduto.value.toLowerCase();
    resultado = resultado.filter((l) => l.produto.toLowerCase().includes(termo) || l.codigo.toLowerCase().includes(termo));
  }

  return resultado;
});

// Estatísticas
const totalLeituras = computed(() => todasLeituras.value.length);

// Dados para o gráfico
const periodos = computed(() => {
  // Agrupa leituras por hora
  const porHora = {};

  leiturasFiltradas.value.forEach((l) => {
    // Extrair hora da string formatada
    const horaParts = l.data_hora_formatada.split(" ")[1].split(":");
    const hora = horaParts[0];

    if (!porHora[hora]) {
      porHora[hora] = {
        hora: hora,
        horario: hora + "h",
        quantidade: 0,
        label: hora + "h",
      };
    }
    porHora[hora].quantidade++;
  });

  // Converte para array e ordena por hora
  return Object.values(porHora).sort((a, b) => a.hora - b.hora);
});

const maxLeituras = computed(() => {
  if (periodos.value.length === 0) return 1;
  return Math.max(...periodos.value.map((p) => p.quantidade));
});

// Colunas da tabela
const colunas = ref([
  { name: "codigo", align: "left", label: "Código", field: "codigo", sortable: true },
  { name: "produto", align: "left", label: "Produto", field: "produto", sortable: true },
  { name: "data_hora", align: "left", label: "Data/Hora", field: "data_hora_formatada", sortable: true },
  { name: "usuario", align: "left", label: "Usuário", field: "usuario", sortable: true },
  { name: "quantidade", align: "center", label: "Qtd", field: "quantidade", sortable: true },
  { name: "sequencia", align: "center", label: "Sequência", field: "sequencia", sortable: true },
  { name: "contagem", align: "center", label: "Contagem", field: "contagem_id", sortable: true },
  { name: "acao", align: "center", label: "", field: "id", sortable: false },
]);

// Helpers
const iniciais = (nome) => {
  if (!nome) return "";
  return nome
    .split(" ")
    .map((parte) => parte[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
};

const calcularAlturaGrafico = (quantidade) => {
  // Calcula altura proporcional com mínimo de 20px
  const percentual = quantidade / maxLeituras.value;
  const alturaMinima = 20;
  const alturaMaxima = 180;
  const altura = Math.max(alturaMinima, percentual * alturaMaxima);
  return `${altura}px`;
};

// Métodos
const verDetalhesLeitura = (leitura) => {
  leituraSelecionada.value = leitura;

  // Busca todas as leituras do mesmo produto
  const todasLeiturasDesteProduto = todasLeituras.value.filter((l) => l.produto_id === leitura.produto_id).sort((a, b) => b.sequencia - a.sequencia);

  sequenciaLeituras.value = todasLeiturasDesteProduto;
  detalhesDialog.value = true;
};
</script>

<style scoped>
.leituras-container {
  width: 100%;
  margin: 0 auto;
}

.bar-chart {
  height: 100%;
}

.bar-container {
  height: 100%;
  flex-direction: column;
  position: relative;
}

.bar {
  transition: height 0.3s ease;
}
</style>
