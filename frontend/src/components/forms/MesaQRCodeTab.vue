<template>
  <div class="qrcode-tab-container q-pa-md">
    <!-- Aviso se não houver slug -->
    <q-banner v-if="!mesaSlug" class="bg-warning text-white" rounded>
      <template v-slot:avatar>
        <q-icon name="warning" />
      </template>
      Preencha o código da mesa (slug) para gerar o QR Code
    </q-banner>

    <div v-else class="row q-col-gutter-md">
      <!-- Card do QR Code -->
      <div class="col-12 col-md-6">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-h6 q-mb-md">
              <q-icon name="qr_code_2" class="q-mr-sm" />
              QR Code da Mesa
            </div>

            <!-- Preview do QR Code -->
            <div class="qrcode-preview-container">
              <canvas
                ref="qrcodeCanvas"
                class="qrcode-canvas"
              ></canvas>
            </div>

            <!-- Informações -->
            <div class="q-mt-md">
              <q-chip color="primary" text-color="white" icon="link">
                {{ mesaUrl }}
              </q-chip>
            </div>

            <div class="q-mt-sm text-caption text-grey-7">
              Clientes podem escanear este QR Code para acessar a mesa
            </div>
          </q-card-section>

          <q-separator />

          <q-card-actions align="center">
            <q-btn
              unelevated
              color="primary"
              icon="download"
              label="Baixar PNG"
              @click="downloadQRCode('png')"
            />
            <q-btn
              unelevated
              color="secondary"
              icon="picture_as_pdf"
              label="Baixar PDF"
              @click="downloadQRCode('pdf')"
            />
          </q-card-actions>
        </q-card>
      </div>

      <!-- Card de Instruções -->
      <div class="col-12 col-md-6">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-h6 q-mb-md">
              <q-icon name="info" class="q-mr-sm" />
              Como usar
            </div>

            <q-list>
              <q-item>
                <q-item-section avatar>
                  <q-avatar color="primary" text-color="white" icon="looks_one" />
                </q-item-section>
                <q-item-section>
                  <q-item-label>Baixe o QR Code</q-item-label>
                  <q-item-label caption>Escolha entre PNG ou PDF</q-item-label>
                </q-item-section>
              </q-item>

              <q-item>
                <q-item-section avatar>
                  <q-avatar color="primary" text-color="white" icon="looks_two" />
                </q-item-section>
                <q-item-section>
                  <q-item-label>Imprima o QR Code</q-item-label>
                  <q-item-label caption>Use papel de qualidade para durabilidade</q-item-label>
                </q-item-section>
              </q-item>

              <q-item>
                <q-item-section avatar>
                  <q-avatar color="primary" text-color="white" icon="looks_3" />
                </q-item-section>
                <q-item-section>
                  <q-item-label>Fixe na mesa</q-item-label>
                  <q-item-label caption>Cole ou plastifique para proteção</q-item-label>
                </q-item-section>
              </q-item>

              <q-item>
                <q-item-section avatar>
                  <q-avatar color="primary" text-color="white" icon="looks_4" />
                </q-item-section>
                <q-item-section>
                  <q-item-label>Clientes escaneiam</q-item-label>
                  <q-item-label caption>Acesso direto ao cardápio da mesa</q-item-label>
                </q-item-section>
              </q-item>
            </q-list>
          </q-card-section>
        </q-card>

        <!-- Card de Preview para Impressão -->
        <q-card flat bordered class="q-mt-md">
          <q-card-section>
            <div class="text-h6 q-mb-md">
              <q-icon name="print" class="q-mr-sm" />
              Tamanho de Impressão
            </div>

            <q-select
              v-model="printSize"
              :options="printSizeOptions"
              label="Tamanho"
              outlined
              dense
              emit-value
              map-options
            />

            <div class="q-mt-sm text-caption text-grey-7">
              {{ printSizeDescription }}
            </div>
          </q-card-section>
        </q-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue';
import { useQuasar } from 'quasar';
import QRCode from 'qrcode';
import jsPDF from 'jspdf';
import { useApiRequest } from '@/composables/useApiRequest';

const props = defineProps({
  modelValue: {
    type: Object,
    required: true
  },
  recordId: {
    type: Number,
    default: null
  }
});

const emit = defineEmits(['update:modelValue']);

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const qrcodeCanvas = ref(null);
const printSize = ref('medium');
const ecommerceBaseUrl = ref('http://localhost:5173'); // fallback

const printSizeOptions = [
  { label: 'Pequeno (5x5 cm)', value: 'small' },
  { label: 'Médio (8x8 cm)', value: 'medium' },
  { label: 'Grande (12x12 cm)', value: 'large' },
  { label: 'Extra Grande (15x15 cm)', value: 'xlarge' }
];

// Computed
const mesaSlug = computed(() => props.modelValue?.slug || '');
const mesaRotulo = computed(() => props.modelValue?.rotulo || '');

const mesaUrl = computed(() => {
  if (!mesaSlug.value) return '';
  return `${ecommerceBaseUrl.value}/m/${mesaSlug.value}`;
});

const printSizeDescription = computed(() => {
  const sizes = {
    small: 'Ideal para mesas pequenas ou espaços limitados',
    medium: 'Tamanho padrão recomendado para a maioria das mesas',
    large: 'Para maior visibilidade e facilidade de escaneamento',
    xlarge: 'Para ambientes amplos ou mesas distantes'
  };
  return sizes[printSize.value];
});

const qrCodeSize = computed(() => {
  const sizes = {
    small: 200,
    medium: 300,
    large: 450,
    xlarge: 600
  };
  return sizes[printSize.value];
});

// Methods
async function loadConfig() {
  try {
    const response = await apiRequest('/api/public/config');
    if (response?.ecommerceBaseUrl) {
      ecommerceBaseUrl.value = response.ecommerceBaseUrl;
    }
  } catch (error) {
    console.error('Erro ao carregar configuração:', error);
    // Mantém fallback padrão
  }
}

async function generateQRCode() {
  if (!mesaSlug.value || !qrcodeCanvas.value) return;

  try {
    await QRCode.toCanvas(qrcodeCanvas.value, mesaUrl.value, {
      width: qrCodeSize.value,
      margin: 2,
      color: {
        dark: '#000000',
        light: '#FFFFFF'
      }
    });
  } catch (error) {
    console.error('Erro ao gerar QR Code:', error);
    $q.notify({
      type: 'negative',
      message: 'Erro ao gerar QR Code'
    });
  }
}

async function downloadQRCode(format) {
  if (!mesaSlug.value) return;

  try {
    if (format === 'png') {
      await downloadPNG();
    } else if (format === 'pdf') {
      await downloadPDF();
    }

    $q.notify({
      type: 'positive',
      message: `QR Code baixado com sucesso!`,
      icon: 'download'
    });
  } catch (error) {
    console.error('Erro ao baixar QR Code:', error);
    $q.notify({
      type: 'negative',
      message: 'Erro ao baixar QR Code'
    });
  }
}

async function downloadPNG() {
  const canvas = qrcodeCanvas.value;
  const dataUrl = canvas.toDataURL('image/png');
  const link = document.createElement('a');
  link.download = `qrcode-mesa-${mesaSlug.value}.png`;
  link.href = dataUrl;
  link.click();
}

async function downloadPDF() {
  const canvas = qrcodeCanvas.value;
  const imgData = canvas.toDataURL('image/png');

  // Calcular tamanho em mm baseado no printSize
  const sizes = {
    small: 50,
    medium: 80,
    large: 120,
    xlarge: 150
  };
  const size = sizes[printSize.value];

  // Criar PDF A4 (210 x 297 mm)
  const pdf = new jsPDF({
    orientation: 'portrait',
    unit: 'mm',
    format: 'a4'
  });

  // Centralizar QR Code na página
  const pageWidth = 210;
  const pageHeight = 297;
  const x = (pageWidth - size) / 2;
  const y = 40; // Margem superior

  // Adicionar título
  pdf.setFontSize(16);
  pdf.text(`Mesa: ${mesaRotulo.value || mesaSlug.value}`, pageWidth / 2, 20, { align: 'center' });

  // Adicionar QR Code
  pdf.addImage(imgData, 'PNG', x, y, size, size);

  // Adicionar URL abaixo do QR Code
  pdf.setFontSize(10);
  pdf.text(mesaUrl.value, pageWidth / 2, y + size + 10, { align: 'center' });

  // Adicionar instruções
  pdf.setFontSize(8);
  pdf.text('Escaneie este QR Code para acessar o cardápio digital', pageWidth / 2, y + size + 20, { align: 'center' });

  // Salvar PDF
  pdf.save(`qrcode-mesa-${mesaSlug.value}.pdf`);
}

// Watchers
watch([mesaSlug, qrCodeSize], async () => {
  await nextTick();
  await generateQRCode();
}, { immediate: true });

// Gerar QR Code quando o canvas estiver disponível
watch(qrcodeCanvas, async (newCanvas) => {
  if (newCanvas) {
    await generateQRCode();
  }
});

// Regenerar QR Code quando a URL base mudar
watch(ecommerceBaseUrl, async () => {
  await nextTick();
  await generateQRCode();
});

// Carregar configuração ao montar
onMounted(async () => {
  await loadConfig();
});
</script>

<style scoped>
.qrcode-tab-container {
  max-width: 1200px;
  margin: 0 auto;
}

.qrcode-preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f5f5f5;
  border-radius: 8px;
  padding: 20px;
  min-height: 320px;
}

.qrcode-canvas {
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  background: white;
}
</style>
