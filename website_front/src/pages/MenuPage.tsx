import Header from "@/components/Header";
import Footer from "@/components/Footer";
import Menu from "@/components/Menu";
import { useDeliveryI18n } from "@/i18n/useDeliveryI18n";

const MenuPage = () => {
  const { t } = useDeliveryI18n();
  return (
    <div className="min-h-screen bg-soft-white">
      <Header />
      <section className="relative h-[400px] md:h-[500px] flex items-center justify-center bg-forest-dark pt-16">
        <div
          className="absolute inset-0 bg-cover bg-center bg-no-repeat"
          style={{ backgroundImage: "url('/cardapio_hero.png')" }}
        />
        <div className="absolute inset-0 bg-gradient-to-b from-forest-dark/70 via-forest-dark/50 to-soft-white" />
        <div className="relative z-10 text-center px-4">
          <h1 className="text-6xl md:text-7xl font-display text-coral-accent tracking-wider mb-4">
            {t("delivery.menuPage.hero.title")}
          </h1>
          <p className="text-cream text-lg md:text-xl max-w-2xl mx-auto">
            {t("delivery.menuPage.hero.description")}
          </p>
        </div>
      </section>
      <Menu />
      <Footer />
    </div>
  );
};

export default MenuPage;
