<template>
  <div class="signage-template-preview">
    <q-banner
      v-if="previewError"
      dense
      rounded
      class="bg-red-2 text-red-9 q-mb-md"
      icon="error"
    >
      {{ previewError }}
    </q-banner>

    <div v-if="!props.productId" class="empty-state">
      Salve o produto para liberar a prévia do template.
    </div>

    <div v-else-if="loadingPreview" class="empty-state">
      Carregando prévia do grid...
    </div>

    <div v-else-if="!previewData" class="empty-state">
      Configure a paleta, frases e template do produto para visualizar o grid real.
    </div>

    <div v-else>
      <q-banner
        v-if="showIaFallback"
        dense
        class="bg-warning text-dark"
        rounded
        icon="warning"
      >
        Imagem IA indisponível. Usando imagem original.
      </q-banner>
      <div class="template-grid">
	        <q-card
	          v-for="template in previewItems"
	          :key="template.key || template.id"
	          flat
	          bordered
          :class="[
            'template-card',
            'template-card--clickable',
            { 'template-card--selected': isSelected(template) }
	          ]"
	          @click="handleSelect(template)"
	        >
	          <q-card-section v-if="!props.compact" class="template-card-header">
	            <div class="template-card-header-row">
	              <div class="template-title-row">
	                <div class="template-title">
	                  {{ template.title }}
	                </div>
	                <q-btn
	                  dense
	                  flat
	                  icon="edit"
	                  size="sm"
	                  color="grey-7"
	                  @click.stop="openEditDialog(template)"
	                />
	              </div>
	              <q-btn
	                dense
	                flat
	                color="primary"
	                label="IA"
	                icon="image"
	                @click.stop="openAiDialog(template)"
	              />
	            </div>
	          </q-card-section>
	          <q-separator v-if="!props.compact" />
	          <q-card-section class="template-card-body" @click="openPreview(template)">
	            <div
	              class="template-preview-wrapper"
	              :ref="(el) => registerThumbWrapper(el, template.id)"
	            >
	              <div v-if="props.compact" class="template-name-badge">
	                {{ template.title }}
	              </div>
	              <div class="template-frame">
	                <iframe
	                  :key="template.key || template.id"
	                  :srcdoc="template.srcdoc"
	                  width="1920"
	                  height="1080"
	                  frameborder="0"
	                  scrolling="no"
	                ></iframe>
	              </div>
	              <div class="template-overlay">
	                <q-icon name="fullscreen" size="48px" color="white" />
	              </div>
	            </div>
	          </q-card-section>
	      </q-card>
	      </div>
	    </div>

    <q-dialog v-model="previewDialog" maximized>
      <q-card class="preview-modal fullscreen">
        <q-card-section class="preview-modal-header">
          <div class="preview-modal-title">{{ previewTemplate?.title }}</div>
          <q-chip
            v-if="renderStatusLabel"
            dense
            :color="renderStatusColor"
            text-color="white"
            :label="renderStatusLabel"
          />
          <q-space />
          <q-btn
            label="Gerar vídeo"
            color="primary"
            unelevated
            :loading="renderingVideo"
            :disable="renderButtonDisabled"
            @click="handleGenerateVideo"
          />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>
        <q-separator />
        <q-card-section class="preview-modal-body">
          <div ref="previewStageRef" class="preview-stage preview-stage--fullscreen">
            <div
              v-if="previewTemplate"
              class="template-frame"
              :style="previewFrameStyle"
            >
              <iframe
                :key="previewTemplate.key || previewTemplate.id"
                :srcdoc="previewTemplate.srcdoc"
                width="1920"
                height="1080"
                frameborder="0"
                scrolling="no"
              ></iframe>
            </div>
          </div>
        </q-card-section>
      </q-card>
    </q-dialog>
    <q-dialog v-model="editDialogOpen" persistent>
      <q-card class="template-edit-dialog">
        <q-card-section>
          <div class="row items-center justify-between">
            <div class="text-h6">Editar template</div>
            <q-btn dense flat icon="close" @click="closeEditDialog" />
          </div>
          <div class="text-caption text-grey-7 q-mt-xs">
            {{ editTemplateId || "—" }}
          </div>
          <q-banner
            v-if="editError"
            dense
            rounded
            class="q-mt-md bg-red-2 text-red-9"
            icon="error"
          >
            {{ editError }}
          </q-banner>
          <q-linear-progress
            v-if="editLoading"
            indeterminate
            color="primary"
            class="q-mt-sm"
          />
          <div class="q-mt-md">
            <q-input
              v-model="editForm.name"
              label="Nome do template"
              outlined
              dense
              :disable="editLoading || editSaving"
            />
            <q-input
              v-model="editForm.aiPrompt"
              label="Prompt da IA"
              type="textarea"
              outlined
              class="q-mt-sm"
              autogrow
              :disable="editLoading || editSaving"
            />
          </div>
        </q-card-section>

        <q-card-actions align="right" class="q-pt-none">
          <q-btn flat label="Cancelar" color="grey-7" @click="closeEditDialog" />
          <q-btn
            label="Salvar"
            color="primary"
            unelevated
            :loading="editSaving"
            :disable="editLoading || editSaving"
            @click="handleSaveTemplate"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
    <SignageTemplateAiDialog
      v-if="aiDialogTemplate"
      v-model="showAiDialog"
      :product-id="props.productId"
      :template-id="aiDialogTemplate.id"
      :template-name="aiDialogTemplate.title"
      :current-image-url="currentImageUrl"
      @refresh="loadPreview"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import { templatePreviews } from "@/components/signage/templates";
import { baseApiUrl } from "@/global";
import SignageTemplateAiDialog from "@/components/signage/SignageTemplateAiDialog.vue";
import { getTemplates as getSignageTemplates, getTemplateById, updateTemplate } from "@/services/signageTemplateService";

const props = defineProps({
  productId: {
    type: [Number, String],
    default: null,
  },
  templatePreference: {
    type: String,
    default: null,
  },
  hasChanges: {
    type: Boolean,
    default: false,
  },
  // Optional draft overrides (used by the config tab for live preview).
  draftPalette: {
    type: Object,
    default: null,
  },
  draftPhrases: {
    type: Object,
    default: null,
  },
  // Shape: { templateId, elementMappings, useCustomMapping }
  draftColorMapping: {
    type: Object,
    default: null,
  },
  // Saved mapping can be used to compute resolvedColors when draft mapping is not provided.
	  savedColorMapping: {
	    type: Object,
	    default: null,
	  },
	  // When true, hides the card header/actions and shows a small title badge on the preview.
	  compact: {
	    type: Boolean,
	    default: false,
	  },
	});

const emit = defineEmits(["select", "rendered"]);

const { apiRequest } = useApiRequest();
const $q = useQuasar();

const previewData = ref(null);
const loadingPreview = ref(false);
const previewError = ref("");

const previewDialog = ref(false);
const previewTemplate = ref(null);
const previewStageRef = ref(null);
const previewScale = ref(1);
const previewOffset = ref({ x: 0, y: 0 });
let previewObserver = null;
let thumbObserver = null;
const thumbWrappers = new Map();
const aiDialogTemplate = ref(null);
const showAiDialog = ref(false);
const templateMetaMap = ref({});
const editDialogOpen = ref(false);
const editTemplateId = ref(null);
const editForm = ref({ name: "", aiPrompt: "" });
const editLoading = ref(false);
const editSaving = ref(false);
const editError = ref("");
const renderingVideo = ref(false);
const renderStatus = ref("idle");
const renderError = ref("");
const previewFrameStyle = computed(() => ({
  transform: `scale(${previewScale.value})`,
  transformOrigin: "top left",
  width: "1920px",
  height: "1080px",
  position: "absolute",
  top: `${previewOffset.value.y}px`,
  left: `${previewOffset.value.x}px`,
}));

const selectedTemplateId = computed(() => {
  return (
    previewData.value?.templatePreference ||
    props.templatePreference ||
    templatePreviews[0]?.id
  );
});

const selectedTemplate = computed(() =>
  templatePreviews.find((template) => template.id === selectedTemplateId.value) || templatePreviews[0]
);

const normalizeHex = (value) => {
  if (!value || typeof value !== "string") return null;
  const raw = value.trim();
  if (!raw) return null;
  const candidate = raw.startsWith("#") ? raw : `#${raw}`;
  return /^#[0-9A-F]{6}$/i.test(candidate) ? candidate.toUpperCase() : null;
};

const resolveColorValue = (value, palette) => {
  if (!value) return null;
  if (typeof value === "string") {
    // Prefer palette token if it exists (e.g. "background", "accent", etc).
    if (palette && value in palette) {
      const tokenValue = palette[value];
      const resolvedToken = normalizeHex(tokenValue);
      if (resolvedToken) return resolvedToken;
    }
    const resolvedHex = normalizeHex(value);
    if (resolvedHex) return resolvedHex;
  }
  return null;
};

const resolveColorsFromMapping = (mapping, palette) => {
  const elementMappings = mapping?.elementMappings || {};
  const resolved = {};
  Object.entries(elementMappings).forEach(([key, value]) => {
    const color = resolveColorValue(value, palette);
    if (color) resolved[key] = color;
  });
  return resolved;
};

const previewPayload = computed(() => {
  if (!previewData.value) return null;
  const product = previewData.value.product || {};
  const phrases =
    props.draftPhrases && typeof props.draftPhrases === "object"
      ? props.draftPhrases
      : previewData.value.phrases || {};
  const palette = buildPaletteWithFallback(
    props.draftPalette && typeof props.draftPalette === "object"
      ? props.draftPalette
      : previewData.value.palette || {}
  );
  const badge = phrases.badge || product.badgeText || "";

  let imageUrl = previewData.value.imageUrl || "";
  if (imageUrl && !imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
    const clean = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
    imageUrl = `${baseApiUrl}/${clean}`;
  }

  const baseResolvedColors = previewData.value.resolvedColors || {};
  const mappingForResolve =
    props.draftColorMapping ||
    props.savedColorMapping ||
    null;
  const resolvedFromMapping = mappingForResolve
    ? resolveColorsFromMapping(mappingForResolve, palette)
    : {};

  return {
    badge,
    headline: phrases.headline || product.nome || "Produto",
    subtitle: phrases.subtitle || product.descricao || "",
    cta: phrases.cta || "Peça já!",
    price: phrases.price || formatPrice(product.precoVenda),
    image: imageUrl,
    palette,
    resolvedColors: {
      ...baseResolvedColors,
      ...resolvedFromMapping,
    },
  };
});

const showIaFallback = computed(() => {
  return Boolean(previewData.value?.isUsingAiImage) && previewData.value?.aiImageAvailable === false;
});

const renderStatusLabel = computed(() => {
  switch (renderStatus.value) {
    case "rendering":
      return "Gerando vídeo";
    case "success":
      return "Vídeo pronto";
    case "error":
      return "Erro ao gerar";
    default:
      return null;
  }
});

const renderStatusColor = computed(() => {
  switch (renderStatus.value) {
    case "rendering":
      return "primary";
    case "success":
      return "positive";
    case "error":
      return "negative";
    default:
      return "grey-7";
  }
});

const renderButtonDisabled = computed(() => {
  return (
    renderingVideo.value ||
    !previewTemplate.value ||
    !previewPayload.value ||
    Boolean(props.hasChanges)
  );
});

const decoratedTemplates = computed(() => {
  if (!templatePreviews || !templatePreviews.length) return [];
  return templatePreviews.map((template) => ({
    ...template,
    title: templateMetaMap.value?.[template.id]?.name || template.title,
    srcdoc: injectPreviewData(template.srcdoc, previewPayload.value),
    key: `${template.id}-${previewData.value?.product?.id}-${previewData.value?.product?.updatedAt || Date.now()}`,
  }));
});

const previewItems = computed(() => decoratedTemplates.value);

const isSelected = (template) => template?.id === selectedTemplateId.value;

const templateAiEnabled = (templateId) => {
  return templateMetaMap.value?.[templateId]?.aiEnabled ?? false;
};

const handleSelect = (template) => {
  if (!template) return;
  emit("select", template.id);
  openPreview(template);
};

const openPreview = (template) => {
  previewTemplate.value = template;
  renderStatus.value = "idle";
  renderError.value = "";
  previewDialog.value = true;
};

const currentImageUrl = computed(() => previewPayload.value?.image || "");

const openAiDialog = (template) => {
  aiDialogTemplate.value = template;
  showAiDialog.value = true;
};

const openEditDialog = async (template) => {
  if (!template?.id) return;
  editTemplateId.value = template.id;
  editForm.value = {
    name: templateMetaMap.value?.[template.id]?.name || template.title || "",
    aiPrompt: "",
  };
  editError.value = "";
  editLoading.value = true;
  editDialogOpen.value = true;
  try {
    const details = await getTemplateById(template.id);
    editForm.value = {
      name: details?.name || editForm.value.name,
      aiPrompt: details?.aiPrompt || "",
    };
  } catch (error) {
    editError.value = error?.message || "Não foi possível carregar o template.";
  } finally {
    editLoading.value = false;
  }
};

const closeEditDialog = () => {
  editDialogOpen.value = false;
  editTemplateId.value = null;
  editError.value = "";
  editLoading.value = false;
  editSaving.value = false;
};

const handleSaveTemplate = async () => {
  if (!editTemplateId.value) return;
  editSaving.value = true;
  editError.value = "";
  try {
    const payload = {
      name: editForm.value?.name || "",
      aiPrompt: editForm.value?.aiPrompt ?? "",
    };
    const updated = await updateTemplate(editTemplateId.value, payload);
    templateMetaMap.value = {
      ...templateMetaMap.value,
      [editTemplateId.value]: {
        aiEnabled: templateMetaMap.value?.[editTemplateId.value]?.aiEnabled ?? false,
        name: updated?.name || payload.name,
      },
    };
    $q.notify({
      type: "positive",
      message: "Template atualizado com sucesso.",
    });
    closeEditDialog();
  } catch (error) {
    editError.value = error?.message || "Não foi possível salvar o template.";
    $q.notify({
      type: "negative",
      message: editError.value,
    });
  } finally {
    editSaving.value = false;
  }
};

const updateThumbScaleFor = (target) => {
  if (!target) return;
  const { width, height } = target.getBoundingClientRect();
  if (!width || !height) return;
  // Use "cover" strategy: scale to fill the container completely
  const scaleX = width / 1920;
  const scaleY = height / 1080;
  const scale = Math.max(scaleX, scaleY);
  const scaledWidth = 1920 * scale;
  const scaledHeight = 1080 * scale;
  const offsetX = Math.round((width - scaledWidth) / 2);
  const offsetY = Math.round((height - scaledHeight) / 2);
  target.style.setProperty("--thumb-scale", scale.toFixed(4));
  target.style.setProperty("--thumb-offset-x", `${offsetX}px`);
  target.style.setProperty("--thumb-offset-y", `${offsetY}px`);
};

const updatePreviewScale = () => {
  const stage = previewStageRef.value;
  if (!stage) return;
  const { width, height } = stage.getBoundingClientRect();
  if (!width || !height) return;
  // Use "cover" strategy: scale to fill the stage completely
  const scaleX = width / 1920;
  const scaleY = height / 1080;
  const scale = Math.max(scaleX, scaleY);
  const scaledWidth = 1920 * scale;
  const scaledHeight = 1080 * scale;
  previewScale.value = Math.max(0.1, Number(scale.toFixed(4)));
  previewOffset.value = {
    x: Math.round((width - scaledWidth) / 2),
    y: Math.round((height - scaledHeight) / 2),
  };
};

const bindPreviewObserver = async () => {
  await nextTick();
  updatePreviewScale();
  if (previewStageRef.value && !previewObserver) {
    previewObserver = new ResizeObserver(() => updatePreviewScale());
    previewObserver.observe(previewStageRef.value);
  }
};

const unbindPreviewObserver = () => {
  if (previewObserver && previewStageRef.value) {
    previewObserver.unobserve(previewStageRef.value);
    previewObserver.disconnect();
    previewObserver = null;
  }
};

const bindThumbObserver = async () => {
  await nextTick();
  if (!thumbObserver) {
    thumbObserver = new ResizeObserver((entries) => {
      entries.forEach((entry) => updateThumbScaleFor(entry.target));
    });
  }
  thumbWrappers.forEach((wrapper) => {
    updateThumbScaleFor(wrapper);
    thumbObserver.observe(wrapper);
  });
};

const unbindThumbObserver = () => {
  if (thumbObserver) {
    thumbObserver.disconnect();
    thumbObserver = null;
  }
};

const registerThumbWrapper = (el, templateId) => {
  if (!templateId) return;
  if (el) {
    thumbWrappers.set(templateId, el);
    if (thumbObserver) {
      updateThumbScaleFor(el);
      thumbObserver.observe(el);
    }
  } else {
    const existing = thumbWrappers.get(templateId);
    if (existing && thumbObserver) {
      thumbObserver.unobserve(existing);
    }
    thumbWrappers.delete(templateId);
  }
};

const loadPreview = async () => {
  if (!props.productId) {
    previewData.value = null;
    previewError.value = "";
    return;
  }
  loadingPreview.value = true;
  previewError.value = "";
  try {
    const response = await apiRequest(`/api/produtos/${props.productId}/signage/preview`);
    previewData.value = response;
    if (!renderingVideo.value) {
      renderStatus.value = response?.mp4Url ? "success" : "idle";
      renderError.value = "";
    }
    await nextTick(() => bindThumbObserver());
  } catch (error) {
    previewData.value = null;
    previewError.value = error?.message || "Não foi possível carregar a prévia do template.";
  } finally {
    loadingPreview.value = false;
  }
};

const loadTemplateAiConfig = async () => {
  try {
    const templates = await getSignageTemplates();
    templateMetaMap.value = templates.reduce((acc, template) => {
      acc[template.templateId] = {
        aiEnabled: template.aiEnabled,
        name: template.name,
      };
      return acc;
    }, {});
  } catch (error) {
    console.warn("Não foi possível carregar templates IA", error);
  }
};

watch(
  () => previewDialog.value,
  (isOpen) => {
    if (isOpen) {
      bindPreviewObserver();
    } else {
      unbindPreviewObserver();
    }
  }
);

watch(
  () => [props.productId, props.templatePreference],
  () => {
    loadPreview();
  },
  { immediate: true }
);

watch(showAiDialog, (visible) => {
  if (!visible) {
    aiDialogTemplate.value = null;
  }
});

onMounted(() => {
  nextTick(() => {
    bindThumbObserver();
  });
  loadTemplateAiConfig();
});

onBeforeUnmount(() => {
  unbindPreviewObserver();
  unbindThumbObserver();
});

const formatPrice = (value) => {
  if (value == null) return "";
  const number = Number(value);
  if (Number.isNaN(number)) return "";
  return number.toLocaleString("pt-BR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

const injectPreviewData = (srcdoc, data) => {
  if (!data || !srcdoc) return srcdoc;

  const safeData = JSON.stringify(data)
    .replace(/</g, "\\u003c")
    .replace(/>/g, "\\u003e")
    .replace(/&/g, "\\u0026")
    .replace(/\//g, "\\/");

  const scriptContent = `<script>
  (function() {
    window.__SIGNAGE_PREVIEW__ = ${safeData};
    window.dispatchEvent(new CustomEvent('__SIGNAGE_PREVIEW_READY__'));
  })();
<\/script>`;

  return srcdoc.replace(/<\/body>/i, `${scriptContent}</body>`);
};

const buildRenderHtml = (templateId) => {
  const baseTemplate =
    templatePreviews.find((template) => template.id === templateId) || selectedTemplate.value;
  if (!baseTemplate?.srcdoc) return "";
  return injectPreviewData(baseTemplate.srcdoc, previewPayload.value);
};

const handleGenerateVideo = async () => {
  if (renderingVideo.value) return;
  if (props.hasChanges) {
    $q.notify({
      type: "warning",
      message: "Salve as alterações antes de gerar o vídeo.",
    });
    return;
  }
  if (!previewTemplate.value || !previewPayload.value) {
    $q.notify({
      type: "warning",
      message: "Prévia indisponível para gerar o vídeo.",
    });
    return;
  }

  const templateId = previewTemplate.value.id;
  const html = buildRenderHtml(templateId);
  if (!html) {
    $q.notify({
      type: "negative",
      message: "Template inválido para renderização.",
    });
    return;
  }

  renderingVideo.value = true;
  renderStatus.value = "rendering";
  renderError.value = "";
  try {
    const payload = {
      templateId,
      html,
      width: 1920,
      height: 1080,
      fps: 30,
      durationMs: 8000,
    };

    await apiRequest(`/api/produtos/${props.productId}/signage/render`, "POST", payload);

    renderStatus.value = "success";
    $q.notify({
      type: "positive",
      message: "Vídeo gerado com sucesso.",
    });
    await loadPreview();
    emit("rendered");
  } catch (error) {
    renderStatus.value = "error";
    renderError.value = error?.message || "Não foi possível gerar o vídeo.";
    $q.notify({
      type: "negative",
      message: renderError.value,
    });
  } finally {
    renderingVideo.value = false;
  }
};

const buildPaletteWithFallback = (palette) => {
  const p = palette || {};
  const choose = (...opts) => opts.find((v) => v && typeof v === "string") || null;
  return {
    vibrant: p.vibrant || choose(p.accent, p.accent2, p.background),
    muted: p.muted || p.background || null,
    lightVibrant: p.lightVibrant || p.background || null,
    darkVibrant: p.darkVibrant || p.accent2 || p.text || null,
    lightMuted: p.lightMuted || p.background || null,
    darkMuted: p.darkMuted || p.text || p.accent || null,
    background: p.background || "#141621",
    text: p.text || "#f7f7fb",
    accent: p.accent || p.vibrant || "#ff6b35",
    accent2: p.accent2 || p.darkVibrant || p.accent || "#a67c52",
    isDark: typeof p.isDark === "boolean" ? p.isDark : null,
  };
};
</script>

<style scoped>
.signage-template-preview {
  display: flex;
  flex-direction: column;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.template-card {
  border-radius: 12px;
  overflow: hidden;
}

.template-card--clickable {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.template-card--selected {
  border: 2px solid var(--q-primary);
  box-shadow: 0 12px 30px color-mix(in srgb, var(--q-primary) 25%, transparent);
}

.template-card--clickable:hover {
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
}

.template-card-header {
  padding: 12px 16px;
}

.template-card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.template-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.template-title {
  font-size: 1rem;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.template-subtitle {
  font-size: 0.85rem;
  color: #6b7280;
}

.template-card-body {
  padding: 12px;
}

.template-preview-wrapper {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 12px;
  overflow: hidden;
  background: #0b0b0f;
  border: 1px solid #e5e7eb;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  --thumb-scale: 0.167;
  --thumb-offset-x: 0px;
  --thumb-offset-y: 0px;
}

.template-name-badge {
  position: absolute;
  left: 10px;
  bottom: 10px;
  max-width: calc(100% - 20px);
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: 0.01em;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(0, 0, 0, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(6px);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
  z-index: 2;
}

.template-frame {
  width: 1920px;
  height: 1080px;
  overflow: hidden;
  position: absolute;
  top: 0;
  left: 0;
  transform: translate(var(--thumb-offset-x), var(--thumb-offset-y)) scale(var(--thumb-scale));
  transform-origin: top left;
}

.template-frame iframe {
  width: 100%;
  height: 100%;
  border: 0;
  display: block;
  pointer-events: none;
}

.template-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s ease;
  pointer-events: none;
  will-change: opacity;
  transform: translateZ(0);
}

.template-preview-wrapper:hover .template-overlay {
  opacity: 1;
  pointer-events: auto;
}

.empty-state {
  border-radius: 12px;
  padding: 28px 18px;
  text-align: center;
  color: #4b5563;
  background: #f8fafc;
  border: 1px dashed rgba(15, 23, 42, 0.1);
  margin-bottom: 12px;
}

.preview-modal-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 8px;
}

.preview-modal-title {
  font-size: 1.05rem;
  font-weight: 600;
}

.preview-modal-body {
  padding: 0;
  height: calc(100vh - 72px);
}

.template-edit-dialog {
  min-width: min(560px, 92vw);
}

.preview-stage {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #0b0b0f;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-stage .template-frame {
  position: absolute;
  top: 50%;
  left: 50%;
  flex-shrink: 0;
}

.preview-stage--fullscreen::before {
  content: "";
  position: absolute;
  inset: 24px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  pointer-events: none;
}
</style>
