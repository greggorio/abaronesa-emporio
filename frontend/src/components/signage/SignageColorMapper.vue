<template>
  <div class="color-mapper-container">
    <div class="color-mapper-header">
      <q-btn
        flat
        dense
        size="sm"
        color="primary"
        icon="refresh"
        label="Resetar padrão"
        @click="resetToDefaults"
      />
    </div>

    <div
      v-if="templateElements.length > 0"
      class="color-mappings-list"
    >
      <div
        v-for="element in templateElements"
        :key="element.key"
        class="color-mapping-row"
      >
        <div class="element-info">
          <div class="element-label">{{ element.label }}</div>
          <div class="element-description">{{ element.description }}</div>
        </div>

        <div class="color-selector">
          <q-select
            v-model="localMappings[element.key]"
            :options="colorSourceOptions"
            option-value="value"
            option-label="label"
            outlined
            dense
            class="color-source-select"
            @update:model-value="(val) => onColorSourceChange(element.key, val)"
          >
            <template v-slot:option="scope">
              <q-item v-bind="scope.itemProps">
                <q-item-section avatar v-if="scope.opt.color">
                  <div
                    class="color-preview-small"
                    :style="{ backgroundColor: scope.opt.color }"
                  />
                </q-item-section>
                <q-item-section>
                  <q-item-label>{{ scope.opt.label }}</q-item-label>
                </q-item-section>
              </q-item>
            </template>

            <template v-slot:selected>
              <div class="selected-color-option">
                <div
                  v-if="getSelectedColor(element.key)"
                  class="color-preview-small"
                  :style="{ backgroundColor: getSelectedColor(element.key) }"
                />
                <span>{{ getSelectedLabel(element.key) }}</span>
              </div>
            </template>
          </q-select>

          <!-- Color picker para cor customizada -->
          <div v-if="isCustomColor(element.key)" class="custom-color-picker">
            <input
              type="color"
              :value="getCustomColor(element.key)"
              @input="(e) => onCustomColorChange(element.key, e.target.value)"
            />
            <q-input
              :model-value="getCustomColor(element.key)"
              outlined
              dense
              class="custom-color-input"
              @update:model-value="
                (val) => onCustomColorChange(element.key, val)
              "
            />
          </div>
        </div>

        <!-- Preview da cor resultante -->
        <div
          class="color-result-preview"
          :style="{ backgroundColor: getResolvedColor(element.key) }"
        >
          <span
            :style="{ color: getContrastColor(getResolvedColor(element.key)) }"
          >
            {{ getResolvedColor(element.key) || "—" }}
          </span>
        </div>
      </div>
    </div>

    <div v-else class="no-elements-message">
      <q-banner class="bg-info text-white" rounded>
        <template v-slot:avatar>
          <q-icon name="info" />
        </template>
        Carregando elementos do template...
      </q-banner>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({
      templateId: null,
      elementMappings: {},
      useCustomMapping: false,
    }),
  },
  templateId: {
    type: String,
    default: null,
  },
  palette: {
    type: Object,
    default: () => ({}),
  },
});

const emit = defineEmits(["update:modelValue"]);

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Estado local
const templateElements = ref([]);
const localMappings = ref({});

// Normalização de chaves de paleta (aceita lower/upper e retorna camelCase usado no frontend)
const paletteKeyMap = {
  vibrant: "vibrant",
  muted: "muted",
  lightvibrant: "lightVibrant",
  darkvibrant: "darkVibrant",
  lightmuted: "lightMuted",
  darkmuted: "darkMuted",
  background: "background",
  text: "text",
  accent: "accent",
  accent2: "accent2",
};

const normalizePaletteKey = (key) => {
  if (!key) return "";
  const k = String(key).toLowerCase();
  return paletteKeyMap[k] || key;
};

const normalizePaletteSource = (value) => {
  if (!value || typeof value !== "string") return value;
  if (!value.startsWith("palette:")) return value;
  const rawKey = value.substring(8);
  const normalized = normalizePaletteKey(rawKey);
  return `palette:${normalized}`;
};

// Opções de origem de cor
const colorSourceOptions = computed(() => {
  const options = [];

  // Opção automática
  options.push({
    label: "Automático (padrão do template)",
    value: "auto",
    color: null,
  });

  // Cores da paleta vibrante
  const paletteColors = [
    { key: "vibrant", label: "Vibrant" },
    { key: "muted", label: "Muted" },
    { key: "lightVibrant", label: "Light Vibrant" },
    { key: "darkVibrant", label: "Dark Vibrant" },
    { key: "lightMuted", label: "Light Muted" },
    { key: "darkMuted", label: "Dark Muted" },
  ];

  paletteColors.forEach((color) => {
    if (props.palette[color.key]) {
      options.push({
        label: color.label,
        value: `palette:${color.key}`,
        color: props.palette[color.key],
      });
    }
  });

  // Cores semânticas
  const semanticColors = [
    { key: "background", label: "Cor de fundo" },
    { key: "text", label: "Cor do texto" },
    { key: "accent", label: "Cor de destaque" },
    { key: "accent2", label: "Cor de destaque secundária" },
  ];

  semanticColors.forEach((color) => {
    if (props.palette[color.key]) {
      options.push({
        label: color.label,
        value: `palette:${color.key}`,
        color: props.palette[color.key],
      });
    }
  });

  // Cor customizada
  options.push({
    label: "Cor customizada...",
    value: "custom",
    color: null,
  });

  return options;
});

// Carregar elementos do template
const loadTemplateElements = async () => {
  if (!props.templateId) return;

  try {
    const response = await apiRequest(
      `/api/signage/templates/${props.templateId}/elements`
    );
    templateElements.value = response.elements || [];

    // Inicializar mapeamentos padrão se necessário
    initializeMappings();
  } catch (error) {
    console.error("Erro ao carregar elementos do template:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao carregar elementos do template",
    });
  }
};

// Inicializar mapeamentos
const initializeMappings = () => {
  const defaults = {};

  templateElements.value.forEach((element) => {
    const saved = props.modelValue?.elementMappings?.[element.key];
    if (typeof saved === "string") {
      defaults[element.key] = normalizePaletteSource(saved);
    } else if (saved && typeof saved === "object" && saved.value) {
      defaults[element.key] = normalizePaletteSource(saved.value);
    } else {
      defaults[element.key] =
        normalizePaletteSource(element.defaultSource) || "auto";
    }
  });

  localMappings.value = defaults;
};

// Verificar se é cor customizada
const isCustomColor = (elementKey) => {
  const value = localMappings.value[elementKey];
  return typeof value === "string" && value.startsWith("custom:");
};

// Obter cor customizada
const getCustomColor = (elementKey) => {
  const value = localMappings.value[elementKey];
  if (typeof value === "string" && value.startsWith("custom:")) {
    return value.substring(7);
  }
  return "#000000";
};

// Obter cor selecionada para preview
const getSelectedColor = (elementKey) => {
  const value = localMappings.value[elementKey];
  if (!value || value === "auto") return null;

  if (typeof value === "string" && value.startsWith("palette:")) {
    const paletteKey = value.substring(8);
    return props.palette[paletteKey];
  }

  if (typeof value === "string" && value.startsWith("custom:")) {
    return value.substring(7);
  }

  return null;
};

// Obter label selecionado
const getSelectedLabel = (elementKey) => {
  const value = localMappings.value[elementKey];
  const option = colorSourceOptions.value.find((opt) => opt.value === value);
  return option?.label || "Automático";
};

// Obter cor resolvida final
const getResolvedColor = (elementKey) => {
  return getSelectedColor(elementKey);
};

// Calcular cor de contraste (preto ou branco)
const getContrastColor = (backgroundColor) => {
  if (!backgroundColor) return "#000000";

  const hex = backgroundColor.replace("#", "");
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);

  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? "#000000" : "#FFFFFF";
};

// Event handlers
const onColorSourceChange = (elementKey, value) => {
  if (value === "custom") {
    localMappings.value[elementKey] = "custom:#000000";
  } else {
    localMappings.value[elementKey] = value;
  }
  emitUpdate();
};

const onCustomColorChange = (elementKey, value) => {
  let color = value;
  if (color && !color.startsWith("#")) {
    color = "#" + color;
  }
  localMappings.value[elementKey] = `custom:${color}`;
  emitUpdate();
};

const resetToDefaults = () => {
  const defaults = {};
  templateElements.value.forEach((element) => {
    defaults[element.key] = element.defaultSource || "auto";
  });
  localMappings.value = defaults;
  emitUpdate();

  $q.notify({
    type: "positive",
    message: "Cores resetadas para o padrão do template",
  });
};

const emitUpdate = () => {
  emit("update:modelValue", {
    templateId: props.templateId,
    elementMappings: Object.fromEntries(
      Object.entries(localMappings.value || {}).map(([k, v]) => [
        k,
        normalizePaletteSource(v),
      ])
    ),
    useCustomMapping: true,
  });
};

// Watchers
watch(
  () => props.templateId,
  (newId) => {
    if (newId) {
      loadTemplateElements();
    }
  },
  { immediate: true }
);

watch(
  () => props.modelValue,
  (newValue) => {
    if (newValue) {
      if (newValue.elementMappings) {
        localMappings.value = { ...newValue.elementMappings };
      }
    }
  },
  { deep: true }
);

onMounted(() => {
  if (props.modelValue) {
    if (props.modelValue.elementMappings) {
      localMappings.value = { ...props.modelValue.elementMappings };
    }
  }
});
</script>

<style scoped>
.color-mapper-container {
  padding: 8px;
}

.color-mapper-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.color-mappings-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.color-mapping-row {
  display: grid;
  grid-template-columns: 1fr 200px 60px;
  gap: 8px;
  align-items: center;
  padding: 8px;
  background: #f8f9fa;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

.element-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.element-label {
  font-weight: 600;
  font-size: 0.85rem;
  color: #2c3e50;
}

.element-description {
  font-size: 0.75rem;
  color: #7f8c8d;
}

.color-selector {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.color-source-select {
  width: 100%;
}

.custom-color-picker {
  display: flex;
  gap: 6px;
  align-items: center;
}

.custom-color-picker input[type="color"] {
  width: 32px;
  height: 28px;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}

.custom-color-input {
  flex: 1;
}

.color-result-preview {
  width: 60px;
  height: 36px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 0.75rem;
  border: 1px solid #e0e0e0;
}

.color-preview-small {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  border: 1px solid #ddd;
}

.selected-color-option {
  display: flex;
  align-items: center;
  gap: 6px;
}

.no-elements-message {
  margin-top: 8px;
}

@media (max-width: 768px) {
  .color-mapping-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .color-result-preview {
    width: 100%;
  }
}
</style>
