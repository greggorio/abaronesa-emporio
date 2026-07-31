<template>
  <q-card-section>
    <div class="text-caption q-mb-sm">
      Embalagens do Produto:
      <strong>{{ modelValue?.nome || "Novo Produto" }}</strong>
    </div>

    <!-- Mensagem quando produto ainda não foi salvo -->
    <div v-if="!recordId" class="text-center q-pa-lg">
      <q-icon name="info" size="md" color="warning" />
      <div class="text-body2 q-mt-sm text-grey-7">Salve o produto primeiro para gerenciar embalagens</div>
    </div>

    <!-- Tabela de embalagens -->
    <div v-else>
      <div class="row items-center q-gutter-sm q-mb-sm">
        <q-btn color="primary" icon="add" label="Adicionar Embalagem" @click="openCreateDialog" />
        <q-btn flat color="primary" icon="refresh" label="Recarregar" :loading="loading" @click="loadEmbalagens" />
      </div>

      <q-table
        :rows="embalagens"
        :columns="columns"
        row-key="id"
        flat
        bordered
        dense
        :loading="loading"
      >
        <template #body-cell-permiteVenda="props">
          <q-td :props="props" class="text-center">
            <q-toggle
              :model-value="props.row.permiteVenda"
              color="primary"
              @update:model-value="(val) => toggleField(props.row, 'permiteVenda', val)"
            />
          </q-td>
        </template>

        <template #body-cell-principal="props">
          <q-td :props="props" class="text-center">
            <q-toggle
              :model-value="props.row.principal"
              color="primary"
              @update:model-value="(val) => togglePrincipal(props.row, val)"
            />
          </q-td>
        </template>

        <template #body-cell-ativo="props">
          <q-td :props="props" class="text-center">
            <q-toggle
              :model-value="props.row.ativo"
              color="primary"
              @update:model-value="(val) => toggleField(props.row, 'ativo', val)"
            />
          </q-td>
        </template>

        <template #body-cell-actions="props">
          <q-td :props="props" class="text-center">
            <q-btn flat round dense icon="edit" color="primary" @click="openEditDialog(props.row)" />
            <q-btn flat round dense icon="delete" color="negative" @click="confirmDelete(props.row)" />
          </q-td>
        </template>
      </q-table>
    </div>

    <!-- Diálogo de cadastro/edição -->
    <q-dialog v-model="dialog.open" persistent>
      <q-card style="min-width: 420px; max-width: 600px;">
        <q-card-section>
          <div class="text-h6">{{ dialog.editing ? "Editar Embalagem" : "Nova Embalagem" }}</div>
        </q-card-section>

        <q-card-section class="q-pt-none">
          <q-form @submit.prevent="saveEmbalagem" ref="formRef">
            <div class="row q-col-gutter-md">
              <div class="col-12">
                <q-input
                  v-model="form.nome"
                  label="Nome"
                  dense
                  :error="!!errors.nome"
                  :error-message="errors.nome"
                  autofocus
                />
              </div>
              <div class="col-12">
                <q-input
                  v-model.number="form.fatorBase"
                  label="Fator (unidade base)"
                  type="number"
                  dense
                  :error="!!errors.fatorBase"
                  :error-message="errors.fatorBase"
                  min="1"
                  step="1"
                />
              </div>
              <div class="col-12">
                <q-input v-model="form.codigoBarras" label="Código de Barras" dense />
              </div>
              <div class="col-12">
                <q-toggle v-model="form.permiteVenda" label="Permite venda" color="primary" />
              </div>
              <div class="col-12">
                <q-toggle v-model="form.principal" label="Principal" color="primary" />
              </div>
              <div class="col-12">
                <q-toggle v-model="form.ativo" label="Ativo" color="primary" />
              </div>
            </div>
          </q-form>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Cancelar" color="secondary" @click="closeDialog" />
          <q-btn flat label="Salvar" color="primary" :loading="saving" @click="saveEmbalagem" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-card-section>
</template>

<script setup>
import { ref, watch, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const props = defineProps({
  modelValue: { type: Object, required: true },
  recordId: { type: Number, default: null },
});
const emit = defineEmits(["update:modelValue"]);

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const embalagens = ref([]);
const loading = ref(false);
const saving = ref(false);
const formRef = ref(null);

const dialog = ref({ open: false, editing: false });
const form = ref({
  id: null,
  produtoId: null,
  nome: "",
  fatorBase: 1,
  codigoBarras: "",
  permiteVenda: true,
  principal: false,
  ativo: true,
});
const errors = ref({});

const columns = [
  { name: "nome", label: "Nome", field: "nome", align: "left", sortable: true },
  { name: "fatorBase", label: "Fator (base)", field: "fatorBase", align: "left", sortable: true },
  { name: "codigoBarras", label: "Código de Barras", field: "codigoBarras", align: "left" },
  { name: "permiteVenda", label: "Permite Venda", field: "permiteVenda", align: "center" },
  { name: "principal", label: "Principal", field: "principal", align: "center" },
  { name: "ativo", label: "Ativo", field: "ativo", align: "center" },
  { name: "actions", label: "Ações", field: "actions", align: "center" },
];

async function loadEmbalagens() {
  if (!props.recordId) return;
  loading.value = true;
  try {
    const url = `/api/embalagens?produtoId=${props.recordId}`;
    const data = await apiRequest(url);
    embalagens.value = Array.isArray(data) ? data : [];
  } catch (e) {
    $q.notify({ type: "negative", message: e.message || "Erro ao carregar embalagens" });
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  form.value = {
    id: null,
    produtoId: props.recordId,
    nome: "",
    fatorBase: 1,
    codigoBarras: "",
    permiteVenda: true,
    principal: false,
    ativo: true,
  };
  errors.value = {};
  dialog.value = { open: true, editing: false };
}

function openEditDialog(row) {
  form.value = {
    id: row.id,
    produtoId: row.produtoId || props.recordId,
    nome: row.nome,
    fatorBase: row.fatorBase,
    codigoBarras: row.codigoBarras,
    permiteVenda: !!row.permiteVenda,
    principal: !!row.principal,
    ativo: !!row.ativo,
  };
  errors.value = {};
  dialog.value = { open: true, editing: true };
}

function closeDialog() {
  dialog.value.open = false;
}

function validateForm() {
  const e = {};
  if (!form.value.nome || !form.value.nome.trim()) e.nome = "Nome é obrigatório";
  const fb = Number(form.value.fatorBase);
  if (!fb || fb <= 0 || !Number.isInteger(fb)) {
    e.fatorBase = "Informe um inteiro > 0";
  }
  errors.value = e;
  return Object.keys(e).length === 0;
}

async function saveEmbalagem() {
  if (!validateForm()) return;
  saving.value = true;
  try {
    if (dialog.value.editing && form.value.id) {
      await apiRequest(`/api/embalagens/${form.value.id}`, "PUT", {
        produtoId: form.value.produtoId,
        nome: form.value.nome,
        fatorBase: form.value.fatorBase,
        codigoBarras: form.value.codigoBarras,
        permiteVenda: form.value.permiteVenda,
        principal: form.value.principal,
        ativo: form.value.ativo,
      });
      $q.notify({ type: "positive", message: "Embalagem atualizada" });
    } else {
      await apiRequest(`/api/embalagens`, "POST", {
        produtoId: props.recordId,
        nome: form.value.nome,
        fatorBase: form.value.fatorBase,
        codigoBarras: form.value.codigoBarras,
        permiteVenda: form.value.permiteVenda,
        principal: form.value.principal,
        ativo: form.value.ativo,
      });
      $q.notify({ type: "positive", message: "Embalagem criada" });
    }
    dialog.value.open = false;
    await loadEmbalagens();
  } catch (e) {
    $q.notify({ type: "negative", message: e.message || "Erro ao salvar embalagem" });
  } finally {
    saving.value = false;
  }
}

async function toggleField(row, field, value) {
  try {
    await apiRequest(`/api/embalagens/${row.id}`, "PUT", {
      produtoId: row.produtoId || props.recordId,
      nome: row.nome,
      fatorBase: row.fatorBase,
      codigoBarras: row.codigoBarras,
      permiteVenda: field === "permiteVenda" ? value : row.permiteVenda,
      principal: field === "principal" ? value : row.principal,
      ativo: field === "ativo" ? value : row.ativo,
    });
    if (field === "principal" && value) {
      await loadEmbalagens();
    } else {
      row[field] = value;
    }
  } catch (e) {
    $q.notify({ type: "negative", message: e.message || "Erro ao atualizar embalagem" });
  }
}

async function togglePrincipal(row, value) {
  await toggleField(row, "principal", value);
}

function confirmDelete(row) {
  $q.dialog({
    title: "Confirmação",
    message: `Excluir embalagem "${row.nome}"?`,
    cancel: true,
    persistent: true,
  }).onOk(async () => {
    try {
      await apiRequest(`/api/embalagens/${row.id}`, "DELETE");
      $q.notify({ type: "positive", message: "Embalagem excluída" });
      await loadEmbalagens();
    } catch (e) {
      $q.notify({ type: "negative", message: e.message || "Erro ao excluir embalagem" });
    }
  });
}

watch(
  () => props.recordId,
  async (val) => {
    embalagens.value = [];
    if (val) await loadEmbalagens();
  },
  { immediate: true }
);

onMounted(async () => {
  if (props.recordId) await loadEmbalagens();
});
</script>

<style scoped>
</style>
