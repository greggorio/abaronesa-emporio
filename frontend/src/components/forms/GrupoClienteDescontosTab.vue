<template>
  <q-card-section>
    <div class="text-caption q-mb-sm">
      Descontos do Grupo:
      <strong>{{ modelValue.descricao || "Novo Grupo" }}</strong>
    </div>

    <div v-if="!recordId" class="text-center q-pa-lg">
      <q-icon name="info" size="md" color="warning" />
      <div class="text-body2 q-mt-sm text-grey-7">Salve o grupo primeiro para configurar os descontos</div>
    </div>

    <div v-else>
      <div class="row items-center q-gutter-sm q-mb-sm">
        <div class="text-body2 text-grey-7">
          Defina o desconto por categoria e, se necessário, por subcategoria. Subcategoria tem prioridade.
        </div>
        <q-space />
        <q-btn color="primary" flat icon="save" label="Salvar" :loading="saving" @click="salvar" />
      </div>

      <q-list bordered separator class="q-mt-sm">
        <q-expansion-item
          v-for="categoria in categorias"
          :key="categoria.id"
          expand-separator
          icon="category"
          :label="categoria.nome"
          :caption="categoria.subcategorias?.length ? `${categoria.subcategorias.length} subcategoria(s)` : 'Sem subcategorias'"
        >
          <div class="row items-center q-col-gutter-sm q-pa-sm">
            <div class="col-12 col-md-6">
              <q-input
                v-model="descontos[categoriaKey(categoria.id)]"
                label="Desconto da categoria (%)"
                type="number"
                dense
                outlined
                inputmode="decimal"
                :min="0"
                :max="100"
                step="0.01"
                hint="Deixe em branco para remover"
                :disable="loading"
                :rules="[valorValido]"
                lazy-rules
                @blur="agendarSalvar"
              />
            </div>
          </div>

          <div v-if="categoria.subcategorias?.length" class="q-px-sm q-pb-sm">
            <q-markup-table dense flat bordered>
              <thead>
                <tr>
                  <th class="text-left">Subcategoria</th>
                  <th class="text-left">Desconto (%)</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="sub in categoria.subcategorias" :key="sub.id">
                  <td class="text-left">{{ sub.nome }}</td>
                  <td class="text-left">
                    <q-input
                      v-model="descontos[subcategoriaKey(sub.id)]"
                      type="number"
                      dense
                      outlined
                      inputmode="decimal"
                      :min="0"
                      :max="100"
                      step="0.01"
                      hint="Opcional"
                      :disable="loading"
                      :rules="[valorValido]"
                      lazy-rules
                      @blur="agendarSalvar"
                    />
                  </td>
                </tr>
              </tbody>
            </q-markup-table>
          </div>
        </q-expansion-item>
      </q-list>

      <q-inner-loading :showing="loading">
        <q-spinner-dots size="40px" color="primary" />
      </q-inner-loading>
    </div>
  </q-card-section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useQuasar } from "quasar";
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

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const categorias = ref([]);
const descontos = ref({});
const loading = ref(false);
const saving = ref(false);
let saveTimer = null;

const categoriaKey = (categoriaId) => `cat-${categoriaId}`;
const subcategoriaKey = (subcategoriaId) => `sub-${subcategoriaId}`;

const valorValido = (val) => {
  if (val === null || val === undefined || val === "") return true;
  const numero = Number(val);
  if (Number.isNaN(numero)) return "Informe um número válido";
  if (numero < 0 || numero > 100) return "Informe um valor entre 0 e 100";
  return true;
};

const carregar = async () => {
  if (!props.recordId) return;
  loading.value = true;
  try {
    const [categoriasResponse, descontosResponse] = await Promise.all([
      apiRequest("/api/categorias/all"),
      apiRequest(`/api/grupos-clientes/${props.recordId}/descontos`),
    ]);

    categorias.value = categoriasResponse || [];
    const mapa = {};
    (descontosResponse || []).forEach((item) => {
      if (item.subcategoriaId) {
        mapa[subcategoriaKey(item.subcategoriaId)] = item.descontoPercentual?.toString();
      } else if (item.categoriaId) {
        mapa[categoriaKey(item.categoriaId)] = item.descontoPercentual?.toString();
      }
    });
    descontos.value = mapa;
  } catch (error) {
    console.error("Erro ao carregar descontos:", error);
  } finally {
    loading.value = false;
  }
};

const normalizarDesconto = (valor) => {
  if (valor === null || valor === undefined || valor === "") return null;
  const numero = Number(valor);
  if (Number.isNaN(numero)) return null;
  if (numero <= 0) return null;
  return Number(numero.toFixed(2));
};

const montarPayload = () => {
  const payload = [];
  categorias.value.forEach((categoria) => {
    const descontoCategoria = normalizarDesconto(descontos.value[categoriaKey(categoria.id)]);
    if (descontoCategoria !== null) {
      payload.push({
        categoriaId: categoria.id,
        descontoPercentual: descontoCategoria,
      });
    }
    (categoria.subcategorias || []).forEach((sub) => {
      const descontoSub = normalizarDesconto(descontos.value[subcategoriaKey(sub.id)]);
      if (descontoSub !== null) {
        payload.push({
          categoriaId: categoria.id,
          subcategoriaId: sub.id,
          descontoPercentual: descontoSub,
        });
      }
    });
  });
  return payload;
};

const salvar = async ({ notify = true, reload = true } = {}) => {
  if (!props.recordId) return;
  saving.value = true;
  try {
    const payload = montarPayload();
    await apiRequest(`/api/grupos-clientes/${props.recordId}/descontos`, "PUT", payload);
    if (notify) {
      $q.notify({ type: "positive", message: "Descontos salvos com sucesso." });
    }
    if (reload) {
      await carregar();
    }
  } catch (error) {
    console.error("Erro ao salvar descontos:", error);
    if (notify) {
      $q.notify({ type: "negative", message: "Erro ao salvar descontos." });
    }
  } finally {
    saving.value = false;
  }
};

const agendarSalvar = () => {
  if (!props.recordId) return;
  if (saveTimer) {
    clearTimeout(saveTimer);
  }
  saveTimer = setTimeout(() => {
    salvar({ notify: false, reload: false });
  }, 400);
};

watch(
  () => props.recordId,
  async (newId) => {
    if (newId) {
      await carregar();
    } else {
      categorias.value = [];
      descontos.value = {};
    }
  },
  { immediate: true }
);

onMounted(async () => {
  if (props.recordId) {
    await carregar();
  }
});

onBeforeUnmount(() => {
  if (saveTimer) {
    clearTimeout(saveTimer);
    saveTimer = null;
  }
});
</script>
