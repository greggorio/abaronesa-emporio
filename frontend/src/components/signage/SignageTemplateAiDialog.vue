<template>
  <q-dialog v-model="internalModelValue" persistent>
    <q-card class="signage-ai-dialog">
      <q-card-section>
        <div class="row items-center justify-between">
          <div class="text-h6">Imagem IA — {{ templateName }}</div>
          <q-btn dense flat icon="close" @click="internalModelValue = false" />
        </div>
        <div class="q-mt-md">
          <q-toggle
            v-model="useAiImage"
            label="Original / IA"
            dense
            color="primary"
            @update:model-value="handleToggleUseAiImage"
          />
        </div>
        <div class="status-row q-mt-sm">
          <q-chip
            dense
            :color="statusChipColor"
            text-color="white"
            :label="statusLabel"
            outline
          />
          <q-chip dense :label="`Template: ${templateName}`" />
        </div>
        <q-card flat bordered class="thumbnail-card q-mt-md">
          <q-img
            class="ai-preview"
            :src="resolvedAiImageUrl || resolvedFallbackImageUrl"
            :img-style="{ objectFit: 'contain', objectPosition: 'center' }"
            ratio="1"
          />
        </q-card>
      </q-card-section>

      <q-card-actions align="right" class="q-pt-none">
        <q-btn
          label="Gerar imagem IA"
          color="primary"
          unelevated
          :loading="aiGenerating"
          :disable="aiGenerating"
          @click="handleGenerateImage"
        />
        <q-btn
          label="Regerar"
          flat
          color="primary"
          :disable="aiGenerating"
          @click="handleRegenerateImage"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import { baseApiUrl } from "@/global";
import {
  generateAiImage,
  toggleUseAiImage,
} from "@/services/signageAiService";

const props = defineProps({
  modelValue: {
    type: Boolean,
    required: true,
  },
  productId: {
    type: Number,
    required: true,
  },
  templateId: {
    type: String,
    required: true,
  },
  templateName: {
    type: String,
    required: true,
  },
  currentImageUrl: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["update:modelValue", "refresh"]);
const { apiRequest } = useApiRequest();
const $q = useQuasar();

const internalModelValue = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value),
});

const aiGenerating = ref(false);
const aiStatus = ref("idle");
const aiImageUrl = ref(props.currentImageUrl);
const aiImageHash = ref(null);
const useAiImage = ref(false);

const resetState = () => {
  aiGenerating.value = false;
  aiStatus.value = "idle";
  aiImageHash.value = null;
  aiImageUrl.value = props.currentImageUrl;
  useAiImage.value = false;
};

watch(
  () => props.modelValue,
  (isOpen) => {
    if (isOpen) {
      resetState();
      loadAiState();
    }
  }
);

watch(
  () => props.currentImageUrl,
  (value) => {
    if (!aiImageHash.value) {
      aiImageUrl.value = value || "";
    }
  }
);

const statusLabel = computed(() => {
  if (aiStatus.value === "error") return "Erro";
  if (aiStatus.value === "cached") return "Em cache";
  if (aiStatus.value === "generated") return "Gerada agora";
  return "Não gerada";
});

const statusChipColor = computed(() => {
  switch (aiStatus.value) {
    case "cached":
    case "generated":
      return "primary";
    case "error":
      return "negative";
    default:
      return "grey-7";
  }
});

const resolveImageUrl = (url) => {
  if (!url) return "";
  let candidate = url;
  if (typeof candidate === "object" && candidate !== null) {
    candidate =
      candidate.url ||
      candidate.path ||
      candidate.src ||
      candidate.imagemPrincipal ||
      "";
  }
  candidate = String(candidate || "").trim();
  if (!candidate) return "";
  if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
    return candidate;
  }
  const clean = candidate.startsWith("/") ? candidate.substring(1) : candidate;
  return `${baseApiUrl}/${clean}`;
};

const resolvedAiImageUrl = computed(() => resolveImageUrl(aiImageUrl.value));
const resolvedFallbackImageUrl = computed(() => resolveImageUrl(props.currentImageUrl));

const loadAiState = async () => {
  try {
    const preview = await apiRequest(`/api/produtos/${props.productId}/signage/preview`);
    const matchesTemplate = preview?.templatePreference === props.templateId;
    useAiImage.value = matchesTemplate ? Boolean(preview?.isUsingAiImage) : false;
    aiImageHash.value = matchesTemplate ? preview?.aiImageHash || null : null;
    if (matchesTemplate && preview?.aiImageUrl) {
      aiImageUrl.value = preview.aiImageUrl;
      aiStatus.value = preview?.aiImageAvailable ? "cached" : "idle";
    } else if (matchesTemplate && preview?.aiImageAvailable && preview?.imageUrl) {
      aiImageUrl.value = preview.imageUrl;
      aiStatus.value = "cached";
    } else {
      aiImageUrl.value = props.currentImageUrl;
      aiStatus.value = "idle";
    }
  } catch (error) {
    aiStatus.value = "error";
  }
};

const ensureTemplatePreference = async () => {
  await apiRequest(`/api/produtos/${props.productId}/signage`, "PATCH", {
    templatePreference: props.templateId,
  });
};

const updateUiAfterGenerate = (response, force) => {
  aiImageHash.value = response.assetHash;
  aiImageUrl.value = response.assetUrl || aiImageUrl.value;
  aiStatus.value = response.cached ? "cached" : "generated";
  const message = response.cached ? "Imagem em cache" : "Imagem gerada";
  $q.notify({
    type: response.cached ? "info" : "positive",
    message,
  });
  if (force) {
    // increment a logical revision indicator if needed in UI
    aiStatus.value = "generated";
  }
  emit("refresh");
};

const handleGenerateImage = async () => {
  if (aiGenerating.value) return;
  aiGenerating.value = true;
  try {
    await ensureTemplatePreference();
    const response = await generateAiImage(props.productId, false);
    updateUiAfterGenerate(response, false);
  } catch (error) {
    aiStatus.value = "error";
    $q.notify({
      type: "negative",
      message: "Falha ao gerar imagem IA",
    });
  } finally {
    aiGenerating.value = false;
  }
};

const handleRegenerateImage = async () => {
  if (aiGenerating.value) return;
  let confirmed = false;
  await $q
    .dialog({
      title: "Regerar imagem IA",
      message:
        "Isso irá gerar uma nova imagem e pode incorrer em custos. Deseja continuar?",
      cancel: true,
      persistent: true,
    })
    .onOk(() => {
      confirmed = true;
    })
    .onCancel(() => {
      confirmed = false;
    })
    .onDismiss(() => {
      confirmed = false;
    });

  if (!confirmed) {
    return;
  }

  aiGenerating.value = true;
  try {
    await ensureTemplatePreference();
    const response = await generateAiImage(props.productId, true);
    updateUiAfterGenerate(response, true);
  } catch (error) {
    aiStatus.value = "error";
    $q.notify({
      type: "negative",
      message: "Falha ao gerar imagem IA",
    });
  } finally {
    aiGenerating.value = false;
  }
};

const handleToggleUseAiImage = async (value) => {
  const previous = useAiImage.value;
  useAiImage.value = value;
  try {
    if (value) {
      await ensureTemplatePreference();
    }
    await toggleUseAiImage(props.productId, value);
    emit("refresh");
  } catch (error) {
    useAiImage.value = previous;
    $q.notify({
      type: "negative",
      message: "Falha ao alternar uso da imagem IA",
    });
  }
};
</script>

<style scoped>
.signage-ai-dialog {
  min-width: 520px;
}

.status-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.thumbnail-card {
  border-radius: 10px;
  overflow: hidden;
  min-height: 280px;
  background: #f8fafc;
}

.ai-preview {
  background-color: #f8fafc;
  background-image:
    linear-gradient(45deg, rgba(148, 163, 184, 0.25) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(148, 163, 184, 0.25) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(148, 163, 184, 0.25) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(148, 163, 184, 0.25) 75%);
  background-size: 18px 18px;
  background-position: 0 0, 0 9px, 9px -9px, -9px 0px;
}
</style>
