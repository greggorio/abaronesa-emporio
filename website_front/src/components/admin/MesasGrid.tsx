import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from '@/components/ui/textarea';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Plus, List, CreditCard, Move, XCircle, RefreshCw } from "lucide-react";
import { FastSaleDrawer } from './FastSaleDrawer';
import { resolvePrice, resolvePromoPrice } from '@/utils/cardapio';

type SessaoMesaResumo = {
  sessaoMesaId: number;
  mesaSlug: string;
  abertaEm: string;
  pessoas: number;
  totalMesaCentavos: number;
  pagoCentavos: number;
  devidoCentavos: number;
  assistida?: boolean;
  hostNome?: string;
};

type ItemMesa = {
  itemPedidoId: number;
  pedidoId: number;
  produtoNome: string;
  quantidade: number;
  precoUnitario: number;
  valorTotal: number;
  status?: string;
};

type AdminProductOption = {
  key: string;
  produtoId: number;
  skuId?: number | null;
  nome: string;
  variacao?: string | null;
  categoria?: string | null;
  preco: number;
  precoPromocional?: number | null;
  disponivel: boolean;
};

type SessaoConvidadoResumo = {
  sessaoConvidadoId: number;
  nome: string;
  host?: boolean;
};

const statusLabels: Record<string, string> = {
  QUEUED: 'Na fila',
  ACCEPTED: 'Aceito',
  PREPARING: 'Preparando',
  READY: 'Pronto',
  DELIVERED: 'Entregue',
  CANCELED: 'Cancelado',
};

interface MesasGridProps {
  isWaiterMode?: boolean;
}

export function MesasGrid({ isWaiterMode = false }: MesasGridProps) {
  const [rows, setRows] = useState<SessaoMesaResumo[]>([]);
  const [loading, setLoading] = useState(false);
  const [showFecharDialog, setShowFecharDialog] = useState(false);
  const [selectedMesaId, setSelectedMesaId] = useState<number | null>(null);
  const [selectedMesaSlug, setSelectedMesaSlug] = useState<string | null>(null);

  // Busca o ID da sessão ativa da mesa BALCAO
  const balcaoSessaoId = useMemo(() => {
    const row = rows.find(row => row.mesaSlug.toUpperCase() === 'BALCAO');
    return row ? row.sessaoMesaId : null;
  }, [rows]);

  const [notificationMessage, setNotificationMessage] = useState<string>('');
  const [showNotification, setShowNotification] = useState(false);
  const [itemsModalOpen, setItemsModalOpen] = useState(false);
  const [itemsLoading, setItemsLoading] = useState(false);
  const [items, setItems] = useState<ItemMesa[]>([]);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelItemId, setCancelItemId] = useState<number | null>(null);
  const [cancelMotivo, setCancelMotivo] = useState<string>('OUTRO');
  const [cancelDetalhe, setCancelDetalhe] = useState('');
  

  const [fastSaleConfig, setFastSaleConfig] = useState<{open: boolean; mesaSlug?: string; sessaoId?: number | null}>({
    open: false,
    mesaSlug: 'BALCAO',
    sessaoId: null
  });
  const [assistDialogOpen, setAssistDialogOpen] = useState(false);

  const [assistMesaSlug, setAssistMesaSlug] = useState('');
  const [assistNome, setAssistNome] = useState('');
  const [assistClienteId, setAssistClienteId] = useState<number | null>(null);
  const [assistSaving, setAssistSaving] = useState(false);
  const [moveDialogOpen, setMoveDialogOpen] = useState(false);
  const [moveSessaoId, setMoveSessaoId] = useState<number | null>(null);
  const [moveMesaSlug, setMoveMesaSlug] = useState('');
  const [moveCurrentMesaSlug, setMoveCurrentMesaSlug] = useState<string | null>(null);
  const [moveSaving, setMoveSaving] = useState(false);
  const [clienteOptions, setClienteOptions] = useState<{ id: number; nome: string }[]>([]);
  const [clientesLoading, setClientesLoading] = useState(false);
  const [mesaOptions, setMesaOptions] = useState<{ slug: string; rotulo: string }[]>([]);
  const [mesasLoading, setMesasLoading] = useState(false);

  const showNotif = (message: string) => {
    setNotificationMessage(message);
    setShowNotification(true);
  };

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes`, {
        params: { status: 'open' },
      });
      setRows(data.sessoes || []);
    } finally { setLoading(false); }
  };
  useEffect(() => { load(); }, []);

  const fechar = async () => {
    if (selectedMesaId === null) return;
    try {
      await axios.post(`${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${selectedMesaId}/fechar`);
      setRows(prev => prev.filter(r => r.sessaoMesaId !== selectedMesaId));
      setShowFecharDialog(false);
      setSelectedMesaId(null);
      showNotif('Mesa fechada com sucesso!');
    } catch (err: any) {
      const msg = err?.response?.data?.error?.message || err?.message || 'Falha ao fechar mesa';
      showNotif(msg);
    }
  };

  const openItems = async (sessaoMesaId: number, mesaSlug: string) => {
    setItemsModalOpen(true);
    setSelectedMesaId(sessaoMesaId);
    setSelectedMesaSlug(mesaSlug);
    await fetchItems(sessaoMesaId);
  };

  const fetchItems = async (sessaoMesaId: number) => {
    setItemsLoading(true);
    try {
      const res = await axios.get(`${apiConfig.erpBaseUrl}/api/admin/itens/sessoes/${sessaoMesaId}`);
      setItems((res.data?.itens || []).map((it: any) => ({
        ...it,
        status: (it.status || 'QUEUED')?.toString().toUpperCase(),
      })));
    } catch (e: any) {
      showNotif(e?.response?.data?.error?.message || 'Erro ao carregar itens da mesa');
    } finally {
      setItemsLoading(false);
    }
  };

  const askCancel = (itemPedidoId: number) => {
    setCancelItemId(itemPedidoId);
    setCancelMotivo('OUTRO');
    setCancelDetalhe('');
    setCancelDialogOpen(true);
  };

  const confirmCancel = async () => {
    if (!cancelItemId || !selectedMesaId) return;
    try {
      await axios.post(`${apiConfig.erpBaseUrl}/api/admin/itens/${cancelItemId}/cancelar`, {
        motivoCodigo: cancelMotivo,
        motivoDetalhe: cancelDetalhe || undefined,
      });
      showNotif('Item cancelado com sucesso');
      // Atualizar a lista de itens com um refetch para garantir consistência com o backend
      await fetchItems(selectedMesaId);
      await load(); // atualizar valores devidos no card da mesa
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message || e?.message || 'Erro ao cancelar item';
      showNotif(msg);
    } finally {
      setCancelDialogOpen(false);
      setCancelItemId(null);
    }
  };



  const openAddItemDialog = () => {
    if (!selectedMesaId) return;
    setFastSaleConfig({
        open: true,
        mesaSlug: selectedMesaSlug || 'Mesa',
        sessaoId: selectedMesaId
    });
  };

  const openFastSaleDialog = () => {
    setFastSaleConfig({ open: true, mesaSlug: 'BALCAO', sessaoId: null });
  };

  const openMoveDialog = async (sessaoMesaId: number, currentMesaSlug: string) => {
    setMoveSessaoId(sessaoMesaId);
    setMoveCurrentMesaSlug(currentMesaSlug);
    const list = await loadMesaOptions();
    const defaultSlug = list.find((m) => m.slug !== currentMesaSlug)?.slug || list[0]?.slug || '';
    setMoveMesaSlug(defaultSlug);
    setMoveDialogOpen(true);
  };

  const moveMesa = async () => {
    if (!moveSessaoId) {
      showNotif('Sessão não selecionada.');
      return;
    }
    if (!moveMesaSlug) {
      showNotif('Selecione a mesa de destino.');
      return;
    }
    setMoveSaving(true);
    try {
      await axios.post(`${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${moveSessaoId}/mover`, {
        mesaDestinoSlug: moveMesaSlug,
      });
      showNotif('Mesa movida com sucesso');
      setMoveDialogOpen(false);
      setMoveSessaoId(null);
      setMoveMesaSlug('');
      await load();
      if (selectedMesaId === moveSessaoId) {
        setSelectedMesaSlug(moveMesaSlug);
      }
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message || e?.message || 'Erro ao mover mesa';
      showNotif(msg);
    } finally {
      setMoveSaving(false);
    }
  };

  const openAssistDialog = async () => {
    const list = await loadMesaOptions();
    if (list.length > 0) setAssistMesaSlug(list[0].slug);
    setAssistNome('');
    setAssistClienteId(null);
    await loadClienteOptions();
    setAssistDialogOpen(true);
  };

  const loadClienteOptions = async () => {
    setClientesLoading(true);
    try {
      const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/clientes/options`);
      const opts = data?.data || [];
      setClienteOptions(opts.map((c: any) => ({
        id: c.id ?? c.value,
        nome: c.nome ?? c.label ?? '',
      })).filter((c: any) => c.id && c.nome));
    } catch (e: any) {
      showNotif(e?.response?.data?.error?.message || 'Erro ao carregar clientes');
      setClienteOptions([]);
    } finally {
      setClientesLoading(false);
    }
  };



  const loadMesaOptions = async (): Promise<{ slug: string; rotulo: string }[]> => {
    setMesasLoading(true);
    try {
      const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/admin/mesas/options`);
      const list = (data?.data || []).filter((m: any) => (m.slug || '').toUpperCase() !== 'BALCAO');
      const mapped = list.map((m: any) => ({ slug: m.slug, rotulo: m.rotulo || m.slug }));
      setMesaOptions(mapped);
      return mapped;
    } catch (e: any) {
      showNotif(e?.response?.data?.error?.message || 'Erro ao carregar mesas');
      setMesaOptions([]);
      return [];
    } finally {
      setMesasLoading(false);
    }
  };





  const startAssistedSession = async () => {
    const slug = assistMesaSlug.trim();
    if (!slug) {
      showNotif('Selecione uma mesa.');
      return;
    }
    if (!assistNome.trim() && !assistClienteId) {
      showNotif('Informe o nome do cliente ou selecione um cadastrado.');
      return;
    }
    setAssistSaving(true);
    try {
      const { data } = await axios.post(`${apiConfig.erpBaseUrl}/api/admin/mesas/${slug}/assistida`, {
        clienteId: assistClienteId,
        nome: assistClienteId ? undefined : (assistNome.trim() || null),
      });
      setAssistDialogOpen(false);
      await load();
      const mesaSlug = data?.mesaSlug || slug;
      const sessaoMesaId = data?.sessaoMesaId;
      if (sessaoMesaId && mesaSlug) {
        setFastSaleConfig({ open: true, mesaSlug, sessaoId: sessaoMesaId });
      }
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message || e?.message || 'Erro ao iniciar pedido assistido';
      showNotif(msg);
    } finally {
      setAssistSaving(false);
    }
  };




  return (
    <div className={isWaiterMode ? "pb-20 bg-[#FBF6F2]" : "p-6 text-[#2A1F1B]"}> 
      {/* Header */}
      {!isWaiterMode && (
        <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
          <div>
            <h1 className="text-3xl font-display tracking-wider text-[#2A1F1B] uppercase">Mesas Abertas</h1> 
            <p className="text-sm text-[#2A1F1B]/70 mt-1"> 
              {rows.length} {rows.length === 1 ? 'mesa ativa' : 'mesas ativas'}
            </p>
          </div>
          <div className="flex flex-col sm:flex-row items-stretch sm:items-end gap-2 w-full sm:w-auto">
            <div className="flex items-center gap-2 flex-wrap justify-end w-full">
              <button
                onClick={load}
                className="px-3 sm:px-4 py-2 rounded-lg border border-[#8B7355]/30 hover:bg-[#8B7355]/10 text-[#2A1F1B] transition-colors flex items-center gap-2 bg-[#FBF6F2] shadow-sm text-xs sm:text-sm flex-1 sm:flex-none justify-center"
              > {/* cafe-com-leite and cafe-dark-roast */}
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                <span className="hidden sm:inline">Atualizar</span>
              </button>
              <Button
                className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 text-xs sm:text-sm flex-1 sm:flex-none"
                onClick={openAssistDialog}
              >
                Pedido assistido
              </Button>
              <Button
                className="bg-[#B5854C] text-white hover:bg-[#B5854C]/90 text-xs sm:text-sm flex-1 sm:flex-none"
                onClick={openFastSaleDialog}
              > {/* cafe-bronze */}
                Venda rápida
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Waiter Mode Header Actions */}
      {isWaiterMode && (
        <div className="flex gap-2 p-4 overflow-x-auto pb-4">
          <Button
            size="sm"
            className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 whitespace-nowrap"
            onClick={openAssistDialog}
          > 
            + Pedido Assistido
          </Button>
          <Button
            size="sm"
            className="bg-[#B5854C] text-white hover:bg-[#B5854C]/90 whitespace-nowrap"
            onClick={openFastSaleDialog}
          > {/* cafe-bronze */}
            + Venda Rápida
          </Button>
          <Button
             size="sm"
             variant="outline"
             className="border-[#8B7355]/30 text-[#2A1F1B]"
             onClick={load}
          > {/* cafe-com-leite and cafe-dark-roast */}
            Atualizar
          </Button>
        </div>
      )}

      {loading && (
        <div className="flex items-center justify-center py-8">
          <div className="text-[#8B7355]/70">Carregando…</div> 
        </div>
      )}

      {/* Grid de Mesas */}
      <div className={`grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 ${isWaiterMode ? 'px-4' : ''}`}>
        {rows.map(r => (
          <div
            key={r.sessaoMesaId}
            className="rounded-xl p-4 shadow-lg hover:shadow-xl transition-all border border-[#8B7355]/20 bg-[#FBF6F2]"
          > {/* cafe-com-leite and cafe-latte-claro */}
            {/* Header do Card */}
            <div className="flex items-start justify-between mb-3">
              <div>
                <h3 className="text-lg font-display font-semibold text-[#2A1F1B]"> 
                  Mesa {r.mesaSlug}
                </h3>
                {r.hostNome && (
                  <p className="text-xs text-[#2A1F1B]/70">Anfitrião: {r.hostNome}</p>
                )}
                <p className="text-xs mt-0.5 text-[#2A1F1B]/60">
                  {new Date(r.abertaEm).toLocaleString('pt-BR', {
                    day: '2-digit',
                    month: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit'
                  })}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {r.assistida && (
                  <span className="px-2 py-1 text-[11px] rounded-full bg-[#D7B899]/30 border border-[#D7B899]/60 text-[#2A1F1B] font-semibold"> 
                    Pedido assistido
                  </span>
                )}
                <div className="flex items-center gap-1 px-2 py-1 rounded-full bg-[#D7B899]/20 border border-[#D7B899]/40 shadow-sm">
                  <svg className="w-3.5 h-3.5 text-[#2A1F1B]" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z" />
                  </svg>
                  <span className="text-xs font-medium text-[#2A1F1B]">{r.pessoas}</span> 
                </div>
              </div>
            </div>

            {/* Métricas */}
              <div className="space-y-2 mb-4 p-3 rounded-lg bg-[#8B7355]/5 border border-[#8B7355]/20 shadow-inner">
              <div className="flex justify-between items-center">
                <span className="text-xs text-[#2A1F1B]/70">Devido</span> 
                <span className={`text-sm font-semibold ${
                  'text-[#8B7355]' /* cafe-com-leite */
                }`}>
                  R$ {(r.devidoCentavos/100).toFixed(2)}
                </span>
              </div>
            </div>

            {/* Ações */}
            <div className="space-y-2">
              {/* Botão Novo Pedido (Principal) */}
              <button
                className="w-full px-3 py-2 rounded-lg text-sm font-bold flex items-center justify-center gap-2 transition-colors bg-[#B5854C] text-white hover:bg-[#B5854C]/90 shadow-sm"
                onClick={() => setFastSaleConfig({ open: true, mesaSlug: r.mesaSlug, sessaoId: r.sessaoMesaId })}
              >
                <Plus className="w-4 h-4" />
                Novo Pedido
              </button>

              <div className="grid grid-cols-2 gap-2">
                <Link
                    to={`/admin/mesas/${r.sessaoMesaId}/pagamentos`}
                    state={{ mesaSlug: r.mesaSlug }}
                    className="px-3 py-2 rounded-lg text-xs font-medium flex items-center justify-center gap-1 transition-colors bg-[#D7B899]/20 text-[#2A1F1B] hover:bg-[#D7B899]/30 border border-[#D7B899]/30"
                >
                    <CreditCard className="w-3 h-3" /> Pagamentos
                </Link>
                <button
                    className="px-3 py-2 rounded-lg text-xs font-medium flex items-center justify-center gap-1 transition-colors bg-[#FBF6F2] text-[#2A1F1B] hover:bg-[#8B7355]/10 border border-[#8B7355]/30 shadow-sm"
                    onClick={() => openItems(r.sessaoMesaId, r.mesaSlug)}
                >
                    <List className="w-3 h-3" /> Ver Itens
                </button>
              </div>

              <div className="grid grid-cols-2 gap-2">
                  <Button
                    className="px-3 py-2 text-xs font-medium flex items-center justify-center gap-1 transition-colors bg-white text-[#2A1F1B] hover:bg-[#D7B899]/20 border border-[#D7B899]/40 h-auto"
                    variant="outline"
                    onClick={() => openMoveDialog(r.sessaoMesaId, r.mesaSlug)}
                    disabled={mesasLoading}
                  >
                    <Move className="w-3 h-3" /> Mover
                  </Button>
                  
                  <AlertDialog open={showFecharDialog && selectedMesaId === r.sessaoMesaId} onOpenChange={setShowFecharDialog}>
                    <AlertDialogTrigger asChild>
                      <button
                        className="px-3 py-2 rounded-lg text-xs font-medium disabled:opacity-50 disabled:cursor-not-allowed transition-colors bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 flex items-center justify-center gap-1"
                        disabled={(r.devidoCentavos ?? 0) > 0}
                        onClick={() => setSelectedMesaId(r.sessaoMesaId)}
                      >
                        <XCircle className="w-3 h-3" /> Fechar
                      </button>
                    </AlertDialogTrigger>
                    <AlertDialogContent className="bg-[#FBF6F2] text-[#2A1F1B] border-[#8B7355]/20 shadow-lg"> 
                      <AlertDialogHeader>
                        <AlertDialogTitle className="text-[#2A1F1B]">Fechar Mesa</AlertDialogTitle> 
                        <AlertDialogDescription className="text-[#2A1F1B]/70"> 
                          Tem certeza que deseja fechar a mesa {rows.find(row => row.sessaoMesaId === selectedMesaId)?.mesaSlug}? Esta ação não pode ser desfeita.
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel className="bg-[#FBF6F2] border-[#8B7355]/30 text-[#2A1F1B] hover:bg-[#8B7355]/10"> {/* cafe-latte-claro, cafe-com-leite and cafe-dark-roast */}
                          Cancelar
                        </AlertDialogCancel>
                        <AlertDialogAction
                          onClick={fechar}
                          className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90"
                        > 
                          Confirmar
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
              </div>
            </div>
          </div>
        ))}
      </div>

      {rows.length === 0 && !loading && (
        <div className="text-center py-12">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[#D7B899]/15 border border-[#D7B899]/30 mb-4"> 
            <svg className="w-8 h-8 text-[#2A1F1B]" fill="none" stroke="currentColor" viewBox="0 0 24 24"> 
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M3 14h18m-9-4v8m-7 0h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
          </div>
          <p className="text-[#2A1F1B]/70">Nenhuma mesa aberta no momento</p> 
        </div>
      )}



      <Dialog open={moveDialogOpen} onOpenChange={setMoveDialogOpen}>
        <DialogContent className="max-w-md bg-[#FBF6F2] text-[#2A1F1B] border border-[#8B7355]/20 shadow-lg">
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Mover sessão</DialogTitle>
            <DialogDescription className="text-[#2A1F1B]/70">
              Escolha a mesa de destino para a sessão {moveCurrentMesaSlug ? `(${moveCurrentMesaSlug})` : ''}.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1">
              <label className="text-xs text-[#2A1F1B]/70">Mesa de destino</label>
              <Select
                value={moveMesaSlug || undefined}
                onValueChange={(v) => setMoveMesaSlug(v)}
                disabled={mesasLoading || mesaOptions.length === 0}
              >
                <SelectTrigger className="bg-white border-[#D7B899]/30 text-[#2A1F1B]">
                  <SelectValue placeholder={mesasLoading ? 'Carregando...' : 'Selecione uma mesa'} />
                </SelectTrigger>
                <SelectContent className="bg-white text-[#2A1F1B] border border-[#D7B899]/30 shadow-lg max-h-64 overflow-y-auto">
                  {mesaOptions.map((m) => (
                    <SelectItem key={m.slug} value={m.slug}>
                      {m.rotulo} ({m.slug})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <p className="text-xs text-[#2A1F1B]/60">
              A mesa de origem ficará livre após a movimentação e poderá receber novas sessões.
            </p>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              className="border-[#D7B899]/30 text-[#2A1F1B] bg-white hover:bg-[#8B7355]/10"
              onClick={() => setMoveDialogOpen(false)}
              disabled={moveSaving}
            >
              Cancelar
            </Button>
            <Button
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90"
              onClick={moveMesa}
              disabled={moveSaving || mesasLoading || mesaOptions.length === 0}
            >
              {moveSaving ? 'Movendo...' : 'Mover sessão'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={itemsModalOpen} onOpenChange={setItemsModalOpen}>
        <DialogContent className="max-w-4xl max-h-[calc(100vh-96px)] overflow-hidden flex flex-col bg-[#FBF6F2] text-[#2A1F1B] border border-[#8B7355]/20 shadow-lg p-0 sm:p-6">
          <DialogHeader className="flex-shrink-0 px-4 sm:px-0 pt-4 sm:pt-0">
            <DialogTitle className="text-[#2A1F1B] text-lg sm:text-xl">Itens da mesa {selectedMesaSlug || ''}</DialogTitle>
            <DialogDescription className="text-[#2A1F1B]/70 text-xs sm:text-sm">Pedidos realizados.</DialogDescription>
          </DialogHeader>
          <div className="bg-[#FBF6F2] rounded-2xl shadow-xl border border-[#D7B899]/20 overflow-hidden flex-1 flex flex-col relative">
            <div className="flex items-center justify-between px-3 sm:px-4 py-2 sm:py-3 border-b border-[#D7B899]/20 bg-[#D7B899]/5 sticky top-0 z-10">
              <div className="text-xs sm:text-sm font-medium text-[#2A1F1B]">Itens da mesa</div>
              <div className="flex items-center gap-2 sm:gap-3">
                <div className="text-[10px] sm:text-xs text-[#8B7355]/70">{items.length} itens</div>
                <Button
                  size="sm"
                  className="bg-[#B5854C] text-white hover:bg-[#B5854C]/90 text-xs sm:text-sm h-8 sm:h-9 px-2 sm:px-4"
                  onClick={openAddItemDialog}
                >
                  + Adicionar
                </Button>
              </div>
            </div>
            <div className="flex-1 overflow-x-auto overflow-y-auto">
              <div className="min-w-[600px]">
                <Table>
                  <TableHeader>
                    <TableRow className="border-b border-[#D7B899]/20 bg-[#D7B899]/5">
                      <TableHead className="text-[#2A1F1B] font-medium text-xs py-2 px-3 w-48 max-w-48">Produto</TableHead>
                      <TableHead className="text-[#2A1F1B] font-medium text-xs py-2 px-3 w-16">Qtd</TableHead>
                      <TableHead className="text-[#2A1F1B] font-medium text-xs py-2 px-3 w-20">Valor</TableHead>
                      <TableHead className="text-[#2A1F1B] font-medium text-xs py-2 px-3 w-24">Status</TableHead>
                      <TableHead className="text-[#2A1F1B] font-medium text-xs py-2 px-3 w-24">Ações</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {itemsLoading && (
                      <TableRow>
                        <TableCell colSpan={5} className="text-center py-6 text-[#8B7355]/70 text-xs">Carregando...</TableCell>
                      </TableRow>
                    )}
                    {!itemsLoading && items.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} className="text-center py-6 text-[#8B7355]/70 text-xs">Nenhum item.</TableCell>
                      </TableRow>
                    )}
                    {items.map((it) => (
                      <TableRow key={it.itemPedidoId} className="hover:bg-[#D7B899]/5 transition-colors border-b border-[#D7B899]/10">
                        <TableCell className="text-[#2A1F1B] text-xs py-2 px-3 font-medium truncate max-w-48">{it.produtoNome}</TableCell>
                        <TableCell className="text-[#2A1F1B] text-xs py-2 px-3">{it.quantidade}</TableCell>
                        <TableCell className="text-[#2A1F1B] text-xs py-2 px-3">R$ {(Number(it.valorTotal) || 0).toFixed(2)}</TableCell>
                        <TableCell className="text-[10px] sm:text-xs text-[#2A1F1B] py-2 px-3">
                          {statusLabels[(it.status || 'QUEUED').toString().toUpperCase()] || 'Desconhecido'}
                        </TableCell>
                        <TableCell className="py-2 px-3">
                          {(it.status || '').toLowerCase() !== 'canceled' ? (
                            <Button
                              size="sm"
                              variant="outline"
                              className="border-[#D7B899]/40 text-[#2A1F1B] bg-white hover:bg-[#D7B899]/10 text-xs h-7 px-2"
                              onClick={() => askCancel(it.itemPedidoId)}
                            >
                              Cancelar
                            </Button>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>



      <Dialog open={assistDialogOpen} onOpenChange={setAssistDialogOpen}>
        <DialogContent className="max-w-xl bg-[#FBF6F2] text-[#2A1F1B] border border-[#8B7355]/20 shadow-lg"> 
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Pedido assistido</DialogTitle> 
            <DialogDescription className="text-[#2A1F1B]/70">
              Inicie uma sessão em uma mesa para atender o cliente pelo staff.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1">
              <label className="text-xs text-[#2A1F1B]/70">Mesa</label> 
              <Select
                value={assistMesaSlug || undefined}
                onValueChange={(v) => setAssistMesaSlug(v)}
                disabled={mesasLoading || mesaOptions.length === 0}
              >
                <SelectTrigger className="bg-white border-[#D7B899]/30 text-[#2A1F1B]"> 
                  <SelectValue placeholder={mesasLoading ? 'Carregando...' : 'Selecione uma mesa'} />
                </SelectTrigger>
                <SelectContent className="bg-white text-[#2A1F1B] border border-[#D7B899]/30 shadow-lg max-h-64 overflow-y-auto"> 
                  {mesaOptions.map((m) => (
                    <SelectItem key={m.slug} value={m.slug}>
                      {m.rotulo} ({m.slug})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <label className="text-xs text-[#2A1F1B]/70">Cliente cadastrado (opcional)</label> 
              <Select
                value={assistClienteId ? String(assistClienteId) : undefined}
                onValueChange={(v) => setAssistClienteId(Number(v))}
                disabled={clientesLoading}
              >
                <SelectTrigger className="bg-white border-[#D7B899]/30 text-[#2A1F1B]"> 
                  <SelectValue placeholder={clientesLoading ? 'Carregando...' : 'Selecione um cliente (opcional)'} />
                </SelectTrigger>
                <SelectContent className="bg-white text-[#2A1F1B] border border-[#D7B899]/30 shadow-lg max-h-64 overflow-y-auto"> 
                  {clienteOptions.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>
                      {c.nome}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <label className="text-xs text-[#2A1F1B]/70">Nome do cliente (se não selecionar um cadastrado)</label> 
              <Input
                value={assistNome}
                onChange={(e) => setAssistNome(e.target.value)}
                placeholder="Nome para exibição na mesa"
                className="bg-white border-[#D7B899]/30 text-[#2A1F1B]" 
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              className="border-[#D7B899]/30 text-[#2A1F1B] bg-white hover:bg-[#8B7355]/10" 
              onClick={() => setAssistDialogOpen(false)}
              disabled={assistSaving}
            >
              Cancelar
            </Button>
            <Button
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90" 
              onClick={startAssistedSession}
              disabled={assistSaving}
            >
              {assistSaving ? 'Iniciando...' : 'Iniciar pedido'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <FastSaleDrawer
        open={fastSaleConfig.open}
        onOpenChange={(isOpen) => setFastSaleConfig(prev => ({ ...prev, open: isOpen }))}
        onSuccess={() => {
            load();
            if (fastSaleConfig.sessaoId && itemsModalOpen && selectedMesaId === fastSaleConfig.sessaoId) {
                fetchItems(fastSaleConfig.sessaoId);
            }
        }}
        balcaoSessaoId={balcaoSessaoId}
        mesaSlug={fastSaleConfig.mesaSlug}
        sessaoMesaId={fastSaleConfig.sessaoId}
      />
      {/* Dialog de Notificação */}
      <Dialog open={showNotification} onOpenChange={setShowNotification}>
        <DialogContent className="bg-[#FBF6F2] text-[#2A1F1B] border-[#8B7355]/20 shadow-lg">
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Notificação</DialogTitle>
            <DialogDescription className="text-[#2A1F1B]/70">
              {notificationMessage}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              onClick={() => setShowNotification(false)}
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90"
            >
              OK
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Diálogo de Confirmação de Cancelamento */}
      <Dialog open={cancelDialogOpen} onOpenChange={setCancelDialogOpen}>
        <DialogContent className="bg-[#FBF6F2] text-[#2A1F1B] border border-[#8B7355]/20 shadow-lg">
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Confirmar Cancelamento</DialogTitle>
            <DialogDescription className="text-[#2A1F1B]/70">
              Selecione o motivo e confirme o cancelamento do item.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div>
              <label className="text-sm font-medium text-[#2A1F1B]">Motivo do cancelamento</label>
              <Select value={cancelMotivo} onValueChange={setCancelMotivo}>
                <SelectTrigger className="mt-1 bg-[#FBF6F2] border-[#8B7355]/30 text-[#2A1F1B]">
                  <SelectValue placeholder="Selecione um motivo" />
                </SelectTrigger>
                <SelectContent className="bg-[#FBF6F2] text-[#2A1F1B] border-[#8B7355]/30">
                  <SelectItem
                    value="FALTA_INSUMO"
                    className="data-[highlighted]:bg-[#D7B899]/30 data-[highlighted]:text-[#2A1F1B] data-[state=checked]:bg-[#D7B899]/50"
                  >
                    Falta de insumo
                  </SelectItem>
                  <SelectItem
                    value="EQUIPE_INDISPONIVEL"
                    className="data-[highlighted]:bg-[#D7B899]/30 data-[highlighted]:text-[#2A1F1B] data-[state=checked]:bg-[#D7B899]/50"
                  >
                    Equipe indisponível
                  </SelectItem>
                  <SelectItem
                    value="ERRO_PEDIDO"
                    className="data-[highlighted]:bg-[#D7B899]/30 data-[highlighted]:text-[#2A1F1B] data-[state=checked]:bg-[#D7B899]/50"
                  >
                    Erro no pedido
                  </SelectItem>
                  <SelectItem
                    value="CLIENTE_DESISTIU"
                    className="data-[highlighted]:bg-[#D7B899]/30 data-[highlighted]:text-[#2A1F1B] data-[state=checked]:bg-[#D7B899]/50"
                  >
                    Cliente desistiu
                  </SelectItem>
                  <SelectItem
                    value="OUTRO"
                    className="data-[highlighted]:bg-[#D7B899]/30 data-[highlighted]:text-[#2A1F1B] data-[state=checked]:bg-[#D7B899]/50"
                  >
                    Outro
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>

            {cancelMotivo === 'OUTRO' && (
              <div>
                <label className="text-sm font-medium text-[#2A1F1B]">Detalhes (opcional)</label>
                <Textarea
                  value={cancelDetalhe}
                  onChange={(e) => setCancelDetalhe(e.target.value)}
                  className="mt-1 bg-[#FBF6F2] border-[#8B7355]/30 text-[#2A1F1B]"
                  placeholder="Forneça mais detalhes sobre o motivo do cancelamento..."
                />
              </div>
            )}
          </div>
          <DialogFooter className="gap-2">
            <Button
              variant="outline"
              className="border-[#8B7355]/30 text-[#2A1F1B] bg-[#FBF6F2] hover:bg-[#8B7355]/10"
              onClick={() => setCancelDialogOpen(false)}
            >
              Cancelar
            </Button>
            <Button
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90"
              onClick={confirmCancel}
            >
              Confirmar Cancelamento
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
