<template>
  <div class="pagseguro-configurations">
    <q-card class="config-card q-mb-lg" flat bordered>
      <q-card-section class="card-header">
        <div class="header-content">
          <div>
            <div class="text-h5 text-weight-medium text-primary">Configurações do PagSeguro</div>
            <div class="text-body2 text-grey-7 q-mt-xs">Configure os parâmetros da integração com o PagSeguro</div>
          </div>
          <div class="search-container">
            <q-input v-model="searchFilter" dense outlined placeholder="Buscar configuração..." class="search-input">
              <template v-slot:prepend>
                <q-icon name="search" color="grey-6" />
              </template>
              <template v-slot:append v-if="searchFilter">
                <q-icon name="clear" color="grey-6" class="cursor-pointer" @click="searchFilter = ''" />
              </template>
            </q-input>
          </div>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section class="q-pa-md q-gutter-sm actions-row">
        <q-btn
          color="primary"
          icon="wifi_protected_setup"
          label="Testar chave pública"
          :loading="testingPublicKey"
          @click="testPublicKey"
        />
      </q-card-section>

      <q-separator />

      <q-card-section class="q-pa-none">
        <q-table
          :rows="filteredConfigs"
          :columns="columnsConfigs"
          :loading="loading"
          flat
          dense
          class="configs-table"
          :rows-per-page-options="[5, 10, 25]"
          :pagination="{ rowsPerPage: 5 }"
          row-key="id"
          binary-state-sort
        >
          <template v-slot:body-cell-nome="props">
            <q-td :props="props" class="config-name-cell">
              <div class="config-name">
                <q-icon name="payment" size="18px" color="primary" class="q-mr-sm" />
                <span class="text-weight-medium">{{ props.value }}</span>
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-chave="props">
            <q-td :props="props">
              <q-chip :label="props.value" color="grey-3" text-color="grey-9" size="sm" class="config-key-chip" />
            </q-td>
          </template>

          <template v-slot:body-cell-valor="props">
            <q-td :props="props" class="config-value-cell">
              <template v-if="editingConfigId === props.row.id">
                <q-input
                  v-model="editingValue"
                  dense
                  outlined
                  autofocus
                  class="edit-input"
                  @keyup.enter="saveEdit(props.row)"
                  @blur="saveEdit(props.row)"
                  @keyup.esc="cancelEdit"
                >
                  <template v-slot:append>
                    <q-btn flat round dense icon="check" color="positive" size="sm" @click="saveEdit(props.row)" />
                    <q-btn flat round dense icon="close" color="negative" size="sm" @click="cancelEdit" />
                  </template>
                </q-input>
              </template>
              <template v-else>
                <div class="config-value" @click="startEditing(props.row)">
                  <span class="value-text">
                    {{ truncatedValue(props.value) }}
                    <q-tooltip v-if="isTruncated(props.value)">{{ props.value }}</q-tooltip>
                  </span>
                  <q-icon name="edit" size="16px" color="grey-5" class="edit-icon" />
                </div>
              </template>
            </q-td>
          </template>

          <template v-slot:body-cell-descricao="props">
            <q-td :props="props" class="description-cell">
              <div class="description-text">{{ props.value }}</div>
            </q-td>
          </template>

          <template v-slot:no-data>
            <div class="full-width row flex-center text-grey-6 q-gutter-sm q-pa-lg">
              <q-icon name="search_off" size="24px" />
              <span class="text-body1">
                {{ searchFilter ? "Nenhuma configuração encontrada para esse termo" : "Nenhuma configuração disponível" }}
              </span>
            </div>
          </template>
        </q-table>
      </q-card-section>

      <q-separator />

      <q-card-section class="q-pa-md q-gutter-sm">
        <div class="row items-center justify-between">
          <div>
            <div class="text-h6 text-weight-medium">Testes rápidos (PagSeguro)</div>
            <div class="text-body2 text-grey-7">Executa tokenização e um pagamento de R$1,00 em sandbox.</div>
          </div>
          <q-btn color="primary" icon="play_arrow" label="Testar configurações" :loading="testingAll" @click="runAllTests" />
        </div>
        <div class="q-mt-md column q-gutter-sm">
          <div class="row items-center" v-for="item in testItems" :key="item.key">
            <q-icon :name="statusIcon(item.status)" :color="statusColor(item.status)" size="20px" class="q-mr-sm" />
            <div class="column">
              <div class="text-body1">{{ item.label }}</div>
              <div class="text-caption text-grey-7">{{ item.message }}</div>
            </div>
          </div>
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

const pagseguroConfigs = ref([]);
const searchFilter = ref("");
const editingConfigId = ref(null);
const editingValue = ref("");
const loading = ref(false);
const VALUE_LIMIT = 60;
const testingPublicKey = ref(false);
const testingAll = ref(false);
const testResults = ref({
  token: { status: "pending", message: "Aguardando teste" },
  payment: { status: "pending", message: "Aguardando teste" },
  status: { status: "pending", message: "Aguardando teste" },
});
const pagSeguroSdkLoaded = ref(false);

const columnsConfigs = [
  { name: "nome", label: "Nome", field: "nome", align: "left", sortable: true, style: "width: 220px" },
  { name: "chave", label: "Chave", field: "chave", align: "left", sortable: true, style: "width: 220px" },
  { name: "valor", label: "Valor", field: "valor", align: "left", sortable: true, style: "min-width: 200px" },
  { name: "descricao", label: "Descrição", field: "descricao", align: "left", sortable: true },
];

const filteredConfigs = computed(() => {
  if (!searchFilter.value) {
    return pagseguroConfigs.value;
  }
  const filter = searchFilter.value.toLowerCase();
  return pagseguroConfigs.value.filter(
    (config) =>
      config.nome.toLowerCase().includes(filter) ||
      config.chave.toLowerCase().includes(filter) ||
      (config.valor || "").toLowerCase().includes(filter) ||
      (config.descricao || "").toLowerCase().includes(filter)
  );
});

const startEditing = (row) => {
  editingConfigId.value = row.id;
  editingValue.value = row.valor;
};

const cancelEdit = () => {
  editingConfigId.value = null;
  editingValue.value = "";
};

const truncatedValue = (val) => {
  if (val === null || val === undefined || val === "") return "Não definido";
  const str = String(val);
  return str.length > VALUE_LIMIT ? str.slice(0, VALUE_LIMIT) + "..." : str;
};

const isTruncated = (val) => {
  if (val === null || val === undefined) return false;
  return String(val).length > VALUE_LIMIT;
};

const saveEdit = async (row) => {
  if (editingConfigId.value === null) {
    return;
  }

  const newValue = editingValue.value || "";
  if (newValue === row.valor) {
    cancelEdit();
    return;
  }

  try {
    await updateConfig({ ...row, valor: newValue });
    $q.notify({
      type: "positive",
      message: "Configuração atualizada com sucesso!",
      position: "top-right",
      timeout: 3000,
    });
  } catch (error) {
    console.error("Erro ao salvar configuração:", error);
    $q.notify({
      type: "negative",
      message: "Não foi possível salvar a configuração.",
      position: "top-right",
    });
  } finally {
    cancelEdit();
  }
};

const updateConfig = async (config) => {
  const apiURL = `/api/configs/${config.id}`;
  await apiRequest(apiURL, "PUT", config);
  await loadPagSeguroConfigs();
};

const testPublicKey = async () => {
  testingPublicKey.value = true;
  try {
    const resp = await apiRequest("/api/v1/payments/pagseguro/public-key");
    $q.notify({
      type: "positive",
      message: resp && resp.publicKey ? "Chave pública carregada com sucesso." : "Chave não retornou valor.",
      position: "top-right",
      timeout: 3000,
    });
  } catch (error) {
    $q.notify({
      type: "negative",
      message: `Falha ao testar chave pública: ${error.message || error}`,
      position: "top-right",
    });
  } finally {
    testingPublicKey.value = false;
  }
};

const statusIcon = (status) => {
  if (status === "success") return "check_circle";
  if (status === "error") return "error";
  return "hourglass_empty";
};

const statusColor = (status) => {
  if (status === "success") return "positive";
  if (status === "error") return "negative";
  return "grey-7";
};

const testItems = computed(() => [
  {
    key: "token",
    label: "Tokenização",
    status: testResults.value.token.status,
    message: testResults.value.token.message,
  },
  {
    key: "payment",
    label: "Pagamento R$1,00 (sandbox)",
    status: testResults.value.payment.status,
    message: testResults.value.payment.message,
  },
  {
    key: "status",
    label: "Verificar status no backend",
    status: testResults.value.status?.status || "pending",
    message: testResults.value.status?.message || "Aguardando teste",
  },
]);

const runAllTests = async () => {
  testingAll.value = true;
  // reset statuses
  testResults.value = {
    token: { status: "pending", message: "Executando..." },
    payment: { status: "pending", message: "Aguardando token" },
    status: { status: "pending", message: "Aguardando pagamento" },
  };

  let tokenValue = null;
  let publicKey = null;
  let externalReference = `pagseguro-test-${Date.now()}`;
  testResults.value.payment.message = `Aguardando token (ref: ${externalReference})`;

  try {
    const pkResp = await apiRequest("/api/v1/payments/pagseguro/public-key");
    publicKey = pkResp?.publicKey;
    tokenValue = await generateEncryptedCard(publicKey);
    testResults.value.token = { status: "success", message: "Token gerado (encryptedCard) com sucesso." };
    testResults.value.status = {
      status: "pending",
      message: `Ref: ${externalReference} (aguardando verificação)`,
    };
  } catch (error) {
    testResults.value.token = { status: "error", message: error?.message || "Falha ao gerar token" };
    testResults.value.payment = { status: "error", message: "Pagamento não executado (sem token)" };
    testingAll.value = false;
    return;
  }

  try {
    const payResp = await apiRequest("/api/payments/card", "POST", {
      amount: 1.0,
      externalReference,
      description: "Teste PagSeguro R$1,00",
      token: tokenValue,
      installments: 1,
      payerEmail: "buyer-teste@example.com",
      payerName: "Teste PagSeguro",
      payerTaxId: "12345678909",
    });
    const status = payResp?.status || "desconhecido";
    const providerId = payResp?.providerPaymentId || "-";
    testResults.value.status = {
      status: "pending",
      message: `Ref: ${externalReference} | providerPaymentId: ${providerId}`,
    };
    if (status === "PAID" || status === "PENDING") {
      testResults.value.payment = {
        status: "success",
        message: `Status: ${status} | providerPaymentId: ${providerId}`,
      };
      await checkStatus(externalReference);
    } else {
      testResults.value.payment = {
        status: "error",
        message: `Status: ${status} | providerPaymentId: ${providerId} | msg: ${payResp?.message || ""}`,
      };
    }
  } catch (error) {
    testResults.value.payment = {
      status: "error",
      message: error?.message || "Falha ao executar pagamento",
    };
  } finally {
    testingAll.value = false;
  }
};

const checkStatus = async (externalReference) => {
  try {
    const resp = await apiRequest(`/api/payments/status?externalReference=${encodeURIComponent(externalReference)}`);
    const norm = resp?.normalizedStatus || resp?.status || "desconhecido";
    testResults.value.status = {
      status: norm === "PAID" || norm === "PENDING" ? "success" : "error",
      message: `Status backend: ${norm} | providerId: ${resp?.providerPaymentId || "-"}`,
    };
  } catch (error) {
    testResults.value.status = {
      status: "error",
      message: `Falha ao consultar backend: ${error?.message || error}`,
    };
  }
};

const ensurePagSeguroSdk = () => {
  if (pagSeguroSdkLoaded.value || typeof window !== "undefined") {
    if (window.PagSeguro) {
      pagSeguroSdkLoaded.value = true;
      return Promise.resolve();
    }
  }
  return new Promise((resolve, reject) => {
    const existing = document.querySelector('script[data-pagseguro-sdk]');
    if (existing) {
      existing.onload = () => { pagSeguroSdkLoaded.value = true; resolve(); };
      existing.onerror = reject;
      return;
    }
    const script = document.createElement("script");
    script.src = "https://assets.pagseguro.com.br/checkout-sdk-js/rc/dist/browser/pagseguro.min.js";
    script.async = true;
    script.dataset.pagseguroSdk = "true";
    script.onload = () => { pagSeguroSdkLoaded.value = true; resolve(); };
    script.onerror = reject;
    document.body.appendChild(script);
  });
};

const generateEncryptedCard = async (publicKey) => {
  await ensurePagSeguroSdk();
  if (!window.PagSeguro) throw new Error("SDK PagSeguro não carregado");
  const encrypted = window.PagSeguro.encryptCard({
    publicKey,
    holder: "Teste PagSeguro",
    number: "4111111111111111",
    expMonth: "12",
    expYear: "2030",
    securityCode: "123",
  });
  if (encrypted?.hasErrors) {
    throw new Error("Falha ao criptografar cartão");
  }
  return encrypted?.encryptedCard || encrypted?.encrypted;
};

const loadPagSeguroConfigs = async () => {
  loading.value = true;
  try {
    const response = await apiRequest("/api/configs");
    if (response) {
      pagseguroConfigs.value = response.filter((config) =>
        config.chave.startsWith("pagseguro_")
      );
    }
  } catch (error) {
    console.error("Erro ao carregar configurações do PagSeguro:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao carregar configurações do PagSeguro",
      position: "top-right",
    });
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadPagSeguroConfigs();
});
</script>

<style scoped lang="scss">
.pagseguro-configurations {
  margin: 0 auto;
  padding: 0 16px;
}

.config-card {
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.card-header {
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  .header-content {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .search-container {
      flex: 1;
      max-width: 400px;
      margin-left: 16px;
    }
  }
}

.configs-table {
  .config-name-cell {
    .config-name {
      display: flex;
      align-items: center;
    }
  }
  .q-tr {
    height: 38px;
  }

  .config-key-chip {
    font-family: "Roboto Mono", monospace;
    font-size: 11px;
    border-radius: 6px;
  }

  .config-value-cell {
    .config-value {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      padding: 8px 12px;
      border-radius: 6px;
      cursor: pointer;
      transition: background-color 0.2s ease;
    }

    .config-value:hover {
      background-color: #f5f5f5;
    }

    .edit-icon {
      opacity: 0;
      transition: opacity 0.2s ease;
      margin-left: 8px;
    }

    .config-value:hover .edit-icon {
      opacity: 1;
    }

    .edit-input {
      .q-field__control {
        min-height: 40px;
      }
    }
  }

  .description-cell {
    .description-text {
      color: #5f6368;
      line-height: 1.4;
    }
  }
}

.actions-row {
  background: #fafafa;
}
</style>
