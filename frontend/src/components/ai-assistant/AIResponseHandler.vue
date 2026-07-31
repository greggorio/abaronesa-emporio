<template>
  <div class="ai-response-handler">
    <!-- Diálogo de Fornecedor -->
    <FornecedorDialog
      v-if="activeDialog === 'fornecedor'"
      v-model="isDialogOpen"
      :initial-data="dialogData"
      @saved="handleSaved"
      @cancelled="handleCancelled"
    />

    <!-- Diálogo de Resposta Genérica -->
    <GenericResponseDialog v-if="activeDialog === 'geral'" v-model="isDialogOpen" :message="genericMessage" @close="handleCancelled" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useQuasar } from "quasar";
import { useAIAssistant } from "@/composables/useAIAssistant";
import { useAIAssistantStore } from "@/stores/aiAssistant";

// Importações dos diálogos
import FornecedorDialog from "@/components/ai-dialogs/FornecedorDialog.vue";
import GenericResponseDialog from "@/components/ai-dialogs/GenericResponseDialog.vue";

// Props
const props = defineProps({
  response: {
    type: Object,
    default: null,
  },
});

// Emits
const emit = defineEmits(["processed", "error"]);

// Composables
const $q = useQuasar();
const { identifyCommandType, requiresDialog, validateResponse } = useAIAssistant();
const store = useAIAssistantStore();

// Estado local
const isDialogOpen = ref(false);
const activeDialog = ref(null);
const dialogData = ref(null);
const genericMessage = ref("");

// Computed
const currentResponse = computed(() => props.response || store.currentResponse);

// Watchers
watch(
  currentResponse,
  (newResponse) => {
    if (newResponse) {
      handleResponse(newResponse);
    }
  },
  { immediate: true }
);

// Watch também o response via props
watch(
  () => props.response,
  (newResponse) => {
    if (newResponse) {
      handleResponse(newResponse);
    }
  },
  { immediate: true }
);

// Métodos
const handleResponse = (response) => {
  console.log("AIResponseHandler - handleResponse chamado:", response);

  if (!response) return;

  const type = identifyCommandType(response);
  console.log("Tipo identificado:", type);

  // Verificar se é erro
  if (type === "erro" || response.tipo === "erro") {
    showError(response.retorno || response.mensagem || "Erro ao processar comando");
    return;
  }

  // Se for fornecedor, abrir diálogo
  if (type === "fornecedor") {
    console.log("Abrindo diálogo de fornecedor");

    // Validar resposta antes de abrir diálogo
    const validation = validateResponse(response);

    if (!validation.valid) {
      showValidationErrors(validation.errors);
      return;
    }

    // Preparar e abrir diálogo de fornecedor
    prepareDialog(type, response);
  } else {
    // Qualquer outra resposta - mostrar em diálogo simples
    console.log("Mostrando resposta genérica");
    if (response.retorno || response.mensagem) {
      showGenericResponse(response.retorno || response.mensagem);
    } else {
      showGenericResponse("Comando processado com sucesso");
    }
  }
};

const prepareDialog = (type, response) => {
  console.log("Preparando diálogo:", type, response);

  // Mapear dados da resposta para o formato esperado pelo diálogo
  const mappedData = mapResponseToDialogData(type, response);

  dialogData.value = mappedData;
  activeDialog.value = type;

  // Pequeno delay para garantir que o Vue processe as mudanças
  setTimeout(() => {
    isDialogOpen.value = true;
  }, 100);

  // Armazenar no store
  store.openDialog(type);
};

const mapResponseToDialogData = (type, response) => {
  if (type === "fornecedor") {
    return {
      razao_social: response.razao_social || response.razaoSocial || response.nome,
      nomeFantasia: response.nomeFantasia || response.nome_fantasia,
      cnpj: response.cnpj,
      telefone: response.telefone,
      email: response.email,
      contato: response.contato,
      endereco: response.endereco,
      cidade: response.cidade,
      estado: response.estado,
      cep: response.cep,
      ativo: response.ativo !== undefined ? response.ativo : true,
    };
  }

  return response;
};

const showGenericResponse = (message) => {
  genericMessage.value = message;
  activeDialog.value = "geral";
  isDialogOpen.value = true;
};

const showError = (message) => {
  $q.notify({
    type: "negative",
    message: message,
    position: "top",
    timeout: 5000,
    actions: [{ label: "Fechar", color: "white", handler: () => {} }],
  });

  emit("error", { message });
};

const showValidationErrors = (errors) => {
  const message = errors.length > 1 ? `Dados incompletos:\n${errors.join("\n")}` : errors[0];

  $q.notify({
    type: "warning",
    message: message,
    position: "top",
    timeout: 4000,
    multiLine: errors.length > 1,
  });
};

// Handlers dos diálogos
const handleSaved = (data) => {
  isDialogOpen.value = false;
  activeDialog.value = null;
  dialogData.value = null;

  // Limpar store
  store.closeDialog();

  // Notificar sucesso
  $q.notify({
    type: "positive",
    message: "Fornecedor salvo com sucesso!",
    position: "top",
    timeout: 3000,
  });

  emit("processed", {
    type: "saved",
    data,
  });
};

const handleCancelled = () => {
  isDialogOpen.value = false;
  activeDialog.value = null;
  dialogData.value = null;
  genericMessage.value = "";

  // Limpar store
  store.closeDialog();

  emit("processed", {
    type: "cancelled",
  });
};

// Expor métodos para uso externo se necessário
defineExpose({
  handleResponse,
});
</script>

<style scoped>
.ai-response-handler {
  /* Container invisível - os diálogos são renderizados como portals */
}
</style>
