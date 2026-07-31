import { useEffect, useState } from 'react';
import { Trophy } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import { Recompensa } from '@/types/cliente';
import { useNavigate } from 'react-router-dom';

interface GamificacaoBannerProps {
  pontos: number;
}

export default function GamificacaoBanner({ pontos }: GamificacaoBannerProps) {
  const navigate = useNavigate();
  const [proximaRecompensa, setProximaRecompensa] = useState<Recompensa | null>(null);

  useEffect(() => {
    const fetchProximaRecompensa = async () => {
      try {
        const res = await axios.get<Recompensa[]>(`${apiConfig.erpBaseUrl}/api/clientes/me/gamificacao/recompensas`);
        const recompensasValidas = res.data
          .filter((r) => r.disponivel && r.pontosNecessarios > pontos)
          .sort((a, b) => a.pontosNecessarios - b.pontosNecessarios);
        setProximaRecompensa(recompensasValidas[0] || null);
      } catch (error) {
        console.error('Erro ao buscar recompensas para o banner:', error);
        setProximaRecompensa(null);
      }
    };

    fetchProximaRecompensa();
  }, [pontos]);

  let mensagem = '';
  if (proximaRecompensa) {
    const pontosFaltando = proximaRecompensa.pontosNecessarios - pontos;
    mensagem = pontosFaltando > 0
      ? `Faltam ${pontosFaltando} pontos para ${proximaRecompensa.nome}.`
      : 'Parabéns! Você tem pontos suficientes para resgatar recompensas.';
  } else if (pontos > 0) {
    mensagem = 'Parabéns! Você tem pontos suficientes para resgatar recompensas.';
  } else {
    mensagem = 'Comece a ganhar pontos hoje! Visite nosso bar e acumule recompensas.';
  }

  return (
    <Card
      className="bg-gradient-to-br from-[hsl(var(--accent)/0.12)] via-[hsl(var(--accent)/0.18)] to-[hsl(var(--accent)/0.24)] text-mesa-text border-none shadow-lg relative overflow-hidden cursor-pointer hover:opacity-90 transition-opacity"
      onClick={() => navigate('/areacliente/gamificacao?tab=recompensas')}
    >
      <CardContent className="p-6 flex items-center gap-5 relative z-10">
        <div className="w-12 h-12 shrink-0 rounded-full bg-white/30 flex items-center justify-center backdrop-blur-sm shadow-inner">
          <Trophy className="w-6 h-6 text-mesa-text" />
        </div>
          <div>
            <h3 className="text-lg font-bold leading-tight text-mesa-text tracking-[0.2em] uppercase">
              Gamificação
            </h3>
            <p className="text-sm mt-1 text-mesa-text/85">
            {mensagem}
          </p>
        </div>
      </CardContent>
      <div className="absolute -bottom-8 -right-8 w-24 h-24 bg-white/20 rounded-full blur-xl"></div>
    </Card>
  );
}
