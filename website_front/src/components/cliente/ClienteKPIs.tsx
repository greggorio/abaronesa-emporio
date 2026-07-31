import { Trophy, History, Gift, CalendarDays } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';

interface ClienteKPIsProps {
  pontos: number;
  totalVisitas?: number;
  eventosTotal?: number;
  proximoEventoTitulo?: string;
  proximoEventoData?: string;
  onEventosClick?: () => void;
  onFaturaClick?: () => void;
  onGamificacaoClick?: () => void;
  onRewardsClick?: () => void;
  rewardsTotal?: number;
  rewardsAvailable?: number;
}

export default function ClienteKPIs({
  pontos,
  totalVisitas,
  eventosTotal = 0,
  proximoEventoTitulo,
  proximoEventoData,
  onEventosClick,
  onGamificacaoClick,
  onRewardsClick,
  rewardsTotal = 0,
  rewardsAvailable = 0,
}: ClienteKPIsProps) {
  
  const hasAvailableRewards = rewardsAvailable > 0;
  const hasEventos = eventosTotal > 0 && !!proximoEventoTitulo && !!proximoEventoData;

  const formatEventDate = (value?: string) => {
    if (!value) return '';
    return new Date(value).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: 'short',
    });
  };

  const formatEventosCount = (total: number) => {
    if (total === 0) return 'Nenhum evento';
    if (total === 1) return '1 evento';
    return `${total} eventos`;
  };

  const kpis = [
    {
      title: 'VISITAS REALIZADAS',
      value: `${totalVisitas || 0} visitas`,
      subtitle: 'nos últimos 3 meses',
      icon: History,
      colorClass: 'text-[hsl(var(--accent))]',
      bgClass: 'bg-[hsl(var(--accent)/0.1)]',
      borderClass: 'border-[hsl(var(--accent)/0.2)]'
    },
    {
      title: 'PRÓXIMOS EVENTOS',
      value: formatEventosCount(eventosTotal),
      subtitle: hasEventos
        ? `${proximoEventoTitulo} • ${formatEventDate(proximoEventoData)}`
        : 'Sem eventos agendados',
      icon: CalendarDays,
      colorClass: 'text-[hsl(var(--accent))]',
      bgClass: 'bg-[hsl(var(--accent)/0.1)]',
      borderClass: 'border-[hsl(var(--accent)/0.2)]',
      onClick: onEventosClick
    },
    {
      title: 'GAMIFICAÇÃO',
      value: `${pontos} Pontos`,
      subtitle: 'Nível Iniciante',
      icon: Trophy,
      colorClass: 'text-[hsl(var(--accent))]',
      bgClass: 'bg-[hsl(var(--accent)/0.1)]',
      borderClass: 'border-[hsl(var(--accent)/0.2)]',
      onClick: onGamificacaoClick
    },
    {
      title: 'RECOMPENSAS',
      value: rewardsTotal === 0 ? 'Nenhuma recompensa' : rewardsTotal === 1 ? '1 recompensa' : `${rewardsTotal} recompensas`,
      subtitle: hasAvailableRewards ? `${rewardsAvailable} disponível(is)` : 'Sorteios e vouchers',
      icon: Gift,
      colorClass: hasAvailableRewards ? 'text-emerald-600' : 'text-[hsl(var(--accent))]',
      bgClass: hasAvailableRewards ? 'bg-emerald-50' : 'bg-[hsl(var(--accent)/0.1)]',
      borderClass: hasAvailableRewards ? 'border-emerald-200' : 'border-[hsl(var(--accent)/0.2)]',
      onClick: onRewardsClick
    }
  ];

  const filtered = kpis.filter(Boolean) as typeof kpis;

  return (
    <div className="grid grid-cols-2 gap-3">
      {filtered.map((kpi, index) => {
        const Icon = kpi.icon;
        return (
          <Card 
            key={index} 
            className={`border ${kpi.borderClass} bg-white shadow-sm hover:shadow-md transition-all relative overflow-hidden group ${kpi.onClick ? 'cursor-pointer' : ''}`}
            onClick={kpi.onClick}
          >
            <CardContent className="p-4 flex flex-col h-full justify-between gap-3">
              {/* Header: Icon + Title */}
              <div className="flex items-center gap-2">
                <div className={`p-1.5 rounded-lg ${kpi.bgClass} shrink-0`}>
                  <Icon className={`h-4 w-4 ${kpi.colorClass}`} />
                </div>
                <span className="text-[10px] font-bold tracking-wider text-mesa-text/60 uppercase truncate">
                  {kpi.title}
                </span>
              </div>

              {/* Body: Value */}
              <div className="space-y-1">
                <div className="flex items-center justify-between gap-2">
                  <div className="text-lg font-bold text-mesa-text leading-tight truncate">
                    {kpi.value}
                  </div>
                  {kpi.title === 'RECOMPENSAS' && hasAvailableRewards && (
                    <span className="text-[10px] font-semibold text-emerald-700 bg-emerald-100 border border-emerald-200 px-2 py-0.5 rounded-full">
                      NOVO
                    </span>
                  )}
                </div>
                {/* Footer: Subtitle */}
                <div className="text-xs text-mesa-text/70 truncate">
                  {kpi.subtitle}
                </div>
              </div>
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
