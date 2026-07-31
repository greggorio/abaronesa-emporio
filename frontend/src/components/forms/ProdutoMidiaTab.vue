<template>
  <div class="midia-tab-container q-pa-md">
    <!-- Aviso se não houver ID -->
    <q-banner v-if="!recordId" class="bg-warning text-white" rounded>
      <template v-slot:avatar>
        <q-icon name="warning" />
      </template>
      Salve o produto primeiro para adicionar imagens
    </q-banner>

    <div v-else class="row q-col-gutter-md">
      <!-- Coluna da imagem principal (esquerda) -->
      <div class="col-12 col-md-5">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-h6 q-mb-md">
              <q-icon name="image" class="q-mr-sm" />
              Imagem Principal
            </div>

            <!-- Preview da imagem principal -->
            <div class="imagem-principal-container">
              <q-img
                v-if="imagemPrincipal"
                :src="getImageUrl(imagemPrincipal)"
                :ratio="2/3"
                class="rounded-borders cursor-pointer"
                @click="abrirZoom(getImageUrl(imagemPrincipal))"
              >
                <template v-slot:error>
                  <div class="absolute-full flex flex-center bg-grey-3">
                    <q-icon name="broken_image" size="64px" color="grey-5" />
                  </div>
                </template>
                <div class="absolute-bottom-right q-pa-xs">
                  <q-btn
                    round
                    dense
                    icon="zoom_in"
                    color="white"
                    text-color="dark"
                    size="sm"
                  />
                </div>
              </q-img>

              <div v-else class="imagem-principal-placeholder">
                <q-icon name="add_photo_alternate" size="64px" color="grey-5" />
                <div class="text-grey-6 q-mt-sm">Nenhuma imagem principal</div>
              </div>
            </div>

            <!-- Botão de upload -->
            <q-btn
              unelevated
              color="primary"
              icon="upload"
              label="Selecionar Imagem"
              class="full-width q-mt-md"
              @click="$refs.inputImagemPrincipal.click()"
            />

            <input
              ref="inputImagemPrincipal"
              type="file"
              accept="image/*"
              style="display: none"
              @change="uploadImagemPrincipal"
            />
          </q-card-section>
        </q-card>
      </div>

      <!-- Coluna da galeria e vídeo (direita) -->
      <div class="col-12 col-md-7">
        <!-- Seção de Vídeo -->
        <q-card flat bordered class="q-mb-md">
          <q-card-section>
            <div class="text-h6 q-mb-md">
              <q-icon name="videocam" class="q-mr-sm" />
              Vídeo do Produto
            </div>

            <!-- Preview do vídeo -->
            <div v-if="videoProduto" class="video-container">
              <video
                :src="getImageUrl(videoProduto.url)"
                controls
                class="rounded-borders"
                style="width: 100%; max-height: 300px"
              >
                Seu navegador não suporta o elemento de vídeo.
              </video>
              <q-btn
                round
                dense
                icon="delete"
                color="negative"
                size="sm"
                class="absolute-top-right q-ma-xs"
                @click="removerVideo"
              />
            </div>

            <div v-else class="video-placeholder">
              <q-icon name="videocam_off" size="64px" color="grey-5" />
              <div class="text-grey-6 q-mt-sm">Nenhum vídeo</div>
            </div>

            <!-- Botão de upload -->
            <q-btn
              unelevated
              color="primary"
              icon="upload"
              label="Selecionar Vídeo"
              class="full-width q-mt-md"
              :disable="!!videoProduto"
              @click="$refs.inputVideo.click()"
            />

            <div class="text-caption text-grey-6 q-mt-sm">
              Máximo: 100MB | Duração máxima: 90 segundos | Formato: MP4
            </div>

            <input
              ref="inputVideo"
              type="file"
              accept="video/mp4,video/*"
              style="display: none"
              @change="uploadVideo"
            />
          </q-card-section>
        </q-card>

        <!-- Galeria de Imagens -->
        <q-card flat bordered>
          <q-card-section>
            <div class="text-h6 q-mb-md">
              <q-icon name="collections" class="q-mr-sm" />
              Galeria
              <q-chip v-if="galeriaImagens.length > 0" :label="`${galeriaImagens.length}/6`" :color="galeriaImagens.length >= 6 ? 'orange' : 'blue-grey'" text-color="white" size="sm" />
            </div>

            <!-- Grid da galeria -->
            <div v-if="galeriaImagens.length > 0" class="galeria-grid">
              <div v-for="imagem in galeriaImagens" :key="imagem.id" class="galeria-item">
                <q-card flat bordered class="galeria-card">
                  <q-img
                    :src="getImageUrl(imagem.url)"
                    :ratio="1"
                    class="cursor-pointer"
                    @click="abrirZoom(getImageUrl(imagem.url))"
                  >
                    <template v-slot:error>
                      <div class="absolute-full flex flex-center bg-grey-3">
                        <q-icon name="broken_image" size="32px" color="grey-5" />
                      </div>
                    </template>
                    <div class="absolute-full galeria-overlay">
                      <q-btn
                        round
                        dense
                        icon="zoom_in"
                        color="white"
                        text-color="dark"
                        size="sm"
                        class="q-ma-xs"
                      />
                      <q-btn
                        round
                        dense
                        icon="delete"
                        color="negative"
                        size="sm"
                        class="q-ma-xs"
                        @click.stop="removerImagemGaleria(imagem.id)"
                      />
                    </div>
                  </q-img>
                </q-card>
              </div>

              <!-- Botão adicionar mais (se < 6) -->
              <div v-if="galeriaImagens.length < 6" class="galeria-item">
                <q-card flat bordered class="galeria-card galeria-card-add" @click="$refs.inputGaleria.click()">
                  <div class="flex flex-center full-height cursor-pointer">
                    <div class="text-center">
                      <q-icon name="add_photo_alternate" size="48px" color="primary" />
                      <div class="text-caption text-grey-7 q-mt-sm">Adicionar</div>
                    </div>
                  </div>
                </q-card>
              </div>
            </div>

            <!-- Placeholder vazio -->
            <div v-else class="galeria-empty">
              <q-icon name="collections" size="64px" color="grey-5" />
              <div class="text-grey-6 q-mt-sm">Nenhuma imagem na galeria</div>
              <q-btn
                unelevated
                color="primary"
                icon="add"
                label="Adicionar Imagens"
                class="q-mt-md"
                @click="$refs.inputGaleria.click()"
              />
            </div>

            <input
              ref="inputGaleria"
              type="file"
              accept="image/*"
              style="display: none"
              @change="uploadImagemGaleria"
            />
          </q-card-section>
        </q-card>
      </div>
    </div>

    <!-- Dialog de zoom -->
    <q-dialog v-model="dialogZoom">
      <q-card style="min-width: 400px; max-width: 800px">
        <q-img :src="imagemZoom" />
        <q-card-actions align="right">
          <q-btn flat label="Fechar" color="primary" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { useQuasar } from 'quasar';
import { useApiRequest } from '@/composables/useApiRequest';
import { baseApiUrl } from '@/global';

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

const imagemPrincipal = ref(null);
const galeriaImagens = ref([]);
const videoProduto = ref(null);
const dialogZoom = ref(false);
const imagemZoom = ref(null);

// Watch para atualizar quando modelValue mudar
watch(() => props.modelValue, (newData) => {
  if (newData) {
    imagemPrincipal.value = newData.imagemPrincipal || null;

    // Separar mídias em galeria (imagens) e vídeo
    const midias = newData.midias || [];
    galeriaImagens.value = midias.filter(m => m.tipo === 'IMAGEM');
    const videos = midias.filter(m => m.tipo === 'VIDEO');
    videoProduto.value = videos.length > 0 ? videos[0] : null;
  }
}, { immediate: true, deep: true });

function getImageUrl(url) {
  if (!url) return "";

  // Se url é um objeto, extrair a propriedade url
  let urlString = url;
  if (typeof url === "object" && url !== null) {
    urlString = url.url || url.path || url.src || "";
  }

  // Converter para string se necessário
  urlString = String(urlString);

  if (!urlString) return "";

  // Se já é uma URL completa, retornar como está
  if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
    return urlString;
  }

  // Se é uma URL relativa, concatenar com baseApiUrl
  const cleanUrl = urlString.startsWith("/") ? urlString.substring(1) : urlString;
  const finalUrl = `${baseApiUrl}/${cleanUrl}`;
  return finalUrl;
}

async function uploadImagemPrincipal(event) {
  const file = event.target.files[0];
  if (!file) return;

  // Validar tamanho (5MB)
  if (file.size > 5 * 1024 * 1024) {
    $q.notify({
      type: 'negative',
      message: 'A imagem deve ter no máximo 5MB'
    });
    return;
  }

  const formData = new FormData();
  formData.append('file', file);

  try {
    $q.loading.show({ message: 'Enviando imagem...' });
    const response = await apiRequest(`/api/produtos/${props.recordId}/upload-imagem`, 'POST', formData);
    imagemPrincipal.value = response.url;

    // Atualizar via emit
    emit('update:modelValue', {
      ...props.modelValue,
      imagemPrincipal: response.url
    });

    $q.notify({
      type: 'positive',
      message: 'Imagem principal atualizada com sucesso'
    });
  } catch (error) {
    $q.notify({
      type: 'negative',
      message: error.message || 'Erro ao enviar imagem'
    });
  } finally {
    $q.loading.hide();
    event.target.value = '';
  }
}

async function uploadImagemGaleria(event) {
  const file = event.target.files[0];
  if (!file) return;

  // Validar espaço disponível
  if (galeriaImagens.value.length >= 6) {
    $q.notify({
      type: 'warning',
      message: 'Máximo de 6 imagens na galeria'
    });
    return;
  }

  // Validar tamanho (5MB)
  if (file.size > 5 * 1024 * 1024) {
    $q.notify({
      type: 'negative',
      message: 'A imagem deve ter no máximo 5MB'
    });
    return;
  }

  const formData = new FormData();
  formData.append('file', file);

  try {
    $q.loading.show({ message: 'Enviando imagem...' });
    const response = await apiRequest(`/api/produtos/${props.recordId}/galeria`, 'POST', formData);
    galeriaImagens.value.push(response);

    // Atualizar via emit
    emit('update:modelValue', {
      ...props.modelValue,
      midias: galeriaImagens.value
    });

    $q.notify({
      type: 'positive',
      message: 'Imagem adicionada à galeria'
    });
  } catch (error) {
    $q.notify({
      type: 'negative',
      message: error.message || 'Erro ao enviar imagem'
    });
  } finally {
    $q.loading.hide();
    event.target.value = '';
  }
}

async function removerImagemGaleria(imagemId) {
  $q.dialog({
    title: 'Confirmar exclusão',
    message: 'Deseja realmente remover esta imagem da galeria?',
    cancel: true,
    persistent: true
  }).onOk(async () => {
    try {
      $q.loading.show({ message: 'Removendo imagem...' });
      await apiRequest(`/api/produtos/${props.recordId}/galeria/${imagemId}`, 'DELETE');
      galeriaImagens.value = galeriaImagens.value.filter(img => img.id !== imagemId);

      // Atualizar via emit
      emit('update:modelValue', {
        ...props.modelValue,
        midias: galeriaImagens.value
      });

      $q.notify({
        type: 'positive',
        message: 'Imagem removida com sucesso'
      });
    } catch (error) {
      $q.notify({
        type: 'negative',
        message: error.message || 'Erro ao remover imagem'
      });
    } finally {
      $q.loading.hide();
    }
  });
}

function abrirZoom(url) {
  imagemZoom.value = url;
  dialogZoom.value = true;
}

async function uploadVideo(event) {
  const file = event.target.files[0];
  if (!file) return;

  // Validar tamanho (100MB)
  if (file.size > 100 * 1024 * 1024) {
    $q.notify({
      type: 'negative',
      message: 'O vídeo deve ter no máximo 100MB'
    });
    return;
  }

  // Validar tipo
  if (!file.type.startsWith('video/')) {
    $q.notify({
      type: 'negative',
      message: 'O arquivo deve ser um vídeo'
    });
    return;
  }

  const formData = new FormData();
  formData.append('file', file);

  try {
    $q.loading.show({ message: 'Processando vídeo... (isso pode levar alguns segundos)' });
    const response = await apiRequest(`/api/produtos/${props.recordId}/video`, 'POST', formData);
    videoProduto.value = response;

    // Atualizar via emit
    const midias = [...galeriaImagens.value, response];
    emit('update:modelValue', {
      ...props.modelValue,
      midias
    });

    $q.notify({
      type: 'positive',
      message: 'Vídeo enviado e processado com sucesso!'
    });
  } catch (error) {
    $q.notify({
      type: 'negative',
      message: error.message || 'Erro ao enviar vídeo'
    });
  } finally {
    $q.loading.hide();
    event.target.value = '';
  }
}

async function removerVideo() {
  if (!videoProduto.value) return;

  $q.dialog({
    title: 'Confirmar exclusão',
    message: 'Deseja realmente remover este vídeo?',
    cancel: true,
    persistent: true
  }).onOk(async () => {
    try {
      $q.loading.show({ message: 'Removendo vídeo...' });
      await apiRequest(`/api/produtos/${props.recordId}/galeria/${videoProduto.value.id}`, 'DELETE');
      videoProduto.value = null;

      // Atualizar via emit
      emit('update:modelValue', {
        ...props.modelValue,
        midias: galeriaImagens.value
      });

      $q.notify({
        type: 'positive',
        message: 'Vídeo removido com sucesso'
      });
    } catch (error) {
      $q.notify({
        type: 'negative',
        message: error.message || 'Erro ao remover vídeo'
      });
    } finally {
      $q.loading.hide();
    }
  });
}
</script>

<style scoped>
.midia-tab-container {
  min-height: 400px;
}

.imagem-principal-container {
  position: relative;
  width: 100%;
  aspect-ratio: 2/3;
  overflow: hidden;
  border-radius: 8px;
}

.imagem-principal-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #f5f5f5;
  border: 2px dashed #ccc;
  border-radius: 8px;
}

.galeria-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.galeria-item {
  position: relative;
}

.galeria-card {
  height: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  transition: transform 0.2s;
}

.galeria-card:hover {
  transform: scale(1.02);
}

.galeria-card-add {
  border: 2px dashed #ccc;
  background-color: #f5f5f5;
}

.galeria-card-add:hover {
  background-color: #e0e0e0;
  border-color: #999;
}

.galeria-overlay {
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.galeria-card:hover .galeria-overlay {
  opacity: 1;
}

.galeria-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  background-color: #f5f5f5;
  border: 2px dashed #ccc;
  border-radius: 8px;
}

.video-container {
  position: relative;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
}

.video-placeholder {
  width: 100%;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #f5f5f5;
  border: 2px dashed #ccc;
  border-radius: 8px;
}
</style>
