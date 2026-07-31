<template>
  <q-card-section class="q-pa-none">
    <div class="row items-center justify-between q-pa-md bg-grey-1">
      <div class="text-subtitle1 text-weight-medium">Disponibilidade da Subcategoria</div>
      <q-btn
        color="primary"
        icon="add"
        label="Adicionar Regra"
        dense
        unelevated
        @click="openDialog()"
        :disable="!recordId"
      />
    </div>

    <!-- Lista Vazia -->
    <div v-if="disponibilidades.length === 0" class="text-center q-pa-xl text-grey-6">
      <q-icon name="event_busy" size="4em" class="q-mb-sm" />
      <div v-if="!recordId">Salve a subcategoria primeiro para adicionar regras de disponibilidade.</div>
      <div v-else>Nenhuma regra de disponibilidade cadastrada. A subcategoria estará sempre disponível se não houver regras.</div>
    </div>

    <!-- Lista de Cards -->
    <div v-else class="q-pa-md row q-col-gutter-sm">
      <div v-for="item in disponibilidades" :key="item.id" class="col-12 col-md-6">
        <q-card bordered flat class="disponibilidade-card">
          <q-item>
            <q-item-section avatar>
              <q-avatar color="primary" text-color="white" icon="event" size="md" />
            </q-item-section>

            <q-item-section>
              <q-item-label class="text-weight-bold">{{ formatDiaSemana(item.diaSemana) }}</q-item-label>
              <q-item-label caption class="text-black">
                {{ formatHorario(item.horarioInicio) }} às {{ formatHorario(item.horarioFim) }}
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
                
                <q-btn
                  flat
                  round
                  dense
                  color="primary"
                  icon="edit"
                  @click="openDialog(item)"
                >
                  <q-tooltip>Editar</q-tooltip>
                </q-btn>
                <q-btn
                  flat
                  round
                  dense
                  color="negative"
                  icon="delete"
                  @click="confirmDelete(item)"
                >
                  <q-tooltip>Remover</q-tooltip>
                </q-btn>
              </div>
            </q-item-section>
          </q-item>
        </q-card>
      </div>
    </div>

    <!-- Dialog de Adição/Edição -->
    <q-dialog v-model="dialogOpen" persistent>
      <q-card style="min-width: 400px">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">{{ isEditing ? 'Editar Regra' : 'Adicionar Regra' }}</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section class="q-pt-md q-gutter-y-md">
          <!-- Dia da Semana -->
          <q-select
            v-model="form.diaSemana"
            :options="diasSemanaOptions"
            label="Dia da Semana"
            outlined
            emit-value
            map-options
            :rules="[val => !!val || 'Campo obrigatório']"
          />

          <div class="row q-col-gutter-md">
            <!-- Horário Início -->
            <div class="col-6">
              <q-input
                v-model="form.horarioInicio"
                label="Início"
                outlined
                type="time"
                :rules="[val => !!val || 'Obrigatório']"
              />
            </div>
            <!-- Horário Fim -->
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

          <!-- Ativo -->
          <q-toggle
            v-model="form.ativo"
            label="Regra Ativa"
            color="primary"
          />

        </q-card-section>

        <q-card-actions align="right" class="text-primary q-pa-md">
          <q-btn flat label="Cancelar" v-close-popup />
          <q-btn
            unelevated
            color="primary"
            label="Salvar"
            @click="saveRegra"
            :loading="saving"
            :disable="!isFormValid"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-card-section>
</template>

<script setup>
import { ref, computed, watch } from "vue";
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

const disponibilidades = ref([]);
const dialogOpen = ref(false);
const saving = ref(false);
const isEditing = ref(false);
const editingId = ref(null);

const form = ref({
  diaSemana: null,
  horarioInicio: "",
  horarioFim: "",
  ativo: true,
});

const diasSemanaOptions = [
  { label: "Domingo", value: "DOMINGO" },
  { label: "Segunda-feira", value: "SEGUNDA" },
  { label: "Terça-feira", value: "TERCA" },
  { label: "Quarta-feira", value: "QUARTA" },
  { label: "Quinta-feira", value: "QUINTA" },
  { label: "Sexta-feira", value: "SEXTA" },
  { label: "Sábado", value: "SABADO" },
];

const isFormValid = computed(() => {
  return form.value.diaSemana && form.value.horarioInicio && form.value.horarioFim;
});

const loadDisponibilidades = async () => {
  if (!props.recordId) return;
  try {
    const data = await apiRequest(`/api/subcategoria-disponibilidade/subcategoria/${props.recordId}`);
    // Ordenar por dia da semana (API já pode vir ordenada, mas garantimos aqui)
    const orderMap = { "DOMINGO": 1, "SEGUNDA": 2, "TERCA": 3, "QUARTA": 4, "QUINTA": 5, "SEXTA": 6, "SABADO": 7 };
    disponibilidades.value = (data || []).sort((a, b) => {
        const diffDia = orderMap[a.diaSemana] - orderMap[b.diaSemana];
        if (diffDia !== 0) return diffDia;
        return a.horarioInicio.localeCompare(b.horarioInicio);
    });
  } catch (error) {
    console.error("Erro ao carregar disponibilidades:", error);
    $q.notify({ type: "negative", message: "Erro ao carregar regras de disponibilidade." });
  }
};

const openDialog = (item = null) => {
  if (item) {
    isEditing.value = true;
    editingId.value = item.id;
    // O horário vem como 'HH:mm:ss' do backend, o input type="time" espera 'HH:mm'
    form.value = {
      diaSemana: item.diaSemana,
      horarioInicio: item.horarioInicio ? item.horarioInicio.substring(0, 5) : "",
      horarioFim: item.horarioFim ? item.horarioFim.substring(0, 5) : "",
      ativo: item.ativo,
    };
  } else {
    isEditing.value = false;
    editingId.value = null;
    form.value = {
      diaSemana: null,
      horarioInicio: "",
      horarioFim: "",
      ativo: true,
    };
  }
  dialogOpen.value = true;
};

const saveRegra = async () => {
  if (!props.recordId) return;

  // Validação básica de horário
  if (form.value.horarioInicio >= form.value.horarioFim) {
      $q.notify({ type: "warning", message: "O horário final deve ser maior que o inicial." });
      return;
  }

  saving.value = true;
  try {
    const payload = {
      subcategoriaId: props.recordId,
      diaSemana: form.value.diaSemana,
      horarioInicio: form.value.horarioInicio + ":00", // Adicionando segundos para o LocalTime
      horarioFim: form.value.horarioFim + ":00",
      ativo: form.value.ativo,
    };

    if (isEditing.value) {
      await apiRequest(`/api/subcategoria-disponibilidade/${editingId.value}`, "PUT", payload);
      $q.notify({ type: "positive", message: "Regra atualizada com sucesso!" });
    } else {
      await apiRequest("/api/subcategoria-disponibilidade", "POST", payload);
      $q.notify({ type: "positive", message: "Regra adicionada com sucesso!" });
    }

    dialogOpen.value = false;
    await loadDisponibilidades();
  } catch (error) {
    console.error("Erro ao salvar:", error);
    $q.notify({ type: "negative", message: "Erro ao salvar regra." });
  } finally {
    saving.value = false;
  }
};

const confirmDelete = (item) => {
  $q.dialog({
    title: "Confirmar Remoção",
    message: `Deseja remover a regra de ${formatDiaSemana(item.diaSemana)} (${formatHorario(item.horarioInicio)} - ${formatHorario(item.horarioFim)})?`,
    cancel: true,
    persistent: true,
  }).onOk(async () => {
    try {
      await apiRequest(`/api/subcategoria-disponibilidade/${item.id}`, "DELETE");
      $q.notify({ type: "positive", message: "Removido com sucesso!" });
      await loadDisponibilidades();
    } catch (error) {
      console.error("Erro ao remover:", error);
      $q.notify({ type: "negative", message: "Erro ao remover regra." });
    }
  });
};

const formatDiaSemana = (val) => {
  const map = diasSemanaOptions.find(d => d.value === val);
  return map ? map.label : val;
};

const formatHorario = (val) => {
  if (!val) return "";
  return val.substring(0, 5); // HH:mm
};

watch(() => props.recordId, async (newId) => {
  if (newId) {
    await loadDisponibilidades();
  } else {
    disponibilidades.value = [];
  }
}, { immediate: true });

</script>

<style scoped>
.disponibilidade-card {
  transition: box-shadow 0.3s;
}
.disponibilidade-card:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
</style>
