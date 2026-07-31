<template>
  <div class="ai-feedback">
    <!-- Status de processamento -->
    <transition name="slide-fade">
      <div v-if="showProcessing" class="processing-status">
        <q-card flat bordered class="processing-card">
          <q-card-section class="q-py-sm">
            <div class="row items-center no-wrap">
              <q-spinner-dots color="primary" size="30px" />
              <span class="q-ml-md text-body2">{{ processingMessage }}</span>
            </div>
          </q-card-section>
        </q-card>
      </div>
    </transition>

    <!-- Feedback de sucesso inline -->
    <transition name="bounce">
      <div v-if="showSuccess" class="success-feedback">
        <q-icon name="check_circle" color="positive" size="24px" />
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useAIAssistant } from "@/composables/useAIAssistant";

// Props
const props = defineProps({
  processing: {
    type: Boolean,
    default: false,
  },
  message: {
    type: String,
    default: "Processando comando...",
  },
});

// Composables
const { isProcessing } = useAIAssistant();

// Estado local
const showSuccess = ref(false);
const successTimeout = ref(null);

// Computed
const showProcessing = computed(() => props.processing || isProcessing.value);
const processingMessage = computed(() => props.message);

// Watchers
watch(showProcessing, (newVal, oldVal) => {
  // Quando para de processar, mostrar sucesso brevemente
  if (oldVal && !newVal) {
    showSuccessFeedback();
  }
});

// Métodos
const showSuccessFeedback = () => {
  showSuccess.value = true;

  clearTimeout(successTimeout.value);
  successTimeout.value = setTimeout(() => {
    showSuccess.value = false;
  }, 2000);
};

// Expor métodos se necessário
defineExpose({
  showSuccessFeedback,
});
</script>

<style scoped>
.ai-feedback {
  position: relative;
}

.processing-status {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 2000;
}

.processing-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  min-width: 250px;
}

.success-feedback {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 2000;
  background: white;
  border-radius: 50%;
  padding: 12px;
  box-shadow: 0 4px 20px rgba(76, 175, 80, 0.3);
}

/* Animações */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s ease;
}

.slide-fade-enter-from {
  transform: translateY(20px);
  opacity: 0;
}

.slide-fade-leave-to {
  transform: translateY(-20px);
  opacity: 0;
}

.bounce-enter-active {
  animation: bounce-in 0.5s;
}

.bounce-leave-active {
  animation: bounce-out 0.3s;
}

@keyframes bounce-in {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes bounce-out {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(0);
    opacity: 0;
  }
}
</style>
