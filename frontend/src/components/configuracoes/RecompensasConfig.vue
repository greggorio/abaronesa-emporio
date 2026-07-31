<template>
  <div class="recompensas-config">
    <q-card class="config-card q-mb-lg" flat bordered>
      <q-card-section class="card-header">
        <div class="header-content">
          <div>
            <div class="text-h5 text-weight-medium text-grey-9">Recompensas</div>
            <div class="text-body2 text-grey-7 q-mt-xs">Gerencie as recompensas disponíveis no programa de gamificação</div>
          </div>
          <q-icon name="o_card_giftcard" size="48px" color="positive" class="header-icon" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row justify-end q-mb-md">
          <q-btn
            label="Nova Recompensa"
            color="primary"
            icon="add"
            @click="openCreateDialog()"
          />
        </div>

        <q-table
          :rows="recompensas"
          :columns="columns"
          row-key="id"
          :loading="loading"
          :pagination="pagination"
          @request="onPaginationChange"
        >
          <template v-slot:body-cell-tipo="props">
            <q-td :props="props">
              <div>
                <div>{{ formatTipoRecompensa(props.row.tipo) }}</div>
                <div v-if="props.row.tipo === 'PRODUTO' && props.row.produtoId" class="text-caption text-grey">
                  ID: {{ props.row.produtoId }}
                </div>
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-pontosNecessarios="props">
            <q-td :props="props">
              {{ props.row.pontosNecessarios }} pts
            </q-td>
          </template>

          <template v-slot:body-cell-ativo="props">
            <q-td :props="props">
              <q-toggle
                v-model="props.row.ativo"
                @update:model-value="toggleAtivo(props.row)"
                :disable="updating.includes(props.row.id)"
              />
            </q-td>
          </template>

          <template v-slot:body-cell-acoes="props">
            <q-td :props="props">
              <q-btn
                icon="edit"
                color="info"
                flat
                size="sm"
                @click="openEditDialog(props.row)"
              />
            </q-td>
          </template>
        </q-table>
      </q-card-section>
    </q-card>

    <!-- Dialog de cadastro/edição -->
    <q-dialog v-model="dialog.show" persistent>
      <q-card style="min-width: 600px;">
        <q-card-section class="row items-center">
          <div class="text-h6">{{ dialog.mode === 'create' ? 'Nova Recompensa' : 'Editar Recompensa' }}</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-form @submit.prevent="handleSave">
          <q-card-section class="q-pt-none">
            <div class="row q-col-gutter-md">
              <div class="col-12">
                <q-input
                  v-model="form.nome"
                  label="Nome *"
                  outlined
                  dense
                  :rules="[val => val && val.length > 0 || 'Nome é obrigatório']"
                  :disable="saving"
                />
              </div>

              <div class="col-12">
                <q-input
                  v-model="form.descricao"
                  label="Descrição"
                  outlined
                  dense
                  type="textarea"
                  :disable="saving"
                />
              </div>

              <div class="col-6">
                <q-select
                  v-model="form.tipo"
                  label="Tipo *"
                  :options="tiposRecompensa"
                  option-label="label"
                  option-value="value"
                  outlined
                  dense
                  emit-value
                  map-options
                  :rules="[val => val != null || 'Tipo é obrigatório']"
                  :disable="saving"
                  @update:model-value="onTipoChange"
                />
              </div>

              <div class="col-6">
                <q-input
                  v-model.number="form.pontosNecessarios"
                  type="number"
                  label="Pontos Necessários *"
                  outlined
                  dense
                  min="1"
                  :rules="[val => val > 0 || 'Pontos necessários devem ser maiores que zero']"
                  :disable="saving"
                />
              </div>

              <div class="col-6">
                <q-input
                  v-model.number="form.estoque"
                  type="number"
                  label="Estoque"
                  outlined
                  dense
                  min="0"
                  :disable="saving || form.estoque === null"
                />
                <q-checkbox
                  v-model="hasEstoque"
                  label="Controlar estoque"
                  :disable="saving"
                  class="q-mt-sm"
                />
              </div>

              <div class="col-6">
                <q-toggle
                  v-model="form.ativo"
                  label="Ativo"
                  color="primary"
                  :disable="saving"
                />
              </div>

              <!-- Campos condicionais por tipo -->
              <template v-if="form.tipo === 'PRODUTO'">
                <div class="col-12">
                  <q-select
                    v-model="selectedProduto"
                    label="Produto *"
                    :options="produtoOptions"
                    option-value="value"
                    option-label="label"
                    outlined
                    dense
                    use-input
                    fill-input
                    hide-selected
                    @filter="filterProdutoOptions"
                    @update:model-value="onProdutoChange"
                    :loading="loadingProdutos"
                    :rules="[val => val != null || 'Produto é obrigatório']"
                    :disable="saving"
                    input-debounce="500"
                  >
                    <template v-slot:no-option>
                      <q-item>
                        <q-item-section class="text-grey">
                          Nenhum produto encontrado
                        </q-item-section>
                      </q-item>
                    </template>
                  </q-select>
                </div>
              </template>

              <template v-if="form.tipo === 'DESCONTO_PERCENTUAL'">
                <div class="col-6">
                  <q-input
                    v-model.number="form.descontoPercentual"
                    type="number"
                    label="Desconto Percentual (%) *"
                    outlined
                    dense
                    min="0.01"
                    max="100"
                    suffix="%"
                    :rules="[
                      val => val != null || 'Desconto é obrigatório',
                      val => val > 0 && val <= 100 || 'Desconto deve estar entre 0 e 100%'
                    ]"
                    :disable="saving"
                  />
                </div>

                <div class="col-6">
                  <q-input
                    v-model.number="form.descontoValorMaximo"
                    type="number"
                    label="Desconto Valor Máximo (R$)"
                    outlined
                    dense
                    min="0"
                    suffix="R$"
                    step="0.01"
                    :disable="saving"
                  />
                </div>
              </template>

              <template v-if="form.tipo === 'DESCONTO_VALOR'">
                <div class="col-6">
                  <q-input
                    v-model.number="form.descontoValor"
                    type="number"
                    label="Desconto em Valor (R$) *"
                    outlined
                    dense
                    min="0.01"
                    suffix="R$"
                    step="0.01"
                    :rules="[
                      val => val != null || 'Valor de desconto é obrigatório',
                      val => val > 0 || 'Valor de desconto deve ser positivo'
                    ]"
                    :disable="saving"
                  />
                </div>
              </template>

              <div class="col-6">
                <q-input
                  v-model="form.validadeInicio"
                  type="date"
                  label="Validade Início"
                  outlined
                  dense
                  :disable="saving"
                />
              </div>

              <div class="col-6">
                <q-input
                  v-model="form.validadeFim"
                  type="date"
                  label="Validade Fim"
                  outlined
                  dense
                  :disable="saving"
                />
              </div>

              <div class="col-12">
                <q-input
                  v-model="form.imageUrl"
                  label="URL da Imagem"
                  outlined
                  dense
                  :disable="saving"
                />
              </div>
            </div>
          </q-card-section>

          <q-card-actions align="right">
            <q-btn label="Cancelar" color="grey" v-close-popup :disable="saving" />
            <q-btn
              type="submit"
              :label="dialog.mode === 'create' ? 'Criar' : 'Atualizar'"
              color="primary"
              :loading="saving"
              :disable="saving"
            />
          </q-card-actions>
        </q-form>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useQuasar } from 'quasar';
import { useApiRequest } from '@/composables/useApiRequest';

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Estados
const recompensas = ref([]);
const loading = ref(false);
const updating = ref([]);
const saving = ref(false);
const loadingProdutos = ref(false);
const produtoOptions = ref([]);
const selectedProduto = ref(null);

const form = ref({
  id: null,
  nome: '',
  descricao: '',
  tipo: null,
  pontosNecessarios: 1,
  estoque: null,
  ativo: true,
  validadeInicio: null,
  validadeFim: null,
  imageUrl: '',
  descontoPercentual: null,
  descontoValor: null,
  descontoValorMaximo: null,
  produtoId: null,
});

const hasEstoque = ref(false);

const dialog = ref({
  show: false,
  mode: 'create',
});

const pagination = ref({
  sortBy: 'id',
  descending: true,
  page: 1,
  rowsPerPage: 10,
  rowsNumber: 0,
});

const columns = [
  { name: 'id', label: '#ID', field: 'id', sortable: true },
  { name: 'nome', label: 'Nome', field: 'nome', sortable: true },
  { name: 'tipo', label: 'Tipo', field: 'tipo', sortable: true },
  { name: 'pontosNecessarios', label: 'Pontos', field: 'pontosNecessarios', sortable: true },
  { name: 'ativo', label: 'Ativo', field: 'ativo' },
  { name: 'acoes', label: 'Ações', field: 'acoes', align: 'center' },
];

const tiposRecompensa = [
  { label: 'Produto', value: 'PRODUTO' },
  { label: 'Desconto Percentual', value: 'DESCONTO_PERCENTUAL' },
  { label: 'Desconto em Valor', value: 'DESCONTO_VALOR' },
  { label: 'Brinde Genérico', value: 'BRINDE_GENERICO' },
];

// Funções auxiliares
const formatDate = (dateString) => {
  if (!dateString) return null;
  const date = new Date(dateString);
  if (isNaN(date.getTime())) return dateString; // Se não for uma data válida, retorna como está
  return date.toISOString().split('T')[0]; // Retorna no formato YYYY-MM-DD
};

// Funções
const loadRecompensas = async () => {
  try {
    loading.value = true;
    const response = await apiRequest('/api/admin/recompensas');
    
    if (response) {
      recompensas.value = response;
      pagination.value.rowsNumber = response.length;
    }
  } catch (error) {
    console.error('Erro ao carregar recompensas:', error);
    $q.notify({
      type: 'negative',
      message: 'Não foi possível carregar as recompensas.',
      position: 'top-right',
    });
  } finally {
    loading.value = false;
  }
};

const formatTipoRecompensa = (tipo) => {
  const tipoObj = tiposRecompensa.find(t => t.value === tipo);
  return tipoObj ? tipoObj.label : tipo;
};

const onTipoChange = async () => {
  // Limpar campos incompatíveis quando o tipo mudar
  if (form.value.tipo !== 'DESCONTO_PERCENTUAL') {
    form.value.descontoPercentual = null;
    form.value.descontoValorMaximo = null;
  }
  
  if (form.value.tipo !== 'DESCONTO_VALOR') {
    form.value.descontoValor = null;
  }
  
  if (form.value.tipo !== 'PRODUTO') {
    form.value.produtoId = null;
    selectedProduto.value = null;
  } else {
    // Se for do tipo PRODUTO, carregar as opções de produtos
    if (produtoOptions.value.length === 0) {
      await loadProdutoOptions();
    }
  }
};

const openCreateDialog = () => {
  resetForm();
  dialog.value.mode = 'create';
  dialog.value.show = true;
};

const openEditDialog = async (recompensa) => {
  Object.assign(form.value, recompensa);
  // Converter datas para o formato esperado pelo input date (YYYY-MM-DD)
  if (form.value.validadeInicio) {
    form.value.validadeInicio = formatDate(form.value.validadeInicio);
  }
  if (form.value.validadeFim) {
    form.value.validadeFim = formatDate(form.value.validadeFim);
  }
  hasEstoque.value = form.value.estoque != null;
  
  // Se for do tipo PRODUTO e não tiver produto selecionado nas opções, buscar o produto individualmente
  if (form.value.tipo === 'PRODUTO' && form.value.produtoId && !produtoOptions.value.some(p => p.value === form.value.produtoId)) {
    try {
      // Primeiro tentar encontrar o produto nas opções já carregadas
      const produtoExistente = produtoOptions.value.find(p => p.value === form.value.produtoId);
      if (!produtoExistente) {
        // Se não estiver nas opções, buscar o produto específico
        const produto = await apiRequest(`/api/produtos/${form.value.produtoId}`);
        if (produto) {
          const produtoOption = {
            value: produto.id,
            label: `${produto.codigoInterno} - ${produto.nome}`
          };
          // Adicionar o produto às opções para que apareça selecionado corretamente
          if (!produtoOptions.value.some(p => p.value === produtoOption.value)) {
            produtoOptions.value.push(produtoOption);
          }
        }
      }
    } catch (error) {
      console.error('Erro ao carregar produto para edição:', error);
    }
  }
  
  // Sincronizar o selectedProduto com o form.produtoId
  if (form.value.tipo === 'PRODUTO' && form.value.produtoId) {
    selectedProduto.value = produtoOptions.value.find(p => p.value === form.value.produtoId) || { value: form.value.produtoId, label: `Produto ${form.value.produtoId}` };
  } else {
    selectedProduto.value = null;
  }
  
  dialog.value.mode = 'edit';
  dialog.value.show = true;
};

const resetForm = () => {
  Object.assign(form.value, {
    id: null,
    nome: '',
    descricao: '',
    tipo: null,
    pontosNecessarios: 1,
    estoque: null,
    ativo: true,
    validadeInicio: null,
    validadeFim: null,
    imageUrl: '',
    descontoPercentual: null,
    descontoValor: null,
    descontoValorMaximo: null,
    produtoId: null,
  });
  hasEstoque.value = false;
  selectedProduto.value = null;
};

const handleSave = async () => {
  if (!validateForm()) return;

  saving.value = true;
  
  try {
    const payload = preparePayload();
    
    if (dialog.value.mode === 'create') {
      await apiRequest('/api/admin/recompensas', 'POST', payload);
      $q.notify({
        type: 'positive',
        message: 'Recompensa criada com sucesso!',
        position: 'top-right',
      });
    } else {
      await apiRequest(`/api/admin/recompensas/${form.value.id}`, 'PUT', payload);
      $q.notify({
        type: 'positive',
        message: 'Recompensa atualizada com sucesso!',
        position: 'top-right',
      });
    }
    
    dialog.value.show = false;
    await loadRecompensas();
  } catch (error) {
    console.error('Erro ao salvar recompensa:', error);
    let message = 'Não foi possível salvar os dados.';
    
    if (error.response?.data?.message) {
      message = error.response.data.message;
    } else if (error.message) {
      message = error.message;
    }
    
    $q.notify({
      type: 'negative',
      message,
      position: 'top-right',
    });
  } finally {
    saving.value = false;
  }
};

const validateForm = () => {
  if (!form.value.nome || form.value.nome.trim().length === 0) {
    $q.notify({
      type: 'warning',
      message: 'Nome é obrigatório.',
      position: 'top-right',
    });
    return false;
  }
  
  if (!form.value.tipo) {
    $q.notify({
      type: 'warning',
      message: 'Tipo é obrigatório.',
      position: 'top-right',
    });
    return false;
  }
  
  if (!form.value.pontosNecessarios || form.value.pontosNecessarios <= 0) {
    $q.notify({
      type: 'warning',
      message: 'Pontos necessários devem ser maiores que zero.',
      position: 'top-right',
    });
    return false;
  }
  
  // Validação específica por tipo
  if (form.value.tipo === 'DESCONTO_PERCENTUAL') {
    if (form.value.descontoPercentual == null || form.value.descontoPercentual <= 0 || form.value.descontoPercentual > 100) {
      $q.notify({
        type: 'warning',
        message: 'Desconto percentual deve estar entre 0 e 100.',
        position: 'top-right',
      });
      return false;
    }
  }
  
  if (form.value.tipo === 'DESCONTO_VALOR') {
    if (form.value.descontoValor == null || form.value.descontoValor <= 0) {
      $q.notify({
        type: 'warning',
        message: 'Valor de desconto deve ser maior que zero.',
        position: 'top-right',
      });
      return false;
    }
  }
  
  return true;
};

const preparePayload = () => {
  const payload = { ...form.value };
  
  // Ajustar datas para o formato correto (não alterar, o input date já envia no formato correto YYYY-MM-DD)
  // Ajustar estoque
  if (!hasEstoque.value) {
    payload.estoque = null;
  }
  
  // Remover campos que não se aplicam ao tipo
  if (payload.tipo !== 'DESCONTO_PERCENTUAL') {
    payload.descontoPercentual = null;
    payload.descontoValorMaximo = null;
  }
  
  if (payload.tipo !== 'DESCONTO_VALOR') {
    payload.descontoValor = null;
  }
  
  return payload;
};

const toggleAtivo = async (recompensa) => {
  updating.value.push(recompensa.id);
  
  try {
    const payload = { ...recompensa, ativo: !recompensa.ativo };
    await apiRequest(`/api/admin/recompensas/${recompensa.id}`, 'PUT', payload);
    
    $q.notify({
      type: recompensa.ativo ? 'positive' : 'warning',
      message: `Recompensa ${recompensa.ativo ? 'ativada' : 'desativada'} com sucesso!`,
      position: 'top-right',
    });
  } catch (error) {
    console.error('Erro ao atualizar status da recompensa:', error);
    recompensa.ativo = !recompensa.ativo; // Reverter status em caso de erro
    
    $q.notify({
      type: 'negative',
      message: 'Falha ao atualizar status da recompensa.',
      position: 'top-right',
    });
  } finally {
    updating.value = updating.value.filter(id => id !== recompensa.id);
  }
};

const onPaginationChange = (props) => {
  pagination.value.page = props.pagination.page;
  pagination.value.rowsPerPage = props.pagination.rowsPerPage;
  pagination.value.sortBy = props.pagination.sortBy;
  pagination.value.descending = props.pagination.descending;
};

// Métodos para produtos
const loadProdutoOptions = async (search = '') => {
  try {
    loadingProdutos.value = true;
    const response = await apiRequest(`/api/produtos/lookup/search?search=${encodeURIComponent(search)}`);
    
    if (response) {
      produtoOptions.value = response.map(produto => ({
        value: produto.id,
        label: `${produto.codigo || produto.codigoInterno} - ${produto.descricao || produto.nome}`
      }));
    }
  } catch (error) {
    console.error('Erro ao carregar produtos:', error);
    $q.notify({
      type: 'negative',
      message: 'Não foi possível carregar os produtos.',
      position: 'top-right',
    });
    produtoOptions.value = [];
  } finally {
    loadingProdutos.value = false;
  }
};

const filterProdutoOptions = async (val, update) => {
  await loadProdutoOptions(val);
  update();
};

const onProdutoChange = (selected) => {
  if (selected && typeof selected === 'object' && selected.value !== undefined) {
    form.value.produtoId = selected.value;
  } else if (typeof selected === 'number') {
    form.value.produtoId = selected;
  } else {
    form.value.produtoId = null;
  }
};

onMounted(async () => {
  await loadRecompensas();
  // Carregar opções de produtos para o lookup
  await loadProdutoOptions();
});
</script>

<style scoped lang="scss">
.recompensas-config {
  margin: 0 auto;
  padding: 0 16px;
}

.config-card {
  border-radius: 12px;
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.08);
}

.card-header {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-icon {
  opacity: 0.85;
}
</style>