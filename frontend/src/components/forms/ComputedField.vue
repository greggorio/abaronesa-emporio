<template>
  <div :class="field?.cols || 'col-6'">
    <q-input
      dense
      outlined
      stack-label
      :label="field?.label || 'Valor Calculado'"
      v-model="displayValue"
      :prefix="field?.prefix"
      :suffix="field?.suffix"
      readonly
      :error="!!error"
      :error-message="error"
      v-bind="field?.props"
    />
  </div>
</template>

<script setup>
import { computed, inject, watch, ref, toRaw, nextTick } from "vue";

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { default: null },
  error: { type: String, default: null },
  disabled: { type: Boolean, default: false },
});

const emit = defineEmits(["update:model-value"]);

// formContext provides reactive formData for formula evaluation
const formContext = inject("formContext", null);
const displayValue = ref(props.modelValue);

// Debug: Log inicial
console.log("ComputedField inicializado:", {
  field: props.field,
  formula: props.field?.formula,
  formContext: formContext,
  formData: formContext?.formData,
});

const calculate = () => {
  if (!props.field?.formula) return props.modelValue;

  try {
    const data = formContext?.formData?.value || formContext?.formData || {};

    // Debug: Log dos dados antes do cálculo
    console.log("ComputedField - Calculando:", {
      formula: props.field.formula,
      data: data,
      itens: data.itens,
      itensLength: data.itens?.length,
      primeiroItem: data.itens?.[0],
    });

    // Provide utility functions for formula
    const sum = (arr) => {
      if (!Array.isArray(arr)) {
        console.log("ComputedField - sum: não é array", arr);
        return 0;
      }
      const result = arr.reduce((acc, v) => acc + Number(v || 0), 0);
      console.log("ComputedField - sum resultado:", result, "de", arr);
      return result;
    };

    const avg = (arr) => {
      if (!Array.isArray(arr) || arr.length === 0) return 0;
      return sum(arr) / arr.length;
    };

    const count = (arr) => {
      if (!Array.isArray(arr)) return 0;
      return arr.length;
    };

    // Create function with utilities
    const fn = new Function("data", "sum", "avg", "count", `with(data){ return ${props.field.formula} }`);

    const result = fn(toRaw(data), sum, avg, count);

    console.log("ComputedField - Resultado do cálculo:", result);

    // Format result if needed
    if (typeof result === "number") {
      // Round to 2 decimal places for currency/numeric fields
      return Math.round(result * 100) / 100;
    }

    return result;
  } catch (err) {
    console.error("Erro ao calcular fórmula:", props.field.formula, err);
    return 0;
  }
};

const updateValue = () => {
  console.log("ComputedField - updateValue chamado");
  const val = calculate();
  displayValue.value = val;
  emit("update:model-value", val);
};

// Initial calculation
updateValue();

// Watch for changes in formData
if (formContext?.formData) {
  // Watch direto no formData
  watch(
    () => formContext.formData.value || formContext.formData,
    (newData, oldData) => {
      console.log("ComputedField - formData mudou:", {
        old: oldData,
        new: newData,
        itensOld: oldData?.itens,
        itensNew: newData?.itens,
      });
      updateValue();
    },
    { deep: true, immediate: false }
  );

  // Watch específico para o array de itens se a fórmula usar
  if (props.field.formula?.includes("itens")) {
    watch(
      () => {
        const data = formContext.formData.value || formContext.formData;
        return data?.itens;
      },
      (newItens, oldItens) => {
        console.log("ComputedField - itens mudaram:", {
          old: oldItens,
          new: newItens,
          oldLength: oldItens?.length,
          newLength: newItens?.length,
        });
        nextTick(() => {
          updateValue();
        });
      },
      { deep: true, immediate: false }
    );
  }
}

// Also watch for manual updates to the model value
watch(
  () => props.modelValue,
  (newVal) => {
    console.log("ComputedField - modelValue mudou:", newVal);
    if (newVal !== displayValue.value) {
      displayValue.value = newVal;
    }
  }
);

// Expose method to force recalculation
defineExpose({
  recalculate: updateValue,
});
</script>
