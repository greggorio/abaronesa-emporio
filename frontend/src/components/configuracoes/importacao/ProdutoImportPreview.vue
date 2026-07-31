<!--
  Componente de Preview de Importação de Produtos

  Instruções de uso:
  1. Acesse o Painel de Controle no menu de configurações
  2. Vá até a aba "Desenvolvimento"
  3. Clique no card "Importação de Produtos"
  4. Selecione um arquivo XLS ou XLSX contendo produtos
  5. O sistema gerará um preview com:
     - Total de linhas
     - Linhas válidas
     - Linhas duplicadas
     - Linhas inválidas
     - Tabela com até 50 primeiros produtos válidos
  6. O botão "Confirmar importação" está desabilitado nesta etapa (em breve)

  Nota: O componente faz upload do arquivo para o backend e exibe o preview retornado
  pela API em /api/produtos/import/preview, conforme especificação do endpoint.
-->
<template>
  <q-card class="q-ma-md" style="min-width: 800px;">
    <q-card-section class="bg-primary text-white">
      <div class="text-h6">
        <q-icon name="file_upload" color="white" size="sm" class="q-mr-sm" />
        Importação de Produtos - Preview
      </div>
    </q-card-section>

    <q-card-section>
      <div class="row q-col-gutter-md">
        <div class="col-12">
          <q-file
            v-model="arquivoImportacao"
            label="Selecione o arquivo XLS/XLSX"
            accept=".xls,.xlsx"
            outlined
            bottom-slots
            counter
            @update:model-value="gerarPreview"
          >
            <template v-slot:prepend>
              <q-icon name="attach_file" />
            </template>
            <template v-slot:append>
              <q-icon
                name="close"
                @click.stop="limparArquivo"
                class="cursor-pointer"
                v-if="arquivoImportacao"
              />
            </template>
            <template v-slot:hint>Formatos suportados: XLS, XLSX</template>
          </q-file>
        </div>
      </div>

      <!-- Indicadores de status -->
      <div v-if="previewResponse" class="q-mt-lg">
        <div class="row q-col-gutter-md">
          <div class="col-xs-6 col-sm-3">
            <q-card flat bordered class="bg-blue-1 q-pa-md text-center">
              <div class="text-h5 text-weight-bold">{{ previewResponse.total }}</div>
              <div class="text-caption text-grey">Total de Linhas</div>
            </q-card>
          </div>
          <div class="col-xs-6 col-sm-3">
            <q-card flat bordered class="bg-green-1 q-pa-md text-center">
              <div class="text-h5 text-weight-bold">{{ previewResponse.validos }}</div>
              <div class="text-caption text-grey">Válidos</div>
            </q-card>
          </div>
          <div class="col-xs-6 col-sm-3">
            <q-card flat bordered class="bg-orange-1 q-pa-md text-center">
              <div class="text-h5 text-weight-bold">{{ previewResponse.duplicadosInternos }}</div>
              <div class="text-caption text-grey">Duplicados</div>
            </q-card>
          </div>
          <div class="col-xs-6 col-sm-3">
            <q-card flat bordered class="bg-red-1 q-pa-md text-center">
              <div class="text-h5 text-weight-bold">{{ previewResponse.invalidos }}</div>
              <div class="text-caption text-grey">Inválidos</div>
            </q-card>
          </div>
        </div>

        <!-- Mensagem de erro se houver -->
        <div v-if="previewResponse.exemploInvalido" class="q-mt-md">
          <q-banner class="bg-red-1 text-negative" rounded>
            <template v-slot:avatar>
              <q-icon name="error" color="negative" />
            </template>
            <strong>Exemplo de erro:</strong> Linha {{ previewResponse.exemploInvalido.linha }} - {{ previewResponse.exemploInvalido.mensagem }}
          </q-banner>
        </div>

        <!-- Painel de categorias detectadas -->
        <div v-if="previewResponse.categoriasDetectadas && previewResponse.categoriasDetectadas.length > 0" class="q-mt-md">
          <div class="text-h6 q-mb-md">Categorias Detectadas</div>
          <q-card flat bordered class="q-pa-md">
            <div class="row q-col-gutter-md">
              <div
                v-for="categoria in previewResponse.categoriasDetectadas"
                :key="categoria.nome"
                :class="categoria.existe ? 'col-lg-3 col-md-4 col-sm-6 col-xs-12' : 'col-lg-3 col-md-4 col-sm-6 col-xs-12'"
              >
                <q-card
                  :class="categoria.existe ? 'bg-green-1' : 'bg-warning'"
                  flat
                  bordered
                  class="q-pa-md text-center"
                >
                  <div class="text-subtitle2 text-weight-bold">{{ categoria.nome }}</div>
                  <div class="text-caption">
                    <span :class="categoria.existe ? 'text-positive' : 'text-negative'">
                      {{ categoria.existe ? 'Encontrada' : 'Não encontrada' }}
                    </span>
                  </div>
                  <div class="text-caption text-grey">
                    ID: {{ categoria.categoriaId || '—' }}
                  </div>
                  <div class="text-caption text-grey">
                    Itens: {{ categoria.contagem }}
                  </div>
                </q-card>
              </div>
            </div>
          </q-card>
        </div>

        <!-- Tabela de preview -->
        <div v-if="previewResponse.linhasValidas && previewResponse.linhasValidas.length > 0" class="q-mt-md">
          <div class="text-h6 q-mb-md">Preview dos Produtos (até 50 primeiros)</div>
          <q-table
            flat
            dense
            :rows="previewResponse.linhasValidas"
            :columns="colunasTabela"
            row-key="codigoInterno"
            v-model:pagination="paginacaoTabela"
            :rows-per-page-options="[50]"
            class="q-mt-sm"
          >
            <template v-slot:body="props">
              <q-tr :props="props">
                <q-td key="nome" :props="props">
                  {{ props.row.nome }}
                </q-td>
                <q-td key="codigoInterno" :props="props">
                  {{ props.row.codigoInterno }}
                </q-td>
                <q-td key="precoCusto" :props="props">
                  {{ formatarMoeda(props.row.precoCusto) }}
                </q-td>
                <q-td key="precoVenda" :props="props">
                  {{ formatarMoeda(props.row.precoVenda) }}
                </q-td>
                <q-td key="margemLucro" :props="props">
                  {{ props.row.margemLucro || '—' }}%
                </q-td>
                <q-td key="unidadeMedida" :props="props">
                  {{ props.row.unidadeMedida || '—' }}
                </q-td>
                <q-td key="ativo" :props="props">
                  <q-icon
                    :name="props.row.ativo ? 'check_circle' : 'cancel'"
                    :color="props.row.ativo ? 'positive' : 'negative'"
                    size="sm"
                  />
                  {{ props.row.ativo ? 'Sim' : 'Não' }}
                </q-td>
                <q-td key="ncm" :props="props">
                  {{ props.row.ncm || '—' }}
                </q-td>
                <q-td key="grupo" :props="props">
                  {{ props.row.grupo || '—' }}
                </q-td>
                <q-td key="categoriaId" :props="props">
                  <span :class="props.row.categoriaId ? 'text-positive' : 'text-grey'">
                    {{ props.row.categoriaId || '(não encontrada)' }}
                  </span>
                </q-td>
              </q-tr>
            </template>
          </q-table>
        </div>
      </div>

      <!-- Mensagem quando não há preview -->
      <div v-else-if="!carregando" class="text-center q-mt-xl text-grey">
        <q-icon name="cloud_upload" size="8rem" class="q-mb-md" />
        <div class="text-h6">Aguardando upload do arquivo</div>
        <div class="text-body1">Selecione um arquivo XLS/XLSX para gerar o preview</div>
      </div>

      <!-- Indicador de carregamento -->
      <div v-if="carregando" class="text-center q-mt-xl">
        <q-spinner size="3rem" color="primary" />
        <div class="text-body1 q-mt-md">Processando arquivo...</div>
      </div>
    </q-card-section>

    <q-separator />

    <q-card-actions align="right">
      <q-btn
        flat
        label="Cancelar"
        color="negative"
        @click="emit('close')"
      />
      <!-- Botão de confirmação de importação -->
      <q-btn
        flat
        label="Confirmar importação"
        color="primary"
        :loading="carregandoConfirmacao"
        :disabled="!arquivoImportacao || !previewResponse || carregando"
        @click="confirmarImportacao"
      >
        <q-tooltip v-if="!arquivoImportacao || !previewResponse">Faça upload e gere o preview primeiro</q-tooltip>
      </q-btn>
    </q-card-actions>
  </q-card>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useApiRequest } from "@/composables/useApiRequest";
import { useQuasar } from "quasar";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Props e Emits
defineProps({
  isOpen: Boolean,
  modelValue: Boolean,
});

const emit = defineEmits(["close", "update:modelValue"]);

// Estados
const arquivoImportacao = ref(null);
const previewResponse = ref(null);
const carregando = ref(false);
const carregandoConfirmacao = ref(false);
const paginacaoTabela = ref({ page: 1, rowsPerPage: 50 });

// Colunas da tabela
const colunasTabela = [
  { name: "nome", label: "Nome", field: "nome", align: "left", sortable: true },
  { name: "codigoInterno", label: "Código", field: "codigoInterno", align: "center", sortable: true },
  { name: "precoCusto", label: "Custo", field: "precoCusto", align: "center", sortable: true },
  { name: "precoVenda", label: "Venda", field: "precoVenda", align: "center", sortable: true },
  { name: "margemLucro", label: "Margem%", field: "margemLucro", align: "center", sortable: true },
  { name: "unidadeMedida", label: "Un.", field: "unidadeMedida", align: "center", sortable: true },
  { name: "ativo", label: "Ativo", field: "ativo", align: "center", sortable: true },
  { name: "ncm", label: "NCM", field: "ncm", align: "center", sortable: true },
  { name: "grupo", label: "Grupo", field: "grupo", align: "center", sortable: true },
  { name: "categoriaId", label: "Cat. ID", field: "categoriaId", align: "center", sortable: true },
];

// Métodos
function limparArquivo() {
  arquivoImportacao.value = null;
  previewResponse.value = null;
}

async function gerarPreview() {
  if (!arquivoImportacao.value) {
    previewResponse.value = null;
    return;
  }

  carregando.value = true;
  try {
    const formData = new FormData();
    formData.append("file", arquivoImportacao.value);

    // Chamar o endpoint de preview, passando o FormData (arquivo)
    // Como o apiRequest padrão não suporta multipart/form-data, precisamos fazer uma chamada direta
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    const baseUrl = window.RuntimeConfig?.apiBaseUrl || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

    const response = await fetch(`${baseUrl}/api/produtos/import/preview`, {
      method: "POST",
      headers: {
        // Não definir Content-Type para multipart/form-data, o browser faz automaticamente com boundary correto
        "Authorization": `Bearer ${token}`
      },
      body: formData
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    previewResponse.value = data;

    $q.notify({
      color: "positive",
      message: `Preview gerado com sucesso! Total: ${data.total}, Válidos: ${data.validos}`,
      icon: "check_circle",
    });
  } catch (error) {
    console.error("Erro ao gerar preview:", error);
    $q.notify({
      color: "negative",
      message: "Erro ao gerar preview: " + (error.message || "Falha na requisição"),
      icon: "error",
    });
    previewResponse.value = null;
  } finally {
    carregando.value = false;
  }
}

// Funções auxiliares
function formatarMoeda(valor) {
  if (!valor) return "R$ 0,00";
  const numero = typeof valor === 'string' ? parseFloat(valor) : valor;
  if (isNaN(numero)) return "R$ 0,00";

  return numero.toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });
}

// Função para confirmar a importação
async function confirmarImportacao() {
  if (!arquivoImportacao.value) {
    $q.notify({
      color: "negative",
      message: "Nenhum arquivo selecionado para importação",
      icon: "error",
    });
    return;
  }

  carregandoConfirmacao.value = true;
  try {
    const formData = new FormData();
    formData.append("file", arquivoImportacao.value);

    // Chamar o endpoint de confirmação de importação
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    const baseUrl = window.RuntimeConfig?.apiBaseUrl || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

    const response = await fetch(`${baseUrl}/api/produtos/import/confirm`, {
      method: "POST",
      headers: {
        // Não definir Content-Type para multipart/form-data, o browser faz automaticamente com boundary correto
        "Authorization": `Bearer ${token}`
      },
      body: formData
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();

    // Mostrar feedback com o resumo
    let mensagem = `Importação concluída! Total: ${data.total}, Processados: ${data.processados}, Criados: ${data.criadas}`;
    if (data.ignoradasDuplicadas > 0) {
      mensagem += `, Ignorados (duplicados): ${data.ignoradasDuplicadas}`;
    }
    if (data.erros > 0) {
      mensagem += `, Erros: ${data.erros}`;
    }

    $q.notify({
      color: data.erros > 0 ? "warning" : "positive",
      message: mensagem,
      icon: data.erros > 0 ? "warning" : "check_circle",
    });

    // Exibir detalhes das categorias criadas, se houver
    if (data.categoriasCriadas && data.categoriasCriadas.length > 0) {
      const categoriasMsg = data.categoriasCriadas.map(cat => `${cat.nome} (ID: ${cat.id})`).join(', ');
      $q.notify({
        color: "info",
        message: `Categorias criadas: ${categoriasMsg}`,
        icon: "category",
      });
    }

    // Limpar o estado após sucesso
    limparArquivo();

  } catch (error) {
    console.error("Erro ao confirmar importação:", error);
    $q.notify({
      color: "negative",
      message: "Erro ao confirmar importação: " + (error.message || "Falha na requisição"),
      icon: "error",
    });
  } finally {
    carregandoConfirmacao.value = false;
  }
}

// Quando o componente for montado
onMounted(() => {
  // Nenhuma ação inicial necessária
});
</script>

<style scoped>
.cursor-pointer {
  cursor: pointer;
}
</style>
