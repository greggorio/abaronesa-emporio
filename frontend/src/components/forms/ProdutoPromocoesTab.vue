<template>
  <q-card-section class="q-pa-none">
    <div class="row items-center justify-between q-pa-md bg-grey-1">
      <div class="text-subtitle1 text-weight-medium">Promoções do Produto</div>
      <q-btn
        color="primary"
        icon="add"
        label="Adicionar Promoção"
        dense
        unelevated
        @click="openDialog()"
        :disable="!recordId"
      />
    </div>

    <!-- Lista vazia -->
    <div v-if="promocoes.length === 0" class="text-center q-pa-xl text-grey-6">
      <q-icon name="local_offer" size="4em" class="q-mb-sm" />
      <div v-if="!recordId">Salve o produto primeiro para adicionar promoções.</div>
      <div v-else>Nenhuma promoção cadastrada para este produto.</div>
    </div>

    <!-- Lista de cards -->
    <div v-else class="q-pa-md row q-col-gutter-sm">
      <div v-for="item in promocoes" :key="item.id" class="col-12 col-md-6">
        <q-card bordered flat class="promocao-card">
          <q-item>
            <q-item-section avatar>
              <q-avatar color="primary" text-color="white" icon="local_offer" size="md" />
            </q-item-section>

            <q-item-section>
              <q-item-label class="text-weight-bold">
                {{ formatDiaSemana(item.diaSemana) }} — {{ formatTipo(item.tipoPromocao) }}
              </q-item-label>
              <q-item-label caption class="text-black">
                {{ formatHorario(item.horarioInicio) }} às {{ formatHorario(item.horarioFim) }}
              </q-item-label>
              <q-item-label caption class="text-primary text-weight-medium">
                {{ formatValor(item) }}
              </q-item-label>
            </q-item-section>

            <q-item-section side>
              <div class="row q-gutter-xs items-center">
                <q-chip
                  :color="item.ativo ? 'positive' : 'grey'"
                  text-color="white"
                  dense
                  size="sm"
                  class="q-mr-sm"
                >
                  {{ item.ativo ? 'Ativo' : 'Inativo' }}
                </q-chip>

                <q-btn flat round dense color="primary" icon="edit" @click="openDialog(item)">
                  <q-tooltip>Editar</q-tooltip>
                </q-btn>
                <q-btn flat round dense color="negative" icon="delete" @click="confirmDelete(item)">
                  <q-tooltip>Remover</q-tooltip>
                </q-btn>
              </div>
            </q-item-section>
          </q-item>
        </q-card>
      </div>
    </div>

    <!-- Dialog -->
    <q-dialog v-model="dialogOpen" persistent>
      <q-card style="min-width: 420px">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">{{ isEditing ? 'Editar Promoção' : 'Adicionar Promoção' }}</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section class="q-pt-md q-gutter-y-md">
          <!-- Dia da semana -->
          <div>
            <div class="text-caption text-grey-8 q-mb-xs">Dias da Semana</div>
            <div class="row q-gutter-xs">
              <template v-if="isEditing">
                <q-chip
                  color="primary"
                  text-color="white"
                  dense
                >
                  {{ formatDiaSemana(form.diaSemana) }}
                </q-chip>
              </template>
              <template v-else>
                <q-chip
                  v-for="dia in diasSemanaOptions"
                  :key="dia.value"
                  clickable
                  :color="diasSelecionados.includes(dia.value) ? 'primary' : 'grey-3'"
                  :text-color="diasSelecionados.includes(dia.value) ? 'white' : 'black'"
                  @click="toggleDia(dia.value)"
                >
                  {{ dia.label.substring(0, 3) }}
                </q-chip>
              </template>
            </div>
            <div v-if="!isEditing && diasSelecionados.length === 0" class="text-caption text-negative q-mt-xs">
              Selecione pelo menos um dia
            </div>
          </div>

          <div class="row q-col-gutter-md">
            <div class="col-6">
              <q-input
                v-model="form.horarioInicio"
                label="Início"
                outlined
                type="time"
                :rules="[val => !!val || 'Obrigatório']"
              />
            </div>
            <div class="col-6">
              <q-input
                v-model="form.horarioFim"
                label="Fim"
                outlined
                type="time"
                :rules="[val => !!val || 'Obrigatório']"
              />
            </div>
          </div>

          <q-select
            v-model="form.tipoPromocao"
            :options="tipoPromocaoOptions"
            label="Tipo de Promoção"
            outlined
            emit-value
            map-options
            :rules="[val => !!val || 'Campo obrigatório']"
          />

          <div v-if="form.tipoPromocao === 'PERCENTUAL'">
            <q-input
              v-model.number="form.percentualDesconto"
              label="Percentual de desconto (%)"
              outlined
              type="number"
              min="0"
              max="100"
              step="0.1"
              :rules="[
                val => val !== null && val !== undefined && val !== '' || 'Obrigatório',
                val => val > 0 || 'Deve ser maior que 0',
                val => val <= 100 || 'Máximo 100%'
              ]"
            />
          </div>
          <div v-else-if="form.tipoPromocao === 'VALOR'">
            <q-input
              v-model.number="form.valorPromocional"
              label="Valor promocional"
              outlined
              type="number"
              min="0"
              step="0.01"
              prefix="R$"
              :rules="[
                val => val !== null && val !== undefined && val !== '' || 'Obrigatório',
                val => val > 0 || 'Deve ser maior que 0'
              ]"
            />
          </div>

          <q-toggle v-model="form.ativo" label="Promoção Ativa" color="primary" />
        </q-card-section>

        <q-card-actions align="right" class="text-primary q-pa-md">
          <q-btn flat label="Cancelar" v-close-popup />
          <q-btn
            unelevated
            color="primary"
            label="Salvar"
            @click="savePromocao"
            :loading="saving"
            :disable="!isFormValid"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-card-section>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { useApiRequest } from "@/composables/useApiRequest";
import { useQuasar } from "quasar";

const props = defineProps({
  recordId: {
    type: Number,
    default: null,
  },
});

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const promocoes = ref([]);
const dialogOpen = ref(false);
const saving = ref(false);
const isEditing = ref(false);
const editingId = ref(null);

const form = ref({
  diaSemana: null,
  horarioInicio: "",
  horarioFim: "",
  tipoPromocao: null,
  percentualDesconto: null,
  valorPromocional: null,
  ativo: true,
});

const diasSelecionados = ref([]);

const diasSemanaOptions = [
  { label: "Domingo", value: "DOMINGO" },
  { label: "Segunda-feira", value: "SEGUNDA" },
  { label: "Terça-feira", value: "TERCA" },
  { label: "Quarta-feira", value: "QUARTA" },
  { label: "Quinta-feira", value: "QUINTA" },
  { label: "Sexta-feira", value: "SEXTA" },
  { label: "Sábado", value: "SABADO" },
];

const tipoPromocaoOptions = [
  { label: "Percentual", value: "PERCENTUAL" },
  { label: "Valor fixo", value: "VALOR" },
];

const isFormValid = computed(() => {
  const hasDia = isEditing.value ? !!form.value.diaSemana : diasSelecionados.value.length > 0;
  if (!hasDia || !form.value.horarioInicio || !form.value.horarioFim || !form.value.tipoPromocao) {
    return false;
  }

  if (form.value.horarioInicio >= form.value.horarioFim) {
    return false;
  }

  if (form.value.tipoPromocao === "PERCENTUAL") {
    return form.value.percentualDesconto > 0 && form.value.percentualDesconto <= 100;
  }
  if (form.value.tipoPromocao === "VALOR") {
    return form.value.valorPromocional > 0;
  }
  return true;
});

const toggleDia = (dia) => {
  const idx = diasSelecionados.value.indexOf(dia);
  if (idx === -1) {
    diasSelecionados.value.push(dia);
  } else {
    diasSelecionados.value.splice(idx, 1);
  }
};

const loadPromocoes = async () => {
  if (!props.recordId) return;
  try {
    const data = await apiRequest(`/api/produto-promocao/produto/${props.recordId}`);
    const orderMap = { DOMINGO: 1, SEGUNDA: 2, TERCA: 3, QUARTA: 4, QUINTA: 5, SEXTA: 6, SABADO: 7 };
    promocoes.value = (data || []).sort((a, b) => {
      const diffDia = orderMap[a.diaSemana] - orderMap[b.diaSemana];
      if (diffDia !== 0) return diffDia;
      return (a.horarioInicio || "").localeCompare(b.horarioInicio || "");
    });
  } catch (error) {
    console.error("Erro ao carregar promoções:", error);
    $q.notify({ type: "negative", message: "Erro ao carregar promoções." });
  }
};

const openDialog = (item = null) => {
  if (item) {
    isEditing.value = true;
    editingId.value = item.id;
    diasSelecionados.value = [];
    form.value = {
      diaSemana: item.diaSemana,
      horarioInicio: item.horarioInicio ? item.horarioInicio.substring(0, 5) : "",
      horarioFim: item.horarioFim ? item.horarioFim.substring(0, 5) : "",
      tipoPromocao: item.tipoPromocao,
      percentualDesconto: item.percentualDesconto,
      valorPromocional: item.valorPromocional,
      ativo: item.ativo,
    };
  } else {
    isEditing.value = false;
    editingId.value = null;
    diasSelecionados.value = [];
    form.value = {
      diaSemana: null,
      horarioInicio: "",
      horarioFim: "",
      tipoPromocao: null,
      percentualDesconto: null,
      valorPromocional: null,
      ativo: true,
    };
  }
  dialogOpen.value = true;
};

const savePromocao = async () => {
  if (!props.recordId) return;

  if (form.value.horarioInicio >= form.value.horarioFim) {
    $q.notify({ type: "warning", message: "O horário final deve ser maior que o inicial." });
    return;
  }

  if (form.value.tipoPromocao === "PERCENTUAL" && !(form.value.percentualDesconto > 0)) {
    $q.notify({ type: "warning", message: "Informe um percentual de desconto maior que zero." });
    return;
  }

  if (form.value.tipoPromocao === "VALOR" && !(form.value.valorPromocional > 0)) {
    $q.notify({ type: "warning", message: "Informe um valor promocional maior que zero." });
    return;
  }

  saving.value = true;
  try {
    const basePayload = {
      produtoId: props.recordId,
      horarioInicio: form.value.horarioInicio + ":00",
      horarioFim: form.value.horarioFim + ":00",
      tipoPromocao: form.value.tipoPromocao,
      percentualDesconto: form.value.tipoPromocao === "PERCENTUAL" ? form.value.percentualDesconto : null,
      valorPromocional: form.value.tipoPromocao === "VALOR" ? form.value.valorPromocional : null,
      ativo: form.value.ativo,
    };

    if (isEditing.value) {
      const payload = { ...basePayload, diaSemana: form.value.diaSemana };
      await apiRequest(`/api/produto-promocao/${editingId.value}`, "PUT", payload);
      $q.notify({ type: "positive", message: "Promoção atualizada com sucesso!" });
      dialogOpen.value = false;
      await loadPromocoes();
    } else {
      const dias = diasSelecionados.value;
      for (const dia of dias) {
        const payload = { ...basePayload, diaSemana: dia };
        await apiRequest("/api/produto-promocao", "POST", payload);
      }
      $q.notify({ type: "positive", message: `${dias.length} promoção(ões) adicionada(s) com sucesso!` });
      dialogOpen.value = false;
      await loadPromocoes();
    }
  } catch (error) {
    console.error("Erro ao salvar promoção:", error);
    const msg = error?.error?.message || error?.message || "Erro ao salvar promoção. Verifique os dados e tente novamente.";
    $q.notify({ type: "negative", message: msg });
  } finally {
    saving.value = false;
  }
};

const confirmDelete = (item) => {
  $q.dialog({
    title: "Confirmar Remoção",
    message: `Deseja remover a promoção de ${formatDiaSemana(item.diaSemana)} (${formatHorario(item.horarioInicio)} - ${formatHorario(item.horarioFim)})?`,
    cancel: true,
    persistent: true,
  }).onOk(async () => {
    try {
      await apiRequest(`/api/produto-promocao/${item.id}`, "DELETE");
      $q.notify({ type: "positive", message: "Promoção removida com sucesso!" });
      await loadPromocoes();
    } catch (error) {
      console.error("Erro ao remover promoção:", error);
      $q.notify({ type: "negative", message: "Erro ao remover promoção." });
    }
  });
};

const formatDiaSemana = (val) => diasSemanaOptions.find((d) => d.value === val)?.label || val;

const formatHorario = (val) => (val ? val.substring(0, 5) : "");

const formatTipo = (val) => tipoPromocaoOptions.find((t) => t.value === val)?.label || val;

const formatValor = (promo) => {
  if (promo.tipoPromocao === "PERCENTUAL" && promo.percentualDesconto !== null && promo.percentualDesconto !== undefined) {
    return `${promo.percentualDesconto}% off`;
  }
  if (promo.tipoPromocao === "VALOR" && promo.valorPromocional !== null && promo.valorPromocional !== undefined) {
    const valor = Number(promo.valorPromocional).toFixed(2);
    return `Preço promocional: R$ ${valor}`;
  }
  return "";
};

watch(
  () => props.recordId,
  async (newId) => {
    if (newId) {
      await loadPromocoes();
    } else {
      promocoes.value = [];
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.promocao-card {
  transition: box-shadow 0.3s;
}
.promocao-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>
