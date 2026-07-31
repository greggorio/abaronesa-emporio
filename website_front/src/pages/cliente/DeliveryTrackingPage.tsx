import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { ArrowLeft, Clock, ExternalLink, MapPin, Package, ShoppingBag, Loader2 } from 'lucide-react';
import { DeliveryTrackingDetail, ServiceMode } from "@/types/delivery";

const normalizeStatus = (value?: string) => {
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

export default function DeliveryTrackingPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<DeliveryTrackingDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchDetail();
  }, [orderId]);

  const fetchDetail = async () => {
    if (!orderId) {
      setError('Pedido não encontrado.');
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      const { data } = await axios.get<DeliveryDetail>(`${apiConfig.erp.deliveryOrders}/${orderId}`);
      setDetail(data);
    } catch (err) {
      console.error('Erro ao carregar detalhes do delivery', err);
      setError('Não foi possível carregar as informações do pedido.');
    } finally {
      setLoading(false);
    }
  };

  const statusInfo = normalizeStatus(detail?.status);
  const fallbackServiceMode: ServiceMode = detail && !detail.dropoffAddress ? "PICKUP" : "DELIVERY";
  const resolvedServiceMode = detail?.serviceMode ?? fallbackServiceMode;
  const isPickup = resolvedServiceMode === "PICKUP";

  return (
    <div className="min-h-screen bg-soft-white flex flex-col">
      <Header />
      <main className="flex-1 container mx-auto px-4 py-8 max-w-3xl space-y-6">
        <button
          onClick={() => navigate('/areacliente')}
          className="inline-flex items-center gap-2 text-sm font-semibold text-foreground hover:text-[hsl(var(--accent))] transition"
        >
          <ArrowLeft className="w-4 h-4" />
          Voltar
        </button>

        <div className="space-y-2">
          <p className="text-xs uppercase tracking-widest text-foreground/60">Delivery</p>
          <div className="flex items-center gap-2">
            <h1 className="text-3xl font-display text-foreground">Acompanhe seu pedido</h1>
            <span className={`text-xs font-semibold px-3 py-1 rounded-full border ${statusInfo.badge}`}>
              {statusInfo.label}
            </span>
          </div>
          <div className="text-sm text-foreground/70">
            {detail?.id ? `Pedido #${detail.id}` : detail?.externalId ? `Pedido ${detail.externalId}` : ''}
          </div>
        </div>

        {error && (
          <Card className="border border-red-200 bg-red-50">
            <CardContent className="p-4 text-sm text-red-700">{error}</CardContent>
          </Card>
        )}

        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="w-4 h-4 animate-spin" />
            Carregando detalhes do pedido...
          </div>
        ) : detail ? (
          <>
            <Card className="border border-[hsl(var(--accent)/0.2)] bg-white shadow-sm">
              <CardContent className="p-5 space-y-3">
                <div className="flex items-start gap-3">
                  <div className="p-2 rounded-lg bg-[hsl(var(--accent)/0.1)] text-[hsl(var(--accent))]">
                    <Package className="w-5 h-5" />
                  </div>
                  <div className="space-y-1 text-sm text-foreground/80">
                    {detail.deliveryId && <div>Delivery ID: <span className="font-semibold text-foreground">{detail.deliveryId}</span></div>}
                    {detail.externalId && <div>Ref: <span className="font-semibold text-foreground">{detail.externalId}</span></div>}
                    {detail.dropoffEta && <div className="flex items-center gap-1"><Clock className="w-4 h-4" /> Entrega estimada: {formatEta(detail.dropoffEta)}</div>}
                    {detail.dropoffAddress && <div className="flex items-center gap-1"><MapPin className="w-4 h-4" /> Destino: {detail.dropoffAddress}</div>}
                {detail.pickupAddress && <div className="flex items-center gap-1"><MapPin className="w-4 h-4" /> Origem: {detail.pickupAddress}</div>}
                <div className="text-sm text-muted-foreground">
                  {isPickup
                    ? "Retirada no balcão do estabelecimento. Apresente o número do pedido ao retirar."
                    : "Acompanhe o entregador através do rastreamento oficial da Uber."}
                </div>
              </div>
            </div>

                {!isPickup && detail.trackingUrl && (
                  <Button asChild variant="outline" className="gap-2">
                    <a href={detail.trackingUrl} target="_blank" rel="noreferrer">
                      <ExternalLink className="w-4 h-4" />
                      Abrir rastreamento (Uber)
                    </a>
                  </Button>
                )}
              </CardContent>
            </Card>

            <Card className="border border-[hsl(var(--accent)/0.2)] bg-white shadow-sm">
              <CardContent className="p-5 space-y-3">
                <div className="flex items-center gap-2">
                  <ShoppingBag className="w-4 h-4 text-[hsl(var(--accent))]" />
                  <h2 className="text-lg font-semibold text-foreground">Itens do pedido</h2>
                </div>
                <Separator />
                {detail.items?.length ? (
                  <div className="space-y-2 text-sm text-foreground/80">
                    {detail.items.map((item, idx) => (
                      <div key={`${item.nome ?? 'item'}-${idx}`} className="flex items-start justify-between gap-3">
                        <div className="flex-1">
                          <div className="font-semibold text-foreground">{item.nome}</div>
                          {item.observacoes && <div className="text-xs text-muted-foreground">Obs: {item.observacoes}</div>}
                        </div>
                        <div className="text-sm text-foreground">x{item.quantidade ?? 1}</div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-sm text-muted-foreground">Itens não disponíveis.</div>
                )}
              </CardContent>
            </Card>
          </>
        ) : (
          <Card className="border border-muted">
            <CardContent className="p-4 text-sm text-muted-foreground">
              Não encontramos informações deste pedido no momento.
            </CardContent>
          </Card>
        )}
      </main>
      <Footer />
    </div>
  );
}
