import { Badge } from "@/components/ui/badge";
import { menuData, type MenuItem as Item, type MenuCategory } from "@/data/menu";
import { cardapioApi } from "@/lib/cardapio-api";
import { useEffect, useState } from "react";
import { useDeliveryI18n } from "@/i18n/useDeliveryI18n";

const ItemCard = ({ item, t }: { item: Item; t: ReturnType<typeof useDeliveryI18n>["t"] }) => (
  <li className="flex gap-4 p-4 bg-white border border-coral-accent/20 rounded-lg shadow-sm hover:shadow-md transition-shadow">
    {item.image && (
      <img
        src={item.image}
        alt={item.name}
        loading="lazy"
        className="w-20 h-20 object-cover rounded-md border border-coral-accent/20"
      />
    )}
    <div className="flex-1">
      <div className="flex items-center gap-3">
        <span className="text-forest-dark text-lg font-medium">{item.name}</span>
        {item.signature && (
          <Badge className="bg-coral-accent text-forest-dark border-0">{t("delivery.menu.signature")}</Badge>
        )}
      </div>
      {item.description && (
        <p className="text-sm text-forest-green/70 mt-1">{item.description}</p>
      )}
    </div>
      {item.price && (
        <span className="text-coral-accent font-display text-2xl whitespace-nowrap">{item.price}</span>
      )}
  </li>
);

const Section = ({ category, t }: { category: MenuCategory; t: ReturnType<typeof useDeliveryI18n>["t"] }) => (
  <div className="mb-10">
    <h3 className="font-display text-3xl text-forest-dark tracking-wider mb-4 flex items-center gap-2">
      {category.title}
      <span className="h-px flex-1 bg-coral-accent/30"></span>
    </h3>
    <ul className="space-y-4">
      {category.items.map((it, i) => (
        <ItemCard key={i} item={it} t={t} />
      ))}
    </ul>
  </div>
);

const Menu = () => {
  const [categories, setCategories] = useState<MenuCategory[]>(menuData);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const { t, locale } = useDeliveryI18n();

  useEffect(() => {
    const carregarCardapio = async () => {
      setLoading(true);
      setError(false);
      try {
        const cardapio = await cardapioApi.buscarCardapio(locale);
        setCategories(cardapio);
        setError(false);
      } catch (err) {
        console.error('Erro ao carregar cardápio da API, usando dados estáticos:', err);
        // Mantém menuData como fallback
        setError(true);
      } finally {
        setLoading(false);
      }
    };

    carregarCardapio();
  }, [locale]);

  return (
    <section id="cardapio" className="py-16 bg-soft-white">
      <div className="container mx-auto px-4">
        {error && (
          <p className="text-center text-forest-green/70 mb-8 text-sm">
            {t("delivery.menu.errorStaticContent")}
          </p>
        )}

        {loading ? (
          <div className="text-center text-forest-green/70 py-12">
            <p className="text-xl">{t("delivery.menu.loading")}</p>
          </div>
        ) : (
          <div className="grid md:grid-cols-2 gap-8 max-w-6xl mx-auto">
            {categories.map((cat, idx) => (
              <Section key={idx} category={cat} t={t} />
            ))}
          </div>
        )}
      </div>
    </section>
  );
};

export default Menu;
