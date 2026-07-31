import { useEffect, useState, useMemo, useRef } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Loader2, RotateCcw, Trophy, Users, Calendar, Search, X } from 'lucide-react';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';

interface Cliente {
  value: number;
  label: string;
  telefone?: string | null;
  email: string;
}

interface Recompensa {
  id: number;
  nome: string;
  descricao: string;
  pontosNecessarios: number;
  podeResgatar: boolean;
}

interface ClienteGamificacaoData {
  saldo: number;
  recompensas: Recompensa[];
}

interface GamificacaoDashboardData {
  kpis: {
    recompensasAtivas: number;
    participantesComPontos: number;
    adesoesUltimos30Dias: number;
    pontosEmitidosUltimos30Dias: number;
    saldoTotalAtivo: number;
    pontosResgatadosUltimos30Dias: number;
    taxaResgate: number;
  };
  rankings: {
    topPontuadoresUltimos30Dias: {
      clienteId: number;
      nome: string;
      pontos: number
    }[];
    topSaldosAtuais: {
      clienteId: number;
      nome: string;
      saldo: number
    }[];
    ultimosResgates: {
      clienteId: number;
      clienteNome: string;
      recompensaId: number;
      recompensaNome: string;
      pontos: number;
      dataHora: string;
    }[];
    topRecompensasResgatadasUltimos30Dias: {
      recompensaId: number;
      nome: string;
      totalResgates: number;
      pontosTotalResgatado: number;
    }[];
  };
}

const normalizeToSearch = (value?: string) =>
  (value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();

export default function GamificacaoAdmin() {
  const [dashboardData, setDashboardData] = useState<GamificacaoDashboardData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [selectedCliente, setSelectedCliente] = useState<Cliente | null>(null);
  const [clienteGamificacaoData, setClienteGamificacaoData] = useState<ClienteGamificacaoData | null>(null);
  const [recompensasEligiveis, setRecompensasEligiveis] = useState<Recompensa[]>([]);
  const [selectedRecompensa, setSelectedRecompensa] = useState<number | null>(null);
  const [resgateLoading, setResgateLoading] = useState(false);
  const [clienteSearch, setClienteSearch] = useState('');
  const [clienteSelectOpen, setClienteSelectOpen] = useState(false);
  const clienteSearchInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    console.log('GamificacaoAdmin component loaded');
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    setLoading(true);
    setError(null);

    try {
      console.log('Chamando API:', `${apiConfig.erpBaseUrl}/api/admin/gamificacao/dashboard`);
      const response = await axios.get<GamificacaoDashboardData>(`${apiConfig.erpBaseUrl}/api/admin/gamificacao/dashboard`);
      console.log('Resposta da API:', response.data);
      setDashboardData(response.data);
    } catch (err: any) {
      console.error('Erro ao carregar dados do dashboard de gamificação:', err);
      let errorMessage = 'Erro ao carregar dados do dashboard de gamificação.';

      if (err.response?.status === 401 || err.response?.status === 403) {
        errorMessage = 'Acesso não autorizado. Verifique se você tem permissões de administrador.';
      } else if (err.response?.status === 404) {
        errorMessage = 'Endpoint não encontrado. Entre em contato com o suporte.';
      } else {
        errorMessage = err.message || errorMessage;
      }

      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  // Carregar opções de clientes na inicialização
  useEffect(() => {
    const loadClienteOptions = async () => {
      try {
        const response = await axios.get<Cliente[]>(`${apiConfig.erpBaseUrl}/api/admin/gamificacao/clientes/com-pontos`);
        setClientes(response.data || []);
      } catch (err) {
        console.error('Erro ao carregar opções de clientes:', err);
        setClientes([]);
      }
    };

    loadClienteOptions();
  }, []);

  // Filtro local de clientes baseado na busca
  const filteredClientes = useMemo(() => {
    const term = normalizeToSearch(clienteSearch);
    if (!term) return clientes;
    return clientes.filter((c) => {
      const nome = normalizeToSearch(c.label);
      const email = normalizeToSearch(c.email);
      return nome.includes(term) || email.includes(term);
    });
  }, [clientes, clienteSearch]);

  // Foco automático no input de busca ao abrir o select
  useEffect(() => {
    if (clienteSelectOpen) {
      setTimeout(() => {
        clienteSearchInputRef.current?.focus();
      }, 0);
    } else {
      setClienteSearch('');
    }
  }, [clienteSelectOpen]);

  const handleClienteSelect = async (cliente: Cliente) => {
    setSelectedCliente(cliente);

    try {
      // O endpoint agora retorna a estrutura completa: { recompensas: [...], saldoCliente: number }
      const response = await axios.get<{ recompensas: Recompensa[], saldoCliente: number }>(
        `${apiConfig.erpBaseUrl}/api/admin/clientes/${cliente.value}/gamificacao/recompensas`
      );

      // Mapeia a resposta para o formato esperado pela interface ClienteGamificacaoData
      const recompensasData: ClienteGamificacaoData = {
        saldo: response.data.saldoCliente,
        recompensas: response.data.recompensas
      };

      setClienteGamificacaoData(recompensasData);
      setRecompensasEligiveis(response.data.recompensas.filter(r => r.podeResgatar));
      setSelectedRecompensa(null);
    } catch (err) {
      console.error('Erro ao carregar dados de gamificação do cliente:', err);
      setClienteGamificacaoData(null);
      setRecompensasEligiveis([]);
      setError('Erro ao carregar recompensas do cliente.');
    }
  };

  const handleResgate = async () => {
    if (!selectedCliente || selectedRecompensa === null) return;

    setResgateLoading(true);
    try {
      await axios.post(`${apiConfig.erpBaseUrl}/api/admin/gamificacao/resgates`, {
        clienteId: selectedCliente.value,
        recompensaId: selectedRecompensa
      });

      // Atualizar dados
      await loadDashboardData();
      if (selectedCliente) {
        await handleClienteSelect(selectedCliente);
      }

      setError(null);
      alert('Resgate realizado com sucesso!');
    } catch (err: any) {
      console.error('Erro ao resgatar recompensa:', err);
      let errorMessage = 'Erro ao realizar resgate.';

      if (err.response?.status === 401 || err.response?.status === 403) {
        errorMessage = 'Acesso não autorizado.';
      } else if (err.response?.status === 400) {
        errorMessage = err.response.data?.message || 'Dados inválidos.';
      }

      setError(errorMessage);
    } finally {
      setResgateLoading(false);
    }
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-3xl font-bold text-[#2A1F1B]">Dashboard de Gamificação</h1> {/* cafe-dark-roast */}
          <p className="text-[#2A1F1B]/70">Gestão e visualização de recompensas e pontos</p> {/* cafe-dark-roast */}
        </div>
        <Button
          variant="outline"
          onClick={loadDashboardData}
          disabled={loading}
        >
          {loading ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Atualizando...
            </>
          ) : (
            <>
              <RotateCcw className="mr-2 h-4 w-4" />
              Atualizar dados
            </>
          )}
        </Button>
      </div>

      {error && (
        <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
          <p className="text-red-700">{error}</p>
        </div>
      )}

      {dashboardData && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-6 mb-8">
          {/* KPI Cards - usando a estrutura semelhante ao código anterior */}
          <div className="bg-gradient-to-br from-[#2A1F1B] to-[#D7B899] text-white p-6 rounded-lg shadow-lg"> {/* cafe-dark-roast to cafe-latte-suave */}
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-white/80">Recompensas Ativas</p>
                <p className="text-3xl font-bold">{dashboardData.kpis.recompensasAtivas}</p>
              </div>
              <div className="w-10 h-10 bg-amber-300 rounded-full flex items-center justify-center">
                <Trophy className="w-5 h-5 text-[#2A1F1B]" /> {/* cafe-dark-roast */}
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow-sm border border-[#8B7355]/20"> {/* cafe-com-leite */}
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-[#2A1F1B]/60">Participantes com Pontos</p> {/* cafe-dark-roast */}
                <p className="text-3xl font-bold text-[#2A1F1B]">{dashboardData.kpis.participantesComPontos}</p> {/* cafe-dark-roast */}
              </div>
              <div className="w-10 h-10 bg-[#8B7355] rounded-full flex items-center justify-center"> {/* cafe-com-leite */}
                <Users className="w-5 h-5 text-white" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow-sm border border-[#8B7355]/20"> {/* cafe-com-leite */}
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-[#2A1F1B]/60">Adesões (30 dias)</p> {/* cafe-dark-roast */}
                <p className="text-3xl font-bold text-[#2A1F1B]">{dashboardData.kpis.adesoesUltimos30Dias}</p> {/* cafe-dark-roast */}
              </div>
              <div className="w-10 h-10 bg-[#D7B899] rounded-full flex items-center justify-center"> {/* cafe-latte-suave */}
                <Calendar className="w-5 h-5 text-white" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow-sm border border-[#8B7355]/20"> {/* cafe-com-leite */}
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-[#2A1F1B]/60">Pontos Emitidos (30 dias)</p> {/* cafe-dark-roast */}
                <p className="text-3xl font-bold text-[#2A1F1B]">{dashboardData.kpis.pontosEmitidosUltimos30Dias}</p> {/* cafe-dark-roast */}
              </div>
              <div className="w-10 h-10 bg-[#B5854C] rounded-full flex items-center justify-center"> {/* cafe-bronze-dourado */}
                <Trophy className="w-5 h-5 text-white" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow-sm border border-[#8B7355]/20"> {/* cafe-com-leite */}
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-[#2A1F1B]/60">Saldo Total Ativo</p> {/* cafe-dark-roast */}
                <p className="text-3xl font-bold text-[#2A1F1B]">{dashboardData.kpis.saldoTotalAtivo}</p> {/* cafe-dark-roast */}
              </div>
              <div className="w-10 h-10 bg-[#C67C48] rounded-full flex items-center justify-center"> {/* cafe-caramelo */}
                <Trophy className="w-5 h-5 text-white" />
              </div>
            </div>
          </div>
        </div>
      )}

      {dashboardData && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
          <div className="bg-white p-6 rounded-lg shadow-sm border border-[#8B7355]/20"> {/* cafe-com-leite */}
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-[#2A1F1B]/60">Pontos Resgatados (30 dias)</p> {/* cafe-dark-roast */}
                <p className="text-3xl font-bold text-[#2A1F1B]">{dashboardData.kpis.pontosResgatadosUltimos30Dias}</p> {/* cafe-dark-roast */}
              </div>
              <div className="w-10 h-10 bg-[#C67C48] rounded-full flex items-center justify-center"> {/* cafe-caramelo */}
                <Trophy className="w-5 h-5 text-white" />
              </div>
            </div>
          </div>

          {/* Taxa de Resgate */}
          <div className="bg-white p-6 rounded-lg shadow-sm border border-[#8B7355]/20"> {/* cafe-com-leite */}
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-[#2A1F1B]/60">Taxa de Resgate</p> {/* cafe-dark-roast */}
                <p className="text-3xl font-bold text-[#2A1F1B]">{(dashboardData.kpis.taxaResgate * 100).toFixed(1)}%</p> {/* cafe-dark-roast */}
              </div>
              <div className="w-10 h-10 bg-[#B5854C] rounded-full flex items-center justify-center"> {/* cafe-bronze-dourado */}
                <Trophy className="w-5 h-5 text-white" />
              </div>
            </div>
          </div>
        </div>
      )}

      {dashboardData && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
          {/* Top Pontuadores */}
          <div className="border border-[#8B7355]/20 bg-white rounded-lg shadow-sm"> {/* cafe-com-leite */}
            <div className="p-4 border-b border-[#8B7355]/10"> {/* cafe-com-leite */}
              <h2 className="text-lg font-semibold text-[#2A1F1B]">Top Pontuadores (30 dias)</h2> {/* cafe-dark-roast */}
            </div>
            <div className="p-4">
              {dashboardData.rankings.topPontuadoresUltimos30Dias.length > 0 ? (
                <div className="space-y-2">
                  {dashboardData.rankings.topPontuadoresUltimos30Dias.map((pontuador, index) => (
                    <div key={pontuador.clienteId} className="flex items-center justify-between p-2 border-b border-[#8B7355]/10 last:border-b-0"> {/* cafe-com-leite */}
                      <div>
                        <span className="font-medium text-[#2A1F1B]">#{index + 1} {pontuador.nome}</span> {/* cafe-dark-roast */}
                      </div>
                      <div className="bg-[#2A1F1B] text-white px-2 py-1 rounded text-sm"> {/* cafe-dark-roast */}
                        {pontuador.pontos} pts
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-4 text-[#2A1F1B]/60"> {/* cafe-dark-roast */}
                  Nenhum dado disponível
                </div>
              )}
            </div>
          </div>

          {/* Top Saldos Atuais */}
          <div className="border border-[#8B7355]/20 bg-white rounded-lg shadow-sm"> {/* cafe-com-leite */}
            <div className="p-4 border-b border-[#8B7355]/10"> {/* cafe-com-leite */}
              <h2 className="text-lg font-semibold text-[#2A1F1B]">Top Saldos Atuais</h2> {/* cafe-dark-roast */}
            </div>
            <div className="p-4">
              {dashboardData.rankings.topSaldosAtuais.length > 0 ? (
                <div className="space-y-2">
                  {dashboardData.rankings.topSaldosAtuais.map((saldo, index) => (
                    <div key={saldo.clienteId} className="flex items-center justify-between p-2 border-b border-[#8B7355]/10 last:border-b-0"> {/* cafe-com-leite */}
                      <div>
                        <span className="font-medium text-[#2A1F1B]">#{index + 1} {saldo.nome}</span> {/* cafe-dark-roast */}
                      </div>
                      <div className="bg-[#2A1F1B] text-white px-2 py-1 rounded text-sm"> {/* cafe-dark-roast */}
                        {saldo.saldo} pts
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-4 text-[#2A1F1B]/60"> {/* cafe-dark-roast */}
                  Nenhum dado disponível
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {dashboardData && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
          {/* Útimos Resgates */}
          <div className="border border-[#8B7355]/20 bg-white rounded-lg shadow-sm"> {/* cafe-com-leite */}
            <div className="p-4 border-b border-[#8B7355]/10"> {/* cafe-com-leite */}
              <h2 className="text-lg font-semibold text-[#2A1F1B]">Últimos Resgates</h2> {/* cafe-dark-roast */}
            </div>
            <div className="p-4">
              {dashboardData.rankings.ultimosResgates.length > 0 ? (
                <div className="space-y-2">
                  {dashboardData.rankings.ultimosResgates.slice(0, 5).map((resgate) => (
                    <div key={`${resgate.clienteId}-${resgate.recompensaId}-${resgate.dataHora}`} className="flex items-center justify-between p-2 border-b border-[#8B7355]/10 last:border-b-0"> {/* cafe-com-leite */}
                      <div>
                        <span className="font-medium text-[#2A1F1B]">{resgate.clienteNome}</span> {/* cafe-dark-roast */}
                        <p className="text-xs text-[#2A1F1B]/70">{resgate.recompensaNome}</p> {/* cafe-dark-roast */}
                      </div>
                      <div className="text-right">
                        <div className="text-sm text-[#2A1F1B]">{resgate.pontos > 0 ? `+${resgate.pontos}` : resgate.pontos} pts</div> {/* cafe-dark-roast */}
                        <div className="text-xs text-[#2A1F1B]/70">{new Date(resgate.dataHora).toLocaleDateString('pt-BR')}</div> {/* cafe-dark-roast */}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-4 text-[#2A1F1B]/60"> {/* cafe-dark-roast */}
                  Nenhum resgate realizado
                </div>
              )}
            </div>
          </div>

          {/* Top Recompensas Resgatadas */}
          <div className="border border-[#8B7355]/20 bg-white rounded-lg shadow-sm"> {/* cafe-com-leite */}
            <div className="p-4 border-b border-[#8B7355]/10"> {/* cafe-com-leite */}
              <h2 className="text-lg font-semibold text-[#2A1F1B]">Top Recompensas Resgatadas (30 dias)</h2> {/* cafe-dark-roast */}
            </div>
            <div className="p-4">
              {dashboardData.rankings.topRecompensasResgatadasUltimos30Dias.length > 0 ? (
                <div className="space-y-2">
                  {dashboardData.rankings.topRecompensasResgatadasUltimos30Dias.map((recompensa) => (
                    <div key={recompensa.recompensaId} className="flex items-center justify-between p-2 border-b border-[#8B7355]/10 last:border-b-0"> {/* cafe-com-leite */}
                      <div>
                        <span className="font-medium text-[#2A1F1B]">{recompensa.nome}</span> {/* cafe-dark-roast */}
                      </div>
                      <div className="text-right">
                        <div className="text-sm text-[#2A1F1B]">{recompensa.totalResgates} resgates</div> {/* cafe-dark-roast */}
                        <div className="text-xs text-[#2A1F1B]/70">{recompensa.pontosTotalResgatado} pts totais</div> {/* cafe-dark-roast */}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-4 text-[#2A1F1B]/60"> {/* cafe-dark-roast */}
                  Nenhuma recompensa resgatada
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Seção de Resgate de Recompensas */}
      <div className="border border-[#8B7355]/20 bg-white rounded-lg shadow-sm"> {/* cafe-com-leite */}
        <div className="p-4 border-b border-[#8B7355]/10"> {/* cafe-com-leite */}
          <h2 className="text-lg font-semibold text-[#2A1F1B]">Resgatar Recompensa</h2> {/* cafe-dark-roast */}
        </div>
        <div className="p-6">
          <p className="text-[#2A1F1B]/70 mb-4">Selecione um cliente para resgatar uma recompensa em seu nome.</p> {/* cafe-dark-roast */}

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-[#2A1F1B] mb-1">Cliente</label> {/* cafe-dark-roast */}
              <Select
                value={selectedCliente?.value ? String(selectedCliente.value) : 'none'}
                onValueChange={(v) => {
                  if (v === 'none') {
                    setSelectedCliente(null);
                    setClienteGamificacaoData(null);
                    setRecompensasEligiveis([]);
                  } else {
                    const cliente = clientes.find(c => c.value === Number(v));
                    if (cliente) handleClienteSelect(cliente);
                  }
                }}
                onOpenChange={(open) => setClienteSelectOpen(open)}
              >
                <SelectTrigger className="w-full h-10 bg-white border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899]">
                  <SelectValue placeholder="Selecione um cliente" />
                </SelectTrigger>
                <SelectContent className="z-[10000] bg-[#FBF6F2] border-[#8B7355]/30 text-[#2A1F1B] max-h-80">
                  <div className="sticky top-0 z-10 bg-[#FBF6F2] px-3 pt-3 pb-1">
                    <div className="relative">
                      <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3 w-3 text-muted-foreground" />
                      <Input
                        ref={clienteSearchInputRef}
                        value={clienteSearch}
                        onChange={(event) => setClienteSearch(event.target.value)}
                        placeholder="Buscar cliente"
                        className="h-8 pl-8 pr-8 text-xs bg-white border-[#D7B899]/30"
                        autoComplete="off"
                        onKeyDown={(event) => {
                          const { key } = event;
                          if (key.length === 1 || key === 'Backspace' || key === 'Delete') {
                            event.stopPropagation();
                          }
                        }}
                      />
                      {clienteSearch && (
                        <button
                          type="button"
                          onClick={() => {
                            setClienteSearch('');
                            clienteSearchInputRef.current?.focus();
                          }}
                          className="absolute right-2 top-1/2 -translate-y-1/2 rounded-full bg-black/5 p-0.5 text-muted-foreground transition hover:bg-black/10"
                        >
                          <X className="h-2 w-2" />
                        </button>
                      )}
                    </div>
                  </div>
                  <SelectItem className="focus:bg-[#D7B899]/30 focus:text-[#2A1F1B]" value="none">
                    Selecione um cliente
                  </SelectItem>
                  {filteredClientes.map((cliente) => (
                    <SelectItem
                      className="focus:bg-[#D7B899]/30 focus:text-[#2A1F1B]"
                      key={cliente.value}
                      value={String(cliente.value)}
                    >
                      <div className="flex flex-col">
                        <span className="font-medium">{cliente.label}</span>
                        <span className="text-xs text-muted-foreground">{cliente.email}</span>
                      </div>
                    </SelectItem>
                  ))}
                  {filteredClientes.length === 0 && (
                    <div className="px-3 py-2 text-xs text-muted-foreground text-center">
                      Nenhum cliente encontrado
                    </div>
                  )}
                </SelectContent>
              </Select>
            </div>

            {selectedCliente && clienteGamificacaoData && (
              <div className="p-4 bg-[#FBF6F2] border border-[#8B7355]/10 rounded-lg"> {/* cafe-latte-claro and cafe-com-leite */}
                <h3 className="font-medium text-[#2A1F1B] mb-2">Cliente Selecionado</h3> {/* cafe-dark-roast */}
                <div className="space-y-1 text-sm">
                  <p><span className="text-[#2A1F1B]/70">Nome:</span> <span className="text-[#2A1F1B]">{selectedCliente.label}</span></p> {/* cafe-dark-roast */}
                  <p><span className="text-[#2A1F1B]/70">Saldo Atual:</span> <span className="text-[#2A1F1B]">{clienteGamificacaoData.saldo} pontos</span></p> {/* cafe-dark-roast */}
                </div>
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-[#2A1F1B] mb-1">Recompensas Elegíveis</label> {/* cafe-dark-roast */}
              <select
                className="w-full p-2 border border-[#8B7355]/30 rounded-md focus:outline-none focus:ring-2 focus:ring-[#D7B899] text-[#2A1F1B] bg-white"
                value={selectedRecompensa === null ? '' : selectedRecompensa}
                onChange={(e) => setSelectedRecompensa(e.target.value ? Number(e.target.value) : null)}
                disabled={recompensasEligiveis.length === 0}
              >
                <option value="" className="text-[#2A1F1B]"> {/* cafe-dark-roast */}
                  Selecione uma recompensa
                </option>
                {recompensasEligiveis.map((recompensa) => (
                  <option key={recompensa.id} value={recompensa.id} className="text-[#2A1F1B]"> {/* cafe-dark-roast */}
                    {recompensa.nome} ({recompensa.pontosNecessarios} pts)
                  </option>
                ))}
              </select>
            </div>

            <button
              className="w-full py-2 bg-[#D7B899] text-white rounded-md hover:bg-[#D7B899]/90 disabled:opacity-50 disabled:cursor-not-allowed"
              onClick={handleResgate}
              disabled={!selectedCliente || selectedRecompensa === null || resgateLoading}
            >
              {resgateLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin inline" />
                  Processando...
                </>
              ) : (
                selectedCliente
                  ? `Confirmar Resgate - ${selectedCliente.label}`
                  : 'Selecione um cliente e recompensa'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}