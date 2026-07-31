<template>
  <q-card-section>
    <div class="text-caption q-mb-sm">
      Permissões do Grupo:
      <strong>{{ modelValue.descricao || "Novo Grupo" }}</strong>
    </div>

    <!-- Mensagem quando grupo não foi salvo ainda -->
    <div v-if="!recordId" class="text-center q-pa-lg">
      <q-icon name="info" size="md" color="warning" />
      <div class="text-body2 q-mt-sm text-grey-7">Salve o grupo primeiro para configurar as permissões</div>
    </div>

    <!-- Tabela de permissões -->
    <q-table
      v-else
      id="table-permissoes"
      class="q-mt-sm"
      :rows="permissoes"
      :columns="columns"
      row-key="permissao"
      dense
      bordered
      flat
      :loading="loading"
    >
      <template v-slot:body-cell-has_permissao="props">
        <q-td :props="props" class="text-center">
          <q-icon
            v-if="props.row.has_permissao"
            name="check_box"
            color="primary"
            class="cursor-pointer"
            @click="setPermissao(props.row.permissao, false)"
          />
          <q-icon v-else name="check_box_outline_blank" color="primary" class="cursor-pointer" @click="setPermissao(props.row.permissao, true)" />
        </q-td>
      </template>
    </q-table>
  </q-card-section>
</template>

<script setup>
import { ref, onMounted, watch } from "vue";
import { useApiRequest } from "@/composables/useApiRequest";

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

const { apiRequest } = useApiRequest();
const permissoes = ref([]);
const loading = ref(false);

const columns = [
  {
    name: "descricao",
    required: true,
    label: "Descrição",
    align: "left",
    field: (row) => row.descricao,
    sortable: true,
  },
  {
    name: "has_permissao",
    required: true,
    label: "Permissão",
    align: "center",
    field: (row) => row.has_permissao,
  },
];

const loadPermissoes = async () => {
  if (!props.recordId) return;

  loading.value = true;
  try {
    const url = `/api/permissoes/permissaoporgrupo/${props.recordId}`;
    const response = await apiRequest(url);
    if (response) {
      permissoes.value = response;
    }
  } catch (error) {
    console.error("Erro ao carregar permissões:", error);
  } finally {
    loading.value = false;
  }
};

const setPermissao = async (permissao, valor) => {
  try {
    const apiURL = `/api/permissoes/setarpermissao/${permissao}/${valor}/${props.recordId}`;
    await apiRequest(apiURL);
    await loadPermissoes();
  } catch (error) {
    console.error("Erro ao alterar permissão:", error);
  }
};

// Carrega permissões quando o recordId muda (modo edição)
watch(
  () => props.recordId,
  async (newId) => {
    if (newId) {
      await loadPermissoes();
    } else {
      permissoes.value = [];
    }
  },
  { immediate: true }
);

// Carrega permissões quando componente é montado (se já tem recordId)
onMounted(async () => {
  if (props.recordId) {
    await loadPermissoes();
  }
});
</script>
