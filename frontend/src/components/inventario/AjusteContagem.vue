<template>
  <q-dialog v-model="dialogVisible" persistent>
    <q-card style="min-width: 350px">
      <q-card-section class="row items-center">
        <div class="text-h6">Ajuste de Contagem</div>
        <q-space />
        <q-btn icon="close" flat round dense v-close-popup />
      </q-card-section>

      <q-card-section>
        <div class="text-subtitle1 q-mb-sm">Produto: {{ contagem.codigo_produto }} - {{ contagem.descricao_produto }}</div>
        <div class="text-subtitle2 q-mb-md">Estoque contado: {{ contagem.quantidade_contada }}</div>

        <div class="q-gutter-y-md">
          <q-radio v-model="tipoAjuste" val="adicionar" label="Adicionar à contagem" />
          <q-radio v-model="tipoAjuste" val="remover" label="Remover da contagem" />

          <q-input filled v-model.number="quantidade" type="number" label="Quantidade" readonly>
            <template v-slot:append>
              <q-btn round dense flat icon="add" @click="quantidade++" />
              <q-btn round dense flat icon="remove" @click="quantidade > 0 ? quantidade-- : 0" />
            </template>
          </q-input>

          <!-- Campo de observação adicionado -->
          <q-input filled v-model="observacao" type="textarea" label="Observação" hint="Detalhe o motivo do ajuste (opcional)" autogrow />
        </div>
      </q-card-section>

      <q-card-actions align="right">
        <q-btn flat label="Cancelar" color="primary" v-close-popup />
        <q-btn flat label="Confirmar" color="primary" :loading="loading" :disable="quantidade <= 0" @click="confirmarAjuste" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import eventBus from "@/eventBus";

// Props
const props = defineProps({
  contagem: {
    type: Object,
    required: true,
  },
  id_inventario: {
    type: [Number, String],
    required: true,
  },
  modelValue: {
    type: Boolean,
    default: false,
  },
});

// Emits
const emit = defineEmits(["update:inventario", "update:modelValue", "ajusteRealizado"]);

// Estado
const $q = useQuasar();
const { apiRequest } = useApiRequest();
const quantidade = ref(0);
const tipoAjuste = ref("remover");
const observacao = ref(""); // Nova ref para observação
const loading = ref(false);

// Computed
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value),
});

// Métodos
async function confirmarAjuste() {
  if (quantidade.value <= 0) return;

  loading.value = true;

  try {
    // Define a quantidade baseada no tipo de ajuste
    const qtdeFinal = tipoAjuste.value === "adicionar" ? quantidade.value : -quantidade.value;

    // Construção dos parâmetros de consulta
    const params = new URLSearchParams();
    params.append("quantidade", qtdeFinal);
    if (observacao.value) {
      params.append("observacao", observacao.value);
    }

    const apiURL = `/api/inventarios/${props.id_inventario}/itens/${props.contagem.id}/ajuste?${params.toString()}`;

    // Usa o novo endpoint PATCH
    const response = await apiRequest(apiURL, "PATCH");

    if (response) {
      emit("update:inventario", {
        ...response,
      });

      // Limpa o formulário
      tipoAjuste.value = "remover";
      quantidade.value = 0;
      observacao.value = ""; // Limpa a observação

      $q.notify({
        color: "positive",
        message: "Ajuste registrado com sucesso",
        icon: "check",
      });

      emit("ajusteRealizado");
      dialogVisible.value = false;
    } else {
      let errorMessage = "Erro ao realizar ajuste.";

      if (error.message) errorMessage = error.message;

      $q.dialog({
        title: "Erro",
        message: errorMessage,
        color: "negative",
        icon: "error",
        ok: "Fechar",
      });
    }
  } catch (error) {
    console.error("Erro ao ajustar contagem:", error);
    $q.notify({
      color: "negative",
      message: `Erro ao ajustar contagem: ${error.message || "Erro desconhecido"}`,
      icon: "warning",
    });
  } finally {
    loading.value = false;
  }
}
</script>
