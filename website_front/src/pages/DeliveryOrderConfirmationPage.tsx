import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import axios from "@/lib/axios";
import { apiConfig } from "@/config/api";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import { Card } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import { CheckCircle2, MapPin, Phone, ShoppingBag, User } from "lucide-react";
import { CartItemData } from "@/components/mesa/CartItem";
import { DeliveryOrderDetail, ServiceMode } from "@/types/delivery";
import { useDeliveryI18n } from "@/i18n/useDeliveryI18n";

type LocationState = {
  orderId?: number;
  totalWithFee?: number;
  deliveryFeeCents?: number;
  customerData?: {
    customerName?: string;
    customerPhone?: string;
    customerEmail?: string;
    dropoffAddress?: string;
    dropoffNotes?: string;
  };
  items?: CartItemData[];
  serviceMode?: ServiceMode;
};

export default function DeliveryOrderConfirmationPage() {
  const navigate = useNavigate();
  const { orderId: orderIdParam } = useParams<{ orderId: string }>();
  const { state } = useLocation();
  const locationState = (state || {}) as LocationState;
  const [order, setOrder] = useState<DeliveryOrderDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const { t, formatCurrency: i18nFormatCurrency } = useDeliveryI18n();

  const orderId = locationState.orderId ?? (orderIdParam ? Number(orderIdParam) : undefined);
  const fallbackServiceMode = locationState.serviceMode ?? "DELIVERY";
  const resolvedServiceMode = order?.serviceMode ?? fallbackServiceMode;
  const isPickup = resolvedServiceMode === "PICKUP";
  const orderIdLabel = orderId ? ` (#${orderId})` : "";
  const formatMaybeCurrency = (value?: number, options?: { fromCents?: boolean }) =>
    typeof value === "number" ? i18nFormatCurrency(value, options) : "—";

  useEffect(() => {
    const fetchOrder = async () => {
      if (!orderId) return;
      try {
        setLoading(true);
        const { data } = await axios.get<DeliveryOrderDetail>(`${apiConfig.erp.deliveryOrders}/${orderId}`);
        setOrder(data);
      } catch (err) {
        console.error("Erro ao carregar pedido confirmado", err);
      } finally {
        setLoading(false);
      }
    };
    fetchOrder();
  }, [orderId]);

  const deliveryFee = useMemo(() => {
    if (typeof order?.deliveryFeeCents === "number") return order.deliveryFeeCents / 100;
    if (typeof locationState.deliveryFeeCents === "number") return locationState.deliveryFeeCents / 100;
    return undefined;
  }, [order?.deliveryFeeCents, locationState.deliveryFeeCents]);

  const totalWithFee = useMemo(() => {
    if (typeof locationState.totalWithFee === "number") return locationState.totalWithFee;
    if (typeof order?.total === "number") return order.total;
    return undefined;
  }, [locationState.totalWithFee, order?.total]);

  const items = useMemo(() => {
    if (locationState.items?.length) return locationState.items;
    if (order?.items?.length) {
      return order.items.map((item) => ({
        nome: item.nome ?? "Item",
        quantidade: item.quantidade ?? 1,
        preco: item.preco ?? 0,
        produtoId: 0,
        observacoes: item.observacoes ?? undefined,
      })) as CartItemData[];
    }
    return [];
  }, [locationState.items, order?.items]);

  const customer = {
    name: order?.customerName ?? locationState.customerData?.customerName,
    phone: order?.customerPhone ?? locationState.customerData?.customerPhone,
    email: order?.customerEmail ?? locationState.customerData?.customerEmail,
    address: order?.dropoffAddress ?? locationState.customerData?.dropoffAddress,
    notes: order?.dropoffNotes ?? locationState.customerData?.dropoffNotes,
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-[hsl(var(--accent)/0.08)] via-soft-white to-white flex flex-col">
      <Header />
      <main className="flex-1">
        <div className="max-w-4xl mx-auto px-4 py-10 space-y-6">
          <div className="bg-white rounded-2xl shadow-sm border border-[hsl(var(--accent)/0.12)] p-6 sm:p-8 flex flex-col gap-4 sm:gap-6">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
              <div className="flex items-center gap-3">
                <div className="h-12 w-12 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center shadow-inner">
                  <CheckCircle2 className="h-7 w-7" />
                </div>
                <div>
                  <p className="text-xs uppercase tracking-widest text-foreground/60">
                    {t("delivery.orderConfirmation.banner.status")}
                  </p>
                  <h1 className="text-2xl sm:text-3xl font-display text-foreground">
                    {t("delivery.orderConfirmation.banner.title")}
                  </h1>
                  <p className="text-sm text-foreground/70">
                    {isPickup
                      ? t("delivery.orderConfirmation.banner.subtitle.pickup", { orderIdLabel })
                      : t("delivery.orderConfirmation.banner.subtitle.delivery", { orderIdLabel })}
                  </p>
                </div>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => navigate("/delivery-menu")}>
                  {t("delivery.orderConfirmation.cta.newOrder")}
                </Button>
                <Button onClick={() => navigate("/areacliente")}>
                  {t("delivery.orderConfirmation.cta.trackOrder")}
                </Button>
              </div>
            </div>

            <Card className="border border-[hsl(var(--accent)/0.15)] shadow-none">
              <div className="p-5 grid gap-4 sm:grid-cols-3">
                <div className="space-y-1">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">
                    {t("delivery.orderConfirmation.summary.orderLabel")}
                  </p>
                  <p className="text-xl font-semibold text-foreground">#{orderId ?? "—"}</p>
                  <p className="text-sm text-foreground/70">{t("delivery.orderConfirmation.summary.statusLabel")}</p>
                </div>
                <div className="space-y-1">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">
                    {t("delivery.orderConfirmation.summary.totalLabel")}
                  </p>
                  <p className="text-xl font-semibold text-foreground">
                    {formatMaybeCurrency(totalWithFee, { fromCents: false })}
                  </p>
                  <p className="text-sm text-foreground/70">
                    {t("delivery.orderConfirmation.summary.feeLabel", {
                      fee:
                        deliveryFee !== undefined
                          ? formatMaybeCurrency(deliveryFee, { fromCents: false })
                          : "—",
                    })}
                  </p>
                </div>
                <div className="space-y-1">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">
                    {t("delivery.orderConfirmation.summary.paymentLabel")}
                  </p>
                  <p className="text-sm text-foreground/80">
                    {t("delivery.orderConfirmation.summary.paymentMethod")}
                  </p>
                  <p className="text-sm text-foreground/70">
                    {t("delivery.orderConfirmation.summary.paymentReceipt")}
                  </p>
                </div>
              </div>
            </Card>

            <div className="grid gap-4 sm:grid-cols-2">
              <Card className="border border-[hsl(var(--accent)/0.15)] shadow-none">
                <div className="p-5 space-y-3">
                  <div className="flex items-center gap-2">
                    <User className="w-4 h-4 text-[hsl(var(--accent))]" />
                    <h2 className="text-lg font-semibold text-foreground">
                      {t("delivery.orderConfirmation.customer.title")}
                    </h2>
                  </div>
                  <Separator />
                  <div className="text-sm text-foreground/80 space-y-1">
                    <div className="font-semibold">{customer.name || "Cliente"}</div>
                    {customer.email && <div>{customer.email}</div>}
                    {customer.phone && (
                      <div className="flex items-center gap-2 text-foreground/70">
                        <Phone className="w-4 h-4" /> {customer.phone}
                      </div>
                    )}
                  </div>
                </div>
              </Card>

              <Card className="border border-[hsl(var(--accent)/0.15)] shadow-none">
                <div className="p-5 space-y-3">
                  <div className="flex items-center gap-2">
                    <MapPin className="w-4 h-4 text-[hsl(var(--accent))]" />
                    <h2 className="text-lg font-semibold text-foreground">
                      {isPickup
                        ? t("delivery.orderConfirmation.location.title.pickup")
                        : t("delivery.orderConfirmation.location.title.delivery")}
                    </h2>
                  </div>
                  <Separator />
                  <div className="text-sm text-foreground/80 space-y-1">
                    {isPickup ? (
                      <>
                        <div>{t("delivery.orderConfirmation.location.pickupInfo")}</div>
                        <div className="text-foreground/60">
                          {t("delivery.orderConfirmation.location.pickupDetails")}
                        </div>
                      </>
                    ) : customer.address ? (
                      <div>{customer.address}</div>
                    ) : (
                      <div>{t("delivery.orderConfirmation.location.addressMissing")}</div>
                    )}
                    {customer.notes && (
                      <div className="text-foreground/60">
                        {t("delivery.orderConfirmation.location.notes", { notes: customer.notes })}
                      </div>
                    )}
                  </div>
                </div>
              </Card>
            </div>

            <Card className="border border-[hsl(var(--accent)/0.15)] shadow-none">
              <div className="p-5 space-y-3">
                <div className="flex items-center gap-2">
                  <ShoppingBag className="w-4 h-4 text-[hsl(var(--accent))]" />
                  <h2 className="text-lg font-semibold text-foreground">
                    {t("delivery.orderConfirmation.items.title")}
                  </h2>
                </div>
                <Separator />
                {loading ? (
                  <div className="text-sm text-muted-foreground">{t("delivery.orderConfirmation.items.loading")}</div>
                ) : items.length ? (
                  <div className="space-y-2 text-sm text-foreground/80">
                    {items.map((item, idx) => (
                      <div
                        key={`${item.produtoId ?? "item"}-${idx}`}
                        className="flex items-start justify-between gap-3 rounded-lg bg-muted/40 px-3 py-2"
                      >
                        <div className="flex-1">
                          <div className="font-semibold text-foreground">{item.nome}</div>
                          {item.observacoes && (
                            <div className="text-xs text-muted-foreground">
                              {t("delivery.orderConfirmation.items.observation", { notes: item.observacoes })}
                            </div>
                          )}
                        </div>
                        <div className="text-sm text-foreground">x{item.quantidade ?? 1}</div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-sm text-muted-foreground">{t("delivery.orderConfirmation.items.empty")}</div>
                )}
              </div>
            </Card>
          </div>
        </div>
      </main>
      <Footer />
    </div>
  );
}
