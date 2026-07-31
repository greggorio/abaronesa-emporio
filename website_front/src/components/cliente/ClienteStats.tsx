import { ClienteStats as StatsType } from '@/types/cliente';
import { Calendar, DollarSign, Star, Music } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';

interface ClienteStatsProps {
  stats: StatsType;
}

const ClienteStats = ({ stats }: ClienteStatsProps) => {
  const statsCards = [
    {
      icon: Calendar,
      label: 'Visitas',
      value: stats.totalVisitas,
      color: 'text-viking-bone',
      bgColor: 'bg-viking-charcoal',
      borderColor: 'border-viking-gold/30'
    },
    {
      icon: DollarSign,
      label: 'Total Gasto',
      value: `R$ ${stats.totalGasto.toFixed(2)}`,
      color: 'text-viking-bone',
      bgColor: 'bg-viking-charcoal',
      borderColor: 'border-viking-gold/30'
    },
    {
      icon: Star,
      label: 'Pontos',
      value: `${stats.pontosFidelidade} ⭐`,
      color: 'text-viking-bone',
      bgColor: 'bg-viking-charcoal',
      borderColor: 'border-viking-gold/30'
    },
    {
      icon: Music,
      label: 'Eventos',
      value: stats.eventosParticipados,
      color: 'text-viking-bone',
      bgColor: 'bg-viking-charcoal',
      borderColor: 'border-viking-gold/30'
    }
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
      {statsCards.map((stat, index) => {
        const Icon = stat.icon;
        return (
          <Card
            key={index}
            className="bg-card border border-viking-gold/30 hover:border-viking-gold/50 transition-all duration-200 shadow-[0_0_20px_hsl(var(--viking-gold)/0.1)] hover:shadow-[0_0_30px_hsl(var(--viking-gold)/0.2)]"
          >
            <CardContent className="p-6">
              <div className="flex flex-col items-center text-center space-y-2">
                <div className="p-3 rounded-full border border-viking-gold/30 bg-viking-gold/10">
                  <Icon className="h-6 w-6 text-viking-gold" />
                </div>
                <p className="text-2xl font-bebas text-viking-bone tracking-wider">{stat.value}</p>
                <p className="text-sm text-viking-bone/60 font-medium">{stat.label}</p>
              </div>
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
};

export default ClienteStats;
