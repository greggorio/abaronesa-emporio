import { KdsTicket, StatusItem } from '@/types/kds';
import { TicketCard } from './TicketCard';
import { CheckCircle } from 'lucide-react';
import { useTheme } from '@/contexts/ThemeContext';

interface TicketGridProps {
  tickets: KdsTicket[];
  onStatusUpdate?: (ticket: KdsTicket, newStatus: StatusItem, motivoCodigo?: string, motivoDetalhe?: string) => Promise<void>;
  onArchiveDelivery?: (ticket: KdsTicket) => Promise<void> | void;
  onPrint?: (ticket: KdsTicket) => Promise<void>;
}

export function TicketGrid({ tickets, onStatusUpdate, onArchiveDelivery, onPrint }: TicketGridProps) {
  const { theme } = useTheme();
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text))]' : 'text-foreground';
  const mesaTextColorLight = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text)/0.4)]' : 'text-foreground/40';

  // Empty state
  if (tickets.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <CheckCircle className={`h-16 w-16 mb-4 opacity-20 ${mesaTextColor}`} />
        <h3 className={`text-xl font-display tracking-wider mb-2 ${mesaTextColor}`}>
          Nenhum pedido pendente
        </h3>
        <p className={`text-sm ${mesaTextColorLight}`}>Aguardando novos pedidos...</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
      {tickets.map((ticket) => (
        <TicketCard
          key={ticket.tipo === 'delivery' ? `delivery-${ticket.deliveryItemId ?? ticket.itemPedidoId}` : `mesa-${ticket.itemPedidoId}`}
          ticket={ticket}
          onStatusUpdate={onStatusUpdate}
          onArchive={onArchiveDelivery}
          onPrint={onPrint}
        />
      ))}
    </div>
  );
}
