<template>
  <div>
    <q-card class="bg-white q-mb-md shadow-1" style="border-radius: 12px">
      <q-card-section class="q-pa-none">
        <div class="row items-center justify-between q-px-md q-pt-md q-pb-sm">
          <div class="col-12 col-md-4"><div class="text-h6 text-secondary">Produtos no Inventário</div></div>
          <div class="col-12 col-md-8">
            <InventarioFiltros :produtos="props.produtos" @search="onSearch" @refresh="onRefresh" @view-change="onViewChange" />
          </div>
        </div>
        <q-separator />

        <!-- Tabela de Produtos -->
        <q-table :rows="produtosFiltrados" :columns="colunas" row-key="id" separator="cell" :pagination="{ rowsPerPage: 10 }" dense color="secondary">
          <template v-slot:header-cell="props">
            <q-th :props="props" :style="{ backgroundColor: '#f5f1ed' }" class="text-secondary">
              {{ props.col.label }}
            </q-th>
          </template>

          <template v-slot:body-cell-codigo="props">
            <q-td :props="props">
              <q-chip square color="secondary" text-color="white" size="sm">
                {{ props.value || "N/D" }}
              </q-chip>
            </q-td>
          </template>

          <template v-slot:body-cell-produto="props">
            <q-td :props="props">
              <div class="row items-center no-wrap">
                <div>
                  <div class="text-weight-medium">{{ props.value }}</div>
                  <div class="text-caption text-grey">{{ props.row.categoria }}</div>
                </div>
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-estoque="props">
            <q-td :props="props" class="text-center">
              {{ props.value }}
            </q-td>
          </template>

          <template v-slot:body-cell-contagem="props">
            <q-td :props="props">
              <div class="row items-center justify-between full-width">
                <div class="col-4 text-weight-medium text-center">
                  {{ getUltimaContagem(props.row) }}
                </div>
                <div class="col-8 text-right">
                  <q-chip
                    v-for="(count, index) in getContagens(props.row)"
                    :key="index"
                    size="sm"
                    dense
                    :color="index === 0 ? 'secondary' : 'grey-3'"
                    :text-color="index === 0 ? 'white' : 'black'"
                    class="q-ml-xs"
                  >
                    {{ count.number }}ª: {{ count.value }}
                  </q-chip>
                </div>
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-divergencia="props">
            <q-td :props="props">
              <template v-if="getDivergenciaValorFormatado(props.row)">
                <q-badge :color="getDivergenciaColor(getDivergenciaBruta(props.row))" class="text-center">
                  {{ getDivergenciaValorFormatado(props.row) }}
                </q-badge>
              </template>

              <q-badge :color="getDivergenciaColor(props.value)" class="text-center q-ml-xs">
                <template v-if="props.value !== null && props.value !== 0">{{ props.value > 0 ? "+" : "-" }}{{ Math.abs(props.value) }}%</template>
                <template v-else>
                  {{ props.value !== null ? "0" : "-" }}
                </template>
              </q-badge>
            </q-td>
          </template>

          <template v-slot:body-cell-custoDivergencia="props">
            <q-td :props="props" class="text-right">
              {{ formatCurrency(props.value) }}
            </q-td>
          </template>

          <template v-slot:body-cell-valorEstoque="props">
            <q-td :props="props" class="text-right">
              {{ formatCurrency(props.value) }}
            </q-td>
          </template>

          <template v-slot:body-cell-acoes="props">
            <q-td :props="props" class="text-center">
              <q-btn round flat size="sm" icon="o_build" color="secondary" :disabled="status !== 'REVISAO'" @click="ajustarContagem(props.row)">
                <q-tooltip>
                  {{ status !== "REVISAO" ? "Ajuste disponível apenas após encerramento da contagem" : "Ajustar Contagem" }}
                </q-tooltip>
              </q-btn>

              <q-btn round flat size="sm" icon="history" color="secondary" @click="showHistory(props.row)">
                <q-tooltip>Histórico</q-tooltip>
              </q-btn>
              <q-btn round flat size="sm" icon="more_vert" color="secondary">
                <q-menu>
                  <q-list style="min-width: 150px">
                    <q-item clickable v-close-popup @click="showDetails(props.row)">
                      <q-item-section>Detalhes</q-item-section>
                    </q-item>
                    <q-item clickable v-close-popup @click="markAsCounted(props.row)">
                      <q-item-section>Marcar como contado</q-item-section>
                    </q-item>
                    <q-item clickable v-close-popup @click="addObservation(props.row)">
                      <q-item-section>Adicionar observação</q-item-section>
                    </q-item>
                  </q-list>
                </q-menu>
              </q-btn>
            </q-td>
          </template>

          <!-- Template para quando não houver dados -->
          <template v-slot:no-data>
            <div class="full-width row flex-center q-pa-md text-grey">
              <q-icon name="inventory_2" size="2rem" class="q-mr-sm" />
              <span>Nenhum produto listado porque o inventário ainda não começou. Clique em 'Iniciar Inventário' para começar.</span>
            </div>
          </template>
        </q-table>
      </q-card-section>
    </q-card>
    <AjusteContagem v-model="showAjusteDialog" :id_inventario="id_inventario" :contagem="contagem" @update:inventario="handleInventarioUpdate" />
    <q-dialog v-model="processandoAjustes">
      <q-card class="q-pa-md text-center" style="min-width: 300px">
        <q-circular-progress indeterminate size="50px" color="primary" class="q-mb-md" />
        <div class="text-h6 q-mb-md">Processando ajustes de estoque</div>
        <div class="text-subtitle1">Ajustando {{ progressoAjustes.atual }} de {{ progressoAjustes.total }} produtos</div>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { defineProps, computed, ref, onMounted, onBeforeUnmount } from "vue";
import InventarioFiltros from "src/components/inventario/InventarioFiltros.vue";
import AjusteContagem from "./AjusteContagem.vue";
import eventBus from "@/eventBus";
import { useApiRequest } from "@/composables/useApiRequest";
import { useQuasar, date } from "quasar";

const { apiRequest } = useApiRequest();

const $q = useQuasar();

const props = defineProps({
  produtos: {
    type: Array,
    required: true,
  },
  status: {
    type: String,
    default: "",
  },
  id_inventario: {
    type: [Number, String],
    required: true,
  },
});

const emit = defineEmits(["update:inventario"]);

const showAjusteDialog = ref(false);
const contagem = ref({});
const processandoAjustes = ref(false);
const progressoAjustes = ref({ atual: 0, total: 0 });

const filtrosAtivos = ref({
  text: "",
  categoria: "Todas",
  status: "Todos",
  showDivergencias: false,
  showNaoContados: false,
  minDivergencia: 0, // Alterado para 0 para não filtrar inicialmente
});

function getDivergenciaValorFormatado(row) {
  const bruta = getDivergenciaBruta(row);
  if (bruta === null || bruta === 0) return null;
  const sinal = bruta > 0 ? "+" : "-";
  return `${sinal}${Math.abs(bruta)}`;
}

// Definição das colunas da tabela
const colunas = ref([
  {
    name: "codigo",
    label: "Código",
    field: (row) => row.codigo_produto,
    align: "left",
    sortable: true,
  },
  {
    name: "produto",
    label: "Produto",
    field: (row) => row.descricao_produto,
    align: "left",
    sortable: true,
  },
  {
    name: "estoque",
    label: "Estoque",
    field: (row) => row.quantidade_esperada,
    align: "center",
    sortable: true,
  },
  {
    name: "contagem",
    label: "Contagem",
    field: (row) => getUltimaContagem(row),
    align: "center",
    sortable: true,
  },
  {
    name: "ajuste",
    label: "Ajuste",
    field: (row) => row.quantidade_ajuste,
    align: "center",
    sortable: true,
  },
  {
    name: "divergencia",
    label: "Divergência",
    field: (row) => row.divergencia,
    align: "center",
    sortable: true,
  },
  {
    name: "custoDivergencia",
    label: "Custo Divergência",
    field: (row) => row.custo_divergencia,
    align: "right",
    sortable: true,
  },
  {
    name: "valorEstoque",
    label: "Valor Estoque",
    field: (row) => row.valor_estoque,
    align: "right",
    sortable: true,
  },
  {
    name: "acoes",
    label: "Ações",
    field: "id",
    align: "center",
    sortable: false,
  },
]);

// Função para movimentar o estoque de um item específico
const movimentarEstoqueItem = async (item) => {
  console.log("Movimentando estoque para o item:", item);
  if (!item || !item.produto_id) {
    console.error("Item inválido para ajuste de estoque:", item);
    return false;
  }

  const divergencia = getDivergenciaBruta(item);

  // Se não houver divergência, não é necessário ajustar o estoque
  if (divergencia === null || divergencia === 0) {
    return true;
  }

  try {
    const movimento = {
      id_produto: item.produto_id,
      qtde: divergencia * -1, // A quantidade a ajustar é exatamente a divergência
      tipo: 4, // INVENTARIO
      id_inventario: props.id_inventario,
      observacao: `Ajuste automático do inventário #${props.id_inventario} em ${new Date().toLocaleString("pt-BR")}`,
    };

    // Usando a função apiRequest já existente no código
    await apiRequest("/api/estoque/ajuste-estoque", "POST", movimento);
    return true;
  } catch (error) {
    console.error(`Erro ao ajustar estoque do produto ${item.codigo_produto}:`, error);
    return false;
  }
};

// Função principal que processa todos os produtos do inventário
const movimentarEstoque = async () => {
  // Apenas produtos com divergências precisam ser ajustados
  const produtosComDivergencia = produtosProcessados.value.filter((item) => {
    const divergencia = getDivergenciaBruta(item);
    return divergencia !== null && divergencia !== 0;
  });

  if (produtosComDivergencia.length === 0) {
    $q.notify({
      color: "positive",
      message: "Inventário concluído! Não há divergências para ajustar no estoque.",
      icon: "check_circle",
    });
    return;
  }

  // Mostra diálogo de progresso
  processandoAjustes.value = true;
  progressoAjustes.value = { atual: 0, total: produtosComDivergencia.length };

  // Array para armazenar resultados
  const resultados = {
    sucesso: 0,
    falha: 0,
    produtos: [],
  };

  // Processa os produtos um por um
  for (let i = 0; i < produtosComDivergencia.length; i++) {
    const produto = produtosComDivergencia[i];
    progressoAjustes.value.atual = i + 1;

    try {
      const sucesso = await movimentarEstoqueItem(produto);
      if (sucesso) {
        resultados.sucesso++;
        resultados.produtos.push({
          id: produto.id_produto,
          codigo: produto.codigo_produto,
          descricao: produto.descricao_produto,
          divergencia: getDivergenciaBruta(produto),
          status: "sucesso",
        });
      } else {
        resultados.falha++;
        resultados.produtos.push({
          id: produto.id_produto,
          codigo: produto.codigo_produto,
          descricao: produto.descricao_produto,
          divergencia: getDivergenciaBruta(produto),
          status: "falha",
        });
      }
    } catch (error) {
      resultados.falha++;
      resultados.produtos.push({
        id: produto.id_produto,
        codigo: produto.codigo_produto,
        descricao: produto.descricao_produto,
        divergencia: getDivergenciaBruta(produto),
        status: "falha",
        erro: error.message,
      });
    }
  }

  // Esconde o diálogo de progresso
  processandoAjustes.value = false;

  // Notifica o resultado
  if (resultados.falha === 0) {
    $q.notify({
      color: "positive",
      message: `Inventário concluído com sucesso! ${resultados.sucesso} produtos ajustados.`,
      icon: "check_circle",
      timeout: 5000,
    });
  } else {
    $q.notify({
      color: "warning",
      message: `Inventário concluído parcialmente. ${resultados.sucesso} produtos ajustados e ${resultados.falha} falhas.`,
      icon: "warning",
      timeout: 5000,
    });

    // Log detalhado das falhas
    console.error(
      "Falhas no ajuste de estoque:",
      resultados.produtos.filter((p) => p.status === "falha")
    );
  }

  // Emite evento para atualizar a UI
  //emit("update:inventario", { status: "CONCLUIDO" });
};

const handleInventarioUpdate = (updatedInventario) => {
  emit("update:inventario", {
    ...updatedInventario,
  });
};

// Processamento dos produtos para adicionar propriedades necessárias
const produtosProcessados = computed(() => {
  return props.produtos.map((item) => {
    // Calculando valor_estoque em caso de estar faltando
    const valorEstoque = item.valor_estoque !== undefined ? item.valor_estoque : item.quantidade_contada * item.preco_custo || 0;

    // Adicionando categoria como propriedade separada para exibição
    return {
      ...item,
      categoria: item.categoria_nome,
      valor_estoque: valorEstoque,
      // Calcular divergência se não estiver definida mas tiver contagens
      divergencia:
        item.divergencia !== null
          ? item.divergencia
          : getUltimaContagem(item) !== "-"
          ? calcularDivergencia(item.quantidade_contada, getUltimaContagem(item))
          : null,
    };
  });
});

// Produtos filtrados conforme os critérios aplicados
// Modifique a função produtosFiltrados para aplicar minDivergencia independentemente
const produtosFiltrados = computed(() => {
  let resultado = [...produtosProcessados.value];

  // Filtro de texto (código ou descrição)
  if (filtrosAtivos.value.text) {
    const texto = filtrosAtivos.value.text.toLowerCase();
    resultado = resultado.filter(
      (produto) =>
        (produto.codigo_produto && produto.codigo_produto.toLowerCase().includes(texto)) ||
        (produto.descricao_produto && produto.descricao_produto.toLowerCase().includes(texto))
    );
  }

  // Filtro de categoria
  if (filtrosAtivos.value.categoria && filtrosAtivos.value.categoria !== "Todas") {
    resultado = resultado.filter((produto) => produto.categoria_nome === filtrosAtivos.value.categoria);
  }

  // Filtro de status
  if (filtrosAtivos.value.status !== "Todos") {
    switch (filtrosAtivos.value.status) {
      case "Divergência":
        resultado = resultado.filter((produto) => produto.divergencia !== 0 && produto.divergencia !== null);
        break;
      case "OK":
        resultado = resultado.filter((produto) => produto.divergencia === 0);
        break;
      case "Não contado":
        resultado = resultado.filter((produto) => getUltimaContagem(produto) === "-");
        break;
    }
  }

  // Filtros avançados
  if (filtrosAtivos.value.showDivergencias) {
    resultado = resultado.filter((produto) => produto.divergencia !== 0 && produto.divergencia !== null);
  }

  // Aplicar filtro de divergência mínima independentemente da opção "Somente divergências"
  if (filtrosAtivos.value.minDivergencia > 0) {
    resultado = resultado.filter(
      (produto) => produto.divergencia !== null && produto.divergencia !== 0 && Math.abs(produto.divergencia) >= filtrosAtivos.value.minDivergencia
    );
  }

  if (filtrosAtivos.value.showNaoContados) {
    resultado = resultado.filter((produto) => getUltimaContagem(produto) === "-");
  }

  return resultado;
});

// Função para calcular divergência
const calcularDivergencia = (quantidade_sistema, quantidade_contada) => {
  if (quantidade_sistema === 0 && quantidade_contada === 0) return 0;
  if (quantidade_sistema === 0) return 100; // Se sistema é zero mas contou algo

  return Math.round(((quantidade_contada - quantidade_sistema) / quantidade_sistema) * 100);
};

// Funções para contagens
const getContagens = (row) => {
  if (row.contagens && Array.isArray(row.contagens) && row.contagens.length > 0) {
    const total = row.contagens.length;
    return row.contagens.map((contagem, index) => {
      return {
        number: total - index,
        value: contagem.quantidade_contada || 0,
      };
    });
  }

  // Se não tiver contagens reais, retorna um array vazio
  return [];
};

const getUltimaContagem = (row) => {
  const contagens = getContagens(row);
  return contagens.length > 0 ? contagens[0].value : "-";
};

// Função para formatar moeda
const formatCurrency = (value) => {
  const numberValue = Number(value) || 0;
  return (
    "R$ " +
    numberValue
      .toFixed(2)
      .replace(".", ",")
      .replace(/(\d)(?=(\d{3})+(?!\d))/g, "$1.")
  );
};

// Função para cores de divergência
const getDivergenciaColor = (bruta) => {
  if (bruta === null || bruta === undefined) return "grey";
  if (bruta === 0) return "positive";
  // agora:
  // positivo (sobrando) → amarelo (warning)
  // negativo (faltando)  → vermelho (negative)
  return bruta > 0 ? "warning" : "negative";
};

function getDivergenciaBruta(row) {
  if (!row) return null;
  const contagemFinal = getUltimaContagem(row) + row.quantidade_ajuste;
  return contagemFinal - row.quantidade_esperada;
}

// Função para processar filtros recebidos do componente de filtro
const onSearch = (filtros) => {
  filtrosAtivos.value = { ...filtros };
};

// Função para lidar com o evento de refresh
const onRefresh = () => {
  console.log("Atualizando dados da tabela");
  // Aqui você pode adicionar a lógica para recarregar os dados do backend
};

// Função para lidar com mudanças na visualização
const onViewChange = (view) => {
  console.log(`Modo de visualização alterado para: ${view}`);
  // Implementar lógica para mudança de visualização se necessário
};

// Funções de ação
const ajustarContagem = (item) => {
  contagem.value = item;
  showAjusteDialog.value = true;
};

const showHistory = (item) => {
  console.log("Mostrar histórico do item:", item);
};

const showDetails = (item) => {
  console.log("Mostrar detalhes do item:", item);
};

const markAsCounted = (item) => {
  console.log("Marcar item como contado:", item);
};

const addObservation = (item) => {
  console.log("Adicionar observação ao item:", item);
};

// Configuração do eventBus para escutar o evento "inventario-concluido"
onMounted(() => {
  eventBus.on("inventario-concluido", handleInventarioConcluido);
});

// Remove o listener quando o componente é desmontado
onBeforeUnmount(() => {
  eventBus.off("inventario-concluido", handleInventarioConcluido);
});

// Função que trata o evento "inventario-concluido"
function handleInventarioConcluido(data) {
  console.log("Evento recebido: inventario-concluido", data);

  movimentarEstoque();
}
</script>
