import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Calendar, Music2, Clock } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { eventoService, EventoResponseDTO, GeneroMusical } from "@/services/eventoService";
import { format } from "date-fns";
import { ptBR } from "date-fns/locale";
import { useSiteI18n } from "@/i18n/useSiteI18n";

const Events = () => {
  const { t } = useSiteI18n();
  // Fetch próximos eventos
  const { data: upcomingEvents = [], isLoading: loadingUpcoming } = useQuery({
    queryKey: ['eventos', 'proximos'],
    queryFn: eventoService.listarProximosEventos,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  // Fetch eventos realizados
  const { data: pastEvents = [], isLoading: loadingPast } = useQuery({
    queryKey: ['eventos', 'realizados'],
    queryFn: eventoService.listarEventosRealizados,
    staleTime: 5 * 60 * 1000,
  });

  // Helper function to format date
  const formatEventDate = (dateString: string) => {
    const date = new Date(dateString);
    return {
      day: format(date, 'dd', { locale: ptBR }),
      month: format(date, 'MMM', { locale: ptBR }).toUpperCase(),
      time: format(date, 'HH\'h\'mm', { locale: ptBR })
    };
  };

  // Helper function to get genre display name
  const getGenreDisplay = (genero: GeneroMusical): string => {
    const genreMap: Record<GeneroMusical, string> = {
      [GeneroMusical.ROCK]: 'Rock',
      [GeneroMusical.METAL]: 'Metal',
      [GeneroMusical.ACUSTICO]: 'Acústico',
      [GeneroMusical.SERTANEJO]: 'Sertanejo',
      [GeneroMusical.MPB]: 'MPB',
      [GeneroMusical.BLUES]: 'Blues',
      [GeneroMusical.JAZZ]: 'Jazz',
      [GeneroMusical.OUTRO]: 'Outro'
    };
    return genreMap[genero] || genero;
  };

  const genreColors: { [key: string]: string } = {
    "Rock": "bg-accent",
    "Metal": "bg-orange-600",
    "Acústico": "bg-foreground",
    "Sertanejo": "bg-green-600",
    "MPB": "bg-blue-600",
    "Blues": "bg-indigo-600",
    "Jazz": "bg-purple-600",
    "Outro": "bg-gray-600"
  };

  return (
    <section id="eventos" className="py-20 bg-soft-white">
      <div className="container mx-auto px-4">
        <div className="text-center mb-12 animate-fade-in">
          <h2 className="text-4xl md:text-5xl font-display mb-4 text-accent">
            {t("site.events.title")}
          </h2>
          <p className="text-lg text-about-text/70">
            {t("site.events.subtitle")}
          </p>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-8 mb-16">
          {loadingUpcoming ? (
            <div className="col-span-full text-center text-about-text/70">
              {t("site.events.loading")}
            </div>
          ) : upcomingEvents.length === 0 ? (
            <div className="col-span-full text-center text-about-text/70">
              {t("site.events.empty")}
            </div>
          ) : (
            upcomingEvents.map((event) => {
              const dateFormatted = formatEventDate(event.dataEvento);
              const genreDisplay = getGenreDisplay(event.genero);

              return (
                <Card key={event.id} className="bg-card border-border hover:border-accent hover:shadow-lg transition-all duration-300 animate-slide-up">
                  <CardHeader>
                    <div className="flex items-center justify-between mb-2">
                      <div className="text-center bg-accent/20 rounded-lg p-3 border border-accent/30">
                        <div className="font-display text-3xl text-accent leading-none">
                          {dateFormatted.day}
                        </div>
                        <div className="text-xs text-about-text/70 uppercase">
                          {dateFormatted.month}
                        </div>
                      </div>
                      <Badge className={`${genreColors[genreDisplay]} text-white border-0`}>
                        {genreDisplay}
                      </Badge>
                    </div>
                    <CardTitle className="font-display text-2xl text-about-text">{event.titulo}</CardTitle>
                    <CardDescription className="flex items-center gap-2 text-about-text/80">
                      <Music2 className="w-4 h-4" />
                      {event.banda}
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    <p className="text-sm text-about-text/80 mb-3">{event.descricao}</p>
                    <div className="flex items-center gap-2 text-sm text-accent font-semibold">
                      <Clock className="w-4 h-4" />
                      {t("site.events.timePrefix")}{dateFormatted.time}
                    </div>
                  </CardContent>
                </Card>
              );
            })
          )}
        </div>

        <div className="bg-accent/10 p-8 rounded-lg border-2 border-accent/30">
          <h3 className="text-3xl font-display text-center mb-6 text-accent">
            {t("site.events.past.title")}
          </h3>
          {loadingPast ? (
            <div className="text-center text-about-text/70">
              {t("site.events.past.loading")}
            </div>
          ) : pastEvents.length === 0 ? (
            <div className="text-center text-about-text/70">
              {t("site.events.past.empty")}
            </div>
          ) : (
            <>
              <div className="flex flex-wrap justify-center gap-4">
                {pastEvents.map((evento) => (
                  <Badge
                    key={evento.id}
                    variant="outline"
                    className="text-base py-2 px-4 border-accent/30 text-about-text hover:bg-accent/10 transition-colors"
                  >
                    {evento.banda} - {getGenreDisplay(evento.genero)}
                  </Badge>
                ))}
              </div>
              <p className="text-center text-about-text/70 mt-6">
                {t("site.events.past.more")}
              </p>
            </>
          )}
        </div>
      </div>
    </section>
  );
};

export default Events;
