import axios from "axios";

// Mesma convencao usada em eventoService para apontar para a API do website.
declare global {
  interface Window {
    RuntimeConfig?: {
      websiteApiUrl: string;
    };
  }
}

const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;

export type AtivosResponse = {
  totalClientes: number;
  novosPeriodo: number;
  appAtivos: number;
  ativos7d: number;
  tokensOrfaos: number;
  adocaoPercentual: number;
  periodoDias: number;
};

export type ClienteAppAtivoResponse = {
  id: number;
  nome: string;
  email: string;
  telefone?: string | null;
  createdAt?: string | null;
  lastSeenAt?: string | null;
  tokensAtivos: number;
  pontos: number;
};

export type TokenOrfaoResponse = {
  token: string;
  deviceInfo: string;
  createdAt: string;
};

export type OportunidadesResponse = {
  novosComApp: ClienteAppAtivoResponse[];
  inativosApp: ClienteAppAtivoResponse[];
  tokensOrfaos: TokenOrfaoResponse[];
};

export type EnviarBrindeRequest = {
  userId: number;
  mensagem: string;
};

export type EnviarBrindeResponse = {
  status: string;
  detalhes: string;
};

export const clientesDashboardService = {
  async getAtivos(rangeDays = 30): Promise<AtivosResponse> {
    const res = await axios.get<AtivosResponse>(`${WEBSITE_API_URL}/api/clientes-dashboard/ativos`, { params: { range: rangeDays } });
    return res.data;
  },

  async getOportunidades(rangeDays = 30): Promise<OportunidadesResponse> {
    const res = await axios.get<OportunidadesResponse>(`${WEBSITE_API_URL}/api/clientes-dashboard/oportunidades`, { params: { range: rangeDays } });
    return res.data;
  },

  async getAppAtivos(): Promise<ClienteAppAtivoResponse[]> {
    const res = await axios.get<ClienteAppAtivoResponse[]>(`${WEBSITE_API_URL}/api/clientes-dashboard/app-ativos`);
    return res.data;
  },

  async enviarBrinde(payload: EnviarBrindeRequest): Promise<EnviarBrindeResponse> {
    const res = await axios.post<EnviarBrindeResponse>(`${WEBSITE_API_URL}/api/clientes-dashboard/enviar-brinde`, payload);
    return res.data;
  },
};
