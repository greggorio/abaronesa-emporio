<template>
  <div class="ai-command-input">
    <q-input
      ref="commandInput"
      v-model="prompt"
      :loading="isProcessing"
      :disable="isProcessing"
      placeholder="Digite ou fale seu comando (ex: cadastrar cliente João...)"
      rounded
      dense
      standout="bg-info"
      class="ai-input"
      @keyup.enter="handleSubmit"
      @clear="clearInput"
    >
      <template v-slot:prepend>
        <q-icon :name="iconName" :color="iconColor" :class="{ 'cursor-pointer': !isProcessing }" @click="handleIconClick">
          <q-tooltip v-if="!isProcessing">
            {{ iconTooltip }}
          </q-tooltip>
        </q-icon>
      </template>

      <template v-slot:append>
        <!-- Botão de enviar (quando há texto) -->
        <q-btn v-if="prompt && !isProcessing" icon="send" flat round dense color="primary" @click="handleSubmit">
          <q-tooltip>Enviar comando</q-tooltip>
        </q-btn>

        <!-- Botão de microfone (quando não há texto) -->
        <q-btn
          v-else-if="!prompt && !isProcessing"
          :icon="isRecording ? 'stop' : 'mic'"
          flat
          round
          dense
          :color="isRecording ? 'negative' : 'primary'"
          :class="{ 'pulse-animation': isRecording }"
          @click="toggleVoiceRecording"
        >
          <q-tooltip>
            {{ isRecording ? "Parar gravação" : "Comando por voz" }}
          </q-tooltip>
        </q-btn>

        <!-- Spinner durante processamento -->
        <q-spinner v-if="isProcessing" color="primary" size="20px" />
      </template>
    </q-input>

    <!-- Indicador visual de gravação -->
    <transition name="fade">
      <div v-if="isRecording" class="recording-indicator q-mt-xs">
        <q-icon name="fiber_manual_record" color="negative" class="recording-dot" />
        <span class="text-caption q-ml-xs">Ouvindo... fale seu comando</span>
      </div>
    </transition>

    <!-- Sugestões de comando (opcional) -->
    <transition name="slide-down">
      <div v-if="showSuggestions && suggestions.length > 0" class="suggestions-container q-mt-sm">
        <div class="text-caption text-grey-7 q-mb-xs">Exemplos de comandos:</div>
        <q-chip
          v-for="(suggestion, index) in suggestions"
          :key="index"
          clickable
          color="grey-3"
          text-color="grey-8"
          size="sm"
          @click="useSuggestion(suggestion)"
        >
          {{ suggestion }}
        </q-chip>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useQuasar } from "quasar";
import { useAIAssistant } from "@/composables/useAIAssistant";
import { useVoiceRecognition } from "@/composables/useVoiceRecognition";

// Emits
const emit = defineEmits(["command-processed", "error"]);

// Composables
const $q = useQuasar();
const { processCommand, isProcessing } = useAIAssistant();
const { isRecording, isSupported: voiceSupported, startRecording, stopRecording, checkSupport: checkVoiceSupport } = useVoiceRecognition();

// Estado local
const prompt = ref("");
const showSuggestions = ref(false);
const commandInput = ref(null);

// Sugestões de comando
const suggestions = ref([
  "cadastrar cliente João Silva cpf 12345678900",
  "novo produto Pão Francês preço 0.50 categoria pães",
  "criar cliente Maria Santos email maria@email.com telefone 11999887766",
]);

// Computed
const iconName = computed(() => {
  if (isProcessing.value) return "hourglass_empty";
  if (isRecording.value) return "mic_off";
  if (prompt.value) return "clear";
  return "auto_awesome";
});

const iconColor = computed(() => {
  if (isProcessing.value) return "grey";
  if (isRecording.value) return "negative";
  return "primary";
});

const iconTooltip = computed(() => {
  if (isRecording.value) return "Parar gravação";
  if (prompt.value) return "Limpar";
  return "Assistente IA";
});

// Métodos
const handleSubmit = async () => {
  if (!prompt.value.trim() || isProcessing.value) return;

  showSuggestions.value = false;

  try {
    const response = await processCommand(prompt.value);

    if (response) {
      emit("command-processed", {
        command: prompt.value,
        response,
      });

      // Limpar input após sucesso
      prompt.value = "";
    }
  } catch (error) {
    console.error("Erro ao processar comando:", error);
    emit("error", error);
  }
};

const handleIconClick = () => {
  if (isProcessing.value) return;

  if (isRecording.value) {
    stopRecording();
  } else if (prompt.value) {
    clearInput();
  } else {
    // Toggle sugestões
    showSuggestions.value = !showSuggestions.value;
  }
};

const clearInput = () => {
  prompt.value = "";
  showSuggestions.value = false;
  commandInput.value?.focus();
};

const toggleVoiceRecording = async () => {
  if (isRecording.value) {
    stopRecording();
  } else {
    try {
      const result = await startRecording({
        onResult: (text) => {
          prompt.value = text;
          // Auto-enviar após reconhecimento
          setTimeout(() => {
            handleSubmit();
          }, 500);
        },
        onError: (error) => {
          console.error("Erro no reconhecimento de voz:", error);
        },
      });
    } catch (error) {
      console.error("Erro ao iniciar gravação:", error);
    }
  }
};

const useSuggestion = (suggestion) => {
  prompt.value = suggestion;
  showSuggestions.value = false;
  commandInput.value?.focus();
};

// Atalhos de teclado
const handleKeyboard = (event) => {
  // Ctrl/Cmd + K para focar no input
  if ((event.ctrlKey || event.metaKey) && event.key === "k") {
    event.preventDefault();
    commandInput.value?.focus();
  }

  // Escape para limpar
  if (event.key === "Escape" && prompt.value) {
    clearInput();
  }
};

// Lifecycle
onMounted(() => {
  checkVoiceSupport();
  document.addEventListener("keydown", handleKeyboard);

  // Focar automaticamente se estiver vazio
  if (!prompt.value) {
    commandInput.value?.focus();
  }
});

onUnmounted(() => {
  document.removeEventListener("keydown", handleKeyboard);
  if (isRecording.value) {
    stopRecording();
  }
});
</script>

<style scoped>
.ai-command-input {
  position: relative;
  width: 100%;
  max-width: 600px;
}

.ai-input {
  transition: all 0.3s ease;
}

.ai-input:focus-within {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.recording-indicator {
  display: flex;
  align-items: center;
  padding-left: 16px;
}

.recording-dot {
  animation: pulse 1.5s infinite;
}

.pulse-animation {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.1);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

.suggestions-container {
  background: var(--q-color-grey-1);
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
