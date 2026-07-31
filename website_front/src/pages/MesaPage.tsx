import { useEffect, useMemo, useRef, useState, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/use-toast';
import { useNotifications } from '@/hooks/useNotifications';
import { useMesaI18n } from '@/i18n/useMesaI18n';
import { NotificationBadge } from '@/components/NotificationBadge';
import { apiConfig } from '@/config/api';
import axios from '@/lib/axios';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ChevronLeft, ChevronRight, ChevronDown, CreditCard, Languages, X } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { MesaPedidosList } from '@/components/mesa/MesaPedidosList';
import { CartItem } from '@/components/mesa/CartItem';
import { ProductDetailsDialog } from '@/components/mesa/ProductDetailsDialog';
import { ProductCard } from '@/components/mesa/ProductCard';
import { productHasValidPrice, sanitizeCardapio, resolvePrice } from '@/utils/cardapio';
import { badgeClass, statusLabel } from '@/utils/status';
import { Textarea } from '@/components/ui/textarea';
import { UserMenuSheet } from '@/components/mesa/UserMenuSheet';
import { ServiceMode } from '@/types/delivery';

type GuestSession = {
  sessaoConvidadoId: number;
  sessaoMesaId: number;
  guestToken: string;
  nomeExibicao: string;
  host?: boolean;
  mesaSlug?: string;
};

type CardapioCategoria = {
  id: number;
  nome: string;
  produtos: {
    id: number;
    nome: string;
    preco?: number;
    precoVenda?: number;
    preco_promocional?: number;
    precoPromocional?: number;
    imagemPrincipal?: string;
    destaque?: boolean; // Adicionado do JSON de exemplo
    produto_disponivel?: boolean; // Adicionado para a funcionalidade
    horarios_disponiveis?: {
      diaSemana: string;
      horarioInicio: string;
      horarioFim: string;
    }[]; // Adicionado para a funcionalidade
    skus?: { id: number; variacao?: string; preco?: number; precoVenda?: number; precoPromocional?: number; origemDesconto?: string }[];
    origem_desconto?: string;
    origemDesconto?: string;
  }[];
};

type ContaConvidado = {
  sessaoConvidadoId: number;
  nome: string;
  grupoClienteId?: number | null;
  grupoClienteDescricao?: string | null;
  subtotalCentavos: number;
  ajustesCentavos: number;
  pagoCentavos: number;
  devidoCentavos: number;
  itens?: { pedidoId: number; pedidoStatus: string; pedidoCriadoEm: string; itemPedidoId: number; produtoId: number; produtoNome: string; quantidade: number; precoUnitCentavos: number; status: string; observacoes?: string | null }[];
};

interface ProductType {
  id: number;
  nome: string;
  preco?: number;
  precoVenda?: number;
  preco_promocional?: number;
  precoPromocional?: number;
  imagemPrincipal?: string;
  destaque?: boolean;
  produto_disponivel?: boolean;
  horarios_disponiveis?: {
    diaSemana: string;
    horarioInicio: string;
    horarioFim: string;
  }[];
  skus?: { id: number; variacao?: string; preco?: number; precoVenda?: number; precoPromocional?: number; origemDesconto?: string }[];
  midias?: { tipo: 'VIDEO' | 'IMAGEM'; url: string; titulo?: string }[];
  descricao?: string;
  origem_desconto?: string;
  origemDesconto?: string;
}

const normalizeToSearch = (value?: string) =>
  (value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();

export default function MesaPage() {
  const { mesaSlug } = useParams();
  const { t, formatCurrency, locale, setLocale } = useMesaI18n(mesaSlug);
  const navigate = useNavigate();
  const storageKey = useMemo(() => `qrGuest:${mesaSlug}`, [mesaSlug]);
  const findOtherActiveGuestSlug = useCallback((): string | null => {
    if (!mesaSlug) return null;
    try {
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (!key || !key.startsWith('qrGuest:')) continue;
        if (key === storageKey) continue;
        const raw = localStorage.getItem(key);
        if (!raw) continue;
        const data = JSON.parse(raw);
        const slugFromData = data?.mesaSlug || key.replace('qrGuest:', '');
        if (slugFromData && slugFromData !== mesaSlug) {
          return slugFromData;
        }
      }
    } catch (e) {
      console.warn('Erro ao verificar sessões salvas', e);
    }
    return null;
  }, [mesaSlug, storageKey]);
  const [guest, setGuest] = useState<GuestSession | null>(null);
  const [nome, setNome] = useState('');
  const [loading, setLoading] = useState(false);
  const [autoJoinInProgress, setAutoJoinInProgress] = useState(false);
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [showShareDialog, setShowShareDialog] = useState(false);
  const [showLocationDialog, setShowLocationDialog] = useState(false);
  const [locationReference, setLocationReference] = useState('');
  const [updatingLocationReference, setUpdatingLocationReference] = useState(false);
  const [tab, setTab] = useState<'menu' | 'pedidos' | 'conta'>('menu');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | 'all'>('all');
  const [search, setSearch] = useState('');
  const [cardapio, setCardapio] = useState<CardapioCategoria[]>([]);
  const [cart, setCart] = useState<{ produtoId: number; nome: string; preco: number; quantidade: number; observacoes?: string; skuId?: number; origemDesconto?: string }[]>([]);
  const [cartOpen, setCartOpen] = useState(false);
  const [orderConfirmOpen, setOrderConfirmOpen] = useState(false);
  const [sendingOrder, setSendingOrder] = useState(false);
  const [conta, setConta] = useState<ContaConvidado | null>(null);
  const [payUnlocked, setPayUnlocked] = useState(false); // Liberação pelo garçom
  const [checkoutRequested, setCheckoutRequested] = useState(false);
  const [paymentProcessing, setPaymentProcessing] = useState(false);
  const [creatingToken, setCreatingToken] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<'CARD' | 'PIX'>('CARD');
  const [pixStatus, setPixStatus] = useState<string | null>(null);
  const [pixMessage, setPixMessage] = useState<string | null>(null);
  const [pixExpiresAt, setPixExpiresAt] = useState<string | null>(null);
  const [cardToken, setCardToken] = useState<string>("");
  const [cardPaymentMethodId, setCardPaymentMethodId] = useState<string>("visa");
  const [cardInstallments, setCardInstallments] = useState<number>(1);
  const [cardNumber, setCardNumber] = useState("");
  const [cardHolder, setCardHolder] = useState("");
  const [cardExpMonth, setCardExpMonth] = useState("");
  const [cardExpYear, setCardExpYear] = useState("");
  const [cardCvv, setCardCvv] = useState("");
  const [cardDoc, setCardDoc] = useState("");
  const [pagseguroPublicKey, setPagseguroPublicKey] = useState<string>("");
  const [activeGateway, setActiveGateway] = useState<string>("MERCADOPAGO");
  const [cardBrand, setCardBrand] = useState<string | null>(null);
  const [contaMesa, setContaMesa] = useState<{
    totalMesaCentavos: number;
    subtotalCentavos: number;
    taxaServicoCentavos: number;
    taxaServicoPendenteCentavos: number;
    devidoTotalCentavos: number;
    pagoCentavos: number;
    devidoCentavos: number;
    selfCheckoutLiberado?: boolean;
    pessoas: {
      sessaoConvidadoId: number;
      name?: string;
      nome?: string;
      subtotalCentavos: number;
      pagoCentavos: number;
      devidoCentavos: number;
      taxaServicoPendenteCentavos?: number;
      devidoTotalCentavos?: number;
    }[];
  } | null>(null);
  const [contaView, setContaView] = useState<'mine' | 'mesa'>('mine');
  const [pedidosView, setPedidosView] = useState<'mine' | 'mesa'>('mine');
  const [qrPayload, setQrPayload] = useState<string | null>(null);
  const [showPixQr, setShowPixQr] = useState(false);
  const [sessionClosed, setSessionClosed] = useState<boolean>(false);
  const [mesaAssistida, setMesaAssistida] = useState<boolean>(false);
  const [showAssistidaDialog, setShowAssistidaDialog] = useState(false);
  const [blockedMesaSlug, setBlockedMesaSlug] = useState<string | null>(null);
  const [unreadNotifications, setUnreadNotifications] = useState<number>(0);
  const [sessaoAtiva, setSessaoAtiva] = useState<{ totalConvidados: number; mesaRotulo: string } | null>(null);
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const sessionGuestsLabel = useMemo(() => {
    if (!sessaoAtiva) return '';
    const count = sessaoAtiva.totalConvidados;
    const guestLabelKey =
      count === 1
        ? 'mesa.dialog.activeSession.personLabelSingular'
        : 'mesa.dialog.activeSession.personLabelPlural';
    return `${count} ${t(guestLabelKey)}`;
  }, [sessaoAtiva, t]);
  const isActiveSessionDialogOpen = !autoJoinInProgress && showConfirmDialog && !!sessaoAtiva;
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [detailProduct, setDetailProduct] = useState<ProductType | null>(null);
  const [showProductUnavailableDialog, setShowProductUnavailableDialog] = useState(false);
  const [unavailableProductInfo, setUnavailableProductInfo] = useState<{
    nome: string;
    horarios_disponiveis: { diaSemana: string; horarioInicio: string; horarioFim: string }[];
  } | null>(null);
  const [showChamarGarcomDialog, setShowChamarGarcomDialog] = useState(false);
  const [chamandoGarcom, setChamandoGarcom] = useState(false);
  const [observacaoGarcom, setObservacaoGarcom] = useState('');
  const cartHasPromocao = cart.some((item) => item.origemDesconto === 'PROMOCAO');
  const cartHasSocio = cart.some((item) => item.origemDesconto === 'SOCIO');
  const orderTotal = useMemo(() => cart.reduce((sum, c) => sum + c.preco * c.quantidade, 0), [cart]);
  const localeOptions = useMemo(
    () => [
      { value: 'pt-BR' as const, label: 'PT' },
      { value: 'en-US' as const, label: 'EN' },
      { value: 'es-ES' as const, label: 'ES' },
    ],
    []
  );
  const handleLocaleChange = useCallback(
    (value: string) => {
      setLocale(value as typeof locale);
    },
    [setLocale]
  );
  const renderLocaleSelector = useCallback(() => {
    return (
      <Select value={locale} onValueChange={handleLocaleChange}>
        <SelectTrigger
          className="h-7 py-0.5 px-3 text-[11px] font-medium border border-accent/30 bg-white/80 text-mesa-text/70 hover:bg-accent/10 shadow-sm self-start rounded-full w-fit min-w-[60px]"
          aria-label={t('mesa.headers.toggleLocaleAria')}
        >
          <Languages className="h-3.5 w-3.5 mr-1" />
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {localeOptions.map((opt) => (
            <SelectItem key={opt.value} value={opt.value}>
              {opt.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    );
  }, [handleLocaleChange, locale, localeOptions, t]);
  const handleGoogleLoginOnMesa = () => {
    try { document.cookie = "oauth_origin=villa; path=/; max-age=300"; } catch {}
    try { if (mesaSlug) localStorage.setItem('post_login_redirect', `/m/${mesaSlug}`); } catch {}
    window.location.href = `${apiConfig.erpBaseUrl}/api/auth/oauth2/google/ecommerce`;
  };

  const handleGoogleLoginClick = () => {
    setSubmitted(true);
    if ((solicitaCpf && !isCpfValid) || (solicitaTelefone && !isTelefoneValid)) {
      toast({
        title: t('mesa.toast.validation.title'),
        description: t('mesa.toast.validation.pending.description'),
        duration: 4000,
        variant: 'destructive',
      });
      return;
    }
    handleGoogleLoginOnMesa();
  };
  const { toast } = useToast();
  const { permission, requestPermission, showNotification } = useNotifications();
  const handlePaymentSuccess = () => {
    if (!mesaSlug) return;
    try { localStorage.removeItem(storageKey); } catch {}
    if (sseRef.current) sseRef.current.close();
    setGuest(null);
    setSessionClosed(true);
    setTab('menu');
    navigate(`/m/${mesaSlug}/pagamento-sucesso`);
  };
  const callToastError = (description: string) =>
    toast({
      title: t('mesa.toast.genericError.title'),
      description,
      variant: 'destructive',
      duration: 4000,
    });
  const checkoutStatusMessage = payUnlocked
    ? t('mesa.account.checkoutReadyMessage')
    : checkoutRequested
      ? t('mesa.account.closureRequestedMessage')
      : t('mesa.account.closurePromptMessage');
  const checkoutButtonLabel = payUnlocked
    ? t('mesa.account.paymentButton.paymentReady')
    : checkoutRequested
      ? t('mesa.account.paymentButton.closureRequested')
      : t('mesa.account.paymentButton.requestClosure');
  const contaPessoa = useMemo(() => {
    if (!guest || !contaMesa?.pessoas) return null;
    return contaMesa.pessoas.find((p) => p.sessaoConvidadoId === guest.sessaoConvidadoId) || null;
  }, [contaMesa, guest]);

  const formatCardNumber = (value: string) => {
    const digits = value.replace(/\D/g, "").slice(0, 16);
    return digits.match(/.{1,4}/g)?.join(" ") ?? digits;
  };

  const formatExpiry = (value: string) => {
    const digits = value.replace(/\D/g, "").slice(0, 4);
    if (digits.length <= 2) return digits;
    return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  };

  const detectCardBrand = (value: string) => {
    const digits = value.replace(/\D/g, "");
    if (digits.startsWith("4")) return "Visa";
    if (/^5[1-5]/.test(digits)) return "Mastercard";
    if (/^3[47]/.test(digits)) return "Amex";
    if (/^6(?:011|5)/.test(digits)) return "Discover";
    return null;
  };

  const isValidEmail = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed) return false;
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed);
  };

  const resolvePayerEmail = (guestName?: string) => {
    const candidate = (user?.email || "").trim();
    if (isValidEmail(candidate)) return candidate;
    const base =
      (guestName || "cliente")
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/(^-|-$)/g, "") || "cliente";
    const mesaId = guest?.sessaoMesaId ?? "mesa";
    const convidadoId = guest?.sessaoConvidadoId ?? "guest";
    return `${base}-${mesaId}-${convidadoId}@mesa.app`;
  };

  const fillTestCard = useCallback(() => {
    setCardNumber("4013 5406 8274 6260");
    setCardHolder("APRO");
    setCardExpMonth("12");
    setCardExpYear("2027");
    setCardCvv("123");
    setCardDoc("123.456.789-09");
  }, []);

  const loadPagSeguroSdk = useCallback(() => {
    return new Promise<void>((resolve, reject) => {
      // @ts-ignore
      if (window.PagSeguro) {
        resolve();
        return;
      }
      const script = document.createElement("script");
      script.src = "https://assets.pagseguro.com.br/checkout-sdk-js/rc/dist/browser/pagseguro.min.js";
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error(t('mesa.payment.errors.pagseguroLoad')));
      document.body.appendChild(script);
    });
  }, []);

  const ensurePagSeguroKey = useCallback(async () => {
    if (pagseguroPublicKey) return pagseguroPublicKey;
    const response = await axios.get<{ publicKey: string }>('/api/v1/payments/pagseguro/public-key');
    if (response.data?.publicKey) {
      setPagseguroPublicKey(response.data.publicKey);
      return response.data.publicKey;
    }
    return "";
  }, [pagseguroPublicKey]);

  const handleCreateCardToken = useCallback(async () => {
    setCreatingToken(true);
    try {
      if (activeGateway === "PAGSEGURO") {
        const publicKey = await ensurePagSeguroKey();
        await loadPagSeguroSdk();
        const encrypted = (window as any).PagSeguro?.encryptCard({
          publicKey,
          holder: cardHolder,
          number: cardNumber.replace(/\s+/g, ""),
          expMonth: cardExpMonth,
          expYear: cardExpYear,
          securityCode: cardCvv,
        });
            const hasErrors = encrypted?.hasErrors;
            if (hasErrors) {
              const errMsg = Array.isArray(encrypted?.errors) && encrypted.errors.length > 0 ? encrypted.errors[0]?.message : null;
              throw new Error(errMsg || t('mesa.payment.errors.cardEncrypt'));
            }
            const token = encrypted?.encryptedCard || encrypted?.encrypted;
            if (!token || typeof token !== "string") throw new Error(t('mesa.payment.errors.cardEncrypt'));
            setCardToken(token);
            return token;
      } else {
        const payload = {
          cardNumber: cardNumber.replace(/\s+/g, ""),
          cardholderName: cardHolder,
          cardExpirationMonth: cardExpMonth,
          cardExpirationYear: cardExpYear,
          securityCode: cardCvv,
          identificationType: "CPF",
          identificationNumber: cardDoc.replace(/\D/g, ""),
        };
        const response = await axios.post<{ token: string }>("/api/v1/payments/create-token", payload);
          if (response.data?.token) {
            setCardToken(response.data.token);
            return response.data.token;
          } else {
            throw new Error(t('mesa.payment.errors.tokenMissing'));
          }
        }
      } catch (err: any) {
        const msg = err?.response?.data?.error?.message || err?.message || t('mesa.payment.errors.tokenGeneration');
        callToastError(msg);
        setCardToken("");
        return null;
    } finally {
      setCreatingToken(false);
    }
  }, [activeGateway, ensurePagSeguroKey, cardHolder, cardNumber, cardExpMonth, cardExpYear, cardCvv, cardDoc]);
  const handleLocationSubmit = async () => {
    if (!mesaSlug) return;
    const trimmed = locationReference.trim();
    if (!trimmed) {
      toast({
        title: t('mesa.toast.location.prompt.title'),
        description: t('mesa.toast.location.prompt.description'),
        duration: 4000,
        variant: 'destructive',
      });
      return;
    }

    try {
      setUpdatingLocationReference(true);
      await axios.patch(`${apiConfig.erpBaseUrl}/api/mesas/${mesaSlug}/referencia`, {
        referencia: trimmed,
      });
      toast({
        title: t('mesa.toast.location.saved.title'),
        description: t('mesa.toast.location.saved.description'),
        duration: 4000,
      });
      setShowLocationDialog(false);
      setLocationReference(trimmed);
    } catch (error: any) {
      const message = error?.response?.data?.error?.message || 'Não foi possível salvar a referência. Tente novamente.';
      toast({
        title: t('mesa.toast.location.saveError.title'),
        description: t('mesa.toast.location.saveError.description', { errorMessage: message }),
        duration: 4000,
        variant: 'destructive',
      });
    } finally {
      setUpdatingLocationReference(false);
    }
  };
  const qrImageUrl = useMemo(() => (qrPayload ? `https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=${encodeURIComponent(qrPayload)}` : null), [qrPayload]);

  // NEW STATES FOR CONFIGS AND INPUTS
  const [solicitaCpf, setSolicitaCpf] = useState(false);
  const [solicitaTelefone, setSolicitaTelefone] = useState(false);
  const [cpf, setCpf] = useState('');
  const [telefone, setTelefone] = useState('');
  const [submitted, setSubmitted] = useState(false); // Novo estado para controlar submissão
  const introFieldsSuffix = useMemo(() => {
    if (solicitaCpf && solicitaTelefone) return ', CPF e Telefone';
    if (solicitaCpf) return ', CPF';
    if (solicitaTelefone) return ' e Telefone';
    return '';
  }, [solicitaCpf, solicitaTelefone]);
  const introDescription = sessionClosed
    ? t('mesa.form.intro.description.closed')
    : t('mesa.form.intro.description.default', { fields: introFieldsSuffix });
  
  // QR Code para compartilhar mesa
  const shareQrUrl = useMemo(() => {
    if (!mesaSlug) return null;
    const mesaUrl = `${window.location.origin}/m/${mesaSlug}`;
    return `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${encodeURIComponent(mesaUrl)}`;
  }, [mesaSlug]);
  const sseRef = useRef<EventSource | null>(null);
  const guestIdRef = useRef<number | null>(null);
  const autoJoinStartedRef = useRef<boolean>(false);
  const { isAuthenticated, user, logout } = useAuth();
  const catScrollRef = useRef<HTMLDivElement | null>(null);
  const [catCanScrollLeft, setCatCanScrollLeft] = useState(false);
  const [catCanScrollRight, setCatCanScrollRight] = useState(false);
  const showCartBar = tab === 'menu' && guest && cart.length > 0 && !autoJoinInProgress;

  // Pairings (fetched from API)
  const [pairings, setPairings] = useState<any[]>([]);
  const [productPairingsMap, setProductPairingsMap] = useState<Record<number, boolean>>({});

  // NEW useEffect to fetch public configs
  useEffect(() => {
    const fetchPublicConfigs = async () => {
      try {
        const res = await fetch(`${apiConfig.erpBaseUrl}/api/public/config`);
        if (res.ok) {
          const data = await res.json();
          setSolicitaCpf(data.site_cardapio_solicita_cpf || false);
          setSolicitaTelefone(data.site_cardapio_solicita_telefone || false);
        }
        // Gateway ativo
        const pg = await fetch(`${apiConfig.erpBaseUrl}/api/payments/config`);
        if (pg.ok) {
          const pgData = await pg.json();
          if (pgData?.activeGateway) {
            setActiveGateway(pgData.activeGateway);
          }
        }
      } catch (e) {
        console.error('Erro ao carregar configurações públicas:', e);
      }
    };
    fetchPublicConfigs();
  }, []); // Empty dependency array means it runs once on mount

  // CPF validation function
  const isValidCPF = (cpf: string) => {
    if (typeof cpf !== "string") return false;
    const strCPF = cpf.replace(/[^\d]+/g, '');
    if (strCPF.length !== 11) return false;
    if (/^(\d)\1{10}$/.test(strCPF)) return false;

    let soma = 0;
    let resto;

    for (let i = 1; i <= 9; i++) soma += parseInt(strCPF.substring(i - 1, i)) * (11 - i);
    resto = (soma * 10) % 11;
    if (resto === 10 || resto === 11) resto = 0;
    if (resto !== parseInt(strCPF.substring(9, 10))) return false;

    soma = 0;
    for (let i = 1; i <= 10; i++) soma += parseInt(strCPF.substring(i - 1, i)) * (12 - i);
    resto = (soma * 10) % 11;
    if (resto === 10 || resto === 11) resto = 0;
    if (resto !== parseInt(strCPF.substring(10, 11))) return false;

    return true;
  };

  // Validation States (Computed)
  const isCpfValid = useMemo(() => !solicitaCpf || (cpf && isValidCPF(cpf)), [solicitaCpf, cpf]);
  const isTelefoneValid = useMemo(() => !solicitaTelefone || (telefone && telefone.trim().length >= 5), [solicitaTelefone, telefone]);
  
  // CPF and Phone formatting functions
  const formatCpf = (value: string) => {
    let cleaned = value.replace(/\D/g, ''); // Remove tudo que não é dígito
    if (cleaned.length > 11) cleaned = cleaned.substring(0, 11);

    if (cleaned.length > 9) {
      return cleaned.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
    } else if (cleaned.length > 6) {
      return cleaned.replace(/(\d{3})(\d{3})(\d{3})/, '$1.$2.$3');
    } else if (cleaned.length > 3) {
      return cleaned.replace(/(\d{3})(\d{3})/, '$1.$2');
    }
    return cleaned;
  };

  const handleCpfChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const formatted = formatCpf(e.target.value);
    setCpf(formatted);
  };
  
  useEffect(() => {
    if (!detailProduct?.id) {
      setPairings([]);
      return;
    }

    const currentProductId = detailProduct.id;
    let active = true;
    (async () => {
      try {
        const res = await axios.get(`${apiConfig.erpBaseUrl}/api/produtos/${detailProduct.id}/harmonizacoes`, {
          headers: {
            ...(locale ? { 'Accept-Language': locale } : {}),
          },
        });
        const pairingData = res.data || [];
        if (active) {
          setPairings(pairingData);
          setProductPairingsMap((prev) => ({
            ...prev,
            [currentProductId]: pairingData.length > 0,
          }));
        }
      } catch (e) {
        console.warn('Falha ao buscar harmonizações', e);
      }
    })();
    return () => { active = false; };
  }, [detailProduct, locale]);

  // Fetch unread notifications count
  const fetchUnreadCount = async (guestToken: string) => {
    try {
      const response = await axios.get<{ count: number }>(
        `${apiConfig.erpBaseUrl}/api/notificacoes/contador`,
        {
          headers: { 'X-Guest-Token': guestToken },
        }
      );
      setUnreadNotifications(response.data.count);
    } catch (error) {
      console.error('Error fetching unread count:', error);
    }
  };

  useEffect(() => {
    // restore guest
    try {
      const otherSlug = findOtherActiveGuestSlug();
      if (otherSlug) {
        setBlockedMesaSlug(otherSlug);
        toast({
          title: t('mesa.toast.sessionAlreadyOpen.title'),
          description: t('mesa.toast.sessionAlreadyOpen.description', { mesaSlugAnterior: otherSlug }),
          duration: 5000,
          variant: 'destructive',
        });
        navigate(`/m/${otherSlug}`, { replace: true });
        return;
      } else {
        setBlockedMesaSlug(null);
      }

      const raw = localStorage.getItem(storageKey);
      console.log('[MesaPage] localStorage guest:', raw ? 'encontrado' : 'não encontrado');
      if (raw) {
        const data = JSON.parse(raw) as GuestSession;
        const normalized = data.mesaSlug ? data : { ...data, mesaSlug };
        setGuest(normalized);
        try { localStorage.setItem(storageKey, JSON.stringify(normalized)); } catch {}
        guestIdRef.current = data.sessaoConvidadoId;
        setNome(data.nomeExibicao || '');
        connectSSE(data.sessaoMesaId);
        refreshConta(data.sessaoConvidadoId);

        // If host, request notification permission and fetch unread notifications
        if (data.host) {
          requestPermission();
          fetchUnreadCount(data.guestToken);
        }
      } else {
        // Se não há guest salvo
        // Caso esteja autenticado como CLIENTE, faz auto ingresso na mesa usando o nome do login
        if (!autoJoinStartedRef.current && isAuthenticated && user?.roles?.includes('CLIENTE')) {
          autoJoinStartedRef.current = true;
          const displayName = (user?.nome || '').trim();
          console.log('[MesaPage] Auto-ingresso ativado para CLIENTE autenticado. Nome:', displayName || '(vazio)');
          if (displayName.length >= 2 && mesaSlug) {
            setAutoJoinInProgress(true);
            setNome(displayName);
            // Usa fluxo padrão de criação de convidado com nome do usuário autenticado
            startAsGuest(displayName).finally(() => {
              setAutoJoinInProgress(false);
            });
          } else {
            // Se não houver nome válido, cai no fluxo manual
            console.log('[MesaPage] Nome inválido para auto-ingresso. Mostrando formulário manual.');
            console.log('[MesaPage] Sem guest no localStorage, verificando sessão ativa');
            verificarSessaoAtiva();
          }
        } else {
          // Fluxo padrão (anônimo ou não-CLIENTE)
          console.log('[MesaPage] Sem guest no localStorage, verificando sessão ativa');
          verificarSessaoAtiva();
        }
      }
    } catch {}
  }, [storageKey, isAuthenticated, user, mesaSlug, findOtherActiveGuestSlug, navigate]);

  const verificarSessaoAtiva = async () => {
    if (!mesaSlug) return;
    console.log('[MesaPage] Verificando sessão ativa para mesa:', mesaSlug);
    try {
      const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/mesas/${mesaSlug}/sessao`, {
        headers: { 'Cache-Control': 'no-store' },
      });
      console.log('[MesaPage] Resposta da verificação:', data);
      setMesaAssistida(!!data.assistida);
      if (data.assistida) {
        setShowAssistidaDialog(true);
        return;
      }
      if (data.sessaoAtiva && data.totalConvidados > 0) {
        console.log('[MesaPage] Sessão ativa detectada, mostrando dialog');
        setSessaoAtiva({
          totalConvidados: data.totalConvidados,
          mesaRotulo: data.mesaRotulo
        });
        setShowConfirmDialog(true);
      } else {
        console.log('[MesaPage] Nenhuma sessão ativa ou sem convidados');
      }
    } catch (e: any) {
      const status = e?.response?.status;
      const msg = e?.response?.data || e?.message || 'Erro ao verificar sessão';
      console.warn('[MesaPage] Falha ao verificar sessão:', status, msg);
      toast({
        title: t('mesa.toast.sessionCheckFail.title'),
        description: t('mesa.toast.sessionCheckFail.description'),
        duration: 4000,
      });
    }
  };

  useEffect(() => {
    // load cardapio (v2 com SKUs)
    (async () => {
      try {
        const headers: Record<string, string> = {};
        if (guest?.guestToken) {
          headers['X-Guest-Token'] = guest.guestToken;
        }
        if (locale) {
          headers['Accept-Language'] = locale;
        }
        const v2 = await fetch(`${apiConfig.erpBaseUrl}/api/public/cardapio/v2`, { headers });
        if (v2.ok) {
          const data = await v2.json();
          setCardapio(sanitizeCardapio(data));
          return;
        }
      } catch (e) {
        console.error('Erro ao carregar cardápio', e);
      }
    })();
  }, [guest?.guestToken, locale]);

  useEffect(() => {
    if (!guest || tab !== 'conta') return;
    refreshContaMesa(guest.sessaoMesaId);
  }, [guest, tab]);

  // Setas de navegação das categorias (scroll horizontal)
  useEffect(() => {
    const el = catScrollRef.current;
    if (!el) return;
    const update = () => {
      const maxScrollLeft = el.scrollWidth - el.clientWidth;
      setCatCanScrollLeft(el.scrollLeft > 4);
      setCatCanScrollRight(maxScrollLeft - el.scrollLeft > 4);
    };
    update();
    el.addEventListener('scroll', update);
    window.addEventListener('resize', update);
    return () => {
      el.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  }, [cardapio]);

  const connectSSE = (sessaoMesaId: number) => {
    try {
      if (sseRef.current) sseRef.current.close();
      const url = `${apiConfig.erpBaseUrl}/api/events/sessoes/${sessaoMesaId}`;
      const es = new EventSource(url);
      es.addEventListener('connected', () => console.debug('SSE conectado'));
      es.addEventListener('ping', () => {
        // Silent heartbeat - keep connection alive
      });
      es.addEventListener('table.closed', (evt: MessageEvent) => {
        console.debug('table.closed', evt.data);
        setSessionClosed(true);
        setCart([]);
        setTab('menu');
        // limpar convidado para forçar novo ingresso
        try { localStorage.removeItem(storageKey); } catch {}
        setGuest(null);
        toast({
          title: t('mesa.toast.sessionClosed.title'),
          description: t('mesa.toast.sessionClosed.description'),
        });
      });
      es.addEventListener('guest.joined', (evt: MessageEvent) => {
        console.debug('guest.joined', evt.data);

        try {
          const data = JSON.parse(evt.data);
          const { sessaoConvidadoId, nomeExibicao } = data;

          // Notificar apenas o host sobre novos convidados (não notificar sobre si mesmo)
          const currentGuestId = guestIdRef.current;
          if (!currentGuestId || sessaoConvidadoId === currentGuestId) {
            // Não notificar sobre si mesmo
            return;
          }

          // Buscar guest atual para verificar se é host
          const savedGuest = localStorage.getItem(storageKey);
          if (savedGuest) {
            const guestData = JSON.parse(savedGuest);
            if (guestData.host) {
              const title = t('mesa.toast.guestJoined.title');
              const description = t('mesa.toast.guestJoined.description', { name: nomeExibicao });

              // Show toast
              toast({
                title,
                description,
                duration: 5000,
              });

              // Show browser notification
              showNotification({
                title,
                body: description,
              });

              // Increment unread counter
              setUnreadNotifications((prev) => prev + 1);
            }
          }
        } catch (e) {
          console.warn('Erro ao processar guest.joined:', e);
        }
      });

      es.addEventListener('table.moved', (evt: MessageEvent) => {
        try {
          const data = JSON.parse(evt.data);
          const novaMesaSlug = data?.mesaSlugNova || data?.mesaSlug || data?.slug;
          if (!novaMesaSlug || novaMesaSlug === mesaSlug) {
            return;
          }

          // Mover storage do guest para o novo slug, mantendo tokens/ids
          try {
            const saved = localStorage.getItem(storageKey);
            if (saved) {
              const parsed = JSON.parse(saved);
              const updated = { ...parsed, mesaSlug: novaMesaSlug };
              localStorage.setItem(`qrGuest:${novaMesaSlug}`, JSON.stringify(updated));
              localStorage.removeItem(storageKey);
            }
          } catch (e) {
            console.warn('Falha ao migrar guest para novo slug', e);
          }

          toast({
            title: t('mesa.toast.mesaMoved.title'),
            description: t('mesa.toast.mesaMoved.description', { novaMesaSlug }),
            duration: 4000,
          });

          navigate(`/m/${novaMesaSlug}`, { replace: true });
        } catch (e) {
          console.warn('Erro ao processar table.moved:', e);
        }
      });

      es.addEventListener('order.created', (evt: MessageEvent) => {
        console.debug('order.created', evt.data);

        try {
          const data = JSON.parse(evt.data);
          const { sessaoConvidadoId, nomeConvidado, itens } = data;

          // Buscar guest atual para verificar se é host e notificar sobre pedidos de outros
          const currentGuestId = guestIdRef.current;
          if (currentGuestId && sessaoConvidadoId !== currentGuestId) {
            const savedGuest = localStorage.getItem(storageKey);
            if (savedGuest) {
              const guestData = JSON.parse(savedGuest);
              if (guestData.host && Array.isArray(itens) && itens.length > 0) {
                const totalItens = itens.reduce((sum: number, item: any) => sum + (item.quantidade || 0), 0);
                const primeiroProduto = itens[0]?.produtoNome || 'item';
                const descricao = itens.length === 1
                  ? `${itens[0].quantidade}x ${primeiroProduto}`
                  : `${totalItens} itens (${primeiroProduto}${itens.length > 1 ? ' +' + (itens.length - 1) : ''})`;
                const title = t('mesa.orders.notification.newOrder', { name: nomeConvidado });

                // Show toast
                toast({
                  title,
                  description: descricao,
                  duration: 6000,
                });

                // Show browser notification
                showNotification({
                  title,
                  body: descricao,
                });

                // Increment unread counter
                setUnreadNotifications((prev) => prev + 1);
              }
            }
          }
        } catch (e) {
          console.warn('Erro ao processar order.created:', e);
        }

        // Atualiza conta apenas se guestId conhecido
        // @ts-ignore
        if (guestIdRef && guestIdRef.current) refreshConta(guestIdRef.current as number);
      });
      es.addEventListener('kds.status_changed', (evt: MessageEvent) => {
        console.debug('kds.status_changed', evt.data);

        // Parse event data
        try {
          const data = JSON.parse(evt.data);
          const { itemPedidoId, status } = data;

          // Show toast if item was canceled
          if (status === 'canceled') {
          toast({
            title: t('mesa.toast.order.itemCanceled.title'),
            description: t('mesa.toast.order.itemCanceled.description'),
            variant: 'destructive',
            duration: 7000,
          });
          } else if (status === 'ready' || (status === 'accepted' && data.necessitaPreparacao === false)) {
            const mode: ServiceMode = (data.serviceMode as ServiceMode) || 'waiter_delivery';
            if (mode === 'customer_pickup' || mode === 'PICKUP') {
              const orderLabel = data.pedidoId
                ? t('mesa.orders.orderNumber', { id: data.pedidoId })
                : t('mesa.orders.yourOrder');
              const hasMultipleItems = Number(data.pedidoItemCount || data.pedido?.itemCount || 0) > 1;
              const title = hasMultipleItems
                ? t('mesa.orders.ready.itemTitle')
                : t('mesa.orders.ready.orderTitle');
              const description = t('mesa.orders.ready.description', { orderLabel });

              toast({
                title,
                description,
                duration: 24 * 60 * 60 * 1000, // manter visível até fechar (24h)
              });

              showNotification({
                title,
                body: description,
              });

              // Increment unread counter
              setUnreadNotifications((prev) => prev + 1);
            }
          }
        } catch (e) {
          console.warn('Erro ao parsear kds.status_changed:', e);
        }

        // Refresh conta to update the list
        // @ts-ignore
        if (guestIdRef && guestIdRef.current) refreshConta(guestIdRef.current as number);
      });
      es.addEventListener('payment.updated', (evt: MessageEvent) => {
        console.debug('payment.updated', evt.data);
        try {
          const data = JSON.parse(evt.data);
          const status = String(data?.status || '').toLowerCase();
          if (status === 'paid') {
            handlePaymentSuccess();
            return;
          } else if (status === 'failed' || status === 'canceled' || status === 'cancelled' || status === 'expired') {
            toast({
              title: t('mesa.toast.payment.failed.title'),
              description: t('mesa.toast.payment.failed.description'),
              duration: 5000,
              variant: 'destructive',
            });
          }
        } catch (e) {
          console.warn('Erro ao processar payment.updated:', e);
        }
        // @ts-ignore
        if (guestIdRef && guestIdRef.current) refreshConta(guestIdRef.current as number);
        // Limpar QR ao receber atualização de pagamento (opcional)
        setQrPayload(null);
        setPixStatus(null);
        setPixMessage(null);
        setPixExpiresAt(null);
      });
      es.addEventListener('payment.registered', (evt: MessageEvent) => {
        console.debug('payment.registered', evt.data);

        try {
          const data = JSON.parse(evt.data);
          const { totalPago, devidoRestante } = data;

          // Show toast about payment
          toast({
            title: t('mesa.toast.payment.success.title'),
            description: t('mesa.toast.payment.success.description', {
              pago: formatCurrency(totalPago),
              restante: formatCurrency(devidoRestante),
            }),
            duration: 5000,
          });
        } catch (e) {
          console.warn('Erro ao processar payment.registered:', e);
        }

        // Refresh conta to update amounts
        // @ts-ignore
        if (guestIdRef && guestIdRef.current) refreshConta(guestIdRef.current as number);
      });
      es.addEventListener('checkout.released', (evt: MessageEvent) => {
        console.debug('checkout.released', evt.data);
        setPayUnlocked(true);
        setCheckoutRequested(true);
    toast({
      title: t('mesa.toast.checkoutReady.title'),
      description: t('mesa.toast.checkoutReady.description'),
      duration: 4000,
    });
      });
      es.onerror = () => console.warn('SSE erro');
      sseRef.current = es;
    } catch (e) {
      console.warn('Falha SSE', e);
    }
  };

  const openProductDetails = (p: ProductType) => {
    setDetailProduct(p);
    setDetailsOpen(true);
  };

  const startAsGuest = async (overrideName?: string) => {
    if (!mesaSlug) return;
    if (isActiveSessionDialogOpen) return;
    if (mesaAssistida) {
      setShowAssistidaDialog(true);
      return;
    }
    setSubmitted(true); // Formulário foi tocado
    const trimmedName = ((overrideName ?? nome) || '').trim();

    // Validar nome
    if (trimmedName.length < 2) {
      toast({
        title: t('mesa.toast.validation.title'),
        description: t('mesa.toast.validation.name.description'),
        duration: 3000,
        variant: 'destructive',
      });
      return;
    }

    // Validar CPF se solicitado e não for válido
    if (solicitaCpf && !isCpfValid) {
      toast({
        title: t('mesa.toast.validation.title'),
        description: t('mesa.toast.validation.cpf.description'),
        duration: 3000,
        variant: 'destructive',
      });
      return;
    }

    // Validar Telefone se solicitado e não for válido
    if (solicitaTelefone && !isTelefoneValid) {
      toast({
        title: t('mesa.toast.validation.title'),
        description: t('mesa.toast.validation.phone.description'),
        duration: 3000,
        variant: 'destructive',
      });
      return;
    }

    const otherSlug = findOtherActiveGuestSlug();
    if (otherSlug) {
      toast({
        title: t('mesa.toast.sessionAlreadyOpen.title'),
        description: t('mesa.toast.sessionAlreadyOpen.description', { mesaSlugAnterior: otherSlug }),
        duration: 5000,
        variant: 'destructive',
      });
      navigate(`/m/${otherSlug}`, { replace: true });
      return;
    }

    setLoading(true);
    try {
      const body: { nomeExibicao: string; cpf?: string; telefone?: string; userToken?: string } = {
        nomeExibicao: trimmedName,
      };
      const cleanedCpfFinal = cpf.replace(/\D/g, ''); // Garante que o CPF enviado é limpo
      const cleanedTelefoneFinal = telefone.trim(); // Garante que o telefone enviado é limpo
      
      if (solicitaCpf && cleanedCpfFinal) body.cpf = cleanedCpfFinal;
      if (solicitaTelefone && cleanedTelefoneFinal) body.telefone = cleanedTelefoneFinal;
      if (isAuthenticated && user?.id) {
        body.userToken = String(user.id);
      }

      const { data } = await axios.post(`${apiConfig.erpBaseUrl}/api/mesas/${mesaSlug}/convidados`, body, {
        headers: { 'Content-Type': 'application/json' },
      });
      const g: GuestSession = {
        sessaoConvidadoId: data.sessaoConvidadoId,
        sessaoMesaId: data.sessaoMesaId,
        guestToken: data.guestToken,
        nomeExibicao: trimmedName,
        host: data.host === true,
        mesaSlug,
      };
      setGuest(g);
      setTab('menu');
      guestIdRef.current = g.sessaoConvidadoId;
      localStorage.setItem(storageKey, JSON.stringify(g));
      connectSSE(g.sessaoMesaId);
      refreshConta(g.sessaoConvidadoId);
    } catch (e: any) {
      console.error(e);
      const errMsg = e?.response?.data?.message || e?.response?.data?.error?.message || 'Falha ao criar convidado';
      toast({
        title: t('mesa.toast.login.failure.title'),
        description: t('mesa.toast.login.failure.description', { errorMessage: errMsg }),
        duration: 5000,
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };


  const addToCart = (p: ProductType, preco: number) => {
    if (p.produto_disponivel === false) {
      if (p.horarios_disponiveis && p.horarios_disponiveis.length > 0) {
        setUnavailableProductInfo({
          nome: p.nome,
          horarios_disponiveis: p.horarios_disponiveis,
        });
        setShowProductUnavailableDialog(true);
      }
      return;
    }

    setCart((prev) => {
      const idx = prev.findIndex((i) => i.produtoId === p.id);
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx] = { ...copy[idx], quantidade: copy[idx].quantidade + 1 };
        return copy;
      }
      const origemDesconto = p.origem_desconto ?? p.origemDesconto;
      return [...prev, { produtoId: p.id, nome: p.nome, preco, quantidade: 1, observacoes: '', origemDesconto }];
    });
  };

  const addToCartSku = (p: ProductType, skuId: number, productName: string, variacao: string | undefined, preco: number) => {
    if (p.produto_disponivel === false) {
      if (p.horarios_disponiveis && p.horarios_disponiveis.length > 0) {
        setUnavailableProductInfo({
          nome: p.nome,
          horarios_disponiveis: p.horarios_disponiveis,
        });
        setShowProductUnavailableDialog(true);
      }
      return;
    }
    const displayName = variacao ? `${productName} (${variacao})` : productName;
    setCart((prev) => {
      const idx = prev.findIndex((i) => (i.skuId ?? i.produtoId) === skuId);
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx] = { ...copy[idx], quantidade: copy[idx].quantidade + 1 };
        return copy;
      }
      const sku = p.skus?.find((s) => s.id === skuId);
      const origemDesconto = sku?.origemDesconto ?? p.origem_desconto ?? p.origemDesconto;
      return [...prev, { produtoId: p.id, skuId, nome: displayName, preco, quantidade: 1, observacoes: '', origemDesconto }];
    });
  };

  const updateCartObservacoes = (itemId: number, observacoes: string) => {
    setCart((prev) => {
      const idx = prev.findIndex((i) => (i.skuId ?? i.produtoId) === itemId);
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx] = { ...copy[idx], observacoes };
        return copy;
      }
      return prev;
    });
  };

  const removeFromCart = (itemId: number) => {
    setCart((prev) => prev.filter((i) => (i.skuId ?? i.produtoId) !== itemId));
  };

  const decreaseQuantity = (itemId: number) => {
    setCart((prev) => {
      const idx = prev.findIndex((i) => (i.skuId ?? i.produtoId) === itemId);
      if (idx >= 0) {
        const copy = [...prev];
        if (copy[idx].quantidade > 1) {
          copy[idx] = { ...copy[idx], quantidade: copy[idx].quantidade - 1 };
          return copy;
        } else {
          // Remove if quantity becomes 0
          return copy.filter((i) => (i.skuId ?? i.produtoId) !== itemId);
        }
      }
      return prev;
    });
  };

  const increaseQuantity = (itemId: number) => {
    setCart((prev) => {
      const idx = prev.findIndex((i) => (i.skuId ?? i.produtoId) === itemId);
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx] = { ...copy[idx], quantidade: copy[idx].quantidade + 1 };
        return copy;
      }
      return prev;
    });
  };

  // Helpers para adicionar recomendados
  const handleAddRecommended = (p: ProductType) => {
    try {
      if (Array.isArray(p.skus) && p.skus.length > 1) {
        const s = p.skus[0];
        const sp = typeof s.preco === 'number' ? s.preco : (typeof s.precoVenda === 'number' ? s.precoVenda : 0);
        addToCartSku(p, s.id, p.nome, s.variacao, sp);
      } else {
        const price = resolvePrice(p);
        addToCart(p, price);
      }
    } catch (e) {
      console.warn('Erro ao adicionar recomendado:', e);
    }
  };

  const handleSendOrderRequest = () => {
    if (!guest) {
      toast({
        title: t('mesa.toast.identify.title'),
        description: t('mesa.toast.identify.description'),
        duration: 4000,
        variant: 'destructive',
      });
      return;
    }
    if (cart.length === 0) {
      toast({
        title: t('mesa.toast.emptyCart.title'),
        description: t('mesa.toast.emptyCart.description'),
        duration: 4000,
        variant: 'destructive',
      });
      return;
    }
    setOrderConfirmOpen(true);
  };

  const sendOrder = async () => {
    if (!guest || cart.length === 0) return;
    setSendingOrder(true);
    try {
      await axios.post(`${apiConfig.erpBaseUrl}/api/pedidos`, {
        itens: cart.map((c) => {
          const trimmedObs = (c.observacoes || '').trim();
          const isSku = !!c.skuId;
          return {
            produtoId: isSku ? null : c.produtoId,
            skuId: isSku ? c.skuId : null,
            quantidade: c.quantidade,
            observacoes: trimmedObs.length > 0 ? trimmedObs : null,
          };
        }),
      }, {
        headers: {
          'X-Guest-Token': guest.guestToken,
          'X-Sessao-Mesa': String(guest.sessaoMesaId),
        },
      });
      setCart([]);
      setOrderConfirmOpen(false);
      setCartOpen(false);
      toast({
        title: t('mesa.toast.order.sent.title'),
        description: t('mesa.toast.order.sent.description'),
        duration: 4000,
      });
      refreshConta(guest.sessaoConvidadoId);
    } catch (e: any) {
      console.error(e);
      const description = e?.response?.data?.error?.message || e?.response?.data?.message || 'Não foi possível enviar o pedido. Tente novamente.';
      toast({
        title: t('mesa.toast.order.sendError.title'),
        description: t('mesa.toast.order.sendError.description', { errorMessage: description }),
        duration: 5000,
        variant: 'destructive',
      });
    } finally {
      setSendingOrder(false);
    }
  };

  const refreshConta = async (sessaoConvidadoId: number) => {
    try {
      const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/conta`, {
        params: { sessaoConvidadoId },
      });
      setConta(data);
      if (guest?.sessaoMesaId) {
        await refreshContaMesa(guest.sessaoMesaId);
      }
    } catch (e: any) {
      const code = e?.response?.data?.error?.code;
      if (code === 'session_closed') {
        setSessionClosed(true);
        setCart([]);
        try { localStorage.removeItem(storageKey); } catch {}
        setGuest(null);
        toast({
          title: t('mesa.toast.sessionClosed.title'),
          description: t('mesa.toast.sessionClosed.description'),
        });
        return;
      }
      console.error(e);
    }
  };

  const refreshContaMesa = async (sessaoMesaId: number) => {
    try {
      const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/conta`, {
        params: { sessaoMesaId },
      });
      setContaMesa(data);
      const liberado = Boolean(data?.selfCheckoutLiberado);
      setPayUnlocked(liberado);
      if (liberado) {
        setCheckoutRequested(true);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const pagarPix = async (escopoMesa: boolean) => {
    if (!guest) return;
    const taxId = (cpf || cardDoc || "").replace(/\D/g, "");
    if (activeGateway === "PAGSEGURO" && !taxId) {
      callToastError(t('mesa.pix.errors.cpfRequired'));
      return;
    }
    setPaymentProcessing(true);
    setQrPayload(null);
    setPixExpiresAt(null);
    setPixStatus(null);
    setPixMessage(null);
    try {
      const payload: any = {
        escopo: escopoMesa ? 'mesa' : 'convidado',
        sessaoMesaId: guest.sessaoMesaId,
        sessaoConvidadoId: escopoMesa ? undefined : guest.sessaoConvidadoId,
        metodo: 'pix',
        payerName: guest.nomeExibicao || 'Cliente',
        payerEmail: resolvePayerEmail(guest.nomeExibicao),
        payerTaxId: taxId || undefined,
      };
      const { data } = await axios.post(`${apiConfig.erpBaseUrl}/api/pagamentos/intent`, payload, {
        headers: { 'X-Guest-Token': guest.guestToken },
      });
      setQrPayload(data?.pixQrCode || data?.pixQrCodeBase64 || null);
      setPixStatus(data?.status || null);
      setPixMessage(data?.friendlyMessage || data?.message || null);
      setPixExpiresAt(data?.expiresAt || null);
      if (!escopoMesa) {
        await refreshConta(guest.sessaoConvidadoId);
      } else {
        await refreshContaMesa(guest.sessaoMesaId);
      }
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message || e?.response?.data?.message || e?.message || t('mesa.pix.errors.generationFailed');
      callToastError(msg);
    } finally {
      setPaymentProcessing(false);
    }
  };

  const pagarCard = async (escopoMesa: boolean) => {
    if (!guest) return;
    let tokenToUse = cardToken;
    if (!tokenToUse) {
      tokenToUse = await handleCreateCardToken();
      if (!tokenToUse) return;
    }
    if (typeof tokenToUse !== "string") {
    callToastError(t('mesa.payment.errors.invalidCardToken'));
      return;
    }
    setPaymentProcessing(true);
    setQrPayload(null);
    setPixStatus(null);
    setPixMessage(null);
    setPixExpiresAt(null);
    try {
      const payload: any = {
        escopo: escopoMesa ? 'mesa' : 'convidado',
        sessaoMesaId: guest.sessaoMesaId,
        sessaoConvidadoId: escopoMesa ? undefined : guest.sessaoConvidadoId,
        metodo: 'card',
        payerName: guest.nomeExibicao || 'Cliente',
        payerEmail: resolvePayerEmail(guest.nomeExibicao),
        payerTaxId: cardDoc.replace(/\D/g, "") || undefined,
        cardToken: tokenToUse,
        paymentMethodId: cardPaymentMethodId || 'visa',
        installments: cardInstallments || 1,
      };
      const { data } = await axios.post(`${apiConfig.erpBaseUrl}/api/pagamentos/intent`, payload, {
        headers: { 'X-Guest-Token': guest.guestToken },
      });
      setPixStatus(data?.status || null);
      setPixMessage(data?.friendlyMessage || data?.message || null);
      setPixExpiresAt(null);
      if (data?.status?.toString().toUpperCase() === 'PAID') {
        handlePaymentSuccess();
        return;
      }
      if (!escopoMesa) {
        await refreshConta(guest.sessaoConvidadoId);
      } else {
        await refreshContaMesa(guest.sessaoMesaId);
      }
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message || e?.response?.data?.message || e?.message || 'Falha ao pagar com cartão';
      callToastError(msg);
    } finally {
      setPaymentProcessing(false);
    }
  };

  useEffect(() => {
    setCardBrand(detectCardBrand(cardNumber));
  }, [cardNumber]);

  useEffect(() => {
    if (paymentMethod !== "CARD") return;
    const cardFilled =
      cardNumber.trim() &&
      cardHolder.trim() &&
      cardExpMonth.trim() &&
      cardExpYear.trim() &&
      cardCvv.trim() &&
      cardDoc.trim();
    if (!cardFilled) return;
    if (cardToken.trim()) return;
    if (creatingToken) return;
    handleCreateCardToken();
  }, [
    cardNumber,
    cardHolder,
    cardExpMonth,
    cardExpYear,
    cardCvv,
    cardDoc,
    cardToken,
    creatingToken,
    handleCreateCardToken,
    paymentMethod,
  ]);

  const chamarGarcom = async () => {
    if (!guest) return;
    if (chamandoGarcom) return;
    setChamandoGarcom(true);
    try {
      await axios.post(`${apiConfig.erpBaseUrl}/api/chamados`, {
        sessaoMesaId: guest.sessaoMesaId,
        tipo: 'garcom',
        observacao: observacaoGarcom.trim() || null,
      }, {
        headers: {
          'X-Guest-Token': guest.guestToken,
        },
      });
      toast({
        title: t('mesa.toast.waiter.called.title'),
        description: t('mesa.toast.waiter.called.description'),
        duration: 4000,
      });
      // Limpar a observação após o envio
      setObservacaoGarcom('');
    } catch (e) {
      console.error(e);
      toast({
        title: t('mesa.toast.waiter.callError.title'),
        description: t('mesa.toast.waiter.callError.description'),
        variant: 'destructive',
      });
    } finally {
      setChamandoGarcom(false);
    }
  };

  const solicitarFechamento = async () => {
    if (!guest) return;
    if (checkoutRequested) return;
    try {
      await axios.post(`${apiConfig.erpBaseUrl}/api/chamados`, {
        sessaoMesaId: guest.sessaoMesaId,
        tipo: 'conta',
        observacao:
          'Solicitou fechamento. Apresentar conta/maquininha ou liberar pagamento no app.',
      }, {
        headers: {
          'X-Guest-Token': guest.guestToken,
        },
      });
      setCheckoutRequested(true);
      toast({
        title: t('mesa.toast.checkout.requested.title'),
        description: t('mesa.toast.checkout.requested.description'),
        duration: 4000,
      });
    } catch (e) {
      console.error(e);
      toast({
        title: t('mesa.toast.checkout.requestError.title'),
        description: t('mesa.toast.checkout.requestError.description'),
        variant: 'destructive',
      });
    }
  };

  const filteredCategories = useMemo(() => {
    const q = normalizeToSearch(search.trim());
    if (!q && selectedCategoryId === 'all') return cardapio;
    return cardapio
      .map(cat => ({
        ...cat,
        produtos: (cat.produtos || []).filter((p: any) => {
          const nameMatch = normalizeToSearch(p.nome).includes(q);
          const catMatch = selectedCategoryId === 'all' || cat.id === selectedCategoryId;
          return (q ? nameMatch : true) && catMatch && productHasValidPrice(p);
        })
      }))
      .filter(cat => (cat.produtos && cat.produtos.length > 0));
  }, [cardapio, search, selectedCategoryId]);

  // Gate de identificação: se não há convidado (não exibimos cardápio/abas ainda)
  if (!guest) {
    if (showAssistidaDialog) {
      return (
        <div className="min-h-screen bg-soft-white text-mesa-text flex items-center">
          <div className="max-w-md mx-auto px-6 py-10 w-full">
            <div className="mb-6 flex items-start justify-between">
              <div>
                <div className="text-[11px] tracking-widest text-accent/80 uppercase">{t('mesa.headers.tableLabel')}</div>
                <h1 className="text-2xl font-display tracking-wider leading-tight">{mesaSlug}</h1>
              </div>
              {renderLocaleSelector()}
            </div>
            <div className="mb-6 border border-red-200 rounded-lg p-4 bg-white shadow-md">
              <h2 className="text-lg font-medium mb-2 flex items-center gap-2 text-red-700">
                <span>🚫</span>
                <span>{t('mesa.dialog.assisted.title')}</span>
              </h2>
              <p className="text-sm text-mesa-text mb-4">
                {t('mesa.dialog.assisted.description')}
              </p>
              <button
                onClick={() => {
                  setShowAssistidaDialog(false);
                  window.location.href = '/';
                }}
                className="w-full bg-accent hover:bg-accent/90 text-mesa-text px-4 py-2 rounded font-medium"
              >
                {t('mesa.dialog.assisted.dismissButton')}
              </button>
            </div>
          </div>
        </div>
      );
    }
    return (
      <div className="min-h-screen bg-soft-white text-mesa-text flex items-center">
        <div className="max-w-md mx-auto px-6 py-10 w-full">
          <div className="mb-6 flex items-start justify-between">
            <div>
                <div className="text-[11px] tracking-widest text-accent/80 uppercase">{t('mesa.headers.tableLabel')}</div>
              <h1 className="text-2xl font-display tracking-wider leading-tight">{mesaSlug}</h1>
            </div>
            {renderLocaleSelector()}
          </div>
          {/* Auto-join loader quando autenticado como CLIENTE */}
          {autoJoinInProgress && isAuthenticated && user?.roles?.includes('CLIENTE') && (
            <div className="mb-6 border border-accent/20 rounded-lg p-4 bg-white shadow-md">
              <h2 className="text-lg font-medium mb-2 flex items-center gap-2">
                <span>🔐</span>
                <span>{t('mesa.autojoin.title')}</span>
              </h2>
              <p className="text-sm text-mesa-text">
                {t('mesa.autojoin.description', { name: nome || t('mesa.headers.guestLabel') })}
              </p>
            </div>
          )}

          {/* Diálogo de sessão ativa (oculto durante auto-join) */}
          {!autoJoinInProgress && showConfirmDialog && sessaoAtiva && (
            <div className="mb-6 border border-accent/20 rounded-lg p-4 bg-white shadow-md">
              <h2 className="text-lg font-medium mb-2 flex items-center gap-2">
                <span>⚠️</span>
                <span>{t('mesa.dialog.activeSession.title')}</span>
              </h2>
              <p className="text-sm text-mesa-text mb-4">
                {t('mesa.dialog.activeSession.description', {
                  mesaRotulo: sessaoAtiva.mesaRotulo,
                  convidados: sessionGuestsLabel,
                })}
              </p>
              <p className="text-sm text-mesa-text mb-4">
                {t('mesa.dialog.activeSession.continueDescription')}
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => {
                    setShowConfirmDialog(false);
                    setSessaoAtiva(null);
                    navigate('/');
                  }}
                  className="flex-1 border border-accent/30 hover:bg-accent/10 bg-white shadow-sm text-mesa-text px-4 py-2 rounded"
                >
                  {t('mesa.dialog.activeSession.cancelButton')}
                </button>
                <button
                  onClick={() => setShowConfirmDialog(false)}
                  className="flex-1 bg-accent hover:bg-accent/90 text-mesa-text px-4 py-2 rounded font-medium"
                >
                  {t('mesa.dialog.activeSession.joinButton')}
                </button>
              </div>
            </div>
          )}
          {/* Diálogo de mesa assistida */}
          {!autoJoinInProgress && showAssistidaDialog && (
            <div className="mb-6 border border-red-200 rounded-lg p-4 bg-white shadow-md">
              <h2 className="text-lg font-medium mb-2 flex items-center gap-2 text-red-700">
                <span>🚫</span>
                <span>{t('mesa.dialog.assisted.title')}</span>
              </h2>
              <p className="text-sm text-mesa-text mb-4">
                {t('mesa.dialog.assisted.description')}
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => {
                    setShowAssistidaDialog(false);
                    window.location.href = '/';
                  }}
                  className="flex-1 bg-accent hover:bg-accent/90 text-mesa-text px-4 py-2 rounded font-medium"
                >
                  {t('mesa.dialog.assisted.dismissButton')}
                </button>
              </div>
            </div>
          )}

          {/* Form de identificação só aparece se não estiver auto-join em progresso */}
          <div className="mb-6">
            <h2 className="text-lg font-medium mb-2">{t('mesa.form.intro.title')}</h2>
            {autoJoinInProgress && isAuthenticated && user?.roles?.includes('CLIENTE') ? (
              <p className="text-sm text-mesa-text/70">{t('mesa.form.autoJoin.message')}</p>
            ) : (
              <p className="text-sm text-mesa-text/70">{introDescription}</p>
            )}
          </div>

          {!autoJoinInProgress && (
            <div className="space-y-4">
              {(solicitaCpf || solicitaTelefone) && (
                <>
                  <div className="space-y-3">
                    {solicitaCpf && (
                    <div>
                      <input
                          className={`w-full border rounded px-3 py-3 placeholder:text-mesa-text/50 ${submitted && !isCpfValid ? 'border-red-500' : 'border-accent/30'} bg-transparent disabled:cursor-not-allowed disabled:bg-soft-white/60 disabled:border-accent/30`}
                          placeholder={t('mesa.form.cpf.placeholder')}
                          value={cpf}
                          onChange={handleCpfChange}
                          maxLength={14} // 000.000.000-00
                          type="text"
                          inputMode="numeric"
                          disabled={isActiveSessionDialogOpen}
                          required
                        />
                        {submitted && !isCpfValid && (
                          <p className="text-red-500 text-xs mt-1">{t('mesa.form.cpf.invalid')}</p>
                        )}
                      </div>
                    )}
                    {solicitaTelefone && (
                    <div>
                      <input
                          className={`w-full border rounded px-3 py-3 placeholder:text-mesa-text/50 ${submitted && telefone.trim().length < 5 ? 'border-red-500' : 'border-accent/30'} bg-transparent disabled:cursor-not-allowed disabled:bg-soft-white/60 disabled:border-accent/30`}
                          placeholder={t('mesa.form.phone.placeholder')}
                          value={telefone}
                          onChange={(e) => setTelefone(e.target.value)}
                          type="text"
                          inputMode="tel" // Manter inputMode tel para teclado numérico em mobile, mas sem máscara
                          disabled={isActiveSessionDialogOpen}
                          required
                        />
                        {submitted && telefone.trim().length < 5 && (
                          <p className="text-red-500 text-xs mt-1">{t('mesa.form.phone.invalid')}</p>
                        )}
                      </div>
                    )}
                  </div>
                  <div className="flex items-center gap-3 text-xs text-mesa-text/60">
                    <div className="h-px flex-1 bg-accent/20" />
                  </div>
                </>
              )}

              <div className="space-y-2">
                <button
                  type="button"
                  onClick={handleGoogleLoginClick}
                  disabled={isActiveSessionDialogOpen}
                  className="w-full bg-accent hover:bg-accent/90 text-mesa-text px-4 py-3 rounded flex items-center justify-center gap-2 font-semibold disabled:cursor-not-allowed disabled:opacity-70"
                >
                  <span>{t('mesa.form.google.button')}</span>
                </button>
                <Card className="p-4 border border-accent/20 bg-white shadow-sm">
                  <p className="text-sm sm:text-base font-medium text-mesa-text/80">
                    {t('mesa.form.google.help')}
                  </p>
                </Card>
              </div>

              <div className="flex items-center gap-3 text-xs text-mesa-text/60">
                <div className="h-px flex-1 bg-accent/20" />
                <div>{t('mesa.form.orSeparator')}</div>
                <div className="h-px flex-1 bg-accent/20" />
              </div>

              <div className="space-y-3">
                  <div>
                    <input
                      className={`w-full border rounded px-3 py-3 placeholder:text-mesa-text/50 ${submitted && nome.trim().length < 2 ? 'border-red-500' : 'border-accent/30'} bg-transparent disabled:cursor-not-allowed disabled:bg-soft-white/60 disabled:border-accent/30`}
                      placeholder={t('mesa.form.name.placeholder')}
                      value={nome}
                      onChange={(e) => setNome(e.target.value)}
                      disabled={isActiveSessionDialogOpen}
                      required
                    />
                    {submitted && nome.trim().length < 2 && (
                      <p className="text-red-500 text-xs mt-1">{t('mesa.form.name.invalid')}</p>
                    )}
                  </div>
                <button
                  className="w-full bg-accent hover:bg-accent/90 text-mesa-text px-4 py-3 rounded disabled:cursor-not-allowed disabled:opacity-70"
                  disabled={loading || isActiveSessionDialogOpen}
                  onClick={() => startAsGuest()}
                >
                  {t('mesa.form.enter.button')}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-soft-white text-mesa-text">
      <div className="max-w-md mx-auto px-4 py-4 pb-24">
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        {/* Info da Mesa */}
        <div>
          <div className="text-[11px] tracking-widest text-accent/80 uppercase">{t('mesa.headers.tableLabel')}</div>
          <h1 className="text-xl font-display tracking-wider leading-tight">{mesaSlug}</h1>
        {guest && (
            <div className="text-xs text-mesa-text/70">{t('mesa.headers.greeting', { name: guest.nomeExibicao })}</div>
          )}
        </div>

        <div className="flex items-center gap-2">
          {guest?.host && (
            <NotificationBadge
              guestToken={guest.guestToken}
              unreadCount={unreadNotifications}
              onCountUpdate={(count) => setUnreadNotifications(count)}
              t={t}
              locale={locale}
            />
          )}

          {renderLocaleSelector()}

          {/* Avatar com setinha */}
          <div className="flex items-center gap-1">
            <button
              onClick={() => setShowUserMenu(true)}
              className="flex flex-col items-center overflow-hidden rounded-2xl border border-accent/30 bg-white/80 hover:bg-accent/10 transition shadow-sm"
              aria-label={t('mesa.headers.userMenuAria')}
            >
              <div className="flex items-center justify-center w-[76px] h-[58px] overflow-hidden">
                {isAuthenticated && user?.fotoPerfil ? (
                  <img
                    src={user.fotoPerfil}
                    alt={user.nome}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <div className="w-full h-full bg-accent/20 flex items-center justify-center text-sm font-medium text-mesa-text">
                    {guest?.nomeExibicao?.[0]?.toUpperCase() || '?'}
                  </div>
                )}
              </div>
              {guest && (
                <span className="flex items-center justify-center gap-1 w-full border-t border-accent/20 bg-white text-[11px] text-accent font-medium py-1 px-2">
                <span className="text-[11px]">
                    {conta?.grupoClienteDescricao ? '👑' : (guest.host ? '🧭' : '🎟️')}
                  </span>
                  {conta?.grupoClienteDescricao
                    ? conta.grupoClienteDescricao
                    : guest.host
                      ? t('mesa.headers.hostLabel')
                      : t('mesa.headers.guestLabel')}
                </span>
              )}
            </button>
            <button
              onClick={() => setShowUserMenu(true)}
              className="p-1 rounded-full hover:bg-accent/10 transition"
              aria-label={t('mesa.headers.userMenuAria')}
            >
              <ChevronDown className="w-4 h-4 text-mesa-text/70" />
            </button>
          </div>
        </div>
      </div>

      {!guest && (
        <div className="mb-3 flex gap-2">
          <input
            className="border border-accent/30 bg-transparent rounded px-3 py-2 flex-1 placeholder:text-mesa-text/50 disabled:cursor-not-allowed disabled:bg-soft-white/60"
            placeholder={t('mesa.form.optionalName.placeholder')}
            value={nome}
            onChange={(e) => setNome(e.target.value)}
            disabled={isActiveSessionDialogOpen}
          />
          <button
            className="bg-accent hover:bg-accent/90 text-mesa-text px-4 py-2 rounded disabled:cursor-not-allowed disabled:opacity-70"
            disabled={loading || isActiveSessionDialogOpen}
            onClick={startAsGuest}
          >
            {t('mesa.form.enter.button')}
          </button>
        </div>
      )}

      {/* Tabs */}
      <div className="flex items-center gap-2 border-b border-foreground/20 mb-3 overflow-x-auto no-scrollbar">
        {[
          { key: 'menu', label: t('mesa.tabs.menu') },
          { key: 'pedidos', label: t('mesa.tabs.orders') },
          { key: 'conta', label: t('mesa.tabs.account') },
        ].map(t => (
          <button key={t.key}
                  className={`px-3 py-2 text-sm whitespace-nowrap border-b-2 font-medium transition-colors ${tab===t.key ? 'border-accent mesa-text' : 'border-transparent mesa-text/60 hover:mesa-text/70'}`}
                  onClick={() => setTab(t.key as any)}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Tab: Menu */}
      {tab==='menu' && (
        <div className="space-y-6">
          <div className="rounded-3xl border border-foreground/10 bg-soft-white shadow-sm flex flex-col overflow-hidden max-h-[calc(100vh-230px)]">
            <div className="space-y-3 sticky top-0 z-10 bg-soft-white/95 px-4 py-4 backdrop-blur">
              <div className="relative">
                <div className="relative rounded-full border border-foreground/30 bg-white shadow-sm focus-within:ring-2 focus-within:ring-accent/40">
                  <input
                    className="w-full rounded-full bg-transparent px-4 py-2 pr-12 text-sm placeholder:mesa-text/50 focus:outline-none"
                    placeholder={t('mesa.form.search.placeholder')}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                  />
                  {search && (
                    <button
                      type="button"
                      aria-label={t('mesa.form.search.clear')}
                      onClick={() => setSearch('')}
                      className="absolute right-3 top-1/2 -translate-y-1/2 rounded-full bg-mesa-text/5 p-1 text-mesa-text/60 transition hover:bg-mesa-text/10 focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      <X className="h-4 w-4" />
                      <span className="sr-only">{t('mesa.form.search.clear')}</span>
                    </button>
                  )}
                </div>
              </div>
              <div className="relative">
                <div ref={catScrollRef} className="flex gap-2 overflow-x-auto no-scrollbar pr-10">
                  <button className={`px-2.5 py-1 rounded-full border text-xs font-medium transition-colors shadow-sm ${selectedCategoryId==='all' ? 'bg-accent text-accent-foreground border-accent' : 'bg-white mesa-text border-accent/30 hover:bg-accent/10'}`} onClick={()=>setSelectedCategoryId('all')}>
                    {t('mesa.categories.all')}
                  </button>
                  {cardapio.map(cat => (
                    <button
                      key={cat.id}
                      className={`px-2.5 py-1 rounded-full border text-xs font-medium whitespace-nowrap transition-colors shadow-sm ${selectedCategoryId===cat.id ? 'bg-accent text-accent-foreground border-accent' : 'bg-white mesa-text border-accent/30 hover:bg-accent/10'}`}
                      onClick={()=>setSelectedCategoryId(cat.id)}
                    >
                      {cat.nome ? cat.nome.charAt(0).toUpperCase() + cat.nome.slice(1).toLowerCase() : ''}
                    </button>
                  ))}
                </div>
                {catCanScrollLeft && (
                  <div className="pointer-events-none absolute left-0 top-0 bottom-0 w-6 bg-gradient-to-r from-soft-white to-transparent" />
                )}
                {catCanScrollRight && (
                  <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-soft-white to-transparent" />
                )}
                {catCanScrollLeft && (
                  <button
                    aria-label={t('mesa.categories.scrollLeft')}
                    className="absolute left-1 top-1/2 -translate-y-1/2 z-10 h-8 w-8 flex items-center justify-center rounded-full bg-white/95 border border-accent/30 text-mesa-text shadow-md"
                    onClick={() => { const el = catScrollRef.current; if (el) el.scrollBy({ left: -180, behavior: 'smooth' }); }}
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                )}
                {catCanScrollRight && (
                  <button
                    aria-label={t('mesa.categories.scrollRight')}
                    className="absolute right-1 top-1/2 -translate-y-1/2 z-10 h-8 w-8 flex items-center justify-center rounded-full bg-white/95 border border-accent/30 text-mesa-text shadow-md"
                    onClick={() => { const el = catScrollRef.current; if (el) el.scrollBy({ left: 180, behavior: 'smooth' }); }}
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                )}
              </div>
            </div>
            <div className="flex-1 overflow-y-auto px-4 pb-4 pt-2 space-y-6">
              {filteredCategories.length === 0 && (
                <div className="border border-foreground/20 rounded-lg p-4 bg-foreground/5 text-sm text-foreground/70 shadow-sm">
                  {selectedCategoryId === 'all'
                    ? t('mesa.categories.emptyAll')
                    : t('mesa.categories.emptyCategory')}
                </div>
              )}

              <div className="space-y-4">
                {filteredCategories.map((cat) => (
                  <div key={cat.id}>
                    <h2 className="text-base font-medium mb-2">{cat.nome}</h2>
                    <div className="grid grid-cols-1 gap-2">
                      {cat.produtos?.map((p: ProductType) => (
                        <ProductCard
                          key={p.id}
                          product={p}
                          onOpenDetails={openProductDetails}
                          onAdd={addToCart}
                          onAddSku={addToCartSku}
                          grupoLabel={conta?.grupoClienteDescricao}
                          disabled={!guest}
                          hasPairingsHint={productPairingsMap[p.id]}
                          mesaSlug={mesaSlug}
                          t={t}
                        />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tab: Meus pedidos */}
      {tab==='pedidos' && guest && (
        <div>
          <div className="flex items-center gap-2 mb-3">
            <button className={`px-3 py-1.5 rounded-full border font-medium transition-colors shadow-sm ${pedidosView==='mine' ? 'bg-accent text-accent-foreground border-accent' : 'bg-white mesa-text border-accent/30 hover:bg-accent/10'}`} onClick={()=>setPedidosView('mine')}>{t('mesa.orders.toggle.mine')}</button>
            <button className={`px-3 py-1.5 rounded-full border font-medium transition-colors shadow-sm ${pedidosView==='mesa' ? 'bg-accent text-accent-foreground border-accent' : 'bg-white mesa-text border-accent/30 hover:bg-accent/10'}`} onClick={()=>{ setPedidosView('mesa'); refreshContaMesa(guest.sessaoMesaId); }}>{t('mesa.orders.toggle.table')}</button>
          </div>
          {pedidosView==='mine' && conta?.itens && conta.itens.length > 0 && (
          <div className="space-y-3 text-sm">
            {Object.entries(
              (conta.itens || []).reduce<Record<string, typeof conta.itens>>((acc, it) => {
                const key = String(it.pedidoId);
                acc[key] = acc[key] || [];
                acc[key].push(it);
                return acc;
              }, {})
            ).sort((a,b) => {
              const d1 = new Date(a[1][0].pedidoCriadoEm).getTime();
              const d2 = new Date(b[1][0].pedidoCriadoEm).getTime();
              return d2 - d1;
            }).map(([pedidoId, itens]) => {
              const created = new Date(itens[0].pedidoCriadoEm).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
              const status = itens[0].pedidoStatus;
              return (
                <div key={pedidoId} className="border border-accent/20 rounded-lg bg-white shadow-sm">
                  <div className="flex items-center justify-between px-3 py-2 bg-accent/5 rounded-t">
                    <div className="font-medium">{t('mesa.orders.orderNumber', { id: pedidoId })}</div>
                    <div className={`text-xs px-2 py-0.5 rounded ${badgeClass(status)}`}>{statusLabel(status)}</div>
                  </div>
                  <div className="px-3 py-2 text-xs text-mesa-text/60">
                    {t('mesa.orders.madeAt', { created })}
                  </div>
                  <div className="divide-y">
                    {itens.map(it => {
                      const isCanceled = it.status?.toLowerCase() === 'canceled';
                      return (
                        <div key={it.itemPedidoId} className={`px-3 py-2 ${isCanceled ? 'opacity-60' : ''}`}>
                          <div className="flex justify-between">
                            <div>
                              <div className={`font-medium text-mesa-text ${isCanceled ? 'line-through' : ''}`}>{it.produtoNome}</div>
                              <div className="text-mesa-text/70">{t('mesa.orders.qtyLabel')} {it.quantidade} • <span className={`px-1 rounded ${badgeClass(it.status)}`}>{statusLabel(it.status)}</span></div>
                              {it.observacoes && (
                                <div className="text-xs text-mesa-text/60 italic mt-1 flex items-center gap-1">
                                  <span>📝</span>
                                  <span>{it.observacoes}</span>
                                </div>
                              )}
                              {isCanceled && (
                                <div className="text-xs text-red-500 mt-1 flex items-center gap-1">
                                  <span>❌</span>
                                  <span>{t('mesa.orders.canceledNotice')}</span>
                                </div>
                              )}
                            </div>
                            <div className={isCanceled ? 'line-through' : ''}>R$ {((it.precoUnitCentavos * it.quantidade)/100).toFixed(2)}</div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
          )}

          {pedidosView==='mesa' && contaMesa && contaMesa.pessoas && (
            <MesaPedidosList sessaoMesaId={guest.sessaoMesaId} pessoas={contaMesa.pessoas} mesaSlug={mesaSlug} t={t} />
          )}
        </div>
      )}

      {/* Tab: Minha conta */}
      {tab==='conta' && guest && (
        <div>
          <div className="flex items-center gap-2 mb-3">
            <button className={`px-3 py-1.5 rounded-full border font-medium transition-colors shadow-sm ${contaView==='mine' ? 'bg-accent text-accent-foreground border-accent' : 'bg-white text-accent border-accent/30 hover:bg-accent/10'}`} onClick={()=>setContaView('mine')}>{t('mesa.account.toggle.mine')}</button>
            <button className={`px-3 py-1.5 rounded-full border font-medium transition-colors shadow-sm ${contaView==='mesa' ? 'bg-accent text-accent-foreground border-accent' : 'bg-white text-accent border-accent/30 hover:bg-accent/10'}`} onClick={()=>{ setContaView('mesa'); refreshContaMesa(guest.sessaoMesaId); }}>{t('mesa.account.toggle.table')}</button>
          </div>
          {contaView==='mine' && (
            <>
              <div className="flex items-center gap-2 mb-2">
                <button className="border border-accent/30 px-3 py-1 rounded-lg hover:bg-accent/10 bg-white shadow-sm font-medium" onClick={() => refreshConta(guest.sessaoConvidadoId)}>{t('mesa.account.refresh')}</button>
                <button
                  className="border border-accent/30 px-3 py-1 rounded-lg bg-white shadow-sm font-medium disabled:opacity-60"
                  onClick={solicitarFechamento}
                  disabled={checkoutRequested}
                >
                  {checkoutButtonLabel}
                </button>
              </div>
              <div className="text-xs text-muted-foreground mb-2">{checkoutStatusMessage}</div>
              {conta && (
                <div className="text-sm space-y-1">
                  <div className="flex justify-between">
                    <span>{t('mesa.account.summary.subtotalLabel')}</span>
                    <span>{formatCurrency(conta.subtotalCentavos)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>{t('mesa.account.summary.serviceFeeLabel')}</span>
                    <span>{formatCurrency(contaPessoa?.taxaServicoPendenteCentavos ?? 0)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>{t('mesa.account.summary.paidLabel')}</span>
                    <span>{formatCurrency((contaPessoa?.pagoCentavos ?? conta.pagoCentavos) ?? 0)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>{t('mesa.account.summary.dueLabel')}</span>
                    <span>{formatCurrency((contaPessoa?.devidoTotalCentavos ?? conta.devidoCentavos) ?? 0)}</span>
                  </div>
                </div>
              )}
              {payUnlocked && (
                <div className="mt-3 space-y-3">
                      <div className="flex flex-wrap gap-2">
                        <Button
                          variant={paymentMethod === "CARD" ? "secondary" : "outline"}
                          onClick={() => setPaymentMethod("CARD")}
                        >
                          {t('mesa.paymentMethod.card')}
                        </Button>
                        <Button
                          variant={paymentMethod === "PIX" ? "secondary" : "outline"}
                          onClick={() => setPaymentMethod("PIX")}
                        >
                          {t('mesa.paymentMethod.pix')}
                        </Button>
                      </div>
                  {paymentMethod === "PIX" ? (
                    <div className="space-y-3">
                          <div className="text-sm text-muted-foreground">
                            {t('mesa.pix.instructions')}
                          </div>
                      {activeGateway === "PAGSEGURO" ? (
                        <div>
                            <Label htmlFor="pixCpf" className="text-xs uppercase tracking-wide text-muted-foreground">
                              {t('mesa.pix.cpfLabel')}
                            </Label>
                          <Input
                            id="pixCpf"
                            placeholder={t('mesa.form.payments.cpf.placeholder')}
                            value={cpf}
                            onChange={(event) => setCpf(event.target.value)}
                          />
                        </div>
                      ) : null}
                      {qrPayload ? (
                        <div className="space-y-2">
                          <div className="text-xs uppercase tracking-wide text-muted-foreground">{t('mesa.pix.copyLabel')}</div>
                          <div className="flex items-start gap-2">
                            <textarea
                              readOnly
                              className="w-full text-xs border rounded-md p-2 bg-muted/40"
                              rows={3}
                              value={qrPayload}
                            />
                                <Button
                                  variant="outline"
                                  onClick={() => {
                                    if (qrPayload) navigator.clipboard?.writeText(qrPayload);
                                  }}
                                >
                                  {t('mesa.pix.copyButton')}
                                </Button>
                          </div>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setShowPixQr((prev) => !prev)}
                          >
                            {showPixQr ? t('mesa.pix.hideQrButton') : t('mesa.pix.showQrButton')}
                          </Button>
                        </div>
                      ) : null}
                      {showPixQr && qrImageUrl ? (
                        <div className="flex justify-center">
                          <img src={qrImageUrl} alt={t('mesa.pix.qrAlt')} className="w-60 h-60 border rounded bg-white" />
                        </div>
                      ) : null}
                      <div className="flex items-center justify-between text-xs text-muted-foreground">
                        <span>{t('mesa.pix.statusLabel')}: {pixStatus || t('mesa.pix.status.pending')}</span>
                        {pixExpiresAt ? (
                          <span>{t('mesa.pix.expiresLabel')}: {new Date(pixExpiresAt).toLocaleString('pt-BR')}</span>
                        ) : null}
                      </div>
                      <Button
                        className="w-full"
                        size="lg"
                        onClick={() => pagarPix(false)}
                        disabled={paymentProcessing}
                      >
                        {paymentProcessing ? t('mesa.pix.generating') : qrPayload ? t('mesa.pix.generateNew') : t('mesa.pix.generate')}
                      </Button>
                      {pixMessage ? <div className="text-xs text-muted-foreground/80">{pixMessage}</div> : null}
                    </div>
                  ) : (
                    <div className="space-y-2">
                      <Label htmlFor="cardNumber" className="text-xs uppercase tracking-wide text-muted-foreground">
                        {t('mesa.form.payments.cardNumberLabel')}
                      </Label>
                      <div className="relative">
                        <Input
                          id="cardNumber"
                          placeholder={t('mesa.form.payments.cardNumber.placeholder')}
                          value={cardNumber}
                          onChange={(event) => setCardNumber(formatCardNumber(event.target.value))}
                          maxLength={19}
                        />
                        <CreditCard className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                        {cardBrand && (
                          <span className="absolute right-12 top-1/2 -translate-y-1/2 text-xs font-semibold uppercase text-muted-foreground">
                            {cardBrand}
                          </span>
                        )}
                      </div>
                      <div>
                        <Label htmlFor="cardHolder" className="text-xs uppercase tracking-wide text-muted-foreground">
                          {t('mesa.form.payments.cardHolderLabel')}
                        </Label>
                        <Input
                          id="cardHolder"
                          placeholder={t('mesa.form.payments.cardHolder.placeholder')}
                          value={cardHolder}
                          onChange={(event) => setCardHolder(event.target.value.toUpperCase())}
                        />
                      </div>
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <Label htmlFor="expiryDate" className="text-xs uppercase tracking-wide text-muted-foreground">
                            {t('mesa.form.payments.expirationLabel')}
                          </Label>
                          <Input
                            id="expiryDate"
                            placeholder={t('mesa.form.payments.expiration.placeholder')}
                            value={cardExpMonth && cardExpYear ? `${cardExpMonth}/${cardExpYear}` : ""}
                            onChange={(event) => {
                              const [month, year = ""] = formatExpiry(event.target.value).split("/");
                              setCardExpMonth(month);
                              setCardExpYear(year);
                            }}
                            maxLength={5}
                          />
                        </div>
                        <div>
                          <Label htmlFor="cvv" className="text-xs uppercase tracking-wide text-muted-foreground">
                            {t('mesa.form.payments.cvvLabel')}
                          </Label>
                          <Input
                            id="cvv"
                            type="password"
                            placeholder={t('mesa.form.payments.cvv.placeholder')}
                            value={cardCvv}
                            onChange={(event) => setCardCvv(event.target.value.replace(/\D/g, ""))}
                            maxLength={4}
                          />
                        </div>
                      </div>
                      <div>
                          <Label htmlFor="cardDoc" className="text-xs uppercase tracking-wide text-muted-foreground">
                            {t('mesa.form.payments.cardCpfLabel')}
                          </Label>
                        <Input
                          id="cardDoc"
                          placeholder={t('mesa.form.payments.cpf.placeholder')}
                          maxLength={14}
                          value={cardDoc}
                          onChange={(event) => setCardDoc(formatCpf(event.target.value))}
                        />
                      </div>
                      <div>
                        <Label className="text-xs uppercase tracking-wide text-muted-foreground">
                          {t('mesa.form.payments.installmentsLabel')}
                        </Label>
                        <Select
                          value={cardInstallments.toString()}
                          onValueChange={(value) => setCardInstallments(Number(value))}
                        >
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {[1, 2, 3, 4].map((option) => (
                              <SelectItem key={option} value={option.toString()}>
                                {option}x
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>
                      <Button variant="ghost" size="sm" onClick={fillTestCard} className="w-full">
                        {t('mesa.form.payments.fillTestData')}
                      </Button>
                      <Button
                        className="w-full flex items-center justify-center gap-2"
                        variant="secondary"
                        disabled={creatingToken || paymentProcessing}
                        onClick={() => pagarCard(false)}
                      >
                        {paymentProcessing ? (
                          <>
                            <CreditCard className="h-4 w-4 animate-spin" />
                            {t('mesa.form.payments.processing')}
                          </>
                        ) : (
                          <>
                            <CreditCard className="h-4 w-4" />
                            {t('mesa.form.payments.payWithCard')}
                          </>
                        )}
                      </Button>
                    </div>
                  )}
                </div>
              )}
            </>
          )}

          {contaView==='mesa' && contaMesa && (
            <div>
              <div className="text-sm space-y-1">
                <div className="flex justify-between">
                  <span>{t('mesa.account.summary.subtotalLabel')}</span>
                  <span>{formatCurrency(contaMesa.subtotalCentavos)}</span>
                </div>
                <div className="flex justify-between">
                  <span>{t('mesa.account.summary.serviceFeeLabel')}</span>
                  <span>{formatCurrency(contaMesa.taxaServicoPendenteCentavos)}</span>
                </div>
                <div className="flex justify-between">
                  <span>{t('mesa.account.summary.paidLabel')}</span>
                  <span>{formatCurrency(contaMesa.pagoCentavos)}</span>
                </div>
                <div className="flex justify-between">
                  <span>{t('mesa.account.summary.dueLabel')}</span>
                  <span>{formatCurrency(contaMesa.devidoTotalCentavos)}</span>
                </div>
              </div>
              <div className="mt-2 flex items-center gap-2">
                <button
                  className="border border-accent/30 px-3 py-1 rounded-lg bg-white shadow-sm font-medium disabled:opacity-60"
                  onClick={solicitarFechamento}
                  disabled={checkoutRequested}
                >
                  {checkoutButtonLabel}
                </button>
              </div>
              <div className="mt-2 text-xs text-muted-foreground">{checkoutStatusMessage}</div>
              <div className="mt-3 grid grid-cols-1 gap-2">
                {payUnlocked && (
                  <div className="space-y-3">
                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant={paymentMethod === "CARD" ? "secondary" : "outline"}
                        onClick={() => setPaymentMethod("CARD")}
                      >
                        {t('mesa.paymentMethod.card')}
                      </Button>
                      <Button
                        variant={paymentMethod === "PIX" ? "secondary" : "outline"}
                        onClick={() => setPaymentMethod("PIX")}
                      >
                        {t('mesa.paymentMethod.pix')}
                      </Button>
                    </div>
                    {paymentMethod === "PIX" ? (
                      <div className="space-y-3">
                        <div className="text-sm text-muted-foreground">
                          {t('mesa.pix.instructions')}
                        </div>
                        {activeGateway === "PAGSEGURO" ? (
                          <div>
                          <Label htmlFor="pixCpfMesa" className="text-xs uppercase tracking-wide text-muted-foreground">
                            {t('mesa.pix.cpfLabel')}
                          </Label>
                          <Input
                            id="pixCpfMesa"
                            placeholder={t('mesa.form.payments.cpf.placeholder')}
                            value={cpf}
                            onChange={(event) => setCpf(event.target.value)}
                          />
                          </div>
                        ) : null}
                        {qrPayload ? (
                          <div className="space-y-2">
                            <div className="text-xs uppercase tracking-wide text-muted-foreground">{t('mesa.pix.copyLabel')}</div>
                            <div className="flex items-start gap-2">
                              <textarea
                                readOnly
                                className="w-full text-xs border rounded-md p-2 bg-muted/40"
                                rows={3}
                                value={qrPayload}
                              />
                              <Button
                                variant="outline"
                                onClick={() => {
                                  if (qrPayload) navigator.clipboard?.writeText(qrPayload);
                                }}
                              >
                                {t('mesa.pix.copyButton')}
                              </Button>
                            </div>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => setShowPixQr((prev) => !prev)}
                            >
                              {showPixQr ? t('mesa.pix.hideQrButton') : t('mesa.pix.showQrButton')}
                            </Button>
                          </div>
                        ) : null}
                        {showPixQr && qrImageUrl ? (
                        <div className="flex justify-center">
                          <img src={qrImageUrl} alt={t('mesa.pix.qrAlt')} className="w-60 h-60 border rounded bg-white" />
                        </div>
                        ) : null}
                        <div className="flex items-center justify-between text-xs text-muted-foreground">
                          <span>{t('mesa.pix.statusLabel')}: {pixStatus || t('mesa.pix.status.pending')}</span>
                          {pixExpiresAt ? (
                            <span>{t('mesa.pix.expiresLabel')}: {new Date(pixExpiresAt).toLocaleString('pt-BR')}</span>
                          ) : null}
                        </div>
                        <Button
                          className="w-full"
                          size="lg"
                          onClick={() => pagarPix(true)}
                          disabled={paymentProcessing}
                        >
                          {paymentProcessing ? t('mesa.pix.generating') : qrPayload ? t('mesa.pix.generateNew') : t('mesa.pix.generate')}
                        </Button>
                        {pixMessage ? <div className="text-xs text-muted-foreground/80">{pixMessage}</div> : null}
                      </div>
                    ) : (
                      <div className="space-y-2">
                        <Label htmlFor="cardNumberMesa" className="text-xs uppercase tracking-wide text-muted-foreground">
                          {t('mesa.form.payments.cardNumberLabel')}
                        </Label>
                        <div className="relative">
                          <Input
                            id="cardNumberMesa"
                            placeholder={t('mesa.form.payments.cardNumber.placeholder')}
                            value={cardNumber}
                            onChange={(event) => setCardNumber(formatCardNumber(event.target.value))}
                            maxLength={19}
                          />
                          <CreditCard className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                          {cardBrand && (
                            <span className="absolute right-12 top-1/2 -translate-y-1/2 text-xs font-semibold uppercase text-muted-foreground">
                              {cardBrand}
                            </span>
                          )}
                        </div>
                        <div>
                          <Label htmlFor="cardHolderMesa" className="text-xs uppercase tracking-wide text-muted-foreground">
                            {t('mesa.form.payments.cardHolderLabel')}
                          </Label>
                          <Input
                            id="cardHolderMesa"
                            placeholder={t('mesa.form.payments.cardHolder.placeholder')}
                            value={cardHolder}
                            onChange={(event) => setCardHolder(event.target.value.toUpperCase())}
                          />
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                          <div>
                          <Label htmlFor="expiryDateMesa" className="text-xs uppercase tracking-wide text-muted-foreground">
                            {t('mesa.form.payments.expirationLabel')}
                          </Label>
                          <Input
                            id="expiryDateMesa"
                            placeholder={t('mesa.form.payments.expiration.placeholder')}
                            value={cardExpMonth && cardExpYear ? `${cardExpMonth}/${cardExpYear}` : ""}
                            onChange={(event) => {
                              const [month, year = ""] = formatExpiry(event.target.value).split("/");
                              setCardExpMonth(month);
                              setCardExpYear(year);
                            }}
                            maxLength={5}
                          />
                          </div>
                          <div>
                          <Label htmlFor="cvvMesa" className="text-xs uppercase tracking-wide text-muted-foreground">
                            {t('mesa.form.payments.cvvLabel')}
                          </Label>
                          <Input
                            id="cvvMesa"
                            type="password"
                            placeholder={t('mesa.form.payments.cvv.placeholder')}
                            value={cardCvv}
                            onChange={(event) => setCardCvv(event.target.value.replace(/\D/g, ""))}
                            maxLength={4}
                          />
                          </div>
                        </div>
                        <div>
                          <Label htmlFor="cardDocMesa" className="text-xs uppercase tracking-wide text-muted-foreground">
                            {t('mesa.form.payments.cardCpfLabel')}
                          </Label>
                        <Input
                          id="cardDocMesa"
                          placeholder={t('mesa.form.payments.cpf.placeholder')}
                          maxLength={14}
                          value={cardDoc}
                          onChange={(event) => setCardDoc(formatCpf(event.target.value))}
                        />
                        </div>
                        <div>
                          <Label className="text-xs uppercase tracking-wide text-muted-foreground">
                            {t('mesa.form.payments.installmentsLabel')}
                          </Label>
                          <Select
                            value={cardInstallments.toString()}
                            onValueChange={(value) => setCardInstallments(Number(value))}
                          >
                            <SelectTrigger>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              {[1, 2, 3, 4].map((option) => (
                                <SelectItem key={option} value={option.toString()}>
                                  {option}x
                                </SelectItem>
                        ))}
                          </SelectContent>
                        </Select>
                      </div>
                        <Button variant="ghost" size="sm" onClick={fillTestCard} className="w-full">
                          {t('mesa.form.payments.fillTestData')}
                        </Button>
                        <Button
                          className="w-full flex items-center justify-center gap-2"
                          variant="secondary"
                          disabled={creatingToken || paymentProcessing}
                          onClick={() => pagarCard(true)}
                          >
                            {paymentProcessing ? (
                              <>
                                <CreditCard className="h-4 w-4 animate-spin" />
                                {t('mesa.form.payments.processing')}
                              </>
                            ) : (
                              <>
                                <CreditCard className="h-4 w-4" />
                                {t('mesa.form.payments.payWithCardMesa')}
                              </>
                          )}
                        </Button>
                      </div>
                    )}
                  </div>
                )}
              </div>

            </div>
          )}
      </div>
      )}
      </div>

      {/* Dialog de detalhes do produto */}
      <ProductDetailsDialog
        open={detailsOpen}
        onOpenChange={setDetailsOpen}
        product={detailProduct}
        onAddSku={addToCartSku}
        onAdd={addToCart}
        pairings={pairings}
        mesaSlug={mesaSlug}
        t={t}
      />

      {/* FAB chamar garçom */}
      <button
        className={`fixed right-4 z-50 flex items-center justify-center w-12 h-12 rounded-full bg-accent text-mesa-text shadow-lg hover:bg-accent/90 ${
          showCartBar ? 'bottom-[140px]' : 'bottom-14'
        }`}
        title={t('mesa.dialog.callWaiter.title')}
        aria-label={t('mesa.dialog.callWaiter.title')}
        onClick={() => setShowChamarGarcomDialog(true)}
      >
        <svg viewBox="0 0 64 64" className="w-6 h-6" aria-hidden="true">
          <path
            fill="currentColor"
            d="M32 12c-1.1 0-2 .9-2 2v3.1C20.6 19.7 13 28.7 12.1 39H9c-1.7 0-3 1.3-3 3s1.3 3 3 3h46c1.7 0 3-1.3 3-3s-1.3-3-3-3h-3.1C51 28.7 43.4 19.7 34 17.1V14c0-1.1-.9-2-2-2zM18.2 39c1.2-8.6 8.6-15 16.8-15s15.6 6.4 16.8 15H18.2z"
          />
        </svg>
      </button>

      <Dialog open={showChamarGarcomDialog} onOpenChange={setShowChamarGarcomDialog}>
        <DialogContent className="bg-white text-mesa-text border border-accent/20 w-[92vw] sm:w-auto max-w-sm">
          <DialogHeader>
            <DialogTitle className="text-base">{t('mesa.dialog.callWaiter.title')}</DialogTitle>
            <DialogDescription className="text-sm text-mesa-text/70">
              {t('mesa.dialog.callWaiter.description')}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
                <div>
                  <label htmlFor="observacaoGarcom" className="text-sm text-mesa-text/80 mb-1 block">
                    {t('mesa.dialog.callWaiter.noteLabel')}
                  </label>
                  <input
                    id="observacaoGarcom"
                    type="text"
                    value={observacaoGarcom}
                    onChange={(e) => setObservacaoGarcom(e.target.value)}
                    placeholder={t('mesa.form.observation.placeholder')}
                    className="w-full border border-accent/30 rounded px-3 py-2 text-mesa-text bg-white placeholder:text-mesa-text/50 focus:outline-none focus:ring-2 focus:ring-accent/30"
                    maxLength={200}
                  />
                </div>
            <div className="flex items-center justify-end gap-2 pt-2">
              <Button variant="outline" onClick={() => {
                setShowChamarGarcomDialog(false);
                setObservacaoGarcom(''); // Limpar ao cancelar
              }}>
                {t('mesa.dialog.callWaiter.cancelButton')}
              </Button>
              <Button
                className="bg-accent text-mesa-text hover:bg-accent/90"
                onClick={async () => {
                  await chamarGarcom();
                  setShowChamarGarcomDialog(false);
                }}
                disabled={chamandoGarcom}
              >
                {chamandoGarcom ? t('mesa.dialog.callWaiter.calling') : t('mesa.dialog.callWaiter.confirmButton')}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Barra fixa do carrinho quando lista é longa */}
      {showCartBar && (
        <div className="fixed bottom-0 inset-x-0 z-40 bg-white border-t shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.1)] border-accent/20 pb-[env(safe-area-inset-bottom)]">
            <div className="max-w-md mx-auto px-3 py-2 flex items-center justify-between gap-2">
              <div className="min-w-0 text-xs sm:text-sm text-mesa-text whitespace-nowrap">
                <span className="font-medium">
                  {(() => {
                    const totalItems = cart.reduce((s, c) => s + c.quantidade, 0);
                    return `${totalItems} ${totalItems === 1 ? t('mesa.cart.itemSingular') : t('mesa.cart.itemPlural')}`;
                  })()}
                </span>
                <span className="mx-2 text-mesa-text/50">•</span>
                <span className="font-medium">R$ {orderTotal.toFixed(2)}</span>
                {cartHasPromocao ? <span className="ml-2 font-medium text-[11px]">🔥 {t('mesa.cart.promotion')}</span> : null}
                {cartHasSocio ? <span className="ml-2 font-medium text-[11px]">💎 {t('mesa.cart.memberPrice')}</span> : null}
              </div>
              <div className="flex items-center gap-2">
                <button
                  className="h-9 sm:h-10 px-3 rounded border border-accent/30 text-mesa-text text-xs sm:text-sm hover:bg-accent/10 whitespace-nowrap"
                  onClick={() => setCartOpen(true)}
                >
                  {t('mesa.cart.view')}
                </button>
                <button
                  className="h-9 sm:h-10 px-4 rounded bg-accent hover:bg-accent/90 text-mesa-text text-xs sm:text-sm"
                  onClick={handleSendOrderRequest}
                  disabled={sendingOrder || !guest}
                >
                  {sendingOrder ? t('mesa.cart.sending') : t('mesa.cart.send')}
                </button>
              </div>
            </div>
        </div>
      )}

      {/* Bottom sheet com carrinho detalhado */}
      <Sheet open={cartOpen} onOpenChange={setCartOpen}>
            <SheetContent side="bottom" className="bg-white text-mesa-text border-t border-accent/20 p-0">
          <div className="max-w-md mx-auto w-full">
            <SheetHeader className="px-4 py-3 border-b border-accent/20">
              <SheetTitle className="text-sm tracking-wide text-mesa-text/80">{t('mesa.cart.title')}</SheetTitle>
            </SheetHeader>
            <div className="p-4">
              {cart.length === 0 ? (
                <div className="text-sm text-mesa-text/60">{t('mesa.cart.emptyShort')}</div>
              ) : (
                <div className="space-y-3">
                  {cart.map((c) => {
                    const keyId = c.skuId ?? c.produtoId;
                    return (
                      <CartItem
                        key={keyId}
                        id={keyId}
                        item={c}
                        onRemove={removeFromCart}
                        onIncrease={increaseQuantity}
                        onDecrease={decreaseQuantity}
                        onChangeObservacao={updateCartObservacoes}
                        mesaSlug={mesaSlug}
                        t={t}
                      />
                    );
                  })}
                    <div className="border-t border-accent/20 pt-3">
                    <div className="flex justify-between font-medium mb-3 text-sm sm:text-base"><span>{t('mesa.cart.totalLabel')}</span><span>R$ {orderTotal.toFixed(2)}</span></div>
                    <button className="bg-accent hover:bg-accent/90 text-mesa-text px-4 py-2 rounded w-full" onClick={handleSendOrderRequest} disabled={sendingOrder || !guest}>{sendingOrder ? t('mesa.cart.sending') : t('mesa.cart.submitOrder')}</button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </SheetContent>
      </Sheet>

      {/* Confirmação de envio do pedido */}
      <Dialog open={orderConfirmOpen} onOpenChange={setOrderConfirmOpen}>
        <DialogContent className="bg-white text-mesa-text border-accent/20 w-[92vw] sm:w-auto max-w-md">
          <DialogHeader>
            <DialogTitle className="text-base">{t('mesa.dialog.confirmOrder.title')}</DialogTitle>
            <DialogDescription className="text-sm text-mesa-text/70">
              {t('mesa.dialog.confirmOrder.description')}
            </DialogDescription>
          </DialogHeader>

          {cart.length === 0 ? (
            <div className="text-sm text-mesa-text/60">{t('mesa.dialog.confirmOrder.emptyMessage')}</div>
          ) : (
            <div className="space-y-3 max-h-[360px] overflow-y-auto pr-1">
              {cart.map((c) => {
                const keyId = c.skuId ?? c.produtoId;
                return (
                  <div key={keyId} className="border border-accent/15 rounded-lg p-3 flex items-start justify-between gap-3 bg-accent/5">
                    <div className="min-w-0 space-y-1">
                      <div className="font-medium text-mesa-text break-words">
                        {c.quantidade}x {c.nome}
                      </div>
                      <div className="text-xs text-mesa-text/70">
                        R$ {c.preco.toFixed(2)} un.
                      </div>
                      {c.observacoes ? (
                        <div className="text-xs text-mesa-text/70 flex items-start gap-1">
                          <span>📝</span>
                          <span className="break-words">{c.observacoes}</span>
                        </div>
                      ) : null}
                    </div>
                    <div className="text-sm font-semibold whitespace-nowrap">
                      R$ {(c.preco * c.quantidade).toFixed(2)}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          <div className="flex items-center justify-between pt-3 text-sm font-semibold">
            <span>{t('mesa.dialog.confirmOrder.totalLabel')}</span>
            <span>R$ {orderTotal.toFixed(2)}</span>
          </div>

          <div className="flex justify-end gap-2 pt-4">
            <Button variant="outline" onClick={() => setOrderConfirmOpen(false)} disabled={sendingOrder}>
              {t('mesa.dialog.confirmOrder.backButton')}
            </Button>
            <Button
              className="bg-accent text-mesa-text hover:bg-accent/90"
              onClick={sendOrder}
              disabled={sendingOrder || cart.length === 0}
            >
              {sendingOrder ? t('mesa.dialog.confirmOrder.submitting') : t('mesa.dialog.confirmOrder.submitButton')}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <UserMenuSheet
        open={showUserMenu}
        onOpenChange={setShowUserMenu}
        guest={guest}
        user={user}
        isAuthenticated={isAuthenticated}
        onGoHome={() => navigate('/')}
        onGoFavorites={() => navigate('/favoritos')}
        onGoClientArea={() => navigate('/areacliente')}
        onOpenLocation={() => setShowLocationDialog(true)}
        onOpenShare={() => setShowShareDialog(true)}
        onLogout={() => logout()}
        mesaSlug={mesaSlug}
        t={t}
      />

      {/* Dialog de Compartilhar Mesa */}
      <Dialog open={showShareDialog} onOpenChange={setShowShareDialog}>
        <DialogContent className="bg-white text-mesa-text border-accent/20 shadow-xl">
          <DialogHeader>
            <DialogTitle className="text-mesa-text">{t('mesa.dialog.share.title', { mesaSlug })}</DialogTitle>
            <DialogDescription className="text-mesa-text/70">
              {t('mesa.dialog.share.description')}
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col items-center gap-4 py-6">
            {/* QR Code */}
            {shareQrUrl && (
              <div className="bg-white p-4 rounded-lg">
                <img
                  src={shareQrUrl}
                  alt={t('mesa.dialog.share.qrAlt', { mesaSlug })}
                  className="w-64 h-64"
                />
              </div>
            )}

            {/* URL para compartilhar */}
            <div className="w-full">
              <p className="text-xs text-mesa-text/70 mb-2">{t('mesa.dialog.share.linkLabel')}</p>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={`${window.location.origin}/m/${mesaSlug}`}
                  readOnly
                  className="flex-1 bg-white border border-accent/30 rounded px-3 py-2 text-sm text-mesa-text"
                  onClick={(e) => (e.target as HTMLInputElement).select()}
                />
                <Button
                  onClick={() => {
                    navigator.clipboard.writeText(`${window.location.origin}/m/${mesaSlug}`);
                    toast({
                      title: t('mesa.toast.share.linkCopied.title'),
                      description: t('mesa.toast.share.linkCopied.description'),
                    });
                  }}
                  className="bg-accent text-mesa-text hover:bg-accent/90"
                >
                  {t('mesa.dialog.share.copyButton')}
                </Button>
              </div>
              </div>

              {/* Informações adicionais */}
            <div className="text-center text-xs text-mesa-text/70">
              <p>{t('mesa.dialog.share.linkHelp')}</p>
              {guest && contaMesa && (
                <p className="mt-2">
                  {t('mesa.dialog.share.tableCount')} <span className="text-accent font-medium">{contaMesa.pessoas.length}</span>
                </p>
              )}
            </div>
          </div>

          <div className="flex justify-end">
            <Button
              onClick={() => setShowShareDialog(false)}
              className="bg-accent text-mesa-text hover:bg-accent/90"
            >
              {t('mesa.dialog.share.closeButton')}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={showLocationDialog} onOpenChange={setShowLocationDialog}>
        <DialogContent className="bg-white text-mesa-text border-accent/20 shadow-xl">
          <DialogHeader>
            <DialogTitle className="text-mesa-text">{t('mesa.dialog.location.title')}</DialogTitle>
            <DialogDescription className="text-mesa-text/70">
              {t('mesa.dialog.location.description')}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <Textarea
              placeholder={t('mesa.form.location.placeholder')}
              value={locationReference}
              onChange={(event) => setLocationReference(event.target.value)}
              className="w-full border border-accent/20 bg-white text-mesa-text placeholder:text-mesa-text/50 min-h-[96px] resize-none"
              maxLength={200}
              rows={4}
            />
            <div className="flex items-center justify-between text-xs text-mesa-text/70">
              <span>{t('mesa.form.location.limit')}</span>
              <span>{locationReference.length}/200</span>
            </div>
          </div>

          <div className="flex justify-end gap-2 mt-6">
            <Button
              variant="ghost"
              onClick={() => setShowLocationDialog(false)}
              disabled={updatingLocationReference}
              className="text-mesa-text hover:bg-accent/10"
            >
              {t('mesa.form.location.cancelButton')}
            </Button>
            <Button
              onClick={handleLocationSubmit}
              disabled={updatingLocationReference || locationReference.trim().length === 0}
              className="bg-accent text-mesa-text hover:bg-accent/90"
            >
              {updatingLocationReference ? t('mesa.form.location.saving') : t('mesa.form.location.saveButton')}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <style>{`.badge{font-weight:500}.no-scrollbar::-webkit-scrollbar{display:none}.no-scrollbar{-ms-overflow-style:none;scrollbar-width:none}`}</style>

      {/* Dialog para Produto Indisponível */}
      <Dialog open={showProductUnavailableDialog} onOpenChange={setShowProductUnavailableDialog}>
        <DialogContent className="bg-white text-mesa-text border border-accent/20 w-[92vw] sm:w-auto max-w-md">
          <DialogHeader>
            <DialogTitle className="text-mesa-text text-base sm:text-lg">
              {t('mesa.dialog.unavailableProduct.title')}
            </DialogTitle>
            <DialogDescription className="text-mesa-text/70 text-[13px] sm:text-sm">
              {t('mesa.dialog.unavailableProduct.description', {
                productName: unavailableProductInfo?.nome ?? '',
              })}
            </DialogDescription>
          </DialogHeader>
          {unavailableProductInfo?.horarios_disponiveis && unavailableProductInfo.horarios_disponiveis.length > 0 && (
            <div className="space-y-2 text-mesa-text/90">
              <p className="font-medium">{t('mesa.dialog.unavailableProduct.availabilityTitle')}</p>
              <ul className="list-disc pl-5">
                {unavailableProductInfo.horarios_disponiveis.map((horario, index) => (
                  <li key={index} className="text-sm">
                    {horario.diaSemana}: {horario.horarioInicio.substring(0, 5)} - {horario.horarioFim.substring(0, 5)}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div className="flex justify-end">
            <Button
              onClick={() => setShowProductUnavailableDialog(false)}
              className="bg-accent text-mesa-text hover:bg-accent/90"
            >
              {t('mesa.dialog.unavailableProduct.closeButton')}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
