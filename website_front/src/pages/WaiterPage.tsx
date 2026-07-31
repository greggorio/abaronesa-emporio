import { useEffect, useState, useRef } from 'react';
import { apiConfig } from '@/config/api';
import axios from '@/lib/axios';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Bell,
  Volume2,
  VolumeX,
  CheckCircle,
  UtensilsCrossed,
  CreditCard,
  Monitor,
  Settings,
  LogOut,
  User,
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { useNotifications } from '@/hooks/useNotifications';
import { useAuth } from '@/hooks/useAuth';
import { MesasGrid } from '@/components/admin/MesasGrid';
import { useTheme } from '@/contexts/ThemeContext';
import { KdsTicket, KdsQueueResponse } from '@/types/kds';
import { ServiceMode } from '@/types/delivery';
import { useNavigate } from 'react-router-dom';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

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
};

type ReadyItem = {
  itemPedidoId: number;
  pedidoId: number;
  mesaSlug: string;
  mesaRotulo: string;
  mesaReferencia?: string | null;
  quantidade: number;
  nome: string;
  observacoes?: string | null;
  atualizadoEm?: string;
};

type WaiterPayment = {
  pagamentoId: number;
  sessaoMesaId?: number | null;
  mesaSlug?: string | null;
  mesaRotulo?: string | null;
  sessaoConvidadoId?: number | null;
  convidado?: string | null;
  pagante?: string | null;
  metodo?: string | null;
  status?: string | null;
  valor?: number | string | null;
  criadoEm?: string | null;
  pagoEm?: string | null;
  resolvido?: boolean | null;
};

export default function WaiterPage() {
  const [chamados, setChamados] = useState<Chamado[]>([]);
  const [readyItems, setReadyItems] = useState<ReadyItem[]>([]);
  const [payments, setPayments] = useState<WaiterPayment[]>([]);
  const [paymentFilter, setPaymentFilter] = useState<'pending' | 'paid' | 'all'>('all');
  const [showResolved, setShowResolved] = useState(false);
  const [activePaymentsCount, setActivePaymentsCount] = useState(0);
  const [showComprovanteModal, setShowComprovanteModal] = useState(false);
  const [comprovantePdfUrl, setComprovantePdfUrl] = useState<string | null>(null);
  const [whatsappPhone, setWhatsappPhone] = useState('');
  const [sendingWhatsapp, setSendingWhatsapp] = useState(false);
  const [showWhatsappDialog, setShowWhatsappDialog] = useState(false);
  const [whatsappTargetPdfUrl, setWhatsappTargetPdfUrl] = useState<string | null>(null);
  const [currentComprovantePayment, setCurrentComprovantePayment] = useState<WaiterPayment | null>(null);
  const [ticketMap, setTicketMap] = useState<Record<number, KdsTicket>>({});
  const ticketMapRef = useRef<Record<number, KdsTicket>>({});
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('chamados');
  const [soundEnabled, setSoundEnabled] = useState(() => {
    const saved = localStorage.getItem('waiter-sound-enabled');
    return saved !== null ? saved === 'true' : true;
  });
  const soundEnabledRef = useRef(soundEnabled);
  const readyNotifiedRef = useRef<Set<number>>(new Set());
  const { toast } = useToast();
  const { requestPermission, showNotification, permission } = useNotifications();
  const { theme } = useTheme();
  const navigate = useNavigate();
  const user = useAuth((state) => state.user);
  const logout = useAuth((state) => state.logout);
  const isAuthenticated = useAuth((state) => state.isAuthenticated);
  const canAccessAdminPanel = useAuth(
    (state) => state.isAdmin() || state.isSystem() || state.isFuncionario()
  );
  const canAccessKdsPanel = useAuth(
    (state) => state.isKds() || state.isAdmin() || state.isSystem()
  );
  const canAccessWaiterPanel = useAuth(
    (state) => state.isWaiter() || state.isCaixa() || state.isAdmin() || state.isSystem()
  );
  const canSeePagamentos = useAuth(
    (state) => state.isCaixa() || state.isAdmin() || state.isSystem()
  );
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text))]' : 'text-foreground';
  const mesaTextColorMuted = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text)/0.7)]' : 'text-foreground/70';
  const mesaTextColorLight = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text)/0.4)]' : 'text-foreground/40';
  const mesaSurfaceAccent = theme?.tokens?.accent ? 'bg-[hsl(var(--accent)/0.1)] border-[hsl(var(--accent)/0.2)]' : 'bg-accent/10 border-accent/20';
  useEffect(() => {
    if (!canSeePagamentos && activeTab === 'pagamentos') {
      setActiveTab('chamados');
    }
  }, [activeTab, canSeePagamentos]);
  const formatCurrency = (value?: number | string | null) => {
    if (value === null || value === undefined) return 'R$ 0,00';
    const num = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(num)) return 'R$ 0,00';
    return `R$ ${num.toFixed(2)}`;
  };

  // Audio blob (same as KDS)
  const notificationSound = 'data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHO8tiJNwgZaLvt559NEAxQp+PwtmMcBjiR1/LMeSwFJHfH8N2QQAoUXrTp66hVFApGn+DyvmwhBTGH0fPTgjMGHm7A7+OZRBEJR6Hh8rtlHAU6jtXyyXkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSd5ye7dn0YOD1Ko5PSxZBkFOo/V8sd4KwUhfsvv4ZNCDRJZR+PytWYeBTiP1PPDeSwEIHTH7+OZRRMJR6Dh8r5kGwU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRRMJR6Dh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRRMJR6Dh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRBMJSKDh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBSh4ye7dn0YOD1Kp5fSwYxkFOo/V8sd4KwUhfsvv4ZNCDRJZsPPytWYeBTiP1PPDeSwEIHPH7+OZRBMJSKDh8r5kHAU6jtXyyHkrBSF+zO/glEILElyx6eyiUQ8JS6Lj88BnHgU2idDz0H8uBQ==';

  const playSound = () => {
    if (soundEnabledRef.current) {
      try {
        const audio = new Audio(notificationSound);
        audio.play().catch(() => {});
      } catch {
        // Ignore errors
      }
    }
  };

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

  const mapTicketToReadyItem = (ticket: KdsTicket): ReadyItem => ({
    itemPedidoId: ticket.itemPedidoId,
    pedidoId: ticket.pedidoId,
    mesaSlug: ticket.mesa.slug,
    mesaRotulo: ticket.mesa.rotulo,
    mesaReferencia: ticket.mesa.referencia ?? null,
    quantidade: ticket.item.quantidade,
    nome: ticket.item.nome,
    observacoes: ticket.item.observacoes ?? null,
    atualizadoEm: ticket.atualizadoEm,
  });

  const fetchReadyItems = async () => {
    try {
      const response = await axios.get<KdsQueueResponse>(
        `${apiConfig.erpBaseUrl}/api/kds/queue`
      );
      const tickets = response.data.tickets || [];
      setTicketMap(
        tickets.reduce<Record<number, KdsTicket>>((acc, t) => {
          acc[t.itemPedidoId] = t;
          return acc;
        }, {})
      );
      const ready = tickets.filter((t) => t.status === 'ready').map(mapTicketToReadyItem);
      setReadyItems(ready);
      readyNotifiedRef.current = new Set(ready.map((r) => r.itemPedidoId));
    } catch (err) {
      console.error('Error fetching ready items:', err);
    }
  };

  const fetchPayments = async () => {
    try {
      const response = await axios.get<WaiterPayment[]>(
        `${apiConfig.erpBaseUrl}/api/waiter/pagamentos`,
        { params: { resolvido: showResolved } }
      );
      setPayments(response.data || []);
      if (showResolved) {
        try {
          const activeResponse = await axios.get<WaiterPayment[]>(
            `${apiConfig.erpBaseUrl}/api/waiter/pagamentos`,
            { params: { resolvido: false } }
          );
          setActivePaymentsCount(activeResponse.data?.length || 0);
        } catch (err) {
          console.error('Error fetching active payments count:', err);
          setActivePaymentsCount(0);
        }
      } else {
        setActivePaymentsCount(response.data?.length || 0);
      }
    } catch (err) {
      console.error('Error fetching payments:', err);
    }
  };

  const handleEmitirNfce = async (pagamentoId: number) => {
    try {
      const response = await axios.post(
        `${apiConfig.erpBaseUrl}/api/waiter/pagamentos/${pagamentoId}/emitir-nfce`
      );
      const status = response?.data?.status;
      if (status && status !== 'AUTORIZADA') {
        const motivo = response?.data?.motivoRejeicao || 'Documento rejeitado.';
        toast({
          title: 'Rejeitado',
          description: motivo,
          variant: 'destructive',
        });
        return;
      }
      const numero = response?.data?.numero;
      toast({
        title: 'NFC-e emitida',
        description: numero ? `Número ${numero}` : 'Documento emitido com sucesso.',
        duration: 3000,
      });
    } catch (err) {
      console.error('Erro ao emitir NFC-e:', err);
      toast({
        title: 'Erro',
        description: 'Falha ao emitir NFC-e.',
        variant: 'destructive',
      });
    }
  };

  const openWhatsappDialog = (url: string | null) => {
    if (!url) {
      toast({
        title: 'Erro',
        description: 'PDF indisponível para envio.',
        variant: 'destructive',
      });
      return;
    }
    setWhatsappTargetPdfUrl(url);
    setWhatsappPhone('');
    setShowWhatsappDialog(true);
  };

  const handleEnviarWhatsapp = async (pdfUrlToSend: string | null) => {
    if (!pdfUrlToSend) {
      toast({
        title: 'Erro',
        description: 'Nenhum comprovante PDF disponível para envio.',
        variant: 'destructive',
      });
      return;
    }
    const cleanPhone = whatsappPhone.replace(/\D/g, '');
    if (!cleanPhone || cleanPhone.length < 10) {
      toast({
        title: 'Telefone inválido',
        description: 'Informe um WhatsApp válido (mínimo 10 dígitos).',
        variant: 'destructive',
      });
      return;
    }

    setSendingWhatsapp(true);
    try {
      const response = await fetch(pdfUrlToSend);
      const pdfBlob = await response.blob();

      const formData = new FormData();
      const mesaLabel =
        currentComprovantePayment?.mesaRotulo ||
        currentComprovantePayment?.mesaSlug ||
        (currentComprovantePayment?.sessaoMesaId
          ? `Mesa ${currentComprovantePayment.sessaoMesaId}`
          : 'mesa');
      formData.append(
        'arquivo',
        pdfBlob,
        `comprovante_${mesaLabel.replace(/\s+/g, '_').toLowerCase()}.pdf`
      );
      formData.append('telefone', cleanPhone);
      formData.append('mensagem', `Segue o comprovante da ${mesaLabel}.`);

      await axios.post(`${apiConfig.erpBaseUrl}/api/whatsapp/enviar-arquivo`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      toast({
        title: 'Enviado',
        description: 'Comprovante enviado via WhatsApp.',
        duration: 3000,
      });
      setWhatsappPhone('');
      setShowWhatsappDialog(false);
    } catch (error: any) {
      const msg = error?.response?.data?.message || 'Erro ao enviar comprovante via WhatsApp.';
      toast({
        title: 'Erro',
        description: msg,
        variant: 'destructive',
      });
    } finally {
      setSendingWhatsapp(false);
    }
  };

  const handleComprovante = async (payment: WaiterPayment) => {
    try {
      setCurrentComprovantePayment(payment);
      if (comprovantePdfUrl) {
        URL.revokeObjectURL(comprovantePdfUrl);
      }
      setComprovantePdfUrl(null);

      const response = await axios.get(
        `${apiConfig.erpBaseUrl}/api/waiter/pagamentos/${payment.pagamentoId}/comprovante`,
        { responseType: 'blob' }
      );
      const pdfUrl = URL.createObjectURL(response.data);
      setComprovantePdfUrl(pdfUrl);
      setShowComprovanteModal(true);
    } catch (err) {
      console.error('Erro ao carregar comprovante:', err);
      toast({
        title: 'Erro',
        description: 'Falha ao carregar comprovante.',
        variant: 'destructive',
      });
    }
  };

  const handleFecharMesa = async (sessaoMesaId?: number | null) => {
    if (!sessaoMesaId) return;
    try {
      await axios.post(`${apiConfig.erpBaseUrl}/api/waiter/mesas/${sessaoMesaId}/fechar`);
      toast({
        title: 'Mesa encerrada',
        description: `Sessão ${sessaoMesaId} encerrada.`,
        duration: 3000,
      });
      fetchPayments();
    } catch (err) {
      console.error('Erro ao fechar mesa:', err);
      toast({
        title: 'Erro',
        description: 'Falha ao encerrar a mesa.',
        variant: 'destructive',
      });
    }
  };

  const handleArquivarPagamento = async (pagamentoId: number, fecharMesa: boolean) => {
    try {
      await axios.post(
        `${apiConfig.erpBaseUrl}/api/waiter/pagamentos/${pagamentoId}/resolver`,
        {},
        { params: { fecharMesa } }
      );
      toast({
        title: fecharMesa ? 'Arquivado e fechado' : 'Arquivado',
        description: fecharMesa ? 'Pagamento arquivado e mesa encerrada.' : 'Pagamento arquivado com sucesso.',
        duration: 3000,
      });
      fetchPayments();
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Falha ao arquivar pagamento.';
      toast({
        title: 'Erro',
        description: msg,
        variant: 'destructive',
      });
    }
  };

  const handleAtenderChamado = async (chamado: Chamado) => {
    try {
      // Optimistic update
      setChamados((prev) => prev.filter((c) => c.id !== chamado.id));

      const path =
        chamado.tipo === 'conta'
          ? `/api/chamados/${chamado.id}/liberar-pagamento`
          : `/api/chamados/${chamado.id}/atender`;
      await axios.patch(
        `${apiConfig.erpBaseUrl}${path}`,
        {},
        { params: { atendidoPor: 'WaiterApp' } }
      );

      toast({
        title: chamado.tipo === 'conta' ? 'Pagamento liberado' : 'Atendido',
        description:
          chamado.tipo === 'conta'
            ? 'Cliente liberado para pagar no app.'
            : 'Cliente notificado que você está a caminho.',
        duration: 2000,
      });
    } catch (err) {
      console.error('Erro ao atender chamado:', err);
      toast({
        title: 'Erro',
        description:
          chamado.tipo === 'conta'
            ? 'Falha ao liberar pagamento.'
            : 'Falha ao registrar atendimento.',
        variant: 'destructive',
      });
      fetchChamados(); // Revert on error
    }
  };

  const handleDeliverReadyItem = async (itemPedidoId: number) => {
    const ticket = ticketMapRef.current[itemPedidoId];
    try {
      await axios.patch(
        `${apiConfig.erpBaseUrl}/api/kds/tickets/${itemPedidoId}`,
        { status: 'delivered' }
      );

      setReadyItems((prev) => prev.filter((r) => r.itemPedidoId !== itemPedidoId));
      readyNotifiedRef.current.delete(itemPedidoId);

      if (ticket) {
        setTicketMap((prev) => ({
          ...prev,
          [itemPedidoId]: { ...ticket, status: 'delivered' },
        }));
      }

      toast({
        title: 'Entregue',
        description: 'Pedido marcado como entregue.',
        duration: 2000,
      });
    } catch (err) {
      console.error('Erro ao entregar pedido:', err);
      toast({
        title: 'Erro',
        description: 'Não foi possível marcar como entregue. Tente novamente.',
        variant: 'destructive',
      });
    }
  };

  const toggleSound = () => {
    setSoundEnabled((prev) => !prev);
  };

  useEffect(() => {
    soundEnabledRef.current = soundEnabled;
    localStorage.setItem('waiter-sound-enabled', soundEnabled.toString());
  }, [soundEnabled]);

  useEffect(() => {
    ticketMapRef.current = ticketMap;
  }, [ticketMap]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      await Promise.all([fetchChamados(), fetchReadyItems(), fetchPayments()]);
      setLoading(false);
    };

    load();

    const eventSource = new EventSource(`${apiConfig.erpBaseUrl}/api/events/kds`);
    const waiterEvents = new EventSource(`${apiConfig.erpBaseUrl}/api/events/waiter`);

    eventSource.addEventListener('chamado.novo', (event) => {
      try {
        const payload = JSON.parse(event.data);
        const { chamadoId, mesaSlug, mesaRotulo, mesaReferencia, tipo, observacao, criadoEm } = payload;

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
          if (prev.some((c) => c.id === chamadoId)) return prev;
          return [...prev, newChamado];
        });

        playSound();

        const title = `Mesa ${mesaRotulo || mesaSlug}`;
        const body = `${tipo === 'conta' ? 'Pediu a conta' : 'Chamou o garçom'}${observacao ? `: ${observacao}` : ''}`;

        toast({
          title: title,
          description: body,
          variant: 'default',
          className: 'bg-[hsl(var(--background))] text-[hsl(var(--foreground))] border-none',
        });

        if (permission === 'granted') {
          showNotification({ title, body });
        }
      } catch (err) {
        console.error('Error parsing event:', err);
      }
    });

    eventSource.addEventListener('kds.new_item', (event) => {
      try {
        const newTicket = JSON.parse(event.data) as KdsTicket;

        setTicketMap((prev) => ({ ...prev, [newTicket.itemPedidoId]: newTicket }));

        const mode: ServiceMode = (newTicket.serviceMode as ServiceMode) || 'waiter_delivery';
        const isDeliveryMode = mode === 'waiter_delivery' || mode === 'DELIVERY';
        if (newTicket.status === 'ready' && isDeliveryMode) {
          setReadyItems((prev) => {
            if (prev.some((r) => r.itemPedidoId === newTicket.itemPedidoId)) return prev;
            readyNotifiedRef.current.add(newTicket.itemPedidoId);
            const newCard = mapTicketToReadyItem(newTicket);

            playSound();

            const orderLabel = newTicket.pedidoId ? `Pedido #${newTicket.pedidoId}` : `Item #${newTicket.itemPedidoId}`;
            const mesaLabel = newTicket.mesa.rotulo || newTicket.mesa.slug || 'mesa';
            const description = `${orderLabel} da ${mesaLabel} está pronto para entrega.`;

            toast({
              title: '🍽️ Pedido pronto',
              description,
              duration: 6000,
            });

            if (permission === 'granted') {
              showNotification({ title: 'Pedido pronto', body: description });
            }

            return [...prev, newCard];
          });
        }
      } catch (err) {
        console.error('[Waiter SSE] Error parsing kds.new_item:', err);
      }
    });

    eventSource.addEventListener('chamado.atendido', (event) => {
      try {
        const payload = JSON.parse(event.data);
        setChamados((prev) => prev.filter((c) => c.id !== payload.chamadoId));
      } catch (err) { console.error(err); }
    });

    eventSource.addEventListener('kds.status_changed', (event) => {
      try {
        const payload = JSON.parse(event.data);
        const { itemPedidoId, status } = payload;
        const mode: ServiceMode = (payload.serviceMode as ServiceMode) || 'waiter_delivery';
        const isDeliveryMode = mode === 'waiter_delivery' || mode === 'DELIVERY';
        const necessitaPreparacao = payload.necessitaPreparacao;
        const currentTicket = ticketMapRef.current[itemPedidoId];

        // Atualiza cache de tickets, se existir
        setTicketMap((prev) => {
          const existing = prev[itemPedidoId];
          if (!existing) return prev;
          return { ...prev, [itemPedidoId]: { ...existing, status } };
        });

        // Determina se devemos tratar como pronto
        const isReadyLike = status === 'ready' || (status === 'accepted' && necessitaPreparacao === false);

        // Remove da lista se saiu de pronto
        if (!isReadyLike) {
          readyNotifiedRef.current.delete(itemPedidoId);
          setReadyItems((prev) => prev.filter((r) => r.itemPedidoId !== itemPedidoId));
          return;
        }

        // Adiciona na lista de prontos
        setReadyItems((prev) => {
          if (prev.some((r) => r.itemPedidoId === itemPedidoId)) return prev;

          const ticket = currentTicket || ticketMapRef.current[itemPedidoId];
          if (!ticket) {
            // Se não tivermos detalhes, recarrega tickets
            fetchReadyItems();
            return prev;
          }

          readyNotifiedRef.current.add(itemPedidoId);

          const newCard = mapTicketToReadyItem({ ...ticket, status: 'ready' });

          // Notifica apenas se modo estiver configurado para garçom
          if (isDeliveryMode) {
            playSound();

            const orderLabel = ticket.pedidoId ? `Pedido #${ticket.pedidoId}` : `Item #${itemPedidoId}`;
            const mesaLabel = ticket.mesa.rotulo || ticket.mesa.slug || 'mesa';
            const hasMultipleItems = Number(payload.pedidoItemCount || ticket.pedido?.itemCount || 0) > 1;
            const itemLabel = hasMultipleItems ? 'Item do pedido pronto' : 'Pedido pronto';
            const description = `${orderLabel} da ${mesaLabel} está pronto para entrega.`;

            toast({
              title: `🍽️ ${itemLabel}`,
              description,
              duration: 6000,
            });

            if (permission === 'granted') {
              showNotification({
                title: itemLabel,
                body: description,
              });
            }
          }

          return [...prev, newCard];
        });
      } catch (err) {
        console.error('Error parsing kds.status_changed:', err);
      }
    });

    waiterEvents.addEventListener('payment.updated', (event) => {
      try {
        const payload = JSON.parse(event.data);
        const status = String(payload?.status || '').toLowerCase();
        if (status === 'paid') {
          const mesaLabel = payload?.mesaRotulo || payload?.mesaSlug || 'mesa';
          toast({
            title: 'Pagamento confirmado',
            description: `Pagamento recebido na ${mesaLabel}.`,
            duration: 4000,
          });
        } else if (status === 'pending') {
          const mesaLabel = payload?.mesaRotulo || payload?.mesaSlug || 'mesa';
          toast({
            title: 'Pagamento pendente',
            description: `Pagamento iniciado na ${mesaLabel}.`,
            duration: 4000,
          });
        }
        fetchPayments();
      } catch (err) {
        console.error('[Waiter SSE] payment.updated parse error', err);
      }
    });

    return () => {
      eventSource.close();
      waiterEvents.close();
    };
  }, [permission, showNotification, showResolved]);

  return (
    <div className={`min-h-screen bg-soft-white flex flex-col ${mesaTextColor}`}>
      {/* Mobile Header */}
      <header className="bg-[hsl(var(--background))] text-[hsl(var(--foreground))] p-4 sticky top-0 z-20 shadow-md">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-xl font-display tracking-wider">Empório Garçom</h1>
            {activeTab === 'chamados' && (
              <p className="text-xs text-foreground/70">
                {chamados.length} chamado(s) pendente(s)
              </p>
            )}
            {activeTab === 'pagamentos' && (
              <p className="text-xs text-foreground/70">
                {payments.length} pagamento(s) recente(s)
              </p>
            )}
            {activeTab === 'mesas' && (
              <p className="text-xs text-foreground/70">
                Gestão de Mesas
              </p>
            )}
          </div>
          <div className="flex items-center gap-2">
            {isAuthenticated && user && (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button
                    type="button"
                    className="flex items-center gap-2 rounded-md border border-border/40 px-3 py-2 text-xs font-medium uppercase tracking-[0.15em] text-foreground transition-colors hover:border-border/60 hover:bg-[hsl(var(--background)/0.9)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
                  >
                    <User className="h-4 w-4 text-foreground" />
                    <span>{user.nome?.split(' ')[0] || 'Você'}</span>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-48 bg-card border-border">
                  {canAccessAdminPanel && (
                    <>
                      <DropdownMenuItem
                        onClick={() => navigate('/admin/dashboard')}
                        className="cursor-pointer hover:bg-accent/10"
                      >
                        <Settings className="w-4 h-4 mr-2" />
                        <span>Painel Admin</span>
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                    </>
                  )}
                  {canAccessKdsPanel && (
                    <>
                      <DropdownMenuItem
                        onClick={() => navigate('/kds')}
                        className="cursor-pointer hover:bg-accent/10"
                      >
                        <Monitor className="w-4 h-4 mr-2" />
                        <span>KDS</span>
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                    </>
                  )}
                  {canAccessWaiterPanel && (
                    <>
                      <DropdownMenuItem
                        onClick={() => navigate('/waiter')}
                        className="cursor-pointer hover:bg-accent/10"
                      >
                        <UtensilsCrossed className="w-4 h-4 mr-2" />
                        <span>Painel Waiter</span>
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                    </>
                  )}
                  <DropdownMenuItem
                    onClick={logout}
                    className="cursor-pointer hover:bg-accent/10"
                  >
                    <LogOut className="w-4 h-4 mr-2" />
                    <span>Sair</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            )}
            <Button
              variant="ghost"
              size="icon"
              onClick={toggleSound}
              className="text-white hover:bg-white/10"
            >
              {soundEnabled ? <Volume2 className="h-6 w-6" /> : <VolumeX className="h-6 w-6" />}
            </Button>
          </div>
        </div>
      </header>

      {/* Main Content with Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1 flex flex-col">
        
        {/* Permission Request Alert */}
        {permission !== 'granted' && (
          <div className={`p-4 border-b ${mesaSurfaceAccent}`}>
            <p className={`text-sm mb-2 ${mesaTextColorMuted}`}>
              Ative as notificações para receber alertas mesmo com a tela bloqueada.
            </p>
            <Button 
              onClick={requestPermission} 
              size="sm" 
              variant="outline"
              className="w-full border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]"
            >
              Ativar Notificações
            </Button>
          </div>
        )}

        <div className="flex-1 overflow-y-auto pb-24">
          <TabsContent value="chamados" className="m-0 p-4 space-y-4 h-full">
            {loading ? (
              [1, 2, 3].map((i) => <Skeleton key={i} className="h-32 w-full rounded-xl" />)
            ) : readyItems.length === 0 && chamados.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-20 text-center">
                <CheckCircle className={`h-16 w-16 mb-4 opacity-20 ${mesaTextColor}`} />
                <h3 className={`text-xl font-display tracking-wider mb-2 ${mesaTextColor}`}>
                  Tudo tranquilo por aqui
                </h3>
                <p className={`text-sm ${mesaTextColorLight}`}>Aguardando chamados ou pedidos prontos...</p>
              </div>
            ) : (
              <div className="space-y-6">
                {readyItems.length > 0 && (
                  <div className="space-y-3">
                    <div className={`flex items-center gap-2 text-sm font-semibold uppercase ${mesaTextColorMuted}`}>
                      <UtensilsCrossed className="h-4 w-4" />
                      <span>Pedidos prontos</span>
                    </div>
                    {readyItems.map((item) => (
                      <Card
                        key={item.itemPedidoId}
                        className="overflow-hidden border-l-[4px] border-l-emerald-500 shadow-lg animate-in slide-in-from-bottom-2 bg-white text-mesa-text border-accent/20"
                      >
                        <div className="p-5">
                          <div className="flex justify-between items-start mb-2">
                            <div>
                              <h2 className={`text-2xl font-bold ${mesaTextColor}`}>
                                {item.mesaRotulo || item.mesaSlug}
                              </h2>
                              {item.mesaReferencia && (
                                <p className={`text-xs mt-1 ${mesaTextColorMuted}`}>
                                  {item.mesaReferencia}
                                </p>
                              )}
                              <p className={`text-xs mt-1 ${mesaTextColorMuted}`}>
                                {item.pedidoId ? `Pedido #${item.pedidoId}` : `Item #${item.itemPedidoId}`}
                              </p>
                            </div>
                            <span className={`text-xs font-mono ${mesaTextColorMuted} bg-white/80 border border-accent/20 px-2 py-1 rounded`}>
                              {new Date(item.atualizadoEm || Date.now()).toLocaleTimeString([], { hour: '2-digit', minute:'2-digit' })}
                            </span>
                          </div>

                          <div className="mb-6">
                            <div className="flex items-center gap-2 mb-1 text-emerald-600 font-bold uppercase tracking-wide">
                              🍽️ Pedido pronto
                            </div>
                            <p className={`text-sm ${mesaTextColorMuted}`}>
                              {item.quantidade}x {item.nome}
                            </p>
                            {item.observacoes && (
                              <p className={`italic bg-white/70 p-2 rounded mt-2 border border-accent/10 ${mesaTextColorMuted}`}>
                                "{item.observacoes}"
                              </p>
                            )}
                          </div>

                          <Button
                            size="lg"
                            className="w-full text-lg h-14 font-semibold shadow-md bg-emerald-500 hover:bg-emerald-600 text-white"
                            onClick={() => handleDeliverReadyItem(item.itemPedidoId)}
                          >
                            ENTREGAR
                          </Button>
                        </div>
                      </Card>
                    ))}
                  </div>
                )}

                {chamados.length > 0 && (
                  <div className="space-y-3">
                    <div className={`flex items-center gap-2 text-sm font-semibold uppercase ${mesaTextColorMuted}`}>
                      <Bell className="h-4 w-4" />
                      <span>Chamados</span>
                    </div>
                    {chamados.map((chamado) => (
                      <Card
                        key={chamado.id}
                        className={`overflow-hidden border-l-[4px] shadow-lg animate-in slide-in-from-bottom-2 bg-white text-mesa-text border-accent/20 ${
                          chamado.tipo === 'conta' ? 'border-l-yellow-500' : 'border-l-[hsl(var(--accent))]'
                        }`}
                      >
                        <div className="p-5">
                          <div className="flex justify-between items-start mb-2">
                            <div>
                              <h2 className={`text-2xl font-bold ${mesaTextColor}`}>
                                {chamado.mesaRotulo || chamado.mesaSlug}
                              </h2>
                              {chamado.mesaReferencia && (
                                <p className={`text-xs mt-1 ${mesaTextColorMuted}`}>
                                  {chamado.mesaReferencia}
                                </p>
                              )}
                            </div>
                            <span className={`text-xs font-mono ${mesaTextColorMuted} bg-white/80 border border-accent/20 px-2 py-1 rounded`}>
                              {new Date(chamado.criadoEm).toLocaleTimeString([], { hour: '2-digit', minute:'2-digit' })}
                            </span>
                          </div>
                          
                          <div className="mb-6">
                            <div className="flex items-center gap-2 mb-1">
                              {chamado.tipo === 'conta' ? (
                                <span className="text-yellow-600 font-bold uppercase tracking-wide flex items-center gap-1">
                                  💰 Solicitou fechamento
                                </span>
                              ) : (
                                <span className="text-[hsl(var(--accent))] font-bold uppercase tracking-wide flex items-center gap-1">
                                  🔔 Chamou Garçom
                                </span>
                              )}
                            </div>
                            {chamado.observacao && (
                              <p className={`italic bg-white/70 p-2 rounded mt-2 border border-accent/10 ${mesaTextColorMuted}`}>
                                "{chamado.observacao}"
                              </p>
                            )}
                          </div>

                          <Button
                            size="lg"
                            className={`w-full text-lg h-14 font-semibold shadow-md ${
                              chamado.tipo === 'conta' 
                                ? 'bg-[hsl(var(--accent))] hover:bg-accent/90 text-[hsl(var(--foreground))]' 
                                : 'bg-accent hover:bg-accent/90 text-mesa-text'
                            }`}
                            onClick={() => handleAtenderChamado(chamado)}
                          >
                            {chamado.tipo === 'conta' ? 'LIBERAR PAGAMENTO' : 'ATENDER'}
                          </Button>
                        </div>
                      </Card>
                    ))}
                  </div>
                )}
              </div>
            )}
          </TabsContent>

          {canSeePagamentos && (
            <TabsContent value="pagamentos" className="m-0 p-4 space-y-4 h-full">
            {loading ? (
              [1, 2, 3].map((i) => <Skeleton key={i} className="h-32 w-full rounded-xl" />)
            ) : payments.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-20 text-center">
                <CheckCircle className={`h-16 w-16 mb-4 opacity-20 ${mesaTextColor}`} />
                <h3 className={`text-xl font-display tracking-wider mb-2 ${mesaTextColor}`}>
                  Nenhum pagamento recente
                </h3>
                <p className={`text-sm ${mesaTextColorLight}`}>Aguardando pagamentos da mesa...</p>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    variant={paymentFilter === 'pending' ? 'secondary' : 'outline'}
                    onClick={() => setPaymentFilter('pending')}
                  >
                    Pendentes
                  </Button>
                  <Button
                    size="sm"
                    variant={paymentFilter === 'paid' ? 'secondary' : 'outline'}
                    onClick={() => setPaymentFilter('paid')}
                  >
                    Confirmados
                  </Button>
                  <Button
                    size="sm"
                    variant={paymentFilter === 'all' ? 'secondary' : 'outline'}
                    onClick={() => setPaymentFilter('all')}
                  >
                    Todos
                  </Button>
                  <Button
                    size="sm"
                    variant={showResolved ? 'secondary' : 'outline'}
                    onClick={() => setShowResolved((prev) => !prev)}
                  >
                    {showResolved ? 'Arquivados' : 'Pendentes de ação'}
                  </Button>
                </div>
                {payments.filter((payment) => {
                  const status = (payment.status || '').toLowerCase();
                  if (paymentFilter === 'all') return true;
                  return status === paymentFilter;
                }).map((payment) => {
                  const status = (payment.status || '').toLowerCase();
                  const statusLabel =
                    status === 'paid' ? 'Confirmado' : status === 'pending' ? 'Pendente' : status || 'Desconhecido';
                  const mesaLabel = payment.mesaRotulo || payment.mesaSlug || `Mesa ${payment.sessaoMesaId ?? ''}`;
                  return (
                    <Card
                      key={payment.pagamentoId}
                      className="overflow-hidden border-l-[4px] shadow-lg bg-white text-mesa-text border-accent/20"
                    >
                      <div className="p-5 space-y-3">
                        <div className="flex justify-between items-start">
                          <div>
                            <h2 className={`text-2xl font-bold ${mesaTextColor}`}>{mesaLabel}</h2>
                            <p className={`text-xs mt-1 ${mesaTextColorMuted}`}>
                              {payment.convidado || payment.pagante || 'Cliente'}
                            </p>
                          </div>
                          <span className={`text-xs font-mono ${mesaTextColorMuted} bg-white/80 border border-accent/20 px-2 py-1 rounded`}>
                            {new Date(payment.criadoEm || Date.now()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </span>
                        </div>
                        <div className="flex flex-wrap items-center gap-3 text-sm">
                          <span className={`px-2 py-1 rounded-full text-xs font-semibold ${status === 'paid' ? 'bg-emerald-100 text-emerald-700' : 'bg-yellow-100 text-yellow-700'}`}>
                            {statusLabel}
                          </span>
                          <span className="text-xs uppercase tracking-wide text-muted-foreground">
                            {payment.metodo === 'pix' ? 'PIX' : 'Cartão'}
                          </span>
                          <span className="text-sm font-semibold">{formatCurrency(payment.valor as number)}</span>
                        </div>
                        <div className="grid grid-cols-1 gap-2">
                          <Button
                            variant="secondary"
                            onClick={() => handleComprovante(payment)}
                            disabled={status !== 'paid'}
                          >
                            Comprovante não fiscal
                          </Button>
                          <Button
                            variant="outline"
                            onClick={() => handleEmitirNfce(payment.pagamentoId)}
                            disabled={status !== 'paid'}
                          >
                            Emitir NFC-e
                          </Button>
                          <Button
                            variant="outline"
                            onClick={() => handleArquivarPagamento(payment.pagamentoId, false)}
                          >
                            Arquivar
                          </Button>
                        </div>
                      </div>
                    </Card>
                  );
                })}
              </div>
            )}
            </TabsContent>
          )}

          <TabsContent value="mesas" className="m-0 h-full">
            <MesasGrid isWaiterMode />
          </TabsContent>
        </div>

        {/* Bottom Navigation Bar */}
        <TabsList className="fixed bottom-0 left-0 right-0 h-20 bg-background border-t border-border/30 flex justify-around items-center p-0 z-30 rounded-none shadow-[0_-5px_15px_rgba(0,0,0,0.1)]">
          <TabsTrigger 
            value="chamados" 
            className="flex-1 h-full flex flex-col gap-1 data-[state=active]:bg-accent/20 data-[state=active]:text-accent text-foreground/60 rounded-none transition-colors"
          >
            <div className="relative">
              <Bell className="h-6 w-6" />
              {chamados.length + readyItems.length > 0 && (
                <span className={`absolute -top-2 -right-2 bg-accent ${mesaTextColor} text-[10px] font-bold h-5 w-5 flex items-center justify-center rounded-full animate-pulse border-2 border-border`}>
                  {chamados.length + readyItems.length}
                </span>
              )}
            </div>
            <span className="text-xs font-medium">Chamados</span>
          </TabsTrigger>
          
          {canSeePagamentos && (
            <TabsTrigger 
              value="pagamentos" 
              className="flex-1 h-full flex flex-col gap-1 data-[state=active]:bg-accent/20 data-[state=active]:text-accent text-foreground/60 rounded-none transition-colors"
            >
              <div className="relative">
                <CreditCard className="h-6 w-6" />
                {activePaymentsCount > 0 && (
                  <span className={`absolute -top-2 -right-2 bg-accent ${mesaTextColor} text-[10px] font-bold h-5 w-5 flex items-center justify-center rounded-full animate-pulse border-2 border-border`}>
                    {activePaymentsCount}
                  </span>
                )}
              </div>
              <span className="text-xs font-medium">Self checkout</span>
            </TabsTrigger>
          )}

          <TabsTrigger 
            value="mesas" 
            className="flex-1 h-full flex flex-col gap-1 data-[state=active]:bg-accent/20 data-[state=active]:text-accent text-foreground/60 rounded-none transition-colors"
          >
            <UtensilsCrossed className="h-6 w-6" />
            <span className="text-xs font-medium">Mesas & Pedidos</span>
          </TabsTrigger>
        </TabsList>
      </Tabs>
      <Dialog open={showComprovanteModal} onOpenChange={(open) => {
        setShowComprovanteModal(open);
        if (!open && comprovantePdfUrl) {
          URL.revokeObjectURL(comprovantePdfUrl);
          setComprovantePdfUrl(null);
        }
      }}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Comprovante não fiscal</DialogTitle>
          </DialogHeader>
          <div className="w-full h-[75vh]">
            {comprovantePdfUrl ? (
              <iframe
                title="Comprovante PDF"
                src={comprovantePdfUrl}
                className="w-full h-full rounded border"
              />
            ) : (
              <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
                Carregando comprovante...
              </div>
            )}
          </div>
          <DialogFooter className="flex flex-wrap gap-2 justify-end">
            <Button
              variant="outline"
              onClick={() => {
                const iframe = document.querySelector('iframe[title="Comprovante PDF"]') as HTMLIFrameElement | null;
                iframe?.contentWindow?.print();
              }}
              disabled={!comprovantePdfUrl}
            >
              Imprimir
            </Button>
            <Button
              variant="secondary"
              onClick={() => openWhatsappDialog(comprovantePdfUrl)}
              disabled={!comprovantePdfUrl}
            >
              Enviar por WhatsApp
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={showWhatsappDialog} onOpenChange={setShowWhatsappDialog}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Enviar comprovante</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <Label htmlFor="whatsapp-input" className="text-[#2A1F1B]">Número do WhatsApp</Label>
            <Input
              id="whatsapp-input"
              value={whatsappPhone}
              onChange={(e) => setWhatsappPhone(e.target.value)}
              placeholder="(11) 99999-9999"
            />
          </div>
          <DialogFooter>
            <Button
              variant="secondary"
              onClick={() => handleEnviarWhatsapp(whatsappTargetPdfUrl)}
              disabled={!whatsappPhone || whatsappPhone.replace(/\D/g, '').length < 10 || sendingWhatsapp}
            >
              {sendingWhatsapp ? 'Enviando...' : 'Enviar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
