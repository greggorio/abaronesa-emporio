import { useTheme } from '@/contexts/ThemeContext';
import { useEffect, useState } from 'react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { FileText, Pencil } from 'lucide-react';
import { useMesaI18n } from '@/i18n/useMesaI18n';

type Props = {
  value: string;
  onChange: (v: string) => void;
  maxLength?: number;
  mesaSlug?: string;
  t?: (key: string, vars?: Record<string, string | number>) => string;
};

export function ObservationEditor({ value, onChange, maxLength = 200, mesaSlug, t: providedT }: Props) {
  const [open, setOpen] = useState(false);
  const [temp, setTemp] = useState(value || '');

  const { theme } = useTheme();
  const { t: hookT } = useMesaI18n(mesaSlug);
  const t = providedT ?? hookT;
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? `text-[hsl(var(--mesa-text))]` : 'text-foreground';

  useEffect(() => {
    if (open) setTemp(value || '');
  }, [open, value]);

  const hasText = (value || '').trim().length > 0;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
        className={`border ${hasText ? 'border-accent' : 'border-accent/30'} px-2 py-1 rounded text-xs hover:bg-accent/10 flex items-center gap-1`}
        title={hasText ? t('mesa.observations.editTitle') : t('mesa.observations.addTitle')}
      >
        <span className="relative inline-block w-4 h-4">
            <FileText className={`absolute w-4 h-4 ${mesaTextColor}/80`} />
            <Pencil className="absolute w-3 h-3 text-accent" style={{ top: -2, right: -2 }} />
        </span>
        <span>{t('mesa.observations.shortLabel')}</span>
      </button>
    </PopoverTrigger>
      <PopoverContent side="top" align="end" className="w-72 bg-white text-foreground border border-accent/20 shadow-lg">
        <div className="space-y-2">
          <div className={`text-xs ${mesaTextColor}/70`}>{t('mesa.observations.title')}</div>
          <textarea
            className={`w-full border border-accent/30 bg-transparent rounded px-2 py-1.5 text-xs placeholder:${mesaTextColor}/40 resize-none`}
            rows={3}
            maxLength={maxLength}
            placeholder={t('mesa.observations.placeholder')}
            value={temp}
            onChange={(e) => setTemp(e.target.value)}
          />
          <div className="flex items-center justify-between">
            <div className={`text-[11px] ${mesaTextColor}/60`}>
              {temp.length}/{maxLength}
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                className={`text-[11px] px-2 py-1 rounded border border-accent/30 hover:bg-accent/10 ${mesaTextColor}`}
                onClick={() => setTemp('')}
              >
                {t('mesa.observations.clear')}
              </button>
              <button
                type="button"
                className={`text-[11px] px-2 py-1 rounded bg-accent text-foreground hover:bg-accent/90`}
                onClick={() => {
                  onChange(temp);
                  setOpen(false);
                }}
              >
                {t('mesa.observations.save')}
              </button>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
