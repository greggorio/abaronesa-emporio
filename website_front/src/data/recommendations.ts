// Mock-based pairing resolver for product detail page (Villa)
// Keeps implementation simple and self-contained for the POC stage

export type SimpleSku = { id: number; variacao?: string; preco?: number; precoVenda?: number };
export type SimpleProduct = {
  id: number;
  nome: string;
  imagemPrincipal?: string;
  preco?: number;
  precoVenda?: number;
  skus?: SimpleSku[];
};
export type SimpleCategory = { id: number; nome: string; produtos: SimpleProduct[] };

export type PairingResolved = SimpleProduct & { nota?: string };

const notesBank: string[] = [
  'Equilibra sabores e textura',
  'Contraste de amargor e doçura',
  'Refresca e realça o prato',
  'Crocância que combina com a bebida',
  'Clássico da casa',
];

function pickNotes(i: number): string {
  return notesBank[i % notesBank.length];
}

function resolvePrice(p: SimpleProduct): number {
  if (typeof p.preco === 'number') return p.preco;
  if (typeof p.precoVenda === 'number') return p.precoVenda as number;
  const s = Array.isArray(p.skus) && p.skus.length > 0 ? p.skus[0] : undefined;
  if (!s) return 0;
  if (typeof s.preco === 'number') return s.preco;
  if (typeof s.precoVenda === 'number') return s.precoVenda as number;
  return 0;
}

function findByKeywords(all: SimpleProduct[], keywords: RegExp[]): SimpleProduct[] {
  return all.filter((p) => keywords.some((r) => r.test((p.nome || '').toLowerCase())));
}

/**
 * Returns up to `max` recommended products from the current cardápio, prioritizing
 * heuristic matches by name. Falls back to the first items (excluding current).
 */
export function getPairingsFor(
  product: SimpleProduct,
  cardapio: SimpleCategory[],
  max: number = 6
): PairingResolved[] {
  const name = (product?.nome || '').toLowerCase();
  const all: SimpleProduct[] = (cardapio || []).flatMap((c) => c.produtos || []);
  const pool = all.filter((p) => p && p.id !== product.id);

  let selected: SimpleProduct[] = [];
  try {
    if (/ipa|pilsen|lager|cerveja|chope/.test(name)) {
      // Bebida → porções/salgados/sanduíches
      selected = findByKeywords(pool, [
        /batata|frita|porção|porcao|onion|anel/i,
        /burger|hamb|sandu/i,
        /costela|frango|asa|nugget/i,
      ]);
    } else if (/burger|hamb|sandu|carne|costela/.test(name)) {
      // Sanduíches/carnes → bebidas
      selected = findByKeywords(pool, [
        /ipa|pilsen|lager|cerveja|chope/i,
        /refrigerante|suco|gin|drink|caip/i,
      ]);
    } else if (/pizza|massa|macarr|queijo/.test(name)) {
      selected = findByKeywords(pool, [
        /vinho|ipa|pilsen|refrigerante/i,
        /tábua|tabua|porção|batata|frita/i,
      ]);
    }
  } catch {
    // ignore heuristic errors and fallback below
  }

  if (!Array.isArray(selected)) selected = [];
  // Ensure unique and limit
  const unique: Record<number, true> = {};
  const bucket: SimpleProduct[] = [];
  for (const p of selected) {
    if (!p || unique[p.id]) continue;
    unique[p.id] = true;
    if (resolvePrice(p) > 0) bucket.push(p);
    if (bucket.length >= max) break;
  }
  // Fallback: fill with first items having price
  if (bucket.length < Math.max(3, Math.min(max, 6))) {
    for (const p of pool) {
      if (!p || unique[p.id]) continue;
      if (resolvePrice(p) <= 0) continue;
      unique[p.id] = true;
      bucket.push(p);
      if (bucket.length >= max) break;
    }
  }

  // Attach friendly notes
  return bucket.slice(0, max).map((p, i) => ({ ...p, nota: pickNotes(i) }));
}

