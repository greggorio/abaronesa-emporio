import { RefObject, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { DeliveryCheckoutState } from "@/hooks/useDeliveryCheckout";
import { CartItemData } from "@/components/mesa/CartItem";
import { CreditCard } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { useDeliveryI18n } from "@/i18n/useDeliveryI18n";

type Props = {
  paymentSectionRef: RefObject<HTMLDivElement | null>;
  total: number;
  checkout: DeliveryCheckoutState;
  cartHasItems: boolean;
  cartItems: CartItemData[];
  locale?: string;
};

type Step = "dados" | "resumo" | "pagamento";

const STATUS_LABEL_KEYS: Record<string, string> = {
  FAILED: "delivery.statusLabels.failed",
  PAID: "delivery.statusLabels.paid",
  PENDING: "delivery.statusLabels.pending",
  IN_PROCESS: "delivery.statusLabels.inProcess",
  AUTHORIZED: "delivery.statusLabels.authorized",
  CANCELED: "delivery.statusLabels.canceled",
  EXPIRED: "delivery.statusLabels.expired",
};

const DeliveryCheckoutSection = ({
  paymentSectionRef,
  total,
  checkout,
  cartHasItems,
  cartItems,
  locale: externalLocale,
}: Props) => {
  const {
    cardData: { cardNumber, cardHolder, cardExpMonth, cardExpYear, cardCvv, cardDoc, cardToken },
    creatingToken,
    handleCreateToken,
    handlePayPix,
    paymentInfo,
    processing,
    paymentMethod,
    setPaymentMethod,
    pixData,
    activeGateway,
  } = checkout;
  const { serviceMode, setServiceMode } = checkout;
  const { t, formatCurrency, locale: currentLocale, setLocale } = useDeliveryI18n();

  const [step, setStep] = useState<Step>("dados");
  const [cardBrand, setCardBrand] = useState<string | null>(null);
  const [useCustomerData, setUseCustomerData] = useState(true);
  const { toast } = useToast();
  const [lastToast, setLastToast] = useState<string | null>(null);
  const isPickup = serviceMode === "PICKUP";
  const translateStatus = (value?: string) => {
    if (!value) return "-";
    const normalized = value.toUpperCase();
    const key = STATUS_LABEL_KEYS[normalized];
    return key ? t(key) : value;
  };
  const pixStatus = (pixData?.status || paymentInfo?.status || "").toString().toUpperCase();
  const isPixPending = pixStatus === "PENDING" || pixStatus === "IN_PROCESS" || pixStatus === "AUTHORIZED";
  const cardInstallmentsOptions = useMemo(() => {
    if (!checkout.installmentsConfig.enabled) return [1];
    if (total * 100 < checkout.installmentsConfig.minAmountCents) return [1];
    const maxTimes = Math.max(1, checkout.installmentsConfig.maxTimes || 1);
    return Array.from({ length: maxTimes }, (_, i) => i + 1);
  }, [checkout.installmentsConfig.enabled, checkout.installmentsConfig.maxTimes, checkout.installmentsConfig.minAmountCents, total]);

  useEffect(() => {
    if (externalLocale && externalLocale !== currentLocale) {
      setLocale(externalLocale as any);
    }
  }, [externalLocale, currentLocale, setLocale]);

  const formatCpf = (value: string) => {
    const digits = value.replace(/\D/g, "").slice(0, 11);
    return digits
      .replace(/^(\d{3})(\d)/, "$1.$2")
      .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
      .replace(/\.(\d{3})(\d)/, ".$1-$2")
      .replace(/(-\d{2})\d+$/, "$1");
  };

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

  useEffect(() => {
    if (useCustomerData) {
      const name = `${checkout.customerData.customerName}`.toUpperCase().trim();
      checkout.updateCardField("cardHolder", name);
    }
  }, [useCustomerData, checkout.customerData.customerName, checkout.updateCardField]);

  useEffect(() => {
    setCardBrand(detectCardBrand(cardNumber));
  }, [cardNumber]);

  useEffect(() => {
    const friendly = checkout.paymentInfo?.friendlyMessage || "";
    const status = checkout.paymentInfo?.status?.toLowerCase() || "";
    const isSuccess = status === "paid" || status === "approved";
    const isPending =
      status === "pending" ||
      status === "in_process" ||
      status === "authorized" ||
      friendly.toLowerCase().includes("pending");
    if (!friendly || isSuccess || isPending) {
      setLastToast(null);
      return;
    }
      if (friendly !== lastToast) {
        toast({
          title: t("delivery.toast.paymentFailed.title"),
          description: friendly,
          variant: "destructive",
        });
      setLastToast(friendly);
    }
  }, [checkout.paymentInfo?.friendlyMessage, checkout.paymentInfo?.status, lastToast, toast, t]);

  const pixExpirationLabel = useMemo(() => {
    if (!pixData?.expiresAt) return null;
    const expiresAt = new Date(pixData.expiresAt);
    if (Number.isNaN(expiresAt.getTime())) return null;
    const diffMs = expiresAt.getTime() - Date.now();
    const diffMinutes = Math.max(0, Math.round(diffMs / 60000));
    const day = String(expiresAt.getDate()).padStart(2, "0");
    const month = String(expiresAt.getMonth() + 1).padStart(2, "0");
    const year = expiresAt.getFullYear();
    const timeLabel = expiresAt.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    return `${day}/${month}/${year} ${timeLabel} (${diffMinutes} min)`;
  }, [pixData?.expiresAt]);

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
    if (step !== "pagamento") return;

    handleCreateToken();
  }, [
    cardNumber,
    cardHolder,
    cardExpMonth,
    cardExpYear,
    cardCvv,
    cardDoc,
    cardToken,
    creatingToken,
    handleCreateToken,
    step,
    paymentMethod,
  ]);

  const handleAdvanceResumo = async () => {
    const res = await checkout.calculateDeliveryFee();
    if (res) {
      setStep("resumo");
    }
  };

  const feeDisplay = checkout.quoteConfirmed
    ? formatCurrency(checkout.deliveryFeeCents, { fromCents: true })
    : "—";
  const totalDisplay = checkout.quoteConfirmed
    ? formatCurrency(checkout.totalWithFee, { fromCents: false })
    : "—";
  const feeLabelKey = isPickup
    ? "delivery.checkout.summary.fee.pickup"
    : "delivery.checkout.summary.fee.delivery";
  const modeTitle = isPickup
    ? t("delivery.checkout.serviceMode.pickup.title")
    : t("delivery.checkout.serviceMode.delivery.title");
  const modeDescriptionCard = isPickup
    ? t("delivery.checkout.card.summary.modeDescription.pickup")
    : t("delivery.checkout.card.summary.modeDescription.delivery");

  const itemsTotal = useMemo(
    () => cartItems.reduce((sum, item) => sum + item.preco * item.quantidade, 0),
    [cartItems]
  );

  return (
    <div ref={paymentSectionRef} className="space-y-3">
      <Card className="p-4 space-y-1">
        <div className="text-sm text-muted-foreground">{t("delivery.checkout.card.summary.created")}</div>
        <div className="text-xl font-semibold">
          {t("delivery.checkout.card.summary.orderLabel", {
            orderId: checkout.orderId ?? "—",
          })}
        </div>
        <div className="text-sm text-muted-foreground">
          {t("delivery.checkout.card.summary.itemsTotal", {
            total: formatCurrency(itemsTotal, { fromCents: false }),
          })}
        </div>
        <div className="text-sm text-muted-foreground">
          <span className="font-medium">{t("delivery.checkout.card.summary.modeLabel")}</span> {modeTitle}
        </div>
        <div className="text-xs uppercase tracking-widest text-muted-foreground/80">{modeDescriptionCard}</div>
      </Card>

      <Accordion type="single" collapsible={false} value={step} onValueChange={(value) => value && setStep(value as Step)}>
        <AccordionItem value="dados" className="border rounded-lg overflow-hidden">
          <AccordionTrigger className="px-4">
            {isPickup ? t("delivery.checkout.section.dataTitle.pickup") : t("delivery.checkout.section.dataTitle.delivery")}
          </AccordionTrigger>
          <AccordionContent className="px-4 pb-4">
            <div className="space-y-3">
              <div className="space-y-2">
                <div className="text-xs uppercase tracking-widest text-muted-foreground">
                  {t("delivery.checkout.section.serviceMode")}
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  <Button
                    variant={serviceMode === "DELIVERY" ? "secondary" : "outline"}
                    className="flex flex-col items-start text-sm"
                    onClick={() => setServiceMode("DELIVERY")}
                  >
                    <span className="font-semibold">{t("delivery.checkout.serviceMode.delivery.title")}</span>
                    <span className="text-xs text-muted-foreground">
                      {t("delivery.checkout.serviceMode.delivery.description")}
                    </span>
                  </Button>
                  <Button
                    variant={serviceMode === "PICKUP" ? "secondary" : "outline"}
                    className="flex flex-col items-start text-sm"
                    onClick={() => setServiceMode("PICKUP")}
                  >
                    <span className="font-semibold">{t("delivery.checkout.serviceMode.pickup.title")}</span>
                    <span className="text-xs text-muted-foreground">
                      {t("delivery.checkout.serviceMode.pickup.description")}
                    </span>
                  </Button>
                </div>
              </div>
              <div className="space-y-3">
                <Input
                  placeholder={t("delivery.checkout.form.name.placeholder")}
                  value={checkout.customerData.customerName}
                  onChange={(event) => checkout.updateCustomerField("customerName", event.target.value)}
                />
                <Input
                  placeholder={t("delivery.checkout.form.phone.placeholder")}
                  value={checkout.customerData.customerPhone}
                  onChange={(event) => checkout.updateCustomerField("customerPhone", event.target.value)}
                />
                <Input
                  placeholder={t("delivery.checkout.form.email.placeholder")}
                  value={checkout.customerData.customerEmail}
                  onChange={(event) => checkout.updateCustomerField("customerEmail", event.target.value)}
                />
                <Input
                  placeholder={t("delivery.checkout.form.cpf.placeholder")}
                  value={checkout.customerData.customerCpf}
                  onChange={(event) => checkout.updateCustomerField("customerCpf", event.target.value)}
                />
                {!isPickup ? (
                  <>
                    <Input
                      placeholder={t("delivery.checkout.form.address.placeholder")}
                      value={checkout.customerData.dropoffAddress}
                      onChange={(event) => checkout.updateCustomerField("dropoffAddress", event.target.value)}
                    />
                    <Input
                      placeholder={t("delivery.checkout.form.notes.placeholder")}
                      value={checkout.customerData.dropoffNotes}
                      onChange={(event) => checkout.updateCustomerField("dropoffNotes", event.target.value)}
                    />
                  </>
                ) : (
                  <>
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-foreground/80">
                      {t("delivery.checkout.form.pickupInfo")}
                    </div>
                    <Input
                      placeholder={t("delivery.checkout.form.pickupNotes.placeholder")}
                      value={checkout.customerData.dropoffNotes}
                      onChange={(event) => checkout.updateCustomerField("dropoffNotes", event.target.value)}
                    />
                  </>
                )}
              </div>
              {checkout.quoteError && <div className="text-xs text-destructive">{checkout.quoteError}</div>}
              <div className="flex justify-end">
                <Button
                  onClick={handleAdvanceResumo}
                  disabled={!cartHasItems || checkout.quoteLoading}
                  className="min-w-[160px]"
                >
                  {checkout.quoteLoading
                    ? t("delivery.checkout.button.calculateFeeLoading")
                    : t("delivery.checkout.button.calculateFee")}
                </Button>
              </div>
            </div>
          </AccordionContent>
        </AccordionItem>

        <AccordionItem value="resumo" className="border rounded-lg overflow-hidden">
          <AccordionTrigger className="px-4">{t("delivery.checkout.summary.title")}</AccordionTrigger>
          <AccordionContent className="px-4 pb-4">
            <div className="space-y-3">
              <div className="text-sm space-y-1">
                <div className="font-semibold">{t("delivery.checkout.summary.customerTitle")}</div>
                <div>{checkout.customerData.customerName}</div>
                <div>{checkout.customerData.customerPhone}</div>
                <div>{checkout.customerData.customerEmail}</div>
                <div>{checkout.customerData.dropoffAddress}</div>
                {checkout.customerData.dropoffNotes ? (
                  <div className="text-muted-foreground">{checkout.customerData.dropoffNotes}</div>
                ) : null}
              </div>
              <div className="text-sm text-muted-foreground">
                <span className="font-semibold">{t("delivery.checkout.summary.modeLabel")}</span> {modeTitle}
              </div>
              <div className="text-xs uppercase tracking-widest text-muted-foreground/70">
                {isPickup
                  ? t("delivery.checkout.summary.modeDescription.pickup")
                  : t("delivery.checkout.summary.modeDescription.delivery")}
              </div>
              <Separator />
              <div className="space-y-2 text-sm">
                <div className="font-semibold">{t("delivery.checkout.summary.itemsTitle")}</div>
                {cartItems.map((item, idx) => (
                  <div key={`${item.produtoId}-${item.skuId ?? "base"}-${idx}`} className="flex justify-between">
                    <span className="truncate">{item.nome} (x{item.quantidade})</span>
                    <span className="font-semibold">
                      {formatCurrency(item.preco * item.quantidade, { fromCents: false })}
                    </span>
                  </div>
                ))}
              </div>
              <Separator />
              <div className="flex items-center justify-between text-sm">
                <span>{t("delivery.checkout.summary.totalLabel")}</span>
                <span className="font-semibold">
                  {formatCurrency(itemsTotal, { fromCents: false })}
                </span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span>{t(feeLabelKey)}</span>
                <span className="font-semibold">{feeDisplay}</span>
              </div>
              <div className="flex items-center justify-between text-sm font-semibold">
                <span>{t("delivery.checkout.summary.totalWithFee")}</span>
                <span>{totalDisplay}</span>
              </div>
              <div className="flex items-center justify-between gap-2">
                <Button variant="outline" onClick={() => setStep("dados")}>
                  {t("delivery.checkout.summary.button.back")}
                </Button>
                <Button
                  onClick={() => setStep("pagamento")}
                  disabled={!checkout.quoteConfirmed}
                  className="min-w-[180px]"
                >
                  {isPickup
                    ? t("delivery.checkout.summary.button.pickup")
                    : t("delivery.checkout.summary.button.payment")}
                </Button>
              </div>
            </div>
          </AccordionContent>
        </AccordionItem>

        <AccordionItem value="pagamento" className="border rounded-lg overflow-hidden">
          <AccordionTrigger className="px-4">{t("delivery.payment.title")}</AccordionTrigger>
          <AccordionContent className="px-4 pb-4">
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">{t("delivery.payment.totalWithFeeLabel")}</span>
                <span className="text-base font-semibold">
                  {formatCurrency(checkout.totalWithFee, { fromCents: false })}
                </span>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button
                  variant={paymentMethod === "CARD" ? "secondary" : "outline"}
                  onClick={() => setPaymentMethod("CARD")}
                >
                  {t("delivery.payment.method.card")}
                </Button>
                <Button
                  variant={paymentMethod === "PIX" ? "secondary" : "outline"}
                  onClick={() => setPaymentMethod("PIX")}
                >
                  {t("delivery.payment.method.pix")}
                </Button>
              </div>
              {paymentMethod === "PIX" ? (
                <div className="space-y-3">
                  {pixData ? (
                    <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                      {t("delivery.payment.pix.instructions.existing")}
                    </div>
                  ) : (
                    <div className="text-sm text-muted-foreground">
                      {t("delivery.payment.pix.instructions.new")}
                    </div>
                  )}
                  {pixData?.pixQrCodeBase64 ? (
                    <div className="flex justify-center">
                      <img
                        src={`data:image/png;base64,${pixData.pixQrCodeBase64}`}
                        alt={t("delivery.payment.pix.qrAlt")}
                        className="w-56 h-56 object-contain rounded border border-muted"
                      />
                    </div>
                  ) : null}
                  {pixData?.qrCode ? (
                    <div className="space-y-2">
                      <div className="text-xs uppercase tracking-wide text-muted-foreground">
                        {t("delivery.payment.pix.copyLabel")}
                      </div>
                      <div className="flex items-start gap-2">
                        <textarea
                          readOnly
                          className="w-full text-xs border rounded-md p-2 bg-muted/40"
                          rows={3}
                          value={pixData.qrCode}
                        />
                        <Button
                          variant="outline"
                          onClick={() => {
                            if (pixData.qrCode) navigator.clipboard?.writeText(pixData.qrCode);
                          }}
                        >
                          {t("delivery.payment.pix.copyButton")}
                        </Button>
                      </div>
                    </div>
                  ) : null}
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>
                      {t("delivery.payment.pix.statusLabel")} {translateStatus(pixData?.status || paymentInfo?.status)}
                    </span>
                    {pixExpirationLabel ? (
                      <span>
                        {t("delivery.payment.pix.expiresLabel")} {pixExpirationLabel}
                      </span>
                    ) : null}
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      className="w-full"
                      size="lg"
                      onClick={handlePayPix}
                      disabled={processing || !checkout.quoteConfirmed}
                    >
                      {processing
                        ? t("delivery.payment.pix.button.generating")
                        : pixData
                        ? t("delivery.payment.pix.button.new")
                        : t("delivery.payment.pix.button.generate")}
                    </Button>
                  </div>
                  {paymentInfo?.friendlyMessage && !isPixPending && (
                    <div className="text-xs text-muted-foreground/80">{paymentInfo.friendlyMessage}</div>
                  )}
                </div>
              ) : (
                <div className="space-y-2">
                  <Label htmlFor="cardNumber" className="text-xs uppercase tracking-wide text-muted-foreground">
                    {t("delivery.payment.card.label.number")}
                  </Label>
                  <div className="relative">
                    <Input
                      id="cardNumber"
                      placeholder={t("delivery.payment.card.placeholder.number")}
                      value={checkout.cardData.cardNumber}
                      onChange={(event) => checkout.updateCardField("cardNumber", formatCardNumber(event.target.value))}
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
                      {t("delivery.payment.card.label.holder")}
                    </Label>
                    <Input
                      id="cardHolder"
                      placeholder={t("delivery.payment.card.placeholder.holder")}
                      value={checkout.cardData.cardHolder}
                      onChange={(event) => checkout.updateCardField("cardHolder", event.target.value.toUpperCase())}
                      disabled={useCustomerData}
                    />
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <Checkbox id="useCustomerData" checked={useCustomerData} onCheckedChange={setUseCustomerData} />
                      <Label htmlFor="useCustomerData">{t("delivery.payment.card.useCustomerData")}</Label>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <Label htmlFor="expiryDate" className="text-xs uppercase tracking-wide text-muted-foreground">
                        {t("delivery.payment.card.label.expiry")}
                      </Label>
                      <Input
                        id="expiryDate"
                        placeholder={t("delivery.payment.card.placeholder.expiry")}
                        value={checkout.cardData.cardExpMonth && checkout.cardData.cardExpYear ? `${checkout.cardData.cardExpMonth}/${checkout.cardData.cardExpYear}` : ""}
                        onChange={(event) => {
                          const [month, year = ""] = formatExpiry(event.target.value).split("/");
                          checkout.updateCardField("cardExpMonth", month);
                          checkout.updateCardField("cardExpYear", year);
                        }}
                        maxLength={5}
                      />
                    </div>
                    <div>
                      <Label htmlFor="cvv" className="text-xs uppercase tracking-wide text-muted-foreground">
                        {t("delivery.payment.card.label.cvv")}
                      </Label>
                      <Input
                        id="cvv"
                        type="password"
                        placeholder={t("delivery.payment.card.placeholder.cvv")}
                        value={checkout.cardData.cardCvv}
                        onChange={(event) => checkout.updateCardField("cardCvv", event.target.value.replace(/\D/g, ""))}
                        maxLength={4}
                      />
                    </div>
                  </div>
                  <div>
                    <Label htmlFor="cardDoc" className="text-xs uppercase tracking-wide text-muted-foreground">
                      {t("delivery.payment.card.label.doc")}
                    </Label>
                    <Input
                      id="cardDoc"
                      placeholder={t("delivery.payment.card.placeholder.doc")}
                      maxLength={14}
                      value={checkout.cardData.cardDoc}
                      onChange={(event) => checkout.updateCardField("cardDoc", formatCpf(event.target.value))}
                    />
                  </div>
                  <Button variant="ghost" size="sm" onClick={checkout.fillTestCard} className="w-full">
                    {t("delivery.payment.card.fillTestData")}
                  </Button>
                  <div>
                  <Label className="text-xs uppercase tracking-wide text-muted-foreground">
                    {t("delivery.payment.card.installments.label")}
                  </Label>
                  <Select
                    value={checkout.cardData.installments.toString()}
                    onValueChange={(value) => checkout.updateCardField("installments", Number(value))}
                  >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {cardInstallmentsOptions.map((option) => (
                        <SelectItem key={option} value={option.toString()}>
                          {t("delivery.payment.card.installments.detail", {
                            count: option,
                            amount: formatCurrency(checkout.totalWithFee / option, { fromCents: false }),
                            suffix:
                              option === 1
                                ? t("delivery.payment.card.installments.detailSingle")
                                : t("delivery.payment.card.installments.detailWithoutInterest"),
                          })}
                        </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <Button
                    className="w-full flex items-center justify-center gap-2"
                    variant="secondary"
                    disabled={!checkout.cardData.cardToken || checkout.processing}
                    onClick={checkout.handlePayCard}
                  >
                    {checkout.processing ? (
                      <>
                        <CreditCard className="h-4 w-4 animate-spin" />
                        {t("delivery.payment.card.button.processing")}
                      </>
                    ) : (
                      <>
                        <CreditCard className="h-4 w-4" />
                        {t("delivery.payment.card.button.pay", {
                          amount: formatCurrency(checkout.totalWithFee, { fromCents: false }),
                        })}
                      </>
                    )}
                  </Button>
                  <Separator />
                  <div className="text-sm text-muted-foreground">
                    {t("delivery.payment.statusLabel")} {translateStatus(checkout.orderStatus)}
                  </div>
                  {checkout.paymentInfo && (
                    <div className="text-xs text-muted-foreground">
                      <div>
                        {t("delivery.payment.info.paymentId", { id: checkout.paymentInfo.id || "-" })}
                      </div>
                      <div>
                        {t("delivery.payment.info.paymentStatus", {
                          status: translateStatus(checkout.paymentInfo.status),
                        })}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          </AccordionContent>
        </AccordionItem>
      </Accordion>
    </div>
  );
}

export { DeliveryCheckoutSection };
export default DeliveryCheckoutSection;
