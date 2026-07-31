import { useEffect, useMemo, useState } from 'react';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import { Button } from '@/components/ui/button';
import { Calendar } from 'lucide-react';
import DatePicker, { registerLocale } from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import ptBR from 'date-fns/locale/pt-BR';
import { format } from 'date-fns';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';

registerLocale('pt-BR', ptBR);

type CancelamentoItem = {
  itemPedidoId: number;
  pedidoId: number;
  produtoNome: string;
  quantidade: number;
  precoUnitario: number;
  valorTotal: number;
  mesaSlug?: string;
  mesaRotulo?: string;
  criadoEm?: string;
  motivoCodigo?: string;
  motivoDetalhe?: string;
};

const formatLocalDate = (d: Date) => format(d, 'yyyy-MM-dd');

export default function CancelamentosPage() {
  const [periodo, setPeriodo] = useState<'hoje' | '7d' | '30d' | 'custom'>('hoje');
  const [inicio, setInicio] = useState<Date | null>(new Date());
  const [fim, setFim] = useState<Date | null>(new Date());
  const [items, setItems] = useState<CancelamentoItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [totais, setTotais] = useState({ totalItens: 0, valorTotal: 0 });

  const load = async () => {
    setLoading(true);
    try {
      let start = inicio;
      let end = fim;
      const now = new Date();
      if (periodo === 'hoje') {
        start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        end = new Date(start);
      } else if (periodo === '7d') {
        end = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        start = new Date(end);
        start.setDate(start.getDate() - 6);
      } else if (periodo === '30d') {
        end = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        start = new Date(end);
        start.setDate(start.getDate() - 29);
      }

      const res = await axios.get(`${apiConfig.erpBaseUrl}/api/admin/cancelamentos`, {
        params: {
          inicio: start ? formatLocalDate(start) : undefined,
          fim: end ? formatLocalDate(end) : undefined,
        },
      });
      setItems(res.data?.itens || []);
      setTotais({
        totalItens: res.data?.totalItens || 0,
        valorTotal: res.data?.valorTotal || 0,
      });
    } catch (e) {
      console.error('Erro ao carregar cancelamentos', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [periodo]);

  const motivosLabel = useMemo(
    () => ({
      FALTA_INSUMO: 'Falta de insumo',
      EQUIPE_INDISPONIVEL: 'Equipe indisponível',
      ERRO_PEDIDO: 'Erro de pedido',
      CLIENTE_DESISTIU: 'Cliente desistiu',
      OUTRO: 'Outro',
    }),
    []
  );

  return (
    <div className="text-forest-dark space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-display tracking-wider text-coral-accent">Cancelamentos</h1>
          <p className="text-sm text-forest-dark/70">Itens cancelados no período selecionado.</p>
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <ToggleGroup type="single" value={periodo} onValueChange={(v) => v && setPeriodo(v as any)} className="bg-white border border-forest-green/20 rounded-md p-1 shadow-sm">
            <ToggleGroupItem value="hoje" className="px-3 py-1 text-sm data-[state=on]:bg-forest-green data-[state=on]:text-white rounded">Hoje</ToggleGroupItem>
            <ToggleGroupItem value="7d" className="px-3 py-1 text-sm data-[state=on]:bg-forest-green data-[state=on]:text-white rounded">Últimos 7 dias</ToggleGroupItem>
            <ToggleGroupItem value="30d" className="px-3 py-1 text-sm data-[state=on]:bg-forest-green data-[state=on]:text-white rounded">Últimos 30 dias</ToggleGroupItem>
            <ToggleGroupItem value="custom" className="px-3 py-1 text-sm data-[state=on]:bg-forest-green data-[state=on]:text-white rounded">Personalizado</ToggleGroupItem>
          </ToggleGroup>
          {periodo === 'custom' && (
            <div className="flex items-center gap-2">
              <DatePicker
                selected={inicio}
                onChange={(d) => setInicio(d)}
                dateFormat="dd/MM/yyyy"
                locale="pt-BR"
                className="bg-white border border-forest-green/30 rounded px-2 py-1 text-sm text-forest-dark shadow-sm"
              />
              <span className="text-sm">até</span>
              <DatePicker
                selected={fim}
                onChange={(d) => setFim(d)}
                dateFormat="dd/MM/yyyy"
                locale="pt-BR"
                className="bg-white border border-forest-green/30 rounded px-2 py-1 text-sm text-forest-dark shadow-sm"
              />
            </div>
          )}
          <Button onClick={load} disabled={loading} className="flex items-center gap-2 bg-coral-accent text-forest-dark hover:bg-coral-accent/90">
            <Calendar size={16} />
            Filtrar
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="p-4 border border-forest-green/20 rounded bg-white shadow-sm">
          <div className="text-sm text-forest-dark/70">Itens cancelados</div>
          <div className="text-2xl font-bold text-coral-accent">{totais.totalItens}</div>
        </div>
        <div className="p-4 border border-forest-green/20 rounded bg-white shadow-sm">
          <div className="text-sm text-forest-dark/70">Valor cancelado</div>
          <div className="text-2xl font-bold text-coral-accent">R$ {totais.valorTotal.toFixed(2)}</div>
        </div>
      </div>

      <div className="overflow-auto border border-forest-green/20 rounded bg-white shadow-sm">
        <table className="w-full text-sm text-forest-dark">
          <thead className="bg-forest-green/5">
            <tr className="text-left">
              <th className="px-3 py-2 font-semibold">Hora</th>
              <th className="px-3 py-2 font-semibold">Produto</th>
              <th className="px-3 py-2 font-semibold">Qtd</th>
              <th className="px-3 py-2 font-semibold">Valor</th>
              <th className="px-3 py-2 font-semibold">Mesa</th>
              <th className="px-3 py-2 font-semibold">Motivo</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 && (
              <tr>
                <td colSpan={6} className="px-3 py-4 text-center text-forest-dark/60">
                  {loading ? 'Carregando...' : 'Nenhum cancelamento no período.'}
                </td>
              </tr>
            )}
            {items.map((it) => (
              <tr key={it.itemPedidoId} className="border-t border-forest-green/10 hover:bg-forest-green/5">
                <td className="px-3 py-2 text-forest-dark/70">
                  {it.criadoEm ? new Date(it.criadoEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : '-'}
                </td>
                <td className="px-3 py-2 text-forest-dark">{it.produtoNome}</td>
                <td className="px-3 py-2 text-forest-dark">{it.quantidade}</td>
                <td className="px-3 py-2 text-forest-dark">
                  R$ {(it.valorTotal || 0).toFixed(2)}
                </td>
                <td className="px-3 py-2 text-forest-dark/70">
                  {it.mesaRotulo || it.mesaSlug || '-'}
                </td>
                <td className="px-3 py-2">
                  <div className="text-forest-dark font-medium">{motivosLabel[it.motivoCodigo as keyof typeof motivosLabel] || '—'}</div>
                  {it.motivoDetalhe && <div className="text-xs text-forest-dark/70">{it.motivoDetalhe}</div>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
