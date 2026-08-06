import { Card, CardContent } from "@/components/ui/card";
import { Clock, Coffee, Cookie, Wifi, Armchair, MapPin, Phone, Axe, Beer, Music, Flame } from "lucide-react";
import { useTheme } from '@/contexts/ThemeContext';
import { useSiteI18n } from "@/i18n/useSiteI18n";

const About = () => {
  const { theme } = useTheme();
  const { t } = useSiteI18n();

  // Recuperar dados do tema
  const aboutTitle = theme?.content?.aboutTitle;
  const aboutDescription1 = theme?.content?.aboutDescription1;
  const aboutDescription2 = theme?.content?.aboutDescription2;
  const aboutAddress = theme?.content?.aboutAddress;
  const aboutPhone = theme?.content?.aboutPhone;

  const aboutHours = theme?.content?.aboutHours;

  const themeFeatures = theme?.content?.aboutFeatures;

  // Mapear strings para ícones reais
  const getIcon = (iconName: string) => {
    switch(iconName) {
      case 'Coffee': return Coffee;
      case 'Cookie': return Cookie;
      case 'Wifi': return Wifi;
      case 'Armchair': return Armchair;
      case 'Axe': return Axe;
      case 'Beer': return Beer;
      case 'Music': return Music;
      case 'Flame': return Flame;
      default: return Coffee;
    }
  };

  return (
    <section id="sobre" className="py-20 bg-soft-white">
      <div className="container mx-auto px-4">
        <div className="grid md:grid-cols-2 gap-12 items-start">
          <div className="space-y-6">
            <div className="relative">
              <img
                src={theme?.assets?.heroBackgroundImageUrl || '/assets/restaurant-photos/estabelecimento_interior.png'}
                alt={t("site.about.imageAlt")}
                className="rounded-lg shadow-2xl w-full h-auto"
              />
            </div>

            {themeFeatures && (
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                {themeFeatures.map((feature, index) => (
                  <div key={index} className="text-center p-4 bg-card border border-accent/20 rounded-lg">
                    {(() => {
                      const IconComponent = getIcon(feature.icon);
                      return IconComponent ? <IconComponent className="w-10 h-10 mx-auto mb-2 text-accent" /> : null;
                    })()}
                    <h3 className="font-display text-lg mb-1 text-about-card-text">{feature.title}</h3>
                    <p className="text-xs text-about-card-text/70">{feature.description}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
          <div className="space-y-6">
            <div>
              {aboutTitle && (
                <h2 className="text-4xl md:text-5xl font-display mb-6 text-accent">
                  {aboutTitle}
                </h2>
              )}
              {aboutDescription1 && <p className="text-lg text-about-text/80 mb-6" dangerouslySetInnerHTML={{__html: aboutDescription1}} />}
              {aboutDescription2 && <p className="text-lg text-about-text/80" dangerouslySetInnerHTML={{__html: aboutDescription2}} />}
            </div>

            <div className="space-y-4">
              {aboutAddress && (
                <div className="flex items-start gap-3">
                  <MapPin className="h-5 w-5 text-accent mt-1 flex-shrink-0" />
                  <div>
                    <p className="text-sm font-semibold text-about-text">{t("site.about.addressLabel")}</p>
                    <p className="text-sm text-about-text/70" dangerouslySetInnerHTML={{__html: aboutAddress}} />
                  </div>
                </div>
              )}

              {aboutPhone && (
                <div className="flex items-start gap-3">
                  <Phone className="h-5 w-5 text-accent mt-1 flex-shrink-0" />
                  <div>
                    <p className="text-sm font-semibold text-about-text">{t("site.about.phoneLabel")}</p>
                    <p className="text-sm text-about-text/70">{aboutPhone}</p>
                  </div>
                </div>
              )}
            </div>

            {aboutHours && (
              <Card className="bg-card border-accent">
                <CardContent className="p-6">
                  <div className="flex items-center gap-2 text-accent mb-4">
                    <Clock className="h-5 w-5" />
                    <span className="font-display text-xl">{t("site.about.hoursLabel")}</span>
                  </div>
                  <div className="grid grid-cols-3 gap-4">
                    {aboutHours.map((hour, index) => (
                      <div key={index} className={`${index === 1 ? 'text-center border-l border-r border-accent/20 px-2' : 'text-center'}`}>
                        <p className="text-sm font-semibold text-about-card-text mb-1">{hour.days}</p>
                        <p className="text-sm text-about-card-text/70">{hour.hours}</p>
                        {hour.notes && <p className="text-xs text-about-card-text/60 mt-1">{hour.notes}</p>}
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default About;
