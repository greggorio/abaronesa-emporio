import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

const testState = vi.hoisted(() => ({
  root: true,
  mode: "publisher",
  deployerMode: "disabled",
  deployerCapability: false,
}));

vi.mock("@/stores/userStore", () => ({
  useUserStore: () => ({
    get isRootUser() {
      return testState.root;
    },
  }),
}));
vi.mock("@/config/releasePublisher", () => ({
  get releasePublisherConfig() {
    return { mode: testState.mode, url: null };
  },
}));
vi.mock("@/global", () => ({ baseApiUrl: "https://erp.invalid" }));
vi.mock("@/config/releaseDeployer", () => ({
  get releaseDeployerConfig() {
    return { mode: testState.deployerMode };
  },
}));
vi.mock("@/services/releaseDeployerClient", () => ({
  createReleaseDeployerClient: () => ({
    capabilities: vi.fn(async () => {
      if (!testState.deployerCapability) throw new Error("invalid");
      return {
        mode: "deployer",
        apiVersion: "v1",
        capabilities: ["deployment:read", "deployment:execute"],
      };
    }),
  }),
}));
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("@/eventBus", () => ({
  default: { emit: vi.fn() },
}));
vi.mock("@/composables/useApiRequest", () => ({
  useApiRequest: () => ({ apiRequest: vi.fn() }),
}));

import PainelControle from "./PainelControle.vue";

const passthrough = {
  template: "<div><slot /></div>",
};

function renderPanel() {
  return mount(PainelControle, {
    global: {
      stubs: {
        "q-page": passthrough,
        "q-tabs": passthrough,
        "q-tab": passthrough,
        "q-separator": true,
        "q-tab-panels": passthrough,
        "q-tab-panel": passthrough,
        "q-card": passthrough,
        "q-card-section": passthrough,
        "q-icon": true,
        "q-btn": passthrough,
      },
    },
  });
}

describe("PainelControle publisher card", () => {
  afterEach(() => {
    testState.root = true;
    testState.mode = "publisher";
    testState.deployerMode = "disabled";
    testState.deployerCapability = false;
  });

  it("shows the exact card only for root with local publisher enabled", () => {
    const wrapper = renderPanel();
    expect(wrapper.text()).toContain("Gerenciamento de Releases");
    expect(wrapper.text()).toContain(
      "Publique uma release global a partir de um candidato validado",
    );
    wrapper.unmount();
  });

  it("hides the card for a non-root user", () => {
    testState.root = false;
    const wrapper = renderPanel();
    expect(wrapper.text()).not.toContain("Gerenciamento de Releases");
    wrapper.unmount();
  });

  it("hides the card when local publisher mode is disabled", () => {
    testState.mode = "disabled";
    const wrapper = renderPanel();
    expect(wrapper.text()).not.toContain("Gerenciamento de Releases");
    wrapper.unmount();
  });

  it("shows the deployer card only after exact capability validation", async () => {
    testState.mode = "disabled";
    testState.deployerMode = "deployer";
    testState.deployerCapability = true;
    const wrapper = renderPanel();
    await vi.waitFor(() => expect(wrapper.text()).toContain("Atualização do sistema"));
    expect(wrapper.text()).toContain("Atualização do sistema");
    expect(wrapper.text()).not.toContain("Gerenciamento de Releases");
    wrapper.unmount();
  });
});
