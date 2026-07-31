import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '@/lib/api-client';
import { Reward } from '@/types/reward';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Loader2, Gift, CheckCircle2, AlertCircle, ArrowLeft } from 'lucide-react';
import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import { useTheme } from '@/contexts/ThemeContext';

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  return format(new Date(value), "dd/MM/yyyy 'às' HH:mm", { locale: ptBR });
};

export default function RecompensasInbox() {
  const navigate = useNavigate();
  const { theme } = useTheme();
  const textColor = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text))]' : 'text-foreground';
  const textMuted = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text)/0.65)]' : 'text-foreground/65';

  const [rewards, setRewards] = useState<Reward[]>([]);
  const [loadingRewards, setLoadingRewards] = useState(true);

  useEffect(() => {
    fetchRewards();
  }, []);

  const fetchRewards = async () => {
    try {
      setLoadingRewards(true);
      const { data } = await apiClient.get<Reward[]>('/api/rewards/my');
      setRewards(data);
    } catch (err) {
      console.error('Erro ao carregar recompensas:', err);
    } finally {
      setLoadingRewards(false);
    }
  };

  const renderRewardStatus = (status: string, validUntil: string, redeemedAt?: string) => {
    if (status === 'REDEEMED') {
      return (
        <Badge className="bg-emerald-50 text-emerald-700 border-emerald-200 flex items-center gap-1" variant="outline">
          <CheckCircle2 className="w-4 h-4" /> Resgatada em {formatDateTime(redeemedAt)}
        </Badge>
      );
    }
    if (status === 'EXPIRED') {
      return (
        <Badge className="bg-red-50 text-red-700 border-red-200" variant="outline">
          Expirada em {formatDateTime(validUntil)}
        </Badge>
      );
    }
    return (
      <Badge className="bg-amber-50 text-amber-700 border-amber-200" variant="outline">
        Válida até {formatDateTime(validUntil)}
      </Badge>
    );
  };

  return (
    <div className={`min-h-screen bg-soft-white flex flex-col ${textColor}`}>
      <Header />
      <main className="flex-1 container mx-auto px-4 py-8 max-w-3xl space-y-6">
        <button
          onClick={() => navigate('/areacliente')}
          className={`inline-flex items-center gap-2 text-sm font-semibold ${textColor} hover:text-[hsl(var(--accent))] transition`}
        >
          <ArrowLeft className="w-4 h-4" />
          Voltar
        </button>

        <div className="flex flex-col gap-1">
          <p className={`text-xs uppercase tracking-widest ${textMuted}`}>Benefícios</p>
          <h1 className="text-3xl font-display flex items-center gap-2">
            <Gift className="w-6 h-6" /> Recompensas
          </h1>
          <p className={`text-sm ${textMuted}`}>Seus prêmios e vouchers disponíveis.</p>
        </div>

        {loadingRewards ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="w-4 h-4 animate-spin" /> Carregando recompensas...
          </div>
        ) : rewards.length === 0 ? (
          <Card className="border-dashed">
            <CardContent className="p-4 text-sm text-muted-foreground flex items-center gap-2">
              <AlertCircle className="w-4 h-4" /> Nenhuma recompensa disponível no momento.
            </CardContent>
          </Card>
        ) : (
          rewards.map((reward) => (
            <Card key={reward.id} className="overflow-hidden border border-[hsl(var(--accent)/0.2)] bg-white shadow-sm">
              <CardContent className="p-4 space-y-2">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold">{reward.title}</p>
                    <p className="text-xs text-muted-foreground mt-1">{reward.description || 'Sem descrição'}</p>
                  </div>
                  {renderRewardStatus(reward.status, reward.validUntil, reward.redeemedAt)}
                </div>
                {reward.imageUrl && (
                  <img src={reward.imageUrl} alt={reward.title} className="w-full h-40 object-cover rounded-md border" />
                )}
              </CardContent>
            </Card>
          ))
        )}
      </main>
      <Footer />
    </div>
  );
}
