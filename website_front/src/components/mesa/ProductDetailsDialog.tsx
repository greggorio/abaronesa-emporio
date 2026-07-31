import { useEffect, useMemo, useState } from 'react';
import { useTheme } from '@/contexts/ThemeContext';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { apiConfig } from '@/config/api';
import { resolveBasePrice, resolvePromoPrice } from '@/utils/cardapio';
import { useMesaI18n } from '@/i18n/useMesaI18n';

type ProductType = {
  id: number;
  nome: string;
  preco?: number;
  precoVenda?: number;
  preco_promocional?: number;
  precoPromocional?: number;
  imagemPrincipal?: string;
  destaque?: boolean;
  produto_disponivel?: boolean;
  horarios_disponiveis?: { diaSemana: string; horarioInicio: string; horarioFim: string }[];
  skus?: { id: number; variacao?: string; preco?: number; precoVenda?: number; precoPromocional?: number; origemDesconto?: string }[];
  midias?: { tipo: 'VIDEO' | 'IMAGEM'; url: string; titulo?: string }[];
  descricao?: string;
  skuId?: number;
  skuVariacao?: string;
  origem_desconto?: string;
  origemDesconto?: string;
};

type PairingItem = { produtoHarmonizado: ProductType; id?: number; descricao?: string };

type Props = {
  open: boolean;
  product: ProductType | null;
  onOpenChange: (open: boolean) => void;
  onAddSku: (p: ProductType, skuId: number, productName: string, variacao: string | undefined, preco: number) => void;
  onAdd: (p: ProductType, preco: number) => void;
  pairings: PairingItem[];
  mesaSlug?: string;
  t?: (key: string, vars?: Record<string, string | number>) => string;
};

export function ProductDetailsDialog({ open, product, onOpenChange, onAddSku, onAdd, pairings, mesaSlug, t: providedT }: Props) {
  const [selectedMedia, setSelectedMedia] = useState<{ type: 'video' | 'image'; url: string } | null>(null);
  const { theme } = useTheme();
  const { t: hookT } = useMesaI18n(mesaSlug);
  const t = providedT ?? hookT;
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? `text-[hsl(var(--mesa-text))]` : 'text-foreground';

  useEffect(() => {
    if (!product) return;
    const videoMidia = product.midias?.find((m) => m.tipo === 'VIDEO');
    if (videoMidia) {
      setSelectedMedia({ type: 'video', url: videoMidia.url });
    } else if (product.imagemPrincipal) {
      setSelectedMedia({ type: 'image', url: product.imagemPrincipal });
    } else {
      setSelectedMedia(null);
    }
  }, [product]);

  const pairingsLimited = useMemo(() => (pairings || []).slice(0, 6), [pairings]);

  if (!product) return null;

  const videoMidia = product.midias?.find((m: any) => m.tipo === 'VIDEO');
  const imagensMidias = product.midias?.filter((m: any) => m.tipo === 'IMAGEM') || [];

  const allImages: string[] = [];
  if (product.imagemPrincipal) {
    allImages.push(product.imagemPrincipal);
  }
  imagensMidias.forEach((m: any) => {
    if (m.url && m.url !== product.imagemPrincipal) {
      allImages.push(m.url);
    }
  });

  const hasMedia = videoMidia || allImages.length > 0;
  const origemDesconto = product.origem_desconto ?? product.origemDesconto;
  const descontoLabel = origemDesconto === 'PROMOCAO'
    ? t('mesa.product.promo')
    : origemDesconto === 'SOCIO'
      ? t('mesa.product.memberPrice', { group: t('mesa.product.defaultMember') })
      : null;
  const basePrice = resolveBasePrice(product);
  const promoPrice = resolvePromoPrice(product);
  const hasPromo = typeof promoPrice === 'number' && typeof basePrice === 'number' && promoPrice < basePrice;
  const finalPrice = typeof promoPrice === 'number' ? promoPrice : basePrice;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className={`bg-white ${mesaTextColor} border border-accent/20 w-[92vw] sm:w-auto max-w-2xl max-h-[92vh] overflow-hidden`}>
        <div className="space-y-4">
          <DialogHeader>
            <DialogTitle className={`${mesaTextColor} text-base sm:text-lg`}>{product.nome}</DialogTitle>
            <DialogDescription className={`${mesaTextColor}/70 text-[13px] sm:text-sm`}>
              {product.descricao || t('mesa.product.noDescription')}
            </DialogDescription>
          </DialogHeader>

          {hasMedia && (
            <div className="flex gap-3 items-start">
              <div className="flex-1">
                {selectedMedia?.type === 'video' ? (
                  <video
                    src={apiConfig.getMediaUrl(selectedMedia.url)}
                    controls
                    className="w-full h-[36vh] sm:h-[40vh] rounded border border-accent/20 bg-black object-contain"
                    autoPlay={false}
                  >
                    {t('mesa.product.videoNotSupported')}
                  </video>
                ) : selectedMedia?.type === 'image' ? (
                  <img
                    src={apiConfig.getMediaUrl(selectedMedia.url)}
                    alt={product.nome}
                    className="w-full h-[36vh] sm:h-[40vh] object-contain rounded border border-accent/20"
                  />
                ) : null}
              </div>

              {(videoMidia || allImages.length > 1) && (
                <div className="flex flex-col gap-2 w-20 max-h-[36vh] sm:max-h-[40vh] overflow-hidden">
                  {videoMidia && (
                    <button
                      onClick={() => setSelectedMedia({ type: 'video', url: videoMidia.url })}
                      className={`relative w-20 h-20 rounded border-2 overflow-hidden ${
                        selectedMedia?.type === 'video' && selectedMedia.url === videoMidia.url ? 'border-accent' : 'border-accent/20'
                      }`}
                    >
                      <div className="absolute inset-0 bg-black/60 flex items-center justify-center">
                        <svg className="w-8 h-8 text-white" fill="currentColor" viewBox="0 0 20 20">
                          <path d="M6.3 2.841A1.5 1.5 0 004 4.11V15.89a1.5 1.5 0 002.3 1.269l9.344-5.89a1.5 1.5 0 000-2.538L6.3 2.84z" />
                        </svg>
                      </div>
                    </button>
                  )}

                  {allImages.map((imgUrl, idx) => (
                    <button
                      key={idx}
                      onClick={() => setSelectedMedia({ type: 'image', url: imgUrl })}
                      className={`w-20 h-20 rounded border-2 overflow-hidden ${
                        selectedMedia?.type === 'image' && selectedMedia.url === imgUrl ? 'border-accent' : 'border-accent/20'
                      }`}
                    >
                      <img
                        src={apiConfig.getMediaUrl(imgUrl)}
                        alt={`${product.nome} ${idx + 1}`}
                        className="w-full h-full object-cover"
                      />
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          {Array.isArray(product.skus) && product.skus.length > 1 ? (
            <div className="space-y-2">
              {product.skus.map((s: { id: number; variacao?: string; preco?: number; precoVenda?: number; precoPromocional?: number; origemDesconto?: string }) => {
                const spBase = typeof s.preco === 'number' ? s.preco : (typeof s.precoVenda === 'number' ? s.precoVenda : 0);
                const spPromo = typeof s.precoPromocional === 'number' ? s.precoPromocional : undefined;
                const sp = typeof spPromo === 'number' ? spPromo : spBase;
                const skuLabel = s.origemDesconto === 'PROMOCAO'
                  ? t('mesa.product.promo')
                  : s.origemDesconto === 'SOCIO'
                    ? t('mesa.product.memberPrice', { group: t('mesa.product.defaultMember') })
                    : null;
                return (
                  <div key={s.id} className="flex items-center justify-between border border-accent/20 rounded p-2 bg-white">
                    <div className={`text-xs sm:text-sm ${mesaTextColor}`}>
                      {s.variacao || t('mesa.product.defaultOption')}{' '}
                      <span className={`${mesaTextColor}/70`}>
                        • R$ {sp.toFixed(2)}{' '}
                        {typeof spPromo === 'number' && spPromo < spBase ? (
                          <span className={`line-through ${mesaTextColor}/50 ml-1`}>R$ {spBase.toFixed(2)}</span>
                        ) : null}
                        {skuLabel ? <span className="ml-2 text-[11px] font-medium">{skuLabel}</span> : null}
                      </span>
                    </div>
                    <button
                      className={`bg-accent hover:bg-accent/90 ${mesaTextColor} px-3 py-1 rounded text-xs sm:text-sm font-medium shadow-sm`}
                      onClick={() => onAddSku(product, s.id, product.nome, s.variacao, sp)}
                    >
                      {t('mesa.product.addButton')}
                    </button>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="flex items-center justify-between border border-accent/20 rounded p-3 bg-white">
                <div className={`text-xs sm:text-sm ${mesaTextColor}`}>
                {t('mesa.product.priceLabel')}{' '}
                <span className={`${mesaTextColor}/70`}>
                  {hasPromo ? (
                    <>
                      R$ {promoPrice!.toFixed(2)} <span className={`line-through ${mesaTextColor}/50 ml-1`}>R$ {basePrice.toFixed(2)}</span>
                      {descontoLabel ? <span className="ml-2 text-[11px] font-medium">{descontoLabel}</span> : null}
                    </>
                  ) : (
                    <>R$ {((product.preco ?? product.precoVenda) || 0).toFixed(2)}</>
                  )}
                </span>
              </div>
              <button
                className={`bg-accent hover:bg-accent/90 ${mesaTextColor} px-3 py-1 rounded text-xs sm:text-sm font-medium shadow-sm`}
                onClick={() => {
                  onAdd(product, finalPrice);
                }}
              >
                {t('mesa.product.addButton')}
              </button>
            </div>
          )}

          <div className="mt-4 block sm:hidden">
            <div className="flex items-center justify-between mb-2">
              <div className="text-sm font-medium">{t('mesa.product.pairingTitle')}</div>
              <div className={`text-[11px] ${mesaTextColor}/60`}>{t('mesa.product.pairingSubtitle')}</div>
            </div>
            {pairingsLimited.length === 0 ? (
              <div className={`text-xs ${mesaTextColor}/60`}>{t('mesa.product.pairingEmpty')}</div>
            ) : (
              <div className="no-scrollbar overflow-x-auto snap-x snap-mandatory w-full max-w-[78vw]">
                <div className="inline-flex gap-2">
                  {pairingsLimited.map((p: PairingItem, idx: number) => {
                    const ph = p.produtoHarmonizado;
                    if (!ph) return null;

                    const targetId = ph.skuId || ph.id;
                    const targetPrice = typeof ph.preco === 'number' ? ph.preco : 0;
                    const targetName = ph.nome;
                    const targetVar = ph.skuVariacao ?? t('mesa.product.defaultOption');

                    return (
                      <div key={p.id || idx} className="snap-start w-32 sm:w-40 shrink-0 border border-accent/20 rounded bg-white">
                        <div className="relative bg-black/20 rounded-t">
                          <img
                            src={apiConfig.getMediaUrl(ph.imagemPrincipal) || ''}
                            alt={targetName}
                            className="w-32 sm:w-40 h-20 sm:h-24 object-cover rounded-t"
                            onError={(e) => {
                              (e.currentTarget as HTMLImageElement).style.visibility = 'hidden';
                            }}
                          />
                        </div>
                        <div className="p-1.5">
                          <div className="text-[11px] font-medium leading-tight line-clamp-2" title={targetName}>{targetName}</div>
                          <div className="text-[10px] ${mesaTextColor}/60 mt-0.5 line-clamp-2" title={p.descricao || ph.descricao}>{p.descricao || ph.descricao}</div>
                          <div className="text-[11px] ${mesaTextColor}/70 mt-1">R$ {targetPrice.toFixed(2)}</div>
                          <button
                            className="mt-1.5 w-full border border-accent/30 rounded px-1.5 py-1 text-[11px] hover:bg-accent/10 disabled:opacity-50 ${mesaTextColor} font-medium shadow-sm"
                            onClick={() => {
                              if (ph.skuId) {
                                onAddSku(ph, ph.skuId, targetName, targetVar === t('mesa.product.defaultOption') ? undefined : targetVar, targetPrice);
                              } else {
                                onAdd(ph, targetPrice);
                              }
                            }}
                          >
                            {t('mesa.product.addButton')}
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
