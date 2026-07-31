<template>
  <div class="color-picker-wrapper">
    <q-btn dense flat size="sm" icon="edit" class="color-edit-btn">
      <q-popup-proxy transition-show="scale" transition-hide="scale">
        <q-card style="min-width: 200px">
          <q-card-section class="q-pa-sm">
            <div class="text-caption text-grey-7 q-mb-sm">Selecione a cor</div>
            <Sketch
              :model-value="colorObject"
              @update:model-value="onColorChange"
              :disable-alpha="true"
              :preset-colors="presetColors"
            />
          </q-card-section>
          <q-card-section class="q-pt-none">
            <q-input
              :model-value="formattedHex"
              @update:model-value="onHexInput"
              dense
              outlined
              maxlength="7"
              placeholder="#2596be"
              class="hex-input"
            >
              <template v-slot:prepend>
                <span class="hash">#</span>
              </template>
            </q-input>
          </q-card-section>
        </q-card>
      </q-popup-proxy>
    </q-btn>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { Sketch } from "@ckpack/vue-color";

const props = defineProps({
  modelValue: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["update:modelValue"]);

// Cores pré-definidas úteis para signage
const presetColors = [
  "#FF0000",
  "#FF4500",
  "#FF8C00",
  "#FFD700",
  "#FFFF00",
  "#9ACD32",
  "#32CD32",
  "#00FF00",
  "#00FA9A",
  "#00CED1",
  "#00BFFF",
  "#1E90FF",
  "#4169E1",
  "#0000FF",
  "#8A2BE2",
  "#9400D3",
  "#FF00FF",
  "#FF1493",
  "#DC143C",
  "#000000",
  "#333333",
  "#666666",
  "#999999",
  "#CCCCCC",
  "#FFFFFF",
  "#F5F5F5",
  "#FFE4E1",
  "#FFF8DC",
  "#F0F8FF",
  "#F0FFF0",
];

// Converte string HEX para objeto de cor esperado pelo vue-color
const colorObject = computed(() => {
  const hex = props.modelValue || "#000000";
  return {
    hex: hex.replace(/^#/, ""),
  };
});

// HEX formatado sem # para exibição no input
const formattedHex = computed(() => {
  if (!props.modelValue) return "";
  return props.modelValue.replace(/^#/, "").toUpperCase();
});

const onColorChange = (color) => {
  if (color && color.hex) {
    const hexValue = color.hex.startsWith("#") ? color.hex : `#${color.hex}`;
    emit("update:modelValue", hexValue.toUpperCase());
  }
};

const onHexInput = (value) => {
  if (!value) return;

  // Remove qualquer # que o usuário possa ter digitado
  let cleanValue = value.replace(/^#/, "");

  // Limita a 6 caracteres
  cleanValue = cleanValue.slice(0, 6);

  // Só emite se for um HEX válido (3 ou 6 caracteres hexadecimais)
  if (/^[0-9A-Fa-f]{6}$/.test(cleanValue)) {
    emit("update:modelValue", `#${cleanValue.toUpperCase()}`);
  } else if (/^[0-9A-Fa-f]{3}$/.test(cleanValue)) {
    // Converte HEX curto (ABC) para longo (AABBCC)
    const expanded = cleanValue
      .split("")
      .map((c) => c + c)
      .join("");
    emit("update:modelValue", `#${expanded.toUpperCase()}`);
  }
};
</script>

<style scoped>
.color-picker-wrapper {
  display: inline-flex;
}

.color-edit-btn {
  color: rgba(255, 255, 255, 0.9);
}

/* Ajustes de estilo para o Sketch picker */
:deep(.vc-sketch) {
  width: 180px;
  box-shadow: none;
  padding: 6px;
}

:deep(.vc-sketch-saturation-wrap) {
  border-radius: 4px;
  overflow: hidden;
  height: 100px;
}

:deep(.vc-sketch-presets) {
  max-height: 60px;
  overflow-y: auto;
}

:deep(.vc-sketch-presets-color) {
  width: 14px;
  height: 14px;
}

:deep(.vc-sketch-field) {
  display: none;
}

.hex-input {
  margin-top: 4px;
}

.hash {
  color: #666;
  font-weight: 500;
  font-size: 0.8rem;
}
</style>
