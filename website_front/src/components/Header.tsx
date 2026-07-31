import { Button } from "@/components/ui/button";
import { Menu as MenuIcon, LogIn, LogOut, User, Settings, UserCircle, X, Monitor, UtensilsCrossed, Languages } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import axios from '@/lib/axios';
import { useAuth } from "@/hooks/useAuth";
import { useTheme } from '@/contexts/ThemeContext';
import { useDeliveryI18n } from "@/i18n/useDeliveryI18n";
import { useSiteI18n } from "@/i18n/useSiteI18n";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

const NAV = [
  { labelKey: "delivery.header.nav.home", to: "/" },
  { labelKey: "delivery.header.nav.about", anchor: "sobre" },
  { labelKey: "delivery.header.nav.events", anchor: "eventos" },
  { labelKey: "delivery.header.nav.gallery", anchor: "galeria" },
  { labelKey: "delivery.header.nav.contact", anchor: "contato" },
  { labelKey: "delivery.header.nav.menu", to: "/cardapio" },
];

const Header = () => {
  const { theme } = useTheme();
  const [isScrolled, setIsScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [mesaAtiva, setMesaAtiva] = useState<{slug: string, nome: string} | null>(null);
  const [headerHeight, setHeaderHeight] = useState(0);
  const headerRef = useRef<HTMLElement | null>(null);
  const mesaCheckInFlight = useRef(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { t, locale, setLocale } = useDeliveryI18n();
  const { setLocale: setSiteLocale } = useSiteI18n();
  const localeOptions = [
    { label: "PT", value: "pt-BR" },
    { label: "EN", value: "en-US" },
    { label: "ES", value: "es-ES" },
  ] as const;
  const handleLocaleChange = (value: string) => {
    if (value === locale) return;
    setLocale(value as typeof locale);
    setSiteLocale(value as any);
  };

  useEffect(() => {
    setSiteLocale(locale as any);
  }, [locale, setSiteLocale]);

  // Hook de autenticação
  const { user, isAuthenticated, logout } = useAuth();
  const isAdminOrSystem = user?.roles?.some((role) => role === 'ADMIN' || role === 'SYSTEM');

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 50);
    };
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  // Medir altura do header dinamicamente e expor via CSS var
  useEffect(() => {
    const measure = () => {
      const h = headerRef.current?.getBoundingClientRect().height || 0;
      const hRounded = Math.ceil(h);
      if (hRounded !== headerHeight) {
        setHeaderHeight(hRounded);
        document.documentElement.style.setProperty("--header-h", `${hRounded}px`);
      }
    };

    // Observer para reagir a mudanças internas (wrap, menus, etc.)
    const ro = new ResizeObserver(() => measure());
    if (headerRef.current) ro.observe(headerRef.current);

    // Medir inicialmente e ao redimensionar janela
    const raf = requestAnimationFrame(measure);
    window.addEventListener("resize", measure);
    return () => {
      ro.disconnect();
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", measure);
    };
  }, [headerHeight]);

  // Verificar se há mesa ativa no localStorage
  useEffect(() => {
    let alive = true;

    const validateMesaSession = async (mesaSlug: string) => {
      try {
        const { data } = await axios.get(`/api/mesas/${mesaSlug}/sessao`, {
          headers: { 'Cache-Control': 'no-store' },
        });
        return data?.sessaoAtiva === true;
      } catch {
        return false;
      }
    };

    const checkActiveMesa = async () => {
      if (mesaCheckInFlight.current) return;
      mesaCheckInFlight.current = true;
      try {
        const keys = Object.keys(localStorage).filter(key => key.startsWith('qrGuest:'));
        for (const key of keys) {
          const mesaSlug = key.replace('qrGuest:', '');
          const data = localStorage.getItem(key);
          if (!data) continue;
          let guestData: { nomeExibicao?: string } | null = null;
          try {
            guestData = JSON.parse(data);
          } catch {
            try { localStorage.removeItem(key); } catch {}
            continue;
          }

          const isActive = await validateMesaSession(mesaSlug);
          if (!isActive) {
            try { localStorage.removeItem(key); } catch {}
            continue;
          }

          if (location.pathname !== `/m/${mesaSlug}` && alive) {
            setMesaAtiva({
              slug: mesaSlug,
              nome: guestData?.nomeExibicao || 'Convidado',
            });
            return;
          }
        }

        if (alive) setMesaAtiva(null);
      } finally {
        mesaCheckInFlight.current = false;
      }
    };

    void checkActiveMesa();
    const interval = setInterval(() => { void checkActiveMesa(); }, 5000);
    return () => {
      alive = false;
      clearInterval(interval);
    };
  }, [location.pathname]);

  const goTo = (item: (typeof NAV)[number]) => {
    if (item.to) {
      if (location.pathname === item.to) {
        // Se já está na página, rola para o topo
        window.scrollTo({ top: 0, behavior: "smooth" });
      } else {
        navigate(item.to);
      }
      setMobileMenuOpen(false);
      return;
    }
    if (item.anchor) {
      if (location.pathname === "/") {
        const el = document.getElementById(item.anchor);
        el?.scrollIntoView({ behavior: "smooth" });
      } else {
        navigate("/");
        setTimeout(() => {
          const el = document.getElementById(item.anchor!);
          el?.scrollIntoView({ behavior: "smooth" });
        }, 50);
      }
      setMobileMenuOpen(false);
    }
  };

  return (
    <>
      <header
        ref={headerRef}
        className={`fixed top-0 left-0 right-0 z-[60] transition-all duration-300 ${
          location.pathname === "/" && !isScrolled ? "bg-transparent" : "bg-background/95 backdrop-blur-md shadow-lg"
        }`}
        style={{ 
          WebkitTapHighlightColor: 'transparent',
          touchAction: 'manipulation'
        }}
      >
        <div className="container mx-auto px-4 py-4">
                <div className="flex items-center justify-between">
            <button 
              onClick={() => navigate('/')} 
              className="font-display leading-none group text-left touch-manipulation"
              style={{ WebkitTapHighlightColor: 'transparent' }}
            >
              <div className="flex items-baseline gap-2">
                <span className="text-2xl md:text-3xl tracking-[0.18em] text-header-text">{theme?.content?.name || 'Negócio'}</span>
                <span className="hidden md:inline text-[11px] tracking-[0.35em] text-accent px-2 py-0.5 rounded-full border border-accent/30 bg-accent/10">
                  {theme?.content?.businessType || 'TIPO'}
                </span>
              </div>
              <span className="md:hidden block text-[10px] tracking-[0.35em] text-accent mt-0.5">{theme?.content?.businessType || 'TIPO'}</span>
            </button>

            {/* Desktop Navigation */}
            <div className="hidden md:flex items-center gap-6">
              <nav className="flex items-center gap-6">
                {NAV.map((item) => (
                  <button
                    key={item.labelKey}
                    onClick={() => goTo(item)}
                    className="text-header-text hover:text-accent transition-colors font-medium touch-manipulation"
                    style={{ WebkitTapHighlightColor: 'transparent' }}
                  >
                    {t(item.labelKey)}
                  </button>
                ))}
              </nav>
              <Select value={locale} onValueChange={handleLocaleChange}>
                <SelectTrigger
                  className="h-7 py-0.5 px-3 text-[11px] font-medium border border-accent/30 bg-white/80 text-foreground hover:bg-accent/10 shadow-sm self-start rounded-full w-fit min-w-[60px]"
                  aria-label={t("delivery.header.localeSelectorAria")}
                >
                  <Languages className="h-3.5 w-3.5 mr-1" />
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {localeOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

                <div className="hidden md:flex items-center gap-3">
              {/* Botão Voltar para Mesa (se há mesa ativa) */}
              {mesaAtiva && (
                <Button
                  variant="outline"
                  onClick={() => navigate(`/m/${mesaAtiva.slug}`)}
                  className="border-button-secondary-border text-button-secondary-foreground hover:bg-button-secondary touch-manipulation"
                  style={{ WebkitTapHighlightColor: 'transparent' }}
                >
                  🍽️ {t("delivery.header.activeTable", { slug: mesaAtiva.slug })}
                </Button>
              )}

              {isAuthenticated && user ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <div 
                      className="flex items-center group cursor-pointer gap-2 px-3 py-2 rounded-md hover:bg-accent/10 transition-colors text-header-text touch-manipulation"
                      style={{ WebkitTapHighlightColor: 'transparent' }}
                    >
                      {user.fotoPerfil ? (
                        <img
                          src={user.fotoPerfil}
                          alt={user.nome}
                          className="h-8 w-8 rounded-full object-cover border border-accent/40"
                        />
                      ) : (
                        <User className="h-5 w-5 text-header-text" />
                      )}
                      <span className="text-sm text-header-text group-hover:text-accent transition-colors">
                        {user.nome?.split(' ')[0]}
                      </span>
                    </div>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="w-48 bg-card border-border">
                    {user.roles?.includes('CLIENTE') && (
                      <>
                        <DropdownMenuItem onClick={() => navigate('/areacliente')} className="cursor-pointer hover:bg-accent/10">
                          <UserCircle className="w-4 h-4 mr-2" />
                          <span>{t("delivery.header.dropdown.customerArea")}</span>
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                      </>
                    )}
                    {(user.roles?.includes('ADMIN') || user.roles?.includes('SYSTEM') || user.roles?.includes('FUNCIONARIO')) && (
                      <>
                        <DropdownMenuItem onClick={() => navigate('/admin/dashboard')} className="cursor-pointer hover:bg-accent/10">
                          <Settings className="w-4 h-4 mr-2" />
                          <span>{t("delivery.header.dropdown.adminPanel")}</span>
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                      </>
                    )}
                    {(user.roles?.includes('KDS') || isAdminOrSystem) && (
                      <>
                        <DropdownMenuItem onClick={() => navigate('/kds')} className="cursor-pointer hover:bg-accent/10">
                          <Monitor className="w-4 h-4 mr-2" />
                          <span>{t("delivery.header.dropdown.kds")}</span>
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                      </>
                    )}
                    {(user.roles?.includes('WAITER') || user.roles?.includes('CAIXA') || isAdminOrSystem) && (
                      <>
                        <DropdownMenuItem onClick={() => navigate('/waiter')} className="cursor-pointer hover:bg-accent/10">
                          <UtensilsCrossed className="w-4 h-4 mr-2" />
                          <span>{t("delivery.header.dropdown.waiterPanel")}</span>
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                      </>
                    )}
                    <DropdownMenuItem onClick={logout} className="cursor-pointer hover:bg-accent/10">
                      <LogOut className="w-4 h-4 mr-2" />
                      <span>{t("delivery.header.dropdown.logout")}</span>
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <Link to="/login">
                    <Button
                      variant="outline"
                      className="border-button-secondary-border text-button-secondary-foreground hover:bg-button-secondary touch-manipulation"
                      style={{ WebkitTapHighlightColor: 'transparent' }}
                    >
                      <LogIn className="w-4 h-4 mr-2" />
                      {t("delivery.header.login")}
                    </Button>
                  </Link>
              )}

              <Button
                onClick={() => window.open("https://wa.me/5515988354989?text=Olá! Gostaria de fazer uma reserva", "_blank")}
                className="bg-button-primary hover:bg-button-primary/90 text-button-primary-foreground font-semibold touch-manipulation"
                style={{ WebkitTapHighlightColor: 'transparent' }}
              >
                {t("delivery.header.reserveTable")}
              </Button>
            </div>

            {/* Mobile Menu Button */}
            <div className="md:hidden flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                  className="text-header-text p-2 touch-manipulation"
                  aria-label={t("delivery.header.mobileMenu")}
                  aria-expanded={mobileMenuOpen}
                  style={{ WebkitTapHighlightColor: 'transparent' }}
                >
                {mobileMenuOpen ? <X size={24} /> : <MenuIcon size={24} />}
              </button>
            </div>
          </div>

          {/* Mobile Menu */}
          {mobileMenuOpen && (
            <nav className="md:hidden mt-4 pb-4 flex flex-col gap-3 animate-fade-in">
              {mesaAtiva && (
                <Button
                  variant="outline"
                  onClick={() => {
                    navigate(`/m/${mesaAtiva.slug}`);
                    setMobileMenuOpen(false);
                  }}
                  className="w-full border-button-secondary-border text-button-secondary-foreground hover:bg-button-secondary touch-manipulation"
                  style={{ WebkitTapHighlightColor: 'transparent' }}
                >
                  🍽️ Voltar para Mesa {mesaAtiva.slug}
                </Button>
              )}
              <div className="flex items-center gap-2">
                <Select value={locale} onValueChange={handleLocaleChange}>
                  <SelectTrigger
                    className="h-7 py-0.5 px-3 text-[11px] font-medium border border-accent/30 bg-white/80 text-foreground hover:bg-accent/10 shadow-sm self-start rounded-full w-fit min-w-[60px]"
                    aria-label={t("delivery.header.localeSelectorAria")}
                  >
                    <Languages className="h-3.5 w-3.5 mr-1" />
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {localeOptions.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {NAV.map((item) => (
                <button
                  key={item.labelKey}
                  onClick={() => goTo(item)}
                  className="text-header-text hover:text-accent transition-colors font-medium text-left py-2 touch-manipulation"
                  style={{ WebkitTapHighlightColor: 'transparent' }}
                >
                  {t(item.labelKey)}
                </button>
              ))}

              {isAuthenticated && user ? (
                <div className="mt-4 pt-4 border-t border-border flex flex-col gap-2">
                  <div className="flex items-center gap-2 mb-2 px-2">
                    {user.fotoPerfil ? (
                      <img
                        src={user.fotoPerfil}
                        alt={user.nome}
                        className="h-8 w-8 rounded-full object-cover border border-accent/40"
                      />
                    ) : (
                      <User className="h-5 w-5 text-header-text" />
                    )}
                    <span className="text-sm text-header-text">{user.nome?.split(' ')[0]}</span>
                  </div>

                  {user.roles?.includes('CLIENTE') && (
                    <Button
                      variant="outline"
                      className="w-full border-button-secondary-border text-button-secondary-foreground hover:bg-button-secondary touch-manipulation"
                      onClick={() => {
                        navigate('/areacliente');
                        setMobileMenuOpen(false);
                      }}
                      style={{ WebkitTapHighlightColor: 'transparent' }}
                    >
                      <UserCircle className="w-4 h-4 mr-2" />
                      Área do Cliente
                    </Button>
                  )}

                  {(user.roles?.includes('ADMIN') || user.roles?.includes('SYSTEM') || user.roles?.includes('FUNCIONARIO')) && (
                    <Button
                      variant="outline"
                      className="w-full border-button-secondary-border text-button-secondary-foreground hover:bg-button-secondary touch-manipulation"
                      onClick={() => {
                        navigate('/admin/dashboard');
                        setMobileMenuOpen(false);
                      }}
                      style={{ WebkitTapHighlightColor: 'transparent' }}
                    >
                      <Settings className="w-4 h-4 mr-2" />
                      Painel Admin
                    </Button>
                  )}

                  {(user.roles?.includes('KDS') || isAdminOrSystem) && (
                    <Button
                      variant="outline"
                      className="w-full border-button-secondary-border text-button-secondary-foreground hover:bg-button-secondary touch-manipulation"
                      onClick={() => {
                        navigate('/kds');
                        setMobileMenuOpen(false);
                      }}
                      style={{ WebkitTapHighlightColor: 'transparent' }}
                    >
                      KDS
                    </Button>
                  )}

                  {(user.roles?.includes('WAITER') || user.roles?.includes('CAIXA') || isAdminOrSystem) && (
                    <Button
                      variant="outline"
                      className="w-full border-button-secondary-border text-button-secondary-foreground hover:bg-button-secondary touch-manipulation"
                      onClick={() => {
                        navigate('/waiter');
                        setMobileMenuOpen(false);
                      }}
                      style={{ WebkitTapHighlightColor: 'transparent' }}
                    >
                      Painel Waiter
                    </Button>
                  )}

                  <Button
                    variant="outline"
                    className="w-full border-button-secondary-border text-button-secondary-foreground hover:bg-button-secondary touch-manipulation"
                    onClick={() => {
                      logout();
                      setMobileMenuOpen(false);
                    }}
                    style={{ WebkitTapHighlightColor: 'transparent' }}
                  >
                    <LogOut className="w-4 h-4 mr-2" />
                    Sair
                  </Button>
                </div>
              ) : (
                <Link to="/login" onClick={() => setMobileMenuOpen(false)}>
                  <Button
                    variant="outline"
                    className="w-full mt-4 border-button-secondary-border text-button-secondary-foreground hover:bg-button-secondary touch-manipulation"
                    style={{ WebkitTapHighlightColor: 'transparent' }}
                  >
                    <LogIn className="w-4 h-4 mr-2" />
                    Login
                  </Button>
                </Link>
              )}

              <Button
                onClick={() => window.open("https://wa.me/5515988354989?text=Olá! Gostaria de fazer uma reserva", "_blank")}
                className="bg-button-primary hover:bg-button-primary/90 text-button-primary-foreground font-semibold w-full touch-manipulation"
                style={{ WebkitTapHighlightColor: 'transparent' }}
              >
                Reservar Mesa
              </Button>
            </nav>
          )}
        </div>
      </header>
      {/* Espaçador: garante que o conteúdo abaixo não fique sob o header */}
      <div aria-hidden style={{ height: headerHeight }} />
    </>
  );
};

export default Header;
