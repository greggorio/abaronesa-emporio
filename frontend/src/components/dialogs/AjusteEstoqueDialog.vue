<template>
  <q-dialog v-model="dialogVisible" persistent>
    <q-card style="min-width: 400px">
      <q-card-section>
        <div class="text-h6">Ajuste de Estoque</div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <q-form @submit.prevent="handleSubmit" ref="formRef">
          <div class="row q-col-gutter-md">
            <!-- Produto/SKU -->
            <div class="col-12" v-if="!produto">
              <q-select
                v-model="formData.skuId"
                label="Produto/SKU"
                :options="skuOptions"
                @filter="filterSkus"
                use-input
                input-debounce="300"
                option-label="label"
                option-value="value"
                emit-value
                map-options
                :loading="loadingSkus"
                :rules="[(val) => !!val || 'Selecione um produto']"
              >
                <template v-slot:no-option>
                  <q-item>
                    <q-item-section class="text-grey">Nenhum produto encontrado</q-item-section>
                  </q-item>
                </template>
              </q-select>
            </div>

            <!-- Produto selecionado (readonly) -->
            <div class="col-12" v-else>
              <q-input :model-value="produto.nome" label="Produto" readonly filled />
              <div class="text-caption text-grey-7 q-mt-xs">Estoque atual: {{ estoqueAtual }}</div>
            </div>

            <!-- Operação -->
            <div class="col-12">
              <q-radio v-model="formData.adicionar" :val="true" label="Adicionar ao estoque" color="positive" />
              <q-radio v-model="formData.adicionar" :val="false" label="Remover do estoque" color="negative" />
            </div>

            <!-- Quantidade -->
            <div class="col-12">
              <q-input
                v-model.number="formData.quantidade"
                label="Quantidade"
                type="number"
                :rules="[(val) => !!val || 'Informe a quantidade', (val) => val > 0 || 'Quantidade deve ser maior que zero']"
              >
                <template v-slot:append>
                  <q-btn round dense flat icon="remove" @click="formData.quantidade = Math.max(1, (formData.quantidade || 1) - 1)" />
                  <q-btn round dense flat icon="add" @click="formData.quantidade = (formData.quantidade || 0) + 1" />
                </template>
              </q-input>
            </div>

            <!-- Motivo -->
            <div class="col-12">
              <q-input
                v-model="formData.motivo"
                label="Motivo do Ajuste"
                type="textarea"
                rows="3"
                :rules="[(val) => !!val || 'Informe o motivo do ajuste']"
              />
            </div>
          </div>
        </q-form>
      </q-card-section>

      <q-card-actions align="right">
        <q-btn flat label="Cancelar" @click="handleCancel" />
        <q-btn flat label="Confirmar" color="primary" @click="handleSubmit" :loading="saving" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  produto: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(["update:modelValue", "saved"]);

const $q = useQuasar();
const { apiRequest } = useApiRequest();

// State
const formRef = ref(null);
const formData = ref({
  skuId: null,
  adicionar: true,
  quantidade: 1,
  motivo: "",
});
const skuOptions = ref([]);
const loadingSkus = ref(false);
const saving = ref(false);

// Computed
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const estoqueAtual = computed(() => {
  return props.produto?.estoque || 0;
});

// Watch para produto pré-selecionado
watch(
  () => props.produto,
  (newProduto) => {
    if (newProduto) {
      formData.value.skuId = newProduto.skuId || newProduto.id;
    }
  },
  { immediate: true }
);

// Methods
async function filterSkus(val, update, abort) {
  if (val.length < 2) {
    abort();
    return;
  }

  loadingSkus.value = true;
  try {
    const response = await apiRequest(`/api/skus/search-options?q=${val}`);
    skuOptions.value = response.data || response;
    update();
  } catch (error) {
    abort();
  } finally {
    loadingSkus.value = false;
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate();
  if (!valid) return;

  saving.value = true;
  try {
    await apiRequest("/api/movimento-estoque/ajuste-rapido", "POST", formData.value);

    $q.notify({
      type: "positive",
      message: "estoque atualizado com sucesso!",
    });

    emit("saved");
    emit("dialogoConcluido");
    handleCancel();
  } catch (error) {
    $q.notify({
      type: "negative",
      message: error.message || "Erro ao realizar ajuste",
    });
  } finally {
    saving.value = false;
  }
}

function handleCancel() {
  formData.value = {
    skuId: null,
    adicionar: true,
    quantidade: 1,
    motivo: "",
  };
  dialogVisible.value = false;
}
</script>
