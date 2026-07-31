import { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import { Loader2, ArrowLeft, Image as ImageIcon, Trophy } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Recompensa, CatalogoRecompensasResponse } from '@/types/cliente';
import { useTheme } from '@/contexts/ThemeContext';

interface ExtratoItem {
  dataHora: string;
  tipo: string;
  origem: string;
  pontos: number;
  saldoApos: number;
  referenciaTipo: string;
  referenciaId: number;
  observacao: string;
}

interface GamificacaoResponse {
  saldo: number;
  extrato: ExtratoItem[];
}

const iconePorTipo: Record<string, string> = {
  GANHO: '🏆',
  AJUSTE: '🟡',
  RESGATE: '🔴'
};

const formatDate = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return date.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
};

export default function GamificacaoExtrato() {
  const navigate = useNavigate();
  const location = useLocation();
  const { theme } = useTheme();
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text))]' : 'text-foreground';
  const mesaTextMuted = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text)/0.65)]' : 'text-foreground/65';
  const [dados, setDados] = useState<GamificacaoResponse | null>(null);
  const [recompensasData, setRecompensasData] = useState<CatalogoRecompensasResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingRecompensas, setLoadingRecompensas] = useState(true);
  const [activeTab, setActiveTab] = useState('extrato');

  useEffect(() => {
    // Check if there's a tab parameter in the URL
    const urlParams = new URLSearchParams(location.search);
    const tabFromUrl = urlParams.get('tab');
    const initialTab = tabFromUrl === 'recompensas' ? 'recompensas' : 'extrato';
    setActiveTab(initialTab);

    const load = async () => {
      try {
        const res = await axios.get<GamificacaoResponse>(`${apiConfig.erpBaseUrl}/api/clientes/me/gamificacao`);
        setDados(res.data);

        // If the initial tab is 'recompensas', load the recompensas data immediately
        if (initialTab === 'recompensas') {
          await loadRecompensas();
        }
      } catch (error) {
        console.error('Erro ao carregar gamificação:', error);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [location.search]);

  const loadRecompensas = async () => {
    try {
      // Load recompensas (the API returns an array of rewards)
      const resRecompensas = await axios.get<Recompensa[]>(`${apiConfig.erpBaseUrl}/api/clientes/me/gamificacao/recompensas`);

      // We already have the saldo from the main gamificacao API call
      if (dados) {
        const combinedData: CatalogoRecompensasResponse = {
          saldo: dados.saldo,
          recompensas: resRecompensas.data
        };
        setRecompensasData(combinedData);
      } else {
        // If dados is not loaded yet, load it first
        const resDados = await axios.get<GamificacaoResponse>(`${apiConfig.erpBaseUrl}/api/clientes/me/gamificacao`);
        const combinedData: CatalogoRecompensasResponse = {
          saldo: resDados.data.saldo,
          recompensas: resRecompensas.data
        };
        setRecompensasData(combinedData);
      }
    } catch (error) {
      console.error('Erro ao carregar recompensas:', error);
    } finally {
      setLoadingRecompensas(false);
    }
  };

  const renderRewardCard = (reward: Recompensa) => {
    return (
      <Card key={reward.id} className="border border-[hsl(var(--accent)/0.2)] bg-white shadow-sm overflow-hidden h-full flex flex-col">
        <div className="relative">
          {reward.imagemUrl ? (
            <img
              src={reward.imagemUrl}
              alt={reward.nome}
              className="w-full h-40 object-cover"
              onError={(e) => {
                const target = e.target as HTMLImageElement;
                target.onerror = null;
                target.src = '/placeholder-recompensa.jpg';
              }}
            />
          ) : (
            <div className="w-full h-40 bg-gray-100 flex items-center justify-center">
              <ImageIcon className="w-12 h-12 text-gray-400" />
            </div>
          )}
        </div>
        <CardContent className="p-4 flex flex-col flex-1">
          <div className="flex-1">
            <div className="flex justify-between items-start mb-2">
              <h3 className={`font-semibold text-sm ${mesaTextColor}`}>{reward.nome}</h3>
              <Badge variant="outline" className="text-xs ml-2 text-[hsl(var(--accent))] bg-white border-[hsl(var(--accent)/0.3)]">
                {reward.pontosNecessarios} pts
              </Badge>
            </div>
            <p className={`text-xs ${mesaTextMuted} mb-3 line-clamp-2`}>{reward.descricao}</p>
          </div>

          <div className="mt-auto">
            {reward.podeResgatar ? (
              <Badge className="w-full py-1.5 text-center bg-[hsl(var(--accent)/0.15)] text-[hsl(var(--accent))] border-[hsl(var(--accent)/0.25)]" variant="outline">
                Disponível
              </Badge>
            ) : (
              <Badge className="w-full py-1.5 text-center bg-white text-[hsl(var(--accent))] border-[hsl(var(--accent)/0.3)]" variant="outline">
                Faltam {reward.faltamPontos} pontos
              </Badge>
            )}
          </div>
        </CardContent>
      </Card>
    );
  };

  return (
    <div className={`min-h-screen bg-soft-white flex flex-col ${mesaTextColor}`}>
      <Header />
      <main className="flex-1 container mx-auto px-4 py-8 max-w-4xl space-y-6">
        <button
          onClick={() => navigate('/areacliente')}
          className={`inline-flex items-center gap-2 text-sm font-semibold ${mesaTextColor} hover:text-[hsl(var(--accent))] transition`}
        >
          <ArrowLeft className="w-4 h-4" />
          Voltar
        </button>

        <section>
          <div className="flex flex-col gap-1">
            <p className={`text-xs uppercase tracking-widest ${mesaTextMuted}`}>Gamificação</p>
            <h1 className={`text-3xl font-display ${mesaTextColor}`}>Seu histórico de pontos</h1>
          </div>
          <div className="mt-6">
            <Card className="border border-[hsl(var(--accent)/0.2)] bg-gradient-to-br from-[hsl(var(--accent)/0.12)] via-[hsl(var(--accent)/0.18)] to-[hsl(var(--accent)/0.24)] text-white shadow-lg">
              <CardContent className="p-6 flex items-center gap-4">
                <div className="w-12 h-12 shrink-0 rounded-full bg-white/30 flex items-center justify-center backdrop-blur-sm shadow-inner">
                  <Trophy className="w-6 h-6 text-mesa-text" />
                </div>
                <div className="flex items-center justify-between gap-4 flex-1">
                  <div>
                    <p className="text-sm text-mesa-text/80">Saldo atual</p>
                    <p className="text-3xl font-bold text-mesa-text">
                      {loading ? '-' : `${dados?.saldo ?? 0} pontos`}
                    </p>
                  </div>
                  <Badge className="bg-white/20 text-mesa-text px-3 py-1 text-xs rounded-full">
                    {loading ? 'Carregando...' : 'Ativo'}
                  </Badge>
                </div>
              </CardContent>
            </Card>
          </div>
        </section>

        <section className="space-y-4">
          <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
            <TabsList className="grid w-full grid-cols-2 bg-white border border-[hsl(var(--accent)/0.2)] rounded-lg">
              <TabsTrigger value="extrato" className="data-[state=active]:bg-accent data-[state=active]:text-mesa-text">Extrato</TabsTrigger>
              <TabsTrigger value="recompensas" onClick={loadRecompensas} className="data-[state=active]:bg-accent data-[state=active]:text-mesa-text">Recompensas</TabsTrigger>
            </TabsList>

            <TabsContent value="extrato" className="space-y-4">
              <div className="flex items-center justify-between gap-2">
                <div>
                  <h2 className={`text-xl font-semibold ${mesaTextColor}`}>Extrato</h2>
                  <p className={`text-sm ${mesaTextMuted}`}>Últimos lançamentos</p>
                </div>
              </div>

              {loading ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="w-6 h-6 animate-spin text-[hsl(var(--accent))]" />
                </div>
              ) : (
                <>
                  {dados?.extrato && dados.extrato.length > 0 ? (
                    <div className="space-y-3">
                      {dados.extrato.map((item) => (
                        <Card key={`${item.referenciaTipo}-${item.referenciaId}-${item.dataHora}`} className="border border-[hsl(var(--accent)/0.2)] bg-white shadow-sm">
                          <CardContent className="p-4 flex flex-col gap-2">
                            <div className="flex items-center justify-between">
                              <div className="flex items-center gap-3">
                                <span className="text-2xl">{iconePorTipo[item.tipo] ?? '⚪'}</span>
                                <div>
                                  <p className={`text-sm font-semibold ${mesaTextColor}`}>
                                    {item.tipo} • {item.origem}
                                  </p>
                                  <p className={`text-xs ${mesaTextMuted}`}>
                                    {item.observacao}
                                  </p>
                                </div>
                              </div>
                              <div className="text-right">
                                <p className={`text-sm font-semibold ${mesaTextColor}`}>
                                  {item.pontos > 0 ? `+${item.pontos}` : `${item.pontos}`} pontos
                                </p>
                                <p className={`text-xs ${mesaTextMuted}`}>Saldo: {item.saldoApos}</p>
                              </div>
                            </div>
                            <div className={`flex items-center justify-between text-[11px] ${mesaTextMuted}`}>
                              <span>{formatDate(item.dataHora)}</span>
                              <span>{item.referenciaTipo} #{item.referenciaId}</span>
                            </div>
                          </CardContent>
                        </Card>
                      ))}
                    </div>
                  ) : (
                    <Card className="border-dashed border-[hsl(var(--accent)/0.3)]">
                      <CardContent className={`py-8 text-center text-sm ${mesaTextMuted}`}>
                        Ainda não há movimentações.
                      </CardContent>
                    </Card>
                  )}
                </>
              )}
            </TabsContent>

            <TabsContent value="recompensas" className="space-y-4">
              <div className="flex items-center justify-between gap-2">
                <div>
                  <h2 className={`text-xl font-semibold ${mesaTextColor}`}>Recompensas</h2>
                  <p className={`text-sm ${mesaTextMuted}`}>Catálogo de recompensas disponíveis</p>
                </div>
              </div>

              {loadingRecompensas ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="w-6 h-6 animate-spin text-[hsl(var(--accent))]" />
                </div>
              ) : (
                <>
                  {recompensasData?.recompensas && recompensasData.recompensas.length > 0 ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                      {recompensasData.recompensas.map(renderRewardCard)}
                    </div>
                  ) : (
                    <Card className="border-dashed border-[hsl(var(--accent)/0.9)]">
                      <CardContent className="py-12 text-center text-white/80">
                        <p className={mesaTextColor}>Nenhuma recompensa disponível no momento.</p>
                      </CardContent>
                    </Card>
                  )}
                </>
              )}
            </TabsContent>
          </Tabs>
        </section>
      </main>
      <Footer />
    </div>
  );
}
