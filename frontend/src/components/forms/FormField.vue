<template>
  <div :class="fieldClass">
    <!-- Upload de Arquivo com Preview -->
    <div v-if="field.component === 'QFile'" class="form-file-upload">
      <q-field :label="field.label || 'Imagem de Capa'" :error="!!error" :error-message="error" filled dense bg-color="grey-2" stack-label>
        <template v-slot:control>
          <div class="self-center full-width no-outline q-pt-xs" tabindex="0">
            <div class="row items-center">
              <div class="col">
                <div v-if="fileName || hasExistingFile" class="text-caption text-grey-8">
                  <q-icon name="insert_drive_file" size="xs" class="q-mr-xs" />
                  {{ fileName || existingFileName || "Arquivo atual" }}
                </div>
                <div v-else class="text-grey-6 text-caption">
                  {{ field.placeholder || "Clique para selecionar um arquivo" }}
                </div>
              </div>

              <q-btn v-if="fileName || hasExistingFile" flat dense round icon="close" size="sm" @click.stop="handleRemoveFile" />

              <q-btn
                flat
                dense
                round
                :icon="isUploading ? 'hourglass_empty' : 'attach_file'"
                size="sm"
                :loading="isUploading"
                @click="triggerFileInput"
              />
            </div>
          </div>
        </template>
      </q-field>

      <!-- Input oculto -->
      <input ref="fileInputRef" type="file" hidden :accept="field.accept || 'image/*'" :multiple="field.multiple" @change="handleFileChange" />

      <!-- Preview de Imagem -->
      <div v-if="imagePreview" class="q-mt-sm">
        <div class="relative-position">
          <img
            :src="imagePreview"
            class="rounded-borders"
            :style="{
              width: '100%',
              maxHeight: previewStyle.maxHeight || '200px',
              objectFit: 'contain',
              display: 'block',
            }"
            @error="handleImageError"
          />

          <!-- Overlay com ações -->
          <div v-if="field.showPreviewActions" class="absolute-bottom text-center q-pa-sm bg-transparent">
            <q-btn round dense color="white" text-color="black" icon="visibility" size="sm" @click="showFullImage" />
            <q-btn
              v-if="!field.readOnly"
              round
              dense
              color="white"
              text-color="black"
              icon="delete"
              size="sm"
              class="q-ml-sm"
              @click="handleRemoveFile"
            />
          </div>
        </div>
      </div>

      <!-- Gallery Preview para múltiplos arquivos -->
      <div v-if="field.multiple && galleryImages.length > 0" class="q-mt-sm">
        <div class="row q-gutter-sm">
          <div v-for="(image, index) in galleryImages" :key="index" class="col-auto">
            <q-img :src="image.preview" :ratio="1" class="rounded-borders" style="width: 100px; height: 100px">
              <div class="absolute-full text-center q-pa-sm">
                <q-btn round dense color="white" text-color="negative" icon="close" size="xs" @click="removeGalleryImage(index)" />
              </div>
            </q-img>
          </div>
        </div>
      </div>
    </div>
    <!-- Lookup modal (Fornecedor, Produto, etc.) -->
    <LookupSelect
      v-else-if="field.component === 'LookupSelect' || field.component === 'LOOKUP'"
      :field="field"
      :model-value="modelValue"
      :error="error"
      :disabled="disabled"
      @update:model-value="updateValue"
    />

    <!-- Tabela dinâmica de itens -->
    <TableField
      v-else-if="field.component === 'TableField' || field.component === 'ChildTable' || field.component === 'CHILD_TABLE'"
      :field="field"
      :model-value="modelValue"
      :error="error"
      :disabled="disabled"
      @update:model-value="updateValue"
    />

    <!-- Campo calculado/aggregate -->
    <ComputedField
      v-else-if="field.component === 'ComputedField' || field.component === 'COMPUTED'"
      :field="field"
      :model-value="modelValue"
      :error="error"
      :disabled="disabled"
    />
    <!-- Checkbox -->
    <q-checkbox
      v-else-if="field.component === 'q-checkbox'"
      :model-value="modelValue"
      :label="field.label"
      :disable="disabled || field.readOnly"
      @update:model-value="updateValue"
    />

    <!-- Select -->
    <q-select
      v-else-if="field.component === 'q-select'"
      :model-value="modelValue"
      :label="field.label"
      :placeholder="field.placeholder"
      :options="computedOptions"
      :loading="loading"
      :disable="disabled || field.readOnly"
      :required="field.required"
      :rules="computedRules"
      :error="!!error"
      :error-message="error"
      dense
      outlined
      emit-value
      map-options
      v-bind="field.props"
      @update:model-value="updateValue"
    />

    <!-- Radio -->
    <div v-else-if="field.component === 'q-radio-group'">
      <div class="text-subtitle2 q-mb-xs">{{ field.label }}</div>
      <q-option-group
        :model-value="modelValue"
        :options="computedOptions"
        type="radio"
        :disable="disabled || field.readOnly"
        @update:model-value="updateValue"
      />
    </div>

    <!-- Toggle -->
    <q-toggle
      v-else-if="field.component === 'q-toggle'"
      :model-value="modelValue"
      :label="field.label"
      :disable="disabled || field.readOnly"
      @update:model-value="updateValue"
    />

    <!-- Date/Time - Usando DateInput para datas simples -->
    <DateInput
      v-else-if="field.component === 'q-date'"
      :model-value="modelValue"
      :label="field.label"
      :disable="disabled || field.readOnly"
      :rules="computedRules"
      @update:model-value="updateValue"
    />

    <!-- DateTime - Usando DateTimeInput para data/hora -->
    <DateTimeInput
      v-else-if="field.component === 'q-datetime'"
      :model-value="modelValue"
      :label="field.label"
      :disable="disabled || field.readOnly"
      :rules="computedRules"
      @update:model-value="updateValue"
    />

    <!-- Time padrão apenas para campos de hora -->
    <q-input
      v-else-if="field.component === 'q-time'"
      :model-value="modelValue"
      :label="field.label"
      :placeholder="field.placeholder"
      :type="getDateType(field.component)"
      :required="field.required"
      :rules="computedRules"
      :disable="disabled || field.readOnly"
      :error="!!error"
      :error-message="error"
      dense
      outlined
      v-bind="field.props"
      @update:model-value="updateValue"
    >
      <template v-slot:append>
        <q-icon :name="getDateIcon(field.component)" class="cursor-pointer">
          <q-popup-proxy cover transition-show="scale" transition-hide="scale">
            <component :is="field.component" :model-value="modelValue" @update:model-value="updateValue" v-bind="field.dateProps" />
          </q-popup-proxy>
        </q-icon>
      </template>
    </q-input>

    <!-- Separator -->
    <q-separator v-else-if="field.component === 'q-separator'" v-bind="field.props" />

    <!-- Button -->
    <q-btn v-else-if="field.component?.toLowerCase() === 'q-btn'" v-bind="field.props" @click="handleButtonClick" />

    <!-- Input padrão e outros componentes -->
    <component
      v-else
      :is="field.component || 'q-input'"
      :model-value="modelValue"
      :label="field.label"
      :placeholder="field.placeholder"
      :type="field.type || 'text'"
      :required="field.required"
      :rules="computedRules"
      :loading="loading"
      :disable="disabled || field.readOnly"
      :error="!!error"
      :error-message="error"
      dense
      outlined
      v-bind="field.props"
      @update:model-value="updateValue"
    />

    <!-- Dialog para visualização de imagem completa -->
    <q-dialog v-model="showImageDialog">
      <q-card>
        <q-img :src="imagePreview" :style="{ maxWidth: '90vw', maxHeight: '90vh' }" />
        <q-card-actions align="right">
          <q-btn flat label="Fechar" color="primary" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, inject, nextTick } from "vue";
import { useQuasar } from "quasar";
import { baseApiUrl } from "@/global";
import { useFileUpload } from "@/composables/useFileUpload";
import LookupSelect from "./LookupSelect.vue";
import TableField from "./TableField.vue";
import ComputedField from "./ComputedField.vue";
import DateInput from "./date/DateInput.vue";
import DateTimeInput from "./date/DateTimeInput.vue";

// Props
const props = defineProps({
  field: {
    type: Object,
    required: true,
  },
  modelValue: {
    default: null,
  },
  error: {
    type: String,
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  options: {
    type: Array,
    default: () => [],
  },
  recordId: {
    type: [String, Number],
    default: null,
  },
});

// Emits
const emit = defineEmits(["update:model-value", "field-event"]);

// Composables
const $q = useQuasar();
const formContext = inject("formContext", null);
const {
  handleFileChange: handleFileChangeComposable,
  uploadFile,
  removeFile: removeFileComposable,
  uploadedFiles,
  uploadedFileNames,
} = useFileUpload();

// Refs
const fileInputRef = ref(null);
const showImageDialog = ref(false);
const uploadedFile = ref(null);
const galleryFiles = ref([]);
const isUploading = ref(false);

// Computed
const fieldClass = computed(() => {
  return props.field.cols || "col-12";
});

const computedOptions = computed(() => {
  return props.options?.length > 0 ? props.options : props.field.options || [];
});

const computedRules = computed(() => {
  if (!props.field.validations) return [];

  return props.field.validations.map((validation) => {
    if (typeof validation === "function") return validation;

    if (validation === "required") {
      return (val) => (val !== null && val !== undefined && val !== "") || "Campo obrigatório";
    }

    if (validation === "email") {
      return (val) => /^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$/.test(val) || "E-mail inválido";
    }

    if (validation === "phone") {
      return (val) => /^\(\d{2}\) \d{5}-\d{4}$/.test(val) || "Telefone inválido";
    }

    // Adicionar outras validações conforme necessário
    return () => true;
  });
});

const hasExistingFile = computed(() => {
  return !!props.modelValue && !uploadedFile.value;
});

const fileName = computed(() => {
  // Primeiro verifica no composable
  if (uploadedFileNames.value[props.field.name]) {
    return uploadedFileNames.value[props.field.name];
  }
  // Depois verifica arquivo local
  if (uploadedFile.value) {
    return uploadedFile.value.name;
  }
  return "";
});

const existingFileName = computed(() => {
  if (typeof props.modelValue === "string") {
    return props.modelValue.split("/").pop();
  }
  if (props.modelValue?.name) {
    return props.modelValue.name;
  }
  return "";
});

const imagePreview = computed(() => {
  // Se tem arquivo novo carregado localmente
  if (uploadedFile.value && uploadedFile.value.preview) {
    return uploadedFile.value.preview;
  }

  // Se tem valor no modelo (URL retornada do backend)
  if (props.modelValue) {
    // Se já é uma URL completa ou path
    if (typeof props.modelValue === "string") {
      return getImageUrl(props.modelValue);
    }
    // Se é um objeto com URL
    if (props.modelValue?.url) {
      return getImageUrl(props.modelValue.url);
    }
    // Se é um objeto com path
    if (props.modelValue?.path) {
      return getImageUrl(props.modelValue.path);
    }
  }

  return null;
});

const galleryImages = computed(() => {
  return galleryFiles.value.map((file) => ({
    file,
    preview: file.preview || getImageUrl(file),
  }));
});

const previewStyle = computed(() => {
  return {
    maxHeight: props.field.previewMaxHeight || "200px",
    width: props.field.previewWidth || "auto",
  };
});

// Computed para extrair o tipo de entidade do endpoint
const entityType = computed(() => {
  // Se foi definido explicitamente no field
  if (props.field.entityType) {
    return props.field.entityType;
  }

  // Tentar extrair do uploadEndpoint
  if (props.field.uploadEndpoint) {
    // Padrão: /api/entidade/{id}/upload
    const match = props.field.uploadEndpoint.match(/\/api\/([^\/]+)\//);
    if (match) {
      return match[1];
    }
  }

  // Fallback para categorias (mantém compatibilidade)
  return "categorias";
});

// Methods
function updateValue(value) {
  emit("update:model-value", value);
}

function getDateType(component) {
  switch (component) {
    case "q-date":
      return "date";
    case "q-time":
      return "time";
    case "q-datetime":
      return "datetime-local";
    default:
      return "text";
  }
}

function getDateIcon(component) {
  switch (component) {
    case "q-date":
      return "event";
    case "q-time":
      return "access_time";
    case "q-datetime":
      return "event";
    default:
      return "event";
  }
}

function getImageUrl(path) {
  if (!path) return "";

  // Se é objeto, extrair URL/path
  if (typeof path === "object" && path !== null) {
    if ("url" in path) {
      path = path.url;
    } else if ("path" in path) {
      path = path.path;
    } else {
      return "";
    }
  }

  if (typeof path !== "string") return "";

  // Se já é uma URL completa
  if (path.startsWith("http://") || path.startsWith("https://")) {
    return path;
  }

  // Se é um data URL (preview local)
  if (path.startsWith("data:")) {
    return path;
  }

  // SEMPRE usar baseApiUrl (porta 8080)
  // Se começa com /
  if (path.startsWith("/")) {
    const url = `${baseApiUrl}${path}`;
    return url;
  }

  // Se é apenas nome do arquivo, assumir que está em /media/
  const url = `${baseApiUrl}/media/${path}`;
  return url;
}

function triggerFileInput() {
  if (fileInputRef.value) {
    fileInputRef.value.click();
  }
}

async function handleFileChange(event) {
  const files = Array.from(event.target.files);
  if (!files.length) return;

  try {
    // Usar o composable para processar o arquivo
    handleFileChangeComposable(props.field.name, event, {
      maxSize: props.field.maxSize,
      allowedTypes: props.field.allowedTypes,
      allowedExtensions: props.field.allowedExtensions,
    });

    // Para upload imediato, processar localmente também
    if (props.field.immediateUpload && props.recordId) {
      if (props.field.multiple) {
        for (const file of files) {
          await processFile(file, true);
        }
      } else {
        await processFile(files[0]);
      }

      // Fazer upload imediato
      await handleImmediateUpload();
    }

    // Emitir evento
    emit("field-event", {
      type: "file-selected",
      field: props.field.name,
      data: props.field.multiple ? files : files[0],
    });
  } catch (error) {
    $q.notify({
      type: "negative",
      message: error.message,
    });
  }
}

function validateFile(file) {
  const { maxSize, allowedTypes, allowedExtensions } = props.field;

  // Validar tamanho
  if (maxSize && file.size > maxSize) {
    throw new Error(`Arquivo muito grande. Máximo: ${(maxSize / 1024 / 1024).toFixed(1)}MB`);
  }

  // Validar tipo
  if (allowedTypes?.length > 0) {
    const isValidType = allowedTypes.some((type) => {
      if (type.endsWith("/*")) {
        return file.type.startsWith(type.slice(0, -1));
      }
      return file.type === type;
    });

    if (!isValidType) {
      throw new Error(`Tipo de arquivo não permitido. Permitidos: ${allowedTypes.join(", ")}`);
    }
  }

  // Validar extensão
  if (allowedExtensions?.length > 0) {
    const ext = file.name.split(".").pop().toLowerCase();
    if (!allowedExtensions.includes(ext)) {
      throw new Error(`Extensão não permitida. Permitidas: ${allowedExtensions.join(", ")}`);
    }
  }
}

async function processFile(file, isMultiple = false) {
  // Criar preview se for imagem
  if (file.type.startsWith("image/")) {
    const reader = new FileReader();
    const preview = await new Promise((resolve) => {
      reader.onload = (e) => resolve(e.target.result);
      reader.readAsDataURL(file);
    });

    file.preview = preview;
  }

  if (isMultiple) {
    galleryFiles.value.push(file);
  } else {
    uploadedFile.value = file;
    updateValue(file);
  }
}

async function handleImmediateUpload() {
  if (!props.field.uploadEndpoint || !props.recordId) return;

  isUploading.value = true;

  try {
    const endpoint = props.field.uploadEndpoint.replace("{id}", props.recordId);
    const file = uploadedFiles.value[props.field.name] || uploadedFile.value;

    if (!file) {
      throw new Error("Nenhum arquivo para upload");
    }

    // Usar o uploadFile do composable
    const result = await uploadFile(endpoint, file, props.field.name, props.field.uploadOptions);

    console.log("Resultado do upload:", result);
    console.log("=== ANALISANDO RESPOSTA DO UPLOAD ===");
    console.log("Tipo do result:", typeof result);
    console.log("Result tem 'url'?", result?.url);
    console.log("Result tem 'data'?", result?.data);
    console.log("Result tem 'cover'?", result?.cover);
    console.log("Result tem campo [" + props.field.name + "]?", result?.[props.field.name]);

    // Processar resultado do upload
    let uploadedUrl = null;

    // Tentar extrair URL de diferentes formatos de resposta
    if (result) {
      // IMPORTANTE: Se o backend retorna success mas não a URL,
      // precisamos buscar a URL de outra forma
      if (result.success && !result.url && !result.data) {
        console.log("Backend retornou sucesso mas sem URL. Precisamos ajustar!");
        // Se temos o recordId e o nome do arquivo, podemos construir a URL
        if (props.recordId && file.name) {
          // CORREÇÃO: Usar o entityType extraído do endpoint
          uploadedUrl = `/media/${entityType.value}/${props.recordId}/${file.name}`;
          console.log("URL construída manualmente:", uploadedUrl);
          console.log("Entity type usado:", entityType.value);
        }
      } else if (typeof result === "string") {
        uploadedUrl = result;
      } else if (result.url) {
        uploadedUrl = result.url;
      } else if (result.data) {
        if (result.data.url) {
          uploadedUrl = result.data.url;
        } else if (result.data[props.field.name]) {
          uploadedUrl = result.data[props.field.name];
        } else if (result.data.cover) {
          uploadedUrl = result.data.cover;
        }
      } else if (result[props.field.name]) {
        uploadedUrl = result[props.field.name];
      } else if (result.cover) {
        uploadedUrl = result.cover;
      }
    }

    // Se encontrou URL, atualizar o campo
    if (uploadedUrl) {
      // Se a URL retornada for apenas o nome do arquivo, construir o path completo
      if (uploadedUrl && !uploadedUrl.startsWith("/") && !uploadedUrl.startsWith("http")) {
        uploadedUrl = `/media/${entityType.value}/${props.recordId}/${uploadedUrl}`;
      }

      // Limpar preview local já que agora temos a URL do servidor
      uploadedFile.value = null;

      // Atualizar o valor
      updateValue(uploadedUrl);

      // Forçar atualização do preview
      await nextTick();
    } else if (result?.success) {
      // ALTERNATIVA: Se o upload foi bem sucedido mas não retornou URL,
      // emitir evento para que o componente pai recarregue os dados
      console.log("Upload bem sucedido mas sem URL. Emitindo evento para recarregar dados.");
      emit("field-event", {
        type: "upload-success-needs-reload",
        field: props.field.name,
        recordId: props.recordId,
      });
    }

    // Emitir evento de upload completo
    console.log("=== FORMFIELD: EMITINDO FILE-UPLOADED ===");
    console.log("Campo:", props.field.name);
    console.log("URL processada:", uploadedUrl);
    console.log("Data completo:", result);
    console.log("Evento sendo emitido:", {
      type: "file-uploaded",
      field: props.field.name,
      data: result,
      url: uploadedUrl,
    });

    emit("field-event", {
      type: "file-uploaded",
      field: props.field.name,
      data: result,
      url: uploadedUrl,
    });
  } catch (error) {
    console.error("Erro no upload:", error);
    $q.notify({
      type: "negative",
      message: "Erro no upload: " + error.message,
    });
  } finally {
    isUploading.value = false;
  }
}

function handleRemoveFile() {
  if (hasExistingFile.value) {
    // Confirmar remoção de arquivo existente
    $q.dialog({
      title: "Remover arquivo",
      message: "Deseja remover o arquivo atual?",
      cancel: true,
      persistent: true,
    }).onOk(() => {
      removeFile();
    });
  } else {
    removeFile();
  }
}

function removeFile() {
  // Remover do composable
  removeFileComposable(props.field.name);

  // Remover localmente
  uploadedFile.value = null;
  galleryFiles.value = [];
  updateValue(null);

  if (fileInputRef.value) {
    fileInputRef.value.value = "";
  }

  emit("field-event", {
    type: "file-removed",
    field: props.field.name,
  });
}

function removeGalleryImage(index) {
  galleryFiles.value.splice(index, 1);
}

function showFullImage() {
  showImageDialog.value = true;
}

function handleImageError(event) {
  console.error("Erro ao carregar imagem:", event.target.src);
  // Podemos adicionar uma imagem de placeholder aqui se necessário
}

function handleButtonClick() {
  if (props.field.props?.onClick) {
    emit("field-event", {
      type: "custom-action",
      field: props.field.name,
      action: props.field.props.onClick,
    });
  }
}
</script>

<style scoped>
.form-file-upload {
  width: 100%;
}

.form-file-upload .q-field__control {
  padding-top: 20px;
}

.rounded-borders {
  border-radius: 4px;
}

.q-img {
  cursor: pointer;
}
</style>
