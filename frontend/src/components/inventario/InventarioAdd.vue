<template>
  <q-dialog v-model="showModal" persistent @hide="onClose">
    <q-card class="q-pa-md" style="max-width: 600px; width: 100%">
      <q-card-section class="row items-center q-pb-none">
        <div class="text-h6 text-primary">Novo Inventário {{ props.mode }}</div>
        <q-space />
        <q-btn v-close-popup icon="close" flat round dense @click="onClose" />
      </q-card-section>

      <q-card-section>
        <q-form @submit="submeterFormulario" class="q-gutter-md">
          <div class="row q-col-gutter-md">
            <div class="col-12">
              <q-input
                v-model="formData.descricao"
                label="Descrição do Inventário *"
                outlined
                dense
                :rules="[(val) => !!val || 'Campo obrigatório']"
              />
            </div>
          </div>

          <!-- Seleção de grupos/subgrupos (para inventário por grupo) -->
          <div v-if="mode === 'GRUPO'" class="row q-col-gutter-md">
            <div class="col-12 col-md-6">
              <q-select
                v-model="selectedGrupo"
                :options="gruposOptions"
                option-value="id"
                option-label="descricao"
                label="Grupo de Produtos *"
                outlined
                dense
                :rules="[(val) => !!val || 'Campo obrigatório']"
                @update:model-value="carregarSubGrupos"
              />
            </div>
            <div class="col-12 col-md-6">
              <q-select
                v-model="selectedSubGrupo"
                :options="subgruposOptions"
                option-value="id"
                option-label="descricao"
                label="Subgrupo"
                outlined
                dense
                :disable="!selectedGrupo"
              />
            </div>
          </div>

          <div class="row q-col-gutter-md">
            <div class="col-12 col-md-6">
              <DateInput v-model="formData.dataAgendamento" label="Data de Agendamento *" />
            </div>
          </div>
          <div class="row justify-end q-mt-md">
            <q-btn label="Cancelar" color="negative" flat class="q-mr-sm" @click="onClose" />
            <q-btn label="Criar Inventário" type="submit" color="primary" :loading="isLoading" />
          </div>
        </q-form>
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, defineProps, defineEmits, watch } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import eventBus from "@/eventBus";
import DateInput from "@/components/forms/date/DateInput.vue";

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  mode: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["close", "created", "reloadListagem"]);

const showModal = ref(false);
const isLoading = ref(false);

const formData = ref({
  descricao: "",
  tipo: "GERAL",
  dataAgendamento: null,
});

// Estados para grupos e subgrupos
const gruposOptions = ref([]);
const subgruposOptions = ref([]);
const selectedGrupo = ref(null);
const selectedSubGrupo = ref(null);

// Sincroniza o estado visível do modal com a prop externa
watch(
  () => props.visible,
  (newValue) => {
    showModal.value = newValue;
    if (newValue && props.mode === "GRUPO") {
      carregarGrupos();
    }
  }
);

// Função para carregar grupos
const carregarGrupos = async () => {
  try {
    const response = await apiRequest("/api/grupoproduto");
    if (response && response.objeto) {
      gruposOptions.value = response.objeto;
    }
  } catch (error) {
    console.error("Erro ao carregar grupos:", error);
    $q.notify({
      color: "negative",
      message: "Não foi possível carregar os grupos de produtos.",
      icon: "error",
    });
  }
};

// Função para carregar subgrupos
const carregarSubGrupos = async (grupoId) => {
  if (!grupoId) {
    subgruposOptions.value = [];
    selectedSubGrupo.value = null;
    return;
  }

  try {
    const id = typeof grupoId === "object" ? grupoId.id : grupoId;
    const response = await apiRequest(`/api/subgrupoproduto/grupo/${id}`);

    if (response) {
      subgruposOptions.value = response;
    } else {
      subgruposOptions.value = [];
    }
  } catch (error) {
    console.error("Erro ao carregar subgrupos:", error);
    subgruposOptions.value = [];
  }
};

// Função para submeter o formulário
const submeterFormulario = async () => {
  isLoading.value = true;

  try {
    // Dados básicos para todos os tipos de inventário
    const inventarioData = {
      descricao: formData.value.descricao,
      tipo: props.mode,
      idUsuarioCriacao: sessionStorage.getItem("user_id"),
      dataAgendamento: formData.value.dataAgendamento ? `${formData.value.dataAgendamento}T00:00:00` : null,
      status: formData.value.dataAgendamento ? "AGENDADO" : "RASCUNHO",
      idGrupo: selectedGrupo.value ? selectedGrupo.value.id : null,
      idSubGrupo: selectedSubGrupo.value ? selectedSubGrupo.value.id : null,
    };

    // Adicionamos filtros específicos com base no modo
    if (props.mode === "GRUPO") {
      inventarioData.filtroGrupo = selectedGrupo.value?.id;
      if (selectedSubGrupo.value) {
        inventarioData.filtroSubGrupo = selectedSubGrupo.value.id;
      }
    }

    const response = await apiRequest("/api/inventarios/", "POST", inventarioData);

    if (response) {
      $q.notify({
        color: "positive",
        message: "Inventário criado com sucesso!",
        icon: "check_circle",
      });

      eventBus.emit("ajuste-realizado");
      //emit("created", response.data);
      onClose();
    } else {
      throw new Error("Resposta inválida do servidor");
    }
  } catch (error) {
    console.error("Erro ao criar inventário:", error);
    $q.notify({
      color: "negative",
      message: "Não foi possível criar o inventário.",
      icon: "error",
    });
  } finally {
    isLoading.value = false;
  }
};

// Função para fechar o modal
const onClose = () => {
  emit("close");
  // Resetar o formulário
  formData.value = {
    descricao: "",
    tipo: "GERAL",
    dataAgendamento: new Date().toISOString().substr(0, 10),
  };
  selectedGrupo.value = null;
  selectedSubGrupo.value = null;
  subgruposOptions.value = [];
};
</script>
