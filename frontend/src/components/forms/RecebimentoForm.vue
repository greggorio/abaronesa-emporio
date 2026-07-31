<template>
  <q-dialog v-model="modelValue" persistent>
    <q-card style="width: 90%; max-width: 1200px; min-height: 600px">
      <q-btn flat round dense icon="close" class="absolute-top-right q-ma-sm" style="cursor: pointer; z-index: 1000" @click="emitirFechar" />
      <q-card-section class="q-pt-none">
        <div class="text-h6">
          {{ isEditando ? "Editar Recebimento" : "Entrada de Mercadoria" }}
        </div>
      </q-card-section>
      <q-form ref="recebimentoForm" @submit.prevent="emitirSalvar">
        <q-card-section>
          <div class="row q-col-gutter-md">
            <!-- Cabeçalho do formulário -->
            <div class="col-12" v-if="!isEditando">
              <div class="row q-col-gutter-md">
                <div class="col-8">
                  <div class="row q-col-gutter-sm">
                    <div class="col-12">
                      <div class="row q-col-gutter-sm items-center">
                        <div class="col-2">NF:</div>
                        <div class="col-6">
                          <q-input dense v-model="recebimento.nf" placeholder="000000" />
                        </div>
                        <!-- Novo botão para importar NF-e -->
                        <div class="col-4">
                          <q-btn color="primary" icon-right="upload_file" label="Importar NF-e" size="sm" outline @click="abrirModalImportacao" />
                        </div>
                      </div>
                    </div>
                    <div class="col-12 q-mt-sm">
                      <div class="row q-col-gutter-sm">
                        <div class="col-2">Fornecedor:</div>
                        <div class="col-6">
                          <q-input
                            dense
                            v-model="recebimento.nomeFornecedor"
                            placeholder="Informe o fornecedor"
                            @keyup.enter="openModalFornecedor"
                            :rules="[(val) => !!val || 'Por favor, informe o fornecedor']"
                          >
                            <template v-slot:append>
                              <q-btn dense flat icon="search" @click="openModalFornecedor" />
                            </template>
                          </q-input>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <!-- Cabeçalho do formulário - Modo Visualização -->
            <div class="col-12" v-else>
              <div class="row q-col-gutter-md">
                <div class="col-12">
                  <div class="row q-col-gutter-sm">
                    <!-- Linha 1 - ID e NF -->
                    <div class="col-3">
                      <q-input dense label="NF" v-model="recebimento.nf" disable class="q-mb-sm" />
                    </div>
                    <div class="col-3">
                      <q-input dense label="Data Recebimento" v-model="recebimento.data_recebimento" disable class="q-mb-sm" />
                    </div>
                    <!-- Linha 2 - Fornecedor e Valor Total -->
                    <div class="col-12">
                      <q-input dense label="Fornecedor" v-model="recebimento.fornecedor" disable class="q-mb-sm" />
                    </div>

                    <div class="col-3">
                      <q-input dense label="Total" v-model="recebimento.vr_total" prefix="R$" disable class="q-mb-sm" />
                    </div>

                    <!-- Linha 3 - Status -->
                    <div class="col-3">
                      <q-input dense label="Status" v-model="recebimento.status" disable class="q-mb-sm" />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Tabela de produtos -->
            <div class="col-12 q-mt-md" style="border-bottom: 1px solid grey">
              <q-table
                flat
                dense
                :rows="recebimento.produtos || []"
                :columns="columns"
                row-key="id_produto"
                :rows-per-page-options="[0]"
                hide-bottom
                style="height: 230px"
                class="q-mb-md"
              >
                <template #body="props">
                  <q-tr :props="props" @mouseover="showIcon(props.row, true)" @mouseleave="showIcon(props.row, false)" @click="showImage(props.row)">
                    <q-td class="col-delete">
                      <q-icon
                        name="o_close"
                        class="cursor-pointer"
                        @click.stop="removerProduto1(props.rowIndex)"
                        :color="props.row.isIconVisible ? 'secondary' : 'white'"
                        size="16px"
                        v-if="props.row.isIconVisible"
                      />
                    </q-td>
                    <q-td class="text-center">{{ props.rowIndex + 1 }}</q-td>
                    <q-td class="text-center">{{ props.row.id_produto }}</q-td>
                    <q-td class="text-left">{{ props.row.descricao }}</q-td>
                    <q-td class="text-left">
                      <q-input
                        :disable="edicao"
                        v-model="props.row.qtde"
                        type="number"
                        min="1"
                        dense
                        style="max-width: 40px"
                        @update:model-value="updateQtde(props.row)"
                      />
                    </q-td>
                    <q-td class="text-right">
                      {{ formatarMoeda(props.row.custo) }}
                    </q-td>
                    <q-td class="text-right">
                      {{ formatarMoeda(props.row.vl_total) }}
                    </q-td>
                  </q-tr>
                </template>

                <template #bottom-row>
                  <q-tr>
                    <q-td :colspan="columns.length">
                      <q-input
                        ref="inputQtde"
                        v-model="filtroProduto"
                        placeholder="+ Adicionar produto"
                        dense
                        @keyup.enter="handleProductSearch"
                        @dblclick="openModalProduto"
                      />
                    </q-td>
                  </q-tr>
                </template>
              </q-table>
            </div>
            <div class="col-12 text-right">
              Valor total:
              <strong>{{ formatarMoeda(recebimento.vr_total) }}</strong>
            </div>

            <!-- Seção da Imagem e Dados do Produto - Melhorada -->
            <div class="col-12 q-mt-md">
              <div class="row q-col-gutter-lg">
                <!-- Container da Imagem -->
                <div class="col-3">
                  <div class="column items-center q-pa-sm" style="border: 1px solid #e0e0e0; border-radius: 8px; height: 100%">
                    <div class="text-caption text-weight-medium q-mb-sm">Pré-visualização</div>
                    <div
                      class="relative-position"
                      style="width: 100%; max-width: 120px; max-height: 100%; display: flex; justify-content: center; align-items: center"
                    >
                      <img
                        v-if="!image"
                        src="@/assets/camera.png"
                        class="cursor-pointer"
                        style="width: 100%; height: auto; max-height: 120px; border-radius: 4px; object-fit: contain"
                      />
                      <img
                        v-else
                        :src="loadImg(image)"
                        class="cursor-pointer"
                        style="
                          width: 100%;
                          height: auto;
                          max-height: 120px;
                          border-radius: 4px;
                          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
                          object-fit: contain;
                        "
                        @mouseover="showZoomedImage = true"
                        @mouseleave="showZoomedImage = false"
                        @error="handleImageError"
                      />

                      <!-- Overlay de Zoom -->
                      <div
                        v-if="showZoomedImage && image"
                        style="position: absolute; top: -100px; left: 150px; z-index: 1000; border: 1px solid #5c636a; background-color: #ffffff"
                      >
                        <img :src="loadImg(image)" style="max-height: 300px" @error="handleImageError" />
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Dados do Produto -->
                <div class="col-9" v-if="recebimento.produtos && recebimento.produtos.length">
                  <div class="column q-pa-md bg-grey-2 rounded-borders" style="gap: 8px; min-height: 180px">
                    <div class="text-h6 text-weight-medium">Detalhes do Produto</div>

                    <div class="row items-center q-gutter-x-md">
                      <div class="col-12 text-body1 text-weight-medium">
                        {{ selected?.descricao || "Nenhum produto selecionado" }}
                      </div>
                    </div>

                    <div class="row items-center q-gutter-x-md">
                      <div class="text-caption text-grey-7" style="min-width: 80px">Código:</div>
                      <div class="text-body2">
                        {{ selected?.id_produto || "--" }}
                      </div>
                    </div>

                    <div class="row items-center q-gutter-x-md">
                      <div class="text-caption text-grey-7" style="min-width: 80px">Custo:</div>
                      <div class="text-body2">{{ formatarMoeda(selected?.custo) || "--" }}</div>
                    </div>

                    <div class="row items-center q-gutter-x-md">
                      <div class="text-caption text-grey-7" style="min-width: 80px">Quantidade:</div>
                      <div class="text-body2">
                        {{ selected?.qtde || "--" }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Cancelar" color="primary" @click="emitirFechar" />
          <q-btn
            :disable="recebimento.status == 'cancelado'"
            flat
            :label="isEditando ? 'Cancelar Recebimento' : 'Finalizar Recebimento'"
            color="secondary"
            @click="isEditando ? cancelarRecebimento() : emitirSalvar()"
          />
        </q-card-actions>
      </q-form>

      <!-- Modal Adicionar Produto -->
      <q-dialog v-model="showModalProduto" persistent>
        <q-card style="width: 80vw; max-width: 800px">
          <q-card-section class="text-h6">Adicionar produto</q-card-section>
          <q-card-section>
            <div class="row q-col-gutter-md">
              <div class="col-2">
                <span class="text-caption">Descrição:</span>
              </div>
              <div class="col-9">
                <q-input v-model="newproduto_descricao" placeholder="Informe a descrição do produto" type="textarea" rows="8" />
              </div>
            </div>
            <div class="row q-col-gutter-md q-mt-sm">
              <div class="col-2"><span class="text-caption">Custo:</span></div>
              <div class="col-4">
                <q-input dense v-model="newproduto_custo" placeholder="" />
              </div>
            </div>
          </q-card-section>
          <q-card-actions align="right">
            <q-btn flat label="Salvar" color="primary" @click="adicionarProduto" />
          </q-card-actions>
        </q-card>
      </q-dialog>

      <!-- Modal para Importação de NF-e -->
      <ImportNFeDialog :isOpen="modalImportacaoAberto" @close="modalImportacaoAberto = false" @importar="handleImportarNFe" />

      <q-dialog v-model="modalFornecedorAberto" persistent>
        <q-card style="width: 80vw; max-width: 800px">
          <q-card-section class="text-h6">Seleção de Fornecedor</q-card-section>
          <q-card-section>
            <!-- Campo de pesquisa com filtro em tempo real -->
            <q-input v-model="filtroFornecedor" label="Pesquisar fornecedor" filled @update:model-value="filtrarFornecedores" />

            <!-- Tabela de fornecedores com duplo clique -->
            <q-table :rows="fornecedoresFiltrados" :columns="colunasFornecedores" row-key="id" flat dense hide-bottom class="q-mt-md">
              <template #body="props">
                <q-tr :props="props" @dblclick="selecionarFornecedor(props.row)" class="cursor-pointer">
                  <q-td v-for="col in props.cols" :key="col.name" :props="props">
                    {{ col.value }}
                  </q-td>
                </q-tr>
              </template>
            </q-table>
          </q-card-section>
          <q-card-actions align="right">
            <q-btn flat label="Cancelar" v-close-popup />
          </q-card-actions>
        </q-card>
      </q-dialog>

      <!-- Modal Seleção de Produto -->
      <q-dialog v-model="modalProdutoSelecaoAberto" persistent>
        <q-card style="width: 80vw; max-width: 800px">
          <q-card-section class="text-h6">Seleção de Produto</q-card-section>
          <q-card-section>
            <q-input v-model="filtroProdutoSelecao" label="Pesquisar produto" filled @keyup.enter="handleProductSearch" />

            <q-table :rows="produtosFiltrados" :columns="colunasProdutos" row-key="id" flat dense hide-bottom class="q-mt-md">
              <template #body="props">
                <q-tr :props="props" @dblclick="selecionarProduto(props.row)" class="cursor-pointer">
                  <q-td v-for="col in props.cols" :key="col.name" :props="props">
                    {{ col.value }}
                  </q-td>
                </q-tr>
              </template>
            </q-table>
          </q-card-section>

          <q-card-actions align="right">
            <q-btn flat label="Cancelar" v-close-popup />
          </q-card-actions>
        </q-card>
      </q-dialog>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";
import { useApiRequest } from "@/composables/useApiRequest";
import { baseApiUrl } from "@/global";
import eventBus from "@/eventBus";
import cameraImage from "@/assets/camera.png";
import ImportNFeDialog from "./ImportNFeDialog.vue";
import { useQuasar } from "quasar";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

const arquivoNFe = ref(null);
const modalImportacaoAberto = ref(false);
const nfePreview = ref(null);
const selected = ref(null);
const edicao = ref(false);

const recebimentoForm = ref(null);

// Colunas para a prévia dos itens da NF-e
const colunasPreviewNFe = [
  { name: "codigo", align: "left", label: "Código", field: "codigo" },
  { name: "descricao", align: "left", label: "Descrição", field: "descricao" },
  { name: "qtde", align: "right", label: "Qtde", field: "qtde" },
  { name: "valorUnitario", align: "right", label: "Valor Unit.", field: "valorUnitario", format: (val) => formatarMoeda(val) },
  { name: "valorTotal", align: "right", label: "Valor Total", field: "valorTotal", format: (val) => formatarMoeda(val) },
];

// Função para abrir o modal de importação
function abrirModalImportacao() {
  arquivoNFe.value = null;
  nfePreview.value = null;
  modalImportacaoAberto.value = true;
}

function handleImportarNFe(dadosImportados) {
  recebimento.value.nf = dadosImportados.numero;
  recebimento.value.id_fornecedor = dadosImportados.idFornecedor;
  recebimento.value.nomeFornecedor = dadosImportados.fornecedor;
  recebimento.value.produtos = dadosImportados.itens.map((item) => ({
    id_produto: item.codigo,
    descricao: item.descricao,
    qtde: item.qtde,
    custo: item.valorUnitario,
    vl_total: item.valorTotal,
  }));
  recebimento.value.vr_total = dadosImportados.itens.reduce((acc, item) => acc + item.valorTotal, 0);
}

// Props e Emits
const props = defineProps({
  modelValue: Boolean,
  registro: Object,
});

const emit = defineEmits(["salvarRegistro", "fecharDialogo", "reloadListagem", "update:modelValue"]);

// Refs e Estados
const modelValue = computed({
  get: () => props.modelValue,
  set: (val) => {
    emit("update:modelValue", val);
    if (!val) emit("fecharDialogo");
  },
});

const isEditando = computed(() => recebimento.value && recebimento.value.id);

const recebimento = ref({
  status: "aberta",
  nf: "",
  id: null,
  tipo: "pedido",
  nomeFornecedor: "",
  nome_fornecedor: "",
  id_fornecedor: "",
  vr_total: "",
  produtos: [],
  data_entrega: "",
  data_recebimento: "",
  peso: "",
  produto: {
    id_recebimento: "",
    image: null,
    id: "",
    id_produto: null,
    qtde: 1,
    descricao: "",
    custo: "",
    vl_total: "",
    isIconVisible: false,
  },
});

const handleImageError = (event) => {
  event.target.src = cameraImage; // Usa a imagem de fallback importada
};

const loadProdutosRecebimento = async (id_recebimento) => {
  edicao.value = true;
  try {
    const apiURL = `/api/recebimento/getprodrecebto/${id_recebimento}`;
    const response = await apiRequest(apiURL);

    if (response) {
      recebimento.value.produtos = response.map((produto) => ({
        ...produto, // Mantém todas as propriedades originais do produto
        descricao: produto.nome, // Define descricao com o valor de nome
        custo: produto.vl_unitario,
      }));

      // Atualiza o valor total
      recebimento.value.vr_total = response.reduce((total, produto) => total + (produto.vl_total || 0), 0);
    }
  } catch (error) {
    console.error("Erro ao carregar produtos do recebimento:", error);
  }
};

watch(
  () => props.registro,
  async (novoValor) => {
    if (novoValor) {
      recebimento.value = {
        ...novoValor,
        produtos: Array.isArray(novoValor.produtos) ? novoValor.produtos : [],
      };
      if (novoValor.id) {
        await loadProdutosRecebimento(novoValor.id);
      } else {
        edicao.value = false;
      }
    } else {
      // Resetar para o estado inicial se novoValor for null/undefined
      recebimento.value = {
        status: "aberta",
        nf: "",
        id: null,
        tipo: "pedido",
        nomeFornecedor: "",
        nome_fornecedor: "",
        id_fornecedor: "",
        vr_total: "",
        produtos: [],
        data_entrega: "",
        peso: "",
        produto: {
          id_recebimento: "",
          image: null,
          id: "",
          id_produto: null,
          qtde: 1,
          descricao: "",
          nome: "",
          custo: "",
          vl_total: "",
          vl_unitario: "",
          isIconVisible: false,
        },
      };
    }
  },
  { immediate: true }
);

const showZoomedImage = ref(false);
const image = ref("");
const filtroProduto = ref("");
const showModalProduto = ref(false);
const modalFornecedorAberto = ref(false);
const modalProdutoSelecaoAberto = ref(false);
const filtroFornecedor = ref("");
const filtroProdutoSelecao = ref("");
const newproduto_custo = ref("");
const newproduto_descricao = ref("");

// Novo método para lidar com a busca
const handleProductSearch = () => {
  const input = filtroProduto.value.trim();
  if (!input) return;

  // Verifica se é numérico
  if (!isNaN(input)) {
    const product = findProductByCode(Number(input));
    if (product) {
      selecionarProduto(product);
    } else {
      alert(`Produto ${input} não encontrado`);
    }
  } else {
    // Atualiza o filtro e abre o modal
    filtroProdutoSelecao.value = input;
    openModalProduto();
  }
  filtroProduto.value = ""; // Limpa o campo após a busca
};

// Função para encontrar produto pelo código
const findProductByCode = (code) => {
  return produtosFiltrados.value.find((p) => p.id === code);
};

const fornecedores = ref([]);

// Dados Mockados
const fornecedoresFiltrados = ref([]);
const filtrarFornecedores = () => {
  if (!filtroFornecedor.value) {
    fornecedoresFiltrados.value = fornecedores.value;
  } else {
    fornecedoresFiltrados.value = fornecedores.value.filter((fornecedor) =>
      fornecedor.razao_social.toLowerCase().includes(filtroFornecedor.value.toLowerCase())
    );
  }
};

const produtos = ref([]);

const produtosFiltrados = computed(() => {
  if (!filtroProdutoSelecao.value) return produtos.value;

  return produtos.value.filter((p) => {
    const searchTerm = filtroProdutoSelecao.value.toLowerCase();
    return p.descricao.toLowerCase().includes(searchTerm) || p.id.toString().includes(filtroProdutoSelecao.value);
  });
});

// Colunas das Tabelas
const columns = [
  { name: "delete", label: "", align: "center" },
  { name: "item", label: "Item", align: "center" },
  { name: "codigo", label: "Código", field: "id_produto", align: "center" },
  { name: "produto", label: "Produto", field: "descricao", align: "left" },
  { name: "qtde", label: "Qtde", align: "center" },
  {
    name: "custo",
    label: "Custo Unitário",
    field: "custo",
    align: "right",
  },
  { name: "total", label: "Valor Total", field: "vl_total", align: "right" },
];

const colunasFornecedores = [
  { name: "id", label: "Código", field: "id", align: "center" },
  {
    name: "razao_social",
    label: "Razão Social",
    field: "razao_social",
    align: "left",
  },
];

const colunasProdutos = [
  { name: "id", label: "Código", field: "id", align: "center" },
  { name: "descricao", label: "Descrição", field: "descricao", align: "left" },
  { name: "custo", label: "Custo", field: "custo", align: "right" },
];

const loadProdutos = async () => {
  try {
    const apiURL = "/api/produtos?semPaginacao=true";
    const response = await apiRequest(apiURL);
    if (response) {
      produtos.value = response.objeto;
    }
  } catch (error) {
    console.error("Erro ao carregar os grupos do menu:", error);
  }
};

const loadFornecedores = async () => {
  try {
    const apiURL = "/api/fornecedor?semPaginacao=true";
    const response = await apiRequest(apiURL);
    if (response) {
      fornecedores.value = response.objeto || [];
    }
  } catch (error) {
    console.error("Erro ao carregar fornecedores:", error);
  }
};

// Métodos
function formatarMoeda(valor) {
  if (typeof valor !== "number") return "";
  return valor.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function loadImg(img) {
  return img ? `${baseApiUrl}/api/imagens/${img}` : "";
}

function showIcon(item, isVisible) {
  item.isIconVisible = isVisible;
}

function showImage(obj) {
  image.value = `${obj.id_produto}.jpg`;
}

function openModalFornecedor() {
  modalFornecedorAberto.value = true;
}

function openModalProduto() {
  modalProdutoSelecaoAberto.value = true;
}

function selecionarFornecedor(fornecedor) {
  recebimento.value.nomeFornecedor = fornecedor.razao_social;
  recebimento.value.id_fornecedor = fornecedor.id;
  modalFornecedorAberto.value = false;
}

function selecionarProduto(produto) {
  const novoProduto = {
    ...recebimento.value.produto,
    id_produto: produto.id,
    descricao: produto.descricao,
    custo: produto.custo,
    qtde: 1, // Garanta a quantidade inicial
    vl_total: produto.custo * 1,
  };

  image.value = produto.image;

  // Garanta que produtos seja um array
  if (!Array.isArray(recebimento.value.produtos)) {
    recebimento.value.produtos = [];
  }

  if (!produtoExists(produto.id)) {
    recebimento.value.produtos.push({ ...novoProduto });
  } else {
    const index = recebimento.value.produtos.findIndex((p) => p.id_produto === produto.id);
    recebimento.value.produtos[index].qtde++;
    recebimento.value.produtos[index].vl_total = recebimento.value.produtos[index].qtde * produto.custo;
  }

  totalPrecoRecebimento();
  modalProdutoSelecaoAberto.value = false;
}

function produtoExists(id) {
  return (recebimento.value.produtos || []).some((p) => p.id_produto === id);
}

function updateQtde(item) {
  item.vl_total = item.qtde * item.custo;
  totalPrecoRecebimento();
}

function removerProduto1(index) {
  recebimento.value.produtos.splice(index, 1);
  totalPrecoRecebimento();
}

function totalPrecoRecebimento() {
  recebimento.value.vr_total = recebimento.value.produtos.reduce((total, produto) => total + (produto.vl_total || 0), 0);
}

async function emitirSalvar() {
  const isValid = await recebimentoForm.value.validate();

  if (!isValid) {
    // Se o formulário não for válido, não prossegue
    return;
  }

  // Verifica explicitamente se o fornecedor foi selecionado
  if (!recebimento.value.nomeFornecedor || !recebimento.value.id_fornecedor) {
    eventBus.emit("showMessage", {
      message: "Por favor, selecione um fornecedor válido",
      color: "warning",
    });
    return;
  }

  // Verifica se há produtos no recebimento
  if (!recebimento.value.produtos.length) {
    $q.notify({
      message: "Informe os produtos do recebimento",
      color: "negative",
      position: "center",
      timeout: 2000,
    });
    return;
  }
  recebimento.value.status = "finalizado";
  try {
    emit("salvarRegistro", recebimento.value);
  } catch (error) {
    console.error("Erro ao salvar:", error);
  }
}

async function finalizarRecebimento(idRecebimento) {
  try {
    for (const produto of recebimento.value.produtos) {
      //Adicionando recebimento_produtos
      produto.nome = produto.descricao;
      produto.vl_unitario = produto.custo;
      await apiRequest("/api/recebimentoprodutos", "POST", {
        ...produto,
        id_recebimento: idRecebimento,
      });

      await apiRequest("/api/estoque", "POST", {
        id_recebimento: idRecebimento,
        id_produto: produto.id_produto,
        qtde: produto.qtde,
        tipo: 1,
        id_usuario: sessionStorage.getItem("user_id"),
      });
    }
  } catch (error) {
    console.error("Erro ao salvar produtos:", error);
    throw error;
  }
}

async function cancelarRecebimento() {
  estornarEstoque();
  recebimento.value.status = "cancelado";
  recebimento.value.data_recebimento = null;
  const apiURL = `/api/recebimento/${recebimento.value.id}`;
  await apiRequest(apiURL, "PUT", recebimento.value);
  emit("fecharDialogo");
  emit("reloadListagem");
}

async function estornarEstoque() {
  try {
    for (const produto of recebimento.value.produtos) {
      await apiRequest("/api/estoque", "POST", {
        id_recebimento: recebimento.value.id,
        id_produto: produto.id_produto,
        qtde: produto.qtde,
        tipo: 6,
        id_usuario: sessionStorage.getItem("user_id"),
      });
    }
  } catch (error) {
    console.error("Erro ao salvar produtos:", error);
    throw error;
  }
}

onMounted(async () => {
  await loadProdutos();
  await loadFornecedores();
  fornecedoresFiltrados.value = fornecedores.value;
  eventBus.on("registro-salvo", handleRegistroSalvo);
  eventBus.emit("update:header", {
    breadcrumbs: [
      { icon: "home", label: "Home", to: "/" },
      { label: "Recebimento de Mercadoria", class: "secondary" },
    ],
    title: "Recebimento de Mercadoria",
    icon: "o_local_shipping",
  });
});

onBeforeUnmount(() => {
  eventBus.off("registro-salvo", handleRegistroSalvo);
});

function handleRegistroSalvo(result) {
  if (result && result.id) {
    finalizarRecebimento(result.id);
  }
}

function emitirFechar() {
  image.value = "";
  emit("fecharDialogo");
}

function adicionarProduto() {
  if (newproduto_descricao.value && newproduto_custo.value) {
    const novoProduto = {
      id: Date.now(),
      descricao: newproduto_descricao.value,
      custo: parseFloat(newproduto_custo.value),
    };

    produtosFiltrados.value.push(novoProduto);
    selecionarProduto(novoProduto);

    newproduto_descricao.value = "";
    newproduto_custo.value = "";
    showModalProduto.value = false;
  }
}
</script>
