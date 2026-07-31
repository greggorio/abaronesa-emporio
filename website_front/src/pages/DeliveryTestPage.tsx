import { useEffect, useMemo, useState } from "react";
import villaApi from "@/services/villaApi";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

type ProdutoSku = {
  id?: number;
  variacao?: string | null;
  preco?: number | null;
  precoPromocional?: number | null;
};

type Produto = {
  id: number;
  nome: string;
  preco?: number | null;
  preco_promocional?: number | null;
  precoPromocional?: number | null;
  skus?: ProdutoSku[];
};

type CardapioCategoria = {
  id: number;
  nome: string;
  produtos: Produto[];
};

type Option = {
  key: string;
  label: string;
  produtoId: number;
  skuId?: number | null;
  price?: number | null;
};

type CartItem = Option & { quantity: number };


type CreateDeliveryPaymentResponse = {
  paymentId: number;
  status: string;
  amountCents: number;
  feeCents?: number;
  currency?: string;
  quoteId?: string;
  qrPayload: string;
};

export default function DeliveryTestPage() {
  const [options, setOptions] = useState<Option[]>([]);
  const [selectedKey, setSelectedKey] = useState<string>("");
  const [quantity, setQuantity] = useState<number>(1);
  const [cart, setCart] = useState<CartItem[]>([]);
  const [customerName, setCustomerName] = useState<string>("");
  const [customerPhone, setCustomerPhone] = useState<string>("");
  const [customerEmail, setCustomerEmail] = useState<string>("");
  const [address, setAddress] = useState<string>("");
  const [notes, setNotes] = useState<string>("");
  const [externalId, setExternalId] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [payment, setPayment] = useState<CreateDeliveryPaymentResponse | null>(null);
  const [paymentStatus, setPaymentStatus] = useState<string>("");
  const [error, setError] = useState<string>("");

  useEffect(() => {
    const loadCardapio = async () => {
      setLoading(true);
      try {
        const { data } = await villaApi.get<CardapioCategoria[]>("/api/public/cardapio/v2");
        const opts: Option[] = [];
        data.forEach((cat) => {
          cat.produtos.forEach((p) => {
            const basePrice = p.precoPromocional ?? p.preco_promocional ?? p.preco;
            opts.push({
              key: `p-${p.id}`,
              label: p.nome,
              produtoId: p.id,
              price: basePrice ?? null,
            });
            (p.skus || []).forEach((sku) => {
              const skuPrice = sku.precoPromocional ?? sku.preco ?? basePrice;
              opts.push({
                key: `s-${p.id}-${sku.id}`,
                label: `${p.nome} (${sku.variacao || "SKU"})`,
                produtoId: p.id,
                skuId: sku.id,
                price: skuPrice ?? null,
              });
            });
          });
        });
        setOptions(opts);
        if (opts.length) setSelectedKey(opts[0].key);
      } catch (e: any) {
        console.error(e);
        setError(e?.response?.data?.error?.message || e?.message || "Falha ao carregar cardápio");
      } finally {
        setLoading(false);
      }
    };
    loadCardapio();
  }, []);

  const selectedOption = useMemo(() => options.find((o) => o.key === selectedKey), [options, selectedKey]);

  const addItem = () => {
    if (!selectedOption || quantity <= 0) return;
    setCart((prev) => {
      const existing = prev.find((i) => i.key === selectedOption.key);
      if (existing) {
        return prev.map((i) => (i.key === selectedOption.key ? { ...i, quantity: i.quantity + quantity } : i));
      }
      return [...prev, { ...selectedOption, quantity }];
    });
  };

  const removeItem = (key: string) => {
    setCart((prev) => prev.filter((i) => i.key !== key));
  };


  const totalCents = useMemo(() => {
    return cart.reduce((sum, item) => {
      const price = item.price ?? 0;
      return sum + Math.round(price * 100) * item.quantity;
    }, 0);
  }, [cart]);

  const createPaymentIntent = async () => {
    setSubmitting(true);
    setError("");
    setPayment(null);
    setPaymentStatus("");
    try {
      const payload = {
        customerName,
        customerPhone,
        customerEmail,
        dropoffAddress: address,
        dropoffNotes: notes,
        externalId: externalId || undefined,
        items: cart.map((c) => ({
          produtoId: c.produtoId,
          skuId: c.skuId,
          quantidade: c.quantity,
          observacoes: "",
          size: c.key.startsWith("s-") ? "small" : undefined,
        })),
      };
      const { data } = await villaApi.post<CreateDeliveryPaymentResponse>("/api/delivery/payments/intent", payload);
      setPayment(data);
      setPaymentStatus(data.status);
    } catch (e: any) {
      console.error(e);
      setError(e?.response?.data?.error?.message || e?.message || "Erro ao criar pagamento");
    } finally {
      setSubmitting(false);
    }
  };

  const simulatePayment = async () => {
    if (!payment?.paymentId) return;
    setSubmitting(true);
    setError("");
    try {
      await villaApi.post("/api/delivery/payments/webhook", {
        paymentId: payment.paymentId,
        evento: "payment.paid",
        referenciaProvedor: "manual-test",
      });
      setPaymentStatus("paid");
    } catch (e: any) {
      console.error(e);
      setError(e?.response?.data?.error?.message || e?.message || "Erro ao confirmar pagamento");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto p-4 space-y-4">
      <h1 className="text-2xl font-semibold">Teste de Delivery (Uber)</h1>
      <p className="text-sm text-muted-foreground">
        Carregando cardápio do ERP e criando pedido via <code>/api/delivery/orders</code>.
      </p>

      {error && <div className="p-3 rounded bg-red-100 text-red-800 text-sm">{error}</div>}

      <div className="grid gap-3 md:grid-cols-2">
        <div className="space-y-2">
          <label className="text-sm font-medium">Produto / SKU</label>
          <select
            className="border rounded p-2 w-full"
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={loading}
          >
            {options.map((opt) => (
              <option key={opt.key} value={opt.key}>
                {opt.label} {opt.price ? `- R$ ${(opt.price).toFixed(2)}` : ""}
              </option>
            ))}
          </select>
          <div className="flex items-center gap-2">
            <Input
              type="number"
              min={1}
              value={quantity}
              onChange={(e) => setQuantity(Number(e.target.value))}
              className="w-24"
            />
            <Button type="button" onClick={addItem} disabled={!selectedOption || loading}>
              Adicionar
            </Button>
          </div>
          <div className="text-sm text-muted-foreground">Itens no carrinho: {cart.length}</div>
        </div>

        <div className="space-y-2">
          <label className="text-sm font-medium">Endereço de entrega</label>
          <Textarea value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Rua, número, cidade, UF" />
          <label className="text-sm font-medium">Notas para entrega</label>
          <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Instruções adicionais" />
        </div>
      </div>

      <div className="grid gap-3 md:grid-cols-3">
        <div className="space-y-2">
          <label className="text-sm font-medium">Nome</label>
          <Input value={customerName} onChange={(e) => setCustomerName(e.target.value)} placeholder="Cliente" />
        </div>
        <div className="space-y-2">
          <label className="text-sm font-medium">Telefone</label>
          <Input value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} placeholder="+55..." />
        </div>
        <div className="space-y-2">
          <label className="text-sm font-medium">E-mail</label>
          <Input value={customerEmail} onChange={(e) => setCustomerEmail(e.target.value)} placeholder="opcional" />
        </div>
      </div>

      <div className="space-y-2">
        <label className="text-sm font-medium">External ID (opcional)</label>
        <Input value={externalId} onChange={(e) => setExternalId(e.target.value)} placeholder="Identificador interno" />
      </div>

      <div className="space-y-2">
        <h2 className="text-lg font-medium">Carrinho</h2>
        {cart.length === 0 && <div className="text-sm text-muted-foreground">Nenhum item adicionado.</div>}
        {cart.length > 0 && (
          <div className="border rounded p-3 space-y-2">
            {cart.map((item) => (
              <div key={item.key} className="flex items-center justify-between text-sm">
                <div>
                  {item.label} — qtd {item.quantity}
                </div>
                <Button variant="ghost" size="sm" onClick={() => removeItem(item.key)}>
                  Remover
                </Button>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="space-y-2">
        <div className="text-sm text-muted-foreground">
          Total (itens): R$ {(totalCents / 100).toFixed(2)}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={createPaymentIntent} disabled={submitting || cart.length === 0}>
            {submitting ? "Processando..." : "Gerar pagamento"}
          </Button>
          <Button
            variant="outline"
            onClick={simulatePayment}
            disabled={submitting || !payment?.paymentId || paymentStatus === "paid"}
          >
            Simular pagamento
          </Button>
        </div>
      </div>

      {payment && (
        <div className="border rounded p-4 space-y-1">
          <div className="font-medium">Pagamento gerado</div>
          <div className="text-sm">paymentId: {payment.paymentId}</div>
          <div className="text-sm">status: {paymentStatus || payment.status}</div>
          <div className="text-sm">fee: {payment.feeCents ? `R$ ${(payment.feeCents / 100).toFixed(2)}` : '-'}</div>
          <div className="text-sm">total: R$ {(payment.amountCents / 100).toFixed(2)}</div>
          <div className="text-sm">quoteId: {payment.quoteId || '-'}</div>
          <div className="text-sm break-all">qrPayload: {payment.qrPayload}</div>
        </div>
      )}

    </div>
  );
}
