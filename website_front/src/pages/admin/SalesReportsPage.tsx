import { useEffect, useMemo, useState } from 'react';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { MoreHorizontal } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

import DatePicker, { registerLocale } from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css'; // Import CSS
import ptBR from 'date-fns/locale/pt-BR'; // Import locale

registerLocale('pt-BR', ptBR); // Register locale once

type VendasPeriodoDTO = {
  faturamento: number; // BigDecimal JSON -> number
  quantidadeVendas: number;
  ticketMedio: number; // BigDecimal
  pix: number; cartao: number; dinheiro: number;
  cartaoCredito: number;
  cartaoDebito: number;
  voucher: number;
  faturamentoBase?: number; // opcional: consumo
  taxaServico?: number;     // opcional: taxa recebida
};

type VendasListResponse = {
  table_data: any[];
  totalElementos: number;
  totalPaginas: number;
  paginaAtual: number;
  tamanhoPagina: number;
};

function Kpi({ label, value, subtitle }: { label: string; value: string; subtitle?: string }) {
  return (
    <Card className="p-4 bg-white backdrop-blur-sm rounded-2xl shadow-xl border border-[#D7B899]/20"> {}
      <div className="text-sm text-[#2A1F1B] font-semibold">{label}</div> {}
      {subtitle && <div className="text-[12px] text-[#2A1F1B]/70">{subtitle}</div>} {}
      <div className="text-2xl font-semibold text-[#D7B899] mt-1">{value}</div> {}
    </Card>
  );
}

// Helper to format date from YYYY-MM-DD to DD/MM/YYYY for display
const formatDateToBR = (dateString: string): string => {
  if (!dateString) return '';
  const [year, month, day] = dateString.split('-');
  if (!year || !month || !day) return '';
  return `${day}/${month}/${year}`;
};

// Helper to parse date from DD/MM/YYYY to YYYY-MM-DD for internal use
const parseDateFromBR = (dateString: string): string => {
  if (!dateString) return '';
  const parts = dateString.split('/');
  if (parts.length === 3) {
    const [day, month, year] = parts;
    // Basic validation to prevent invalid dates like 32/13/2023
    if (parseInt(day) > 0 && parseInt(day) <= 31 &&
        parseInt(month) > 0 && parseInt(month) <= 12 &&
        parseInt(year) > 1900 && parseInt(year) < 2100) { // arbitrary year range
      return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
    }
  }
  return ''; // Return empty string for invalid format or date
};

export default function SalesReportsPage() {
  const [periodo, setPeriodo] = useState<'hoje'|'7d'|'30d'|'custom'>('hoje');
  const [kpis, setKpis] = useState<VendasPeriodoDTO | null>(null);
  const [loadingKpis, setLoadingKpis] = useState(false);

  const [dateFrom, setDateFrom] = useState<Date | null>(null);
  const [dateTo, setDateTo] = useState<Date | null>(null);

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [rows, setRows] = useState<any[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loadingTable, setLoadingTable] = useState(false);

  // Estados para o modal de comprovante
  const [showComprovanteModal, setShowComprovanteModal] = useState(false);
  const [comprovantePdfUrl, setComprovantePdfUrl] = useState<string | null>(null);
  const [loadingPdf, setLoadingPdf] = useState(false);

  // Estados para o diálogo de confirmação
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [confirmData, setConfirmData] = useState({
    valorTotal: 0,
    nomeCliente: '',
    vendaId: null as number | null
  });

  const formatCurrency = (n: number) => `R$ ${Number(n || 0).toFixed(2)}`;
  const kpisBase = kpis || { faturamento: 0, quantidadeVendas: 0, ticketMedio: 0, pix: 0, cartao: 0, dinheiro: 0, cartaoCredito: 0, cartaoDebito: 0, voucher: 0, faturamentoBase: 0, taxaServico: 0 };

  // Derivar totais a partir das linhas da tabela quando KPIs não trouxerem base/taxa
  const derived = useMemo(() => {
    const base = rows.reduce((sum, r: any) => sum + (Number(r.valorBase) || Number(r.valor) || 0), 0);
    const taxa = rows.reduce((sum, r: any) => sum + (Number(r.valorTaxaServico) || 0), 0);
    return { base, taxa, total: base + taxa };
  }, [JSON.stringify(rows)]);

  const fetchKpis = async () => {
    setLoadingKpis(true);
    try {
      const params: any = { periodo };
      if (periodo === 'custom') {
        if (dateFrom) params.dateFrom = dateFrom.toISOString().split('T')[0]; // Convert Date to YYYY-MM-DD string
        if (dateTo) params.dateTo = dateTo.toISOString().split('T')[0];     // Convert Date to YYYY-MM-DD string
      }
      const res = await axios.get<VendasPeriodoDTO>(`${apiConfig.erpBaseUrl}/api/dashboard/vendas-periodo`, { params });
      setKpis(res.data);
    } catch (e) {
      setKpis(null);
    } finally {
      setLoadingKpis(false);
    }
  };

  const buildFilter = () => {
    const f: any = {};
    // período rápido aplicado também na tabela
    if (periodo !== 'custom') {
      const now = new Date();
      const end = new Date(now);
      let start = new Date(now);
      if (periodo === 'hoje') {
        start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
      } else if (periodo === '7d') {
        start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6, 0, 0, 0);
      }
      else if (periodo === '30d') {
        start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29, 0, 0, 0);
      }
      const v1 = start.toISOString().slice(0,19);
      const v2 = end.toISOString().slice(0,19);
      f['pagoEm'] = { operator: 'between', value: v1, value2: v2 };
    } else {
      // Use dateFrom and dateTo Date objects directly
      const formattedDateFrom = dateFrom ? dateFrom.toISOString().split('T')[0] : '';
      const formattedDateTo = dateTo ? dateTo.toISOString().split('T')[0] : '';

      if (formattedDateFrom && formattedDateTo) {
        const v1 = `${formattedDateFrom}T00:00:00`;
        const v2 = `${formattedDateTo}T23:59:59`;
        f['pagoEm'] = { operator: 'between', value: v1, value2: v2 };
      } else if (formattedDateFrom && !formattedDateTo) {
        const v1 = `${formattedDateFrom}T00:00:00`;
        f['pagoEm'] = { operator: 'gte', value: v1 };
      } else if (!formattedDateFrom && formattedDateTo) {
        const v2 = `${formattedDateTo}T23:59:59`;
        f['pagoEm'] = { operator: 'lte', value: v2 };
      }
    }
    return Object.keys(f).length ? JSON.stringify(f) : undefined;
  };

  const fetchTable = async () => {
    setLoadingTable(true);
    try {
      const params: any = { pagina: page, tamanho: size, ordenacao: 'pagoEm', direcao: 'desc' };
      const filter = buildFilter();
      if (filter) params.filter = filter;
      const res = await axios.get<VendasListResponse>(`${apiConfig.erpBaseUrl}/api/vendas/report-table`, { params });
      const body = res.data as any;
      setRows((body.table_data || body.tableData || []) as any[]);
      setTotalPages(body.totalPaginas || 0);
    } catch (e) {
      setRows([]);
      setTotalPages(0);
    } finally {
      setLoadingTable(false);
    }
  };

  useEffect(() => { fetchKpis(); }, [periodo, dateFrom, dateTo]);
  useEffect(() => { fetchTable(); }, [page, size, dateFrom, dateTo, periodo]);

  const onSearch = () => { setPage(0); fetchTable(); };

  const abrirConfirmacaoComprovante = (venda: any) => {
    setConfirmData({
      valorTotal: venda.valor || venda.valorBase || 0,
      nomeCliente: venda.beneficiario || '',
      vendaId: venda.id
    });
    setShowConfirmModal(true);
  };

  const gerarComprovanteConsumo = async () => {
    setLoadingPdf(true);
    try {
      const token = localStorage.getItem('auth_token'); // ou onde quer que você armazene o token

      const response = await axios.post(
        `${apiConfig.erpBaseUrl}/api/admin/consumo/comprovante`,
        {
          valorTotal: confirmData.valorTotal,
          nomeCliente: confirmData.nomeCliente || null
        },
        {
          headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
            'Accept': 'application/pdf'
          },
          responseType: 'blob' // importante para lidar com PDF
        }
      );

      // Converter resposta para blob
      const blob = response.data; // Removi o await porque response.data já é o blob

      // Criar URL para o blob
      const url = window.URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));

      // Definir a URL do PDF e mostrar o modal
      setComprovantePdfUrl(url);
      setShowComprovanteModal(true);
      setShowConfirmModal(false); // Fechar o diálogo de confirmação

    } catch (error) {
      console.error('Erro ao gerar comprovante de consumo:', error);
      // Aqui você pode adicionar uma notificação de erro se tiver um sistema de toast
    } finally {
      setLoadingPdf(false);
    }
  };

  return (
    <>
      {/* Modal para exibir o comprovante de consumo */}
      <Dialog
        open={showComprovanteModal}
        onOpenChange={(open) => {
          if (!open) {
            setShowComprovanteModal(false);
            if (comprovantePdfUrl) {
              window.URL.revokeObjectURL(comprovantePdfUrl);
              setComprovantePdfUrl(null);
            }
          }
        }}
      >
        <DialogContent className="bg-white/95 backdrop-blur-md border-[#D7B899]/20 max-w-[95vw] md:max-w-2xl z-[9999]"> {}
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Comprovante de Consumo</DialogTitle> {}
          </DialogHeader>

          <div className="h-[500px] w-full border border-[#D7B899]/30 rounded bg-gray-100 overflow-hidden"> {}
            {comprovantePdfUrl ? (
              <iframe
                src={comprovantePdfUrl}
                className="w-full h-full"
                title="Comprovante de Consumo PDF"
              />
            ) : (
              <div className="flex items-center justify-center h-full text-[#2A1F1B]/50"> {}
                {loadingPdf ? 'Carregando PDF...' : 'Nenhum PDF disponível'}
              </div>
            )}
          </div>

          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={() => {
                if (comprovantePdfUrl) {
                  const iframe = document.querySelector('iframe[title="Comprovante de Consumo PDF"]') as HTMLIFrameElement;
                  if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.print();
                  }
                }
              }}
              disabled={!comprovantePdfUrl}
              className="bg-white border-[#D7B899]/50 text-[#2A1F1B] hover:bg-[#D7B899]/10"
            >
              Imprimir
            </Button>
            <Button
              onClick={() => {
                setShowComprovanteModal(false);
                if (comprovantePdfUrl) {
                  window.URL.revokeObjectURL(comprovantePdfUrl);
                  setComprovantePdfUrl(null);
                }
              }}
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90" 
            >
              Fechar
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Diálogo de confirmação para geração do comprovante */}
      <Dialog
        open={showConfirmModal}
        onOpenChange={(open) => {
          if (!open) {
            setShowConfirmModal(false);
          }
        }}
      >
        <DialogContent className="bg-white/95 backdrop-blur-md border-[#D7B899]/20 max-w-md"> {}
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Confirma geração do comprovante</DialogTitle> {}
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-[#2A1F1B] mb-1">Valor Total</label> {}
              <Input
                type="number"
                step="0.01"
                value={confirmData.valorTotal}
                onChange={(e) => setConfirmData({...confirmData, valorTotal: parseFloat(e.target.value) || 0})}
                className="bg-white border-[#D7B899]/30 text-[#2A1F1B]" 
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-[#2A1F1B] mb-1">Nome do Cliente</label> {}
              <Input
                type="text"
                value={confirmData.nomeCliente}
                onChange={(e) => setConfirmData({...confirmData, nomeCliente: e.target.value})}
                className="bg-white border-[#D7B899]/30 text-[#2A1F1B]" 
                placeholder="Nome do cliente"
              />
            </div>
          </div>

          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={() => setShowConfirmModal(false)}
              className="bg-white border-[#D7B899]/50 text-[#2A1F1B] hover:bg-[#D7B899]/10" 
            >
              Cancelar
            </Button>
            <Button
              onClick={gerarComprovanteConsumo}
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90" 
            >
              Confirmar
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <div className="space-y-6 text-[#2A1F1B]"> {}
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-display tracking-wider text-[#2A1F1B] uppercase">Relatório de Vendas</h1>
          <p className="text-sm text-[#2A1F1B]/70">Baseado em pagamentos confirmados</p>
        </div>

      {/* Filtros rápidos e período */}
      <div className="bg-white backdrop-blur-sm rounded-2xl border border-[#D7B899]/20 p-4 flex flex-col gap-3 relative"> {}
        <div className="flex flex-wrap items-center gap-2">
          {[
            { value: 'hoje', label: 'Hoje' },
            { value: '7d', label: 'Últimos 7 dias' },
            { value: '30d', label: 'Últimos 30 dias' },
            { value: 'custom', label: 'Personalizado' },
          ].map((opt) => {
            const active = periodo === opt.value;
            return (
              <Button
                key={opt.value}
                size="sm"
                variant="outline"
                className={active
                  ? 'bg-[#D7B899] text-[#2A1F1B] border-[#D7B899]' 
                  : 'bg-white text-[#2A1F1B] border-[#D7B899]/40 hover:bg-[#D7B899]/10'} 
                onClick={() => { setPeriodo(opt.value as any); setPage(0); }}
              >
                {opt.label}
              </Button>
            );
          })}
          <Button
            onClick={fetchKpis}
            disabled={loadingKpis}
            size="sm"
            variant="outline"
            className="ml-auto border-[#D7B899]/30 text-[#2A1F1B] bg-white hover:bg-[#D7B899]/10 disabled:opacity-60"
          > {}
            Atualizar KPIs
          </Button>
        </div>
        {periodo === 'custom' && (
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col">
              <label className="text-xs text-[#2A1F1B]/70">De</label> {}
              <DatePicker
                locale="pt-BR"
                dateFormat="dd/MM/yyyy"
                selected={dateFrom}
                onChange={(date: Date | null) => setDateFrom(date)}
                selectsStart
                startDate={dateFrom}
                endDate={dateTo}
                popperClassName="z-50"
                portalId="root"
                className="w-48 bg-white border border-[#D7B899]/30 text-[#2A1F1B] p-2 rounded" 
                wrapperClassName="date-picker-wrapper"
              />
            </div>
            <div className="flex flex-col">
              <label className="text-xs text-[#2A1F1B]/70">Até</label> {}
              <DatePicker
                locale="pt-BR"
                dateFormat="dd/MM/yyyy"
                selected={dateTo}
                onChange={(date: Date | null) => setDateTo(date)}
                selectsEnd
                startDate={dateFrom}
                endDate={dateTo}
                popperClassName="z-50"
                portalId="root"
                className="w-48 bg-white border border-[#D7B899]/30 text-[#2A1F1B] p-2 rounded" 
                wrapperClassName="date-picker-wrapper"
              />
            </div>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-3">
        <Kpi label="Faturamento (Base+Taxa)" value={formatCurrency(kpisBase.faturamento || derived.total)} />
        <Kpi label="Faturamento Base" value={formatCurrency(kpisBase.faturamentoBase || derived.base)} subtitle="Consumo (sem taxa)" />
        <Kpi label="Taxa de Serviço" value={formatCurrency(kpisBase.taxaServico || derived.taxa)} />
        <Kpi label="Qtd. Vendas" value={`${kpisBase.quantidadeVendas || rows.length || 0}`} />
        <Kpi label="Ticket Médio" value={formatCurrency(kpisBase.ticketMedio || (rows.length ? (derived.total / rows.length) : 0))} />

        <Kpi label="PIX" value={formatCurrency(kpisBase.pix || 0)} />
        <Kpi label="Cartão Crédito" value={formatCurrency(kpisBase.cartaoCredito || 0)} />
        <Kpi label="Cartão Débito" value={formatCurrency(kpisBase.cartaoDebito || 0)} />
        <Kpi label="Voucher" value={formatCurrency(kpisBase.voucher || 0)} />
        <Kpi label="Dinheiro" value={formatCurrency(kpisBase.dinheiro || 0)} />
      </div>
      {/* Tabela de vendas */}
      <div className="bg-white backdrop-blur-sm rounded-2xl shadow-xl border border-[#D7B899]/20 overflow-hidden"> {}
        <Table>
          <TableHeader>
            <TableRow className="border-b border-[#D7B899]/20 bg-[#D7B899]/5"> {}
              <TableHead className="w-[80px] text-[#2A1F1B] font-medium">Código</TableHead> {}
              <TableHead className="text-[#2A1F1B] font-medium">Pago em</TableHead> {}
              <TableHead className="text-[#2A1F1B] font-medium">Mesa</TableHead> {}
              <TableHead className="text-[#2A1F1B] font-medium">Beneficiário</TableHead> {}
              <TableHead className="text-[#2A1F1B] font-medium">Pagamento</TableHead> {}
              <TableHead className="text-right text-[#2A1F1B] font-medium">Base</TableHead> {}
              <TableHead className="text-right text-[#2A1F1B] font-medium">Taxa</TableHead> {}
              <TableHead className="text-right text-[#2A1F1B] font-medium">Valor</TableHead> {}
              <TableHead className="w-[40px] text-[#2A1F1B] font-medium text-center">Ações</TableHead> {}
            </TableRow>
          </TableHeader>
          <TableBody>
            {loadingTable ? (
              <TableRow>
                <TableCell colSpan={8} className="text-center py-8 text-[#2A1F1B]/70">Carregando vendas...</TableCell> {}
              </TableRow>
            ) : rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} className="text-center py-8 text-[#2A1F1B]/70">Nenhuma venda encontrada</TableCell> {}
              </TableRow>
            ) : (
              rows.map((r:any) => (
                <TableRow key={r.id} className="hover:bg-white transition-colors border-b border-[#D7B899]/15"> {}
                  <TableCell className="font-medium text-[#2A1F1B]">#{r.id}</TableCell> {}
                  <TableCell className="text-[#2A1F1B]">{r.pagoEm ? new Date(r.pagoEm).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' ' + new Date(r.pagoEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : '-'}</TableCell> {}
                  <TableCell className="text-[#2A1F1B]">{r.mesaRotulo || r.mesaSlug || '-'}</TableCell> {}
                  <TableCell className="text-[#2A1F1B]">{r.beneficiario || '-'}</TableCell> {}
                  <TableCell className="text-[#2A1F1B] uppercase">{r.metodo || '-'}</TableCell> {}
                  <TableCell className="text-right text-[#2A1F1B]">
                    {formatCurrency((r.valorBase || r.valor || 0))}
                  </TableCell>
                  <TableCell className="text-right text-[#2A1F1B]">
                    {formatCurrency((r.valorTaxaServico || 0))}
                  </TableCell>
                  <TableCell className="text-right text-[#D7B899] font-semibold">{formatCurrency((r.valor || 0))}</TableCell> {}
                  <TableCell className="text-[#2A1F1B] text-center"> {}
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" className="h-8 w-8 p-0">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" className="bg-white border-[#D7B899]/30"> {}
                        <DropdownMenuItem onClick={() => abrirConfirmacaoComprovante(r)}>
                          Emissão de Comprovante
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        <div className="flex items-center justify-between px-6 py-4 border-t border-[#D7B899]/20 bg-white text-sm text-[#2A1F1B]"> {}
          <div className="text-[#2A1F1B]/70">Página {page + 1} de {Math.max(1, totalPages)}</div> {}
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" disabled={page<=0} onClick={()=>setPage(0)} className="text-[#2A1F1B] bg-white hover:bg-[#D7B899]/10 disabled:opacity-50"> {}
              «
            </Button>
            <Button variant="ghost" size="sm" disabled={page<=0} onClick={()=>setPage(p=>Math.max(0,p-1))} className="text-[#2A1F1B] bg-white hover:bg-[#D7B899]/10 disabled:opacity-50"> {}
              ‹
            </Button>
            <span className="px-3 py-1 text-sm font-medium text-[#2A1F1B]">{page + 1}</span> {}
            <Button variant="ghost" size="sm" disabled={page+1>=totalPages} onClick={()=>setPage(p=>p+1)} className="text-[#2A1F1B] bg-white hover:bg-[#D7B899]/10 disabled:opacity-50"> {}
              ›
            </Button>
            <Button variant="ghost" size="sm" disabled={page+1>=totalPages} onClick={()=>setPage(totalPages-1)} className="text-[#2A1F1B] bg-white hover:bg-[#D7B899]/10 disabled:opacity-50"> {}
              »
            </Button>
          </div>
        </div>
      </div>
    </div>
  </>
);
}
