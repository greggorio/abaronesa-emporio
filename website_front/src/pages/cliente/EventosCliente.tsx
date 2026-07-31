import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import { eventoService, EventoResponseDTO } from '@/services/eventoService';
import { Loader2, Calendar, ArrowLeft } from 'lucide-react';
import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';

const formatDateTime = (value: string) =>
  format(new Date(value), "dd 'de' MMMM 'às' HH:mm", { locale: ptBR });

export default function EventosCliente() {
  const navigate = useNavigate();
  const [eventos, setEventos] = useState<EventoResponseDTO[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadEventos = async () => {
      try {
        const data = await eventoService.listarProximosEventos();
        setEventos(data);
      } catch (error) {
        console.error('Erro ao carregar eventos:', error);
      } finally {
        setLoading(false);
      }
    };
    loadEventos();
  }, []);

  return (
    <div className="min-h-screen bg-soft-white flex flex-col">
      <Header />
      <main className="flex-1 container mx-auto px-4 py-8 space-y-6 max-w-4xl">
        <button
          onClick={() => navigate('/areacliente')}
          className="inline-flex items-center gap-2 text-sm font-semibold text-muted-foreground hover:text-[hsl(var(--accent))]"
        >
          <ArrowLeft className="w-4 h-4" />
          Voltar
        </button>

        <div>
          <p className="text-xs uppercase tracking-widest text-muted-foreground">Agenda</p>
          <h1 className="text-3xl font-display flex items-center gap-2">
            <Calendar className="w-6 h-6 text-[hsl(var(--accent))]" /> Eventos agendados
          </h1>
          <p className="text-sm text-muted-foreground mt-1">Veja todos os eventos confirmados.</p>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="w-4 h-4 animate-spin" /> Carregando eventos...
          </div>
        ) : eventos.length === 0 ? (
          <div className="border border-dashed border-muted-foreground rounded-xl p-6 text-center text-sm text-muted-foreground">
            Nenhum evento agendado no momento.
          </div>
        ) : (
          eventos.map((evento) => (
            <div key={evento.id} className="bg-white border border-[hsl(var(--accent)/0.12)] rounded-2xl shadow-sm overflow-hidden">
              {evento.imagemUrl && (
                <img src={evento.imagemUrl} alt={evento.titulo} className="w-full h-48 object-cover" />
              )}
              <div className="p-5 space-y-2">
                <div className="flex items-center justify-between text-sm text-muted-foreground">
                  <span>{evento.statusDescricao || evento.status}</span>
                  <span>{evento.generoDescricao || 'Evento especial'}</span>
                </div>
                <h2 className="text-2xl font-semibold">{evento.titulo}</h2>
                <p className="text-sm text-muted-foreground">
                  {formatDateTime(evento.dataEvento)}
                  {evento.dataHoraFim ? ` - ${formatDateTime(evento.dataHoraFim)}` : ''}
                </p>
                <p className="text-sm text-muted-foreground">
                  {evento.descricao || 'Mais detalhes em breve.'}
                </p>
                <div className="text-xs uppercase text-muted-foreground">
                  {evento.gratuito ? 'Evento gratuito' : `Ingressos a partir de R$ ${evento.preco?.toFixed(2) ?? '0,00'}`}
                </div>
              </div>
            </div>
          ))
        )}
      </main>
      <Footer />
    </div>
  );
}
