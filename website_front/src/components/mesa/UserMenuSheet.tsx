import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Home, LogOut, MapPin, Share2, Star, User } from 'lucide-react';
import { useMesaI18n } from '@/i18n/useMesaI18n';

type GuestInfo = {
  nomeExibicao?: string;
};

type UserInfo = {
  email?: string | null;
  roles?: string[];
  fotoPerfil?: string | null;
  nome?: string | null;
};

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  guest: GuestInfo | null;
  user: UserInfo | null;
  isAuthenticated: boolean;
  onGoHome: () => void;
  onGoFavorites: () => void;
  onGoClientArea: () => void;
  onOpenLocation: () => void;
  onOpenShare: () => void;
  onLogout: () => void;
  mesaSlug?: string;
  t?: (key: string, vars?: Record<string, string | number>) => string;
};

export function UserMenuSheet({
  open,
  onOpenChange,
  guest,
  user,
  isAuthenticated,
  onGoHome,
  onGoFavorites,
  onGoClientArea,
  onOpenLocation,
  onOpenShare,
  onLogout,
  mesaSlug,
  t: providedT,
}: Props) {
  const { t: hookT } = useMesaI18n(mesaSlug);
  const t = providedT ?? hookT;
  const closeMenu = () => onOpenChange(false);
  const initials = guest?.nomeExibicao?.[0]?.toUpperCase() || '?';

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="bg-white border-l border-accent/20">
        <SheetHeader className="pb-4 border-b border-accent/20">
          <SheetTitle className="sr-only">{t('mesa.userMenu.title')}</SheetTitle>
          <div className="flex items-center gap-3">
            {isAuthenticated && user?.fotoPerfil ? (
              <img
                src={user.fotoPerfil}
                alt={user.nome || t('mesa.userMenu.userFallback')}
                className="w-12 h-12 rounded-full object-cover border-2 border-accent/30"
              />
            ) : (
              <div className="w-12 h-12 rounded-full bg-accent/20 border-2 border-accent/30 flex items-center justify-center text-lg font-medium text-mesa-text">
                {initials}
              </div>
            )}
            <div className="text-left">
              <div className="text-mesa-text font-medium">{t('mesa.userMenu.greeting', { name: guest?.nomeExibicao })}</div>
              {user?.email && <div className="text-xs text-mesa-text/70">{user.email}</div>}
            </div>
          </div>
        </SheetHeader>

        <div className="mt-6 space-y-2">
          <Button
            variant="outline"
            onClick={() => {
              closeMenu();
              onGoHome();
            }}
            className="w-full justify-start border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]"
          >
            <Home className="w-4 h-4 mr-3" />
            {t('mesa.userMenu.home')}
          </Button>

          {isAuthenticated && (
            <Button
              variant="outline"
            onClick={() => {
              closeMenu();
              onGoFavorites();
            }}
            className="w-full justify-start border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]"
          >
            <Star className="w-4 h-4 mr-3" />
            {t('mesa.userMenu.favorites')}
          </Button>
          )}

          {isAuthenticated && user?.roles?.includes('CLIENTE') && (
            <Button
              variant="outline"
            onClick={() => {
              closeMenu();
              onGoClientArea();
            }}
            className="w-full justify-start border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]"
          >
            <User className="w-4 h-4 mr-3" />
            {t('mesa.userMenu.clientArea')}
          </Button>
          )}

          <Button
            variant="outline"
            onClick={() => {
              closeMenu();
              onOpenLocation();
            }}
            className="w-full justify-start border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]"
          >
            <MapPin className="w-4 h-4 mr-3" />
            {t('mesa.userMenu.location')}
          </Button>

          <Button
            variant="outline"
            onClick={() => {
              closeMenu();
              onOpenShare();
            }}
            className="w-full justify-start border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]"
          >
            <Share2 className="w-4 h-4 mr-3" />
            {t('mesa.userMenu.share')}
          </Button>

          {isAuthenticated && (
            <Button
              variant="outline"
              onClick={() => {
                closeMenu();
                onLogout();
              }}
              className="w-full justify-start border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]"
            >
              <LogOut className="w-4 h-4 mr-3" />
              {t('mesa.userMenu.logout')}
            </Button>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
