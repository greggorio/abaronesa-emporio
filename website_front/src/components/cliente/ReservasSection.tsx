import { Reserva, getReservaStatusColor, getReservaStatusLabel } from '@/types/cliente';
import { Calendar, Users, MapPin, Plus } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { format, isFuture } from 'date-fns';
import { ptBR } from 'date-fns/locale';

interface ReservasSectionProps {
  reservas: Reserva[];
}

const ReservasSection = ({ reservas }: ReservasSectionProps) => {
  const reservasFuturas = reservas.filter(r => isFuture(r.dataHora));
  const reservasPassadas = reservas.filter(r => !isFuture(r.dataHora));

  const ReservaCard = ({ reserva }: { reserva: Reserva }) => {
    const isFuturaReserva = isFuture(reserva.dataHora);

    return (
      <Card className="bg-card border border-viking-gold/30 hover:border-viking-gold/50 transition-all duration-200 shadow-[0_0_20px_hsl(var(--viking-gold)/0.1)] hover:shadow-[0_0_30px_hsl(var(--viking-gold)/0.2)]">
        <CardContent className="p-4">
          <div className="flex items-start justify-between gap-4">
            <div className="flex-1 space-y-2">
              {/* Data e Hora */}
              <div className="flex items-center gap-2 text-viking-gold">
                <Calendar className="h-4 w-4" />
                <span className="font-semibold">
                  {format(reserva.dataHora, "dd 'de' MMM 'às' HH:mm", { locale: ptBR })}
                </span>
              </div>

              {/* Mesa e Pessoas */}
              <div className="flex items-center gap-4 text-viking-bone">
                <div className="flex items-center gap-1">
                  <MapPin className="h-4 w-4 text-viking-gold" />
                  <span className="text-sm">Mesa {reserva.mesa}</span>
                </div>
                <div className="flex items-center gap-1">
                  <Users className="h-4 w-4 text-viking-gold" />
                  <span className="text-sm">{reserva.numeroPessoas} {reserva.numeroPessoas === 1 ? 'pessoa' : 'pessoas'}</span>
                </div>
              </div>

              {/* Observações */}
              {reserva.observacoes && (
                <p className="text-sm text-viking-bone/60 italic">"{reserva.observacoes}"</p>
              )}
            </div>

            {/* Status Badge */}
            <Badge className={`${getReservaStatusColor(reserva.status)} border`}>
              {getReservaStatusLabel(reserva.status)}
            </Badge>
          </div>

          {/* Ações (apenas para reservas futuras) */}
          {isFuturaReserva && reserva.status === 'CONFIRMADA' && (
            <div className="mt-4 flex gap-2">
              <Button
                variant="outline"
                size="sm"
                className="flex-1 bg-transparent border-viking-gold/40 text-viking-bone hover:bg-viking-gold/10"
              >
                Cancelar
              </Button>
              <Button
                size="sm"
                className="flex-1 bg-viking-gold hover:bg-viking-gold/90 text-viking-charcoal font-bebas tracking-wider"
              >
                Detalhes
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    );
  };

  return (
    <div className="space-y-6">
      {/* Título da Seção */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-viking-gold/10 rounded-lg border border-viking-gold/30">
            <Calendar className="h-6 w-6 text-viking-gold" />
          </div>
          <h2 className="text-2xl font-bebas text-viking-bone tracking-wider">Minhas Reservas</h2>
        </div>
        <Button className="bg-viking-gold hover:bg-viking-gold/90 text-viking-charcoal font-bebas tracking-wider">
          <Plus className="h-4 w-4 mr-2" />
          Nova Reserva
        </Button>
      </div>

      {/* Reservas Futuras */}
      {reservasFuturas.length > 0 && (
        <div>
          <h3 className="text-lg font-semibold text-viking-gold mb-3 uppercase tracking-wider">Próximas Reservas</h3>
          <div className="space-y-3">
            {reservasFuturas.map(reserva => (
              <ReservaCard key={reserva.id} reserva={reserva} />
            ))}
          </div>
        </div>
      )}

      {/* Reservas Passadas */}
      {reservasPassadas.length > 0 && (
        <div>
          <h3 className="text-lg font-semibold text-viking-bone/60 mb-3 uppercase tracking-wider">Histórico</h3>
          <div className="space-y-3">
            {reservasPassadas.map(reserva => (
              <ReservaCard key={reserva.id} reserva={reserva} />
            ))}
          </div>
        </div>
      )}

      {/* Sem reservas */}
      {reservas.length === 0 && (
        <Card className="bg-card border border-viking-gold/20 shadow-[0_0_20px_hsl(var(--viking-gold)/0.1)]">
          <CardContent className="p-8 text-center">
            <Calendar className="h-12 w-12 text-viking-bone/30 mx-auto mb-4" />
            <p className="text-viking-bone/60 mb-4">Você ainda não tem reservas.</p>
            <Button className="bg-viking-gold hover:bg-viking-gold/90 text-viking-charcoal font-bebas tracking-wider">
              <Plus className="h-4 w-4 mr-2" />
              Fazer Primeira Reserva
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default ReservasSection;
