import { useState } from 'react';
import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { KdsTicket, StatusItem } from '@/types/kds';
import { formatDistanceToNow } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { MoreVertical, Archive, Printer } from 'lucide-react';

interface TicketCardProps {
  ticket: KdsTicket;
  onStatusUpdate?: (ticket: KdsTicket, newStatus: StatusItem, motivoCodigo?: string, motivoDetalhe?: string) => Promise<void>;
  onArchive?: (ticket: KdsTicket) => Promise<void> | void;
  onPrint?: (ticket: KdsTicket) => Promise<void>;
}

/**
 * Returns Tailwind CSS classes for status badge based on item status
 */
function getStatusColor(status: StatusItem): string {
  const statusColors: Record<StatusItem, string> = {
    queued: 'bg-background/10 text-foreground border-border/30',
    accepted: 'bg-background/20 text-foreground border-border/40',
    preparing: 'bg-accent/20 text-accent border-accent/40',
    ready: 'bg-success text-accent-foreground border-success font-medium', // Ajuste para usar cores genéricas
    delivered: 'bg-muted/10 text-muted-foreground border-border/30',
    canceled: 'bg-destructive/20 text-destructive border-destructive/40',
  };
  return statusColors[status];
}

/**
 * Returns status label in Portuguese
 */
function getStatusLabel(status: StatusItem, isDelivery: boolean): string {
  const statusLabels: Record<StatusItem, string> = {
    queued: 'Aguardando',
    accepted: 'Aceito',
    preparing: 'Preparando',
    ready: 'Pronto',
    delivered: isDelivery ? 'Retirado' : 'Entregue',
    canceled: 'Cancelado',
  };
  return statusLabels[status];
}

/**
 * Formats ISO timestamp to relative time (e.g., "5 min atrás")
 */
function formatTimestamp(isoString: string): string {
  try {
    const date = new Date(isoString);
    return formatDistanceToNow(date, { addSuffix: true, locale: ptBR });
  } catch {
    return isoString;
  }
}

/**
 * Returns available actions for current status
 * @param status Current status of the item
 * @param necessitaPreparacao Whether the product requires preparation (default: true)
 */
function getAvailableActions(
  status: StatusItem,
  isDelivery: boolean,
  necessitaPreparacao: boolean = true,
  uberStatus?: string | null
): { status: StatusItem; label: string; variant: 'default' | 'outline' | 'destructive' }[] {
  if (isDelivery) {
    const uberCanceled = uberStatus === 'canceled' || uberStatus === 'cancelled' || uberStatus === 'failed';
    if (uberCanceled) {
      return [];
    }
    const deliveryActions: Record<StatusItem, { status: StatusItem; label: string; variant: 'default' | 'outline' | 'destructive' }[]> = {
      queued: [
        { status: 'accepted', label: 'Aceitar', variant: 'default' },
        { status: 'canceled', label: 'Cancelar', variant: 'destructive' },
      ],
      accepted: [{ status: 'preparing', label: 'Preparar', variant: 'default' }],
      preparing: [{ status: 'ready', label: 'Pronto', variant: 'default' }],
      ready: [{ status: 'delivered', label: 'Retirado', variant: 'default' }],
      delivered: [],
      canceled: [],
    };
    return deliveryActions[status] || [];
  }

  const actions: Record<StatusItem, { status: StatusItem; label: string; variant: 'default' | 'outline' | 'destructive' }[]> = {
    queued: [
      { status: 'accepted', label: 'Aceitar', variant: 'default' },
      { status: 'canceled', label: 'Cancelar', variant: 'destructive' },
    ],
    accepted: necessitaPreparacao
      ? [{ status: 'preparing', label: 'Preparar', variant: 'default' }]
      : [{ status: 'delivered', label: 'Entregar', variant: 'default' }],
    preparing: [
      { status: 'ready', label: 'Pronto', variant: 'default' },
    ],
    ready: [
      { status: 'delivered', label: 'Entregar', variant: 'default' },
    ],
    delivered: [],
    canceled: [],
  };
  return actions[status] || [];
}

export function TicketCard({ ticket, onStatusUpdate, onArchive, onPrint }: TicketCardProps) {
  const { item, mesa, pedido, status, pedidoId, itemPedidoId } = ticket;
  const [loading, setLoading] = useState(false);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelReasonCode, setCancelReasonCode] = useState<string>('OUTRO');
  const [archiving, setArchiving] = useState(false);
  const [printing, setPrinting] = useState(false);

  const doUpdate = async (newStatus: StatusItem, motivoCodigo?: string, motivoDetalhe?: string) => {
    if (!onStatusUpdate) return;

    setLoading(true);
    try {
      await onStatusUpdate(ticket, newStatus, motivoCodigo, motivoDetalhe);
    } catch (error) {
      console.error('Erro ao atualizar status:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleStatusChange = async (newStatus: StatusItem) => {
    if (newStatus === 'canceled') {
      setCancelDialogOpen(true);
      return;
    }
    await doUpdate(newStatus);
  };

  const confirmCancel = async () => {
    const detalhe = cancelReason.trim() || undefined;
    await doUpdate('canceled', cancelReasonCode, detalhe);
    setCancelReason('');
    setCancelReasonCode('OUTRO');
    setCancelDialogOpen(false);
  };

  const uberStatus = ticket.delivery?.status?.toLowerCase();
  const actions = getAvailableActions(status, ticket.tipo === 'delivery', item.necessitaPreparacao ?? true, uberStatus);
  const canArchive = ticket.tipo === 'delivery'
    && (uberStatus === 'canceled' || uberStatus === 'cancelled' || uberStatus === 'failed');

  const handleArchive = async () => {
    if (!onArchive) return;
    setArchiving(true);
    try {
      await onArchive(ticket);
    } catch (error) {
      console.error('Erro ao arquivar delivery:', error);
    } finally {
      setArchiving(false);
    }
  };

  const handlePrint = async () => {
    if (!onPrint || ticket.tipo === 'delivery') return;
    setPrinting(true);
    try {
      await onPrint(ticket);
    } catch (error) {
      console.error('Erro ao imprimir:', error);
    } finally {
      setPrinting(false);
    }
  };

  const cardStyle = ticket.estacao === 'bar'
    ? 'border-l-primary'
    : 'border-l-accent';

  return (
    <Card className={`w-full bg-card border border-border/20 ${cardStyle} border-l-4 transition-all hover:shadow-lg shadow-md rounded-lg overflow-visible`}>
      {/* Header: Mesa, Pedido #, Timestamp */}
      <CardHeader className="pb-3">
        <div className="grid grid-cols-[1fr_auto] gap-3 items-start">
          <div className="min-w-0">
            <h3 className="font-display text-lg tracking-wider text-foreground">
              {mesa.rotulo || mesa.slug}
            </h3>
            <p className="text-sm text-foreground/60">
              Pedido #{pedidoId}
            </p>
            {mesa.referencia && (
              <p className="text-xs text-foreground/60 mt-1">
                {mesa.referencia}
              </p>
            )}
          </div>
          <div className="flex flex-col items-end gap-1">
            <span className="text-xs text-foreground/50 text-right leading-tight max-w-[110px] break-words">
              {formatTimestamp(pedido.criadoEm)}
            </span>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="outline"
                  size="icon"
                  className="h-7 w-7 border-border/30 text-foreground/80 bg-card hover:bg-accent"
                >
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent
                align="end"
                side="bottom"
                sideOffset={6}
                collisionPadding={12}
                className="bg-card border-border/30 z-50"
              >
                <DropdownMenuItem
                  onClick={handleArchive}
                  disabled={!canArchive || archiving}
                  className={`gap-2 ${canArchive && !archiving ? 'cursor-pointer' : 'cursor-not-allowed text-foreground/50'}`}
                >
                  <Archive className="h-4 w-4" />
                  {archiving ? 'Arquivando...' : 'Arquivar'}
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={handlePrint}
                  disabled={!onPrint || ticket.tipo === 'delivery' || printing}
                  className={`gap-2 ${(!onPrint || ticket.tipo === 'delivery') ? 'cursor-not-allowed text-foreground/50' : ''}`}
                >
                  <Printer className="h-4 w-4" />
                  {printing ? 'Enviando...' : 'Imprimir'}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </CardHeader>

      {/* Content: Item name, quantity, observations */}
      <CardContent className="pb-3 space-y-3">
        <div>
          <p className="text-base font-medium text-foreground">
            {item.quantidade}x {item.nome}
          </p>
        </div>

        {/* Observations (highlighted) */}
        {item.observacoes && (
          <div className="bg-destructive/10 border border-destructive/30 rounded p-2">
            <div className="flex items-start gap-2">
              <span className="text-base">📝</span>
              <p className="text-sm font-medium text-foreground break-words">
                {item.observacoes}
              </p>
            </div>
          </div>
        )}
      </CardContent>

      {/* Footer: Status badge and action buttons */}
      <CardFooter className="pt-0 flex-col items-start gap-3">
        <Badge className={getStatusColor(status)}>
          {getStatusLabel(status, ticket.tipo === 'delivery')}
        </Badge>
        {ticket.tipo === 'delivery' && ticket.delivery?.status && (
          <Badge className="bg-muted/10 text-foreground border-border/30">
            Uber: {ticket.delivery.status}
          </Badge>
        )}

        {actions.length > 0 && (
          <div className="flex gap-2 w-full">
            {actions.map((action) => (
              <Button
                key={action.status}
                variant={action.variant === 'destructive' ? 'outline' : 'default'}
                size="sm"
                onClick={() => handleStatusChange(action.status)}
                disabled={loading}
                className={`flex-1 font-medium ${
                  action.variant === 'destructive'
                    ? 'bg-card border-destructive/40 text-destructive hover:bg-destructive/10'
                    : 'bg-accent text-accent-foreground hover:bg-accent/90 border-0 shadow-sm'
                }`}
              >
                {loading ? 'Aguarde...' : action.label}
              </Button>
            ))}
          </div>
        )}

      </CardFooter>

      <Dialog open={cancelDialogOpen} onOpenChange={setCancelDialogOpen}>
        <DialogContent className="bg-card text-foreground border border-border/20">
          <DialogHeader>
            <DialogTitle>Cancelar item</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <p className="text-sm text-foreground/80">
              Informe o motivo do cancelamento para registro.
            </p>
            <div className="space-y-1">
              <Label className="text-xs text-foreground/70">Motivo</Label>
              <Select value={cancelReasonCode} onValueChange={setCancelReasonCode}>
                <SelectTrigger className="bg-background border-border/30 text-foreground">
                  <SelectValue placeholder="Selecione um motivo" />
                </SelectTrigger>
                <SelectContent className="bg-background text-foreground border border-border/30">
                  <SelectItem value="FALTA_INSUMO">Falta de insumo</SelectItem>
                  <SelectItem value="EQUIPE_INDISPONIVEL">Equipe indisponível</SelectItem>
                  <SelectItem value="ERRO_PEDIDO">Erro de pedido</SelectItem>
                  <SelectItem value="CLIENTE_DESISTIU">Cliente desistiu</SelectItem>
                  <SelectItem value="OUTRO">Outro</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {cancelReasonCode === 'OUTRO' && (
              <div className="space-y-1">
                <Label className="text-xs text-foreground/70">Observação</Label>
                <Textarea
                  value={cancelReason}
                  onChange={(e) => setCancelReason(e.target.value)}
                  placeholder="Ex.: Falta de insumo, cliente desistiu, erro no pedido..."
                  className="min-h-[100px] bg-background border-border/30"
                />
              </div>
            )}
          </div>
          <DialogFooter className="gap-2">
            <Button
              variant="outline"
              onClick={() => {
                setCancelDialogOpen(false);
                setCancelReason('');
              }}
              className="border-border/30 text-foreground bg-card hover:bg-accent"
            >
              Voltar
            </Button>
            <Button
              onClick={confirmCancel}
              disabled={loading}
              className="bg-accent text-accent-foreground hover:bg-accent/90"
            >
              Confirmar cancelamento
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
