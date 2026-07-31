<template>
  <q-dialog :model-value="props.modelValue || props.isOpen" @update:model-value="handleDialogClose" persistent>
    <q-card style="width: 80vw; max-width: 900px">
      <q-card-section class="row items-center q-pb-none">
        <div class="text-h6">
          <q-icon name="receipt_long" color="primary" size="sm" class="q-mr-sm" />
          Importar Nota Fiscal Eletrônica
        </div>
        <q-space />
        <q-btn icon="close" flat round dense v-close-popup />
      </q-card-section>

      <q-card-section>
        <div class="row q-col-gutter-md">
          <div class="col-12">
            <q-file
              v-model="arquivoNFe"
              label="Selecione o arquivo XML da NF-e"
              accept=".xml"
              outlined
              bottom-slots
              counter
              @update:model-value="verificarArquivoNFe"
            >
              <template v-slot:prepend>
                <q-icon name="attach_file" />
              </template>
              <template v-slot:append>
                <q-icon
                  name="close"
                  @click.stop="
                    arquivoNFe = null;
                    nfePreview = null;
                  "
                  class="cursor-pointer"
                  v-if="arquivoNFe"
                />
              </template>
              <template v-slot:hint>Formatos suportados: XML da NF-e</template>
            </q-file>
          </div>
        </div>

        <!-- Preview da NF-e -->
        <div v-if="nfePreview" class="q-mt-lg">
          <!-- Alerta de NF já existente -->
          <q-banner v-if="nfExistente" class="text-white bg-negative q-mb-md" rounded>
            <template v-slot:avatar>
              <q-icon name="error" color="white" />
            </template>
            Nota fiscal já foi importada anteriormente!
          </q-banner>

          <!-- Dados da Nota -->
          <q-card flat bordered class="q-pa-md bg-grey-1">
            <div class="row items-center q-mb-md">
              <q-icon name="description" color="primary" size="sm" class="q-mr-sm" />
              <div class="text-subtitle1 text-weight-medium">Dados da Nota Fiscal</div>
            </div>

            <div class="row q-col-gutter-md">
              <div class="col-xs-12 col-sm-6">
                <div class="text-caption text-grey-7">Número NF:</div>
                <div class="text-body1 text-weight-medium">{{ nfePreview.numero }}</div>
              </div>
              <div class="col-xs-12 col-sm-6">
                <div class="text-caption text-grey-7">Data Emissão:</div>
                <div class="text-body1">{{ formatarDataBrasileira(nfePreview.dataEmissao) }}</div>
              </div>
            </div>

            <q-separator class="q-my-md" />

            <div class="row q-col-gutter-md">
              <div class="col-xs-12 col-sm-8">
                <div class="text-caption text-grey-7">
                  Fornecedor
                  <q-chip :color="fornecedorCadastrado ? 'positive' : 'warning'" text-color="white" size="sm" dense>
                    {{ fornecedorCadastrado ? "Cadastrado" : "Não cadastrado" }}
                  </q-chip>
                </div>
                <div class="text-body1" :class="{ 'text-negative': !fornecedorCadastrado }">
                  {{ nfePreview.fornecedor }}
                </div>
                <q-btn
                  v-if="!fornecedorCadastrado"
                  label="Cadastrar Fornecedor"
                  color="primary"
                  size="sm"
                  dense
                  class="q-mt-sm"
                  @click="cadastrarFornecedor"
                  :loading="cadastrandoFornecedor"
                />
              </div>
              <div class="col-xs-12 col-sm-4">
                <div class="text-caption text-grey-7">CNPJ:</div>
                <div class="text-body1">{{ formatarCNPJ(nfePreview.cnpj) }}</div>
              </div>
            </div>
          </q-card>

          <!-- Tabela de Produtos -->
          <div class="q-mt-md">
            <div class="row items-center q-mb-sm">
              <q-icon name="inventory_2" color="primary" size="sm" class="q-mr-sm" />
              <div class="text-subtitle1 text-weight-medium">
                Produtos da Nota
                <q-badge color="grey-7" class="q-ml-sm">{{ filteredItens.length }} {{ filteredItens.length === 1 ? "item" : "itens" }}</q-badge>
              </div>
            </div>

            <q-table flat dense :rows="filteredItens" :columns="colunasProdutos" row-key="codigo" :pagination="{ rowsPerPage: 10 }" class="q-mt-sm">
              <template v-slot:body="props">
                <q-tr :props="props">
                  <q-td key="codigo_interno" :props="props">
                    <q-badge v-if="props.row.produtoId" color="positive" outline>ID: {{ props.row.produtoId }}</q-badge>
                    <span v-else class="text-grey-5">—</span>
                  </q-td>
                  <q-td key="codigo" :props="props">
                    {{ props.row.codigo }}
                  </q-td>
                  <q-td key="descricaoNFe" :props="props">
                    <div v-if="!props.row.produtoId" class="text-italic text-grey-7" style="text-decoration: line-through">
                      {{ props.row.descricaoNFe }}
                    </div>
                    <div v-else>
                      {{ props.row.descricaoNFe }}
                    </div>
                    <div v-if="props.row.produtoId && props.row.descricaoInterna" class="text-caption text-primary">
                      <q-icon name="check_circle" size="xs" />
                      {{ props.row.descricaoInterna }}
                    </div>
                    <div v-if="!props.row.produtoId" class="text-negative text-caption">
                      <q-icon name="warning" size="xs" />
                      Produto não cadastrado
                    </div>
                  </q-td>
                  <q-td key="qtde" :props="props" class="text-center">
                    {{ props.row.qtde }}
                  </q-td>
                  <q-td key="valorUnitario" :props="props" class="text-right">
                    {{ formatarMoeda(props.row.valorUnitario) }}
                  </q-td>
                  <q-td key="valorTotal" :props="props" class="text-right">
                    {{ formatarMoeda(props.row.valorTotal) }}
                  </q-td>
                  <q-td key="actions" :props="props">
                    <div class="row q-gutter-xs justify-end no-wrap">
                      <q-btn
                        v-if="!props.row.produtoId"
                        icon="add_circle"
                        color="primary"
                        size="sm"
                        dense
                        flat
                        round
                        @click="addProduto(props.row)"
                        :loading="cadastrandoProduto[props.row.codigo]"
                      >
                        <q-tooltip>Cadastrar produto</q-tooltip>
                      </q-btn>
                      <q-btn v-if="!props.row.produtoId" icon="delete" color="negative" size="sm" dense flat round @click="removerItem(props.row)">
                        <q-tooltip>Remover da lista</q-tooltip>
                      </q-btn>
                    </div>
                  </q-td>
                </q-tr>
              </template>

              <template v-slot:bottom-row>
                <q-tr class="bg-grey-2">
                  <q-td colspan="4" class="text-right text-weight-bold">Total Geral:</q-td>
                  <q-td></q-td>
                  <q-td class="text-right text-weight-bold">
                    {{ formatarMoeda(totalGeral) }}
                  </q-td>
                  <q-td></q-td>
                </q-tr>
              </template>
            </q-table>
          </div>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-actions align="right">
        <q-btn flat label="Cancelar" color="negative" @click="emitClose" />
        <q-btn flat label="Importar Dados" color="primary" @click="importarDadosNFe" :disable="!podeImportar" :loading="importando">
          <q-tooltip v-if="!podeImportar">
            {{ tooltipImportar }}
          </q-tooltip>
        </q-btn>
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed } from "vue";
import { useApiRequest } from "@/composables/useApiRequest";
import { useQuasar } from "quasar";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Props
const props = defineProps({
  isOpen: Boolean,
  modelValue: Boolean,
  registro: {
    type: Object,
    default: () => ({}),
  },
});

// Emits
const emit = defineEmits(["close", "importar", "update:modelValue"]);

// State
const arquivoNFe = ref(null);
const nfePreview = ref(null);
const filteredItens = ref([]);
const fornecedorCadastrado = ref(false);
const idFornecedor = ref(null);
const nfExistente = ref(false);
const cadastrandoFornecedor = ref(false);
const cadastrandoProduto = ref({});
const importando = ref(false);

// Computed
const podeImportar = computed(() => {
  if (!filteredItens.value || filteredItens.value.length === 0 || !fornecedorCadastrado.value) {
    return false;
  }
  if (nfExistente.value) {
    return false;
  }
  return filteredItens.value.every((item) => item.produtoId);
});

const tooltipImportar = computed(() => {
  if (nfExistente.value) return "Nota fiscal já foi importada";
  if (!fornecedorCadastrado.value) return "Cadastre o fornecedor primeiro";
  if (!filteredItens.value || filteredItens.value.length === 0) return "Nenhum item para importar";
  const naoRemovidos = filteredItens.value.filter((item) => !item.produtoId);
  if (naoRemovidos.length > 0) return `Cadastre ou remova ${naoRemovidos.length} produto(s) não cadastrado(s)`;
  return "";
});

const totalGeral = computed(() => {
  return filteredItens.value.reduce((total, item) => total + item.valorTotal, 0);
});

// Colunas da tabela
const colunasProdutos = [
  { name: "codigo_interno", label: "Cód. Interno", field: "codigo_interno", align: "center" },
  { name: "codigo", label: "Cód. Fornecedor", field: "codigo", align: "center" },
  { name: "descricaoNFe", label: "Descrição", field: "descricaoNFe", align: "left" },
  { name: "qtde", label: "Qtde", field: "qtde", align: "center" },
  { name: "valorUnitario", label: "Valor Unit.", field: "valorUnitario", align: "right" },
  { name: "valorTotal", label: "Valor Total", field: "valorTotal", align: "right" },
  { name: "actions", label: "Ações", field: "actions", align: "center" },
];

// Methods
function handleDialogClose(value) {
  if (!value) {
    emit("update:modelValue", false);
    emit("close");
  }
}

function emitClose() {
  emit("update:modelValue", false);
  emit("close");
}

function verificarArquivoNFe() {
  if (!arquivoNFe.value) {
    nfePreview.value = null;
    return;
  }

  const reader = new FileReader();
  reader.onload = async (e) => {
    try {
      const xmlContent = e.target.result;
      const parser = new DOMParser();
      const xmlDoc = parser.parseFromString(xmlContent, "text/xml");

      if (xmlDoc.getElementsByTagName("parsererror").length > 0) {
        $q.notify({
          color: "negative",
          message: "Erro ao processar arquivo XML",
          icon: "error",
        });
        return;
      }

      // Extraindo dados do XML
      const nfe = xmlDoc.getElementsByTagName("NFe")[0];
      const infNFe = nfe?.getElementsByTagName("infNFe")[0];
      const ide = infNFe?.getElementsByTagName("ide")[0];
      const emitente = infNFe?.getElementsByTagName("emit")[0];
      const itensXml = infNFe?.getElementsByTagName("det");

      const numero = ide?.getElementsByTagName("nNF")[0]?.textContent || "";
      const dataEmissao = ide?.getElementsByTagName("dhEmi")[0]?.textContent || "";
      const fornecedor = emitente?.getElementsByTagName("xNome")[0]?.textContent || "";
      const cnpj = emitente?.getElementsByTagName("CNPJ")[0]?.textContent || "";

      const itens = Array.from(itensXml || []).map((item, index) => {
        const prod = item?.getElementsByTagName("prod")[0];
        return {
          codigo: prod?.getElementsByTagName("cProd")[0]?.textContent || `ITEM${index + 1}`,
          descricaoNFe: prod?.getElementsByTagName("xProd")[0]?.textContent || "",
          qtde: parseFloat(prod?.getElementsByTagName("qCom")[0]?.textContent) || 0,
          valorUnitario: parseFloat(prod?.getElementsByTagName("vUnCom")[0]?.textContent) || 0,
          valorTotal: parseFloat(prod?.getElementsByTagName("vProd")[0]?.textContent) || 0,
          codigo_interno: null,
          descricaoInterna: null,
        };
      });

      console.log("Data extraída do XML:", dataEmissao);
      nfePreview.value = { numero, dataEmissao, fornecedor, cnpj, itens };
      await verificarFornecedor(cnpj, itens, numero);
    } catch (error) {
      console.error("Erro ao processar XML:", error);
      $q.notify({
        color: "negative",
        message: "Erro ao processar arquivo XML",
        icon: "error",
      });
    }
  };
  reader.readAsText(arquivoNFe.value);
}

async function verificarFornecedor(cnpj, itens, nf) {
  try {
    const response = await apiRequest(`/api/recebimentos/verificar-nfe?numeroNf=${nf}&cnpj=${cnpj}`, "GET");

    nfExistente.value = !!response.exists;
    fornecedorCadastrado.value = response.success;
    idFornecedor.value = response.idFornecedor || null;

    if (!response.success) {
      filteredItens.value = itens.map((item) => ({
        ...item,
        codigo_interno: null,
      }));
      return;
    }

    const produtosResponse = await apiRequest(`/api/produtos/lookup/by-fornecedor?cnpj=${cnpj}`, "GET");
    const produtosFornecedor = produtosResponse || [];

    filteredItens.value = itens.map((item) => {
      // Buscar produto pelo codigoFornecedor que agora é retornado corretamente
      const produtoCadastrado = produtosFornecedor.find((prod) => prod.codigoFornecedor === item.codigo);

      return {
        ...item,
        codigo_interno: produtoCadastrado?.id || null, // Usar o ID como código interno
        descricaoInterna: produtoCadastrado?.descricao || null,
        produtoId: produtoCadastrado?.id || null, // Guardar o ID do produto para referência
      };
    });
  } catch (error) {
    console.error("Erro ao verificar fornecedor e NF:", error);
    fornecedorCadastrado.value = false;
    nfExistente.value = false;
    idFornecedor.value = null;
  }
}

async function cadastrarFornecedor() {
  if (!nfePreview.value || !nfePreview.value.cnpj || !nfePreview.value.fornecedor) {
    return;
  }

  cadastrandoFornecedor.value = true;
  try {
    const response = await apiRequest("/api/fornecedores", "POST", {
      cnpj: nfePreview.value.cnpj,
      razaoSocial: nfePreview.value.fornecedor,
    });

    idFornecedor.value = response.id;
    fornecedorCadastrado.value = true;

    $q.notify({
      color: "positive",
      message: "Fornecedor cadastrado com sucesso!",
      icon: "check_circle",
    });
  } catch (error) {
    console.error("Erro ao cadastrar fornecedor:", error);
    $q.notify({
      color: "negative",
      message: "Erro ao cadastrar fornecedor",
      icon: "error",
    });
  } finally {
    cadastrandoFornecedor.value = false;
  }
}

async function addProduto(produto) {
  cadastrandoProduto.value[produto.codigo] = true;
  try {
    const response = await apiRequest("/api/produtos", "POST", {
      nome: produto.descricaoNFe,
      tipoPrecificacao: "SIMPLES",
      ativo: true,
      codigoFornecedor: produto.codigo,
      fornecedorId: Number(idFornecedor.value),
      precificacao: {
        precoCusto: 0.0,
        precoVenda: 0.0,
        tipoCalculoMargem: "SOBRE_CUSTO",
      },
    });

    const index = filteredItens.value.findIndex((item) => item.codigo === produto.codigo);
    if (index !== -1) {
      filteredItens.value[index].codigo_interno = response.id; // Usar o ID retornado
      filteredItens.value[index].produtoId = response.id;
      filteredItens.value[index].descricaoInterna = response.nome || produto.descricaoNFe;
    }

    $q.notify({
      color: "positive",
      message: "Produto cadastrado com sucesso!",
      icon: "check_circle",
    });
  } catch (error) {
    console.error("Erro ao cadastrar produto:", error);
    $q.notify({
      color: "negative",
      message: "Erro ao cadastrar produto",
      icon: "error",
    });
  } finally {
    delete cadastrandoProduto.value[produto.codigo];
  }
}

function removerItem(produto) {
  const index = filteredItens.value.findIndex((item) => item.codigo === produto.codigo);
  if (index !== -1) {
    filteredItens.value.splice(index, 1);
    $q.notify({
      color: "info",
      message: "Item removido da lista",
      icon: "info",
      timeout: 1000,
    });
  }
}

async function importarDadosNFe() {
  if (!nfePreview.value) return;

  importando.value = true;
  try {
    const dadosImportacao = {
      numero: nfePreview.value.numero,
      fornecedor: nfePreview.value.fornecedor,
      idFornecedor: idFornecedor.value,
      dataEmissao: nfePreview.value.dataEmissao,
      itens: filteredItens.value
        .filter((item) => item.produtoId)
        .map((item) => ({
          codigo: item.produtoId, // Usar o ID do produto
          descricao: item.descricaoInterna || item.descricaoNFe,
          qtde: item.qtde,
          valorUnitario: item.valorUnitario,
          valorTotal: item.valorTotal,
          codigo_fornecedor: item.codigo || null,
        })),
    };

    // Emitir evento com dados formatados para o formulário dinâmico
    emit("importar", dadosImportacao);

    // Converter data para formato yyyy-MM-dd
    let dataFormatada = null;
    if (dadosImportacao.dataEmissao) {
      // Extrair apenas a parte da data (yyyy-MM-dd) do formato ISO
      dataFormatada = dadosImportacao.dataEmissao.split("T")[0];
    }

    // Emitir evento específico para o dialog dinâmico
    const dadosParaEnviar = {
      numeroNf: dadosImportacao.numero,
      fornecedorId: dadosImportacao.idFornecedor,
      dataEmissaoNf: dataFormatada,
      itens: dadosImportacao.itens.map((item) => ({
        produtoId: item.codigo, // item.codigo já contém o produtoId
        quantidade: item.qtde,
        custoUnitario: item.valorUnitario,
        valorTotal: item.valorTotal, // IMPORTANTE: incluir o valorTotal
        lote: "",
        dataValidade: null,
      })),
    };

    console.log("=== DEBUG ImportNFeDialog ===");
    console.log("idFornecedor.value:", idFornecedor.value);
    console.log("dadosImportacao.idFornecedor:", dadosImportacao.idFornecedor);
    console.log("dadosImportacao.dataEmissao (original):", dadosImportacao.dataEmissao);
    console.log("dataEmissaoNf (formatada):", dataFormatada);
    console.log("Dados sendo enviados:", dadosParaEnviar);
    console.log("=============================");

    emit("dialogoConcluido", dadosParaEnviar);

    $q.notify({
      color: "positive",
      message: "Dados importados com sucesso!",
      icon: "check_circle",
    });

    emitClose();
  } catch (error) {
    console.error("Erro ao importar dados:", error);
    $q.notify({
      color: "negative",
      message: "Erro ao importar dados",
      icon: "error",
    });
  } finally {
    importando.value = false;
  }
}

// Utility functions
function formatarDataBrasileira(dataStr) {
  if (!dataStr) return "";
  try {
    const data = new Date(dataStr);
    return data.toLocaleDateString("pt-BR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    });
  } catch (e) {
    return dataStr;
  }
}

function formatarMoeda(valor) {
  return valor.toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });
}

function formatarCNPJ(cnpj) {
  if (!cnpj) return "";
  return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}
</script>

<style scoped>
.cursor-pointer {
  cursor: pointer;
}
</style>
