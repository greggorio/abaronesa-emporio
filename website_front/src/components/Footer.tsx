import { useTheme } from '@/contexts/ThemeContext';
import { useSiteI18n } from '@/i18n/useSiteI18n';

const Footer = () => {
  const { theme } = useTheme();
  const businessName = theme?.content?.name || 'Negócio';
  const businessType = theme?.content?.businessType || 'TIPO';
  const { t } = useSiteI18n();
  const year = new Date().getFullYear();

  return (
    <footer className="bg-background border-t-2 border-accent/20 py-8">
      <div className="container mx-auto px-4">
        <div className="text-center">
          <h3 className="font-display text-4xl text-accent mb-2">{businessName}</h3>
          <p className="text-muted-foreground mb-4">
            {t('site.footer.tagline', { businessType })}
          </p>
          <p className="text-sm text-muted-foreground/60">
            {t('site.footer.copyright', { year, businessName })}
          </p>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
