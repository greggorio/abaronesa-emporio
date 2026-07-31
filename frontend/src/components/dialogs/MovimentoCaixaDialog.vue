<template>
  <q-dialog v-model="dialogVisible" persistent>
    <q-card class="movimento-dialog">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon :name="iconeTipo" :color="corTipo" size="xs" />
            <h5 class="dialog-title q-my-none q-ml-sm">{{ tituloDialog }}</h5>
          </div>
          <q-btn icon="close" flat round dense v-close-popup class="close-btn" />
        </div>
      </q-card-section>

      <q-card-section class="form-section">
        <q-form @submit.prevent="handleSubmit" ref="formRef">
          <div class="row q-col-gutter-md">
            <!-- Tipo de Movimento -->
            <div class="col-12" v-if="!tipoFixo">
              <div class="input-wrapper">
                <q-select
                  v-model="formData.tipo"
                  label="Tipo de Movimento"
                  :options="tiposDisponiveis"
                  option-label="label"
                  option-value="value"
                  emit-value
                  map-options
                  :rules="[(val) => !!val || 'Selecione o tipo de movimento']"
                  class="styled-select"
                  outlined
                  dense
                />
              </div>
            </div>

            <!-- Valor -->
            <div class="col-12">
              <div class="valor-input-wrapper">
                <q-input
                  v-model="formData.valor"
                  label="Valor"
                  type="number"
                  prefix="R$"
                  :rules="[(val) => !!val || 'Informe o valor', (val) => val > 0 || 'O valor deve ser maior que zero']"
                  class="valor-input"
                  outlined
                  dense
                  step="0.01"
                />
              </div>
            </div>

            <!-- Meio de Pagamento -->
            <div class="col-12" v-if="mostrarMeioPagamento">
              <div class="input-wrapper">
                <q-select
                  v-model="formData.meioPagamento"
                  label="Forma de Pagamento"
                  :options="meiosPagamento"
                  option-label="label"
                  option-value="value"
                  emit-value
                  map-options
                  :rules="[(val) => !!val || 'Selecione a forma de pagamento']"
                  class="styled-select"
                  outlined
                  dense
                />
              </div>
            </div>

            <!-- Tipo de Operação -->
            <div class="col-12" v-if="formData.tipo === 'OUTROS'">
              <div class="operacao-wrapper">
                <div class="operacao-label">Tipo de Operação</div>
                <q-option-group
                  v-model="formData.operacao"
                  :options="tiposOperacaoStyled"
                  inline
                  :rules="[(val) => !!val || 'Selecione o tipo de operação']"
                  class="operacao-group"
                />
              </div>
            </div>

            <!-- Observação -->
            <div class="col-12">
              <div class="input-wrapper">
                <q-input
                  v-model="formData.observacao"
                  label="Observação"
                  type="textarea"
                  rows="3"
                  :rules="[(val) => !!val || 'Informe o motivo/observação']"
                  :placeholder="placeholderObservacao"
                  class="styled-textarea"
                  outlined
                  dense
                />
              </div>
            </div>

            <!-- Afeta Caixa -->
            <div class="col-12">
              <div class="checkbox-wrapper">
                <q-checkbox
                  v-model="formData.afetaCaixa"
                  label="Este movimento afeta o saldo do caixa"
                  :disable="tipoFixo && formData.tipo !== 'OUTROS'"
                  class="styled-checkbox"
                />
              </div>
            </div>
          </div>
        </q-form>
      </q-card-section>

      <q-card-actions class="actions-section">
        <q-btn flat label="Cancelar" @click="handleCancel" class="cancel-btn" />
        <q-btn unelevated label="Confirmar" :color="corTipo" @click="handleSubmit" :loading="saving" :icon="iconeTipo" class="confirm-btn" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  tipoMovimento: {
    type: String,
    default: null,
  },
});

const emit = defineEmits(["update:modelValue", "saved", "dialogoConcluido"]);

const $q = useQuasar();
const { apiRequest } = useApiRequest();

// State
const formRef = ref(null);
const saving = ref(false);
const formData = ref({
  tipo: null,
  valor: null,
  meioPagamento: "DINHEIRO",
  afetaCaixa: true,
  operacao: null,
  observacao: "",
  referenciaId: null,
  referenciaTipo: null,
});

// Options
const tiposDisponiveis = [
  { label: "Caixa Inicial", value: "CAIXA_INICIAL" },
  { label: "Reforço", value: "REFORCO" },
  { label: "Sangria", value: "SANGRIA" },
  { label: "Outros", value: "OUTROS" },
];

const meiosPagamento = [
  { label: "Dinheiro", value: "DINHEIRO" },
  { label: "Cartão", value: "CARTAO" },
  { label: "Pix", value: "PIX" },
  { label: "Vale Cartão", value: "VALE_CARTAO" },
];

const tiposOperacao = [
  { label: "Entrada", value: "ENTRADA" },
  { label: "Saída", value: "SAIDA" },
];

const tiposOperacaoStyled = computed(() => [
  {
    label: "Entrada",
    value: "ENTRADA",
    color: "positive",
  },
  {
    label: "Saída",
    value: "SAIDA",
    color: "negative",
  },
]);

// Computed
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const tipoFixo = computed(() => !!props.tipoMovimento);

const tituloDialog = computed(() => {
  const tipo = formData.value.tipo;
  switch (tipo) {
    case "CAIXA_INICIAL":
      return "Registrar Caixa Inicial";
    case "REFORCO":
      return "Registrar Reforço de Caixa";
    case "SANGRIA":
      return "Registrar Sangria";
    case "OUTROS":
      return "Registrar Movimento";
    default:
      return "Movimento de Caixa";
  }
});

const iconeTipo = computed(() => {
  const tipo = formData.value.tipo;
  switch (tipo) {
    case "CAIXA_INICIAL":
      return "o_savings";
    case "REFORCO":
      return "o_add_circle";
    case "SANGRIA":
      return "o_remove_circle";
    case "OUTROS":
      return "o_more_horiz";
    default:
      return "o_account_balance_wallet";
  }
});

const corTipo = computed(() => {
  const tipo = formData.value.tipo;
  switch (tipo) {
    case "CAIXA_INICIAL":
      return "primary";
    case "REFORCO":
      return "positive";
    case "SANGRIA":
      return "negative";
    case "OUTROS":
      return "info";
    default:
      return "primary";
  }
});

const placeholderObservacao = computed(() => {
  const tipo = formData.value.tipo;
  switch (tipo) {
    case "CAIXA_INICIAL":
      return "Ex: Abertura do caixa do dia";
    case "REFORCO":
      return "Ex: Reforço para troco";
    case "SANGRIA":
      return "Ex: Depósito bancário";
    case "OUTROS":
      return "Descreva o movimento";
    default:
      return "Observação";
  }
});

const mostrarMeioPagamento = computed(() => {
  return formData.value.tipo === "OUTROS";
});

// Watch
watch(
  () => props.tipoMovimento,
  (newVal) => {
    if (newVal) {
      formData.value.tipo = newVal;

      switch (newVal) {
        case "CAIXA_INICIAL":
          formData.value.operacao = "ENTRADA";
          formData.value.meioPagamento = "DINHEIRO";
          break;
        case "REFORCO":
          formData.value.operacao = "ENTRADA";
          formData.value.meioPagamento = "DINHEIRO";
          break;
        case "SANGRIA":
          formData.value.operacao = "SAIDA";
          formData.value.meioPagamento = "DINHEIRO";
          break;
      }
    }
  },
  { immediate: true }
);

// Methods
async function handleSubmit() {
  const valid = await formRef.value.validate();
  if (!valid) return;

  if (formData.value.tipo !== "OUTROS") {
    switch (formData.value.tipo) {
      case "CAIXA_INICIAL":
      case "REFORCO":
        formData.value.operacao = "ENTRADA";
        break;
      case "SANGRIA":
        formData.value.operacao = "SAIDA";
        break;
    }
  }

  saving.value = true;
  try {
    const response = await apiRequest("/api/movimento-caixa/manual", "POST", formData.value);

    $q.notify({
      type: "positive",
      message: "Movimento registrado com sucesso!",
      position: "top",
    });

    emit("saved", response);
    emit("dialogoConcluido");
    handleCancel();
  } catch (error) {
    $q.notify({
      type: "negative",
      message: error.message || "Erro ao registrar movimento",
      position: "top",
    });
  } finally {
    saving.value = false;
  }
}

function handleCancel() {
  formData.value = {
    tipo: props.tipoMovimento || null,
    valor: null,
    meioPagamento: "DINHEIRO",
    afetaCaixa: true,
    operacao: null,
    observacao: "",
    referenciaId: null,
    referenciaTipo: null,
  };

  dialogVisible.value = false;
}

// Lifecycle
onMounted(() => {
  if (props.tipoMovimento) {
    handleCancel();
  }
});
</script>

<style scoped>
.movimento-dialog {
  min-width: 500px;
  max-width: 600px;
  border-radius: 16px;
  box-shadow: 0 6px 25px rgba(0, 0, 0, 0.07);
  overflow: hidden;
}

.header-section {
  padding: 16px 20px;
  background: linear-gradient(to right, #f8fafd, #ffffff);
  border-bottom: 1px solid #f0f4f8;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-wrapper {
  display: flex;
  align-items: center;
}

.dialog-title {
  font-weight: 600;
  font-size: 1.1rem;
  color: #2c3e50;
}

.close-btn {
  color: #7f8c8d;
}

.close-btn:hover {
  color: #34495e;
}

.form-section {
  padding: 20px;
}

.input-wrapper {
  margin-bottom: 4px;
}

.styled-select :deep(.q-field__control) {
  border-radius: 10px;
  background-color: #f8fafd;
  border: 1px solid #e8ecf0;
  transition: all 0.2s ease;
}

.styled-select:hover :deep(.q-field__control) {
  border-color: #d0d7de;
}

.styled-select.q-field--focused :deep(.q-field__control) {
  background-color: #ffffff;
  border-color: #3498db;
  box-shadow: 0 0 0 3px rgba(52, 152, 219, 0.1);
}

.valor-input-wrapper {
  margin-bottom: 4px;
}

.valor-input :deep(.q-field__control) {
  border-radius: 10px;
  background-color: #f8fafd;
  border: 1px solid #e8ecf0;
  transition: all 0.2s ease;
}

.valor-input :deep(.q-field__control input) {
  font-size: 1.2rem;
  font-weight: 600;
  color: #34495e;
}

.valor-input:hover :deep(.q-field__control) {
  border-color: #d0d7de;
}

.valor-input.q-field--focused :deep(.q-field__control) {
  background-color: #ffffff;
  border-color: #3498db;
  box-shadow: 0 0 0 3px rgba(52, 152, 219, 0.1);
}

.styled-textarea :deep(.q-field__control) {
  border-radius: 10px;
  background-color: #f8fafd;
  border: 1px solid #e8ecf0;
  transition: all 0.2s ease;
}

.styled-textarea:hover :deep(.q-field__control) {
  border-color: #d0d7de;
}

.styled-textarea.q-field--focused :deep(.q-field__control) {
  background-color: #ffffff;
  border-color: #3498db;
  box-shadow: 0 0 0 3px rgba(52, 152, 219, 0.1);
}

.operacao-wrapper {
  background-color: #f8fafd;
  padding: 16px;
  border-radius: 10px;
  border: 1px solid #e8ecf0;
  transition: all 0.2s ease;
}

.operacao-wrapper:hover {
  border-color: #d0d7de;
}

.operacao-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: #7f8c8d;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.operacao-group :deep(.q-radio) {
  margin-right: 20px;
}

.operacao-group :deep(.q-radio__label) {
  font-weight: 500;
  color: #34495e;
}

.checkbox-wrapper {
  background-color: #f8fafd;
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid #e8ecf0;
  transition: all 0.2s ease;
}

.checkbox-wrapper:hover {
  border-color: #d0d7de;
}

.styled-checkbox :deep(.q-checkbox__label) {
  font-size: 0.9rem;
  color: #34495e;
}

.actions-section {
  padding: 16px 20px;
  background-color: #f8fafd;
  border-top: 1px solid #f0f4f8;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn {
  color: #7f8c8d;
  background-color: transparent;
  border-radius: 8px;
  padding: 8px 20px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.cancel-btn:hover {
  background-color: #e8ecf0;
  color: #34495e;
}

.confirm-btn {
  border-radius: 8px;
  padding: 8px 24px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
}

.confirm-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* Ajustes responsivos */
@media (max-width: 600px) {
  .movimento-dialog {
    min-width: 90vw;
  }
}
</style>
