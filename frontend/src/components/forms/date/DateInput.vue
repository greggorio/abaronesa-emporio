<!-- src/components/DateInput.vue -->
<template>
  <q-input :disable="disable" dense :label="label" v-model="displayValue" mask="##/##/####" :rules="rules">
    <template v-slot:append>
      <q-icon name="event" class="cursor-pointer">
        <q-popup-proxy cover transition-show="scale" transition-hide="scale">
          <q-date v-model="displayValue" mask="DD/MM/YYYY" />
        </q-popup-proxy>
      </q-icon>
    </template>
  </q-input>
</template>

<script setup>
import { ref, watch, onMounted } from "vue";

const props = defineProps({
  /** Valor vindo do backend no formato ISO (YYYY-MM-DD ou timestamp) */
  modelValue: {
    type: String,
    default: null,
  },
  /** Rótulo do campo */
  label: {
    type: String,
    default: "Data",
  },
  /** Desabilita o input e o picker */
  disable: {
    type: Boolean,
    default: false,
  },
  /** Regras de validação adicionais */
  rules: {
    type: Array,
    default: () => [(val) => !!val || "Campo obrigatório"],
  },
});

const emit = defineEmits(["update:modelValue"]);

// Estado interno para a string exibida (DD/MM/YYYY)
const displayValue = ref("");

// 1) Gera DD/MM/YYYY para hoje
function getToday() {
  const d = new Date();
  const dd = String(d.getDate()).padStart(2, "0");
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const yyyy = d.getFullYear();
  return `${dd}/${mm}/${yyyy}`;
}

// 2) Converte "DD/MM/YYYY" → "YYYY-MM-DD"
function toIso(dateStr) {
  if (!dateStr || dateStr.trim() === "") return null;
  const digits = dateStr.replace(/[^\d]/g, "");
  // Verificar se há pelo menos dia e mês (mínimo 4 dígitos)
  if (digits.length < 4) return null;
  const dd = digits.slice(0, 2).padStart(2, "0");
  const mm = digits.slice(2, 4).padStart(2, "0");
  const yyyy = digits.slice(4, 8).padStart(4, String(new Date().getFullYear()));
  return `${yyyy}-${mm}-${dd}`;
}

// 3) Converte "YYYY-MM-DD[T...]"/"YYYY-MM-DD" → "DD/MM/YYYY"
function fromIso(isoStr) {
  if (!isoStr) return "";
  // só parte da data
  const datePart = isoStr.split("T")[0];
  const [yyyy, mm, dd] = datePart.split("-");
  return `${dd.padStart(2, "0")}/${mm.padStart(2, "0")}/${yyyy}`;
}

// Inicializa displayValue quando montar ou quando props.modelValue mudar
onMounted(() => {
  displayValue.value = fromIso(props.modelValue);
});

watch(
  () => props.modelValue,
  (nv) => {
    displayValue.value = fromIso(nv);
  }
);

// Sempre que o usuário editar a data, emite o formato ISO para o pai
watch(displayValue, (sv) => {
  emit("update:modelValue", toIso(sv));
});
</script>
