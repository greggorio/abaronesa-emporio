<template>
  <div class="recebimentos-container">
    <!-- Gerador de Parcelas (apenas para novos registros ou quando não há parcelas) -->
    <div v-if="!isEditMode || parcelas.length === 0" class="q-mb-md">
      <q-card flat bordered>
        <q-card-section>
          <div class="text-subtitle2 q-mb-sm">Gerar Parcelas</div>
          <div class="row q-col-gutter-sm">
            <div class="col-12">
              <q-btn color="primary" label="Gerar Parcelas" icon="calculate" outline @click="gerarParcelas" :disable="!podeGerarParcelas" />
              <q-btn v-if="parcelas.length > 0" color="negative" label="Limpar" icon="clear" outline class="q-ml-sm" @click="limparParcelas" />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>

    <!-- Lista de Parcelas -->
    <div v-if="parcelas.length > 0">
      <div class="parcelas-grid">
        <q-card v-for="parcela in parcelas" :key="parcela.index" flat class="parcela-card">
          <q-card-section class="parcela-header">
            <div class="parcela-title">
              <div class="text-caption text-grey">Parcela</div>
              <div class="text-weight-bold">{{ parcela.numeroParcela }}</div>
            </div>
            <q-badge :color="getStatusColor(parcela)" :label="getStatusLabel(parcela)">
              <q-tooltip v-if="parcela.diasAtraso > 0">{{ parcela.diasAtraso }} dias de atraso</q-tooltip>
            </q-badge>
          </q-card-section>

          <q-separator />

          <q-card-section class="parcela-body">
            <div class="row q-col-gutter-xs">
              <div class="col-12 col-sm-6 col-md-3">
                <div class="field-label">Vencimento</div>
                <q-input
                  :model-value="formatDateToBR(parcela.dataVencimento)"
                  @update:model-value="
                    (val) => {
                      parcela.dataVencimento = formatDateToISO(val);
                      onParcelaChanged();
                    }
                  "
                  mask="##/##/####"
                  placeholder="dd/mm/aaaa"
                  dense
                  filled
                  bg-color="white"
                  class="compact-input"
                  :rules="[(val) => !!val || 'Obrigatório', (val) => isValidDate(val) || 'Data inválida']"
                >
                  <template v-slot:append>
                    <q-icon name="event" class="cursor-pointer">
                      <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                        <q-date
                          :model-value="formatDateToISO(parcela.dataVencimento)"
                          @update:model-value="
                            (val) => {
                              parcela.dataVencimento = val;
                              onParcelaChanged();
                            }
                          "
                          mask="YYYY-MM-DD"
                        >
                          <div class="row items-center justify-end">
                            <q-btn v-close-popup label="Fechar" color="primary" flat />
                          </div>
                        </q-date>
                      </q-popup-proxy>
                    </q-icon>
                  </template>
                </q-input>
              </div>

              <div class="col-12 col-sm-6 col-md-3">
                <div class="field-label">Valor</div>
                <q-input
                  v-model.number="parcela.valor"
                  type="number"
                  dense
                  filled
                  bg-color="white"
                  class="compact-input"
                  prefix="R$"
                  :step="0.01"
                  :min="0.01"
                  :rules="[(val) => val > 0 || 'Valor inválido']"
                  @update:model-value="onParcelaChanged"
                />
              </div>

              <div class="col-12 col-sm-6 col-md-3">
                <div class="field-label">Data Receb.</div>
                <q-input
                  v-if="!parcela.recebida"
                  :model-value="formatDateToBR(parcela.dataRecebimento)"
                  @update:model-value="(val) => onDataRecebimentoChanged(parcela, formatDateToISO(val))"
                  mask="##/##/####"
                  placeholder="dd/mm/aaaa"
                  dense
                  filled
                  bg-color="white"
                  class="compact-input"
                >
                  <template v-slot:append>
                    <q-icon name="event" class="cursor-pointer">
                      <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                        <q-date
                          :model-value="formatDateToISO(parcela.dataRecebimento)"
                          @update:model-value="(val) => onDataRecebimentoChanged(parcela, val)"
                          mask="YYYY-MM-DD"
                        >
                          <div class="row items-center justify-end">
                            <q-btn v-close-popup label="Fechar" color="primary" flat />
                          </div>
                        </q-date>
                      </q-popup-proxy>
                    </q-icon>
                  </template>
                </q-input>
                <div v-else class="text-positive q-mt-sm">
                  {{ formatDateToBR(parcela.dataRecebimento) }}
                </div>
              </div>

              <div class="col-12 col-sm-6 col-md-3">
                <div class="field-label">Forma</div>
                <q-select
                  v-if="parcela.dataRecebimento && !parcela.recebida"
                  v-model="parcela.formaRecebimento"
                  :options="formasRecebimento"
                  dense
                  filled
                  bg-color="white"
                  class="compact-input"
                  emit-value
                  map-options
                  @update:model-value="onParcelaChanged"
                >
                  <template v-slot:selected-item="scope">
                    <q-item dense>
                      <q-item-section v-if="scope.opt?.icon" avatar>
                        <q-icon :name="scope.opt.icon" size="16px" />
                      </q-item-section>
                      <q-item-section>
                        <q-item-label>{{ scope.opt?.label }}</q-item-label>
                      </q-item-section>
                    </q-item>
                  </template>
                  <template v-slot:option="scope">
                    <q-item v-bind="scope.itemProps" dense>
                      <q-item-section v-if="scope.opt?.icon" avatar>
                        <q-icon :name="scope.opt.icon" size="16px" />
                      </q-item-section>
                      <q-item-section>
                        <q-item-label>{{ scope.opt?.label }}</q-item-label>
                      </q-item-section>
                    </q-item>
                  </template>
                </q-select>
                <div v-else-if="parcela.recebida" class="text-positive q-mt-sm">
                  {{ parcela.formaRecebimento }}
                </div>
                <div v-else class="text-grey-6 q-mt-sm">-</div>
              </div>
            </div>
          </q-card-section>

          <q-separator />

          <q-card-section class="parcela-actions">
            <div class="row items-center justify-between q-col-gutter-sm">
              <div class="col-12 col-sm-auto">
                <div v-if="!parcela.recebida">
                  <q-btn
                    v-if="!parcela.cobrancaEnviada"
                    size="sm"
                    color="warning"
                    icon="mail"
                    flat
                    dense
                    @click="marcarCobrancaEnviada(parcela)"
                  >
                    <q-tooltip>Marcar cobrança como enviada</q-tooltip>
                  </q-btn>
                  <div v-else class="text-caption text-grey">
                    <q-icon name="check" color="positive" size="sm" />
                    {{ formatDateToBR(parcela.dataEnvioCobranca) }}
                  </div>
                </div>
              </div>
              <div class="col-12 col-sm-auto text-right">
                <q-btn
                  v-if="parcela.dataRecebimento && !parcela.recebida"
                  size="sm"
                  color="positive"
                  icon="check"
                  flat
                  dense
                  @click="confirmarRecebimento(parcela)"
                >
                  <q-tooltip>Confirmar Recebimento</q-tooltip>
                </q-btn>
                <q-btn v-if="parcela.recebida" size="sm" color="warning" icon="undo" flat dense @click="desfazerRecebimento(parcela)">
                  <q-tooltip>Desfazer Recebimento</q-tooltip>
                </q-btn>
              </div>
            </div>
          </q-card-section>
        </q-card>
      </div>

      <q-card flat bordered class="parcelas-summary">
        <q-card-section>
          <div class="row q-col-gutter-sm">
            <div class="col-12 col-sm-4">
              <div class="text-caption text-grey">Total de Parcelas</div>
              <div class="text-weight-bold">{{ parcelas.length }}</div>
            </div>
            <div class="col-12 col-sm-4">
              <div class="text-caption text-grey">Valor Total</div>
              <div class="text-weight-bold">{{ formatCurrency(valorTotalParcelas) }}</div>
            </div>
            <div class="col-12 col-sm-4">
              <div class="text-caption text-grey">Valor Pendente</div>
              <div class="text-weight-bold text-warning">{{ formatCurrency(valorPendente) }}</div>
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>

    <!-- Mensagem quando não há parcelas -->
    <div v-else class="text-center q-pa-lg text-grey-6">
      <q-icon name="event_busy" size="48px" />
      <div class="q-mt-sm">Nenhuma parcela gerada</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from "vue";
import { useQuasar } from "quasar";
import { format, addMonths } from "date-fns";
import { ptBR } from "date-fns/locale";

const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  recordId: {
    type: Number,
    default: null,
  },
});

const emit = defineEmits(["update:modelValue"]);

const $q = useQuasar();

// Estado local
const parcelas = ref([]);
const isEditMode = computed(() => !!props.recordId);

// Debug logs
console.log("RecebimentosTab montado");
watch(
  () => props.modelValue,
  (newVal) => {
    console.log("RecebimentosTab recebeu modelValue:", newVal);
  },
  { deep: true, immediate: true }
);

// ==================== FUNÇÕES UTILITÁRIAS DE DATA ====================

// Função para validar data no formato brasileiro
function isValidDate(dateStr) {
  // Aceita vazio, null ou undefined
  if (!dateStr || dateStr === '' || dateStr === null || dateStr === undefined) return true;

  // Verifica se está no formato dd/MM/yyyy
  const regex = /^(\d{2})\/(\d{2})\/(\d{4})$/;
  const match = dateStr.match(regex);

  if (!match) return false;

  const [, day, month, year] = match;
  const date = new Date(year, month - 1, day);

  // Verifica se a data é válida
  return date.getFullYear() == year && date.getMonth() == month - 1 && date.getDate() == day;
}

// Converte data do formato brasileiro (dd/MM/yyyy) para ISO (yyyy-MM-dd)
function formatDateToISO(dateStr) {
  if (!dateStr) return "";

  // Se já está no formato ISO, retorna
  if (dateStr.match(/^\d{4}-\d{2}-\d{2}$/)) {
    return dateStr;
  }

  // Se está no formato brasileiro dd/MM/yyyy
  if (dateStr.match(/^\d{2}\/\d{2}\/\d{4}$/)) {
    const [day, month, year] = dateStr.split("/");
    return `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`;
  }

  // Se é uma data válida em outro formato, tenta converter
  try {
    const date = new Date(dateStr);
    if (!isNaN(date.getTime())) {
      return format(date, "yyyy-MM-dd");
    }
  } catch (e) {
    console.warn("Erro ao converter data:", dateStr, e);
  }

  return dateStr;
}

// Converte data do formato ISO (yyyy-MM-dd) para brasileiro (dd/MM/yyyy)
function formatDateToBR(dateStr) {
  if (!dateStr) return "-";

  try {
    // Se está no formato ISO
    if (dateStr.match(/^\d{4}-\d{2}-\d{2}$/)) {
      const [year, month, day] = dateStr.split("-");
      return `${day}/${month}/${year}`;
    }

    // Se já está no formato brasileiro, retorna
    if (dateStr.match(/^\d{2}\/\d{2}\/\d{4}$/)) {
      return dateStr;
    }

    // Tenta converter usando Date
    const date = new Date(dateStr);
    if (!isNaN(date.getTime())) {
      return format(date, "dd/MM/yyyy", { locale: ptBR });
    }

    return dateStr;
  } catch (e) {
    console.warn("Erro ao formatar data:", dateStr, e);
    return dateStr;
  }
}

// Função para formatar data de exibição
function formatDate(date) {
  return formatDateToBR(date);
}

// ==================== OPÇÕES E CONFIGURAÇÕES ====================

const formasRecebimento = [
  { label: "Crédito", value: "CARTAO_CREDITO", icon: "credit_card" },
  { label: "Débito", value: "CARTAO_DEBITO", icon: "credit_card" },
  { label: "Dinheiro", value: "DINHEIRO", icon: "payments" },
  { label: "Pix", value: "PIX", icon: "pix" },
  { label: "Transferência", value: "TRANSFERENCIA", icon: "sync_alt" },
];

// ==================== COMPUTED PROPERTIES ====================

const podeGerarParcelas = computed(() => {
  console.log("podeGerarParcelas - valorTotal:", props.modelValue.valorTotal, "numeroParcelas:", props.modelValue.numeroParcelas);
  return props.modelValue.valorTotal > 0 && props.modelValue.numeroParcelas > 0;
});

const valorTotalParcelas = computed(() => {
  return parcelas.value.reduce((sum, p) => sum + (p.valor || 0), 0);
});

const valorRecebido = computed(() => {
  return parcelas.value.filter((p) => p.recebida).reduce((sum, p) => sum + (p.valor || 0), 0);
});

const valorPendente = computed(() => {
  return parcelas.value.filter((p) => !p.recebida).reduce((sum, p) => sum + (p.valor || 0), 0);
});

// ==================== MÉTODOS PRINCIPAIS ====================

function gerarParcelas() {
  console.log("gerarParcelas chamado");
  if (!podeGerarParcelas.value) {
    console.log("Não pode gerar parcelas - condições não atendidas");
    return;
  }

  const valorTotal = props.modelValue.valorTotal;
  const numParcelas = props.modelValue.numeroParcelas;

  console.log("Gerando parcelas - Total:", valorTotal, "Parcelas:", numParcelas);

  // Calcula valor de cada parcela
  const valorParcela = Math.floor((valorTotal / numParcelas) * 100) / 100;
  const diferenca = valorTotal - valorParcela * numParcelas;

  // Gera as parcelas
  const novasParcelas = [];
  const hoje = new Date();

  for (let i = 0; i < numParcelas; i++) {
    const vencimento = addMonths(hoje, i + 1);

    novasParcelas.push({
      index: i,
      numeroParcela: i + 1,
      valor: i === numParcelas - 1 ? valorParcela + diferenca : valorParcela,
      dataVencimento: format(vencimento, "yyyy-MM-dd"), // FORMATO ISO CORRETO
      dataRecebimento: null,
      formaRecebimento: null,
      recebida: false,
      cobrancaEnviada: false,
      dataEnvioCobranca: null,
      diasAtraso: 0,
    });
  }

  parcelas.value = novasParcelas;
  console.log("Parcelas geradas:", parcelas.value);
  emitirMudanca();
}

function limparParcelas() {
  $q.dialog({
    title: "Confirmar",
    message: "Deseja limpar todas as parcelas?",
    cancel: true,
    persistent: true,
  }).onOk(() => {
    parcelas.value = [];
    emitirMudanca();
  });
}

function onParcelaChanged() {
  emitirMudanca();
}

function onDataRecebimentoChanged(parcela, valor) {
  parcela.dataRecebimento = valor;

  if (valor && !parcela.formaRecebimento) {
    parcela.formaRecebimento = "PIX"; // Default
  }

  // Calcula dias de atraso se houver
  if (valor && parcela.dataVencimento) {
    // Cria data de vencimento no timezone local para evitar conversão UTC
    const [anoVenc, mesVenc, diaVenc] = parcela.dataVencimento.split('-');
    const vencimento = new Date(parseInt(anoVenc), parseInt(mesVenc) - 1, parseInt(diaVenc));
    vencimento.setHours(0, 0, 0, 0);

    // Cria data de recebimento no timezone local para evitar conversão UTC
    const [anoReceb, mesReceb, diaReceb] = valor.split('-');
    const recebimento = new Date(parseInt(anoReceb), parseInt(mesReceb) - 1, parseInt(diaReceb));
    recebimento.setHours(0, 0, 0, 0);

    if (recebimento > vencimento) {
      parcela.diasAtraso = Math.floor((recebimento - vencimento) / (1000 * 60 * 60 * 24));
    } else {
      parcela.diasAtraso = 0;
    }
  }

  emitirMudanca();
}

function confirmarRecebimento(parcela) {
  if (!parcela.dataRecebimento) {
    $q.notify({
      type: "warning",
      message: "Informe a data de recebimento para confirmar",
    });
    return;
  }

  if (!parcela.formaRecebimento) {
    $q.notify({
      type: "warning",
      message: "Informe a forma de recebimento para confirmar",
    });
    return;
  }

  parcela.recebida = true;
  emitirMudanca();
}

function desfazerRecebimento(parcela) {
  $q.dialog({
    title: "Confirmar",
    message: "Deseja desfazer este recebimento?",
    cancel: true,
    persistent: true,
  }).onOk(() => {
    parcela.recebida = false;
    emitirMudanca();
  });
}

function marcarCobrancaEnviada(parcela) {
  parcela.cobrancaEnviada = true;
  parcela.dataEnvioCobranca = format(new Date(), "yyyy-MM-dd");
  $q.notify({
    type: "positive",
    message: "Cobrança marcada como enviada",
  });
  emitirMudanca();
}

function getStatusColor(parcela) {
  if (parcela.recebida) return "positive";
  if (parcela.dataRecebimento) return "warning";

  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0); // Zera as horas para comparar apenas a data

  // Cria data de vencimento no timezone local para evitar conversão UTC
  const [ano, mes, dia] = parcela.dataVencimento.split('-');
  const vencimento = new Date(parseInt(ano), parseInt(mes) - 1, parseInt(dia));
  vencimento.setHours(0, 0, 0, 0); // Zera as horas para comparar apenas a data

  // Debug: log para verificar comparação
  console.log('getStatusColor - Vencimento:', vencimento, 'Hoje:', hoje, 'Vencido?', vencimento < hoje);

  if (vencimento < hoje) {
    parcela.diasAtraso = Math.floor((hoje - vencimento) / (1000 * 60 * 60 * 24));
    return "negative";
  }
  return "grey";
}

function getStatusLabel(parcela) {
  if (parcela.recebida) return "Recebido";
  if (parcela.dataRecebimento) return "Aguardando";

  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0); // Zera as horas para comparar apenas a data

  // Cria data de vencimento no timezone local para evitar conversão UTC
  const [ano, mes, dia] = parcela.dataVencimento.split('-');
  const vencimento = new Date(parseInt(ano), parseInt(mes) - 1, parseInt(dia));
  vencimento.setHours(0, 0, 0, 0); // Zera as horas para comparar apenas a data

  if (vencimento < hoje) return "Vencido";
  return "Em aberto";
}

function formatCurrency(value) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(value || 0);
}

// ==================== DEBOUNCE E EMISSÃO ====================

// Métodos com debounce para evitar múltiplas chamadas
let updateTimeout = null;

function emitirMudanca() {
  // Limpa timeout anterior se existir
  if (updateTimeout) {
    clearTimeout(updateTimeout);
  }

  // Define novo timeout para evitar múltiplas emissões
  updateTimeout = setTimeout(() => {
    console.log("Emitindo mudança - parcelas:", parcelas.value);
    emit("update:modelValue", {
      ...props.modelValue,
      parcelas: parcelas.value,
    });
  }, 300); // 300ms de debounce
}

// ==================== WATCHERS ====================

watch(
  () => props.modelValue.numeroParcelas,
  (newVal, oldVal) => {
    console.log("numeroParcelas mudou de", oldVal, "para", newVal);
    if (newVal !== oldVal && !isEditMode.value && parcelas.value.length > 0) {
      // Regenera parcelas se mudou o número
      gerarParcelas();
    }
  }
);

watch(
  () => props.modelValue.valorTotal,
  (newVal, oldVal) => {
    console.log("valorTotal mudou de", oldVal, "para", newVal);
    if (newVal !== oldVal && !isEditMode.value && parcelas.value.length > 0) {
      // Regenera parcelas se mudou o valor
      gerarParcelas();
    }
  }
);

// ==================== LIFECYCLE ====================

onMounted(() => {
  console.log("RecebimentosTab onMounted - isEditMode:", isEditMode.value);

  // Se em modo de edição e tem parcelas, carrega e converte as datas
  if (isEditMode.value && props.modelValue.parcelas) {
    parcelas.value = props.modelValue.parcelas.map((p, index) => ({
      ...p,
      index,
      diasAtraso: p.diasAtraso || 0,
      // Garante que as datas estejam no formato correto
      dataVencimento: formatDateToISO(p.dataVencimento),
      dataRecebimento: p.dataRecebimento ? formatDateToISO(p.dataRecebimento) : null,
      dataEnvioCobranca: p.dataEnvioCobranca ? formatDateToISO(p.dataEnvioCobranca) : null,
    }));

    console.log("Parcelas carregadas e formatadas:", parcelas.value);
  }
});
</script>

<style scoped>
.recebimentos-container {
  padding: 0;
}

.parcelas-table {
  margin-top: 16px;
}

.parcelas-table :deep(.q-table__bottom) {
  background-color: #f5f5f5;
}

.parcelas-table :deep(thead th) {
  font-weight: 600;
  font-size: 12px;
}

.parcelas-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.parcela-card {
  background: #FBF6F2;
  border-radius: 16px;
  box-shadow: 0 6px 25px rgba(107, 62, 38, 0.08);
  overflow: hidden;
}

.parcela-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(to right, #FBF6F2, #ffffff);
  border-bottom: 1px solid #D7B899;
  padding: 10px 12px;
}

.parcela-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #2A1F1B;
}

.parcela-body {
  padding: 10px 12px 6px;
}

.parcela-body .field-label {
  font-size: 0.72rem;
  color: #8B7355;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.2px;
}

.parcela-actions {
  padding: 6px 12px 10px;
}

.parcelas-summary {
  margin-top: 12px;
  border-radius: 12px;
  background: #FBF6F2;
  border: 1px solid rgba(107, 62, 38, 0.12);
}

.compact-input :deep(.q-field__control) {
  min-height: 32px;
}

.compact-input :deep(.q-field__native),
.compact-input :deep(.q-field__input) {
  padding: 0 8px;
  font-size: 0.85rem;
}

.compact-input :deep(.q-field__append) {
  padding-left: 4px;
}
</style>
