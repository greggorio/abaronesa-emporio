import { PedidoFavorito } from '@/types/cliente';
import { UtensilsCrossed, RefreshCw, Clock } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';

interface PedidosSectionProps {
  pedidos: PedidoFavorito[];
}

const PedidosSection = ({ pedidos }: PedidosSectionProps) => {
  const PedidoCard = ({ pedido }: { pedido: PedidoFavorito }) => {
    return (
      <Card className="bg-card border border-viking-gold/30 hover:border-viking-gold/50 transition-all duration-200 shadow-[0_0_20px_hsl(var(--viking-gold)/0.1)] hover:shadow-[0_0_30px_hsl(var(--viking-gold)/0.2)]">
        <CardContent className="p-4">
          <div className="space-y-3">
            {/* Cabeçalho com data e badge */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-viking-bone/60 text-sm">
                <Clock className="h-4 w-4" />
                <span>Último pedido: {format(pedido.data, "dd/MM/yyyy", { locale: ptBR })}</span>
              </div>
              <Badge className="bg-viking-gold/20 text-viking-gold border-viking-gold/30">
                {pedido.vezesRepetido}x pedido
              </Badge>
            </div>

            {/* Lista de itens */}
            <div className="space-y-2">
              {pedido.itens.map((item) => (
                <div key={item.id} className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-2">
                    <span className="text-viking-gold">●</span>
                    <span className="text-viking-bone">
                      {item.quantidade}x {item.nome}
                    </span>
                  </div>
                  <span className="text-viking-bone/60">R$ {item.preco.toFixed(2)}</span>
                </div>
              ))}
            </div>

            {/* Total e botão */}
            <div className="pt-3 border-t border-viking-gold/20 flex items-center justify-between">
              <div className="text-viking-bone font-semibold">
                Total: <span className="text-viking-gold">R$ {pedido.total.toFixed(2)}</span>
              </div>
              <Button
                size="sm"
                className="bg-viking-gold hover:bg-viking-gold/90 text-viking-charcoal font-bebas tracking-wider"
              >
                <RefreshCw className="h-4 w-4 mr-2" />
                Repetir Pedido
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    );
  };

  return (
    <div className="space-y-6">
      {/* Título da Seção */}
      <div className="flex items-center gap-3">
        <div className="p-2 bg-viking-gold/10 rounded-lg border border-viking-gold/30">
          <UtensilsCrossed className="h-6 w-6 text-viking-gold" />
        </div>
        <div>
          <h2 className="text-2xl font-bebas text-viking-bone tracking-wider">Pedidos Favoritos</h2>
          <p className="text-viking-bone/60 text-sm">Seus pedidos mais repetidos</p>
        </div>
      </div>

      {/* Lista de Pedidos */}
      {pedidos.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {pedidos.map(pedido => (
            <PedidoCard key={pedido.id} pedido={pedido} />
          ))}
        </div>
      ) : (
        <Card className="bg-card border border-viking-gold/20 shadow-[0_0_20px_hsl(var(--viking-gold)/0.1)]">
          <CardContent className="p-8 text-center">
            <UtensilsCrossed className="h-12 w-12 text-viking-bone/30 mx-auto mb-4" />
            <p className="text-viking-bone/60 mb-4">Você ainda não tem pedidos registrados.</p>
            <p className="text-sm text-viking-bone/40">Faça seu primeiro pedido para começar a criar seu histórico!</p>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default PedidosSection;
