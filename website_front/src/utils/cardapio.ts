export type CategoriaComProdutos<TProduto = any> = {
  produtos?: TProduto[];
  [key: string]: any;
};

const num = (v: any) => (typeof v === 'number' ? v : undefined);
const promoProduto = (p: any) => num(p?.preco_promocional ?? p?.precoPromocional);
const baseProduto = (p: any) => num(p?.preco ?? p?.precoVenda);
const promoSku = (s: any) => num(s?.precoPromocional ?? s?.preco_promocional);
const baseSku = (s: any) => num(s?.preco ?? s?.precoVenda);

export const productHasValidPrice = (p: any) => {
  const price = promoProduto(p) ?? baseProduto(p);
  const hasSkuPrice = Array.isArray(p?.skus)
    ? p.skus.some((s: any) => {
        const sp = promoSku(s) ?? baseSku(s);
        return typeof sp === 'number' && sp > 0;
      })
    : false;
  return (typeof price === 'number' && price > 0) || hasSkuPrice;
};

export const sanitizeCardapio = <T extends CategoriaComProdutos>(data: T[]): T[] => {
  return (data || [])
    .map((cat) => ({
      ...cat,
      produtos: (cat.produtos || []).filter(productHasValidPrice),
    }))
    .filter((cat) => cat.produtos && cat.produtos.length > 0);
};

export const resolveBasePrice = (p: any) => {
  const base = baseProduto(p);
  if (typeof base === 'number') return base;
  const s = Array.isArray(p?.skus) && p.skus.length > 0 ? p.skus[0] : null;
  if (s) {
    const sp = baseSku(s);
    if (typeof sp === 'number') return sp;
  }
  return 0;
};

export const resolvePromoPrice = (p: any) => {
  const promo = promoProduto(p);
  if (typeof promo === 'number') return promo;
  const s = Array.isArray(p?.skus) && p.skus.length > 0 ? p.skus[0] : null;
  if (s) {
    const sp = promoSku(s);
    if (typeof sp === 'number') return sp;
  }
  return undefined;
};

export const resolvePrice = (p: any) => {
  const promo = resolvePromoPrice(p);
  if (typeof promo === 'number') return promo;
  return resolveBasePrice(p);
};
