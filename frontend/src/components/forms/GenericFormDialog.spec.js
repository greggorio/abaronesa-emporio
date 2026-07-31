import { describe, it, expect, vi } from "vitest";
import { shallowMount } from "@vue/test-utils";
import { nextTick } from "vue";

vi.mock("quasar", () => ({
  useQuasar: () => ({
    screen: { lt: { md: false } },
    notify: vi.fn(),
  }),
}));

vi.mock("@/composables/useApiRequest", () => ({
  useApiRequest: () => ({
    apiRequest: vi.fn(),
  }),
}));

vi.mock("@/composables/useFileUpload", () => ({
  useFileUpload: () => ({
    uploadedFiles: { value: {} },
    uploadMultipleFiles: vi.fn(),
    clearAllFiles: vi.fn(),
  }),
}));

const { componentStub } = vi.hoisted(() => ({
  componentStub: {
    name: "ComponentStub",
    template: "<div />",
  },
}));

vi.mock("./FormField.vue", () => ({ default: componentStub }));
vi.mock("@/components/forms/VencimentosTab.vue", () => ({ default: componentStub }));
vi.mock("@/components/forms/RecebimentosTab.vue", () => ({ default: componentStub }));
vi.mock("./CartaoJurosManager.vue", () => ({ default: componentStub }));
vi.mock("./PermissoesTab.vue", () => ({ default: componentStub }));
vi.mock("./GrupoClienteDescontosTab.vue", () => ({ default: componentStub }));
vi.mock("./ProdutoVariacoesTab.vue", () => ({ default: componentStub }));
vi.mock("./ProdutoPrecificacaoTab.vue", () => ({ default: componentStub }));
vi.mock("./ProdutoMidiaTab.vue", () => ({ default: componentStub }));
vi.mock("./ProdutoValidadeTab.vue", () => ({ default: componentStub }));
vi.mock("./ProdutoSignageTab.vue", () => ({ default: componentStub }));
vi.mock("./ProdutoFichaTecnicaTab.vue", () => ({ default: componentStub }));
vi.mock("./ParcelasCrediarioTab.vue", () => ({ default: componentStub }));
vi.mock("./DocumentoFiscalTab.vue", () => ({ default: componentStub }));
vi.mock("./InventarioFormDialog.vue", () => ({ default: componentStub }));
vi.mock("./MesaQRCodeTab.vue", () => ({ default: componentStub }));
vi.mock("./EmbalagensTab.vue", () => ({ default: componentStub }));
vi.mock("./RecebimentoItensTab.vue", () => ({ default: componentStub }));
vi.mock("./ProdutoHarmonizacaoTab.vue", () => ({ default: componentStub }));
vi.mock("./ProdutoDisponibilidadeTab.vue", () => ({ default: componentStub }));
vi.mock("./SubcategoriaDisponibilidadeTab.vue", () => ({ default: componentStub }));
vi.mock("./ProdutoPromocoesTab.vue", () => ({ default: componentStub }));
vi.mock("./ComprovanteFiscalTab.vue", () => ({ default: componentStub }));

import GenericFormDialog from "./GenericFormDialog.vue";

const baseConfig = {
  title: {
    new: "Novo Registro",
    edit: "Editar Registro",
  },
  labels: {
    save: "Salvar",
    cancel: "Cancelar",
  },
  features: {
    tabs: true,
  },
};

async function flushComponentUpdates() {
  await Promise.resolve();
  await nextTick();
  await Promise.resolve();
  await nextTick();
}

async function mountDialog(registro) {
  const wrapper = shallowMount(GenericFormDialog, {
    props: {
      modelValue: true,
      registro,
      form_definitions: [],
      config: baseConfig,
    },
    global: {
      config: {
        compilerOptions: {
          isCustomElement: (tag) => tag.startsWith("q-"),
        },
      },
    },
  });

  await flushComponentUpdates();
  return wrapper;
}

function getToolbarTitle(wrapper) {
  return wrapper.find(".text-h6").text().trim();
}

describe("GenericFormDialog - computedTitle", () => {
  it("deve mostrar o titulo de criacao quando nao esta em edicao", async () => {
    const wrapper = await mountDialog({});

    expect(getToolbarTitle(wrapper)).toBe("Novo Registro");
  });

  it("deve montar titulo dinamico em edicao com campo textual valido", async () => {
    const wrapper = await mountDialog({
      id: 123,
      nome: "Bolo de Cenoura",
    });

    expect(getToolbarTitle(wrapper)).toBe("#123 - Bolo de Cenoura");
  });

  it("deve truncar o valor textual quando ultrapassar 40 caracteres", async () => {
    const longValue = "1234567890123456789012345678901234567890EXTRA";
    const wrapper = await mountDialog({
      id: 7,
      descricao: longValue,
    });

    expect(getToolbarTitle(wrapper)).toBe(`#7 - ${longValue.slice(0, 40)}...`);
  });

  it("deve ignorar campos tecnicos e usar o primeiro campo textual elegivel", async () => {
    const wrapper = await mountDialog({
      id: 88,
      imageUrl: "https://cdn.exemplo.com/produto.png",
      image_path: "/tmp/produto.png",
      updatedAt: "2026-04-09T12:00:00Z",
      nome: "Pao Frances",
    });

    expect(getToolbarTitle(wrapper)).toBe("#88 - Pao Frances");
  });

  it("deve fazer fallback para 'Editar Registro' quando nao houver campo textual elegivel", async () => {
    const wrapper = await mountDialog({
      id: 55,
      imageUrl: "https://cdn.exemplo.com/produto.png",
      imagePath: "/tmp/produto.png",
      version: 3,
      ativo: true,
      preco: 12.5,
    });

    expect(getToolbarTitle(wrapper)).toBe("Editar Registro");
  });
});
