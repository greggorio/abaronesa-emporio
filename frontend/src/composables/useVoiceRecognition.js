// src/composables/useVoiceRecognition.js
import { ref } from "vue";

export function useVoiceRecognition() {
  const isRecording = ref(false);
  const isSupported = ref(false);
  const recognition = ref(null);

  /**
   * Verifica se o navegador suporta reconhecimento de voz
   */
  const checkSupport = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

    if (SpeechRecognition) {
      isSupported.value = true;
      recognition.value = new SpeechRecognition();

      // Configurações padrão
      recognition.value.lang = "pt-BR";
      recognition.value.continuous = false;
      recognition.value.interimResults = false;
      recognition.value.maxAlternatives = 1;

      console.log("Reconhecimento de voz disponível");
    } else {
      isSupported.value = false;
      console.warn("Reconhecimento de voz não suportado neste navegador");
    }

    return isSupported.value;
  };

  /**
   * Inicia gravação de voz
   */
  const startRecording = async (options = {}) => {
    if (!isSupported.value || !recognition.value) {
      console.error("Reconhecimento de voz não disponível");
      return false;
    }

    if (isRecording.value) {
      console.warn("Já está gravando");
      return false;
    }

    return new Promise((resolve, reject) => {
      try {
        // Configurar callbacks
        recognition.value.onstart = () => {
          console.log("Reconhecimento de voz iniciado");
          isRecording.value = true;
        };

        recognition.value.onresult = (event) => {
          const transcript = event.results[0][0].transcript;
          console.log("Texto reconhecido:", transcript);

          if (options.onResult) {
            options.onResult(transcript);
          }

          resolve(transcript);
        };

        recognition.value.onerror = (event) => {
          console.error("Erro no reconhecimento de voz:", event.error);
          isRecording.value = false;

          if (options.onError) {
            options.onError(event.error);
          }

          reject(event.error);
        };

        recognition.value.onend = () => {
          console.log("Reconhecimento de voz finalizado");
          isRecording.value = false;
        };

        // Iniciar reconhecimento
        recognition.value.start();
      } catch (error) {
        console.error("Erro ao iniciar gravação:", error);
        isRecording.value = false;
        reject(error);
      }
    });
  };

  /**
   * Para gravação de voz
   */
  const stopRecording = () => {
    if (!recognition.value || !isRecording.value) {
      return;
    }

    try {
      recognition.value.stop();
      isRecording.value = false;
      console.log("Gravação interrompida");
    } catch (error) {
      console.error("Erro ao parar gravação:", error);
      isRecording.value = false;
    }
  };

  /**
   * Limpar recursos
   */
  const cleanup = () => {
    if (recognition.value && isRecording.value) {
      stopRecording();
    }
  };

  // Verificar suporte ao inicializar
  checkSupport();

  return {
    // Estado
    isRecording,
    isSupported,

    // Métodos
    startRecording,
    stopRecording,
    checkSupport,
    cleanup,
  };
}
