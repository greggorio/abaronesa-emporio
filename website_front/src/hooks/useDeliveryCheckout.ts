import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "@/lib/axios";
import villaApi from "@/services/villaApi";
import { apiConfig } from "@/config/api";
import { CartItemData } from "@/components/mesa/CartItem";
import { CustomerData, DeliveryOrder, PaymentCardData, PaymentInfo } from "@/types/delivery";
import { useDeliveryI18n } from "@/i18n/useDeliveryI18n";

type ServiceMode = "DELIVERY" | "PICKUP";
type PaymentMethod = "CARD" | "PIX";

type UseDeliveryCheckoutParams = {
  cart: CartItemData[];
  total: number;
  onError?: (message: string) => void;
  locale?: string;
};

const initialCustomerData: CustomerData = {
  customerName: "",
  customerPhone: "",
  customerEmail: "",
  customerCpf: "",
  dropoffAddress: "",
  dropoffNotes: "",
};

const initialCardData: PaymentCardData = {
  cardNumber: "",
  cardHolder: "",
  cardExpMonth: "",
  cardExpYear: "",
  cardCvv: "",
  cardDoc: "",
  cardToken: "",
  paymentMethodId: "visa",
  installments: 1,
};

export function useDeliveryCheckout({ cart, total, onError, locale: externalLocale }: UseDeliveryCheckoutParams) {
  const navigate = useNavigate();
  const { t, locale: currentLocale, setLocale } = useDeliveryI18n();
  const [customerData, setCustomerData] = useState<CustomerData>(initialCustomerData);
  const [cardData, setCardData] = useState<PaymentCardData>(initialCardData);
  const [serviceMode, setServiceMode] = useState<ServiceMode>("DELIVERY");
  const [orderId, setOrderId] = useState<number | null>(null);
  const [orderStatus, setOrderStatus] = useState("");
  const [paymentInfo, setPaymentInfo] = useState<PaymentInfo>(null);
  const [deliveryFeeCents, setDeliveryFeeCents] = useState(0);
  const [quoteConfirmed, setQuoteConfirmed] = useState(false);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState("");
  const [quoteId, setQuoteId] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);
  const [creatingToken, setCreatingToken] = useState(false);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [orderConfirmOpen, setOrderConfirmOpen] = useState(false);
  const [paymentSuccessOpen, setPaymentSuccessOpen] = useState(false);
  const [cartOpen, setCartOpen] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CARD");
  const [activeGateway, setActiveGateway] = useState<"MERCADOPAGO" | "PAGSEGURO">("MERCADOPAGO");
  const [installmentsConfig, setInstallmentsConfig] = useState<{
    enabled: boolean;
    minAmountCents: number;
    maxTimes: number;
  }>({ enabled: true, minAmountCents: 0, maxTimes: 3 });
  const [pagseguroPublicKey, setPagseguroPublicKey] = useState("");
  const [pixData, setPixData] = useState<{
    qrCode?: string;
    qrCodeBase64?: string;
    expiresAt?: string;
    providerPaymentId?: string;
    status?: string;
    friendlyMessage?: string;
  } | null>(null);
  const pixPollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const dispatchError = useCallback(
    (message: string) => {
      if (onError) onError(message);
    },
    [onError]
  );

  useEffect(() => {
    if (externalLocale && externalLocale !== currentLocale) {
      setLocale(externalLocale as any);
    }
  }, [externalLocale, currentLocale, setLocale]);

  const stopPixPolling = useCallback(() => {
    if (pixPollRef.current) {
      clearInterval(pixPollRef.current);
      pixPollRef.current = null;
    }
  }, []);

  const shouldRefreshToken = useCallback((message?: string) => {
    if (!message) return false;
    const normalized = message.toLowerCase();
    return (
      normalized.includes("token") ||
      normalized.includes("cartão") ||
      normalized.includes("invalid") ||
      normalized.includes("corrompidos") ||
      normalized.includes("cripto")
    );
  }, []);

  const cartSignature = useMemo(
    () =>
      cart
        .map(
          (item) =>
            `${item.produtoId}-${item.skuId ?? "base"}-${item.quantidade}-${item.observacoes ?? ""}`
        )
        .join("|"),
    [cart]
  );

  const normalizePhoneForQuote = useCallback((input: string) => {
    const digits = (input || "").replace(/\D/g, "");
    if (!digits) return "";
    if (digits.startsWith("55")) {
      return `+${digits}`;
    }
    return `+55${digits}`;
  }, []);

  const buildCustomerPayload = useCallback(() => {
    const validCpf = customerData.customerCpf?.replace(/\D/g, "") || undefined;
    return {
      customerName: customerData.customerName || "Cliente",
      customerPhone: customerData.customerPhone,
      customerEmail: customerData.customerEmail,
      customerCpf: validCpf,
      dropoffAddress: customerData.dropoffAddress,
      dropoffNotes: customerData.dropoffNotes,
    };
  }, [customerData]);

  const buildItemsPayload = useCallback(
    () =>
      cart.map((item) => ({
        produtoId: item.produtoId,
        skuId: item.skuId,
        quantidade: item.quantidade,
        observacoes: item.observacoes,
      })),
    [cart]
  );

  const buildOrderPayload = useCallback(() => {
    const tipo = serviceMode === "PICKUP" ? "RETIRADA" : "DELIVERY";
    return {
      tipo,
      serviceMode,
      ...buildCustomerPayload(),
      deliveryFeeCents: serviceMode === "PICKUP" ? 0 : deliveryFeeCents,
      items: buildItemsPayload(),
    };
  }, [buildCustomerPayload, buildItemsPayload, deliveryFeeCents, serviceMode]);

  const buildQuotePayload = useCallback(() => {
    const customerPayload = buildCustomerPayload();
    return {
      ...customerPayload,
      customerPhone: normalizePhoneForQuote(customerPayload.customerPhone),
      items: buildItemsPayload(),
      serviceMode,
    };
  }, [buildCustomerPayload, buildItemsPayload, normalizePhoneForQuote, serviceMode]);

  const handleCreateOrder = useCallback(async () => {
    if (!cart.length) return null;
    setProcessing(true);
    dispatchError("");
    try {
      const payload = buildOrderPayload();
      const response =
        orderId !== null
          ? await axios.put<DeliveryOrder>(`${apiConfig.erp.deliveryOrders}/${orderId}`, payload)
          : await axios.post<DeliveryOrder>(apiConfig.erp.deliveryOrders, payload);
      setOrderId(response.data.id);
      setOrderStatus(response.data.status || "");
      setPaymentInfo(null);
      setCheckoutOpen(true);
      return response.data;
    } catch (err) {
      const errorMessage =
        err && typeof err === "object" && "response" in err && (err as any).response?.data?.error?.message
          ? (err as any).response.data.error.message
          : err instanceof Error
          ? err.message
          : t("delivery.errors.createOrder");
      dispatchError(errorMessage);
    } finally {
      setProcessing(false);
    }
    return null;
  }, [buildOrderPayload, cart.length, dispatchError, orderId, t]);

  const syncOrderDetails = useCallback(() => {
    if (!orderId) return null;
    return axios.put<DeliveryOrder>(`${apiConfig.erp.deliveryOrders}/${orderId}`, buildOrderPayload());
  }, [buildOrderPayload, orderId]);

  const friendlyPaymentMessages = useMemo(
    () => ({
      installments_excludes_country: t("delivery.errors.installmentsCountry"),
    }),
    [t]
  );

  const getFriendlyPaymentMessage = (detail?: string) => {
    if (!detail) return undefined;
    const codeMatch = detail.match(/"message"\s*:\s*"([^"]+)"/);
    const code = codeMatch?.[1];
    if (code && friendlyPaymentMessages[code]) {
      return friendlyPaymentMessages[code];
    }
    if (detail.includes("installments_excludes_country")) {
      return friendlyPaymentMessages.installments_excludes_country;
    }
    return detail;
  };

  const handleConfirmOrder = useCallback(async () => {
    const created = await handleCreateOrder();
    if (created) {
      setOrderConfirmOpen(false);
    }
    return created;
  }, [handleCreateOrder]);

  const handleBackToCart = useCallback(() => {
    setCheckoutOpen(false);
    setPaymentInfo(null);
  }, []);

  const fetchActiveGateway = useCallback(async () => {
    try {
      const response = await axios.get<{
        activeGateway: "MERCADOPAGO" | "PAGSEGURO";
        installments?: { enabled: boolean; minAmount: string; maxTimes: number };
      }>("/api/payments/config");
      if (response.data?.activeGateway) {
        setActiveGateway(response.data.activeGateway);
      }
      const inst = response.data?.installments;
      if (inst) {
        const minAmountCents = Math.round(parseFloat(inst.minAmount || "0") * 100) || 0;
        setInstallmentsConfig({
          enabled: inst.enabled,
          minAmountCents,
          maxTimes: inst.maxTimes || 1,
        });
      } else {
        setInstallmentsConfig({ enabled: true, minAmountCents: 0, maxTimes: 3 });
      }
    } catch (error) {
      console.error("Falha ao buscar gateway ativo", error);
      setInstallmentsConfig({ enabled: true, minAmountCents: 0, maxTimes: 3 });
    }
  }, []);

  useEffect(() => {
    fetchActiveGateway();
  }, [fetchActiveGateway]);

  useEffect(() => {
    return () => {
      stopPixPolling();
    };
  }, [stopPixPolling]);

  useEffect(() => {
    if (paymentMethod !== "PIX") {
      stopPixPolling();
    }
  }, [paymentMethod, stopPixPolling]);

  useEffect(() => {
    if (serviceMode === "PICKUP") {
      setQuoteError("");
      return;
    }
    setQuoteConfirmed(false);
    setDeliveryFeeCents(0);
    setQuoteId(null);
    setQuoteError("");
  }, [
    cartSignature,
    customerData.customerName,
    customerData.customerPhone,
    customerData.customerEmail,
    customerData.dropoffAddress,
    customerData.dropoffNotes,
    serviceMode,
  ]);

  useEffect(() => {
    if (serviceMode === "PICKUP") {
      setDeliveryFeeCents(0);
      setQuoteId(null);
      setQuoteError("");
      setQuoteConfirmed(true);
      return;
    }
    setQuoteConfirmed(false);
  }, [serviceMode]);

  const loadPagSeguroSdk = useCallback(() => {
    return new Promise<void>((resolve, reject) => {
      if (window.PagSeguro) {
        resolve();
        return;
      }
      const script = document.createElement("script");
      script.src = "https://assets.pagseguro.com.br/checkout-sdk-js/rc/dist/browser/pagseguro.min.js";
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error("Falha ao carregar SDK PagSeguro"));
      document.body.appendChild(script);
    });
  }, []);

  const feeAmount = deliveryFeeCents / 100;
  const totalWithFee = total + feeAmount;
  const handlePaymentSuccessNavigation = useCallback(() => {
    if (!orderId) return;
    try {
      localStorage.setItem("last_delivery_order_id", `${orderId}`);
    } catch {}
    navigate(`/delivery/pedido/${orderId}/confirmado`, {
      replace: true,
      state: {
        orderId,
        totalWithFee,
        deliveryFeeCents,
        customerData,
        items: cart,
        serviceMode,
      },
    });
  }, [cart, customerData, deliveryFeeCents, navigate, orderId, serviceMode, totalWithFee]);

  const calculateDeliveryFee = useCallback(async () => {
    if (!cart.length) {
      const message = t("delivery.errors.cartRequired");
      dispatchError(message);
      return null;
    }
    if (serviceMode === "PICKUP") {
      setDeliveryFeeCents(0);
      setQuoteId(null);
      setQuoteConfirmed(true);
      setQuoteError("");
      return { feeCents: 0 };
    }
    const requiredFields = [
      customerData.customerName,
      customerData.customerPhone,
      customerData.dropoffAddress,
    ];
    if (requiredFields.some((field) => !field?.trim())) {
      const message = t("delivery.errors.customerDataRequired");
      dispatchError(message);
      return null;
    }
    setQuoteLoading(true);
    setQuoteError("");
    try {
    const customerPayload = buildQuotePayload();
    const normalizedPhone = normalizePhoneForQuote(customerPayload.customerPhone);
    const payload = { ...customerPayload, customerPhone: normalizedPhone };
      const response = await villaApi.post<{ feeCents?: number; quoteId?: string }>("/api/delivery/payments/quote", payload);
      const feeCents = typeof response.data?.feeCents === "number" ? response.data.feeCents : 0;
      setDeliveryFeeCents(feeCents);
      setQuoteId(response.data?.quoteId ?? null);
      setQuoteConfirmed(true);
      return response.data;
    } catch (err) {
      const errorMessage =
        err && typeof err === "object" && "response" in err && (err as any).response?.data?.error?.message
          ? (err as any).response.data.error.message
          : err instanceof Error
          ? err.message
          : t("delivery.errors.calculateFee");
      setQuoteError(errorMessage);
      dispatchError(errorMessage);
    } finally {
      setQuoteLoading(false);
    }
    return null;
  }, [
    buildQuotePayload,
    cart.length,
    customerData.customerName,
    customerData.customerPhone,
    customerData.dropoffAddress,
    dispatchError,
    serviceMode,
    t,
  ]);

  const handlePayCard = useCallback(async () => {
    if (!orderId) {
      dispatchError(t("delivery.errors.orderBeforePayment"));
      return;
    }
    if (!cardData.cardToken) {
      dispatchError(t("delivery.errors.tokenBeforePayment"));
      return;
    }
    if (total <= 0) {
      dispatchError(t("delivery.errors.invalidAmount"));
      return;
    }

    setProcessing(true);
    dispatchError("");
    setPaymentInfo(null);

    try {
      await syncOrderDetails();
      const payload = {
        amount: Number(totalWithFee.toFixed(2)),
        paymentMethodId: cardData.paymentMethodId || "credit_card",
        installments: cardData.installments || 1,
        token: cardData.cardToken,
        description: `Pedido delivery #${orderId}`,
        externalReference: `${orderId}`,
        payerEmail: customerData.customerEmail || "cliente@example.com",
        payerName: customerData.customerName || "Cliente",
        payerTaxId: cardData.cardDoc.replace(/\D/g, "") || undefined,
        serviceMode,
      };

      const response = await axios.post("/api/payments/card", payload);
      const payment = response.data as any;
      const backendFriendly = payment?.friendlyMessage;
      const friendlyMessage = backendFriendly ?? getFriendlyPaymentMessage(payment?.message);
      setPaymentInfo({
        id: payment?.providerPaymentId,
        status: payment?.status,
        statusDetail: payment?.message,
        friendlyMessage,
      });

      if (payment?.status) {
        setOrderStatus(payment.status);
        const statusStr = String(payment.status).toLowerCase();
        const isSuccess = ["approved", "authorized", "paid"].includes(statusStr);
        if (isSuccess && orderId) {
          handlePaymentSuccessNavigation();
        }
      }
    } catch (err) {
      const errorMessage =
        err && typeof err === "object" && "response" in err && (err as any).response?.data?.message
          ? (err as any).response.data.message
          : err instanceof Error
          ? err.message
          : t("delivery.errors.generalPayment");
      const backendFriendlyFromError = err && typeof err === "object" && "response" in err ? (err as any).response?.data?.friendlyMessage : undefined;
      const errDetail = err && typeof err === "object" && "response" in err ? (err as any).response?.data?.message : errorMessage;
      const friendlyMessage = backendFriendlyFromError ?? getFriendlyPaymentMessage(errDetail);
      setPaymentInfo({
        status: (err as any).response?.status?.toString() || "FAILED",
        statusDetail: errDetail,
        friendlyMessage,
      });
      if (shouldRefreshToken(friendlyMessage) || shouldRefreshToken(errDetail)) {
        updateCardField("cardToken", "");
      }
      dispatchError(friendlyMessage || errorMessage);
    } finally {
      setProcessing(false);
    }
  }, [
    cardData,
    customerData.customerEmail,
    customerData.customerName,
    dispatchError,
    orderId,
    syncOrderDetails,
    totalWithFee,
    navigate,
    deliveryFeeCents,
    cart,
    serviceMode,
    t,
  ]);

  const startPixPolling = useCallback(() => {
    stopPixPolling();
    if (!orderId) return;
    pixPollRef.current = setInterval(async () => {
      try {
        const res = await axios.get("/api/payments/status", {
          params: { externalReference: `${orderId}`, gateway: activeGateway },
        });
        const data = res.data as any;
        const normalized = (data?.normalizedStatus || "").toString().toUpperCase();
        setPaymentInfo((prev) => ({
          ...(prev || {}),
          id: data?.providerPaymentId,
          status: normalized || prev?.status,
          statusDetail: data?.providerStatus,
          friendlyMessage: prev?.friendlyMessage,
        }));
        setOrderStatus(normalized);
        setPixData((prev) => (prev ? { ...prev, status: normalized } : prev));
        if (normalized === "PAID") {
          stopPixPolling();
          handlePaymentSuccessNavigation();
        } else if (normalized === "CANCELED" || normalized === "EXPIRED") {
          stopPixPolling();
          dispatchError(t("delivery.errors.paymentNotCompleted", { status: normalized }));
        }
      } catch (error) {
        // Tolerar falhas temporárias de polling
      }
    }, 4000);
  }, [activeGateway, dispatchError, handlePaymentSuccessNavigation, orderId, stopPixPolling, t]);

  const handlePayPix = useCallback(async () => {
    if (!orderId) {
      dispatchError(t("delivery.errors.pixBeforeOrder"));
      return;
    }
    if (total <= 0) {
      dispatchError(t("delivery.errors.pixInvalidAmount"));
      return;
    }

    setProcessing(true);
    dispatchError("");
    setPaymentInfo(null);
    try {
      await syncOrderDetails();
      const taxId = (customerData.customerCpf || cardData.cardDoc || "").replace(/\D/g, "");
      const payload = {
        amount: Number(totalWithFee.toFixed(2)),
        externalReference: `${orderId}`,
        description: `Pedido delivery #${orderId}`,
        payerEmail: customerData.customerEmail || "cliente@example.com",
        payerName: customerData.customerName || "Cliente",
        payerTaxId: taxId || undefined,
      };
      const response = await axios.post("/api/payments/pix", payload);
      const payment = response.data as any;
      const normalizedStatus = (payment?.status || "").toString().toUpperCase();
      const pendingMessage =
        normalizedStatus === "PENDING" || normalizedStatus === "IN_PROCESS"
          ? t("delivery.payment.pix.instructions.existing")
          : undefined;
      const friendlyMessage = pendingMessage || payment?.friendlyMessage;
      setPixData({
        qrCode: payment?.pixQrCode || payment?.message,
        qrCodeBase64: payment?.pixQrCodeBase64,
        expiresAt: payment?.expiresAt,
        providerPaymentId: payment?.providerPaymentId,
        status: normalizedStatus || payment?.status,
        friendlyMessage,
      });
      setPaymentInfo({
        id: payment?.providerPaymentId,
        status: normalizedStatus || payment?.status,
        statusDetail: payment?.message,
        friendlyMessage,
      });
      startPixPolling();
    } catch (err) {
      const errorMessage =
        err && typeof err === "object" && "response" in err && (err as any).response?.data?.message
          ? (err as any).response.data.message
          : err instanceof Error
          ? err.message
          : t("delivery.errors.pixGeneration");
      dispatchError(errorMessage);
    } finally {
      setProcessing(false);
    }
  }, [
    cardData.cardDoc,
    customerData.customerCpf,
    customerData.customerEmail,
    customerData.customerName,
    dispatchError,
    handlePaymentSuccessNavigation,
    orderId,
    startPixPolling,
    syncOrderDetails,
    total,
    totalWithFee,
    t,
  ]);

  const ensurePagSeguroKey = useCallback(async () => {
    if (pagseguroPublicKey) return pagseguroPublicKey;
    const response = await axios.get<{ publicKey: string }>('/api/v1/payments/pagseguro/public-key');
    if (response.data?.publicKey) {
      setPagseguroPublicKey(response.data.publicKey);
      return response.data.publicKey;
    }
    return "";
  }, [pagseguroPublicKey]);

  const handleCreateToken = useCallback(async () => {
    setCreatingToken(true);
    dispatchError("");
    setPaymentInfo(null);
    try {
      if (activeGateway === "PAGSEGURO") {
        const publicKey = await ensurePagSeguroKey();
        await loadPagSeguroSdk();
        const encrypted = window.PagSeguro?.encryptCard({
          publicKey,
          holder: cardData.cardHolder,
          number: cardData.cardNumber.replace(/\s+/g, ""),
          expMonth: cardData.cardExpMonth,
          expYear: cardData.cardExpYear,
          securityCode: cardData.cardCvv,
        });
        const token = encrypted?.encryptedCard || encrypted?.encrypted || encrypted;
        if (!token) throw new Error("Falha ao criptografar cartão");
        setCardData((prev) => ({ ...prev, cardToken: token }));
      } else {
        const payload = {
          cardNumber: cardData.cardNumber.replace(/\s+/g, ""),
          cardholderName: cardData.cardHolder,
          cardExpirationMonth: cardData.cardExpMonth,
          cardExpirationYear: cardData.cardExpYear,
          securityCode: cardData.cardCvv,
          identificationType: "CPF",
          identificationNumber: cardData.cardDoc.replace(/\D/g, ""),
        };
        const response = await axios.post<{ token: string }>("/api/v1/payments/create-token", payload);
        if (response.data?.token) {
          setCardData((prev) => ({ ...prev, cardToken: response.data.token }));
        } else {
          throw new Error("Token não retornado pelo serviço.");
        }
      }
    } catch (err) {
      const errorMessage =
        err && typeof err === "object" && "response" in err && (err as any).response?.data?.error?.message
          ? (err as any).response.data.error.message
          : err instanceof Error
          ? err.message
          : t("delivery.errors.tokenCreation");
      dispatchError(errorMessage);
    } finally {
      setCreatingToken(false);
    }
  }, [activeGateway, cardData, dispatchError, ensurePagSeguroKey, loadPagSeguroSdk, t]);

  const fillTestCard = useCallback(() => {
    setCardData((prev) => ({
      ...prev,
      cardNumber: "4013 5406 8274 6260",
      cardHolder: "APRO",
      cardExpMonth: "12",
      cardExpYear: "2027",
      cardCvv: "123",
      cardDoc: "123.456.789-09",
    }));
  }, []);

  const updateCustomerField = useCallback(<K extends keyof CustomerData>(field: K, value: CustomerData[K]) => {
    setCustomerData((prev) => ({ ...prev, [field]: value }));
  }, []);

  const updateCardField = useCallback(<K extends keyof PaymentCardData>(field: K, value: PaymentCardData[K]) => {
    setCardData((prev) => ({ ...prev, [field]: value }));
  }, []);

  return {
    customerData,
    cardData,
    orderId,
    orderStatus,
    paymentInfo,
    deliveryFeeCents,
    totalWithFee,
    quoteConfirmed,
    quoteLoading,
    quoteError,
    quoteId,
    serviceMode,
    processing,
    creatingToken,
    checkoutOpen,
    setCheckoutOpen,
    orderConfirmOpen,
    setOrderConfirmOpen,
    paymentSuccessOpen,
    setPaymentSuccessOpen,
    cartOpen,
    setCartOpen,
    handleCreateOrder,
    handleConfirmOrder,
    handlePayCard,
    handlePayPix,
    handleCreateToken,
    fillTestCard,
    handleBackToCart,
    updateCustomerField,
    updateCardField,
    activeGateway,
    installmentsConfig,
    setPaymentInfo,
    calculateDeliveryFee,
    setServiceMode,
    paymentMethod,
    setPaymentMethod,
    pixData,
  };
}

export type DeliveryCheckoutState = ReturnType<typeof useDeliveryCheckout>;
