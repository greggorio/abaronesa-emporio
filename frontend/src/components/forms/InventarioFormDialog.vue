<template>
  <q-page>
    <!-- Loading -->
    <div v-if="loading" class="q-pa-md text-center">
      <q-spinner-dots color="primary" size="40px" />
      <p>Carregando dados do inventário...</p>
    </div>

    <!-- Erro -->
    <div v-else-if="error" class="text-center q-pa-xl">
      <q-icon name="error" color="negative" size="50px" />
      <p class="q-mt-md text-negative">{{ error }}</p>
      <q-btn label="Tentar Novamente" color="primary" @click="carregarInventario" class="q-mt-md" />
    </div>

    <!-- Conteúdo do Inventário -->
    <template v-else-if="inventarioLocal">
      <InventarioHeader :inventario="inventarioLocal" @update:inventario="handleInventarioUpdate" />
      <InventarioEstatisticas :estatisticas="inventarioLocal.estatisticas" />

      <InventarioTabela
        :produtos="inventarioLocal.itens"
        @update:inventario="handleInventarioUpdate"
        :id_inventario="inventarioLocal.id"
        :status="inventarioLocal.status"
      />
      <InventarioAbas :inventario="inventarioLocal" />
      <InventarioEquipe :equipe="inventarioLocal.equipe" />
    </template>

    <!-- Modal de Criação de Inventário -->
    <InventarioModal :visible="showInventarioModal" :mode="inventarioMode" @close="fecharModal" />
  </q-page>
</template>

<script setup>
import { ref, onMounted, computed, onBeforeUnmount, watch } from "vue";
import { useRouter } from "vue-router";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import InventarioHeader from "src/components/inventario/InventarioHeader.vue";
import InventarioModal from "src/components/inventario/InventarioAdd.vue";
import InventarioEstatisticas from "src/components/inventario/InventarioEstatisticas.vue";
import InventarioEquipe from "src/components/inventario/InventarioEquipe.vue";
import InventarioAbas from "src/components/inventario/InventarioAbas.vue";
import eventBus from "@/eventBus";

import InventarioTabela from "src/components/inventario/InventarioTabela.vue";

const router = useRouter();
const $q = useQuasar();
const { apiRequest } = useApiRequest();

const emit = defineEmits(["reloadListagem"]);

const props = defineProps({
  form_data: {
    type: Object,
    default: () => ({}),
  },
  modelValue: {
    type: Object,
    default: () => ({}),
  },
});

// Estados
const inventarioLocal = ref(null);
const loading = ref(false);
const error = ref(null);

// Estado do modal de criação de inventário
const showInventarioModal = ref(false);
const inventarioMode = ref("");

// Função para carregar os dados do inventário
const carregarInventario = async () => {
  // Tenta pegar o ID do form_data ou modelValue
  const inventarioId = props.form_data?.id || props.modelValue?.id;

  if (!inventarioId) {
    console.warn("ID do inventário não fornecido");
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    console.log(`Buscando inventário ID: ${inventarioId}`);
    const response = await apiRequest(`/api/inventarios/${inventarioId}/detalhado`);
    inventarioLocal.value = response.data || response;
    console.log("Dados do inventário carregados:", inventarioLocal.value);

    // Atualizar breadcrumbs com os dados carregados
    atualizarBreadcrumbs();
  } catch (err) {
    console.error("Erro ao carregar inventário:", err);
    error.value = err.message || "Erro ao carregar dados do inventário";
    $q.notify({
      type: "negative",
      message: error.value,
      position: "top",
    });
  } finally {
    loading.value = false;
  }
};

// Função para atualizar breadcrumbs
const atualizarBreadcrumbs = () => {
  if (inventarioLocal.value) {
    eventBus.emit("update:header", {
      breadcrumbs: [
        { icon: "home", label: "Home", to: "/" },
        { label: "Inventário", to: "inventarios" },
        { label: `#${inventarioLocal.value.id}`, class: "secondary" },
      ],
      title: `Inventário #${inventarioLocal.value.id}`,
      icon: "o_qr_code_scanner",
    });
  } else {
    reloadBreadcrumbs();
  }
};

// Função para lidar com atualizações do inventário
const handleInventarioUpdate = async (updatedInventario) => {
  console.log("Inventário atualizado");

  // Se recebemos um inventário atualizado completo, usa ele
  if (updatedInventario && updatedInventario.id) {
    inventarioLocal.value = updatedInventario;
  } else {
    // Caso contrário, recarrega do servidor
    await carregarInventario();
  }

  emit("reloadListagem");
};

// Função para abrir o modal de criar inventário
const abrirInventario = (tipo) => {
  inventarioMode.value = tipo;
  showInventarioModal.value = true;
};

// Função para fechar o modal
const fecharModal = () => {
  showInventarioModal.value = false;
};

function reloadBreadcrumbs() {
  eventBus.emit("update:header", {
    breadcrumbs: [
      { icon: "home", label: "Home", to: "/" },
      { label: "Inventário", class: "secondary" },
    ],
    title: "Inventário",
    icon: "o_qr_code_scanner",
  });
}

// Watcher para mudanças no form_data
watch(
  () => props.form_data,
  (newVal) => {
    if (newVal?.id) {
      carregarInventario();
    }
  },
  { immediate: true }
);

// Watcher adicional para modelValue (caso seja usado em vez de form_data)
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal?.id && !props.form_data?.id) {
      carregarInventario();
    }
  },
  { immediate: true }
);

// Registrar listeners de eventos
onMounted(() => {
  console.log("InventarioFormDialog montado");
  console.log("Props recebidas:", { form_data: props.form_data, modelValue: props.modelValue });

  eventBus.on("inv-loja", () => abrirInventario("GERAL"));
  eventBus.on("inv-grupo", () => abrirInventario("GRUPO"));
  eventBus.on("customFormClear", () => reloadBreadcrumbs());

  // Ocultar actionbar quando o componente é montado
  eventBus.emit("hide-actionbar");

  // Se já temos um ID, carrega os dados
  const inventarioId = props.form_data?.id || props.modelValue?.id;
  if (inventarioId) {
    carregarInventario();
  } else {
    reloadBreadcrumbs();
  }
});

// Remover listeners ao desmontar o componente
onBeforeUnmount(() => {
  eventBus.off("inv-loja", () => abrirInventario("GERAL"));
  eventBus.off("inv-grupo", () => abrirInventario("GRUPO"));
  eventBus.off("customFormClear", () => reloadBreadcrumbs());
});
</script>

<style scoped>
.bg-primary {
  background: #0a7db5 !important;
}

.text-primary {
  color: #0a7db5 !important;
}

.text-dark {
  color: #1b254b !important;
}

.bg-dark {
  background: #1b254b !important;
}
</style>
