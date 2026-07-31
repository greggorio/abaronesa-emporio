<template>
  <div class="signage-tab-container q-pa-md">
    <q-tabs v-model="activeTab" dense class="text-primary">
      <q-tab name="config" label="Configuração" />
      <q-tab name="templates" label="Templates" />
      <q-tab name="video" label="Vídeo" />
    </q-tabs>
    <q-separator class="q-mt-sm q-mb-md" />

    <q-tab-panels v-model="activeTab" animated>
      <q-tab-panel name="config" class="config-panel">
        <q-banner v-if="!recordId" class="bg-warning text-white" rounded>
          <template v-slot:avatar>
            <q-icon name="warning" />
          </template>
          Salve o produto para gerenciar o signage
        </q-banner>

        <div v-else class="config-layout">
          <!-- Coluna Esquerda: Controles -->
          <div class="controls-column">
            <q-card flat bordered class="signage-card">
              <q-card-section class="signage-section">
                <div class="signage-header">
                  <div class="signage-title">Signage do produto</div>
                  <div class="signage-meta">
                    <q-chip
                      v-if="status"
                      :label="`Status: ${formatStatus(status)}`"
                      color="primary"
                      text-color="white"
                      dense
                      size="sm"
                    />
                  </div>
                </div>

                <div class="compact-meta q-mt-xs">
                  <div class="signage-pill-compact">
                    Template: <strong>{{ templateApplied || "—" }}</strong>
                  </div>
                  <div class="signage-pill-compact">
                    Atualizado: <strong>{{ formattedUpdatedAt }}</strong>
                  </div>
                </div>

                <div v-if="hasChanges" class="unsaved-indicator">
                  <q-chip
                    dense
                    size="sm"
                    icon="edit"
                    label="Alterações não salvas"
                    color="orange"
                    text-color="white"
                  />
                  <q-btn
                    v-if="modifiedColors.size > 0"
                    dense
                    size="sm"
                    icon="restart_alt"
                    label="Resetar cores"
                    color="grey-8"
                    @click="resetColors"
                  />
                </div>

                <q-banner
                  v-if="errorMessage"
                  dense
                  rounded
                  class="q-mt-sm bg-red-2 text-red-9"
                  icon="error"
                >
                  {{ errorMessage }}
                </q-banner>

                <q-linear-progress
                  v-if="loading"
                  indeterminate
                  color="primary"
                  class="q-mt-sm"
                />

                <!-- Frases -->
                <section class="signage-block-compact q-mt-sm">
                  <div class="block-title-compact">Frases</div>
                  <div class="phrases-cards-grid">
                    <div
                      v-for="phraseField in phraseFields"
                      :key="phraseField.key"
                      class="phrase-card"
                      @click="startEditingPhrase(phraseField.key)"
                    >
                      <div class="phrase-card-content">
                        <div class="phrase-card-label">
                          {{ phraseField.label }}
                        </div>
                        <input
                          v-if="editingPhrase === phraseField.key"
                          type="text"
                          :value="phrases[phraseField.key] || ''"
                          :placeholder="phraseField.placeholder || ''"
                          @input="
                            updatePhraseValue(
                              phraseField.key,
                              $event.target.value
                            )
                          "
                          @blur="stopEditingPhrase"
                          @keyup.enter="stopEditingPhrase"
                          @keyup.esc="cancelEditingPhrase"
                          class="phrase-card-input"
                          ref="phraseInput"
                          @click.stop
                        />
                        <div v-else class="phrase-card-value">
                          {{
                            phrases[phraseField.key] ||
                            phraseField.placeholder ||
                            "—"
                          }}
                        </div>
                      </div>
                    </div>
                  </div>
                </section>

                <!-- Paleta -->
                <section class="signage-block-compact q-mt-sm">
                  <div class="block-title-compact">Paleta</div>

                  <div v-if="canGeneratePalette" class="q-mb-xs">
                    <q-btn
                      label="Gerar paleta (Vibrant)"
                      color="primary"
                      size="sm"
                      unelevated
                      class="full-width"
                      :loading="generatingPalette"
                      :disable="generatingPalette"
                      @click="generatePalette"
                    />
                  </div>

                  <!-- Grid de 8 cards de cores -->
                  <div class="color-cards-grid">
                    <div
                      v-for="colorField in allColorFields"
                      :key="colorField.key"
                      class="color-card"
                      :style="{ background: getColorValue(colorField.key) }"
                    >
                      <div class="color-card-overlay">
                        <div class="color-card-label">
                          {{ colorField.label }}
                          <span
                            v-if="isColorModified(colorField.key)"
                            class="modified-badge"
                            title="Clique para restaurar cor original"
                            @click.stop="resetColor(colorField.key)"
                            >×</span
                          >
                        </div>
                        <div class="color-card-value-row">
                          <span class="color-card-value">
                            {{ palette[colorField.key] || "—" }}
                          </span>
                          <SignageColorPicker
                            :model-value="palette[colorField.key]"
                            @update:model-value="
                              updateColorValue(colorField.key, $event)
                            "
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                </section>

                <!-- Botão para abrir drawer de personalização de cores -->
                <div v-if="templateApplied" class="q-mt-sm">
                  <q-btn
                    icon="palette"
                    label="Personalizar cores do template"
                    color="primary"
                    outline
                    size="sm"
                    class="full-width"
                    @click="openColorDrawer"
                  />
                </div>

                <!-- Drawer lateral para personalização de cores -->
                <div
                  v-if="colorDrawerOpen"
                  class="color-drawer-overlay"
                  @click="colorDrawerOpen = false"
                >
                  <div class="color-drawer" @click.stop>
                    <div class="color-drawer-header">
                      <div class="color-drawer-title">Personalizar Cores</div>
                      <q-btn
                        icon="close"
                        flat
                        round
                        dense
                        @click="colorDrawerOpen = false"
                      />
                    </div>
                    <div class="color-drawer-content">
                      <SignageColorMapper
                        v-model="colorMapping"
                        :template-id="templateApplied"
                        :palette="palette"
                      />
                    </div>
                  </div>
                </div>
              </q-card-section>

              <q-card-actions align="right" class="signage-actions">
                <q-btn
                  label="Salvar alterações"
                  color="primary"
                  unelevated
                  :loading="saving"
                  :disabled="loading || saving"
                  @click="saveSignage"
                />
              </q-card-actions>
            </q-card>
          </div>

          <!-- Coluna Direita: Preview -->
          <div class="preview-column">
            <div class="signage-preview-container-sticky">
              <div class="signage-preview-header">
                <div class="block-title-compact">Preview</div>
              </div>

              <div class="signage-preview-content">
                <SignageTemplatePreview
                  v-if="templateApplied !== undefined"
                  :product-id="recordId"
                  :template-preference="templateApplied"
                  :draft-palette="palette"
                  :draft-phrases="phrases"
                  :draft-color-mapping="colorMapping"
                  compact
                />
              </div>

              <div v-if="!templateApplied" class="signage-preview-empty">
                Selecione um template na aba 'Templates'
              </div>
            </div>
          </div>
        </div>
      </q-tab-panel>

      <q-tab-panel name="templates">
        <SignageTemplatePreview
          :product-id="recordId"
          :template-preference="templateApplied"
          :has-changes="hasChanges"
          :draft-palette="palette"
          :draft-phrases="phrases"
          :draft-color-mapping="draftColorMappingForPreview"
          :saved-color-mapping="normalizedSavedColorMapping"
          @select="handleTemplateSelect"
          @rendered="loadVideoInfo"
        />
      </q-tab-panel>

      <q-tab-panel name="video">
        <q-banner v-if="!recordId" class="bg-warning text-white" rounded>
          <template v-slot:avatar>
            <q-icon name="warning" />
          </template>
          Salve o produto para visualizar o vídeo do signage
        </q-banner>

        <div v-else>
          <q-banner
            v-if="videoError"
            dense
            rounded
            class="q-mt-md bg-red-2 text-red-9"
            icon="error"
          >
            {{ videoError }}
          </q-banner>

          <q-linear-progress
            v-if="loadingVideo"
            indeterminate
            color="primary"
            class="q-mt-sm"
          />

          <div v-else-if="!resolvedMp4Url" class="empty-state">
            Nenhum vídeo gerado para este produto ainda.
          </div>

          <div v-else class="signage-video-wrapper">
            <video
              class="signage-video-player"
              :src="resolvedMp4Url"
              controls
            />
          </div>
        </div>
      </q-tab-panel>
    </q-tab-panels>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import { baseApiUrl } from "@/global";
import SignageTemplatePreview from "@/components/signage/SignageTemplatePreview.vue";
import SignageColorMapper from "@/components/signage/SignageColorMapper.vue";
import SignageColorPicker from "@/components/signage/SignageColorPicker.vue";

const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  recordId: {
    type: [Number, String],
    default: null,
  },
});

const emit = defineEmits(["update:modelValue"]);

const { apiRequest } = useApiRequest();
const $q = useQuasar();

const activeTab = ref("config");
const phrases = ref({ headline: "", subtitle: "", cta: "", badge: "" });

const createEmptyPalette = () => ({
  vibrant: null,
  muted: null,
  lightVibrant: null,
  darkVibrant: null,
  lightMuted: null,
  darkMuted: null,
  background: "#F5F5F5",
  text: "#000000",
  accent: null,
  accent2: null,
  isDark: false,
});

const palette = ref(createEmptyPalette());

const editingPhrase = ref(null);
const phraseBeforeEdit = ref("");
const colorDrawerOpen = ref(false);

const phraseFields = [
  { key: "headline", label: "Headline" },
  { key: "subtitle", label: "Subtítulo" },
  { key: "cta", label: "CTA" },
  {
    key: "badge",
    label: "Etiqueta (Badge)",
    placeholder: "Ex: Clássico, Novidade...",
  },
];

const allColorFields = computed(() => [
  { key: "vibrant", label: "Preço / Destaque" },
  { key: "muted", label: "Separadores" },
  { key: "lightVibrant", label: "Gradiente Claro" },
  { key: "darkVibrant", label: "Gradiente Escuro" },
  { key: "lightMuted", label: "Texto Suave" },
  { key: "darkMuted", label: "Fundo Escuro" },
  { key: "background", label: "Cor de Fundo" },
  { key: "text", label: "Cor do Texto" },
  { key: "accent", label: "Cor Destaque 1" },
  { key: "accent2", label: "Cor Destaque 2" },
]);

const loading = ref(false);
const saving = ref(false);
const generatingPalette = ref(false);
const errorMessage = ref("");
const templateApplied = ref(null);
const status = ref(null);
const updatedAt = ref(null);
const hasChanges = ref(false);
const colorMapping = ref({});
const savedColorMapping = ref({});
const lastSavedSnapshot = ref(null);
const modifiedColors = ref(new Set());
const videoError = ref("");
const loadingVideo = ref(false);
const resolvedMp4Url = ref("");

const formattedUpdatedAt = computed(() => {
  if (!updatedAt.value) return "—";
  return new Date(updatedAt.value).toLocaleString("pt-BR");
});

const formatStatus = (statusValue) => {
  const map = {
    PENDING: "Pendente",
    RENDERED: "Renderizado",
    ERROR: "Erro",
  };
  return map[statusValue] || statusValue;
};

const primaryImageUrl = computed(() => {
  const img =
    props.modelValue?.imagemPrincipal ||
    props.modelValue?.images?.find((i) => i.isPrimary);
  if (!img) return "";

  let url = "";
  if (typeof img === "string") {
    url = img;
  } else if (typeof img === "object") {
    url = img.url || img.path || img.src || "";
  }

  if (!url) return "";
  if (url.startsWith("http://") || url.startsWith("https://")) {
    return url;
  }

  const clean = url.startsWith("/") ? url.substring(1) : url;
  return `${baseApiUrl}/${clean}`;
});

const canGeneratePalette = computed(
  () => !!props.recordId && !!primaryImageUrl.value
);

const getColorValue = (key) => {
  const color = palette.value[key];
  if (!color) return "#cccccc";
  return color;
};

const openColorDrawer = () => {
  colorDrawerOpen.value = true;
};

const updateColorValue = (key, value) => {
  if (!modifiedColors.value.has(key)) {
    modifiedColors.value.add(key);
  }
  palette.value[key] = value;
};

const startEditingPhrase = (key) => {
  phraseBeforeEdit.value = phrases.value[key] || "";
  editingPhrase.value = key;
  nextTick(() => {
    const input = document.querySelector(".phrase-card-input");
    if (input) {
      input.focus();
      input.select();
    }
  });
};

const stopEditingPhrase = () => {
  editingPhrase.value = null;
  phraseBeforeEdit.value = "";
};

const cancelEditingPhrase = () => {
  if (editingPhrase.value) {
    phrases.value[editingPhrase.value] = phraseBeforeEdit.value;
  }
  editingPhrase.value = null;
  phraseBeforeEdit.value = "";
};

const updatePhraseValue = (key, value) => {
  phrases.value[key] = value;
};

const normalizedSavedColorMapping = computed(() => {
  return savedColorMapping.value || {};
});

const draftColorMappingForPreview = computed(() => {
  if (!hasChanges.value) return {};
  return colorMapping.value;
});

const normalizeColor = (key) => {
  let val = palette.value[key];
  if (!val) return;
  val = val.trim();
  if (!/^#/.test(val)) {
    val = `#${val}`;
  }
  if (/^#[0-9A-Fa-f]{6}$/.test(val)) {
    palette.value[key] = val.toUpperCase();
  }
};

const markDirty = () => {
  if (!lastSavedSnapshot.value) {
    hasChanges.value = false;
    return;
  }
  const current = {
    phrases: phrases.value,
    palette: palette.value,
    colorMapping: colorMapping.value,
  };
  hasChanges.value =
    JSON.stringify(current) !== JSON.stringify(lastSavedSnapshot.value);
};

const isColorModified = (key) => {
  return modifiedColors.value.has(key);
};

const resetColors = () => {
  const savedPalette = lastSavedSnapshot.value?.palette || {};
  Object.keys(palette.value).forEach((key) => {
    if (key !== "isDark" && savedPalette[key] !== undefined) {
      palette.value[key] = savedPalette[key];
    }
  });
  modifiedColors.value.clear();
  markDirty();
};

const resetColor = (key) => {
  const savedPalette = lastSavedSnapshot.value?.palette || {};
  if (savedPalette[key] !== undefined) {
    palette.value[key] = savedPalette[key];
    modifiedColors.value.delete(key);
    markDirty();
  }
};

const parseJson = (value) => {
  if (!value) return {};
  if (typeof value === "object") return value;
  try {
    return JSON.parse(value);
  } catch (error) {
    return {};
  }
};

const applySignage = (signage) => {
  if (!signage) return;

  // Parse phrases e palette se vierem como string JSON
  const parsedPhrases = parseJson(signage.phrases);
  const parsedPalette = parseJson(signage.palette);

  phrases.value = {
    headline: parsedPhrases.headline || "",
    subtitle: parsedPhrases.subtitle || "",
    cta: parsedPhrases.cta || "",
    badge: parsedPhrases.badge || "",
  };
  palette.value = { ...createEmptyPalette(), ...parsedPalette };
  templateApplied.value = signage.templatePreference || null;
  status.value = signage.status || null;
  updatedAt.value = signage.updatedAt || null;
  colorMapping.value = parseJson(signage.colorMapping);
  savedColorMapping.value = parseJson(signage.colorMapping);
  const snapshot = {
    phrases: { ...phrases.value },
    palette: { ...palette.value },
    colorMapping: { ...colorMapping.value },
  };
  lastSavedSnapshot.value = snapshot;
  hasChanges.value = false;
};

const loadSignage = async () => {
  if (!props.recordId) {
    hasChanges.value = false;
    lastSavedSnapshot.value = null;
    return;
  }

  loading.value = true;
  errorMessage.value = "";

  try {
    const product = await apiRequest(`/api/produtos/${props.recordId}`);
    updatedAt.value = product.updatedAt || product.updated_at || null;
    applySignage(product.signage);
    emit("update:modelValue", product);
  } catch (error) {
    errorMessage.value =
      error?.message ||
      error?.error ||
      "Não foi possível carregar os dados do signage";
  } finally {
    loading.value = false;
  }
};

const saveSignage = async () => {
  if (!props.recordId) return;

  saving.value = true;
  errorMessage.value = "";

  try {
    const payload = {
      templatePreference: templateApplied.value,
      phrases: { ...phrases.value },
      palette: { ...palette.value },
      colorMapping: { ...colorMapping.value },
    };

    const response = await apiRequest(
      `/api/produtos/${props.recordId}/signage`,
      "PATCH",
      payload
    );

    applySignage(response);
    emit("update:modelValue", {
      ...props.modelValue,
      signage: response,
    });

    lastSavedSnapshot.value = {
      phrases: { ...phrases.value },
      palette: { ...palette.value },
      colorMapping: { ...colorMapping.value },
    };
    hasChanges.value = false;

    $q.notify({
      type: "positive",
      message: "Dados de signage salvos com sucesso",
    });
  } catch (error) {
    errorMessage.value =
      error?.message || error?.error || "Não foi possível salvar o signage";
    $q.notify({
      type: "negative",
      message: errorMessage.value,
    });
  } finally {
    saving.value = false;
  }
};

const handleTemplateSelect = (templateId) => {
  templateApplied.value = templateId;
  markDirty();
};

const loadVideoInfo = async () => {
  if (!props.recordId) {
    resolvedMp4Url.value = "";
    return;
  }
  loadingVideo.value = true;
  videoError.value = "";
  try {
    const response = await apiRequest(
      `/api/produtos/${props.recordId}/signage/preview`
    );
    resolvedMp4Url.value = response?.mp4Url || "";
  } catch (error) {
    videoError.value =
      error?.message ||
      error?.error ||
      "Não foi possível carregar o vídeo do signage";
  } finally {
    loadingVideo.value = false;
  }
};

const derivePaletteFromImage = async (imageUrl) => {
  const image = await loadImageForPalette(imageUrl);
  const pixels = extractPixelsFromImage(image);
  if (!pixels.length) {
    throw new Error("Não foi possível extrair pixels da imagem principal.");
  }
  const clusters = kMeans(pixels, 6, 10);
  if (!clusters.length) {
    throw new Error("Não foi possível agrupar a paleta da imagem.");
  }
  const swatches = classifySwatches(clusters);
  const tokens = deriveTokensFromSwatches(swatches);
  return {
    ...createEmptyPalette(),
    ...swatches,
    background: tokens.background,
    text: tokens.text,
    accent: tokens.accent,
    accent2: tokens.accent2,
    isDark: tokens.isDark,
  };
};

const loadImageForPalette = (url) =>
  new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = "Anonymous";
    image.onload = () => resolve(image);
    image.onerror = () =>
      reject(
        new Error(
          "Não foi possível carregar a imagem principal (verifique CORS e permissão de acesso)."
        )
      );
    image.src = url;
  });

const extractPixelsFromImage = (image) => {
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d");
  const width = image.naturalWidth || image.width;
  const height = image.naturalHeight || image.height;
  const maxSide = 320;
  const scale = Math.min(1, maxSide / Math.max(width, height));
  canvas.width = Math.max(1, Math.round(width * scale));
  canvas.height = Math.max(1, Math.round(height * scale));
  ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
  const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
  const pixels = [];
  const step = 4 * 6;
  for (let i = 0; i < data.length; i += step) {
    const r = data[i];
    const g = data[i + 1];
    const b = data[i + 2];
    const a = data[i + 3];
    if (a < 180) continue;
    pixels.push([r, g, b]);
  }
  return pixels;
};

const squaredDistance = (a, b) => {
  if (!a || !b) return Infinity;
  return (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2;
};

const kMeans = (pixels, k = 6, maxIter = 8) => {
  if (!pixels.length) return [];
  const centers = [];
  const sampleCount = Math.min(k, pixels.length);
  for (let i = 0; i < sampleCount; i++) {
    centers.push([...pixels[Math.floor(Math.random() * pixels.length)]]);
  }
  while (centers.length < k) {
    centers.push([...pixels[Math.floor(Math.random() * pixels.length)]]);
  }

  const assignments = new Array(pixels.length).fill(0);
  for (let iter = 0; iter < maxIter; iter++) {
    let moved = false;
    for (let idx = 0; idx < pixels.length; idx++) {
      const pixel = pixels[idx];
      let bestIndex = 0;
      let bestDistance = Infinity;
      centers.forEach((center, centerIdx) => {
        const distance = squaredDistance(pixel, center);
        if (distance < bestDistance) {
          bestDistance = distance;
          bestIndex = centerIdx;
        }
      });
      if (assignments[idx] !== bestIndex) {
        assignments[idx] = bestIndex;
        moved = true;
      }
    }
    if (!moved && iter > 0) break;
    const sums = centers.map(() => [0, 0, 0, 0]);
    assignments.forEach((centerIdx, pixelIdx) => {
      const pixel = pixels[pixelIdx];
      sums[centerIdx][0] += pixel[0];
      sums[centerIdx][1] += pixel[1];
      sums[centerIdx][2] += pixel[2];
      sums[centerIdx][3] += 1;
    });
    sums.forEach((sum, centerIdx) => {
      if (sum[3] === 0) return;
      centers[centerIdx] = [
        Math.round(sum[0] / sum[3]),
        Math.round(sum[1] / sum[3]),
        Math.round(sum[2] / sum[3]),
      ];
    });
  }

  const result = centers.map((center) => ({
    rgb: center,
    population: 0,
  }));
  assignments.forEach((centerIdx) => {
    if (result[centerIdx]) {
      result[centerIdx].population += 1;
    }
  });
  return result
    .filter((cluster) => cluster.population > 0)
    .sort((a, b) => b.population - a.population);
};

const toHex = ([r, g, b]) => {
  if (r == null || g == null || b == null) return null;
  const pad = (value) => value.toString(16).padStart(2, "0");
  return `#${pad(r)}${pad(g)}${pad(b)}`.toUpperCase();
};

const rgbToHsl = (r, g, b) => {
  r /= 255;
  g /= 255;
  b /= 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  let h = 0;
  let s = 0;
  let l = (max + min) / 2;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r:
        h = (g - b) / d + (g < b ? 6 : 0);
        break;
      case g:
        h = (b - r) / d + 2;
        break;
      case b:
        h = (r - g) / d + 4;
        break;
    }
    h /= 6;
  }
  return [h, s, l];
};

const classifySwatches = (clusters) => {
  if (!clusters.length) return {};
  const annotated = clusters.map((cluster) => {
    const [h, s, l] = rgbToHsl(cluster.rgb[0], cluster.rgb[1], cluster.rgb[2]);
    return {
      ...cluster,
      hex: toHex(cluster.rgb),
      h,
      s,
      l,
    };
  });
  const first = annotated[0] || null;
  const pick = (filterFn, fallback) =>
    annotated.filter(filterFn).sort((a, b) => b.population - a.population)[0] ||
    fallback;

  const vibrant = pick((c) => c.s >= 0.45 && c.l >= 0.25 && c.l <= 0.75, first);
  const muted = pick((c) => c.s < 0.35 && c.l >= 0.2 && c.l <= 0.8, first);
  const lightVibrant =
    pick((c) => c.s >= 0.45 && c.l > 0.7, vibrant) || vibrant;
  const darkVibrant =
    pick((c) => c.s >= 0.45 && c.l < 0.35, vibrant) || vibrant;
  const lightMuted = pick((c) => c.s < 0.35 && c.l > 0.7, muted) || muted;
  const darkMuted = pick((c) => c.s < 0.35 && c.l < 0.3, muted) || muted;

  return {
    vibrant: vibrant?.hex || null,
    muted: muted?.hex || null,
    lightVibrant: lightVibrant?.hex || null,
    darkVibrant: darkVibrant?.hex || null,
    lightMuted: lightMuted?.hex || null,
    darkMuted: darkMuted?.hex || null,
  };
};

const deriveTokensFromSwatches = (swatches) => {
  const candidates = Object.entries(swatches || {})
    .map(([name, hex]) => ({
      name,
      hex,
      luminance: hex ? getLuminance(hex) : 0,
    }))
    .filter((item) => item.hex);
  const lightest = candidates.sort((a, b) => b.luminance - a.luminance)[0];
  const background = lightest?.hex || "#F5F5F5";
  const text = bestTextColor(background);
  const accent =
    swatches?.lightVibrant ||
    swatches?.vibrant ||
    swatches?.darkVibrant ||
    swatches?.muted ||
    text;
  const accent2 =
    [
      swatches?.vibrant,
      swatches?.darkVibrant,
      swatches?.muted,
      swatches?.lightMuted,
    ].find((hex) => hex && hex !== accent) || accent;
  const isDark = getLuminance(background) < 0.5;
  return { background, text, accent, accent2, isDark };
};

const getLuminance = (hex) => relativeLuminance(hex);

const relativeLuminance = (hex) => {
  const rgb = hexToRgb(hex);
  if (!rgb) return 0;
  const [r, g, b] = rgb.map((channel) => {
    const value = channel / 255;
    return value <= 0.03928
      ? value / 12.92
      : Math.pow((value + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

const hexToRgb = (hex) => {
  const match = /^#([0-9A-F]{6})$/i.exec(hex || "");
  if (!match) return null;
  return [
    parseInt(match[1].slice(0, 2), 16),
    parseInt(match[1].slice(2, 4), 16),
    parseInt(match[1].slice(4, 6), 16),
  ];
};

const bestTextColor = (background) => {
  if (!background) return "#000000";
  return contrastRatio(background, "#FFFFFF") >=
    contrastRatio(background, "#000000")
    ? "#FFFFFF"
    : "#000000";
};

const contrastRatio = (a, b) => {
  const lumA = relativeLuminance(a);
  const lumB = relativeLuminance(b);
  const hi = Math.max(lumA, lumB);
  const lo = Math.min(lumA, lumB);
  return (hi + 0.05) / (lo + 0.05);
};

const sanitizeHex = (value) => {
  if (!value) return null;
  const raw = typeof value === "string" ? value.trim() : "";
  if (!raw) return null;
  const candidate = raw.startsWith("#") ? raw : `#${raw}`;
  return /^#[0-9A-F]{6}$/i.test(candidate) ? candidate.toUpperCase() : null;
};

const generatePalette = async () => {
  if (generatingPalette.value) return;
  generatingPalette.value = true;
  errorMessage.value = "";
  try {
    const imageUrl = primaryImageUrl.value;
    if (!imageUrl) {
      throw new Error("Imagem principal não encontrada para gerar a paleta.");
    }
    const derived = await derivePaletteFromImage(imageUrl);
    Object.keys(derived).forEach((key) => {
      if (key !== "isDark") {
        modifiedColors.value.add(key);
      }
    });
    palette.value = derived;
    markDirty();
  } catch (error) {
    errorMessage.value =
      error?.message ||
      "Não foi possível gerar a paleta. Verifique se a imagem permite CORS.";
  } finally {
    generatingPalette.value = false;
  }
};

watch(
  () => props.recordId,
  () => {
    loadSignage();
  },
  { immediate: true }
);

watch(
  () => props.modelValue?.signage,
  (newSignage) => {
    if (newSignage) {
      applySignage(newSignage);
    }
  },
  { immediate: true }
);

watch(
  phrases,
  () => {
    markDirty();
  },
  { deep: true }
);

watch(
  palette,
  () => {
    markDirty();
  },
  { deep: true }
);

watch(
  colorMapping,
  () => {
    markDirty();
  },
  { deep: true }
);

watch(activeTab, (value) => {
  if (value === "video") {
    loadVideoInfo();
  }
});
</script>

<style scoped>
.signage-tab-container {
  min-height: 180px;
}

.config-panel {
  padding: 0 !important;
}

/* Layout lado a lado */
.config-layout {
  display: grid;
  grid-template-columns: 1fr 350px;
  gap: 16px;
  align-items: start;
}

.controls-column {
  min-width: 0;
}

.preview-column {
  position: relative;
}

.signage-preview-container-sticky {
  position: sticky;
  top: 16px;
  width: 350px;
  /* In dialogs, a fixed `height: 100vh ...` forces the panel itself to scroll.
     Let the panel size to content and only cap it, keeping the scroll inside. */
  max-height: min(calc(100dvh - 385px), 70vh);
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 8px;
  background: #fcfcfd;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.08);
}

.signage-preview-container-sticky::-webkit-scrollbar {
  width: 6px;
}

.signage-preview-container-sticky::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.signage-preview-container-sticky::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.signage-preview-container-sticky::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.signage-card {
  border-radius: 12px;
}

.signage-preview-header {
  padding: 8px 12px 6px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.12);
}

.signage-preview-content {
  padding: 8px;
}

.signage-preview-empty {
  margin: 0 12px 12px;
  color: #4b5563;
  font-size: 0.9rem;
}

.signage-section {
  padding: 12px;
}

.signage-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.signage-title {
  font-size: 1rem;
  font-weight: 600;
}

.signage-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.compact-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.signage-pill-compact {
  background: #f5f6f8;
  color: #4b5563;
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 0.7rem;
  line-height: 1.4;
  white-space: nowrap;
}

.signage-block-compact {
  background: #fafafa;
  border: 1px solid #ececec;
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.block-title-compact {
  font-size: 0.8rem;
  font-weight: 600;
  color: #4b5563;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  margin-bottom: 4px;
}

.compact-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

/* Phrases Cards Grid - Layout compacto */
.phrases-cards-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.phrase-card {
  position: relative;
  border-radius: 6px;
  padding: 8px 10px;
  min-height: auto;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  border: 1px solid #dee2e6;
  cursor: pointer;
  transition: all 0.2s ease;
  overflow: hidden;
}

.phrase-card:hover {
  border-color: #1976d2;
  background: linear-gradient(135deg, #ffffff 0%, #f1f3f5 100%);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
}

.phrase-card-content {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  height: auto;
}

.phrase-card-label {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: #6c757d;
  white-space: nowrap;
  flex-shrink: 0;
}

.phrase-card-value {
  font-size: 0.8rem;
  color: #212529;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.phrase-card-value:empty::before {
  content: "—";
  color: #adb5bd;
}

.phrase-card-input {
  flex: 1;
  background: #ffffff;
  border: 2px solid #1976d2;
  border-radius: 4px;
  padding: 4px 6px;
  font-size: 0.8rem;
  color: #212529;
  outline: none;
  line-height: 1.3;
  min-width: 0;
}

.phrase-card-input:focus {
  box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.15);
}

.phrase-card-input::placeholder {
  color: #adb5bd;
  font-size: 0.75rem;
}

/* Color Cards Grid */
.color-cards-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}

.color-card {
  position: relative;
  border-radius: 8px;
  padding: 12px 8px;
  min-height: 80px;
  border: 2px solid rgba(255, 255, 255, 0.15);
  cursor: pointer;
  transition: all 0.2s ease;
  overflow: hidden;
}

.color-card:hover {
  border-color: rgba(255, 255, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.color-card-overlay {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 100%;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(4px);
  border-radius: 4px;
  padding: 8px;
}

.color-card-label {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #ffffff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
  text-align: center;
  line-height: 1.2;
}

.modified-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin-left: 4px;
  background: #ff5252;
  color: #ffffff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: bold;
  line-height: 1;
}

.color-card-value {
  font-size: 0.75rem;
  font-weight: 600;
  color: #ffffff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
  font-family: "Courier New", monospace;
}

.color-card-value-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  width: 100%;
}

.color-picker-input {
  width: 100%;
  margin-top: 4px;
}

.color-card-input:focus {
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(25, 118, 210, 0.2);
}

.color-picker-input {
  width: 100%;
  margin-top: 4px;
}

.grow {
  flex: 1;
}

.signage-actions {
  padding: 8px 16px 16px;
}

.unsaved-indicator {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}

.full-width {
  width: 100%;
}

.color-mapping-section {
  margin-top: 4px;
}

/* Drawer de personalização de cores */
.color-drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.color-drawer {
  width: 400px;
  max-width: 90vw;
  height: 100%;
  background: #ffffff;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  animation: slideIn 0.3s ease;
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

.color-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #e0e0e0;
  background: #f5f5f5;
}

.color-drawer-title {
  font-size: 1rem;
  font-weight: 600;
  color: #333;
}

.color-drawer-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.empty-state {
  border-radius: 12px;
  padding: 24px 16px;
  text-align: center;
  color: #4b5563;
  background: #f8fafc;
  border: 1px dashed rgba(15, 23, 42, 0.1);
  margin-top: 12px;
}

.signage-video-wrapper {
  margin-top: 16px;
  background: #0b0b0f;
  border-radius: 12px;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, 0.1);
}

.signage-video-player {
  width: 100%;
  max-height: 70vh;
  border-radius: 8px;
  background: #000;
}

/* Responsivo - volta ao layout original em telas menores */
@media (max-width: 1200px) {
  .config-layout {
    grid-template-columns: 1fr;
  }

  .preview-column {
    order: -1;
  }

  .signage-preview-container-sticky {
    position: relative;
    top: 0;
    width: 100%;
    max-width: 240px;
    height: auto;
    max-height: none;
    margin: 0 auto 16px;
  }
}

@media (max-width: 700px) {
  .compact-grid {
    grid-template-columns: 1fr;
  }

  .phrases-cards-grid {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .phrase-card-content {
    gap: 6px;
  }

  .color-cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
