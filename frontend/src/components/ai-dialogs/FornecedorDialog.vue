<template>
  <q-dialog :model-value="modelValue" @update:model-value="updateDialog" persistent>
    <q-card style="width: 800px; max-width: 90vw">
      <!-- Header -->
      <q-toolbar class="bg-primary text-white">
        <q-icon name="o_business" size="28px" class="q-mr-sm" />
        <q-toolbar-title>
          <div class="text-h6">Cadastrar Fornecedor</div>
          <div class="text-caption">Cadastro rápido via Assistente IA</div>
        </q-toolbar-title>
        <q-btn flat round dense icon="close" @click="onCancel" />
      </q-toolbar>

      <!-- Avisos da IA (se houver) -->
      <div v-if="hasWarnings" class="q-pa-md q-pb-none">
        <q-banner class="bg-amber-1" rounded>
          <template v-slot:avatar>
            <q-icon name="o_tips_and_updates" color="amber-8" />
          </template>
          <div class="text-body2 text-amber-9">
            <strong>Sugestões da IA:</strong>
            <ul class="q-my-xs q-pl-md">
              <li v-for="(warning, index) in warnings" :key="index">{{ warning }}</li>
            </ul>
          </div>
        </q-banner>
      </div>

      <!-- Formulário -->
      <q-card-section class="q-pa-md">
        <q-form ref="formRef" @submit="onSubmit">
          <!-- Status -->
          <div class="row q-col-gutter-md q-mb-md">
            <div class="col-12">
              <q-card flat class="bg-grey-1">
                <q-card-section class="q-py-sm">
                  <div class="row items-center">
                    <q-toggle v-model="form.ativo" label="Fornecedor Ativo" color="positive" dense />
                    <q-space />
                    <q-avatar :color="form.ativo ? 'positive' : 'negative'" text-color="white" size="28px">
                      <q-icon :name="form.ativo ? 'o_check' : 'o_close'" size="16px" />
                    </q-avatar>
                  </div>
                </q-card-section>
              </q-card>
            </div>
          </div>

          <!-- Dados em duas colunas -->
          <div class="row q-col-gutter-md">
            <!-- Coluna Esquerda -->
            <div class="col-12 col-md-6">
              <!-- Dados da Empresa -->
              <q-card flat class="bg-grey-1 q-mb-md">
                <q-card-section>
                  <div class="row items-center q-mb-sm">
                    <q-icon name="o_business" size="20px" color="primary" class="q-mr-sm" />
                    <div class="text-subtitle2 text-weight-medium">Dados da Empresa</div>
                  </div>

                  <div class="q-gutter-sm">
                    <q-input
                      v-model="form.razaoSocial"
                      label="Razão Social"
                      outlined
                      dense
                      :rules="[(val) => !!val || 'Razão Social é obrigatória']"
                    />

                    <q-input
                      v-model="form.nomeFantasia"
                      label="Nome Fantasia"
                      outlined
                      dense
                    />

                    <q-input
                      v-model="form.cnpj"
                      label="CNPJ"
                      outlined
                      dense
                      mask="##.###.###/####-##"
                      unmasked-value
                      :rules="[validateCNPJ]"
                    />
                  </div>
                </q-card-section>
              </q-card>

              <!-- Contato -->
              <q-card flat class="bg-grey-1">
                <q-card-section>
                  <div class="row items-center q-mb-sm">
                    <q-icon name="o_contact_phone" size="20px" color="primary" class="q-mr-sm" />
                    <div class="text-subtitle2 text-weight-medium">Contato</div>
                  </div>

                  <div class="q-gutter-sm">
                    <q-input
                      v-model="form.contato"
                      label="Nome do Contato"
                      outlined
                      dense
                    >
                      <template v-slot:prepend>
                        <q-icon name="o_person" size="18px" />
                      </template>
                    </q-input>

                    <q-input
                      v-model="form.email"
                      label="Email"
                      outlined
                      dense
                      type="email"
                      :rules="[(val) => !val || isValidEmail(val) || 'Email inválido']"
                    >
                      <template v-slot:prepend>
                        <q-icon name="o_email" size="18px" />
                      </template>
                    </q-input>

                    <q-input
                      v-model="form.telefone"
                      label="Telefone"
                      outlined
                      dense
                      mask="(##) #####-####"
                      unmasked-value
                    >
                      <template v-slot:prepend>
                        <q-icon name="o_phone" size="18px" />
                      </template>
                    </q-input>
                  </div>
                </q-card-section>
              </q-card>
            </div>

            <!-- Coluna Direita -->
            <div class="col-12 col-md-6">
              <!-- Endereço -->
              <q-card flat class="bg-grey-1">
                <q-card-section>
                  <div class="row items-center q-mb-sm">
                    <q-icon name="o_location_on" size="20px" color="primary" class="q-mr-sm" />
                    <div class="text-subtitle2 text-weight-medium">Endereço</div>
                  </div>

                  <div class="q-gutter-sm">
                    <div class="row q-col-gutter-sm">
                      <div class="col-5">
                        <q-input
                          v-model="form.cep"
                          label="CEP"
                          outlined
                          dense
                          mask="#####-###"
                          unmasked-value
                          @blur="buscarCEP"
                        >
                          <template v-slot:append>
                            <q-btn
                              v-if="!buscandoCep && form.cep?.length === 8"
                              icon="o_search"
                              flat
                              dense
                              round
                              size="sm"
                              @click="buscarCEP"
                            />
                            <q-spinner v-if="buscandoCep" size="20px" color="primary" />
                          </template>
                        </q-input>
                      </div>
                      <div class="col-7">
                        <q-input v-model="form.endereco" label="Endereço" outlined dense />
                      </div>
                    </div>

                    <div class="row q-col-gutter-sm">
                      <div class="col-8">
                        <q-input v-model="form.cidade" label="Cidade" outlined dense />
                      </div>
                      <div class="col-4">
                        <q-input v-model="form.estado" label="UF" outlined dense mask="AA" />
                      </div>
                    </div>
                  </div>
                </q-card-section>
              </q-card>
            </div>
          </div>
        </q-form>
      </q-card-section>

      <!-- Actions -->
      <q-separator />
      <q-card-actions align="right" class="q-pa-md">
        <q-btn label="Cancelar" flat dense @click="onCancel" />
        <q-btn
          label="Salvar Fornecedor"
          color="primary"
          unelevated
          dense
          icon-right="o_save"
          :loading="loading"
          @click="onSubmit"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import { useAIService } from "@/services/aiService";

// Props
const props = defineProps({
  modelValue: {
    type: Boolean,
    required: true,
  },
  initialData: {
    type: Object,
    default: null,
  },
});

// Emits
const emit = defineEmits(["update:modelValue", "saved", "cancelled"]);

// Composables
const $q = useQuasar();
const { apiRequest } = useApiRequest();
const aiService = useAIService();

// Refs
const formRef = ref(null);
const loading = ref(false);
const buscandoCep = ref(false);

// Estado do formulário
const form = reactive({
  razaoSocial: "",
  nomeFantasia: "",
  cnpj: "",
  telefone: "",
  email: "",
  contato: "",
  endereco: "",
  cidade: "",
  estado: "",
  cep: "",
  ativo: true,
});

// Computed
const hasWarnings = computed(() => {
  return props.initialData?.avisos && props.initialData.avisos.length > 0;
});

const warnings = computed(() => {
  return props.initialData?.avisos || [];
});

// Métodos
const updateDialog = (value) => {
  emit("update:modelValue", value);
};

const populateForm = (data) => {
  if (!data) return;

  // Mapear dados da IA para o formulário
  form.razaoSocial = data.razao_social || data.nome || "";
  form.nomeFantasia = data.nomeFantasia || "";
  form.cnpj = data.cnpj || "";
  form.telefone = data.telefone || "";
  form.email = data.email || "";
  form.contato = data.contato || "";
  form.endereco = data.endereco || "";
  form.cidade = data.cidade || "";
  form.estado = data.estado || "";
  form.cep = data.cep || "";
  form.ativo = data.ativo !== undefined ? data.ativo : true;
};

// Watchers
watch(
  () => props.initialData,
  (newData) => {
    if (newData) {
      populateForm(newData);
    }
  },
  { immediate: true }
);

const validateCNPJ = (val) => {
  if (!val) return true; // CNPJ não é obrigatório
  return aiService.validateCNPJ(val) || "CNPJ inválido";
};

const isValidEmail = (email) => {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return re.test(email);
};

const buscarCEP = async () => {
  if (!form.cep || form.cep.length !== 8) return;

  buscandoCep.value = true;

  try {
    const response = await fetch(`https://viacep.com.br/ws/${form.cep}/json/`);
    const data = await response.json();

    if (!data.erro) {
      form.endereco = data.logradouro || "";
      form.cidade = data.localidade || "";
      form.estado = data.uf || "";

      $q.notify({
        type: "positive",
        message: "CEP encontrado!",
        position: "top",
        timeout: 1500,
      });
    } else {
      $q.notify({
        type: "warning",
        message: "CEP não encontrado",
        position: "top",
      });
    }
  } catch (error) {
    console.error("Erro ao buscar CEP:", error);
  } finally {
    buscandoCep.value = false;
  }
};

const onSubmit = async () => {
  const valid = await formRef.value.validate();
  if (!valid) return;

  loading.value = true;

  try {
    // Preparar dados para envio
    const requestData = {
      razaoSocial: form.razaoSocial,
      nomeFantasia: form.nomeFantasia,
      cnpj: form.cnpj,
      telefone: form.telefone,
      email: form.email,
      contato: form.contato,
      endereco: form.endereco,
      cidade: form.cidade,
      estado: form.estado,
      cep: form.cep,
      ativo: form.ativo,
    };

    // Remover campos vazios
    Object.keys(requestData).forEach((key) => {
      if (requestData[key] === null || requestData[key] === undefined || requestData[key] === "") {
        delete requestData[key];
      }
    });

    // Enviar para o backend
    await apiRequest("/api/fornecedores", "POST", requestData);

    $q.notify({
      type: "positive",
      message: "Fornecedor cadastrado com sucesso!",
      position: "top",
    });

    emit("saved", {
      tipo: "fornecedor",
      ...requestData,
    });

    // Fechar diálogo
    updateDialog(false);
  } catch (error) {
    console.error("Erro ao salvar fornecedor:", error);

    $q.notify({
      type: "negative",
      message: "Erro ao salvar fornecedor",
      caption: error.response?.data?.message || error.message,
      position: "top",
    });
  } finally {
    loading.value = false;
  }
};

const onCancel = () => {
  // Confirmar cancelamento se houver dados preenchidos
  const hasData = Object.values(form).some((val) => val && val !== true);

  if (hasData) {
    $q.dialog({
      title: "Cancelar Cadastro",
      message: "Deseja realmente cancelar? Os dados preenchidos serão perdidos.",
      cancel: true,
      persistent: true,
    }).onOk(() => {
      emit("cancelled");
      updateDialog(false);
    });
  } else {
    emit("cancelled");
    updateDialog(false);
  }
};
</script>

<style scoped>
.full-height {
  height: 100%;
}
</style>
