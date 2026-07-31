import { ComandaAberta } from '@/types/cliente';
import { Receipt, Clock, DollarSign, Eye, CreditCard } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';

interface ComandaAtualProps {
  comanda: ComandaAberta | null;
}

const ComandaAtual = ({ comanda }: ComandaAtualProps) => {
  if (!comanda) {
    return (
      <div className="space-y-6">
        {/* Título da Seção */}
        <div className="flex items-center gap-3">
          <div className="p-2 bg-viking-gold/10 rounded-lg border border-viking-gold/30">
            <Receipt className="h-6 w-6 text-viking-gold" />
          </div>
          <div>
            <h2 className="text-2xl font-bebas text-viking-bone tracking-wider">Comanda Atual</h2>
            <p className="text-viking-bone/60 text-sm">Você não possui comanda aberta</p>
          </div>
        </div>

        <Card className="bg-card border border-viking-gold/20 shadow-[0_0_20px_hsl(var(--viking-gold)/0.1)]">
          <CardContent className="p-8 text-center">
            <Receipt className="h-12 w-12 text-viking-bone/30 mx-auto mb-4" />
            <p className="text-viking-bone/60">Nenhuma comanda aberta no momento.</p>
            <p className="text-sm text-viking-bone/40 mt-2">
              Quando você fizer um pedido, sua comanda aparecerá aqui.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  const tempoAberto = Math.floor((Date.now() - comanda.aberturaEm.getTime()) / (1000 * 60)); // minutos

  return (
    <div className="space-y-6">
      {/* Título da Seção */}
      <div className="flex items-center gap-3">
        <div className="p-2 bg-viking-gold/20 rounded-lg border border-viking-gold/50 animate-pulse">
          <Receipt className="h-6 w-6 text-viking-gold" />
        </div>
        <div>
          <h2 className="text-2xl font-bebas text-viking-bone tracking-wider">Comanda Atual</h2>
          <p className="text-viking-gold text-sm font-medium">● Aberta</p>
        </div>
      </div>

      <Card className="bg-card border-2 border-viking-gold/50 shadow-[0_0_30px_hsl(var(--viking-gold)/0.2)]">
        <CardContent className="p-6 space-y-4">
          {/* Informações da Mesa e Tempo */}
          <div className="flex items-center justify-between pb-4 border-b border-viking-gold/20">
            <div>
              <p className="text-viking-bone/60 text-sm">Mesa</p>
              <p className="text-3xl font-bebas text-viking-gold tracking-wider">#{comanda.mesa}</p>
            </div>
            <div className="text-right">
              <div className="flex items-center gap-2 text-viking-bone/60 text-sm mb-1">
                <Clock className="h-4 w-4" />
                <span>Aberta há {tempoAberto} min</span>
              </div>
              <p className="text-sm text-viking-bone/40">
                {format(comanda.aberturaEm, "HH:mm", { locale: ptBR })}
              </p>
            </div>
          </div>

          {/* Lista de Itens */}
          <div className="space-y-2">
            <p className="text-sm font-semibold text-viking-gold uppercase tracking-wider">Itens</p>
            {comanda.itens.map((item) => (
              <div key={item.id} className="flex items-center justify-between text-sm py-2 border-b border-viking-gold/10">
                <div className="flex items-center gap-2">
                  <span className="text-viking-gold font-bold">{item.quantidade}x</span>
                  <div>
                    <p className="text-viking-bone">{item.nome}</p>
                    <p className="text-viking-bone/40 text-xs">{item.categoria}</p>
                  </div>
                </div>
                <span className="text-viking-bone font-medium">
                  R$ {(item.preco * item.quantidade).toFixed(2)}
                </span>
              </div>
            ))}
          </div>

          {/* Total */}
          <div className="pt-4 border-t-2 border-viking-gold/50">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2 text-viking-bone/60">
                <DollarSign className="h-5 w-5" />
                <span className="text-lg font-semibold">Total Parcial</span>
              </div>
              <span className="text-3xl font-bebas text-viking-gold tracking-wider">
                R$ {comanda.total.toFixed(2)}
              </span>
            </div>

            {/* Botões de Ação */}
            <div className="grid grid-cols-2 gap-3">
              <Button
                variant="outline"
                className="border-viking-gold/40 text-viking-bone hover:bg-viking-gold/10"
              >
                <Eye className="h-4 w-4 mr-2" />
                Ver Detalhes
              </Button>
              <Button className="bg-viking-gold hover:bg-viking-gold/90 text-viking-charcoal font-bebas tracking-wider">
                <CreditCard className="h-4 w-4 mr-2" />
                Pedir Conta
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Aviso */}
      <div className="bg-viking-gold/10 border border-viking-gold/30 rounded-lg p-4">
        <p className="text-viking-gold text-sm text-center">
          ⚠️ Os valores podem ser atualizados conforme novos itens são adicionados
        </p>
      </div>
    </div>
  );
};

export default ComandaAtual;
