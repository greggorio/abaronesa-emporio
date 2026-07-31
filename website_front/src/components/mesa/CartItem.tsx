import { useTheme } from '@/contexts/ThemeContext';
import { ObservationEditor } from './ObservationEditor';
import { useMesaI18n } from '@/i18n/useMesaI18n';

export type CartItemData = {
  produtoId: number;
  skuId?: number;
  nome: string;
  preco: number;
  quantidade: number;
  observacoes?: string;
  origemDesconto?: string;
};

type Props = {
  item: CartItemData;
  id: number;
  onRemove: (id: number) => void;
  onIncrease: (id: number) => void;
  onDecrease: (id: number) => void;
  onChangeObservacao: (id: number, value: string) => void;
  mesaSlug?: string;
  t?: (key: string, vars?: Record<string, string | number>) => string;
};

export function CartItem({ item, id, onRemove, onIncrease, onDecrease, onChangeObservacao, mesaSlug, t: providedT }: Props) {
  const { theme } = useTheme();
  const { t: hookT } = useMesaI18n(mesaSlug);
  const t = providedT ?? hookT;
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? `text-[hsl(var(--mesa-text))]` : 'text-foreground';

  return (
    <div className="border border-foreground/20 rounded p-2 sm:p-3 bg-foreground/5 shadow-sm">
      <div className="flex justify-between items-start mb-2">
        <div className="flex-1">
          <div className={`font-medium text-xs sm:text-sm ${mesaTextColor}`}>{item.nome}</div>
          <div className={`text-[11px] sm:text-xs ${mesaTextColor}/70`}>
            {t('mesa.cart.priceEach', { price: item.preco.toFixed(2) })}
            {item.origemDesconto === 'PROMOCAO' ? <span className="ml-2 font-medium">{t('mesa.cart.promotion')}</span> : null}
            {item.origemDesconto === 'SOCIO' ? (
              <span className="ml-2 font-medium">{t('mesa.cart.memberPrice')}</span>
            ) : null}
          </div>
        </div>
        <button
          onClick={() => onRemove(id)}
          className="text-red-600 hover:text-red-700 px-2 py-1 text-sm"
          title={t('mesa.cart.remove')}
        >
          ✕
        </button>
      </div>

      <div className="flex items-center justify-between mb-2 gap-2">
        <div className="flex items-center gap-2">
          <button
            onClick={() => onDecrease(id)}
            className="border border-accent/30 px-2 py-1 rounded hover:bg-accent/10 w-7 h-7 sm:w-8 sm:h-8 flex items-center justify-center"
          >
            −
          </button>
          <span className={`text-xs sm:text-sm font-medium w-7 sm:w-8 text-center ${mesaTextColor}`}>{item.quantidade}</span>
          <button
            onClick={() => onIncrease(id)}
            className="border border-accent/30 px-2 py-1 rounded hover:bg-accent/10 w-7 h-7 sm:w-8 sm:h-8 flex items-center justify-center"
          >
            +
          </button>
        </div>
        <ObservationEditor
          value={item.observacoes || ''}
          onChange={(v) => onChangeObservacao(id, v)}
          mesaSlug={mesaSlug}
          t={t}
        />
        <div className={`font-medium text-sm sm:text-base whitespace-nowrap ${mesaTextColor}`}>R$ {(item.preco * item.quantidade).toFixed(2)}</div>
      </div>
    </div>
  );
}
