<template>
  <div>
    <div class="text-h5 q-mb-md">Configurações do Servidor SMTP</div>
    <div class="text-caption text-grey q-mb-lg">Configure o servidor de e-mail para envio de mensagens automáticas</div>

    <!-- Status da Conexão -->
    <q-card class="q-mb-md">
      <q-card-section>
        <div class="row items-center">
          <div class="col">
            <q-banner :class="statusConexao.cor" rounded dense>
              <template v-slot:avatar>
                <q-icon :name="statusConexao.icone" />
              </template>
              {{ statusConexao.mensagem }}
              <template v-slot:action v-if="!statusConexao.conectado">
                <q-btn flat color="white" label="Testar Conexão" @click="testarConexao" :loading="testando" />
              </template>
            </q-banner>
          </div>
        </div>
      </q-card-section>
    </q-card>

    <!-- Configurações do Servidor -->
    <q-card class="q-mb-md">
      <q-card-section>
        <div class="text-h6 q-mb-md">Configurações do Servidor</div>

        <div class="row q-col-gutter-md">
          <!-- Servidor SMTP -->
          <div class="col-md-6 col-sm-12">
            <q-input
              v-model="config.servidor"
              label="Servidor SMTP *"
              outlined
              dense
              :disable="loading"
              hint="Ex: smtp.gmail.com, smtp.outlook.com"
            />
          </div>

          <!-- Porta -->
          <div class="col-md-3 col-sm-12">
            <q-input v-model.number="config.porta" label="Porta *" outlined dense type="number" :disable="loading" hint="587, 465, 25" />
          </div>

          <!-- Segurança -->
          <div class="col-md-3 col-sm-12">
            <q-select v-model="config.seguranca" :options="opcoesSeguranca" label="Segurança" outlined dense :disable="loading" />
          </div>

          <!-- E-mail Remetente -->
          <div class="col-md-6 col-sm-12">
            <q-input
              v-model="config.email_remetente"
              label="E-mail Remetente *"
              outlined
              dense
              type="email"
              :disable="loading"
              hint="E-mail que aparecerá como remetente"
            />
          </div>

          <!-- Nome Remetente -->
          <div class="col-md-6 col-sm-12">
            <q-input
              v-model="config.nome_remetente"
              label="Nome Remetente"
              outlined
              dense
              :disable="loading"
              hint="Nome que aparecerá como remetente"
            />
          </div>

          <!-- Usuário -->
          <div class="col-md-6 col-sm-12">
            <q-input v-model="config.usuario" label="Usuário *" outlined dense :disable="loading" hint="Geralmente o mesmo e-mail" />
          </div>

          <!-- Senha -->
          <div class="col-md-6 col-sm-12">
            <q-input
              v-model="config.senha"
              label="Senha *"
              outlined
              dense
              :type="mostrarSenha ? 'text' : 'password'"
              :disable="loading"
              hint="Senha do e-mail ou senha de app"
            >
              <template v-slot:append>
                <q-icon :name="mostrarSenha ? 'visibility' : 'visibility_off'" class="cursor-pointer" @click="mostrarSenha = !mostrarSenha" />
              </template>
            </q-input>
          </div>
        </div>
      </q-card-section>
    </q-card>

    <!-- Provedores Predefinidos -->
    <q-card class="q-mb-md">
      <q-card-section>
        <div class="text-h6 q-mb-md">Configuração Rápida</div>
        <div class="text-caption text-grey q-mb-md">Selecione um provedor para configuração automática</div>

        <div class="row q-col-gutter-sm">
          <div class="col-auto" v-for="provedor in provedores" :key="provedor.nome">
            <q-btn :label="provedor.nome" outline @click="aplicarProvedor(provedor)" :disable="loading" class="full-width" />
          </div>
        </div>
      </q-card-section>
    </q-card>

    <!-- Teste de Envio -->
    <q-card class="q-mb-md">
      <q-card-section>
        <div class="text-h6 q-mb-md">Teste de Envio</div>

        <div class="row q-col-gutter-md">
          <div class="col-md-8 col-sm-12">
            <q-input
              v-model="emailTeste"
              label="E-mail de teste"
              outlined
              dense
              type="email"
              :disable="loading || testando"
              hint="E-mail para receber o teste"
            />
          </div>

          <div class="col-md-4 col-sm-12">
            <q-btn
              label="Enviar Teste"
              color="secondary"
              @click="enviarTeste"
              :loading="testando"
              :disable="loading || !emailTeste || !isFormValid"
              class="full-width"
            />
          </div>
        </div>
      </q-card-section>
    </q-card>

    <!-- Botões de ação -->
    <div class="row justify-end q-gutter-sm">
      <q-btn label="Testar Conexão" color="secondary" outline @click="testarConexao" :loading="testando" :disable="loading || !isFormValid" />
      <q-btn label="Salvar" color="primary" @click="salvar" :loading="loading" :disable="!isFormValid" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Estados
const loading = ref(false);
const testando = ref(false);
const mostrarSenha = ref(false);
const emailTeste = ref("");

// Configurações SMTP
const config = ref({
  servidor: "",
  porta: 587,
  seguranca: "tls",
  email_remetente: "",
  nome_remetente: "",
  usuario: "",
  senha: "",
});

// Status da conexão
const statusConexao = ref({
  conectado: false,
  mensagem: "Conexão não testada",
  cor: "bg-grey-3 text-grey-8",
  icone: "help",
});

// Opções
const opcoesSeguranca = [
  { label: "TLS", value: "tls" },
  { label: "SSL", value: "ssl" },
  { label: "Nenhuma", value: "none" },
];

// Provedores predefinidos
const provedores = [
  {
    nome: "Gmail",
    servidor: "smtp.gmail.com",
    porta: 587,
    seguranca: "tls",
  },
  {
    nome: "Outlook",
    servidor: "smtp-mail.outlook.com",
    porta: 587,
    seguranca: "tls",
  },
  {
    nome: "Yahoo",
    servidor: "smtp.mail.yahoo.com",
    porta: 587,
    seguranca: "tls",
  },
  {
    nome: "Hotmail",
    servidor: "smtp.live.com",
    porta: 587,
    seguranca: "tls",
  },
];

// Computed
const isFormValid = computed(() => {
  return config.value.servidor && config.value.porta && config.value.email_remetente && config.value.usuario && config.value.senha;
});

// Métodos
const carregarConfiguracoes = async () => {
  loading.value = true;
  try {
    const response = await apiRequest("/api/smtp/config");
    if (response) {
      // Mapear os campos do backend para o frontend
      config.value = {
        servidor: response.servidor || "",
        porta: response.porta || 587,
        seguranca: response.seguranca || "tls",
        email_remetente: response.emailRemetente || "",
        nome_remetente: response.nomeRemetente || "",
        usuario: response.usuario || "",
        senha: response.senha || "",
      };

      // Converter segurança string para objeto do q-select
      if (typeof config.value.seguranca === "string") {
        const opcao = opcoesSeguranca.find((o) => o.value === config.value.seguranca);
        if (opcao) {
          config.value.seguranca = opcao;
        }
      }
    }
  } catch (error) {
    console.error("Erro ao carregar configurações SMTP:", error);
  } finally {
    loading.value = false;
  }
};

const aplicarProvedor = (provedor) => {
  config.value.servidor = provedor.servidor;
  config.value.porta = provedor.porta;
  config.value.seguranca = provedor.seguranca;

  $q.notify({
    type: "info",
    message: `Configuração ${provedor.nome} aplicada`,
    position: "top",
  });
};

const testarConexao = async () => {
  if (!isFormValid.value) {
    $q.notify({
      type: "negative",
      message: "Preencha todos os campos obrigatórios",
      position: "top",
    });
    return;
  }

  testando.value = true;
  try {
    // Preparar payload
    const payload = {
      servidor: config.value.servidor,
      porta: config.value.porta,
      seguranca: typeof config.value.seguranca === "object" ? config.value.seguranca.value : config.value.seguranca,
      emailRemetente: config.value.email_remetente,
      nomeRemetente: config.value.nome_remetente,
      usuario: config.value.usuario,
      senha: config.value.senha,
    };

    const response = await apiRequest("/api/smtp/testar-conexao", "POST", payload);

    if (response?.sucesso) {
      statusConexao.value = {
        conectado: true,
        mensagem: "Conexão estabelecida com sucesso!",
        cor: "bg-positive text-white",
        icone: "check_circle",
      };
    } else {
      throw new Error(response?.erro || "Erro na conexão");
    }
  } catch (error) {
    statusConexao.value = {
      conectado: false,
      mensagem: `Erro: ${error.message}`,
      cor: "bg-negative text-white",
      icone: "error",
    };
  } finally {
    testando.value = false;
  }
};

const enviarTeste = async () => {
  if (!emailTeste.value || !isFormValid.value) return;

  testando.value = true;
  try {
    // Preparar payload
    const payload = {
      servidor: config.value.servidor,
      porta: config.value.porta,
      seguranca: typeof config.value.seguranca === "object" ? config.value.seguranca.value : config.value.seguranca,
      emailRemetente: config.value.email_remetente,
      nomeRemetente: config.value.nome_remetente,
      usuario: config.value.usuario,
      senha: config.value.senha,
      email_destino: emailTeste.value,
    };

    const response = await apiRequest("/api/smtp/enviar-teste", "POST", payload);

    if (response?.sucesso) {
      $q.notify({
        type: "positive",
        message: "E-mail de teste enviado com sucesso!",
        position: "top",
      });
    }
  } catch (error) {
    $q.notify({
      type: "negative",
      message: "Erro ao enviar e-mail de teste: " + error.message,
      position: "top",
    });
  } finally {
    testando.value = false;
  }
};

const salvar = async () => {
  if (!isFormValid.value) return;

  loading.value = true;
  try {
    // Preparar payload com segurança como string
    const payload = {
      servidor: config.value.servidor,
      porta: config.value.porta,
      seguranca: typeof config.value.seguranca === "object" ? config.value.seguranca.value : config.value.seguranca,
      emailRemetente: config.value.email_remetente,
      nomeRemetente: config.value.nome_remetente,
      usuario: config.value.usuario,
      senha: config.value.senha,
    };

    await apiRequest("/api/smtp/salvar", "PUT", payload);

    $q.notify({
      type: "positive",
      message: "Configurações SMTP salvas com sucesso!",
      position: "top",
    });
  } catch (error) {
    console.error("Erro ao salvar configurações SMTP:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao salvar configurações SMTP",
      position: "top",
    });
  } finally {
    loading.value = false;
  }
};

// Lifecycle
onMounted(async () => {
  await carregarConfiguracoes();
});
</script>