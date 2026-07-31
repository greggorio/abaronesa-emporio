import { useEffect, useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { useNavigate } from 'react-router-dom';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import { Bell, Loader2, Package, Clock, MapPin, ExternalLink } from 'lucide-react';
import { useTheme } from '@/contexts/ThemeContext';
import { formatCurrency } from '@/utils/format';
import { eventoService } from '@/services/eventoService';
import { Card, CardContent } from '@/components/ui/card';

// Subcomponents
import ClienteKPIs from '@/components/cliente/ClienteKPIs';
import GamificacaoBanner from '@/components/cliente/GamificacaoBanner';
import FavoritosList from '@/components/cliente/FavoritosList';
import MenuAction from '@/components/cliente/MenuAction';
import apiClient from '@/lib/api-client';

// Interfaces
interface ContaReceberDTO {
  valorPendente: number;
  quitada: boolean;
}

interface MinhasContasResponse {
  clienteNome: string;
  clienteDesde: string;
  totalVisitasTrimestre?: number;
  resumo: {
    totalAberto: number;
  };
  faturasAbertas: ContaReceberDTO[];
  historico: ContaReceberDTO[];
}

interface ExtratoGamificacao {
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
  extrato: ExtratoGamificacao[];
}

interface ProdutoFrequenteDTO {
  produtoId: number;
  nome: string;
  imagem: string;
  quantidadeTotal: number;
}

interface SessaoAtivaResponse {
  ativa: boolean;
  mesaSlug?: string;
  mesaRotulo?: string;
  totalConsumido?: number;
}

interface EventoResumo {
  id: number;
  titulo: string;
  descricao?: string;
  dataEvento: string;
  gratuito: boolean;
  imagemUrl?: string;
  status: string;
}

interface DeliveryStatus {
  id: number;
  externalId?: string;
  deliveryId?: string;
  status?: string;
  trackingUrl?: string;
  dropoffEta?: string;
  dropoffAddress?: string;
  updatedAt?: string;
}

export default function AreaCliente() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { theme } = useTheme();
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text))]' : 'text-foreground';
  const mesaTextMuted = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text)/0.65)]' : 'text-foreground/65';
  const [loading, setLoading] = useState(true);
  const [dados, setDados] = useState<MinhasContasResponse | null>(null);
  const [favoritos, setFavoritos] = useState<ProdutoFrequenteDTO[]>([]);
  const [sessaoAtiva, setSessaoAtiva] = useState<SessaoAtivaResponse | null>(null);
  const [gamificacao, setGamificacao] = useState<GamificacaoResponse | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const [rewardsTotal, setRewardsTotal] = useState(0);
  const [rewardsAvailable, setRewardsAvailable] = useState(0);
  const [eventos, setEventos] = useState<EventoResumo[]>([]);
  const [eventoDestaque, setEventoDestaque] = useState<EventoResumo | null>(null);
  const [eventosLoading, setEventosLoading] = useState(true);
  const [deliveryStatus, setDeliveryStatus] = useState<DeliveryStatus | null>(null);
  const [deliveryLoading, setDeliveryLoading] = useState(true);

  useEffect(() => {
    const init = async () => {
      try {
        await Promise.all([
          loadFinanceiro(),
          loadFavoritos(),
          loadSessaoAtiva(),
          loadRewardsSummary(),
          loadUnreadNotifications(),
          loadDeliveryStatus()
        ]);
        await loadEventos();
      } finally {
        setLoading(false);
      }
      loadGamificacao();
    };
    init();
  }, []);

  const loadFinanceiro = async () => {
    try {
      const res = await axios.get<MinhasContasResponse>(`${apiConfig.erpBaseUrl}/api/contas-receber/me`);
      setDados(res.data);
    } catch (error) {
      console.error('Erro ao carregar contas:', error);
    }
  };

  const loadDeliveryStatus = async () => {
    try {
      setDeliveryLoading(true);
      const { data } = await axios.get<DeliveryStatus>(`${apiConfig.erpBaseUrl}/api/delivery/orders/my/active`);
      setDeliveryStatus(data);
      if (data?.id) {
        try {
          localStorage.setItem('last_delivery_order_id', `${data.id}`);
        } catch {}
      }
    } catch (error) {
      // Fallback: tenta recuperar pelo último pedido salvo localmente
      try {
        const stored = localStorage.getItem('last_delivery_order_id');
        if (stored) {
          const { data } = await axios.get<DeliveryStatus>(`${apiConfig.erp.deliveryOrders}/${stored}`);
          setDeliveryStatus(data);
        } else {
          setDeliveryStatus(null);
        }
      } catch (err) {
        console.error('Erro ao carregar delivery ativo:', error || err);
        setDeliveryStatus(null);
      }
    } finally {
      setDeliveryLoading(false);
    }
  };

  const loadFavoritos = async () => {
     try {
       const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/pedidos/me/favoritos`);
       setFavoritos(data);
     } catch (e) { console.error('Erro ao carregar favoritos', e); }
  };

  const loadSessaoAtiva = async () => {
     try {
       const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/mesas/me/ativa`);
       setSessaoAtiva(data);
     } catch (e) { console.error('Erro ao carregar sessão ativa', e); }
  };

  const loadGamificacao = async () => {
    try {
      const res = await axios.get<GamificacaoResponse>(`${apiConfig.erpBaseUrl}/api/clientes/me/gamificacao`);
      setGamificacao(res.data);
    } catch (error) {
      console.error('Erro ao carregar gamificação:', error);
    }
  };

  const loadUnreadNotifications = async () => {
    try {
      const { data } = await apiClient.get<{ unreadCount: number }>('/api/notifications/my/unread-count');
      setUnreadCount(data.unreadCount);
    } catch (error) {
      console.error('Erro ao carregar notificações:', error);
    }
  };

  const loadRewardsSummary = async () => {
    try {
      const { data } = await apiClient.get<Array<{ status: string }>>('/api/rewards/my');
      setRewardsTotal(data.length);
      setRewardsAvailable(data.filter((reward) => reward.status === 'AVAILABLE').length);
    } catch (error) {
      console.error('Erro ao carregar recompensas:', error);
    }
  };

  const loadEventos = async () => {
    try {
      setEventosLoading(true);
      const data = await eventoService.listarProximosEventos();
      const ordenados = [...data].sort(
        (a, b) => new Date(a.dataEvento).getTime() - new Date(b.dataEvento).getTime()
      );
      setEventos(ordenados);
      setEventoDestaque(ordenados[0] || null);
    } catch (error) {
      console.error('Erro ao carregar eventos:', error);
    } finally {
      setEventosLoading(false);
    }
  };

  const normalizeDeliveryStatus = (value?: string) => {
    if (!value) return { label: 'Em andamento', badge: 'bg-amber-50 text-amber-700 border-amber-200' };
    const status = value.toLowerCase();
    const map: Record<string, { label: string; badge: string }> = {
      pending: { label: 'Pendente', badge: 'bg-amber-50 text-amber-700 border-amber-200' },
      accepted: { label: 'Aceito', badge: 'bg-blue-50 text-blue-700 border-blue-200' },
      courier_imminent: { label: 'A caminho', badge: 'bg-blue-50 text-blue-700 border-blue-200' },
      picking_up: { label: 'Retirando', badge: 'bg-blue-50 text-blue-700 border-blue-200' },
      on_the_way: { label: 'A caminho', badge: 'bg-blue-50 text-blue-700 border-blue-200' },
      delivered: { label: 'Entregue', badge: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
      canceled: { label: 'Cancelado', badge: 'bg-red-50 text-red-700 border-red-200' },
      failed: { label: 'Falhou', badge: 'bg-red-50 text-red-700 border-red-200' },
      ready: { label: 'Pronto', badge: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
      dispatched: { label: 'Despachado', badge: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
    };
    return map[status] || { label: value, badge: 'bg-amber-50 text-amber-700 border-amber-200' };
  };

  const formatEta = (value?: string) => {
    if (!value) return null;
    try {
      const date = new Date(value);
      return date.toLocaleString('pt-BR', {
        hour: '2-digit',
        minute: '2-digit',
        day: '2-digit',
        month: '2-digit'
      });
    } catch {
      return value;
    }
  };

  const formatUpdated = (value?: string) => {
    if (!value) return null;
    try {
      const date = new Date(value);
      return date.toLocaleString('pt-BR', {
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return value;
    }
  };

  if (loading) {
    return (
      <div className={`min-h-screen bg-soft-white flex flex-col ${mesaTextColor}`}>
        <Header />
        <div className={`flex-1 flex items-center justify-center ${mesaTextMuted}`}>
          <Loader2 className="w-8 h-8 animate-spin" />
        </div>
      </div>
    );
  }

  const totalAberto = dados?.resumo.totalAberto || 0;

  return (
    <div className={`min-h-screen bg-soft-white flex flex-col ${mesaTextColor}`}>
      <Header />

      <main className="flex-1 container mx-auto px-4 py-8 md:py-12 max-w-lg space-y-8">
        
        {/* Header da Área */}
        <div className="flex items-center justify-between">
          <div>
          <h1 className={`text-2xl font-display mb-1 ${mesaTextColor}`}>
            Olá, {dados?.clienteNome?.split(' ')[0] || user?.nome?.split(' ')[0]}
          </h1>
          <p className={`text-sm ${mesaTextMuted}`}>
            Bem-vindo à sua área exclusiva
          </p>
          </div>
          <button
            onClick={() => navigate('/areacliente/notificacoes')}
            className="relative inline-flex items-center justify-center h-10 w-10 rounded-full border border-[hsl(var(--accent)/0.25)] bg-white text-[hsl(var(--accent))] shadow-sm"
          >
            <Bell className="h-5 w-5" />
            {unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 h-5 min-w-[20px] px-1 rounded-full bg-red-500 text-white text-[11px] flex items-center justify-center">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>
        </div>

        {/* KPIs (Grid) - sem consumo atual */}
        <ClienteKPIs
          pontos={gamificacao?.saldo ?? 0}
          totalVisitas={dados?.totalVisitasTrimestre}
          eventosTotal={eventos.length}
          proximoEventoTitulo={eventoDestaque?.titulo}
          proximoEventoData={eventoDestaque?.dataEvento}
          onEventosClick={() => navigate('/areacliente/eventos')}
          onGamificacaoClick={() => navigate('/areacliente/gamificacao')}
          onRewardsClick={() => navigate('/areacliente/recompensas')}
          rewardsTotal={rewardsTotal}
          rewardsAvailable={rewardsAvailable}
        />

        {/* Delivery */}
        {deliveryLoading ? (
          <Card className="border border-[hsl(var(--accent)/0.15)] bg-white shadow-sm">
            <CardContent className="p-5 text-sm text-mesa-text/70 flex items-center gap-2">
              <Loader2 className="w-4 h-4 animate-spin" />
              Buscando seu delivery em andamento...
            </CardContent>
          </Card>
        ) : deliveryStatus ? (
          <Card className="border border-[hsl(var(--accent)/0.2)] bg-white shadow-lg">
            <CardContent className="p-5 flex flex-col gap-3 text-mesa-text">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                  <div className="p-2 rounded-lg bg-[hsl(var(--accent)/0.08)] text-[hsl(var(--accent))]">
                    <Package className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-xs uppercase tracking-widest text-mesa-text/60">Delivery em andamento</p>
                    <p className="text-lg font-semibold text-mesa-text">
                      Pedido #{deliveryStatus.id ?? deliveryStatus.externalId}
                    </p>
                    {deliveryStatus.deliveryId && (
                      <p className="text-xs text-mesa-text/60">Delivery ID: {deliveryStatus.deliveryId}</p>
                    )}
                    {deliveryStatus.updatedAt && (
                      <p className="text-xs text-mesa-text/60">Atualizado: {formatUpdated(deliveryStatus.updatedAt)}</p>
                    )}
                  </div>
                </div>
                {(() => {
                  const statusInfo = normalizeDeliveryStatus(deliveryStatus.status);
                  return (
                    <span className={`text-xs font-semibold px-3 py-1 rounded-full border ${statusInfo.badge}`}>
                      {statusInfo.label}
                    </span>
                  );
                })()}
              </div>

              {deliveryStatus.dropoffEta && (
                <div className="flex items-center gap-2 text-sm text-mesa-text/80">
                  <Clock className="w-4 h-4" />
                  <span>Entrega estimada: {formatEta(deliveryStatus.dropoffEta)}</span>
                </div>
              )}

              {deliveryStatus.dropoffAddress && (
                <div className="flex items-center gap-2 text-sm text-mesa-text/70">
                  <MapPin className="w-4 h-4" />
                  <span className="line-clamp-2">{deliveryStatus.dropoffAddress}</span>
                </div>
              )}

              <div className="flex flex-wrap items-center gap-2">
                {(() => {
                  const deliveryPath = deliveryStatus.id ?? deliveryStatus.externalId;
                  return deliveryPath ? (
                    <button
                      onClick={() => navigate(`/areacliente/delivery/${deliveryPath}`)}
                      className="rounded-full border border-[hsl(var(--accent)/0.4)] bg-[hsl(var(--accent))] px-5 py-1.5 text-xs font-semibold uppercase tracking-[0.2em] text-white shadow-sm hover:shadow-md transition"
                    >
                      Acompanhe seu pedido
                    </button>
                  ) : null;
                })()}
                {deliveryStatus.trackingUrl && (
                  <a
                    href={deliveryStatus.trackingUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-sm font-semibold text-[hsl(var(--accent))] hover:underline"
                  >
                    <ExternalLink className="w-4 h-4" />
                    Abrir rastreamento
                  </a>
                )}
              </div>
            </CardContent>
          </Card>
        ) : (
          <Card className="border border-[hsl(var(--accent)/0.2)] bg-gradient-to-br from-white to-[hsl(var(--accent)/0.03)] shadow-lg">
            <CardContent className="p-5 flex flex-col justify-center gap-3 text-mesa-text">
              <div className="flex items-center gap-3">
                <span className="text-base">🛍️</span>
                <h3 className="text-lg font-bold leading-tight text-mesa-text uppercase tracking-[0.2em]">
                  DELIVERY
                </h3>
              </div>

              <div className="space-y-2">
                <p className="text-lg font-semibold text-foreground">Peça agora e receba em casa ☕</p>
                <p className="text-sm text-foreground/70 leading-relaxed">
                  O sabor do Empório A Baronesa sem sair do conforto do lar.
                </p>
              </div>

              <div className="flex justify-center">
                <button
                  onClick={() => navigate('/delivery-menu')}
                  className="rounded-full border border-[hsl(var(--accent)/0.4)] bg-[hsl(var(--accent))] px-6 py-1.5 text-xs font-semibold uppercase tracking-[0.3em] text-white shadow-[0_8px_20px_rgba(0,0,0,0.15)] transition hover:shadow-[0_10px_25px_rgba(0,0,0,0.25)]"
                >
                  Ver cardápio
                </button>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Ação Principal: Voltar ao Cardápio */}
        <MenuAction mesaSlug={sessaoAtiva?.mesaSlug} />

        {/* Gamificação */}
        <GamificacaoBanner pontos={gamificacao?.saldo ?? 0} />

        {/* Favoritos (Lista Horizontal) */}
        <FavoritosList favoritos={favoritos} />

        {/* Financeiro (mensalistas) */}
        {totalAberto > 0 && (
          <div className="bg-white border border-[hsl(var(--accent)/0.15)] rounded-xl p-4 shadow-sm space-y-1">
            <p className={`text-xs uppercase tracking-widest ${mesaTextMuted}`}>Financeiro</p>
            <div className="flex items-center justify-between">
              <div>
                <p className={`text-sm ${mesaTextMuted}`}>Fatura mensal (mensalistas)</p>
                <p className={`text-lg font-semibold ${mesaTextColor}`}>{formatCurrency(totalAberto * 100)}</p>
              </div>
              <button
                onClick={() => navigate('/areacliente/financeiro')}
                className="text-sm font-semibold text-[hsl(var(--accent))] hover:underline"
              >
                Ver detalhes
              </button>
            </div>
          </div>
        )}

      </main>
      <Footer />
    </div>
  );
}
