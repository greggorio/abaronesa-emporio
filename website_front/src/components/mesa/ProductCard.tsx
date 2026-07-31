import { useTheme } from '@/contexts/ThemeContext';
import { useMesaI18n } from '@/i18n/useMesaI18n';
import { apiConfig } from '@/config/api';
import { resolveBasePrice, resolvePrice, resolvePromoPrice } from '@/utils/cardapio';
import { Image as ImageIcon, Video as VideoIcon, Wine as WineIcon } from 'lucide-react';

type Sku = { id: number; variacao?: string; preco?: number; precoVenda?: number; origemDesconto?: string };

export type ProductType = {
  id: number;
  nome: string;
  preco?: number;
  precoVenda?: number;
  imagemPrincipal?: string;
  skus?: Sku[];
  midias?: { tipo: 'VIDEO' | 'IMAGEM'; url: string; titulo?: string }[];
  harmonizacoes?: any[];
  produtosHarmonizados?: any[];
  pairings?: any[];
  sugestoesHarmonizacao?: any[];
  temHarmonizacao?: boolean;
  descricao?: string;
  produto_disponivel?: boolean;
  horarios_disponiveis?: { diaSemana: string; horarioInicio: string; horarioFim: string }[];
  preco_promocional?: number;
  precoPromocional?: number;
  origem_desconto?: string;
  origemDesconto?: string;
};

type Props = {
  product: ProductType;
  onOpenDetails: (p: ProductType) => void;
  onAdd: (p: ProductType, preco: number) => void;
  onAddSku: (p: ProductType, skuId: number, productName: string, variacao: string | undefined, preco: number) => void;
  disabled?: boolean;
  grupoLabel?: string | null;
  hasPairingsHint?: boolean;
  mesaSlug?: string;
  t?: (key: string, vars?: Record<string, string | number>) => string;
};

export function ProductCard({
  product,
  onOpenDetails,
  onAdd,
  onAddSku,
  disabled,
  grupoLabel,
  hasPairingsHint,
  mesaSlug,
  t: providedT,
}: Props) {
  const { theme } = useTheme();
  const { t: hookT } = useMesaI18n(mesaSlug);
  const t = providedT ?? hookT;
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? `text-[hsl(var(--mesa-text))]` : 'text-foreground';

  const price = resolvePrice(product);
  const promo = resolvePromoPrice(product);
  const basePrice = resolveBasePrice(product);
  const hasPromo = typeof promo === 'number' && typeof basePrice === 'number' && promo < basePrice;
  const hasMultipleSkus = Array.isArray(product.skus) && product.skus.length > 1;
  const origemDesconto = product.origem_desconto ?? product.origemDesconto;
  const descontoLabel = origemDesconto === 'PROMOCAO'
    ? t('mesa.product.promo')
    : origemDesconto === 'SOCIO'
      ? t('mesa.product.memberPrice', { group: grupoLabel || t('mesa.product.defaultMember') })
      : null;

  const galleryImages = Array.isArray(product.midias)
    ? product.midias.filter((m) => m.tipo === 'IMAGEM')
    : [];
  const hasGalleryImages = galleryImages.length > 0;
  const hasVideo = Array.isArray(product.midias) && product.midias.some((m) => m.tipo === 'VIDEO');
  const pairingSources = [
    product.harmonizacoes,
    product.produtosHarmonizados,
    product.pairings,
    product.sugestoesHarmonizacao,
  ];
  const hasPairingsFromSources = pairingSources.some((list) => Array.isArray(list) && list.length > 0);
  const hasPairings = hasPairingsHint ?? product.temHarmonizacao ?? hasPairingsFromSources;

  return (
    <div
      key={product.id}
      className="border border-accent/20 rounded-lg p-3 bg-accent/5 shadow-sm hover:shadow-md transition-all hover:bg-accent/10"
    >
      <div className="flex items-center gap-3">
        <button onClick={() => onOpenDetails(product)} className="shrink-0">
          <img
            src={apiConfig.getMediaUrl(product.imagemPrincipal) || ''}
            alt={product.nome}
            className="w-12 h-12 sm:w-14 sm:h-14 rounded object-cover bg-accent/10 border border-accent/20"
            onError={(e) => {
              (e.currentTarget as HTMLImageElement).style.visibility = 'hidden';
            }}
          />
        </button>

        <div className="flex-1">
          <button onClick={() => onOpenDetails(product)} className="text-left">
            <div className={`font-medium text-sm sm:text-base leading-tight ${mesaTextColor}`}>{product.nome}</div>
            {hasMultipleSkus ? (
              <div className={`text-[11px] sm:text-xs ${mesaTextColor}/60`}>
                {t('mesa.product.multipleSkus')}
              </div>
            ) : (
              typeof price === 'number' && (
                <div className={`text-xs sm:text-sm ${mesaTextColor}/70`}>
                  {hasPromo ? (
                    <span className="text-accent font-semibold">
                      R$ {promo!.toFixed(2)} <span className={`line-through ${mesaTextColor}/50 ml-1`}>R$ {basePrice.toFixed(2)}</span>
                      {descontoLabel ? <span className="ml-2 text-[11px] font-medium">{descontoLabel}</span> : null}
                    </span>
                  ) : (
                    <>
                      R$ {price.toFixed(2)} • {t('mesa.product.seeDetails')}
                    </>
                  )}
                </div>
              )
            )}
            {(hasGalleryImages || hasVideo || hasPairings) && (
              <div className="mt-1 flex items-center gap-1 text-[10px] sm:text-xs text-mesa-text/50">
                {hasGalleryImages && (
                  <span
                    className="flex h-5 w-5 items-center justify-center rounded-full bg-accent/10 text-accent"
                    title={t('mesa.product.gallery')}
                  >
                    <ImageIcon className="h-3 w-3" />
                  </span>
                )}
                {hasVideo && (
                  <span
                    className="flex h-5 w-5 items-center justify-center rounded-full bg-accent/10 text-accent"
                    title={t('mesa.product.video')}
                  >
                    <VideoIcon className="h-3 w-3" />
                  </span>
                )}
                {hasPairings && (
                  <span
                    className="flex h-5 w-5 items-center justify-center rounded-full bg-accent/10 text-accent"
                    title={t('mesa.product.pairing')}
                  >
                    <WineIcon className="h-3 w-3" />
                  </span>
                )}
              </div>
            )}
          </button>

          {hasMultipleSkus && (
            <div className="mt-2 grid grid-cols-1 gap-2">
              {product.skus?.map((s: Sku) => {
                    const spPromo = typeof (s as any).precoPromocional === 'number' ? (s as any).precoPromocional : undefined;
                    const spBase = typeof s.preco === 'number' ? s.preco : (typeof s.precoVenda === 'number' ? s.precoVenda : 0);
                    const sp = typeof spPromo === 'number' ? spPromo : spBase;
                    const skuLabel = s.origemDesconto === 'PROMOCAO'
                      ? t('mesa.product.promo')
                      : s.origemDesconto === 'SOCIO'
                        ? t('mesa.product.memberPrice', { group: grupoLabel || t('mesa.product.defaultMember') })
                        : null;
                    return (
                  <div key={s.id} className="flex items-center justify-between border border-accent/20 rounded p-2 bg-white shadow-sm">
                    <div className={`text-xs sm:text-sm ${mesaTextColor}`}>
                      {s.variacao || t('mesa.product.defaultOption')}{' '}
                      {typeof sp === 'number' && (
                        <span className={`${mesaTextColor}/70`}>
                          • R$ {sp.toFixed(2)}{' '}
                          {typeof spPromo === 'number' && spPromo < spBase ? (
                            <span className={`line-through ${mesaTextColor}/50 ml-1`}>R$ {spBase.toFixed(2)}</span>
                          ) : null}
                          {skuLabel ? <span className="ml-2 text-[11px] font-medium">{skuLabel}</span> : null}
                        </span>
                      )}
                    </div>
                    <button
                      className={`bg-accent hover:bg-accent/90 ${mesaTextColor} px-3 py-1.5 rounded-lg text-xs sm:text-sm font-medium shadow-sm`}
                      onClick={() => onAddSku(product, s.id, product.nome, s.variacao, typeof sp === 'number' ? sp : 0)}
                      disabled={disabled}
                    >
                      {t('mesa.product.addButton')}
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {!hasMultipleSkus && (
          <button
            className={`bg-accent hover:bg-accent/90 ${mesaTextColor} px-3 py-1.5 rounded-lg text-xs sm:text-sm font-medium shadow-sm`}
            onClick={() => onAdd(product, typeof price === 'number' ? price : 0)}
            disabled={disabled}
          >
            {t('mesa.product.addButton')}
          </button>
        )}
      </div>
    </div>
  );
}
