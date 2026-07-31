import { useEffect, useState } from 'react';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import { badgeClass, statusLabel } from '@/utils/status';
import { useMesaI18n } from '@/i18n/useMesaI18n';

type Pessoa = { sessaoConvidadoId: number; nome?: string; name?: string };

import { useTheme } from '@/contexts/ThemeContext';

export function MesaPedidosList({
  sessaoMesaId,
  pessoas,
  mesaSlug,
  t: providedT,
}: {
  sessaoMesaId: number;
  pessoas: Pessoa[];
  mesaSlug?: string;
  t?: (key: string, vars?: Record<string, string | number>) => string;
}) {
  const [itemsByGuest, setItemsByGuest] = useState<Record<number, any[]>>({});

  const { theme } = useTheme();
  const { t: hookT, locale } = useMesaI18n(mesaSlug);
  const t = providedT ?? hookT;
  const mesaTextColor = theme?.tokens?.['mesa-text'] ? `text-[hsl(var(--mesa-text))]` : 'text-foreground';

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const entries: Record<number, any[]> = {};
      for (const p of pessoas || []) {
        try {
          const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/conta`, {
            params: { sessaoConvidadoId: p.sessaoConvidadoId },
          });
          entries[p.sessaoConvidadoId] = data?.itens || [];
        } catch {}
      }
      if (!cancelled) setItemsByGuest(entries);
    })();
    return () => {
      cancelled = true;
    };
  }, [sessaoMesaId, JSON.stringify(pessoas)]);

  const guests = pessoas || [];
  if (guests.length === 0) return <div className="text-sm text-foreground/70">{t('mesa.orders.table.empty')}</div>;

  return (
    <div className="space-y-4">
      {guests.map((p) => {
        const items = itemsByGuest[p.sessaoConvidadoId] || [];
        if (!items.length) return null;
        const grouped: Record<string, any[]> = items.reduce((acc: any, it: any) => {
          const k = String(it.pedidoId);
          (acc[k] ||= []).push(it);
          return acc;
        }, {});
        return (
          <div key={p.sessaoConvidadoId} className="border border-accent/20 rounded bg-white shadow-sm">
            <div className="px-3 py-2 text-sm font-medium">
              {p.nome || (p as any).name || t('mesa.orders.table.guest', { id: p.sessaoConvidadoId })}
            </div>
            <div className="divide-y">
              {Object.entries(grouped).map(([pedidoId, itens]: any) => {
                const created = new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(itens[0].pedidoCriadoEm));
                const status = itens[0].pedidoStatus;
                return (
                  <div key={pedidoId}>
                    <div className="flex items-center justify-between px-3 py-2 bg-accent/10">
                      <div className="text-xs">
                        {t('mesa.orders.orderNumber', { id: pedidoId })} • {created}
                      </div>
                      <div className={`text-xs px-2 py-0.5 rounded ${badgeClass(status)}`}>{statusLabel(status)}</div>
                    </div>
                    <div className="px-3 py-2 space-y-2">
                      {itens.map((it: any) => {
                        const isCanceled = it.status?.toLowerCase() === 'canceled';
                        return (
                          <div key={it.itemPedidoId} className={isCanceled ? 'opacity-60' : ''}>
                            <div className="flex justify-between text-sm">
                              <div className={`${mesaTextColor} ${isCanceled ? 'line-through' : ''}`}>
                                {it.produtoNome} <span className={`${mesaTextColor}/70`}>x {it.quantidade}</span>
                              </div>
                              <div className={isCanceled ? 'line-through' : ''}>R$ {((it.precoUnitCentavos * it.quantidade) / 100).toFixed(2)}</div>
                            </div>
                            {it.observacoes && (
                              <div className="text-xs text-foreground/60 italic mt-0.5 flex items-center gap-1">
                                <span>📝</span>
                                <span>{it.observacoes}</span>
                              </div>
                            )}
                            {isCanceled && (
                              <div className="text-xs text-red-600 mt-0.5 flex items-center gap-1">
                                <span>❌</span>
                                <span>{t('mesa.orders.status.canceled')}</span>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
