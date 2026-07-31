import { useState, useEffect, useMemo, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { apiConfig } from '@/config/api';
import axios from '@/lib/axios';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { useIsMobile } from '@/hooks/use-mobile';
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
} from '@/components/ui/alert-dialog';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Switch } from '@/components/ui/switch';
import { Progress } from '@/components/ui/progress';
import { X, Plus, QrCode, CreditCard, Banknote, Ticket, Wand2, Loader2, Building2, ArrowLeft } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

interface Pessoa {
  sessaoConvidadoId: number;
  nome: string;
  subtotalCentavos: number;
  pagoCentavos: number;
  devidoCentavos: number;
  pagoBaseCentavos?: number;
  pagoTaxaServicoCentavos?: number;
  taxaServicoSugeridaCentavos?: number;
  taxaServicoPendenteCentavos?: number;
  couvertCentavos?: number;
  pagoCouvertCentavos?: number;
  devidoCouvertCentavos?: number;
  devidoTotalCentavos?: number;
}

interface ContaMesa {
  totalMesaCentavos: number;
  taxaServicoCentavos: number;
  descontosCentavos: number;
  pagoCentavos: number;
  devidoCentavos: number;
  pessoas: Pessoa[];
  subtotalCentavos?: number;
  pagoBaseCentavos?: number;
  pagoTaxaServicoCentavos?: number;
  taxaServicoPagaCentavos?: number;
  taxaServicoPendenteCentavos?: number;
  couvertCentavos?: number;
  pagoCouvertCentavos?: number;
  devidoCouvertCentavos?: number;
  devidoTotalCentavos?: number;
}

interface PagamentoHistorico {
  id: number;
  beneficiario: string;
  beneficiarioId: number | null;
  pagante: string | null;
  paganteId: number | null;
  valorCentavos: number;
  valorBaseCentavos?: number;
  valorTaxaServicoCentavos?: number;
  percentualTaxaServico?: number;
  incluiTaxaServico?: boolean;
  metodo: string;
  status: string;
  criadoEm: string;
  pagoEm: string | null;
}

interface ElegibilidadeMensalista {
  possivel: boolean;
  motivoBloqueio: string | null;
  hostNome?: string;
  hostId?: number;
}

interface PagamentoFormItem {
  id: string;
  beneficiarioId: number | null;
  paganteId: number | null;
  valorCentavos: string;
  metodo: 'pix' | 'card' | 'cash' | 'voucher';
  cartaoTipo?: 'credito' | 'debito';
  incluiTaxaServico?: boolean;
}

interface NfcePreviewItem {
  descricao: string;
  codigo: string;
  quantidade: number;
  valorUnitario: number;
  valorTotal: number;
  ncm: string;
  cfop: string;
  cst: string;
}

interface NfcePreviewPagamento {
  tipo: string;
  valor: number;
  dataPagamento?: string | null;
  codigoAutorizacao?: string | null;
}

interface NfcePreviewData {
  pagamentoId: number;
  valorTotal: number;
  taxaServico: number;
  statusVenda: string;
  statusNfe: string;
  observacoes?: string;
  itens: NfcePreviewItem[];
  pagamentos: NfcePreviewPagamento[];
  clienteId?: number | null;
  clienteNome?: string | null;
}

type ContaItem = {
  pedidoId: number;
  pedidoStatus: string;
  pedidoCriadoEm: string;
  itemPedidoId: number;
  produtoId: number;
  produtoNome: string;
  quantidade: number;
  precoUnitCentavos: number;
  status: string;
  observacoes?: string | null;
};

export default function PagamentosPage() {
  const { sessaoMesaId } = useParams<{ sessaoMesaId: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const isMobile = useIsMobile();
  const [mesaSlug, setMesaSlug] = useState<string>('');
  const [conta, setConta] = useState<ContaMesa | null>(null);
  const [historico, setHistorico] = useState<PagamentoHistorico[]>([]);
  const [pagamentos, setPagamentos] = useState<PagamentoFormItem[]>([
    {
      id: '1',
      beneficiarioId: null,
      paganteId: null,
      valorCentavos: '',
      metodo: 'cash',
      cartaoTipo: 'credito',
      incluiTaxaServico: false,
    },
  ]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [showFecharMesaDialog, setShowFecharMesaDialog] = useState(false);
  const [notificationMessage, setNotificationMessage] = useState<string>('');
  const [showNotification, setShowNotification] = useState(false);
  const [itemsByGuest, setItemsByGuest] = useState<Record<number, ContaItem[]>>({});
  const [showHistorico, setShowHistorico] = useState(false);
  const [showHistoricoModal, setShowHistoricoModal] = useState(false);
  const [showItemsModal, setShowItemsModal] = useState(false);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [metodoSimples, setMetodoSimples] = useState<'pix'|'card'|'cash'>('cash'); // legado (não mais usado no submit múltiplo)
  const [pixValor, setPixValor] = useState<string>('');
  const [cardCreditoValor, setCardCreditoValor] = useState<string>('');
  const [cardDebitoValor, setCardDebitoValor] = useState<string>('');
  const [voucherValor, setVoucherValor] = useState<string>('');
  const [cashValor, setCashValor] = useState<string>('');
  const [closeAfterPay, setCloseAfterPay] = useState<boolean>(false);
  const [showOnlyOpenItems, setShowOnlyOpenItems] = useState<boolean>(true);
  const [redirectAfterNotif, setRedirectAfterNotif] = useState<string | null>(null);
  const [taxaAtiva, setTaxaAtiva] = useState<boolean>(false);
  const [taxaPercentual, setTaxaPercentual] = useState<number>(10);
  const [incluirTaxaServico, setIncluirTaxaServico] = useState<boolean>(false);
  const [nfcePreview, setNfcePreview] = useState<NfcePreviewData | null>(null);
  const [showNfcePreviewModal, setShowNfcePreviewModal] = useState(false);
  const [loadingNfcePreview, setLoadingNfcePreview] = useState(false);
  const [emittingNfce, setEmittingNfce] = useState(false);
  const [showNfceConfirmDialog, setShowNfceConfirmDialog] = useState(false);
  const [nfceCpfInput, setNfceCpfInput] = useState('');
  const [nfceCpfError, setNfceCpfError] = useState<string | null>(null);
  const [comprovantePdfUrl, setComprovantePdfUrl] = useState<string | null>(null);
  const [printingComprovanteAgent, setPrintingComprovanteAgent] = useState(false);
  const [whatsappPhone, setWhatsappPhone] = useState<string>('');
  const [sendingWhatsapp, setSendingWhatsapp] = useState(false);
  const [paymentsExpanded, setPaymentsExpanded] = useState(false);
  const registerButtonRef = useRef<HTMLButtonElement | null>(null);
  
  // Dialog de confirmação do WhatsApp
  const [showWhatsappDialog, setShowWhatsappDialog] = useState(false);
  const [whatsappTargetPdfUrl, setWhatsappTargetPdfUrl] = useState<string | null>(null);

  const openWhatsappDialog = (url: string | null) => {
    if (!url) {
      showNotif('PDF indisponível para envio.');
      return;
    }
    setWhatsappTargetPdfUrl(url);
    setWhatsappPhone(''); // Limpar telefone anterior
    setShowWhatsappDialog(true);
  };

  // Estados para visualização da DANFCE
  const [showDanfceModal, setShowDanfceModal] = useState(false);
  const [danfcePdfUrl, setDanfcePdfUrl] = useState<string | null>(null);
  const [danfcePaymentId, setDanfcePaymentId] = useState<number | null>(null);
  const [printingDanfceAgent, setPrintingDanfceAgent] = useState(false);

  const [elegibilidadeMensalista, setElegibilidadeMensalista] = useState<ElegibilidadeMensalista | null>(null);
  const [showMensalistaDialog, setShowMensalistaDialog] = useState(false);
  const hasMultiplePessoas = (conta?.pessoas?.length || 0) > 1;
  const temClienteNfce = !!nfcePreview?.clienteId;

  const showNotif = (message: string, redirectTo?: string) => {
    setNotificationMessage(message);
    setShowNotification(true);
    setRedirectAfterNotif(redirectTo || null);
  };

  const formatCpfInput = (value: string) => {
    const digits = value.replace(/\D/g, '').slice(0, 11);
    if (digits.length <= 3) return digits;
    if (digits.length <= 6) return `${digits.slice(0, 3)}.${digits.slice(3)}`;
    if (digits.length <= 9) return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`;
    return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`;
  };

  const isValidCpf = (cpf: string) => {
    if (!cpf || cpf.length !== 11) return false;
    if (/^(\d)\1{10}$/.test(cpf)) return false;
    let soma = 0;
    for (let i = 0; i < 9; i += 1) {
      soma += Number(cpf[i]) * (10 - i);
    }
    let mod = soma % 11;
    const dv1 = mod < 2 ? 0 : 11 - mod;
    if (dv1 !== Number(cpf[9])) return false;
    soma = 0;
    for (let i = 0; i < 10; i += 1) {
      soma += Number(cpf[i]) * (11 - i);
    }
    mod = soma % 11;
    const dv2 = mod < 2 ? 0 : 11 - mod;
    return dv2 === Number(cpf[10]);
  };

  const nfceCpfDigits = nfceCpfInput.replace(/\D/g, '');
  const nfceCpfInvalido = !temClienteNfce && nfceCpfDigits.length > 0 && !isValidCpf(nfceCpfDigits);

  const openNfceConfirmDialog = () => {
    if (!nfcePreview) return;
    setNfceCpfInput('');
    setNfceCpfError(null);
    setShowNfceConfirmDialog(true);
  };

  const loadPreviewData = async (pagamentoId: number, mesaFechada: boolean): Promise<boolean> => {
    setLoadingNfcePreview(true);
    setComprovantePdfUrl(null);
    try {
      const res = await axios.get<NfcePreviewData>(
        `${apiConfig.erpBaseUrl}/api/admin/nfce/pagamentos/${pagamentoId}/preview`
      );
      setNfcePreview(res.data);

      try {
        const resPdf = await axios.get(
          `${apiConfig.erpBaseUrl}/api/admin/nfce/pagamentos/${pagamentoId}/comprovante`,
          { responseType: 'blob' }
        );
        const pdfUrl = URL.createObjectURL(resPdf.data);
        setComprovantePdfUrl(pdfUrl);
      } catch (pdfError) {
        console.error('Erro ao carregar PDF do comprovante:', pdfError);
      }

      setShowNfcePreviewModal(true);
      return true;
    } catch (error: any) {
      console.error('Erro ao carregar preview da NFC-e:', error);
      const msg = error?.response?.data?.error?.message || 'Pagamento registrado, mas falhou o preview da NFC-e.';
      // showNotif(msg); // Notificar depois que o modal fechar ou se não abrir
      if (mesaFechada) setRedirectAfterNotif('/admin/mesas');
      return false;
    } finally {
      setLoadingNfcePreview(false);
    }
  };

  const handleEmitirNfce = async (cpf?: string) => {
    if (!nfcePreview) return;
    const pagamentoId = nfcePreview.pagamentoId;
    setEmittingNfce(true);
    try {
      // 1. Emitir a NFC-e
      const res = await axios.post(
        `${apiConfig.erpBaseUrl}/api/admin/nfce/pagamentos/${nfcePreview.pagamentoId}/emitir`,
        cpf ? { cpf } : undefined
      );

      const status = res.data?.status;
      if (status && status !== 'AUTORIZADA') {
        const motivo = res.data?.motivoRejeicao || 'Documento rejeitado.';
        showNotif(motivo);
        return;
      }
      
      // Apenas recarrega dados se NÃO houver redirecionamento pendente (mesa não fechada)
      if (!redirectAfterNotif) {
        await loadData();
      }
      
      // Fecha modal de prévia
      setShowNfcePreviewModal(false);
      setNfcePreview(null);

      // 2. Buscar o PDF da DANFCE recém gerada
      // Backend retorna { success: true, nfeId: 123, ... }
      const nfeId = res.data?.nfeId || res.data?.id;
      const modelo = res.data?.modelo;
      
      if (nfeId) {
        try {
          const pdfEndpoint = modelo === 55
            ? `${apiConfig.erpBaseUrl}/api/admin/nfe/${nfeId}/danfe.pdf`
            : `${apiConfig.erpBaseUrl}/api/danfce/${nfeId}/pdf`;
          const resPdf = await axios.get(pdfEndpoint, { responseType: 'blob' });
          const pdfUrl = URL.createObjectURL(resPdf.data);
          setDanfcePdfUrl(pdfUrl);
          setShowDanfceModal(true);
          setDanfcePaymentId(pagamentoId);
        } catch (pdfError) {
          console.error('Erro ao carregar PDF da DANFCE:', pdfError);
          // Fallback para mensagem de texto se falhar o PDF
          const chave = res.data?.chaveAcesso ? `\nChave: ${res.data.chaveAcesso}` : '';
          showNotif(`NFC-e gerada com sucesso! (PDF indisponível)${chave}`, redirectAfterNotif || undefined);
        }
      } else {
        // Fallback se não vier ID
        const chave = res.data?.chaveAcesso ? `\nChave: ${res.data.chaveAcesso}` : '';
        showNotif(`NFC-e gerada com sucesso!${chave}`, redirectAfterNotif || undefined);
      }

    } catch (error: any) {
      const msg = error?.response?.data?.error?.message || 'Erro ao gerar NFC-e';
      showNotif(msg);
    } finally {
      setEmittingNfce(false);
    }
  };

  const handleConfirmEmitirNfce = async () => {
    if (!nfcePreview) return;
    const temCliente = !!nfcePreview.clienteId;
    let cpfDigits = '';

    if (!temCliente) {
      cpfDigits = nfceCpfInput.replace(/\D/g, '');
      if (cpfDigits.length > 0 && !isValidCpf(cpfDigits)) {
        setNfceCpfError('CPF inválido.');
        return;
      }
    }

    setShowNfceConfirmDialog(false);
    await handleEmitirNfce(cpfDigits.length > 0 ? cpfDigits : undefined);
  };

  const handlePrintComprovanteOnAgent = async () => {
    if (!nfcePreview) return;
    setPrintingComprovanteAgent(true);
    try {
      const response = await axios.post(
        `${apiConfig.erpBaseUrl}/api/admin/nfce/pagamentos/${nfcePreview.pagamentoId}/comprovante/print`
      );
      toast({
        title: 'Impressor enviado',
        description: `Comprovante ${nfcePreview.pagamentoId} enviado para impressão (${response.data?.job_id || 'sem ID'}).`,
      });
    } catch (error: any) {
      const msg = error?.response?.data?.message || 'Falha ao enviar comprovante para o Print Agent.';
      toast({
        title: 'Erro',
        description: msg,
        variant: 'destructive',
      });
    } finally {
      setPrintingComprovanteAgent(false);
    }
  };

  const handlePrintDanfceOnAgent = async () => {
    if (!danfcePaymentId) return;
    setPrintingDanfceAgent(true);
    try {
      const response = await axios.post(
        `${apiConfig.erpBaseUrl}/api/admin/nfce/pagamentos/${danfcePaymentId}/danfce/print`
      );
      toast({
        title: 'Impressor enviado',
        description: `DANFCE ${danfcePaymentId} enviado para impressão (${response.data?.job_id || 'sem ID'}).`,
      });
    } catch (error: any) {
      const msg = error?.response?.data?.message || 'Falha ao enviar o DANFCE para o Print Agent.';
      toast({
        title: 'Erro',
        description: msg,
        variant: 'destructive',
      });
    } finally {
      setPrintingDanfceAgent(false);
    }
  };

  useEffect(() => {
    if (sessaoMesaId) {
      loadData();
    }
  }, [sessaoMesaId]);

  useEffect(() => {
    loadTaxaConfig();
  }, []);

  // Carrega itens consumidos por convidado quando a conta/pessoas mudar
  useEffect(() => {
    (async () => {
      if (!conta || !Array.isArray(conta.pessoas)) return;
      const map: Record<number, ContaItem[]> = {};
      for (const p of conta.pessoas) {
        try {
          const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/conta`, {
            params: { sessaoConvidadoId: p.sessaoConvidadoId },
          });
          map[p.sessaoConvidadoId] = (data?.itens || []) as ContaItem[];
        } catch {}
      }
      setItemsByGuest(map);
      // Seleciona todos por padrão conforme solicitado
      try {
        setSelectedIds((conta.pessoas || [])
          .filter((p:any) => (p.devidoCentavos || 0) > 0)
          .map((p:any) => p.sessaoConvidadoId));
      } catch {}
    })();
  }, [JSON.stringify(conta?.pessoas || [])]);

  // Ajusta toggles de taxa quando configuração é carregada
  useEffect(() => {
    const isBalcao = mesaSlug.toUpperCase() === 'BALCAO';
    setPagamentos((prev) =>
      prev.map((p) => ({
        ...p,
        incluiTaxaServico: isBalcao
          ? false
          : typeof p.incluiTaxaServico === 'boolean'
            ? p.incluiTaxaServico
            : taxaAtiva,
      }))
    );
    setIncluirTaxaServico(isBalcao ? false : taxaAtiva);
  }, [taxaAtiva, mesaSlug]);

  const loadData = async () => {
    setLoading(true);
    try {
      // Buscar conta da mesa
      const contaRes = await axios.get<ContaMesa>(
        `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/conta`
      );
      setConta(contaRes.data);

      // Extrair slug da mesa do primeiro pessoa (se houver)
      if (contaRes.data.pessoas.length > 0) {
        // Buscar info da sessão para pegar o slug
        const sessoesRes = await axios.get(
          `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes`,
          { params: { status: 'open' } }
        );
        const sessoesData = sessoesRes.data;
        const sessao = sessoesData.sessoes?.find(
          (s: any) => s.sessaoMesaId === Number(sessaoMesaId)
        );
        if (sessao) {
          setMesaSlug(sessao.mesaSlug);
        }
      }

      // Buscar histórico de pagamentos
      const historicoRes = await axios.get<{ pagamentos: PagamentoHistorico[], elegibilidadeMensalista?: ElegibilidadeMensalista }>(
        `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/pagamentos`
      );
      setHistorico(historicoRes.data.pagamentos || []);
      if (historicoRes.data.elegibilidadeMensalista) {
        setElegibilidadeMensalista(historicoRes.data.elegibilidadeMensalista);
      }
    } catch (error) {
      console.error('Erro ao carregar dados:', error);
      showNotif('Erro ao carregar dados da mesa');
    } finally {
      setLoading(false);
    }
  };

  const loadTaxaConfig = async () => {
    try {
      let ativa = false;
      let pct = 10;
      try {
        const resAtiva = await axios.get<string>(`/api/configs/config/taxa_servico_ativa`, { baseURL: apiConfig.erpBaseUrl, responseType: 'text' });
        const txt = (resAtiva.data || '').trim().toLowerCase();
        ativa = txt === 'true' || txt === '1' || txt === 'sim';
      } catch {}
      try {
        const resPct = await axios.get<string>(`/api/configs/config/taxa_servico_percentual`, { baseURL: apiConfig.erpBaseUrl, responseType: 'text' });
        const txt = (resPct.data || '').trim();
        const num = parseFloat(txt);
        if (!Number.isNaN(num)) pct = num;
      } catch {}
      setTaxaAtiva(ativa);
      setTaxaPercentual(pct);
      setIncluirTaxaServico(ativa);
    } catch (e) {
      console.error('Erro ao carregar config de taxa de serviço', e);
    }
  };

  const adicionarPagamento = () => {
    const newId = String(Date.now());
    setPagamentos([
      ...pagamentos,
      {
        id: newId,
        beneficiarioId: null,
        paganteId: null,
        valorCentavos: '',
        metodo: 'cash',
        cartaoTipo: 'credito',
        incluiTaxaServico: taxaAtiva,
      },
    ]);
  };

  const removerPagamento = (id: string) => {
    if (pagamentos.length === 1) return;
    setPagamentos(pagamentos.filter((p) => p.id !== id));
  };

  const atualizarPagamento = (
    id: string,
    field: keyof PagamentoFormItem,
    value: any
  ) => {
    setPagamentos(
      pagamentos.map((p) => (p.id === id ? { ...p, [field]: value } : p))
    );
  };

  const quitarTotal = (id: string, beneficiarioId: number | null) => {
    if (beneficiarioId === null) {
      const devido = conta?.devidoCentavos || 0;
      atualizarPagamento(id, 'valorCentavos', String(devido));
    } else {
      const pessoa = conta?.pessoas.find(
        (p) => p.sessaoConvidadoId === beneficiarioId
      );
      if (pessoa) {
        atualizarPagamento(id, 'valorCentavos', String(pessoa.devidoCentavos));
      }
    }
  };

  const handleSubmit = async () => {
    const pagamentosValidos = pagamentos.filter(
      (p) => p.valorCentavos && Number(p.valorCentavos) > 0
    );

    if (pagamentosValidos.length === 0) {
      showNotif('Informe ao menos um pagamento com valor válido');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        pagamentos: pagamentosValidos.map((p) => ({
          sessaoConvidadoId: p.beneficiarioId,
          paganteId: p.paganteId,
          ...(() => {
            const totalInformado = Number(p.valorCentavos);
            const { base, taxa } = splitBaseETaxa(totalInformado);
            return {
              valorCentavos: base,
              valorTaxaServicoCentavos: taxa,
            };
          })(),
          incluiTaxaServico: incluirTaxaServico,
          metodo: p.metodo,
          cartaoTipo: p.cartaoTipo,
        })),
      };

      await axios.post(
        `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/pagamentos/multiplo`,
        payload
      );

      await loadData();

      setPagamentos([
        {
          id: String(Date.now()),
          beneficiarioId: null,
          paganteId: null,
          valorCentavos: '',
          metodo: 'cash',
          cartaoTipo: 'credito',
          incluiTaxaServico: taxaAtiva,
        },
      ]);

      showNotif('Pagamento(s) registrado(s) com sucesso!');
    } catch (error: any) {
      console.error('Erro ao registrar pagamentos:', error);
      const msg =
        error.response?.data?.error?.message || 'Erro ao registrar pagamentos';
      showNotif(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleFecharMesa = async () => {
    setSubmitting(true);
    try {
      await axios.post(
        `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/fechar`
      );

      showNotif('Mesa fechada com sucesso!', '/admin/mesas');
    } catch (error: any) {
      console.error('Erro ao fechar mesa:', error);
      const msg = error.response?.data?.error?.message || 'Erro ao fechar mesa';
      showNotif(msg);
    } finally {
      setSubmitting(false);
      setShowFecharMesaDialog(false);
    }
  };

  const handleFaturarMensalista = async () => {
    if (!elegibilidadeMensalista?.possivel) return;

    setSubmitting(true);
    try {
      await axios.post(
        `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/faturar-mensalista`
      );

      setShowMensalistaDialog(false);
      showNotif(`Faturamento registrado com sucesso para ${elegibilidadeMensalista.hostNome}!`, '/admin/mesas');
    } catch (error: any) {
      console.error('Erro ao faturar mensalista:', error);
      const msg = error.response?.data?.error?.message || 'Erro ao registrar faturamento mensalista';
      showNotif(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleEnviarWhatsapp = async (pdfUrlToSend: string | null) => {
    if (!pdfUrlToSend) {
      showNotif('Nenhum comprovante PDF disponível para envio.');
      return;
    }
    const cleanPhone = whatsappPhone.replace(/\D/g, ''); // Clean phone number
    if (!cleanPhone || cleanPhone.length < 10) { // Basic validation for phone number
      showNotif('Por favor, informe um telefone válido (mínimo 10 dígitos).');
      return;
    }

    setSendingWhatsapp(true);
    try {
      // 1. Fetch the PDF blob
      const response = await fetch(pdfUrlToSend);
      const pdfBlob = await response.blob();

      // 2. Create FormData
      const formData = new FormData();
      formData.append('arquivo', pdfBlob, `comprovante_mesa_${sessaoMesaId}.pdf`);
      formData.append('telefone', cleanPhone);
      formData.append('mensagem', `Segue o comprovante da sua mesa ${mesaSlug || sessaoMesaId}.`);

      // 3. Send to backend (ERP)
      await axios.post(`${apiConfig.erpBaseUrl}/api/whatsapp/enviar-arquivo`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      showNotif('Comprovante enviado via WhatsApp com sucesso!', '/admin/mesas');
      setWhatsappPhone(''); // Clear phone input after sending
      setShowWhatsappDialog(false); // Fecha o diálogo de confirmação
    } catch (error: any) {
      console.error('Erro ao enviar comprovante via WhatsApp:', error);
      const msg = error.response?.data?.message || 'Erro ao enviar comprovante via WhatsApp.';
      showNotif(msg);
    } finally {
      setSendingWhatsapp(false);
    }
  };

  const formatCurrency = (centavos: number) => {
    return `R$ ${(centavos / 100).toFixed(2)}`;
  };

  const formatCurrencyFromFloat = (valor: number) => {
    if (valor === undefined || valor === null || isNaN(valor)) {
      return formatCurrency(0);
    }
    return formatCurrency(Math.round(valor * 100));
  };

  const formatDateTime = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleString('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'short',
    });
  };

  const metodosLabel = {
    pix: 'PIX',
    card: 'Cartão',
    cash: 'Dinheiro',
    voucher: 'Voucher',
  };

  // Cálculo de taxa de serviço
  const calcularTaxaServico = (valorCentavos: number, incluir: boolean) => {
    if (!incluir || !taxaAtiva) return 0;
    const base = Math.max(0, valorCentavos || 0);
    return Math.round((base * taxaPercentual) / 100);
  };

  // Se o usuário informou o total (base + taxa) e a taxa está ativa,
  // derivamos base e taxa para enviar corretamente ao backend.
  const splitBaseETaxa = (totalCentavos: number): { base: number; taxa: number } => {
    if (!incluirTaxaServico || !taxaAtiva) {
      return { base: Math.max(0, totalCentavos), taxa: 0 };
    }
    const ratio = 1 + (taxaPercentual / 100);
    const base = Math.max(0, Math.round(totalCentavos / ratio));
    const taxa = Math.max(0, totalCentavos - base);
    return { base, taxa };
  };

  // Helpers locais para status de item
  const badgeClassLocal = (status?: string) => {
    switch ((status || '').toLowerCase()) {
      case 'queued': return 'bg-gray-100 text-gray-700';
      case 'pending': return 'bg-gray-100 text-gray-700';
      case 'accepted': return 'bg-blue-100 text-blue-700';
      case 'preparing': return 'bg-amber-100 text-amber-700';
      case 'ready': return 'bg-emerald-100 text-emerald-700';
      case 'delivered': return 'bg-green-100 text-green-700';
      case 'canceled': return 'bg-red-100 text-red-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  };
  const statusLabelLocal = (status?: string) => {
    switch ((status || '').toLowerCase()) {
      case 'queued': return 'Pendente';
      case 'pending': return 'Pendente';
      case 'accepted': return 'Aceito';
      case 'preparing': return 'Preparando';
      case 'ready': return 'Pronto';
      case 'delivered': return 'Entregue';
      case 'canceled': return 'Cancelado';
      default: return (status || '');
    }
  };

  const totalCouvertMesa = conta?.couvertCentavos ?? 0;
  const subtotalMesa = conta?.subtotalCentavos ?? conta?.totalMesaCentavos ?? 0;
  const isBalcao = mesaSlug.toUpperCase() === 'BALCAO';
  const taxaServicoMesa = (taxaAtiva && !isBalcao) ? Math.max(0, Math.round((subtotalMesa * taxaPercentual) / 100)) : 0;
  const devidoSemTaxaMesa = (conta?.devidoCentavos || 0) + (conta?.devidoCouvertCentavos || 0);
  const devidoTotalMesa = isBalcao ? devidoSemTaxaMesa : (conta?.devidoTotalCentavos ?? conta?.devidoCentavos ?? 0);

  const getPessoaBaseDevido = (pessoa: Pessoa) => pessoa.devidoCentavos || 0;
  const getPessoaCouvertDevido = (pessoa: Pessoa) => pessoa.devidoCouvertCentavos || 0;
  const getPessoaDevidoTotal = (pessoa: Pessoa) => getPessoaBaseDevido(pessoa) + getPessoaCouvertDevido(pessoa);

  // Total selecionado (centavos)
  const totalBaseSelecionadoCentavos = (conta?.pessoas || [])
    .filter((p) => selectedIds.includes(p.sessaoConvidadoId))
    .reduce((sum, p) => sum + getPessoaBaseDevido(p), 0);
  const totalCouvertSelecionadoCentavos = (conta?.pessoas || [])
    .filter((p) => selectedIds.includes(p.sessaoConvidadoId))
    .reduce((sum, p) => sum + (p.devidoCouvertCentavos || 0), 0);
  const totalSelecionadoCentavos = totalBaseSelecionadoCentavos + totalCouvertSelecionadoCentavos;
  const taxaSelecionadaCentavos = calcularTaxaServico(totalBaseSelecionadoCentavos, incluirTaxaServico);
  const totalComTaxaSelecionada = totalBaseSelecionadoCentavos + totalCouvertSelecionadoCentavos + taxaSelecionadaCentavos;
  const selectedCount = selectedIds.length;
  const totalPorPessoaCentavos = selectedCount > 0 ? Math.round(totalComTaxaSelecionada / selectedCount) : 0;

  // Parser em centavos: usuário digita valores em centavos (ex.: 5400 => R$ 54,00)
  const parseMoneyToCentavos = (v: string): number => {
    if (!v) return 0;
    const digits = v.replace(/\D/g, '');
    if (!digits) return 0;
    return Math.max(0, parseInt(digits, 10));
  };
  const pixCent = parseMoneyToCentavos(pixValor);
  const cardCreditoCent = parseMoneyToCentavos(cardCreditoValor);
  const cardDebitoCent = parseMoneyToCentavos(cardDebitoValor);
  const cardCent = cardCreditoCent + cardDebitoCent;
  const voucherCent = parseMoneyToCentavos(voucherValor);
  const cashCent = parseMoneyToCentavos(cashValor);
  const somaInformadaCentavos = pixCent + cardCent + cashCent + voucherCent;
  const excede = somaInformadaCentavos > totalComTaxaSelecionada;
  const falta = somaInformadaCentavos < totalComTaxaSelecionada;

  // Restantes por campo
  const remainingForPix = Math.max(0, totalComTaxaSelecionada - (cardCent + cashCent + voucherCent));
  const remainingForCardCredito = Math.max(0, totalComTaxaSelecionada - (pixCent + cashCent + voucherCent + cardDebitoCent));
  const remainingForCardDebito = Math.max(0, totalComTaxaSelecionada - (pixCent + cashCent + voucherCent + cardCreditoCent));
  const remainingForVoucher = Math.max(0, totalComTaxaSelecionada - (pixCent + cardCent + cashCent));
  const remainingForCash = Math.max(0, totalComTaxaSelecionada - (pixCent + cardCent + voucherCent));
  const disabledPix = remainingForPix === 0 && pixCent === 0;
  const disabledCardCredito = remainingForCardCredito === 0 && cardCreditoCent === 0;
  const disabledCardDebito = remainingForCardDebito === 0 && cardDebitoCent === 0;
  const disabledVoucher = remainingForVoucher === 0 && voucherCent === 0;
  const disabledCash = remainingForCash === 0 && cashCent === 0;

  const fillRemaining = (target: 'pix'|'cardCredito'|'cardDebito'|'voucher'|'cash') => {
    if (target === 'pix') setPixValor(String(remainingForPix));
    if (target === 'cardCredito') setCardCreditoValor(String(remainingForCardCredito));
    if (target === 'cardDebito') setCardDebitoValor(String(remainingForCardDebito));
    if (target === 'voucher') setVoucherValor(String(remainingForVoucher));
    if (target === 'cash') setCashValor(String(remainingForCash));
  };

  const togglePessoa = (id: number) => {
    const pessoa = (conta?.pessoas || []).find(p => p.sessaoConvidadoId === id);
    const due = pessoa ? getPessoaDevidoTotal(pessoa) : 0;
    if (due <= 0) return; // não selecionar pessoas sem devido
    setSelectedIds((prev) => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);
  };

  const selecionarTodos = () => {
    if (!conta?.pessoas) return;
    setSelectedIds(conta.pessoas.filter(p => getPessoaDevidoTotal(p) > 0).map(p => p.sessaoConvidadoId));
  };

  const limparSelecao = () => setSelectedIds([]);

  const handleSubmitSimples = async () => {
    if (!sessaoMesaId || !conta) return;
    const pessoas = (conta.pessoas || []).filter(p => selectedIds.includes(p.sessaoConvidadoId));
    const totalBase = pessoas.reduce((sum, p) => sum + getPessoaBaseDevido(p), 0);
    const totalCouvert = pessoas.reduce((sum, p) => sum + getPessoaCouvertDevido(p), 0);
    const taxaTotal = calcularTaxaServico(totalBase, incluirTaxaServico);
    const totalCobrar = totalBase + totalCouvert + taxaTotal;
    if (somaInformadaCentavos !== totalCobrar) {
      showNotif('A soma informada precisa igualar o total a cobrar (base + taxa + couvert).');
      return;
    }
    const beneficiarioId = selectedIds.length === 1 ? pessoas[0]?.sessaoConvidadoId ?? null : null;
    const devidoSemTaxaSelecionado = (conta?.pessoas || [])
      .filter((p) => selectedIds.includes(p.sessaoConvidadoId))
      .reduce((sum, p) => sum + getPessoaBaseDevido(p) + getPessoaCouvertDevido(p), 0);
    const multiBeneficiario = selectedIds.length > 1;
    // construir pagamentos por método (distribuindo base/taxa/couvert conforme o valor informado)
    const pagamentos: any[] = [];
    const metodos = [
      { metodo: 'pix' as const, valor: pixCent, cartaoTipo: undefined as 'credito'|'debito'|undefined },
      { metodo: 'card' as const, valor: cardCreditoCent, cartaoTipo: 'credito' as const },
      { metodo: 'card' as const, valor: cardDebitoCent, cartaoTipo: 'debito' as const },
      { metodo: 'cash' as const, valor: cashCent, cartaoTipo: undefined as 'credito'|'debito'|undefined },
      { metodo: 'voucher' as const, valor: voucherCent, cartaoTipo: undefined as 'credito'|'debito'|undefined },
    ].filter(m => m.valor > 0);

    let restanteBase = totalBase;
    let restanteTaxa = taxaTotal;
    let restanteCouvert = totalCouvert;
    let restanteTotal = totalCobrar;

    const metodosDetalhados = metodos.map((m, idx) => {
      const isLast = idx === metodos.length - 1;
      if (isLast) {
        return { ...m, base: restanteBase, taxa: restanteTaxa, couvert: restanteCouvert };
      }
      const ratio = restanteTotal > 0 ? m.valor / restanteTotal : 0;
      const couvert = Math.round(restanteCouvert * ratio);
      const totalSemCouvert = Math.max(0, m.valor - couvert);
      const { base, taxa } = splitBaseETaxa(totalSemCouvert);
      restanteBase = Math.max(0, restanteBase - base);
      restanteTaxa = Math.max(0, restanteTaxa - taxa);
      restanteCouvert = Math.max(0, restanteCouvert - couvert);
      restanteTotal = Math.max(0, restanteTotal - m.valor);
      return { ...m, base, taxa, couvert };
    });

    const totalBaseRef = pessoas.reduce((sum, p) => sum + getPessoaBaseDevido(p), 0);

    const buildAlocacoesBase = (baseValor: number) => {
      if (baseValor <= 0 || totalBaseRef <= 0) return [];
      const allocs: { sessaoConvidadoId: number; valorCentavos: number }[] = [];
      let restante = baseValor;
      pessoas.forEach((p, idx) => {
        const quota = Math.max(0, getPessoaBaseDevido(p));
        const val = idx === pessoas.length - 1
          ? restante
          : Math.floor((baseValor * quota) / totalBaseRef);
        restante -= val;
        if (val > 0) {
          allocs.push({ sessaoConvidadoId: p.sessaoConvidadoId, valorCentavos: val });
        }
      });
      return allocs;
    };

    metodosDetalhados.forEach((m) => {
      if (beneficiarioId && !multiBeneficiario) {
        // caso 1: só uma pessoa selecionada
        pagamentos.push({
          sessaoConvidadoId: beneficiarioId,
          paganteId: null,
          valorCentavos: m.base,
          valorTaxaServicoCentavos: m.taxa,
          valorCouvertCentavos: m.couvert,
          incluiTaxaServico: incluirTaxaServico,
          metodo: m.metodo,
          cartaoTipo: m.cartaoTipo
        });
      } else {
        // caso 2: múltiplas pessoas — pagamento de mesa com alocações por base
        pagamentos.push({
          sessaoConvidadoId: null,
          paganteId: null,
          valorCentavos: m.base,
          valorTaxaServicoCentavos: m.taxa,
          valorCouvertCentavos: m.couvert,
          incluiTaxaServico: incluirTaxaServico,
          metodo: m.metodo,
          cartaoTipo: m.cartaoTipo,
          alocacoes: buildAlocacoesBase(m.base)
        });
      }
    });
    if (pagamentos.length === 0) {
      showNotif('Informe ao menos um valor em método de pagamento.');
      return;
    }
    if (totalBase + totalCouvert <= 0 || pagamentos.length === 0) {
      showNotif('Nenhum valor devido nas pessoas selecionadas');
      return;
    }
    setSubmitting(true);
    try {
      const response = await axios.post(
        `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/pagamentos/multiplo`,
        { pagamentos }
      );
      await loadData(); // Reload data to get updated `conta` object
      let mesaFoiFechada = false;

      // Tentar fechar a mesa se a opção estiver ativa e o pagamento zerou o devido
      if (closeAfterPay && conta && devidoSemTaxaSelecionado === devidoSemTaxaMesa && devidoSemTaxaSelecionado > 0) {
        try {
          await axios.post(`${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/fechar`);
          mesaFoiFechada = true;
          setRedirectAfterNotif('/admin/mesas'); // Definir redirecionamento aqui
        } catch (e: any) {
          console.error('Pagamento ok, mas falhou ao fechar a mesa:', e);
          const msg = e?.response?.data?.error?.message || 'Erro ao fechar mesa';
          // showNotif(msg); // Não mostrar notif aqui, para não conflitar com a do preview
        }
      }

      // Limpar os campos de pagamento (para o modo simples)
      setPixValor('');
      setCardCreditoValor('');
      setCardDebitoValor('');
      setVoucherValor('');
      setCashValor('');

      const pagamentoIds: number[] = response.data?.pagamentoIds || [];
      const lastPagamentoId = pagamentoIds.length > 0 ? pagamentoIds[pagamentoIds.length - 1] : null;

      // Sempre tentar carregar o preview, e o preview lidará com a notificação
      // e redirecionamento, se a mesa foi fechada.
      const previewMostrado = lastPagamentoId ? await loadPreviewData(lastPagamentoId, mesaFoiFechada) : false;

      if (!previewMostrado) {
        // Se o preview NÃO foi mostrado (ex: não há NFCE para esse pagamento ou loadPreviewData falhou)
        // então mostramos uma notificação padrão e, se a mesa foi fechada, redirecionamos.
        if (mesaFoiFechada) {
          showNotif('Pagamento registrado e mesa fechada com sucesso!', '/admin/mesas');
        } else {
          showNotif('Pagamento(s) registrado(s) com sucesso!');
        }
      }
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message || 'Erro ao registrar pagamento';
      showNotif(msg);
    } finally {
      setSubmitting(false);
    }
  };

  // Tabela unificada de itens consumidos (apenas pessoas selecionadas)
  const consumoRows = useMemo(() => {
    const list: Array<{
      pessoaId: number;
      pessoaNome: string;
      pedidoId: number;
      pedidoCriadoEm: string;
      itemPedidoId: number;
      produtoNome: string;
      quantidade: number;
      precoUnitCentavos: number;
      totalCentavos: number;
      outstandingCentavos: number;
      status: string;
      observacoes?: string | null;
    }> = [];
    // Mapa com pago por pessoa (para ocultar itens já quitados)
    const pagoPorPessoa: Record<number, number> = {};
    for (const p of (conta?.pessoas || [])) {
      pagoPorPessoa[p.sessaoConvidadoId] = p.pagoCentavos || 0;
    }
    const pessoas = (conta?.pessoas || []).filter(p => selectedIds.includes(p.sessaoConvidadoId));
    for (const p of pessoas) {
      const itens = (itemsByGuest[p.sessaoConvidadoId] || [])
        .slice()
        .sort((a,b) => new Date(a.pedidoCriadoEm).getTime() - new Date(b.pedidoCriadoEm).getTime() || a.itemPedidoId - b.itemPedidoId);
      let pagoRestante = pagoPorPessoa[p.sessaoConvidadoId] || 0;
      for (const it of itens) {
        const lineTotal = (it.precoUnitCentavos || 0) * (it.quantidade || 0);
        let outstanding = lineTotal;
        if (pagoRestante > 0) {
          const abatimento = Math.min(pagoRestante, lineTotal);
          outstanding = lineTotal - abatimento;
          pagoRestante -= abatimento;
        }
        // Se mostrando somente em aberto e o item foi totalmente quitado, pular
        if (showOnlyOpenItems && outstanding <= 0) continue;
        list.push({
          pessoaId: p.sessaoConvidadoId,
          pessoaNome: p.nome,
          pedidoId: it.pedidoId,
          pedidoCriadoEm: it.pedidoCriadoEm,
          itemPedidoId: it.itemPedidoId,
          produtoNome: it.produtoNome,
          quantidade: it.quantidade,
          precoUnitCentavos: it.precoUnitCentavos,
          totalCentavos: it.precoUnitCentavos * it.quantidade,
          outstandingCentavos: outstanding,
          status: it.status,
          observacoes: it.observacoes,
        });
      }
    }
    list.sort((a,b) => {
      const pn = (a.pessoaNome || '').localeCompare(b.pessoaNome || '', 'pt-BR');
      if (pn !== 0) return pn;
      const ta = new Date(a.pedidoCriadoEm).getTime();
      const tb = new Date(b.pedidoCriadoEm).getTime();
      if (ta !== tb) return ta - tb;
      if (a.pedidoId !== b.pedidoId) return a.pedidoId - b.pedidoId;
      return a.itemPedidoId - b.itemPedidoId;
    });
    return list;
  }, [JSON.stringify(selectedIds), JSON.stringify(itemsByGuest), JSON.stringify(conta?.pessoas || [])]);

  // Habilita checkbox de fechar mesa quando o pagamento selecionado zera o devido (consumo + couvert, sem taxa)
  useEffect(() => {
    const devidoSemTaxaSelecionado = devidoSemTaxaMesa;
    if (conta && totalSelecionadoCentavos === devidoSemTaxaSelecionado && totalSelecionadoCentavos > 0) {
      setCloseAfterPay(true);
    } else {
      setCloseAfterPay(false);
    }
  }, [totalSelecionadoCentavos, devidoSemTaxaMesa, conta]);

  // Atalhos: Shift + P alterna painel; Shift + 1..5 aciona varinhas e foca CTA
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      // não interferir com modais/diálogos abertos
      if (showNfcePreviewModal || showDanfceModal || showNotification || showWhatsappDialog || showItemsModal || showHistoricoModal || showMensalistaDialog) {
        return;
      }
      const tag = (e.target as HTMLElement)?.tagName?.toLowerCase();
      const isFormField = tag === 'input' || tag === 'textarea' || (e.target as HTMLElement)?.isContentEditable;

      if (e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey) {
        // Toggle painel
        if ((e.key === 'p' || e.key === 'P') && !isFormField) {
          e.preventDefault();
          setPaymentsExpanded((prev) => !prev);
          return;
        }

        // Varinhas rápidas: 1=PIX, 2=Crédito, 3=Débito, 4=Voucher, 5=Dinheiro
        const mapFill: Record<string, () => void> = {
          Digit1: () => fillRemaining('pix'),
          Digit2: () => fillRemaining('cardCredito'),
          Digit3: () => fillRemaining('cardDebito'),
          Digit4: () => fillRemaining('voucher'),
          Digit5: () => fillRemaining('cash'),
        };
        const action = mapFill[e.code];
        if (action) {
          e.preventDefault(); // evita digitar no input e sobrescrever
          setPaymentsExpanded(true);
          action();
          setTimeout(() => {
            registerButtonRef.current?.focus();
          }, 0);
        }
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [
    showNfcePreviewModal,
    showDanfceModal,
    showNotification,
    showWhatsappDialog,
    showItemsModal,
    showHistoricoModal,
    showMensalistaDialog,
    remainingForPix,
    remainingForCardCredito,
    remainingForCardDebito,
    remainingForVoucher,
    remainingForCash,
  ]);

  return (
    <div className="min-h-screen bg-soft-white text-forest-dark">
      <div className="w-full max-w-[1400px] mx-auto px-3 py-4 md:px-6 lg:px-8">
        <div className="mb-2">
          <button
            type="button"
            onClick={() => navigate('/admin/mesas')}
            className="inline-flex items-center gap-1 text-xs text-forest-dark/70 hover:text-forest-dark"
          >
            <ArrowLeft className="h-3 w-3" />
            Voltar para mesas
          </button>
        </div>

        {loading ? (
          <div className="text-center py-16 text-forest-dark/70">
            Carregando...
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-11 gap-4 lg:gap-5 xl:gap-6">
            {isMobile && (
              <div className="col-span-1 md:hidden">
                <div className="rounded-xl shadow-xl border border-forest-green/20 bg-white px-3 py-2">
                  <div className="font-display text-lg tracking-wide uppercase text-forest-green">Mesa {mesaSlug || '—'}</div>
                  <div className="mt-1 flex items-center justify-between text-[12px] gap-2 whitespace-nowrap text-forest-dark/80">
                    <span>Total: <span className="font-medium text-forest-dark">{formatCurrency(conta?.totalMesaCentavos || 0)}</span></span>
                    <span>Pago: <span className="font-medium text-forest-dark">{formatCurrency(conta?.pagoCentavos || 0)}</span></span>
                    <span>Devido: <span className="font-semibold text-forest-green">{formatCurrency(conta?.devidoCentavos || 0)}</span></span>
                  </div>
                  <div className="mt-2 flex items-center gap-2">
                    <button type="button" onClick={() => setShowItemsModal(true)} className="px-2.5 py-1 rounded border border-forest-green/30 text-forest-dark text-xs hover:bg-forest-green/10">Itens consumidos</button>
                    {historico.length > 0 && (
                      <button type="button" onClick={() => setShowHistoricoModal(true)} className="px-2.5 py-1 rounded border border-forest-green/30 text-forest-dark text-xs hover:bg-forest-green/10">Histórico</button>
                    )}
                  </div>
                </div>
              </div>
            )}
            {/* Coluna Esquerda: Contexto da Mesa em um único card */}
            <div className="lg:col-span-4 flex flex-col md:h-[calc(100vh-140px)] md:min-h-[600px]">
              <div className="bg-white rounded-2xl shadow-xl border border-[#8B7355]/20 p-4 md:p-5 flex-1 hidden md:flex flex-col gap-4">
                {/* Cabeçalho da mesa */}
                <div className="flex items-start justify-between">
                  <div>
                    <div className="text-[11px] tracking-widest uppercase text-[#8B7355] mb-1">Mesa</div>
                    <div className="text-2xl font-display text-[#2A1F1B] leading-tight">{mesaSlug || '—'}</div>
                    <div className="text-sm text-[#2A1F1B]/70 mt-1">
                      {conta?.pessoas?.length || 0} pessoa(s)
                    </div>
                  </div>
                  <div className="text-xs px-2 py-1 rounded-full bg-[#D7B899]/30 border border-[#D7B899]/60 text-[#2A1F1B] font-semibold">
                    Mesa aberta
                  </div>
                </div>

                {/* Resumo financeiro */}
                <div className="rounded-xl border border-[#8B7355]/15 bg-[#8B7355]/5 px-3 py-3 space-y-2.5">
                  <div className="flex justify-between items-center text-sm">
                    <span className="text-[#2A1F1B]/70">Subtotal consumo</span>
                    <span className="font-semibold text-[#8B7355] whitespace-nowrap">
                      {formatCurrency(conta?.subtotalCentavos || conta?.totalMesaCentavos || 0)}
                    </span>
                  </div>
                  <div className="flex justify-between items-center text-sm">
                    <span className="text-[#2A1F1B]/70">Couvert artístico</span>
                    <span className="font-semibold text-[#8B7355]">
                      {formatCurrency(totalCouvertMesa)}
                    </span>
                  </div>
                  {taxaAtiva && (
                    <div className="flex justify-between items-center text-sm">
                      <span className="text-[#2A1F1B]/70">Taxa de serviço ({taxaPercentual}%)</span>
                      <span className="font-semibold text-[#8B7355]">
                        {formatCurrency(taxaServicoMesa)}
                      </span>
                    </div>
                  )}
                  <div className="flex justify-between items-center text-sm">
                    <span className="text-[#2A1F1B]/70">Pago</span>
                    <span className="font-semibold text-[#8B7355]">
                      {formatCurrency(conta?.pagoCentavos || 0)}
                    </span>
                  </div>
                  <div className="border-t border-[#8B7355]/15 pt-2 flex justify-between items-center">
                    <span className="text-[#2A1F1B]/70">Devido</span>
                    <span className="font-bold text-xl text-[#8B7355]">
                      {formatCurrency(devidoTotalMesa)}
                    </span>
                  </div>
                </div>

                {/* Pessoas */}
                <div className="flex flex-col gap-2 overflow-hidden flex-1 min-h-[200px]">
                  <div className="flex items-center justify-between">
                    <h3 className="font-display text-lg text-[#2A1F1B]">Pessoas na mesa</h3>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={selecionarTodos}
                        className="text-[11px] px-3 py-1 rounded bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 shadow-sm"
                      >
                        Selecionar todos
                      </button>
                      <button
                        type="button"
                        onClick={limparSelecao}
                        className="text-[11px] px-3 py-1 rounded bg-white text-[#2A1F1B] border border-[#D7B899]/30 hover:bg-[#D7B899]/10 shadow-sm"
                      >
                        Limpar
                      </button>
                    </div>
                  </div>
                  <div className="space-y-2 overflow-y-auto pr-1">
                    {conta?.pessoas.map((pessoa) => {
                      const selected = selectedIds.includes(pessoa.sessaoConvidadoId);
                      const disabled = (pessoa.devidoCentavos || 0) === 0;
                      return (
                        <button
                          type="button"
                          key={pessoa.sessaoConvidadoId}
                          onClick={() => togglePessoa(pessoa.sessaoConvidadoId)}
                          className={`w-full rounded-lg px-3 py-2.5 flex justify-between items-center border ${selected ? 'bg-[#D7B899]/20 border-[#D7B899]/50 text-[#2A1F1B]' : 'bg-white/5 border-[#8B7355]/20 text-[#2A1F1B]'} ${disabled ? 'opacity-60' : ''}`}
                        >
                          <span className="font-medium text-[#2A1F1B] text-left text-sm">{pessoa.nome}</span>
                          <span className="text-[#2A1F1B]/70 text-sm whitespace-nowrap">{formatCurrency(pessoa.devidoCentavos)}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>
            </div>

            {/* Coluna Direita: Itens Consumidos acima de Registrar Pagamentos */}
            <div className="lg:col-span-7 flex flex-col md:h-[calc(100vh-140px)] md:min-h-[600px]">
                {/* Itens Consumidos (tabela única e compacta) */}
                <div className="bg-white/5 backdrop-blur-sm rounded-2xl shadow-xl border border-[#8B7355]/20 p-6 flex-1 min-h-0 hidden md:flex flex-col"> {/* cafe-com-leite */}
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-display text-xl text-[#2A1F1B]">Itens Consumidos</h3> {/* cafe-dark-roast */}
                    <div className="flex items-center gap-2 text-xs">
                      <button
                        type="button"
                        onClick={() => setShowOnlyOpenItems(true)}
                        className={`px-2 py-1 rounded border ${showOnlyOpenItems ? 'bg-[#D7B899]/20 border-[#D7B899]/40 text-[#2A1F1B]' : 'bg-transparent border-[#8B7355]/30 text-[#2A1F1B]/80'} hover:bg-[#8B7355]/10`} /* cafe-latte-suave and cafe-com-leite */
                      >
                        Em aberto
                      </button>
                      <button
                        type="button"
                        onClick={() => setShowOnlyOpenItems(false)}
                        className={`px-2 py-1 rounded border ${!showOnlyOpenItems ? 'bg-[#D7B899]/20 border-[#D7B899]/40 text-[#2A1F1B]' : 'bg-transparent border-[#8B7355]/30 text-[#2A1F1B]/80'} hover:bg-[#8B7355]/10`} /* cafe-latte-suave and cafe-com-leite */
                      >
                        Todos
                      </button>
                    </div>
                  </div>
                  {consumoRows.length === 0 ? (
                    <div className="text-[#2A1F1B]/70">Sem itens para exibir.</div>
                  ) : (
                    <div className="w-full flex-1 overflow-y-auto min-h-0">
                        <table className="w-full text-[13px] leading-5">
                        <thead className="sticky top-0 bg-white">
                          <tr className="text-[#2A1F1B] border-b border-[#8B7355]/20">
                            {hasMultiplePessoas && (
                              <th className="text-left font-semibold uppercase tracking-wider text-[11px] py-2 px-3">Pessoa</th>
                            )}
                            <th className="text-left font-semibold uppercase tracking-wider text-[11px] py-2 px-3 w-32">Pedido</th>
                            <th className="text-left font-semibold uppercase tracking-wider text-[11px] py-2 px-3">Item</th>
                            <th className="text-right font-semibold uppercase tracking-wider text-[11px] py-2 px-3 w-14">Qtd</th>
                            <th className="text-right font-semibold uppercase tracking-wider text-[11px] py-2 px-3 w-24">Unitário</th>
                            <th className="text-right font-semibold uppercase tracking-wider text-[11px] py-2 px-3 w-24">Total</th>
                          </tr>
                        </thead>
                        <tbody>
                          {consumoRows.map((r) => {
                            const isCanceled = (r.status || '').toLowerCase() === 'canceled';
                            const pedidoLabel = `#${r.pedidoId}`;
                            const hora = new Date(r.pedidoCriadoEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
                            return (
                              <tr key={r.itemPedidoId} className={`border-b border-[#D7B899]/15 odd:bg-[#8B7355]/5 hover:bg-[#D7B899]/5 transition-colors ${isCanceled ? 'opacity-80' : ''}`}>
                                {hasMultiplePessoas && (
                                  <td className="py-2 px-3 align-top text-[#2A1F1B] font-medium">{r.pessoaNome}</td>
                                )}
                                <td className="py-2 px-3 align-top text-[#2A1F1B]/80">{pedidoLabel} • {hora}</td>
                                <td className="py-2 px-3 align-top">
                                  <div className={`text-[#2A1F1B] ${isCanceled ? 'line-through' : 'font-medium'}`}>{r.produtoNome}</div>
                                  {isCanceled && (
                                    <span className="inline-block mt-0.5 text-[10px] px-1.5 py-0.5 rounded border border-red-600/30 text-red-600 bg-red-600/15">Cancelado</span>
                                  )}
                                </td>
                                <td className="py-2 px-3 text-right align-top tabular-nums text-[#2A1F1B]">{r.quantidade}</td>
                                <td className="py-2 px-3 text-right align-top tabular-nums text-[#2A1F1B]">{formatCurrency(r.precoUnitCentavos)}</td>
                                <td className="py-2 px-3 text-right align-top tabular-nums text-[#2A1F1B] font-semibold">
                                  {showOnlyOpenItems && r.outstandingCentavos < r.totalCentavos
                                    ? `${formatCurrency(r.outstandingCentavos)} `
                                    : formatCurrency(r.totalCentavos)}
                                  {showOnlyOpenItems && r.outstandingCentavos < r.totalCentavos && (
                                    <span className="ml-1 text-[10px] text-[#2A1F1B]/70">(parcial)</span>
                                  )}
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>

                {/* Registrar Pagamentos (modo colapsável) */}
                <div className="bg-white/5 backdrop-blur-sm rounded-2xl shadow-xl border border-[#8B7355]/20 p-3 md:p-5 mt-2 md:mt-4 md:static sticky bottom-2 z-10">
                  <div className="flex items-center justify-between mb-3 md:mb-4">
                    <h3 className="font-display text-base md:text-lg text-[#2A1F1B] flex items-center gap-2">
                      <Banknote className="w-5 h-5 text-[#8B7355]" />
                      Pagamento · Em aberto
                    </h3>
                    <div className="flex items-center gap-2 text-xs leading-4 text-[#2A1F1B]/70 whitespace-nowrap">
                      <span>Selecionados: <span className="font-medium text-[#2A1F1B]">{selectedIds.length}</span></span>
                      <button
                        type="button"
                        onClick={() => setPaymentsExpanded((v) => !v)}
                        className="px-2 py-1 rounded border border-[#D7B899]/40 text-[#2A1F1B]/70 hover:bg-[#D7B899]/15"
                      >
                        {paymentsExpanded ? 'Recolher' : 'Abrir'}
                      </button>
                    </div>
                  </div>

                  {!paymentsExpanded && (
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                      <div className="bg-[#2A1F1B]/5 border border-[#8B7355]/20 rounded-xl px-3 py-2.5 sm:px-4 sm:py-3 flex items-center justify-between w-full sm:w-auto sm:min-w-[240px]">
                        <span className="text-sm text-[#2A1F1B]/80">Total a cobrar</span>
                        <span className="text-xl font-bold text-[#8B7355]">{formatCurrency(totalComTaxaSelecionada)}</span>
                      </div>
                      <Button
                        onClick={() => setPaymentsExpanded(true)}
                        className="w-full sm:w-auto bg-[#D7B899] hover:bg-[#D7B899]/90 text-[#2A1F1B] font-semibold shadow-md"
                      >
                        Registrar pagamento
                      </Button>
                    </div>
                  )}

                  {paymentsExpanded && (
                    <>
                      {elegibilidadeMensalista?.possivel && (
                        <div className="mb-6 bg-emerald-50 border border-emerald-200 rounded-xl p-4 flex flex-col sm:flex-row items-center justify-between gap-3">
                          <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-full bg-emerald-100 flex items-center justify-center text-emerald-600">
                              <Building2 className="w-5 h-5" />
                            </div>
                            <div>
                              <h4 className="font-semibold text-emerald-900 text-sm">Cliente Mensalista</h4>
                              <p className="text-xs text-emerald-700">
                                Faturar consumo para <strong>{elegibilidadeMensalista.hostNome}</strong>
                              </p>
                            </div>
                          </div>
                          <Button 
                            onClick={() => setShowMensalistaDialog(true)}
                            className="bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm w-full sm:w-auto"
                            size="sm"
                          >
                            Faturar {formatCurrency(conta?.devidoCentavos || 0)}
                          </Button>
                        </div>
                      )}

                      {/* Barra de Status */}
                      <div className="mb-4 md:mb-6 space-y-1.5">
                         <div className="flex justify-between text-xs font-medium">
                            <span className={excede ? "text-red-600" : "text-[#2A1F1B]/80"}>
                               Informado: {formatCurrency(somaInformadaCentavos)}
                            </span>
                            <span className={falta ? "text-amber-400" : (excede ? "text-red-600" : "text-emerald-400")}>
                               {falta ? `Falta: ${formatCurrency(Math.max(0, totalComTaxaSelecionada - somaInformadaCentavos))}` : (excede ? `Troco: ${formatCurrency(somaInformadaCentavos - totalComTaxaSelecionada)}` : "")}
                            </span>
                         </div>
                         <Progress
                            value={totalComTaxaSelecionada > 0 ? Math.min(100, (somaInformadaCentavos / totalComTaxaSelecionada) * 100) : 0}
                            className={`h-2 ${excede ? "bg-red-600/20 [&>div]:bg-red-600" : (falta ? "bg-amber-400/20 [&>div]:bg-amber-400" : "bg-[#D7B899]/20 [&>div]:bg-[#D7B899]")}`}
                         />
                      </div>

                      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 md:gap-6 items-start">
                        {/* Coluna 1: Formas de pagamento */}
                        <div className="space-y-2.5 md:space-y-3">
                          
                          {/* PIX */}
                          <div className="flex items-center gap-2">
                            <div className="w-7 md:w-8 flex justify-center"><QrCode className="w-4 h-4 md:w-5 md:h-5 text-[#2A1F1B]/60" /></div>
                            <div className="flex-1 relative">
                               <Input
                                  type="text"
                                  inputMode="numeric"
                                  placeholder="PIX (centavos)"
                                  value={pixValor}
                                  onChange={(e)=>setPixValor(e.target.value)}
                                  disabled={disabledPix}
                                  className="h-10 md:h-11 text-sm md:text-base px-3 bg-white/5 border-[#8B7355]/30 text-[#2A1F1B] disabled:opacity-50"
                               />
                               {!disabledPix && (
                                  <button
                                     type="button"
                                     onClick={()=>fillRemaining('pix')}
                                     className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-[#8B7355] hover:bg-[#8B7355]/10 rounded-md transition-colors"
                                     title="Preencher com o restante"
                                  >
                                     <Wand2 className="w-4 h-4" />
                                  </button>
                               )}
                            </div>
                          </div>

                          {/* Cartão Crédito */}
                          <div className="flex items-center gap-2">
                            <div className="w-7 md:w-8 flex justify-center"><CreditCard className="w-4 h-4 md:w-5 md:h-5 text-[#2A1F1B]/60" /></div>
                            <div className="flex-1 relative">
                               <Input
                                  type="text"
                                  inputMode="numeric"
                                  placeholder="Crédito (centavos)"
                                  value={cardCreditoValor}
                                  onChange={(e)=>setCardCreditoValor(e.target.value)}
                                  disabled={disabledCardCredito}
                                  className="h-10 md:h-11 text-sm md:text-base px-3 bg-white/5 border-[#8B7355]/30 text-[#2A1F1B] disabled:opacity-50"
                               />
                               {!disabledCardCredito && (
                                  <button
                                     type="button"
                                     onClick={()=>fillRemaining('cardCredito')}
                                     className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-[#8B7355] hover:bg-[#8B7355]/10 rounded-md transition-colors"
                                     title="Preencher com o restante"
                                  >
                                     <Wand2 className="w-4 h-4" />
                                  </button>
                               )}
                            </div>
                          </div>

                          {/* Cartão Débito */}
                          <div className="flex items-center gap-2">
                            <div className="w-7 md:w-8 flex justify-center"><CreditCard className="w-4 h-4 md:w-5 md:h-5 text-[#2A1F1B]/60" /></div>
                             <div className="flex-1 relative">
                               <Input
                                  type="text"
                                  inputMode="numeric"
                                  placeholder="Débito (centavos)"
                                  value={cardDebitoValor}
                                  onChange={(e)=>setCardDebitoValor(e.target.value)}
                                  disabled={disabledCardDebito}
                                  className="h-10 md:h-11 text-sm md:text-base px-3 bg-white/5 border-[#8B7355]/30 text-[#2A1F1B] disabled:opacity-50"
                               />
                               {!disabledCardDebito && (
                                  <button
                                     type="button"
                                     onClick={()=>fillRemaining('cardDebito')}
                                     className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-[#8B7355] hover:bg-[#8B7355]/10 rounded-md transition-colors"
                                     title="Preencher com o restante"
                                  >
                                     <Wand2 className="w-4 h-4" />
                                  </button>
                               )}
                            </div>
                          </div>

                          {/* Voucher */}
                          <div className="flex items-center gap-2">
                            <div className="w-7 md:w-8 flex justify-center"><Ticket className="w-4 h-4 md:w-5 md:h-5 text-[#2A1F1B]/60" /></div>
                            <div className="flex-1 relative">
                               <Input
                                  type="text"
                                  inputMode="numeric"
                                  placeholder="Voucher (centavos)"
                                  value={voucherValor}
                                  onChange={(e)=>setVoucherValor(e.target.value)}
                                  disabled={disabledVoucher}
                                  className="h-10 md:h-11 text-sm md:text-base px-3 bg-white/5 border-[#8B7355]/30 text-[#2A1F1B] disabled:opacity-50"
                               />
                               {!disabledVoucher && (
                                  <button
                                     type="button"
                                     onClick={()=>fillRemaining('voucher')}
                                     className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-[#8B7355] hover:bg-[#8B7355]/10 rounded-md transition-colors"
                                     title="Preencher com o restante"
                                  >
                                     <Wand2 className="w-4 h-4" />
                                  </button>
                               )}
                            </div>
                          </div>

                          {/* Dinheiro */}
                          <div className="flex items-center gap-2">
                            <div className="w-7 md:w-8 flex justify-center"><Banknote className="w-4 h-4 md:w-5 md:h-5 text-[#2A1F1B]/60" /></div>
                            <div className="flex-1 relative">
                               <Input
                                  type="text"
                                  inputMode="numeric"
                                  placeholder="Dinheiro (centavos)"
                                  value={cashValor}
                                  onChange={(e)=>setCashValor(e.target.value)}
                                  disabled={disabledCash}
                                  className="h-10 md:h-11 text-sm md:text-base px-3 bg-white/5 border-[#8B7355]/30 text-[#2A1F1B] disabled:opacity-50"
                               />
                               {!disabledCash && (
                                  <button
                                     type="button"
                                     onClick={()=>fillRemaining('cash')}
                                     className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-[#8B7355] hover:bg-[#8B7355]/10 rounded-md transition-colors"
                                     title="Preencher com o restante"
                                  >
                                     <Wand2 className="w-4 h-4" />
                                  </button>
                               )}
                            </div>
                          </div>
                        </div>

                        {/* Coluna 2: Total + ação */}
                        <div className="flex flex-col gap-3.5 md:gap-4 h-full">
                          {/* Card de totais estilo Cupom */}
                          <div className="bg-black/20 rounded-xl p-3 md:p-4 border border-[#D7B899]/10 space-y-2.5">
                             <div className="flex items-center justify-between text-xs md:text-sm text-[#2A1F1B]/80">
                                <span>Base (selecionados)</span>
                                <span>{formatCurrency(totalBaseSelecionadoCentavos)}</span>
                             </div>
                             {totalCouvertSelecionadoCentavos > 0 && (
                               <div className="flex items-center justify-between text-xs md:text-sm text-[#2A1F1B]/80">
                                 <span>Couvert (selecionados)</span>
                                 <span>{formatCurrency(totalCouvertSelecionadoCentavos)}</span>
                               </div>
                             )}

                             {taxaAtiva && (
                               <div className="flex items-center justify-between">
                                  <div className="flex items-center gap-2">
                                     <span className="text-xs md:text-sm text-[#2A1F1B]/80">Taxa {taxaPercentual}%</span>
                              <Switch
                                 checked={incluirTaxaServico}
                                 onCheckedChange={setIncluirTaxaServico}
                                 disabled={mesaSlug.toUpperCase() === 'BALCAO'}
                                 className="scale-75 data-[state=checked]:bg-[#D7B899]"
                              />
                                  </div>
                                  <span className="text-xs md:text-sm text-[#2A1F1B]/80">{formatCurrency(calcularTaxaServico(totalBaseSelecionadoCentavos, incluirTaxaServico))}</span>
                               </div>
                             )}

                             <div className="flex items-center justify-between text-xs md:text-sm text-[#2A1F1B]/80">
                                <span>Total por pessoa</span>
                                <span>{formatCurrency(totalPorPessoaCentavos)}</span>
                             </div>

                             <div className="border-t border-dashed border-[#8B7355]/30 my-2"></div>

                             <div className="flex items-center justify-between">
                                <span className="text-sm md:text-base font-medium text-[#2A1F1B]">Total a cobrar</span>
                                <span className="text-xl md:text-2xl font-bold text-[#8B7355]">
                                   {formatCurrency(totalComTaxaSelecionada)}
                                </span>
                             </div>
                          </div>

                          <div className="flex-1 flex flex-col justify-end gap-2.5 md:gap-3">
                             {(conta && totalSelecionadoCentavos === devidoSemTaxaMesa && totalSelecionadoCentavos > 0) && (
                               <div className="flex items-center justify-end gap-2 px-1">
                                  <span className="text-xs text-[#2A1F1B]/70">Fechar mesa após pagamento</span>
                             <Switch
                                 checked={closeAfterPay}
                                 onCheckedChange={setCloseAfterPay}
                                 className="scale-90 data-[state=checked]:bg-[#D7B899]"
                              />
                           </div>
                         )}

                             <Button
                                onClick={handleSubmitSimples}
                                disabled={submitting || loading || totalSelecionadoCentavos === 0 || excede || falta}
                                className={`w-full h-12 text-base font-semibold shadow-lg transition-all
                                   ${submitting ? 'opacity-80' : ''}
                                   ${!falta && !excede ? 'bg-[#D7B899] hover:bg-[#D7B899]/90 text-[#2A1F1B]' : 'bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90'}
                                `}
                                ref={registerButtonRef}
                              >
                                {submitting ? 'Registrando...' : 'Registrar Pagamento'}
                              </Button>
                          </div>
                        </div>
                      </div>
                    </>
                  )}
                </div>
            </div>
          </div>
        )}
      </div>

      {/* Modais móveis */}
      <Dialog open={showItemsModal} onOpenChange={setShowItemsModal}>
        <DialogContent className="bg-white/95 backdrop-blur-md border-forest-green/20 p-0 max-w-[95vw] md:max-w-3xl">
          <div className="flex items-center justify-between px-4 py-3 border-b border-forest-green/20">
            <DialogTitle className="text-forest-dark">Itens Consumidos</DialogTitle>
            <div className="flex items-center gap-2 text-xs">
              <button
                type="button"
                onClick={() => setShowOnlyOpenItems(true)}
                className={`px-2 py-1 rounded border ${showOnlyOpenItems ? 'bg-coral-accent/20 border-coral-accent/40 text-forest-dark' : 'bg-transparent border-forest-green/30 text-forest-dark/70'} hover:bg-forest-green/10`}
              >
                Em aberto
              </button>
              <button
                type="button"
                onClick={() => setShowOnlyOpenItems(false)}
                className={`px-2 py-1 rounded border ${!showOnlyOpenItems ? 'bg-coral-accent/20 border-coral-accent/40 text-forest-dark' : 'bg-transparent border-forest-green/30 text-forest-dark/70'} hover:bg-forest-green/10`}
              >
                Todos
              </button>
            </div>
          </div>
          <div className="p-3 max-h-[70vh] overflow-y-auto">
            {consumoRows.length === 0 ? (
              <div className="text-forest-dark/70">Sem itens para exibir.</div>
            ) : (
              <table className="w-full text-[12px] leading-5 text-forest-dark">
                <thead className="sticky top-0 bg-forest-green/10">
                  <tr className="border-b border-forest-green/20">
                    {hasMultiplePessoas && (
                      <th className="text-left font-semibold uppercase tracking-wider text-[10px] py-2 px-2">Pessoa</th>
                    )}
                    <th className="text-left font-semibold uppercase tracking-wider text-[10px] py-2 px-2 w-24">Pedido</th>
                    <th className="text-left font-semibold uppercase tracking-wider text-[10px] py-2 px-2">Item</th>
                    <th className="text-right font-semibold uppercase tracking-wider text-[10px] py-2 px-2 w-20">QTD</th>
                    <th className="text-right font-semibold uppercase tracking-wider text-[10px] py-2 px-2 w-24">Valor</th>
                    {showOnlyOpenItems && (
                      <th className="text-right font-semibold uppercase tracking-wider text-[10px] py-2 px-2 w-24">Em aberto</th>
                    )}
                    <th className="text-right font-semibold uppercase tracking-wider text-[10px] py-2 px-2 w-24">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {consumoRows.map((r) => (
                    <tr key={`${r.pessoaId}-${r.itemPedidoId}`} className="border-b border-coral-accent/15">
                      {hasMultiplePessoas && (
                        <td className="py-2 px-2 text-forest-dark font-medium">{r.pessoaNome}</td>
                      )}
                      <td className="py-2 px-2 text-forest-dark/80">#{r.pedidoId}</td>
                      <td className="py-2 px-2 text-forest-dark">{r.produtoNome}</td>
                      <td className="py-2 px-2 text-right text-forest-dark">{r.quantidade}</td>
                      <td className="py-2 px-2 text-right text-forest-dark">{formatCurrency(r.totalCentavos)}</td>
                      {showOnlyOpenItems && (
                        <td className="py-2 px-2 text-right text-forest-dark">{formatCurrency(r.outstandingCentavos)}</td>
                      )}
                      <td className="py-2 px-2 text-right">
                        <span className={`inline-block px-2 py-0.5 rounded text-[10px] ${badgeClassLocal(r.status)}`}>{statusLabelLocal(r.status)}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={showHistoricoModal} onOpenChange={setShowHistoricoModal}>
        <DialogContent className="bg-white/95 backdrop-blur-md border-forest-green/20 max-w-[95vw] md:max-w-xl">
          <DialogHeader>
            <DialogTitle className="text-forest-dark">Histórico de Pagamentos</DialogTitle>
            <DialogDescription className="text-forest-dark/70">Pagamentos registrados nesta mesa</DialogDescription>
          </DialogHeader>
          <div className="space-y-2 max-h-[60vh] overflow-y-auto pr-1">
            {historico.map((hist) => (
              <div key={hist.id} className="rounded border border-forest-green/20 p-2 bg-white/60">
                <div className="text-forest-dark text-sm">
                  <span className="font-medium">{metodosLabel[hist.metodo as 'pix'|'card'|'cash']}</span> — {formatCurrency(hist.valorCentavos)}
                </div>
                <div className="text-[11px] text-forest-dark/70">
                  Base: {formatCurrency(hist.valorBaseCentavos || hist.valorCentavos)} {hist.incluiTaxaServico ? ` • Taxa: ${formatCurrency(hist.valorTaxaServicoCentavos || 0)}` : ''}
                </div>
                <div className="text-forest-dark/80 text-xs">
                  {hist.pagante && <>{hist.pagante} → </>}
                  {hist.beneficiario}
                </div>
                <div className="text-forest-dark/60 text-[11px] mt-1">{formatDateTime(hist.criadoEm)}</div>
              </div>
            ))}
          </div>
        </DialogContent>
      </Dialog>

      <Dialog
        open={showNfcePreviewModal}
        onOpenChange={(open) => {
          setShowNfcePreviewModal(open);
          if (!open) {
            setNfcePreview(null);
          }
        }}
      >
        <DialogContent className="bg-white/95 backdrop-blur-md border-[#8B7355]/20 max-w-[95vw] md:max-w-2xl">
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Prévia da NFC-e</DialogTitle>
            <DialogDescription className="text-[#2A1F1B]/70">
              Confira os itens antes de gerar a nota fiscal
            </DialogDescription>
          </DialogHeader>
          {loadingNfcePreview ? (
            <div className="py-6 text-center text-[#2A1F1B]/70">Carregando prévia...</div>
          ) : nfcePreview ? (
            <div className="space-y-4">
              <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                <div>
                  <div className="text-sm text-[#2A1F1B]/70">Pagamento #{nfcePreview.pagamentoId}</div>
                  <div className="text-2xl font-semibold text-[#2A1F1B]">
                    {formatCurrencyFromFloat(nfcePreview.valorTotal || 0)}
                  </div>
                </div>
                <div className="text-right text-sm text-[#2A1F1B]/80 space-y-1">
                  <div>Status da venda: <span className="font-semibold text-[#2A1F1B]">{nfcePreview.statusVenda || '-'}</span></div>
                  <div>Status NFC-e: <span className="font-semibold text-[#2A1F1B]">{nfcePreview.statusNfe || 'Não emitida'}</span></div>
                </div>
              </div>
              <div>
                <h4 className="text-sm font-semibold text-[#2A1F1B] mb-2">Comprovante Não Fiscal</h4>
                <div className="h-[400px] w-full border border-[#8B7355]/30 rounded bg-gray-100 overflow-hidden">
                  {comprovantePdfUrl ? (
                    <iframe
                      src={comprovantePdfUrl}
                      className="w-full h-full"
                      title="Comprovante Não Fiscal"
                    />
                  ) : (
                    <div className="flex items-center justify-center h-full text-[#2A1F1B]/50">
                      Carregando comprovante...
                    </div>
                  )}
                </div>
              </div>

            </div>
          ) : (
            <div className="py-6 text-center text-forest-dark/70">
              Não foi possível carregar a prévia da NFC-e.
            </div>
          )}
          <DialogFooter className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            {/* <Button
              variant="outline"
              onClick={() => {
                if (comprovantePdfUrl) {
                  const iframe = document.querySelector('iframe[title="Comprovante Não Fiscal"]') as HTMLIFrameElement;
                  if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.print();
                  }
                }
              }}
              disabled={!comprovantePdfUrl}
              className="mr-auto bg-white border-[#D7B899]/50 text-[#2A1F1B] hover:bg-[#8B7355]/10"
            >
              Imprimir Comprovante
            </Button> */}

            <Button
              onClick={handlePrintComprovanteOnAgent}
              disabled={printingComprovanteAgent || !nfcePreview}
              className="bg-[#2A1F1B] text-white hover:bg-[#4d403b] disabled:opacity-60"
            >
              {printingComprovanteAgent ? 'Enviando...' : 'Imprimir'}
            </Button>

            <Button
              onClick={() => openWhatsappDialog(comprovantePdfUrl)}
              disabled={!comprovantePdfUrl}
              className="bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-60"
            >
              Enviar WhatsApp
            </Button>
            <Button
              onClick={openNfceConfirmDialog}
              disabled={emittingNfce || !nfcePreview}
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 disabled:opacity-60"
            >
              {emittingNfce ? 'Gerando...' : 'Gerar NFC-e'}
            </Button>
            <Button
              variant="ghost"
              onClick={() => { setShowNfcePreviewModal(false); if (redirectAfterNotif) navigate(redirectAfterNotif); setRedirectAfterNotif(null); setNfcePreview(null); }}
              className="text-[#2A1F1B] hover:bg-[#D7B899]/20"
            >
              Fechar
            </Button>            
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={showNfceConfirmDialog} onOpenChange={setShowNfceConfirmDialog}>
        <AlertDialogContent className="bg-white/95 backdrop-blur-md border-[#8B7355]/20">
          <AlertDialogHeader>
            <AlertDialogTitle className="text-[#2A1F1B]">Confirmar emissão da NFC-e</AlertDialogTitle>
            <AlertDialogDescription className="text-[#2A1F1B]/70">
              Deseja gerar a NFC-e para este pagamento?
              {temClienteNfce && (
                <>
                  <br />
                  Cliente vinculado: <strong>{nfcePreview?.clienteNome || 'não informado'}</strong>
                </>
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>

          {!temClienteNfce && (
            <div className="space-y-1">
              <Label htmlFor="nfce-cpf" className="text-[#2A1F1B]">CPF do consumidor (opcional)</Label>
              <Input
                id="nfce-cpf"
                placeholder="000.000.000-00"
                value={nfceCpfInput}
                onChange={(e) => {
                  setNfceCpfInput(formatCpfInput(e.target.value));
                  if (nfceCpfError) setNfceCpfError(null);
                }}
              />
              {(nfceCpfError || nfceCpfInvalido) && (
                <div className="text-xs text-red-600">CPF inválido.</div>
              )}
            </div>
          )}

          <AlertDialogFooter>
            <AlertDialogCancel className="border-[#8B7355]/30 text-[#2A1F1B] hover:bg-[#8B7355]/10">
              Cancelar
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault();
                handleConfirmEmitirNfce();
              }}
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90"
              disabled={emittingNfce || nfceCpfInvalido}
            >
              {emittingNfce ? 'Gerando...' : 'Confirmar'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Modal de Visualização da DANFCE */}
        <Dialog 
          open={showDanfceModal} 
          onOpenChange={(open) => {
            if (!open) {
              setShowDanfceModal(false);
              setDanfcePdfUrl(null);
              setDanfcePaymentId(null);
              if (redirectAfterNotif) {
                navigate(redirectAfterNotif);
                setRedirectAfterNotif(null);
              }
            }
          }}
        >
        <DialogContent className="bg-white/95 backdrop-blur-md border-forest-green/20 max-w-[95vw] md:max-w-2xl">
          <DialogHeader>
            <DialogTitle className="text-forest-dark">NFC-e Emitida com Sucesso</DialogTitle>
            <DialogDescription className="text-forest-dark/70">
              Visualize ou imprima o DANFCE abaixo.
            </DialogDescription>
          </DialogHeader>
          
          <div className="h-[500px] w-full border border-forest-green/30 rounded bg-gray-100 overflow-hidden">
            {danfcePdfUrl ? (
              <iframe 
                src={danfcePdfUrl} 
                className="w-full h-full" 
                title="DANFCE PDF"
              />
            ) : (
              <div className="flex items-center justify-center h-full text-forest-dark/50">
                Carregando PDF...
              </div>
            )}
          </div>

          <DialogFooter className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              variant="outline"
              onClick={handlePrintDanfceOnAgent}
              disabled={!danfcePdfUrl || printingDanfceAgent || !danfcePaymentId}
              className="mr-auto bg-white border-coral-accent/50 text-forest-dark hover:bg-forest-green/10"
            >
              {printingDanfceAgent ? 'Enviando...' : 'Imprimir'}
            </Button>
            
            <Button
              onClick={() => openWhatsappDialog(danfcePdfUrl)}
              disabled={!danfcePdfUrl}
              className="bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-60"
            >
              Enviar WhatsApp
            </Button>

            <Button
              onClick={() => { 
                setShowDanfceModal(false); 
                setDanfcePdfUrl(null);
                setDanfcePaymentId(null);
                if (redirectAfterNotif) {
                  navigate(redirectAfterNotif);
                  setRedirectAfterNotif(null);
                }
              }}
              className="bg-coral-accent text-forest-dark hover:bg-coral-accent/90"
            >
              Fechar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Loading Overlay */}
      {submitting && (
        <div className="fixed inset-0 z-[60] bg-black/60 backdrop-blur-sm flex items-center justify-center">
          <div className="bg-white/95 rounded-xl p-8 shadow-2xl border border-forest-green/30 flex flex-col items-center gap-4 animate-in fade-in zoom-in duration-300">
            <Loader2 className="w-10 h-10 text-forest-green animate-spin" />
            <div className="text-forest-dark font-medium text-lg">
              Processando pagamento...
            </div>
          </div>
        </div>
      )}

      {/* Dialog de Confirmação Mensalista */}
      <AlertDialog open={showMensalistaDialog} onOpenChange={setShowMensalistaDialog}>
        <AlertDialogContent className="bg-white/95 backdrop-blur-md border-forest-green/20">
          <AlertDialogHeader>
            <AlertDialogTitle className="text-forest-dark flex items-center gap-2">
              <Building2 className="w-5 h-5 text-emerald-600" />
              Confirmar Faturamento Mensal
            </AlertDialogTitle>
            <AlertDialogDescription className="text-forest-dark/70">
              Deseja faturar o valor total de <strong>{formatCurrency(conta?.devidoCentavos || 0)}</strong> para o cliente <strong>{elegibilidadeMensalista?.hostNome}</strong>?
              <br/><br/>
              O vencimento será gerado para o primeiro dia útil do próximo mês e a mesa será fechada automaticamente.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="border-[#8B7355]/30 text-[#2A1F1B] hover:bg-[#8B7355]/10">Cancelar</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => { e.preventDefault(); handleFaturarMensalista(); }}
              className="bg-emerald-600 text-white hover:bg-emerald-700"
              disabled={submitting}
            >
              {submitting ? 'Processando...' : 'Confirmar Faturamento'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Dialog de Notificação */}
      <Dialog open={showNotification} onOpenChange={setShowNotification}>
        <DialogContent className="bg-white/95 backdrop-blur-md border-[#8B7355]/20">
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Notificação</DialogTitle>
            <DialogDescription className="text-[#2A1F1B]/70">
              {notificationMessage}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              onClick={() => { setShowNotification(false); const dest = redirectAfterNotif; setRedirectAfterNotif(null); if (dest) navigate(dest); }}
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90"
            >
              OK
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Dialog para input de WhatsApp */}
      <Dialog open={showWhatsappDialog} onOpenChange={setShowWhatsappDialog}>
        <DialogContent className="bg-white/95 backdrop-blur-md border-[#8B7355]/20">
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Confirma comprovante por WhatsApp?</DialogTitle>
            <DialogDescription className="text-[#2A1F1B]/70">
              Informe o número para envio do documento.
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="whatsapp-input" className="text-[#2A1F1B] mb-2 block">Número do WhatsApp</Label>
            <Input
              id="whatsapp-input"
              type="tel"
              placeholder="(99) 99999-9999"
              value={whatsappPhone}
              onChange={(e) => setWhatsappPhone(e.target.value)}
              className="bg-white border-[#8B7355]/30 text-[#2A1F1B]"
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button
              variant="ghost"
              onClick={() => setShowWhatsappDialog(false)}
              className="text-[#2A1F1B] hover:bg-[#8B7355]/10"
            >
              Cancelar
            </Button>
            <Button
              onClick={() => handleEnviarWhatsapp(whatsappTargetPdfUrl)}
              disabled={!whatsappPhone || whatsappPhone.length < 10 || sendingWhatsapp}
              className="bg-emerald-600 text-white hover:bg-emerald-700"
            >
              {sendingWhatsapp ? 'Enviando...' : 'Enviar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
