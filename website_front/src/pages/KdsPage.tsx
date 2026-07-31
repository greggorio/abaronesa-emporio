import { useEffect, useMemo, useState, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { StationFilter } from '@/components/kds/StationFilter';
import { TicketGrid } from '@/components/kds/TicketGrid';
import { KdsTicket, KdsQueueResponse, Estacao, StatusItem } from '@/types/kds';
import { apiConfig } from '@/config/api';
import axios from '@/lib/axios';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { RefreshCw, AlertTriangle, Volume2, VolumeX, Bell } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { useTheme } from '@/contexts/ThemeContext';

type Chamado = {
  id: number;
  sessaoMesaId: number;
  mesaSlug: string;
  mesaRotulo: string;
  mesaReferencia?: string | null;
  tipo: 'garcom' | 'conta' | 'ajuda';
  status: 'pendente' | 'atendido' | 'cancelado';
  observacao?: string;
  criadoEm: string;
  atendidoPor?: string;
  atendidoEm?: string;
  tempoEsperaSegundos?: number;
};

type DeliveryQueueResponse = {
  tickets: {
    deliveryItemId: number;
    pedidoId?: number;
    status: StatusItem;
    atualizadoEm: string;
    estacao?: Estacao;
    item: {
      nome: string;
      quantidade: number;
      observacoes?: string | null;
      necessitaPreparacao?: boolean;
      skuId?: number;
    };
    delivery: {
      deliveryId?: string | null;
      externalId?: string | null;
      customerName?: string | null;
      dropoffAddress?: string | null;
      status?: string | null;
    };
    tipo: 'delivery';
  }[];
};

export default function KdsPage() {
  const { theme, isLoading: themeIsLoading } = useTheme();
  const { toast } = useToast();
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text))]' : 'text-foreground';
  const mesaTextColorMuted = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text)/0.7)]' : 'text-foreground/70';
  const accentOutlineBtn = 'border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]';

  const [tickets, setTickets] = useState<KdsTicket[]>([]);
  const [archivedTickets, setArchivedTickets] = useState<KdsTicket[]>([]);
  const [showArchived, setShowArchived] = useState(false);
  const [chamados, setChamados] = useState<Chamado[]>([]);
  const [station, setStation] = useState<Estacao | 'all'>('all');
  const location = useLocation();
  const forcedStation = useMemo<Estacao | null>(() => {
    const params = new URLSearchParams(location.search);
    const raw = params.get('station');
    if (raw === 'kitchen' || raw === 'bar') return raw;
    return null;
  }, [location.search]);
  const [statusTab, setStatusTab] = useState<'all' | 'queued' | 'accepted' | 'preparing' | 'ready'>(() => {
    try {
      const saved = localStorage.getItem('kds-status-tab');
      if (saved === 'queued' || saved === 'accepted' || saved === 'preparing' || saved === 'ready' || saved === 'all') {
        return saved;
      }
    } catch {}
    return 'all';
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [soundEnabled, setSoundEnabled] = useState(() => {
    const saved = localStorage.getItem('kds-sound-enabled');
    return saved !== null ? saved === 'true' : true;
  });
  const soundEnabledRef = useRef(soundEnabled);

  /**
   * Fetches pending chamados from API
   */
  const fetchChamados = async () => {
    try {
      const response = await axios.get<Chamado[]>(
        `${apiConfig.erpBaseUrl}/api/chamados/pendentes`
      );
      setChamados(response.data);
    } catch (err) {
      console.error('Error fetching chamados:', err);
    }
  };

  /**
   * Handle attending a chamado
   */
  const handleAtenderChamado = async (chamadoId: number) => {
    try {
      await axios.patch(
        `${apiConfig.erpBaseUrl}/api/chamados/${chamadoId}/atender`,
        {},
        { params: { atendidoPor: 'KDS' } }
      );

      // Remove from local state
      setChamados((prev) => prev.filter((c) => c.id !== chamadoId));

      toast({
        title: 'Chamado atendido',
        description: 'Chamado marcado como atendido.',
      });
    } catch (err) {
      console.error('Erro ao atender chamado:', err);
      toast({
        title: 'Erro',
        description: 'Falha ao atender chamado. Tente novamente.',
        variant: 'destructive',
      });
    }
  };

  /**
   * Fetches tickets from the KDS queue API
   */
  const fetchTickets = async () => {
    try {
      setLoading(true);
      setError(null);

      const [mesaResponse, deliveryResponse] = await Promise.all([
        axios.get<KdsQueueResponse>(`${apiConfig.erpBaseUrl}/api/kds/queue`),
        axios
          .get<DeliveryQueueResponse>(`${apiConfig.erpBaseUrl}/api/delivery/kds/queue`)
          .catch(() => ({ data: { tickets: [] } as DeliveryQueueResponse })),
      ]);

      // Filter by forced station (URL) if present
      const filteredTickets = (forcedStation
        ? mesaResponse.data.tickets.filter((t) => t.estacao === forcedStation)
        : mesaResponse.data.tickets
      ).map((t) => ({ ...t, tipo: 'mesa' as const }));

      const deliveryTickets: KdsTicket[] = (deliveryResponse.data.tickets || []).map((t) => ({
        itemPedidoId: t.deliveryItemId,
        deliveryItemId: t.deliveryItemId,
        pedidoId: t.pedidoId ?? t.deliveryItemId,
        estacao: t.estacao ?? 'kitchen',
        status: t.status,
        atualizadoEm: t.atualizadoEm,
        tipo: 'delivery',
        item: t.item,
        mesa: {
          slug: 'DELIVERY',
          rotulo: 'DELIVERY',
          referencia: t.delivery?.deliveryId || t.delivery?.externalId || null,
        },
        pedido: {
          criadoEm: t.atualizadoEm,
        },
        delivery: t.delivery,
      }));

      const allTickets = [...filteredTickets, ...deliveryTickets];

      // Sort tickets by creation time (oldest first - FIFO)
      const sortedTickets = allTickets.sort((a, b) => {
        return new Date(a.pedido.criadoEm).getTime() - new Date(b.pedido.criadoEm).getTime();
      });

      setTickets(sortedTickets);
      setLastUpdated(new Date());
    } catch (err) {
      console.error('Error fetching tickets:', err);
      setError('Erro ao carregar pedidos. Tente novamente.');
    } finally {
      setLoading(false);
    }
  };

  /**
   * Handle station filter change
   */
  const handleStationChange = (newStation: Estacao | 'all') => {
    if (forcedStation) {
      toast({
        title: 'Filtro travado',
        description: `A estação está fixa em ${forcedStation === 'bar' ? 'Bar' : 'Cozinha'} pela URL.`,
        duration: 3000,
      });
      return;
    }
    setStation(newStation);
  };

  /**
   * Handle status tab change
   */
  const handleStatusTabChange = (tab: 'all' | 'queued' | 'accepted' | 'preparing' | 'ready') => {
    setStatusTab(tab);
    try { localStorage.setItem('kds-status-tab', tab); } catch {}
  };

  /**
   * Handle manual refresh
   */
  const handleRefresh = () => {
    fetchTickets();
  };

  /**
   * Toggle sound notifications
   */
  const toggleSound = () => {
    setSoundEnabled((prev) => !prev);
  };

  /**
   * Persist sound preference to localStorage and update ref
   */
  useEffect(() => {
    soundEnabledRef.current = soundEnabled;
    localStorage.setItem('kds-sound-enabled', soundEnabled.toString());
  }, [soundEnabled]);

  useEffect(() => {
    // Lock station to URL param if provided
    if (forcedStation && station !== forcedStation) {
      setStation(forcedStation);
    }
  }, [forcedStation, station]);

  useEffect(() => {
    const raw = new URLSearchParams(location.search).get('station');
    if (raw && raw !== 'bar' && raw !== 'kitchen') {
      toast({
        title: 'Filtro de estação ignorado',
        description: 'O parâmetro station deve ser "bar" ou "kitchen". Exibindo todas as estações.',
        variant: 'destructive',
        duration: 4000,
      });
    }
  }, [location.search, toast]);

  /**
   * Station counts
   */
  const stationCounts = useMemo(() => {
    const base = { all: 0, kitchen: 0, bar: 0 } as Record<'all' | 'kitchen' | 'bar', number>;
    for (const t of tickets) {
      base.all += 1;
      if (t.estacao === 'kitchen') base.kitchen += 1;
      else if (t.estacao === 'bar') base.bar += 1;
    }
    return base;
  }, [tickets]);

  /**
   * Status counts (based on loaded tickets after station filter)
   */
  const statusCounts = useMemo(() => {
    const base = { all: 0, queued: 0, accepted: 0, preparing: 0, ready: 0 } as Record<'all'|'queued'|'accepted'|'preparing'|'ready', number>;
    for (const t of tickets) {
      base.all += 1;
      if (t.status === 'queued') base.queued += 1;
      else if (t.status === 'accepted') base.accepted += 1;
      else if (t.status === 'preparing') base.preparing += 1;
      else if (t.status === 'ready') base.ready += 1;
    }
    return base;
  }, [tickets]);

  /**
   * Apply station and status filters for display
   */
  const displayedTickets = useMemo(() => {
    let filtered = tickets;

    // Filter by station
    if (station !== 'all') {
      filtered = filtered.filter((t) => t.estacao === station);
    }

    // Filter by status
    if (statusTab !== 'all') {
      filtered = filtered.filter((t) => t.status === statusTab);
    }

    return filtered;
  }, [tickets, station, statusTab]);

  /**
   * Handle status update for a ticket
   */
  const handleStatusUpdate = async (ticket: KdsTicket, newStatus: StatusItem, motivoCodigo?: string, motivoDetalhe?: string) => {
    try {
      const isDelivery = ticket.tipo === 'delivery';
      const targetId = isDelivery ? (ticket.deliveryItemId ?? ticket.itemPedidoId) : ticket.itemPedidoId;
      if (isDelivery) {
        await axios.patch(`${apiConfig.erpBaseUrl}/api/delivery/kds/tickets/${targetId}`, { status: newStatus });
      } else {
        await axios.patch(`${apiConfig.erpBaseUrl}/api/kds/tickets/${targetId}`, {
          status: newStatus,
          motivoCodigo: motivoCodigo || undefined,
          motivoDetalhe: motivoDetalhe || undefined,
        });
      }

      // Update ticket locally
      setTickets((prevTickets) =>
        prevTickets.map((t) => {
          const matches = t.tipo === 'delivery'
            ? (t.deliveryItemId ?? t.itemPedidoId) === targetId
            : t.itemPedidoId === targetId;
          return matches ? { ...t, status: newStatus } : t;
        })
      );

      toast({
        title: 'Status atualizado',
        description: `Item atualizado para ${newStatus}`,
      });

      // SSE will handle the real-time update, no need to refetch
    } catch (err) {
      console.error('Erro ao atualizar status:', err);
      toast({
        title: 'Erro',
        description: 'Falha ao atualizar status. Tente novamente.',
        variant: 'destructive',
      });
      throw err;
    }
  };

  const getDeliveryKey = (ticket: KdsTicket) =>
    ticket.deliveryItemId ?? ticket.itemPedidoId;

  const handleArchiveDelivery = async (ticket: KdsTicket) => {
    if (ticket.tipo !== 'delivery') return;
    const key = getDeliveryKey(ticket);
    try {
      await axios.patch(`${apiConfig.erpBaseUrl}/api/delivery/kds/tickets/${key}/archive`);

      setTickets((prev) => prev.filter((t) => t.tipo !== 'delivery' || getDeliveryKey(t) !== key));
      setArchivedTickets((prev) => {
        if (prev.some((t) => getDeliveryKey(t) === key)) return prev;
        return [...prev, ticket];
      });
      setShowArchived(true);

      toast({
        title: 'Arquivado',
        description: 'Item de delivery arquivado no KDS.',
      });
    } catch (err) {
      console.error('Erro ao arquivar delivery:', err);
      toast({
        title: 'Erro',
        description: 'Não foi possível arquivar o delivery.',
        variant: 'destructive',
      });
      throw err;
    }
  };

  const handlePrintTicket = async (ticket: KdsTicket) => {
    if (ticket.tipo === 'delivery') {
      toast({
        title: 'Impressão indisponível',
        description: 'Itens de delivery não podem ser impressos aqui.',
        variant: 'destructive',
      });
      return;
    }

    try {
      await axios.post(`${apiConfig.erpBaseUrl}/api/kds/tickets/${ticket.itemPedidoId}/print`);
      toast({
        title: 'Impresso',
        description: `Ticket do pedido #${ticket.pedidoId} enviado para impressão.`,
      });
    } catch (err) {
      console.error('Erro ao imprimir ticket:', err);
      toast({
        title: 'Erro',
        description: 'Falha ao enviar para impressão. Verifique o agente.',
        variant: 'destructive',
      });
      throw err;
    }
  };

  /**
   * Initial load
   */
  useEffect(() => {
    if (themeIsLoading) return;
    fetchTickets();
    fetchChamados();
  }, [themeIsLoading, forcedStation]);

  /**
   * Connect to SSE for real-time updates
   */
  useEffect(() => {
    if (themeIsLoading) return;

    const eventSource = new EventSource(`${apiConfig.erpBaseUrl}/api/events/kds`);

    eventSource.addEventListener('connected', () => {
      console.log('[KDS SSE] Connected');
    });

    // Listen for heartbeat pings
    eventSource.addEventListener('ping', () => {
      // Silent heartbeat - keeps connection alive
    });

    const handleNewItem = (event: MessageEvent, forceDelivery?: boolean) => {
      try {
        const newTicket = JSON.parse(event.data) as KdsTicket;
        const ticketTipo = newTicket.tipo ?? 'mesa';
        if (forceDelivery && ticketTipo !== 'delivery') return;

        if (forcedStation && newTicket.estacao !== forcedStation) return;

        setTickets((prev) => {
          // Avoid duplicates
          const exists = prev.some((t) => t.itemPedidoId === newTicket.itemPedidoId && t.tipo === ticketTipo);
          if (exists) return prev;

          // Add and sort by creation time (FIFO)
          const updated = [...prev, { ...newTicket, tipo: ticketTipo }];
          return updated.sort((a, b) => {
            return new Date(a.pedido.criadoEm).getTime() - new Date(b.pedido.criadoEm).getTime();
          });
        });

        setLastUpdated(new Date());

        // Show notification
        toast({
          title: '🔔 Novo Pedido!',
          description: `${newTicket.mesa.rotulo || newTicket.mesa.slug} - ${newTicket.item.quantidade}x ${newTicket.item.nome}`,
          duration: 5000,
        });

        // Play notification sound (if enabled)
        if (soundEnabledRef.current) {
          try {
            const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHO8tiJNwgZaLvt559NEAxQp+PwtmMcBjiR1/LMeSwFJHfH8N2QQAoUXrTp66hVFApGn+DyvmwhBTGH0fPTgjMGHm7A7+OZRBEJR6Hh8rtlHAU6jtXyyXkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSd5ye7dn0YOD1Ko5PSxZBkFOo/V8sd4KwUhfsvv4ZNCDRJZR+PytWYeBTiP1PPDeSwEIHTH7+OZRRMJR6Dh8r5kGwU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRRMJR6Dh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRRMJR6Dh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRRMJR6Dh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRBMJSKDh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRBMJSKDh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBQ==');
            audio.play().catch(() => {
              // Ignore if autoplay is blocked
            });
          } catch {
            // Ignore audio errors
          }
        }
      } catch (err) {
        console.error('[KDS SSE] Error parsing kds.new_item:', err);
      }
    };

    eventSource.addEventListener('kds.new_item', (event) => handleNewItem(event));

    eventSource.addEventListener('kds.status_changed', (event) => {
      try {
        const payload = JSON.parse(event.data);

        const { itemPedidoId, status } = payload;

        setTickets((prev) => {
          if (forcedStation) {
            const target = prev.find((t) => t.itemPedidoId === itemPedidoId);
            if (!target || target.estacao !== forcedStation || target.tipo === 'delivery') {
              return prev;
            }
          }
          // If status is delivered, remove from queue
          if (status === 'delivered') {
            return prev.filter((t) => t.itemPedidoId !== itemPedidoId || t.tipo === 'delivery');
          }

          // Otherwise update the status
          return prev.map((t) =>
            t.itemPedidoId === itemPedidoId && t.tipo !== 'delivery'
              ? { ...t, status: status as StatusItem }
              : t
          );
        });

        setLastUpdated(new Date());
      } catch (err) {
        console.error('[KDS SSE] Error parsing kds.status_changed:', err);
      }
    });

    const handleDeliveryStatusChanged = (event: MessageEvent) => {
      try {
        const payload = JSON.parse(event.data);
        const { deliveryItemId, status } = payload;
        if (!deliveryItemId) return;

        const updateItemStatus = (list: KdsTicket[]) =>
          list.map((t) =>
            (t.deliveryItemId ?? t.itemPedidoId) === deliveryItemId && t.tipo === 'delivery'
              ? { ...t, status: status as StatusItem }
              : t
          );

        setTickets((prev) => updateItemStatus(prev));
        setArchivedTickets((prev) => updateItemStatus(prev));

        setLastUpdated(new Date());
      } catch (err) {
        console.error('[KDS SSE] Error parsing kds.delivery_status_changed:', err);
      }
    };

    eventSource.addEventListener('kds.delivery_status_changed', handleDeliveryStatusChanged);

    const handleDeliveryStatus = (event: MessageEvent) => {
      try {
        const payload = JSON.parse(event.data);
        const { deliveryId, status, deliveryItemId, externalId } = payload;
        if (!deliveryId && !deliveryItemId) return;

        const updateDeliveryTickets = (list: KdsTicket[]) => {
          const shouldRemove = status === 'delivered';
          return list.flatMap((t) => {
            if (t.tipo !== 'delivery') return [t];
            const matches = deliveryItemId
              ? (t.deliveryItemId ?? t.itemPedidoId) === deliveryItemId
              : t.delivery?.deliveryId === deliveryId;
            if (!matches) return [t];
            if (shouldRemove) return [];
            return [
              {
                ...t,
                delivery: {
                  ...t.delivery,
                  deliveryId: deliveryId ?? t.delivery?.deliveryId,
                  externalId: externalId ?? t.delivery?.externalId,
                  status,
                },
                mesa: {
                  ...t.mesa,
                  referencia: deliveryId ?? t.mesa.referencia,
                },
              },
            ];
          });
        };

        setTickets((prev) => updateDeliveryTickets(prev));
        setArchivedTickets((prev) => updateDeliveryTickets(prev));

        setLastUpdated(new Date());
      } catch (err) {
        console.error('[KDS SSE] Error parsing kds.delivery_status:', err);
      }
    };

    eventSource.addEventListener('kds.delivery_status', handleDeliveryStatus);

    const handleDeliveryArchived = (event: MessageEvent) => {
      try {
        const payload = JSON.parse(event.data);
        const { deliveryItemId } = payload;
        if (!deliveryItemId) return;

        const removeArchived = (list: KdsTicket[]) =>
          list.filter((t) => (t.deliveryItemId ?? t.itemPedidoId) !== deliveryItemId);

        setTickets((prev) => removeArchived(prev));
        setArchivedTickets((prev) => removeArchived(prev));
      } catch (err) {
        console.error('[KDS SSE] Error parsing kds.delivery_archived:', err);
      }
    };

    eventSource.addEventListener('kds.delivery_archived', handleDeliveryArchived);

    eventSource.addEventListener('chamado.novo', (event) => {
      try {
        const payload = JSON.parse(event.data);

        const { chamadoId, mesaSlug, mesaRotulo, mesaReferencia, tipo, observacao, criadoEm } = payload;

        // Add to chamados list
        const newChamado: Chamado = {
          id: chamadoId,
          sessaoMesaId: payload.sessaoMesaId,
          mesaSlug,
          mesaRotulo,
          mesaReferencia,
          tipo,
          status: 'pendente',
          observacao,
          criadoEm,
        };

        setChamados((prev) => {
          const exists = prev.some((c) => c.id === chamadoId);
          if (exists) return prev;
          return [...prev, newChamado];
        });

        // Show notification
        const tipoLabel = tipo === 'garcom' ? 'Garçom' : tipo === 'conta' ? 'Conta' : 'Ajuda';
        toast({
          title: `🔔 Chamado: ${tipoLabel}`,
          description: `${mesaRotulo || mesaSlug}${observacao ? ` - ${observacao}` : ''}`,
          duration: 5000,
        });

        // Play sound
        if (soundEnabledRef.current) {
          try {
            const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHO8tiJNwgZaLvt559NEAxQp+PwtmMcBjiR1/LMeSwFJHfH8N2QQAoUXrTp66hVFApGn+DyvmwhBTGH0fPTgjMGHm7A7+OZRBEJR6Hh8rtlHAU6jtXyyXkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSd5ye7dn0YOD1Ko5PSxZBkFOo/V8sd4KwUhfsvv4ZNCDRJZR+PytWYeBTiP1PPDeSwEIHTH7+OZRRMJR6Dh8r5kGwU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRRMJR6Dh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRRMJR6Dh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRRMJR6Dh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRBMJSKDh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRBMJSKDh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBQ==');
            audio.play().catch(() => {
              // Ignore if autoplay is blocked
            });
          } catch {
            // Ignore audio errors
          }
        }
      } catch (err) {
        console.error('[KDS SSE] Error parsing chamado.novo:', err);
      }
    });

    eventSource.addEventListener('chamado.atendido', (event) => {
      try {
        const payload = JSON.parse(event.data);

        const { chamadoId } = payload;

        // Remove from chamados list
        setChamados((prev) => prev.filter((c) => c.id !== chamadoId));
      } catch (err) {
        console.error('[KDS SSE] Error parsing chamado.atendido:', err);
      }
    });

    eventSource.onerror = (err) => {
      console.error('[KDS SSE] Connection error:', err);
      // Don't close automatically - let browser handle reconnection
    };

    // Cleanup on unmount
    return () => {
      eventSource.close();
    };
  }, [themeIsLoading, forcedStation]); // Reconnect when filtro de estação muda

  /**
   * Format last updated timestamp
   */
  const formatLastUpdated = (): string => {
    if (!lastUpdated) return '';
    const hours = lastUpdated.getHours().toString().padStart(2, '0');
    const minutes = lastUpdated.getMinutes().toString().padStart(2, '0');
    return `${hours}:${minutes}`;
  };

  // Verificar loading do tema DEPOIS de todos os hooks terem sido declarados
  if (themeIsLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-current mx-auto"></div>
          <p className="mt-4">Carregando tema...</p>
        </div>
      </div>
    );
  }

  return (
    <div className={`min-h-screen bg-soft-white ${mesaTextColor}`}>
      {/* Header */}
      <header className="bg-background border-b border-border/20 sticky top-0 z-20">
        <div className="container mx-auto px-4 py-4">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            {/* Title */}
            <div>
              <h1 className="font-display text-3xl md:text-4xl tracking-wider text-foreground">
                KDS — Kitchen Display System
              </h1>
              {lastUpdated && (
                <p className="text-sm text-foreground/70 mt-1">
                  Atualizado às {formatLastUpdated()}
                </p>
              )}
            </div>

            {/* Actions */}
            <div className="flex flex-col gap-3 items-start lg:items-end">
              <div className="flex items-end gap-6 flex-wrap">
                {/* Estação Group */}
                <div>
                  <div className="text-xs uppercase tracking-wide text-foreground/70 font-medium mb-1 flex items-center gap-2">
                    <span>Estação</span>
                    {forcedStation && (
                      <span className="px-2 py-0.5 text-[10px] rounded-full bg-amber-100 text-amber-800 border border-amber-200">
                        Travado via URL ({forcedStation === 'bar' ? 'Bar' : 'Cozinha'})
                      </span>
                    )}
                  </div>
                  <div className={forcedStation ? 'opacity-60 pointer-events-none' : ''}>
                    <StationFilter
                      currentStation={station}
                      onStationChange={handleStationChange}
                      compact
                      counts={stationCounts}
                    />
                  </div>
                  {forcedStation && (
                    <p className="text-[11px] text-foreground/60 mt-1">
                      Mostrando apenas itens do {forcedStation === 'bar' ? 'Bar' : 'Cozinha'} (station param).
                    </p>
                  )}
                </div>

                {/* Status Group */}
                <div>
                  <div className="text-xs uppercase tracking-wide text-foreground/70 font-medium mb-1">Status</div>
                  <div className="flex items-center gap-2 flex-wrap">
                    {(
                      [
                        { key: 'all', label: 'Todos' },
                        { key: 'queued', label: 'Aguardando' },
                        { key: 'accepted', label: 'Aceito' },
                        { key: 'preparing', label: 'Preparando' },
                        { key: 'ready', label: 'Pronto' },
                      ] as const
                    ).map((tab) => {
                      const selected = statusTab === tab.key;
                      return (
                        <button
                          key={tab.key}
                          onClick={() => handleStatusTabChange(tab.key)}
                          className={`px-3 py-1.5 rounded-full text-sm border transition-colors font-medium ${
                            selected
                              ? 'bg-accent text-accent-foreground border-accent shadow-sm'
                              : 'border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]'
                          }`}
                          title={tab.label}
                        >
                          <span>{tab.label}</span>
                          <span className={`ml-2 inline-flex items-center justify-center min-w-[18px] h-[18px] text-xs rounded-full font-semibold ${
                            selected ? 'bg-accent-foreground/30 text-accent-foreground' : 'bg-[hsl(var(--accent)/0.15)] text-[hsl(var(--accent))]'
                          }`}>
                            {statusCounts[tab.key]}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <Button
                  onClick={toggleSound}
                  variant="outline"
                  className={`flex items-center gap-2 font-medium ${accentOutlineBtn}`}
                  title={soundEnabled ? 'Desabilitar som' : 'Habilitar som'}
                >
                  {soundEnabled ? (
                    <Volume2 className="h-4 w-4" />
                  ) : (
                    <VolumeX className="h-4 w-4" />
                  )}
                  Som
                </Button>
                <Button
                  onClick={handleRefresh}
                  variant="outline"
                  disabled={loading}
                  className={`flex items-center gap-2 font-medium ${accentOutlineBtn}`}
                >
                  <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
                  Atualizar
                </Button>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="container mx-auto px-4 py-6">
        {/* Chamados Section */}
        {chamados.length > 0 && (
          <div className="mb-6">
            <div className="flex items-center gap-2 mb-4">
              <Bell className="h-5 w-5 text-accent animate-pulse" />
              <h2 className={`text-xl font-display tracking-wider ${mesaTextColor}`}>
                Chamados Pendentes ({chamados.length})
              </h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
              {chamados.map((chamado) => {
                const tipoIcon = chamado.tipo === 'garcom' ? '🔔' : chamado.tipo === 'conta' ? '💰' : '❓';
                const tipoLabel = chamado.tipo === 'garcom' ? 'Garçom' : chamado.tipo === 'conta' ? 'Conta' : 'Ajuda';

                // Calculate elapsed time
                const criadoEm = new Date(chamado.criadoEm);
                const now = new Date();
                const elapsedSeconds = Math.floor((now.getTime() - criadoEm.getTime()) / 1000);
                const minutes = Math.floor(elapsedSeconds / 60);
                const seconds = elapsedSeconds % 60;
                const elapsedText = minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;

                return (
                  <Card
                    key={chamado.id}
                    className={`p-4 border border-accent/20 bg-white shadow-lg rounded-lg hover:shadow-xl transition-shadow overflow-hidden border-l-[4px] ${
                      chamado.tipo === 'conta' ? 'border-l-yellow-500' : 'border-l-[hsl(var(--accent))]'
                    }`}
                  >
                    <div className="flex items-start justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <span className="text-2xl">{tipoIcon}</span>
                        <div>
                          <div className={`font-semibold ${mesaTextColor}`}>
                            {chamado.mesaRotulo || chamado.mesaSlug}
                          </div>
                          {chamado.mesaReferencia && (
                            <p className={`text-xs ${mesaTextColorMuted}`}>
                              {chamado.mesaReferencia}
                            </p>
                          )}
                          <div className="text-xs inline-flex px-2 py-0.5 rounded-full border border-accent/30 bg-accent/10 text-accent">
                            {tipoLabel}
                          </div>
                        </div>
                      </div>
                      <div className={`text-xs text-right ${mesaTextColorMuted}`}>
                        <div>{elapsedText}</div>
                        <div>{criadoEm.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</div>
                      </div>
                    </div>
                    {chamado.observacao && (
                      <div className={`text-sm mb-3 italic bg-white/70 border border-accent/10 rounded p-2 ${mesaTextColorMuted}`}>
                        "{chamado.observacao}"
                      </div>
                    )}
                    <Button
                      onClick={() => handleAtenderChamado(chamado.id)}
                      className={`w-full font-semibold shadow-sm ${
                        chamado.tipo === 'conta'
                          ? 'bg-yellow-500 hover:bg-yellow-600 text-white'
                          : 'bg-accent hover:bg-accent/90 text-mesa-text'
                      }`}
                      size="sm"
                    >
                      Atender
                    </Button>
                  </Card>
                );
              })}
            </div>
          </div>
        )}

        {/* Loading State */}
        {loading && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {[1, 2, 3, 4].map((i) => (
              <Card key={i} className="p-6 border border-accent/20 bg-white shadow-md rounded-lg">
                <Skeleton className="h-6 w-32 mb-4" />
                <Skeleton className="h-4 w-20 mb-6" />
                <Skeleton className="h-16 w-full mb-4" />
                <Skeleton className="h-6 w-24" />
              </Card>
            ))}
          </div>
        )}

        {/* Error State */}
        {!loading && error && (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <AlertTriangle className="h-16 w-16 text-destructive mb-4" />
            <h3 className="text-xl font-semibold text-foreground mb-2">
              {error}
            </h3>
            <Button
              onClick={handleRefresh}
              variant="outline"
              className={`mt-4 font-medium ${accentOutlineBtn}`}
            >
              Tentar novamente
            </Button>
          </div>
        )}

        {/* Tickets Grid */}
        {!loading && !error && (
          <>
            <TicketGrid
              tickets={displayedTickets}
              onStatusUpdate={handleStatusUpdate}
              onArchiveDelivery={handleArchiveDelivery}
              onPrint={handlePrintTicket}
            />

            {archivedTickets.length > 0 && (
              <div className="mt-8">
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-lg font-display tracking-wider text-foreground">
                    Arquivados ({archivedTickets.length})
                  </h2>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setShowArchived((prev) => !prev)}
                    className={`font-medium ${accentOutlineBtn}`}
                  >
                    {showArchived ? 'Ocultar' : 'Mostrar'}
                  </Button>
                </div>
                {showArchived && (
                  <TicketGrid tickets={archivedTickets} onPrint={handlePrintTicket} />
                )}
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}
