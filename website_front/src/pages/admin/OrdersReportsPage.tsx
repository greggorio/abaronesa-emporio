import { useEffect, useState } from 'react';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';

type KPIs = {
  pedidosNoBar: number;
  aguardandoPreparoBar: number;
  pedidosNaCozinha: number;
  emPreparoCozinha: number;
  pedidosEmEspera: number;
  aguardandoEntrega: number;
};

type KdsTicket = {
  itemPedidoId: number;
  pedidoId: number;
  estacao: 'kitchen'|'bar';
  status: string;
  atualizadoEm: string;
  item: { nome: string; quantidade: number; observacoes?: string|null; necessitaPreparacao?: boolean; };
  mesa: { slug: string; rotulo: string };
  pedido: { criadoEm: string };
};

export default function OrdersReportsPage() {
  const [kpis, setKpis] = useState<KPIs | null>(null);
  const [tickets, setTickets] = useState<KdsTicket[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [a, b] = await Promise.all([
        axios.get<KPIs>(`${apiConfig.erpBaseUrl}/api/dashboard/pedidos-preparacao`),
        axios.get<{ tickets: KdsTicket[] }>(`${apiConfig.erpBaseUrl}/api/kds/queue`),
      ]);
      setKpis(a.data);
      setTickets((b.data?.tickets || []).sort((x,y) => new Date(x.pedido.criadoEm).getTime() - new Date(y.pedido.criadoEm).getTime()));
    } catch (e) {
      setKpis(null);
      setTickets([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchAll(); }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-display tracking-wider text-[#2A1F1B]">Relatório Operacional de Pedidos</h1>
          <p className="text-sm text-[#8B7355]/70">Visão consolidada de produção (bar/cozinha) e itens na fila</p>
        </div>
        <Button variant="outline" onClick={fetchAll} disabled={loading} className="border-[#D7B899]/30 text-[#2A1F1B] bg-white hover:bg-[#D7B899]/10">
          Atualizar
        </Button>
      </div>

      {/* KPIs (padrão cards em branco) */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <Card className="p-4 bg-white rounded-2xl shadow-xl border border-[#D7B899]/20">
          <div className="text-sm text-[#2A1F1B] font-semibold">Bar</div>
          <div className="text-[12px] text-[#8B7355]/70">Aguardando • Em fila</div>
          <div className="text-2xl font-semibold text-[#2A1F1B] mt-1">{kpis?.aguardandoPreparoBar || 0} • {kpis?.pedidosNoBar || 0}</div>
        </Card>
        <Card className="p-4 bg-white rounded-2xl shadow-xl border border-[#D7B899]/20">
          <div className="text-sm text-[#2A1F1B] font-semibold">Cozinha</div>
          <div className="text-[12px] text-[#8B7355]/70">Aguardando • Em fila</div>
          <div className="text-2xl font-semibold text-[#2A1F1B] mt-1">{kpis?.emPreparoCozinha || 0} • {kpis?.pedidosNaCozinha || 0}</div>
        </Card>
        <Card className="p-4 bg-white rounded-2xl shadow-xl border border-[#D7B899]/20">
          <div className="text-sm text-[#2A1F1B] font-semibold">Prontos</div>
          <div className="text-[12px] text-[#8B7355]/70">Aguardando entrega</div>
          <div className="text-2xl font-semibold text-[#2A1F1B] mt-1">{kpis?.aguardandoEntrega || 0}</div>
        </Card>
      </div>

      {/* Tabela de itens ativos (padrão /eventos) */}
      <div className="bg-white rounded-2xl shadow-xl border border-[#D7B899]/20 overflow-hidden">
        <div className="flex items-center justify-between px-6 py-3 border-b border-[#D7B899]/20 bg-[#D7B899]/5">
          <div className="text-sm font-medium text-[#2A1F1B]">Itens Ativos na Fila</div>
          <div className="text-xs text-[#8B7355]/70">{tickets.length} itens</div>
        </div>
        <Table>
          <TableHeader>
            <TableRow className="border-b border-[#D7B899]/20 bg-[#D7B899]/5">
              <TableHead className="text-[#2A1F1B] font-medium">Pedido</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Mesa</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Estação</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Status</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Item</TableHead>
              <TableHead className="text-right text-[#2A1F1B] font-medium">Qtd</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Criado em</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={7} className="text-center py-8 text-[#8B7355]/70">Carregando...</TableCell>
              </TableRow>
            ) : tickets.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="text-center py-8 text-[#8B7355]/70">Nenhum item na fila</TableCell>
              </TableRow>
            ) : (
              tickets.map((t) => (
                <TableRow key={t.itemPedidoId} className="hover:bg-[#D7B899]/5 transition-colors border-b border-[#D7B899]/10">
                  <TableCell className="text-[#2A1F1B] font-medium">#{t.pedidoId}</TableCell>
                  <TableCell className="text-[#2A1F1B]">{t.mesa.rotulo || t.mesa.slug}</TableCell>
                  <TableCell className="text-[#2A1F1B]">{t.estacao === 'bar' ? 'Bar' : 'Cozinha'}</TableCell>
                  <TableCell className="text-[#2A1F1B] capitalize">{t.status}</TableCell>
                  <TableCell className="text-[#2A1F1B]">{t.item.nome}</TableCell>
                  <TableCell className="text-right text-[#2A1F1B]">{t.item.quantidade}</TableCell>
                  <TableCell className="text-[#2A1F1B]">{t.pedido.criadoEm ? new Date(t.pedido.criadoEm).toLocaleString('pt-BR') : '-'}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
