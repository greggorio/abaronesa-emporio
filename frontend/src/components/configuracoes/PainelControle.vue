<template>
  <q-page padding>
    <div class="q-py-md" v-if="!activeConfig">
      <!-- Abas principais -->
      <q-tabs
        v-model="tab"
        class="text-primary"
        indicator-color="primary"
        active-color="primary"
        align="justify"
      >
        <q-tab name="configuracoes" label="Configurações" />
        <q-tab name="site" label="Site" />
        <q-tab name="integracoes" label="Integrações & APIs" />
        <q-tab name="comunicacoes" label="Comunicações" />
        <q-tab name="fiscal" label="Fiscal" />
        <q-tab name="estoque" label="Estoque" />
        <q-tab v-if="isRootUser" name="desenvolvimento" label="Desenvolvimento" />
      </q-tabs>

      <q-separator />

      <!-- Painéis das abas -->
      <q-tab-panels v-model="tab" animated class="conteudo">
        <!-- Aba Configurações -->
        <q-tab-panel name="configuracoes">
          <div class="row q-col-gutter-md">
            <!-- Card Sistema Geral -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('sistema-geral')">
                <q-card-section class="text-center">
                  <q-icon name="o_settings" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">Sistema Geral</div>
                  <div class="text-caption text-grey">Parâmetros gerais do sistema</div>
                </q-card-section>
              </q-card>
            </div>
          </div>
        </q-tab-panel>

        <!-- Aba Site -->
        <q-tab-panel name="site">
          <div class="row q-col-gutter-md">
            <!-- Card Cardápio Digital / Site -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('site-config')">
                <q-card-section class="text-center">
                  <q-icon name="o_web" size="3em" color="positive" />
                  <div class="text-h6 q-mt-sm">Cardápio Digital</div>
                  <div class="text-caption text-grey">Configurações do site e cardápio</div>
                </q-card-section>
              </q-card>
            </div>
            <!-- Card Gamificação -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('gamificacao-config')">
                <q-card-section class="text-center">
                  <q-icon name="o_emoji_events" size="3em" color="amber-5" />
                  <div class="text-h6 q-mt-sm">Gamificação</div>
                  <div class="text-caption text-grey">Regras de pontuação e recompensas</div>
                </q-card-section>
              </q-card>
            </div>
            <!-- Card Traduções do Cardápio -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('menu-translations')">
                <q-card-section class="text-center">
                  <q-icon name="translate" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">Traduções do Cardápio</div>
                  <div class="text-caption text-grey">Revisão/edição das traduções do menu</div>
                </q-card-section>
              </q-card>
            </div>
          </div>
        </q-tab-panel>

        <!-- Aba Integrações & APIs -->
        <q-tab-panel name="integracoes">
          <div class="row q-col-gutter-md">
            <!-- Card OpenAI / IA -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('openai-config')">
                <q-card-section class="text-center">
                  <q-icon name="o_psychology" size="3em" color="secondary" />
                  <div class="text-h6 q-mt-sm">OpenAI / IA</div>
                  <div class="text-caption text-grey">Chaves e modelos de IA</div>
                </q-card-section>
              </q-card>
            </div>
            <!-- Card Mercado Pago -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('mercado-pago')">
                <q-card-section class="text-center">
                  <q-icon name="o_credit_card" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">Mercado Pago</div>
                  <div class="text-caption text-grey">Configurações de pagamento</div>
                </q-card-section>
              </q-card>
            </div>
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('pagseguro')">
                <q-card-section class="text-center">
                  <q-icon name="o_credit_card" size="3em" color="secondary" />
                  <div class="text-h6 q-mt-sm">PagSeguro</div>
                  <div class="text-caption text-grey">Configurações de pagamento</div>
                </q-card-section>
              </q-card>
            </div>
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('uber-direct')">
                <q-card-section class="text-center">
                  <q-icon name="delivery_dining" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">Uber Direct</div>
                  <div class="text-caption text-grey">Configurações de delivery</div>
                </q-card-section>
              </q-card>
            </div>
            <!-- Card Agente de Impressão -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('print-agent')">
                <q-card-section class="text-center">
                  <q-icon name="print" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">Agente de Impressão</div>
                  <div class="text-caption text-grey">Pareamento com servidor de impressão</div>
                </q-card-section>
              </q-card>
            </div>
          </div>
        </q-tab-panel>

        <!-- Aba Comunicações -->
        <q-tab-panel name="comunicacoes">
          <div class="row q-col-gutter-md">
            <!-- Card WhatsApp -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('whatsapp-config')">
                <q-card-section class="text-center">
                  <q-icon name="o_chat" size="3em" color="positive" />
                  <div class="text-h6 q-mt-sm">WhatsApp</div>
                  <div class="text-caption text-grey">Sessão, QR e teste de envio</div>
                </q-card-section>
              </q-card>
            </div>

            <!-- Card Servidor SMTP -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('smtp-config')">
                <q-card-section class="text-center">
                  <q-icon name="o_mail" size="3em" color="info" />
                  <div class="text-h6 q-mt-sm">Servidor SMTP</div>
                  <div class="text-caption text-grey">Configurações do servidor de e-mail</div>
                </q-card-section>
              </q-card>
            </div>
          </div>
        </q-tab-panel>

        <!-- Aba Fiscal -->
        <q-tab-panel name="fiscal">
          <div class="row q-col-gutter-md">
            <!-- Card NFe -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('nfe-config')">
                <q-card-section class="text-center">
                  <q-icon name="o_description" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">NFe</div>
                  <div class="text-caption text-grey">Configurações e testes de nota fiscal eletrônica</div>
                </q-card-section>
              </q-card>
            </div>

            <!-- Card NFCe -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('nfce-config')">
                <q-card-section class="text-center">
                  <q-icon name="o_receipt" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">NFCe</div>
                  <div class="text-caption text-grey">Configurações e testes de cupom fiscal eletrônico</div>
                </q-card-section>
              </q-card>
            </div>

            <!-- Card Upload de Certificado -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('certificado-upload')">
                <q-card-section class="text-center">
                  <q-icon name="lock" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">Certificado Digital</div>
                  <div class="text-caption text-grey">Upload e gerenciamento do certificado digital</div>
                </q-card-section>
              </q-card>
            </div>
          </div>
        </q-tab-panel>

        <!-- Aba Estoque -->
        <q-tab-panel name="estoque">
          <div class="row q-col-gutter-md">
            <!-- Card Zerar Estoque -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('zerar-estoque')">
                <q-card-section class="text-center">
                  <q-icon name="inventory_2" size="3em" color="negative" />
                  <div class="text-h6 q-mt-sm">Zerar Estoque</div>
                  <div class="text-caption text-grey">Rotina para zerar estoque do sistema</div>
                </q-card-section>
              </q-card>
            </div>
          </div>
        </q-tab-panel>

        <!-- Aba Desenvolvimento -->
        <q-tab-panel v-if="isRootUser" name="desenvolvimento">
          <div class="row q-col-gutter-md">
            <!-- Card Form Builder -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="navigateToFormBuilder">
                <q-card-section class="text-center">
                  <q-icon name="o_dashboard_customize" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">Form Builder</div>
                  <div class="text-caption text-grey">Editor visual de formulários dinâmicos</div>
                  <div class="text-caption text-positive q-mt-xs">
                    <q-icon name="circle" size="xs" /> Ferramenta visual
                  </div>
              </q-card-section>
            </q-card>
          </div>

            <!-- Card Form Definitions -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('form-definitions')">
                <q-card-section class="text-center">
                  <q-icon name="o_dynamic_form" size="3em" color="secondary" />
                  <div class="text-h6 q-mt-sm">Definições de Formulários</div>
                  <div class="text-caption text-grey">Gerencie templates dos formulários dinâmicos</div>
                  <div class="text-caption text-positive q-mt-xs">
                    <q-icon name="circle" size="xs" /> Gerenciamento ativo
                  </div>
                </q-card-section>
              </q-card>
            </div>

            <!-- Card Identidade -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('app-identity')">
                <q-card-section class="text-center">
                  <q-icon name="badge" size="3em" color="accent" />
                  <div class="text-h6 q-mt-sm">Identidade do App</div>
                  <div class="text-caption text-grey">Nome do app e segmento</div>
                  <div class="text-caption text-primary q-mt-xs">
                    <q-icon name="circle" size="xs" /> Configuração persistida
                  </div>
                </q-card-section>
              </q-card>
            </div>

            <!-- Card Importação de Produtos -->
            <div class="col-md-4 col-sm-6 col-xs-12">
              <q-card class="cursor-pointer" @click="handleCardClick('produto-import-preview')">
                <q-card-section class="text-center">
                  <q-icon name="inventory_2" size="3em" color="accent" />
                  <div class="text-h6 q-mt-sm">Importação de Produtos</div>
                  <div class="text-caption text-grey">Upload e preview de produtos via XLS/XLSX</div>
                  <div class="text-caption text-positive q-mt-xs">
                    <q-icon name="circle" size="xs" /> Nova funcionalidade
                  </div>
                </q-card-section>
              </q-card>
            </div>

            <!-- Card Publisher local -->
            <div
              v-if="isRootUser && releasePublisherConfig.mode === 'publisher'"
              class="col-md-4 col-sm-6 col-xs-12"
            >
              <q-card
                class="cursor-pointer"
                aria-label="Abrir Gerenciamento de Releases"
                @click="handleCardClick('release-publisher')"
              >
                <q-card-section class="text-center">
                  <q-icon name="new_releases" size="3em" color="primary" />
                  <div class="text-h6 q-mt-sm">Gerenciamento de Releases</div>
                  <div class="text-caption text-grey">
                    Publique uma release global a partir de um candidato validado
                  </div>
                </q-card-section>
              </q-card>
            </div>

            <!-- Card Atualização do sistema -->
            <div
              v-if="isRootUser && releaseDeployerConfig.mode === 'deployer' && deployerCapabilityValid"
              class="col-md-4 col-sm-6 col-xs-12"
            >
              <q-card
                class="cursor-pointer"
                aria-label="Abrir Atualização do sistema"
                @click="navigateToDeployment"
              >
                <q-card-section class="text-center">
                  <q-icon name="system_update" size="3em" color="positive" />
                  <div class="text-h6 q-mt-sm">Atualização do sistema</div>
                  <div class="text-caption text-grey">
                    Atualize a instalação para a próxima release global elegível
                  </div>
                </q-card-section>
              </q-card>
            </div>

          </div>
        </q-tab-panel>
      </q-tab-panels>
    </div>

    <!-- Área de conteúdo quando um card é selecionado -->
    <div v-else>
      <!-- Barra de navegação -->
      <div class="row items-center q-mb-md">
        <q-btn
          flat
          round
          icon="arrow_back"
          @click="activeConfig = null"
          class="q-mr-sm"
        />
        <div class="text-h6">{{ getConfigTitle() }}</div>
      </div>

      <!-- Componente de configuração -->
      <component :is="getConfigComponent()" v-if="getConfigComponent()" />
    </div>
  </q-page>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import eventBus from "@/eventBus";
import { useApiRequest } from "@/composables/useApiRequest";
import { useUserStore } from "@/stores/userStore";
import ConfiguracoesGerais from "./ConfiguracoesGerais.vue";
import FormDefinitionsConfig from "./FormDefinitionsConfig.vue";
import OpenAIConfig from "./OpenAIConfig.vue";
import AppIdentityConfig from "./AppIdentityConfig.vue";
import NFeConfig from "./NFeConfig.vue";
import NFCeConfig from "./NFCeConfig.vue";
import CertificadoUpload from "./CertificadoUpload.vue";
import MenuTranslationsConfig from "./menu/MenuTranslationsConfig.vue";
import SiteConfig from "./SiteConfig.vue";
import GamificacaoConfig from "./GamificacaoConfig.vue";
import WhatsAppConfig from "./WhatsAppConfig.vue";
import ProdutoImportPreview from "./importacao/ProdutoImportPreview.vue";
import MercadoPagoConfig from "./MercadoPagoConfig.vue";
import PagSeguroConfig from "./PagSeguroConfig.vue";
import UberDirectConfig from "./UberDirectConfig.vue";
import ParearPrintAgent from "./ParearPrintAgent.vue";
import ServidorSMTPConfig from "./ServidorSMTPConfig.vue";
import ZerarEstoqueSimples from "./ZerarEstoqueSimples.vue";
import ReleasePublisherConfig from "./ReleasePublisherConfig.vue";
import { releasePublisherConfig } from "@/config/releasePublisher";
import { baseApiUrl } from "@/global";
import { releaseDeployerConfig } from "@/config/releaseDeployer";
import { createReleaseDeployerClient } from "@/services/releaseDeployerClient";

const { apiRequest } = useApiRequest();
const router = useRouter();
const userStore = useUserStore();

// Estado
const tab = ref("configuracoes");
const activeConfig = ref(null);
const isRootUser = computed(() => userStore.isRootUser);
const deployerCapabilityValid = ref(false);
const deployerClient =
  releaseDeployerConfig.mode === "deployer"
    ? createReleaseDeployerClient({
        baseApiUrl,
        getErpToken: () => globalThis.sessionStorage?.getItem("token") ?? null,
      })
    : null;

// Configurações disponíveis
const configComponents = {
  'sistema-geral': ConfiguracoesGerais,
  'site-config': SiteConfig,
  'gamificacao-config': GamificacaoConfig,
  'form-definitions': FormDefinitionsConfig,
  'openai-config': OpenAIConfig,
  'mercado-pago': MercadoPagoConfig,
  'pagseguro': PagSeguroConfig,
  'uber-direct': UberDirectConfig,
  'menu-translations': MenuTranslationsConfig,
  'app-identity': AppIdentityConfig,
  'nfe-config': NFeConfig,
  'nfce-config': NFCeConfig,
  'certificado-upload': CertificadoUpload,
  'whatsapp-config': WhatsAppConfig,
  'produto-import-preview': ProdutoImportPreview,
  'print-agent': ParearPrintAgent,
  'smtp-config': ServidorSMTPConfig,
  'zerar-estoque': ZerarEstoqueSimples,
  'release-publisher': ReleasePublisherConfig,
};

const configTitles = {
  'sistema-geral': 'Configurações Gerais',
  'site-config': 'Configurações do Site / Cardápio',
  'gamificacao-config': 'Configurações de Gamificação',
  'form-definitions': 'Definições de Formulários',
  'openai-config': 'Configurações OpenAI / IA',
  'mercado-pago': 'Configurações Mercado Pago',
  'pagseguro': 'Configurações PagSeguro',
  'uber-direct': 'Configurações Uber Direct',
  'menu-translations': 'Traduções do Cardápio',
  'app-identity': 'Identidade da Aplicação',
  'nfe-config': 'Configurações NFe',
  'nfce-config': 'Configurações NFCe',
  'certificado-upload': 'Upload de Certificado Digital',
  'produto-import-preview': 'Importação de Produtos',
  'print-agent': 'Pareamento com Agente de Impressão',
  'smtp-config': 'Configurações Servidor SMTP',
  'zerar-estoque': 'Zerar Estoque',
  'release-publisher': 'Gerenciamento de Releases',
};

// Métodos
const handleCardClick = (card) => {
  activeConfig.value = card;
};

const navigateToFormBuilder = () => {
  router.push("/form-builder");
};

const navigateToDeployment = () => {
  router.push("/configuracoes/atualizacao-sistema");
};

const getConfigComponent = () => {
  return configComponents[activeConfig.value];
};

const getConfigTitle = () => {
  return configTitles[activeConfig.value] || '';
};

// Computed
const breadcrumbs = computed(() => {
  const items = [
    { icon: "home", label: "Home", to: "/" },
    { label: "Painel de Controle", to: "/configuracoes" },
  ];
  
  if (activeConfig.value) {
    items.push({ label: getConfigTitle(), class: "secondary" });
  }
  
  return items;
});

// Lifecycle
onMounted(async () => {
  updateHeader();
  if (isRootUser.value && deployerClient) {
    try {
      await deployerClient.capabilities();
      deployerCapabilityValid.value = true;
    } catch {
      deployerCapabilityValid.value = false;
    }
  }
});

// Watchers
const updateHeader = () => {
  eventBus.emit("update:header", {
    breadcrumbs: breadcrumbs.value,
    title: activeConfig.value ? getConfigTitle() : "Painel de Controle",
    icon: "o_settings",
  });
};

// Watch for config changes
import { watch } from 'vue';
watch([activeConfig], () => {
  updateHeader();
});
</script>

<style lang="scss" scoped>
.cursor-pointer {
  cursor: pointer;
  transition: all 0.3s;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  }
}

.conteudo {
  background: transparent;
}
</style>
