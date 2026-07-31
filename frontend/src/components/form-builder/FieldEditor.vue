<template>
  <q-card class="field-editor" flat>
    <q-card-section class="q-pa-md">
      <q-form @submit="saveField">
        <!-- Propriedades Básicas -->
        <div class="property-group">
          <div class="group-header">
            <q-icon name="tune" size="18px" />
            <span>CONFIGURAÇÃO BÁSICA</span>
          </div>

          <div class="group-content">
            <div class="field-row">
              <label class="field-label">Nome do Campo</label>
              <q-input v-model="localField.name" filled dense disable>
                <template v-slot:append>
                  <q-icon name="info">
                    <q-tooltip>O nome do campo não pode ser alterado</q-tooltip>
                  </q-icon>
                </template>
              </q-input>
            </div>

            <div class="field-row">
              <label class="field-label">Label Exibido</label>
              <q-input v-model="localField.label" filled dense />
            </div>

            <div class="field-row">
              <label class="field-label">Tipo do Campo</label>
              <q-select v-model="localField.type" :options="fieldTypes" filled dense emit-value map-options />
            </div>

            <div class="field-row">
              <label class="field-label">Largura da Coluna</label>
              <q-select v-model="localField.cols" :options="colOptions" filled dense />
            </div>

            <div class="field-row">
              <label class="field-label">Placeholder</label>
              <q-input v-model="localField.placeholder" filled dense />
            </div>

            <div class="field-row">
              <q-checkbox v-model="localField.required" label="Campo obrigatório" />
            </div>

            <div class="field-row">
              <q-checkbox v-model="localField.readOnly" label="Somente leitura" />
            </div>
          </div>
        </div>

        <!-- Propriedades Específicas por Tipo -->
        <template v-if="localField.type === 'NUMBER' || localField.type === 'CURRENCY'">
          <q-separator class="q-my-md" />
          <div class="property-group">
            <div class="group-header">
              <q-icon name="numbers" size="18px" />
              <span>PROPRIEDADES NUMÉRICAS</span>
            </div>

            <div class="group-content">
              <div class="row q-col-gutter-sm">
                <div class="col-6">
                  <div class="field-row">
                    <label class="field-label">Valor mínimo</label>
                    <q-input v-model.number="localField.props.min" type="number" filled dense />
                  </div>
                </div>
                <div class="col-6">
                  <div class="field-row">
                    <label class="field-label">Valor máximo</label>
                    <q-input v-model.number="localField.props.max" type="number" filled dense />
                  </div>
                </div>
              </div>

              <div class="field-row">
                <label class="field-label">Incremento</label>
                <q-input v-model.number="localField.props.step" type="number" filled dense />
              </div>

              <div v-if="localField.type === 'CURRENCY'" class="field-row">
                <label class="field-label">Prefixo</label>
                <q-input v-model="localField.props.prefix" filled dense />
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="localField.type === 'TEXT'">
          <q-separator class="q-my-md" />
          <div class="property-group">
            <div class="group-header">
              <q-icon name="text_fields" size="18px" />
              <span>PROPRIEDADES DE TEXTO</span>
            </div>

            <div class="group-content">
              <div class="field-row">
                <label class="field-label">Tamanho máximo</label>
                <q-input v-model.number="localField.props.maxlength" type="number" filled dense />
              </div>

              <div class="field-row">
                <label class="field-label">Máscara</label>
                <q-select v-model="localField.props.mask" :options="maskOptions" filled dense clearable emit-value map-options />
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="localField.type === 'TEXTAREA'">
          <q-separator class="q-my-md" />
          <div class="property-group">
            <div class="group-header">
              <q-icon name="notes" size="18px" />
              <span>PROPRIEDADES DE TEXTAREA</span>
            </div>

            <div class="group-content">
              <div class="field-row">
                <label class="field-label">Número de linhas</label>
                <q-input v-model.number="localField.props.rows" type="number" filled dense />
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="localField.type === 'SELECT'">
          <q-separator class="q-my-md" />
          <div class="property-group">
            <div class="group-header">
              <q-icon name="list" size="18px" />
              <span>PROPRIEDADES DE SELECT</span>
            </div>

            <div class="group-content">
              <div class="field-row">
                <q-checkbox v-model="localField.props['use-input']" label="Permitir busca" />
              </div>

              <div class="field-row">
                <q-checkbox v-model="localField.props.multiple" label="Múltipla seleção" />
              </div>

              <div class="field-row">
                <label class="field-label">Endpoint de opções</label>
                <q-input
                  v-model="localField.optionsEndpoint"
                  filled
                  dense
                  placeholder="/api/entidade/options"
                  hint="Endpoint para buscar opções remotamente (deixe vazio para opções manuais)"
                />
              </div>

              <div v-if="!localField.optionsEndpoint" class="field-row">
                <label class="field-label">Opções (uma por linha)</label>
                <q-input v-model="optionsText" type="textarea" filled dense rows="4" placeholder="valor1|Label 1&#10;valor2|Label 2" />
              </div>
            </div>
          </div>
        </template>

        <!-- Configurações para campo TABLE -->
        <template v-else-if="localField.type === 'TABLE'">
          <q-separator class="q-my-md" />
          <div class="property-group">
            <div class="group-header">
              <q-icon name="table_chart" size="18px" />
              <span>CONFIGURAÇÃO DA TABELA</span>
            </div>

            <div class="group-content">
              <div class="field-row">
                <q-checkbox v-model="localField.rowAddable" label="Permitir adicionar linhas" />
              </div>

              <div class="field-row">
                <q-checkbox v-model="localField.rowRemovable" label="Permitir remover linhas" />
              </div>

              <div class="field-row">
                <label class="field-label">Colunas da Tabela</label>
                <TableColumnEditor v-model="localField.columns" />
              </div>
            </div>
          </div>
        </template>

        <!-- Configurações para campo LOOKUP -->
        <template v-else-if="localField.type === 'LOOKUP'">
          <q-separator class="q-my-md" />
          <div class="property-group">
            <div class="group-header">
              <q-icon name="search" size="18px" />
              <span>CONFIGURAÇÃO DE LOOKUP</span>
            </div>

            <div class="group-content">
              <div class="field-row">
                <label class="field-label">Endpoint de busca</label>
                <q-input v-model="localField.lookupEndpoint" filled dense placeholder="/api/entidade/lookup/search" />
              </div>

              <div class="field-row">
                <label class="field-label">Colunas para exibir na busca</label>
                <q-select
                  v-model="localField.displayColumns"
                  filled
                  dense
                  multiple
                  use-chips
                  use-input
                  hide-dropdown-icon
                  @new-value="addDisplayColumn"
                  hint="Digite e pressione Enter para adicionar"
                />
              </div>

              <div class="field-row">
                <q-checkbox v-model="localField.allowCreate" label="Permitir criar novo registro" />
              </div>

              <div v-if="localField.allowCreate" class="field-row">
                <label class="field-label">Componente de criação rápida</label>
                <q-input v-model="localField.createDialogComponent" filled dense placeholder="Ex: ProdutoQuickCreate" />
              </div>
            </div>
          </div>
        </template>

        <!-- Configurações para campo COMPUTED -->
        <template v-else-if="localField.type === 'COMPUTED'">
          <q-separator class="q-my-md" />
          <div class="property-group">
            <div class="group-header">
              <q-icon name="calculate" size="18px" />
              <span>CAMPO CALCULADO</span>
            </div>

            <div class="group-content">
              <div class="field-row">
                <label class="field-label">Fórmula</label>
                <q-input
                  v-model="localField.formula"
                  filled
                  dense
                  type="textarea"
                  rows="3"
                  placeholder="Ex: formData.quantidade * formData.valorUnitario"
                />
                <div class="text-caption text-grey-6 q-mt-sm">
                  Variáveis disponíveis:
                  <ul class="q-pl-md q-my-xs">
                    <li>formData - dados do formulário completo</li>
                    <li>data - dados do formulário (alias)</li>
                    <li>sum() - função para somar arrays</li>
                  </ul>
                </div>
              </div>

              <div class="field-row">
                <label class="field-label">Prefixo</label>
                <q-input v-model="localField.prefix" filled dense placeholder="Ex: R$" />
              </div>

              <div class="field-row">
                <label class="field-label">Sufixo</label>
                <q-input v-model="localField.suffix" filled dense placeholder="Ex: %" />
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="localField.type === 'FILE'">
          <q-separator class="q-my-md" />
          <div class="property-group">
            <div class="group-header">
              <q-icon name="attach_file" size="18px" />
              <span>PROPRIEDADES DE UPLOAD</span>
            </div>

            <div class="group-content">
              <div class="field-row">
                <label class="field-label">Tipos aceitos</label>
                <q-input v-model="localField.accept" filled dense placeholder="image/*" />
              </div>

              <div class="field-row">
                <label class="field-label">Tamanho máximo (bytes)</label>
                <q-input v-model.number="localField.maxSize" type="number" filled dense />
              </div>

              <div class="field-row">
                <q-checkbox v-model="localField.showPreview" label="Mostrar preview" />
              </div>

              <div class="field-row">
                <q-checkbox v-model="localField.immediateUpload" label="Upload imediato" />
              </div>
            </div>
          </div>
        </template>

        <!-- Propriedades para DATE/DATETIME/TIME -->
        <template v-else-if="['DATE', 'DATETIME', 'TIME'].includes(localField.type)">
          <q-separator class="q-my-md" />
          <div class="property-group">
            <div class="group-header">
              <q-icon name="event" size="18px" />
              <span>PROPRIEDADES DE DATA/HORA</span>
            </div>

            <div class="group-content">
              <div v-if="localField.type === 'DATETIME'" class="field-row">
                <q-checkbox v-model="localField.props.format24h" label="Formato 24 horas" />
              </div>

              <div v-if="localField.type === 'DATETIME'" class="field-row">
                <q-checkbox v-model="localField.props['with-seconds']" label="Incluir segundos" />
              </div>

              <div class="field-row">
                <label class="field-label">Máscara</label>
                <q-input v-model="localField.props.mask" filled dense :placeholder="getDateMaskPlaceholder()" />
              </div>

              <div v-if="localField.type === 'DATE'" class="field-row">
                <label class="field-label">Formato de exibição</label>
                <q-select v-model="localField.props.displayFormat" :options="dateFormatOptions" filled dense emit-value map-options />
              </div>
            </div>
          </div>
        </template>

        <!-- Validações -->
        <q-separator class="q-my-md" />
        <div class="property-group">
          <div class="group-header">
            <q-icon name="fact_check" size="18px" />
            <span>VALIDAÇÃO E REGRAS</span>
          </div>

          <div class="group-content">
            <div class="field-row">
              <label class="field-label">Validações</label>
              <q-select v-model="localField.validations" :options="validationOptions" filled dense multiple use-chips stack-label />
            </div>
          </div>
        </div>

        <!-- Visibilidade Condicional -->
        <q-separator class="q-my-md" />
        <div class="property-group">
          <div class="group-header">
            <q-icon name="visibility" size="18px" />
            <span>VISIBILIDADE CONDICIONAL</span>
          </div>

          <div class="group-content">
            <div class="field-row">
              <label class="field-label">Condição de visibilidade</label>
              <q-input v-model="localField.visibilityCondition" filled dense placeholder="Ex: formData.tipo === 'PJ'">
                <template v-slot:append>
                  <q-icon name="help">
                    <q-tooltip class="bg-grey-8" max-width="300px">
                      Use expressões JavaScript. Variáveis disponíveis:
                      <br />
                      - formData: dados do formulário
                      <br />
                      - field: campo atual
                      <br />
                      - isEditing: modo de edição
                    </q-tooltip>
                  </q-icon>
                </template>
              </q-input>
            </div>
          </div>
        </div>

        <!-- Componente Customizado -->
        <q-separator class="q-my-md" />
        <div class="property-group">
          <div class="group-header">
            <q-icon name="code" size="18px" />
            <span>AVANÇADO</span>
          </div>

          <div class="group-content">
            <div class="field-row">
              <label class="field-label">Componente customizado</label>
              <q-input v-model="localField.component" filled dense placeholder="Ex: CustomDatePicker" />
            </div>

            <div class="field-row" v-if="localField.type !== 'COMPUTED'">
              <label class="field-label">Fórmula (campo calculado)</label>
              <q-input v-model="localField.formula" filled dense placeholder="Ex: formData.quantidade * formData.valorUnitario" />
            </div>
          </div>
        </div>
      </q-form>
    </q-card-section>

    <q-separator />

    <q-card-actions align="right" class="q-pa-md">
      <q-btn flat label="Cancelar" @click="close" />
      <q-btn unelevated label="Salvar" color="primary" @click="saveField" />
    </q-card-actions>
  </q-card>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useFormBuilderStore } from "@/stores/formBuilderStore";
import TableColumnEditor from "./TableColumnEditor.vue";

const store = useFormBuilderStore();

// Props reativas
const field = computed(() => store.selectedField);
const localField = ref({});
const optionsText = ref("");

// Options
const fieldTypes = [
  { label: "Texto", value: "TEXT" },
  { label: "Número", value: "NUMBER" },
  { label: "Moeda", value: "CURRENCY" },
  { label: "Data", value: "DATE" },
  { label: "Data e Hora", value: "DATETIME" },
  { label: "Hora", value: "TIME" },
  { label: "Área de Texto", value: "TEXTAREA" },
  { label: "Checkbox", value: "CHECKBOX" },
  { label: "Select", value: "SELECT" },
  { label: "Lookup Avançado", value: "LOOKUP" },
  { label: "Tabela", value: "TABLE" },
  { label: "Campo Calculado", value: "COMPUTED" },
  { label: "Arquivo", value: "FILE" },
  { label: "E-mail", value: "EMAIL" },
  { label: "URL", value: "URL" },
  { label: "Telefone", value: "PHONE" },
];

// Mapeamento de tipo para componente
const typeToComponentMap = {
  TEXT: "q-input",
  NUMBER: "q-input",
  CURRENCY: "q-input",
  DATE: "q-date",
  DATETIME: "q-datetime",
  TIME: "q-time",
  TEXTAREA: "q-input",
  CHECKBOX: "q-checkbox",
  SELECT: "q-select",
  LOOKUP: "LookupSelect",
  TABLE: "TableField",
  COMPUTED: "ComputedField",
  FILE: "QFile",
  EMAIL: "q-input",
  URL: "q-input",
  PHONE: "q-input",
};

const colOptions = ["col-12", "col-6", "col-4", "col-3", "col-8", "col-9"];

const maskOptions = [
  { label: "CPF", value: "###.###.###-##" },
  { label: "CNPJ", value: "##.###.###/####-##" },
  { label: "CEP", value: "#####-###" },
  { label: "Telefone", value: "(##) ####-####" },
  { label: "Celular", value: "(##) #####-####" },
  { label: "Data", value: "##/##/####" },
];

const dateFormatOptions = [
  { label: "DD/MM/AAAA", value: "DD/MM/YYYY" },
  { label: "MM/DD/AAAA", value: "MM/DD/YYYY" },
  { label: "AAAA-MM-DD", value: "YYYY-MM-DD" },
];

const validationOptions = ["required", "email", "url", "minLength", "maxLength", "min", "max", "pattern", "cpf", "cnpj"];

// Watchers
watch(
  field,
  (newField) => {
    if (newField) {
      localField.value = {
        ...newField,
        props: { ...newField.props },
        validations: [...(newField.validations || [])],
      };

      if (localField.value.readOnly == null) {
        const propsReadOnly = localField.value.props?.readonly ?? localField.value.props?.readOnly;
        localField.value.readOnly = !!propsReadOnly;
      }

      // Garantir que o componente correto seja atribuído baseado no tipo
      if (!localField.value.component && localField.value.type) {
        localField.value.component = typeToComponentMap[localField.value.type];
      }

      // Inicializar arrays se não existirem
      if (localField.value.type === "LOOKUP" && !localField.value.displayColumns) {
        localField.value.displayColumns = [];
      }

      if (localField.value.type === "TABLE" && !localField.value.columns) {
        localField.value.columns = [];
      }

      // Converter opções para texto
      if (newField.options && Array.isArray(newField.options)) {
        optionsText.value = newField.options.map((opt) => `${opt.value}|${opt.label}`).join("\n");
      }
    }
  },
  { immediate: true }
);

// Methods
function getDateMaskPlaceholder() {
  switch (localField.value.type) {
    case "DATE":
      return "##/##/####";
    case "DATETIME":
      return "####-##-## ##:##";
    case "TIME":
      return "##:##";
    default:
      return "";
  }
}

function addDisplayColumn(val) {
  if (!localField.value.displayColumns) {
    localField.value.displayColumns = [];
  }
  localField.value.displayColumns.push(val);
}

function saveField() {
  // Garantir componente correto baseado no tipo
  if (!localField.value.component && localField.value.type) {
    localField.value.component = typeToComponentMap[localField.value.type];
  }

  // Configurações específicas por tipo
  if (localField.value.type === "DATETIME" && !localField.value.props.mask) {
    localField.value.props.mask = "####-##-## ##:##";
    localField.value.props.format24h = true;
    localField.value.props["with-seconds"] = false;
  }

  // Converter texto de opções para array (apenas se não tiver endpoint)
  if (localField.value.type === "SELECT" && !localField.value.optionsEndpoint && optionsText.value) {
    localField.value.options = optionsText.value
      .split("\n")
      .filter((line) => line.trim())
      .map((line) => {
        const [value, label] = line.split("|");
        return { value: value.trim(), label: (label || value).trim() };
      });
  }

  // Se tem endpoint, garantir que não tem opções estáticas
  if (localField.value.optionsEndpoint) {
    delete localField.value.options;
  }

  store.updateField(localField.value.name, localField.value);
  // NÃO fechar o painel após salvar - manter aberto para continuar editando
}

function close() {
  store.selectedField = null;

  // Emit event to close the right panel
  const event = new CustomEvent("close-field-editor");
  window.dispatchEvent(event);
}
</script>

<style lang="scss" scoped>
.field-editor {
  height: 100%;
  overflow-y: auto;
}

.property-group {
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-weight: 700;
  color: #666;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 16px;

  q-icon {
    color: #999;
  }
}

.group-content {
  padding-left: 4px;
}

.field-row {
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #333;
  margin-bottom: 6px;
}

:deep(.q-field--filled .q-field__control) {
  background-color: #f5f5f5;
}

:deep(.q-field--filled .q-field__control:hover:before) {
  opacity: 0;
}
</style>
