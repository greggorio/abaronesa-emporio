import { Link } from "react-router-dom";
import { menuData, MenuItem } from "@/data/menu";
import { cardapioApi } from "@/lib/cardapio-api";
import { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { useSiteI18n } from "@/i18n/useSiteI18n";

const FeaturedMenu = () => {
  const [featured, setFeatured] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const { t, locale } = useSiteI18n();

  useEffect(() => {
    const carregarDestaques = async () => {
      try {
        const destaques = await cardapioApi.buscarDestaques(locale);
        setFeatured(destaques);
        setError(false);
      } catch (err) {
        console.error('Erro ao carregar destaques da API, usando dados estáticos:', err);
        // Fallback para dados hardcoded
        const signatureItems = menuData
          .flatMap((c) => c.items)
          .filter((i) => i.signature);

        const fillers = [] as typeof signatureItems;
        for (const cat of menuData) {
          for (const it of cat.items) {
            if (fillers.length + signatureItems.length >= 6) break;
            if (!it.signature) fillers.push(it);
          }
          if (fillers.length + signatureItems.length >= 6) break;
        }

        setFeatured([...signatureItems.slice(0, 4), ...fillers].slice(0, 6));
        setError(true);
      } finally {
        setLoading(false);
      }
    };

    carregarDestaques();
  }, [locale]);

  return (
    <section id="cardápio" className="py-20 bg-background">
      <div className="container mx-auto px-4">
        <div className="text-center mb-12 animate-fade-in">
          <h2 className="text-4xl md:text-5xl font-display mb-4 text-accent">
            {t("site.featured.title")}
          </h2>
          <p className="text-lg text-muted-foreground">
            {error
              ? t("site.featured.subtitle.fallback")
              : t("site.featured.subtitle.default")}
          </p>
        </div>

        {loading ? (
          <div className="text-center text-muted-foreground py-12">
            <p className="text-xl">{t("site.featured.loading")}</p>
          </div>
        ) : (
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
            {featured.map((item, idx) => (
              <Card
                key={idx}
                className="bg-card border-border hover:shadow-lg hover:border-accent transition-all duration-300 overflow-hidden group animate-slide-up"
              >
                {item.image && (
                  <div className="h-48 overflow-hidden">
                    <img
                      src={item.image}
                      alt={item.name}
                      loading="lazy"
                      className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
                    />
                  </div>
                )}
                <CardContent className="p-4">
                  <h3 className="font-display text-xl mb-2 text-foreground">
                    {item.name}
                  </h3>
                  {item.price && (
                    <p className="text-accent font-semibold text-lg">
                      {item.price}
                    </p>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        )}

        <div className="text-center mt-10">
          <Link
            to="/cardapio"
            className="inline-block bg-accent hover:bg-accent/90 text-accent-foreground font-semibold text-lg px-8 py-3 rounded-lg shadow-lg transition-all"
          >
            {t("site.featured.cta")}
          </Link>
        </div>
      </div>
    </section>
  );
};

export default FeaturedMenu;
