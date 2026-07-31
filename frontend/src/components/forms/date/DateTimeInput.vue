<!-- src/components/DateTimeInput.vue -->
<template>
  <q-input :disable="disable" dense :label="label" v-model="displayValue" mask="##/##/#### ##:##" :rules="rules">
    <template v-slot:append>
      <q-icon name="event" class="cursor-pointer">
        <q-popup-proxy cover transition-show="scale" transition-hide="scale">
          <q-date v-model="dateValue" mask="DD/MM/YYYY" />
        </q-popup-proxy>
      </q-icon>
      <q-icon name="access_time" class="cursor-pointer q-ml-xs">
        <q-popup-proxy cover transition-show="scale" transition-hide="scale">
          <q-time v-model="timeValue" mask="HH:mm" format24h />
        </q-popup-proxy>
      </q-icon>
    </template>
  </q-input>
</template>

<script setup>
import { ref, watch, onMounted, computed } from "vue";

const props = defineProps({
  /** Valor vindo do backend no formato ISO (YYYY-MM-DDTHH:mm:ss ou timestamp) */
  modelValue: {
    type: String,
    default: null,
  },
  /** Rótulo do campo */
  label: {
    type: String,
    default: "Data/Hora",
  },
  /** Desabilita o input e os pickers */
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

// Estados internos
const displayValue = ref("");
const dateValue = ref("");
const timeValue = ref("");

// 1) Gera DD/MM/YYYY HH:mm para agora
function getNow() {
  const d = new Date();
  const dd = String(d.getDate()).padStart(2, "0");
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const yyyy = d.getFullYear();
  const hh = String(d.getHours()).padStart(2, "0");
  const min = String(d.getMinutes()).padStart(2, "0");
  return `${dd}/${mm}/${yyyy} ${hh}:${min}`;
}

// 2) Converte "DD/MM/YYYY HH:mm" → "YYYY-MM-DDTHH:mm"
function toIso(dateTimeStr) {
  if (!dateTimeStr) return null;
  
  // Extrair apenas dígitos e separar data e hora
  const cleanStr = dateTimeStr.replace(/[^\d]/g, "");
  if (cleanStr.length < 12) return null;
  
  const dd = cleanStr.slice(0, 2).padStart(2, "0");
  const mm = cleanStr.slice(2, 4).padStart(2, "0");
  const yyyy = cleanStr.slice(4, 8).padStart(4, String(new Date().getFullYear()));
  const hh = cleanStr.slice(8, 10).padStart(2, "0");
  const min = cleanStr.slice(10, 12).padStart(2, "0");
  
  return `${yyyy}-${mm}-${dd}T${hh}:${min}`;
}

// 3) Converte "YYYY-MM-DDTHH:mm[:ss]" → "DD/MM/YYYY HH:mm"
function fromIso(isoStr) {
  if (!isoStr) return getNow();
  
  // Separar data e hora
  const [datePart, timePart] = isoStr.split("T");
  const [yyyy, mm, dd] = datePart.split("-");
  
  let hh = "00", min = "00";
  if (timePart) {
    const timeComponents = timePart.split(":");
    hh = timeComponents[0] || "00";
    min = timeComponents[1] || "00";
  }
  
  return `${dd.padStart(2, "0")}/${mm.padStart(2, "0")}/${yyyy} ${hh.padStart(2, "0")}:${min.padStart(2, "0")}`;
}

// Computed para valores separados dos pickers
const separateValues = computed(() => {
  if (!displayValue.value) return { date: "", time: "" };
  
  const [datePart, timePart] = displayValue.value.split(" ");
  return {
    date: datePart || "",
    time: timePart || "",
  };
});

// Inicializa displayValue quando montar ou quando props.modelValue mudar
onMounted(() => {
  displayValue.value = fromIso(props.modelValue);
  updateSeparateValues();
});

watch(
  () => props.modelValue,
  (nv) => {
    displayValue.value = fromIso(nv);
    updateSeparateValues();
  }
);

// Atualiza valores separados para os pickers
function updateSeparateValues() {
  const separated = separateValues.value;
  dateValue.value = separated.date;
  timeValue.value = separated.time;
}

// Watch nos pickers para atualizar o displayValue
watch([dateValue, timeValue], ([newDate, newTime]) => {
  if (newDate && newTime) {
    displayValue.value = `${newDate} ${newTime}`;
  }
});

// Sempre que o usuário editar a data/hora, emite o formato ISO para o pai
watch(displayValue, (sv) => {
  emit("update:modelValue", toIso(sv));
});
</script>