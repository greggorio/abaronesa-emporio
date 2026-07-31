import { Button } from "@/components/ui/button";
import {
  Coffee, Heart, Users, Music, Wine, Utensils as UtensilsIcon, Search,
  Book, Gift, Flame, Zap, Smile, Phone, Mail, ShoppingCart, Home,
  Settings, Bell, Camera, Video, MessageSquare,
  ThumbsUp, ThumbsDown, RefreshCw
} from "lucide-react";
import { useTheme } from '@/contexts/ThemeContext';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from "@/hooks/useAuth";

// Função para obter o ícone com base no nome

const getIconByName = (iconName: string) => {
  switch(iconName.toLowerCase()) {
    case 'coffee': return Coffee;
    case 'heart': return Heart;
    case 'users': return Users;
    case 'guitar': return Music;
    case 'beer': return Wine;
    case 'utensilscrossed': return UtensilsIcon;
    case 'utensils': return UtensilsIcon;
    case 'music': return Music;
    case 'wine': return Wine;
    case 'search': return Search;
    case 'book': return Book;
    case 'gift': return Gift;
    case 'fire': return Flame;
    case 'lightning': return Zap;
    case 'smile': return Smile;
    case 'phone': return Phone;
    case 'envelope': return Mail;
    case 'shoppingcart': return ShoppingCart;
    case 'home': return Home;
    case 'settings': return Settings;
    case 'bell': return Bell;
    case 'camera': return Camera;
    case 'video': return Video;
    case 'message': return MessageSquare;
    case 'like': return ThumbsUp;
    case 'dislike': return ThumbsDown;
    case 'share': return RefreshCw;
    case 'mappin': return () => <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 10c0 4.4-3.6 8-8 8a5.5 5.5 0 0 1-3.3-.9L2 22l5.8-6.7A6.5 6.5 0 0 1 4 10c0-4.4 3.6-8 8-8s8 3.6 8 8Z"/></svg>;
    case 'calendar': return () => <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect width="18" height="18" x="3" y="4" rx="2" ry="2"/><line x1="16" x2="16" y1="2" y2="6"/><line x1="8" x2="8" y1="2" y2="6"/><line x1="3" x2="21" y1="10" y2="10"/></svg>;
    case 'clock': return () => <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>;
    case 'star': return () => <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>;
    default: return Coffee; // ícone padrão
  }
};

const Hero = () => {
  const { theme, isLoading, error, refreshTheme } = useTheme();
  const [key, setKey] = useState(0);
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const scrollToSection = (id: string) => {
    const element = document.getElementById(id);
    element?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    if (theme) {
      setKey(prev => prev + 1);
    }
  }, [theme]);

  // Valores padrão
  const heroTitle = theme?.content?.heroTitle || 'Cafés Especiais, Momentos Inesquecíveis';
  const heroSubtitle = theme?.content?.heroSubtitle || 'Espaço aconchegante para estudar, trabalhar e saborear';
  const heroCtaText = theme?.content?.heroCtaText || 'Ver Cardápio';
  const heroCtaHref = theme?.content?.heroCtaHref || '#cardapio';
  const heroSecondaryCtaText = theme?.content?.heroSecondaryCtaText || 'Reservar Mesa';
  const reservationLink = theme?.content?.reservationLink || 'https://wa.me/5515988354989?text=Olá! Gostaria de fazer uma reserva';
  const logoUrl = theme?.assets?.logoUrl || '/logo.png';
  const businessName = theme?.content?.name || 'Empório A Baronesa';
  const heroBackgroundImageUrl = theme?.assets?.heroBackgroundImageUrl || '/assets/restaurant-photos/estabelecimento_interior.png';

  if (isLoading) {
    return (
      <section className="relative min-h-[75vh] md:min-h-[78vh] lg:min-h-[80vh] flex items-center justify-center bg-background" key={`hero-loading-${key}`}>
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto"></div>
          <p className="mt-4 text-foreground">Carregando tema...</p>
        </div>
      </section>
    );
  }

  if (error) {
    console.error('Erro ao carregar tema:', error);
    return (
      <section className="relative min-h-[75vh] md:min-h-[78vh] lg:min-h-[80vh] flex items-center justify-center bg-background" key={`hero-error-${key}`}>
        <div className="text-center">
          <p className="text-destructive">Erro ao carregar tema: {error}</p>
          <Button
            onClick={() => refreshTheme()}
            className="mt-4 bg-primary hover:bg-primary/90 text-primary-foreground"
          >
            Tentar novamente
          </Button>
        </div>
      </section>
    );
  }

  return (
    <section
      id="início"
      className="relative min-h-[75vh] md:min-h-[78vh] lg:min-h-[80vh] flex items-start md:items-center justify-center pt-8 md:pt-12"
      style={{
        backgroundImage: `url(${heroBackgroundImageUrl})`,
        backgroundSize: "cover",
        backgroundPosition: "center",
      }}
      key={`hero-${key}`}
    >
      {/* Overlay escuro sobre a imagem de fundo */}
      <div className="absolute inset-0 bg-hero-overlay/70" />

      <div className="relative z-10 text-center px-4 animate-fade-in">
        {/* Logo */}
        <div className="mb-4 md:mb-8">
          <img
            src={logoUrl}
            alt={businessName}
            className="mx-auto h-24 md:h-52 w-auto max-w-[90vw]"
            style={{
              filter: 'drop-shadow(0 4px 12px rgba(0, 0, 0, 0.9)) drop-shadow(0 8px 24px rgba(0, 0, 0, 0.7)) brightness(1.1) contrast(1.05)'
            }}
          />
        </div>

        {/* Título principal */}
        <h1 className="text-2xl md:text-6xl font-display mb-3 md:mb-4 text-hero-title">
          {heroTitle}
        </h1>

        {/* Subtítulo */}
        <p className="text-sm md:text-xl text-hero-subtitle mb-6 md:mb-8 max-w-2xl mx-auto">
          {heroSubtitle}
        </p>

        {/* Botões CTA */}
        <div className="flex flex-col sm:flex-row gap-3 md:gap-4 justify-center">
          <Button
            onClick={() => {
              if (heroCtaHref.startsWith('#')) {
                scrollToSection(heroCtaHref.substring(1));
              } else {
                window.open(heroCtaHref, '_self');
              }
            }}
            size="lg"
            className="bg-button-primary hover:bg-button-primary/90 text-button-primary-foreground font-semibold text-base md:text-lg px-6 md:px-8 shadow-lg border-none"
          >
            {heroCtaText}
          </Button>
          <Button
            onClick={() => window.open(reservationLink, "_blank")}
            size="lg"
            variant="outline"
            className="border-2 border-button-secondary-border bg-button-secondary hover:bg-button-secondary text-button-secondary-foreground font-semibold text-base md:text-lg px-6 md:px-8 backdrop-blur-sm"
          >
            {heroSecondaryCtaText}
          </Button>
          {!isAuthenticated && (
            <Button
              onClick={() => navigate('/login')}
              size="lg"
              variant="outline"
              className="border-2 border-button-secondary-border bg-white/80 hover:bg-white text-button-secondary-foreground font-semibold text-base md:text-lg px-6 md:px-8 backdrop-blur-sm"
            >
              Login
            </Button>
          )}
        </div>

        {/* Cards de destaques rápidos */}
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3 md:gap-4 mt-8 md:mt-12 max-w-4xl mx-auto">
          {theme?.content?.heroCards && Array.isArray(theme.content.heroCards) ? (
            theme.content.heroCards.map((card: any, index: number) => {
              const IconComponent = getIconByName(card.icon || 'Coffee');
              return (
                <div
                  key={index}
                  className="p-3 md:p-4 bg-foreground/80 border border-primary/30 rounded-lg backdrop-blur-sm"
                >
                  <div className="flex flex-col md:flex-row items-center justify-center gap-2 mb-1">
                    <IconComponent className="h-8 w-8 md:h-6 md:w-6 text-primary" />
                    <h3 className="text-sm md:text-base font-display text-primary uppercase tracking-wide">
                      {card.title}
                    </h3>
                  </div>
                  <p className="text-xs text-background/80 text-center md:text-left">
                    {card.description}
                  </p>
                </div>
              );
            })
          ) : (
            // Cards fallback para manter compatibilidade com temas antigos sem heroCards
            <>
              <div className="p-3 md:p-4 bg-foreground/80 border border-primary/30 rounded-lg backdrop-blur-sm">
                <div className="flex flex-col md:flex-row items-center justify-center gap-2 mb-1">
                  <Coffee className="h-8 w-8 md:h-6 md:w-6 text-primary" />
                  <h3 className="text-sm md:text-base font-display text-primary uppercase tracking-wide">
                    Cafés Especiais
                  </h3>
                </div>
                <p className="text-xs text-background/80 text-center md:text-left">
                  Grãos selecionados e baristas experientes
                </p>
              </div>

              <div className="p-3 md:p-4 bg-foreground/80 border border-primary/30 rounded-lg backdrop-blur-sm">
                <div className="flex flex-col md:flex-row items-center justify-center gap-2 mb-1">
                  <Heart className="h-8 w-8 md:h-6 md:w-6 text-primary" />
                  <h3 className="text-sm md:text-base font-display text-primary uppercase tracking-wide">
                    Salgados & Doces
                  </h3>
                </div>
                <p className="text-xs text-background/80 text-center md:text-left">
                  Receitas artesanais preparadas diariamente
                </p>
              </div>

              <div className="p-3 md:p-4 bg-foreground/80 border border-primary/30 rounded-lg backdrop-blur-sm">
                <div className="flex flex-col md:flex-row items-center justify-center gap-2 mb-1">
                  <Users className="h-8 w-8 md:h-6 md:w-6 text-primary" />
                  <h3 className="text-sm md:text-base font-display text-primary uppercase tracking-wide">
                    Ambiente Aconchegante
                  </h3>
                </div>
                <p className="text-xs text-background/80 text-center md:text-left">
                  Perfeito para estudar, trabalhar ou relaxar
                </p>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
};

export default Hero;
